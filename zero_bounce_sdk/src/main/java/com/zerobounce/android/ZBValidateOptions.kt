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
    /** Maps to domain_info. Fills domainWebsiteExists and domainRegistrantCompanyName. */
    val domainInfo: Boolean? = null,
    /** Maps to greylist_processing. Retries a greylisted address in the background. */
    val greylistProcessing: Boolean? = null,
    /**
     * Maps to callback_url: where the greylist retry posts its result. It needs
     * [greylistProcessing] set to true and is rejected without it, and when
     * blank, before any request is sent.
     */
    val callbackUrl: String? = null,
    /** When true, uses form-encoded POST for /validate. */
    val usePost: Boolean = false,
)
