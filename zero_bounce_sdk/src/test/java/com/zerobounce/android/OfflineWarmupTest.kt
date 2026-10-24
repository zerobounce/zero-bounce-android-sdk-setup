package com.zerobounce.android

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Offline tests for the Warmup API client. A fake OkHttp interceptor records
 * every call and answers it; nothing here contacts the live API, and the
 * assertions are on what actually went on the wire.
 */
class OfflineWarmupTest {

    private val fake = FakeWarmupApi()
    private lateinit var warmup: ZeroBounceWarmup

    private val root = "/api/v1/warmup"
    private val apiKey = "warmup-key"

    @Before
    fun setUp() {
        warmup = client(apiKey)
    }

    private fun client(key: String, baseUrl: String = ZeroBounceWarmup.DEFAULT_BASE_URL):
        ZeroBounceWarmup {
        fake.clear()
        val instance = ZeroBounceWarmup()
        instance.transportInterceptor = fake
        instance.initialize(key, baseUrl)
        return instance
    }

    // -- construction --------------------------------------------------------

    @Test
    fun theKeyTravelsInTheApiKeyHeader() {
        val outcome = await<JsonElement> { ok, err -> warmup.getSeedCount(ok, err) }
        assertNull(outcome.errorText())
        val request = onlyRequest()
        assertEquals("warmup-api.zerobounce.net", request.host)
        assertEquals("$root/seed-count", request.path)
        assertEquals(apiKey, request.apiKeyHeader)
        // The key must never reach the query string, where it would be logged.
        assertTrue(request.query.isEmpty())
    }

    @Test
    fun aKeyThatIsEmptyOrPaddedIsRejected() {
        for (bad in listOf("", " key ", "key\n", "key\tx")) {
            val instance = client(bad)
            val outcome = await<JsonElement> { ok, err -> instance.getSeeds(ok, err) }
            assertTrue(outcome.errorText().orEmpty().contains("api_key"))
        }
        assertEquals(0, fake.size())
    }

    @Test
    fun aPlainHttpBaseUrlIsRejected() {
        val instance = client(apiKey, "http://warmup-api.zerobounce.net")
        val outcome = await<JsonElement> { ok, err -> instance.getSeeds(ok, err) }
        assertTrue(outcome.errorText().orEmpty().contains("https"))
        assertEquals(0, fake.size())
    }

    @Test
    fun aCustomHttpsBaseUrlIsUsed() {
        val instance = client(apiKey, "https://warmup.example.com/")
        await<JsonElement> { ok, err -> instance.getSeeds(ok, err) }
        val request = onlyRequest()
        assertEquals("warmup.example.com", request.host)
        assertEquals("$root/seeds", request.path)
    }

    // -- paths and verbs -----------------------------------------------------

    @Test
    fun everyReadUsesItsDocumentedPath() {
        val reads = listOf<Pair<String, ((JsonElement?) -> Unit, (ErrorResponse?) -> Unit) -> Unit>>(
            "/sending-accounts" to { ok, err -> warmup.listSendingAccounts(ok, err) },
            "/sending-account" to { ok, err -> warmup.getSendingAccount("a@b.com", ok, err) },
            "/from-addresses" to { ok, err -> warmup.listFromAddresses(null, null, null, ok, err) },
            "/engagement-rules" to { ok, err -> warmup.getEngagementRules(ok, err) },
            "/custom-engagement/a%40b.com" to { ok, err -> warmup.getCustomEngagement("a@b.com", ok, err) },
            "/seeds" to { ok, err -> warmup.getSeeds(ok, err) },
            "/seed-count" to { ok, err -> warmup.getSeedCount(ok, err) },
            "/progress-today" to { ok, err -> warmup.getProgressToday(ok, err) },
            "/sent-today" to { ok, err -> warmup.getSentToday(ok, err) },
            "/emails-per-day" to { ok, err -> warmup.getEmailsPerDay(ok, err) },
            "/statistics" to { ok, err -> warmup.getStatistics("a@b.com", ok, err) },
            "/statistics-detailed" to { ok, err -> warmup.getStatisticsDetailed("a@b.com", ok, err) },
            "/from-address-statistics" to { ok, err -> warmup.getFromAddressStatistics("a@b.com", ok, err) },
            "/check-domain-dmarc" to { ok, err -> warmup.checkDomainDmarc("a@b.com", null, ok, err) },
        )
        for ((path, call) in reads) {
            await<JsonElement> { ok, err -> call(ok, err) }
            val request = onlyRequest()
            assertEquals(path, "GET", request.method)
            assertEquals("$root$path", request.path)
        }
    }

