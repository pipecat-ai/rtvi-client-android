package ai.rtvi.client.utils

import ai.rtvi.client.types.ServiceConfig
import kotlinx.serialization.Serializable

@Serializable
internal data class ConnectionBundle(
    val services: Map<String, String>,
    val config: List<ServiceConfig>
)