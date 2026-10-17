package com.zerobounce.android

/**
 * Optional query/body parameters for GET /validate and POST /validatebatch.
 */
data class ZBValidateOptions(
    /** Validation timeout in seconds (3–60 for single validate, 10–120 for batch). */
    val timeout: Int? = null,
    /** Maps to activity_data. */
    val activityData: Boolean? = null,
    /** Maps to verify_plus. */
    val verifyPlus: Boolean? = null,
)
