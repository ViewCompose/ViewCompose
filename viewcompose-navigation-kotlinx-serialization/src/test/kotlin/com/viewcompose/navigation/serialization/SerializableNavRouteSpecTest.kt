package com.viewcompose.navigation.serialization

import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.navigation.core.NavValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SerializableNavRouteSpecTest {
    @Test
    fun `round trip preserves declared scalar storage types and defaults`() {
        val spec = serializableNavRouteSpec<SupportedRoute>("supported")

        val encoded = spec.encode(
            SupportedRoute(
                byte = 1,
                short = 2,
                int = 3,
                long = 4L,
                float = 5.5f,
                double = 6.5,
                boolean = true,
                char = 'x',
                text = "value",
                mode = Mode.Detail,
                nullable = null,
            ),
        )

        assertEquals(NavValue.IntValue(1), encoded["byte"])
        assertEquals(NavValue.IntValue(2), encoded["short"])
        assertEquals(NavValue.IntValue(3), encoded["int"])
        assertEquals(NavValue.LongValue(4L), encoded["long"])
        assertEquals(NavValue.FloatValue(5.5f), encoded["float"])
        assertEquals(NavValue.DoubleValue(6.5), encoded["double"])
        assertEquals(NavValue.BooleanValue(true), encoded["boolean"])
        assertEquals(NavValue.Text("x"), encoded["char"])
        assertEquals(NavValue.Text("value"), encoded["text"])
        assertEquals(NavValue.Text("Detail"), encoded["mode"])
        assertEquals(NavValue.Null, encoded["nullable"])
        assertFalse("default argument should remain omitted", encoded.arguments.containsKey("page"))
        assertEquals(1, spec.decode(encoded).page)
    }

    @Test
    fun `long storage does not depend on the concrete value range`() {
        val spec = serializableNavRouteSpec<LongRoute>("long")

        assertEquals(NavValue.LongValue(1L), spec.encode(LongRoute(1L))["value"])
        assertEquals(
            NavValue.LongValue(Int.MAX_VALUE.toLong() + 1L),
            spec.encode(LongRoute(Int.MAX_VALUE.toLong() + 1L))["value"],
        )
    }

    @Test
    fun `explicit serializer overload supports object routes`() {
        val spec = serializableNavRouteSpec("home", HomeRoute.serializer())

        assertTrue(spec.encode(HomeRoute).arguments.isEmpty())
        assertEquals(HomeRoute, spec.decode(NavRoute("home")))
    }

    @Test
    fun `inline scalar fields retain their underlying declared storage`() {
        val spec = serializableNavRouteSpec<InlineRoute>("inline")

        val encoded = spec.encode(InlineRoute(UserId(7L)))

        assertEquals(NavValue.LongValue(7L), encoded["userId"])
        assertEquals(InlineRoute(UserId(7L)), spec.decode(encoded))
    }

    @Test
    fun `serialized field names are the stable route argument names`() {
        val spec = serializableNavRouteSpec<NamedRoute>("named")

        val encoded = spec.encode(NamedRoute(userId = 9L))

        assertEquals(setOf("user_id"), encoded.arguments.keys)
        assertEquals(NamedRoute(9L), spec.decode(encoded))
    }

    @Test
    fun `non-finite floating point values are rejected`() {
        val spec = serializableNavRouteSpec<FloatRoute>("float")

        assertThrows(SerializationException::class.java) {
            spec.encode(FloatRoute(Float.NaN))
        }
    }

    @Test
    fun `decode rejects unknown names and mismatched storage variants`() {
        val spec = serializableNavRouteSpec<LongRoute>("long")

        val unknown = assertThrows(IllegalArgumentException::class.java) {
            spec.decode(NavRoute("long", mapOf("other" to NavValue.LongValue(1L))))
        }
        assertTrue(unknown.message.orEmpty().contains("unknown argument 'other'"))

        val mismatch = assertThrows(IllegalArgumentException::class.java) {
            spec.decode(NavRoute("long", mapOf("value" to NavValue.IntValue(1))))
        }
        assertTrue(mismatch.message.orEmpty().contains("expected LongValue"))
    }

    @Test
    fun `decode rejects null for non-null field`() {
        val spec = serializableNavRouteSpec<LongRoute>("long")

        val failure = assertThrows(IllegalArgumentException::class.java) {
            spec.decode(NavRoute("long", mapOf("value" to NavValue.Null)))
        }

        assertTrue(failure.message.orEmpty().contains("null for a non-null descriptor"))
    }

    @Test
    fun `spec creation rejects nested collection and unsigned fields`() {
        val nested = assertThrows(IllegalArgumentException::class.java) {
            serializableNavRouteSpec<NestedRoute>("nested")
        }
        assertTrue(nested.message.orEmpty().contains("field 'nested'"))

        val collection = assertThrows(IllegalArgumentException::class.java) {
            serializableNavRouteSpec<CollectionRoute>("collection")
        }
        assertTrue(collection.message.orEmpty().contains("field 'items'"))

        val unsigned = assertThrows(IllegalArgumentException::class.java) {
            serializableNavRouteSpec<UnsignedRoute>("unsigned")
        }
        assertTrue(unsigned.message.orEmpty().contains("unsupported unsigned type"))
    }

    @Test
    fun `spec creation rejects a non-object root`() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            serializableNavRouteSpec("text", String.serializer())
        }

        assertTrue(failure.message.orEmpty().contains("class or object root"))
    }

    @Serializable
    private data class SupportedRoute(
        val byte: Byte,
        val short: Short,
        val int: Int,
        val long: Long,
        val float: Float,
        val double: Double,
        val boolean: Boolean,
        val char: Char,
        val text: String,
        val mode: Mode,
        val nullable: String?,
        val page: Int = 1,
    )

    @Serializable
    private enum class Mode { List, Detail }

    @Serializable
    private data class LongRoute(val value: Long)

    @Serializable
    private data object HomeRoute

    @JvmInline
    @Serializable
    private value class UserId(val value: Long)

    @Serializable
    private data class InlineRoute(val userId: UserId)

    @Serializable
    private data class NamedRoute(@SerialName("user_id") val userId: Long)

    @Serializable
    private data class FloatRoute(val value: Float)

    @Serializable
    private data class NestedValue(val value: String)

    @Serializable
    private data class NestedRoute(val nested: NestedValue)

    @Serializable
    private data class CollectionRoute(val items: List<String>)

    @Serializable
    private data class UnsignedRoute(val value: UInt)
}
