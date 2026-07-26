package com.viewcompose.navigation

import android.os.Looper
import androidx.annotation.MainThread
import com.viewcompose.navigation.core.NavBackStackController
import com.viewcompose.navigation.core.NavBackStackSnapshot
import com.viewcompose.navigation.core.NavCommand
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavHostLifecycleState
import com.viewcompose.navigation.core.NavPreparation
import com.viewcompose.navigation.core.NavTransaction
import com.viewcompose.navigation.core.NavTransactionStatus
import com.viewcompose.widget.core.RenderFrameReport
import com.viewcompose.widget.core.RenderFrameStatus
import com.viewcompose.widget.core.UiLocalSnapshot
import java.util.ArrayDeque

/**
 * Owns the synchronous commit boundary between the pure back stack and Android page sessions.
 *
 * The class stays internal until restoration, platform back, and transition completion can share
 * this same boundary. All methods are main-thread APIs; re-entrant commands are queued and drained
 * only after the current command reaches a terminal result.
 */
internal class TransactionalNavHostCoordinator(
    private val controller: NavBackStackController,
    private val ownerStore: NavEntryOwnerStore,
    private val sessionStore: NavDestinationSessionStore,
    initialHostLifecycleState: NavHostLifecycleState = NavHostLifecycleState.Created,
) {
    val hostView: NavHostView
        get() = sessionStore.hostView

    var state: NavHostCoordinatorState = NavHostCoordinatorState.Detached
        private set

    val snapshot: NavBackStackSnapshot
        get() = controller.snapshot()

    private var hostLifecycleState = initialHostLifecycleState
    private var localSnapshot: UiLocalSnapshot? = null
    private var destinationContent: NavDestinationContent? = null
    private val queuedCommands = ArrayDeque<NavCommand>()
    private var executing = false

    @MainThread
    fun attach(
        localSnapshot: UiLocalSnapshot,
        content: NavDestinationContent,
    ): NavHostAttachmentResult {
        requireMainThread()
        check(state == NavHostCoordinatorState.Detached) {
            "Navigation host can attach only from Detached; current=$state."
        }
        check(hostLifecycleState != NavHostLifecycleState.Initialized) {
            "Navigation host pages cannot attach before the platform host reaches Created."
        }
        check(hostLifecycleState != NavHostLifecycleState.Destroyed) {
            "A destroyed platform host cannot attach navigation pages."
        }
        state = NavHostCoordinatorState.Attaching
        executing = true
        this.localSnapshot = localSnapshot
        destinationContent = content
        val attachedEntryIds = mutableListOf<NavEntryId>()
        var result: NavHostAttachmentResult
        try {
            val currentSnapshot = controller.snapshot()
            result = attachEntries(
                entries = currentSnapshot.entries,
                attachedEntryIds = attachedEntryIds,
            )
            if (result is NavHostAttachmentResult.Attached) {
                applySettledState(currentSnapshot)
                state = NavHostCoordinatorState.Attached
            } else {
                removeSessions(attachedEntryIds)
                state = NavHostCoordinatorState.Detached
            }
        } catch (throwable: Throwable) {
            removeSessions(attachedEntryIds)
            state = NavHostCoordinatorState.Detached
            val entry = controller.snapshot().top
            result = NavHostAttachmentResult.Failed(
                entry = entry,
                phase = NavHostFailurePhase.CommitEffects,
                frameReport = sessionStore.sessionOrNull(entry.id)?.lastFrameReport,
                cause = throwable,
            )
        } finally {
            executing = false
            if (state != NavHostCoordinatorState.Attached) {
                queuedCommands.clear()
            }
        }
        if (state == NavHostCoordinatorState.Attached) {
            drainQueuedCommands()
            result = NavHostAttachmentResult.Attached(controller.snapshot())
        }
        return result
    }

    @MainThread
    fun navigate(command: NavCommand): NavHostNavigationResult {
        requireMainThread()
        check(
            state == NavHostCoordinatorState.Attached ||
                state == NavHostCoordinatorState.Attaching,
        ) {
            "Navigation commands require an attached host; current=$state."
        }
        if (executing) {
            queuedCommands.addLast(command)
            return NavHostNavigationResult.Queued(command)
        }
        check(state == NavHostCoordinatorState.Attached) {
            "Navigation commands cannot start while the host is $state."
        }
        executing = true
        return try {
            val firstResult = execute(command)
            if (firstResult is NavHostNavigationResult.Failed) {
                queuedCommands.clear()
            } else {
                drainQueuedCommandsWhileExecuting()
            }
            firstResult
        } finally {
            executing = false
        }
    }

    @MainThread
    fun refresh(
        localSnapshot: UiLocalSnapshot,
        content: NavDestinationContent,
    ): NavHostRefreshResult {
        requireMainThread()
        check(state == NavHostCoordinatorState.Attached) {
            "Navigation pages can refresh only while attached; current=$state."
        }
        check(!executing) {
            "Navigation pages cannot refresh during another host operation."
        }
        this.localSnapshot = localSnapshot
        destinationContent = content
        executing = true
        return try {
            val reports = linkedMapOf<NavEntryId, RenderFrameReport?>()
            controller.snapshot().entries.forEach { entry ->
                reports[entry.id] = checkNotNull(sessionStore.sessionOrNull(entry.id)) {
                    "Attached destination ${entry.id} has no page session."
                }.render(localSnapshot, content)
            }
            val result = NavHostRefreshResult(reports)
            if (result.failedEntryIds.isEmpty()) {
                drainQueuedCommandsWhileExecuting()
            } else {
                queuedCommands.clear()
            }
            result
        } catch (throwable: Throwable) {
            state = NavHostCoordinatorState.Failed
            queuedCommands.clear()
            throw throwable
        } finally {
            executing = false
        }
    }

    @MainThread
    fun moveHostTo(hostLifecycleState: NavHostLifecycleState) {
        requireMainThread()
        check(state != NavHostCoordinatorState.Destroyed) {
            "A destroyed navigation host cannot change lifecycle state."
        }
        if (hostLifecycleState == NavHostLifecycleState.Destroyed) {
            destroy()
            return
        }
        check(
            hostLifecycleState != NavHostLifecycleState.Initialized ||
                state == NavHostCoordinatorState.Detached,
        ) {
            "An attached navigation host cannot return to Initialized."
        }
        this.hostLifecycleState = hostLifecycleState
        if (state != NavHostCoordinatorState.Attached) {
            return
        }
        check(!executing) {
            "Navigation host lifecycle cannot change during another host operation."
        }
        executing = true
        try {
            reconcileOwners(controller.snapshot())
            drainQueuedCommandsWhileExecuting()
        } catch (throwable: Throwable) {
            state = NavHostCoordinatorState.Failed
            queuedCommands.clear()
            throw throwable
        } finally {
            executing = false
        }
    }

    @MainThread
    fun destroy() {
        requireMainThread()
        if (state == NavHostCoordinatorState.Destroyed) {
            return
        }
        queuedCommands.clear()
        try {
            sessionStore.destroy()
        } finally {
            state = NavHostCoordinatorState.Destroyed
            executing = false
        }
    }

    private fun attachEntries(
        entries: List<NavEntry>,
        attachedEntryIds: MutableList<NavEntryId>,
    ): NavHostAttachmentResult {
        val currentLocalSnapshot = checkNotNull(localSnapshot)
        val currentContent = checkNotNull(destinationContent)
        entries.forEach { entry ->
            when (
                val preparation = sessionStore.prepare(
                    entry = entry,
                    localSnapshot = currentLocalSnapshot,
                    content = currentContent,
                )
            ) {
                is NavDestinationPreparation.Failed -> {
                    return NavHostAttachmentResult.Failed(
                        entry = entry,
                        phase = NavHostFailurePhase.DestinationPreparation,
                        frameReport = preparation.frameReport,
                        cause = preparation.cause,
                    )
                }

                is NavDestinationPreparation.Ready -> {
                    val candidate = preparation.candidate
                    try {
                        candidate.stage()
                        candidate.commit()
                        attachedEntryIds += entry.id
                    } catch (throwable: Throwable) {
                        if (
                            candidate.status == NavDestinationCandidateStatus.Prepared ||
                            candidate.status == NavDestinationCandidateStatus.Staged
                        ) {
                            runCatching(candidate::rollback)
                        } else if (
                            candidate.status == NavDestinationCandidateStatus.Committed
                        ) {
                            runCatching { sessionStore.remove(entry.id) }
                        }
                        return NavHostAttachmentResult.Failed(
                            entry = entry,
                            phase = NavHostFailurePhase.DestinationStage,
                            frameReport = candidate.destinationSession.lastFrameReport,
                            cause = throwable,
                        )
                    }
                }
            }
        }
        return NavHostAttachmentResult.Attached(controller.snapshot())
    }

    private fun execute(command: NavCommand): NavHostNavigationResult {
        return when (val preparation = controller.prepare(command)) {
            is NavPreparation.NoChange -> {
                NavHostNavigationResult.NoChange(
                    command = command,
                    reason = preparation.reason,
                    snapshot = preparation.snapshot,
                )
            }

            is NavPreparation.Ready -> execute(preparation.transaction)
        }
    }

    private fun execute(transaction: NavTransaction): NavHostNavigationResult {
        val mutation = transaction.mutation
        check(mutation.added.size <= 1) {
            "A navigation command cannot add more than one destination."
        }
        val addedEntry = mutation.added.singleOrNull()
        var candidate: NavDestinationCandidate? = null
        if (addedEntry != null) {
            when (
                val preparation = runCatching {
                    sessionStore.prepare(
                        entry = addedEntry,
                        localSnapshot = checkNotNull(localSnapshot),
                        content = checkNotNull(destinationContent),
                    )
                }.getOrElse { throwable ->
                    rollback(transaction)
                    return failedBeforeCommit(
                        transaction = transaction,
                        phase = NavHostFailurePhase.DestinationPreparation,
                        failedEntry = addedEntry,
                        cause = throwable,
                    )
                }
            ) {
                is NavDestinationPreparation.Failed -> {
                    rollback(transaction)
                    return failedBeforeCommit(
                        transaction = transaction,
                        phase = NavHostFailurePhase.DestinationPreparation,
                        failedEntry = addedEntry,
                        frameReport = preparation.frameReport,
                        cause = preparation.cause,
                    )
                }

                is NavDestinationPreparation.Ready -> {
                    candidate = preparation.candidate
                }
            }
        } else {
            val revealedEntry = transaction.after.top
            val frameReport = runCatching {
                checkNotNull(sessionStore.sessionOrNull(revealedEntry.id)) {
                    "Revealed destination ${revealedEntry.id} has no page session."
                }.render(
                    localSnapshot = checkNotNull(localSnapshot),
                    content = checkNotNull(destinationContent),
                )
            }.getOrElse { throwable ->
                rollback(transaction)
                return failedBeforeCommit(
                    transaction = transaction,
                    phase = NavHostFailurePhase.DestinationRefresh,
                    failedEntry = revealedEntry,
                    cause = throwable,
                )
            }
            if (frameReport?.status != RenderFrameStatus.Committed) {
                rollback(transaction)
                return failedBeforeCommit(
                    transaction = transaction,
                    phase = NavHostFailurePhase.DestinationRefresh,
                    failedEntry = revealedEntry,
                    frameReport = frameReport,
                    cause = frameReport?.failures?.firstOrNull()?.cause,
                )
            }
        }

        if (candidate != null) {
            try {
                candidate.stage()
                candidate.commit()
            } catch (throwable: Throwable) {
                rollbackCandidate(candidate)
                rollback(transaction)
                return failedBeforeCommit(
                    transaction = transaction,
                    phase = NavHostFailurePhase.DestinationStage,
                    failedEntry = addedEntry,
                    frameReport = candidate.destinationSession.lastFrameReport,
                    cause = throwable,
                )
            }
        }

        val committedSnapshot = try {
            transaction.commit()
        } catch (throwable: Throwable) {
            addedEntry?.let { entry ->
                runCatching { sessionStore.remove(entry.id) }
            }
            rollback(transaction)
            return failedBeforeCommit(
                transaction = transaction,
                phase = NavHostFailurePhase.StackCommit,
                failedEntry = addedEntry,
                cause = throwable,
            )
        }

        return try {
            removeSessions(mutation.removed.map(NavEntry::id).asReversed())
            applySettledState(committedSnapshot)
            NavHostNavigationResult.Committed(
                command = transaction.command,
                snapshot = committedSnapshot,
                mutation = mutation,
            )
        } catch (throwable: Throwable) {
            state = NavHostCoordinatorState.Failed
            NavHostNavigationResult.Failed(
                command = transaction.command,
                snapshot = controller.snapshot(),
                phase = NavHostFailurePhase.CommitEffects,
                failedEntry = null,
                frameReport = null,
                cause = throwable,
                stackCommitted = true,
            )
        }
    }

    private fun applySettledState(snapshot: NavBackStackSnapshot) {
        val topId = snapshot.top.id
        sessionStore.present(
            layerOrder = snapshot.entries.map(NavEntry::id),
            visibleEntryIds = setOf(topId),
        )
        reconcileOwners(snapshot)
    }

    private fun reconcileOwners(snapshot: NavBackStackSnapshot) {
        val topId = snapshot.top.id
        ownerStore.reconcile(
            retainedEntries = snapshot.entries,
            visibleEntryIds = setOf(topId),
            interactiveEntryId = topId,
            hostState = hostLifecycleState,
        )
    }

    private fun failedBeforeCommit(
        transaction: NavTransaction,
        phase: NavHostFailurePhase,
        failedEntry: NavEntry?,
        frameReport: RenderFrameReport? = null,
        cause: Throwable?,
    ): NavHostNavigationResult.Failed {
        return NavHostNavigationResult.Failed(
            command = transaction.command,
            snapshot = controller.snapshot(),
            phase = phase,
            failedEntry = failedEntry,
            frameReport = frameReport,
            cause = cause,
            stackCommitted = false,
        )
    }

    private fun rollbackCandidate(candidate: NavDestinationCandidate) {
        when (candidate.status) {
            NavDestinationCandidateStatus.Prepared,
            NavDestinationCandidateStatus.Staged,
            -> runCatching(candidate::rollback)

            NavDestinationCandidateStatus.Committed -> {
                runCatching { sessionStore.remove(candidate.entry.id) }
            }

            NavDestinationCandidateStatus.RolledBack -> Unit
        }
    }

    private fun rollback(transaction: NavTransaction) {
        if (transaction.status == NavTransactionStatus.Prepared) {
            runCatching(transaction::rollback)
        }
    }

    private fun removeSessions(entryIds: List<NavEntryId>) {
        val failures = mutableListOf<Throwable>()
        entryIds.forEach { entryId ->
            runCatching {
                sessionStore.remove(entryId)
            }.exceptionOrNull()?.let(failures::add)
        }
        failures.firstOrNull()?.let { first ->
            failures.drop(1).forEach(first::addSuppressed)
            throw first
        }
    }

    private fun drainQueuedCommands() {
        check(!executing)
        if (queuedCommands.isEmpty() || state != NavHostCoordinatorState.Attached) {
            return
        }
        executing = true
        try {
            drainQueuedCommandsWhileExecuting()
        } finally {
            executing = false
        }
    }

    private fun drainQueuedCommandsWhileExecuting() {
        check(executing)
        var processed = 0
        while (
            queuedCommands.isNotEmpty() &&
            state == NavHostCoordinatorState.Attached
        ) {
            check(++processed <= MAX_REENTRANT_COMMANDS_PER_DRAIN) {
                queuedCommands.clear()
                "Navigation produced more than $MAX_REENTRANT_COMMANDS_PER_DRAIN " +
                    "re-entrant commands in one synchronous drain."
            }
            val result = execute(queuedCommands.removeFirst())
            if (result is NavHostNavigationResult.Failed) {
                queuedCommands.clear()
                return
            }
        }
    }

    private fun requireMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "Transactional navigation must run on the Android main thread."
        }
    }
}

private const val MAX_REENTRANT_COMMANDS_PER_DRAIN = 64