    @Test
    fun everyWriteUsesItsDocumentedPathAndVerb() {
        val writes = listOf<Triple<String, String, ((JsonElement?) -> Unit, (ErrorResponse?) -> Unit) -> Unit>>(
            Triple("POST", "/sending-accounts") { ok, err ->
                warmup.addSendingAccount(
                    "a@b.com", "self_managed",
                    ZBWarmupAddSendingAccountOptions(engagementRuleId = 0), ok, err,
                )
            },
            Triple("PUT", "/sending-accounts") { ok, err ->
                warmup.updateSendingAccount("a@b.com", 1, 4, true, ok, err)
            },
            Triple("DELETE", "/sending-accounts") { ok, err ->
                warmup.deleteSendingAccount("a@b.com", ok, err)
            },
            Triple("PUT", "/smtp-credentials") { ok, err ->
                warmup.changeFromAddress("a@b.com", "c@d.com", ok, err)
            },
            Triple("PUT", "/engagement-rule") { ok, err ->
                warmup.setEngagementRule("a@b.com", 2, ok, err)
            },
            Triple("POST", "/custom-engagement") { ok, err ->
                warmup.createCustomEngagement("a@b.com", 10, 10, 5, ok, err)
            },
            Triple("PUT", "/custom-engagement") { ok, err ->
                warmup.updateCustomEngagement("a@b.com", 10, 10, 5, ok, err)
            },
            Triple("DELETE", "/custom-engagement") { ok, err ->
                warmup.deleteCustomEngagement("a@b.com", 1, ok, err)
            },
            Triple("POST", "/test-smtp-configuration") { ok, err ->
                warmup.testSmtpConfiguration("a@b.com", "u", "p", "smtp.example.com", 587, ok, err)
            },
        )
        for ((method, path, call) in writes) {
            await<JsonElement> { ok, err -> call(ok, err) }
            val request = onlyRequest()
            assertEquals(path, method, request.method)
            assertEquals("$root$path", request.path)
        }
    }

    @Test
    fun statisticsPerDaySendsBothDates() {
        await<JsonElement> { ok, err ->
            warmup.getStatisticsPerDay("a@b.com", "2026-01-02", "2026-01-09", ok, err)
        }
        val request = onlyRequest()
        assertEquals("$root/statistics-per-day", request.path)
        assertEquals("2026-01-02", request.query["start_date"])
        assertEquals("2026-01-09", request.query["end_date"])
    }

    // -- argument validation -------------------------------------------------

    @Test
    fun requiredArgumentsAreCheckedBeforeAnyRequest() {
        val calls = listOf<((JsonElement?) -> Unit, (ErrorResponse?) -> Unit) -> Unit>(
            { ok, err -> warmup.getSendingAccount("", ok, err) },
            { ok, err -> warmup.deleteSendingAccount(" ", ok, err) },
            { ok, err -> warmup.changeFromAddress("", "b@c.com", ok, err) },
            { ok, err -> warmup.changeFromAddress("a@b.com", "", ok, err) },
            { ok, err -> warmup.setEngagementRule("", 1, ok, err) },
            { ok, err -> warmup.getStatistics("", ok, err) },
            { ok, err -> warmup.checkDomainDmarc("", null, ok, err) },
        )
        for (call in calls) {
            val outcome = await<JsonElement> { ok, err -> call(ok, err) }
            assertTrue(outcome.errorText() != null)
        }
        assertEquals(0, fake.size())
    }

    @Test
    fun engagementRuleIdsAreRangeChecked() {
        for (bad in listOf(-1, 5)) {
            val outcome = await<JsonElement> { ok, err -> warmup.setEngagementRule("a@b.com", bad, ok, err) }
            assertTrue(outcome.errorText().orEmpty().contains("rule_id"))
        }
        assertEquals(0, fake.size())

        for (good in 0..4) {
            await<JsonElement> { ok, err -> warmup.setEngagementRule("a@b.com", good, ok, err) }
            assertEquals(good, onlyRequest().json()!!["rule_id"].asInt)
        }
    }

