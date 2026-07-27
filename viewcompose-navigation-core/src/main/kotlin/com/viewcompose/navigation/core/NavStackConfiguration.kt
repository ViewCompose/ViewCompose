package com.viewcompose.navigation.core

import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Stable application identity for one independently retained navigation stack.
 */
@JvmInline
value class NavStackId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) {
            "NavStackId must not be blank."
        }
    }

    override fun toString(): String = value

    companion object {
        internal val Default = NavStackId("default")
    }
}

/**
 * Behavior applied when an application selects a stack.
 */
enum class NavStackSelectionMode {
    /** Preserve the selected stack exactly where the user left it. */
    Preserve,

    /** Remove every entry above the selected stack's root before presenting it. */
    PopToRoot,
}

/**
 * System-Back behavior when the active stack is already at its root.
 */
enum class NavRootBackBehavior {
    /** Do not consume Back; delegate it to the enclosing host or the Android platform. */
    Delegate,

    /** Return to the most recently selected stack, then delegate when no history remains. */
    PreviousStack,
}

/**
 * Declares the initial route of one independently retained navigation stack.
 */
data class NavStackSpec(
    val id: NavStackId,
    val startDestination: NavRoute,
)

/**
 * Immutable configuration for a set of independently retained navigation stacks.
 */
class NavStackConfiguration(
    val initialStackId: NavStackId,
    stacks: List<NavStackSpec>,
    val rootBackBehavior: NavRootBackBehavior = NavRootBackBehavior.Delegate,
) {
    val stacks: List<NavStackSpec> = Collections.unmodifiableList(
        ArrayList(stacks),
    )

    init {
        require(this.stacks.isNotEmpty()) {
            "Navigation stack configuration must contain at least one stack."
        }
        require(this.stacks.map(NavStackSpec::id).distinct().size == this.stacks.size) {
            "Navigation stack IDs must be unique."
        }
        require(this.stacks.any { stack -> stack.id == initialStackId }) {
            "Initial navigation stack '$initialStackId' is not declared."
        }
    }

    operator fun get(stackId: NavStackId): NavStackSpec? {
        return stacks.firstOrNull { stack -> stack.id == stackId }
    }

    override fun equals(other: Any?): Boolean {
        return other is NavStackConfiguration &&
            initialStackId == other.initialStackId &&
            stacks == other.stacks &&
            rootBackBehavior == other.rootBackBehavior
    }

    override fun hashCode(): Int {
        var result = initialStackId.hashCode()
        result = 31 * result + stacks.hashCode()
        result = 31 * result + rootBackBehavior.hashCode()
        return result
    }

    override fun toString(): String {
        return "NavStackConfiguration(" +
            "initialStackId=$initialStackId, " +
            "stacks=$stacks, " +
            "rootBackBehavior=$rootBackBehavior" +
            ")"
    }

    companion object {
        fun single(startDestination: NavRoute): NavStackConfiguration {
            return NavStackConfiguration(
                initialStackId = NavStackId.Default,
                stacks = listOf(
                    NavStackSpec(
                        id = NavStackId.Default,
                        startDestination = startDestination,
                    ),
                ),
            )
        }
    }
}

/**
 * Complete immutable state of every retained navigation stack.
 *
 * [selectionHistory] is oldest-to-newest and excludes [activeStackId].
 */
class NavStackSetSnapshot(
    val activeStackId: NavStackId,
    stacks: Map<NavStackId, NavBackStackSnapshot>,
    selectionHistory: List<NavStackId> = emptyList(),
) {
    val stacks: Map<NavStackId, NavBackStackSnapshot> = Collections.unmodifiableMap(
        LinkedHashMap(stacks),
    )
    val selectionHistory: List<NavStackId> = Collections.unmodifiableList(
        ArrayList(selectionHistory),
    )

    val activeStack: NavBackStackSnapshot
        get() = checkNotNull(stacks[activeStackId])

    val allEntries: List<NavEntry> = Collections.unmodifiableList(
        this.stacks.values.flatMap(NavBackStackSnapshot::entries),
    )

    init {
        require(this.stacks.isNotEmpty()) {
            "A navigation stack set must contain at least one stack."
        }
        require(activeStackId in this.stacks) {
            "Active navigation stack '$activeStackId' is not present."
        }
        require(this.selectionHistory.distinct().size == this.selectionHistory.size) {
            "Navigation stack selection history must not contain duplicates."
        }
        require(activeStackId !in this.selectionHistory) {
            "Active navigation stack must not also appear in selection history."
        }
        require(this.selectionHistory.all(this.stacks::containsKey)) {
            "Navigation stack selection history references an unknown stack."
        }
        validateGlobalOwnerIdentities()
    }

    operator fun get(stackId: NavStackId): NavBackStackSnapshot? = stacks[stackId]

    override fun equals(other: Any?): Boolean {
        return other is NavStackSetSnapshot &&
            activeStackId == other.activeStackId &&
            stacks == other.stacks &&
            selectionHistory == other.selectionHistory
    }

    override fun hashCode(): Int {
        var result = activeStackId.hashCode()
        result = 31 * result + stacks.hashCode()
        result = 31 * result + selectionHistory.hashCode()
        return result
    }

    override fun toString(): String {
        return "NavStackSetSnapshot(" +
            "activeStackId=$activeStackId, " +
            "stacks=$stacks, " +
            "selectionHistory=$selectionHistory" +
            ")"
    }

    private fun validateGlobalOwnerIdentities() {
        val destinationStackById = linkedMapOf<NavEntryId, NavStackId>()
        val graphStackById = linkedMapOf<NavEntryId, NavStackId>()
        stacks.forEach { (stackId, snapshot) ->
            snapshot.entries.forEach { entry ->
                require(destinationStackById.putIfAbsent(entry.id, stackId) == null) {
                    "A destination entry ID must not be shared by navigation stacks."
                }
                require(entry.id !in graphStackById) {
                    "A destination entry ID must not be reused by a graph in another stack."
                }
                entry.graphEntries.forEach { graphEntry ->
                    require(graphEntry.id !in destinationStackById) {
                        "A graph entry ID must not be reused by a destination in another stack."
                    }
                    val existingStack = graphStackById.putIfAbsent(graphEntry.id, stackId)
                    require(existingStack == null || existingStack == stackId) {
                        "A graph entry ID must not be shared by navigation stacks."
                    }
                }
            }
        }
    }
}
