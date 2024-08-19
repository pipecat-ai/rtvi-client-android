package ai.rtvi.client.transport

import ai.rtvi.client.VoiceClientOptions
import ai.rtvi.client.VoiceEventCallbacks
import ai.rtvi.client.utils.ThreadRef

/**
 * Context for an RTVI transport.
 */
interface TransportContext {

    val options: VoiceClientOptions
    val callbacks: VoiceEventCallbacks
    val thread: ThreadRef

    /**
     * Invoked by the transport when an RTVI message is received.
     */
    fun onMessage(msg: MsgServerToClient)
}