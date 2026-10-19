package com.zerobounce.android

import com.google.gson.JsonParser

internal fun looksLikeHtml(body: String): Boolean {
    val head = body.take(2000).lowercase()
    return "<!doctype html" in head || "<html" in head || "<head" in head || "<body" in head
}

internal fun stripErrorPrefix(message: String): String {
    var rest = message.trim()
    while (true) {
        rest = when {
            rest.startsWith("Error:") -> rest.removePrefix("Error:").trim()
            rest.startsWith("[Error]:") -> rest.removePrefix("[Error]:").trim()
            else -> return rest
        }
    }
}

internal fun httpError(statusCode: Int, body: String?): ErrorResponse {
    val text = body?.trim().orEmpty()
    if (text.isEmpty()) {
        return ErrorResponse(errors = arrayListOf("Empty response from ZeroBounce (HTTP $statusCode)"))
    }
    if (looksLikeHtml(body.orEmpty())) {
        val title = htmlTitle(body.orEmpty())
        val message = if (title.isEmpty()) {
            "ZeroBounce returned an HTML error page instead of an API response."
        } else {
            "ZeroBounce returned an HTML error page (\"$title\") instead of an API response."
        }
        return ErrorResponse(errors = arrayListOf(message))
    }
    if (text.startsWith("{") || text.startsWith("[")) {
        cloudflareTitle(text)?.let { title ->
            return ErrorResponse(errors = arrayListOf(stripErrorPrefix(title)))
        }
        return try {
            JsonParser.parseString(text)
            ErrorResponse.parseError(text)
        } catch (_: Throwable) {
            ErrorResponse(errors = arrayListOf("ZeroBounce returned a response that is not valid JSON."))
        }
    }
    if (text.length > 1000) {
        return ErrorResponse(errors = arrayListOf("ZeroBounce returned a response that is not valid JSON."))
    }
    val message = stripErrorPrefix(text).ifEmpty {
        "Empty response from ZeroBounce (HTTP $statusCode)"
    }
    return ErrorResponse(errors = arrayListOf(message))
}

internal fun jsonIsUsable(statusCode: Int, body: String?): Boolean {
    val text = body?.trim().orEmpty()
    if (statusCode > 299 || text.isEmpty() || looksLikeHtml(body.orEmpty())) return false
    return text.startsWith("{") || text.startsWith("[")
}

internal fun deliverJson(
    statusCode: Int,
    body: String?,
    errorCallback: (ErrorResponse?) -> Unit,
    onJson: (String) -> Unit,
) {
    val text = body?.trim().orEmpty()
    val startsJson = text.startsWith("{") || text.startsWith("[")
    if (statusCode > 299 || text.isEmpty() || looksLikeHtml(body.orEmpty()) || !startsJson) {
        errorCallback(httpError(statusCode, body))
        return
    }
    try {
        onJson(text)
    } catch (_: Throwable) {
        errorCallback(httpError(statusCode, body))
    }
}

internal fun downloadRejected(statusCode: Int, contentType: String, bytes: ByteArray): Boolean {
    if (statusCode > 299) return true
    val text = bytes.toString(Charsets.UTF_8)
    if (text.isBlank() || looksLikeHtml(text)) return true
    if (contentType.contains("application/json", ignoreCase = true)) return true
    return Priority3Api.looksLikeJsonError(text)
}

private fun htmlTitle(body: String): String {
    val lower = body.lowercase()
    val start = lower.indexOf("<title")
    if (start < 0) return ""
    val openEnd = lower.indexOf('>', start)
    if (openEnd < 0) return ""
    val close = lower.indexOf("</title>", openEnd)
    if (close < 0) return ""
    return body.substring(openEnd + 1, close).trim()
}

private fun cloudflareTitle(body: String): String? {
    return try {
        val obj = JsonParser.parseString(body).asJsonObject
        if (!obj.has("title") || !obj.get("title").isJsonPrimitive) return null
        val title = obj.get("title").asString
        val accessDenied = title.contains("Access denied") || title.contains("1020")
        val cloudflare = obj.has("cloudflare_error") &&
            obj.get("cloudflare_error").isJsonPrimitive &&
            obj.get("cloudflare_error").asBoolean
        val hasErrorOrMessage = obj.keySet().any { key ->
            key.contains("error") || key.contains("message")
        }
        if (accessDenied && (cloudflare || !hasErrorOrMessage)) title else null
    } catch (_: Throwable) {
        null
    }
}
