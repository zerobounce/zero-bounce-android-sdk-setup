package com.zerobounce.android

import android.content.Context
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Offline tests for what the SDK does with the caller's own callbacks, with
 * file downloads that turn out to be JSON errors, with the API key handed to
 * initialize, and with booleans in responses. Every request is answered by an
 * in-process interceptor; nothing here reaches the live API.
 */
class OfflineCallbackTest {

    /** Answers every call with the same response and records the URLs. */
    private class Canned(
        private val code: Int,
        private val contentType: String,
        private val body: String,
    ) : Interceptor {
        val urls = CopyOnWriteArrayList<String>()

        override fun intercept(chain: Interceptor.Chain): Response {
            urls.add(chain.request().url.toString())
            val builder = Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code in 200..299) "OK" else "Error")
            if (contentType.isNotEmpty()) builder.header("Content-Type", contentType)
            return builder.body(body.toResponseBody(contentType.toMediaTypeOrNull())).build()
        }
    }

    private val context: Context = mock()
    private lateinit var dir: File
    private lateinit var csv: File
    private val uncaught = CopyOnWriteArrayList<Throwable>()
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "zb-android-callback-${System.nanoTime()}")
        dir.mkdirs()
        whenever(context.getExternalFilesDir(null)).thenReturn(dir)
        csv = File(dir, "in.csv").apply { writeText("email\nperson@example.com\n") }
        // OkHttp rethrows an exception from onResponse on its dispatcher
        // thread, which is where the caller's own exception surfaces.
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaught.add(e) }
    }

    @After
    fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        dir.deleteRecursively()
    }

    private fun client(answer: Canned, key: String = "callback-key"): ZeroBounceClient {
        val sdk = ZeroBounceClient()
        sdk.transportInterceptor = answer
        sdk.initialize(key)
        return sdk
    }

    private fun warmup(answer: Canned): ZeroBounceWarmup {
        val instance = ZeroBounceWarmup()
        instance.transportInterceptor = answer
        instance.initialize("warmup-key")
        return instance
    }

    private fun interface Call {
        fun run(answer: Canned, ok: (Any?) -> Unit, err: (ErrorResponse?) -> Unit)
    }

    /** One public method per request path the SDK has, with a body it accepts. */
    private fun everyRequestPath(): Map<String, Pair<String, Call>> {
        val out = File(dir, "out.csv").path
        val json = """{"success":true,"message":"ok","file_id":"fid","address":"a@example.com","email":"a@example.com","status":"valid","domain":"example.com","format":"first"}"""
        val file = "email,status\nperson@example.com,valid\n"
        return linkedMapOf(
            "validate" to (json to Call { a, ok, err -> client(a).validate("a@example.com", null, ok, err) }),
            "validatePost" to (json to Call { a, ok, err ->
                client(a).validate("a@example.com", null, ZBValidateOptions(usePost = true), ok, err)
            }),
            "validateBatch" to ("""{"email_batch":[],"errors":[]}""" to Call { a, ok, err ->
                client(a).validateBatch(listOf(ZBValidateBatchData("a@example.com", null)), ok, err)
            }),
            "findEmail" to (json to Call { a, ok, err ->
                client(a).findEmail("Ada", domain = "example.com", responseCallback = ok, errorCallback = err)
            }),
            "scoreEmailPost" to (json to Call { a, ok, err -> client(a).scoreEmailPost("a@example.com", ok, err) }),
            "listFilters" to ("[]" to Call { a, ok, err -> client(a).listFilters(false, ok, err) }),
            "listS3Files" to ("[]" to Call { a, ok, err -> client(a).listS3Files(null, null, ok, err) }),
            "getInboxPlacementProviders" to ("{}" to Call { a, ok, err ->
                client(a).getInboxPlacementProviders(ok, err)
            }),
            "sendFile" to (json to Call { a, ok, err ->
                client(a).sendFile(context, csv, 1, responseCallback = ok, errorCallback = err)
            }),
            "sendListEvaluatorFile" to (json to Call { a, ok, err ->
                client(a).sendListEvaluatorFile(csv, 1, ok, err)
            }),
            "getFile" to (file to Call { a, ok, err -> client(a).getFile(context, "fid", ok, err) }),
            "emailFinderGetFile" to (file to Call { a, ok, err -> client(a).emailFinderGetFile("fid", out, ok, err) }),
            "warmupGetSeeds" to ("[]" to Call { a, ok, err -> warmup(a).getSeeds(ok, err) }),
            "warmupDownloadSeeds" to (file to Call { a, ok, err -> warmup(a).downloadSeeds(ok, err) }),
        )
    }

    private fun contentTypeFor(body: String) =
        if (body.startsWith("{") || body.startsWith("[")) "application/json" else "text/csv"

    /** Waits until [condition] holds, for up to five seconds. */
    private fun eventually(message: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition()) {
            if (System.nanoTime() > deadline) fail(message)
            Thread.sleep(10)
        }
    }

    @Test
    fun anExceptionFromTheSuccessCallbackPropagatesOnEveryRequestPath() {
        for ((name, entry) in everyRequestPath()) {
            val (body, call) = entry
            val thrown = IllegalStateException("from the caller's success callback: $name")
            val errors = AtomicInteger()
            val reached = CountDownLatch(1)
            call.run(
                Canned(200, contentTypeFor(body), body),
                {
                    reached.countDown()
                    throw thrown
                },
                {
                    errors.incrementAndGet()
                    reached.countDown()
                },
            )
            assertTrue(name, reached.await(5, TimeUnit.SECONDS))
            eventually("$name did not let the caller's exception through: ${uncaught.map { it.message }}") {
                uncaught.any { it === thrown }
            }
            Thread.sleep(50)
            assertEquals("$name reported the caller's exception through the error callback", 0, errors.get())
        }
    }

    @Test
    fun anExceptionFromTheErrorCallbackPropagatesAndIsNotReportedTwice() {
        for ((name, entry) in everyRequestPath()) {
            val call = entry.second
            val thrown = IllegalStateException("from the caller's error callback: $name")
            val errors = AtomicInteger()
            // A failure on every path; for validateBatch the account-level error
            // on an HTTP 200, which the SDK turns into an error after decoding.
            val answer = if (name == "validateBatch") {
                Canned(200, "application/json", """{"email_batch":[],"errors":[{"email_address":"all","error":"Invalid API Key"}]}""")
            } else {
                Canned(500, "application/json", """{"error":"Server error"}""")
            }
            call.run(
                answer,
                { fail("$name reached the success callback") },
                {
                    errors.incrementAndGet()
                    throw thrown
                },
            )
            eventually("$name did not let the caller's exception through: ${uncaught.map { it.message }}") {
                uncaught.any { it === thrown }
            }
            Thread.sleep(50)
            assertEquals("$name called the error callback again", 1, errors.get())
        }
    }

    @Test
    fun anUnreadableBodyStillReachesTheErrorCallback() {
        val error = AtomicReference<ErrorResponse?>()
        val latch = CountDownLatch(1)
        client(Canned(200, "application/json", """{"address":""")).validate(
            "a@example.com",
            null,
            { fail("success") },
            {
                error.set(it)
                latch.countDown()
            },
        )
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertNotNull(error.get())
        assertTrue(uncaught.isEmpty())
    }

    // -- getfile and JSON error bodies -----------------------------------------

    @Test
    fun getFileRejectsAJsonErrorBodyWhateverTheContentType() {
        val bodies = listOf(
            """{"error":"File not found"}""",
            """{"Error":"File not found"}""",
            """{"Message":"File not found"}""",
            """{"message":"File not found"}""",
            """{"error_message":"File not found"}""",
            """{"detail":"File not found"}""",
            """{"success":false}""",
            """{"success":"False","message":"File not ready"}""",
            "  {\"error\":\"File not found\"}\n",
        )
        val contentTypes = listOf("text/plain", "application/octet-stream", "", "text/csv; charset=utf-8")
        for (body in bodies) {
            for (contentType in contentTypes) {
                for (status in listOf(200, 400)) {
                    for (method in listOf("getFile", "scoringGetFile", "emailFinderGetFile", "domainSearchGetFile")) {
                        val label = "$method $status '$contentType' ${body.trim()}"
                        dir.listFiles()?.filter { it.name != "in.csv" }?.forEach { it.delete() }
                        val path = File(dir, "out.csv").path
                        val answer = Canned(status, contentType, body)
                        val sdk = client(answer)
                        val error = AtomicReference<ErrorResponse?>()
                        val value = AtomicReference<ZBGetFileResponse?>()
                        val latch = CountDownLatch(1)
                        val ok: (ZBGetFileResponse) -> Unit = { value.set(it); latch.countDown() }
                        val err: (ErrorResponse?) -> Unit = { error.set(it); latch.countDown() }
                        when (method) {
                            "getFile" -> sdk.getFile(context, "fid", ok, err)
                            "scoringGetFile" -> sdk.scoringGetFile(context, "fid", ok, err)
                            "emailFinderGetFile" -> sdk.emailFinderGetFile("fid", path, ok, err)
                            else -> sdk.domainSearchGetFile("fid", path, ok, err)
                        }
                        assertTrue(label, latch.await(5, TimeUnit.SECONDS))
                        assertNull("$label was saved as a file", value.get())
                        assertNotNull(label, error.get())
                        assertEquals(label, status, error.get()!!.statusCode)
                        assertFalse("$label has no message", error.get()!!.errors.isEmpty())
                        val written = dir.listFiles()?.filter { it.name != "in.csv" }.orEmpty()
                        assertTrue("$label wrote $written", written.isEmpty())
                    }
                }
            }
        }
    }

    @Test
    fun getFileStillSavesACsvWhateverTheContentType() {
        for (contentType in listOf("text/plain", "application/octet-stream", "", "text/csv")) {
            for (method in listOf("getFile", "emailFinderGetFile")) {
                val body = "email,status\n\"{a}\"@example.com,valid\n"
                val sdk = client(Canned(200, contentType, body))
                val value = AtomicReference<ZBGetFileResponse?>()
                val latch = CountDownLatch(1)
                val ok: (ZBGetFileResponse) -> Unit = { value.set(it); latch.countDown() }
                val err: (ErrorResponse?) -> Unit = { fail("$method '$contentType': ${it?.errors}") }
                if (method == "getFile") {
                    sdk.getFile(context, "fid", ok, err)
                } else {
                    sdk.emailFinderGetFile("fid", File(dir, "found.csv").path, ok, err)
                }
                assertTrue(latch.await(5, TimeUnit.SECONDS))
                assertEquals(body, File(value.get()!!.localFilePath).readText())
            }
        }
    }

    // -- the API key handed to initialize ---------------------------------------

    @Test
    fun initializeRefusesAKeyWithSurroundingWhitespaceOrControlCharacters() {
        for (key in listOf(" key", "key ", "key\n", "\tkey", "ke\u0000y", "ke\ny", "ke\u007fy")) {
            val answer = Canned(200, "application/json", """{"Credits":"5"}""")
            for (shared in listOf(false, true)) {
                val error = AtomicReference<ErrorResponse?>()
                val ok: (ZBCreditsResponse?) -> Unit = { fail("sent ${key.replace("\n", "\\n")}") }
                if (shared) {
                    ZeroBounceSDK.transportInterceptor = answer
                    try {
                        ZeroBounceSDK.initialize(key)
                        ZeroBounceSDK.getCredits(ok) { error.set(it) }
                    } finally {
                        ZeroBounceSDK.transportInterceptor = null
                        ZeroBounceSDK.initialize("reset-key")
                    }
                } else {
                    val sdk = client(answer, key)
                    sdk.getCredits(ok) { error.set(it) }
                    val ipt = AtomicReference<ErrorResponse?>()
                    sdk.getInboxPlacementProviders({ fail("sent") }) { ipt.set(it) }
                    assertNotNull(ipt.get())
                }
                // Refused before any request, so the error callback ran synchronously.
                assertNotNull(error.get())
                assertEquals(
                    listOf("api_key has surrounding whitespace or control characters"),
                    error.get()!!.errors,
                )
                assertEquals(0, error.get()!!.statusCode)
            }
            Thread.sleep(50)
            assertTrue("a request was sent with the refused key", answer.urls.isEmpty())
        }
    }

    @Test
    fun aCleanKeyAfterARefusedOneIsAccepted() {
        val answer = Canned(200, "application/json", """{"Credits":"5"}""")
        val sdk = ZeroBounceClient()
        sdk.transportInterceptor = answer
        sdk.initialize("bad key\n")
        sdk.initialize("good-key")
        val value = AtomicReference<ZBCreditsResponse?>()
        val latch = CountDownLatch(1)
        sdk.getCredits({ value.set(it); latch.countDown() }) { fail(it?.errors.toString()) }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals("5", value.get()?.credits)
        assertTrue(answer.urls.single().contains("api_key=good-key"))
    }

    // -- booleans in responses ----------------------------------------------------

    private fun <T> outcome(start: ((T?) -> Unit, (ErrorResponse?) -> Unit) -> Unit): Pair<T?, ErrorResponse?> {
        val value = AtomicReference<T?>()
        val error = AtomicReference<ErrorResponse?>()
        val latch = CountDownLatch(1)
        start({ value.set(it); latch.countDown() }, { error.set(it); latch.countDown() })
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        return value.get() to error.get()
    }

    @Test
    fun booleansAcceptTheDocumentedSpellings() {
        val cases = listOf(
            "true" to true, "false" to false,
            "\"true\"" to true, "\"false\"" to false,
            "\"True\"" to true, "\"False\"" to false,
            "1" to true, "0" to false,
            "\"1\"" to true, "\"0\"" to false,
        )
        for ((raw, expected) in cases) {
            val body = """{"address":"a@example.com","status":"valid","free_email":$raw,"mx_found":$raw,"catchall_domain":$raw}"""
            val (value, error) = outcome<ZBValidateResponse> { ok, err ->
                client(Canned(200, "application/json", body)).validate("a@example.com", null, ok, err)
            }
            assertNull("$body: ${error?.errors}", error)
            assertEquals(body, expected, value!!.freeEmail)
            assertEquals(body, expected, value.mxFound)
            assertEquals(body, expected, value.catchallDomain)
        }
        val (value, _) = outcome<ZBValidateResponse> { ok, err ->
            client(Canned(200, "application/json", """{"address":"a@example.com","catchall_domain":null}"""))
                .validate("a@example.com", null, ok, err)
        }
        assertNull(value!!.catchallDomain)
    }

    @Test
    fun anythingElseInABooleanIsAShortError() {
        for (raw in listOf("\"yes\"", "\"no\"", "\"\"", "2", "-1", "1.5", "\"t\"", "[]", "{}")) {
            val body = """{"address":"a@example.com","status":"valid","free_email":$raw,"padding":"${"x".repeat(2000)}"}"""
            val (value, error) = outcome<ZBValidateResponse> { ok, err ->
                client(Canned(200, "application/json", body)).validate("a@example.com", null, ok, err)
            }
            assertNull("parsed $raw as ${value?.freeEmail}", value)
            val message = error!!.errors.joinToString(" ")
            assertTrue(message, message.contains("free_email"))
            assertTrue(message, message.length < 200)
            assertFalse("the whole body: $message", message.contains("xxxxxxxx"))
            assertFalse("a parser dump: $message", message.contains("Exception"))
        }
    }

    @Test
    fun booleansAreStrictOnEveryModel() {
        val (status, statusError) = outcome<ZBFileStatusResponse> { ok, err ->
            client(Canned(200, "application/json", """{"success":"yes","file_id":"fid"}""")).fileStatus("fid", ok, err)
        }
        assertNull(status)
        assertTrue(statusError!!.errors.toString(), statusError.errors.joinToString(" ").contains("success"))

        val (activity, activityError) = outcome<ZBActivityDataResponse> { ok, err ->
            client(Canned(200, "application/json", """{"found":1,"active_in_days":"3"}"""))
                .getActivityData("a@example.com", ok, err)
        }
        assertNull(activityError?.errors?.toString(), activityError)
        assertTrue(activity!!.found)

        val (batch, batchError) = outcome<ZBValidateBatchResponse> { ok, err ->
            client(
                Canned(200, "application/json", """{"email_batch":[{"address":"a@example.com","free_email":"yes"}],"errors":[]}"""),
            ).validateBatch(listOf(ZBValidateBatchData("a@example.com", null)), ok, err)
        }
        assertNull(batch)
        assertTrue(batchError!!.errors.toString(), batchError.errors.joinToString(" ").contains("free_email"))
    }

    @Test
    fun aStreamUploadIsCoveredToo() {
        val thrown = IllegalStateException("stream")
        val reached = CountDownLatch(1)
        val errors = AtomicInteger()
        client(Canned(200, "application/json", """{"success":true,"file_id":"fid"}""")).sendFile(
            ByteArrayInputStream("email\na@example.com\n".toByteArray()),
            "in.csv",
            1,
            responseCallback = {
                reached.countDown()
                throw thrown
            },
            errorCallback = {
                errors.incrementAndGet()
                reached.countDown()
            },
        )
        assertTrue(reached.await(5, TimeUnit.SECONDS))
        eventually("the stream upload swallowed the exception") { uncaught.any { it === thrown } }
        assertEquals(0, errors.get())
    }
}
