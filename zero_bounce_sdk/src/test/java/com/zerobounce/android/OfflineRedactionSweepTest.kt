package com.zerobounce.android

import android.content.Context
import com.google.gson.Gson
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * The API key, and the SMTP password Warmup sends, never reach an error callback,
 * even when the server quotes the request back in its error body: the request URL
 * (with `api_key=`), the headers, the form, JSON or multipart body, or the key itself.
 * Every public request method of [ZeroBounceClient] and [ZeroBounceWarmup] is swept;
 * the methods come from [RequestSweep], whose completeness
 * [theSweepCoversEveryPublicRequestMethod] checks.
 */
class OfflineRedactionSweepTest {

    private val context: Context = mock()
    private lateinit var dir: File
    private lateinit var csv: File

    /** A key with characters that URL encoding changes, so the encoded form differs. */
    private val key = "echo+key/1234567890"

    /** The SMTP password the Warmup sweep sends to testSmtpConfiguration. */
    private val password = "smtp-pass"

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "zb-android-redaction-${System.nanoTime()}")
        dir.mkdirs()
        whenever(context.getExternalFilesDir(null)).thenReturn(dir)
        csv = File(dir, "in.csv").apply { writeText("email\na@example.com\n") }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    /** What the server quotes back: the request line, the key headers and the body. */
    private fun echo(request: Request): String {
        val body = request.body?.let { requestBody ->
            val buffer = okio.Buffer()
            requestBody.writeTo(buffer)
            buffer.readUtf8()
        }.orEmpty()
        val quoted = "${request.method} ${request.url} Authorization=${request.header("Authorization")} " +
            "API-Key=${request.header("API-Key")} $body"
        // Under the SDK's 1000-character cut, so the quote is not replaced by a generic message.
        return quoted.take(900)
    }

    private fun json(text: String): String = Gson().toJson(text)

    private fun leaks(text: String, secret: String): Boolean {
        if (text.contains(secret) || text.contains(URLEncoder.encode(secret, "UTF-8"))) return true
        return try {
            URLDecoder.decode(text, "UTF-8").contains(secret)
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun answering(status: Int, contentType: String, body: (Request) -> String) = Interceptor { chain ->
        val request = chain.request()
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("Status")
            .body(body(request).toResponseBody(contentType.toMediaType())).build()
    }

    private fun sweep(label: String, interceptor: Interceptor, everyCallFails: Boolean) {
        val sweep = RequestSweep(context, dir, csv)
        val client = ZeroBounceClient()
        client.transportInterceptor = interceptor
        client.initialize(key)
        val warmup = ZeroBounceWarmup()
        warmup.transportInterceptor = interceptor
        warmup.initialize(key)
        val failures = mutableListOf<String>()
        var swept = 0
        val methods = RequestSweep.requestMethods(ZeroBounceClient::class.java) +
            RequestSweep.requestMethods(ZeroBounceWarmup::class.java)
        for (method in methods) {
            val target: Any = if (method.declaringClass == ZeroBounceWarmup::class.java) warmup else client
            val outcome = sweep.invoke(target, method)
            if (outcome.thrown != null || outcome.timedOut) {
                failures.add("$label $outcome")
                continue
            }
            if (everyCallFails && (outcome.successes.isNotEmpty() || outcome.errors.size != 1)) {
                failures.add("$label expected one error: $outcome")
            }
            for (error in outcome.errors) {
                val text = "${error?.errors} $error"
                if (leaks(text, key) || leaks(text, password)) {
                    failures.add("$label leaked a secret: $outcome")
                }
            }
            swept++
        }
        assertTrue("${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
        assertEquals("every sweep entry was invoked", sweep.clientArgs.size + sweep.warmupArgs.size, swept)
    }

    @Test
    fun theSweepCoversEveryPublicRequestMethod() {
        val sweep = RequestSweep(context, dir, csv)
        for (type in listOf(ZeroBounceClient::class.java, ZeroBounceWarmup::class.java)) {
            assertEquals("${type.simpleName} public request methods missing from the sweep",
                sortedSetOf<String>(), sweep.missing(type))
            assertEquals("${type.simpleName} sweep entries that match no public request method",
                sortedSetOf<String>(), sweep.stale(type))
        }
    }

    /** A 400 whose plain-text body quotes the whole request. */
    @Test
    fun aPlainTextErrorQuotingTheRequestNeverCarriesTheKey() {
        sweep("400-text", answering(400, "text/plain") { "Bad request: ${echo(it)}" }, everyCallFails = true)
    }

    /** A 400 JSON error with several fields, each of which quotes the key or the request. */
    @Test
    fun aJsonErrorQuotingTheRequestNeverCarriesTheKey() {
        sweep("400-json", answering(400, "application/json") {
            "{\"success\": false, \"message\": ${json("Invalid API key $key")}, \"detail\": ${json(echo(it))}}"
        }, everyCallFails = true)
    }

    /**
     * An HTTP 200 that carries an API error naming the key: a dead-key answer on
     * the JSON calls, and a JSON error body instead of a file on the downloads
     * (sent as text/plain, which the API has done). Warmup reads a 200 as its
     * result, so only the errors that do come back are checked.
     */
    @Test
    fun anErrorInASuccessBodyNeverCarriesTheKey() {
        sweep("200-error", answering(200, "text/plain") {
            "{\"success\": false, \"error\": ${json("Invalid API key $key for ${it.url}")}}"
        }, everyCallFails = false)
    }

    /**
     * Sweeps every public Warmup request method against a server that answers
     * HTTP 200 with a body Warmup must refuse, and checks that each call reports
     * one error with status 200 that carries neither the key nor the SMTP
     * password in any form.
     */
    private fun warmupSweep(label: String, interceptor: Interceptor) {
        val sweep = RequestSweep(context, dir, csv)
        val warmup = ZeroBounceWarmup()
        warmup.transportInterceptor = interceptor
        warmup.initialize(key)
        val failures = mutableListOf<String>()
        val methods = RequestSweep.requestMethods(ZeroBounceWarmup::class.java)
        for (method in methods) {
            val outcome = sweep.invoke(warmup, method)
            if (outcome.thrown != null || outcome.timedOut || outcome.successes.isNotEmpty() ||
                outcome.errors.size != 1 || outcome.errors[0]?.statusCode != 200
            ) {
                failures.add("$label expected one error with status 200: $outcome")
            }
            for (error in outcome.errors) {
                val text = "${error?.errors} $error"
                if (leaks(text, key) || leaks(text, password)) {
                    failures.add("$label leaked a secret: $outcome")
                }
            }
        }
        assertTrue("${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
        assertEquals("every Warmup sweep entry was invoked", sweep.warmupArgs.size, methods.size)
    }

    /**
     * Warmup builds an error from a 200 body in two places: a JSON call answered
     * with text that is not JSON, and a CSV download answered with JSON. Both
     * bodies here quote the key and the request (URL, API-Key header, body with
     * the SMTP password).
     */
    @Test
    fun aWarmupErrorBuiltFromA200BodyNeverCarriesTheKey() {
        warmupSweep("warmup-200", Interceptor { chain ->
            val request = chain.request()
            val download = request.url.encodedPath.contains("/download")
            val body = if (download) {
                "{\"error\": ${json("Invalid API key $key for ${echo(request)}")}}"
            } else {
                "Invalid API key $key for ${echo(request)}"
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody((if (download) "application/json" else "text/plain").toMediaType()))
                .build()
        })
    }

    /** An HTML page on a 200 whose title quotes the key, from every Warmup call. */
    @Test
    fun aWarmupHtmlPageOnA200NeverCarriesTheKey() {
        warmupSweep("warmup-html", answering(200, "text/html") {
            "<!DOCTYPE html><html><head><title>Denied $key ${URLEncoder.encode(key, "UTF-8")}</title></head>" +
                "<body>${echo(it)}</body></html>"
        })
    }

    /**
     * The account-level batch error ("all") is read from a decoded 200 body, after
     * the request helper has handed it on, and is redacted as well.
     */
    @Test
    fun theAccountLevelBatchErrorNeverCarriesTheKey() {
        val client = ZeroBounceClient()
        client.transportInterceptor = answering(200, "application/json") {
            "{\"email_batch\": [], \"errors\": [{\"email_address\": \"all\", \"error\": ${json("Invalid API key $key")}}]}"
        }
        client.initialize(key)
        val sweep = RequestSweep(context, dir, csv)
        val method = RequestSweep.requestMethods(ZeroBounceClient::class.java)
            .first { RequestSweep.signature(it) == "validateBatch(List)" }
        val outcome = sweep.invoke(client, method)
        assertEquals(outcome.toString(), 1, outcome.errors.size)
        assertTrue(outcome.toString(), outcome.errors.none { leaks("${it?.errors}", key) })
        assertTrue(outcome.toString(), outcome.errors[0]?.errors.toString().contains("***"))
    }
}
