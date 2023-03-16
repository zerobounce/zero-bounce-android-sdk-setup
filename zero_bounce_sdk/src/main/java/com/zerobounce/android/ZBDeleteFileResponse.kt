package com.zerobounce.android

import com.google.gson.annotations.SerializedName

/**
 * The model class for the GET /deletefile request.
 */
data class ZBDeleteFileResponse(
    val success: Boolean? = false,

    val message: String? = null,

    @SerializedName("file_name")
    val fileName: String? = null,

    @SerializedName("file_id")
    val fileId: String? = null
) : JSONConvertable