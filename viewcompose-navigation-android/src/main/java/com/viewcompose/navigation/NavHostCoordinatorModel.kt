package com.viewcompose.navigation

import com.viewcompose.navigation.core.NavBackStackSnapshot
import com.viewcompose.navigation.core.NavCommand
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavNoChangeReason
import com.viewcompose.navigation.core.NavStackMutation
import com.viewcompose.ui.foundation.RenderFrameReport
import com.viewcompose.ui.foundation.RenderFrameStatus

/**
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
 * Internal result produced after the coordinator executes a navigation command.
 */
internal sealed interface NavHostNavigationResult {
    val command: NavCommand

    /**
     * The stack committed and produced a visual transaction for the transition driver.
     */
    data class Committed(
        override val command: NavCommand,
        val snapshot: NavBackStackSnapshot,
        val mutation: NavStackMutation,
        val transition: NavHostTransition,
    ) : NavHostNavigationResult

    /**
     * The command did not change the current stack.
     */
    data class NoChange(
        override val command: NavCommand,
        val reason: NavNoChangeReason,
        val snapshot: NavBackStackSnapshot,
    ) : NavHostNavigationResult

    /**
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
     * The runtime cannot execute the command immediately, so it entered the serial queue.
     */
    data class Queued(
        override val command: NavCommand,
    ) : NavHostNavigationResult
}

/**
 * Render reports collected after refreshing retained destinations.
 */
internal data class NavHostRefreshResult(
    val reports: Map<NavEntryId, RenderFrameReport?>,
) {
    val failedEntryIds: Set<NavEntryId> = reports
        .filterValues { report -> report?.status != RenderFrameStatus.Committed }
        .keys
}

/** Failure produced while synchronously refreshing a retained page before it becomes visible. */
internal data class NavHostDestinationRefreshFailure(
    val failedEntry: NavEntry?,
    val frameReport: RenderFrameReport?,
    val cause: Throwable?,
)
