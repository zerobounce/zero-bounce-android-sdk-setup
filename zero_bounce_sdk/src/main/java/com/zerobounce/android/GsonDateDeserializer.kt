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
 *
 * Every pattern here uses only pattern letters that SimpleDateFormat has
 * supported since API 1. The ISO-8601 offset letter `X` is API 24+: on API
 * 21-23 constructing a pattern that contains it throws
 * IllegalArgumentException, and because this deserializer is built while
 * [ZeroBounceSDK] initializes, that failed every call on those devices.
 * ISO-8601 is parsed by hand instead, see [parseIso8601].
 */
class GsonDateDeserializer : JsonDeserializer<Date?> {

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
        // LIVE: bulk `filestatus` answers with an ISO-8601 `upload_date`, for
        // example `2026-10-24T05:24:13Z`.
        parseIso8601(dateString.trim())?.let { return it }
        // SimpleDateFormat is not thread-safe and responses are parsed on
        // OkHttp's threads, so each call builds its own formats.
        for (pattern in PATTERNS) {
            try {
                return SimpleDateFormat(pattern, Locale.ROOT).parse(dateString)
            } catch (_: ParseException) {
                continue
            }
        }
        throw ParseException("Unparseable date: \"$dateString\"", 0)
    }

    internal companion object {
        /**
         * The legacy formats, then the offset-less ISO shapes. Letters used:
         * y M d H h m s S a, all supported since API 1. Never add `X`, `Y`
         * or `u`: they are API 24+.
         */
        internal val PATTERNS = listOf(
            "yyyy-MM-dd HH:mm:ss.SSS",
            "MM/dd/yyyy h:mm:ss a",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
        )

        private val ISO_8601 = Regex(
            """^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d+))?(Z|z|[+-]\d{2}(?::?\d{2})?)?$"""
        )

        /**
         * Parses `yyyy-MM-ddTHH:mm:ss[.fraction][Z|±hh|±hhmm|±hh:mm]`, or
         * returns null when [text] is not that shape. Without an offset the
         * time is read in the device's time zone, as the other offset-less
         * formats are. The fraction is read as milliseconds: its first three
         * digits, right-padded with zeros.
         */
        internal fun parseIso8601(text: String): Date? {
            val match = ISO_8601.matchEntire(text) ?: return null
            val g = match.groupValues
            val offset = g[8]
            val zone = if (offset.isEmpty()) TimeZone.getDefault() else TimeZone.getTimeZone("UTC")
            // Lenient, as SimpleDateFormat is by default.
            val calendar = GregorianCalendar(zone, Locale.ROOT).apply {
                clear()
                set(g[1].toInt(), g[2].toInt() - 1, g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].toInt())
                set(Calendar.MILLISECOND, g[7].take(3).padEnd(3, '0').toInt())
            }
            return Date(calendar.timeInMillis - offsetMillis(offset))
        }

        private fun offsetMillis(offset: String): Long {
            if (offset.isEmpty() || offset == "Z" || offset == "z") return 0
            val sign = if (offset[0] == '-') -1 else 1
            val digits = offset.substring(1).replace(":", "")
            val hours = digits.substring(0, 2).toInt()
            val minutes = if (digits.length > 2) digits.substring(2, 4).toInt() else 0
            return sign * (hours * 60L + minutes) * 60_000L
        }
    }
}
