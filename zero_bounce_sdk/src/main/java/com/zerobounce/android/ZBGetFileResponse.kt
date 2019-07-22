package com.zerobounce.android

import com.google.gson.annotations.SerializedName

class ZBGetFileResponse(
    @SerializedName("localFilePath") val localFilePath: String
) : JSONConvertable