package ai.rtvi.client.types

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

@Serializable(with = ValueSerializer::class)
sealed interface Value {

    @Serializable(with = ValueNullSerializer::class)
    object Null : Value

    @Serializable
    @JvmInline
    value class Str(val value: String) : Value

    @Serializable
    @JvmInline
    value class Bool(val value: Boolean) : Value

    @Serializable
    @JvmInline
    value class Number(val value: Double) : Value

    @Serializable(with = ValueArraySerializer::class)
    @JvmInline
    value class Array(val value: List<Value>) : Value {
        constructor(vararg values: Value) : this(value = values.toList())
    }

    @Serializable(with = ValueObjectSerializer::class)
    @JvmInline
    value class Object(val value: Map<String, Value>) : Value {
        constructor(vararg values: Pair<String, Value>) : this(value = values.toMap())
    }
}

internal object ValueSerializer :
    JsonContentPolymorphicSerializer<Value>(
        Value::class
    ) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Value> {

        return when (element) {
            is JsonArray -> Value.Array.serializer()
            is JsonObject -> Value.Object.serializer()
            is JsonPrimitive -> {

                if (element is JsonNull) {
                    return Value.Null.serializer()
                }

                if (element.isString) {
                    return Value.Str.serializer()
                }

                element.doubleOrNull?.apply {
                    return Value.Number.serializer()
                }

                element.booleanOrNull?.apply {
                    return Value.Bool.serializer()
                }

                throw RuntimeException("Unknown type: $element")
            }
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
internal object ValueNullSerializer : KSerializer<Value.Null> {

    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("Value.Null", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Value.Null {

        if (decoder.decodeNotNullMark()) {
            throw RuntimeException("Expecting 'null'")
        }

        decoder.decodeNull()

        return Value.Null
    }

    override fun serialize(encoder: Encoder, value: Value.Null) {
        encoder.encodeNull()
    }
}

internal object ValueArraySerializer : KSerializer<Value.Array> {

    private val innerSerializer = ListSerializer(Value.serializer())

    override val descriptor: SerialDescriptor
        get() = innerSerializer.descriptor

    override fun deserialize(decoder: Decoder): Value.Array {
        return Value.Array(innerSerializer.deserialize(decoder))
    }

    override fun serialize(encoder: Encoder, value: Value.Array) {
        innerSerializer.serialize(encoder, value.value)
    }
}

internal object ValueObjectSerializer : KSerializer<Value.Object> {

    private val innerSerializer = MapSerializer(ValueObjectKeySerializer, Value.serializer())

    override val descriptor: SerialDescriptor
        get() = innerSerializer.descriptor

    override fun deserialize(decoder: Decoder): Value.Object {
        return Value.Object(innerSerializer.deserialize(decoder))
    }

    override fun serialize(encoder: Encoder, value: Value.Object) {
        innerSerializer.serialize(encoder, value.value)
    }
}

internal object ValueObjectKeySerializer : KSerializer<String> {

    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("Value.Object.Key", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        return decoder.decodeString()
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}