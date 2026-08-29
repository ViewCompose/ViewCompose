package com.viewcompose.navigation

import androidx.annotation.MainThread
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryPresence
import com.viewcompose.navigation.core.NavPaneRole
import com.viewcompose.navigation.core.NavSceneEntry
import com.viewcompose.navigation.core.NavSceneInteraction
import com.viewcompose.navigation.core.NavSceneLayerRole
import com.viewcompose.navigation.core.NavSceneTransitionPhase
import com.viewcompose.navigation.core.NavSceneVisibility
import com.viewcompose.runtime.State
import com.viewcompose.runtime.mutableStateOf

/**
 * Android-facing name for the immutable Navigation Core scene projection of one destination.
 *
 * This source alias deliberately reuses [NavSceneEntry] and its Core-owned visibility,
 * interaction, transition, pane, and layer values instead of introducing an Android enum model.
 * It is not a distinct runtime type. Continuous transition progress is not part of this value;
 * Core may extend the scene model while Navigation remains Alpha.
 */
typealias NavDestinationPresentation = NavSceneEntry

/**
 * Stable destination-local identity and observable coarse presentation state.
 *
 * One holder belongs to one retained [entry]. Its identity and [presentation] state survive native
 * View and child render-session disposal or recreation. Coarse scene updates may invalidate
 * destination content that reads [State.value]; animation-frame and predictive-Back progress do
 * not update this state. AndroidX Lifecycle remains the resource-threshold contract.
 *
 * The holder is available through [LocalNavDestinationContext] only while building destination
 * content. A captured local snapshot retains this holder, not a one-time presentation value. After
 * permanent entry removal, the holder receives no further updates and its owner Lifecycle is
 * authoritative for terminal cleanup.
 *
 * Updates are serialized on the Android main thread by the owning [NavHost]. Consumers receive no
 * mutable state reference. [NavHost] creates holders; applications cannot construct or replace
 * them. Reads and structurally distinct semantic updates are constant-time and allocate no
 * frame-progress values. This API is Alpha with the owning Navigation artifact.
 *
 * @sample com.viewcompose.navigation.samples.destinationContextSample
 * @property entry stable logical destination entry owned by this context
 * @property results stable result mailbox retained with this destination owner
 * @param initialPresentation initial prepared projection published before destination commit
 */
class NavDestinationContext internal constructor(
    val entry: NavEntry,
    val results: NavResultInbox,
    initialPresentation: NavDestinationPresentation,
) {
    private val mutablePresentation = mutableStateOf(initialPresentation)

    /**
     * Returns one stable read-only state whose value is the current immutable scene projection.
     *
     * Equivalent scene updates are suppressed. The state object remains identical until [entry]
     * is permanently removed, including across native presentation disposal and recreation.
     */
    val presentation: State<NavDestinationPresentation>
        get() = mutablePresentation

    @MainThread
    internal fun updatePresentation(value: NavDestinationPresentation) {
        check(value.entryId == entry.id) {
            "Destination context ${entry.id} cannot publish presentation for ${value.entryId}."
        }
        mutablePresentation.value = value
    }
}

/** Returns the valid pre-commit presentation for a newly created destination owner. */
internal fun NavEntry.preparedDestinationPresentation(): NavDestinationPresentation {
    return NavDestinationPresentation(
        entryId = id,
        presence = NavEntryPresence.Prepared,
        visibility = NavSceneVisibility.Hidden,
        interaction = NavSceneInteraction.NonInteractive,
        transitionPhase = NavSceneTransitionPhase.Prepared,
        paneRole = null,
        layerRole = NavSceneLayerRole.Content,
    )
}
