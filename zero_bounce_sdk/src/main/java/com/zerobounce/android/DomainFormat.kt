package com.zerobounce.android

/**
 * A model class that stores the data of a domain format found in the [ZBEmailFinderResponse].
 */
data class DomainFormat(

    var format: String,

    var confidence: String
)