package com.viewcompose.navigation.core

import java.util.Collections

/** Platform-neutral lifecycle state that caps every navigation owner hosted by Android. */
enum class NavHostLifecycleState {
    Initialized,
    Created,
    Started,
    Resumed,
    Destroyed,
}

/** Lifecycle state assigned to one retained navigation entry or graph owner. */
enum class NavEntryLifecycleState {
    Initialized,
    Created,
    Started,
    Resumed,
    Destroyed,
}

/**
 * Lifecycle-state transition for one owner.
 *
 * @property entryId destination or graph-owner identity
 * @property from state before this planning pass
 * @property to target state after this planning pass
 */
data class NavLifecycleTransition(
    val entryId: NavEntryId,
    val from: NavEntryLifecycleState,
    val to: NavEntryLifecycleState,
)

/**
 * Immutable result of one lifecycle planning pass.
 *
 * [transitions] is already ordered for host application: downward and destroy transitions precede
 * upward transitions so replacing an interactive entry cannot temporarily leave two owners
 * resumed. IDs that retain their current state remain in [targetStates] but not [transitions].
 *
 * @param targetStates copied final state for every current or retained owner
 * @param transitions copied ordered state changes
 */
class NavLifecyclePlan(
    targetStates: Map<NavEntryId, NavEntryLifecycleState>,
    transitions: List<NavLifecycleTransition>,
) {
    /** Immutable final state for every current or retained owner. */
    val targetStates: Map<NavEntryId, NavEntryLifecycleState> = Collections.unmodifiableMap(
        LinkedHashMap(targetStates),
    )
    /** Immutable host-application order of actual state changes. */
    val transitions: List<NavLifecycleTransition> = Collections.unmodifiableList(
        ArrayList(transitions),
    )
}

/**
 * Computes navigation-owner lifecycle transitions from retention and pane visibility.
 *
 * Retained background owners target `Created`, visible non-interactive owners target `Started`, and
 * interactive owners target `Resumed`; [NavHostLifecycleState] caps all three. Owners absent from
 * the retained list target `Destroyed` and may not later be retained again.
 *
 * @sample com.viewcompose.navigation.core.samples.lifecyclePlanningSample
 */
object NavLifecyclePlanner {
    /**
     * Plans a scene with at most one interactive owner.
     *
     * @param currentStates previous host-applied owner states
     * @param retainedEntryIds unique stable owner order retained by navigation state
     * @param visibleEntryIds retained owners currently displayed by the pane scene
     * @param interactiveEntryId optional visible owner receiving input
     * @param hostState current host lifecycle cap
     * @throws IllegalArgumentException when set relationships are invalid or a destroyed owner is retained
     */
    fun plan(
        currentStates: Map<NavEntryId, NavEntryLifecycleState>,
        retainedEntryIds: List<NavEntryId>,
        visibleEntryIds: Set<NavEntryId>,
        interactiveEntryId: NavEntryId?,
        hostState: NavHostLifecycleState,
    ): NavLifecyclePlan {
        return plan(
            currentStates = currentStates,
            retainedEntryIds = retainedEntryIds,
            visibleEntryIds = visibleEntryIds,
            interactiveEntryIds = interactiveEntryId?.let(::setOf).orEmpty(),
            hostState = hostState,
        )
    }

    /**
     * Plans a scene that may expose multiple interactive pane owners.
     *
     * @param currentStates previous host-applied owner states
     * @param retainedEntryIds unique stable owner order retained by navigation state
     * @param visibleEntryIds retained owners currently displayed by the pane scene
     * @param interactiveEntryIds visible owners receiving input
     * @param hostState current host lifecycle cap
     * @throws IllegalArgumentException when set relationships are invalid or a destroyed owner is retained
     */
    fun plan(
        currentStates: Map<NavEntryId, NavEntryLifecycleState>,
        retainedEntryIds: List<NavEntryId>,
        visibleEntryIds: Set<NavEntryId>,
        interactiveEntryIds: Set<NavEntryId>,
        hostState: NavHostLifecycleState,
    ): NavLifecyclePlan {
        require(retainedEntryIds.distinct().size == retainedEntryIds.size) {
            "Retained navigation entry IDs must be unique."
        }
        val retainedSet = retainedEntryIds.toSet()
        require(visibleEntryIds.all(retainedSet::contains)) {
            "Visible navigation entries must also be retained."
        }
        require(interactiveEntryIds.all(visibleEntryIds::contains)) {
            "Interactive navigation entries must also be visible."
        }
        val resurrected = retainedEntryIds.filter { entryId ->
            currentStates[entryId] == NavEntryLifecycleState.Destroyed
        }
        require(resurrected.isEmpty()) {
            "Destroyed navigation entries cannot be retained again: $resurrected"
        }

        val orderedEntryIds = buildList {
            addAll(retainedEntryIds)
            currentStates.keys.forEach { entryId ->
                if (entryId !in retainedSet) {
                    add(entryId)
                }
            }
        }
        val targetStates = linkedMapOf<NavEntryId, NavEntryLifecycleState>()
        orderedEntryIds.forEach { entryId ->
            targetStates[entryId] = if (entryId !in retainedSet) {
                NavEntryLifecycleState.Destroyed
            } else {
                targetForRetainedEntry(
                    isVisible = entryId in visibleEntryIds,
                    isInteractive = entryId in interactiveEntryIds,
                    hostState = hostState,
                )
            }
        }

        val transitions = orderedEntryIds.mapNotNull { entryId ->
            val from = currentStates[entryId] ?: NavEntryLifecycleState.Initialized
            val to = checkNotNull(targetStates[entryId])
            if (from == to) {
                null
            } else {
                NavLifecycleTransition(
                    entryId = entryId,
                    from = from,
                    to = to,
                )
            }
        }
        val (downward, upward) = transitions.partition { transition ->
            transition.to == NavEntryLifecycleState.Destroyed ||
                transition.to.activeRank() < transition.from.activeRank()
        }
        // Run downward/destroy transitions before upward transitions so two entries are not RESUMED at once.
        return NavLifecyclePlan(
            targetStates = targetStates,
            transitions = downward + upward,
        )
    }

    private fun targetForRetainedEntry(
        isVisible: Boolean,
        isInteractive: Boolean,
        hostState: NavHostLifecycleState,
    ): NavEntryLifecycleState {
        if (hostState == NavHostLifecycleState.Destroyed) {
            return NavEntryLifecycleState.Destroyed
        }
        val desired = when {
            isInteractive -> NavEntryLifecycleState.Resumed
            isVisible -> NavEntryLifecycleState.Started
            else -> NavEntryLifecycleState.Created
        }
        val hostCap = when (hostState) {
            NavHostLifecycleState.Initialized -> NavEntryLifecycleState.Initialized
            NavHostLifecycleState.Created -> NavEntryLifecycleState.Created
            NavHostLifecycleState.Started -> NavEntryLifecycleState.Started
            NavHostLifecycleState.Resumed -> NavEntryLifecycleState.Resumed
            NavHostLifecycleState.Destroyed -> error("Handled above.")
        }
        return if (desired.activeRank() <= hostCap.activeRank()) {
            desired
        } else {
            hostCap
        }
    }

    private fun NavEntryLifecycleState.activeRank(): Int {
        return when (this) {
            NavEntryLifecycleState.Initialized -> 0
            NavEntryLifecycleState.Created -> 1
            NavEntryLifecycleState.Started -> 2
            NavEntryLifecycleState.Resumed -> 3
            NavEntryLifecycleState.Destroyed -> -1
        }
    }
}
