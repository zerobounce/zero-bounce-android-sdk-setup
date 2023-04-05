package com.zerobounce.android

import com.google.gson.annotations.SerializedName

/**
 * The model used for constructing the body of the POST /validatebatch request.
 */
data class ZBValidateBatchData(

    @SerializedName("email_address")
    val email: String,

    @SerializedName("ip_address")
    val ip: String?
)