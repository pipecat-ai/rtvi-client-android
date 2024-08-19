package ai.rtvi.client

import ai.rtvi.client.types.ServiceConfig
import ai.rtvi.client.types.ServiceRegistration

/**
 * Configuration options when instantiating a [VoiceClient].
 */
data class VoiceClientOptions(
    /**
     * Enable the user mic input.
     *
     * Defaults to true.
     */
    val enableMic: Boolean = true,

    /**
     * Enable user cam input.
     *
     * Defaults to false.
     */
    val enableCam: Boolean = false,

    /**
     * A list of services to use on the backend.
     */
    val services: List<ServiceRegistration> = emptyList(),

    /**
     * Further configuration options for the backend.
     */
    val config: List<ServiceConfig> = emptyList(),

    /**
     * Custom HTTP headers to be sent with the POST request to baseUrl.
     */
    val customHeaders: List<Pair<String, String>> = emptyList()
)