package com.zerobounce.android

import android.annotation.TargetApi
import android.os.Build
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale

@JvmSynthetic
internal fun looksLikeHtml(body: String): Boolean {
    var start = 0
    while (true) {
        while (start < body.length && (body[start].isWhitespace() || body[start] == '\uFEFF')) start++
        // A page may open with comments or an XML prolog (any <?...?>) before its tag. An unterminated one is not JSON either: count it as a page.
        val end = when {
            body.startsWith("<!--", start) -> body.indexOf("-->", start + 4).let { if (it < 0) return true else it + 3 }
            body.startsWith("<?", start) -> body.indexOf("?>", start + 2).let { if (it < 0) return true else it + 2 }
            else -> break
        }
        start = end
    }
    if (start >= body.length) return false
    if (body[start] == '{' || body[start] == '[') return false
    val rest = body.substring(start).lowercase(Locale.ROOT)
    return htmlTag(rest, "<!doctype") || htmlTag(rest, "<html") || htmlTag(rest, "<head") || htmlTag(rest, "<body")
}

private fun htmlTag(rest: String, tag: String): Boolean {
    if (!rest.startsWith(tag)) return false
    if (rest.length == tag.length) return true
    val next = rest[tag.length]
    return next.isWhitespace() || next == '>' || next == '/'
}

@JvmSynthetic
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
@JvmSynthetic
internal fun redactSecrets(text: String, secrets: Iterable<String?>): String {
    var value = text
    for (secret in secrets) {
        if (secret.isNullOrEmpty()) continue
        for (form in secretForms(secret)) {
            value = value.replace(form, "***", ignoreCase = true)
        }
    }
    // A server can nest those encodings in more ways than any list of spellings
    // holds, so what is left is decoded too; a message that still holds a secret
    // in any decoded view is dropped whole.
    return if (exposesSecret(value, secrets)) CREDENTIAL_OMITTED else value
}

/** The error for a 2xx body that is valid JSON but not the shape the call returns (a JSON `null` for a result). */
internal const val WRONG_SHAPE = "ZeroBounce returned JSON that is not the shape this call returns."

/** A typed call's body decoded to nothing: JSON `null`. */
internal class WrongShapeException : RuntimeException(WRONG_SHAPE)

/** What a message becomes when a secret is left in it in a spelling [redactSecrets] cannot replace. */
internal const val CREDENTIAL_OMITTED = "The response body was omitted because it contained a credential."

/** NFD, then the Unicode full case fold (uppercase then lowercase, no locale), then NFD again. U+0307 is dropped only when it follows `i`. */
private fun folded(text: String): String {
    val nfd = Normalizer.normalize(text, Normalizer.Form.NFD)
    val builder = StringBuilder()
    var index = 0
    while (index < nfd.length) {
        val point = nfd.codePointAt(index)
        val mapped = String(Character.toChars(point)).uppercase(Locale.ROOT).lowercase(Locale.ROOT)
        builder.append(Normalizer.normalize(mapped, Normalizer.Form.NFD))
        index += Character.charCount(point)
    }
    val folded = builder.toString()
    val out = StringBuilder()
    var at = 0
    while (at < folded.length) {
        val point = folded.codePointAt(at)
        val previous = if (out.isEmpty()) 0 else out.codePointBefore(out.length)
        if (point == 0x307 && previous == 'i'.code) {
            at += Character.charCount(point)
            continue
        }
        out.appendCodePoint(point)
        at += Character.charCount(point)
    }
    return out.toString()
}

/**
 * Whether [text], or any view of it decoded up to four times in any order (the
 * SDK's own request encoding plus three layers a server can add; URL-decoding
 * with and without `+` as a space, and JSON unescaping), holds one of [secrets].
 */
@JvmSynthetic
internal fun exposesSecret(text: String, secrets: Iterable<String?>): Boolean {
    val wanted = secrets.filterNotNull().filter { it.isNotEmpty() }.map { folded(it) }
    if (wanted.isEmpty()) return false
    val seen = hashSetOf(text)
    var layer = listOf(text)
    for (depth in 0..4) {
        if (layer.any { view -> folded(view).let { f -> wanted.any { f.contains(it) } } }) return true
        if (depth == 4) break
        layer = layer.flatMap { listOf(percentDecoded(it, false), percentDecoded(it, true), jsonUnescaped(it)) }
            .filter { seen.add(it) }
    }
    return false
}

