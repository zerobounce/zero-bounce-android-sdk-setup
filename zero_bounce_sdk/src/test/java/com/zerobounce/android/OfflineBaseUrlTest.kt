package com.zerobounce.android

import android.content.Context
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The base-URL entry points: every public method of [ZeroBounceClient] and
 * [ZeroBounceWarmup] that takes a base URL ([ZeroBounceClient.initialize] with a
 * base URL, each `set...BaseUrl`), found by reflection, so a new one cannot
 * escape. Each strips trailing slashes, and the per-API setters refuse a plain
 * http URL without changing anything.
 */
class OfflineBaseUrlTest {

    private class Recorder : Interceptor {
        val urls = CopyOnWriteArrayList<String>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            urls.add(request.url.toString())
            val path = request.url.encodedPath.lowercase()
            val csv = path.contains("getfile") || path.contains("download")
            val type = if (csv) "text/csv" else "application/json"
            val body = if (csv) "email\na@example.com\n" else """{"success":true,"file_id":"fid"}"""
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("Content-Type", type)
                .body(body.toResponseBody(type.toMediaType()))
                .build()
        }
    }

    private val key = "base-url-key"
    private val context: Context = mock()
    private lateinit var dir: File
    private lateinit var csv: File

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "zb-android-base-url-${System.nanoTime()}")
        dir.mkdirs()
        whenever(context.getExternalFilesDir(null)).thenReturn(dir)
        csv = File(dir, "in.csv").apply { writeText("email\na@example.com\n") }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    /**
     * Public instance methods of [type] that take a base URL: `initialize` with a
     * URL after the key, and every method whose name ends in `BaseUrl`.
     */
    private fun baseUrlEntryPoints(type: Class<*>): List<Method> = type.declaredMethods
        .filter { Modifier.isPublic(it.modifiers) && !Modifier.isStatic(it.modifiers) }
        .filter { !it.isSynthetic && !it.isBridge && !it.name.contains('$') }
        .filter { method -> method.parameterTypes.none { RequestSweep.isCallback(it) } }
        .filter { method ->
            val strings = method.parameterTypes.count { it == String::class.java }
            (method.name == "initialize" && strings >= 2) || (method.name.endsWith("BaseUrl") && strings >= 1)
        }
        .sortedBy { RequestSweep.signature(it) }

    /** The URL goes in the last String parameter, the key in any String before it. */
    private fun argsFor(method: Method, url: String): Array<Any?> {
        val last = method.parameterTypes.indexOfLast { it == String::class.java }
        return method.parameterTypes.indices.map { index ->
            when {
                index == last -> url
                method.parameterTypes[index] == String::class.java -> key
                else -> throw IllegalStateException("${RequestSweep.signature(method)}: no argument for $index")
            }
        }.toTypedArray()
    }

    private fun recordingClients(recorder: Recorder): Pair<ZeroBounceClient, ZeroBounceWarmup> {
        val client = ZeroBounceClient()
        client.transportInterceptor = recorder
        client.initialize(key)
        val warmup = ZeroBounceWarmup()
        warmup.transportInterceptor = recorder
        warmup.initialize(key)
        return client to warmup
    }

    @Test
    fun theEntryPointsAreFound() {
        val client = baseUrlEntryPoints(ZeroBounceClient::class.java).map { RequestSweep.signature(it) }.toSortedSet()
        val shared = baseUrlEntryPoints(ZeroBounceSDK::class.java).map { RequestSweep.signature(it) }.toSortedSet()
        val warmup = baseUrlEntryPoints(ZeroBounceWarmup::class.java).map { RequestSweep.signature(it) }.toSortedSet()
        assertTrue("found $client",
            client.containsAll(listOf("initialize(String,String)", "setListEvaluatorBaseUrl(String)",
                "setInboxPlacementBaseUrl(String)")))
        // ZeroBounceClient is swept; ZeroBounceSDK runs the same code, so each must match.
        assertEquals("ZeroBounceSDK and ZeroBounceClient base-URL entry points differ", shared, client)
        assertTrue("found $warmup", warmup.contains("initialize(String,String)"))
    }

    /**
     * Each entry point given `https://host/base///` sends no request with an
     * empty path segment (such as `/v2//validate`) on any path of either client,
     * and some request goes to that host, so the entry point is in use.
     */
    @Test
    fun everyBaseUrlEntryPointStripsTrailingSlashes() {
        val failures = mutableListOf<String>()
        val entryPoints = baseUrlEntryPoints(ZeroBounceClient::class.java) +
            baseUrlEntryPoints(ZeroBounceWarmup::class.java)
        for (entry in entryPoints) {
            val name = "${entry.declaringClass.simpleName}.${RequestSweep.signature(entry)}"
            val host = "${entry.declaringClass.simpleName}-${entry.name}".lowercase() + ".example.test"
            val recorder = Recorder()
            val (client, warmup) = recordingClients(recorder)
            entry.invoke(if (entry.declaringClass == ZeroBounceWarmup::class.java) warmup else client,
                *argsFor(entry, "https://$host/base///"))
            val sweep = RequestSweep(context, dir, csv)
            val methods = RequestSweep.requestMethods(ZeroBounceClient::class.java) +
                RequestSweep.requestMethods(ZeroBounceWarmup::class.java)
            var reached = 0
            for (method in methods) {
                recorder.urls.clear()
                val outcome = sweep.invoke(if (method.declaringClass == ZeroBounceWarmup::class.java) warmup else client,
                    method, 5)
                if (outcome.thrown != null || outcome.timedOut) failures.add("$name then $outcome")
                for (url in recorder.urls) {
                    val parsed = url.toHttpUrl()
                    if (parsed.encodedPath.contains("//")) failures.add("$name then ${outcome.signature} sent $url")
                    if (parsed.host == host) reached++
                }
            }
            if (reached == 0) failures.add("$name is used by no request")
        }
        assertTrue("${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * Both per-API setters refuse a plain http URL and keep the one they had: the
     * next request still goes to the https URL set before.
     */
    @Test
    fun thePerApiBaseUrlSettersRefuseHttpAndChangeNothing() {
        val recorder = Recorder()
        val (client, _) = recordingClients(recorder)
        client.setInboxPlacementBaseUrl("https://ipt.example.test/api/ipt")
        client.setListEvaluatorBaseUrl("https://le.example.test/v2/listevaluator")
        for ((label, set) in listOf<Pair<String, () -> Unit>>(
            "setInboxPlacementBaseUrl" to { client.setInboxPlacementBaseUrl("http://evil.example/api/ipt") },
            "setListEvaluatorBaseUrl" to { client.setListEvaluatorBaseUrl("http://evil.example/v2/listevaluator") },
        )) {
            try {
                set()
                fail("$label accepted an http URL")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
        for (call in listOf<(() -> Unit) -> Unit>(
            { done -> client.getInboxPlacementProviders({ done() }, { done() }) },
            { done -> client.getListEvaluatorResult("fid", { done() }, { done() }) },
        )) {
            val latch = CountDownLatch(1)
            call { latch.countDown() }
            assertTrue(latch.await(5, TimeUnit.SECONDS))
        }
        assertEquals(2, recorder.urls.size)
        assertEquals("https://ipt.example.test/api/ipt/providers", recorder.urls[0])
        assertEquals("le.example.test", recorder.urls[1].toHttpUrl().host)
    }
}
