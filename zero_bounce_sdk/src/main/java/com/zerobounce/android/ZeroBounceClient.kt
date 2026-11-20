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
 *
 * Every request method comes from [ZeroBounceApi]. Code that should be testable without the
 * network can depend on that interface and receive a mock in tests.
 */
class ZeroBounceClient : ZeroBounceApi {
    private val state = SdkState()

    @get:JvmSynthetic
    @set:JvmSynthetic
    internal var transportInterceptor: Interceptor?
        get() = use { ZeroBounceSDK.transportInterceptor }
        set(value) {
            use { ZeroBounceSDK.transportInterceptor = value }
        }

    fun initialize(apiKey: String, apiBaseUrl: String? = null) = use {
        ZeroBounceSDK.initialize(apiKey, apiBaseUrl)
    }

    override fun validate(
        email: String,
        ipAddress: String?,
        responseCallback: (response: ZBValidateResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.validate(email, ipAddress, responseCallback, errorCallback) }

    override fun validate(
        email: String,
        ipAddress: String?,
        options: ZBValidateOptions?,
        responseCallback: (response: ZBValidateResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.validate(email, ipAddress, options, responseCallback, errorCallback) }

    override fun validateBatch(
        emails: List<ZBValidateBatchData>,
        responseCallback: (response: ZBValidateBatchResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.validateBatch(emails, responseCallback, errorCallback) }

    override fun validateBatch(
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
    override fun guessFormat(
        domain: String,
        firstName: String?,
        middleName: String?,
        lastName: String?,
        responseCallback: (response: ZBEmailFinderResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use {
        ZeroBounceSDK.guessFormat(domain, firstName, middleName, lastName, responseCallback, errorCallback)
    }

    override fun findEmail(
        firstName: String,
        domain: String?,
        companyName: String?,
        middleName: String?,
        lastName: String?,
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

    override fun findDomain(
        domain: String?,
        companyName: String?,
        responseCallback: (response: ZBFindDomainResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.findDomain(domain, companyName, responseCallback, errorCallback) }

    override fun getApiUsage(
        startDate: Date,
        endDate: Date,
        responseCallback: (response: ZBGetApiUsageResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.getApiUsage(startDate, endDate, responseCallback, errorCallback) }

    override fun getCredits(
        responseCallback: (response: ZBCreditsResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.getCredits(responseCallback, errorCallback) }

    @Throws(ZBException::class)
    override fun sendFile(
        context: Context,
        file: File,
        emailAddressColumnIndex: Int,
        firstNameColumnIndex: Int?,
        lastNameColumnIndex: Int?,
        genderColumnIndex: Int?,
        ipAddressColumnIndex: Int?,
        returnUrl: String?,
        hasHeaderRow: Boolean,
        removeDuplicate: Boolean?,
        allowPhase2: Boolean?,
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
    override fun sendFile(
        inputStream: InputStream,
        fileName: String,
        emailAddressColumnIndex: Int,
        firstNameColumnIndex: Int?,
        lastNameColumnIndex: Int?,
        genderColumnIndex: Int?,
        ipAddressColumnIndex: Int?,
        returnUrl: String?,
        hasHeaderRow: Boolean,
        removeDuplicate: Boolean?,
        allowPhase2: Boolean?,
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
    override fun scoringSendFile(
        context: Context,
        file: File,
        emailAddressColumnIndex: Int,
        returnUrl: String?,
        hasHeaderRow: Boolean,
        removeDuplicate: Boolean?,
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
    override fun scoringSendFile(
        inputStream: InputStream,
        fileName: String,
        emailAddressColumnIndex: Int,
        returnUrl: String?,
        hasHeaderRow: Boolean,
        removeDuplicate: Boolean?,
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

    override fun fileStatus(
        fileId: String,
        responseCallback: (response: ZBFileStatusResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.fileStatus(fileId, responseCallback, errorCallback) }

    override fun scoringFileStatus(
        fileId: String,
        responseCallback: (response: ZBFileStatusResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.scoringFileStatus(fileId, responseCallback, errorCallback) }

    override fun getFile(
        context: Context,
        fileId: String,
        responseCallback: (response: ZBGetFileResponse) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
        downloadType: ZBDownloadType?,
        activityData: Boolean?,
    ) = use {
        ZeroBounceSDK.getFile(context, fileId, responseCallback, errorCallback, downloadType, activityData)
    }

    override fun scoringGetFile(
        context: Context,
        fileId: String,
        responseCallback: (response: ZBGetFileResponse) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
        downloadType: ZBDownloadType?,
    ) = use {
        ZeroBounceSDK.scoringGetFile(context, fileId, responseCallback, errorCallback, downloadType)
    }

    override fun deleteFile(
        fileId: String,
        successCallback: (response: ZBDeleteFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.deleteFile(fileId, successCallback, errorCallback) }

    override fun scoringDeleteFile(
        fileId: String,
        successCallback: (response: ZBDeleteFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.scoringDeleteFile(fileId, successCallback, errorCallback) }

    override fun getActivityData(
        email: String,
        responseCallback: (response: ZBActivityDataResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.getActivityData(email, responseCallback, errorCallback) }

    override fun scoreEmail(
        email: String,
        responseCallback: (response: ZBScoreEmailResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.scoreEmail(email, responseCallback, errorCallback) }

    override fun scoreEmailPost(
        email: String,
        responseCallback: (response: ZBScoreEmailResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.scoreEmailPost(email, responseCallback, errorCallback) }

    override fun sendEmailFinderFile(
        file: File,
        domainColumn: Int,
        options: ZBEmailFinderSendFileOptions?,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.sendEmailFinderFile(file, domainColumn, options, responseCallback, errorCallback) }

    override fun sendEmailFinderFileStream(
        inputStream: InputStream,
        fileName: String,
        domainColumn: Int,
        options: ZBEmailFinderSendFileOptions?,
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

    override fun emailFinderFileStatus(
        fileId: String,
        responseCallback: (response: ZBFileStatusResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.emailFinderFileStatus(fileId, responseCallback, errorCallback) }

    override fun emailFinderGetFile(
        fileId: String,
        downloadPath: String,
        responseCallback: (response: ZBGetFileResponse) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.emailFinderGetFile(fileId, downloadPath, responseCallback, errorCallback) }

    override fun emailFinderDeleteFile(
        fileId: String,
        responseCallback: (response: ZBDeleteFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.emailFinderDeleteFile(fileId, responseCallback, errorCallback) }

    override fun sendDomainSearchFile(
        file: File,
        domainColumn: Int,
        options: ZBDomainSearchSendFileOptions?,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.sendDomainSearchFile(file, domainColumn, options, responseCallback, errorCallback) }

    override fun sendDomainSearchFileStream(
        inputStream: InputStream,
        fileName: String,
        domainColumn: Int,
        options: ZBDomainSearchSendFileOptions?,
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

    override fun domainSearchFileStatus(
        fileId: String,
        responseCallback: (response: ZBFileStatusResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.domainSearchFileStatus(fileId, responseCallback, errorCallback) }

    override fun domainSearchGetFile(
        fileId: String,
        downloadPath: String,
        responseCallback: (response: ZBGetFileResponse) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.domainSearchGetFile(fileId, downloadPath, responseCallback, errorCallback) }

    override fun domainSearchDeleteFile(
        fileId: String,
        responseCallback: (response: ZBDeleteFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.domainSearchDeleteFile(fileId, responseCallback, errorCallback) }

    override fun sendListEvaluatorFile(
        file: File,
        emailAddressColumn: Int,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.sendListEvaluatorFile(file, emailAddressColumn, responseCallback, errorCallback) }

    override fun sendListEvaluatorFileStream(
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

    override fun getListEvaluatorResult(
        fileId: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.getListEvaluatorResult(fileId, responseCallback, errorCallback) }

    override fun sendEvaluatedFile(
        fileId: String,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.sendEvaluatedFile(fileId, responseCallback, errorCallback) }

    override fun addFilter(
        rule: String,
        target: String,
        value: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.addFilter(rule, target, value, responseCallback, errorCallback) }

    override fun listFilters(
        usePost: Boolean,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.listFilters(usePost, responseCallback, errorCallback) }

    override fun deleteFilter(
        rule: String,
        target: String,
        value: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.deleteFilter(rule, target, value, responseCallback, errorCallback) }

    override fun listS3Files(
        bucket: String?,
        search: String?,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.listS3Files(bucket, search, responseCallback, errorCallback) }

    override fun validateS3File(
        options: ZBValidateS3FileOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.validateS3File(options, responseCallback, errorCallback) }

    override fun scoreS3File(
        options: ZBScoreS3FileOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.scoreS3File(options, responseCallback, errorCallback) }

    override fun exportS3File(
        fileId: String,
        exportBucket: String?,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.exportS3File(fileId, exportBucket, responseCallback, errorCallback) }

    override fun emailFinderS3File(
        options: ZBBulkFileJobOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.emailFinderS3File(options, responseCallback, errorCallback) }

    override fun domainSearchS3File(
        options: ZBBulkFileJobOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.domainSearchS3File(options, responseCallback, errorCallback) }

    override fun validateSFTPFile(
        options: ZBBulkFileJobOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.validateSFTPFile(options, responseCallback, errorCallback) }

    override fun scoreSFTPFile(
        options: ZBBulkFileJobOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.scoreSFTPFile(options, responseCallback, errorCallback) }

    override fun emailFinderSFTPFile(
        options: ZBBulkFileJobOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.emailFinderSFTPFile(options, responseCallback, errorCallback) }

    override fun domainSearchSFTPFile(
        options: ZBBulkFileJobOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.domainSearchSFTPFile(options, responseCallback, errorCallback) }

    override fun exportSFTPFile(
        fileId: String,
        exportPath: String?,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.exportSFTPFile(fileId, exportPath, responseCallback, errorCallback) }

    override fun getInboxPlacementProviders(
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.getInboxPlacementProviders(responseCallback, errorCallback) }

    override fun getInboxPlacementTestTypes(
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.getInboxPlacementTestTypes(responseCallback, errorCallback) }

    override fun createInboxPlacementTest(
        name: String,
        options: ZBCreateInboxPlacementTestOptions?,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.createInboxPlacementTest(name, options, responseCallback, errorCallback) }

    override fun getInboxPlacementTest(
        testId: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.getInboxPlacementTest(testId, responseCallback, errorCallback) }

    override fun listInboxPlacementTests(
        options: ZBListInboxPlacementTestsOptions?,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.listInboxPlacementTests(options, responseCallback, errorCallback) }

    override fun deleteInboxPlacementTest(
        testId: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.deleteInboxPlacementTest(testId, responseCallback, errorCallback) }

    override fun getInboxPlacementWebhook(
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    ) = use { ZeroBounceSDK.getInboxPlacementWebhook(responseCallback, errorCallback) }

    override fun setInboxPlacementWebhook(
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
