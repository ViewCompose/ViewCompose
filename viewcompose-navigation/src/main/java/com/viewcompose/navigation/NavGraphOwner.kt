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
 *
 * @property entry platform-neutral graph identity and route arguments
 */
class NavGraphOwner internal constructor(
    val entry: NavGraphEntry,
    internal val delegate: NavEntryOwner,
) : LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner,
    HasDefaultViewModelProviderFactory {
    /** Lifecycle capped by the host and current descendant visibility. */
    override val lifecycle: Lifecycle
        get() = delegate.lifecycle

    /** Store retained until this concrete graph instance leaves every back stack. */
    override val viewModelStore: ViewModelStore
        get() = delegate.viewModelStore

    /** Registry namespace persisted under [entry]'s stable identity. */
    override val savedStateRegistry: SavedStateRegistry
        get() = delegate.savedStateRegistry

    /** Default factory backed by this graph owner's saved-state registry. */
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = delegate.defaultViewModelProviderFactory

    /** Creation extras exposing this owner and its default arguments. */
    override val defaultViewModelCreationExtras: CreationExtras
        get() = delegate.defaultViewModelCreationExtras
}

/**
 * Ordered graph-owner hierarchy for the destination currently being rendered.
 *
 * Entries are ordered from the root graph to the destination's direct parent graph.
 *
 * @param entries copied platform-neutral graph entries
 * @param owners Android owners corresponding one-to-one with [entries]
 */
class NavGraphOwnerScope internal constructor(
    entries: List<NavGraphEntry>,
    owners: List<NavGraphOwner>,
) {
    /** Immutable root-to-leaf graph entries active for the current destination. */
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

    /** Returns the graph owner for [route] in the current destination hierarchy. */
    operator fun get(route: String): NavGraphOwner? = ownersByRoute[route]

    /**
     * Returns the active owner for [route].
     *
     * @throws IllegalStateException if the graph is not in the current destination hierarchy
     */
    fun requireOwner(route: String): NavGraphOwner {
        return checkNotNull(this[route]) {
            "Navigation graph '$route' is not active in the current destination. " +
                "Active graphs: ${entries.map { entry -> entry.route.name }}"
        }
    }
}
