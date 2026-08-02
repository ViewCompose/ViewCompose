package com.viewcompose.navigation.core

import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Stable application identity for one independently retained navigation stack.
 *
 * Use a durable value such as a tab or top-level destination key. The value participates in save
 * and restore and therefore must not be localized or generated per process.
 *
 * @property value non-blank durable stack key
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

    /** Returns the durable [value]. */
    override fun toString(): String = value

    /** Internal default identity used by single-stack controllers. */
    companion object {
        internal val Default = NavStackId("default")
    }
}

/** Behavior applied when an application selects a retained stack. */
enum class NavStackSelectionMode {
    /**
     * Preserve the selected stack exactly where the user left it.
     */
    Preserve,

    /**
     * Remove every entry above the selected stack's root before presenting it.
     */
    PopToRoot,
}

/** System-Back behavior when the active stack is already at its root. */
enum class NavRootBackBehavior {
    /**
     * Do not consume Back; delegate it to the enclosing host or the Android platform.
     */
    Delegate,

    /**
     * Return to the most recently selected stack, then delegate when no history remains.
     */
    PreviousStack,
}

/**
 * Declares one independently retained stack and its initial route.
 *
 * @property id durable application identity for the stack
 * @property startDestination route used to create the stack's immutable root entry
 */
data class NavStackSpec(
    val id: NavStackId,
    val startDestination: NavRoute,
)

/**
 * Immutable configuration for one or more independently retained navigation stacks.
 *
 * Stack order is preserved, IDs must be unique, and [initialStackId] must identify one declared
 * stack. Persisted [NavStackSetSnapshot] values can be restored only against a configuration with
 * exactly the same stack IDs.
 *
 * @property initialStackId stack selected when creating fresh state
 * @param stacks non-empty stack declarations copied in application order
 * @property rootBackBehavior behavior after the active stack reaches its root
 */
class NavStackConfiguration(
    val initialStackId: NavStackId,
    stacks: List<NavStackSpec>,
    val rootBackBehavior: NavRootBackBehavior = NavRootBackBehavior.Delegate,
) {
    /** Immutable application-ordered stack declarations. */
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

    /** Returns the declaration for [stackId], or `null` when it is unknown. */
    operator fun get(stackId: NavStackId): NavStackSpec? {
        return stacks.firstOrNull { stack -> stack.id == stackId }
    }

    /** Compares the complete immutable configuration structurally. */
    override fun equals(other: Any?): Boolean {
        return other is NavStackConfiguration &&
            initialStackId == other.initialStackId &&
            stacks == other.stacks &&
            rootBackBehavior == other.rootBackBehavior
    }

    /** Returns the structural configuration hash. */
    override fun hashCode(): Int {
        var result = initialStackId.hashCode()
        result = 31 * result + stacks.hashCode()
        result = 31 * result + rootBackBehavior.hashCode()
        return result
    }

    /** Returns a diagnostic representation of the complete configuration. */
    override fun toString(): String {
        return "NavStackConfiguration(" +
            "initialStackId=$initialStackId, " +
            "stacks=$stacks, " +
            "rootBackBehavior=$rootBackBehavior" +
            ")"
    }

    /** Convenience configuration factories. */
    companion object {
        /**
         * Creates a single-stack configuration for hosts that do not need independent bottom-nav/tab stacks.
         *
         * @param startDestination root route of the sole retained stack
         */
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
 * [selectionHistory] is oldest-to-newest, contains only declared inactive stacks, and excludes
 * [activeStackId]. Destination and graph-owner identities are globally unique across stacks so
 * platform lifecycle and saved-state owners cannot leak between tabs.
 *
 * @property activeStackId currently selected stack
 * @param stacks copied map of every retained stack
 * @param selectionHistory copied least-recent-to-most-recent stack selection history
 */
class NavStackSetSnapshot(
    val activeStackId: NavStackId,
    stacks: Map<NavStackId, NavBackStackSnapshot>,
    selectionHistory: List<NavStackId> = emptyList(),
) {
    /** Immutable map of every retained stack. */
    val stacks: Map<NavStackId, NavBackStackSnapshot> = Collections.unmodifiableMap(
        LinkedHashMap(stacks),
    )
    /** Immutable oldest-to-newest inactive-stack selection history. */
    val selectionHistory: List<NavStackId> = Collections.unmodifiableList(
        ArrayList(selectionHistory),
    )

    /** Snapshot selected by [activeStackId]. */
    val activeStack: NavBackStackSnapshot
        get() = checkNotNull(stacks[activeStackId])

    /** Immutable flattened destination list in stack-map order. */
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

    /** Returns the retained snapshot for [stackId], or `null` when it is unknown. */
    operator fun get(stackId: NavStackId): NavBackStackSnapshot? = stacks[stackId]

    /** Compares active selection, stack contents, and selection history structurally. */
    override fun equals(other: Any?): Boolean {
        return other is NavStackSetSnapshot &&
            activeStackId == other.activeStackId &&
            stacks == other.stacks &&
            selectionHistory == other.selectionHistory
    }

    /** Returns the structural hash of the complete retained state. */
    override fun hashCode(): Int {
        var result = activeStackId.hashCode()
        result = 31 * result + stacks.hashCode()
        result = 31 * result + selectionHistory.hashCode()
        return result
    }

    /** Returns a diagnostic representation of the complete retained state. */
    override fun toString(): String {
        return "NavStackSetSnapshot(" +
            "activeStackId=$activeStackId, " +
            "stacks=$stacks, " +
            "selectionHistory=$selectionHistory" +
            ")"
    }

    private fun validateGlobalOwnerIdentities() {
        // Destination and graph owner IDs must be globally unique across stacks to avoid cross-stack state sharing.
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
