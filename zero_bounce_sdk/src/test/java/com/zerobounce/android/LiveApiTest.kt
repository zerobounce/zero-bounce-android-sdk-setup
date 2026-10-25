package com.zerobounce.android

import android.content.Context
import com.google.gson.JsonElement
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Opt-in live API suite: calls every SDK method against the real API to check
 * payloads and response shapes.
 *
 * The default test task excludes it, so `gradle test` and CI never run it.
 * Run it explicitly:
 *
 * ```
 * export ZEROBOUNCE_API_KEY=...          # validation API (skipped without it)
 * export ZEROBOUNCE_WARMUP_API_KEY=...   # Warmup API (skipped without it)
 * gradle :zero_bounce_sdk:testDebugUnitTest -PzbLive \
 *   --tests "com.zerobounce.android.LiveApiTest"
 * ```
 *
 * By default only free calls run: sandbox addresses (no credits), reads, and
 * requests expected to fail (unknown file IDs, S3/SFTP without an integration)
 * that still prove the API accepts the payload. The free tier checks that the
 * credit balance did not change. Two flags opt in to more:
 *
 * * `ZEROBOUNCE_LIVE_SPEND=1` spends credits: scoring, activity, email finder,
 *   and a bulk round trip whose job is deleted again.
 * * `ZEROBOUNCE_LIVE_CHANGES=1` reversible account changes, each undone in
 *   cleanup even when a check fails: a test filter, and a self-managed Warmup
 *   sending account.
 *
 * Never called, because the API cannot undo them: creating an Inbox Placement
 * test (it cannot be deleted while running), setting the Inbox Placement
 * webhook (it cannot be cleared), and ZB-managed Warmup onboarding (it starts
 * mailbox provisioning).
 *
 * No key is ever written to a file or printed. Keys come from the environment.
 */
class LiveApiTest {

    /**
     * Well-formed but matching no file: a malformed id can be
     * Cloudflare-blocked instead of answered.
     */
    private val unknownId = "0d1b7a4e-1111-2222-3333-444455556666"
    private val callback = "https://example.com/zerobounce-sdk-live-test"
    private val sandbox = "valid@example.com"

    private var passed = 0
    private var failed = 0
    private var skipped = 0
    private val failures = mutableListOf<String>()

    private fun env(name: String): String = System.getenv(name).orEmpty().trim()
    private val apiKey get() = env("ZEROBOUNCE_API_KEY")
    private val warmupKey get() = env("ZEROBOUNCE_WARMUP_API_KEY")
    private fun flag(name: String) = env(name) == "1"

    private fun redact(text: String?): String {
        var value = text.orEmpty()
        for (secret in listOf(apiKey, warmupKey)) {
            if (secret.isNotEmpty()) value = value.replace(secret, "***")
        }
        return value
    }

    /** A check either passes, or records why it did not. */
    private fun check(name: String, body: () -> String?) {
        val problem = try {
            body()
        } catch (e: Throwable) {
            redact(e.message)
        }
        if (problem.isNullOrEmpty()) {
            passed++
            println("  + $name")
        } else {
            failed++
            failures += "$name: ${redact(problem)}"
            println("  X $name\n      ${redact(problem)}")
        }
    }

    private fun skip(name: String, reason: String) {
        skipped++
        println("  - SKIP $name ($reason)")
    }

    private class Outcome<T>(val value: T?, val error: ErrorResponse?) {
        fun errorText(): String? = error?.errors?.joinToString(", ")
    }

    private fun <T> await(start: ((T?) -> Unit, (ErrorResponse?) -> Unit) -> Unit): Outcome<T> {
        val latch = CountDownLatch(1)
        var value: T? = null
        var error: ErrorResponse? = null
        start(
            { parsed -> value = parsed; latch.countDown() },
            { parsed -> error = parsed; latch.countDown() },
        )
        check(latch.await(180, TimeUnit.SECONDS)) { "the call never answered" }
        return Outcome(value, error)
    }

    private fun awaitText(start: ((String) -> Unit, (ErrorResponse?) -> Unit) -> Unit): Outcome<String> {
        val latch = CountDownLatch(1)
        var value: String? = null
        var error: ErrorResponse? = null
        start(
            { text -> value = text; latch.countDown() },
            { parsed -> error = parsed; latch.countDown() },
        )
        check(latch.await(180, TimeUnit.SECONDS)) { "the call never answered" }
        return Outcome(value, error)
    }

