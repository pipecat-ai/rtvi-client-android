package ai.rtvi.client

import ai.rtvi.client.helper.RTVIClientHelper
import ai.rtvi.client.helper.RegisteredRTVIClient
import ai.rtvi.client.result.Future
import ai.rtvi.client.result.Promise
import ai.rtvi.client.result.RTVIError
import ai.rtvi.client.result.RTVIException
import ai.rtvi.client.result.Result
import ai.rtvi.client.result.resolvedPromiseErr
import ai.rtvi.client.result.withPromise
import ai.rtvi.client.result.withTimeout
import ai.rtvi.client.transport.AuthBundle
import ai.rtvi.client.transport.MsgClientToServer
import ai.rtvi.client.transport.MsgServerToClient
import ai.rtvi.client.transport.Transport
import ai.rtvi.client.transport.TransportContext
import ai.rtvi.client.transport.TransportFactory
import ai.rtvi.client.types.ActionDescription
import ai.rtvi.client.types.Config
import ai.rtvi.client.types.MediaDeviceId
import ai.rtvi.client.types.Option
import ai.rtvi.client.types.RegisteredHelper
import ai.rtvi.client.types.ServiceConfig
import ai.rtvi.client.types.ServiceConfigDescription
import ai.rtvi.client.types.Transcript
import ai.rtvi.client.types.TransportState
import ai.rtvi.client.types.Value
import ai.rtvi.client.utils.ConnectionBundle
import ai.rtvi.client.utils.JSON_INSTANCE
import ai.rtvi.client.utils.ThreadRef
import ai.rtvi.client.utils.parseServerSentEvents
import ai.rtvi.client.utils.post
import ai.rtvi.client.utils.valueFrom
import android.util.Log
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

private const val RTVI_PROTOCOL_VERSION = "0.2.0"

/**
 * An RTVI client. Connects to an RTVI backend and handles bidirectional audio and video
 * streaming.
 *
 * The client must be cleaned up using the [release] method when it is no longer required.
 *
 * @param transport Transport for media streaming.
 * @param callbacks Callbacks invoked when changes occur in the voice session.
 * @param options Additional options for configuring the client and backend.
 */
