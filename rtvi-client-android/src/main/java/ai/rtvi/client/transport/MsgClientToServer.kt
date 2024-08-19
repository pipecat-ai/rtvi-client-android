package ai.rtvi.client.transport

import ai.rtvi.client.types.Option
import ai.rtvi.client.types.ServiceConfig
import ai.rtvi.client.utils.JSON_INSTANCE
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.util.UUID

/**
 * An RTVI control message sent to the backend.
 */
@Suppress("DataClassPrivateConstructor")
@Serializable
data class MsgClientToServer private constructor(
    val id: String,
    val label: String,
    val type: String,
    val data: JsonElement?
) {

    constructor(
        type: String,
        data: JsonElement?,
        id: String = UUID.randomUUID().toString()
    ) : this(
        id = id,
        label = "rtvi-ai",
        type = type,
        data = data
    )

    object Type {
        const val DescribeConfig = "describe-config"
        const val GetConfig = "get-config"
        const val UpdateConfig = "update-config"
        const val DescribeActions = "describe-actions"
        const val Action = "action"
    }

    object Data {

        @Serializable
        data class Action(
            val service: String,
            val action: String,
            val arguments: List<Option>
        )
    }

    @Suppress("FunctionName")
    companion object {

        // Functions not values, as these generate a new ID every time
        fun DescribeConfig() = MsgClientToServer(
            type = Type.DescribeConfig,
            data = null
        )

        fun GetConfig() = MsgClientToServer(
            type = Type.GetConfig,
            data = null
        )

        fun UpdateConfig(update: List<ServiceConfig>) = MsgClientToServer(
            type = Type.UpdateConfig,
            data = JSON_INSTANCE.encodeToJsonElement(update)
        )

        fun DescribeActions() = MsgClientToServer(
            type = Type.DescribeActions,
            data = null
        )

        fun Action(
            service: String,
            action: String,
            arguments: List<Option>
        ) = MsgClientToServer(
            type = Type.Action,
            data = JSON_INSTANCE.encodeToJsonElement(Data.Action(
                service = service,
                action = action,
                arguments = arguments
            ))
        )
    }
}