package com.viewcompose.navigation

import android.app.Application
import android.os.Bundle
import androidx.annotation.MainThread
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavEntryLifecycleState
import com.viewcompose.navigation.core.NavGraphEntry
import com.viewcompose.navigation.core.NavHostLifecycleState
import com.viewcompose.navigation.core.NavLifecyclePlan
import com.viewcompose.navigation.core.NavLifecyclePlanner
import com.viewcompose.viewmodel.ViewModelScopeProvider

/**
 * Stores and reconciles lifecycle state for destination owners and graph owners.
 */
internal class NavEntryOwnerStore(
    private val application: Application?,
    private val viewModelScopeProvider: ViewModelScopeProvider,
    restoredState: Bundle? = null,
    private val parentViewModelProviderFactory: ViewModelProvider.Factory? = null,
    parentViewModelCreationExtras: CreationExtras = CreationExtras.Empty,
) {
    private val owners = linkedMapOf<NavEntryId, NavEntryOwner>()
    private val graphOwners = linkedMapOf<NavEntryId, NavGraphOwner>()
    private val ownerDepths = linkedMapOf<NavEntryId, Int>()
    private val restoredOwnerStates = decodeOwnerStates(restoredState)
    private val inheritedCreationExtras = MutableCreationExtras(parentViewModelCreationExtras)
    private var destroyed = false

    @MainThread
    fun ownerFor(entry: NavEntry): NavEntryOwner {
        check(!destroyed) {
            "A destroyed navigation entry owner store cannot create owners."
        }
        owners[entry.id]?.let { existing ->
            check(entry.id !in graphOwners) {
                "Navigation entry ID ${entry.id} is already owned by a navigation graph."
            }
            check(existing.entry == entry) {
                "Navigation entry ID ${entry.id} was reused for a different route."
            }
            check(ownerDepths[entry.id] == entry.graphEntries.size) {
                "Navigation entry ${entry.id} changed graph depth."
            }
            return existing
        }
        return createOwner(
            entry = entry,
            restoredState = restoredOwnerStates.remove(entry.id),
        ).also { owner ->
            owners[entry.id] = owner
            ownerDepths[entry.id] = entry.graphEntries.size
        }
    }

    @MainThread
    fun graphOwnerFor(
        entry: NavGraphEntry,
        depth: Int,
    ): NavGraphOwner {
        check(!destroyed) {
            "A destroyed navigation entry owner store cannot create graph owners."
        }
        require(depth >= 0) {
            "Navigation graph owner depth must be non-negative."
        }
        graphOwners[entry.id]?.let { existing ->
            check(existing.entry == entry) {
                "Navigation graph entry ID ${entry.id} was reused for a different graph."
            }
            check(ownerDepths[entry.id] == depth) {
                "Navigation graph entry ${entry.id} changed hierarchy depth."
            }
            return existing
        }
        check(entry.id !in owners) {
            "Navigation graph entry ID ${entry.id} is already owned by a destination."
        }
        // Graph owners reuse NavEntryOwner as their Android delegate while exposing NavGraphEntry identity.
        val delegate = createOwner(
            entry = NavEntry(
                id = entry.id,
                route = entry.route,
            ),
            restoredState = restoredOwnerStates.remove(entry.id),
        )
        return NavGraphOwner(
            entry = entry,
            delegate = delegate,
        ).also { owner ->
            owners[entry.id] = delegate
            graphOwners[entry.id] = owner
            ownerDepths[entry.id] = depth
        }
    }

    @MainThread
    fun ownerOrNull(entryId: NavEntryId): NavEntryOwner? = owners[entryId]

    @MainThread
    fun graphOwnerOrNull(entryId: NavEntryId): NavGraphOwner? = graphOwners[entryId]

    @MainThread
    fun reconcile(
        retainedEntries: List<NavEntry>,
        visibleEntryIds: Set<NavEntryId>,
        interactiveEntryIds: Set<NavEntryId>,
        hostState: NavHostLifecycleState,
    ): NavLifecyclePlan {
        check(!destroyed) {
            "A destroyed navigation entry owner store cannot be reconciled."
        }
        // Retained IDs include destinations and parent graphs so shared graph state outlives children.
        val retainedOwnerIds = linkedSetOf<NavEntryId>()
        retainedEntries.forEach { entry ->
            entry.graphEntries.forEachIndexed { depth, graphEntry ->
                graphOwnerFor(
                    entry = graphEntry,
                    depth = depth,
                )
                retainedOwnerIds += graphEntry.id
            }
            ownerFor(entry)
            retainedOwnerIds += entry.id
        }
        val entriesById = retainedEntries.associateBy(NavEntry::id)
        val visibleOwnerIds = linkedSetOf<NavEntryId>()
        visibleEntryIds.forEach { entryId ->
            val entry = checkNotNull(entriesById[entryId]) {
                "Visible navigation entry $entryId is not retained."
            }
            entry.graphEntries.forEach { graphEntry ->
                visibleOwnerIds += graphEntry.id
            }
            visibleOwnerIds += entry.id
        }
        val interactiveOwnerIds = linkedSetOf<NavEntryId>()
        interactiveEntryIds.forEach { entryId ->
            val entry = checkNotNull(entriesById[entryId]) {
                "Interactive navigation entry $entryId is not retained."
            }
            entry.graphEntries.forEach { graphEntry ->
                interactiveOwnerIds += graphEntry.id
            }
            interactiveOwnerIds += entry.id
        }
        val plan = NavLifecyclePlanner.plan(
            currentStates = owners.mapValues { (_, owner) -> owner.entryLifecycleState },
            retainedEntryIds = retainedOwnerIds.toList(),
            visibleEntryIds = visibleOwnerIds,
            interactiveEntryIds = interactiveOwnerIds,
            hostState = hostState,
        )
        val orderedTransitions = plan.transitions
            .partition { transition -> transition.isDownward() }
            .let { (downward, upward) ->
                // Move children down first and parents up first to preserve Android owner hierarchy order.
                downward.sortedByDescending { transition ->
                    checkNotNull(ownerDepths[transition.entryId])
                } + upward.sortedBy { transition ->
                    checkNotNull(ownerDepths[transition.entryId])
                }
            }
        orderedTransitions.forEach { transition ->
            if (transition.to == NavEntryLifecycleState.Destroyed) {
                viewModelScopeProvider.clear(transition.entryId)
            }
            checkNotNull(owners[transition.entryId]) {
                "Lifecycle plan referenced unknown navigation entry ${transition.entryId}."
            }.moveTo(transition.to)
        }
        val destroyedEntryIds = plan.targetStates
            .filterValues { state -> state == NavEntryLifecycleState.Destroyed }
            .keys
        destroyedEntryIds.forEach { entryId ->
            owners.remove(entryId)
            graphOwners.remove(entryId)
            ownerDepths.remove(entryId)
            restoredOwnerStates.remove(entryId)
        }
        if (hostState == NavHostLifecycleState.Destroyed) {
            viewModelScopeProvider.clearAll()
            destroyed = true
            restoredOwnerStates.clear()
        }
        return NavLifecyclePlan(
            targetStates = plan.targetStates,
            transitions = orderedTransitions,
        )
    }

    @MainThread
    fun remove(entryId: NavEntryId) {
        restoredOwnerStates.remove(entryId)
        viewModelScopeProvider.clear(entryId)
        owners.remove(entryId)?.moveTo(NavEntryLifecycleState.Destroyed)
        graphOwners.remove(entryId)
        ownerDepths.remove(entryId)
    }

    @MainThread
    fun removeGraphOwner(entryId: NavEntryId) {
        check(entryId in graphOwners || entryId !in owners) {
            "Navigation entry $entryId is not a graph owner."
        }
        remove(entryId)
    }

    @MainThread
    fun performSave(retainedEntryIds: Set<NavEntryId>): Bundle {
        check(!destroyed) {
            "A destroyed navigation entry owner store cannot be saved."
        }
        val ownerStates = linkedMapOf<NavEntryId, Bundle>()
        retainedEntryIds.forEach { entryId ->
            val state = owners[entryId]?.performSave()
                ?: restoredOwnerStates[entryId]?.let(::Bundle)
            if (state != null) {
                ownerStates[entryId] = state
            }
        }
        return encodeOwnerStates(ownerStates)
    }

    @MainThread
    fun destroy(retainViewModelScopes: Boolean = false) {
        if (destroyed) {
            return
        }
        if (!retainViewModelScopes) {
            viewModelScopeProvider.clearAll()
        }
        owners.values.toList().asReversed().forEach { owner ->
            owner.moveTo(NavEntryLifecycleState.Destroyed)
        }
        owners.clear()
        graphOwners.clear()
        ownerDepths.clear()
        restoredOwnerStates.clear()
        destroyed = true
    }

    private fun createOwner(
        entry: NavEntry,
        restoredState: Bundle?,
    ): NavEntryOwner {
        val owner = NavEntryOwner(
            entry = entry,
            application = application,
            restoredState = restoredState,
            parentViewModelProviderFactory = parentViewModelProviderFactory,
            parentViewModelCreationExtras = inheritedCreationExtras,
        )
        val lease = try {
            viewModelScopeProvider.acquireOwner(
                key = entry.id,
                savedStateRegistryOwner = owner,
            )
        } catch (failure: Throwable) {
            owner.moveTo(NavEntryLifecycleState.Destroyed)
            throw failure
        }
        owner.bindViewModelStoreOwnerLease(lease)
        return owner
    }
}

