package ai.rtvi.client.types

/**
 * Information about a session participant.
 */
data class Participant(
    val id: ParticipantId,
    val name: String?,

    /**
     * True if this participant represents the local user, false otherwise.
     */
    val local: Boolean
)