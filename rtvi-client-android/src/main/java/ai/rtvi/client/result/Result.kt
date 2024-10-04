package ai.rtvi.client.result

/**
 * The result of an operation: either Result.Ok or Result.Err.
 */
sealed interface Result<out V, out E> {

    /**
     * The operation completed successfully.
     */
    data class Ok<V>(
        /**
         * The return value of the operation.
         */
        val value: V
    ) : Result<V, Nothing>

    /**
     * The operation failed.
     */
    data class Err<E>(
        /**
         * The error returned by the operation.
         */
        val error: E
    ) : Result<Nothing, E>

    /**
     * If the result is a success, this method transforms the value to a different type.
     */
    fun <V2> map(filter: (V) -> V2) = when (this) {
        is Err -> this
        is Ok -> Ok(filter(value))
    }

    /**
     * If the result is an error, this method transforms the error to a different type.
     */
    fun <E2> mapError(filter: (E) -> E2) = when (this) {
        is Err -> Err(filter(error))
        is Ok -> this
    }

    /**
     * If the operation failed, throw a VoiceException, otherwise return the result.
     */
    @Throws(RTVIException::class)
    fun throwError(): V = when(this) {
        is Err -> throw RTVIException.from(error)
        is Ok -> value
    }

    /**
     * Returns true if the operation succeeded, or false if there was an error.
     */
    val ok
        get() = this is Ok

    /**
     * If the operation was successful, this is the return value. Otherwise this is null.
     */
    val valueOrNull: V?
        get() = (this as? Ok)?.value

    /**
     * If the operation failed, this is the error. Otherwise this is null.
     */
    val errorOrNull: E?
        get() = (this as? Err)?.error
}

/**
 * Kotlin convenience method: return the value if Ok, otherwise invoke the callback with the error.
 */
inline fun <V, E> Result<V, E>.orError(onError: (E) -> Nothing): V {
    return when (this) {
        is Result.Err -> onError(error)
        is Result.Ok -> value
    }
}

/**
 * Kotlin convenience method: invoke the callback if the result is an error.
 */
inline fun <V, E> Result<V, E>.ifError(onError: (E) -> Unit) {
    when (this) {
        is Result.Err -> onError(error)
        is Result.Ok -> {}
    }
}