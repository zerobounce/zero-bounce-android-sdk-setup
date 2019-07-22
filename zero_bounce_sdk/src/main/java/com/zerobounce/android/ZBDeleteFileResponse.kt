package com.zerobounce.android

import com.google.gson.annotations.SerializedName

data class ZBDeleteFileResponse(
    @SerializedName("success") val success: Boolean? = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("file_name") val fileName: String? = null,
    @SerializedName("file_id") val fileId: String? = null
) : JSONConvertable