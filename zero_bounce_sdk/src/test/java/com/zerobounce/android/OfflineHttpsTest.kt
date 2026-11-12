package com.zerobounce.android

import android.content.Context
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Every request goes out over HTTPS, on the SDK's own default base URLs.
 *
 * The other offline suites reset the shared base URLs to https before each
 * test, so a default that had turned into http:// would pass there. This one
 * uses a fresh [ZeroBounceClient], which starts from the defaults, and checks
 * the default of every base URL directly.
 *
 * [everyPublicRequestMethodSendsOverHttps] finds the request methods by
 * reflection (every public method of [ZeroBounceClient] that takes a callback)
 * and invokes each overload by reflection. [sweepArgs] gives each one the
 * arguments other than its callbacks; a public request method missing from it
 * fails [everyPublicRequestMethodIsInTheSweep], so a new request path cannot
 * skip the sweep.
 */
class OfflineHttpsTest {

    private class Recorder : Interceptor {
        val urls = CopyOnWriteArrayList<String>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            urls.add(request.url.toString())
            val body = if (request.url.encodedPath.contains("getfile")) {
                "email\na@example.com\n"
            } else {
                """{"success":true,"file_id":"fid"}"""
            }
            val type = if (body.startsWith("{")) "application/json" else "text/csv"
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

    private val context: Context = mock()
    private lateinit var dir: File
    private lateinit var csv: File

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "zb-android-https-${System.nanoTime()}")
        dir.mkdirs()
        whenever(context.getExternalFilesDir(null)).thenReturn(dir)
        csv = File(dir, "in.csv").apply { writeText("email\na@example.com\n") }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun everyDefaultBaseUrlIsHttps() {
        val urls = SdkState::class.java.declaredFields
            .filter { it.type == String::class.java && it.name.endsWith("Url") }
            .map { field ->
                field.isAccessible = true
                field.name to field.get(SdkState()) as String
            }
        assertTrue("no base URL fields found", urls.size >= 6)
        for ((name, url) in urls) {
            assertTrue("$name defaults to $url", url.startsWith("https://"))
        }
        assertTrue(ZeroBounceWarmup.DEFAULT_BASE_URL.startsWith("https://"))
    }

    @Test
    fun everyRequestPathUsesHttpsOnTheDefaults() {
        val recorder = Recorder()
        val sdk = ZeroBounceClient()
        sdk.transportInterceptor = recorder
        sdk.initialize("https-sweep-key")
        val out = File(dir, "out.csv").path
        val job = ZBBulkFileJobOptions(fileName = "f.csv", returnUrl = "https://example.com/done")
        val calls: List<(() -> Unit, (ErrorResponse?) -> Unit) -> Unit> = listOf(
            // apiBaseUrl
            { done, err -> sdk.validate("a@example.com", null, { done() }, err) },
            { done, err -> sdk.getCredits({ done() }, err) },
            { done, err -> sdk.listFilters(false, { done() }, err) },
            // bulkApiBaseUrl
            { done, err -> sdk.sendFile(context, csv, 1, responseCallback = { done() }, errorCallback = err) },
            { done, err ->
                sdk.sendFile(
                    ByteArrayInputStream("email\na@example.com\n".toByteArray()),
                    "in.csv",
                    1,
                    responseCallback = { done() },
                    errorCallback = err,
                )
            },
            { done, err -> sdk.fileStatus("fid", { done() }, err) },
            { done, err -> sdk.getFile(context, "fid", { done() }, err) },
            { done, err -> sdk.deleteFile("fid", { done() }, err) },
            // bulkApiScoringBaseUrl
            { done, err -> sdk.scoringSendFile(context, csv, 1, responseCallback = { done() }, errorCallback = err) },
            { done, err -> sdk.scoringFileStatus("fid", { done() }, err) },
            { done, err -> sdk.scoringGetFile(context, "fid", { done() }, err) },
            // bulkApiRootUrl: email finder, domain search, S3 and SFTP jobs
            { done, err -> sdk.sendDomainSearchFile(csv, 1, responseCallback = { done() }, errorCallback = err) },
            { done, err -> sdk.emailFinderFileStatus("fid", { done() }, err) },
            { done, err -> sdk.emailFinderGetFile("fid", out, { done() }, err) },
            { done, err -> sdk.domainSearchFileStatus("fid", { done() }, err) },
            { done, err -> sdk.listS3Files(null, null, { done() }, err) },
            { done, err -> sdk.validateSFTPFile(job, { done() }, err) },
            { done, err -> sdk.exportS3File("fid", "bucket", { done() }, err) },
            // listEvaluatorBaseUrl
            { done, err -> sdk.sendListEvaluatorFile(csv, 1, { done() }, err) },
            { done, err -> sdk.getListEvaluatorResult("fid", { done() }, err) },
            { done, err -> sdk.sendEvaluatedFile("fid", { done() }, err) },
            // inboxPlacementBaseUrl
            { done, err -> sdk.getInboxPlacementProviders({ done() }, err) },
        )
        for ((index, call) in calls.withIndex()) {
            val latch = CountDownLatch(1)
            call({ latch.countDown() }, { latch.countDown() })
            assertTrue("call $index did not finish", latch.await(5, TimeUnit.SECONDS))
        }
        assertEquals(recorder.urls.toString(), calls.size, recorder.urls.size)
        for (url in recorder.urls) {
            assertTrue("sent over plain http: $url", url.startsWith("https://"))
        }
        // Each default base URL was exercised.
        for (prefix in listOf(
            "https://api.zerobounce.net/v2/",
            "https://bulkapi.zerobounce.net/v2/sendfile",
            "https://bulkapi.zerobounce.net/v2/scoring/",
            "https://bulkapi.zerobounce.net/email-finder/",
            "https://bulkapi.zerobounce.net/listS3files",
            "https://bulkapi.zerobounce.net/v2/listevaluator",
            "https://bulkapi.zerobounce.net/api/ipt/",
        )) {
            assertTrue("nothing went to $prefix: ${recorder.urls}", recorder.urls.any { it.startsWith(prefix) })
        }
    }

