package ai.rtvi.client.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A written transcript of some spoken words.
 */
@Serializable
data class Transcript(
    val text: String,
    val final: Boolean,
    val timestamp: String,
    @SerialName("user_id")
    val userId: String
)