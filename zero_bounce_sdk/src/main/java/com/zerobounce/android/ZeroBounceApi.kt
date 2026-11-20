package com.zerobounce.android

import android.content.Context
import com.google.gson.JsonElement
import java.io.File
import java.io.InputStream
import java.util.Date

/**
 * Every request method of the ZeroBounce email validation API, as [ZeroBounceClient] offers them.
 *
 * Depend on this interface instead of [ZeroBounceClient] where your code makes the calls, and
 * pass a [ZeroBounceClient] in production. In tests, mock it with any mocking library (Mockito's
 * default proxy mock maker is enough) or a [java.lang.reflect.Proxy]: [ZeroBounceClient] is a
 * final class and needs mockk or Mockito's inline mock maker.
 *
 * Construction and configuration (`initialize`, `setListEvaluatorBaseUrl`,
 * `setInboxPlacementBaseUrl`) are not part of it.
 *
 * Every method answers through its response (or success) callback or its `errorCallback`.
 * Failures carry the HTTP status on [ErrorResponse.statusCode].
 *
 * New API methods are added to this interface in minor releases. It is meant to be mocked, not
 * implemented by your own classes: a class of yours that implements it may stop compiling when
 * a method is added.
 */
interface ZeroBounceApi {
    /**
     * Validates [email].
     *
     * @param ipAddress the IP address the email signed up from, or null
     */
    fun validate(
        email: String,
        ipAddress: String?,
        responseCallback: (response: ZBValidateResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Validates [email] with optional v2 query parameters such as `timeout`.
     */
    fun validate(
        email: String,
        ipAddress: String?,
        options: ZBValidateOptions?,
        responseCallback: (response: ZBValidateResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Validates a list of addresses in one request.
     *
     * A per-address error stays on the success result; an account-level error goes to [errorCallback].
     */
    fun validateBatch(
        emails: List<ZBValidateBatchData>,
        responseCallback: (response: ZBValidateBatchResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Validates a list of addresses in one request with optional parameters.
     */
    fun validateBatch(
        emails: List<ZBValidateBatchData>,
        options: ZBValidateOptions?,
        responseCallback: (response: ZBValidateBatchResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Finds an email address or a domain's email format.
     */
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
    )

    /**
     * Finds a person's email address from their name and a [domain] or [companyName].
     *
     * At least one of [domain] or [companyName] must be set.
     */
    fun findEmail(
        firstName: String,
        domain: String? = null,
        companyName: String? = null,
        middleName: String? = null,
        lastName: String? = null,
        responseCallback: (response: ZBFindEmailResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Finds the email format a [domain] or [companyName] uses.
     *
     * At least one of [domain] or [companyName] must be set.
     */
    fun findDomain(
        domain: String? = null,
        companyName: String? = null,
        responseCallback: (response: ZBFindDomainResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * API usage between [startDate] and [endDate].
     */
    fun getApiUsage(
        startDate: Date,
        endDate: Date,
        responseCallback: (response: ZBGetApiUsageResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * The account's remaining credits.
     */
    fun getCredits(
        responseCallback: (response: ZBCreditsResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Submits a [file] for bulk validation.
     *
     * Column indexes are 1-based; a null optional column is left out.
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
    )

    /**
     * Submits a CSV read from [inputStream] for bulk validation. The stream is not closed.
     *
     * Column indexes are 1-based; a null optional column is left out.
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
    )

    /**
     * Submits a [file] for bulk AI scoring.
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
    )

    /**
     * Submits a CSV read from [inputStream] for bulk AI scoring. The stream is not closed.
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
    )

    /**
     * Status of a bulk validation file.
     */
    fun fileStatus(
        fileId: String,
        responseCallback: (response: ZBFileStatusResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Status of a bulk scoring file.
     */
    fun scoringFileStatus(
        fileId: String,
        responseCallback: (response: ZBFileStatusResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Downloads the results of a bulk validation file.
     *
     * Read the saved path from [ZBGetFileResponse.localFilePath].
     */
    fun getFile(
        context: Context,
        fileId: String,
        responseCallback: (response: ZBGetFileResponse) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
        downloadType: ZBDownloadType? = null,
        activityData: Boolean? = null,
    )

    /**
     * Downloads the results of a bulk scoring file.
     */
    fun scoringGetFile(
        context: Context,
        fileId: String,
        responseCallback: (response: ZBGetFileResponse) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
        downloadType: ZBDownloadType? = null,
    )

    /**
     * Deletes a bulk validation file.
     */
    fun deleteFile(
        fileId: String,
        successCallback: (response: ZBDeleteFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Deletes a bulk scoring file.
     */
    fun scoringDeleteFile(
        fileId: String,
        successCallback: (response: ZBDeleteFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Days since [email] was last active.
     */
    fun getActivityData(
        email: String,
        responseCallback: (response: ZBActivityDataResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * AI score for one [email].
     */
    fun scoreEmail(
        email: String,
        responseCallback: (response: ZBScoreEmailResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * AI score for one [email]; sends the address in a POST body.
     */
    fun scoreEmailPost(
        email: String,
        responseCallback: (response: ZBScoreEmailResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Submits a [file] for bulk email finding.
     */
    fun sendEmailFinderFile(
        file: File,
        domainColumn: Int,
        options: ZBEmailFinderSendFileOptions? = null,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Submits a CSV read from [inputStream] for bulk email finding. The stream is not closed.
     */
    fun sendEmailFinderFileStream(
        inputStream: InputStream,
        fileName: String,
        domainColumn: Int,
        options: ZBEmailFinderSendFileOptions? = null,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Status of a bulk email finder file.
     */
    fun emailFinderFileStatus(
        fileId: String,
        responseCallback: (response: ZBFileStatusResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Downloads the results of a bulk email finder file to [downloadPath].
     */
    fun emailFinderGetFile(
        fileId: String,
        downloadPath: String,
        responseCallback: (response: ZBGetFileResponse) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Deletes a bulk email finder file.
     */
    fun emailFinderDeleteFile(
        fileId: String,
        responseCallback: (response: ZBDeleteFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Submits a [file] for bulk domain search.
     */
    fun sendDomainSearchFile(
        file: File,
        domainColumn: Int,
        options: ZBDomainSearchSendFileOptions? = null,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Submits a CSV read from [inputStream] for bulk domain search. The stream is not closed.
     */
    fun sendDomainSearchFileStream(
        inputStream: InputStream,
        fileName: String,
        domainColumn: Int,
        options: ZBDomainSearchSendFileOptions? = null,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Status of a bulk domain search file.
     */
    fun domainSearchFileStatus(
        fileId: String,
        responseCallback: (response: ZBFileStatusResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Downloads the results of a bulk domain search file to [downloadPath].
     */
    fun domainSearchGetFile(
        fileId: String,
        downloadPath: String,
        responseCallback: (response: ZBGetFileResponse) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Deletes a bulk domain search file.
     */
    fun domainSearchDeleteFile(
        fileId: String,
        responseCallback: (response: ZBDeleteFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Submits a [file] to the list evaluator.
     */
    fun sendListEvaluatorFile(
        file: File,
        emailAddressColumn: Int,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Submits a CSV read from [inputStream] to the list evaluator. The stream is not closed.
     */
    fun sendListEvaluatorFileStream(
        inputStream: InputStream,
        fileName: String,
        emailAddressColumn: Int,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * The list evaluator's result for [fileId], as JSON. A message-only answer is a success.
     */
    fun getListEvaluatorResult(
        fileId: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Sends a list-evaluated file on to bulk validation.
     */
    fun sendEvaluatedFile(
        fileId: String,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Adds an allow or block filter rule.
     */
    fun addFilter(
        rule: String,
        target: String,
        value: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Lists the account's filter rules, by POST when [usePost] is true and by GET otherwise.
     */
    fun listFilters(
        usePost: Boolean,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Deletes a filter rule.
     */
    fun deleteFilter(
        rule: String,
        target: String,
        value: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Lists files in a connected S3 [bucket], optionally matching [search].
     */
    fun listS3Files(
        bucket: String? = null,
        search: String? = null,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Starts bulk validation of a file in S3. Results go to `return_url`.
     */
    fun validateS3File(
        options: ZBValidateS3FileOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Starts bulk AI scoring of a file in S3.
     */
    fun scoreS3File(
        options: ZBScoreS3FileOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Exports the results of [fileId] to an S3 bucket (`export_bucket`).
     */
    fun exportS3File(
        fileId: String,
        exportBucket: String?,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Starts a bulk email finder job on a file in S3.
     */
    fun emailFinderS3File(
        options: ZBBulkFileJobOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Starts a bulk domain search job on a file in S3.
     */
    fun domainSearchS3File(
        options: ZBBulkFileJobOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Starts bulk validation of a file on an SFTP server.
     */
    fun validateSFTPFile(
        options: ZBBulkFileJobOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Starts bulk AI scoring of a file on an SFTP server.
     */
    fun scoreSFTPFile(
        options: ZBBulkFileJobOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Starts a bulk email finder job on a file on an SFTP server.
     */
    fun emailFinderSFTPFile(
        options: ZBBulkFileJobOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Starts a bulk domain search job on a file on an SFTP server.
     */
    fun domainSearchSFTPFile(
        options: ZBBulkFileJobOptions,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Exports the results of [fileId] to an SFTP path (`export_path`).
     */
    fun exportSFTPFile(
        fileId: String,
        exportPath: String?,
        responseCallback: (response: ZBSendFileResponse?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Inbox Placement Tester: the mailbox providers a test can seed.
     */
    fun getInboxPlacementProviders(
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Inbox Placement Tester: the available test types.
     */
    fun getInboxPlacementTestTypes(
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Inbox Placement Tester: creates a test named [name].
     */
    fun createInboxPlacementTest(
        name: String,
        options: ZBCreateInboxPlacementTestOptions? = null,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Inbox Placement Tester: one test and its results.
     */
    fun getInboxPlacementTest(
        testId: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Inbox Placement Tester: lists tests, one page at a time.
     */
    fun listInboxPlacementTests(
        options: ZBListInboxPlacementTestsOptions? = null,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Inbox Placement Tester: deletes a test.
     */
    fun deleteInboxPlacementTest(
        testId: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Inbox Placement Tester: the account's webhook URL.
     */
    fun getInboxPlacementWebhook(
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )

    /**
     * Inbox Placement Tester: sets the account's webhook URL.
     */
    fun setInboxPlacementWebhook(
        webhookUrl: String,
        responseCallback: (response: JsonElement?) -> Unit,
        errorCallback: (errorResponse: ErrorResponse?) -> Unit,
    )
}
