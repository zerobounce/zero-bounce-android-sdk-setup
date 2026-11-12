package com.zerobounce.android

import com.google.gson.stream.JsonReader
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.StringReader

class StrictBooleanAdapterTest {

    /** Whether [text] holds half of a surrogate pair. */
    private fun splitsACharacter(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= text.length || !Character.isLowSurrogate(text[i + 1])) return true
                i += 2
                continue
            }
            if (Character.isLowSurrogate(c)) return true
            i++
        }
        return false
    }

    /**
     * A long value that is not a boolean is quoted shortened, and the cut never
     * falls inside a character: here an emoji (a surrogate pair) straddles the
     * 40th UTF-16 unit of the quoted value (P3).
     */
    @Test
    fun aLongValueIsCutAtACharacterBoundary() {
        val value = "a".repeat(38) + "😀" + "b".repeat(10)
        val reader = JsonReader(StringReader("\"$value\""))
        try {
            StrictBooleanAdapter.read(reader)
            fail("read a boolean from $value")
        } catch (e: StrictBooleanException) {
            val message = e.message.orEmpty()
            assertTrue(message, message.contains("..."))
            assertFalse(message, splitsACharacter(message))
        }
    }
}
