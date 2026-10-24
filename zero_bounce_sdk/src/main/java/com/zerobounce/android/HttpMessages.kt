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
        return ErrorResponse(
            errors = arrayListOf("Empty response from ZeroBounce (HTTP $statusCode)"),
            statusCode = statusCode,
        )
    }
    if (looksLikeHtml(body.orEmpty())) {
        val title = htmlTitle(body.orEmpty())
        val message = if (title.isEmpty()) {
            "ZeroBounce returned an HTML error page instead of an API response."
        } else {
            "ZeroBounce returned an HTML error page (\"$title\") instead of an API response."
        }
        return ErrorResponse(errors = arrayListOf(message), statusCode = statusCode)
    }
    if (text.startsWith("{") || text.startsWith("[")) {
        cloudflareTitle(text)?.let { title ->
            return ErrorResponse(errors = arrayListOf(stripErrorPrefix(title)), statusCode = statusCode)
        }
        return try {
            JsonParser.parseString(text)
            ErrorResponse.parseError(text, statusCode)
        } catch (_: Throwable) {
            ErrorResponse(errors = arrayListOf("ZeroBounce returned a response that is not valid JSON."))
        }
    }
    if (text.length > 1000) {
        return ErrorResponse(
            errors = arrayListOf("ZeroBounce returned a response that is not valid JSON."),
            statusCode = statusCode,
        )
    }
    val message = stripErrorPrefix(text).ifEmpty {
        "Empty response from ZeroBounce (HTTP $statusCode)"
    }
    return ErrorResponse(errors = arrayListOf(message), statusCode = statusCode)
}

internal fun jsonIsUsable(statusCode: Int, body: String?): Boolean {
    val text = body?.trim().orEmpty()
    if (statusCode > 299 || text.isEmpty() || looksLikeHtml(body.orEmpty())) return false
    if (!text.startsWith("{") && !text.startsWith("[")) return false
    // LIVE: {"success": false} on an HTTP 200 is a failure, not a result. The
    // API answers an unknown bulk file id that way.
    return !successMeansFailure(text)
}

/**
 * Whether a JSON body says the call failed although the HTTP status was a
 * success. The API sends "success" as a JSON false, "false" or "False"
 * depending on the endpoint.
 */
internal fun successMeansFailure(body: String): Boolean {
    if (!body.trimStart().startsWith("{")) return false
    return try {
        val parsed = com.google.gson.JsonParser.parseString(body).asJsonObject
        val value = parsed.get("success") ?: return false
        if (!value.isJsonPrimitive) return false
        val primitive = value.asJsonPrimitive
        when {
            primitive.isBoolean -> !primitive.asBoolean
            primitive.isString -> primitive.asString.trim().equals("false", ignoreCase = true)
            else -> false
        }
    } catch (_: Throwable) {
        false
    }
}

internal fun deliverJson(
    statusCode: Int,
    body: String?,
    errorCallback: (ErrorResponse?) -> Unit,
    onJson: (String) -> Unit,
) {
    val text = body?.trim().orEmpty()
    val startsJson = text.startsWith("{") || text.startsWith("[")
    if (statusCode > 299 || text.isEmpty() || looksLikeHtml(body.orEmpty()) || !startsJson ||
        successMeansFailure(text)
    ) {
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
