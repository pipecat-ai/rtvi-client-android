package ai.rtvi.client.utils

import ai.rtvi.client.result.Future
import ai.rtvi.client.result.withPromise
import android.os.Handler
import android.os.Looper

/**
 * The thread used by the VoiceClient for callbacks and other operations.
 */
class ThreadRef(
    val thread: Thread,
    val handler: Handler,
) {
    companion object {
        fun forCurrent() = ThreadRef(
            Thread.currentThread(),
            Handler(
                Looper.myLooper()
                    ?: throw Exception("Must be called from the main thread, or a thread with a registered Looper")
            )
        )

        fun forMain() = ThreadRef(
            Looper.getMainLooper().thread,
            Handler(Looper.getMainLooper())
        )
    }

    /**
     * Returns true if this thread is the current thread.
     */
    fun isCurrent() = thread == Thread.currentThread()

    /**
     * Runs the specified Runnable on this thread. If this thread is the current thread,
     * this will be executed immediately.
     */
    fun runOnThread(action: Runnable) {
        if (isCurrent()) {
            action.run()
        } else {
            handler.post(action)
        }
    }

    /**
     * Runs the specified action on this thread, returning a `Future` to the original thread.
     */
    fun <V, E> runOnThreadReturningFuture(action: () -> Future<V, E>): Future<V, E> =
        withPromise(this) { promise ->
            runOnThread {
                action().withCallback(promise::resolve)
            }
        }

    /**
     * Throws an exception if this thread is not the current thread.
     */
    fun assertCurrent() {
        if (!isCurrent()) {
            throw Exception("Must be invoked from the same thread as the constructor")
        }
    }

    fun <R> assertCurrent(action: () -> R): R {
        assertCurrent()
        return action()
    }
}