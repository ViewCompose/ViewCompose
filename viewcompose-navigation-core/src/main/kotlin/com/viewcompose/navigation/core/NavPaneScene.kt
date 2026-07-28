package com.viewcompose.navigation.core

import java.util.ArrayList
import java.util.Collections

/**
 * 多 pane 导航布局中的逻辑角色。
 * Logical role in a multi-pane navigation layout.
 */
enum class NavPaneRole {
    Primary,
    Secondary,
    Tertiary,
}

/**
 * 一个 pane 与其展示 entry 的绑定。
 * Binding between one pane and the entry it displays.
 */
data class NavPane(
    val role: NavPaneRole,
    val entryId: NavEntryId,
)

/**
 * 当前导航快照应展示的 pane 场景。
 * Pane scene that should be displayed for the current navigation snapshot.
 */
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

/**
 * 根据 back stack 与最大 pane 数计算可见 pane 场景。
 * Calculates the visible pane scene from a back stack and maximum pane count.
 */
fun interface NavPaneStrategy {
    fun calculate(
        snapshot: NavBackStackSnapshot,
        maxPaneCount: Int,
    ): NavPaneScene
}

/**
 * 内置 pane 策略集合。
 * Built-in pane strategy collection.
 */
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
     * 将最新提交的 entry 放入连续 pane，并始终保留 top entry。
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

/**
 * 执行 pane 策略并校验输出仍引用当前 back stack。
 * Runs a pane strategy and validates that it still references the current back stack.
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
