package ai.rtvi.client.types

import kotlinx.serialization.Serializable

@Serializable
data class ServiceRegistration(
    val service: String,
    val value: String
)