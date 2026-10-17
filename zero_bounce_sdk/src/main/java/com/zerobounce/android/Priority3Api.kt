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

    private fun buildEmailFinderFields(
        domainColumn: Int,
        options: ZBEmailFinderSendFileOptions?,
    ): Map<String, String> {
        val fields = mutableMapOf("domain_column" to domainColumn.toString())
        if (options != null) {
            if (options.hasHeaderRow == true) {
                fields["has_header_row"] = "true"
            }
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
        if (options?.hasHeaderRow == true) {
            fields["has_header_row"] = "true"
        }
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
            "${ZeroBounceSDK.priority3BulkApiBaseUrl()}/listevaluator/",
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
            "${ZeroBounceSDK.priority3BulkApiBaseUrl()}/listevaluator/",
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
        val key = ZeroBounceSDK.priority3ApiKey() ?: return
        ZeroBounceSDK.priority3GetJson(
            "${ZeroBounceSDK.priority3BulkApiBaseUrl()}/listevaluator/$fileId/",
            mapOf("api_key" to key),
            responseCallback,
            errorCallback,
        )
    }

    fun sendEvaluatedFile(
        file: File,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        sendBulkRootMultipart(
            "${ZeroBounceSDK.priority3BulkApiBaseUrl()}/send-evaluated-file/",
            file,
            emptyMap(),
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

    fun validateS3File(
        options: ZBValidateS3FileOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (options.fileName.isBlank()) {
            errorCallback(ErrorResponse.parseError("Empty parameter: file_name"))
            return
        }
        postBulkRootForm("/validateS3file", buildValidateS3Fields(options), responseCallback, errorCallback)
    }

    fun scoreS3File(
        options: ZBScoreS3FileOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (options.fileName.isBlank()) {
            errorCallback(ErrorResponse.parseError("Empty parameter: file_name"))
            return
        }
        val fields = mutableMapOf("file_name" to options.fileName)
        putOptionalBool(fields, "has_header_row", options.hasHeaderRow)
        putOptionalBool(fields, "remove_duplicates", options.removeDuplicates)
        putOptionalBool(fields, "remove_duplicate", options.removeDuplicate)
        options.emailAddressColumn?.let { fields["email_address_column"] = it.toString() }
        postBulkRootForm("/scoreS3file", fields, responseCallback, errorCallback)
    }

    fun exportS3File(
        fileId: String,
        exportBucket: String,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        if (fileId.isBlank()) {
            errorCallback(ErrorResponse.parseError("Empty parameter: file_id"))
            return
        }
        if (exportBucket.isBlank()) {
            errorCallback(ErrorResponse.parseError("Empty parameter: export_bucket"))
            return
        }
        postBulkRootForm(
            "/exportS3file",
            mapOf("file_id" to fileId, "export_bucket" to exportBucket),
            responseCallback,
            errorCallback,
        )
    }

    private fun buildValidateS3Fields(options: ZBValidateS3FileOptions): Map<String, String> {
        val fields = mutableMapOf("file_name" to options.fileName)
        putOptionalBool(fields, "has_header_row", options.hasHeaderRow)
        putOptionalBool(fields, "remove_duplicates", options.removeDuplicates)
        putOptionalBool(fields, "remove_duplicate", options.removeDuplicate)
        options.emailAddressColumn?.let { fields["email_address_column"] = it.toString() }
        options.returnUrl?.let { fields["return_url"] = it }
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

    private fun postBulkRootForm(
        path: String,
        fields: Map<String, String>,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) {
        val key = ZeroBounceSDK.priority3ApiKey() ?: return
        val form = fields.toMutableMap()
        form["api_key"] = key
        ZeroBounceSDK.priority3FormPost(
            "${ZeroBounceSDK.priority3BulkApiRootUrl()}$path",
            form,
            responseCallback,
            errorCallback,
        )
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
