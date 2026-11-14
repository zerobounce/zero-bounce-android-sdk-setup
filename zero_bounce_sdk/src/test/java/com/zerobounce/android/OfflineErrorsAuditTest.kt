package com.zerobounce.android

import android.content.Context
import com.google.gson.Gson
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Android audit 1, "errors" area: message-only bodies, JSON null, ip_address, htmlTitle, dates, missing fields. */
class OfflineErrorsAuditTest {

    private val context: Context = mock()
    private lateinit var dir: File
    private lateinit var csv: File

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "zb-android-errors-${System.nanoTime()}")
        dir.mkdirs()
        csv = File(dir, "in.csv").apply { writeText("email\na@example.com\n") }
        whenever(context.getExternalFilesDir(null)).thenReturn(dir)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun reply(status: Int, contentType: String, body: String) = Interceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("Status")
            .body(body.toResponseBody(contentType.toMediaType())).build()
    }

    private fun client(body: String, status: Int = 200, contentType: String = "application/json"): ZeroBounceClient {
        val client = ZeroBounceClient()
        client.transportInterceptor = reply(status, contentType, body)
        client.initialize("errors-key")
        return client
    }

    /** Methods whose success may legitimately be a bare message. */
    private fun exempt(name: String) = name == "validate" || name.endsWith("Filter") || name == "listFilters" ||
        name.contains("InboxPlacement")

    @Test
    fun aMessageOnly200IsAnErrorOnEveryTypedCall() {
        val methods = RequestSweep.requestMethods(ZeroBounceClient::class.java).filter { !exempt(it.name) }
        assertTrue("found ${methods.size}", methods.size > 40)
        val failures = mutableListOf<String>()
        for (body in listOf("""{"message":"x"}""", """{"Message":"x"}""", """{"detail":"x"}""", "{}")) {
            for (method in methods) {
                val outcome = RequestSweep(context, dir, csv).invoke(client(body), method)
                if (outcome.thrown != null || outcome.timedOut || outcome.successes.isNotEmpty() ||
                    outcome.errors.size != 1 || outcome.errors[0]?.statusCode != 200
                ) {
                    failures.add("$body $outcome")
                }
            }
        }
        assertTrue("${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun validateGreylistMessageStaysAResultAndFiltersKeepTheirMessage() {
        val sweep = RequestSweep(context, dir, csv)
        val validate = RequestSweep.requestMethods(ZeroBounceClient::class.java).filter { it.name == "validate" }
        for (method in validate) {
            val outcome = sweep.invoke(client("""{"Message":"Greylist validation still in progress"}"""), method)
            // validate's greylist answer is a result, not an error: a SUCCESS carrying the Message text.
            assertTrue(outcome.toString(), outcome.errors.isEmpty())
            assertEquals(outcome.toString(), 1, outcome.successes.size)
            val result = outcome.successes[0] as ZBValidateResponse
            assertEquals("Greylist validation still in progress", result.message)
        }
        val filters = RequestSweep.requestMethods(ZeroBounceClient::class.java).filter { it.name.endsWith("Filter") }
        for (method in filters) {
            val outcome = sweep.invoke(client("""{"Message":"Filter has been added."}"""), method)
            assertEquals(outcome.toString(), 1, outcome.successes.size)
        }
    }

    @Test
    fun aJsonNullBodyIsAnErrorOnRawJsonCalls() {
        val methods = RequestSweep.requestMethods(ZeroBounceClient::class.java)
            .filter { it.name.endsWith("Filter") || it.name == "listFilters" || it.name.contains("InboxPlacement") }
        assertTrue(methods.size >= 10)
        for (method in methods) {
            val outcome = RequestSweep(context, dir, csv).invoke(client("null"), method)
            assertTrue(outcome.toString(), outcome.successes.isEmpty() && outcome.errors.size == 1 && outcome.thrown == null)
        }
    }

    @Test
    fun aJsonNullBodyIsAnErrorOnWarmupCalls() {
        val sweep = RequestSweep(context, dir, csv)
        val methods = RequestSweep.requestMethods(ZeroBounceWarmup::class.java)
        assertTrue(methods.size > 20)
        val failures = mutableListOf<String>()
        for (method in methods) {
            val warmup = ZeroBounceWarmup()
            warmup.transportInterceptor = reply(200, "application/json", "null")
            warmup.initialize("warmup-key")
            val outcome = sweep.invoke(warmup, method)
            if (outcome.thrown != null || outcome.timedOut) failures.add(outcome.toString())
            else if (outcome.successes.any { it == null || it is com.google.gson.JsonElement && it.isJsonNull }) {
                failures.add("null success: $outcome")
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun urls(block: (ZeroBounceClient) -> Unit): Pair<List<okhttp3.Request>, List<String>> {
        val seen = CopyOnWriteArrayList<okhttp3.Request>()
        val bodies = CopyOnWriteArrayList<String>()
        val client = ZeroBounceClient()
        client.transportInterceptor = Interceptor { chain ->
            seen.add(chain.request())
            val buffer = okio.Buffer()
            chain.request().body?.writeTo(buffer)
            bodies.add(buffer.readUtf8())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"address":"a@example.com","status":"valid"}""".toResponseBody("application/json".toMediaType())).build()
        }
        client.initialize("errors-key")
        block(client)
        return seen to bodies
    }

    private fun validateOnce(ip: String?, post: Boolean): Pair<List<okhttp3.Request>, List<String>> {
        val latch = CountDownLatch(1)
        val result = urls { client ->
            client.validate("a@example.com", ip, ZBValidateOptions(usePost = post), { latch.countDown() }, { latch.countDown() })
            assertTrue(latch.await(8, TimeUnit.SECONDS))
        }
        return result
    }

    @Test
    fun validateOmitsAnEmptyOrBlankIpAddress() {
        for (ip in listOf(null, "", "   ")) {
            val (get, _) = validateOnce(ip, post = false)
            assertFalse(get[0].url.toString(), get[0].url.queryParameterNames.contains("ip_address"))
            val (post, bodies) = validateOnce(ip, post = true)
            assertFalse(bodies[0], bodies[0].contains("ip_address"))
            assertEquals(1, post.size)
        }
        val (get, _) = validateOnce("203.0.113.5", post = false)
        assertEquals("203.0.113.5", get[0].url.queryParameter("ip_address"))
        val (_, bodies) = validateOnce("203.0.113.5", post = true)
        assertTrue(bodies[0].contains("ip_address=203.0.113.5"))
    }

    @Test
    fun anHtmlTitleWithDottedCapitalIIsCutWhereItIs() {
        val title = "İ".repeat(10) + " blocked"
        val body = "<html><head><TITLE>$title</TITLE></head><body>secret-key-fragment</body></html>"
        val saved = Locale.getDefault()
        try {
            for (locale in listOf(Locale.ROOT, Locale("tr", "TR"))) {
                Locale.setDefault(locale)
                val error = httpError(500, body)
                assertEquals(
                    "ZeroBounce returned an HTML error page (\"$title\") instead of an API response.",
                    error.errors[0],
                )
            }
            // Capital dotted I before the title, which shifts a lower-cased copy.
            val shifted = "<html>" + "İ".repeat(10) + "<title>t</title></html>"
            assertEquals("ZeroBounce returned an HTML error page (\"t\") instead of an API response.", httpError(500, shifted).errors[0])
        } finally {
            Locale.setDefault(saved)
        }
    }

    private fun parse(text: String): java.util.Date? = try {
        Gson().newBuilder().registerTypeAdapter(java.util.Date::class.java, GsonDateDeserializer()).create()
            .fromJson("\"$text\"", java.util.Date::class.java)
    } catch (_: com.google.gson.JsonParseException) {
        throw IllegalArgumentException(text)
    }

    @Test
    fun datesAreStrict() {
        val saved = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            for (bad in listOf(
                "2026-02-31", "2026-13-01", "2026-02-30 10:00:00", "2026-01-01 25:00:00", "2026-01-01 10:61:00",
                "2026-01-01 garbage", "2026-01-01xyz", "2026-02-31T10:00:00Z", "2026-01-01T24:00:00Z",
                "2026-01-01T10:60:00Z", "2026-01-01T10:00:61Z", "2026-01-01T10:00:00+25:00",
                "01/01/2026 1:00:00 PM extra", "13/45/2026 1:00:00 PM",
            )) {
                try {
                    parse(bad)
                    throw AssertionError("accepted $bad")
                } catch (_: IllegalArgumentException) {
                    // rejected
                }
            }
            for (good in listOf(
                "2026-02-28", "2026-01-01 10:00:00", "2026-01-01 10:00:00.123", "2026-1-5", "2026-10-24T05:24:13Z",
                "2026-10-24T05:24:13.5+02:00", "10/24/2026 5:24:13 pm", "10/24/2026 5:24:13 AM", "2026-01-01 10:00:00 ",
            )) {
                assertTrue(good, parse(good) != null)
            }
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    @Test
    fun aMissingFieldReadsLikeANullOne() {
        val gson = Gson()
        assertNull(gson.fromJson("{}", ZBDeleteFileResponse::class.java).success)
        assertNull(gson.fromJson("""{"success":null}""", ZBDeleteFileResponse::class.java).success)
    }

    @Test
    fun aBadColumnIndexIsAnErrorCallbackAndSendsNothing() {
        val seen = CopyOnWriteArrayList<okhttp3.Request>()
        val client = ZeroBounceClient()
        client.transportInterceptor = Interceptor { chain ->
            seen.add(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("{}".toResponseBody("application/json".toMediaType())).build()
        }
        client.initialize("errors-key")
        for (scoring in listOf(false, true)) {
            for (stream in listOf(false, true)) {
                val latch = CountDownLatch(1)
                var error: ErrorResponse? = null
                val ok: (ZBSendFileResponse?) -> Unit = { latch.countDown() }
                val err: (ErrorResponse?) -> Unit = { error = it; latch.countDown() }
                val index = 0
                when {
                    !scoring && !stream -> client.sendFile(context, csv, index, responseCallback = ok, errorCallback = err)
                    !scoring -> client.sendFile(csv.inputStream(), "in.csv", index, responseCallback = ok, errorCallback = err)
                    !stream -> client.scoringSendFile(context, csv, index, responseCallback = ok, errorCallback = err)
                    else -> client.scoringSendFile(csv.inputStream(), "in.csv", index, responseCallback = ok, errorCallback = err)
                }
                assertTrue(latch.await(8, TimeUnit.SECONDS))
                assertTrue(error?.errors.toString(), error?.errors.toString().contains("emailAddressColumnIndex"))
            }
        }
        assertEquals(0, seen.size)
    }
}
