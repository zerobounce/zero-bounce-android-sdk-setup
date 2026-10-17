package com.zerobounce.android

/**
 * Response for GET/POST /scoring (real-time AI scoring).
 */
data class ZBScoreEmailResponse(
    val email: String? = null,
    val score: Int? = null,
) : JSONConvertable