    @Test
    fun customEngagementPercentagesAreRangeChecked() {
        val bad = listOf(
            Triple(4, 10, 5),    // reply_pct below 5
            Triple(46, 10, 5),   // reply_pct above 45
            Triple(10, 4, 5),    // important_pct below 5
            Triple(10, 41, 5),   // important_pct above 40
            Triple(10, 10, 71),  // click_pct above 70
            Triple(10, 10, 18),  // click_pct above important_pct x 1.75
        )
        for ((reply, important, click) in bad) {
            val outcome = await<JsonElement> { ok, err ->
                warmup.createCustomEngagement("a@b.com", reply, important, click, ok, err)
            }
            assertTrue(outcome.errorText() != null)
        }
        assertEquals(0, fake.size())

        await<JsonElement> { ok, err -> warmup.createCustomEngagement("a@b.com", 20, 20, 35, ok, err) }
        val body = onlyRequest().json()!!
        assertEquals(20, body["reply_pct"].asInt)
        assertEquals(35, body["click_pct"].asInt)
    }

    @Test
    fun deleteCustomEngagementOnlyFallsBackToAStandardRule() {
        val rejected = await<JsonElement> { ok, err ->
            warmup.deleteCustomEngagement("a@b.com", 0, ok, err)
        }
        assertTrue(rejected.errorText().orEmpty().contains("engagement_rule_id"))
        assertEquals(0, fake.size())

        await<JsonElement> { ok, err -> warmup.deleteCustomEngagement("a@b.com", 3, ok, err) }
        assertEquals(3, onlyRequest().json()!!["engagement_rule_id"].asInt)
    }

    @Test
    fun updateSendingAccountChecksItsRanges() {
        await<JsonElement> { ok, err -> warmup.updateSendingAccount("a@b.com", 5, 4, true, ok, err) }
        await<JsonElement> { ok, err -> warmup.updateSendingAccount("a@b.com", 1, 9, true, ok, err) }
        assertEquals(0, fake.size())

        await<JsonElement> { ok, err -> warmup.updateSendingAccount("a@b.com", 1, 5, false, ok, err) }
        val body = onlyRequest().json()!!
        assertEquals(1, body["team_id"].asInt)
        assertEquals(5, body["status_id"].asInt)
        assertEquals(false, body["auto_enable"].asBoolean)
    }

    @Test
    fun addSendingAccountChecksTheOnboardingModel() {
        val badModel = await<JsonElement> { ok, err ->
            warmup.addSendingAccount("a@b.com", "managed", ZBWarmupAddSendingAccountOptions(), ok, err)
        }
        assertTrue(badModel.errorText().orEmpty().contains("self_managed"))

        // self_managed needs its engagement rule and takes no ZB-managed fields.
        val noRule = await<JsonElement> { ok, err ->
            warmup.addSendingAccount("a@b.com", "self_managed", ZBWarmupAddSendingAccountOptions(), ok, err)
        }
        assertTrue(noRule.errorText().orEmpty().contains("engagement_rule_id"))
        val mixed = await<JsonElement> { ok, err ->
            warmup.addSendingAccount(
                "a@b.com", "self_managed",
                ZBWarmupAddSendingAccountOptions(
                    engagementRuleId = 0,
                    emailContent = ZBWarmupEmailContent(),
                ),
                ok, err,
            )
        }
        assertTrue(mixed.errorText().orEmpty().contains("zb_managed"))
        assertEquals(0, fake.size())

        await<JsonElement> { ok, err ->
            warmup.addSendingAccount(
                "a@b.com", "self_managed",
                ZBWarmupAddSendingAccountOptions(engagementRuleId = 2, ignoreConflict = true),
                ok, err,
            )
        }
        val body = onlyRequest().json()!!
        assertEquals("self_managed", body["onboarding_model"].asString)
        assertEquals(2, body["engagement_rule_id"].asInt)
        assertEquals(true, body["ignore_conflict"].asBoolean)
    }

