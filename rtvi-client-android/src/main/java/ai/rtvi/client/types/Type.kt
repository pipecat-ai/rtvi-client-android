package ai.rtvi.client.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Type {
    @SerialName("string")
    Str,

    @SerialName("bool")
    Bool,

    @SerialName("number")
    Number,

    @SerialName("array")
    Array,

    @SerialName("object")
    Object,
}