    private fun check(condition: Boolean, message: () -> String) {
        if (!condition) throw AssertionError(message())
    }

    /** Reports what a call produced: null on success. */
    private fun <T> ran(outcome: Outcome<T>): String? = outcome.errorText()

    /** Reports when a call that had to fail did not. */
    private fun <T> mustFail(what: String, outcome: Outcome<T>): String? =
        if (outcome.errorText() == null) "$what unexpectedly succeeded" else null

    private fun sdk() {
        ZeroBounceSDK.transportInterceptor = null
        ZeroBounceSDK.initialize(apiKey)
    }

    private fun warmupClient(): ZeroBounceWarmup =
        ZeroBounceWarmup().also { it.initialize(warmupKey) }

    @Test
    fun liveApiSuite() {
        val spend = flag("ZEROBOUNCE_LIVE_SPEND")
        val changes = flag("ZEROBOUNCE_LIVE_CHANGES")

        println("ZeroBounce live API suite")
        println("  validation key: ${if (apiKey.isEmpty()) "MISSING" else "set"}")
        println("  warmup key:     ${if (warmupKey.isEmpty()) "MISSING" else "set"}")
        println("  flags:          SPEND=${if (spend) 1 else 0} CHANGES=${if (changes) 1 else 0}\n")

        if (apiKey.isNotEmpty()) {
            sdk()
            validationFree()
            if (spend) validationSpend()
            if (changes) validationChanges()
        }
        if (warmupKey.isNotEmpty()) {
            warmupFree()
            if (changes) warmupChanges()
        }

        println("\n$passed passed, $failed failed, $skipped skipped")
        if (failures.isNotEmpty()) {
            println("\nFailures:")
            failures.forEach { println("  - $it") }
        }
        if (failed > 0) throw AssertionError("$failed live checks failed")
    }

    // -- validation API, free ------------------------------------------------

