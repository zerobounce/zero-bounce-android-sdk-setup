package com.zerobounce.android

/**
 * The optional columns of a bulk email finder file.
 *
 * One of [firstNameColumn] and [fullNameColumn] is required: without a name
 * column the API has nothing to build an address from.
 */
data class ZBEmailFinderSendFileOptions(
    val firstNameColumn: Int = 0,
    val lastNameColumn: Int = 0,
    val middleNameColumn: Int = 0,
    val fullNameColumn: Int = 0,
    val hasHeaderRow: Boolean? = null,
)

data class ZBDomainSearchSendFileOptions(
    val hasHeaderRow: Boolean? = null,
)

/**
 * The fields of the S3 validation job.
 *
 * LIVE: [returnUrl] is required, although the docs list it as optional.
 * Without it the API answers 400 "Provided return URL is not permitted: None".
 */
data class ZBValidateS3FileOptions(
    val fileName: String,
    val hasHeaderRow: Boolean? = null,
    val removeDuplicates: Boolean? = null,
    val removeDuplicate: Boolean? = null,
    val emailAddressColumn: Int? = null,
    val returnUrl: String? = null,
    val firstNameColumn: Int = 0,
    val lastNameColumn: Int = 0,
    val genderColumn: Int = 0,
    val ipAddressColumn: Int = 0,
    val allowPhase2: Boolean? = null,
)

/**
 * The fields of the S3 scoring job.
 *
 * LIVE: [returnUrl] is required, as it is on every other S3 and SFTP job.
 * Without it the API rejects the call and it can never succeed.
 */
data class ZBScoreS3FileOptions(
    val fileName: String,
    val hasHeaderRow: Boolean? = null,
    val removeDuplicates: Boolean? = null,
    val removeDuplicate: Boolean? = null,
    val emailAddressColumn: Int? = null,
    val returnUrl: String? = null,
)

/**
 * The documented fields shared by the six S3 and SFTP/FTP file jobs.
 *
 * LIVE: [fileName] and [returnUrl] are required by all six, including
 * [ZeroBounceSDK.validateSFTPFile], for which the docs call [fileName]
 * optional. Both are checked before a request is sent.
 */
data class ZBBulkFileJobOptions(
    val fileName: String? = null,
    val hasHeaderRow: Boolean = true,
    val removeDuplicates: Boolean? = null,
    val returnUrl: String? = null,
)

/**
 * The optional fields of a new Inbox Placement test. Only what is set is sent.
 *
 * [testType] must be 1, 2 or 3, and type 1 (inbox placement) also needs a
 * [senderEmail]: the address the seeds are mailed from.
 */
data class ZBCreateInboxPlacementTestOptions(
    val testType: Int? = null,
    val senderEmail: String? = null,
    val providerIds: List<Int>? = null,
    val seedIds: List<Int>? = null,
    val providers: List<Map<String, Any?>>? = null,
    val webhookUrl: String? = null,
)

/**
 * The documented filters of the Inbox Placement test list.
 *
 * [status] is "running" or "finished", [createdAfter] and [createdBefore] are
 * dates, and [pageSize] must be 1 to 100.
 *
 * LIVE: [pageSize] goes on the wire as page_size. Sent as pageSize it is
 * silently ignored and the whole list comes back.
 */
data class ZBListInboxPlacementTestsOptions(
    val status: String? = null,
    val page: Int? = null,
    val pageSize: Int? = null,
    val createdAfter: String? = null,
    val createdBefore: String? = null,
)