    @Test
    fun addSendingAccountChecksTheZbManagedPayload() {
        val badTypes = await<JsonElement> { ok, err ->
            warmup.addSendingAccount(
                "a@b.com", "zb_managed",
                ZBWarmupAddSendingAccountOptions(
                    emailContent = ZBWarmupEmailContent(typesOfEmails = listOf("promotional")),
                ),
                ok, err,
            )
        }
        assertTrue(badTypes.errorText().orEmpty().contains("types_of_emails"))

        val badOffset = await<JsonElement> { ok, err ->
            warmup.addSendingAccount(
                "a@b.com", "zb_managed",
                ZBWarmupAddSendingAccountOptions(
                    managedSending = ZBWarmupManagedSending(timezoneOffset = 15),
                ),
                ok, err,
            )
        }
        assertTrue(badOffset.errorText().orEmpty().contains("timezone_offset"))

        val badDate = await<JsonElement> { ok, err ->
            warmup.addSendingAccount(
                "a@b.com", "zb_managed",
                ZBWarmupAddSendingAccountOptions(
                    managedSending = ZBWarmupManagedSending(warmupStartDate = "02/01/2026"),
                ),
                ok, err,
            )
        }
        assertTrue(badDate.errorText().orEmpty().contains("warmup_start_date"))
        assertEquals(0, fake.size())

        await<JsonElement> { ok, err ->
            warmup.addSendingAccount(
                "a@b.com", "zb_managed",
                ZBWarmupAddSendingAccountOptions(
                    emailContent = ZBWarmupEmailContent(
                        typesOfEmails = listOf("newsletter", "marketing"),
                    ),
                    managedSending = ZBWarmupManagedSending(
                        timezoneOffset = -5,
                        warmupStartDate = "2026-03-04",
                    ),
                ),
                ok, err,
            )
        }
        val body = onlyRequest().json()!!
        assertEquals("zb_managed", body["onboarding_model"].asString)
        assertEquals(
            "marketing",
            body["email_content"].asJsonObject["types_of_emails"].asJsonArray[1].asString,
        )
        assertEquals(
            "2026-03-04",
            body["managed_sending"].asJsonObject["warmup_start_date"].asString,
        )
    }

    @Test
    fun listFromAddressesChecksItsSearchAndSendsPagingAsStrings() {
        val badSearch = await<JsonElement> { ok, err ->
            warmup.listFromAddresses("bad search!", null, null, ok, err)
        }
        assertTrue(badSearch.errorText().orEmpty().contains("search_from"))
        val badOffset = await<JsonElement> { ok, err ->
            warmup.listFromAddresses(null, -1, null, ok, err)
        }
        assertTrue(badOffset.errorText().orEmpty().contains("offset"))
        assertEquals(0, fake.size())

        await<JsonElement> { ok, err -> warmup.listFromAddresses("a.b-c_d@e+f", 0, 50, ok, err) }
        val query = onlyRequest().query
        assertEquals("a.b-c_d@e+f", query["search_from"])
        assertEquals("0", query["offset"])
        assertEquals("50", query["limit"])
    }

    @Test
    fun statisticsPerDayRejectsABackwardsRangeAndBadDates() {
        val backwards = await<JsonElement> { ok, err ->
            warmup.getStatisticsPerDay("a@b.com", "2026-02-02", "2026-01-01", ok, err)
        }
        assertTrue(backwards.errorText().orEmpty().contains("later"))
        val badFormat = await<JsonElement> { ok, err ->
            warmup.getStatisticsPerDay("a@b.com", "01/02/2026", "2026-01-01", ok, err)
        }
        assertTrue(badFormat.errorText().orEmpty().contains("YYYY-MM-DD"))
        assertEquals(0, fake.size())
    }

    @Test
    fun checkDomainDmarcRequiresAnEmailAddressNotADomain() {
        // LIVE: the API answers a bare domain with "Please provide a valid
        // email address", although the docs describe a domain.
        val outcome = await<JsonElement> { ok, err ->
            warmup.checkDomainDmarc("example.com", null, ok, err)
        }
        assertTrue(outcome.errorText().orEmpty().contains("email address"))
        assertEquals(0, fake.size())

        await<JsonElement> { ok, err -> warmup.checkDomainDmarc("sender@example.com", "zb", ok, err) }
        assertEquals("zb", onlyRequest().query["dkim_selector"])
    }

    @Test
    fun customEngagementPathIdsCannotTraverse() {
        for (bad in listOf(".", "..", " .. ")) {
            val outcome = await<JsonElement> { ok, err -> warmup.getCustomEngagement(bad, ok, err) }
            assertTrue(outcome.errorText().orEmpty().contains("invalid"))
        }
        assertEquals(0, fake.size())
    }

