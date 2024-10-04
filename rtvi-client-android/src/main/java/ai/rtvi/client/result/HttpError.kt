package ai.rtvi.client.result

/**
 * An error occurring due to an HTTP request failing.
 */
sealed interface HttpError {

    /**
     * A human-readable description of the error.
     */
    val description: String

    /**
     * The URL of the failed request.
     */
    val url: String

    /**
     * The HTTP request returned an invalid status code.
     */
    data class BadStatusCode(
        override val url: String,
        val code: Int,
        val responseBody: String?
    ) : HttpError {
        override val description =
            "Server returned status code $code: response body '$responseBody'"
    }

    /**
     * An exception was thrown during the HTTP request.
     */
    data class ExceptionThrown(override val url: String, val e: Exception) : HttpError {
        override val description = "An exception was thrown ($e)"
    }

    /**
     * The HTTP response was expected to have a body.
     */
    data class MissingResponseBody(override val url: String) : HttpError {
        override val description = "The response had no body data"
    }
}