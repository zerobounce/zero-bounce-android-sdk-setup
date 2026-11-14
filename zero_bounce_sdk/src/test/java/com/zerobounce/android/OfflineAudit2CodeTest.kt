package com.zerobounce.android

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Android audit 2, "code" area: stream failures, non-ASCII upload names, https-only init, key scans, BOM downloads. */
class OfflineAudit2CodeTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "zb-android-audit2-${System.nanoTime()}")
        dir.mkdirs()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private class Wire : Interceptor {
        val bodies = CopyOnWriteArrayList<ByteArray>()
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val buffer = Buffer()
            request.body?.writeTo(buffer)
            bodies.add(buffer.readByteArray())
            return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"success":true,"message":"ok"}""".toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    private class Failing : InputStream() {
        override fun read(): Int = throw IOException("disk went away")
    }

    // -- initialize is https only -----------------------------------------------

    @Test
    fun initializeRefusesPlainHttpLoopbackToo() {
        for (url in listOf("http://localhost:8080", "http://127.0.0.1:8080/v2", "http://[::1]:8080")) {
            try {
                ZeroBounceSDK.initialize("some-key", url)
                fail("$url was accepted")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message, e.message!!.contains("https"))
            }
            try {
                ZeroBounceClient().initialize("some-key", url)
                fail("$url was accepted by a client")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }

    // -- a failing upload stream is an error callback, not an exception -----------

    @Test
    fun aFailingUploadStreamIsReportedToTheErrorCallbackAndSendsNothing() {
        val wire = Wire()
        val client = ZeroBounceClient()
        client.transportInterceptor = wire
        client.initialize("stream-key")
        val uploads: List<((ZBSendFileResponse?) -> Unit, (ErrorResponse?) -> Unit) -> Unit> = listOf(
            { ok, err -> client.sendFile(Failing(), "in.csv", 1, responseCallback = ok, errorCallback = err) },
            { ok, err -> client.scoringSendFile(Failing(), "in.csv", 1, responseCallback = ok, errorCallback = err) },
            { ok, err -> client.sendEmailFinderFileStream(Failing(), "in.csv", 1, ZBEmailFinderSendFileOptions(firstNameColumn = 2), ok, err) },
            { ok, err -> client.sendDomainSearchFileStream(Failing(), "in.csv", 1, null, ok, err) },
        )
        for ((index, upload) in uploads.withIndex()) {
            val latch = CountDownLatch(1)
            var error: ErrorResponse? = null
            var success: ZBSendFileResponse? = null
            upload({ success = it; latch.countDown() }, { error = it; latch.countDown() })
            assertTrue("upload $index: a callback ran", latch.await(5, TimeUnit.SECONDS))
            assertNull("upload $index", success)
            assertNotNull("upload $index", error)
            assertEquals(0, error!!.statusCode)
            assertTrue(error!!.errors.toString(), error!!.errors.joinToString().contains("disk went away"))
        }
        assertTrue("a request was sent", wire.bodies.isEmpty())
    }

    // -- a non-ASCII upload name is on the wire as raw UTF-8 (X3 lesson 12) --------

    @Test
    fun aNonAsciiUploadNameGoesOutAsRawUtf8() {
        val name = "résumé 名.csv"
        val expected = "filename=\"$name\"".toByteArray(Charsets.UTF_8)
        val wire = Wire()
        val client = ZeroBounceClient()
        client.transportInterceptor = wire
        client.initialize("name-key")
        val latch = CountDownLatch(2)
        client.sendFile(ByteArrayInputStream("a@example.com\n".toByteArray()), name, 1,
            responseCallback = { latch.countDown() }, errorCallback = { fail(it.toString()); latch.countDown() })
        val file = File(dir, name).apply { writeText("a@example.com\n") }
        val context: android.content.Context = mock()
        client.sendFile(context, file, 1,
            responseCallback = { latch.countDown() }, errorCallback = { fail(it.toString()); latch.countDown() })
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(2, wire.bodies.size)
        for (body in wire.bodies) {
            assertTrue(String(body, Charsets.UTF_8).take(300), indexOf(body, expected) >= 0)
        }
    }

    // -- key scans ----------------------------------------------------------------

    @Test
    fun theKeyScanSeesSupplementaryCharactersAndNothingElse() {
        // U+1D173 is a format character outside the BMP (a surrogate pair); U+1F600 is a plain symbol.
        assertTrue(hasUnsafeKeyCharacters("ab" + String(Character.toChars(0x1D173)) + "cd"))
        assertFalse(hasUnsafeKeyCharacters("ab" + String(Character.toChars(0x1F600)) + "cd"))
        assertTrue(hasUnsafeKeyCharacters("a b"))
        assertTrue(hasUnsafeKeyCharacters("a b"))
        assertTrue(hasUnsafeKeyCharacters("a​b"))
        assertTrue(hasUnsafeKeyCharacters("a\nb"))
        assertFalse(hasUnsafeKeyCharacters("plain-KEY_123"))
        assertFalse(hasUnsafeKeyCharacters(""))
    }

    @Test
    fun theHeaderSafeCheckIsVisibleAsciiOnly() {
        assertTrue(isHeaderSafeKey("abc-XYZ_123~!"))
        assertFalse(isHeaderSafeKey("abc\u007F"))
        assertFalse(isHeaderSafeKey("abcé"))
        assertFalse(isHeaderSafeKey("ab c"))
        assertFalse(isHeaderSafeKey("ab\u0000c"))
    }

    @Test
    fun warmupInitializeRefusesAKeyOkHttpCannotWriteAsAHeader() {
        val fake = FakeWarmupApi()
        val warmup = ZeroBounceWarmup()
        warmup.transportInterceptor = fake
        warmup.initialize("dummyékey")
        val latch = CountDownLatch(1)
        var error: ErrorResponse? = null
        warmup.getSeeds({ latch.countDown() }, { error = it; latch.countDown() })
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(API_KEY_HEADER_PROBLEM), error!!.errors)
        assertEquals(0, fake.size())
    }

    /** The send-time catch, past initialize's own checks (reached by setting the key directly). */
    @Test
    fun warmupSendNeverLetsOkHttpQuoteAHeaderKey() {
        val fake = FakeWarmupApi()
        val warmup = ZeroBounceWarmup()
        warmup.transportInterceptor = fake
        warmup.initialize("dummy-key")
        warmup.apiKey = "dummyékey"
        val latch = CountDownLatch(1)
        var error: ErrorResponse? = null
        warmup.getSeeds({ latch.countDown() }, { error = it; latch.countDown() })
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(API_KEY_HEADER_PROBLEM), error!!.errors)
        assertEquals(0, fake.size())
    }

    // -- BOM-prefixed JSON in place of a CSV (Warmup) --------------------------------

    @Test
    fun warmupCsvDownloadsRefuseABomPrefixedJsonBody() {
        for (body in listOf("﻿{\"message\":\"No statistics for this address.\"}", "﻿[{\"error\":\"x\"}]")) {
            for (download in 0..1) {
                val fake = FakeWarmupApi()
                val warmup = ZeroBounceWarmup()
                warmup.transportInterceptor = fake
                warmup.initialize("dummy-key")
                fake.replyWith(200, "text/csv", body)
                val latch = CountDownLatch(1)
                var error: ErrorResponse? = null
                var text: String? = null
                val ok: (String) -> Unit = { text = it; latch.countDown() }
                val err: (ErrorResponse?) -> Unit = { error = it; latch.countDown() }
                if (download == 0) warmup.downloadSeeds(ok, err) else warmup.downloadStatistics("a@b.com", ok, err)
                assertTrue(latch.await(5, TimeUnit.SECONDS))
                assertNull("$body saved as CSV: $text", text)
                assertEquals(body, 200, error!!.statusCode)
                assertFalse(error!!.errors.joinToString().contains("﻿"))
            }
        }
    }

    // -- the priority-3 raw download callback survives a RuntimeException ----------------

    @Test
    fun aRuntimeFailureWhileSavingAPriority3DownloadIsAnErrorCallback() {
        val original = downloadFileOpener
        downloadFileOpener = { throw IllegalStateException("opener exploded") }
        try {
            LoopbackServer(LoopbackServer.answer(200, "text/csv", "email,status\na@b.c,valid\n".toByteArray())).use { server ->
                val client = ZeroBounceClient()
                client.transportInterceptor = server.reroute
                client.initialize("p3-key")
                val target = File(dir, "out.csv")
                val latch = CountDownLatch(1)
                var error: ErrorResponse? = null
                var path: String? = null
                client.emailFinderGetFile("fid", target.path, { path = it.localFilePath; latch.countDown() },
                    { error = it; latch.countDown() })
                assertTrue(latch.await(10, TimeUnit.SECONDS))
                assertNull(path)
                assertNotNull(error)
                assertTrue(error!!.errors.joinToString(), error!!.errors.joinToString().contains("opener exploded"))
                assertFalse(target.exists())
            }
        } finally {
            downloadFileOpener = original
        }
    }
}
