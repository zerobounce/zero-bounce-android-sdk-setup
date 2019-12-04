package com.zerobounce.android

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.util.Log
import com.android.volley.*
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.entity.mime.content.FileBody
import org.apache.http.entity.mime.content.StringBody
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * ZeroBounceSDK
 */
class ZeroBounceSDK {

    companion object {
        private var apiKey: String? = null
        private var queue: RequestQueue? = null
        private const val apiBaseUrl = "https://api.zerobounce.net/v2"
        private const val bulkApiBaseUrl = "https://bulkapi.zerobounce.net/v2"

        private const val READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"
        private const val WRITE_EXTERNAL_STORAGE = "android.permission.WRITE_EXTERNAL_STORAGE"
        private var logEnabled = false

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        private inline fun <reified T : JSONConvertable> String.toObject(): T =
            Gson().fromJson(this, T::class.java)

        fun initialize(context: Context, apiKey: String) {
            this.queue = Volley.newRequestQueue(context)
            this.apiKey = apiKey
        }

        /**
         * @param email The email address you want to validate
         * @param ipAddress The IP Address the email signed up from (Can be blank)
         */
        fun validate(
            email: String,
            ipAddress: String?,
            responseCallback: (response: ZBValidateResponse) -> Unit,
            errorCallback: (errorMessage: String?) -> Unit
        ) {
            if (apiKey == null) {
                errorCallback("ZeroBounce SDK is not initialized. Please call ZeroBounceSDK.initialize(context, apiKey) first")
                return
            }

            val ipAddressPart = if (ipAddress != null) "$ipAddress" else ""
            request(
                "$apiBaseUrl/validate?api_key=$apiKey&email=$email&ip_address=$ipAddressPart",
                null,
                responseCallback,
                errorCallback
            )
        }

        /**
         * @param startDate The start date of when you want to view API usage
         * @param endDate The end date of when you want to view API usage
         */
        fun getApiUsage(
            startDate: Date, endDate: Date,
            responseCallback: (response: ZBGetApiUsageResponse) -> Unit,
            errorCallback: (errorMessage: String?) -> Unit
        ) {
            if (apiKey == null) {
                errorCallback("ZeroBounce SDK is not initialized. Please call ZeroBounceSDK.initialize(context, apiKey) first")
                return
            }

            request(
                "$apiBaseUrl/getapiusage?api_key=$apiKey&start_date=${dateFormat.format(startDate)}&end_date=${dateFormat.format(
                    endDate
                )}",
                null, responseCallback,
                errorCallback
            )
        }

        /**
         * This API will tell you how many credits you have left on your account. It's simple, fast and easy to use.
         */
        fun getCredits(
            responseCallback: (response: ZBCreditsResponse) -> Unit,
            errorCallback: (errorMessage: String?) -> Unit
        ) {
            if (apiKey == null) {
                errorCallback("ZeroBounce SDK is not initialized. Please call ZeroBounceSDK.initialize(context, apiKey) first")
                return
            }

            request("$apiBaseUrl/getcredits?api_key=$apiKey", null, responseCallback, errorCallback)
        }


        /**
         * The sendfile API allows user to send a file for bulk email validation
         */
        fun sendFile(
            context: Context,
            file: File, email_address_column: Int,
            responseCallback: (response: ZBSendFileResponse) -> Unit,
            errorCallback: (errorMessage: String?) -> Unit,
            returnUrl: String? = null, firstNameColumn: Int? = null, lastNameColumn: Int? = null,
            genderColumn: Int? = null, ipAddressColumn: Int? = null, hasHeaderRow: Boolean = false
        ) {
            _sendFile(
                false,
                context,
                file,
                email_address_column,
                responseCallback,
                errorCallback,
                returnUrl,
                firstNameColumn,
                lastNameColumn,
                genderColumn,
                ipAddressColumn,
                hasHeaderRow
            )
        }

        /**
         * The scoringSendFile API allows user to send a file for bulk email validation
         */
        fun scoringSendFile(
            context: Context,
            file: File, email_address_column: Int,
            responseCallback: (response: ZBSendFileResponse) -> Unit,
            errorCallback: (errorMessage: String?) -> Unit,
            returnUrl: String? = null, hasHeaderRow: Boolean = false
        ) {
            _sendFile(
                true, context, file, email_address_column, responseCallback, errorCallback,
                returnUrl, hasHeaderRow = hasHeaderRow
            )
        }

        private fun _sendFile(
            scoring: Boolean, context: Context,
            file: File, email_address_column: Int,
            responseCallback: (response: ZBSendFileResponse) -> Unit,
            errorCallback: (errorMessage: String?) -> Unit,
            returnUrl: String? = null, firstNameColumn: Int? = null, lastNameColumn: Int? = null,
            genderColumn: Int? = null, ipAddressColumn: Int? = null, hasHeaderRow: Boolean = false
        ) {
            if (apiKey == null) {
                errorCallback("ZeroBounce SDK is not initialized. Please call ZeroBounceSDK.initialize(context, apiKey) first")
                return
            }

            if (!hasPermissions(context, arrayOf(READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE))) {
                errorCallback("Cannot send file, permissions $READ_EXTERNAL_STORAGE and $WRITE_EXTERNAL_STORAGE are not granted")
                return
            }

            if (!file.exists()) {
                errorCallback("File does not exist")
                return
            }

            if (queue == null) {
                errorCallback("ZeroBounce SDK is not initialized. Please call ZeroBounceSDK.initialize(context, apiKey) first")
                return
            }

            val builder = MultipartEntityBuilder.create()
            builder.addPart("file", FileBody(file))
            try {
                builder.addPart("api_key", StringBody(apiKey, ContentType.TEXT_PLAIN))
                if (returnUrl != null) builder.addPart(
                    "return_url",
                    StringBody(returnUrl, ContentType.TEXT_PLAIN)
                )
                builder.addPart(
                    "email_address_column",
                    StringBody(email_address_column.toString(), ContentType.TEXT_PLAIN)
                )
                if (firstNameColumn != null) builder.addPart(
                    "first_name_column",
                    StringBody(firstNameColumn.toString(), ContentType.TEXT_PLAIN)
                )
                if (lastNameColumn != null) builder.addPart(
                    "last_name_column",
                    StringBody(lastNameColumn.toString(), ContentType.TEXT_PLAIN)
                )
                if (genderColumn != null) builder.addPart(
                    "gender_column",
                    StringBody(genderColumn.toString(), ContentType.TEXT_PLAIN)
                )
                if (ipAddressColumn != null) builder.addPart(
                    "ip_address_column",
                    StringBody(ipAddressColumn.toString(), ContentType.TEXT_PLAIN)
                )
                builder.addPart(
                    "has_header_row",
                    StringBody(hasHeaderRow.toString(), ContentType.TEXT_PLAIN)
                )
            } catch (e: UnsupportedEncodingException) {
                VolleyLog.e("UnsupportedEncodingException")
            }

            val r = ZBMultiPartRequest(
                bulkApiBaseUrl + (if (scoring) "/scoring" else "") + "/sendFile",

                builder,
                Response.Listener { response ->
                    if (logEnabled) Log.d("ZeroBounceSDK", "sendFile response: $response")
                    val rsp = response.toObject<ZBSendFileResponse>()
                    responseCallback(rsp)
                },
                Response.ErrorListener { error ->
                    if (logEnabled) Log.d(
                        "ZeroBounceSDK",
                        "sendFile error=$error, ${error.networkResponse.data}"
                    )
                    errorCallback(error.message)
                })
            queue!!.add(r)
        }

        /**
         * @param fileId The returned file ID when calling sendFile API.
         */
        fun fileStatus(
            fileId: String,
            responseCallback: (response: ZBFileStatusResponse) -> Unit,
            errorCallback: (errorMessage: String?) -> Unit
        ) {
            _fileStatus(false, fileId, responseCallback, errorCallback)
        }

        /**
         * @param fileId The returned file ID when calling sendFile API.
         */
        fun scoringFileStatus(
            fileId: String,
            responseCallback: (response: ZBFileStatusResponse) -> Unit,
            errorCallback: (errorMessage: String?) -> Unit
        ) {
            _fileStatus(true, fileId, responseCallback, errorCallback)
        }

        private fun _fileStatus(
            scoring: Boolean,
            fileId: String,
            responseCallback: (response: ZBFileStatusResponse) -> Unit,
            errorCallback: (errorMessage: String?) -> Unit
        ) {
            if (apiKey == null) {
                errorCallback("ZeroBounce SDK is not initialized. Please call ZeroBounceSDK.initialize(context, apiKey) first")
                return
            }

            request(
                bulkApiBaseUrl + (if (scoring) "/scoring" else "") + "/filestatus?api_key=$apiKey&file_id=$fileId",
                null,
                responseCallback,
                errorCallback
            )
        }

        /**
         * The getfile API allows users to get the validation results file for the file been submitted using sendfile API
         */
        fun getFile(
            context: Context,
            fileId: String,
            responseCallback: (response: ZBGetFileResponse) -> Unit,
            errorCallback: (errorMessage: String?) -> Unit
        ) {
            _getFile(false, context, fileId, responseCallback, errorCallback)
        }

        /**
         * The scoringGetFile API allows users to get the validation results file for the file been submitted using scoringSendFile API
         */
        fun scoringGetFile(
            context: Context,
            fileId: String,
            responseCallback: (response: ZBGetFileResponse) -> Unit,
            errorCallback: (errorMessage: String?) -> Unit
        ) {
            _getFile(true, context, fileId, responseCallback, errorCallback)
        }

        private fun _getFile(
            scoring: Boolean,
            context: Context,
            fileId: String,
            responseCallback: (response: ZBGetFileResponse) -> Unit,
            errorCallback: (errorMessage: String?) -> Unit
        ) {
            if (apiKey == null) {
                errorCallback("ZeroBounce SDK is not initialized. Please call ZeroBounceSDK.initialize(context, apiKey) first")
                return
            }

            if (!hasPermissions(context, arrayOf(READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE))) {
                errorCallback("Cannot get file, permissions $READ_EXTERNAL_STORAGE and $WRITE_EXTERNAL_STORAGE are not granted")
                return
            }

            if (queue == null) {
                errorCallback("ZeroBounce SDK is not initialized. Please call ZeroBounceSDK.initialize(context, apiKey) first")
                return
            }

            val req = ZBByteArrayRequest(Request.Method.GET,
                bulkApiBaseUrl +(if (scoring) "/scoring" else "")+"/getFile?api_key=$apiKey&file_id=$fileId",
                Response.Listener { response ->
                    val f = File(Environment.getExternalStorageDirectory().path + "/ZeroBounce")
                    if (!f.isDirectory) f.mkdir()

                    val fileName = response.fileName ?: "$fileId.csv"
                    val filePath =
                        Environment.getExternalStorageDirectory().path + "/ZeroBounce/" + fileName

                    try {
                        val file = FileOutputStream(filePath)
                        file.write(response.data)
                        file.flush()
                        file.close()
                        responseCallback(ZBGetFileResponse(filePath))
                    } catch (e: FileNotFoundException) {
                        errorCallback(e.message)
                    } catch (e: IOException) {
                        errorCallback(e.message)
                    }

                },
                Response.ErrorListener { error ->
                    errorCallback(error.message)
                })

            queue!!.add(req)
        }

        /**
         * @param fileId The returned file ID when calling sendfile API.
         */
        fun deleteFile(
            fileId: String,
            successCallback: (response: ZBDeleteFileResponse) -> Unit,
            errorCallback: (errorMessage: String?) -> Unit
        ) {
            if (apiKey == null) {
                errorCallback("ZeroBounce SDK is not initialized. Please call ZeroBounceSDK.initialize(context, apiKey) first")
                return
            }

            request(
                "$bulkApiBaseUrl/deletefile?api_key=$apiKey&file_id=$fileId",
                null,
                successCallback,
                errorCallback
            )
        }


        private inline fun <reified T> request(
            url: String, req: JSONConvertable?,
            crossinline responseCallback: (response: T) -> Unit,
            crossinline errorCallback: (errorMessage: String?) -> Unit
        )
                where T : JSONConvertable {
            if (queue == null) {
                errorCallback("ZeroBounce SDK is not initialized. Please call ZeroBounceSDK.initialize(context, apiKey) first")
                return
            }

            val jsonData = req?.toJSON()
            if (logEnabled) Log.d("ZeroBounceSDK", "request url: $url")
            val jsonObjectRequest =
                JsonObjectRequest(if (jsonData != null) Request.Method.POST else Request.Method.GET,
                    url, if (jsonData != null) JSONObject(jsonData) else null,
                    Response.Listener { response ->
                        if (logEnabled) Log.d("ZeroBounceSDK", "response: $response")
                        val rsp = response.toString().toObject<T>()
                        responseCallback(rsp)
                    },
                    Response.ErrorListener { error ->
                        errorCallback(error.message)
                    }
                )
            queue!!.add(jsonObjectRequest)
        }

        /** Determines if the context calling has the required permission
         * @param context - the app context
         * @param permission - The permission to check
         * @return true if the app has the granted permission
         */
        private fun hasPermission(context: Context, permission: String): Boolean {

            val res = context.checkCallingOrSelfPermission(permission)

            if (logEnabled) Log.v(
                "ZeroBounce", "permission: " + permission + " = \t\t" +
                        if (res == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"
            )

            return res == PackageManager.PERMISSION_GRANTED
        }

        /** Determines if the context calling has the required permissions
         * @param context - the app context
         * @param permissions - The permissions to check
         * @return true if the app has the granted permission
         */
        private fun hasPermissions(context: Context, permissions: Array<String>): Boolean {

            var hasAllPermissions = true

            for (permission in permissions) {
                //you can return false instead of assigning, but by assigning you can log all permission values
                if (!hasPermission(context, permission)) {
                    hasAllPermissions = false
                }
            }
            return hasAllPermissions
        }
    }
}