package com.zerobounce.android

import com.google.gson.annotations.SerializedName

/**
 * The model class used for the GET /guessformat request for find domain.
 */
class ZBFindDomainResponse(

    var email: String,


    var domain: String,

    @SerializedName("company_name")
    var companyName: String,

    var format: String,

    var confidence: String,

    @SerializedName("did_you_mean")
    var didYouMean: String,

    @SerializedName("failure_reason")
    var failureReason: String,

    @SerializedName("other_domain_formats")
    var otherDomainFormats: List<DomainFormat>
) : JSONConvertable