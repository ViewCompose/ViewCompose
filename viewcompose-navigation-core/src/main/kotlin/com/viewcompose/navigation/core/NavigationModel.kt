package com.viewcompose.navigation.core

import java.util.UUID
import java.util.Collections

/**
 * Stable identity of one destination or navigation-graph instance on a retained back stack.
 *
 * IDs are host-owned state keys rather than route names. They must remain unique for the lifetime of
 * a controller and should be persisted with the corresponding navigation snapshot.
 *
 * @property value non-blank opaque identity
 */
@JvmInline
value class NavEntryId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "NavEntryId must not be blank." }
    }

    /** Returns the opaque [value] for diagnostics and persistence adapters. */
    override fun toString(): String = value
}

/**
 * Allocates globally unique [NavEntryId] values for a controller.
 *
 * A factory is invoked for destination entries. Graph-owner identities are derived from the
 * destination identity, so a duplicate result aborts navigation before state is committed. Tests
 * may inject a deterministic factory to make snapshots reproducible.
 */
fun interface NavEntryIdFactory {
    /** Returns the next non-blank identity that has not previously been returned to this controller. */
    fun nextId(): NavEntryId

    /** Built-in identity factories. */
    companion object {
        /** Creates a factory backed by random UUID strings. */
        fun random(): NavEntryIdFactory {
            return NavEntryIdFactory {
                NavEntryId(UUID.randomUUID().toString())
            }
        }
    }
}

/** Platform-neutral value permitted in a [NavRoute] argument map. */
sealed interface NavValue {
    /** Explicit null argument. */
    data object Null : NavValue

    /** String argument. */
    data class Text(
        /** Unmodified string value. */
        val value: String,
    ) : NavValue

    /** 32-bit signed integer argument. */
    data class IntValue(
        /** Integer value. */
        val value: Int,
    ) : NavValue

    /** 64-bit signed integer argument. */
    data class LongValue(
        /** Long value. */
        val value: Long,
    ) : NavValue

    /** Boolean argument. */
    data class BooleanValue(
        /** Boolean value. */
        val value: Boolean,
    ) : NavValue

    /** Finite or non-finite 32-bit floating-point argument supplied by the application. */
    data class FloatValue(
        /** Floating-point value. Deep-link parsing accepts only finite values. */
        val value: Float,
    ) : NavValue

    /** Finite or non-finite 64-bit floating-point argument supplied by the application. */
    data class DoubleValue(
        /** Double-precision value. Deep-link parsing accepts only finite values. */
        val value: Double,
    ) : NavValue
}

/**
 * Immutable navigation request containing a registered route name and typed arguments.
 *
 * The constructor copies [arguments]. Equality includes both the route name and every argument, so
 * `SingleTop` considers argument changes to be a different destination request.
 *
 * @property name non-blank graph route name
 * @param arguments argument snapshot keyed by non-blank names
 */
class NavRoute(
    val name: String,
    arguments: Map<String, NavValue> = emptyMap(),
) {
    /** Immutable copy of the route arguments. */
    val arguments: Map<String, NavValue> = Collections.unmodifiableMap(
        LinkedHashMap(arguments),
    )

    init {
        require(name.isNotBlank()) { "Navigation route name must not be blank." }
        require(this.arguments.keys.none(String::isBlank)) {
            "Navigation argument names must not be blank."
        }
    }

    /** Returns the value of [argumentName], or `null` when the argument is absent. */
    operator fun get(argumentName: String): NavValue? = arguments[argumentName]

    /** Compares the route name and complete argument map structurally. */
    override fun equals(other: Any?): Boolean {
        return other is NavRoute &&
            name == other.name &&
            arguments == other.arguments
    }

    /** Returns the structural hash of [name] and [arguments]. */
    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + arguments.hashCode()
        return result
    }

    /** Returns the route name followed by arguments when present. */
    override fun toString(): String {
        return if (arguments.isEmpty()) {
            name
        } else {
            "$name$arguments"
        }
    }
}

/**
 * Stable owner for one concrete navigation-graph instance on a back stack.
 *
 * Consecutive destinations inside the same graph may share this value, allowing the Android host
 * to share lifecycle, saved-state, and ViewModel ownership at graph scope.
 *
 * @property id stable graph-owner identity
 * @property route graph route and inherited arguments for this instance
 */
class NavGraphEntry(
    val id: NavEntryId,
    val route: NavRoute,
) {
    /** Compares both stable identity and graph route. */
    override fun equals(other: Any?): Boolean {
        return other is NavGraphEntry &&
            id == other.id &&
            route == other.route
    }

    /** Returns the structural hash of [id] and [route]. */
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + route.hashCode()
        return result
    }

    /** Returns a diagnostic representation of the graph owner. */
    override fun toString(): String {
        return "NavGraphEntry(id=$id, route=$route)"
    }
}

/**
 * Immutable destination instance retained on a navigation back stack.
 *
 * [graphEntries] is ordered from the root graph to the immediate owning graph. A destination ID
 * cannot also identify a graph owner, and graph-owner routes and identities must be unique within
 * this hierarchy.
 *
 * @property id stable destination-owner identity
 * @property route resolved leaf route and arguments
 * @param graphEntries copied root-to-leaf graph-owner chain
 */
class NavEntry(
    val id: NavEntryId,
    val route: NavRoute,
    graphEntries: List<NavGraphEntry> = emptyList(),
) {
    /** Immutable root-to-leaf graph-owner chain. */
    val graphEntries: List<NavGraphEntry> = Collections.unmodifiableList(
        ArrayList(graphEntries),
    )
    /** Immutable route-name projection of [graphEntries]. */
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

    /** Compares destination identity, route, and owning graph instances. */
    override fun equals(other: Any?): Boolean {
        return other is NavEntry &&
            id == other.id &&
            route == other.route &&
            graphEntries == other.graphEntries
    }

    /** Returns the structural hash of all destination and owner fields. */
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + route.hashCode()
        result = 31 * result + graphEntries.hashCode()
        return result
    }

    /** Returns a diagnostic representation including the graph-owner chain. */
    override fun toString(): String {
        return "NavEntry(id=$id, route=$route, graphEntries=$graphEntries)"
    }
}

/**
 * Immutable, non-empty snapshot of one retained navigation stack.
 *
 * Entries are ordered from root to top and copied on construction. Destination identities must be
 * unique. A graph-owner identity may be shared by entries only when every occurrence describes the
 * same [NavGraphEntry].
 *
 * @param entries root-to-top destination entries
 */
class NavBackStackSnapshot(
    entries: List<NavEntry>,
) {
    /** Immutable root-to-top destination entries. */
    val entries: List<NavEntry> = Collections.unmodifiableList(
        ArrayList(entries),
    )

    /** Currently presented destination entry. */
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

    /** Returns whether this stack retains a destination identified by [entryId]. */
    fun contains(entryId: NavEntryId): Boolean = entries.any { it.id == entryId }

    /** Compares the ordered [entries] structurally. */
    override fun equals(other: Any?): Boolean {
        return other is NavBackStackSnapshot && entries == other.entries
    }

    /** Returns the ordered-entry hash. */
    override fun hashCode(): Int = entries.hashCode()

    /** Returns a diagnostic representation of all retained entries. */
    override fun toString(): String = "NavBackStackSnapshot(entries=$entries)"
}
