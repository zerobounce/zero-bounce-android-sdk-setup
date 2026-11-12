package com.zerobounce.android

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.StringReader
import java.net.URLEncoder

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

/**
 * Replaces each secret in [text] with ***, in every form a request carries it and
 * a server can quote it back: as is (a header, a JSON or multipart body),
 * URL-encoded (a query string or a form) and JSON-escaped.
 */
internal fun redactSecrets(text: String, secrets: Iterable<String?>): String {
    var value = text
    for (secret in secrets) {
        if (secret.isNullOrEmpty()) continue
        for (form in secretForms(secret)) {
            value = value.replace(form, "***")
        }
    }
    return value
}

/**
 * The spellings of [secret] a server can quote back, composed from the encodings
 * it can meet up to two deep: as is and JSON-escaped (with or without Gson's
 * HTML-safe escapes such as `\u003c`, and with or without `/` written as a
 * backslash and a slash, as PHP's json_encode does), each of those URL-encoded
 * with `+` or `%20` for a space (a query string, a form, or a server that echoes
 * a URL-encoded JSON body), and JSON-escaped twice (a JSON error quoted in
 * another JSON string). Longest first, so a shorter form never leaves part of a
 * longer one behind.
 */
private fun secretForms(secret: String): List<String> {
    val jsonEscapes = mutableListOf<(String) -> String>()
    for (gson in listOf(Gson(), GsonBuilder().disableHtmlEscaping().create())) {
        val escape: (String) -> String = { text -> gson.toJson(text).let { it.substring(1, it.length - 1) } }
        jsonEscapes.add(escape)
        jsonEscapes.add { text -> escape(text).replace("/", "\\/") }
    }
    val once = listOf(secret) + jsonEscapes.map { it(secret) }
    val forms = linkedSetOf<String>()
    for (form in once) {
        val encoded = URLEncoder.encode(form, "UTF-8")
        forms.add(form)
        forms.add(encoded)
        forms.add(encoded.replace("+", "%20"))
    }
    for (escaped in once.drop(1)) {
        for (escape in jsonEscapes) {
            forms.add(escape(escaped))
        }
    }
    return forms.sortedByDescending { it.length }
}

/**
 * Whether [body] is one JSON value as RFC 8259 defines it. Gson's own parsers are
 * lenient: they read `{a:1}`, `{'a':'b'}`, `[1,]`, `NaN` and an unquoted word as
 * JSON, so a 2xx body like that would reach the caller as a result.
 */
internal fun isStrictJson(body: String): Boolean = try {
    val reader = JsonReader(StringReader(body))
    reader.setStrictness(Strictness.STRICT)
    JsonParser.parseReader(reader)
    reader.peek() == JsonToken.END_DOCUMENT
} catch (_: Exception) {
    false
}

/** Whether a content type names an HTML page. */
internal fun isHtmlContentType(contentType: String?): Boolean =
    contentType.orEmpty().contains("text/html", ignoreCase = true)

/** This error with each of [secrets] redacted from its messages. */
internal fun ErrorResponse.redacted(secrets: Iterable<String?>): ErrorResponse {
    // The list is declared as strings, but a JSON array in an error body can
    // put numbers or objects in it. Those are kept as they are unless their
    // text holds a secret.
    @Suppress("UNCHECKED_CAST")
    val messages = errors as MutableList<Any?>
    messages.replaceAll { message ->
        val text = message?.toString() ?: return@replaceAll message
        val redacted = redactSecrets(text, secrets)
        if (redacted == text) message else redacted
    }
    return this
}

