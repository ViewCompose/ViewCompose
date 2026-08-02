package com.viewcompose.navigation.core

import java.util.ArrayList
import java.util.Collections

/** Logical leading-to-trailing role in a multi-pane navigation layout. */
enum class NavPaneRole {
    Primary,
    Secondary,
    Tertiary,
}

/**
 * Binding between one logical pane and the destination entry it displays.
 *
 * @property role contiguous pane role beginning with [NavPaneRole.Primary]
 * @property entryId retained destination displayed in the pane
 */
data class NavPane(
    val role: NavPaneRole,
    val entryId: NavEntryId,
)

/**
 * Immutable pane scene displayed for one active navigation stack.
 *
 * A scene contains one to three panes. Roles must be unique and contiguous beginning with
 * [NavPaneRole.Primary], and one destination cannot occupy multiple panes. All visible panes are
 * interactive by default; a host can derive a narrower interaction policy before lifecycle
 * planning when its UI requires one focused pane.
 *
 * @param panes copied leading-to-trailing pane bindings
 */
class NavPaneScene(
    panes: List<NavPane>,
) {
    /** Immutable leading-to-trailing pane bindings. */
    val panes: List<NavPane> = Collections.unmodifiableList(
        ArrayList(panes),
    )
    /** Immutable entry IDs displayed by this scene. */
    val visibleEntryIds: Set<NavEntryId> = Collections.unmodifiableSet(
        this.panes.mapTo(linkedSetOf(), NavPane::entryId),
    )
    /** Entry IDs treated as interactive by the default scene contract. */
    val interactiveEntryIds: Set<NavEntryId>
        get() = visibleEntryIds

    init {
        require(this.panes.isNotEmpty()) {
            "A navigation pane scene must contain at least one pane."
        }
        require(this.panes.size <= NavPaneRole.entries.size) {
            "A navigation pane scene cannot exceed ${NavPaneRole.entries.size} panes."
        }
        require(this.panes.map(NavPane::entryId).distinct().size == this.panes.size) {
            "A navigation entry cannot occupy more than one pane."
        }
        require(
            this.panes.map(NavPane::role) ==
                NavPaneRole.entries.take(this.panes.size),
        ) {
            "Navigation panes must use unique contiguous roles beginning with Primary."
        }
    }

    /** Returns the pane assigned to [role], or `null` when this scene has fewer panes. */
    operator fun get(role: NavPaneRole): NavPane? {
        return panes.firstOrNull { pane -> pane.role == role }
    }

    /** Compares ordered pane bindings structurally. */
    override fun equals(other: Any?): Boolean {
        return other is NavPaneScene && panes == other.panes
    }

    /** Returns the ordered-pane hash. */
    override fun hashCode(): Int = panes.hashCode()

    /** Returns a diagnostic representation of all pane bindings. */
    override fun toString(): String = "NavPaneScene(panes=$panes)"
}

/**
 * Calculates the visible pane scene from a back stack and pane-count limit.
 *
 * Implementations should be deterministic and side-effect free. Call [calculateValidated] at host
 * boundaries to enforce that the result uses only retained entries and includes the active top.
 */
fun interface NavPaneStrategy {
    /**
     * Calculates a scene for [snapshot].
     *
     * @param snapshot immutable active back stack
     * @param maxPaneCount requested limit in `1..3`
     */
    fun calculate(
        snapshot: NavBackStackSnapshot,
        maxPaneCount: Int,
    ): NavPaneScene
}

/** Built-in deterministic pane strategies. */
object NavPaneStrategies {
    /** Scene containing only the active top destination in the primary pane. */
    val Single = NavPaneStrategy { snapshot, _ ->
        NavPaneScene(
            listOf(
                NavPane(
                    role = NavPaneRole.Primary,
                    entryId = snapshot.top.id,
                ),
            ),
        )
    }

    /** Places the newest committed entries into contiguous panes while always retaining the top. */
    val BackStack = NavPaneStrategy { snapshot, maxPaneCount ->
        require(maxPaneCount in 1..NavPaneRole.entries.size) {
            "Navigation max pane count must be between 1 and ${NavPaneRole.entries.size}."
        }
        val entries = snapshot.entries.takeLast(maxPaneCount)
        NavPaneScene(
            entries.mapIndexed { index, entry ->
                NavPane(
                    role = NavPaneRole.entries[index],
                    entryId = entry.id,
                )
            },
        )
    }
}

/**
 * Runs this strategy and validates its result against [snapshot] and [maxPaneCount].
 *
 * @return a non-empty scene containing only retained entries and always including the active top
 * @throws IllegalArgumentException if the requested limit or returned scene violates the contract
 */
fun NavPaneStrategy.calculateValidated(
    snapshot: NavBackStackSnapshot,
    maxPaneCount: Int,
): NavPaneScene {
    require(maxPaneCount in 1..NavPaneRole.entries.size) {
        "Navigation max pane count must be between 1 and ${NavPaneRole.entries.size}."
    }
    val scene = calculate(
        snapshot = snapshot,
        maxPaneCount = maxPaneCount,
    )
    require(scene.panes.size <= maxPaneCount) {
        "Navigation pane strategy produced ${scene.panes.size} panes with a limit of $maxPaneCount."
    }
    val stackEntryIds = snapshot.entries.mapTo(hashSetOf(), NavEntry::id)
    require(scene.visibleEntryIds.all(stackEntryIds::contains)) {
        "Navigation pane strategy referenced an entry outside the active back stack."
    }
    require(snapshot.top.id in scene.visibleEntryIds) {
        "Navigation pane strategy must include the active back-stack top."
    }
    return scene
}
