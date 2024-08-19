package ai.rtvi.client.types

import kotlinx.serialization.Serializable

@Serializable
data class Option(
    val name: String,
    val value: Value
) {
    constructor(name: String, value: String) : this(name = name, value = Value.Str(value))

    constructor(name: String, value: Boolean) : this(name = name, value = Value.Bool(value))

    constructor(name: String, value: Double) : this(name = name, value = Value.Number(value))

    constructor(name: String, value: List<Value>) : this(name = name, value = Value.Array(value))

    constructor(name: String, value: Map<String, Value>) : this(
        name = name,
        value = Value.Object(value)
    )

}