    private fun validationFree() {
        println("Validation API (free calls)")
        var before: String? = null

        check("getCredits returns a balance") {
            val outcome = await<ZBCreditsResponse> { ok, err -> ZeroBounceSDK.getCredits(ok, err) }
            before = outcome.value?.credits
            if (before == null) ran(outcome) ?: "no credits in the response" else null
        }

        check("getApiUsage parses the documented date formats") {
            val end = java.util.Date()
            val start = java.util.Date(end.time - 7L * 24 * 3600 * 1000)
            ran(await<ZBGetApiUsageResponse> { ok, err ->
                ZeroBounceSDK.getApiUsage(start, end, ok, err)
            })
        }

        check("validate on a sandbox address spends nothing") {
            val outcome = await<ZBValidateResponse> { ok, err ->
                ZeroBounceSDK.validate(sandbox, null, ok, err)
            }
            ran(outcome) ?: if (outcome.value?.address == sandbox) null else "address did not echo"
        }

        check("validate works over POST as well as GET") {
            // No billable add-on here: activity_data, verify_plus and
            // domain_info are charged even on a sandbox address, so they live
            // in the SPEND tier.
            ran(await<ZBValidateResponse> { ok, err ->
                ZeroBounceSDK.validate(
                    sandbox, null, ZBValidateOptions(timeout = 30, usePost = true), ok, err,
                )
            })
        }

        check("LIVE: validateBatch accepts timeout as a string") {
            // Sent as a JSON number the API rejects the whole batch with the
            // account-level "Invalid API Key or your account ran out of credits".
            val outcome = await<ZBValidateBatchResponse> { ok, err ->
                ZeroBounceSDK.validateBatch(
                    listOf(ZBValidateBatchData(sandbox, null)),
                    ZBValidateOptions(timeout = 30), ok, err,
                )
            }
            ran(outcome)
        }

        check("an unknown file id is a short, clean error") {
            val outcome = await<ZBFileStatusResponse> { ok, err ->
                ZeroBounceSDK.fileStatus(unknownId, ok, err)
            }
            mustFail("fileStatus", outcome)
                ?: if (outcome.errorText().orEmpty().length < 500) null else "message too long"
        }

        check("listS3Files answers (empty without an S3 integration)") {
            val outcome = await<JsonElement> { ok, err -> ZeroBounceSDK.listS3Files(null, null, ok, err) }
            // No S3 integration configured is an expected, documented failure.
            if (outcome.errorText().orEmpty().length >= 500) "message too long" else null
        }

        check("LIVE: S3/SFTP jobs reject a payload without return_url before sending") {
            val outcome = await<ZBSendFileResponse> { ok, err ->
                ZeroBounceSDK.emailFinderS3File(
                    ZBBulkFileJobOptions(fileName = "f.csv", hasHeaderRow = true), ok, err,
                )
            }
            mustFail("emailFinderS3File", outcome)
                ?: if (outcome.errorText().orEmpty().contains("return_url")) null
                else "unexpected message: ${redact(outcome.errorText())}"
        }

        check("LIVE: a complete S3 job payload is accepted by the API") {
            // Without an S3 integration this fails on the integration, not the payload.
            val outcome = await<ZBSendFileResponse> { ok, err ->
                ZeroBounceSDK.emailFinderS3File(
                    ZBBulkFileJobOptions(
                        fileName = "f.csv", hasHeaderRow = true, returnUrl = callback,
                    ),
                    ok, err,
                )
            }
            val message = redact(outcome.errorText())
            if (message.contains("return URL is not permitted") ||
                message.contains("This field is required")
            ) {
                "payload rejected: $message"
            } else {
                null
            }
        }

        check("filters list") {
            ran(await<JsonElement> { ok, err -> ZeroBounceSDK.listFilters(false, ok, err) })
        }

        check("LIVE: the Inbox Placement Tester accepts a Bearer key") {
            ran(await<JsonElement> { ok, err -> ZeroBounceSDK.getInboxPlacementProviders(ok, err) })
        }

        check("inbox placement test types") {
            ran(await<JsonElement> { ok, err -> ZeroBounceSDK.getInboxPlacementTestTypes(ok, err) })
        }

        check("LIVE: inbox placement tests are listed from /tests") {
            // The listing is GET /tests (plural); /test is the single-test path
            // and answers Method "GET" not allowed. The page size has to go as
            // page_size: pageSize is accepted and then ignored.
            ran(await<JsonElement> { ok, err ->
                ZeroBounceSDK.listInboxPlacementTests(
                    ZBListInboxPlacementTestsOptions(page = 1, pageSize = 10), ok, err,
                )
            }) ?: ran(await<JsonElement> { ok, err ->
                ZeroBounceSDK.getInboxPlacementWebhook(ok, err)
            })
        }

        check("list evaluator result for an unknown id fails cleanly") {
            mustFail(
                "getListEvaluatorResult",
                await<JsonElement> { ok, err -> ZeroBounceSDK.getListEvaluatorResult(unknownId, ok, err) },
            )
        }

        check("LIVE: sendEvaluatedFile posts the file id as a form field") {
            val outcome = await<ZBSendFileResponse> { ok, err ->
                ZeroBounceSDK.sendEvaluatedFile(unknownId, ok, err)
            }
            mustFail("sendEvaluatedFile", outcome)
                ?: if (redact(outcome.errorText()).contains("not allowed")) {
                    "wrong method or path: ${redact(outcome.errorText())}"
                } else {
                    null
                }
        }

        check("the free calls did not change the credit balance") {
            val outcome = await<ZBCreditsResponse> { ok, err -> ZeroBounceSDK.getCredits(ok, err) }
            val after = outcome.value?.credits
            if (after == before) null else "credits went from $before to $after"
        }
    }

    // -- validation API, spends credits --------------------------------------

