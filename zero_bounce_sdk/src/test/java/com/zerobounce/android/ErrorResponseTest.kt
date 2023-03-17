package com.zerobounce.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ErrorResponseTest {

    @Test
    fun parseError_ContainsOnlyError() {
        val errors = arrayListOf(
            "Invalid API Key or your account ran out of credits"
        )
        val expectedResponse = ErrorResponse(errors = errors)

        val responseJson = "{\"error\":[\"Invalid API Key or your account ran out of credits\"]}"
        val actualResponse = ErrorResponse.parseError(responseJson)
        assertEquals(expectedResponse, actualResponse)

        val responseJsonVariant =
            "{\"error\":\"Invalid API Key or your account ran out of credits\"}"
        val actualResponseVariant = ErrorResponse.parseError(responseJsonVariant)
        assertEquals(expectedResponse, actualResponseVariant)
    }

    @Test
    fun parseError_ContainsOnlyErrors() {
        val errors = arrayListOf(
            "Invalid API Key or your account ran out of credits"
        )
        val expectedResponse = ErrorResponse(errors = errors)

        val responseJson = "{\"errors\":[\"Invalid API Key or your account ran out of credits\"]}"
        val actualResponse = ErrorResponse.parseError(responseJson)
        assertEquals(expectedResponse, actualResponse)

        val responseJsonVariant =
            "{\"errors\":\"Invalid API Key or your account ran out of credits\"}"
        val actualResponseVariant = ErrorResponse.parseError(responseJsonVariant)
        assertEquals(expectedResponse, actualResponseVariant)
    }

    @Test
    fun parseError_ContainsOnlyMessage() {
        val errors = arrayListOf(
            "Invalid API Key or your account ran out of credits"
        )
        val expectedResponse = ErrorResponse(errors = errors)

        val responseJson = "{\"message\":[\"Invalid API Key or your account ran out of credits\"]}"
        val actualResponse = ErrorResponse.parseError(responseJson)
        assertEquals(expectedResponse, actualResponse)

        val responseJsonVariant =
            "{\"message\":\"Invalid API Key or your account ran out of credits\"}"
        val actualResponseVariant = ErrorResponse.parseError(responseJsonVariant)
        assertEquals(expectedResponse, actualResponseVariant)
    }

    @Test
    fun parseError_ContainsOnlyMessages() {
        val errors = arrayListOf(
            "Invalid API Key or your account ran out of credits"
        )
        val expectedResponse = ErrorResponse(errors = errors)

        val responseJson = "{\"messages\":[\"Invalid API Key or your account ran out of credits\"]}"
        val actualResponse = ErrorResponse.parseError(responseJson)
        assertEquals(expectedResponse, actualResponse)

        val responseJsonVariant =
            "{\"messages\":\"Invalid API Key or your account ran out of credits\"}"
        val actualResponseVariant = ErrorResponse.parseError(responseJsonVariant)
        assertEquals(expectedResponse, actualResponseVariant)
    }

    @Test
    fun parseError_ContainsOnlySuccess() {
        val expectedResponse = ErrorResponse(success = true)

        val responseJson = "{\"success\": true}"
        val actualResponse = ErrorResponse.parseError(responseJson)
        assertEquals(expectedResponse, actualResponse)

        val responseJsonVariant = "{\"success\":[true]}"
        val actualResponseVariant = ErrorResponse.parseError(responseJsonVariant)
        assertNotEquals(expectedResponse, actualResponseVariant)
    }

    @Test
    fun parseError_ContainsMixedData() {
        val errors = arrayListOf(
            "Invalid API Key or your account ran out of credits",
            "Error messages",
            "More data to look into"
        )
        val expectedResponse = ErrorResponse(success = false, errors = errors)

        val responseJson = "{\n" +
                "    \"success\": false,\n" +
                "    \"error\": \"Invalid API Key or your account ran out of credits\"\n," +
                "    \"data\": \"More data to look into\"\n," +
                "    \"message\": \"Error messages\"\n" +
                "}"
        val actualResponse = ErrorResponse.parseError(responseJson)
        assertEquals(expectedResponse, actualResponse)

        val responseJsonVariant = "{\n" +
                "    \"success\": false,\n" +
                "    \"error\": [\"Invalid API Key or your account ran out of credits\"\n]," +
                "    \"data\": \"More data to look into\"\n," +
                "    \"message\": \"Error messages\"\n" +
                "}"
        val actualResponseVariant = ErrorResponse.parseError(responseJsonVariant)
        assertEquals(expectedResponse, actualResponseVariant)

        val responseJsonVariant2 = "{\n" +
                "    \"success\": false,\n" +
                "    \"error\": [\"Invalid API Key or your account ran out of credits\"\n]," +
                "    \"data\": \"More data to look into\"\n," +
                "    \"message\": [\"Error messages\"\n]" +
                "}"
        val actualResponseVariant2 = ErrorResponse.parseError(responseJsonVariant2)
        assertEquals(expectedResponse, actualResponseVariant2)

        val responseJsonVariant3 = "{\n" +
                "    \"success\": false,\n" +
                "    \"error\": \"Invalid API Key or your account ran out of credits\"\n," +
                "    \"data\": \"More data to look into\"\n," +
                "    \"message\": [\"Error messages\"\n]" +
                "}"
        val actualResponseVariant3 = ErrorResponse.parseError(responseJsonVariant3)
        assertEquals(expectedResponse, actualResponseVariant3)

        errors.apply {
            clear()
            add("Invalid API Key or your account ran out of credits")
            add("Error messages")
            add("More errors")
            add("More data to look into")
        }

        expectedResponse.success = true
        expectedResponse.errors = errors

        val responseJsonVariant4 = "{\n" +
                "    \"success\": true,\n" +
                "    \"error\": \"Invalid API Key or your account ran out of credits\"\n," +
                "    \"data\": \"More data to look into\"\n," +
                "    \"message\": [\"Error messages\", \"More errors\"\n]" +
                "}"
        val actualResponseVariant4 = ErrorResponse.parseError(responseJsonVariant4)
        assertEquals(expectedResponse, actualResponseVariant4)
    }
}