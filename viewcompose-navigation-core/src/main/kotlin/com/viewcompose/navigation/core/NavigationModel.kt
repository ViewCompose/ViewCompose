package com.viewcompose.navigation.core

import java.util.UUID
import java.util.Collections

/**
 * 单个导航目的地或图实例在回退栈中的稳定 ID。
 * Stable ID for one destination or graph instance on the navigation back stack.
 */
@JvmInline
value class NavEntryId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "NavEntryId must not be blank." }
    }

    override fun toString(): String = value
}

/**
 * NavEntryId 生成器，测试可注入确定性 ID。
 * NavEntryId factory; tests can inject deterministic IDs.
 */
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

/**
 * 导航参数的跨平台值模型。
 * Cross-platform value model for navigation arguments.
 */
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

/**
 * 导航 route 名称及其不可变参数集合。
 * Navigation route name plus immutable argument map.
 */
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
 * 回退栈上一个具体导航图实例的稳定身份和参数。
 * Stable identity and arguments for one concrete navigation-graph instance on the back stack.
 *
 * 多个 destination entry 在仍位于该图内时可以引用同一个 graph instance。
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

/**
 * 回退栈上的一个目的地实例。
 * One destination instance on a back stack.
 *
 * graphEntries 记录该目的地所属的图实例链，用于生命周期、ViewModelStore 和 saved-state 共享。
 * graphEntries records owning graph instances for lifecycle, ViewModelStore, and saved-state sharing.
 */
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

/**
 * 单个导航栈的不可变快照。
 * Immutable snapshot of one navigation stack.
 */
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