    private fun validationSpend() {
        println("\nValidation API (ZEROBOUNCE_LIVE_SPEND=1)")

        check("validate accepts every documented option") {
            // LIVE: activity_data, verify_plus and domain_info are billable
            // add-ons and are charged even for a sandbox address.
            ran(await<ZBValidateResponse> { ok, err ->
                ZeroBounceSDK.validate(
                    sandbox, null,
                    ZBValidateOptions(
                        timeout = 30,
                        activityData = true,
                        verifyPlus = true,
                        domainInfo = true,
                        greylistProcessing = true,
                        callbackUrl = callback,
                    ),
                    ok, err,
                )
            })
        }

        check("scoreEmail") {
            ran(await<ZBScoreEmailResponse> { ok, err -> ZeroBounceSDK.scoreEmail(sandbox, ok, err) })
        }
        check("scoreEmail over POST") {
            ran(await<ZBScoreEmailResponse> { ok, err ->
                ZeroBounceSDK.scoreEmailPost(sandbox, ok, err)
            })
        }
        check("getActivityData") {
            ran(await<ZBActivityDataResponse> { ok, err ->
                ZeroBounceSDK.getActivityData(sandbox, ok, err)
            })
        }
        check("email finder") {
            ran(await<ZBFindEmailResponse> { ok, err ->
                ZeroBounceSDK.findEmail("John", "example.com", null, null, "Doe", ok, err)
            })
        }
        check("domain search") {
            ran(await<ZBFindDomainResponse> { ok, err ->
                ZeroBounceSDK.findDomain("example.com", null, ok, err)
            })
        }

        var fileId: String? = null
        check("bulk validation round trip: upload, status, delete") {
            val context: Context = mock()
            val downloads = File(
                System.getProperty("java.io.tmpdir"),
                "zb-live-${System.nanoTime()}",
            ).also { it.mkdirs() }
            whenever(context.getExternalFilesDir(null)).thenReturn(downloads)
            val csv = File(downloads, "zb-sdk-live.csv")
            csv.writeText("email\nvalid@example.com\ninvalid@example.com\n")

            val sent = await<ZBSendFileResponse> { ok, err ->
                ZeroBounceSDK.sendFile(
                    context, csv, 1,
                    hasHeaderRow = true,
                    responseCallback = ok,
                    errorCallback = err,
                )
            }
            downloads.deleteRecursively()
            val problem = ran(sent)
            if (problem != null) return@check problem
            fileId = sent.value?.fileId
            if (fileId.isNullOrEmpty()) return@check "no file_id in the upload response"

            val status = await<ZBFileStatusResponse> { ok, err ->
                ZeroBounceSDK.fileStatus(fileId!!, ok, err)
            }
            // fileId is private on the response, so the status is checked by it
            // having been accepted at all.
            ran(status)
        }

        val id = fileId
        if (id == null) {
            skip("the uploaded validation job is deleted again", "upload did not produce a file id")
        } else {
            check("the uploaded validation job is deleted again") {
                // LIVE: a just-accepted job answers "File cannot be found" for
                // a short while, so the delete is retried rather than leaving
                // the job behind.
                var last = ""
                repeat(12) {
                    val outcome = await<ZBDeleteFileResponse> { ok, err ->
                        ZeroBounceSDK.deleteFile(id, ok, err)
                    }
                    if (outcome.errorText() == null) return@check null
                    last = redact(outcome.errorText())
                    Thread.sleep(10_000)
                }
                "could not delete $id after retrying: $last"
            }
        }
    }

    // -- validation API, reversible account changes --------------------------

    private fun validationChanges() {
        println("\nValidation API (ZEROBOUNCE_LIVE_CHANGES=1)")
        val value = "zb-sdk-live-${System.currentTimeMillis() / 1000}.example.com"
        var added = false

        check("addFilter creates a reversible allow rule") {
            val outcome = await<JsonElement> { ok, err ->
                ZeroBounceSDK.addFilter("allow", "domain", value, ok, err)
            }
            if (outcome.errorText() == null) added = true
            ran(outcome)
        }

        if (!added) {
            skip("the test filter is removed again", "filter was not created")
            return
        }
        check("the test filter is removed again") {
            ran(await<JsonElement> { ok, err ->
                ZeroBounceSDK.deleteFilter("allow", "domain", value, ok, err)
            })
        }
    }

    // -- Warmup API ----------------------------------------------------------