    private fun sweepArgs(): Map<String, () -> Array<Any?>> = RequestSweep(context, dir, csv).clientArgs

    private fun requestMethods(type: Class<*>): List<Method> = RequestSweep.requestMethods(type)

    private fun signature(method: Method): String = RequestSweep.signature(method)

    private fun isCallback(type: Class<*>) = RequestSweep.isCallback(type)

    @Test
    fun everyPublicRequestMethodIsInTheSweep() {
        val client = requestMethods(ZeroBounceClient::class.java).map { signature(it) }.toSortedSet()
        val shared = requestMethods(ZeroBounceSDK::class.java).map { signature(it) }.toSortedSet()
        assertTrue("found only ${client.size} request methods: $client", client.size >= 50)
        // ZeroBounceClient is swept; ZeroBounceSDK runs the same code, so each must match.
        assertEquals("ZeroBounceSDK and ZeroBounceClient request methods differ", shared, client)
        val swept = sweepArgs().keys
        assertEquals("public request methods missing from the HTTPS sweep", sortedSetOf<String>(), client - swept)
        assertEquals("sweep entries that match no public request method", sortedSetOf<String>(), swept - client)
    }

    @Test
    fun everyPublicRequestMethodSendsOverHttps() {
        val recorder = Recorder()
        val sdk = ZeroBounceClient()
        sdk.transportInterceptor = recorder
        sdk.initialize("https-sweep-key")
        val args = sweepArgs()
        val failures = mutableListOf<String>()
        var swept = 0
        for (method in requestMethods(ZeroBounceClient::class.java)) {
            val signature = signature(method)
            val before = args[signature]?.invoke() ?: continue // reported by everyPublicRequestMethodIsInTheSweep
            val latch = CountDownLatch(1)
            val outcome = CopyOnWriteArrayList<Any?>()
            val callback: (Any?) -> Unit = { value ->
                outcome.add(value)
                latch.countDown()
            }
            var next = 0
            val call = method.parameterTypes.map { type -> if (isCallback(type)) callback else before[next++] }
            assertEquals("$signature: argument count", before.size, next)

            recorder.urls.clear()
            try {
                method.invoke(sdk, *call.toTypedArray())
            } catch (e: InvocationTargetException) {
                failures.add("$signature threw ${e.cause}")
                continue
            }
            if (!latch.await(5, TimeUnit.SECONDS)) {
                failures.add("$signature did not call back")
            }
            if (recorder.urls.isEmpty()) {
                failures.add("$signature sent no request: $outcome")
            }
            for (url in recorder.urls) {
                if (!url.startsWith("https://")) failures.add("$signature sent over plain http: $url")
            }
            swept++
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
        assertEquals("every sweep entry was invoked", args.size, swept)
    }
}
