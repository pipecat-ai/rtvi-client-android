package ai.rtvi.client.utils

import ai.rtvi.client.types.Value
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

internal val JSON_INSTANCE = Json { ignoreUnknownKeys = true }

internal fun <E> valueFrom(serializer: KSerializer<E>, value: E): Value {
    return JSON_INSTANCE.decodeFromJsonElement(JSON_INSTANCE.encodeToJsonElement(serializer, value))
}