package com.viewcompose.navigation

import com.viewcompose.navigation.core.NavBackStackSnapshot
import com.viewcompose.navigation.core.NavCommand
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavNoChangeReason
import com.viewcompose.navigation.core.NavStackMutation
import com.viewcompose.widget.core.RenderFrameReport
import com.viewcompose.widget.core.RenderFrameStatus

internal enum class NavHostCoordinatorState {
    Detached,
    Attaching,
    Attached,
    Failed,
    Destroyed,
}

internal enum class NavHostFailurePhase {
    DestinationPreparation,
    DestinationRefresh,
    DestinationStage,
    StackCommit,
    CommitEffects,
}

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

internal sealed interface NavHostNavigationResult {
    val command: NavCommand

    data class Committed(
        override val command: NavCommand,
        val snapshot: NavBackStackSnapshot,
        val mutation: NavStackMutation,
        val transition: NavHostTransition,
    ) : NavHostNavigationResult

    data class NoChange(
        override val command: NavCommand,
        val reason: NavNoChangeReason,
        val snapshot: NavBackStackSnapshot,
    ) : NavHostNavigationResult

    data class Failed(
        override val command: NavCommand,
        val snapshot: NavBackStackSnapshot,
        val phase: NavHostFailurePhase,
        val failedEntry: NavEntry?,
        val frameReport: RenderFrameReport?,
        val cause: Throwable?,
        val stackCommitted: Boolean,
    ) : NavHostNavigationResult

    data class Queued(
        override val command: NavCommand,
    ) : NavHostNavigationResult
}

internal data class NavHostRefreshResult(
    val reports: Map<NavEntryId, RenderFrameReport?>,
) {
    val failedEntryIds: Set<NavEntryId> = reports
        .filterValues { report -> report?.status != RenderFrameStatus.Committed }
        .keys
}
