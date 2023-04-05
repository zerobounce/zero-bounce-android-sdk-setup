package com.zerobounce.android

/**
 * The model used for the GET /scoring and GET /getfile requests.
 */
data class ZBGetFileResponse(
    val localFilePath: String
) : JSONConvertable