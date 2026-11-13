package com.zerobounce.android

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

/** A typed result's field arrived with the wrong JSON type, or a field the call needs is missing. */
internal class StrictTypeException(message: String) : JsonParseException(message)

/**
 * Field types are part of the shape (check E7): a typed result's field that
 * arrives with another JSON type is an API error with status 200, never coerced
 * (`true` read as `"true"`), defaulted or dropped. `null` is accepted for an
 * optional field. Numbers keep Gson's reading (a number, or a string holding one);
 * a boolean, an array or an object is already an error there.
 */
internal object StrictTypes {

    private val INTEGER = Regex("-?[0-9]+")

    /** A string field: a JSON string or null, nothing else. */
    val STRING: TypeAdapter<String?> = object : TypeAdapter<String?>() {
        override fun write(out: JsonWriter, value: String?) {
            out.value(value)
        }

        override fun read(reader: JsonReader): String? = when (val token = reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }
            JsonToken.STRING -> reader.nextString()
            else -> {
                val path = reader.path
                reader.skipValue()
                throw wrongType(path, token, "a string")
            }
        }
    }

    /**
     * A field the API sends as a number or as a string holding one (`Credits`):
     * a JSON number or string, kept as its text; a boolean, an array or an object
     * is an error. Whether the text is a whole number is checked by [requireFields].
     */
    internal class TextOrNumber : TypeAdapter<String?>() {
        override fun write(out: JsonWriter, value: String?) {
            out.value(value)
        }

        override fun read(reader: JsonReader): String? = when (val token = reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }
            JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
            else -> {
                val path = reader.path
                reader.skipValue()
                throw wrongType(path, token, "a number")
            }
        }
    }

    /**
     * A file job's `message`: a JSON string, or a list of strings that is joined
     * with a newline into the one string the SDK exposes (an empty list is an empty
     * message). Every item must be a string; null, a number, a boolean, an array or
     * an object inside the list is an error (E7).
     */
    internal class TextOrListOfText : TypeAdapter<String?>() {
        override fun write(out: JsonWriter, value: String?) {
            out.value(value)
        }

        override fun read(reader: JsonReader): String? = when (val token = reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }
            JsonToken.STRING -> reader.nextString()
            JsonToken.BEGIN_ARRAY -> {
                val items = mutableListOf<String>()
                reader.beginArray()
                while (reader.hasNext()) {
                    val itemToken = reader.peek()
                    if (itemToken != JsonToken.STRING) {
                        val path = reader.path
                        reader.skipValue()
                        throw StrictTypeException(
                            "ZeroBounce returned ${wrongName(itemToken)} in ${path.removePrefix("$.")}, which is not a string."
                        )
                    }
                    items.add(reader.nextString())
                }
                reader.endArray()
                items.joinToString("\n")
            }
            else -> {
                val path = reader.path
                reader.skipValue()
                throw wrongType(path, token, "a string or a list of strings")
            }
        }

        private fun wrongName(token: JsonToken): String = when (token) {
            JsonToken.NULL -> "null"
            JsonToken.BOOLEAN -> "a boolean"
            JsonToken.NUMBER -> "a number"
            JsonToken.BEGIN_ARRAY -> "an array"
            JsonToken.BEGIN_OBJECT -> "an object"
            else -> "a string"
        }
    }

    /**
     * An integer field (the AI score): a JSON integer or a string holding one, within the
     * int range; null is accepted. A fraction (`5.0`), an exponent (`1e2`), a string with
     * anything else in it (`" 9"`, `"+9"`), a value out of range, a boolean, an array or an
     * object is an error (E7).
     */
    internal class IntegerValue : TypeAdapter<Int?>() {
        override fun write(out: JsonWriter, value: Int?) {
            out.value(value)
        }

        override fun read(reader: JsonReader): Int? = when (val token = reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }
            JsonToken.NUMBER, JsonToken.STRING -> {
                val path = reader.path
                val text = reader.nextString()
                val whole = if (INTEGER.matches(text)) text.toIntOrNull() else null
                whole ?: throw StrictTypeException(
                    "ZeroBounce returned ${if (token == JsonToken.NUMBER) "a number" else "a string"} for " +
                        "${path.removePrefix("$.")}, which is not an integer."
                )
            }
            else -> {
                val path = reader.path
                reader.skipValue()
                throw wrongType(path, token, "an integer")
            }
        }
    }

    /**
     * A property the public type never leaves null (standard.md section 3): a JSON null
     * reads as the property's empty value. Used with `nullSafe = false` so the null
     * reaches this adapter, and every other value goes to the property's own adapter.
     */
    internal abstract class NullAsEmpty<T : Any> : TypeAdapterFactory {
        abstract fun empty(): T

        override fun <R> create(gson: Gson, type: TypeToken<R>): TypeAdapter<R> {
            val delegate = gson.getDelegateAdapter(this, type)
            return object : TypeAdapter<R>() {
                override fun write(out: JsonWriter, value: R) = delegate.write(out, value)

                override fun read(reader: JsonReader): R {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                        @Suppress("UNCHECKED_CAST")
                        return empty() as R
                    }
                    return delegate.read(reader)
                }
            }
        }
    }

    /** A text property that is never null: null reads as "". */
    internal class NullAsEmptyText : NullAsEmpty<String>() {
        override fun empty(): String = ""
    }

    /** A list property that is never null: null reads as no rows. */
    internal class NullAsNoRows : NullAsEmpty<List<Any?>>() {
        override fun empty(): List<Any?> = emptyList()
    }

    /**
     * Enums (status, sub-status): a JSON string or null, Gson's own reading of the
     * string kept. Maps (batch error rows): a JSON object or null, not the array
     * of pairs Gson would otherwise also read as a map.
     */
    val ENUMS_AND_MAPS: TypeAdapterFactory = object : TypeAdapterFactory {
        override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
            val raw = type.rawType
            val isEnum = raw.isEnum
            if (!isEnum && !Map::class.java.isAssignableFrom(raw)) return null
            val wanted = if (isEnum) JsonToken.STRING else JsonToken.BEGIN_OBJECT
            val expected = if (isEnum) "a string" else "an object"
            val delegate = gson.getDelegateAdapter(this, type)
            return object : TypeAdapter<T>() {
                override fun write(out: JsonWriter, value: T) = delegate.write(out, value)

                override fun read(reader: JsonReader): T {
                    val token = reader.peek()
                    if (token != wanted && token != JsonToken.NULL) {
                        val path = reader.path
                        reader.skipValue()
                        throw wrongType(path, token, expected)
                    }
                    return delegate.read(reader)
                }
            }
        }
    }

    /**
     * The fields a call cannot answer without: getCredits' `Credits` is a whole
     * number, sent as a JSON string (`"-1"`, `"12345"`) or a JSON integer
     * (`2375323`); validate's `address` and `status` are strings (except in the
     * greylist answer, which carries `Message` instead of a result); and every
     * validatebatch row is an object with `address` and `status` as strings.
     *
     * @throws StrictTypeException when [body] is not a [type] result
     */
    fun requireFields(type: Class<*>, body: String) {
        if (type != ZBCreditsResponse::class.java && type != ZBValidateResponse::class.java &&
            type != ZBValidateBatchResponse::class.java
        ) return
        val parsed = JsonParser.parseString(body)
        if (!parsed.isJsonObject) return
        val obj = parsed.asJsonObject
        if (type == ZBCreditsResponse::class.java) {
            val credits = obj.get("Credits")
            if (credits == null || !credits.isJsonPrimitive ||
                !INTEGER.matches(credits.asString)
            ) {
                throw StrictTypeException(
                    "ZeroBounce returned ${kind(credits)} for Credits, which is not a whole number."
                )
            }
            return
        }
        if (type == ZBValidateBatchResponse::class.java) {
            val rows = obj.get("email_batch")
            if (rows == null || !rows.isJsonArray) return
            for (row in rows.asJsonArray) {
                if (!row.isJsonObject) {
                    throw StrictTypeException(
                        "ZeroBounce returned ${kind(row)} for a row of email_batch, which is not an object."
                    )
                }
                requireStrings(row.asJsonObject, "email_batch row ")
            }
            return
        }
        // The greylist answer: a result that may carry nothing but Message (or
        // the address with it) while the retry is still running.
        if (obj.has("Message")) return
        requireStrings(obj, "")
    }

    /** Throws unless [obj] has `address` and `status` as strings. */
    private fun requireStrings(obj: JsonObject, where: String) {
        for (field in listOf("address", "status")) {
            val value = obj.get(field)
            if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isString) {
                throw StrictTypeException("ZeroBounce returned ${kind(value)} for $where$field, which is not a string.")
            }
        }
    }

    private fun kind(element: JsonElement?): String = when {
        element == null -> "no value"
        element.isJsonNull -> "null"
        element.isJsonArray -> "an array"
        element.isJsonObject -> "an object"
        element.asJsonPrimitive.isBoolean -> "a boolean"
        element.asJsonPrimitive.isNumber -> "a number"
        else -> "a string"
    }

    private fun wrongType(path: String, token: JsonToken, expected: String): StrictTypeException {
        val found = when (token) {
            JsonToken.BOOLEAN -> "a boolean"
            JsonToken.NUMBER -> "a number"
            JsonToken.BEGIN_ARRAY -> "an array"
            JsonToken.BEGIN_OBJECT -> "an object"
            else -> "a string"
        }
        return StrictTypeException("ZeroBounce returned $found for ${path.removePrefix("$.")}, which is not $expected.")
    }
}
