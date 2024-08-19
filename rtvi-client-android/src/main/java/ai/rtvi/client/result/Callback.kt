package ai.rtvi.client.result

/**
 * A callback function receiving a value.
 */
fun interface Callback<T> {
    fun onComplete(value: T)
}



