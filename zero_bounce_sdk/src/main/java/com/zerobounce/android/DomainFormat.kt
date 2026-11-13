package com.zerobounce.android

import com.google.gson.annotations.JsonAdapter

/**
 * A model class that stores the data of a domain format found in the [ZBEmailFinderResponse].
 */
data class DomainFormat(

    @JsonAdapter(value = StrictTypes.NullAsEmptyText::class, nullSafe = false)
    var format: String = "",

    @JsonAdapter(value = StrictTypes.NullAsEmptyText::class, nullSafe = false)
    var confidence: String = ""
)