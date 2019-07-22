package com.zerobounce.android

import com.google.gson.annotations.SerializedName

data class ZBFileStatusResponse(
    @SerializedName("file_status") val fileStatus: String? = null
) : JSONConvertable