package ai.rtvi.client.types

import kotlinx.serialization.Serializable

/**
 * Metrics data received from a Pipecat instance.
 */
@Serializable
data class PipecatMetricsData(
    val processor: String,
    val value: Double
)