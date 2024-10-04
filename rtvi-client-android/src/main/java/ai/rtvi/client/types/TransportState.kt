package ai.rtvi.client.types

/**
 * The current state of the session transport.
 */
enum class TransportState {
    Disconnected,
    Initializing,
    Initialized,
    Authorizing,
    Connecting,
    Connected,
    Ready,
    Error
}