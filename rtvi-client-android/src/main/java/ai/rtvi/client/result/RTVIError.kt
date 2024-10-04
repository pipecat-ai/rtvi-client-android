package ai.rtvi.client.result

import ai.rtvi.client.types.TransportState

/**
 * An error occurring during an operation.
 */
abstract class RTVIError {

    /**
     * A human-readable description of the error.
     */
    abstract val description: String

    /**
     * If the error was caused by an exception, this value is set.
     */
    open val exception: Exception? = null

    override fun toString() = description

    /**
     * Failed to fetch the authentication bundle from the RTVI backend.
     */
    data class HttpError(val error: ai.rtvi.client.result.HttpError) : RTVIError() {
        override val description = error.description
    }

    /**
     * An exception was thrown.
     */
    data class ExceptionThrown(override val exception: Exception) : RTVIError() {
        override val description = "An exception was thrown ($exception)"
    }

    /**
     * An unknown error occurred.
     */
    data class OtherError(val message: String) : RTVIError() {
        override val description = message
    }

    /**
     * Operation cannot be performed because the transport is not initialized.
     */
    data object TransportNotInitialized : RTVIError() {
        override val description = "Transport not initialized"
    }

    /**
     * Operation cannot be performed in this state.
     */
    data class InvalidState(
        val expected: TransportState,
        val actual: TransportState
    ) : RTVIError() {
        override val description = "Invalid state: expected ${expected.name}, actual ${actual.name}"
    }

    /**
     * The operation was cancelled before it could complete.
     */
    data object OperationCancelled : RTVIError() {
        override val description = "The operation was cancelled"
    }

    data class ErrorResponse(
        val message: String
    ) : RTVIError() {
        override val description = "Received error response from backend: $message"
    }

    /**
     * The operation timed out before it could complete.
     */
    data object Timeout : RTVIError() {
        override val description = "The operation timed out"
    }

    /**
     * The previous connection is still active.
     */
    data object PreviousConnectionStillActive : RTVIError() {
        override val description = "The previous connection is still active"
    }

    /**
     * This helper is not registered to a VoiceClient.
     */
    data object HelperNotRegistered : RTVIError() {
        override val description = "This helper is not registered to a VoiceClient"
    }
}