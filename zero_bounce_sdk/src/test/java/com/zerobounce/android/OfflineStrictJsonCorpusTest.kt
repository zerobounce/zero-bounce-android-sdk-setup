package com.zerobounce.android

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

/**
 * Strict JSON (check E7) on every JSON call of [ZeroBounceClient] and
 * [ZeroBounceWarmup]: each REJECT body of the shared corpus is an API error with
 * status 200, and each ACCEPT body is read as JSON (it may still not be the shape a
 * call expects, which is then an error with status 200, but never "not valid JSON").
 *
 * The corpus, `src/test/resources/strict-json-corpus.tsv`, is a byte-identical copy
 * of `sdk-docs/skills/zerobounce-sdk/references/strict-json-corpus.tsv` in
 * zerobounce/sdk-docs; copy it again when that file changes. Each line's second
 * field is a JSON-encoded string whose value is the exact response body.
 */
class OfflineStrictJsonCorpusTest {

    private val context: Context = mock()
    private lateinit var dir: File
    private lateinit var csv: File

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "zb-android-corpus-${System.nanoTime()}")
        dir.mkdirs()
        whenever(context.getExternalFilesDir(null)).thenReturn(dir)
        csv = File(dir, "in.csv").apply { writeText("email\na@example.com\n") }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private class Entry(val accept: Boolean, val body: String) {
        override fun toString() = (if (accept) "ACCEPT " else "REJECT ") + Gson().toJson(body)
    }

    private fun corpus(): List<Entry> {
        val stream = javaClass.getResourceAsStream("/strict-json-corpus.tsv")
        assertNotNull("strict-json-corpus.tsv is on the test classpath", stream)
        val entries = stream!!.use { it.readBytes().toString(Charsets.UTF_8) }
            .split("\n")
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                val fields = line.split("\t", limit = 2)
                assertEquals(line, 2, fields.size)
                assertTrue(fields[0], fields[0] == "ACCEPT" || fields[0] == "REJECT")
                Entry(fields[0] == "ACCEPT", JsonParser.parseString(fields[1]).asString)
            }
        assertTrue("the corpus has ${entries.size} entries", entries.size >= 50)
        return entries
    }

    private fun isDownload(method: Method): Boolean {
        val name = method.name.lowercase()
        return name.contains("getfile") || name.startsWith("download")
    }

    /**
     * Where an empty 2xx (zero bytes; a blank body is not empty) is a documented
     * success, as the corpus header says: the Inbox Placement DELETE (check E8) and
     * every Warmup call, whose empty 200 is a documented null result.
     */
    private fun emptyIsSuccess(method: Method) =
        method.declaringClass == ZeroBounceWarmup::class.java || method.name == "deleteInboxPlacementTest"

    @Test
    fun theStrictCheckAloneAgreesWithTheCorpus() {
        val failures = corpus().filter { isStrictJson(it.body) != it.accept }
        assertTrue("${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun everyJsonCallRejectsEveryRejectBodyAndReadsEveryAcceptBody() {
        val failures = mutableListOf<String>()
        for (entry in corpus()) {
            sweep(entry, failures)
        }
        assertTrue("${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /** A failure line without the whole (possibly huge) body in it. */
    private fun shortened(failures: List<String>) =
        failures.map { if (it.length <= 600) it else it.take(300) + " ... " + it.takeLast(300) }

    /**
     * The strict check's depth limit: a 2xx body nested 200000 deep is "not valid
     * JSON", an API error with status 200 on every JSON call, and never a
     * StackOverflowError on OkHttp's dispatcher thread (no callback at all), which
     * is what a recursive reader without the limit does.
     */
    @Test
    fun aBodyNestedPastTheDepthLimitIsAnErrorWithStatus200OnEveryJsonCall() {
        val deep = "[".repeat(200_000) + "]".repeat(200_000)
        assertFalse("200000 nested arrays are past the limit", isStrictJson(deep))
        assertFalse("nested under an object", isStrictJson("{\"a\":$deep}"))
        assertTrue("500 nested arrays are within it", isStrictJson("[".repeat(500) + "]".repeat(500)))
        val failures = mutableListOf<String>()
        sweep(Entry(false, deep), failures)
        assertTrue("${failures.size} failures:\n" + shortened(failures).joinToString("\n"), failures.isEmpty())
    }

    /** The type a request method hands its response callback (its first callback). */
    private fun responseType(method: Method): Type {
        val callback = method.genericParameterTypes.first {
            it is ParameterizedType && it.rawType == Function1::class.java
        } as ParameterizedType
        val arg = callback.actualTypeArguments[0]
        return if (arg is WildcardType) (arg.lowerBounds.firstOrNull() ?: arg.upperBounds[0]) else arg
    }

    /**
     * Wrong shape (check E7): a 2xx body that is strict JSON but not the shape a
     * typed call returns is an API error with status 200 on every typed call, never
     * a default-filled result, a null result or an error without a status. Typed
     * calls are found by reflection: every request method of [ZeroBounceClient]
     * that is not a download and does not hand back raw JSON ([JsonElement]).
     * [ZeroBounceWarmup] hands back any JSON value, so no JSON value is the wrong
     * shape for it.
     */
    @Test
    fun aWrongShapeIsAnErrorWithStatus200OnEveryTypedCall() {
        val failures = mutableListOf<String>()
        var typed = 0
        for (body in listOf("0", "\"x\"", "true", "null", "[]", "[{}]")) {
            val interceptor = Interceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(body.toByteArray(Charsets.UTF_8).toResponseBody("application/json".toMediaType()))
                    .build()
            }
            val sweep = RequestSweep(context, dir, csv)
            val client = ZeroBounceClient()
            client.transportInterceptor = interceptor
            client.initialize("shape-key")
            for (method in RequestSweep.requestMethods(ZeroBounceClient::class.java)) {
                if (isDownload(method) || responseType(method) == JsonElement::class.java) continue
                typed++
                val outcome = sweep.invoke(client, method)
                if (outcome.thrown != null || outcome.timedOut || outcome.successes.isNotEmpty() ||
                    outcome.errors.size != 1 || outcome.errors[0]?.statusCode != 200 ||
                    body == "null" && outcome.errors[0]?.errors != listOf(WRONG_SHAPE)
                ) {
                    failures.add("$body $outcome")
                }
            }
        }
        assertTrue("typed calls swept: $typed", typed > 200)
        assertTrue("${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    private fun sweep(entry: Entry, failures: MutableList<String>) {
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath.lowercase()
            val download = path.contains("getfile") || path.contains("download")
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(
                    if (download) "email,status\na@example.com,valid\n".toResponseBody("text/csv".toMediaType())
                    else entry.body.toByteArray(Charsets.UTF_8).toResponseBody("application/json".toMediaType())
                ).build()
        }
        val sweep = RequestSweep(context, dir, csv)
        val client = ZeroBounceClient()
        client.transportInterceptor = interceptor
        client.initialize("corpus-key")
        val warmup = ZeroBounceWarmup()
        warmup.transportInterceptor = interceptor
        warmup.initialize("corpus-key")
        var swept = 0
        val methods = RequestSweep.requestMethods(ZeroBounceClient::class.java) +
            RequestSweep.requestMethods(ZeroBounceWarmup::class.java)
        for (method in methods) {
            val target: Any = if (method.declaringClass == ZeroBounceWarmup::class.java) warmup else client
            val outcome = sweep.invoke(target, method)
            swept++
            if (outcome.thrown != null || outcome.timedOut) {
                failures.add("$entry $outcome")
                continue
            }
            if (isDownload(method)) continue
            if (!entry.accept && entry.body.isEmpty() && emptyIsSuccess(method)) continue
            if (entry.accept) {
                for (error in outcome.errors) {
                    val text = error?.errors.toString()
                    if (error?.statusCode != 200 || text.contains("not valid JSON") || text.contains("Empty response")) {
                        failures.add("$entry $outcome")
                    }
                }
                if (outcome.errors.size + outcome.successes.size != 1) {
                    failures.add("$entry expected one outcome: $outcome")
                }
            } else if (outcome.successes.isNotEmpty() || outcome.errors.size != 1 ||
                outcome.errors[0]?.statusCode != 200
            ) {
                failures.add("$entry $outcome")
            }
        }
        assertEquals("every sweep entry was invoked", sweep.clientArgs.size + sweep.warmupArgs.size, swept)
    }
}
