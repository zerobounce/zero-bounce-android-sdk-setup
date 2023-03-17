package com.zerobounce.android

import com.google.gson.annotations.SerializedName

/**
 * The model used for the GET /activity request.
 */
data class ZBActivityDataResponse(

    val found: Boolean = false,

    @SerializedName("active_in_days")
    val activeInDays: Int? = null
) : JSONConvertable