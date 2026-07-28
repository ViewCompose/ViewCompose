package com.viewcompose.navigation

import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryOwner
import com.viewcompose.navigation.core.NavGraphEntry
import java.util.ArrayList
import java.util.Collections

/**
 * Android ownership boundary for one concrete navigation-graph instance.
 *
 * Destinations in the same graph instance share this lifecycle, ViewModel store, and saved-state
 * registry. A later entry into the same graph route receives a different owner.
 */
class NavGraphOwner internal constructor(
    val entry: NavGraphEntry,
    internal val delegate: NavEntryOwner,
) : LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner,
    HasDefaultViewModelProviderFactory {
    override val lifecycle: Lifecycle
        get() = delegate.lifecycle

    override val viewModelStore: ViewModelStore
        get() = delegate.viewModelStore

    override val savedStateRegistry: SavedStateRegistry
        get() = delegate.savedStateRegistry

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = delegate.defaultViewModelProviderFactory

    override val defaultViewModelCreationExtras: CreationExtras
        get() = delegate.defaultViewModelCreationExtras
}

/**
 * Ordered graph-owner hierarchy for the destination currently being rendered.
 *
 * Entries are ordered from the root graph to the destination's direct parent graph.
 */
class NavGraphOwnerScope internal constructor(
    entries: List<NavGraphEntry>,
    owners: List<NavGraphOwner>,
) {
    val entries: List<NavGraphEntry> = Collections.unmodifiableList(
        ArrayList(entries),
    )
    private val ownersByRoute: Map<String, NavGraphOwner>

    init {
        require(entries.size == owners.size) {
            "Navigation graph entries and owners must have the same size."
        }
        entries.zip(owners).forEach { (entry, owner) ->
            require(owner.entry == entry) {
                "Navigation graph owner ${owner.entry.id} does not match entry ${entry.id}."
            }
        }
        ownersByRoute = Collections.unmodifiableMap(
            owners.associateBy { owner -> owner.entry.route.name },
        )
    }

    operator fun get(route: String): NavGraphOwner? = ownersByRoute[route]

    /** Returns the active owner for [route], or fails when that graph is not in this destination. */
    fun requireOwner(route: String): NavGraphOwner {
        return checkNotNull(this[route]) {
            "Navigation graph '$route' is not active in the current destination. " +
                "Active graphs: ${entries.map { entry -> entry.route.name }}"
        }
    }
}
