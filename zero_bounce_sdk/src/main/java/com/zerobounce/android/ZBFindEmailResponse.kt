package com.zerobounce.android

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

/**
 * The model class used for the GET /guessformat request for find email.
 */
data class ZBFindEmailResponse(

    @JsonAdapter(value = StrictTypes.NullAsEmptyText::class, nullSafe = false)
    var email: String,

    @SerializedName("email_confidence")
    @JsonAdapter(value = StrictTypes.NullAsEmptyText::class, nullSafe = false)
    var emailConfidence: String,

    @JsonAdapter(value = StrictTypes.NullAsEmptyText::class, nullSafe = false)
    var domain: String,

    @SerializedName("company_name")
    @JsonAdapter(value = StrictTypes.NullAsEmptyText::class, nullSafe = false)
    var companyName: String,

    @SerializedName("did_you_mean")
    @JsonAdapter(value = StrictTypes.NullAsEmptyText::class, nullSafe = false)
    var didYouMean: String,

    @SerializedName("failure_reason")
    @JsonAdapter(value = StrictTypes.NullAsEmptyText::class, nullSafe = false)
    var failureReason: String,
) : JSONConvertable