package com.zerobounce.android

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

/**
 * The model class used for the GET /guessformat request for find domain.
 */
data class ZBFindDomainResponse(

    @JsonAdapter(value = StrictTypes.NullAsEmptyText::class, nullSafe = false)
    var email: String,

    @JsonAdapter(value = StrictTypes.NullAsEmptyText::class, nullSafe = false)
    var domain: String,

    @SerializedName("company_name")
    @JsonAdapter(value = StrictTypes.NullAsEmptyText::class, nullSafe = false)
    var companyName: String,

    @JsonAdapter(value = StrictTypes.NullAsEmptyText::class, nullSafe = false)
    var format: String,

    @JsonAdapter(value = StrictTypes.NullAsEmptyText::class, nullSafe = false)
    var confidence: String,

    @SerializedName("did_you_mean")
    @JsonAdapter(value = StrictTypes.NullAsEmptyText::class, nullSafe = false)
    var didYouMean: String,

    @SerializedName("failure_reason")
    @JsonAdapter(value = StrictTypes.NullAsEmptyText::class, nullSafe = false)
    var failureReason: String,

    @SerializedName("other_domain_formats")
    @JsonAdapter(value = StrictTypes.NullAsNoRows::class, nullSafe = false)
    var otherDomainFormats: List<DomainFormat>
) : JSONConvertable