@Suppress("unused")
open class RTVIClient(
    transport: TransportFactory,
    callbacks: RTVIEventCallbacks,
    private var options: RTVIClientOptions,
) {
    companion object {
        private const val TAG = "VoiceClient"
    }

    /**
     * The thread used by the VoiceClient for callbacks and other operations.
     */
    val thread = ThreadRef.forCurrent()

    private val callbacks = CallbackInterceptor(object : RTVIEventCallbacks() {
        override fun onBackendError(message: String) {}

        override fun onDisconnected() {
            discardWaitingResponses()
            connection?.ready?.resolveErr(RTVIError.OperationCancelled)
            connection = null
        }
    }, callbacks)

    private val helpers = mutableMapOf<String, RegisteredHelper>()

    private val awaitingServerResponse =
        mutableMapOf<String, (Result<JsonElement, RTVIError>) -> Unit>()

    private inline fun handleResponse(
        msg: MsgServerToClient,
        action: ((Result<JsonElement, RTVIError>) -> Unit) -> Unit
    ) {
        val id = msg.id ?: throw Exception("${msg.type} missing ID")

        if (id == "END") {
            return
        }

        val respondTo = awaitingServerResponse.remove(id)
            ?: throw Exception("${msg.type}: no responder for $id")

        action(respondTo)
    }

    private val transportCtx = object : TransportContext {

        override val options
            get() = this@RTVIClient.options

        override val callbacks
            get() = this@RTVIClient.callbacks

        override val thread = this@RTVIClient.thread

        override fun onMessage(msg: MsgServerToClient) = thread.runOnThread {

            try {
                when (msg.type) {
                    MsgServerToClient.Type.BotReady -> {

                        val data =
                            JSON_INSTANCE.decodeFromJsonElement<MsgServerToClient.Data.BotReady>(msg.data)

                        this@RTVIClient.transport.setState(TransportState.Ready)

                        connection?.ready?.resolveOk(Unit)

                        callbacks.onBotReady(
                            version = data.version,
                            config = data.config
                        )
                    }

                    MsgServerToClient.Type.Error -> {
                        val data =
                            JSON_INSTANCE.decodeFromJsonElement<MsgServerToClient.Data.Error>(msg.data)
                        callbacks.onBackendError(data.error)
                    }

                    MsgServerToClient.Type.ErrorResponse -> {

                        val data =
                            JSON_INSTANCE.decodeFromJsonElement<MsgServerToClient.Data.Error>(
                                msg.data
                            )

                        try {
                            handleResponse(msg) { respondTo ->
                                respondTo(Result.Err(RTVIError.ErrorResponse(data.error)))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Got exception handling error response", e)
                            callbacks.onBackendError(data.error)
                        }
                    }

                    MsgServerToClient.Type.ActionResponse,
                    MsgServerToClient.Type.DescribeActionsResponse,
                    MsgServerToClient.Type.DescribeConfigResponse,
                    MsgServerToClient.Type.GetOrUpdateConfigResponse -> {
                        handleResponse(msg) { respondTo ->
                            respondTo(Result.Ok(msg.data))
                        }
                    }

                    MsgServerToClient.Type.UserTranscription -> {
                        val data = JSON_INSTANCE.decodeFromJsonElement<Transcript>(msg.data)
                        callbacks.onUserTranscript(data)
                    }

                    MsgServerToClient.Type.BotTranscription,
                    MsgServerToClient.Type.BotTranscriptionLegacy -> {
                        val text = (msg.data.jsonObject.get("text") as JsonPrimitive).content
                        callbacks.onBotTranscript(text)
                    }

                    MsgServerToClient.Type.UserStartedSpeaking -> {
                        callbacks.onUserStartedSpeaking()
                    }

                    MsgServerToClient.Type.UserStoppedSpeaking -> {
                        callbacks.onUserStoppedSpeaking()
                    }

                    MsgServerToClient.Type.BotStartedSpeaking -> {
                        callbacks.onBotStartedSpeaking()
                    }

                    MsgServerToClient.Type.BotStoppedSpeaking -> {
                        callbacks.onBotStoppedSpeaking()
                    }

                    MsgServerToClient.Type.BotLlmText -> {
                        val data: MsgServerToClient.Data.BotLLMTextData =
                            JSON_INSTANCE.decodeFromJsonElement(msg.data)

                        callbacks.onBotLLMText(data)
                    }

                    MsgServerToClient.Type.BotTtsText -> {
                        val data: MsgServerToClient.Data.BotTTSTextData =
                            JSON_INSTANCE.decodeFromJsonElement(msg.data)

                        callbacks.onBotTTSText(data)
                    }

                    MsgServerToClient.Type.StorageItemStored -> {
                        val data: MsgServerToClient.Data.StorageItemStoredData =
                            JSON_INSTANCE.decodeFromJsonElement(msg.data)

                        callbacks.onStorageItemStored(data)
                    }

                    else -> {

                        var match = false

                        helpers.values
                            .filter { it.supportedMessages.contains(msg.type) }
                            .forEach { entry ->
                                match = true
                                entry.helper.handleMessage(msg)
                            }

                        if (!match) {
                            Log.w(TAG, "Unexpected message type '${msg.type}'")

                            callbacks.onGenericMessage(msg)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while handling VoiceMessage", e)
            }
        }
    }

    private val transport: Transport = transport.createTransport(transportCtx)

    private inner class Connection {
        val ready = Promise<Unit, RTVIError>(thread)
    }

    private var connection: Connection? = null

    /**
     * Initialize local media devices such as camera and microphone.
     *
     * @return A Future, representing the asynchronous result of this operation.
     */
    fun initDevices(): Future<Unit, RTVIError> = transport.initDevices()

    @Deprecated("start() renamed to connect()")
    fun start() = connect()

    /**
     * Initiate an RTVI session, connecting to the backend.
     */
    fun connect(): Future<Unit, RTVIError> = thread.runOnThreadReturningFuture {

        if (connection != null) {
            return@runOnThreadReturningFuture resolvedPromiseErr(
                thread,
                RTVIError.PreviousConnectionStillActive
            )
        }

        transport.setState(TransportState.Authorizing)

        // Send POST request to the provided base_url to connect and start the bot

        val connectionData = ConnectionData.from(options)

        val body = ConnectionBundle(
            services = options.services?.associate { it.service to it.value },
            config = connectionData.config
        )
            .serializeWithCustomParams(connectionData.requestData)
            .toRequestBody("application/json".toMediaType())

        val currentConnection = Connection().apply { connection = this }

        return@runOnThreadReturningFuture post(
            thread = thread,
            url = options.params.baseUrl + options.params.endpoints.connect,
            body = body,
            customHeaders = connectionData.headers
        )
            .mapError<RTVIError> {
                RTVIError.HttpError(it)
            }
            .chain { authBundle ->
                if (currentConnection == connection) {
                    transport.connect(AuthBundle(authBundle))
                } else {
                    resolvedPromiseErr(thread, RTVIError.OperationCancelled)
                }
            }
            .chain { currentConnection.ready }
            .withTimeout(30000)
            .withErrorCallback {
                disconnect()
            }
    }

    /**
     * Disconnect an active RTVI session.
     *
     * @return A Future, representing the asynchronous result of this operation.
     */
    fun disconnect(): Future<Unit, RTVIError> {
        return transport.disconnect()
    }

    /**
     * Directly send a message to the bot via the transport.
     */
    fun sendMessage(msg: MsgClientToServer) = transport.sendMessage(msg)

    private fun discardWaitingResponses() {

        thread.assertCurrent()

        awaitingServerResponse.values.forEach {
            it(Result.Err(RTVIError.OperationCancelled))
        }

        awaitingServerResponse.clear()
    }

    /**
     * Registers a new helper with the client.
     *
     * @param service Target service for this helper
     * @param helper Helper instance
     */
    @Throws(RTVIException::class)
    fun <E : RTVIClientHelper> registerHelper(service: String, helper: E): E {

        thread.assertCurrent()

        if (helpers.containsKey(service)) {
            throw RTVIException(RTVIError.OtherError("Helper targeting service '$service' already registered"))
        }

        helper.registerVoiceClient(RegisteredRTVIClient(this, service))

        val entry = RegisteredHelper(
            helper = helper,
            supportedMessages = HashSet(helper.getMessageTypes())
        )

        helpers[service] = entry

        return helper
    }

    /**
     * Unregisters a helper from the client.
     */
    @Throws(RTVIException::class)
    fun unregisterHelper(service: String) {

        thread.assertCurrent()

        val entry = helpers.remove(service)
            ?: throw RTVIException(RTVIError.OtherError("Helper targeting service '$service' not found"))

        entry.helper.unregisterVoiceClient()
    }

    private inline fun <reified M, R> sendWithResponse(
        msg: MsgClientToServer,
        allowSingleTurn: Boolean = false,
        crossinline filter: (M) -> R
    ) = withPromise(thread) { promise ->
        thread.runOnThread {

            awaitingServerResponse[msg.id] = { result ->
                when (result) {
                    is Result.Err -> promise.resolveErr(result.error)
                    is Result.Ok -> {
                        val data = JSON_INSTANCE.decodeFromJsonElement<M>(result.value)
                        promise.resolveOk(filter(data))
                    }
                }
            }

            when (transport.state()) {
                TransportState.Connected, TransportState.Ready -> {
                    transport.sendMessage(msg)
                        .withTimeout(10000)
                        .withErrorCallback {
                            awaitingServerResponse.remove(msg.id)
                            promise.resolveErr(it)
                        }
                }

                else -> if (allowSingleTurn) {

                    val connectionData = ConnectionData.from(options)

                    post(
                        thread = thread,
                        url = options.params.baseUrl + options.params.endpoints.action,
                        body = JSON_INSTANCE.encodeToString(
                            Value.serializer(), Value.Object(
                                (connectionData.requestData + listOf(
                                    "actions" to Value.Array(
                                        valueFrom(MsgClientToServer.serializer(), msg)
                                    )
                                )).toMap()
                            )
                        ).toRequestBody("application/json".toMediaType()),
                        customHeaders = connectionData.headers,
                        responseHandler = { inputStream ->
                            inputStream.parseServerSentEvents { msg ->
                                transportCtx.onMessage(JSON_INSTANCE.decodeFromString(msg))
                            }
                        }
                    ).withCallback {
                        promise.resolveErr(
                            when (it) {
                                is Result.Err -> RTVIError.HttpError(it.error)
                                is Result.Ok -> RTVIError.OtherError("Connection ended before result received")
                            }
                        )
                    }
                }
            }
        }
    }

    /**
     * Instruct a backend service to perform an action.
     */
    fun action(
        service: String,
        action: String,
        arguments: List<Option> = emptyList()
    ): Future<Value, RTVIError> = sendWithResponse<MsgServerToClient.Data.ActionResponse, Value>(
        msg = MsgClientToServer.Action(
            service = service,
            action = action,
            arguments = arguments
        ),
        allowSingleTurn = true,
    ) { it.result }

    /**
     * Gets the current config from the server.
     */
    fun getConfig(): Future<Config, RTVIError> =
        sendWithResponse<MsgServerToClient.Data.GetOrUpdateConfigResponse, Config>(
            MsgClientToServer.GetConfig()
        ) { Config(it.config) }

    /**
     * Updates the config on the server.
     */
    fun updateConfig(update: List<ServiceConfig>): Future<Config, RTVIError> =
        sendWithResponse<MsgServerToClient.Data.GetOrUpdateConfigResponse, Config>(
            MsgClientToServer.UpdateConfig(update)
        ) { Config(it.config) }

    /**
     * Returns the expected structure of the server config.
     */
    fun describeConfig(): Future<List<ServiceConfigDescription>, RTVIError> =
        sendWithResponse<MsgServerToClient.Data.DescribeConfigResponse, List<ServiceConfigDescription>>(
            MsgClientToServer.DescribeConfig()
        ) { it.config }

    /**
     * Returns a list of supported actions.
     */
    fun describeActions(): Future<List<ActionDescription>, RTVIError> =
        sendWithResponse<MsgServerToClient.Data.DescribeActionsResponse, List<ActionDescription>>(
            MsgClientToServer.DescribeActions()
        ) { it.actions }

    /**
     * The current state of the session.
     */
    val state
        get() = transport.state()

    /**
     * Returns a list of available audio input devices.
     */
    fun getAllMics() = transport.getAllMics()

    /**
     * Returns a list of available video input devices.
     */
    fun getAllCams() = transport.getAllCams()

    /**
     * Returns the selected audio input device.
     */
    val selectedMic
        get() = transport.selectedMic()

    /**
     * Returns the selected video input device.
     */
    val selectedCam
        get() = transport.selectedCam()

    /**
     * Use the specified audio input device.
     *
     * @return A Future, representing the asynchronous result of this operation.
     */
    fun updateMic(micId: MediaDeviceId) = transport.updateMic(micId)

    /**
     * Use the specified video input device.
     *
     * @return A Future, representing the asynchronous result of this operation.
     */
    fun updateCam(camId: MediaDeviceId) = transport.updateCam(camId)

    /**
     * Enables or disables the audio input device.
     *
     * @return A Future, representing the asynchronous result of this operation.
     */
    fun enableMic(enable: Boolean) = transport.enableMic(enable)

    /**
     * Enables or disables the video input device.
     *
     * @return A Future, representing the asynchronous result of this operation.
     */
    fun enableCam(enable: Boolean) = transport.enableCam(enable)

    /**
     * Returns true if the microphone is enabled, false otherwise.
     */
    val isMicEnabled
        get() = transport.isMicEnabled()

    /**
     * Returns true if the camera is enabled, false otherwise.
     */
    val isCamEnabled
        get() = transport.isCamEnabled()

    /**
     * The expiry time for the transport session, if applicable. Measured in seconds
     * since the UNIX epoch (UTC).
     */
    val expiry
        get() = transport.expiry()

    /**
     * Returns a list of participant media tracks.
     */
    val tracks
        get() = transport.tracks()

    /**
     * Destroys this VoiceClient and cleans up any allocated resources.
     */
    fun release() {
        thread.assertCurrent()
        discardWaitingResponses()
        transport.release()
    }

    private inline fun assertReadyOrReturn(
        returnAction: (Future<Unit, RTVIError>) -> Nothing
    ) {
        thread.assertCurrent()

        if (state != TransportState.Ready) {
            returnAction(
                resolvedPromiseErr(
                    thread,
                    RTVIError.InvalidState(
                        expected = TransportState.Ready,
                        actual = state
                    )
                )
            )
        }
    }
}

private class ConnectionData(
    val headers: List<Pair<String, String>>,
    val requestData: List<Pair<String, Value>>,
    val config: List<ServiceConfig>,
) {
    companion object {
        fun from(value: RTVIClientOptions) = ConnectionData(
            headers = value.customHeaders + value.params.headers,
            requestData = listOf("rtvi_client_version" to Value.Str(RTVI_PROTOCOL_VERSION))
                    + value.customBodyParams
                    + value.params.requestData,
            config = value.config + value.params.config
        )
    }
}