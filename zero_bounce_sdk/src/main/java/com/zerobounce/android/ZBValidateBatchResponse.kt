package com.zerobounce.android

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

/**
 * The model used for the POST /validatebatch request.
 */
data class ZBValidateBatchResponse(

    @SerializedName("email_batch")
    @JsonAdapter(value = StrictTypes.NullAsNoRows::class, nullSafe = false)
    val emailBatch: List<ZBValidateResponse>,

    val errors: List<LinkedHashMap<String, Any?>>? = null
): JSONConvertable