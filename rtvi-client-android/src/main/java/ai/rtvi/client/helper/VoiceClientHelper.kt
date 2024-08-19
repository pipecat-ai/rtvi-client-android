package ai.rtvi.client.helper

import ai.rtvi.client.VoiceClient
import ai.rtvi.client.result.Future
import ai.rtvi.client.result.VoiceError
import ai.rtvi.client.result.VoiceException
import ai.rtvi.client.result.resolvedPromiseErr
import ai.rtvi.client.transport.MsgServerToClient
import ai.rtvi.client.types.Option
import ai.rtvi.client.utils.ThreadRef
import java.util.concurrent.atomic.AtomicReference

abstract class VoiceClientHelper {

    private val voiceClient = AtomicReference<RegisteredVoiceClient?>(null)

    protected fun <R> withClient(
        action: (RegisteredVoiceClient) -> Future<R, VoiceError>
    ): Future<R, VoiceError> {

        val client = voiceClient.get() ?: return resolvedPromiseErr(
            ThreadRef.forMain(),
            VoiceError.HelperNotRegistered
        )

        return client.client.thread.runOnThreadReturningFuture { action(client) }
    }

    protected val client: RegisteredVoiceClient?
        get() = voiceClient.get()

    /**
     * Handle a message received from the backend.
     */
    abstract fun handleMessage(msg: MsgServerToClient)

    /**
     * Returns a list of message types supported by this helper. Messages received from the
     * backend which have these types will be passed to [handleMessage].
     */
    abstract fun getMessageTypes(): Set<String>

    @Throws(VoiceException::class)
    internal fun registerVoiceClient(client: RegisteredVoiceClient) {
        if (!this.voiceClient.compareAndSet(null, client)) {
            throw VoiceException(VoiceError.OtherError("Helper is already registered to a client"))
        }
    }

    @Throws(VoiceException::class)
    internal fun unregisterVoiceClient() {
        if (voiceClient.getAndSet(null) == null) {
            throw VoiceException(VoiceError.OtherError("Helper is not registered to a client"))
        }
    }
}

class RegisteredVoiceClient(
    val client: VoiceClient,
    val service: String,
) {
    fun action(action: String, args: List<Option> = emptyList()) =
        client.action(service = service, action = action, arguments = args)
}