    private fun warmupFree() {
        println("\nWarmup API (reads)")
        val zb = warmupClient()

        val reads = listOf<Pair<String, ((JsonElement?) -> Unit, (ErrorResponse?) -> Unit) -> Unit>>(
            "listSendingAccounts" to { ok, err -> zb.listSendingAccounts(ok, err) },
            "listFromAddresses" to { ok, err -> zb.listFromAddresses(null, null, null, ok, err) },
            "getEngagementRules" to { ok, err -> zb.getEngagementRules(ok, err) },
            "getSeeds" to { ok, err -> zb.getSeeds(ok, err) },
            "getSeedCount" to { ok, err -> zb.getSeedCount(ok, err) },
            "getProgressToday" to { ok, err -> zb.getProgressToday(ok, err) },
            "getSentToday" to { ok, err -> zb.getSentToday(ok, err) },
            "getEmailsPerDay" to { ok, err -> zb.getEmailsPerDay(ok, err) },
        )
        for ((name, call) in reads) {
            check(name) { ran(await<JsonElement> { ok, err -> call(ok, err) }) }
        }

        check("downloadSeeds returns CSV text") {
            val outcome = awaitText { ok, err -> zb.downloadSeeds(ok, err) }
            ran(outcome)
                ?: if (outcome.value.orEmpty().trimStart().startsWith("{")) {
                    "got JSON instead of CSV"
                } else {
                    null
                }
        }

        check("LIVE: checkDomainDmarc rejects a bare domain before sending") {
            val outcome = await<JsonElement> { ok, err ->
                zb.checkDomainDmarc("example.com", null, ok, err)
            }
            mustFail("checkDomainDmarc", outcome)
                ?: if (outcome.errorText().orEmpty().contains("email address")) null
                else "unexpected message: ${outcome.errorText()}"
        }

        check("LIVE: checkDomainDmarc accepts an email address") {
            ran(await<JsonElement> { ok, err ->
                zb.checkDomainDmarc("sender@example.com", null, ok, err)
            })
        }

        // The statistics endpoints need a from-address this team actually has;
        // the API answers "Sending account ... was not found" for anything else.
        val accounts = await<JsonElement> { ok, err -> zb.listSendingAccounts(ok, err) }
        val known = runCatching {
            val payload = accounts.value
            val list = when {
                payload == null -> null
                payload.isJsonObject && payload.asJsonObject.has("data") ->
                    payload.asJsonObject["data"].asJsonArray
                payload.isJsonArray -> payload.asJsonArray
                else -> null
            }
            val first = list?.firstOrNull()?.asJsonObject ?: return@runCatching null
            listOf("from_address", "c_from", "fromAddress")
                .firstNotNullOfOrNull { field ->
                    if (first.has(field)) first[field].asString else null
                }
        }.getOrNull()

        if (known == null) {
            listOf(
                "statistics for a registered from-address",
                "statistics per day over a short range",
                "detailed statistics and the CSV download for that address",
                "from-address statistics",
            ).forEach { skip(it, "this team has no sending account") }
        } else {
            check("statistics for a registered from-address") {
                ran(await<JsonElement> { ok, err -> zb.getStatistics(known, ok, err) })
            }
            check("statistics per day over a short range") {
                ran(await<JsonElement> { ok, err ->
                    zb.getStatisticsPerDay(
                        known,
                        LocalDate.now().minusDays(6).toString(),
                        LocalDate.now().toString(),
                        ok, err,
                    )
                })
            }
            check("detailed statistics and the CSV download for that address") {
                ran(await<JsonElement> { ok, err -> zb.getStatisticsDetailed(known, ok, err) })
                    ?: ran(awaitText { ok, err -> zb.downloadStatistics(known, ok, err) })
            }
            check("from-address statistics") {
                ran(await<JsonElement> { ok, err -> zb.getFromAddressStatistics(known, ok, err) })
            }
        }

        check("an unknown Warmup resource fails with a status code") {
            val outcome = await<JsonElement> { ok, err ->
                zb.getCustomEngagement("no-such-address@example.com", ok, err)
            }
            val error = outcome.error ?: return@check null
            when {
                error.statusCode == 0 -> "no status code on the error"
                outcome.errorText().orEmpty().contains(warmupKey) -> "the key leaked into the message"
                else -> null
            }
        }
    }

    private fun warmupChanges() {
        println("\nWarmup API (ZEROBOUNCE_LIVE_CHANGES=1)")
        val zb = warmupClient()
        val address = "zb-sdk-live-${System.currentTimeMillis() / 1000}@example.com"
        var added = false

        check("a self-managed sending account can be added") {
            val outcome = await<JsonElement> { ok, err ->
                zb.addSendingAccount(
                    address, "self_managed",
                    ZBWarmupAddSendingAccountOptions(engagementRuleId = 0, ignoreConflict = true),
                    ok, err,
                )
            }
            if (outcome.errorText() == null) added = true
            ran(outcome)
        }

        if (!added) {
            skip("the test sending account is removed again", "account was not created")
            return
        }
        check("the test sending account is removed again") {
            ran(await<JsonElement> { ok, err -> zb.deleteSendingAccount(address, ok, err) })
        }
    }
}
