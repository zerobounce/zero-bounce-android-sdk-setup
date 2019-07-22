package com.zerobounce.android

import com.google.gson.annotations.SerializedName

data class ZBSendFileResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("file_name") val fileName: String? = null,
    @SerializedName("file_id") val fileId: String? = null
) : JSONConvertable