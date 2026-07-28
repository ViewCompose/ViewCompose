package com.viewcompose.navigation.core

import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashMap

/**
 * 一个独立保留导航栈的稳定业务身份。
 * Stable application identity for one independently retained navigation stack.
 */
@JvmInline
value class NavStackId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) {
            "NavStackId must not be blank."
        }
    }

    override fun toString(): String = value

    companion object {
        internal val Default = NavStackId("default")
    }
}

/**
 * 应用选择某个 stack 时采用的行为。
 * Behavior applied when an application selects a stack.
 */
enum class NavStackSelectionMode {
    /**
     * 保留被选中 stack 的完整状态。
     * Preserve the selected stack exactly where the user left it.
     */
    Preserve,

    /**
     * 展示前移除被选中 stack 根节点之上的所有 entry。
     * Remove every entry above the selected stack's root before presenting it.
     */
    PopToRoot,
}

/**
 * 活跃 stack 已位于根节点时的系统 Back 行为。
 * System-Back behavior when the active stack is already at its root.
 */
enum class NavRootBackBehavior {
    /**
     * 不消费 Back，交给外层 host 或 Android 平台处理。
     * Do not consume Back; delegate it to the enclosing host or the Android platform.
     */
    Delegate,

    /**
     * 返回最近选择过的 stack；没有历史后再委托给外层。
     * Return to the most recently selected stack, then delegate when no history remains.
     */
    PreviousStack,
}

/**
 * 声明一个独立保留导航栈的初始 route。
 * Declares the initial route of one independently retained navigation stack.
 */
data class NavStackSpec(
    val id: NavStackId,
    val startDestination: NavRoute,
)

/**
 * 多个独立保留导航栈的不可变配置。
 * Immutable configuration for a set of independently retained navigation stacks.
 */
class NavStackConfiguration(
    val initialStackId: NavStackId,
    stacks: List<NavStackSpec>,
    val rootBackBehavior: NavRootBackBehavior = NavRootBackBehavior.Delegate,
) {
    val stacks: List<NavStackSpec> = Collections.unmodifiableList(
        ArrayList(stacks),
    )

    init {
        require(this.stacks.isNotEmpty()) {
            "Navigation stack configuration must contain at least one stack."
        }
        require(this.stacks.map(NavStackSpec::id).distinct().size == this.stacks.size) {
            "Navigation stack IDs must be unique."
        }
        require(this.stacks.any { stack -> stack.id == initialStackId }) {
            "Initial navigation stack '$initialStackId' is not declared."
        }
    }

    operator fun get(stackId: NavStackId): NavStackSpec? {
        return stacks.firstOrNull { stack -> stack.id == stackId }
    }

    override fun equals(other: Any?): Boolean {
        return other is NavStackConfiguration &&
            initialStackId == other.initialStackId &&
            stacks == other.stacks &&
            rootBackBehavior == other.rootBackBehavior
    }

    override fun hashCode(): Int {
        var result = initialStackId.hashCode()
        result = 31 * result + stacks.hashCode()
        result = 31 * result + rootBackBehavior.hashCode()
        return result
    }

    override fun toString(): String {
        return "NavStackConfiguration(" +
            "initialStackId=$initialStackId, " +
            "stacks=$stacks, " +
            "rootBackBehavior=$rootBackBehavior" +
            ")"
    }

    companion object {
        /**
         * 创建单 stack 配置，用于不需要 bottom-nav/tab 独立栈的场景。
         * Creates a single-stack configuration for hosts that do not need independent bottom-nav/tab stacks.
         */
        fun single(startDestination: NavRoute): NavStackConfiguration {
            return NavStackConfiguration(
                initialStackId = NavStackId.Default,
                stacks = listOf(
                    NavStackSpec(
                        id = NavStackId.Default,
                        startDestination = startDestination,
                    ),
                ),
            )
        }
    }
}

/**
 * 所有保留导航栈的完整不可变状态。
 * Complete immutable state of every retained navigation stack.
 *
 * [selectionHistory] 从旧到新排序，且不包含 [activeStackId]。
 * [selectionHistory] is oldest-to-newest and excludes [activeStackId].
 */
class NavStackSetSnapshot(
    val activeStackId: NavStackId,
    stacks: Map<NavStackId, NavBackStackSnapshot>,
    selectionHistory: List<NavStackId> = emptyList(),
) {
    val stacks: Map<NavStackId, NavBackStackSnapshot> = Collections.unmodifiableMap(
        LinkedHashMap(stacks),
    )
    val selectionHistory: List<NavStackId> = Collections.unmodifiableList(
        ArrayList(selectionHistory),
    )

    val activeStack: NavBackStackSnapshot
        get() = checkNotNull(stacks[activeStackId])

    val allEntries: List<NavEntry> = Collections.unmodifiableList(
        this.stacks.values.flatMap(NavBackStackSnapshot::entries),
    )

    init {
        require(this.stacks.isNotEmpty()) {
            "A navigation stack set must contain at least one stack."
        }
        require(activeStackId in this.stacks) {
            "Active navigation stack '$activeStackId' is not present."
        }
        require(this.selectionHistory.distinct().size == this.selectionHistory.size) {
            "Navigation stack selection history must not contain duplicates."
        }
        require(activeStackId !in this.selectionHistory) {
            "Active navigation stack must not also appear in selection history."
        }
        require(this.selectionHistory.all(this.stacks::containsKey)) {
            "Navigation stack selection history references an unknown stack."
        }
        validateGlobalOwnerIdentities()
    }

    operator fun get(stackId: NavStackId): NavBackStackSnapshot? = stacks[stackId]

    override fun equals(other: Any?): Boolean {
        return other is NavStackSetSnapshot &&
            activeStackId == other.activeStackId &&
            stacks == other.stacks &&
            selectionHistory == other.selectionHistory
    }

    override fun hashCode(): Int {
        var result = activeStackId.hashCode()
        result = 31 * result + stacks.hashCode()
        result = 31 * result + selectionHistory.hashCode()
        return result
    }

    override fun toString(): String {
        return "NavStackSetSnapshot(" +
            "activeStackId=$activeStackId, " +
            "stacks=$stacks, " +
            "selectionHistory=$selectionHistory" +
            ")"
    }

    private fun validateGlobalOwnerIdentities() {
        // destination 和 graph owner ID 在所有 stack 间必须全局唯一，避免跨栈状态串联。
        // Destination and graph owner IDs must be globally unique across stacks to avoid cross-stack state sharing.
        val destinationStackById = linkedMapOf<NavEntryId, NavStackId>()
        val graphStackById = linkedMapOf<NavEntryId, NavStackId>()
        stacks.forEach { (stackId, snapshot) ->
            snapshot.entries.forEach { entry ->
                require(destinationStackById.putIfAbsent(entry.id, stackId) == null) {
                    "A destination entry ID must not be shared by navigation stacks."
                }
                require(entry.id !in graphStackById) {
                    "A destination entry ID must not be reused by a graph in another stack."
                }
                entry.graphEntries.forEach { graphEntry ->
                    require(graphEntry.id !in destinationStackById) {
                        "A graph entry ID must not be reused by a destination in another stack."
                    }
                    val existingStack = graphStackById.putIfAbsent(graphEntry.id, stackId)
                    require(existingStack == null || existingStack == stackId) {
                        "A graph entry ID must not be shared by navigation stacks."
                    }
                }
            }
        }
    }
}
