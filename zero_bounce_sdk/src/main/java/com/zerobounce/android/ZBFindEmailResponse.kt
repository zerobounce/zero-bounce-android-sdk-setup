package com.zerobounce.android

import com.google.gson.annotations.SerializedName

/**
 * The model class used for the GET /guessformat request for find email.
 */
class ZBFindEmailResponse(

    var email: String,

    @SerializedName("email_confidence")
    var emailConfidence: String,

    var domain: String,

    @SerializedName("company_name")
    var companyName: String,

    @SerializedName("did_you_mean")
    var didYouMean: String,

    @SerializedName("failure_reason")
    var failureReason: String,
) : JSONConvertable