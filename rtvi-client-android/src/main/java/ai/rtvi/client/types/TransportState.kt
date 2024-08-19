package ai.rtvi.client.types

/**
 * The current state of the session transport.
 */
enum class TransportState {
    Idle,
    Initializing,
    Initialized,
    Authorizing,
    Connecting,
    Connected,
    Ready,
    Disconnected,
    Error
}