package com.zerobounce.android

import com.google.gson.annotations.SerializedName

/**
 * The model used for the POST /validatebatch request.
 */
data class ZBValidateBatchResponse(

    @SerializedName("email_batch")
    val emailBatch: List<ZBValidateResponse>
): JSONConvertable