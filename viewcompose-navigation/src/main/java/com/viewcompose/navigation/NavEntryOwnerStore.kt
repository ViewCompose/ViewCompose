package com.viewcompose.navigation

import android.app.Application
import android.os.Bundle
import androidx.annotation.MainThread
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavEntryLifecycleState
import com.viewcompose.navigation.core.NavHostLifecycleState
import com.viewcompose.navigation.core.NavLifecyclePlan
import com.viewcompose.navigation.core.NavLifecyclePlanner

internal class NavEntryOwnerStore(
    private val application: Application?,
    restoredState: Bundle? = null,
) {
    private val owners = linkedMapOf<NavEntryId, NavEntryOwner>()
    private val restoredOwnerStates = decodeOwnerStates(restoredState)
    private var destroyed = false

    @MainThread
    fun ownerFor(entry: NavEntry): NavEntryOwner {
        check(!destroyed) {
            "A destroyed navigation entry owner store cannot create owners."
        }
        owners[entry.id]?.let { existing ->
            check(existing.entry == entry) {
                "Navigation entry ID ${entry.id} was reused for a different route."
            }
            return existing
        }
        return NavEntryOwner(
            entry = entry,
            application = application,
            restoredState = restoredOwnerStates.remove(entry.id),
        ).also { owner ->
            owners[entry.id] = owner
        }
    }

    @MainThread
    fun ownerOrNull(entryId: NavEntryId): NavEntryOwner? = owners[entryId]

    @MainThread
    fun reconcile(
        retainedEntries: List<NavEntry>,
        visibleEntryIds: Set<NavEntryId>,
        interactiveEntryId: NavEntryId?,
        hostState: NavHostLifecycleState,
    ): NavLifecyclePlan {
        check(!destroyed) {
            "A destroyed navigation entry owner store cannot be reconciled."
        }
        retainedEntries.forEach(::ownerFor)
        val plan = NavLifecyclePlanner.plan(
            currentStates = owners.mapValues { (_, owner) -> owner.entryLifecycleState },
            retainedEntryIds = retainedEntries.map(NavEntry::id),
            visibleEntryIds = visibleEntryIds,
            interactiveEntryId = interactiveEntryId,
            hostState = hostState,
        )
        plan.transitions.forEach { transition ->
            checkNotNull(owners[transition.entryId]) {
                "Lifecycle plan referenced unknown navigation entry ${transition.entryId}."
            }.moveTo(transition.to)
        }
        val destroyedEntryIds = plan.targetStates
            .filterValues { state -> state == NavEntryLifecycleState.Destroyed }
            .keys
        destroyedEntryIds.forEach { entryId ->
            owners.remove(entryId)
            restoredOwnerStates.remove(entryId)
        }
        if (hostState == NavHostLifecycleState.Destroyed) {
            destroyed = true
            restoredOwnerStates.clear()
        }
        return plan
    }

    @MainThread
    fun remove(entryId: NavEntryId) {
        restoredOwnerStates.remove(entryId)
        owners.remove(entryId)?.moveTo(NavEntryLifecycleState.Destroyed)
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
    fun destroy() {
        if (destroyed) {
            return
        }
        owners.values.toList().asReversed().forEach { owner ->
            owner.moveTo(NavEntryLifecycleState.Destroyed)
        }
        owners.clear()
        restoredOwnerStates.clear()
        destroyed = true
    }
}

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
