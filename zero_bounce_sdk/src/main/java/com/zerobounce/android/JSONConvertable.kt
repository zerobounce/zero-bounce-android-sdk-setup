package com.zerobounce.android

import com.google.gson.GsonBuilder

/**
 * An interface that provides a *[toJSON]* method for generating a JSON String representation of
 * the object.
 */
interface JSONConvertable {
    fun toJSON(): String = GsonBuilder()
        .setDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
        .create()
        .toJson(this)
}