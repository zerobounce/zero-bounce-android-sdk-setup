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
        return if (dateString != null && dateString.trim { it <= ' ' }.isNotEmpty()) {
            try {
                format1.parse(dateString)
            } catch (pe: ParseException) {
                format2.parse(dateString)
            }
        } else {
            null
        }
    }
}