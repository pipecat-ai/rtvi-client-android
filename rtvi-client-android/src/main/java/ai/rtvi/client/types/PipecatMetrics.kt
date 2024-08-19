package ai.rtvi.client.types

import kotlinx.serialization.Serializable

/**
 * Metrics received from a Pipecat instance.
 */
@Serializable
data class PipecatMetrics(
    val processing: List<PipecatMetricsData>? = null,
    val ttfb: List<PipecatMetricsData>? = null,
)