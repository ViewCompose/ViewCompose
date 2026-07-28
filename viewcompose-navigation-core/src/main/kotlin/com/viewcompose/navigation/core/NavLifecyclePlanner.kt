package com.viewcompose.navigation.core

import java.util.Collections

/**
 * Android host 生命周期的抽象状态。
 * Abstract lifecycle state of the Android host.
 */
enum class NavHostLifecycleState {
    Initialized,
    Created,
    Started,
    Resumed,
    Destroyed,
}

/**
 * 单个导航 entry 的目标生命周期状态。
 * Target lifecycle state for one navigation entry.
 */
enum class NavEntryLifecycleState {
    Initialized,
    Created,
    Started,
    Resumed,
    Destroyed,
}

/**
 * entry 生命周期状态迁移记录。
 * Lifecycle-state transition record for one entry.
 */
data class NavLifecycleTransition(
    val entryId: NavEntryId,
    val from: NavEntryLifecycleState,
    val to: NavEntryLifecycleState,
)

/**
 * 一次生命周期规划的目标状态和有序迁移。
 * Target states and ordered transitions for one lifecycle planning pass.
 */
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

/**
 * 根据 retained/visible/interactive entry 集合计算生命周期迁移。
 * Computes lifecycle transitions from retained, visible, and interactive entry sets.
 */
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
        // 先执行降级/销毁，再执行升级，避免两个 entry 同时处于 RESUMED。
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
