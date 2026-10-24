package com.zerobounce.android

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Client for the ZeroBounce Warmup API
 * ([docs](https://www.zerobounce.net/docs/warmup-api)).
 *
 * The Warmup API is separate from the email validation API: its own host, its
 * own team-scoped key, and an `API-Key` header instead of an `api_key`
 * parameter. Use [ZeroBounceWarmup] with a Warmup API key; [ZeroBounceSDK]
 * stays for the validation API key.
 *
 * Every method answers through a callback, like [ZeroBounceSDK]. A documented
 * empty success (204, or an empty 200) arrives as a null payload; a payload
 * that is not a JSON object - `/seeds` is an array, `/seed-count` a bare
 * number - arrives as that [JsonElement]. Failures carry the HTTP status on
 * [ErrorResponse.statusCode].
 */
class ZeroBounceWarmup {

    companion object {
        const val DEFAULT_BASE_URL = "https://warmup-api.zerobounce.net"
        private const val BASE_PATH = "/api/v1/warmup"

        /** Required by the schema but always replaced with the key's own team. */
        private const val ANY_TEAM_ID = 1

        private val ONBOARDING_MODELS = setOf("self_managed", "zb_managed")
        private val EMAIL_TYPES = setOf("transactional", "newsletter", "marketing")
        private val SEARCH_FROM = Regex("^[A-Za-z0-9.\\-_@+]*$")
        private val ISO_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    }

    private val gson = Gson()
    private var apiKey: String = ""
    private var root: String = DEFAULT_BASE_URL + BASE_PATH

    /** Why the client is unusable, or null when it is ready. */
    private var setupProblem: String? =
        "ZeroBounceWarmup is not initialized. Call initialize(apiKey) first"

    private var client: OkHttpClient = buildClient(null)

    /**
     * Test hook. When set, [initialize] installs it on the OkHttp client so
     * unit tests can answer requests without the network.
     */
    internal var transportInterceptor: Interceptor? = null

    private fun buildClient(interceptor: Interceptor?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
        interceptor?.let { builder.addInterceptor(it) }
        return builder.build()
    }

    /**
     * Stores the Warmup API key, and optionally a custom https base URL.
     *
     * A key with surrounding whitespace or control characters, or a base URL
     * that is not https, is rejected here: every later call then fails with
     * that message rather than sending the key anywhere.
     */
    @JvmOverloads
    fun initialize(apiKey: String, baseUrl: String = DEFAULT_BASE_URL) {
        client = buildClient(transportInterceptor)
        this.apiKey = apiKey
        setupProblem = when {
            apiKey.isBlank() -> "api_key is required"
            // A key with surrounding whitespace or control characters would be
            // quoted, unredacted, in the transport's own error.
            apiKey != apiKey.trim() || apiKey.any { it.isISOControl() } ->
                "api_key has surrounding whitespace or control characters"
            !baseUrl.lowercase().startsWith("https://") -> "base_url must be an https:// URL"
            else -> null
        }
        if (setupProblem == null) {
            root = baseUrl.trimEnd('/') + BASE_PATH
        }
    }

    // -- sending accounts ----------------------------------------------------

    /** Every sending account of the key's team. GET /sending-accounts */
    fun listSendingAccounts(
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (notReady(errorCallback)) return
        json("GET", "/sending-accounts", emptyMap(), null, responseCallback, errorCallback)
    }

    /** One sending account. GET /sending-account */
    fun getSendingAccount(
        fromAddress: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (notReady(errorCallback) || missing(fromAddress, "from_address", errorCallback)) return
        json("GET", "/sending-account", mapOf("from_address" to fromAddress), null,
            responseCallback, errorCallback)
    }

    /**
     * Adds a from-address to warmup. POST /sending-accounts
     *
     * `self_managed` runs your own mailbox and needs
     * [ZBWarmupAddSendingAccountOptions.engagementRuleId] 0-4. `zb_managed`
     * has ZeroBounce provision it and takes `emailContent` and
     * `managedSending`; a 200 means accepted, and the mailbox is provisioned
     * afterwards.
     */
    @JvmOverloads
    fun addSendingAccount(
        fromAddress: String,
        onboardingModel: String,
        options: ZBWarmupAddSendingAccountOptions = ZBWarmupAddSendingAccountOptions(),
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (notReady(errorCallback) || missing(fromAddress, "from_address", errorCallback)) return
        if (onboardingModel !in ONBOARDING_MODELS) {
            errorCallback(ErrorResponse.parseError("onboarding_model must be self_managed or zb_managed"))
            return
        }

        val body = linkedMapOf<String, Any?>(
            "from_address" to fromAddress,
            "onboarding_model" to onboardingModel,
        )
        if (onboardingModel == "self_managed") {
            val rule = options.engagementRuleId
            if (rule == null) {
                errorCallback(ErrorResponse.parseError("engagement_rule_id is required for self_managed"))
                return
            }
            if (outOfRange(rule, 0, 4, "engagement_rule_id", errorCallback)) return
            body["engagement_rule_id"] = rule
            if (options.emailContent != null || options.managedSending != null) {
                errorCallback(ErrorResponse.parseError("email_content and managed_sending are for zb_managed"))
                return
            }
        }
        options.ignoreConflict?.let { body["ignore_conflict"] = it }

        options.emailContent?.let { content ->
            val payload = linkedMapOf<String, Any?>()
            if (content.typesOfEmails.isNotEmpty()) {
                for (kind in content.typesOfEmails) {
                    if (kind !in EMAIL_TYPES) {
                        errorCallback(
                            ErrorResponse.parseError(
                                "types_of_emails must be transactional, newsletter and/or marketing",
                            ),
                        )
                        return
                    }
                }
                payload["types_of_emails"] = content.typesOfEmails
            }
            content.industry?.let { payload["industry"] = it }
            content.companyName?.let { payload["company_name"] = it }
            content.website?.let { payload["website"] = it }
            body["email_content"] = payload
        }
        options.managedSending?.let { sending ->
            val payload = linkedMapOf<String, Any?>()
            sending.timezoneOffset?.let {
                if (outOfRange(it, -11, 14, "timezone_offset", errorCallback)) return
                payload["timezone_offset"] = it
            }
            sending.warmupStartDate?.let {
                if (!ISO_DATE.matches(it)) {
                    errorCallback(ErrorResponse.parseError("warmup_start_date must be YYYY-MM-DD"))
                    return
                }
                payload["warmup_start_date"] = it
            }
            sending.dailyLimit?.let { payload["daily_limit"] = it }
            body["managed_sending"] = payload
        }

        json("POST", "/sending-accounts", emptyMap(), body, responseCallback, errorCallback)
    }

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
    ) {
        if (notReady(errorCallback) || missing(fromAddress, "from_address", errorCallback)) return
        if (outOfRange(engagementRuleId, 0, 4, "engagement_rule_id", errorCallback)) return
        if (outOfRange(statusId, 0, 8, "status_id", errorCallback)) return

        json(
            "PUT", "/sending-accounts", emptyMap(),
            linkedMapOf(
                "team_id" to ANY_TEAM_ID,
                "from_address" to fromAddress,
                "engagement_rule_id" to engagementRuleId,
                "status_id" to statusId,
                "auto_enable" to autoEnable,
            ),
            responseCallback, errorCallback,
        )
    }

    /** Removes a sending account. DELETE /sending-accounts. Destructive and final. */
    fun deleteSendingAccount(
        fromAddress: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (notReady(errorCallback) || missing(fromAddress, "from_address", errorCallback)) return
        json("DELETE", "/sending-accounts", mapOf("from_address" to fromAddress), null,
            responseCallback, errorCallback)
    }

    /**
     * Moves an account and its history to a new from-address.
     * PUT /smtp-credentials - despite the path, no SMTP credentials are stored.
     */
    fun changeFromAddress(
        oldFromAddress: String,
        newFromAddress: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (notReady(errorCallback) ||
            missing(oldFromAddress, "old_from_address", errorCallback) ||
            missing(newFromAddress, "new_from_address", errorCallback)
        ) {
            return
        }
        json(
            "PUT", "/smtp-credentials", emptyMap(),
            linkedMapOf(
                "team_id" to ANY_TEAM_ID,
                "old_from_address" to oldFromAddress,
                "new_from_address" to newFromAddress,
            ),
            responseCallback, errorCallback,
        )
    }

    /**
     * Registered from-addresses. GET /from-addresses
     *
     * [searchFrom] takes letters, digits and `. - _ @ +` only. [offset] and
     * [limit] go on the wire as strings.
     */
    @JvmOverloads
    fun listFromAddresses(
        searchFrom: String? = null,
        offset: Int? = null,
        limit: Int? = null,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (notReady(errorCallback)) return
        val query = linkedMapOf<String, String>()
        searchFrom?.let {
            if (!SEARCH_FROM.matches(it)) {
                errorCallback(
                    ErrorResponse.parseError("search_from takes letters, digits and . - _ @ + only"),
                )
                return
            }
            query["search_from"] = it
        }
        offset?.let {
            if (it < 0) {
                errorCallback(ErrorResponse.parseError("offset must be a non-negative integer"))
                return
            }
            query["offset"] = it.toString()
        }
        limit?.let {
            if (it < 0) {
                errorCallback(ErrorResponse.parseError("limit must be a non-negative integer"))
                return
            }
            query["limit"] = it.toString()
        }
        json("GET", "/from-addresses", query, null, responseCallback, errorCallback)
    }

    // -- engagement rules ----------------------------------------------------

    /** -1 Custom, 0 Default, 1 Slow Pace, 2 Repair, 3 Boost, 4 Protect. */
    fun getEngagementRules(
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (notReady(errorCallback)) return
        json("GET", "/engagement-rules", emptyMap(), null, responseCallback, errorCallback)
    }

    /** Sets a standard rule (0 to 4) on an address. PUT /engagement-rule */
    fun setEngagementRule(
        cFrom: String,
        ruleId: Int,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (notReady(errorCallback) || missing(cFrom, "c_from", errorCallback)) return
        if (outOfRange(ruleId, 0, 4, "rule_id", errorCallback)) return
        json("PUT", "/engagement-rule", mapOf("c_from" to cFrom),
            linkedMapOf("rule_id" to ruleId), responseCallback, errorCallback)
    }

    /** An address's custom rule, with can_update and remaining_seconds. */
    fun getCustomEngagement(
        cFrom: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (notReady(errorCallback) || missing(cFrom, "c_from", errorCallback)) return
        val segment = cFrom.trim()
        if (segment == "." || segment == "..") {
            errorCallback(ErrorResponse.parseError("c_from is invalid"))
            return
        }
        json("GET", "/custom-engagement/${encode(segment)}", emptyMap(), null,
            responseCallback, errorCallback)
    }

    /** Creates a custom rule. Starts a 7 day cooldown on updates. */
    fun createCustomEngagement(
        cFrom: String,
        replyPct: Int,
        importantPct: Int,
        clickPct: Int,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (notReady(errorCallback)) return
        val body = engagementBody(cFrom, replyPct, importantPct, clickPct, errorCallback) ?: return
        json("POST", "/custom-engagement", emptyMap(), body, responseCallback, errorCallback)
    }

    /** Updates a custom rule. During the 7 day cooldown the API answers 429. */
    fun updateCustomEngagement(
        cFrom: String,
        replyPct: Int,
        importantPct: Int,
        clickPct: Int,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (notReady(errorCallback)) return
        val body = engagementBody(cFrom, replyPct, importantPct, clickPct, errorCallback) ?: return
        json("PUT", "/custom-engagement", emptyMap(), body, responseCallback, errorCallback)
    }

    /** Deletes a custom rule and falls back to standard rule 1-4. */
    fun deleteCustomEngagement(
        cFrom: String,
        engagementRuleId: Int,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (notReady(errorCallback) || missing(cFrom, "c_from", errorCallback)) return
        if (outOfRange(engagementRuleId, 1, 4, "engagement_rule_id", errorCallback)) return
        json(
            "DELETE", "/custom-engagement", emptyMap(),
            linkedMapOf("c_from" to cFrom, "engagement_rule_id" to engagementRuleId),
            responseCallback, errorCallback,
        )
    }

    // -- statistics, seeds and utilities -------------------------------------

    /** The seed list. GET /seeds */
    fun getSeeds(
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (notReady(errorCallback)) return
        json("GET", "/seeds", emptyMap(), null, responseCallback, errorCallback)
    }

    /** The seed list as CSV (columns seed,provider). GET /seeds/download */
    fun downloadSeeds(
        responseCallback: (csv: String) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (notReady(errorCallback)) return
        csv("/seeds/download", emptyMap(), responseCallback, errorCallback)
    }

    /** Number of seeds. GET /seed-count */
    fun getSeedCount(
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (notReady(errorCallback)) return
        json("GET", "/seed-count", emptyMap(), null, responseCallback, errorCallback)
    }

    /** Today's warmup progress. GET /progress-today */
    fun getProgressToday(
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (notReady(errorCallback)) return
        json("GET", "/progress-today", emptyMap(), null, responseCallback, errorCallback)
    }

    /** Messages sent today. GET /sent-today */
    fun getSentToday(
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (notReady(errorCallback)) return
        json("GET", "/sent-today", emptyMap(), null, responseCallback, errorCallback)
    }

    /** Current daily volume. GET /emails-per-day */
    fun getEmailsPerDay(
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (notReady(errorCallback)) return
        json("GET", "/emails-per-day", emptyMap(), null, responseCallback, errorCallback)
    }

    /** Statistics for an address. An address with no history returns zeros. */
    fun getStatistics(
        fromAddress: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (notReady(errorCallback) || missing(fromAddress, "from_address", errorCallback)) return
        json("GET", "/statistics", mapOf("from_address" to fromAddress), null,
            responseCallback, errorCallback)
    }

    /** Daily statistics between two dates, inclusive. GET /statistics-per-day */
    fun getStatisticsPerDay(
        fromAddress: String,
        startDate: String,
        endDate: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (notReady(errorCallback) || missing(fromAddress, "from_address", errorCallback)) return
        if (!ISO_DATE.matches(startDate)) {
            errorCallback(ErrorResponse.parseError("start_date must be YYYY-MM-DD"))
            return
        }
        if (!ISO_DATE.matches(endDate)) {
            errorCallback(ErrorResponse.parseError("end_date must be YYYY-MM-DD"))
            return
        }
        if (startDate > endDate) {
            errorCallback(ErrorResponse.parseError("start_date is later than end_date"))
            return
        }
        json(
            "GET", "/statistics-per-day",
            linkedMapOf(
                "from_address" to fromAddress,
                "start_date" to startDate,
                "end_date" to endDate,
            ),
            null, responseCallback, errorCallback,
        )
    }

    /** Detailed statistics for an address. GET /statistics-detailed */
    fun getStatisticsDetailed(
        cFrom: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (notReady(errorCallback) || missing(cFrom, "c_from", errorCallback)) return
        json("GET", "/statistics-detailed", mapOf("c_from" to cFrom), null,
            responseCallback, errorCallback)
    }

    /** An address's statistics as CSV. GET /statistics/download */
    fun downloadStatistics(
        cFrom: String,
        responseCallback: (csv: String) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (notReady(errorCallback) || missing(cFrom, "c_from", errorCallback)) return
        csv("/statistics/download", mapOf("c_from" to cFrom), responseCallback, errorCallback)
    }

    /** Statistics for one from-address. GET /from-address-statistics */
    fun getFromAddressStatistics(
        fromAddress: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (notReady(errorCallback) || missing(fromAddress, "from_address", errorCallback)) return
        json("GET", "/from-address-statistics", mapOf("from_address" to fromAddress), null,
            responseCallback, errorCallback)
    }

    /**
     * SPF, DKIM and DMARC status of a from-address's domain.
     * GET /check-domain-dmarc
     *
     * LIVE: pass an EMAIL ADDRESS. The live API answers "Please provide a
     * valid email address" for a bare domain, although the docs describe a
     * domain.
     */
    @JvmOverloads
    fun checkDomainDmarc(
        fromAddress: String,
        dkimSelector: String? = null,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (notReady(errorCallback) || missing(fromAddress, "from_address", errorCallback)) return
        if (!fromAddress.contains('@')) {
            errorCallback(
                ErrorResponse.parseError(
                    "from_address must be an email address, e.g. sender@example.com",
                ),
            )
            return
        }
        val query = linkedMapOf("from_address" to fromAddress)
        dkimSelector?.let {
            if (missing(it, "dkim_selector", errorCallback)) return
            query["dkim_selector"] = it
        }
        json("GET", "/check-domain-dmarc", query, null, responseCallback, errorCallback)
    }

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
    ) {
        if (notReady(errorCallback) ||
            missing(fromAddress, "from_address", errorCallback) ||
            missing(username, "username", errorCallback) ||
            missing(password, "password", errorCallback) ||
            missing(server, "server", errorCallback)
        ) {
            return
        }
        if (outOfRange(port, 1, 65535, "port", errorCallback)) return
        json(
            "POST", "/test-smtp-configuration", emptyMap(),
            linkedMapOf(
                "from_address" to fromAddress,
                "username" to username,
                "password" to password,
                "server" to server,
                "port" to port,
            ),
            responseCallback, errorCallback, listOf(password, username),
        )
    }

    // -- internals -----------------------------------------------------------

    private fun notReady(errorCallback: (ErrorResponse?) -> Unit): Boolean {
        val problem = setupProblem ?: return false
        errorCallback(ErrorResponse.parseError(problem))
        return true
    }

    private fun missing(
        value: String?,
        name: String,
        errorCallback: (ErrorResponse?) -> Unit,
    ): Boolean {
        if (!value.isNullOrBlank()) return false
        errorCallback(ErrorResponse.parseError("$name is required"))
        return true
    }

    private fun outOfRange(
        value: Int,
        low: Int,
        high: Int,
        name: String,
        errorCallback: (ErrorResponse?) -> Unit,
    ): Boolean {
        if (value in low..high) return false
        errorCallback(ErrorResponse.parseError("$name must be $low to $high"))
        return true
    }

    private fun engagementBody(
        cFrom: String,
        replyPct: Int,
        importantPct: Int,
        clickPct: Int,
        errorCallback: (ErrorResponse?) -> Unit,
    ): LinkedHashMap<String, Any?>? {
        if (missing(cFrom, "c_from", errorCallback)) return null
        if (outOfRange(replyPct, 5, 45, "reply_pct", errorCallback)) return null
        if (outOfRange(importantPct, 5, 40, "important_pct", errorCallback)) return null
        if (outOfRange(clickPct, 0, 70, "click_pct", errorCallback)) return null
        if (clickPct > (importantPct * 1.75).toInt()) {
            errorCallback(ErrorResponse.parseError("click_pct must be at most important_pct x 1.75"))
            return null
        }
        return linkedMapOf(
            "c_from" to cFrom,
            "reply_pct" to replyPct,
            "important_pct" to importantPct,
            "click_pct" to clickPct,
        )
    }

    /**
     * Whether a body could be JSON at all. Gson's parser is lenient and would
     * read bare junk as a string, so the shape is checked first.
     */
    private fun looksLikeJson(body: String): Boolean {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return false
        val first = trimmed[0]
        if (first == '{' || first == '[' || first == '"' || first == '-' || first.isDigit()) return true
        return trimmed == "true" || trimmed == "false" || trimmed == "null"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    /** Replaces every form of a secret in a message with ***. */
    private fun redact(text: String?, secrets: List<String>): String {
        var value = text.orEmpty()
        for (secret in listOf(apiKey) + secrets) {
            if (secret.isNotEmpty()) value = value.replace(secret, "***")
        }
        return value
    }

    private fun url(path: String, query: Map<String, String>): String {
        if (query.isEmpty()) return "$root$path"
        val suffix = query.entries.joinToString("&") { (k, v) -> "${encode(k)}=${encode(v)}" }
        return "$root$path?$suffix"
    }

    private fun redacted(response: ErrorResponse?, secrets: List<String>): ErrorResponse? {
        response ?: return null
        response.errors = ArrayList(response.errors.map { redact(it, secrets) })
        return response
    }

    private fun send(
        method: String,
        path: String,
        query: Map<String, String>,
        body: Map<String, Any?>?,
        secrets: List<String>,
        errorCallback: (ErrorResponse?) -> Unit,
        onBody: (statusCode: Int, contentType: String, text: String) -> Unit,
    ) {
        val builder = Request.Builder()
            .url(url(path, query))
            .header("Accept", "application/json")
            .header("API-Key", apiKey)
        val payload = body?.let {
            gson.toJson(it).toRequestBody("application/json".toMediaTypeOrNull())
        }
        when (method) {
            "GET" -> builder.get()
            "DELETE" -> if (payload == null) builder.delete() else builder.delete(payload)
            else -> builder.method(method, payload ?: ByteArray(0).toRequestBody(null))
        }
        client.newCall(builder.build()).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val contentType = response.header("Content-Type")
                    ?: response.body?.contentType()?.toString().orEmpty()
                val text = response.body?.string().orEmpty()
                if (response.code in 300..399) {
                    errorCallback(
                        ErrorResponse(
                            errors = arrayListOf(
                                "ZeroBounce returned a redirect, which this client does not follow.",
                            ),
                            statusCode = response.code,
                        ),
                    )
                    return
                }
                if (response.code > 299) {
                    errorCallback(redacted(httpError(response.code, text), secrets))
                    return
                }
                onBody(response.code, contentType, text)
            }

            override fun onFailure(call: Call, e: IOException) {
                // Rebuilt rather than wrapped: the original holds the URL and headers.
                errorCallback(ErrorResponse(errors = arrayListOf(redact(e.message, secrets))))
            }
        })
    }

    private fun json(
        method: String,
        path: String,
        query: Map<String, String>,
        body: Map<String, Any?>?,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
        secrets: List<String> = emptyList(),
    ) {
        send(method, path, query, body, secrets, errorCallback) { statusCode, _, text ->
            if (statusCode == 204 || text.isBlank()) {
                responseCallback(null)
                return@send
            }
            // Gson parses leniently, so junk would come back as a string
            // instead of raising: reject it before it reaches the parser.
            if (!looksLikeJson(text)) {
                errorCallback(redacted(httpError(statusCode, text), secrets))
                return@send
            }
            try {
                // LIVE: not every endpoint answers with an object. /seeds is a
                // JSON array and /seed-count is a bare number.
                responseCallback(JsonParser.parseString(text))
            } catch (_: Throwable) {
                errorCallback(redacted(httpError(statusCode, text), secrets))
            }
        }
    }

    private fun csv(
        path: String,
        query: Map<String, String>,
        responseCallback: (csv: String) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        send("GET", path, query, null, emptyList(), errorCallback) { statusCode, contentType, text ->
            if (text.isBlank() || contentType.lowercase().contains("application/json")) {
                errorCallback(redacted(httpError(statusCode, text), emptyList()))
            } else {
                responseCallback(text)
            }
        }
    }
}

/** What ZeroBounce should send on your behalf, for zb_managed onboarding. */
data class ZBWarmupEmailContent(
    /** Any of transactional, newsletter, marketing. */
    val typesOfEmails: List<String> = emptyList(),
    val industry: String? = null,
    val companyName: String? = null,
    val website: String? = null,
)

/** When ZeroBounce should send, for zb_managed onboarding. */
data class ZBWarmupManagedSending(
    /** -11 to 14. */
    val timezoneOffset: Int? = null,
    /** YYYY-MM-DD. */
    val warmupStartDate: String? = null,
    val dailyLimit: Int? = null,
)

/** The optional arguments of [ZeroBounceWarmup.addSendingAccount]. */
data class ZBWarmupAddSendingAccountOptions(
    /** Required for self_managed: 0 Default, 1 Slow Pace, 2 Repair, 3 Boost, 4 Protect. */
    val engagementRuleId: Int? = null,
    val ignoreConflict: Boolean? = null,
    /** zb_managed only. */
    val emailContent: ZBWarmupEmailContent? = null,
    /** zb_managed only. */
    val managedSending: ZBWarmupManagedSending? = null,
)
