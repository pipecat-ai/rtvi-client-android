package ai.rtvi.client.result

import ai.rtvi.client.types.TransportState

/**
 * An error occurring during an operation.
 */
abstract class VoiceError {

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
    data class FailedToFetchAuthBundle(val error: HttpError) : VoiceError() {
        override val description = "Failed to fetch the auth bundle: ${error.description}"
    }

    /**
     * An exception was thrown.
     */
    data class ExceptionThrown(override val exception: Exception) : VoiceError() {
        override val description = "An exception was thrown ($exception)"
    }

    /**
     * An unknown error occurred.
     */
    data class OtherError(val message: String) : VoiceError() {
        override val description = message
    }

    /**
     * Operation cannot be performed because the transport is not initialized.
     */
    data object TransportNotInitialized : VoiceError() {
        override val description = "Transport not initialized"
    }

    /**
     * Operation cannot be performed in this state.
     */
    data class InvalidState(
        val expected: TransportState,
        val actual: TransportState
    ) : VoiceError() {
        override val description = "Invalid state: expected ${expected.name}, actual ${actual.name}"
    }

    /**
     * The operation was cancelled before it could complete.
     */
    data object OperationCancelled : VoiceError() {
        override val description = "The operation was cancelled"
    }

    data class ErrorResponse(
        val message: String
    ) : VoiceError() {
        override val description = "Received error response from backend: $message"
    }

    /**
     * The operation timed out before it could complete.
     */
    data object Timeout : VoiceError() {
        override val description = "The operation timed out"
    }

    /**
     * The previous connection is still active.
     */
    data object PreviousConnectionStillActive : VoiceError() {
        override val description = "The previous connection is still active"
    }

    /**
     * This helper is not registered to a VoiceClient.
     */
    data object HelperNotRegistered : VoiceError() {
        override val description = "This helper is not registered to a VoiceClient"
    }
}