package com.viewcompose.navigation.core

import java.util.Collections

enum class NavHostLifecycleState {
    Initialized,
    Created,
    Started,
    Resumed,
    Destroyed,
}

enum class NavEntryLifecycleState {
    Initialized,
    Created,
    Started,
    Resumed,
    Destroyed,
}

data class NavLifecycleTransition(
    val entryId: NavEntryId,
    val from: NavEntryLifecycleState,
    val to: NavEntryLifecycleState,
)

class NavLifecyclePlan(
    targetStates: Map<NavEntryId, NavEntryLifecycleState>,
    transitions: List<NavLifecycleTransition>,
) {
    val targetStates: Map<NavEntryId, NavEntryLifecycleState> = Collections.unmodifiableMap(
        LinkedHashMap(targetStates),
    )
    val transitions: List<NavLifecycleTransition> = Collections.unmodifiableList(
        ArrayList(transitions),
    )
}

object NavLifecyclePlanner {
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
