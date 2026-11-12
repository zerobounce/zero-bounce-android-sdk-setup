package com.zerobounce.android

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
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
import okio.buffer
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

    /**
     * A key with characters that URL encoding and JSON escaping change (`+`, `/`,
     * `"`, `\`, `%` and a space) and that Gson's HTML-safe escaping changes
     * (`= < > & '`), so each of its spellings (see [forms]) differs from every other.
     */
    private val key = "echo+key/12\"34%56 7=<>&'\\890"

    /** The SMTP password the Warmup sweep sends to testSmtpConfiguration. */
    private val password = RequestSweep.SMTP_PASSWORD

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

    /** [json] as PHP's json_encode writes it: every `/` as a backslash and a slash. */
    private fun phpJson(text: String): String = json(text).replace("/", "\\/")

    /** The secret JSON-escaped, as Gson and as PHP write it, without the quotes. */
    private fun jsonForms(secret: String): List<String> =
        listOf(json(secret), phpJson(secret)).map { it.substring(1, it.length - 1) }

    /** [text] JSON-escaped by [gson], without the quotes. */
    private fun escaped(gson: Gson, text: String): String =
        gson.toJson(text).let { it.substring(1, it.length - 1) }

    /**
     * The JSON escapes a server can apply: Gson's HTML-safe escaping (`\u003c`),
     * its plain escaping, and each with `/` as a backslash and a slash (PHP's
     * json_encode).
     */
    private val jsonEscapes: List<(String) -> String> = listOf(
        { text -> escaped(Gson(), text) },
        { text -> escaped(Gson(), text).replace("/", "\\/") },
        { text -> escaped(GsonBuilder().disableHtmlEscaping().create(), text) },
        { text -> escaped(GsonBuilder().disableHtmlEscaping().create(), text).replace("/", "\\/") },
    )

    /** The URL encodings: none, a space as `+` (a form) and as `%20`. */
    private val urlEncodings: List<(String) -> String> = listOf(
        { text -> text },
        { text -> URLEncoder.encode(text, "UTF-8") },
        { text -> URLEncoder.encode(text, "UTF-8").replace("+", "%20") },
    )

    /**
     * Every spelling a server can quote a secret back in, each of which the SDK
     * must redact, composed from the encoders up to two deep: {raw, each JSON
     * escape} x {each URL encoding}, and each JSON escape applied to each
     * JSON-escaped form (a JSON error quoted inside another JSON string).
     * Duplicates are dropped; [everySpellingOfTheTestSecretsIsDistinct] pins how
     * many remain.
     */
    private fun forms(secret: String): List<String> {
        val once = listOf(secret) + jsonEscapes.map { it(secret) }
        val forms = linkedSetOf<String>()
        for (base in once) urlEncodings.forEach { forms.add(it(base)) }
        for (base in once.drop(1)) jsonEscapes.forEach { forms.add(it(base)) }
        return forms.toList()
    }

    @Test
    fun everySpellingOfTheTestSecretsIsDistinct() {
        for (secret in listOf(key, password)) {
            // 5 bases x 3 URL encodings, and 4 x 4 double escapes less the 4
            // where the plain escape of an HTML-safe one repeats its HTML-safe
            // escape (nothing HTML-sensitive is left to tell them apart).
            assertEquals(forms(secret).toString(), 15 + 12, forms(secret).size)
        }
    }

    /** Each spelling alone is replaced, with nothing of it left around the ***. */
    @Test
    fun eachSpellingOfASecretIsRedactedWhole() {
        for (secret in listOf(key, password)) {
            for (form in forms(secret)) {
                assertEquals(form, "[***]", redactSecrets("[$form]", listOf(secret)))
            }
        }
    }

    /**
     * A 400 whose plain-text body quotes the key in every spelling of [forms],
     * and on testSmtpConfiguration the SMTP password in every spelling too: each
     * is redacted on every path, so dropping any one spelling from the SDK fails
     * this test. The quote reaches the message, redacted, on every path.
     */
    @Test
    fun everySpellingOfASecretInAnErrorBodyIsRedacted() {
        // A few spellings at a time, so each body stays under the SDK's
        // 1000-character cut and is quoted, not replaced by a generic message.
        val chunks = forms(key).indices.chunked(6)
        for (chunk in chunks) {
            for (secret in listOf(key, password)) {
                val body = "Bad request: " + chunk.map { forms(secret)[it] }.joinToString(" | ")
                assertTrue(body, body.length < 1000)
            }
            val quote: (Request) -> String = { request ->
                val secret = if (request.url.encodedPath.contains("test-smtp")) password else key
                "Bad request: " + chunk.map { forms(secret)[it] }.joinToString(" | ")
            }
            sweep("400-forms-${chunk.first()}", answering(400, "text/plain", quote), everyCallFails = true) { outcome ->
                outcome.errors.firstOrNull()?.errors.toString().contains("Bad request: *** | ***")
            }
        }
    }

    private fun leaks(text: String, secret: String): Boolean {
        if (forms(secret).any { text.contains(it) }) return true
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

    private fun sweep(
        label: String,
        interceptor: Interceptor,
        everyCallFails: Boolean,
        quoted: (RequestSweep.Outcome) -> Boolean = { true },
    ) {
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
            if (!quoted(outcome)) {
                failures.add("$label did not quote the body: $outcome")
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
     * A 400 JSON error written by a server that escapes `/` as a backslash and a
     * slash (PHP's json_encode), quoting the key and the request.
     */
    @Test
    fun aJsonErrorWithEscapedSlashesNeverCarriesTheKey() {
        sweep("400-php-json", answering(400, "application/json") {
            "{\"error\": ${phpJson("Invalid API key $key for ${echo(it)}")}}"
        }, everyCallFails = true)
    }

    /**
     * A transport error whose message quotes the key and the SMTP password as sent,
     * URL-encoded and JSON-escaped (Gson's and PHP's spelling) never carries them,
     * on any path (S4, S7).
     */
    @Test
    fun aTransportErrorQuotingEverySpellingNeverCarriesASecret() {
        sweep("transport", Interceptor { chain ->
            throw java.io.IOException("failed to connect to ${chain.request().url}: ${spellings(chain.request())}")
        }, everyCallFails = true)
    }

    /**
     * Every spelling of the key, and for testSmtpConfiguration of the SMTP
     * password, which only Warmup holds: every one of [forms].
     */
    private fun spellings(request: Request): String {
        val secrets = if (request.url.encodedPath.contains("test-smtp-configuration")) listOf(key, password) else listOf(key)
        return secrets.joinToString(" ") { forms(it).joinToString(" ") }
    }

    /**
     * A body that fails while it is being read, with an error quoting every
     * spelling of the key and the SMTP password, is a transport error on every
     * path, Warmup included, and carries neither secret (S7).
     */
    @Test
    fun aBodyReadErrorNeverCarriesASecret() {
        sweep("read-error", Interceptor { chain ->
            val quoted = spellings(chain.request())
            val failing = object : okhttp3.ResponseBody() {
                override fun contentType() = "application/json".toMediaType()
                override fun contentLength() = -1L
                override fun source(): okio.BufferedSource = object : okio.Source {
                    override fun read(sink: okio.Buffer, byteCount: Long): Long =
                        throw java.io.IOException("connection reset reading $quoted")
                    override fun timeout() = okio.Timeout.NONE
                    override fun close() {}
                }.buffer()
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Content-Type", "application/json").body(failing).build()
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
