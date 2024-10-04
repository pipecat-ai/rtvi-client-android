package ai.rtvi.client.helper

import ai.rtvi.client.result.Future
import ai.rtvi.client.result.RTVIError
import ai.rtvi.client.result.resolvedPromiseErr
import ai.rtvi.client.transport.MsgClientToServer
import ai.rtvi.client.transport.MsgServerToClient
import ai.rtvi.client.types.Option
import ai.rtvi.client.types.TransportState
import ai.rtvi.client.types.Value
import ai.rtvi.client.utils.JSON_INSTANCE
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive

/**
 * Helper for interacting with an LLM service.
 */
class LLMHelper(private val callbacks: Callbacks) : RTVIClientHelper() {

    companion object {
        const val TAG = "LLMHelper"
    }

    abstract class Callbacks {
        open fun onLLMJsonCompletion(jsonString: String) {}

        /**
         * Invoked when the LLM attempts to invoke a function. The provided callback must be
         * provided with a return value.
         *
         * @param func Details of the function call
         * @param onResult Invoke this callback to provide a return value to the LLM
         */
        open fun onLLMFunctionCall(func: LLMFunctionCall, onResult: (Value) -> Unit) {
            onResult(Value.Object("error" to Value.Str("no handler registered")))
        }
    }

    override fun handleMessage(msg: MsgServerToClient) {
        when (msg.type) {
            LLMMessageType.Incoming.LLMJsonCompletion -> {
                callbacks.onLLMJsonCompletion(msg.data.jsonPrimitive.content)
            }

            LLMMessageType.Incoming.LLMFunctionCall -> {
                val data = JSON_INSTANCE.decodeFromJsonElement<LLMFunctionCall>(msg.data)

                callbacks.onLLMFunctionCall(data) { result ->
                    client?.client?.sendMessage(
                        MsgClientToServer(
                            type = LLMMessageType.Outgoing.LLMFunctionCallResult,
                            data = JSON_INSTANCE.encodeToJsonElement(
                                LLMFunctionCallResult(
                                    functionName = data.functionName,
                                    toolCallId = data.toolCallId,
                                    arguments = data.args,
                                    result = result
                                )
                            )
                        )
                    )?.logError(TAG, "function call response")
                }
            }
        }
    }

    override fun getMessageTypes() = setOf(
        LLMMessageType.Incoming.LLMFunctionCall,
        LLMMessageType.Incoming.LLMJsonCompletion
    )

    /**
     * Returns the bot's current LLM context. Bot must be in the ready state.
     */
    fun getContext(): Future<LLMContext, RTVIError> = withClient {
        it.ensureReady {
            it.action("get_context")
                .map { result -> JSON_INSTANCE.decodeFromJsonElement<LLMContext>(result.asJsonElement()) }
        }
    }

    /**
     * Update the bot's LLM context.
     *
     * @param context The new context
     * @param interrupt Whether to interrupt the bot, or wait until it has finished speaking
     */
    fun setContext(
        context: LLMContext,
        interrupt: Boolean = false
    ): Future<Unit, RTVIError> = withClient {
        it.ensureReady {
            it.action(
                "set_context",
                listOf(
                    "messages" setTo (context.messages ?: emptyList()).convertToRtviValue(),
                    "interrupt" setTo interrupt
                )
            ).throwAwayResult()
        }
    }

    /**
     * Append a new message to the LLM context.
     *
     * @param message The message
     * @param runImmediately If false, wait until pipeline is idle before running
     */
    fun appendToMessages(
        message: LLMContextMessage,
        runImmediately: Boolean = false
    ): Future<Unit, RTVIError> = withClient {
        it.ensureReady {
            it.action(
                "append_to_messages",
                listOf(
                    "messages" setTo listOf(message.convertToRtviValue()),
                    "run_immediately" setTo runImmediately
                )
            ).throwAwayResult()
        }
    }

    /**
     * Run the bot's current LLM context.
     *
     * Useful when appending messages to the context without runImmediately set to true.
     *
     * Will do nothing if the bot is not in the ready state.
     *
     * @param interrupt boolean - Whether to interrupt the bot, or wait until it has finished speaking
     */
    fun run(interrupt: Boolean = false): Future<Unit, RTVIError> = withClient {
        it.ensureReady {
            it.action("run", listOf("interrupt" setTo interrupt)).throwAwayResult()
        }
    }
}

@Serializable
data class LLMFunctionCall(
    @SerialName("function_name")
    val functionName: String,
    @SerialName("tool_call_id")
    val toolCallId: String,
    val args: Value,
)

@Serializable
data class LLMFunctionCallResult(
    @SerialName("function_name")
    val functionName: String,
    @SerialName("tool_call_id")
    val toolCallId: String,
    val arguments: Value,
    val result: Value,
)

@Serializable
data class LLMContextMessage(
    val role: String,
    val content: String
)

@Serializable
data class LLMContext(
    val messages: List<LLMContextMessage>? = null
)

private object LLMMessageType {
    object Incoming {
        const val LLMFunctionCall = "llm-function-call"
        const val LLMJsonCompletion = "llm-json-completion"
    }

    object Outgoing {
        const val LLMFunctionCallResult = "llm-function-call-result"
    }
}

private infix fun String.setTo(value: Value) = Option(this, value)
private infix fun String.setTo(value: Boolean) = Option(this, value)
private infix fun String.setTo(value: String) = Option(this, value)
private infix fun String.setTo(value: List<Value>) = Option(this, value)

private inline fun <reified E> E.convertToRtviValue() =
    JSON_INSTANCE.encodeToJsonElement(this).asRtviValue()

private fun JsonElement.asRtviValue(): Value = JSON_INSTANCE.decodeFromJsonElement(this)

private fun Value.asJsonElement(): JsonElement = when (this) {
    is Value.Array -> JsonArray(value.map(Value::asJsonElement))
    is Value.Object -> JsonObject(value.mapValues { it.value.asJsonElement() })
    is Value.Bool -> JsonPrimitive(value)
    is Value.Number -> JsonPrimitive(value)
    is Value.Str -> JsonPrimitive(value)
    Value.Null -> JsonNull
}

private fun <V> RegisteredRTVIClient.ensureReady(action: () -> Future<V, RTVIError>): Future<V, RTVIError> =
    if (client.state == TransportState.Ready) {
        action()
    } else {
        resolvedPromiseErr(
            client.thread,
            RTVIError.InvalidState(expected = TransportState.Ready, actual = client.state)
        )
    }

private fun Future<Value, RTVIError>.throwAwayResult() = map {}
