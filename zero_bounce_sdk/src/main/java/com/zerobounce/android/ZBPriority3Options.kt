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