private fun hexValue(c: Int): Int = when (c) {
    in '0'.code..'9'.code -> c - '0'.code
    in 'a'.code..'f'.code -> c - 'a'.code + 10
    in 'A'.code..'F'.code -> c - 'A'.code + 10
    else -> -1
}

/** URL-decodes every valid `%XX`, and `+` as a space when asked; anything else is kept. */
private fun percentDecoded(text: String, plusIsSpace: Boolean): String {
    if ('%' !in text && (!plusIsSpace || '+' !in text)) return text
    val raw = text.toByteArray(Charsets.UTF_8)
    val bytes = java.io.ByteArrayOutputStream(raw.size)
    var i = 0
    while (i < raw.size) {
        val b = raw[i].toInt()
        if (b == '%'.code && i + 2 < raw.size && hexValue(raw[i + 1].toInt()) >= 0 && hexValue(raw[i + 2].toInt()) >= 0) {
            bytes.write(hexValue(raw[i + 1].toInt()) * 16 + hexValue(raw[i + 2].toInt()))
            i += 3
            continue
        }
        bytes.write(if (b == '+'.code && plusIsSpace) ' '.code else b)
        i++
    }
    return bytes.toString("UTF-8")
}

/** Undoes JSON string escapes, including `\u{27}`, `\U00000027`, `\x27` and octal `\047`. */
private fun jsonUnescaped(text: String): String {
    if ('\\' !in text) return text
    val out = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        if (text[i] != '\\' || i + 1 >= text.length) {
            out.append(text[i])
            i++
            continue
        }
        val escape = text[i + 1]
        val simple = "\"\\/bfnrt'".indexOf(escape)
        if (simple >= 0) {
            out.append("\"\\/\b\u000c\n\r\t'"[simple])
            i += 2
            continue
        }
        if ((escape == 'u' || escape == 'U') && i + 2 < text.length && text[i + 2] == '{') {
            var j = i + 3
            var digits = ""
            while (j < text.length && digits.length < 8 && hexValue(text[j].code) >= 0) {
                digits += text[j]
                j++
            }
            // toLong: 8 hex digits can exceed Int.MAX_VALUE. A value past U+10FFFF is left as written.
            if (digits.isNotEmpty() && j < text.length && text[j] == '}' && digits.toLong(16) <= 0x10FFFF) {
                out.appendCodePoint(digits.toLong(16).toInt())
                i = j + 1
                continue
            }
        }
        if (escape == 'U' && i + 9 < text.length && (2..9).all { hexValue(text[i + it].code) >= 0 }) {
            val code = text.substring(i + 2, i + 10).toLong(16)
            if (code <= 0x10FFFF) {
                out.appendCodePoint(code.toInt())
                i += 10
                continue
            }
            // Past U+10FFFF: left as written, not read as a 4-digit \U escape.
            out.append(text[i])
            i++
            continue
        }
        if ((escape == 'u' || escape == 'U') && i + 5 < text.length && (2..5).all { hexValue(text[i + it].code) >= 0 }) {
            out.append(text.substring(i + 2, i + 6).toInt(16).toChar())
            i += 6
            continue
        }
        if (escape == 'x' && i + 3 < text.length && hexValue(text[i + 2].code) >= 0 && hexValue(text[i + 3].code) >= 0) {
            out.append((hexValue(text[i + 2].code) * 16 + hexValue(text[i + 3].code)).toChar())
            i += 4
            continue
        }
        if (escape in '0'..'7') {
            var value = escape.code - '0'.code
            var length = 2
            if (i + 2 < text.length && text[i + 2] in '0'..'7') {
                value = value * 8 + (text[i + 2].code - '0'.code)
                length = 3
                if (escape <= '3' && i + 3 < text.length && text[i + 3] in '0'..'7') {
                    value = value * 8 + (text[i + 3].code - '0'.code)
                    length = 4
                }
            }
            out.append(value.toChar())
            i += length
            continue
        }
        out.append(text[i])
        i++
    }
    return out.toString()
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
 * Whether [body], exactly as it arrived, is one JSON value as RFC 8259 defines it,
 * with only space, tab, CR and LF around it. Gson's own parsers are lenient: they
 * read `{a:1}`, `{'a':'b'}`, `[1,]`, `NaN` and an unquoted word as JSON, and even
 * its strict reader skips a byte order mark and reads an empty body as null. Kotlin's
 * `trim()` drops more than JSON whitespace (NBSP, U+2003, U+000B, U+001C to U+001F),
 * so the body is never trimmed before this check.
 */
