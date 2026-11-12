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

    /**
     * The exact JSON bodies of testSmtpConfiguration and changeFromAddress, so a
     * renamed or dropped wire field fails (X3).
     */
    @Test
    fun smtpAndFromAddressBodiesUseTheDocumentedFieldNames() {
        await<JsonElement> { ok, err ->
            warmup.testSmtpConfiguration("a@b.com", "bob", "pw", "smtp.example.com", 587, ok, err)
        }
        assertEquals(
            JsonParser.parseString("""{"from_address":"a@b.com","username":"bob","password":"pw",""" +
                """"server":"smtp.example.com","port":587}"""),
            onlyRequest().json(),
        )
        await<JsonElement> { ok, err -> warmup.changeFromAddress("old@b.com", "new@b.com", ok, err) }
        assertEquals(
            JsonParser.parseString("""{"team_id":1,"old_from_address":"old@b.com","new_from_address":"new@b.com"}"""),
            onlyRequest().json(),
        )
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
    @Suppress("DEPRECATION")
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

    private fun addZbManaged(options: ZBWarmupAddSendingAccountOptions) = await<JsonElement> { ok, err ->
        warmup.addSendingAccount("a@b.com", "zb_managed", options, ok, err)
    }

    /**
     * The zb_managed body has exactly the documented fields
     * (https://www.zerobounce.net/docs/warmup-api/adding-a-sending-account).
     */
    @Test
    fun addSendingAccountSendsTheDocumentedZbManagedBody() {
        addZbManaged(
            ZBWarmupAddSendingAccountOptions(
                emailContent = ZBWarmupEmailContent(
                    companyName = "Acme",
                    companyWebsite = "https://acme.example",
                    companyDescription = "B2B email tools",
                    typesOfEmails = listOf("marketing", "newsletter"),
                    exampleEmail = "Hi, short intro to Acme.",
                ),
                managedSending = ZBWarmupManagedSending(
                    weekdaysOnly = true,
                    timezone = "Asia/Kolkata",
                    timezoneOffsetHours = 5.5,
                    warmupStartDate = "2026-03-04",
                ),
            ),
        )
        assertEquals(
            """{"from_address":"a@b.com","onboarding_model":"zb_managed",""" +
                """"email_content":{"company_name":"Acme","company_website":"https://acme.example",""" +
                """"company_description":"B2B email tools","types_of_emails":["marketing","newsletter"],""" +
                """"example_email":"Hi, short intro to Acme."},""" +
                """"managed_sending":{"weekdays_only":true,"timezone":"Asia/Kolkata",""" +
                """"timezone_offset":5.5,"warmup_start_date":"2026-03-04"}}""",
            onlyRequest().body,
        )

        addZbManaged(ZBWarmupAddSendingAccountOptions(managedSending = ZBWarmupManagedSending(weekdaysOnly = false)))
        assertEquals(
            """{"from_address":"a@b.com","onboarding_model":"zb_managed","managed_sending":{"weekdays_only":false}}""",
            onlyRequest().body,
        )
    }

    /** Whole and fractional hours from -11 to 14 go out as numbers; anything else is refused. */
    @Test
    fun addSendingAccountTakesFractionalTimezoneOffsets() {
        for (offset in listOf(5.5, 3.0, -11.0, 14.0, -3.5, 5.75)) {
            val outcome = addZbManaged(
                ZBWarmupAddSendingAccountOptions(managedSending = ZBWarmupManagedSending(timezoneOffsetHours = offset)),
            )
            assertNull(outcome.errorText())
            val sent = onlyRequest().json()!!["managed_sending"].asJsonObject["timezone_offset"]
            assertEquals(offset, sent.asDouble, 0.0)
        }
        addZbManaged(ZBWarmupAddSendingAccountOptions(managedSending = ZBWarmupManagedSending(timezoneOffsetHours = 3.0)))
        assertTrue(onlyRequest().body.contains("\"timezone_offset\":3.0"))

        for (offset in listOf(-11.5, 14.5, -12.0, 15.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            val outcome = addZbManaged(
                ZBWarmupAddSendingAccountOptions(managedSending = ZBWarmupManagedSending(timezoneOffsetHours = offset)),
            )
            assertEquals("offset $offset", "timezone_offset must be -11 to 14", outcome.errorText())
        }
        @Suppress("DEPRECATION")
        val both = addZbManaged(
            ZBWarmupAddSendingAccountOptions(
                managedSending = ZBWarmupManagedSending(timezoneOffset = 3, timezoneOffsetHours = 3.5),
            ),
        )
        assertTrue(both.errorText().orEmpty().contains("not both"))
        assertEquals("a refused offset sends nothing", 0, fake.size())
    }

    /**
     * Callers of 3.0.1's fields still compile: website goes out as
     * company_website (company_website wins when both are set), the whole-hour
     * timezoneOffset as timezone_offset, and industry and daily_limit, which the
     * API does not read, are left out.
     */
    @Test
    @Suppress("DEPRECATION")
    fun addSendingAccountMapsTheOldFieldsToTheDocumentedOnes() {
        addZbManaged(
            ZBWarmupAddSendingAccountOptions(
                emailContent = ZBWarmupEmailContent(
                    listOf("marketing"), "Software", "Acme", "https://old.example",
                ),
                managedSending = ZBWarmupManagedSending(-5, "2026-03-04", 50),
            ),
        )
        assertEquals(
            """{"from_address":"a@b.com","onboarding_model":"zb_managed",""" +
                """"email_content":{"company_name":"Acme","company_website":"https://old.example",""" +
                """"types_of_emails":["marketing"]},""" +
                """"managed_sending":{"timezone_offset":-5.0,"warmup_start_date":"2026-03-04"}}""",
            onlyRequest().body,
        )

        addZbManaged(
            ZBWarmupAddSendingAccountOptions(
                emailContent = ZBWarmupEmailContent(
                    website = "https://old.example",
                    companyWebsite = "https://new.example",
                ),
            ),
        )
        assertEquals(
            "https://new.example",
            onlyRequest().json()!!["email_content"].asJsonObject["company_website"].asString,
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

    /**
     * An HTML page served on a 200 (a proxy's or Cloudflare's block page) is an
     * error with status 200 from both CSV downloads, by its content type or by
     * its look, never a CSV; the page's title is redacted like any body text.
     */
    @Test
    fun anHtmlPageInPlaceOfACsvIsAnError() {
        val page = "<!DOCTYPE html><html><head><title>Denied for $apiKey</title></head><body>blocked</body></html>"
        val answers = listOf(
            page to "text/html; charset=utf-8",
            page to "text/csv",
            "<html><body>blocked</body></html>" to "application/octet-stream",
            "seed,provider" to "text/html",
        )
        for ((body, contentType) in answers) {
            for (download in 0..1) {
                fake.clear()
                fake.replyWith(200, contentType, body)
                val outcome = awaitText { ok, err ->
                    if (download == 0) warmup.downloadSeeds(ok, err) else warmup.downloadStatistics("a@b.com", ok, err)
                }
                assertNull("$contentType ${outcome.value}", outcome.value)
                assertEquals("$contentType ${outcome.errorText()}", 200, outcome.error?.statusCode)
                assertTrue(outcome.errorText().orEmpty(), outcome.errorText().orEmpty().contains("HTML"))
                assertTrue(outcome.errorText().orEmpty(), !outcome.errorText().orEmpty().contains(apiKey))
            }
        }
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

    // -- every request on the wire (X3) -----------------------------------------

    /** Asserts one request's method, path, whole query and whole JSON body (null for none). */
    private fun expectRequest(label: String, method: String, path: String, query: Map<String, String>, body: String?) {
        val request = onlyRequest()
        assertEquals(label, method, request.method)
        assertEquals(label, "$root$path", request.path)
        assertEquals(label, query, request.query)
        if (body == null) {
            assertTrue("$label sent a body: ${request.body}", request.body.isEmpty())
        } else {
            assertEquals("$label sent ${request.body}", JsonParser.parseString(body), JsonParser.parseString(request.body))
        }
    }

    /**
     * Every public Warmup request method, with every wire field it can send pinned:
     * method, path, the whole query and the whole JSON body. Renaming, dropping or
     * adding a field fails here (X3). The table is checked against the methods found
     * by reflection, overloads included, so a new request method must be added to it.
     */
    @Test
    fun everyWarmupRequestSendsExactlyTheDocumentedFields() {
        val a = "a@b.com"
        fun json(call: ((JsonElement?) -> Unit, (ErrorResponse?) -> Unit) -> Unit) {
            val outcome = await<JsonElement> { ok, err -> call(ok, err) }
            assertEquals(null, outcome.errorText())
        }
        fun text(call: ((String) -> Unit, (ErrorResponse?) -> Unit) -> Unit) {
            fake.replyWith(200, "text/csv", "email\na@b.com\n")
            val outcome = awaitText { ok, err -> call(ok, err) }
            assertEquals(null, outcome.errorText())
        }
        val custom = """{"c_from":"a@b.com","reply_pct":20,"important_pct":30,"click_pct":40}"""
        val calls = linkedMapOf<String, () -> Unit>(
            "listSendingAccounts()" to {
                json { ok, err -> warmup.listSendingAccounts(ok, err) }
                expectRequest("list", "GET", "/sending-accounts", mapOf(), null)
            },
            "getSendingAccount(String)" to {
                json { ok, err -> warmup.getSendingAccount(a, ok, err) }
                expectRequest("get", "GET", "/sending-account", mapOf("from_address" to a), null)
            },
            "addSendingAccount(String,String)" to {
                json { ok, err -> warmup.addSendingAccount(a, "zb_managed", responseCallback = ok, errorCallback = err) }
                expectRequest("zb_managed bare", "POST", "/sending-accounts", mapOf(),
                    """{"from_address":"a@b.com","onboarding_model":"zb_managed"}""")
            },
            "addSendingAccount(String,String,ZBWarmupAddSendingAccountOptions)" to {
                json { ok, err ->
                    warmup.addSendingAccount(a, "self_managed", ZBWarmupAddSendingAccountOptions(engagementRuleId = 2), ok, err)
                }
                // ignore_conflict is left out when the caller did not set it.
                expectRequest("self_managed", "POST", "/sending-accounts", mapOf(),
                    """{"from_address":"a@b.com","onboarding_model":"self_managed","engagement_rule_id":2}""")
                json { ok, err ->
                    warmup.addSendingAccount(a, "self_managed",
                        ZBWarmupAddSendingAccountOptions(engagementRuleId = 4, ignoreConflict = false), ok, err)
                }
                expectRequest("self_managed ignore_conflict", "POST", "/sending-accounts", mapOf(),
                    """{"from_address":"a@b.com","onboarding_model":"self_managed","engagement_rule_id":4,""" +
                        """"ignore_conflict":false}""")
                json { ok, err ->
                    warmup.addSendingAccount(a, "zb_managed",
                        ZBWarmupAddSendingAccountOptions(
                            ignoreConflict = true,
                            emailContent = ZBWarmupEmailContent(
                                companyName = "Acme",
                                companyWebsite = "https://acme.example",
                                companyDescription = "Tools",
                                typesOfEmails = listOf("newsletter"),
                                exampleEmail = "Hi",
                            ),
                            managedSending = ZBWarmupManagedSending(
                                weekdaysOnly = false,
                                timezone = "Europe/Bucharest",
                                timezoneOffsetHours = -3.5,
                                warmupStartDate = "2026-12-01",
                            ),
                        ), ok, err)
                }
                expectRequest("zb_managed full", "POST", "/sending-accounts", mapOf(),
                    """{"from_address":"a@b.com","onboarding_model":"zb_managed","ignore_conflict":true,""" +
                        """"email_content":{"company_name":"Acme","company_website":"https://acme.example",""" +
                        """"company_description":"Tools","types_of_emails":["newsletter"],"example_email":"Hi"},""" +
                        """"managed_sending":{"weekdays_only":false,"timezone":"Europe/Bucharest",""" +
                        """"timezone_offset":-3.5,"warmup_start_date":"2026-12-01"}}""")
            },
            "updateSendingAccount(String,int,int,boolean)" to {
                json { ok, err -> warmup.updateSendingAccount(a, 2, 3, true, ok, err) }
                expectRequest("update", "PUT", "/sending-accounts", mapOf(),
                    """{"team_id":1,"from_address":"a@b.com","engagement_rule_id":2,"status_id":3,"auto_enable":true}""")
            },
            "deleteSendingAccount(String)" to {
                json { ok, err -> warmup.deleteSendingAccount(a, ok, err) }
                expectRequest("delete", "DELETE", "/sending-accounts", mapOf("from_address" to a), null)
            },
            "changeFromAddress(String,String)" to {
                json { ok, err -> warmup.changeFromAddress("old@b.com", "new@b.com", ok, err) }
                expectRequest("change", "PUT", "/smtp-credentials", mapOf(),
                    """{"team_id":1,"old_from_address":"old@b.com","new_from_address":"new@b.com"}""")
            },
            "listFromAddresses()" to {
                json { ok, err -> warmup.listFromAddresses(responseCallback = ok, errorCallback = err) }
                expectRequest("from bare", "GET", "/from-addresses", mapOf(), null)
            },
            "listFromAddresses(String)" to {
                json { ok, err -> warmup.listFromAddresses("acme", responseCallback = ok, errorCallback = err) }
                expectRequest("from search", "GET", "/from-addresses", mapOf("search_from" to "acme"), null)
            },
            "listFromAddresses(String,Integer)" to {
                json { ok, err -> warmup.listFromAddresses("acme", 10, responseCallback = ok, errorCallback = err) }
                expectRequest("from offset", "GET", "/from-addresses",
                    mapOf("search_from" to "acme", "offset" to "10"), null)
            },
            "listFromAddresses(String,Integer,Integer)" to {
                json { ok, err -> warmup.listFromAddresses("acme", 10, 20, ok, err) }
                expectRequest("from", "GET", "/from-addresses",
                    mapOf("search_from" to "acme", "offset" to "10", "limit" to "20"), null)
            },
            "getEngagementRules()" to {
                json { ok, err -> warmup.getEngagementRules(ok, err) }
                expectRequest("rules", "GET", "/engagement-rules", mapOf(), null)
            },
            "setEngagementRule(String,int)" to {
                json { ok, err -> warmup.setEngagementRule(a, 3, ok, err) }
                expectRequest("rule", "PUT", "/engagement-rule", mapOf("c_from" to a), """{"rule_id":3}""")
            },
            "getCustomEngagement(String)" to {
                json { ok, err -> warmup.getCustomEngagement(a, ok, err) }
                expectRequest("custom get", "GET", "/custom-engagement/a%40b.com", mapOf(), null)
            },
            "createCustomEngagement(String,int,int,int)" to {
                json { ok, err -> warmup.createCustomEngagement(a, 20, 30, 40, ok, err) }
                expectRequest("custom create", "POST", "/custom-engagement", mapOf(), custom)
            },
            "updateCustomEngagement(String,int,int,int)" to {
                json { ok, err -> warmup.updateCustomEngagement(a, 20, 30, 40, ok, err) }
                expectRequest("custom update", "PUT", "/custom-engagement", mapOf(), custom)
            },
            "deleteCustomEngagement(String,int)" to {
                json { ok, err -> warmup.deleteCustomEngagement(a, 2, ok, err) }
                expectRequest("custom delete", "DELETE", "/custom-engagement", mapOf(),
                    """{"c_from":"a@b.com","engagement_rule_id":2}""")
            },
            "getSeeds()" to {
                json { ok, err -> warmup.getSeeds(ok, err) }
                expectRequest("seeds", "GET", "/seeds", mapOf(), null)
            },
            "downloadSeeds()" to {
                text { ok, err -> warmup.downloadSeeds(ok, err) }
                expectRequest("seeds csv", "GET", "/seeds/download", mapOf(), null)
            },
            "getSeedCount()" to {
                json { ok, err -> warmup.getSeedCount(ok, err) }
                expectRequest("seed count", "GET", "/seed-count", mapOf(), null)
            },
            "getProgressToday()" to {
                json { ok, err -> warmup.getProgressToday(ok, err) }
                expectRequest("progress", "GET", "/progress-today", mapOf(), null)
            },
            "getSentToday()" to {
                json { ok, err -> warmup.getSentToday(ok, err) }
                expectRequest("sent", "GET", "/sent-today", mapOf(), null)
            },
            "getEmailsPerDay()" to {
                json { ok, err -> warmup.getEmailsPerDay(ok, err) }
                expectRequest("per day", "GET", "/emails-per-day", mapOf(), null)
            },
            "getStatistics(String)" to {
                json { ok, err -> warmup.getStatistics(a, ok, err) }
                expectRequest("stats", "GET", "/statistics", mapOf("from_address" to a), null)
            },
            "getStatisticsPerDay(String,String,String)" to {
                json { ok, err -> warmup.getStatisticsPerDay(a, "2026-01-02", "2026-01-09", ok, err) }
                expectRequest("stats per day", "GET", "/statistics-per-day",
                    mapOf("from_address" to a, "start_date" to "2026-01-02", "end_date" to "2026-01-09"), null)
            },
            "getStatisticsDetailed(String)" to {
                json { ok, err -> warmup.getStatisticsDetailed(a, ok, err) }
                expectRequest("stats detailed", "GET", "/statistics-detailed", mapOf("c_from" to a), null)
            },
            "downloadStatistics(String)" to {
                text { ok, err -> warmup.downloadStatistics(a, ok, err) }
                expectRequest("stats csv", "GET", "/statistics/download", mapOf("c_from" to a), null)
            },
            "getFromAddressStatistics(String)" to {
                json { ok, err -> warmup.getFromAddressStatistics(a, ok, err) }
                expectRequest("from stats", "GET", "/from-address-statistics", mapOf("from_address" to a), null)
            },
            "checkDomainDmarc(String)" to {
                json { ok, err -> warmup.checkDomainDmarc(a, responseCallback = ok, errorCallback = err) }
                expectRequest("dmarc bare", "GET", "/check-domain-dmarc", mapOf("from_address" to a), null)
            },
            "checkDomainDmarc(String,String)" to {
                json { ok, err -> warmup.checkDomainDmarc(a, "s1", ok, err) }
                expectRequest("dmarc", "GET", "/check-domain-dmarc",
                    mapOf("from_address" to a, "dkim_selector" to "s1"), null)
            },
            "testSmtpConfiguration(String,String,String,String,int)" to {
                json { ok, err -> warmup.testSmtpConfiguration(a, "bob", "pw", "smtp.example.com", 465, ok, err) }
                expectRequest("smtp", "POST", "/test-smtp-configuration", mapOf(),
                    """{"from_address":"a@b.com","username":"bob","password":"pw","server":"smtp.example.com","port":465}""")
            },
        )
        for (call in calls.values) call()
        assertEquals("every Warmup request method is pinned",
            RequestSweep.requestMethods(ZeroBounceWarmup::class.java).map { RequestSweep.signature(it) }.toSortedSet(),
            calls.keys.toSortedSet())
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
    val body: String,
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
