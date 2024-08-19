package ai.rtvi.client.types

import kotlinx.serialization.Serializable

@Serializable
data class ActionDescription(
    val service: String,
    val action: String,
    val arguments: List<OptionDescription>,
    val result: Type
)