package ai.rtvi.client.result

/**
 * An error occurring due to an HTTP request failing.
 */
sealed interface HttpError {

    /**
     * A humaan-readable description of the error.
     */
    val description: String

    /**
     * The HTTP request returned an invalid status code.
     */
    data class BadStatusCode(val code: Int, val responseBody: String?) : HttpError {
        override val description = "Server returned status code $code: response body '$responseBody'"
    }

    /**
     * An exception was thrown during the HTTP request.
     */
    data class ExceptionThrown(val e: Exception) : HttpError {
        override val description = "An exception was thrown ($e)"
    }

    /**
     * The HTTP response was expected to have a body.
     */
    data object MissingResponseBody : HttpError {
        override val description = "The response had no body data"
    }
}