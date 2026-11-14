package com.zerobounce.android

import android.content.Context
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
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
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.io.InputStream
import java.lang.reflect.Modifier
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Android audit 3, "code" area. */
class OfflineAudit3Test {

    private val context: Context = mock()
    private lateinit var dir: File
    private val bodies = CopyOnWriteArrayList<String>()
    private val urls = CopyOnWriteArrayList<String>()
    private var contentTypes = CopyOnWriteArrayList<String>()

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "zb-android-audit3-${System.nanoTime()}")
        dir.mkdirs()
        whenever(context.getExternalFilesDir(null)).thenReturn(dir)
        // Storage permissions denied, as on Android 13+: nothing may depend on them.
        whenever(context.checkCallingOrSelfPermission(org.mockito.kotlin.any())).thenReturn(-1)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private val recorder = Interceptor { chain ->
        val request = chain.request()
        urls.add(request.url.toString())
        val text = request.body?.let { body ->
            val buffer = Buffer()
            body.writeTo(buffer)
            contentTypes.add(body.contentType().toString())
            val raw = buffer.readUtf8()
            if (body is MultipartBody) raw.replace(body.boundary, "BOUNDARY") else raw
        } ?: ""
        bodies.add(text)
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body("""{"success":true,"file_id":"f1","file_name":"n.csv"}""".toResponseBody("application/json".toMediaType()))
            .build()
    }

    private fun client(): ZeroBounceClient {
        val client = ZeroBounceClient()
        client.transportInterceptor = recorder
        client.initialize("audit3-key")
        return client
    }

    private class Outcome {
        var ok: Any? = null
        var error: ErrorResponse? = null
        val done = CountDownLatch(1)
        fun await(): Outcome {
            assertTrue("a callback ran", done.await(10, TimeUnit.SECONDS))
            return this
        }
    }

    private fun send(start: ((ZBSendFileResponse?) -> Unit, (ErrorResponse?) -> Unit) -> Unit): Outcome {
        val o = Outcome()
        start({ o.ok = it; o.done.countDown() }, { o.error = it; o.done.countDown() })
        return o.await()
    }

    // ---- internal seams are hidden from Java ---------------------------------

    private val seamNames = setOf(
        "client", "transportInterceptor", "apiBaseUrl", "bulkApiBaseUrl", "bulkApiScoringBaseUrl",
        "bulkApiRootUrl", "listEvaluatorBaseUrl", "inboxPlacementBaseUrl", "downloadFileOpener",
        "nioPermissionsAvailable",
    )

    /** Documented public API whose names merely look like a seam. */
    private val publicSetters = setOf("setListEvaluatorBaseUrl", "setInboxPlacementBaseUrl")

    private fun compiledClasses(): List<Class<*>> {
        val root = File(ZeroBounceSDK::class.java.protectionDomain.codeSource.location.toURI())
        val prefix = "com/zerobounce/android/"
        val names = if (root.isFile) {
            java.util.zip.ZipFile(root).use { zip ->
                zip.entries().toList().map { it.name }.filter { it.startsWith(prefix) && it.endsWith(".class") }
            }
        } else {
            File(root, prefix).listFiles { f -> f.name.endsWith(".class") }!!.map { prefix + it.name }
        }
        return names.map { Class.forName(it.removeSuffix(".class").replace('/', '.')) }
    }

    @Test
    fun noInternalMemberIsPublicAndNotSynthetic() {
        val classes = compiledClasses()
        assertTrue("found ${classes.size} classes", classes.size > 20)
        val leaks = mutableListOf<String>()
        for (c in classes) {
            val members = c.declaredMethods.map { Triple(it.name, it.modifiers, it.isSynthetic) } +
                c.declaredFields.map { Triple(it.name, it.modifiers, it.isSynthetic) }
            for ((name, mods, synthetic) in members) {
                if (!Modifier.isPublic(mods) || synthetic) continue
                if (name in publicSetters) continue
                val bare = name.removePrefix("get").removePrefix("set").replaceFirstChar { it.lowercase() }
                if (name.contains("\$zero_bounce_sdk") || bare in seamNames || name in seamNames) {
                    leaks.add("${c.name}.$name")
                }
            }
        }
        // The top-level file facades hold nothing but internal helpers.
        for (facade in listOf("HttpMessagesKt")) {
            for (m in Class.forName("com.zerobounce.android.$facade").declaredMethods) {
                if (Modifier.isPublic(m.modifiers) && !m.isSynthetic) leaks.add("$facade.${m.name}")
            }
        }
        assertTrue("public, not synthetic: $leaks", leaks.isEmpty())
    }

    // ---- sendFile(File) no longer needs storage permissions ----------------------

    @Test
    fun sendFileFromDiskDoesNotNeedStoragePermissions() {
        val file = File(dir, "in.csv").apply { writeText("a@example.com\n") }
        val sdk = client()
        val o = send { ok, err ->
            sdk.sendFile(context, file, 1, responseCallback = ok, errorCallback = err)
        }
        assertNull(o.error?.errors?.toString(), o.error)
        assertEquals(1, urls.size)
        val scoring = send { ok, err ->
            sdk.scoringSendFile(context, file, 1, responseCallback = ok, errorCallback = err)
        }
        assertNull(scoring.error)
        assertEquals(2, urls.size)
    }

    @Test
    fun anUnreadableUploadFileReachesTheErrorCallbackWithoutARequest() {
        val sdk = client()
        val missing = File(dir, "nope.csv")
        val folder = File(dir, "folder").apply { mkdirs() }
        for (file in listOf(missing, folder)) {
            for (scoring in listOf(false, true)) {
                val o = send { ok, err ->
                    if (scoring) sdk.scoringSendFile(context, file, 1, responseCallback = ok, errorCallback = err)
                    else sdk.sendFile(context, file, 1, responseCallback = ok, errorCallback = err)
                }
                assertNotNull(o.error)
                assertEquals(0, o.error!!.statusCode)
                assertTrue(o.error!!.errors.joinToString(), o.error!!.errors.any { it.startsWith("File ") })
            }
        }
        assertTrue("a request was sent: $urls", urls.isEmpty())
    }

    // ---- base URLs are parsed -----------------------------------------------

    @Test
    fun aMalformedHttpsBaseUrlIsRefused() {
        val bad = listOf("https://exa mple.com/v2", "https://")
        for (url in bad) {
            assertFalse(url, ZeroBounceSDK.isAllowedApiBaseUrl(url))
            val sdk = ZeroBounceClient()
            try {
                sdk.initialize("k", url)
                throw AssertionError("initialize accepted $url")
            } catch (_: IllegalArgumentException) {
            }
            try {
                sdk.setListEvaluatorBaseUrl(url)
                throw AssertionError("setListEvaluatorBaseUrl accepted $url")
            } catch (_: IllegalArgumentException) {
            }
            try {
                sdk.setInboxPlacementBaseUrl(url)
                throw AssertionError("setInboxPlacementBaseUrl accepted $url")
            } catch (_: IllegalArgumentException) {
            }
            val warmup = ZeroBounceWarmup()
            warmup.initialize("warm-key", url)
            val o = Outcome()
            warmup.listSendingAccounts({ o.ok = it; o.done.countDown() }, { o.error = it; o.done.countDown() })
            o.await()
            assertNotNull(url, o.error)
        }
        assertTrue(ZeroBounceSDK.isAllowedApiBaseUrl("https://api-eu.zerobounce.net/v2/"))
    }

    // ---- upload stream failures ---------------------------------------------

    @Test
    fun aRuntimeExceptionFromTheUploadStreamIsReportedNotThrown() {
        val sdk = client()
        val broken = object : InputStream() {
            override fun read(): Int = throw IllegalStateException("stream closed")
        }
        for (scoring in listOf(false, true)) {
            val o = send { ok, err ->
                if (scoring) sdk.scoringSendFile(broken, "in.csv", 1, responseCallback = ok, errorCallback = err)
                else sdk.sendFile(broken, "in.csv", 1, responseCallback = ok, errorCallback = err)
            }
            assertNotNull(o.error)
            assertEquals(0, o.error!!.statusCode)
        }
        assertTrue(urls.isEmpty())
    }

    // ---- getFile -------------------------------------------------------------

    @Test
    fun getFileWithoutAnExternalFilesDirectoryFailsBeforeSending() {
        val noDir: Context = mock()
        whenever(noDir.getExternalFilesDir(null)).thenReturn(null)
        val sdk = client()
        val o = Outcome()
        sdk.getFile(noDir, "fid", { o.ok = it; o.done.countDown() }, { o.error = it; o.done.countDown() })
        o.await()
        assertEquals(0, o.error!!.statusCode)
        assertEquals(listOf("No external files directory available"), o.error!!.errors)
        assertTrue(urls.isEmpty())
    }

    // ---- exact request bodies --------------------------------------------------

    @Test
    fun aFormPostBodyIsExactlyTheseBytes() {
        val sdk = client()
        val o = Outcome()
        sdk.validate("a@example.com", "203.0.113.5", ZBValidateOptions(timeout = 15, usePost = true),
            { o.ok = it; o.done.countDown() }, { o.error = it; o.done.countDown() })
        o.await()
        assertEquals(1, bodies.size)
        assertEquals("application/x-www-form-urlencoded", contentTypes.single())
        assertEquals("api_key=audit3-key&email=a%40example.com&ip_address=203.0.113.5&timeout=15", bodies.single())
    }

    @Test
    fun aMultipartBodyIsExactlyTheseBytes() {
        val file = File(dir, "in.csv").apply { writeText("a@example.com\n") }
        val sdk = client()
        send { ok, err ->
            sdk.sendFile(context, file, 1, firstNameColumnIndex = 2, returnUrl = "https://example.com/hook",
                hasHeaderRow = true, removeDuplicate = false, responseCallback = ok, errorCallback = err)
        }
        val crlf = "\r\n"
        fun part(name: String, value: String) =
            "--BOUNDARY${crlf}Content-Disposition: form-data; name=\"$name\"$crlf$crlf$value$crlf"
        val expected = "--BOUNDARY${crlf}Content-Disposition: form-data; name=\"file\"; filename=\"in.csv\"${crlf}" +
            "Content-Type: application/octet-stream$crlf${crlf}a@example.com\n$crlf" +
            part("api_key", "audit3-key") + part("email_address_column", "1") +
            part("return_url", "https://example.com/hook") + part("first_name_column", "2") +
            part("has_header_row", "true") + part("remove_duplicate", "false") +
            "--BOUNDARY--$crlf"
        assertEquals(expected, bodies.single())
        assertEquals("https://bulkapi.zerobounce.net/v2/sendfile", urls.single())
    }
}