    @Test
    fun theSmtpTestChecksItsPort() {
        for (bad in listOf(0, 65536)) {
            val outcome = await<JsonElement> { ok, err ->
                warmup.testSmtpConfiguration("a@b.com", "u", "p", "s", bad, ok, err)
            }
            assertTrue(outcome.errorText().orEmpty().contains("port"))
        }
        assertEquals(0, fake.size())
    }

    // -- responses -----------------------------------------------------------

    @Test
    fun anEmptySuccessBecomesNull() {
        fake.replyWith(204, "application/json", "")
        val deleted = await<JsonElement> { ok, err -> warmup.deleteSendingAccount("a@b.com", ok, err) }
        assertNull(deleted.errorText())
        assertNull(deleted.value)
        fake.clear()

        fake.replyWith(200, "application/json", "")
        val counted = await<JsonElement> { ok, err -> warmup.getSeedCount(ok, err) }
        assertNull(counted.value)
    }

    @Test
    fun aPayloadThatIsNotAnObjectComesBackAsIs() {
        // LIVE: /seeds answers with an array and /seed-count with a bare number.
        fake.replyWith(200, "application/json", """[{"seed":"a@b.com"},{"seed":"c@d.com"}]""")
        val seeds = await<JsonElement> { ok, err -> warmup.getSeeds(ok, err) }
        assertEquals(2, seeds.value!!.asJsonArray.size())
        fake.clear()

        fake.replyWith(200, "application/json", "250")
        val count = await<JsonElement> { ok, err -> warmup.getSeedCount(ok, err) }
        assertEquals(250, count.value!!.asInt)
    }

    @Test
    fun csvDownloadsComeBackAsText() {
        fake.replyWith(200, "text/csv", "seed,provider\na@b.com,gmail\n")
        val csv = awaitText { ok, err -> warmup.downloadSeeds(ok, err) }
        assertEquals("seed,provider\na@b.com,gmail\n", csv.value)
        fake.clear()

        fake.replyWith(200, "text/csv", "day,sent\n2026-01-01,10\n")
        awaitText { ok, err -> warmup.downloadStatistics("a@b.com", ok, err) }
        assertEquals("a@b.com", onlyRequest().query["c_from"])
    }

    @Test
    fun aJsonErrorInPlaceOfACsvIsAnError() {
        fake.replyWith(200, "application/json", """{"message":"No statistics for this address."}""")
        val outcome = awaitText { ok, err -> warmup.downloadSeeds(ok, err) }
        assertTrue(outcome.errorText().orEmpty().contains("No statistics for this address."))
    }

    @Test
    fun theHttpStatusIsOnTheError() {
        fake.replyWith(
            429, "application/json",
            """{"message":"Custom rule was updated less than 7 days ago."}""",
        )
        val outcome = await<JsonElement> { ok, err ->
            warmup.updateCustomEngagement("a@b.com", 10, 10, 5, ok, err)
        }
        assertEquals(429, outcome.error?.statusCode)
        assertTrue(outcome.errorText().orEmpty().contains("Custom rule was updated"))
    }

    @Test
    fun anHtmlErrorPageIsNotPastedIntoTheMessage() {
        val page = buildString {
            append("<!DOCTYPE html><html><head><title>Access denied</title></head><body>")
            repeat(2000) { append("<p>blocked</p>") }
            append("</body></html>")
        }
        fake.replyWith(403, "text/html", page)
        val outcome = await<JsonElement> { ok, err -> warmup.getSeeds(ok, err) }
        assertTrue(outcome.errorText().orEmpty().contains("HTML error page"))
        assertTrue(outcome.errorText().orEmpty().length < 500)
        assertEquals(403, outcome.error?.statusCode)
    }

    @Test
    fun aRedirectIsRefusedRatherThanFollowed() {
        fake.replyWith(302, "text/html", "")
        val outcome = await<JsonElement> { ok, err -> warmup.getSeeds(ok, err) }
        assertTrue(outcome.errorText().orEmpty().contains("redirect"))
        assertEquals(302, outcome.error?.statusCode)
    }

    @Test
    fun theKeyNeverAppearsInAnError() {
        fake.replyWith(401, "application/json", """{"message":"Invalid key $apiKey"}""")
        val outcome = await<JsonElement> { ok, err -> warmup.getSeeds(ok, err) }
        assertTrue(!outcome.errorText().orEmpty().contains(apiKey))
        assertTrue(outcome.errorText().orEmpty().contains("***"))
    }

