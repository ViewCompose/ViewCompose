package com.viewcompose.navigation.core

import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Defines whether a destination is being prepared, retained, leaving, or permanently removed.
 *
 * [Prepared] has not committed and cannot exceed `Created`; [Retained] remains part of navigation
 * state and may reach `Resumed`; [Exiting] has left retained state but keeps a presentation long
 * enough to animate and cannot exceed `Created`; [Removed] is terminal and targets `Destroyed`.
 */
enum class NavEntryPresence {
    Prepared,
    Retained,
    Exiting,
    Removed,
}

/**
 * Defines how a destination occupies the current scene.
 *
 * [Hidden] has no rendered scene slot, [Visible] is directly presented, and [Covered] remains
 * rendered behind another scene layer without owning interaction.
 */
enum class NavSceneVisibility {
    Hidden,
    Visible,
    Covered,
}

/**
 * Defines whether a destination may own scene input and focus.
 *
 * [Interactive] is valid only for a retained, visible, settled destination. [NonInteractive]
 * covers every hidden, covered, prepared, entering, exiting, and predictive-preview role.
 */
enum class NavSceneInteraction {
    Interactive,
    NonInteractive,
}

/**
 * Defines the coarse transition role of a destination without frame-rate progress.
 *
 * [Prepared] precedes commit, [Entering] and [Exiting] participate in ordinary motion, [Settled]
 * has no active transition, and [PredictivePreview] participates in an uncommitted Back preview.
 */
enum class NavSceneTransitionPhase {
    Prepared,
    Entering,
    Settled,
    Exiting,
    PredictivePreview,
}

/**
 * Defines whether a destination occupies the normal content layer or an overlay layer.
 *
 * Content destinations use [NavPaneRole] when rendered. Overlay destinations are ordered after
 * content and do not consume a content-pane role.
 */
enum class NavSceneLayerRole {
    Content,
    Overlay,
}

/**
 * Declares one destination's immutable semantic role in a [NavScene].
 *
 * [sceneLifecycleCap] is derived from visibility, interaction, and transition role. Hidden and
 * prepared entries cap at `Created`; covered or active-transition entries cap at `Started`; only a
 * visible, interactive, settled entry reaches `Resumed`. [entryLifecycleCap] is independently
 * derived from [presence]. This separation lets [NavLifecyclePlanner] apply the single
 * `min(host, scene, entry)` rule without inferring policy from unrelated ID sets.
 *
 * Construction validates contradictory roles before a host can publish effects. The value owns no
 * mutable state, Android object, transition progress, or presentation lifetime.
 *
 * @sample com.viewcompose.navigation.core.samples.lifecyclePlanningSample
 * @property entryId stable destination identity described by this projection
 * @property presence relationship between the destination and retained navigation state
 * @property visibility coarse occupancy in the current scene
 * @property interaction whether the destination owns scene input and focus
 * @property transitionPhase coarse transition role, excluding continuous progress
 * @property paneRole content-pane role, or `null` for hidden and overlay destinations
 * @property layerRole content or overlay layer occupied by the destination
 * @throws IllegalArgumentException when the supplied fields describe a contradictory role
 */
