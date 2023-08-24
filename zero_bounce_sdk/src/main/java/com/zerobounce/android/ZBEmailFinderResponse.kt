package com.zerobounce.android

import com.google.gson.annotations.SerializedName

/**
 * The model class used for the GET /guessformat request.
 */
data class ZBEmailFinderResponse(

    var email: String,

    var domain: String,

    var format: String,

    var status: String,

    @SerializedName("sub_status")
    var subStatus: String,

    var confidence: String,

    @SerializedName("did_you_mean")
    var didYouMean: String,

    @SerializedName("failure_reason")
    var failureReason: String,

    @SerializedName("other_domain_formats")
    var otherDomainFormats: List<DomainFormat>
) : JSONConvertable