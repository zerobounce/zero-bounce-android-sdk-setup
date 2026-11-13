package com.zerobounce.android

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

/**
 * The model used for the GET /scoring and GET /sendfile requests.
 */
data class ZBSendFileResponse(
    val success: Boolean = false,

    /** The API's message: a string, or a list of strings joined with a newline (E7). */
    @JsonAdapter(StrictTypes.TextOrListOfText::class)
    val message: String? = null,

    @SerializedName("file_name")

    val fileName: String? = null,

    @SerializedName("file_id")
    val fileId: String? = null
) : JSONConvertable