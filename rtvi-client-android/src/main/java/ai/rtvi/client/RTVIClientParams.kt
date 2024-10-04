package ai.rtvi.client

import ai.rtvi.client.types.RTVIURLEndpoints
import ai.rtvi.client.types.ServiceConfig
import ai.rtvi.client.types.Value

/**
 * Connection options when instantiating a [RTVIClient].
 */
data class RTVIClientParams(

    /**
     * The base URL for the RTVI POST request.
     */
    val baseUrl: String,

    /**
     * Custom HTTP headers to be sent with the POST request.
     */
    val headers: List<Pair<String, String>> = emptyList(),

    /**
     * API endpoint names for the RTVI POST requests.
     */
    val endpoints: RTVIURLEndpoints = RTVIURLEndpoints(),

    /**
     * Custom parameters to add to the auth request body.
     */
    val requestData: List<Pair<String, Value>> = emptyList(),

    /**
     * Further configuration options for the backend.
     */
    val config: List<ServiceConfig> = emptyList(),
)