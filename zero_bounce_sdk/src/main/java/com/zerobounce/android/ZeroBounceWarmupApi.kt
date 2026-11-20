package com.zerobounce.android

import com.google.gson.JsonElement

/**
 * Every request method of the ZeroBounce Warmup API, as [ZeroBounceWarmup] offers them.
 *
 * Depend on this interface instead of [ZeroBounceWarmup] where your code makes the calls, and
 * pass a [ZeroBounceWarmup] in production. In tests, mock it with any mocking library (Mockito's
 * default proxy mock maker is enough) or a [java.lang.reflect.Proxy]: [ZeroBounceWarmup] is a
 * final class and needs mockk or Mockito's inline mock maker.
 *
 * Construction and configuration (`initialize`) are not part of it.
 *
 * New API methods are added to this interface in minor releases. It is meant to be mocked, not
 * implemented by your own classes: a class of yours that implements it may stop compiling when
 * a method is added.
 */
interface ZeroBounceWarmupApi {
    /** Every sending account of the key's team. GET /sending-accounts */
    fun listSendingAccounts(
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /** One sending account. GET /sending-account */
    fun getSendingAccount(
        fromAddress: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Adds a from-address to warmup. POST /sending-accounts
     *
     * `self_managed` runs your own mailbox and needs
     * [ZBWarmupAddSendingAccountOptions.engagementRuleId] 0-4. `zb_managed`
     * has ZeroBounce provision it and takes `emailContent` and
     * `managedSending`; a 200 means accepted, and the mailbox is provisioned
     * afterwards.
     *
     * The zb_managed body is `email_content` (`company_name`, `company_website`,
     * `company_description`, `types_of_emails`, `example_email`) and
     * `managed_sending` (`weekdays_only`, `timezone`, `timezone_offset`,
     * `warmup_start_date`), as documented at
     * https://www.zerobounce.net/docs/warmup-api/adding-a-sending-account.
     */
    fun addSendingAccount(
        fromAddress: String,
        onboardingModel: String,
        options: ZBWarmupAddSendingAccountOptions = ZBWarmupAddSendingAccountOptions(),
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Changes an account's engagement rule, status and auto-enable flag.
     * PUT /sending-accounts. [statusId] is 0 to 8 (4 Paused, 5 Disabled).
     */
    fun updateSendingAccount(
        fromAddress: String,
        engagementRuleId: Int,
        statusId: Int,
        autoEnable: Boolean,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /** Removes a sending account. DELETE /sending-accounts. Destructive and final. */
    fun deleteSendingAccount(
        fromAddress: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Moves an account and its history to a new from-address.
     * PUT /smtp-credentials - despite the path, no SMTP credentials are stored.
     */
    fun changeFromAddress(
        oldFromAddress: String,
        newFromAddress: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Registered from-addresses. GET /from-addresses
     *
     * [searchFrom] takes letters, digits and `. - _ @ +` only. [offset] and
     * [limit] go on the wire as strings.
     */
    fun listFromAddresses(
        searchFrom: String? = null,
        offset: Int? = null,
        limit: Int? = null,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /** -1 Custom, 0 Default, 1 Slow Pace, 2 Repair, 3 Boost, 4 Protect. */
    fun getEngagementRules(
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /** Sets a standard rule (0 to 4) on an address. PUT /engagement-rule */
    fun setEngagementRule(
        cFrom: String,
        ruleId: Int,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /** An address's custom rule, with can_update and remaining_seconds. */
    fun getCustomEngagement(
        cFrom: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /** Creates a custom rule. Starts a 7 day cooldown on updates. */
    fun createCustomEngagement(
        cFrom: String,
        replyPct: Int,
        importantPct: Int,
        clickPct: Int,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /** Updates a custom rule. During the 7 day cooldown the API answers 429. */
    fun updateCustomEngagement(
        cFrom: String,
        replyPct: Int,
        importantPct: Int,
        clickPct: Int,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /** Deletes a custom rule and falls back to standard rule 1-4. */
    fun deleteCustomEngagement(
        cFrom: String,
        engagementRuleId: Int,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /** The seed list. GET /seeds */
    fun getSeeds(
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /** The seed list as CSV (columns seed,provider). GET /seeds/download */
    fun downloadSeeds(
        responseCallback: (csv: String) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /** Number of seeds. GET /seed-count */
    fun getSeedCount(
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /** Today's warmup progress. GET /progress-today */
    fun getProgressToday(
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /** Messages sent today. GET /sent-today */
    fun getSentToday(
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /** Current daily volume. GET /emails-per-day */
    fun getEmailsPerDay(
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /** Statistics for an address. An address with no history returns zeros. */
    fun getStatistics(
        fromAddress: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /** Daily statistics between two dates, inclusive. GET /statistics-per-day */
    fun getStatisticsPerDay(
        fromAddress: String,
        startDate: String,
        endDate: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /** Detailed statistics for an address. GET /statistics-detailed */
    fun getStatisticsDetailed(
        cFrom: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /** An address's statistics as CSV. GET /statistics/download */
    fun downloadStatistics(
        cFrom: String,
        responseCallback: (csv: String) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /** Statistics for one from-address. GET /from-address-statistics */
    fun getFromAddressStatistics(
        fromAddress: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * SPF, DKIM and DMARC status of a from-address's domain.
     * GET /check-domain-dmarc
     *
     * LIVE: pass an EMAIL ADDRESS. The live API answers "Please provide a
     * valid email address" for a bare domain, although the docs describe a
     * domain.
     */
    fun checkDomainDmarc(
        fromAddress: String,
        dkimSelector: String? = null,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Checks that SMTP credentials connect and hand off a message.
     * POST /test-smtp-configuration
     *
     * Nothing is stored, and the test message goes to a ZeroBounce test
     * recipient, not [fromAddress].
     *
     * LIVE: the API answers any failed SMTP login with HTTP 500. That is an
     * API bug. The username and password are masked in every error from here.
     */
    fun testSmtpConfiguration(
        fromAddress: String,
        username: String,
        password: String,
        server: String,
        port: Int,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )
}
