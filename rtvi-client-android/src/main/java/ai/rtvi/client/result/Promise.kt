package ai.rtvi.client.result

import ai.rtvi.client.utils.ThreadRef

/**
 * Represents an ongoing asynchronous operation.
 *
 * Promise implements the Future interface, which means it can be returned from
 * functions performing asynchronous operations to provide callers a flexible way
 * to wait for the response.
 */
class Promise<V, E>(override val thread: ThreadRef) : Future<V, E> {

    private var value: Result<V, E>? = null
    private val pendingCallbacks = mutableListOf<ResultCallback<V, E>>()

    /**
     * Resolve the promise, notifying any pending callbacks.
     *
     * If a promise is resolved twice, the second invocation of resolve() will be ignored.
     */
    fun resolve(value: Result<V, E>) {
        thread.runOnThread {
            if (this.value == null) {
                this.value = value
                pendingCallbacks.forEach {
                    it.onResult(value)
                }
            }
        }
    }

    /**
     * Convenience method to resolve a promise with a success value.
     */
    fun resolveOk(value: V) = resolve(Result.Ok(value))

    /**
     * Convenience method to resolve a promise with an error.
     */
    fun resolveErr(error: E) = resolve(Result.Err(error))

    override fun withCallback(callback: ResultCallback<V, E>): Future<V, E> {
        thread.runOnThread {
            val currentVal = value

            if (currentVal != null) {
                callback.onResult(currentVal)
            } else {
                pendingCallbacks.add(callback)
            }
        }
        return this
    }
}

/**
 * Convenience method to create and return a Promise.
 */
fun <V, E> withPromise(thread: ThreadRef, action: (Promise<V, E>) -> Unit): Future<V, E> {
    return Promise<V, E>(thread).apply(action)
}

/**
 * Returns a Future which has already resolved successfully.
 */
fun <V, E> resolvedPromiseOk(thread: ThreadRef, value: V): Future<V, E> {
    return Promise<V, E>(thread).apply {
        resolveOk(value)
    }
}

/**
 * Returns a Future which has already resolved with an error.
 */
fun <V, E> resolvedPromiseErr(thread: ThreadRef, error: E): Future<V, E> {
    return Promise<V, E>(thread).apply {
        resolveErr(error)
    }
}