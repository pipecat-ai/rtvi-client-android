package ai.rtvi.client.transport

import ai.rtvi.client.result.Future
import ai.rtvi.client.result.VoiceError
import ai.rtvi.client.types.MediaDeviceId
import ai.rtvi.client.types.MediaDeviceInfo
import ai.rtvi.client.types.Tracks
import ai.rtvi.client.types.TransportState

/**
 * An RTVI transport.
 */
abstract class Transport {

    abstract fun initDevices(): Future<Unit, VoiceError>
    abstract fun release()

    abstract fun connect(authBundle: AuthBundle): Future<Unit, VoiceError>

    abstract fun disconnect(): Future<Unit, VoiceError>

    abstract fun getAllMics(): Future<List<MediaDeviceInfo>, VoiceError>
    abstract fun getAllCams(): Future<List<MediaDeviceInfo>, VoiceError>

    abstract fun updateMic(micId: MediaDeviceId): Future<Unit, VoiceError>
    abstract fun updateCam(camId: MediaDeviceId): Future<Unit, VoiceError>

    abstract fun selectedMic(): MediaDeviceInfo?
    abstract fun selectedCam(): MediaDeviceInfo?

    abstract fun enableMic(enable: Boolean): Future<Unit, VoiceError>
    abstract fun enableCam(enable: Boolean): Future<Unit, VoiceError>

    abstract fun isCamEnabled(): Boolean
    abstract fun isMicEnabled(): Boolean

    /**
     * The expiry time for the transport session, if applicable. Measured in seconds
     * since the UNIX epoch (UTC).
     */
    abstract fun expiry(): Long?

    abstract fun sendMessage(message: MsgClientToServer): Future<Unit, VoiceError>

    abstract fun state(): TransportState
    abstract fun setState(state: TransportState)

    abstract fun tracks(): Tracks
}