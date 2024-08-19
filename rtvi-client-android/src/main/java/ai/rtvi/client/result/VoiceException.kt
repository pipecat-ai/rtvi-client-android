package ai.rtvi.client.result

class VoiceException(
    val error: VoiceError
) : Exception(error.description, error.exception) {

    companion object {
        internal fun <E> from(e: E) = VoiceException(
            (e as? VoiceError) ?: VoiceError.OtherError(e.toString())
        )
    }
}