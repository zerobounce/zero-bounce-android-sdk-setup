package com.zerobounce.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.zerobounce.android.ZeroBounceSDK.apiKey
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit


/**
 * The ZeroBounce main class. All the requests are implemented here.
 */
object ZeroBounceSDK {

    private val sharedState = SdkState()
    private val threadState = ThreadLocal<SdkState>()

    private fun state(): SdkState = threadState.get() ?: sharedState

    internal fun <T> useState(state: SdkState, block: () -> T): T {
        val previous = threadState.get()
        threadState.set(state)
        try {
            return block()
        } finally {
            if (previous == null) threadState.remove() else threadState.set(previous)
        }
    }

    private var apiKey: String?
        get() = state().apiKey
        set(value) { state().apiKey = value }

    internal var client: OkHttpClient
        get() = state().client
        set(value) { state().client = value }

    /**
     * Test hook. When set, [initialize] installs it on the OkHttp client so unit tests can
     * answer requests without the network. Production leaves this null.
     */
    internal var transportInterceptor: Interceptor?
        get() = state().transportInterceptor
        set(value) { state().transportInterceptor = value }

    internal var apiBaseUrl: String
        get() = state().apiBaseUrl
        set(value) { state().apiBaseUrl = value }

    internal var bulkApiBaseUrl: String
        get() = state().bulkApiBaseUrl
        set(value) { state().bulkApiBaseUrl = value }

    internal var listEvaluatorBaseUrl: String
        get() = state().listEvaluatorBaseUrl
        set(value) { state().listEvaluatorBaseUrl = value }

    internal var inboxPlacementBaseUrl: String
        get() = state().inboxPlacementBaseUrl
        set(value) { state().inboxPlacementBaseUrl = value }

    internal var bulkApiScoringBaseUrl: String
        get() = state().bulkApiScoringBaseUrl
        set(value) { state().bulkApiScoringBaseUrl = value }

    internal var bulkApiRootUrl: String
        get() = state().bulkApiRootUrl
        set(value) { state().bulkApiRootUrl = value }

    private val priority3Api = Priority3Api()

    private const val READ_EXTERNAL_STORAGE = Manifest.permission.READ_EXTERNAL_STORAGE
    private const val WRITE_EXTERNAL_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE
    private val STORAGE_PERMISSIONS = arrayOf(READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE)
    private var logEnabled = false

    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .registerTypeAdapter(Date::class.java, GsonDateDeserializer())
        .create()

    private inline fun <reified T : JSONConvertable> String.toObject(): T? =
        gson.fromJson(this, T::class.java)

    /**
     * Initializes the SDK.
     *
     * @param apiKey the API key
     * @param apiBaseUrl the API base URL
     */
    fun initialize(apiKey: String, apiBaseUrl: String? = null) {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
        transportInterceptor?.let { builder.addInterceptor(it) }
        client = builder.build()
        this.apiKey = apiKey
        apiBaseUrl?.let {
            require(isAllowedApiBaseUrl(it)) { "apiBaseUrl must be an https:// URL" }
            this.apiBaseUrl = it
        }
    }

