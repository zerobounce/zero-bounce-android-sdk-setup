package com.zerobounce.android

import android.content.Context
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.lang.reflect.Method
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The transport guards on every public request method of [ZeroBounceClient] and
 * [ZeroBounceWarmup], against a real HTTP server on a loopback socket
 * ([LoopbackServer]): a body cut short of its Content-Length, a redirect with a
 * Location, the timeouts and redirect policy of the client that sends each request,
 * and the key in a transport error. The methods come from [RequestSweep], whose
 * completeness [theSweepCoversEveryPublicRequestMethod] checks.
 */
class OfflineTransportTest {

    private val context: Context = mock()
    private lateinit var dir: File
    private lateinit var csv: File

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "zb-android-transport-${System.nanoTime()}")
        dir.mkdirs()
        whenever(context.getExternalFilesDir(null)).thenReturn(dir)
        csv = File(dir, "in.csv").apply { writeText("email\na@example.com\n") }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private val key = "transport-key"

    private fun isDownload(method: Method): Boolean {
        val name = method.name.lowercase()
        return name.contains("getfile") || name.startsWith("download")
    }

    /** What every request of a swept client looked like on the way out. */
    private class Seen(val connectMs: Int, val readMs: Int, val writeMs: Int,
                       val followRedirects: Boolean, val followSslRedirects: Boolean)

    /**
     * Calls every public request method of both clients with [interceptor] as the
     * test transport and hands each outcome to [check].
     */
    private fun sweep(
        interceptor: Interceptor,
        seconds: Long = 10,
        check: (Method, RequestSweep.Outcome, MutableList<String>) -> Unit,
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
            val outcome = sweep.invoke(target, method, seconds)
            if (outcome.thrown != null || outcome.timedOut) {
                failures.add(outcome.toString())
                continue
            }
            check(method, outcome, failures)
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
        assertTrue(RequestSweep.requestMethods(ZeroBounceClient::class.java).size >= 50)
        assertTrue(RequestSweep.requestMethods(ZeroBounceWarmup::class.java).size >= 25)
    }

    /**
     * A 200 that closes before the Content-Length it declared reaches the
     * error callback as a transport failure (status 0) on every path. OkHttp throws
     * the short read inside onResponse, where an uncaught exception is only
     * logged and no callback would run. A download leaves no partial file.
     */
    @Test
    fun everyRequestPathTreatsABodyCutShortAsATransportFailure() {
        // Longer than the 8 KiB the downloads sniff first, so the cut comes
        // while the file is being written and a partial file exists.
        val csvBody = ("email,status\n" + "a@example.com,valid\n".repeat(1000)).toByteArray()
        val jsonBody = "{\"success\": true}                ".toByteArray()
        val csv = LoopbackServer.cutShort("text/csv", csvBody, csvBody.size + 1000)
        val json = LoopbackServer.cutShort("application/json", jsonBody, 1000)
        LoopbackServer { request, out ->
            val target = request.target.lowercase()
            (if (target.contains("getfile") || target.contains("download")) csv else json)(request, out)
        }.use { server ->
            sweep(server.reroute) { method, outcome, failures ->
                if (outcome.successes.isNotEmpty() || outcome.errors.size != 1 ||
                    outcome.errors[0]?.statusCode != 0 || outcome.errors[0]?.errors.isNullOrEmpty()
                ) {
                    failures.add(outcome.toString())
                }
                if (isDownload(method)) {
                    val left = outcome.out?.let { listOf(File(it)) }
                        ?: dir.listFiles().orEmpty().filter { it.name != "in.csv" }
                    left.filter { it.exists() }.forEach {
                        failures.add("${outcome.signature} kept a partial file ${it.name} (${it.length()} bytes)")
                        it.delete()
                    }
                }
            }
        }
    }

    /**
     * Every path refuses each redirect status with a Location header: the
     * error callback gets the 3xx status and nothing reaches the Location.
     */
    @Test
    fun everyRequestPathRefusesARedirect() {
        LoopbackServer(LoopbackServer.answer(200, "application/json", "{}".toByteArray())).use { elsewhere ->
            for (status in listOf(301, 302, 303, 307, 308)) {
                val location = "http://127.0.0.1:${elsewhere.port}/moved"
                LoopbackServer(LoopbackServer.redirect(status, location)).use { server ->
                    sweep(server.reroute) { _, outcome, failures ->
                        if (elsewhere.requests.isNotEmpty()) {
                            failures.add("${outcome.signature} followed a $status to $location")
                            elsewhere.requests.clear()
                        }
                        if (outcome.successes.isNotEmpty() || outcome.errors.size != 1 ||
                            outcome.errors[0]?.statusCode != status
                        ) {
                            failures.add("$status $outcome")
                        }
                    }
                }
            }
        }
    }

    /**
     * Every request goes out on a client with non-zero connect, read and write
     * timeouts that follows no redirect, plain or between http and https.
     */
    @Test
    fun everyRequestPathHasTimeoutsAndNoRedirects() {
        val seen = CopyOnWriteArrayList<Seen>()
        var consumed = 0
        val recorder = Interceptor { chain ->
            // The client that runs the call, read from OkHttp's call object.
            val call = chain.call()
            val client = call.javaClass.getMethod("getClient").invoke(call) as OkHttpClient
            seen.add(Seen(chain.connectTimeoutMillis(), chain.readTimeoutMillis(),
                chain.writeTimeoutMillis(), client.followRedirects, client.followSslRedirects))
            val request = chain.request()
            val body = if (request.url.encodedPath.contains("getfile") || request.url.encodedPath.contains("download")) {
                "email\na@example.com\n"
            } else {
                "{}"
            }
            val type = if (body.startsWith("{")) "application/json" else "text/csv"
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("Content-Type", type)
                .body(body.toResponseBody(type.toMediaType()))
                .build()
        }
        sweep(recorder) { _, outcome, failures ->
            // The call has answered, so its request is recorded.
            val mine = seen.subList(consumed, seen.size).toList()
            consumed = seen.size
            if (mine.isEmpty()) failures.add("${outcome.signature} sent no request")
            for (request in mine) {
                if (request.connectMs <= 0 || request.readMs <= 0 || request.writeMs <= 0) {
                    failures.add("${outcome.signature}: connect ${request.connectMs} ms, read ${request.readMs} ms, write ${request.writeMs} ms")
                }
                if (request.followRedirects || request.followSslRedirects) {
                    failures.add("${outcome.signature} went out on a client that follows redirects")
                }
            }
        }
    }

    /**
     * The platform's own transport error can quote the request URL; the key
     * never reaches the error callback, on any path.
     */
    @Test
    fun aTransportErrorNeverCarriesTheKey() {
        val thrower = Interceptor { chain ->
            throw IOException("failed to connect to ${chain.request().url} (key $key)")
        }
        sweep(thrower) { _, outcome, failures ->
            val error = outcome.errors.singleOrNull()
            if (error == null || outcome.successes.isNotEmpty()) {
                failures.add(outcome.toString())
                return@sweep
            }
            if (error.statusCode != 0 || error.errors.isEmpty() || error.errors.any { it.contains(key) }) {
                failures.add(outcome.toString())
            }
        }
    }

    /** An initialize that throws leaves the client exactly as it was. */
    @Test
    fun initializeThatThrowsChangesNothing() {
        val urls = CopyOnWriteArrayList<String>()
        val recorder = Interceptor { chain ->
            urls.add(chain.request().url.toString())
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{\"Credits\":\"5\"}".toResponseBody("application/json".toMediaType()))
                .build()
        }
        val client = ZeroBounceClient()
        client.transportInterceptor = recorder
        client.initialize("first-key")
        try {
            client.initialize("second-key", "http://api.example.test/v2")
            fail("an http base URL was accepted")
        } catch (_: IllegalArgumentException) {
            // expected
        }
        val done = java.util.concurrent.CountDownLatch(1)
        client.getCredits({ done.countDown() }, { done.countDown() })
        assertTrue(done.await(5, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(listOf("https://api.zerobounce.net/v2/getcredits?api_key=first-key"), urls)
    }
}
