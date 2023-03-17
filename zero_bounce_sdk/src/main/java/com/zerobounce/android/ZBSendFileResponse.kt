package com.zerobounce.android

import com.google.gson.annotations.SerializedName

/**
 * The model used for the GET /scoring and GET /sendFile requests.
 */
data class ZBSendFileResponse(
    val success: Boolean = false,

    val message: String? = null,

    @SerializedName("file_name")

    val fileName: String? = null,

    @SerializedName("file_id")
    val fileId: String? = null
) : JSONConvertable