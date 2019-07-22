package com.zerobounce.android

import com.google.gson.annotations.SerializedName

data class ZBCreditsResponse(
    @SerializedName("Credits") val credits: String? = null
) : JSONConvertable