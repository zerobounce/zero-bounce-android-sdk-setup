package com.zerobounce.android

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

internal class Priority3Api {

    fun sendEmailFinderFile(
        file: File,
        domainColumn: Int,
        options: ZBEmailFinderSendFileOptions?,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (domainColumn < 1) {
            errorCallback(ErrorResponse.parseError("Empty parameter: domain_column"))
            return
        }
        if (missingEmailFinderNameColumn(options, errorCallback)) return
        val url = "${ZeroBounceSDK.priority3EmailFinderBaseUrl()}/sendfile"
        sendBulkRootMultipart(url, file, buildEmailFinderFields(domainColumn, options), responseCallback, errorCallback)
    }

    fun sendEmailFinderFileStream(
        inputStream: InputStream,
        fileName: String,
        domainColumn: Int,
        options: ZBEmailFinderSendFileOptions?,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (domainColumn < 1) {
            errorCallback(ErrorResponse.parseError("Empty parameter: domain_column"))
            return
        }
        if (missingEmailFinderNameColumn(options, errorCallback)) return
        val url = "${ZeroBounceSDK.priority3EmailFinderBaseUrl()}/sendfile"
        sendBulkRootMultipartStream(
            url,
            inputStream,
            fileName,
            buildEmailFinderFields(domainColumn, options),
            responseCallback,
            errorCallback,
        )
    }

    /**
     * The bulk email finder needs a name to work from: the docs require
     * first_name_column or full_name_column alongside domain_column. A file
     * sent without either is accepted and then comes back with no results.
     */
    private fun missingEmailFinderNameColumn(
        options: ZBEmailFinderSendFileOptions?,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ): Boolean {
        if (options != null && (options.firstNameColumn > 0 || options.fullNameColumn > 0)) {
            return false
        }
        errorCallback(ErrorResponse.parseError("Empty parameter: first_name_column or full_name_column"))
        return true
    }

    private fun buildEmailFinderFields(
        domainColumn: Int,
        options: ZBEmailFinderSendFileOptions?,
    ): Map<String, String> {
        val fields = mutableMapOf("domain_column" to domainColumn.toString())
        if (options != null) {
            options.hasHeaderRow?.let { fields["has_header_row"] = if (it) "true" else "false" }
            if (options.firstNameColumn > 0) {
                fields["first_name_column"] = options.firstNameColumn.toString()
            }
            if (options.lastNameColumn > 0) {
                fields["last_name_column"] = options.lastNameColumn.toString()
            }
            if (options.middleNameColumn > 0) {
                fields["middle_name_column"] = options.middleNameColumn.toString()
            }
            if (options.fullNameColumn > 0) {
                fields["full_name_column"] = options.fullNameColumn.toString()
            }
        }
        return fields
    }

    fun sendDomainSearchFile(
        file: File,
        domainColumn: Int,
        options: ZBDomainSearchSendFileOptions?,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (domainColumn < 1) {
            errorCallback(ErrorResponse.parseError("Empty parameter: domain_column"))
            return
        }
        sendBulkRootMultipart(
            "${ZeroBounceSDK.priority3DomainSearchBaseUrl()}/sendfile",
            file,
            buildDomainSearchFields(domainColumn, options),
            responseCallback,
            errorCallback,
        )
    }

    fun sendDomainSearchFileStream(
        inputStream: InputStream,
        fileName: String,
        domainColumn: Int,
        options: ZBDomainSearchSendFileOptions?,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (domainColumn < 1) {
            errorCallback(ErrorResponse.parseError("Empty parameter: domain_column"))
            return
        }
        sendBulkRootMultipartStream(
            "${ZeroBounceSDK.priority3DomainSearchBaseUrl()}/sendfile",
            inputStream,
            fileName,
            buildDomainSearchFields(domainColumn, options),
            responseCallback,
            errorCallback,
        )
    }

    private fun buildDomainSearchFields(
        domainColumn: Int,
        options: ZBDomainSearchSendFileOptions?,
    ): Map<String, String> {
        val fields = mutableMapOf("domain_column" to domainColumn.toString())
        options?.hasHeaderRow?.let { fields["has_header_row"] = if (it) "true" else "false" }
        return fields
    }