internal fun httpError(statusCode: Int, body: String?): ErrorResponse {
    val text = body?.trim().orEmpty()
    if (text.isEmpty()) {
        return ErrorResponse(
            errors = arrayListOf("Empty response from ZeroBounce (HTTP $statusCode)"),
            statusCode = statusCode,
        )
    }
    if (looksLikeHtml(body.orEmpty())) return htmlPageError(statusCode, body.orEmpty())
    if (text.startsWith("{") || text.startsWith("[")) {
        cloudflareTitle(text)?.let { title ->
            return ErrorResponse(errors = arrayListOf(stripErrorPrefix(title)), statusCode = statusCode)
        }
        return if (isStrictJson(text)) {
            ErrorResponse.parseError(text, statusCode)
        } else {
            ErrorResponse(
                errors = arrayListOf("ZeroBounce returned a response that is not valid JSON."),
                statusCode = statusCode,
            )
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

/** An HTML page in place of an API response, named by its title when it has one. */
internal fun htmlPageError(statusCode: Int, body: String): ErrorResponse {
    val title = htmlTitle(body)
    val message = if (title.isEmpty()) {
        "ZeroBounce returned an HTML error page instead of an API response."
    } else {
        "ZeroBounce returned an HTML error page (\"$title\") instead of an API response."
    }
    return ErrorResponse(errors = arrayListOf(message), statusCode = statusCode)
}

internal fun jsonIsUsable(statusCode: Int, body: String?): Boolean {
    val text = body?.trim().orEmpty()
    if (statusCode > 299 || text.isEmpty() || looksLikeHtml(body.orEmpty())) return false
    if (!text.startsWith("{") && !text.startsWith("[") || !isStrictJson(text)) return false
    // LIVE: {"success": false} on an HTTP 200 is a failure, not a result. The
    // API answers an unknown bulk file id that way.
    return !successMeansFailure(text) && !errorOnlyBody(text)
}

/**
 * Whether a JSON body is an error with no "success" field to gate on: a
 * non-empty "error", "error_message" or "Error" string. LIVE: an invalid or
 * drained key answers validate, activity and getapiusage with {"error": ...},
 * and the filters with {"Error": ...}, on an HTTP 200. "message" and
 * "Message" are not among the keys: successful responses use them too.
 */
internal fun errorOnlyBody(body: String): Boolean {
    val parsed = objectWithoutSuccess(body) ?: return false
    return listOf("error", "error_message", "Error").any { nonEmptyString(parsed, it) }
}

private val MESSAGE_KEYS = setOf("error", "message", "error_message", "Message", "Error", "detail")

/**
 * Whether a JSON body is nothing but a message: an object with no "success"
 * field whose keys are all message keys, at least one a non-empty string. Only
 * for the email finder and domain search, whose result is never a bare
 * message. LIVE: guessformat answers an invalid or drained key with an HTTP
 * 200 and {"Message": "Invalid API key or your account ran out of credits"}.
 * Not for validate: a repeated greylist_processing request answers
 * {"Message": "Greylist validation still in progress..."}, which is a result.
 */
internal fun messageOnlyBody(body: String): Boolean {
    val parsed = objectWithoutSuccess(body) ?: return false
    val keys = parsed.keySet()
    return keys.isNotEmpty() && keys.all { it in MESSAGE_KEYS } && keys.any { nonEmptyString(parsed, it) }
}

private fun objectWithoutSuccess(body: String): com.google.gson.JsonObject? {
    if (!body.trimStart().startsWith("{")) return null
    return try {
        val parsed = JsonParser.parseString(body)
        if (!parsed.isJsonObject) return null
        parsed.asJsonObject.takeIf { !it.has("success") }
    } catch (_: Throwable) {
        null
    }
}

private fun nonEmptyString(obj: com.google.gson.JsonObject, key: String): Boolean {
    val value = obj.get(key) ?: return false
    return value.isJsonPrimitive && value.asJsonPrimitive.isString && value.asString.isNotBlank()
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

/**
 * The error for a body the SDK could not decode. A boolean that is not one is
 * reported by name; anything else as [httpError] reports the body.
 */
internal fun parseFailure(statusCode: Int, body: String?, cause: Exception): ErrorResponse {
    if (cause is StrictBooleanException) {
        return ErrorResponse(errors = arrayListOf(cause.message.orEmpty()), statusCode = statusCode)
    }
    return httpError(statusCode, body)
}

internal fun downloadRejected(statusCode: Int, contentType: String, bytes: ByteArray): Boolean {
    if (statusCode > 299 || isHtmlContentType(contentType)) return true
    val text = bytes.toString(Charsets.UTF_8)
    if (text.isBlank() || looksLikeHtml(text)) return true
    if (contentType.contains("application/json", ignoreCase = true)) return true
    return Priority3Api.looksLikeJsonError(text)
}

/** How much of a download is looked at before deciding whether it is a file or an error. */
private const val DOWNLOAD_SNIFF_BYTES = 8192L

/**
 * Saves a bulk results download to [file], streaming it to disk rather than
 * holding the whole file in memory. A response that is, or may be, an error
 * instead of a file (an error status, a JSON content type, an empty body, an
 * HTML page, or a body that starts with `{`) is read whole and checked with
 * [downloadRejected]; those are small.
 *
 * @return the error to report, or null when the file was saved
 * @throws IOException when reading the body or writing [file] fails. A partly
 * written file is deleted first.
 */
internal fun saveDownload(response: Response, file: File): ErrorResponse? {
    val code = response.code
    val contentType = response.body.contentType()?.toString()
        ?: response.header("Content-Type").orEmpty()
    val head = response.peekBody(DOWNLOAD_SNIFF_BYTES).bytes().toString(Charsets.UTF_8)
    val mayBeError = code > 299 ||
        contentType.contains("application/json", ignoreCase = true) ||
        isHtmlContentType(contentType) ||
        head.isBlank() ||
        looksLikeHtml(head) ||
        head.trimStart().startsWith("{")
    if (mayBeError) {
        val bytes = response.body.bytes()
        if (downloadRejected(code, contentType, bytes)) {
            val text = bytes.toString(Charsets.UTF_8)
            // A text/html content type on a 2xx is a proxy's page, whatever the body.
            val error = if (code <= 299 && isHtmlContentType(contentType)) {
                htmlPageError(code, text)
            } else {
                httpError(code, text)
            }
            if (error.errors.isEmpty()) {
                // {"success": false} with no message.
                error.errors = arrayListOf("ZeroBounce returned an error instead of the file (HTTP $code).")
            }
            return error
        }
        writeDownload(file) { it.write(bytes) }
        return null
    }
    writeDownload(file) { out -> response.body.byteStream().use { it.copyTo(out) } }
    return null
}

/** Opens the temporary file a download is written to. Tests replace it with one that fails part-way. */
@Volatile
internal var downloadFileOpener: (File) -> OutputStream = { FileOutputStream(it) }

/**
 * Writes a download to a temporary file next to [file] and moves it over [file]
 * only once it is complete, so a download whose network read or disk write fails
 * part-way (a full disk, a file size limit) leaves whatever was at [file]
 * untouched, and no partial file behind (D4).
 */
private fun writeDownload(file: File, write: (OutputStream) -> Unit) {
    val dir = file.absoluteFile.parentFile
    dir?.mkdirs()
    val temp = File.createTempFile(".${file.name}.", ".part", dir)
    try {
        downloadFileOpener(temp).use(write)
        // rename replaces an existing file on Android (POSIX); a platform that
        // will not rename over a file gets the old one removed first.
        if (!temp.renameTo(file) && !(file.delete() && temp.renameTo(file))) {
            throw IOException("Could not save the download as ${file.name}")
        }
    } finally {
        temp.delete()
    }
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
