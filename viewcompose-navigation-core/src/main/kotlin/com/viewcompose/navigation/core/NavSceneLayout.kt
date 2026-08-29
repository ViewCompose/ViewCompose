package com.viewcompose.navigation.core

import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashSet

/**
 * Combines one content-pane projection with zero or more modal overlay destinations.
 *
 * Overlay identities are stored bottom to top and cannot also occupy a content pane. A layout does
 * not validate stack membership by itself; use [resolveNavSceneLayout] at a host boundary or pass it
 * to [NavExecutionReducer], both of which require overlays to be the active stack's exact trailing
 * suffix.
 *
 * The value owns no platform surface, lifecycle owner, input dispatcher, or mutable collection.
 * Construction copies [overlayEntryIds], and all derived collections are immutable snapshots.
 *
 * @sample com.viewcompose.navigation.core.samples.navigationSceneStrategySample
 * @property contentPaneScene non-empty content projection rendered below every overlay
 * @param overlayEntryIds overlay destination identities ordered bottom to top
 * @throws IllegalArgumentException when overlay identities repeat or also occupy a content pane
 */
class NavSceneLayout(
    val contentPaneScene: NavPaneScene,
    overlayEntryIds: List<NavEntryId> = emptyList(),
) {
    /** Immutable overlay destination identities ordered bottom to top. */
    val overlayEntryIds: List<NavEntryId> = Collections.unmodifiableList(
        ArrayList(overlayEntryIds),
    )

    /** Immutable identities rendered by this layout in content-then-overlay order. */
    val visibleEntryIds: Set<NavEntryId> = Collections.unmodifiableSet(
        LinkedHashSet<NavEntryId>().apply {
            addAll(contentPaneScene.visibleEntryIds)
            addAll(this@NavSceneLayout.overlayEntryIds)
        },
    )

    /**
     * Immutable settled input owners: every content pane when no overlay exists, otherwise only the
     * top overlay.
     */
    val interactiveEntryIds: Set<NavEntryId> = Collections.unmodifiableSet(
        if (this.overlayEntryIds.isEmpty()) {
            LinkedHashSet(contentPaneScene.interactiveEntryIds)
        } else {
            linkedSetOf(this.overlayEntryIds.last())
        },
    )

    init {
        require(this.overlayEntryIds.distinct().size == this.overlayEntryIds.size) {
            "A navigation overlay destination cannot occupy more than one scene layer."
        }
        require(this.overlayEntryIds.none(contentPaneScene.visibleEntryIds::contains)) {
            "A navigation destination cannot occupy both content and overlay layers."
        }
    }

    /** Compares the content projection and ordered overlay identities structurally. */
    override fun equals(other: Any?): Boolean {
        return other is NavSceneLayout &&
            contentPaneScene == other.contentPaneScene &&
            overlayEntryIds == other.overlayEntryIds
    }

    /** Returns the structural content-and-overlay hash. */
    override fun hashCode(): Int = 31 * contentPaneScene.hashCode() + overlayEntryIds.hashCode()

    /** Returns a diagnostic representation of the content and overlay projections. */
    override fun toString(): String {
        return "NavSceneLayout(contentPaneScene=$contentPaneScene, " +
            "overlayEntryIds=$overlayEntryIds)"
    }
}

/**
 * Supplies immutable stack and pane-policy inputs to one [NavSceneStrategy] calculation.
 *
 * Call [projectContent] to apply the host's fallback [NavPaneStrategy] to the complete stack or a
 * non-empty prefix. The scope is valid only during the synchronous strategy call; retaining it is
 * unsupported. Implementations run on the caller's thread and must not mutate source collections.
 *
 * @property snapshot immutable active stack being projected
 * @property maxPaneCount requested content-pane limit in `1..3`
 */
class NavSceneStrategyScope internal constructor(
    val snapshot: NavBackStackSnapshot,
    val maxPaneCount: Int,
    private val paneStrategy: NavPaneStrategy,
) {
    /**
     * Projects [snapshot] through the host's validated fallback content-pane strategy.
     *
     * @param snapshot complete active stack or non-empty content prefix to project
     * @return a validated pane scene containing that snapshot's active top
     * @throws IllegalArgumentException when the pane limit or strategy result is invalid
     */
    fun projectContent(snapshot: NavBackStackSnapshot = this.snapshot): NavPaneScene {
        return paneStrategy.calculateValidated(snapshot, maxPaneCount)
    }
}

/**
 * Optionally calculates a complete content-and-overlay layout for one active stack.
 *
 * Strategies are synchronous, deterministic policy functions. Return `null` when the strategy does
 * not apply so the next ordered strategy can run. A non-null result wins and is validated by
 * [resolveNavSceneLayout]. Implementations own no navigation state and must not perform host effects
 * or re-enter the controller.
 */
fun interface NavSceneStrategy {
    /**
     * Calculates an optional scene layout from [scope].
     *
     * @param scope immutable calculation inputs and validated fallback content projection
     * @return a complete candidate layout, or `null` to continue ordered strategy resolution
     */
    fun calculate(scope: NavSceneStrategyScope): NavSceneLayout?
}