    fun bulkRootFileStatus(
        baseUrl: String,
        fileId: String,
        responseCallback: (response: ZBFileStatusResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        val key = ZeroBounceSDK.priority3ApiKey() ?: return
        ZeroBounceSDK.priority3Get(
            "$baseUrl/filestatus",
            mapOf("api_key" to key, "file_id" to fileId),
            ZBFileStatusResponse::class.java,
            responseCallback,
            errorCallback,
        )
    }

    fun bulkRootGetFile(
        baseUrl: String,
        fileId: String,
        downloadPath: String,
        responseCallback: (response: ZBGetFileResponse) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        val key = ZeroBounceSDK.priority3ApiKey() ?: return
        val url =
            "$baseUrl/getfile?api_key=${ZeroBounceSDK.encQuery(key)}&file_id=${ZeroBounceSDK.encQuery(fileId)}"
        val request = Request.Builder().url(url).build()
        ZeroBounceSDK.priority3Client().newCall(request).enqueue(
            ZeroBounceSDK.priority3RawDownloadCallback(downloadPath, responseCallback, errorCallback),
        )
    }

    fun bulkRootDeleteFile(
        baseUrl: String,
        fileId: String,
        responseCallback: (response: ZBDeleteFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        val key = ZeroBounceSDK.priority3ApiKey() ?: return
        ZeroBounceSDK.priority3Get(
            "$baseUrl/deletefile",
            mapOf("api_key" to key, "file_id" to fileId),
            ZBDeleteFileResponse::class.java,
            responseCallback,
            errorCallback,
        )
    }

    fun sendListEvaluatorFile(
        file: File,
        emailAddressColumn: Int,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (emailAddressColumn < 1) {
            errorCallback(ErrorResponse.parseError("Empty parameter: email_address_column"))
            return
        }
        val fields = mapOf("email_address_column" to emailAddressColumn.toString())
        sendBulkRootMultipart(
            // The upload has to follow the same base URL as the result fetch.
            "${ZeroBounceSDK.priority3ListEvaluatorBaseUrl()}/",
            file,
            fields,
            responseCallback,
            errorCallback,
        )
    }

    fun sendListEvaluatorFileStream(
        inputStream: InputStream,
        fileName: String,
        emailAddressColumn: Int,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (emailAddressColumn < 1) {
            errorCallback(ErrorResponse.parseError("Empty parameter: email_address_column"))
            return
        }
        val fields = mapOf("email_address_column" to emailAddressColumn.toString())
        sendBulkRootMultipartStream(
            // The upload has to follow the same base URL as the result fetch.
            "${ZeroBounceSDK.priority3ListEvaluatorBaseUrl()}/",
            inputStream,
            fileName,
            fields,
            responseCallback,
            errorCallback,
        )
    }

    fun getListEvaluatorResult(
        fileId: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (fileId.isBlank()) {
            errorCallback(ErrorResponse.parseError("Empty parameter: file_id"))
            return
        }
        val key = ZeroBounceSDK.priority3ApiKey() ?: return
        ZeroBounceSDK.priority3GetJson(
            // The file id is one path segment, so a slash in it must stay encoded.
            "${ZeroBounceSDK.priority3ListEvaluatorBaseUrl()}/${ZeroBounceSDK.encPathSegment(fileId.trim())}/",
            mapOf("api_key" to key),
            responseCallback,
            errorCallback,
        )
    }

    /**
     * Submits a finished evaluation for validation. POST /v2/send-evaluated-file/
     *
     * LIVE: this takes the list evaluator's file_id as a form field, not the
     * file upload the docs describe - an upload is rejected with
     * "file_id: This field is required". It returns a new validation job's
     * file_id, which answers "File cannot be found" for a short while after it
     * is accepted.
     */
    fun sendEvaluatedFile(
        fileId: String,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (fileId.isBlank()) {
            errorCallback(ErrorResponse.parseError("Empty parameter: file_id"))
            return
        }
        postBulkRootFormAt(
            ZeroBounceSDK.priority3SendEvaluatedFileUrl(),
            mapOf("file_id" to fileId.trim()),
            responseCallback,
            errorCallback,
        )
    }

    fun addFilter(
        rule: String,
        target: String,
        value: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        val key = ZeroBounceSDK.priority3ApiKey() ?: return
        ZeroBounceSDK.priority3FormPostJson(
            "${ZeroBounceSDK.priority3ApiBaseUrl()}/filters/add",
            mapOf("api_key" to key, "rule" to rule, "target" to target, "value" to value),
            responseCallback,
            errorCallback,
        )
    }

    fun listFilters(
        usePost: Boolean,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        val key = ZeroBounceSDK.priority3ApiKey() ?: return
        if (usePost) {
            ZeroBounceSDK.priority3FormPostJson(
                "${ZeroBounceSDK.priority3ApiBaseUrl()}/filters/list",
                mapOf("api_key" to key),
                responseCallback,
                errorCallback,
            )
        } else {
            ZeroBounceSDK.priority3GetJson(
                "${ZeroBounceSDK.priority3ApiBaseUrl()}/filters/list",
                mapOf("api_key" to key),
                responseCallback,
                errorCallback,
            )
        }
    }

    fun deleteFilter(
        rule: String,
        target: String,
        value: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        val key = ZeroBounceSDK.priority3ApiKey() ?: return
        ZeroBounceSDK.priority3FormPostJson(
            "${ZeroBounceSDK.priority3ApiBaseUrl()}/filters/delete",
            mapOf("api_key" to key, "rule" to rule, "target" to target, "value" to value),
            responseCallback,
            errorCallback,
        )
    }

    fun listS3Files(
        bucket: String?,
        search: String?,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        val key = ZeroBounceSDK.priority3ApiKey() ?: return
        val params = mutableMapOf("api_key" to key)
        if (!bucket.isNullOrBlank()) params["bucket"] = bucket
        if (!search.isNullOrBlank()) params["search"] = search
        ZeroBounceSDK.priority3GetJson(
            "${ZeroBounceSDK.priority3BulkApiRootUrl()}/listS3files",
            params,
            responseCallback,
            errorCallback,
        )
    }

    fun validateS3File(
        options: ZBValidateS3FileOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = bulkRootFileJob(
        "/validateS3file",
        options.fileName,
        options.returnUrl,
        buildValidateS3Fields(options),
        responseCallback,
        errorCallback,
    )

    fun scoreS3File(
        options: ZBScoreS3FileOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = bulkRootFileJob(
        "/scoreS3file",
        options.fileName,
        options.returnUrl,
        buildScoreS3Fields(options),
        responseCallback,
        errorCallback,
    )

    fun exportS3File(
        fileId: String,
        exportBucket: String?,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (fileId.isBlank()) {
            errorCallback(ErrorResponse.parseError("Empty parameter: file_id"))
            return
        }
        if (exportBucket != null && exportBucket.isBlank()) {
            errorCallback(ErrorResponse.parseError("Empty parameter: export_bucket"))
            return
        }
        // Without an export_bucket the one from the account's settings is used.
        val fields = mutableMapOf("file_id" to fileId)
        exportBucket?.let { fields["export_bucket"] = it }
        postBulkRootForm("/exportS3file", fields, responseCallback, errorCallback)
    }

    fun emailFinderS3File(
        options: ZBBulkFileJobOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = bulkRootFileJob("/emailFinderS3file", options, responseCallback, errorCallback)

    fun domainSearchS3File(
        options: ZBBulkFileJobOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = bulkRootFileJob("/domainSearchS3file", options, responseCallback, errorCallback)

    // SFTP / FTP bulk. The server, path and protocol are never sent by the SDK;
    // they come from your ZeroBounce settings.

    fun validateSFTPFile(
        options: ZBBulkFileJobOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = bulkRootFileJob("/validateSFTPfile", options, responseCallback, errorCallback)

    fun scoreSFTPFile(
        options: ZBBulkFileJobOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = bulkRootFileJob("/scoreSFTPfile", options, responseCallback, errorCallback)

    fun emailFinderSFTPFile(
        options: ZBBulkFileJobOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = bulkRootFileJob("/emailFinderSFTPfile", options, responseCallback, errorCallback)

    fun domainSearchSFTPFile(
        options: ZBBulkFileJobOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = bulkRootFileJob("/domainSearchSFTPfile", options, responseCallback, errorCallback)

    /**
     * Exports a validated or scored file back to your SFTP/FTP server.
     *
     * Note the field name: this endpoint takes export_path, not the
     * export_bucket that [exportS3File] sends.
     */
    fun exportSFTPFile(
        fileId: String,
        exportPath: String?,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (fileId.isBlank()) {
            errorCallback(ErrorResponse.parseError("Empty parameter: file_id"))
            return
        }
        if (exportPath != null && exportPath.isBlank()) {
            errorCallback(ErrorResponse.parseError("Empty parameter: export_path"))
            return
        }
        val fields = mutableMapOf("file_id" to fileId)
        exportPath?.let { fields["export_path"] = it }
        postBulkRootForm("/exportSFTPfile", fields, responseCallback, errorCallback)
    }

    /**
     * POSTs one of the eight documented S3 / SFTP file jobs on the bulkapi root.
     *
     * Booleans go on the wire as the strings "true"/"false", and an option the
     * caller left unset is not sent at all.
     *
     * LIVE: file_name and return_url are required on every one of them,
     * validateS3file, scoreS3file and validateSFTPfile included, although the
     * docs list them as optional. Without a return_url the API answers 400
     * "Provided return URL is not permitted: None"; without a file_name it
     * answers "This field is required". Both are checked here, once, so that
     * every job rejects the same call with the same message.
     */
    private fun bulkRootFileJob(
        path: String,
        fileName: String?,
        returnUrl: String?,
        extraFields: Map<String, String>,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (fileName.isNullOrBlank()) {
            errorCallback(ErrorResponse.parseError("Empty parameter: file_name"))
            return
        }
        if (returnUrl.isNullOrBlank()) {
            errorCallback(ErrorResponse.parseError("Empty parameter: return_url"))
            return
        }
        val fields = mutableMapOf(
            "file_name" to fileName,
            "return_url" to returnUrl,
        )
        fields.putAll(extraFields)
        postBulkRootForm(path, fields, responseCallback, errorCallback)
    }

    /** The four jobs that take nothing beyond the shared [ZBBulkFileJobOptions]. */
    private fun bulkRootFileJob(
        path: String,
        options: ZBBulkFileJobOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        val fields = mutableMapOf("has_header_row" to options.hasHeaderRow.toString())
        options.removeDuplicates?.let { fields["remove_duplicates"] = it.toString() }
        bulkRootFileJob(path, options.fileName, options.returnUrl, fields, responseCallback, errorCallback)
    }

    private fun buildScoreS3Fields(options: ZBScoreS3FileOptions): Map<String, String> {
        val fields = mutableMapOf<String, String>()
        putOptionalBool(fields, "has_header_row", options.hasHeaderRow)
        putOptionalBool(fields, "remove_duplicates", options.removeDuplicates)
        putOptionalBool(fields, "remove_duplicate", options.removeDuplicate)
        options.emailAddressColumn?.let { fields["email_address_column"] = it.toString() }
        return fields
    }

    private fun buildValidateS3Fields(options: ZBValidateS3FileOptions): Map<String, String> {
        val fields = mutableMapOf<String, String>()
        putOptionalBool(fields, "has_header_row", options.hasHeaderRow)
        putOptionalBool(fields, "remove_duplicates", options.removeDuplicates)
        putOptionalBool(fields, "remove_duplicate", options.removeDuplicate)
        options.emailAddressColumn?.let { fields["email_address_column"] = it.toString() }
        if (options.firstNameColumn > 0) {
            fields["first_name_column"] = options.firstNameColumn.toString()
        }
        if (options.lastNameColumn > 0) {
            fields["last_name_column"] = options.lastNameColumn.toString()
        }
        if (options.genderColumn > 0) {
            fields["gender_column"] = options.genderColumn.toString()
        }
        if (options.ipAddressColumn > 0) {
            fields["ip_address_column"] = options.ipAddressColumn.toString()
        }
        options.allowPhase2?.let { fields["allow_phase_2"] = it.toString() }
        return fields
    }

    private fun putOptionalBool(fields: MutableMap<String, String>, key: String, value: Boolean?) {
        if (value != null) {
            fields[key] = value.toString()
        }
    }

    fun inboxPlacementRequest(
        method: String,
        path: String,
        query: Map<String, String>,
        body: Map<String, Any?>?,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        val key = ZeroBounceSDK.priority3ApiKey() ?: return
        // LIVE: the Inbox Placement Tester authenticates with a Bearer header.
        // Sent as an api_key parameter the request is rejected as unauthorized,
        // and the key would end up in a query string that gets logged.
        val queryString = query.entries.joinToString("&") { (k, v) ->
            "${ZeroBounceSDK.encQuery(k)}=${ZeroBounceSDK.encQuery(v)}"
        }
        val suffix = if (queryString.isEmpty()) "" else "?$queryString"
        ZeroBounceSDK.priority3JsonRequest(
            method,
            "${ZeroBounceSDK.priority3InboxPlacementBaseUrl()}$path$suffix",
            body,
            responseCallback,
            errorCallback,
            mapOf("Authorization" to "Bearer $key"),
        )
    }

    private fun postBulkRootForm(
        path: String,
        fields: Map<String, String>,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = postBulkRootFormAt(
        "${ZeroBounceSDK.priority3BulkApiRootUrl()}$path",
        fields,
        responseCallback,
        errorCallback,
    )

    /** A form POST to an arbitrary bulk URL, for the endpoints off the root. */
    private fun postBulkRootFormAt(
        url: String,
        fields: Map<String, String>,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        val key = ZeroBounceSDK.priority3ApiKey() ?: return
        val form = fields.toMutableMap()
        form["api_key"] = key
        ZeroBounceSDK.priority3FormPost(url, form, responseCallback, errorCallback)
    }

    private fun sendBulkRootMultipart(
        url: String,
        file: File,
        fields: Map<String, String>,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (!file.exists()) {
            errorCallback(ErrorResponse.parseError("File does not exist"))
            return
        }
        val key = ZeroBounceSDK.priority3ApiKey() ?: return
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
        for ((name, value) in fields) {
            builder.addFormDataPart(name, value)
        }
        builder.addFormDataPart("api_key", key)
        builder.addFormDataPart(
            "file",
            file.name,
            file.asRequestBody("application/octet-stream".toMediaTypeOrNull()),
        )
        ZeroBounceSDK.priority3EnqueueMultipart(url, builder.build(), responseCallback, errorCallback)
    }

    private fun sendBulkRootMultipartStream(
        url: String,
        inputStream: InputStream,
        fileName: String,
        fields: Map<String, String>,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        val key = ZeroBounceSDK.priority3ApiKey() ?: return
        val fileBody = inputStream.readBytes().toRequestBody("text/csv".toMediaTypeOrNull())
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
        for ((name, value) in fields) {
            builder.addFormDataPart(name, value)
        }
        builder.addFormDataPart("api_key", key)
        builder.addFormDataPart("file", fileName, fileBody)
        ZeroBounceSDK.priority3EnqueueMultipart(url, builder.build(), responseCallback, errorCallback)
    }

    companion object {
        internal fun looksLikeJsonError(bodyStr: String): Boolean {
            val trimmed = bodyStr.trim()
            if (!trimmed.startsWith("{")) return false
            return try {
                val obj = JsonParser.parseString(trimmed).asJsonObject
                if (obj.has("success") && obj.get("success").isJsonPrimitive && !obj.get("success").asBoolean) {
                    true
                } else {
                    obj.has("message") || obj.has("error") || obj.has("error_message")
                }
            } catch (_: Exception) {
                false
            }
        }

        internal fun writeDownloadFile(downloadPath: String, bytes: ByteArray): ZBGetFileResponse {
            val out = File(downloadPath)
            out.parentFile?.mkdirs()
            FileOutputStream(out).use { it.write(bytes) }
            return ZBGetFileResponse(out.path)
        }
    }
}
