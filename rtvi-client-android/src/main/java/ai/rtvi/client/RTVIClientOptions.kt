package ai.rtvi.client

import ai.rtvi.client.types.ServiceConfig
import ai.rtvi.client.types.ServiceRegistration
import ai.rtvi.client.types.Value

/**
 * Configuration options when instantiating a [RTVIClient].
 */
data class RTVIClientOptions(

    /**
     * Connection parameters.
     */
    val params: RTVIClientParams,

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
    val services: List<ServiceRegistration>? = null,

    /**
     * Further configuration options for the backend.
     */
    @Deprecated("Use params.config")
    val config: List<ServiceConfig> = emptyList(),

    /**
     * Custom HTTP headers to be sent with the POST request to baseUrl.
     */
    @Deprecated("Use params.headers")
    val customHeaders: List<Pair<String, String>> = emptyList(),

    /**
     * Custom parameters to add to the auth request body.
     */
    @Deprecated("Use params.requestData")
    val customBodyParams: List<Pair<String, Value>> = emptyList()
)