/** Built-in deterministic scene strategies. */
object NavSceneStrategies {
    /**
     * Creates a strategy that presents every consecutive matching top entry as a modal overlay.
     *
     * The [predicate] is evaluated from the top downward until the first non-matching entry. At least
     * one non-overlay content entry must remain. The matching suffix retains stack order and delegates
     * content-pane selection to [NavSceneStrategyScope.projectContent].
     *
     * @param predicate synchronous, side-effect-free classification invoked for top stack entries
     * @return a reusable strategy that returns `null` when the active top is not an overlay
     * @throws IllegalArgumentException during calculation when every stack entry matches
     */
    fun trailingOverlays(
        predicate: (NavEntry) -> Boolean,
    ): NavSceneStrategy {
        return NavSceneStrategy { scope ->
            val overlays = scope.snapshot.entries.takeLastWhile(predicate)
            if (overlays.isEmpty()) {
                null
            } else {
                require(overlays.size < scope.snapshot.entries.size) {
                    "A navigation scene must retain at least one content destination below overlays."
                }
                val contentSnapshot = NavBackStackSnapshot(
                    scope.snapshot.entries.dropLast(overlays.size),
                )
                NavSceneLayout(
                    contentPaneScene = scope.projectContent(contentSnapshot),
                    overlayEntryIds = overlays.map(NavEntry::id),
                )
            }
        }
    }
}

/**
 * Resolves the first applicable [sceneStrategies] result or a content-only fallback layout.
 *
 * Resolution is synchronous and invokes strategies in list order. The returned layout must retain
 * at least one content destination, use only active-stack identities, contain the content-prefix
 * top, and represent every overlay as the stack's exact trailing suffix. All validation completes
 * before the value is returned; this function mutates no input or controller state.
 *
 * @sample com.viewcompose.navigation.core.samples.navigationSceneStrategySample
 * @param snapshot immutable active stack to project
 * @param maxPaneCount requested content-pane limit in `1..3`
 * @param sceneStrategies ordered optional scene policies; the first non-null result wins
 * @param paneStrategy fallback content-pane policy used by [NavSceneStrategyScope.projectContent]
 * and when no scene strategy applies
 * @return one validated immutable scene layout
 * @throws IllegalArgumentException when the pane limit or selected layout violates stack ordering
 */
fun resolveNavSceneLayout(
    snapshot: NavBackStackSnapshot,
    maxPaneCount: Int,
    sceneStrategies: List<NavSceneStrategy> = emptyList(),
    paneStrategy: NavPaneStrategy = NavPaneStrategies.Single,
): NavSceneLayout {
    require(maxPaneCount in 1..NavPaneRole.entries.size) {
        "Navigation max pane count must be between 1 and ${NavPaneRole.entries.size}."
    }
    val scope = NavSceneStrategyScope(
        snapshot = snapshot,
        maxPaneCount = maxPaneCount,
        paneStrategy = paneStrategy,
    )
    val layout = sceneStrategies.firstNotNullOfOrNull { strategy ->
        strategy.calculate(scope)
    } ?: NavSceneLayout(scope.projectContent())
    validateNavSceneLayout(
        snapshot = snapshot,
        layout = layout,
        maxPaneCount = maxPaneCount,
    )
    return layout
}

internal fun validateNavSceneLayout(
    snapshot: NavBackStackSnapshot,
    layout: NavSceneLayout,
    maxPaneCount: Int = NavPaneRole.entries.size,
    label: String? = null,
) {
    require(maxPaneCount in 1..NavPaneRole.entries.size) {
        "Navigation max pane count must be between 1 and ${NavPaneRole.entries.size}."
    }
    val prefix = label?.let { "$it " }.orEmpty()
    require(layout.contentPaneScene.panes.size <= maxPaneCount) {
        "The ${prefix}navigation scene produced ${layout.contentPaneScene.panes.size} content " +
            "panes with a limit of $maxPaneCount."
    }
    val overlayCount = layout.overlayEntryIds.size
    require(overlayCount < snapshot.entries.size) {
        "The ${prefix}navigation scene must retain at least one content destination."
    }
    val contentEntries = snapshot.entries.dropLast(overlayCount)
    val contentEntryIds = contentEntries.mapTo(linkedSetOf(), NavEntry::id)
    require(layout.contentPaneScene.visibleEntryIds.all(contentEntryIds::contains)) {
        "The ${prefix}navigation content scene references a destination outside its stack prefix."
    }
    require(contentEntries.last().id in layout.contentPaneScene.visibleEntryIds) {
        "The ${prefix}navigation content scene must include its stack-prefix top."
    }
    val expectedOverlayIds = snapshot.entries.takeLast(overlayCount).map(NavEntry::id)
    require(layout.overlayEntryIds == expectedOverlayIds) {
        "The ${prefix}navigation overlays must equal the active stack's trailing suffix."
    }
}
