package ai.rtvi.client

import ai.rtvi.client.transport.MsgServerToClient
import ai.rtvi.client.types.MediaDeviceInfo
import ai.rtvi.client.types.Participant
import ai.rtvi.client.types.PipecatMetrics
import ai.rtvi.client.types.ServiceConfig
import ai.rtvi.client.types.Tracks
import ai.rtvi.client.types.Transcript
import ai.rtvi.client.types.TransportState

/**
 * Callbacks invoked when changes occur in the voice session.
 */
@Suppress("unused")
abstract class VoiceEventCallbacks {

    /**
     * Invoked when the underlying transport has connected.
     */
    open fun onConnected() {}

    /**
     * Invoked when the underlying transport has disconnected.
     */
    open fun onDisconnected() {}

    /**
     * Invoked when the session state has changed.
     */
    open fun onTransportStateChanged(state: TransportState) {}

    /**
     * Invoked when the bot has connected to the session.
     */
    open fun onBotConnected(participant: Participant) {}

    /**
     * Invoked when the bot has indicated it is ready for commands.
     */
    open fun onBotReady(
        version: String,
        config: List<ServiceConfig>
    ) {}

    /**
     * An error has occurred in the RTVI backend.
     */
    abstract fun onBackendError(message: String)

    /**
     * Invoked when the bot has disconnected from the session.
     */
    open fun onBotDisconnected(participant: Participant) {}

    /**
     * Invoked when a participant has joined the session.
     */
    open fun onParticipantJoined(participant: Participant) {}

    /**
     * Invoked when a participant has left the session.
     */
    open fun onParticipantLeft(participant: Participant) {}

    /**
     * Invoked when the list of available cameras has changed.
     */
    open fun onAvailableCamsUpdated(cams: List<MediaDeviceInfo>) {}

    /**
     * Invoked when the list of available microphones has updated.
     */
    open fun onAvailableMicsUpdated(mics: List<MediaDeviceInfo>) {}

    /**
     * Invoked regularly with the volume of the locally captured audio.
     */
    open fun onUserAudioLevel(level: Float) {}

    /**
     * Invoked regularly with the audio volume of each remote participant.
     */
    open fun onRemoteAudioLevel(level: Float, participant: Participant) {}

    /**
     * Invoked when the bot starts talking.
     */
    open fun onBotStartedSpeaking() {}

    /**
     * Invoked when the bot stops talking.
     */
    open fun onBotStoppedSpeaking() {}

    /**
     * Invoked when the local user starts talking.
     */
    open fun onUserStartedSpeaking() {}

    /**
     * Invoked when the local user stops talking.
     */
    open fun onUserStoppedSpeaking() {}

    /**
     * Invoked when session metrics are received.
     */
    open fun onPipecatMetrics(data: PipecatMetrics) {}

    /**
     * Invoked when user transcript data is avaiable.
     */
    open fun onUserTranscript(data: Transcript) {}

    /**
     * Invoked when bot transcript data is avaiable.
     */
    open fun onBotTranscript(text: String) {}

    /**
     * Invoked when a message from the backend is received which was not handled
     * by the VoiceClient or a registered helper.
     */
    open fun onGenericMessage(msg: MsgServerToClient) {}

    /**
     * Invoked when the state of the input devices changes.
     */
    open fun onInputsUpdated(camera: Boolean, mic: Boolean) {}

    /**
     * Invoked when the set of available cam/mic tracks changes.
     */
    open fun onTracksUpdated(tracks: Tracks) {}
}

internal class CallbackInterceptor(vararg listeners: VoiceEventCallbacks): VoiceEventCallbacks() {
    
    private val callbacks = listeners.toMutableList()
    
    fun addListener(listener: VoiceEventCallbacks) {
        callbacks.add(listener)
    }
    
    override fun onConnected() {
        callbacks.forEach { it.onConnected() }
    }

    override fun onDisconnected() {
        callbacks.forEach { it.onDisconnected() }
    }

    override fun onTransportStateChanged(state: TransportState) {
        callbacks.forEach { it.onTransportStateChanged(state) }
    }

    override fun onBotConnected(participant: Participant) {
        callbacks.forEach { it.onBotConnected(participant) }
    }

    override fun onBotReady(version: String, config: List<ServiceConfig>) {
        callbacks.forEach { it.onBotReady(version, config) }
    }

    override fun onBackendError(message: String) {
        callbacks.forEach { it.onBackendError(message) }
    }

    override fun onBotDisconnected(participant: Participant) {
        callbacks.forEach { it.onBotDisconnected(participant) }
    }

    override fun onParticipantJoined(participant: Participant) {
        callbacks.forEach { it.onParticipantJoined(participant) }
    }

    override fun onParticipantLeft(participant: Participant) {
        callbacks.forEach { it.onParticipantLeft(participant) }
    }

    override fun onAvailableCamsUpdated(cams: List<MediaDeviceInfo>) {
        callbacks.forEach { it.onAvailableCamsUpdated(cams) }
    }

    override fun onAvailableMicsUpdated(mics: List<MediaDeviceInfo>) {
        callbacks.forEach { it.onAvailableMicsUpdated(mics) }
    }

    override fun onUserAudioLevel(level: Float) {
        callbacks.forEach { it.onUserAudioLevel(level) }
    }

    override fun onRemoteAudioLevel(level: Float, participant: Participant) {
        callbacks.forEach { it.onRemoteAudioLevel(level, participant) }
    }

    override fun onBotStartedSpeaking() {
        callbacks.forEach { it.onBotStartedSpeaking() }
    }

    override fun onBotStoppedSpeaking() {
        callbacks.forEach { it.onBotStoppedSpeaking() }
    }

    override fun onUserStartedSpeaking() {
        callbacks.forEach { it.onUserStartedSpeaking() }
    }

    override fun onUserStoppedSpeaking() {
        callbacks.forEach { it.onUserStoppedSpeaking() }
    }

    override fun onPipecatMetrics(data: PipecatMetrics) {
        callbacks.forEach { it.onPipecatMetrics(data) }
    }

    override fun onUserTranscript(data: Transcript) {
        callbacks.forEach { it.onUserTranscript(data) }
    }

    override fun onBotTranscript(text: String) {
        callbacks.forEach { it.onBotTranscript(text) }
    }

    override fun onGenericMessage(msg: MsgServerToClient) {
        callbacks.forEach { it.onGenericMessage(msg) }
    }

    override fun onInputsUpdated(camera: Boolean, mic: Boolean) {
        callbacks.forEach { it.onInputsUpdated(camera = camera, mic = mic) }
    }

    override fun onTracksUpdated(tracks: Tracks) {
        callbacks.forEach { it.onTracksUpdated(tracks) }
    }
}