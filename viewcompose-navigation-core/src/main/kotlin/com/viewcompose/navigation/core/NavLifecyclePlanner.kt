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
 * Computes destination and graph-owner lifecycle transitions from one semantic [NavScene].
 *
 * Every destination target is `min(host cap, scene cap, entry cap)`. Graph-owner targets are the
 * highest effective target among their descendants, so a parent is never below an active child.
 * Owners absent from `entries` target `Destroyed`, downward or terminal transitions precede upward
 * transitions, and a destroyed identity cannot be resurrected.
 *
 * Planning is side-effect free and linear in destinations, graph-path depth, and current owners.
 * The caller must not mutate `currentStates` during a call. This Alpha hard cut accepts only a
 * semantic scene; visible and interactive ID-set overloads are intentionally absent.
 *
 * @sample com.viewcompose.navigation.core.samples.lifecyclePlanningSample
 */
object NavLifecyclePlanner {
    /**
     * Plans lifecycle targets and ordered transitions for one immutable scene snapshot.
     *
     * The returned plan is an immutable snapshot. No owner is mutated by this function; a platform
     * host applies [NavLifecyclePlan.transitions] in order after its surrounding transaction commits.
     *
     * @param currentStates previous host-applied states keyed by destination or graph-owner identity
     * @param entries destination records currently owned by the candidate scene, including prepared
     * or exiting records until they reach terminal removal
     * @param scene semantic projection containing exactly one role for every destination in `entries`
     * @param hostState outer host lifecycle cap applied to every destination and graph owner
     * @return immutable final targets and downward-before-upward transition order
     * @throws IllegalArgumentException when destination or graph identities conflict, the scene and
     * entries differ, terminal entries have no current owner, or a destroyed identity would revive
     */
    fun plan(
        currentStates: Map<NavEntryId, NavEntryLifecycleState>,
        entries: List<NavEntry>,
        scene: NavScene,
        hostState: NavHostLifecycleState,
    ): NavLifecyclePlan {
        val destinationIds = entries.map(NavEntry::id)
        require(destinationIds.distinct().size == destinationIds.size) {
            "Navigation lifecycle entries must have unique destination identities."
        }
        require(scene.entryIds == destinationIds.toSet()) {
            "A navigation scene must describe every owned destination exactly once."
        }
        val terminalWithoutOwner = scene.entries.filter { sceneEntry ->
            sceneEntry.presence == NavEntryPresence.Removed &&
                sceneEntry.entryId !in currentStates
        }
        require(terminalWithoutOwner.isEmpty()) {
            "Removed navigation scene entries require a current owner: ${terminalWithoutOwner.map(NavSceneEntry::entryId)}"
        }

        val orderedOwnerIds = linkedSetOf<NavEntryId>()
        val graphEntriesById = linkedMapOf<NavEntryId, Pair<NavGraphEntry, Int>>()
        entries.forEach { entry ->
            entry.graphEntries.forEachIndexed { depth, graphEntry ->
                val existing = graphEntriesById[graphEntry.id]
                require(existing == null || existing == (graphEntry to depth)) {
                    "Navigation graph entry ${graphEntry.id} changed identity or hierarchy depth."
                }
                graphEntriesById[graphEntry.id] = graphEntry to depth
                orderedOwnerIds += graphEntry.id
            }
            require(entry.id !in graphEntriesById) {
                "Navigation destination ${entry.id} cannot reuse a graph-owner identity."
            }
            orderedOwnerIds += entry.id
        }
        val graphOwnerIds = graphEntriesById.keys
        require(destinationIds.none(graphOwnerIds::contains)) {
            "Navigation destination and graph-owner identities must be disjoint."
        }

        val hostCap = hostState.toEntryLifecycleState()
        val destinationTargets = linkedMapOf<NavEntryId, NavEntryLifecycleState>()
        entries.forEach { entry ->
            val sceneEntry = checkNotNull(scene[entry.id])
            destinationTargets[entry.id] = minimumLifecycleState(
                hostCap,
                sceneEntry.sceneLifecycleCap,
                sceneEntry.entryLifecycleCap,
            )
        }

        val ownedTargets = linkedMapOf<NavEntryId, NavEntryLifecycleState>()
        entries.forEach { entry ->
            val target = checkNotNull(destinationTargets[entry.id])
            entry.graphEntries.forEach { graphEntry ->
                val currentTarget = ownedTargets[graphEntry.id]
                ownedTargets[graphEntry.id] = if (currentTarget == null) {
                    target
                } else {
                    maximumLifecycleState(currentTarget, target)
                }
            }
            ownedTargets[entry.id] = target
        }

        currentStates.keys.forEach(orderedOwnerIds::add)
        val targetStates = linkedMapOf<NavEntryId, NavEntryLifecycleState>()
        orderedOwnerIds.forEach { ownerId ->
            targetStates[ownerId] = ownedTargets[ownerId] ?: NavEntryLifecycleState.Destroyed
        }
        val resurrected = targetStates.filter { (ownerId, target) ->
            currentStates[ownerId] == NavEntryLifecycleState.Destroyed &&
                target != NavEntryLifecycleState.Destroyed
        }.keys
        require(resurrected.isEmpty()) {
            "Destroyed navigation owners cannot be retained again: $resurrected"
        }

        val transitions = orderedOwnerIds.mapNotNull { ownerId ->
            val from = currentStates[ownerId] ?: NavEntryLifecycleState.Initialized
            val to = checkNotNull(targetStates[ownerId])
            if (from == to) {
                null
            } else {
                NavLifecycleTransition(
                    entryId = ownerId,
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

    private fun NavHostLifecycleState.toEntryLifecycleState(): NavEntryLifecycleState {
        return when (this) {
            NavHostLifecycleState.Initialized -> NavEntryLifecycleState.Initialized
            NavHostLifecycleState.Created -> NavEntryLifecycleState.Created
            NavHostLifecycleState.Started -> NavEntryLifecycleState.Started
            NavHostLifecycleState.Resumed -> NavEntryLifecycleState.Resumed
            NavHostLifecycleState.Destroyed -> NavEntryLifecycleState.Destroyed
        }
    }

    private fun minimumLifecycleState(
        first: NavEntryLifecycleState,
        second: NavEntryLifecycleState,
        third: NavEntryLifecycleState,
    ): NavEntryLifecycleState {
        val firstPairMinimum = if (first.activeRank() <= second.activeRank()) first else second
        return if (firstPairMinimum.activeRank() <= third.activeRank()) firstPairMinimum else third
    }

    private fun maximumLifecycleState(
        first: NavEntryLifecycleState,
        second: NavEntryLifecycleState,
    ): NavEntryLifecycleState {
        return if (first.activeRank() >= second.activeRank()) first else second
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
