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

/**
 * Stable identity and arguments for one concrete navigation-graph instance on the back stack.
 *
 * Multiple destination entries may reference the same instance while they remain in that graph.
 */
class NavGraphEntry(
    val id: NavEntryId,
    val route: NavRoute,
) {
    override fun equals(other: Any?): Boolean {
        return other is NavGraphEntry &&
            id == other.id &&
            route == other.route
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + route.hashCode()
        return result
    }

    override fun toString(): String {
        return "NavGraphEntry(id=$id, route=$route)"
    }
}

class NavEntry(
    val id: NavEntryId,
    val route: NavRoute,
    graphEntries: List<NavGraphEntry> = emptyList(),
) {
    val graphEntries: List<NavGraphEntry> = Collections.unmodifiableList(
        ArrayList(graphEntries),
    )
    val graphHierarchy: List<String> = Collections.unmodifiableList(
        this.graphEntries.map { entry -> entry.route.name },
    )

    init {
        require(this.graphHierarchy.distinct().size == this.graphHierarchy.size) {
            "Navigation graph hierarchy routes must be unique."
        }
        require(this.graphEntries.map(NavGraphEntry::id).distinct().size == this.graphEntries.size) {
            "Navigation graph entries must not contain duplicate IDs."
        }
        require(this.graphEntries.none { graphEntry -> graphEntry.id == id }) {
            "A destination entry must not reuse one of its navigation graph entry IDs."
        }
    }

    override fun equals(other: Any?): Boolean {
        return other is NavEntry &&
            id == other.id &&
            route == other.route &&
            graphEntries == other.graphEntries
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + route.hashCode()
        result = 31 * result + graphEntries.hashCode()
        return result
    }

    override fun toString(): String {
        return "NavEntry(id=$id, route=$route, graphEntries=$graphEntries)"
    }
}

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
        val destinationIds = this.entries.mapTo(hashSetOf(), NavEntry::id)
        val graphEntriesById = linkedMapOf<NavEntryId, NavGraphEntry>()
        this.entries.forEach { entry ->
            entry.graphEntries.forEach { graphEntry ->
                require(graphEntry.id !in destinationIds) {
                    "A navigation graph entry ID must not be reused by a destination entry."
                }
                val existing = graphEntriesById.putIfAbsent(graphEntry.id, graphEntry)
                require(existing == null || existing == graphEntry) {
                    "A navigation graph entry ID must identify one stable graph instance."
                }
            }
        }
    }

    fun contains(entryId: NavEntryId): Boolean = entries.any { it.id == entryId }

    override fun equals(other: Any?): Boolean {
        return other is NavBackStackSnapshot && entries == other.entries
    }

    override fun hashCode(): Int = entries.hashCode()

    override fun toString(): String = "NavBackStackSnapshot(entries=$entries)"
}
