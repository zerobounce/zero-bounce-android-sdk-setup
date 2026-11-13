package com.zerobounce.android

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

/**
 * The model class used for the GET /getcredits request.
 */
data class ZBCreditsResponse(
    /**
     * The balance as text. The API sends it as a JSON string (`"12345"`, `"-1"`)
     * or as a JSON integer (`2375323`, zerobounce.net's own example); both are
     * kept as the integer's text.
     */
    @SerializedName("Credits")
    @JsonAdapter(StrictTypes.TextOrNumber::class)
    val credits: String? = null
) : JSONConvertable