@JvmSynthetic
internal fun isStrictJson(body: String): Boolean {
    val pos = intArrayOf(skipJsonWhitespace(body, 0))
    if (!strictValue(body, pos, 0)) return false
    return skipJsonWhitespace(body, pos[0]) == body.length
}

private fun skipJsonWhitespace(text: String, from: Int): Int {
    var at = from
    while (at < text.length && (text[at] == ' ' || text[at] == '\t' || text[at] == '\r' || text[at] == '\n')) at++
    return at
}

/** Reads one value at `pos[0]`, leaving `pos[0]` just after it. */
private fun strictValue(text: String, pos: IntArray, depth: Int): Boolean {
    if (pos[0] >= text.length || depth > 512) return false
    val c = text[pos[0]]
    if (c == '{' || c == '[') {
        val close = if (c == '{') '}' else ']'
        pos[0] = skipJsonWhitespace(text, pos[0] + 1)
        if (pos[0] < text.length && text[pos[0]] == close) {
            pos[0]++
            return true
        }
        while (true) {
            if (c == '{') {
                if (pos[0] >= text.length || text[pos[0]] != '"' || !strictString(text, pos)) return false
                pos[0] = skipJsonWhitespace(text, pos[0])
                if (pos[0] >= text.length || text[pos[0]] != ':') return false
                pos[0] = skipJsonWhitespace(text, pos[0] + 1)
            }
            if (!strictValue(text, pos, depth + 1)) return false
            pos[0] = skipJsonWhitespace(text, pos[0])
            if (pos[0] >= text.length) return false
            val next = text[pos[0]++]
            if (next == close) return true
            if (next != ',') return false
            pos[0] = skipJsonWhitespace(text, pos[0])
        }
    }
    if (c == '"') return strictString(text, pos)
    for (literal in arrayOf("true", "false", "null")) {
        if (text.startsWith(literal, pos[0])) {
            pos[0] += literal.length
            return true
        }
    }
    return strictNumber(text, pos)
}

private fun strictString(text: String, pos: IntArray): Boolean {
    var at = pos[0] + 1
    while (at < text.length) {
        val c = text[at]
        when {
            c == '"' -> {
                pos[0] = at + 1
                return true
            }
            c.code < 0x20 -> return false
            c == '\\' -> {
                if (at + 1 >= text.length) return false
                val escape = text[at + 1]
                if (escape == 'u') {
                    if (at + 6 > text.length || (at + 2 until at + 6).any { hexValue(text[it].code) < 0 }) return false
                    at += 6
                } else {
                    if (escape !in "\"\\/bfnrt") return false
                    at += 2
                }
            }
            else -> at++
        }
    }
    return false
}

private fun strictNumber(text: String, pos: IntArray): Boolean {
    var at = pos[0]
    if (at < text.length && text[at] == '-') at++
    if (at >= text.length) return false
    when {
        text[at] == '0' -> at++
        text[at] in '1'..'9' -> at = jsonDigits(text, at)
        else -> return false
    }
    if (at < text.length && text[at] == '.') {
        val end = jsonDigits(text, at + 1)
        if (end == at + 1) return false
        at = end
    }
    if (at < text.length && (text[at] == 'e' || text[at] == 'E')) {
        at++
        if (at < text.length && (text[at] == '+' || text[at] == '-')) at++
        val end = jsonDigits(text, at)
        if (end == at) return false
        at = end
    }
    pos[0] = at
    return true
}

private fun jsonDigits(text: String, from: Int): Int {
    var at = from
    while (at < text.length && text[at] in '0'..'9') at++
    return at
}