/**
 * Returns whether a lifecycle transition moves downward or destroys the owner.
 */
private fun com.viewcompose.navigation.core.NavLifecycleTransition.isDownward(): Boolean {
    return to == NavEntryLifecycleState.Destroyed ||
        to.activeRank() < from.activeRank()
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

/**
 * Encodes owner saved state using entryId values as nested Bundle keys.
 */
private fun encodeOwnerStates(
    states: Map<NavEntryId, Bundle>,
): Bundle {
    return Bundle().apply {
        putInt(KEY_FORMAT_VERSION, FORMAT_VERSION)
        putBundle(
            KEY_ENTRIES,
            Bundle().apply {
                states.forEach { (entryId, state) ->
                    putBundle(entryId.value, Bundle(state))
                }
            },
        )
    }
}

/**
 * Defensively reads saved owner state and discards it when the format version mismatches.
 */
private fun decodeOwnerStates(
    state: Bundle?,
): LinkedHashMap<NavEntryId, Bundle> {
    if (state == null || state.getInt(KEY_FORMAT_VERSION) != FORMAT_VERSION) {
        return linkedMapOf()
    }
    val entries = state.getBundle(KEY_ENTRIES) ?: return linkedMapOf()
    return entries.keySet()
        .sorted()
        .mapNotNull { entryId ->
            entries.getBundle(entryId)?.let { restored ->
                NavEntryId(entryId) to Bundle(restored)
            }
        }
        .toMap(LinkedHashMap())
}

private const val FORMAT_VERSION = 1
private const val KEY_FORMAT_VERSION = "formatVersion"
private const val KEY_ENTRIES = "entries"
