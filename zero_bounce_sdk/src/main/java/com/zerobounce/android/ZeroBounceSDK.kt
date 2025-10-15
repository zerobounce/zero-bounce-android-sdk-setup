package com.zerobounce.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.zerobounce.android.ZeroBounceSDK.apiKey
import okhttp3.Call
import okhttp3.Callback
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


/**
 * The ZeroBounce main class. All the requests are implemented here.
 */
object ZeroBounceSDK {

    private var apiKey: String? = null
    private lateinit var client: OkHttpClient
    internal var apiBaseUrl = "https://api.zerobounce.net/v2"
    internal var bulkApiBaseUrl = "https://bulkapi.zerobounce.net/v2"
    internal var bulkApiScoringBaseUrl = "https://bulkapi.zerobounce.net/v2/scoring"

    private const val READ_EXTERNAL_STORAGE = Manifest.permission.READ_EXTERNAL_STORAGE
    private const val WRITE_EXTERNAL_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE
    private val STORAGE_PERMISSIONS = arrayOf(READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE)
    private var logEnabled = false

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
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
        client = OkHttpClient()
        this.apiKey = apiKey
        apiBaseUrl?.let { this.apiBaseUrl = apiBaseUrl }
    }

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
        if (invalidApiKey(errorCallback)) return

        val ipAddressPart = ipAddress ?: ""
        request(
            url = "$apiBaseUrl/validate?api_key=$apiKey&email=$email&ip_address=$ipAddressPart",
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
        if (invalidApiKey(errorCallback)) return
        val apiKey = apiKey ?: return

        val body = mapOf(
            "api_key" to apiKey,
            "email_batch" to emails
        )

        request(
            url = "$apiBaseUrl/validatebatch",
            body = body,
            responseCallback = responseCallback,
            errorCallback = errorCallback
        )
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

        var url = "$apiBaseUrl/guessformat?api_key=$apiKey&domain=$domain"
        firstName?.let { url += "&first_name=$firstName" }
        middleName?.let { url += "&middle_name=$middleName" }
        lastName?.let { url += "&last_name=$lastName" }

        request(
            url = url,
            body = null,
            responseCallback = responseCallback,
            errorCallback = errorCallback
        )
    }

    /**
     * Find the email based on a given [firstName], [domain] or [companyName].
     *
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

        var url = "$apiBaseUrl/guessformat?api_key=$apiKey&first_name=$firstName"
        domain?.let { url += "&domain=$domain" }
        companyName?.let { url += "&company_name=$companyName" }
        middleName?.let { url += "&middle_name=$middleName" }
        lastName?.let { url += "&last_name=$lastName" }

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

        var url = "$apiBaseUrl/guessformat?api_key=$apiKey"
        domain?.let { url += "&domain=$domain" }
        companyName?.let { url += "&company_name=$companyName" }

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

        val sDate = dateFormat.format(startDate)
        val eDate = dateFormat.format(endDate)

        request(
            url = "$apiBaseUrl/getapiusage?api_key=$apiKey&start_date=$sDate&end_date=$eDate",
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
            url = "$apiBaseUrl/getcredits?api_key=$apiKey",
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
                    Log.d("ZeroBounceSDK", "response: $json")
                }

                if (response.isSuccessful) {
                    try {
                        val rsp = json?.toObject<ZBSendFileResponse>()
                        responseCallback(rsp)
                    } catch (_: Throwable) {
                        errorCallback(ErrorResponse.parseError(json))
                    }
                } else {
                    errorCallback(ErrorResponse.parseError(json))
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
            url = "$baseUrl/filestatus?api_key=$apiKey&file_id=$fileId",
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
     */
    fun getFile(
        context: Context,
        fileId: String,
        responseCallback: (response: ZBGetFileResponse) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit
    ) {
        getFileInternal(
            context = context,
            scoring = false,
            fileId = fileId,
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
     */
    fun scoringGetFile(
        context: Context,
        fileId: String,
        responseCallback: (response: ZBGetFileResponse) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit
    ) {
        getFileInternal(
            context = context,
            scoring = true,
            fileId = fileId,
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
        responseCallback: (response: ZBGetFileResponse) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit
    ) {
        if (invalidApiKey(errorCallback)) return

        if (!hasPermissions(context, STORAGE_PERMISSIONS)) {
            val errorResponse = ErrorResponse.parseError(
                "Cannot send file, permissions $READ_EXTERNAL_STORAGE and $WRITE_EXTERNAL_STORAGE are not granted"
            )
            errorCallback(errorResponse)
            return
        }

        val baseUrl = if (scoring) bulkApiScoringBaseUrl else bulkApiBaseUrl
        val requestBuilder = Request.Builder()
            .url("$baseUrl/getfile?api_key=$apiKey&file_id=$fileId")

        val request = requestBuilder.build()

        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val fileName =
                    response.headers["content-disposition"]?.substringAfter("filename=")
                        ?.substringBefore(";")
                val name = fileName ?: "$fileId.csv"
                val file = File(context.getExternalFilesDir(null), "/$name")

                if (response.isSuccessful) {
                    try {
                        FileOutputStream(file.path).use { fos ->
                            response.body?.byteStream()?.copyTo(fos)
                            fos.flush()
                            fos.close()
                        }
                        responseCallback(ZBGetFileResponse(file.path))
                    } catch (e: FileNotFoundException) {
                        errorCallback(ErrorResponse.parseError(e.message))
                    } catch (e: IOException) {
                        errorCallback(ErrorResponse.parseError(e.message))
                    }
                } else {
                    errorCallback(ErrorResponse.parseError(response.body?.string()))
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
            url = "$baseUrl/deletefile?api_key=$apiKey&file_id=$fileId",
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
            url = "$apiBaseUrl/activity?api_key=$apiKey&email=$email",
            body = null,
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
        if (apiKey == null) {
            val errorResponse = ErrorResponse.parseError(
                "ZeroBounce SDK is not initialized. Please call ZeroBounceSDK.initialize(context, apiKey) first!"
            )
            errorCallback(errorResponse)
            return true
        }
        return false
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
            Log.d("ZeroBounceSDK", "request url: $url")
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
                    Log.d("ZeroBounceSDK", "response: $json")
                }

                if (response.isSuccessful) {
                    try {
                        val rsp = json?.toObject<T>()
                        responseCallback(rsp)
                    } catch (_: Throwable) {
                        errorCallback(ErrorResponse.parseError(json))
                    }
                } else {
                    errorCallback(ErrorResponse.parseError(json))
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
}
