package com.zerobounce.android

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

data class ZBScoreS3FileOptions(
    val fileName: String,
    val hasHeaderRow: Boolean? = null,
    val removeDuplicates: Boolean? = null,
    val removeDuplicate: Boolean? = null,
    val emailAddressColumn: Int? = null,
)

/**
 * The documented fields shared by the six S3 and SFTP/FTP file jobs.
 *
 * [fileName] is required by every job except [ZeroBounceSDK.validateSFTPFile],
 * where the docs list it as optional: leaving it null there uses the input
 * bucket configured in your ZeroBounce settings.
 */
data class ZBBulkFileJobOptions(
    val fileName: String? = null,
    val hasHeaderRow: Boolean = true,
    val removeDuplicates: Boolean? = null,
    val returnUrl: String? = null,
)

/** The optional fields of a new Inbox Placement test. Only what is set is sent. */
data class ZBCreateInboxPlacementTestOptions(
    val testType: Int? = null,
    val senderEmail: String? = null,
    val providerIds: List<Int>? = null,
    val seedIds: List<Int>? = null,
    val providers: List<Map<String, Any?>>? = null,
    val webhookUrl: String? = null,
)

/** The documented filters of the Inbox Placement test list. */
data class ZBListInboxPlacementTestsOptions(
    val status: String? = null,
    val page: Int? = null,
    val pageSize: Int? = null,
)
