package com.viewcompose.navigation.serialization

import com.viewcompose.navigation.core.NavRouteSpec
import com.viewcompose.navigation.core.NavValue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.double
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.long
import kotlinx.serialization.serializer

private val routeJson = Json {
    allowSpecialFloatingPointValues = false
    allowStructuredMapKeys = false
    encodeDefaults = false
    explicitNulls = true
    ignoreUnknownKeys = false
    useAlternativeNames = false
}

/**
 * Creates a typed navigation route backed by an explicit Kotlinx [serializer].
 *
 * The serializer root must be a class or object containing only scalar, enum, nullable scalar, or
 * supported inline-scalar fields. The adapter maps those fields to the closed [NavValue] model;
 * it never persists JSON, [serializer], or decoded objects. Unsupported descriptors fail while the
 * spec is created, before the spec can enter a graph or controller command.
 *
 * Defaults omitted by the serializer remain absent from the route and are reconstructed during
 * decoding. Unknown argument names, nullability violations, and mismatched [NavValue] variants are
 * rejected rather than coerced.
 *
 * @sample com.viewcompose.navigation.serialization.samples.serializableRouteSample
 * @param T serializable application route value
 * @param name non-blank stable route identity
 * @param serializer explicit generated or custom serializer for [T]
 */
fun <T> serializableNavRouteSpec(
    name: String,
    serializer: KSerializer<T>,
): NavRouteSpec<T> {
    val codec = KotlinxNavRouteCodec(serializer)
    return NavRouteSpec(
        name = name,
        encodeArguments = codec::encode,
        decodeArguments = codec::decode,
    )
}

/**
 * Creates a typed navigation route using the generated Kotlinx serializer for [T].
 *
 * Use the overload accepting `KSerializer<T>` for Java callers and custom serializers.
 *
 * @sample com.viewcompose.navigation.serialization.samples.serializableRouteSample
 * @param T serializable application route value
 * @param name non-blank stable route identity
 */
inline fun <reified T> serializableNavRouteSpec(name: String): NavRouteSpec<T> =
    serializableNavRouteSpec(name, serializer())

@OptIn(ExperimentalSerializationApi::class)
private class KotlinxNavRouteCodec<T>(
    private val serializer: KSerializer<T>,
) {
    private val descriptor = serializer.descriptor

    init {
        require(descriptor.kind == StructureKind.CLASS || descriptor.kind == StructureKind.OBJECT) {
            "Serializable navigation route '${descriptor.serialName}' must encode a class or object root."
        }
        repeat(descriptor.elementsCount) { index ->
            val name = descriptor.getElementName(index)
            descriptor.getElementDescriptor(index).requireSupportedScalar(name)
        }
    }

    fun encode(value: T): Map<String, NavValue> {
        val encoded = routeJson.encodeToJsonElement(serializer, value)
        require(encoded is JsonObject) {
            "Serializable navigation route '${descriptor.serialName}' encoded a non-object root."
        }
        return buildMap(encoded.size) {
            encoded.forEach { (name, element) ->
                val elementDescriptor = descriptor.descriptorFor(name)
                put(name, element.toNavValue(elementDescriptor, name))
            }
        }
    }

    fun decode(arguments: Map<String, NavValue>): T {
        val jsonArguments = buildMap<String, JsonElement>(arguments.size) {
            arguments.forEach { (name, value) ->
                val elementDescriptor = descriptor.descriptorFor(name)
                put(name, value.toJsonElement(elementDescriptor, name))
            }
        }
        return routeJson.decodeFromJsonElement(serializer, JsonObject(jsonArguments))
    }
}

@OptIn(ExperimentalSerializationApi::class)
private fun SerialDescriptor.descriptorFor(name: String): SerialDescriptor {
    val index = getElementIndex(name)
    require(index != CompositeDecoder.UNKNOWN_NAME) {
        "Serializable navigation route contains unknown argument '$name' for '$serialName'."
    }
    return getElementDescriptor(index)
}

