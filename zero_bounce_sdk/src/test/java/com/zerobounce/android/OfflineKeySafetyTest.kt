package com.zerobounce.android

import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * What a key can be made of (S2, S3, S7), case-insensitive and `\'` redaction (S4),
 * and that the SDK writes nothing to the log (lesson 19: nothing for a logger scan to find).
 * Every key here is an obvious dummy.
 */
class OfflineKeySafetyTest {

    private class Recorder : Interceptor {
        val urls = CopyOnWriteArrayList<String>()
        override fun intercept(chain: Interceptor.Chain): Response {
            urls.add(chain.request().url.toString())
            return Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body("{}".toResponseBody(null)).build()
        }
    }

    /** Zero-width and other format characters, at the start, middle and end, and Unicode whitespace inside. */
    private val unsafeKeys: List<String> = buildList {
        for (c in listOf('​', '﻿', '᠎', '⁠', '‍', ' ', ' ', ' ')) {
            add("${c}dummy-key")
            add("dummy${c}key")
            add("dummy-key$c")
        }
    }

    @Test
    fun theMainClientRefusesInvisibleAndInnerWhitespaceKeys() {
        for (key in unsafeKeys) {
            val recorder = Recorder()
            val sdk = ZeroBounceClient()
            sdk.transportInterceptor = recorder
            sdk.initialize(key)
            val error = AtomicReference<ErrorResponse?>()
            sdk.getCredits({ fail("sent a request with key ${key.map { it.code }}") }) { error.set(it) }
            assertNotNull(key.map { it.code }.toString(), error.get())
            assertEquals(listOf(API_KEY_CHARACTERS_PROBLEM), error.get()!!.errors)
            assertTrue(recorder.urls.isEmpty())
        }
    }

    @Test
    fun warmupRefusesInvisibleNonAsciiAndInnerWhitespaceKeysWithoutQuotingThem() {
        for (key in unsafeKeys + listOf("dummyékey", "dümmy-key", "dummy-keyé", "中dummy")) {
            val recorder = Recorder()
            val warmup = ZeroBounceWarmup()
            warmup.transportInterceptor = recorder
            warmup.initialize(key)
            val error = AtomicReference<ErrorResponse?>()
            warmup.getSeeds({ fail("sent a request with key ${key.map { it.code }}") }) { error.set(it) }
            assertNotNull(error.get())
            val text = error.get()!!.errors.joinToString()
            assertTrue(text, text.startsWith("api_key has "))
            assertFalse(text.contains(key))
            assertTrue(recorder.urls.isEmpty())
        }
    }

    @Test
    fun aRefusedWarmupInitializeKeepsNeitherTheOldKeyNorTheOldClient() {
        val recorder = Recorder()
        val warmup = ZeroBounceWarmup()
        warmup.transportInterceptor = recorder
        warmup.initialize("dummy-key")
        warmup.initialize("dummy​key")
        val error = AtomicReference<ErrorResponse?>()
        warmup.getSeeds({ fail("went on with the old key") }) { error.set(it) }
        assertNotNull(error.get())
        assertTrue(recorder.urls.isEmpty())
        // A clean key afterwards works again.
        warmup.initialize("dummy-key")
        val ok = AtomicReference<Any?>()
        warmup.getSeeds({ ok.set(true) }) { fail(it?.errors.toString()) }
        Thread.sleep(200)
        assertEquals(1, recorder.urls.size)
    }

    @Test
    fun aKeyOkHttpCannotWriteAsAHeaderNeverEscapesAsAnExceptionOrAQuote() {
        // The main key goes in a Bearer header for the Inbox Placement Tester;
        // a non-ASCII one passes initialize (it is a query value elsewhere) but
        // OkHttp refuses it and its own message quotes the value.
        val key = "dummyékey"
        val recorder = Recorder()
        val sdk = ZeroBounceClient()
        sdk.transportInterceptor = recorder
        sdk.initialize(key)
        val error = AtomicReference<ErrorResponse?>()
        sdk.getInboxPlacementProviders({ fail("sent") }) { error.set(it) }
        assertNotNull(error.get())
        assertEquals(listOf(API_KEY_HEADER_PROBLEM), error.get()!!.errors)
        assertTrue(recorder.urls.isEmpty())
    }

    @Test
    fun redactionIgnoresCase() {
        val key = "dummy-Key-1"
        assertEquals("bad ***", redactSecrets("bad DUMMY-KEY-1", listOf(key)))
        assertEquals("bad ***", redactSecrets("bad dummy-key-1", listOf(key)))
        assertTrue(exposesSecret("echo DuMmY-kEy-1", listOf(key)))
        // Percent-escapes in either case.
        assertEquals("a=***", redactSecrets("a=pw%2fx%3Dy".replace("%2f", "%2F"), listOf("pw/x=y")))
        assertEquals("a=***", redactSecrets("a=pw%2fx%3dy", listOf("pw/x=y")))
    }

