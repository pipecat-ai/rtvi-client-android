package ai.rtvi.client.result

class RTVIException(
    val error: RTVIError
) : Exception(error.description, error.exception) {

    companion object {
        internal fun <E> from(e: E) = RTVIException(
            (e as? RTVIError) ?: RTVIError.OtherError(e.toString())
        )
    }
}