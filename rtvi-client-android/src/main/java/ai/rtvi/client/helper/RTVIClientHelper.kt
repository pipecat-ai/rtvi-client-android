package ai.rtvi.client.helper

import ai.rtvi.client.RTVIClient
import ai.rtvi.client.result.Future
import ai.rtvi.client.result.RTVIError
import ai.rtvi.client.result.RTVIException
import ai.rtvi.client.result.resolvedPromiseErr
import ai.rtvi.client.transport.MsgServerToClient
import ai.rtvi.client.types.Option
import ai.rtvi.client.utils.ThreadRef
import java.util.concurrent.atomic.AtomicReference

abstract class RTVIClientHelper {

    private val voiceClient = AtomicReference<RegisteredRTVIClient?>(null)

    protected fun <R> withClient(
        action: (RegisteredRTVIClient) -> Future<R, RTVIError>
    ): Future<R, RTVIError> {

        val client = voiceClient.get() ?: return resolvedPromiseErr(
            ThreadRef.forMain(),
            RTVIError.HelperNotRegistered
        )

        return client.client.thread.runOnThreadReturningFuture { action(client) }
    }

    protected val client: RegisteredRTVIClient?
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

    @Throws(RTVIException::class)
    internal fun registerVoiceClient(client: RegisteredRTVIClient) {
        if (!this.voiceClient.compareAndSet(null, client)) {
            throw RTVIException(RTVIError.OtherError("Helper is already registered to a client"))
        }
    }

    @Throws(RTVIException::class)
    internal fun unregisterVoiceClient() {
        if (voiceClient.getAndSet(null) == null) {
            throw RTVIException(RTVIError.OtherError("Helper is not registered to a client"))
        }
    }
}

class RegisteredRTVIClient(
    val client: RTVIClient,
    val service: String,
) {
    fun action(action: String, args: List<Option> = emptyList()) =
        client.action(service = service, action = action, arguments = args)
}