/**
 * A response body as text, byte for byte: OkHttp's `string()` drops a leading byte
 * order mark, which would let a BOM-prefixed body pass the strict JSON check.
 */
@JvmSynthetic
internal fun bodyText(response: Response): String {
    val body = response.body
    return String(body.bytes(), body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
}

/** Whether a content type names an HTML page. */
@JvmSynthetic
internal fun isHtmlContentType(contentType: String?): Boolean =
    contentType.orEmpty().contains("text/html", ignoreCase = true)

/** This error with each of [secrets] redacted from its messages. */
@JvmSynthetic
internal fun ErrorResponse.redacted(secrets: Iterable<String?>): ErrorResponse {
    // The list is declared as strings, but a JSON array in an error body can
    // put numbers or objects in it. Those are kept as they are unless their
    // text holds a secret.
    @Suppress("UNCHECKED_CAST")
    val messages = errors as MutableList<Any?>
    // An index loop, not List.replaceAll: that method needs API 24 and the SDK's minSdk is 21.
    for (i in messages.indices) {
        val message = messages[i]
        val text = message?.toString() ?: continue
        val redacted = redactSecrets(text, secrets)
        if (redacted != text) messages[i] = redacted
    }
    return this
}

@JvmSynthetic
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
        return if (isStrictJson(body.orEmpty())) {
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
@JvmSynthetic
internal fun htmlPageError(statusCode: Int, body: String): ErrorResponse {
    val title = htmlTitle(body)
    val message = if (title.isEmpty()) {
        "ZeroBounce returned an HTML error page instead of an API response."
    } else {
        "ZeroBounce returned an HTML error page (\"$title\") instead of an API response."
    }
    return ErrorResponse(errors = arrayListOf(message), statusCode = statusCode)
}

@JvmSynthetic
internal fun jsonIsUsable(statusCode: Int, body: String?): Boolean {
    val text = body?.trim().orEmpty()
    if (statusCode > 299 || text.isEmpty() || looksLikeHtml(body.orEmpty())) return false
    // The body as it arrived: trim() drops characters JSON does not allow around a value.
    if (!isStrictJson(body.orEmpty())) return false
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
@JvmSynthetic
internal fun errorOnlyBody(body: String): Boolean {
    val parsed = objectWithoutSuccess(body) ?: return false
    return listOf("error", "error_message", "Error").any { nonEmptyString(parsed, it) }
}

private val MESSAGE_KEYS = setOf("error", "message", "error_message", "Message", "Error", "detail")

/**
 * The error for a body that is nothing but a message: an object with no "success" field
 * whose keys are all message keys, whatever their values (a string, blank, null or a list);
 * null otherwise. Only for the email finder and domain search, whose result is never a bare
 * message. The text is the first message that has any. LIVE: guessformat answers an invalid
 * or drained key with an HTTP 200 and {"Message": "Invalid API key or your account ran out of
 * credits"}. Not for validate: a repeated greylist_processing request answers
 * {"Message": "Greylist validation still in progress..."}, which is a result.
 */
@JvmSynthetic
internal fun messageOnlyError(statusCode: Int, body: String): ErrorResponse? {
    val parsed = objectWithoutSuccess(body) ?: return null
    val keys = parsed.keySet()
    val onlyMessages = keys.all { it in MESSAGE_KEYS }
    // A message beside documented fields that are all null is the same error.
    // {"domain":null} with no message text stays a result.
    val besideNulls = keys.any { it in MESSAGE_KEYS && messageText(parsed.get(it)) != null } &&
        keys.any { it !in MESSAGE_KEYS } &&
        keys.filter { it !in MESSAGE_KEYS }.all { parsed.get(it)?.isJsonNull == true }
    if (!onlyMessages && !besideNulls) return null
    val text = keys.firstNotNullOfOrNull { messageText(parsed.get(it)) }
    return ErrorResponse(
        errors = arrayListOf(text ?: "ZeroBounce returned an error with no message text."),
        statusCode = statusCode,
    )
}

/** Known file-status fields. One of them must be non-null; `{"foo":1}` is not a status. */
private val FILE_STATUS_FIELDS = setOf(
    "file_id", "file_name", "upload_date", "file_status", "file_phase_2_status",
    "complete_percentage", "error_reason", "return_url", "success",
)

/** Whether a file-status body has one non-null known field. `{"file_status":"Processing"}` does. */
@JvmSynthetic
internal fun fileStatusHasResult(body: String): Boolean {
    val parsed = try {
        JsonParser.parseString(body)
    } catch (_: Throwable) {
        return false
    }
    if (!parsed.isJsonObject) return false
    val obj = parsed.asJsonObject
    return FILE_STATUS_FIELDS.any { key ->
        val value = obj.get(key) ?: return@any false
        if (value.isJsonNull) return@any false
        if (value.isJsonPrimitive && value.asJsonPrimitive.isString && value.asString.isBlank()) return@any false
        true
    }
}

/** A message's text: a string, or the non-blank strings of a list joined with newlines; null when there is none. */
private fun messageText(value: com.google.gson.JsonElement?): String? {
    if (value == null || value.isJsonNull) return null
    val parts = when {
        value.isJsonPrimitive && value.asJsonPrimitive.isString -> listOf(value.asString)
        value.isJsonArray -> value.asJsonArray
            .filter { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            .map { it.asString }
        else -> emptyList()
    }
    return parts.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
        .takeIf { it.isNotEmpty() }?.let { stripErrorPrefix(it) }
}

private fun objectWithoutSuccess(body: String): com.google.gson.JsonObject? {
    if (!body.trimStart().startsWith("{")) return null
    return try {
        val parsed = JsonParser.parseString(body)
        if (!parsed.isJsonObject) return null
        val obj = parsed.asJsonObject
        if (obj.has("success")) {
            // A real flag decides on its own. A non-flag is not a result field,
            // so the message and dead-key rules still see the rest of the body.
            if (successFlag(obj.get("success")) != null) return null
            obj.remove("success")
        }
        obj
    } catch (_: Throwable) {
        null
    }
}

/** A real success flag: boolean, 0 or 1 (including 1.0), or true/false/0/1. Anything else is not a flag. */
@JvmSynthetic
internal fun successFlag(value: com.google.gson.JsonElement?): Boolean? {
    if (value == null || value.isJsonNull || !value.isJsonPrimitive) return null
    val primitive = value.asJsonPrimitive
    return when {
        primitive.isBoolean -> primitive.asBoolean
        primitive.isNumber -> when (primitive.asDouble) {
            0.0 -> false
            1.0 -> true
            else -> null
        }
        primitive.isString -> when (primitive.asString.trim().lowercase(java.util.Locale.ROOT)) {
            "false", "0" -> false
            "true", "1" -> true
            else -> null
        }
        else -> null
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
@JvmSynthetic
internal fun successMeansFailure(body: String): Boolean {
    if (!body.trimStart().startsWith("{")) return false
    return try {
        val parsed = com.google.gson.JsonParser.parseString(body).asJsonObject
        successFlag(parsed.get("success")) == false
    } catch (_: Throwable) {
        false
    }
}

/**
 * The error for a body the SDK could not decode. A boolean that is not one is
 * reported by name; anything else as [httpError] reports the body.
 */
@JvmSynthetic
internal fun parseFailure(statusCode: Int, body: String?, cause: Exception): ErrorResponse {
    if (cause is WrongShapeException) {
        return ErrorResponse(errors = arrayListOf(WRONG_SHAPE), statusCode = statusCode)
    }
    if (cause is StrictBooleanException || cause is StrictTypeException) {
        return ErrorResponse(errors = arrayListOf(cause.message.orEmpty()), statusCode = statusCode)
    }
    return httpError(statusCode, body)
}

/** A UTF-8 byte-order mark, which some servers put before a JSON error. */
private const val BOM = '\uFEFF'

/** [text] without a leading byte-order mark. */
@JvmSynthetic
internal fun stripBom(text: String): String = text.removePrefix(BOM.toString())

/** Whether [contentType] is `application/json` or any `+json` type (`application/problem+json`). */
@JvmSynthetic
internal fun isJsonContentType(contentType: String?): Boolean {
    val type = contentType.orEmpty().substringBefore(';').trim().lowercase(java.util.Locale.ROOT)
    return type == "application/json" || type.endsWith("+json")
}

/**
 * Whether a download body is JSON, not a results file: a JSON object or array
 * after an optional byte-order mark, whatever its content type. A CSV never
 * parses as one.
 */
@JvmSynthetic
internal fun looksLikeJsonDownload(text: String): Boolean {
    val trimmed = stripBom(text).trim()
    if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return false
    return try {
        val parsed = JsonParser.parseString(trimmed)
        parsed.isJsonObject || parsed.isJsonArray
    } catch (_: Exception) {
        false
    }
}

@JvmSynthetic
internal fun downloadRejected(statusCode: Int, contentType: String, bytes: ByteArray): Boolean {
    if (statusCode > 299 || isHtmlContentType(contentType)) return true
    val text = stripBom(bytes.toString(Charsets.UTF_8))
    if (text.isBlank() || looksLikeHtml(text)) return true
    if (isJsonContentType(contentType)) return true
    return looksLikeJsonDownload(text) || Priority3Api.looksLikeJsonError(text)
}

/** How much of a download is looked at before deciding whether it is a file or an error. */
private const val DOWNLOAD_SNIFF_BYTES = 8192L

/**
 * Why [path] cannot be a download target, or null when it can: blank, ending in
 * a separator, or an existing directory. Checked before the request is sent, so
 * nothing is asked of the server for a file that cannot be saved.
 */
@JvmSynthetic
internal fun downloadTargetError(path: String?): ErrorResponse? {
    if (path.isNullOrBlank()) {
        return ErrorResponse(errors = arrayListOf("download_path must not be blank"))
    }
    if (path.endsWith("/") || path.endsWith(File.separator)) {
        return ErrorResponse(errors = arrayListOf("download_path must be a file path, not a directory: $path"))
    }
    if (File(path).isDirectory) {
        return ErrorResponse(errors = arrayListOf("download_path is a directory: $path"))
    }
    return null
}

/**
 * Saves a bulk results download to [file], streaming it to disk rather than
 * holding the whole file in memory. A response that is, or may be, an error
 * instead of a file (an error status, a JSON content type, an empty body, an
 * HTML page, or a body that starts with `{` or `[`) is read whole and checked with
 * [downloadRejected]; those are small.
 *
 * @return the error to report, or null when the file was saved
 * @throws IOException when reading the body or writing [file] fails. A partly
 * written file is deleted first.
 */
@JvmSynthetic
internal fun saveDownload(response: Response, file: File): ErrorResponse? {
    val code = response.code
    val contentType = response.body.contentType()?.toString()
        ?: response.header("Content-Type").orEmpty()
    downloadTargetError(file.path)?.let { return it }
    val head = stripBom(response.peekBody(DOWNLOAD_SNIFF_BYTES).bytes().toString(Charsets.UTF_8))
    val mayBeError = code > 299 ||
        isJsonContentType(contentType) ||
        isHtmlContentType(contentType) ||
        head.isBlank() ||
        looksLikeHtml(head) ||
        head.trimStart().startsWith("{") ||
        head.trimStart().startsWith("[")
    if (mayBeError) {
        val bytes = response.body.bytes()
        if (downloadRejected(code, contentType, bytes)) {
            val text = stripBom(bytes.toString(Charsets.UTF_8))
            // A text/html content type on a 2xx is a proxy's page, whatever the body.
            val error = if (code <= 299 && isHtmlContentType(contentType)) {
                htmlPageError(code, text)
            } else {
                httpError(code, text)
            }
            if (error.errors.isEmpty()) {
                // {"success": false} with no message, or a JSON array.
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
@get:JvmSynthetic
@set:JvmSynthetic
internal var downloadFileOpener: (File) -> OutputStream = { FileOutputStream(it) }

/**
 * Writes a download to a temporary file next to [file] and moves it over [file]
 * only once it is complete, so a download whose network read or disk write fails
 * part-way (a full disk, a file size limit) leaves whatever was at [file]
 * untouched, and no partial file behind (D4).
 */
private fun writeDownload(requested: File, write: (OutputStream) -> Unit) {
    // A symbolic link at the target is written through: the real file is
    // replaced and the link stays.
    val file = try {
        requested.canonicalFile
    } catch (_: IOException) {
        requested.absoluteFile
    }
    val dir = file.parentFile
    dir?.mkdirs()
    if (dir != null && !dir.canWrite()) {
        throw IOException("Cannot write ${requested.path} (permission denied)")
    }
    val temp = try {
        File.createTempFile(".${file.name}.", ".part", dir)
    } catch (e: IOException) {
        throw IOException("Cannot write ${requested.path} (${e.message ?: "permission denied"})")
    }
    try {
        downloadFileOpener(temp).use(write)
        // A file that is already there keeps its permissions.
        if (file.exists()) copyPermissions(file, temp)
        // rename replaces an existing file on Android (POSIX); a platform that
        // will not rename over a file gets the old one removed first.
        if (!temp.renameTo(file) && !(file.delete() && temp.renameTo(file))) {
            throw IOException("Could not save the download as ${file.name}")
        }
    } finally {
        temp.delete()
    }
}

/** Whether java.nio.file exists on this platform (API 26+); a unit test on the JVM replaces it. */
@get:JvmSynthetic
@set:JvmSynthetic
internal var nioPermissionsAvailable: () -> Boolean = { Build.VERSION.SDK_INT >= 26 }

// Only reached when nioPermissionsAvailable() is true, i.e. on API 26+.
// RequiresApi would need the androidx.annotation dependency, which this library does not carry.
@Suppress("UseRequiresApi")
@TargetApi(26)
private fun copyPosixPermissions(from: File, to: File) {
    val perms = java.nio.file.Files.getPosixFilePermissions(from.toPath())
    java.nio.file.Files.setPosixFilePermissions(to.toPath(), perms)
}

/** Gives [to] the permissions of [from]: exact POSIX bits where the platform has them, owner bits otherwise. */
private fun copyPermissions(from: File, to: File) {
    if (nioPermissionsAvailable()) {
        try {
            copyPosixPermissions(from, to)
            return
        } catch (_: Throwable) {
            // Not a POSIX file system: fall through.
        }
    }
    to.setReadable(from.canRead(), true)
    to.setWritable(from.canWrite(), true)
    to.setExecutable(from.canExecute(), true)
}

private fun htmlTitle(body: String): String {
    // Searched in the original text, ignoring case: a lower-cased copy can be longer
    // (U+0130 becomes two chars), and its indexes would cut the original in the wrong place.
    val start = body.indexOf("<title", ignoreCase = true)
    if (start < 0) return ""
    val openEnd = body.indexOf('>', start)
    if (openEnd < 0) return ""
    val close = body.indexOf("</title>", openEnd, ignoreCase = true)
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

/** Why [initialize][ZeroBounceSDK.initialize] refuses an API key with whitespace, control or invisible characters. */
internal const val API_KEY_CHARACTERS_PROBLEM = "api_key has whitespace, control or invisible characters"

/** The fixed message when a key cannot be written into an HTTP header (OkHttp's own message quotes the value). */
internal const val API_KEY_HEADER_PROBLEM = "api_key has characters that cannot be sent in an HTTP header"

/**
 * Whether [key] holds a character that no real key has and that could be hidden
 * or mangled in a log or a header: any whitespace (Unicode ones included, such as
 * NBSP and U+2003), a control character, or a format character (Unicode category
 * Cf: zero-width space and joiner, BOM, word joiner, U+180E, bidi marks).
 */
@JvmSynthetic
internal fun hasUnsafeKeyCharacters(key: String): Boolean =
    unsafeKeyCharacterScan(key)

// A plain loop: String.codePoints() is API 24 and the minSdk is 21.
private fun unsafeKeyCharacterScan(key: String): Boolean {
    var i = 0
    while (i < key.length) {
        val cp = Character.codePointAt(key, i)
        if (Character.isISOControl(cp) || Character.isWhitespace(cp) || Character.isSpaceChar(cp) ||
            Character.getType(cp) == Character.FORMAT.toInt()
        ) return true
        i += Character.charCount(cp)
    }
    return false
}

/** Whether [key] can be a header value as is: visible ASCII only (0x21 to 0x7E). */
@JvmSynthetic
internal fun isHeaderSafeKey(key: String): Boolean = key.all { it.code in 0x21..0x7E }
