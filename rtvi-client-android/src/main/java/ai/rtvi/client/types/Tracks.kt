package ai.rtvi.client.types

/**
 * Media tracks for the local user and remote bot.
 */
data class Tracks(
    val local: ParticipantTracks,
    val bot: ParticipantTracks?,
)