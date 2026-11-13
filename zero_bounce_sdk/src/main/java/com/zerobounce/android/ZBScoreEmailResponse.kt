package com.zerobounce.android

import com.google.gson.annotations.JsonAdapter

/**
 * Response for GET/POST /scoring (real-time AI scoring).
 */
data class ZBScoreEmailResponse(
    val email: String? = null,
    /** The AI score: a whole number, sent as a JSON integer or a string holding one (E7). */
    @JsonAdapter(StrictTypes.IntegerValue::class)
    val score: Int? = null,
) : JSONConvertable
