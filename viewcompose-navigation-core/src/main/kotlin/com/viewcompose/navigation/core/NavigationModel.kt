package com.viewcompose.navigation.core

import java.util.UUID
import java.util.Collections

@JvmInline
value class NavEntryId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "NavEntryId must not be blank." }
    }

    override fun toString(): String = value
}

fun interface NavEntryIdFactory {
    fun nextId(): NavEntryId

    companion object {
        fun random(): NavEntryIdFactory {
            return NavEntryIdFactory {
                NavEntryId(UUID.randomUUID().toString())
            }
        }
    }
}

sealed interface NavValue {
    data object Null : NavValue

    data class Text(
        val value: String,
    ) : NavValue

    data class IntValue(
        val value: Int,
    ) : NavValue

    data class LongValue(
        val value: Long,
    ) : NavValue

    data class BooleanValue(
        val value: Boolean,
    ) : NavValue

    data class FloatValue(
        val value: Float,
    ) : NavValue

    data class DoubleValue(
        val value: Double,
    ) : NavValue
}

class NavRoute(
    val name: String,
    arguments: Map<String, NavValue> = emptyMap(),
) {
    val arguments: Map<String, NavValue> = Collections.unmodifiableMap(
        LinkedHashMap(arguments),
    )

    init {
        require(name.isNotBlank()) { "Navigation route name must not be blank." }
        require(this.arguments.keys.none(String::isBlank)) {
            "Navigation argument names must not be blank."
        }
    }

    operator fun get(argumentName: String): NavValue? = arguments[argumentName]

    override fun equals(other: Any?): Boolean {
        return other is NavRoute &&
            name == other.name &&
            arguments == other.arguments
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + arguments.hashCode()
        return result
    }

    override fun toString(): String {
        return if (arguments.isEmpty()) {
            name
        } else {
            "$name$arguments"
        }
    }
}

data class NavEntry(
    val id: NavEntryId,
    val route: NavRoute,
)

class NavBackStackSnapshot(
    entries: List<NavEntry>,
) {
    val entries: List<NavEntry> = Collections.unmodifiableList(
        ArrayList(entries),
    )

    val top: NavEntry
        get() = entries.last()

    init {
        require(this.entries.isNotEmpty()) {
            "A navigation back stack must contain at least one entry."
        }
        require(this.entries.map(NavEntry::id).distinct().size == this.entries.size) {
            "A navigation back stack must not contain duplicate entry IDs."
        }
    }

    fun contains(entryId: NavEntryId): Boolean = entries.any { it.id == entryId }

    override fun equals(other: Any?): Boolean {
        return other is NavBackStackSnapshot && entries == other.entries
    }

    override fun hashCode(): Int = entries.hashCode()

    override fun toString(): String = "NavBackStackSnapshot(entries=$entries)"
}
