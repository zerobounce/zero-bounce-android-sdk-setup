package com.zerobounce.android

import com.google.gson.JsonParseException
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

/** A boolean field in a response that holds something else. */
internal class StrictBooleanException(message: String) : JsonParseException(message)

/**
 * Reads a boolean the way every ZeroBounce SDK does: `true`/`false`, the
 * strings `"true"`/`"false"` in any case, and `1`/`0` as a number or a string.
 * Anything else is an error that names the field, rather than Gson's default,
 * which reads `"yes"` and `"1"` as false and fails on a bare `1`.
 */
internal object StrictBooleanAdapter : TypeAdapter<Boolean?>() {

    override fun write(out: JsonWriter, value: Boolean?) {
        out.value(value)
    }

    override fun read(reader: JsonReader): Boolean? {
        val path = reader.path
        return when (val token = reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.STRING, JsonToken.NUMBER -> {
                val text = reader.nextString()
                fromText(text) ?: throw invalid(path, if (token == JsonToken.STRING) "\"$text\"" else text)
            }
            else -> {
                reader.skipValue()
                throw invalid(path, if (token == JsonToken.BEGIN_ARRAY) "an array" else "an object")
            }
        }
    }

    private fun fromText(text: String): Boolean? = when {
        text.trim().equals("true", ignoreCase = true) || text.trim() == "1" -> true
        text.trim().equals("false", ignoreCase = true) || text.trim() == "0" -> false
        else -> null
    }

    private fun invalid(path: String, value: String): StrictBooleanException {
        val field = path.removePrefix("$.")
        val shown = if (value.length > 40) value.take(40) + "..." else value
        return StrictBooleanException("ZeroBounce returned $shown for $field, which is not a boolean.")
    }
}
