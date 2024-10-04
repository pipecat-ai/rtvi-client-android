package ai.rtvi.client.utils

import ai.rtvi.client.types.ServiceConfig
import ai.rtvi.client.types.Value
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

@Serializable
internal data class ConnectionBundle(
    val services: Map<String, String>?,
    val config: List<ServiceConfig>
) {

    fun serializeWithCustomParams(customBodyParams: List<Pair<String, Value>>): String {

        val customMap =
            customBodyParams.associate { it.first to JSON_INSTANCE.encodeToJsonElement(it.second) }

        val result = JsonObject(JSON_INSTANCE.encodeToJsonElement(this).jsonObject + customMap)

        return JSON_INSTANCE.encodeToString(JsonObject.serializer(), result)
    }
}