@OptIn(ExperimentalSerializationApi::class)
private fun SerialDescriptor.requireSupportedScalar(path: String): SerialDescriptor {
    require(serialName !in unsignedSerialNames) {
        "Serializable navigation route field '$path' uses unsupported unsigned type '$serialName'."
    }
    if (isInline) {
        require(elementsCount == 1) {
            "Serializable navigation route field '$path' has an invalid inline descriptor '$serialName'."
        }
        return getElementDescriptor(0).requireSupportedScalar(path)
    }
    require(kind is PrimitiveKind || kind == SerialKind.ENUM) {
        "Serializable navigation route field '$path' uses unsupported descriptor '$serialName' ($kind)."
    }
    return this
}

@OptIn(ExperimentalSerializationApi::class)
private fun JsonElement.toNavValue(
    declaredDescriptor: SerialDescriptor,
    path: String,
): NavValue {
    if (this === JsonNull) {
        require(declaredDescriptor.isNullable) {
            "Serializable navigation route field '$path' encoded null for a non-null descriptor."
        }
        return NavValue.Null
    }
    require(this is JsonPrimitive) {
        "Serializable navigation route field '$path' encoded a structured JSON value."
    }
    val descriptor = declaredDescriptor.requireSupportedScalar(path)
    return when (descriptor.kind) {
        PrimitiveKind.BOOLEAN -> NavValue.BooleanValue(boolean)
        PrimitiveKind.BYTE,
        PrimitiveKind.SHORT,
        PrimitiveKind.INT,
        -> NavValue.IntValue(int)
        PrimitiveKind.LONG -> NavValue.LongValue(long)
        PrimitiveKind.FLOAT -> NavValue.FloatValue(float)
        PrimitiveKind.DOUBLE -> NavValue.DoubleValue(double)
        PrimitiveKind.CHAR,
        PrimitiveKind.STRING,
        SerialKind.ENUM,
        -> {
            require(isString) {
                "Serializable navigation route field '$path' encoded a non-string value."
            }
            NavValue.Text(content)
        }
        else -> error("Descriptor validation accepted unsupported kind '${descriptor.kind}'.")
    }
}

@OptIn(ExperimentalSerializationApi::class)
private fun NavValue.toJsonElement(
    declaredDescriptor: SerialDescriptor,
    path: String,
): JsonElement {
    if (this === NavValue.Null) {
        require(declaredDescriptor.isNullable) {
            "Serializable navigation route field '$path' is null for a non-null descriptor."
        }
        return JsonNull
    }
    val descriptor = declaredDescriptor.requireSupportedScalar(path)
    return when (descriptor.kind) {
        PrimitiveKind.BOOLEAN -> JsonPrimitive(requireValue<NavValue.BooleanValue>(path).value)
        PrimitiveKind.BYTE,
        PrimitiveKind.SHORT,
        PrimitiveKind.INT,
        -> JsonPrimitive(requireValue<NavValue.IntValue>(path).value)
        PrimitiveKind.LONG -> JsonPrimitive(requireValue<NavValue.LongValue>(path).value)
        PrimitiveKind.FLOAT -> JsonPrimitive(requireValue<NavValue.FloatValue>(path).value)
        PrimitiveKind.DOUBLE -> JsonPrimitive(requireValue<NavValue.DoubleValue>(path).value)
        PrimitiveKind.CHAR,
        PrimitiveKind.STRING,
        SerialKind.ENUM,
        -> JsonPrimitive(requireValue<NavValue.Text>(path).value)
        else -> error("Descriptor validation accepted unsupported kind '${descriptor.kind}'.")
    }
}

private inline fun <reified T : NavValue> NavValue.requireValue(path: String): T {
    require(this is T) {
        "Serializable navigation route field '$path' expected ${T::class.simpleName}, " +
            "but found ${this::class.simpleName}."
    }
    return this
}

private val unsignedSerialNames = setOf(
    "kotlin.UByte",
    "kotlin.UShort",
    "kotlin.UInt",
    "kotlin.ULong",
)
