package ai.rtvi.client.result

import ai.rtvi.client.utils.ThreadRef
import android.util.Log
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * The output of an asynchronous operation.
 *
 * When the operation completes, it may return an error.
 */
interface Future<V, E> {

    val thread: ThreadRef

    /**
     * Give a callback when the Future completes.
     */
    fun withCallback(callback: ResultCallback<V, E>): Future<V, E>

    /**
     * Give a callback if the Future returns an error.
     */
    fun withErrorCallback(callback: Callback<E>) = withCallback {
        if (it is Result.Err) {
            callback.onComplete(it.error)
        }
    }

    /**
     * If the Future returns successfully, transform the result to another type.
     */
    fun <V2> map(filter: (V) -> V2): Future<V2, E> = withPromise(thread) { promise ->
        withCallback { result ->
            promise.resolve(result.map(filter))
        }
    }

    /**
     * If the Future returns an error, transform the error to another type.
     */
    fun <E2> mapError(filter: (E) -> E2): Future<V, E2> = withPromise(thread) { promise ->
        withCallback { result ->
            promise.resolve(result.mapError(filter))
        }
    }

    /**
     * When this Future completes, run another async operation.
     *
     * @return A Future that will resolve when both operations have completed.
     */
    fun <V2> chain(action: (V) -> Future<V2, E>): Future<V2, E> {
        val promise = Promise<V2, E>(thread)

        withCallback { firstResult ->

            when (firstResult) {
                is Result.Err -> promise.resolve(firstResult)
                is Result.Ok -> {
                    action(firstResult.value).withCallback { secondResult ->
                        promise.resolve(secondResult)
                    }
                }
            }
        }

        return promise
    }

    /**
     * Wait for the Future to complete using Kotlin Coroutines. Will return
     * errors rather than throwing an exception.
     */
    suspend fun awaitNoThrow(): Result<V, E> {
        return suspendCoroutine { continuation ->
            withCallback {
                continuation.resume(it)
            }
        }
    }

    /**
     * Wait for the Future to complete using Kotlin Coroutines, and throw
     * a VoiceException if the operation failed.
     */
    @Throws(RTVIException::class)
    suspend fun await() = awaitNoThrow().throwError()

    /**
     * If this operation fails, log an error.
     */
    fun logError(tag: String, description: String) = withErrorCallback {
        Log.e(tag, "Operation $description failed: $it")
    }
}

/**
 * Returns a new `Future` which times out after the specified duration.
 *
 * @param durationMs Timeout in milliseconds.
 */
fun <V> Future<V, RTVIError>.withTimeout(durationMs: Long): Future<V, RTVIError> =
    withPromise(thread) { promise ->

        val timeoutEvent = Runnable {
            promise.resolveErr(RTVIError.Timeout)
        }

        this.withCallback {
            promise.resolve(it)
            thread.handler.removeCallbacks(timeoutEvent)
        }

        thread.handler.postDelayed(timeoutEvent, durationMs)
    }