    @Test
    fun smtpCredentialsNeverAppearInAnError() {
        fake.replyWith(
            500, "application/json",
            """{"message":"login failed for user bob with s3cret"}""",
        )
        val outcome = await<JsonElement> { ok, err ->
            warmup.testSmtpConfiguration("a@b.com", "bob", "s3cret", "smtp.example.com", 587, ok, err)
        }
        val text = outcome.errorText().orEmpty()
        assertTrue(!text.contains("s3cret"))
        assertTrue(!text.contains("bob"))
        // LIVE: a failed SMTP login comes back as a 500, not a 4xx.
        assertEquals(500, outcome.error?.statusCode)
    }

    @Test
    fun aMalformedBodyDoesNotLeakAParserDump() {
        fake.replyWith(200, "application/json", "<<<not-json>>>" + "x".repeat(5000))
        val outcome = await<JsonElement> { ok, err -> warmup.getSeeds(ok, err) }
        assertTrue(outcome.errorText().orEmpty().contains("not valid JSON"))
    }

    // -- helpers -------------------------------------------------------------

    private fun onlyRequest(): WarmupRequest {
        val records = fake.take()
        assertEquals("expected exactly one request", 1, records.size)
        fake.clear()
        return records.first()
    }

    private data class Outcome<T>(val value: T?, val error: ErrorResponse?) {
        fun errorText(): String? = error?.errors?.joinToString(", ")
    }

    private fun <T> await(start: ((T?) -> Unit, (ErrorResponse?) -> Unit) -> Unit): Outcome<T> {
        val latch = CountDownLatch(1)
        var value: T? = null
        var error: ErrorResponse? = null
        start(
            { parsed ->
                value = parsed
                latch.countDown()
            },
            { parsed ->
                error = parsed
                latch.countDown()
            },
        )
        assertTrue("timed out with ${fake.size()} request(s)", latch.await(8, TimeUnit.SECONDS))
        return Outcome(value, error)
    }

    private fun awaitText(start: ((String) -> Unit, (ErrorResponse?) -> Unit) -> Unit): Outcome<String> {
        val latch = CountDownLatch(1)
        var value: String? = null
        var error: ErrorResponse? = null
        start(
            { text ->
                value = text
                latch.countDown()
            },
            { parsed ->
                error = parsed
                latch.countDown()
            },
        )
        assertTrue("timed out with ${fake.size()} request(s)", latch.await(8, TimeUnit.SECONDS))
        return Outcome(value, error)
    }
}

internal class WarmupRequest(
    val method: String,
    val host: String,
    val path: String,
    val apiKeyHeader: String,
    val query: Map<String, String>,
    private val body: String,
) {
    fun json(): com.google.gson.JsonObject? =
        if (body.isBlank()) null else JsonParser.parseString(body).asJsonObject
}

internal class FakeWarmupApi : Interceptor {
    private val records = CopyOnWriteArrayList<WarmupRequest>()

    @Volatile
    private var next: Triple<Int, String, String>? = null

    fun take(): List<WarmupRequest> = records.toList()

    fun clear() {
        records.clear()
        next = null
    }

    fun size(): Int = records.size

    fun replyWith(code: Int, contentType: String, body: String) {
        next = Triple(code, contentType, body)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val query = linkedMapOf<String, String>()
        for (name in request.url.queryParameterNames) {
            query[name] = request.url.queryParameter(name).orEmpty()
        }
        val body = request.body?.let { raw ->
            val buffer = Buffer()
            raw.writeTo(buffer)
            buffer.readUtf8()
        }.orEmpty()
        records.add(
            WarmupRequest(
                method = request.method,
                host = request.url.host,
                path = request.url.encodedPath,
                apiKeyHeader = request.header("API-Key").orEmpty(),
                query = query,
                body = body,
            ),
        )

        val answer = next ?: Triple(
            200,
            "application/json",
            """{"ok":true,"echo_api_key":"${request.header("API-Key").orEmpty()}"}""",
        )
        next = null
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(answer.first)
            .message("OK")
            .header("Content-Type", answer.second)
            .body(answer.third.toResponseBody(answer.second.toMediaType()))
            .build()
    }
}