    @Test
    fun redactionFoldsAndDecodesBraceEscapes() {
        assertEquals(CREDENTIAL_OMITTED, redactSecrets("echo STRASSE", listOf("straße")))
        val secret = "ab'cd"
        assertTrue(exposesSecret("ab\\u{27}cd", listOf(secret)))
        assertTrue(exposesSecret("ab\\047cd", listOf(secret)))
        assertTrue(exposesSecret("ab\\U00000027cd", listOf(secret)))
        assertFalse(redactSecrets("ab\\u{27}cd", listOf(secret)).contains(secret))
    }

    @Test
    fun escapesPastTheLastCodePointAreLeftAsWritten() {
        // 8 hex digits can exceed Int.MAX_VALUE: these threw NumberFormatException on the OkHttp thread.
        val secret = "ab'cd"
        for (text in listOf("bad \\UFFFFFFFFx", "bad \\u{FFFFFFFF}", "bad \\U80000000", "bad \\u{110000}", "bad \\UFFFFFFFF")) {
            assertFalse(text, exposesSecret(text, listOf(secret)))
            assertEquals(text, text, redactSecrets(text, listOf(secret)))
        }
        assertTrue(exposesSecret("\\UFFFFFFFF ab\\u{27}cd", listOf(secret)))
        // An 8-digit \U past U+10FFFF is not read as a 4-digit escape either: no U+FFFF appears.
        assertFalse(exposesSecret("\\UFFFFFFFF", listOf("\uFFFF")))
    }

    @Test
    fun htmlAfterACommentPrologOrBomIsHtml() {
        assertTrue(looksLikeHtml("<!-- generated --><html><body>x</body></html>"))
        assertTrue(looksLikeHtml("<?xml version=\"1.0\"?>\n<!DOCTYPE html><html>"))
        assertTrue(looksLikeHtml("\uFEFF<html>"))
        assertTrue(looksLikeHtml("  <!-- a -->  <!-- b -->\n<body>"))
        assertTrue(looksLikeHtml("<!-- never closed"))
        assertFalse(looksLikeHtml("{\"error\":\"<html>\"}"))
        assertFalse(looksLikeHtml("<!-- a -->{\"a\":1}"))
        assertFalse(looksLikeHtml("email,status\n<html>@x.com,invalid"))
    }

    @Test
    fun htmlAfterPaddingIsHtmlAndJsonIsNot() {
        assertTrue(looksLikeHtml(" ".repeat(2500) + "<html><body>x</body></html>"))
        assertTrue(looksLikeHtml(" ".repeat(9000) + "<html>"))
        assertFalse(looksLikeHtml("""{"note":"<body>"}"""))
    }

    @Test
    fun savedDownloadNameKeepsTheFileId() {
        val sdk = ZeroBounceSDK
        assertEquals("file-1_results.csv", sdk.downloadSavedName("file-1", "attachment; filename=results.csv"))
        assertEquals("file-2.csv", sdk.downloadSavedName("file-2", null))
        assertEquals(
            "file-1_results.csv",
            sdk.downloadSavedName("file-1", "attachment; filename=results.csv"),
        )
        assertFalse(
            sdk.downloadSavedName("file-1", "attachment; filename=results.csv") ==
                sdk.downloadSavedName("file-2", "attachment; filename=results.csv"),
        )
    }

    @Test
    fun redactionDecodesAnEscapedApostrophe() {
        val secret = "dummy'pass"
        assertTrue(exposesSecret("""{"error":"bad dummy\'pass"}""", listOf(secret)))
        val redacted = redactSecrets("""{"error":"bad dummy\'pass"}""", listOf(secret))
        assertFalse(redacted, redacted.contains("pass"))
    }

    @Test
    fun theSdkHasNoLoggingToLeakFrom() {
        // Lesson 19 asks for a scan of dependency jars for loggers. On Android
        // OkHttp, Okio and Gson log only through the platform's own logger,
        // which this SDK never enables (no HttpLoggingInterceptor, no
        // java.util.logging level change), so the part that is ours is
        // asserted: the SDK itself writes nothing to android.util.Log.
        val main = File("src/main/java/com/zerobounce/android")
        val files = main.listFiles { f -> f.name.endsWith(".kt") }.orEmpty()
        assertTrue(files.isNotEmpty())
        for (file in files) {
            val text = file.readText()
            for (banned in listOf("android.util.Log", "HttpLoggingInterceptor", "java.util.logging", "logEnabled", "println(")) {
                assertFalse("${file.name} uses $banned", text.contains(banned))
            }
        }
        assertNull(ZeroBounceSDK::class.java.declaredFields.firstOrNull { it.name == "logEnabled" })
    }

    @Test
    fun sendFileErrorHasNoStrayBrace() {
        val text = File("src/main/java/com/zerobounce/android/ZeroBounceSDK.kt").readText()
        assertFalse(text.contains("error=\$e}"))
    }
}
