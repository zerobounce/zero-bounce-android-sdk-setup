package com.zerobounce.android

import com.google.gson.annotations.SerializedName

/**
 * The model class used for the GET /guessformat request.
 */
data class ZBEmailFinderResponse(

    var email: String,

    @SerializedName("email_confidence")
    var emailConfidence: String,

    var domain: String,

    @SerializedName("company_name")
    var companyName: String? = null,

    var format: String? = null,

    var confidence: String? = null,

    @SerializedName("did_you_mean")
    var didYouMean: String,

    @SerializedName("failure_reason")
    var failureReason: String,

    @SerializedName("other_domain_formats")
    var otherDomainFormats: List<DomainFormat>
) : JSONConvertable