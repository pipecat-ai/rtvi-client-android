package ai.rtvi.client.types

/**
 * Media tracks associated with a participant.
 */
data class ParticipantTracks(
    val audio: MediaTrackId?,
    val video: MediaTrackId?,
)