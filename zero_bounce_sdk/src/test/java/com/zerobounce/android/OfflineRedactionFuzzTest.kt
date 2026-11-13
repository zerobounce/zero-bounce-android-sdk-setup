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
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLEncoder
import kotlin.random.Random

/**
 * Redaction is closed under encoding (checks S4 and S7): a secret quoted back in any
 * composition of up to four encodings (the SDK's own request encoding plus three a
 * server can add) (URL with a space as `%20`, URL with a space
 * as `+`, JSON, JSON with `/` as a backslash and a slash) never appears in any
 * decoded view of what the caller gets. The compositions are random, from a fixed
 * seed so a failure repeats; the decoder here is the test's own, not the SDK's.
 */
class OfflineRedactionFuzzTest {

    private val context: Context = mock()
    private lateinit var dir: File
    private lateinit var csv: File

    /** A key with a space, `" / \ + % & = < > '`: every encoding changes it. */
    private val key = "fz+k/1\"2%3 4=<>&'\\5"
    private val password = RequestSweep.SMTP_PASSWORD

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "zb-android-fuzz-${System.nanoTime()}")
        dir.mkdirs()
        whenever(context.getExternalFilesDir(null)).thenReturn(dir)
        csv = File(dir, "in.csv").apply { writeText("email\na@example.com\n") }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun jsonEscape(text: String, htmlSafe: Boolean): String {
        val gson = if (htmlSafe) Gson() else GsonBuilder().disableHtmlEscaping().create()
        return gson.toJson(text).let { it.substring(1, it.length - 1) }
    }

    private val names = listOf("url%20", "url+", "json", "json\\/")

    private fun encode(which: Int, htmlSafe: Boolean, text: String): String = when (which) {
        0 -> URLEncoder.encode(text, "UTF-8").replace("+", "%20")
        1 -> URLEncoder.encode(text, "UTF-8")
        2 -> jsonEscape(text, htmlSafe)
        else -> jsonEscape(text, htmlSafe).replace("/", "\\/")
    }

    /** One random composition of up to four encodings, and its name. */
    private fun compose(random: Random, secret: String): Pair<String, String> {
        var value = secret
        val name = mutableListOf<String>()
        repeat(1 + random.nextInt(4)) {
            val which = random.nextInt(4)
            val htmlSafe = random.nextBoolean()
            value = encode(which, htmlSafe, value)
            name.add(names[which] + if (htmlSafe) "(html)" else "")
        }
        return value to name.joinToString(" then ")
    }

    // -- the test's own decoders ----------------------------------------------

    private fun percentDecode(text: String, plusIsSpace: Boolean): String {
        val raw = text.toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < raw.size) {
            val b = raw[i]
            if (b == '%'.code.toByte() && i + 2 < raw.size &&
                Character.digit(raw[i + 1].toInt().toChar(), 16) >= 0 &&
                Character.digit(raw[i + 2].toInt().toChar(), 16) >= 0
            ) {
                out.write(Character.digit(raw[i + 1].toInt().toChar(), 16) * 16 +
                    Character.digit(raw[i + 2].toInt().toChar(), 16))
                i += 3
                continue
            }
            out.write(if (b == '+'.code.toByte() && plusIsSpace) ' '.code else b.toInt())
            i++
        }
        return out.toString("UTF-8")
    }

    private fun jsonUnescape(text: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c != '\\' || i + 1 >= text.length) {
                out.append(c); i++; continue
            }
            val e = text[i + 1]
            val at = "\"\\/bfnrt".indexOf(e)
            when {
                at >= 0 -> { out.append("\"\\/\b\u000c\n\r\t"[at]); i += 2 }
                e == 'u' && i + 5 < text.length && text.substring(i + 2, i + 6).toIntOrNull(16) != null -> {
                    out.append(text.substring(i + 2, i + 6).toInt(16).toChar()); i += 6
                }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }

    /** The text and every view of it decoded up to four times, in any order. */
    private fun views(text: String): Set<String> {
        val all = linkedSetOf(text)
        var layer = listOf(text)
        repeat(4) {
            layer = layer.flatMap { listOf(percentDecode(it, false), percentDecode(it, true), jsonUnescape(it)) }
                .filter { all.add(it) }
        }
        return all
    }

    private fun exposes(text: String?, vararg secrets: String): Boolean =
        text != null && views(text).any { view -> secrets.any { view.contains(it) } }

    @Test
    fun theTestDecoderFindsEveryComposition() {
        val random = Random(20261112)
        repeat(500) {
            for (secret in listOf(key, password)) {
                val (encoded, name) = compose(random, secret)
                assertTrue(name, exposes("x $encoded y", secret))
            }
        }
    }

    @Test
    fun noCompositionOfEncodingsSurvivesRedaction() {
        val random = Random(20261112)
        val failures = mutableListOf<String>()
        repeat(3000) {
            for (secret in listOf(key, password)) {
                val (encoded, name) = compose(random, secret)
                val message = redactSecrets("Bad request: $encoded (quoted)", listOf(key, password))
                if (exposes(message, secret)) failures.add("$name: $message")
            }
        }
        assertTrue("${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /** Every `%XX` in [text] with lower-case hex digits. */
    private fun lowerHex(text: String): String =
        Regex("%[0-9A-F]{2}").replace(text) { it.value.lowercase() }

    /** Every UTF-16 unit of [text] as a JSON u-escape (backslash, u, four hex digits), lower-case hex. */
    private fun unicodeEscaped(text: String): String =
        text.map { "\\u" + String.format("%04x", it.code) }.joinToString("")

    /** A secret outside the BMP, so its JSON u-escaped spelling is a surrogate pair. */
    private val astral = "pw😀x/\"9"

    /** A secret whose only special character is a space, so its URL spelling has no `%`. */
    private val spaced = "sp ace7"

    /** Secrets with a 2-byte and a 3-byte UTF-8 character ([astral] has a 4-byte one). */
    private val twoByte = "pw\u00e9x7"
    private val threeByte = "pw\u20acx7"

    /**
     * Each decode step of the check on its own, so removing one fails here and not
     * only by chance in the random compositions: percent-decoding with lower-case hex
     * and after an invalid UTF-8 run, `+` as a space, JSON unescaping incl. u-escapes
     * and a surrogate pair, a view with no `%` that still needs `+` decoded, four
     * layers, and a NUL (raw or `%00`) before the secret, which must not end the check.
     */
    @Test
    fun everyDecodeStepIsChecked() {
        val url20 = { t: String -> encode(0, false, t) }
        val urlPlus = { t: String -> encode(1, false, t) }
        val json = { t: String -> encode(2, false, t) }
        val cases = listOf(
            "lower-case hex" to lowerHex(url20(key)),
            "invalid UTF-8 run first" to "%FF%C3%28 " + url20(urlPlus(key)),
            "invalid UTF-8 run, lower-case hex" to "%ff%c3 " + lowerHex(urlPlus(url20(key))),
            "+ under u-escapes" to unicodeEscaped(urlPlus(key)),
            "u-escapes" to unicodeEscaped(key),
            "surrogate pair" to unicodeEscaped(astral),
            "surrogate pair under URL" to url20(unicodeEscaped(astral)),
            "+ without %" to unicodeEscaped(urlPlus(spaced)),
            "four layers" to url20(json(urlPlus(json(url20(key))))),
            "four layers, u-escapes inside" to urlPlus(url20(unicodeEscaped(url20(key)))),
            "NUL first" to "a\u0000b " + lowerHex(url20(key)),
            "%00 first" to "%00 " + lowerHex(url20(url20(key))),
            "%00 first, JSON" to "%00\\u0000 " + unicodeEscaped(key),
            // Percent-decoding works on bytes, then reads them as UTF-8: one case per
            // UTF-8 sequence length, so decoding each %XX as its own character fails here.
            "2-byte UTF-8 under %" to lowerHex(url20(twoByte)),
            "3-byte UTF-8 under %" to lowerHex(url20(threeByte)),
            "4-byte UTF-8 under %" to lowerHex(url20(astral)),
            "4-byte UTF-8 under two % layers" to lowerHex(url20(url20(astral))),
        )
        val secrets = listOf(key, astral, spaced, twoByte, threeByte)
        val failures = mutableListOf<String>()
        for ((name, encoded) in cases) {
            val body = "Bad request: $encoded (quoted)"
            assertTrue("$name: the test's decoder finds the secret in $body", exposes(body, *secrets.toTypedArray()))
            val message = redactSecrets(body, secrets)
            if (exposes(message, *secrets.toTypedArray())) failures.add("$name: $message")
        }
        assertTrue("${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun aMessageWithoutASecretIsKept() {
        assertEquals("Bad request: %41 \\u0041 + plain", redactSecrets("Bad request: %41 \\u0041 + plain", listOf(key)))
    }

    /**
     * Every public request method, answered with an error that quotes the key (and,
     * on testSmtpConfiguration, the SMTP password) in a random composition of
     * encodings: as a 400 text body and as a JSON error on a 200. Nothing the error
     * callback gets exposes a secret in any decoded view.
     */
    @Test
    fun everyRequestPathDropsAnEncodedSecret() {
        val random = Random(1112)
        val failures = mutableListOf<String>()
        for (round in 0 until 6) {
            val (keyForm, keyName) = compose(random, key)
            val (passwordForm, passwordName) = compose(random, password)
            val jsonOn200 = round % 2 == 1
            val label = "fuzz-$round key $keyName, password $passwordName"
            val interceptor = Interceptor { chain ->
                val request: Request = chain.request()
                val quoted = if (request.url.encodedPath.contains("test-smtp")) "$passwordForm $keyForm" else keyForm
                val (status, type, body) = if (jsonOn200) {
                    Triple(200, "application/json", "{\"error\": \"" + jsonEscape("Invalid API key $quoted", false) + "\"}")
                } else {
                    Triple(400, "text/plain", "Bad request: $quoted")
                }
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("Status")
                    .body(body.toResponseBody(type.toMediaType())).build()
            }
            val sweep = RequestSweep(context, dir, csv)
            val client = ZeroBounceClient()
            client.transportInterceptor = interceptor
            client.initialize(key)
            val warmup = ZeroBounceWarmup()
            warmup.transportInterceptor = interceptor
            warmup.initialize(key)
            var swept = 0
            val methods = RequestSweep.requestMethods(ZeroBounceClient::class.java) +
                RequestSweep.requestMethods(ZeroBounceWarmup::class.java)
            for (method in methods) {
                val target: Any = if (method.declaringClass == ZeroBounceWarmup::class.java) warmup else client
                val outcome = sweep.invoke(target, method)
                swept++
                if (outcome.thrown != null || outcome.timedOut) {
                    failures.add("$label $outcome")
                    continue
                }
                for (error in outcome.errors) {
                    if (error?.errors.orEmpty().any { exposes(it, key, password) }) {
                        failures.add("$label exposed a secret: $outcome")
                    }
                    if (exposes(error.toString(), key, password)) {
                        failures.add("$label toString exposed a secret: $outcome")
                    }
                }
            }
            assertEquals("every sweep entry was invoked", sweep.clientArgs.size + sweep.warmupArgs.size, swept)
        }
        assertTrue("${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
