package com.zerobounce.android

import com.google.gson.annotations.SerializedName
import java.util.*

/**
 * The model used for the GET /scoring and GET /filestatus requests.
 */
data class ZBFileStatusResponse(

    private var success: Boolean = false,

    private var message: String? = null,

    @SerializedName("file_id")
    private var fileId: String? = null,

    @SerializedName("file_name")
    private var fileName: String? = null,

    @SerializedName("upload_date")
    private var uploadDate: Date? = null,

    @SerializedName("file_status")
    private var fileStatus: String? = null,

    @SerializedName("complete_percentage")
    private var completePercentage: String? = null,

    @SerializedName("error_reason")
    private var errorReason: String? = null,

    @SerializedName("return_url")
    private var returnUrl: String? = null
) : JSONConvertable