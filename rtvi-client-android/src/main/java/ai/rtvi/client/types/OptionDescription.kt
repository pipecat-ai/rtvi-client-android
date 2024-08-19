package ai.rtvi.client.types

import kotlinx.serialization.Serializable

@Serializable
data class OptionDescription(
    val name: String,
    val type: Type
)