    internal fun isAllowedApiBaseUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.startsWith("https://")) return true
        if (!lower.startsWith("http://")) return false
        val host = url.toHttpUrlOrNull()?.host ?: return false
        return host == "localhost" || host == "127.0.0.1" || host == "::1"
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun urlWithQuery(base: String, params: List<Pair<String, String>>): String {
        if (params.isEmpty()) return base
        val query = params.joinToString("&") { (name, value) -> "${enc(name)}=${enc(value)}" }
        return "$base?$query"
    }

    private fun redactedBody(body: String?): String {
        if (body.isNullOrEmpty()) return ""
        if (looksLikeHtml(body) || body.length > 500) return "omitted (${body.length} chars)"
        return body
    }

    private inline fun acceptJson(
        statusCode: Int,
        body: String?,
        errorCallback: (ErrorResponse?) -> Unit,
        parse: (String) -> Unit,
    ) {
        if (!jsonIsUsable(statusCode, body)) {
            errorCallback(httpError(statusCode, body))
            return
        }
        try {
            parse(body!!.trim())
        } catch (_: Throwable) {
            errorCallback(httpError(statusCode, body))
        }
    }

    private fun accountLevelBatchError(response: ZBValidateBatchResponse?): String? {
        val entry = response?.errors?.firstOrNull { it["email_address"]?.toString() == "all" }
            ?: return null
        return entry["error"]?.toString() ?: "Invalid API Key or your account ran out of credits"
    }

    internal fun safeFileName(raw: String?): String? =
        raw?.substringAfterLast('/')?.takeIf { it.isNotBlank() && it != "." && it != ".." }

    /**
     * Validates the given [email].
     *
     * @param email the email address you want to validate
     * @param ipAddress the IP Address the email signed up from (Can be blank)
     * @param responseCallback the response callback
     * @param errorCallback the error callback
     */
    fun validate(
        email: String,
        ipAddress: String?,
        responseCallback: (response: ZBValidateResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit
    ) {
        validate(email, ipAddress, null, responseCallback, errorCallback)
    }

    /**
     * Validates the given [email] with optional v2 query parameters.
     */
    fun validate(
        email: String,
        ipAddress: String?,
        options: ZBValidateOptions?,
        responseCallback: (response: ZBValidateResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit
    ) {
        if (invalidApiKey(errorCallback)) return
        if (options != null && !checkValidateTimeout(options.timeout, errorCallback)) return
        if (!checkCallbackUrl(options, errorCallback)) return

        val ipAddressPart = ipAddress ?: ""
        if (options?.usePost == true) {
            val key = apiKey ?: return
            val fields = mutableMapOf(
                "api_key" to key,
                "email" to email,
                "ip_address" to ipAddressPart,
            )
            options.timeout?.let { fields["timeout"] = it.toString() }
            options.activityData?.let { fields["activity_data"] = if (it) "true" else "false" }
            options.verifyPlus?.let { fields["verify_plus"] = if (it) "true" else "false" }
            options.domainInfo?.let { fields["domain_info"] = if (it) "true" else "false" }
            options.greylistProcessing?.let { fields["greylist_processing"] = if (it) "true" else "false" }
            options.callbackUrl?.let { fields["callback_url"] = it }
            requestFormPost(
                url = "$apiBaseUrl/validate",
                formFields = fields,
                responseCallback = responseCallback,
                errorCallback = errorCallback
            )
            return
        }
        val params = mutableListOf(
            "api_key" to (apiKey ?: ""),
            "email" to email,
            "ip_address" to ipAddressPart,
        )
        if (options != null) {
            options.timeout?.let { params += "timeout" to it.toString() }
            options.activityData?.let { params += "activity_data" to if (it) "true" else "false" }
            options.verifyPlus?.let { params += "verify_plus" to if (it) "true" else "false" }
            options.domainInfo?.let { params += "domain_info" to if (it) "true" else "false" }
            options.greylistProcessing?.let { params += "greylist_processing" to if (it) "true" else "false" }
            options.callbackUrl?.let { params += "callback_url" to it }
        }
        request(
            url = urlWithQuery("$apiBaseUrl/validate", params),
            body = null,
            responseCallback = responseCallback,
            errorCallback = errorCallback
        )
    }

    /**
     * Validates a batch of emails.
     *
     * @param emails the email addresses you want to validate
     * @param responseCallback the response callback
     * @param errorCallback the error callback
     */
    fun validateBatch(
        emails: List<ZBValidateBatchData>,
        responseCallback: (response: ZBValidateBatchResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit
    ) {
        validateBatch(emails, null, responseCallback, errorCallback)
    }

    /**
     * Validates a batch of emails with optional v2 body parameters.
     */
    fun validateBatch(
        emails: List<ZBValidateBatchData>,
        options: ZBValidateOptions?,
        responseCallback: (response: ZBValidateBatchResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit
    ) {
        if (invalidApiKey(errorCallback)) return
        val apiKey = apiKey ?: return
        if (options != null && !checkValidateBatchTimeout(options.timeout, errorCallback)) return

        val body = mutableMapOf<String, Any>(
            "api_key" to apiKey,
            "email_batch" to emails
        )
        // LIVE: validatebatch takes timeout as a JSON *string*. Sent as a
        // number the API rejects the whole batch with the account-level
        // "Invalid API Key or your account ran out of credits".
        options?.timeout?.let { body["timeout"] = it.toString() }
        options?.activityData?.let { body["activity_data"] = it }
        options?.verifyPlus?.let { body["verify_plus"] = it }

        request<ZBValidateBatchResponse>(
            url = "$apiBaseUrl/validatebatch",
            body = body,
            responseCallback = { parsed ->
                val accountMessage = accountLevelBatchError(parsed)
                if (accountMessage != null) {
                    errorCallback(ErrorResponse(errors = arrayListOf(accountMessage)))
                } else {
                    responseCallback(parsed)
                }
            },
            errorCallback = errorCallback
        )
    }

    /**
     * The callback is where the greylist retry posts its result, so it is
     * meaningless — and rejected by the API — without the retry itself.
     * Checked before any request is sent.
     */
    private fun checkCallbackUrl(
        options: ZBValidateOptions?,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ): Boolean {
        val callbackUrl = options?.callbackUrl ?: return true
        if (callbackUrl.isBlank()) {
            errorCallback(ErrorResponse.parseError("Empty parameter: callback_url"))
            return false
        }
        if (options.greylistProcessing != true) {
            errorCallback(
                ErrorResponse.parseError(
                    "Invalid parameter: callback_url requires greylist_processing to be true"
                )
            )
            return false
        }
        return true
    }

    private fun checkValidateTimeout(
        timeout: Int?,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit
    ): Boolean {
        if (timeout == null || timeout in 3..60) return true
        errorCallback(
            ErrorResponse.parseError("Invalid parameter: timeout (must be between 3 and 60 seconds)")
        )
        return false
    }

    private fun checkValidateBatchTimeout(
        timeout: Int?,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit
    ): Boolean {
        if (timeout == null || timeout in 10..120) return true
        errorCallback(
            ErrorResponse.parseError("Invalid parameter: timeout (must be between 10 and 120 seconds)")
        )
        return false
    }

    /**
     * Find the email and other domain formats based on a given [domain] name.
     *
     * @param domain the email domain for which to find the email format
     * @param firstName the first name of the person whose email format is being searched; optional
     * @param middleName the middle name of the person whose email format is being searched;
     * optional
     * @param lastName the last name of the person whose email format is being searched; optional
     * @param responseCallback the response callback
     * @param errorCallback the error callback
     */
    @Deprecated(
        "The 'guessFormat' method has been split into two specific functions. " +
                "If you are finding a person's email, use 'findEmail()'. " +
                "If you are only determining the domain's email pattern, use 'findDomain()'.",
        level = DeprecationLevel.WARNING
    )
    fun guessFormat(
        domain: String,
        firstName: String? = null,
        middleName: String? = null,
        lastName: String? = null,
        responseCallback: (response: ZBEmailFinderResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit
    ) {
        if (invalidApiKey(errorCallback)) return

        val params = mutableListOf("api_key" to (apiKey ?: ""), "domain" to domain)
        firstName?.let { params += "first_name" to it }
        middleName?.let { params += "middle_name" to it }
        lastName?.let { params += "last_name" to it }
        val url = urlWithQuery("$apiBaseUrl/guessformat", params)

        request(
            url = url,
            body = null,
            responseCallback = responseCallback,
            errorCallback = errorCallback
        )
    }

    /**
     * Finds the email based on a given [firstName], [domain] or [companyName].
     *
     * **Note**: At least one of [domain] or [companyName] must be provided.
     *
     * @param firstName the first name of the person whose email format is being searched
     * @param domain the email domain for which to find the email format
     * @param companyName the company name for which to find the email format
     * @param middleName the middle name of the person whose email format is being searched;
     * optional
     * @param lastName the last name of the person whose email format is being searched; optional
     * @param responseCallback the response callback
     * @param errorCallback the error callback
     */
    fun findEmail(
        firstName: String,
        domain: String? = null,
        companyName: String? = null,
        middleName: String? = null,
        lastName: String? = null,
        responseCallback: (response: ZBFindEmailResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit
    ) {
        if (invalidApiKey(errorCallback)) return

        // Validate that at least one of companyName or domain is provided.
        if (companyName.isNullOrBlank() && domain.isNullOrBlank()) {
            errorCallback(
                ErrorResponse.parseError("Either companyName or domain must be provided.")
            )
            return
        }

        val params = mutableListOf("api_key" to (apiKey ?: ""), "first_name" to firstName)
        domain?.let { params += "domain" to it }
        companyName?.let { params += "company_name" to it }
        middleName?.let { params += "middle_name" to it }
        lastName?.let { params += "last_name" to it }
        val url = urlWithQuery("$apiBaseUrl/guessformat", params)

        request(
            url = url,
            body = null,
            responseCallback = responseCallback,
            errorCallback = errorCallback
        )
    }

    /**
     * Find other domain formats based on a given [domain] or [companyName].
     *
     * **Note**: At least one of [domain] or [companyName] must be provided.
     *
     * @param domain the email domain for which to find the email format
     * @param companyName the company name for which to find the email format
     * @param responseCallback the response callback
     * @param errorCallback the error callback
     */
    fun findDomain(
        domain: String? = null,
        companyName: String? = null,
        responseCallback: (response: ZBFindDomainResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit
    ) {
        if (invalidApiKey(errorCallback)) return

        // Validate that at least one of companyName or domain is provided.
        if (companyName.isNullOrBlank() && domain.isNullOrBlank()) {
            errorCallback(
                ErrorResponse.parseError("Either companyName or domain must be provided.")
            )
            return
        }

        val params = mutableListOf("api_key" to (apiKey ?: ""))
        domain?.let { params += "domain" to it }
        companyName?.let { params += "company_name" to it }
        val url = urlWithQuery("$apiBaseUrl/guessformat", params)

        request(
            url = url,
            body = null,
            responseCallback = responseCallback,
            errorCallback = errorCallback
        )
    }

    /**
     * Returns the API usage between the given dates.
     *
     * @param startDate the start date of when you want to view API usage
     * @param endDate the end date of when you want to view API usage
     * @param responseCallback the response callback
     * @param errorCallback the error callback
     */
    fun getApiUsage(
        startDate: Date,
        endDate: Date,
        responseCallback: (response: ZBGetApiUsageResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit
    ) {
        if (invalidApiKey(errorCallback)) return

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        val sDate = dateFormat.format(startDate)
        val eDate = dateFormat.format(endDate)

        request(
            url = urlWithQuery(
                "$apiBaseUrl/getapiusage",
                listOf("api_key" to (apiKey ?: ""), "start_date" to sDate, "end_date" to eDate),
            ),
            body = null,
            responseCallback = responseCallback,
            errorCallback = errorCallback
        )
    }

    /**
     * This API will tell you how many credits you have left on your account.
     * It's simple, fast and easy to use.
     *
     * @param responseCallback the response callback
     * @param errorCallback the error callback
     */
    fun getCredits(
        responseCallback: (response: ZBCreditsResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit
    ) {
        if (invalidApiKey(errorCallback)) return

        request(
            url = urlWithQuery("$apiBaseUrl/getcredits", listOf("api_key" to (apiKey ?: ""))),
            body = null,
            responseCallback = responseCallback,
            errorCallback = errorCallback
        )
    }


    /**
     * The sendFile API allows user to send a file for bulk email validation.
     *
     * @param context the app context
     * @param file the file to send
     * @param emailAddressColumnIndex the column index of the email address in the file.
     * Index starts from 1.
     * @param firstNameColumnIndex the column index of the first name column
     * @param lastNameColumnIndex the column index of the last name column
     * @param genderColumnIndex the column index of the gender column
     * @param ipAddressColumnIndex the IP Address the email signed up from
     * @param returnUrl the URL that will be used to call back when the validation is completed
     * @param hasHeaderRow if the first row from the submitted file is a header row
     * @param removeDuplicate if you want the system to remove duplicate emails (true or false,
     * default on the server is true)
     * @param allowPhase2 if not null, sent as `allow_phase_2` (validation bulk only). When true,
     * catch-all (phase 2) validation may run after phase 1 if Verify+ is enabled and the file has
     * more than 10 catch-all emails per API rules; default on the server is false.
     * @param responseCallback the response callback
     * @param errorCallback the error callback
     */
    @Throws(ZBException::class)
    fun sendFile(
        context: Context,
        file: File,
        emailAddressColumnIndex: Int,
        firstNameColumnIndex: Int? = null,
        lastNameColumnIndex: Int? = null,
        genderColumnIndex: Int? = null,
        ipAddressColumnIndex: Int? = null,
        returnUrl: String? = null,
        hasHeaderRow: Boolean = false,
        removeDuplicate: Boolean? = null,
        allowPhase2: Boolean? = null,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        sendFileInternal(
            scoring = false,
            context = context,
            file = file,
            emailAddressColumnIndex = emailAddressColumnIndex,
            firstNameColumnIndex = firstNameColumnIndex,
            lastNameColumnIndex = lastNameColumnIndex,
            genderColumnIndex = genderColumnIndex,
            ipAddressColumnIndex = ipAddressColumnIndex,
            returnUrl = returnUrl,
            hasHeaderRow = hasHeaderRow,
            removeDuplicate = removeDuplicate,
            allowPhase2 = allowPhase2,
            responseCallback = responseCallback,
            errorCallback = errorCallback,
        )
    }

    /**
     * The sendFile API allows user to send a file from a stream (e.g. in-memory CSV or upload stream).
     * The stream is not closed by the SDK.
     */
    @Throws(ZBException::class)
    fun sendFile(
        inputStream: InputStream,
        fileName: String,
        emailAddressColumnIndex: Int,
        firstNameColumnIndex: Int? = null,
        lastNameColumnIndex: Int? = null,
        genderColumnIndex: Int? = null,
        ipAddressColumnIndex: Int? = null,
        returnUrl: String? = null,
        hasHeaderRow: Boolean = false,
        removeDuplicate: Boolean? = null,
        allowPhase2: Boolean? = null,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        sendFileInternalStream(
            scoring = false,
            inputStream = inputStream,
            fileName = fileName,
            emailAddressColumnIndex = emailAddressColumnIndex,
            firstNameColumnIndex = firstNameColumnIndex,
            lastNameColumnIndex = lastNameColumnIndex,
            genderColumnIndex = genderColumnIndex,
            ipAddressColumnIndex = ipAddressColumnIndex,
            returnUrl = returnUrl,
            hasHeaderRow = hasHeaderRow,
            removeDuplicate = removeDuplicate,
            allowPhase2 = allowPhase2,
            responseCallback = responseCallback,
            errorCallback = errorCallback,
        )
    }

    /**
     * The scoringSendFile API allows user to send a file for bulk email validation.
     *
     * @param context the app context
     * @param file the file to send
     * @param emailAddressColumnIndex the column index of the email address in the file.
     * Index starts from 1.
     * @param returnUrl the URL that will be used to call back when the validation is completed
     * @param hasHeaderRow if the first row from the submitted file is a header row
     * @param removeDuplicate if you want the system to remove duplicate emails (true or false,
     * default is true)
     * @param responseCallback the response callback
     * @param errorCallback the error callback
     */
    @Throws(ZBException::class)
    fun scoringSendFile(
        context: Context,
        file: File,
        emailAddressColumnIndex: Int,
        returnUrl: String? = null,
        hasHeaderRow: Boolean = false,
        removeDuplicate: Boolean? = null,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        sendFileInternal(
            context = context,
            scoring = true,
            file = file,
            emailAddressColumnIndex = emailAddressColumnIndex,
            returnUrl = returnUrl,
            hasHeaderRow = hasHeaderRow,
            removeDuplicate = removeDuplicate,
            responseCallback = responseCallback, errorCallback = errorCallback,
        )
    }

    /**
     * The scoringSendFile API allows user to send a file from a stream.
     * The stream is not closed by the SDK.
     */
    @Throws(ZBException::class)
    fun scoringSendFile(
        inputStream: InputStream,
        fileName: String,
        emailAddressColumnIndex: Int,
        returnUrl: String? = null,
        hasHeaderRow: Boolean = false,
        removeDuplicate: Boolean? = null,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        sendFileInternalStream(
            scoring = true,
            inputStream = inputStream,
            fileName = fileName,
            emailAddressColumnIndex = emailAddressColumnIndex,
            returnUrl = returnUrl,
            hasHeaderRow = hasHeaderRow,
            removeDuplicate = removeDuplicate,
            responseCallback = responseCallback,
            errorCallback = errorCallback,
        )
    }

    /**
     * The sendFile API allows user to send a file for bulk email validation. This method
     * implements the actual request logic.
     *
     * @param context the app context
     * @param scoring *true* if the AI scoring should be used, or *false* otherwise
     * @param file the file to send
     * @param emailAddressColumnIndex the column index of the email address in the file.
     * Index starts from 1.
     * @param returnUrl the URL that will be used to call back when the validation is completed
     * @param firstNameColumnIndex the column index of the first name column
     * @param lastNameColumnIndex the column index of the last name column
     * @param genderColumnIndex the column index of the gender column
     * @param ipAddressColumnIndex the IP Address the email signed up from
     * @param hasHeaderRow if the first row from the submitted file is a header row
     * @param removeDuplicate if you want the system to remove duplicate emails (true or false,
     * default on the server is true)
     * @param allowPhase2 validation bulk only; multipart `allow_phase_2` when non-null
     * @param responseCallback the response callback
     * @param errorCallback the error callback
     */
    @Throws(ZBException::class)
    private fun sendFileInternal(
        context: Context,
        scoring: Boolean,
        file: File,
        emailAddressColumnIndex: Int,
        returnUrl: String? = null,
        firstNameColumnIndex: Int? = null,
        lastNameColumnIndex: Int? = null,
        genderColumnIndex: Int? = null,
        ipAddressColumnIndex: Int? = null,
        hasHeaderRow: Boolean = false,
        removeDuplicate: Boolean? = null,
        allowPhase2: Boolean? = null,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return

        if (!hasPermissions(context, STORAGE_PERMISSIONS)) {
            val errorResponse = ErrorResponse.parseError(
                "Cannot send file, permissions $READ_EXTERNAL_STORAGE and $WRITE_EXTERNAL_STORAGE are not granted"
            )
            errorCallback(errorResponse)
            return
        }

        if (!file.exists()) {
            val errorResponse = ErrorResponse.parseError("File does not exist")
            errorCallback(errorResponse)
            return
        }

        if (emailAddressColumnIndex < 1) {
            throw ZBException("Index for emailAddressColumnIndex must start from 1.")
        }

        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                file.name,
                file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            )
            .addFormDataPart("api_key", "$apiKey")
            .addFormDataPart("email_address_column", "$emailAddressColumnIndex")

        if (returnUrl != null) {
            builder.addFormDataPart("return_url", "$returnUrl")
        }
        if (firstNameColumnIndex != null) {
            builder.addFormDataPart("first_name_column", "$firstNameColumnIndex")
        }
        if (lastNameColumnIndex != null) {
            builder.addFormDataPart("last_name_column", "$lastNameColumnIndex")
        }
        if (genderColumnIndex != null) {
            builder.addFormDataPart("gender_column", "$genderColumnIndex")
        }
        if (ipAddressColumnIndex != null) {
            builder.addFormDataPart("ip_address_column", "$ipAddressColumnIndex")
        }

        builder.addFormDataPart("has_header_row", "$hasHeaderRow")

        if (removeDuplicate != null) {
            builder.addFormDataPart("remove_duplicate", "$removeDuplicate")
        }
        if (!scoring && allowPhase2 != null) {
            builder.addFormDataPart("allow_phase_2", "$allowPhase2")
        }

        val baseUrl = if (scoring) bulkApiScoringBaseUrl else bulkApiBaseUrl
        val requestBuilder = Request.Builder()
            .url("$baseUrl/sendfile")
            .post(builder.build())

        val request = requestBuilder.build()

        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string()
                if (logEnabled) {
                    Log.d("ZeroBounceSDK", "response: ${redactedBody(json)}")
                }

                acceptJson(response.code, json, errorCallback) {
                    responseCallback(it.toObject<ZBSendFileResponse>())
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                if (logEnabled) {
                    Log.d(
                        "ZeroBounceSDK",
                        "sendFile error=$e}"
                    )
                }
                errorCallback(ErrorResponse.parseError(e.message))
            }
        })

    }

    @Throws(ZBException::class)
    private fun sendFileInternalStream(
        scoring: Boolean,
        inputStream: InputStream,
        fileName: String,
        emailAddressColumnIndex: Int,
        returnUrl: String? = null,
        firstNameColumnIndex: Int? = null,
        lastNameColumnIndex: Int? = null,
        genderColumnIndex: Int? = null,
        ipAddressColumnIndex: Int? = null,
        hasHeaderRow: Boolean = false,
        removeDuplicate: Boolean? = null,
        allowPhase2: Boolean? = null,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        if (emailAddressColumnIndex < 1) {
            throw ZBException("Index for emailAddressColumnIndex must start from 1.")
        }

        val fileBody = inputStream.readBytes().toRequestBody("text/csv".toMediaTypeOrNull())
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, fileBody)
            .addFormDataPart("api_key", "$apiKey")
            .addFormDataPart("email_address_column", "$emailAddressColumnIndex")

        if (returnUrl != null) builder.addFormDataPart("return_url", returnUrl)
        if (firstNameColumnIndex != null) builder.addFormDataPart("first_name_column", "$firstNameColumnIndex")
        if (lastNameColumnIndex != null) builder.addFormDataPart("last_name_column", "$lastNameColumnIndex")
        if (genderColumnIndex != null) builder.addFormDataPart("gender_column", "$genderColumnIndex")
        if (ipAddressColumnIndex != null) builder.addFormDataPart("ip_address_column", "$ipAddressColumnIndex")
        builder.addFormDataPart("has_header_row", "$hasHeaderRow")
        if (removeDuplicate != null) builder.addFormDataPart("remove_duplicate", "$removeDuplicate")
        if (!scoring && allowPhase2 != null) {
            builder.addFormDataPart("allow_phase_2", "$allowPhase2")
        }

        val baseUrl = if (scoring) bulkApiScoringBaseUrl else bulkApiBaseUrl
        val request = Request.Builder()
            .url("$baseUrl/sendfile")
            .post(builder.build())
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string()
                if (logEnabled) Log.d("ZeroBounceSDK", "response: ${redactedBody(json)}")
                acceptJson(response.code, json, errorCallback) {
                    responseCallback(it.toObject<ZBSendFileResponse>())
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                if (logEnabled) Log.d("ZeroBounceSDK", "sendFile error=$e")
                errorCallback(ErrorResponse.parseError(e.message))
            }
        })
    }

    /**
     * Returns the status of a file submitted for email validation.
     *
     * @param fileId the returned file ID when calling sendFile API
     * @param responseCallback the response callback
     * @param errorCallback the error callback
     */
    fun fileStatus(
        fileId: String,
        responseCallback: (response: ZBFileStatusResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit
    ) {
        fileStatusInternal(
            scoring = false,
            fileId = fileId,
            responseCallback = responseCallback,
            errorCallback = errorCallback
        )
    }

    /**
     * Returns the status of a file submitted for email validation using the AI Scoring request.
     *
     * @param fileId the returned file ID when calling scoringSendFile API
     * @param responseCallback the response callback
     * @param errorCallback the error callback
     */
    fun scoringFileStatus(
        fileId: String,
        responseCallback: (response: ZBFileStatusResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit
    ) {
        fileStatusInternal(
            scoring = true,
            fileId = fileId,
            responseCallback = responseCallback,
            errorCallback = errorCallback
        )
    }

    /**
     * Returns the status of a file submitted for email validation. This method implements the
     * actual request logic.
     *
     * @param scoring *true* if the AI scoring should be used, or *false* otherwise
     * @param fileId the returned file ID when calling either the sendFile or scoringSendFile
     * APIs
     * @param responseCallback the response callback
     * @param errorCallback the error callback
     */
    private fun fileStatusInternal(
        scoring: Boolean,
        fileId: String,
        responseCallback: (response: ZBFileStatusResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit
    ) {
        if (invalidApiKey(errorCallback)) return

        val baseUrl = if (scoring) bulkApiScoringBaseUrl else bulkApiBaseUrl
        request(
            url = urlWithQuery(
                "$baseUrl/filestatus",
                listOf("api_key" to (apiKey ?: ""), "file_id" to fileId),
            ),
            body = null,
            responseCallback = responseCallback,
            errorCallback = errorCallback
        )
    }

    /**
     * The getFile API allows users to get the validation results file for the file been
     * submitted using sendFile API.
     *
     * @param context the app context
     * @param fileId the returned file ID when calling sendFile API
     * @param responseCallback the response callback
     * @param errorCallback the error callback
     * @param downloadType optional `download_type` query value (phase_1, phase_2, combined)
     * @param activityData if not null, sent as `activity_data` (validation bulk only)
     */
    fun getFile(
        context: Context,
        fileId: String,
        responseCallback: (response: ZBGetFileResponse) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
        downloadType: ZBDownloadType? = null,
        activityData: Boolean? = null,
    ) {
        getFileInternal(
            context = context,
            scoring = false,
            fileId = fileId,
            downloadType = downloadType,
            activityData = activityData,
            responseCallback = responseCallback,
            errorCallback = errorCallback
        )
    }

    /**
     * The scoringGetFile API allows users to get the validation results file for the file been
     * submitted using scoringSendFile API.
     *
     * @param context the app context
     * @param fileId the returned file ID when calling scoringSendFile API
     * @param responseCallback the response callback
     * @param errorCallback the error callback
     * @param downloadType optional `download_type` query value (phase_1, phase_2, combined)
     */
    fun scoringGetFile(
        context: Context,
        fileId: String,
        responseCallback: (response: ZBGetFileResponse) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
        downloadType: ZBDownloadType? = null,
    ) {
        getFileInternal(
            context = context,
            scoring = true,
            fileId = fileId,
            downloadType = downloadType,
            activityData = null,
            responseCallback = responseCallback,
            errorCallback = errorCallback
        )
    }

    /**
     * The getFile API allows users to get the validation results file for the file that has
     * been submitted. This method implements the actual request logic.
     *
     * @param context the app context
     * @param scoring *true* if the AI scoring should be used, or *false* otherwise
     * @param fileId the returned file ID when calling either the sendFile or scoringSendFile
     * APIs
     * @param responseCallback the response callback
     * @param errorCallback the error callback
     */
    private fun getFileInternal(
        context: Context,
        scoring: Boolean,
        fileId: String,
        downloadType: ZBDownloadType? = null,
        activityData: Boolean? = null,
        responseCallback: (response: ZBGetFileResponse) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit
    ) {
        if (invalidApiKey(errorCallback)) return

        val baseUrl = if (scoring) bulkApiScoringBaseUrl else bulkApiBaseUrl
        val params = mutableListOf("api_key" to (apiKey ?: ""), "file_id" to fileId)
        downloadType?.let { params += "download_type" to it.apiValue }
        if (!scoring && activityData != null) {
            params += "activity_data" to activityData.toString()
        }
        val requestBuilder = Request.Builder()
            .url(urlWithQuery("$baseUrl/getfile", params))

        val request = requestBuilder.build()

        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val bytes = it.body.bytes()
                    val text = bytes.toString(Charsets.UTF_8)
                    val contentType = it.body.contentType()?.toString()
                        ?: it.header("Content-Type").orEmpty()
                    if (downloadRejected(it.code, contentType, bytes)) {
                        errorCallback(httpError(it.code, text))
                        return
                    }
                    val fileName =
                        it.headers["content-disposition"]?.substringAfter("filename=")
                            ?.substringBefore(";")
                    val name = safeFileName(fileName) ?: "$fileId.csv".substringAfterLast('/')
                    val file = File(context.getExternalFilesDir(null), name)
                    try {
                        FileOutputStream(file.path).use { fos -> fos.write(bytes) }
                        responseCallback(ZBGetFileResponse(file.path))
                    } catch (e: FileNotFoundException) {
                        errorCallback(ErrorResponse.parseError(e.message))
                    } catch (e: IOException) {
                        errorCallback(ErrorResponse.parseError(e.message))
                    }
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                errorCallback(ErrorResponse.parseError(e.message))
            }
        })
    }

    /**
     * Delete a file.
     *
     * @param fileId the returned file ID when calling sendFile API
     * @param successCallback the response callback
     * @param errorCallback the error callback
     */
    fun deleteFile(
        fileId: String,
        successCallback: (response: ZBDeleteFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit
    ) {
        deleteFileInternal(
            scoring = false,
            fileId = fileId,
            successCallback = successCallback,
            errorCallback = errorCallback
        )
    }

    /**
     * Delete a file submitted using the scoring API.
     *
     * @param fileId the returned file ID when calling sendFile API
     * @param successCallback the response callback
     * @param errorCallback the error callback
     */
    fun scoringDeleteFile(
        fileId: String,
        successCallback: (response: ZBDeleteFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit
    ) {
        deleteFileInternal(
            scoring = true,
            fileId = fileId,
            successCallback = successCallback,
            errorCallback = errorCallback
        )
    }

    /**
     * Delete a file. This method implements the actual request logic.
     *
     * @param fileId the returned file ID when calling sendFile API
     * @param scoring *true* if the AI scoring should be used, or *false* otherwise
     * @param successCallback the response callback
     * @param errorCallback the error callback
     */
    private fun deleteFileInternal(
        scoring: Boolean,
        fileId: String,
        successCallback: (response: ZBDeleteFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit
    ) {
        if (invalidApiKey(errorCallback)) return

        val baseUrl = if (scoring) bulkApiScoringBaseUrl else bulkApiBaseUrl
        request(
            url = urlWithQuery(
                "$baseUrl/deletefile",
                listOf("api_key" to (apiKey ?: ""), "file_id" to fileId),
            ),
            body = null,
            responseCallback = successCallback,
            errorCallback = errorCallback
        )
    }

    /**
     * The request returns data regarding opens, clicks, forwards and unsubscribes that have
     * taken place in the past 30, 90, 180 or 365 days.
     *
     * @param email the email address
     * @param responseCallback the response callback
     * @param errorCallback the error callback
     */
    fun getActivityData(
        email: String,
        responseCallback: (response: ZBActivityDataResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit
    ) {
        if (invalidApiKey(errorCallback)) return

        request(
            url = urlWithQuery(
                "$apiBaseUrl/activity",
                listOf("api_key" to (apiKey ?: ""), "email" to email),
            ),
            body = null,
            responseCallback = responseCallback,
            errorCallback = errorCallback
        )
    }

    // Priority 3 — bulk email finder, domain search, list evaluator, filters, S3

    fun sendEmailFinderFile(
        file: File,
        domainColumn: Int,
        options: ZBEmailFinderSendFileOptions? = null,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        priority3Api.sendEmailFinderFile(file, domainColumn, options, responseCallback, errorCallback)
    }

    fun sendEmailFinderFileStream(
        inputStream: InputStream,
        fileName: String,
        domainColumn: Int,
        options: ZBEmailFinderSendFileOptions? = null,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        priority3Api.sendEmailFinderFileStream(
            inputStream,
            fileName,
            domainColumn,
            options,
            responseCallback,
            errorCallback,
        )
    }

    fun emailFinderFileStatus(
        fileId: String,
        responseCallback: (response: ZBFileStatusResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        priority3Api.bulkRootFileStatus(priority3EmailFinderBaseUrl(), fileId, responseCallback, errorCallback)
    }

    fun emailFinderGetFile(
        fileId: String,
        downloadPath: String,
        responseCallback: (response: ZBGetFileResponse) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        priority3Api.bulkRootGetFile(priority3EmailFinderBaseUrl(), fileId, downloadPath, responseCallback, errorCallback)
    }

    fun emailFinderDeleteFile(
        fileId: String,
        responseCallback: (response: ZBDeleteFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        priority3Api.bulkRootDeleteFile(priority3EmailFinderBaseUrl(), fileId, responseCallback, errorCallback)
    }

    fun sendDomainSearchFile(
        file: File,
        domainColumn: Int,
        options: ZBDomainSearchSendFileOptions? = null,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        priority3Api.sendDomainSearchFile(file, domainColumn, options, responseCallback, errorCallback)
    }

    fun sendDomainSearchFileStream(
        inputStream: InputStream,
        fileName: String,
        domainColumn: Int,
        options: ZBDomainSearchSendFileOptions? = null,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        priority3Api.sendDomainSearchFileStream(
            inputStream,
            fileName,
            domainColumn,
            options,
            responseCallback,
            errorCallback,
        )
    }

    fun domainSearchFileStatus(
        fileId: String,
        responseCallback: (response: ZBFileStatusResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        priority3Api.bulkRootFileStatus(priority3DomainSearchBaseUrl(), fileId, responseCallback, errorCallback)
    }

    fun domainSearchGetFile(
        fileId: String,
        downloadPath: String,
        responseCallback: (response: ZBGetFileResponse) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        priority3Api.bulkRootGetFile(priority3DomainSearchBaseUrl(), fileId, downloadPath, responseCallback, errorCallback)
    }

    fun domainSearchDeleteFile(
        fileId: String,
        responseCallback: (response: ZBDeleteFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        priority3Api.bulkRootDeleteFile(priority3DomainSearchBaseUrl(), fileId, responseCallback, errorCallback)
    }

    fun sendListEvaluatorFile(
        file: File,
        emailAddressColumn: Int,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        priority3Api.sendListEvaluatorFile(file, emailAddressColumn, responseCallback, errorCallback)
    }

    fun sendListEvaluatorFileStream(
        inputStream: InputStream,
        fileName: String,
        emailAddressColumn: Int,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        priority3Api.sendListEvaluatorFileStream(
            inputStream,
            fileName,
            emailAddressColumn,
            responseCallback,
            errorCallback,
        )
    }

    fun getListEvaluatorResult(
        fileId: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        priority3Api.getListEvaluatorResult(fileId, responseCallback, errorCallback)
    }

    /**
     * Submits a finished evaluation for validation. POST /v2/send-evaluated-file/
     *
     * LIVE: this takes the list evaluator's file_id as a form field, not the
     * file upload the docs describe.
     */
    fun sendEvaluatedFile(
        fileId: String,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        priority3Api.sendEvaluatedFile(fileId, responseCallback, errorCallback)
    }

    fun addFilter(
        rule: String,
        target: String,
        value: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        priority3Api.addFilter(rule, target, value, responseCallback, errorCallback)
    }

    fun listFilters(
        usePost: Boolean,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        priority3Api.listFilters(usePost, responseCallback, errorCallback)
    }

    fun deleteFilter(
        rule: String,
        target: String,
        value: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        priority3Api.deleteFilter(rule, target, value, responseCallback, errorCallback)
    }

    fun listS3Files(
        bucket: String? = null,
        search: String? = null,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        priority3Api.listS3Files(bucket, search, responseCallback, errorCallback)
    }

    fun validateS3File(
        options: ZBValidateS3FileOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        priority3Api.validateS3File(options, responseCallback, errorCallback)
    }

    fun scoreS3File(
        options: ZBScoreS3FileOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        priority3Api.scoreS3File(options, responseCallback, errorCallback)
    }

    /**
     * Exports a validated or scored file back to Amazon S3.
     *
     * Pass null for [exportBucket] to use the export bucket from your
     * ZeroBounce settings.
     */
    fun exportS3File(
        fileId: String,
        exportBucket: String?,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        priority3Api.exportS3File(fileId, exportBucket, responseCallback, errorCallback)
    }

    /** Runs the email finder on a file in your Amazon S3 bucket. */
    fun emailFinderS3File(
        options: ZBBulkFileJobOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        priority3Api.emailFinderS3File(options, responseCallback, errorCallback)
    }

    /** Runs domain search on a file in your Amazon S3 bucket. */
    fun domainSearchS3File(
        options: ZBBulkFileJobOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        priority3Api.domainSearchS3File(options, responseCallback, errorCallback)
    }

    /**
     * Validates a file from your SFTP/FTP server.
     *
     * This is the one job whose file_name the docs list as optional: leave
     * [ZBBulkFileJobOptions.fileName] null to use the input bucket from your
     * ZeroBounce settings.
     */
    fun validateSFTPFile(
        options: ZBBulkFileJobOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        priority3Api.validateSFTPFile(options, responseCallback, errorCallback)
    }

    /** Scores a file from your SFTP/FTP server. */
    fun scoreSFTPFile(
        options: ZBBulkFileJobOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        priority3Api.scoreSFTPFile(options, responseCallback, errorCallback)
    }

    /** Runs the email finder on a file from your SFTP/FTP server. */
    fun emailFinderSFTPFile(
        options: ZBBulkFileJobOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        priority3Api.emailFinderSFTPFile(options, responseCallback, errorCallback)
    }

    /** Runs domain search on a file from your SFTP/FTP server. */
    fun domainSearchSFTPFile(
        options: ZBBulkFileJobOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        priority3Api.domainSearchSFTPFile(options, responseCallback, errorCallback)
    }

    /**
     * Exports a validated or scored file back to your SFTP/FTP server.
     *
     * Note the field name: this endpoint takes export_path, not the
     * export_bucket that [exportS3File] sends. Pass null to use the export
     * path from your ZeroBounce settings.
     */
    fun exportSFTPFile(
        fileId: String,
        exportPath: String?,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        priority3Api.exportSFTPFile(fileId, exportPath, responseCallback, errorCallback)
    }

    // Inbox Placement Tester (bulkapi.zerobounce.net/api/ipt). The API key goes
    // on the query string, bodies are camelCase JSON, and errors come back
    // under a "detail" key. Spends the account's inbox-test credits.

    /** Seed providers available for new tests. */
    fun getInboxPlacementProviders(
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        priority3Api.inboxPlacementRequest("GET", "/providers", emptyMap(), null, responseCallback, errorCallback)
    }

    /** Supported test types and the default one. */
    fun getInboxPlacementTestTypes(
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        priority3Api.inboxPlacementRequest("GET", "/test-types", emptyMap(), null, responseCallback, errorCallback)
    }

    /** Starts a test and returns its seed list. */
    fun createInboxPlacementTest(
        name: String,
        options: ZBCreateInboxPlacementTestOptions?,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        if (name.isBlank()) {
            errorCallback(ErrorResponse.parseError("Empty parameter: name"))
            return
        }
        val body = mutableMapOf<String, Any?>("name" to name)
        options?.testType?.let { body["testType"] = it }
        options?.senderEmail?.let { body["senderEmail"] = it }
        options?.providerIds?.let { body["providerIds"] = it }
        options?.seedIds?.let { body["seedIds"] = it }
        options?.providers?.let { body["providers"] = it }
        options?.webhookUrl?.let { body["webhookUrl"] = it }
        priority3Api.inboxPlacementRequest("POST", "/test", emptyMap(), body, responseCallback, errorCallback)
    }

    /** The results of one test. */
    fun getInboxPlacementTest(
        testId: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        val segment = inboxPlacementTestSegment(testId, errorCallback) ?: return
        priority3Api.inboxPlacementRequest("GET", "/test/$segment", emptyMap(), null, responseCallback, errorCallback)
    }

    /** The account's tests. */
    fun listInboxPlacementTests(
        options: ZBListInboxPlacementTestsOptions?,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        val query = mutableMapOf<String, String>()
        options?.status?.let { query["status"] = it }
        options?.page?.let { query["page"] = it.toString() }
        options?.pageSize?.let { query["pageSize"] = it.toString() }
        // LIVE: the listing is GET /tests (plural). /test is the single-test
        // path and answers Method "GET" not allowed.
        priority3Api.inboxPlacementRequest("GET", "/tests", query, null, responseCallback, errorCallback)
    }

    /** Deletes one test. */
    fun deleteInboxPlacementTest(
        testId: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        val segment = inboxPlacementTestSegment(testId, errorCallback) ?: return
        priority3Api.inboxPlacementRequest("DELETE", "/test/$segment", emptyMap(), null, responseCallback, errorCallback)
    }

    /** The configured webhook. */
    fun getInboxPlacementWebhook(
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        priority3Api.inboxPlacementRequest("GET", "/webhook", emptyMap(), null, responseCallback, errorCallback)
    }

    /** Sets the webhook that test results post to. */
    fun setInboxPlacementWebhook(
        webhookUrl: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (invalidApiKey(errorCallback)) return
        if (webhookUrl.isBlank()) {
            errorCallback(ErrorResponse.parseError("Empty parameter: webhook_url"))
            return
        }
        priority3Api.inboxPlacementRequest(
            "PUT",
            "/webhook",
            emptyMap(),
            mapOf("webhookUrl" to webhookUrl),
            responseCallback,
            errorCallback,
        )
    }

    private fun inboxPlacementTestSegment(
        testId: String,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ): String? {
        if (testId.isBlank()) {
            errorCallback(ErrorResponse.parseError("Empty parameter: test_id"))
            return null
        }
        return encPathSegment(testId.trim())
    }

    /**
     * Real-time AI scoring for a single email (GET /scoring).
     */
    fun scoreEmail(
        email: String,
        responseCallback: (response: ZBScoreEmailResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit
    ) {
        if (invalidApiKey(errorCallback)) return

        request(
            url = urlWithQuery(
                "$apiBaseUrl/scoring",
                listOf("api_key" to (apiKey ?: ""), "email" to email),
            ),
            body = null,
            responseCallback = responseCallback,
            errorCallback = errorCallback
        )
    }

    /**
     * Real-time AI scoring for a single email (POST /scoring, form-encoded).
     */
    fun scoreEmailPost(
        email: String,
        responseCallback: (response: ZBScoreEmailResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit
    ) {
        if (invalidApiKey(errorCallback)) return
        val key = apiKey ?: return

        requestFormPost(
            url = "$apiBaseUrl/scoring",
            formFields = mapOf("api_key" to key, "email" to email),
            responseCallback = responseCallback,
            errorCallback = errorCallback
        )
    }

    /**
     * Checks if the [apiKey] is invalid or not and if it is, then it throws an error through
     * the provided [errorCallback].
     *
     * @param errorCallback the error callback
     * @return **true** if the [apiKey] is null or **false** otherwise
     */
    private fun invalidApiKey(errorCallback: (ErrorResponse) -> Unit): Boolean {
        if (apiKey.isNullOrBlank()) {
            val errorResponse = ErrorResponse.parseError(
                "ZeroBounce SDK is not initialized. Please call ZeroBounceSDK.initialize(context, apiKey) first!"
            )
            errorCallback(errorResponse)
            return true
        }
        return false
    }

    private fun redactSecret(value: String): String {
        return value.replace(Regex("(?i)([?&]api_key=)[^&]*"), "$1REDACTED")
    }

    /**
     * The helper method that handles any type of request.
     *
     * @param url the url
     * @param body if not null, then this will be the request's body data adn the request is
     * considered a POST request; otherwise, the request is considered a GET request
     * @param responseCallback the response callback
     * @param errorCallback the error callback
     */
    private inline fun <reified T> request(
        url: String,
        body: Map<String, Any>?,
        crossinline responseCallback: (response: T?) -> Unit,
        crossinline errorCallback: (errorResponse: ErrorResponse?) -> Unit
    ) where T : JSONConvertable {
        if (logEnabled) {
            Log.d("ZeroBounceSDK", "request url: ${redactSecret(url)}")
        }

        val requestBuilder = Request.Builder()
            .url(url)

        if (body != null) {
            val requestBody =
                Gson().toJson(body).toRequestBody("application/json".toMediaTypeOrNull())
            requestBuilder.post(requestBody)
        }

        val request = requestBuilder.build()

        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string()
                if (logEnabled) {
                    Log.d("ZeroBounceSDK", "response: ${redactedBody(json)}")
                }

                acceptJson(response.code, json, errorCallback) {
                    responseCallback(it.toObject<T>())
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                errorCallback(ErrorResponse.parseError(e.message))
            }
        })
    }

    private inline fun <reified T> requestFormPost(
        url: String,
        formFields: Map<String, String>,
        crossinline responseCallback: (response: T?) -> Unit,
        crossinline errorCallback: (errorResponse: ErrorResponse?) -> Unit
    ) where T : JSONConvertable {
        if (logEnabled) {
            Log.d("ZeroBounceSDK", "request url: ${redactSecret(url)}")
        }

        val formBuilder = FormBody.Builder()
        for ((name, value) in formFields) {
            formBuilder.add(name, value)
        }

        val request = Request.Builder()
            .url(url)
            .post(formBuilder.build())
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string()
                if (logEnabled) {
                    Log.d("ZeroBounceSDK", "response: ${redactedBody(json)}")
                }

                acceptJson(response.code, json, errorCallback) {
                    responseCallback(it.toObject<T>())
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                errorCallback(ErrorResponse.parseError(e.message))
            }
        })
    }

    /**
     * Determines if the context calling has the required permission.
     *
     * @param context the app context
     * @param permission the permission to check
     *
     * @return *true* if the app has the granted permission, or *false* otherwise
     */
    private fun hasPermission(context: Context, permission: String): Boolean {
        val res = context.checkCallingOrSelfPermission(permission)

        if (logEnabled) {
            Log.v(
                "ZeroBounce", "permission: " + permission + " = \t\t" +
                        if (res == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"
            )
        }
        return res == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Determines if the context calling has the required permissions.
     *
     * @param context the app context
     * @param permissions the permissions to check
     *
     * @return *true* if the app has the granted permissions, or *false* otherwise
     */
    private fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
        var hasAllPermissions = true

        for (permission in permissions) {
            // you can return false instead of assigning, but by assigning you can log all
            // permission values
            if (!hasPermission(context, permission)) {
                hasAllPermissions = false
            }
        }
        return hasAllPermissions
    }

    internal fun priority3ApiKey(): String? = apiKey

    internal fun priority3ApiBaseUrl(): String = apiBaseUrl

    internal fun priority3BulkApiBaseUrl(): String = bulkApiBaseUrl

    internal fun priority3BulkApiRootUrl(): String = bulkApiRootUrl

    internal fun priority3ListEvaluatorBaseUrl(): String = listEvaluatorBaseUrl

    internal fun priority3InboxPlacementBaseUrl(): String = inboxPlacementBaseUrl

    /**
     * Overrides the list evaluator base URL. It is a separate host rather than
     * a path built from the bulk base, so [initialize] alone does not change it.
     */
    fun setListEvaluatorBaseUrl(url: String) {
        require(isAllowedApiBaseUrl(url)) { "listEvaluatorBaseUrl must be an https:// URL" }
        listEvaluatorBaseUrl = url.trimEnd('/')
    }

    /** Overrides the Inbox Placement Tester base URL. See [setListEvaluatorBaseUrl]. */
    fun setInboxPlacementBaseUrl(url: String) {
        require(isAllowedApiBaseUrl(url)) { "inboxPlacementBaseUrl must be an https:// URL" }
        inboxPlacementBaseUrl = url.trimEnd('/')
    }

    internal fun priority3EmailFinderBaseUrl(): String = "$bulkApiRootUrl/email-finder"

    internal fun priority3DomainSearchBaseUrl(): String = "$bulkApiRootUrl/domain-search"

    internal fun encQuery(value: String): String = enc(value)

    /**
     * Encodes a value as a single path segment.
     *
     * [enc] is form encoding, which turns a space into "+". That is correct in
     * a query string but means a literal plus in a path, so the file id and the
     * Inbox Placement test id use this instead.
     */
    internal fun encPathSegment(value: String): String = enc(value).replace("+", "%20")

    internal fun priority3Client(): OkHttpClient = client

    internal fun <T : JSONConvertable> priority3Get(
        urlPath: String,
        queryParameters: Map<String, String>,
        responseClass: Class<T>,
        responseCallback: (response: T?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        val query = queryParameters.entries.joinToString("&") { (k, v) ->
            "${enc(k)}=${enc(v)}"
        }
        val url = if (query.isEmpty()) urlPath else "$urlPath?$query"
        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string()
                acceptJson(response.code, json, errorCallback) { text ->
                    responseCallback(gson.fromJson(text, responseClass))
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                errorCallback(ErrorResponse.parseError(e.message))
            }
        })
    }

    internal fun priority3GetJson(
        urlPath: String,
        queryParameters: Map<String, String>,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        val query = queryParameters.entries.joinToString("&") { (k, v) ->
            "${enc(k)}=${enc(v)}"
        }
        val url = if (query.isEmpty()) urlPath else "$urlPath?$query"
        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string()
                acceptJson(response.code, json, errorCallback) { text ->
                    responseCallback(JsonParser.parseString(text))
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                errorCallback(ErrorResponse.parseError(e.message))
            }
        })
    }

    /**
     * One Inbox Placement Tester call.
     *
     * Unlike the rest of the SDK this one takes camelCase JSON bodies and uses
     * PUT and DELETE as well as GET and POST, so it builds the request itself.
     */
    internal fun priority3JsonRequest(
        method: String,
        url: String,
        body: Map<String, Any?>?,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
        extraHeaders: Map<String, String> = emptyMap(),
    ) {
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        extraHeaders.forEach { (name, value) -> builder.header(name, value) }
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
                val json = response.body?.string()
                acceptJson(response.code, json, errorCallback) { text ->
                    // DELETE answers with an empty body.
                    if (text.isBlank()) responseCallback(null)
                    else responseCallback(JsonParser.parseString(text))
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                errorCallback(ErrorResponse.parseError(e.message))
            }
        })
    }

    internal fun priority3FormPostJson(
        url: String,
        formFields: Map<String, String>,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        val formBuilder = FormBody.Builder()
        for ((name, value) in formFields) {
            formBuilder.add(name, value)
        }
        val request = Request.Builder().url(url).post(formBuilder.build()).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string()
                acceptJson(response.code, json, errorCallback) { text ->
                    responseCallback(JsonParser.parseString(text))
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                errorCallback(ErrorResponse.parseError(e.message))
            }
        })
    }

    internal fun priority3FormPost(
        url: String,
        formFields: Map<String, String>,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        requestFormPost(url, formFields, responseCallback, errorCallback)
    }

    internal fun priority3EnqueueMultipart(
        url: String,
        body: MultipartBody,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        val request = Request.Builder().url(url).post(body).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string()
                acceptJson(response.code, json, errorCallback) {
                    responseCallback(it.toObject<ZBSendFileResponse>())
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                errorCallback(ErrorResponse.parseError(e.message))
            }
        })
    }

    internal fun priority3RawDownloadCallback(
        downloadPath: String,
        responseCallback: (response: ZBGetFileResponse) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ): Callback = object : Callback {
        override fun onResponse(call: Call, response: Response) {
            response.use {
                val bodyBytes = it.body.bytes()
                val bodyStr = bodyBytes.toString(Charsets.UTF_8)
                val contentType = it.header("Content-Type").orEmpty()
                if (downloadRejected(it.code, contentType, bodyBytes)) {
                    errorCallback(httpError(it.code, bodyStr))
                    return
                }
                try {
                    responseCallback(Priority3Api.writeDownloadFile(downloadPath, bodyBytes))
                } catch (e: IOException) {
                    errorCallback(ErrorResponse.parseError(e.message))
                }
            }
        }

        override fun onFailure(call: Call, e: IOException) {
            errorCallback(ErrorResponse.parseError(e.message))
        }
    }
}

internal class SdkState {
    var apiKey: String? = null
    lateinit var client: OkHttpClient
    var transportInterceptor: Interceptor? = null
    var apiBaseUrl = "https://api.zerobounce.net/v2"
    var bulkApiBaseUrl = "https://bulkapi.zerobounce.net/v2"
    var bulkApiScoringBaseUrl = "https://bulkapi.zerobounce.net/v2/scoring"
    var bulkApiRootUrl = "https://bulkapi.zerobounce.net"

    // The list evaluator and the Inbox Placement Tester have their own base
    // URLs, not paths built from the bulk one. Overriding apiBaseUrl alone
    // leaves them pointing at the live API.
    var listEvaluatorBaseUrl = "https://bulkapi.zerobounce.net/v2/listevaluator"
    var inboxPlacementBaseUrl = "https://bulkapi.zerobounce.net/api/ipt"
}
