package com.viewcompose.navigation

import com.viewcompose.navigation.core.NavBackStackSnapshot
import com.viewcompose.navigation.core.NavCommand
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavNoChangeReason
import com.viewcompose.navigation.core.NavStackMutation
import com.viewcompose.widget.core.RenderFrameReport
import com.viewcompose.widget.core.RenderFrameStatus

/**
 * NavHost 协调器的宿主生命周期状态。
 * Host lifecycle state tracked by the NavHost coordinator.
 */
internal enum class NavHostCoordinatorState {
    Detached,
    Attaching,
    Attached,
    Failed,
    Destroyed,
}

/**
 * 内部事务失败阶段，最终会映射为公开的 [NavFailurePhase]。
 * Internal transaction failure phase that is mapped to public [NavFailurePhase].
 */
internal enum class NavHostFailurePhase {
    DestinationPreparation,
    DestinationRefresh,
    DestinationStage,
    StackCommit,
    CommitEffects,
}

/**
 * 首次挂载或重新挂载目的地时的结果。
 * Result of initially attaching or reattaching destination content.
 */
internal sealed interface NavHostAttachmentResult {
    data class Attached(
        val snapshot: NavBackStackSnapshot,
    ) : NavHostAttachmentResult

    data class Failed(
        val entry: NavEntry,
        val phase: NavHostFailurePhase,
        val frameReport: RenderFrameReport?,
        val cause: Throwable?,
    ) : NavHostAttachmentResult
}

/**
 * 协调器执行导航命令后的内部结果。
 * Internal result produced after the coordinator executes a navigation command.
 */
internal sealed interface NavHostNavigationResult {
    val command: NavCommand

    /**
     * 栈已提交，并且创建了可交给转场驱动器的视觉事务。
     * The stack committed and produced a visual transaction for the transition driver.
     */
    data class Committed(
        override val command: NavCommand,
        val snapshot: NavBackStackSnapshot,
        val mutation: NavStackMutation,
        val transition: NavHostTransition,
    ) : NavHostNavigationResult

    /**
     * 命令没有改变当前栈。
     * The command did not change the current stack.
     */
    data class NoChange(
        override val command: NavCommand,
        val reason: NavNoChangeReason,
        val snapshot: NavBackStackSnapshot,
    ) : NavHostNavigationResult

    /**
     * 命令失败，字段保留提交前后边界用于故障上报。
     * The command failed; fields keep commit boundaries for failure reporting.
     */
    data class Failed(
        override val command: NavCommand,
        val snapshot: NavBackStackSnapshot,
        val phase: NavHostFailurePhase,
        val failedEntry: NavEntry?,
        val frameReport: RenderFrameReport?,
        val cause: Throwable?,
        val stackCommitted: Boolean,
    ) : NavHostNavigationResult

    /**
     * 运行时当前无法立即执行命令，已进入串行队列。
     * The runtime cannot execute the command immediately, so it entered the serial queue.
     */
    data class Queued(
        override val command: NavCommand,
    ) : NavHostNavigationResult
}

/**
 * 批量刷新目的地后收集的渲染报告。
 * Render reports collected after refreshing retained destinations.
 */
internal data class NavHostRefreshResult(
    val reports: Map<NavEntryId, RenderFrameReport?>,
) {
    val failedEntryIds: Set<NavEntryId> = reports
        .filterValues { report -> report?.status != RenderFrameStatus.Committed }
        .keys
}
