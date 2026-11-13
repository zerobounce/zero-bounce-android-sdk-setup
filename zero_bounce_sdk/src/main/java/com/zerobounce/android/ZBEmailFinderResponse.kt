package com.zerobounce.android

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

/**
 * The model class used for the GET /guessformat request.
 */
data class ZBEmailFinderResponse(

    @JsonAdapter(value = StrictTypes.NullAsEmptyText::class, nullSafe = false)
    var email: String = "",

    @JsonAdapter(value = StrictTypes.NullAsEmptyText::class, nullSafe = false)
    var domain: String = "",

    @JsonAdapter(value = StrictTypes.NullAsEmptyText::class, nullSafe = false)
    var format: String = "",

    @JsonAdapter(value = StrictTypes.NullAsEmptyText::class, nullSafe = false)
    var status: String = "",

    @SerializedName("sub_status")
    @JsonAdapter(value = StrictTypes.NullAsEmptyText::class, nullSafe = false)
    var subStatus: String = "",

    @JsonAdapter(value = StrictTypes.NullAsEmptyText::class, nullSafe = false)
    var confidence: String = "",

    @SerializedName("did_you_mean")
    @JsonAdapter(value = StrictTypes.NullAsEmptyText::class, nullSafe = false)
    var didYouMean: String = "",

    @SerializedName("failure_reason")
    @JsonAdapter(value = StrictTypes.NullAsEmptyText::class, nullSafe = false)
    var failureReason: String = "",

    @SerializedName("other_domain_formats")
    @JsonAdapter(value = StrictTypes.NullAsNoRows::class, nullSafe = false)
    var otherDomainFormats: List<DomainFormat> = emptyList()
) : JSONConvertable