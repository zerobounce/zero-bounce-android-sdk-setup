package com.zerobounce.android

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * The model used for when a request throws an error. This model was introduced in order to provide
 * a standardized way of handling the error responses that a request can return.
 *
 * If the error JSON received from the server includes the words "error" or "message", then the
 * values of those keys will be added to the [errors] array. If the error is not a JSON dictionary,
 * then JSON String will be added to the [errors] array.
 *
 * If any type of messages are received, then they will be added according to the same rule above
 * after the errors found above.
 */
data class ErrorResponse(

    var success: Boolean? = null,

    var errors: ArrayList<String> = arrayListOf()
) {

    companion object {
        /**
         * Tries to parse the given [error] String into a LinkedHashMap (i.e.: a dictionary
         * structure) in order to provide a standardized way of handling the various error
         * responses.
         *
         * If the [error] cannot be parsed, then the error will be added as is in the [errors]
         * array.
         *
         * @param error the error that will be parsed
         *
         * @return an [ErrorResponse] object
         */
        fun parseError(error: String?): ErrorResponse {
            val response = ErrorResponse()
            error ?: return response

            val errors = ArrayList<String>()
            val otherMessages = ArrayList<String>()

            val type = object : TypeToken<LinkedHashMap<String, Any?>>() {}.type

            try {
                val hashMap: LinkedHashMap<String, Any?> = Gson().fromJson(error, type)
                for ((key, value) in hashMap) {
                    if (key.contains("error") || key.contains("message")) {
                        if (value is ArrayList<*>) {
                            val values = value as? ArrayList<String>
                            values?.let { errors.addAll(it) }
                        } else if (value != null) {
                            errors.add(value.toString())
                        }
                    } else {
                        if (key == "success" && value is Boolean) {
                            response.success = value
                        } else if (value is ArrayList<*>) {
                            val values = value as? ArrayList<String>
                            values?.let { otherMessages.addAll(it) }
                        } else if (value != null) {
                            otherMessages.add(value.toString())
                        }
                    }
                }
                errors.addAll(otherMessages)
            } catch (t: Throwable) {
                // If the error couldn't be parsed as a JSON, then we'll show the actual error we
                // received
                errors.add(error)
            }
            response.errors = errors
            return response
        }
    }
}