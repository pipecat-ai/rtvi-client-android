package ai.rtvi.client

import ai.rtvi.client.helper.RegisteredVoiceClient
import ai.rtvi.client.helper.VoiceClientHelper
import ai.rtvi.client.result.Future
import ai.rtvi.client.result.Promise
import ai.rtvi.client.result.Result
import ai.rtvi.client.result.VoiceError
import ai.rtvi.client.result.VoiceException
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
import ai.rtvi.client.utils.post
import android.util.Log
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * An RTVI client. Connects to an RTVI backend and handles bidirectional audio and video
 * streaming.
 *
 * The client must be cleaned up using the [release] method when it is no longer required.
 *
 * @param baseUrl URL of the RTVI backend.
 * @param transport Transport for media streaming.
 * @param callbacks Callbacks invoked when changes occur in the voice session.
 * @param options Additional options for configuring the client and backend.
 */
@Suppress("unused")
open class VoiceClient(
    private val baseUrl: String,
    transport: TransportFactory,
    callbacks: VoiceEventCallbacks,
    private var options: VoiceClientOptions = VoiceClientOptions()
) {
    companion object {
        private const val TAG = "VoiceClient"
    }

    /**
     * The thread used by the VoiceClient for callbacks and other operations.
     */
    val thread = ThreadRef.forCurrent()

    private val callbacks = CallbackInterceptor(object : VoiceEventCallbacks() {
        override fun onBackendError(message: String) {}

        override fun onDisconnected() {
            discardWaitingResponses()
            connection?.ready?.resolveErr(VoiceError.OperationCancelled)
            connection = null
        }
    }, callbacks)

    private val helpers = mutableMapOf<String, RegisteredHelper>()

    private val awaitingServerResponse =
        mutableMapOf<String, (Result<JsonElement, VoiceError>) -> Unit>()

    private inline fun handleResponse(
        msg: MsgServerToClient,
        action: ((Result<JsonElement, VoiceError>) -> Unit) -> Unit
    ) {
        val id = msg.id ?: throw Exception("${msg.type} missing ID")

        val respondTo = awaitingServerResponse.remove(id)
            ?: throw Exception("${msg.type}: no responder for $id")

        action(respondTo)
    }

    private val transportCtx = object : TransportContext {

        override val options
            get() = this@VoiceClient.options

        override val callbacks
            get() = this@VoiceClient.callbacks

        override val thread = this@VoiceClient.thread

        override fun onMessage(msg: MsgServerToClient) = thread.runOnThread {

            Log.i(TAG, "onMessage($msg)")

            try {
                when (msg.type) {
                    MsgServerToClient.Type.BotReady -> {

                        val data =
                            JSON_INSTANCE.decodeFromJsonElement<MsgServerToClient.Data.BotReady>(msg.data)

                        this@VoiceClient.transport.setState(TransportState.Ready)

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
                        handleResponse(msg) { respondTo ->
                            val data =
                                JSON_INSTANCE.decodeFromJsonElement<MsgServerToClient.Data.Error>(
                                    msg.data
                                )
                            respondTo(Result.Err(VoiceError.ErrorResponse(data.error)))
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

                    MsgServerToClient.Type.BotTranscription -> {
                        val text = (msg.data.jsonObject.get("text") as JsonPrimitive).content
                        callbacks.onBotTranscript(text)
                    }

                    MsgServerToClient.Type.UserStartedSpeaking -> {
                        callbacks.onUserStartedSpeaking()
                    }

                    MsgServerToClient.Type.UserStoppedSpeaking -> {
                        callbacks.onUserStoppedSpeaking()
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
        val ready = Promise<Unit, VoiceError>(thread)
    }

    private var connection: Connection? = null

    /**
     * Initialize local media devices such as camera and microphone.
     *
     * @return A Future, representing the asynchronous result of this operation.
     */
    fun initDevices(): Future<Unit, VoiceError> = transport.initDevices()

    /**
     * Initiate an RTVI session, connecting to the backend.
     */
    fun start(): Future<Unit, VoiceError> = thread.runOnThreadReturningFuture {

        if (connection != null) {
            return@runOnThreadReturningFuture resolvedPromiseErr(
                thread,
                VoiceError.PreviousConnectionStillActive
            )
        }

        transport.setState(TransportState.Authorizing)

        // Send POST request to the provided base_url to connect and start the bot

        val body = JSON_INSTANCE.encodeToString(
            ConnectionBundle.serializer(),
            ConnectionBundle(
                services = options.services.associate { it.service to it.value },
                config = options.config
            )
        ).toRequestBody("application/json".toMediaType())

        val currentConnection = Connection().apply { connection = this }

        return@runOnThreadReturningFuture post(
            thread = thread,
            url = baseUrl,
            body = body,
            customHeaders = options.customHeaders
        )
            .mapError<VoiceError> {
                VoiceError.FailedToFetchAuthBundle(it)
            }
            .chain { authBundle ->
                if (currentConnection == connection) {
                    transport.connect(AuthBundle(authBundle))
                } else {
                    resolvedPromiseErr(thread, VoiceError.OperationCancelled)
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
    fun disconnect(): Future<Unit, VoiceError> {
        return transport.disconnect()
    }

    /**
     * Directly send a message to the bot via the transport.
     */
    fun sendMessage(msg: MsgClientToServer) = transport.sendMessage(msg)

    private fun discardWaitingResponses() {

        thread.assertCurrent()

        awaitingServerResponse.values.forEach {
            it(Result.Err(VoiceError.OperationCancelled))
        }

        awaitingServerResponse.clear()
    }

    /**
     * Registers a new helper with the client.
     *
     * @param service Target service for this helper
     * @param helper Helper instance
     */
    @Throws(VoiceException::class)
    fun <E : VoiceClientHelper> registerHelper(service: String, helper: E): E {

        thread.assertCurrent()

        if (helpers.containsKey(service)) {
            throw VoiceException(VoiceError.OtherError("Helper targeting service '$service' already registered"))
        }

        helper.registerVoiceClient(RegisteredVoiceClient(this, service))

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
    @Throws(VoiceException::class)
    fun unregisterHelper(service: String) {

        thread.assertCurrent()

        val entry = helpers.remove(service)
            ?: throw VoiceException(VoiceError.OtherError("Helper targeting service '$service' not found"))

        entry.helper.unregisterVoiceClient()
    }

    private inline fun <reified M, R> sendWithResponse(
        msg: MsgClientToServer,
        crossinline filter: (M) -> R
    ): Future<R, VoiceError> = withPromise(thread) { promise ->
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

            transport.sendMessage(msg).withErrorCallback {
                awaitingServerResponse.remove(msg.id)
                promise.resolveErr(it)
            }
        }
    }.withTimeout(10000)

    /**
     * Instruct a backend service to perform an action.
     */
    fun action(
        service: String,
        action: String,
        arguments: List<Option> = emptyList()
    ): Future<Value, VoiceError> = sendWithResponse<MsgServerToClient.Data.ActionResponse, Value>(
        MsgClientToServer.Action(
            service = service,
            action = action,
            arguments = arguments
        ),
    ) { it.result }

    /**
     * Gets the current config from the server.
     */
    fun getConfig(): Future<Config, VoiceError> =
        sendWithResponse<MsgServerToClient.Data.GetOrUpdateConfigResponse, Config>(
            MsgClientToServer.GetConfig()
        ) { Config(it.config) }

    /**
     * Updates the config on the server.
     */
    fun updateConfig(update: List<ServiceConfig>): Future<Config, VoiceError> =
        sendWithResponse<MsgServerToClient.Data.GetOrUpdateConfigResponse, Config>(
            MsgClientToServer.UpdateConfig(update)
        ) { Config(it.config) }

    /**
     * Returns the expected structure of the server config.
     */
    fun describeConfig(): Future<List<ServiceConfigDescription>, VoiceError> =
        sendWithResponse<MsgServerToClient.Data.DescribeConfigResponse, List<ServiceConfigDescription>>(
            MsgClientToServer.DescribeConfig()
        ) { it.config }

    /**
     * Returns a list of supported actions.
     */
    fun describeActions(): Future<List<ActionDescription>, VoiceError> =
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
        returnAction: (Future<Unit, VoiceError>) -> Nothing
    ) {
        thread.assertCurrent()

        if (state != TransportState.Ready) {
            returnAction(
                resolvedPromiseErr(
                    thread,
                    VoiceError.InvalidState(
                        expected = TransportState.Ready,
                        actual = state
                    )
                )
            )
        }
    }
}