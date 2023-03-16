package com.zerobounce.android

import com.google.gson.annotations.SerializedName

/**
 * The model class used for the GET /getcredits request.
 */
data class ZBCreditsResponse(
    @SerializedName("Credits")
    val credits: String? = null
) : JSONConvertable