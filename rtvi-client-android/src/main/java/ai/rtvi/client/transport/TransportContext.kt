package ai.rtvi.client.transport

import ai.rtvi.client.RTVIClientOptions
import ai.rtvi.client.RTVIEventCallbacks
import ai.rtvi.client.utils.ThreadRef

/**
 * Context for an RTVI transport.
 */
interface TransportContext {

    val options: RTVIClientOptions
    val callbacks: RTVIEventCallbacks
    val thread: ThreadRef

    /**
     * Invoked by the transport when an RTVI message is received.
     */
    fun onMessage(msg: MsgServerToClient)
}