data class NavSceneEntry(
    val entryId: NavEntryId,
    val presence: NavEntryPresence,
    val visibility: NavSceneVisibility,
    val interaction: NavSceneInteraction,
    val transitionPhase: NavSceneTransitionPhase,
    val paneRole: NavPaneRole?,
    val layerRole: NavSceneLayerRole = NavSceneLayerRole.Content,
) {
    /** Lifecycle cap derived from this destination's role in the complete scene. */
    val sceneLifecycleCap: NavEntryLifecycleState
        get() = when {
            presence == NavEntryPresence.Removed -> NavEntryLifecycleState.Destroyed
            presence == NavEntryPresence.Prepared -> NavEntryLifecycleState.Created
            visibility == NavSceneVisibility.Hidden -> NavEntryLifecycleState.Created
            transitionPhase != NavSceneTransitionPhase.Settled -> NavEntryLifecycleState.Started
            visibility == NavSceneVisibility.Covered -> NavEntryLifecycleState.Started
            interaction == NavSceneInteraction.Interactive -> NavEntryLifecycleState.Resumed
            else -> NavEntryLifecycleState.Started
        }

    /** Lifecycle cap derived from retention independently of scene visibility. */
    val entryLifecycleCap: NavEntryLifecycleState
        get() = when (presence) {
            NavEntryPresence.Prepared,
            NavEntryPresence.Exiting,
            -> NavEntryLifecycleState.Created

            NavEntryPresence.Retained -> NavEntryLifecycleState.Resumed
            NavEntryPresence.Removed -> NavEntryLifecycleState.Destroyed
        }

    init {
        if (visibility == NavSceneVisibility.Hidden) {
            require(interaction == NavSceneInteraction.NonInteractive) {
                "A hidden navigation scene entry cannot be interactive."
            }
            require(paneRole == null) {
                "A hidden navigation scene entry cannot occupy a pane."
            }
        }
        if (visibility == NavSceneVisibility.Covered) {
            require(interaction == NavSceneInteraction.NonInteractive) {
                "A covered navigation scene entry cannot be interactive."
            }
        }
        if (interaction == NavSceneInteraction.Interactive) {
            require(presence == NavEntryPresence.Retained) {
                "Only a retained navigation scene entry can be interactive."
            }
            require(visibility == NavSceneVisibility.Visible) {
                "Only a visible navigation scene entry can be interactive."
            }
            require(transitionPhase == NavSceneTransitionPhase.Settled) {
                "A transitioning navigation scene entry cannot be interactive."
            }
        }
        when (presence) {
            NavEntryPresence.Prepared -> {
                require(visibility == NavSceneVisibility.Hidden) {
                    "A prepared navigation scene entry must remain hidden before commit."
                }
                require(transitionPhase == NavSceneTransitionPhase.Prepared) {
                    "A prepared navigation scene entry requires the Prepared transition phase."
                }
            }

            NavEntryPresence.Retained -> {
                require(transitionPhase != NavSceneTransitionPhase.Prepared) {
                    "A retained navigation scene entry cannot use the Prepared transition phase."
                }
            }

            NavEntryPresence.Exiting -> {
                require(visibility != NavSceneVisibility.Hidden) {
                    "An exiting navigation scene entry must remain rendered until motion ends."
                }
                require(interaction == NavSceneInteraction.NonInteractive) {
                    "An exiting navigation scene entry cannot be interactive."
                }
                require(transitionPhase == NavSceneTransitionPhase.Exiting) {
                    "An exiting navigation scene entry requires the Exiting transition phase."
                }
            }

            NavEntryPresence.Removed -> {
                require(visibility == NavSceneVisibility.Hidden) {
                    "A removed navigation scene entry must be hidden."
                }
                require(transitionPhase == NavSceneTransitionPhase.Settled) {
                    "A removed navigation scene entry cannot participate in a transition."
                }
            }
        }
        if (transitionPhase != NavSceneTransitionPhase.Settled) {
            require(interaction == NavSceneInteraction.NonInteractive) {
                "An active navigation transition cannot own interaction."
            }
        }
        when (layerRole) {
            NavSceneLayerRole.Content -> {
                require(
                    visibility == NavSceneVisibility.Hidden || paneRole != null,
                ) {
                    "A rendered content scene entry requires a pane role."
                }
            }

            NavSceneLayerRole.Overlay -> {
                require(paneRole == null) {
                    "An overlay navigation scene entry cannot consume a content-pane role."
                }
            }
        }
    }
}

/**
 * Owns one immutable bottom-to-top projection of destination presentation semantics.
 *
 * Entries must have unique identities, content entries must precede overlays, and an active
 * transition scene cannot contain an interactive destination. The constructor copies [entries];
 * queries return immutable snapshots and run in constant time after linear construction.
 *
 * This scene is platform-neutral and deliberately excludes native Views, focus operations,
 * transition progress, and retention policy. Hosts may construct it on any thread, but must not
 * mutate the source list concurrently with construction.
 *
 * @sample com.viewcompose.navigation.core.samples.lifecyclePlanningSample
 * @param entries bottom-to-top destination projections copied by the scene
 * @throws IllegalArgumentException when identities repeat, layers are misordered, or an active
 * transition scene contains an interactive destination
 */
class NavScene(
    entries: List<NavSceneEntry>,
) {
    /** Immutable bottom-to-top destination projections. */
    val entries: List<NavSceneEntry> = Collections.unmodifiableList(ArrayList(entries))

    private val entriesById: Map<NavEntryId, NavSceneEntry> = Collections.unmodifiableMap(
        LinkedHashMap<NavEntryId, NavSceneEntry>().apply {
            this@NavScene.entries.forEach { entry -> put(entry.entryId, entry) }
        },
    )

    /** Immutable destination identities in bottom-to-top iteration order. */
    val entryIds: Set<NavEntryId> = Collections.unmodifiableSet(
        LinkedHashSet(entriesById.keys),
    )

    init {
        require(entriesById.size == this.entries.size) {
            "A navigation scene cannot contain duplicate destination identities."
        }
        var sawOverlay = false
        this.entries.forEach { entry ->
            when (entry.layerRole) {
                NavSceneLayerRole.Content -> require(!sawOverlay) {
                    "Navigation content entries must precede overlay entries."
                }

                NavSceneLayerRole.Overlay -> sawOverlay = true
            }
        }
        val hasActiveTransition = this.entries.any { entry ->
            entry.transitionPhase != NavSceneTransitionPhase.Settled
        }
        require(
            !hasActiveTransition ||
                this.entries.none { entry ->
                    entry.interaction == NavSceneInteraction.Interactive
                },
        ) {
            "An active navigation transition scene cannot contain an interactive destination."
        }
    }

    /** Returns the immutable projection for [entryId], or `null` when the scene omits that identity. */
    operator fun get(entryId: NavEntryId): NavSceneEntry? = entriesById[entryId]

    /** Compares ordered destination projections structurally. */
    override fun equals(other: Any?): Boolean = other is NavScene && entries == other.entries

    /** Returns the ordered-projection hash. */
    override fun hashCode(): Int = entries.hashCode()

    /** Returns a diagnostic representation of the ordered destination projections. */
    override fun toString(): String = "NavScene(entries=$entries)"
}
