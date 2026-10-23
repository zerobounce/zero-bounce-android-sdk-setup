package com.zerobounce.android

import android.content.Context
import com.google.gson.JsonElement
import okhttp3.Interceptor
import java.io.File
import java.io.InputStream
import java.util.Date

/**
 * One ZeroBounce API key. Construct a separate client per key. [ZeroBounceSDK] is a single shared
 * client and must not be reused across keys.
 */
class ZeroBounceClient {
    private val state = SdkState()

    internal var transportInterceptor: Interceptor?
        get() = use { ZeroBounceSDK.transportInterceptor }
        set(value) {
            use { ZeroBounceSDK.transportInterceptor = value }
        }

    fun initialize(apiKey: String, apiBaseUrl: String? = null) = use {
        ZeroBounceSDK.initialize(apiKey, apiBaseUrl)
    }

    fun validate(
        email: String,
        ipAddress: String?,
        responseCallback: (response: ZBValidateResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.validate(email, ipAddress, responseCallback, errorCallback) }

    fun validate(
        email: String,
        ipAddress: String?,
        options: ZBValidateOptions?,
        responseCallback: (response: ZBValidateResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.validate(email, ipAddress, options, responseCallback, errorCallback) }

    fun validateBatch(
        emails: List<ZBValidateBatchData>,
        responseCallback: (response: ZBValidateBatchResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.validateBatch(emails, responseCallback, errorCallback) }

    fun validateBatch(
        emails: List<ZBValidateBatchData>,
        options: ZBValidateOptions?,
        responseCallback: (response: ZBValidateBatchResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.validateBatch(emails, options, responseCallback, errorCallback) }

    @Deprecated(
        "The 'guessFormat' method has been split into two specific functions. " +
            "If you are finding a person's email, use 'findEmail()'. " +
            "If you are only determining the domain's email pattern, use 'findDomain()'.",
        level = DeprecationLevel.WARNING,
    )
    fun guessFormat(
        domain: String,
        firstName: String? = null,
        middleName: String? = null,
        lastName: String? = null,
        responseCallback: (response: ZBEmailFinderResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use {
        ZeroBounceSDK.guessFormat(domain, firstName, middleName, lastName, responseCallback, errorCallback)
    }

    fun findEmail(
        firstName: String,
        domain: String? = null,
        companyName: String? = null,
        middleName: String? = null,
        lastName: String? = null,
        responseCallback: (response: ZBFindEmailResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use {
        ZeroBounceSDK.findEmail(
            firstName,
            domain,
            companyName,
            middleName,
            lastName,
            responseCallback,
            errorCallback,
        )
    }

    fun findDomain(
        domain: String? = null,
        companyName: String? = null,
        responseCallback: (response: ZBFindDomainResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.findDomain(domain, companyName, responseCallback, errorCallback) }

    fun getApiUsage(
        startDate: Date,
        endDate: Date,
        responseCallback: (response: ZBGetApiUsageResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.getApiUsage(startDate, endDate, responseCallback, errorCallback) }

    fun getCredits(
        responseCallback: (response: ZBCreditsResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.getCredits(responseCallback, errorCallback) }

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
    ) = use {
        ZeroBounceSDK.sendFile(
            context,
            file,
            emailAddressColumnIndex,
            firstNameColumnIndex,
            lastNameColumnIndex,
            genderColumnIndex,
            ipAddressColumnIndex,
            returnUrl,
            hasHeaderRow,
            removeDuplicate,
            allowPhase2,
            responseCallback,
            errorCallback,
        )
    }

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
    ) = use {
        ZeroBounceSDK.sendFile(
            inputStream,
            fileName,
            emailAddressColumnIndex,
            firstNameColumnIndex,
            lastNameColumnIndex,
            genderColumnIndex,
            ipAddressColumnIndex,
            returnUrl,
            hasHeaderRow,
            removeDuplicate,
            allowPhase2,
            responseCallback,
            errorCallback,
        )
    }

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
    ) = use {
        ZeroBounceSDK.scoringSendFile(
            context,
            file,
            emailAddressColumnIndex,
            returnUrl,
            hasHeaderRow,
            removeDuplicate,
            responseCallback,
            errorCallback,
        )
    }

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
    ) = use {
        ZeroBounceSDK.scoringSendFile(
            inputStream,
            fileName,
            emailAddressColumnIndex,
            returnUrl,
            hasHeaderRow,
            removeDuplicate,
            responseCallback,
            errorCallback,
        )
    }

    fun fileStatus(
        fileId: String,
        responseCallback: (response: ZBFileStatusResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.fileStatus(fileId, responseCallback, errorCallback) }

    fun scoringFileStatus(
        fileId: String,
        responseCallback: (response: ZBFileStatusResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.scoringFileStatus(fileId, responseCallback, errorCallback) }

    fun getFile(
        context: Context,
        fileId: String,
        responseCallback: (response: ZBGetFileResponse) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
        downloadType: ZBDownloadType? = null,
        activityData: Boolean? = null,
    ) = use {
        ZeroBounceSDK.getFile(context, fileId, responseCallback, errorCallback, downloadType, activityData)
    }

    fun scoringGetFile(
        context: Context,
        fileId: String,
        responseCallback: (response: ZBGetFileResponse) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
        downloadType: ZBDownloadType? = null,
    ) = use {
        ZeroBounceSDK.scoringGetFile(context, fileId, responseCallback, errorCallback, downloadType)
    }

    fun deleteFile(
        fileId: String,
        successCallback: (response: ZBDeleteFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.deleteFile(fileId, successCallback, errorCallback) }

    fun scoringDeleteFile(
        fileId: String,
        successCallback: (response: ZBDeleteFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.scoringDeleteFile(fileId, successCallback, errorCallback) }

    fun getActivityData(
        email: String,
        responseCallback: (response: ZBActivityDataResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.getActivityData(email, responseCallback, errorCallback) }

    fun scoreEmail(
        email: String,
        responseCallback: (response: ZBScoreEmailResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.scoreEmail(email, responseCallback, errorCallback) }

    fun scoreEmailPost(
        email: String,
        responseCallback: (response: ZBScoreEmailResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.scoreEmailPost(email, responseCallback, errorCallback) }

    fun sendEmailFinderFile(
        file: File,
        domainColumn: Int,
        options: ZBEmailFinderSendFileOptions? = null,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.sendEmailFinderFile(file, domainColumn, options, responseCallback, errorCallback) }

    fun sendEmailFinderFileStream(
        inputStream: InputStream,
        fileName: String,
        domainColumn: Int,
        options: ZBEmailFinderSendFileOptions? = null,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use {
        ZeroBounceSDK.sendEmailFinderFileStream(
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
    ) = use { ZeroBounceSDK.emailFinderFileStatus(fileId, responseCallback, errorCallback) }

    fun emailFinderGetFile(
        fileId: String,
        downloadPath: String,
        responseCallback: (response: ZBGetFileResponse) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.emailFinderGetFile(fileId, downloadPath, responseCallback, errorCallback) }

    fun emailFinderDeleteFile(
        fileId: String,
        responseCallback: (response: ZBDeleteFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.emailFinderDeleteFile(fileId, responseCallback, errorCallback) }

    fun sendDomainSearchFile(
        file: File,
        domainColumn: Int,
        options: ZBDomainSearchSendFileOptions? = null,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.sendDomainSearchFile(file, domainColumn, options, responseCallback, errorCallback) }

    fun sendDomainSearchFileStream(
        inputStream: InputStream,
        fileName: String,
        domainColumn: Int,
        options: ZBDomainSearchSendFileOptions? = null,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use {
        ZeroBounceSDK.sendDomainSearchFileStream(
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
    ) = use { ZeroBounceSDK.domainSearchFileStatus(fileId, responseCallback, errorCallback) }

    fun domainSearchGetFile(
        fileId: String,
        downloadPath: String,
        responseCallback: (response: ZBGetFileResponse) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.domainSearchGetFile(fileId, downloadPath, responseCallback, errorCallback) }

    fun domainSearchDeleteFile(
        fileId: String,
        responseCallback: (response: ZBDeleteFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.domainSearchDeleteFile(fileId, responseCallback, errorCallback) }

    fun sendListEvaluatorFile(
        file: File,
        emailAddressColumn: Int,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.sendListEvaluatorFile(file, emailAddressColumn, responseCallback, errorCallback) }

    fun sendListEvaluatorFileStream(
        inputStream: InputStream,
        fileName: String,
        emailAddressColumn: Int,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use {
        ZeroBounceSDK.sendListEvaluatorFileStream(
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
    ) = use { ZeroBounceSDK.getListEvaluatorResult(fileId, responseCallback, errorCallback) }

    fun sendEvaluatedFile(
        file: File,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.sendEvaluatedFile(file, responseCallback, errorCallback) }

    fun addFilter(
        rule: String,
        target: String,
        value: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.addFilter(rule, target, value, responseCallback, errorCallback) }

    fun listFilters(
        usePost: Boolean,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.listFilters(usePost, responseCallback, errorCallback) }

    fun deleteFilter(
        rule: String,
        target: String,
        value: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.deleteFilter(rule, target, value, responseCallback, errorCallback) }

    fun listS3Files(
        bucket: String? = null,
        search: String? = null,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.listS3Files(bucket, search, responseCallback, errorCallback) }

    fun validateS3File(
        options: ZBValidateS3FileOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.validateS3File(options, responseCallback, errorCallback) }

    fun scoreS3File(
        options: ZBScoreS3FileOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.scoreS3File(options, responseCallback, errorCallback) }

    fun exportS3File(
        fileId: String,
        exportBucket: String?,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.exportS3File(fileId, exportBucket, responseCallback, errorCallback) }

    fun emailFinderS3File(
        options: ZBBulkFileJobOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.emailFinderS3File(options, responseCallback, errorCallback) }

    fun domainSearchS3File(
        options: ZBBulkFileJobOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.domainSearchS3File(options, responseCallback, errorCallback) }

    fun validateSFTPFile(
        options: ZBBulkFileJobOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.validateSFTPFile(options, responseCallback, errorCallback) }

    fun scoreSFTPFile(
        options: ZBBulkFileJobOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.scoreSFTPFile(options, responseCallback, errorCallback) }

    fun emailFinderSFTPFile(
        options: ZBBulkFileJobOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.emailFinderSFTPFile(options, responseCallback, errorCallback) }

    fun domainSearchSFTPFile(
        options: ZBBulkFileJobOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.domainSearchSFTPFile(options, responseCallback, errorCallback) }

    fun exportSFTPFile(
        fileId: String,
        exportPath: String?,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.exportSFTPFile(fileId, exportPath, responseCallback, errorCallback) }

    fun getInboxPlacementProviders(
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.getInboxPlacementProviders(responseCallback, errorCallback) }

    fun getInboxPlacementTestTypes(
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.getInboxPlacementTestTypes(responseCallback, errorCallback) }

    fun createInboxPlacementTest(
        name: String,
        options: ZBCreateInboxPlacementTestOptions? = null,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.createInboxPlacementTest(name, options, responseCallback, errorCallback) }

    fun getInboxPlacementTest(
        testId: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.getInboxPlacementTest(testId, responseCallback, errorCallback) }

    fun listInboxPlacementTests(
        options: ZBListInboxPlacementTestsOptions? = null,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.listInboxPlacementTests(options, responseCallback, errorCallback) }

    fun deleteInboxPlacementTest(
        testId: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.deleteInboxPlacementTest(testId, responseCallback, errorCallback) }

    fun getInboxPlacementWebhook(
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.getInboxPlacementWebhook(responseCallback, errorCallback) }

    fun setInboxPlacementWebhook(
        webhookUrl: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.setInboxPlacementWebhook(webhookUrl, responseCallback, errorCallback) }

    /**
     * Overrides the list evaluator base URL. It is a separate host rather than
     * a path built from the bulk base, so [initialize] alone does not change it.
     */
    fun setListEvaluatorBaseUrl(url: String) = use { ZeroBounceSDK.setListEvaluatorBaseUrl(url) }

    /** Overrides the Inbox Placement Tester base URL. See [setListEvaluatorBaseUrl]. */
    fun setInboxPlacementBaseUrl(url: String) = use { ZeroBounceSDK.setInboxPlacementBaseUrl(url) }

    private fun <T> use(block: () -> T): T = ZeroBounceSDK.useState(state, block)
}
