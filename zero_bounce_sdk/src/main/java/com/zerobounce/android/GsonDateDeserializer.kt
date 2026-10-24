package com.zerobounce.android

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

/**
 * A custom deserializer for transforming date Strings into Date objects.
 */
class GsonDateDeserializer : JsonDeserializer<Date?> {

    private val format1 = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)
    private val format2 = SimpleDateFormat("MM/dd/yyyy h:mm:ss a", Locale.ROOT)

    /**
     * LIVE: bulk `filestatus` answers with an ISO-8601 `upload_date`, for
     * example `2026-10-24T05:24:13Z`. Without these the whole response failed
     * to parse and every file-status call came back as an error.
     */
    private val isoFormats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd",
    ).map { pattern ->
        SimpleDateFormat(pattern, Locale.ROOT).apply {
            if (pattern.endsWith("'Z'")) timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    @Throws(JsonParseException::class)
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Date? {
        return try {
            val dateString = json.asJsonPrimitive.asString
            parseDate(dateString)
        } catch (e: ParseException) {
            throw JsonParseException(e.message, e)
        }
    }

    /**
     * Tries to parse the given [dateString] into a [Date] object.
     *
     * @param dateString a date string
     *
     * @return a [Date] object or null
     */
    @Throws(ParseException::class)
    private fun parseDate(dateString: String?): Date? {
        if (dateString == null || dateString.trim { it <= ' ' }.isEmpty()) {
            return null
        }
        for (format in listOf(format1, format2) + isoFormats) {
            try {
                return format.parse(dateString)
            } catch (_: ParseException) {
                continue
            }
        }
        throw ParseException("Unparseable date: \"$dateString\"", 0)
    }
}