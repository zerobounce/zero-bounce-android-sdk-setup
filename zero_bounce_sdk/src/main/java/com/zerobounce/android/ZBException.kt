package com.zerobounce.android

/**
 * A model class used for throwing exceptions related to the SDK (validation errors any other kind
 * of errors that are unrelated to the server's response). This class is not and should not be used
 * for throwing any kind of server errors. For error responses, use [ErrorResponse] instead.
 */
class ZBException(message: String?) : Exception(message)