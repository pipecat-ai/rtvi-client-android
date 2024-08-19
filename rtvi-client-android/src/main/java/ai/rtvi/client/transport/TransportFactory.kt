package ai.rtvi.client.transport

/**
 * A creator of Transport objects.
 */
fun interface TransportFactory {
    fun createTransport(context: TransportContext): Transport
}