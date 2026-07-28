package com.viewcompose.navigation.core

import java.util.ArrayList
import java.util.Collections

enum class NavPaneRole {
    Primary,
    Secondary,
    Tertiary,
}

data class NavPane(
    val role: NavPaneRole,
    val entryId: NavEntryId,
)

class NavPaneScene(
    panes: List<NavPane>,
) {
    val panes: List<NavPane> = Collections.unmodifiableList(
        ArrayList(panes),
    )
    val visibleEntryIds: Set<NavEntryId> = Collections.unmodifiableSet(
        this.panes.mapTo(linkedSetOf(), NavPane::entryId),
    )
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

    operator fun get(role: NavPaneRole): NavPane? {
        return panes.firstOrNull { pane -> pane.role == role }
    }

    override fun equals(other: Any?): Boolean {
        return other is NavPaneScene && panes == other.panes
    }

    override fun hashCode(): Int = panes.hashCode()

    override fun toString(): String = "NavPaneScene(panes=$panes)"
}

fun interface NavPaneStrategy {
    fun calculate(
        snapshot: NavBackStackSnapshot,
        maxPaneCount: Int,
    ): NavPaneScene
}

object NavPaneStrategies {
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

    /**
     * Places the newest committed entries into contiguous panes while always retaining the top.
     */
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
