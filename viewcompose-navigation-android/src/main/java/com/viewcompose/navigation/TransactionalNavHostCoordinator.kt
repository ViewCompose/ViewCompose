package com.viewcompose.navigation

import android.os.Looper
import android.os.Trace
import androidx.annotation.MainThread
import com.viewcompose.navigation.core.NavBackStackController
import com.viewcompose.navigation.core.NavBackStackSnapshot
import com.viewcompose.navigation.core.NavCommand
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavHostLifecycleState
import com.viewcompose.navigation.core.NavPaneScene
import com.viewcompose.navigation.core.NavPaneStrategies
import com.viewcompose.navigation.core.NavPaneStrategy
import com.viewcompose.navigation.core.NavPreparation
import com.viewcompose.navigation.core.NavStackSetSnapshot
import com.viewcompose.navigation.core.NavTransaction
import com.viewcompose.navigation.core.NavTransactionStatus
import com.viewcompose.navigation.core.calculateValidated
import com.viewcompose.ui.foundation.RenderFrameReport
import com.viewcompose.ui.foundation.RenderFrameStatus
import com.viewcompose.ui.foundation.UiLocalSnapshot
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
    private val transitionDriver: NavHostTransitionDriver = ImmediateNavHostTransitionDriver,
    initialPaneStrategy: NavPaneStrategy = NavPaneStrategies.Single,
    initialMaxPaneCount: Int = 1,
) {
    val hostView: NavHostView
        get() = sessionStore.hostView

    var state: NavHostCoordinatorState = NavHostCoordinatorState.Detached
        private set

    val snapshot: NavBackStackSnapshot
        get() = controller.snapshot()

    val stackState: NavStackSetSnapshot
        get() = controller.stackStateSnapshot()

    val activeTransition: NavHostTransition?
        get() = activeTransitionRecord?.transition

    val activeBackPreview: NavHostBackPreview?
        get() = activeBackPreviewRecord?.preview

    var lastTransitionResult: NavHostTransitionResult? = null
        private set

    private var hostLifecycleState = initialHostLifecycleState
    private var localSnapshot: UiLocalSnapshot? = null
    private var destinationContent: NavDestinationContent? = null
    private val queuedCommands = ArrayDeque<NavCommand>()
    private var executing = false
    private var nextTransitionId = 0L
    private var nextBackPreviewId = 0L
    private var activeTransitionRecord: ActiveNavHostTransition? = null
    private var activeBackPreviewRecord: ActiveNavHostBackPreview? = null
    private var paneStrategy = initialPaneStrategy
    private var maxPaneCount = initialMaxPaneCount

    init {
        paneStrategy.calculateValidated(controller.snapshot(), maxPaneCount)
    }

    @MainThread
    fun attach(
        localSnapshot: UiLocalSnapshot,
        content: NavDestinationContent,
    ): NavHostAttachmentResult {
        requireMainThread()
        check(state == NavHostCoordinatorState.Detached) {
            "Navigation host can attach only from Detached; current=$state."
        }
        check(hostLifecycleState != NavHostLifecycleState.Destroyed) {
            "A destroyed platform host cannot attach navigation pages."
        }
        state = NavHostCoordinatorState.Attaching
        executing = true
        this.localSnapshot = localSnapshot
        destinationContent = content
        // Track sessions created during this attach so failure rolls back only this attempt.
        val attachedEntryIds = mutableListOf<NavEntryId>()
        var result: NavHostAttachmentResult
        try {
            val currentSnapshot = controller.snapshot()
            result = attachEntries(
                entries = controller.retainedEntries(),
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
            // Navigation may be triggered from render/lifecycle callbacks; queue it to avoid re-entry.
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
        } catch (throwable: Throwable) {
            state = NavHostCoordinatorState.Failed
            queuedCommands.clear()
            throw throwable
        } finally {
            executing = false
        }
    }

    @MainThread
    fun updatePaneStrategy(
        strategy: NavPaneStrategy,
        maxPaneCount: Int,
        onDestinationRefreshFailure: (NavHostDestinationRefreshFailure) -> Unit = {},
    ): NavPaneScene {
        requireMainThread()
        check(state != NavHostCoordinatorState.Destroyed) {
            "A destroyed navigation host cannot change its pane strategy."
        }
        val snapshot = controller.snapshot()
        val scene = strategy.calculateValidated(snapshot, maxPaneCount)
        if (strategy === paneStrategy && maxPaneCount == this.maxPaneCount) {
            return scene
        }
        if (state == NavHostCoordinatorState.Detached) {
            paneStrategy = strategy
            this.maxPaneCount = maxPaneCount
            return scene
        }
        check(state == NavHostCoordinatorState.Attached) {
            "Navigation panes can change only while detached or attached; current=$state."
        }
        check(!executing) {
            "Navigation panes cannot change during another host operation."
        }
        executing = true
        val previousStrategy = paneStrategy
        val previousMaxPaneCount = this.maxPaneCount
        val previousScene = calculatePaneScene(snapshot)
        var destinationRefreshFailure: NavHostDestinationRefreshFailure? = null
        val result = try {
            redirectActiveBackPreview(preserveVisualState = false)
            redirectActiveTransition(preserveVisualState = false)
            val failure = refreshNewlyVisibleDestinations(
                beforeScene = previousScene,
                afterScene = scene,
                retainedEntries = controller.retainedEntries(),
            )
            if (failure == null) {
                paneStrategy = strategy
                this.maxPaneCount = maxPaneCount
                applySettledState(snapshot, scene)
                drainQueuedCommandsWhileExecuting()
                scene
            } else {
                destinationRefreshFailure = failure
                queuedCommands.clear()
                previousScene
            }
        } catch (throwable: Throwable) {
            paneStrategy = previousStrategy
            this.maxPaneCount = previousMaxPaneCount
            runCatching { applySettledState(controller.snapshot()) }
                .exceptionOrNull()
                ?.let(throwable::addSuppressed)
            state = NavHostCoordinatorState.Failed
            queuedCommands.clear()
            throw throwable
        } finally {
            executing = false
        }
        destinationRefreshFailure?.let(onDestinationRefreshFailure)
        return result
    }

    @MainThread
    fun completeTransition(transitionId: NavHostTransitionId): Boolean {
        return terminateTransition(
            transitionId = transitionId,
            outcome = NavHostTransitionOutcome.Completed,
            cancelDriver = false,
        )
    }

    @MainThread
    fun cancelTransition(transitionId: NavHostTransitionId): Boolean {
        return terminateTransition(
            transitionId = transitionId,
            outcome = NavHostTransitionOutcome.Cancelled,
            cancelDriver = true,
        )
    }

    @MainThread
    fun beginBackPreview(
        event: NavHostBackEvent,
        onDestinationRefreshFailure: (NavHostDestinationRefreshFailure) -> Unit = {},
    ): NavHostBackPreview? {
        requireMainThread()
        check(state == NavHostCoordinatorState.Attached) {
            "Predictive back requires an attached host; current=$state."
        }
        check(!executing) {
            "Predictive back cannot start during another host operation."
        }
        executing = true
        var destinationRefreshFailure: NavHostDestinationRefreshFailure? = null
        val result = try {
            redirectActiveBackPreview(preserveVisualState = false)
            redirectActiveTransition(preserveVisualState = false)
            val currentSnapshot = controller.snapshot()
            val command = controller.systemBackCommand() ?: return null
            val incomingEntry = when (command) {
                NavCommand.Pop -> {
                    currentSnapshot.entries[currentSnapshot.entries.lastIndex - 1]
                }

                NavCommand.PopStackHistory -> {
                    val previousStackId = checkNotNull(
                        controller.stackStateSnapshot().selectionHistory.lastOrNull(),
                    )
                    controller.stackSnapshot(previousStackId).top
                }

                is NavCommand.Push,
                is NavCommand.ReplaceTop,
                is NavCommand.Reset,
                is NavCommand.SelectStack,
                is NavCommand.OpenDeepLink,
                -> error("System Back produced a forward navigation command: $command")
            }
            val retainedEntries = controller.retainedEntries()
            val afterSnapshot = when (command) {
                NavCommand.Pop -> NavBackStackSnapshot(
                    currentSnapshot.entries.dropLast(1),
                )

                NavCommand.PopStackHistory -> {
                    val previousStackId = checkNotNull(
                        controller.stackStateSnapshot().selectionHistory.lastOrNull(),
                    )
                    controller.stackSnapshot(previousStackId)
                }

                is NavCommand.Push,
                is NavCommand.ReplaceTop,
                is NavCommand.Reset,
                is NavCommand.SelectStack,
                is NavCommand.OpenDeepLink,
                -> error("System Back produced a forward navigation command: $command")
            }
            val beforeScene = calculatePaneScene(currentSnapshot)
            val afterScene = calculatePaneScene(afterSnapshot)
            val failure = refreshNewlyVisibleDestinations(
                beforeScene = beforeScene,
                afterScene = afterScene,
                retainedEntries = retainedEntries,
            )
            if (failure != null) {
                destinationRefreshFailure = failure
                queuedCommands.clear()
                null
            } else {
                // Preview builds only a visual afterSnapshot; the pure stack commits later.
                val preview = NavHostBackPreview(
                    id = NavHostBackPreviewId(++nextBackPreviewId),
                    command = command,
                    snapshot = currentSnapshot,
                    outgoingEntry = currentSnapshot.top,
                    incomingEntry = incomingEntry,
                    beforeScene = beforeScene,
                    afterScene = afterScene,
                    retainedEntries = retainedEntries,
                    visibleEntryIds = unionSceneEntryIds(beforeScene, afterScene),
                    layerOrder = retainedEntries.map(NavEntry::id),
                )
                val active = ActiveNavHostBackPreview(preview)
                activeBackPreviewRecord = active
                try {
                    applyBackPreviewState(preview)
                    active.handle = transitionDriver.startBackPreview(
                        preview = preview,
                        initialEvent = event,
                    )
                    preview
                } catch (throwable: Throwable) {
                    if (activeBackPreviewRecord === active) {
                        activeBackPreviewRecord = null
                        runCatching { active.handle?.dispose() }
                            .exceptionOrNull()
                            ?.let(throwable::addSuppressed)
                        runCatching { applySettledState(currentSnapshot) }
                            .exceptionOrNull()
                            ?.let(throwable::addSuppressed)
                    }
                    throw throwable
                }
            }
        } finally {
            executing = false
        }
        destinationRefreshFailure?.let(onDestinationRefreshFailure)
        return result
    }

    @MainThread
    fun updateBackPreview(
        previewId: NavHostBackPreviewId,
        event: NavHostBackEvent,
    ): Boolean {
        requireMainThread()
        val active = activeBackPreviewRecord
            ?.takeIf { record -> record.preview.id == previewId }
            ?: return false
        check(!executing) {
            "Predictive back cannot update during another host operation."
        }
        check(state == NavHostCoordinatorState.Attached) {
            "Predictive back can update only while attached; current=$state."
        }
        active.handle?.update(event)
        return true
    }

    @MainThread
    fun cancelBackPreview(previewId: NavHostBackPreviewId): Boolean {
        requireMainThread()
        val active = activeBackPreviewRecord
            ?.takeIf { record -> record.preview.id == previewId }
            ?: return false
        check(!executing) {
            "Predictive back cannot cancel during another host operation."
        }
        executing = true
        return try {
            finishBackPreview(
                active = active,
                termination = NavBackPreviewTermination.Cancel,
            )
            drainQueuedCommandsWhileExecuting()
            true
        } catch (throwable: Throwable) {
            state = NavHostCoordinatorState.Failed
            queuedCommands.clear()
            throw throwable
        } finally {
            executing = false
        }
    }

    @MainThread
    fun commitBackPreview(
        previewId: NavHostBackPreviewId,
    ): NavHostNavigationResult {
        requireMainThread()
        val active = checkNotNull(
            activeBackPreviewRecord
                ?.takeIf { record -> record.preview.id == previewId },
        ) {
            "Only the active predictive-back preview can commit."
        }
        check(!executing) {
            "Predictive back cannot commit during another host operation."
        }
        check(state == NavHostCoordinatorState.Attached) {
            "Predictive back can commit only while attached; current=$state."
        }
        executing = true
        activeBackPreviewRecord = null
        return try {
            val result = when (
                val preparation = controller.prepare(active.preview.command)
            ) {
                is NavPreparation.NoChange -> {
                    active.handle?.dispose()
                    applySettledState(controller.snapshot())
                    NavHostNavigationResult.NoChange(
                        command = active.preview.command,
                        reason = preparation.reason,
                        snapshot = preparation.snapshot,
                    )
                }

                is NavPreparation.Ready -> {
                    val committed = execute(
                        transaction = preparation.transaction,
                        backPreviewHandle = checkNotNull(active.handle),
                    )
                    if (
                        committed is NavHostNavigationResult.Failed &&
                        !committed.stackCommitted
                    ) {
                        active.handle?.dispose()
                        applySettledState(controller.snapshot())
                    }
                    committed
                }
            }
            if (result is NavHostNavigationResult.Failed) {
                queuedCommands.clear()
            } else {
                drainQueuedCommandsWhileExecuting()
            }
            result
        } catch (throwable: Throwable) {
            runCatching { active.handle?.dispose() }
                .exceptionOrNull()
                ?.let(throwable::addSuppressed)
            runCatching { applySettledState(controller.snapshot()) }
                .exceptionOrNull()
                ?.let(throwable::addSuppressed)
            state = NavHostCoordinatorState.Failed
            queuedCommands.clear()
            throw throwable
        } finally {
            executing = false
        }
    }

    @MainThread
    fun updateRenderEnvironment(
        localSnapshot: UiLocalSnapshot,
        content: NavDestinationContent,
    ) {
        requireMainThread()
        check(state == NavHostCoordinatorState.Attached) {
            "Navigation environment can update only while attached; current=$state."
        }
        check(!executing) {
            "Navigation environment cannot update during another host operation."
        }
        this.localSnapshot = localSnapshot
        destinationContent = content
        sessionStore.updateRenderEnvironment(localSnapshot, content)
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
        updateRenderEnvironment(localSnapshot, content)
        executing = true
        return try {
            val reports = linkedMapOf<NavEntryId, RenderFrameReport?>()
            val entries = activeTransitionRecord
                ?.transition
                ?.retainedEntries
                ?: controller.retainedEntries()
            // Refresh only visible entries; hidden retained pages render with the latest environment later.
            val visibleEntryIds = when {
                activeTransitionRecord != null -> {
                    checkNotNull(activeTransitionRecord).transition.visibleEntryIds
                }
                activeBackPreviewRecord != null -> {
                    checkNotNull(activeBackPreviewRecord).preview.visibleEntryIds
                }
                else -> calculatePaneScene(controller.snapshot()).visibleEntryIds
            }
            entries.filter { entry -> entry.id in visibleEntryIds }.forEach { entry ->
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
            when {
                activeTransitionRecord != null -> {
                    reconcileOwners(checkNotNull(activeTransitionRecord).transition)
                }
                activeBackPreviewRecord != null -> {
                    reconcileOwners(checkNotNull(activeBackPreviewRecord).preview)
                }
                else -> {
                    reconcileOwners(controller.snapshot())
                }
            }
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
        val failures = mutableListOf<Throwable>()
        activeBackPreviewRecord?.let { active ->
            activeBackPreviewRecord = null
            runCatching {
                active.handle?.dispose()
            }.exceptionOrNull()?.let(failures::add)
        }
        activeTransitionRecord?.let { active ->
            activeTransitionRecord = null
            runCatching {
                active.handle?.cancel()
            }.exceptionOrNull()?.let(failures::add)
            lastTransitionResult = NavHostTransitionResult(
                transition = active.transition,
                outcome = NavHostTransitionOutcome.HostDestroyed,
            )
        }
        runCatching {
            transitionDriver.destroy()
        }.exceptionOrNull()?.let(failures::add)
        try {
            runCatching {
                sessionStore.destroy()
            }.exceptionOrNull()?.let(failures::add)
        } finally {
            state = NavHostCoordinatorState.Destroyed
            executing = false
        }
        failures.firstOrNull()?.let { first ->
            failures.drop(1).forEach(first::addSuppressed)
            throw first
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
                    hostLifecycleState = hostLifecycleState,
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
        redirectActiveBackPreview(preserveVisualState = true)
        redirectActiveTransition(preserveVisualState = true)
        return when (
            val preparation = traceSection("VC.Nav.PrepareCommand") {
                controller.prepare(command)
            }
        ) {
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

    private fun execute(
        transaction: NavTransaction,
        backPreviewHandle: NavHostBackPreviewHandle? = null,
    ): NavHostNavigationResult {
        val mutation = transaction.mutation
        check(mutation.added.size <= 1) {
            "A navigation command cannot add more than one destination."
        }
        val addedEntry = mutation.added.singleOrNull()
        var candidate: NavDestinationCandidate? = null
        if (addedEntry != null) {
            when (
                val preparation = runCatching {
                    traceSection("VC.Nav.PrepareDestination") {
                        sessionStore.prepare(
                            entry = addedEntry,
                            localSnapshot = checkNotNull(localSnapshot),
                            hostLifecycleState = hostLifecycleState,
                            content = checkNotNull(destinationContent),
                        )
                    }
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
            checkNotNull(sessionStore.sessionOrNull(revealedEntry.id)) {
                "Revealed destination ${revealedEntry.id} has no retained page session."
            }
        }

        val destinationRefreshFailure = refreshNewlyVisibleDestinations(
            beforeScene = calculatePaneScene(transaction.before),
            afterScene = calculatePaneScene(transaction.after),
            retainedEntries = controller.retainedEntries(),
            excludedEntryIds = addedEntry?.let { setOf(it.id) }.orEmpty(),
        )
        if (destinationRefreshFailure != null) {
            candidate?.let(::rollbackCandidate)
            rollback(transaction)
            return destinationRefreshFailure.toNavigationFailure(transaction.command)
        }

        if (candidate != null) {
            try {
                traceSection("VC.Nav.StageDestination") {
                    candidate.stage()
                    candidate.commit()
                }
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

        // Commit the pure stack only after the new destination stages; remove the candidate on commit failure.
        val committedSnapshot = try {
            traceSection("VC.Nav.CommitStack") {
                transaction.commit()
            }
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
            val transition = traceSection("VC.Nav.BeginTransition") {
                beginTransition(
                    transaction = transaction,
                    committedSnapshot = committedSnapshot,
                    backPreviewHandle = backPreviewHandle,
                )
            }
            NavHostNavigationResult.Committed(
                command = transaction.command,
                snapshot = committedSnapshot,
                mutation = mutation,
                transition = transition,
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

    private fun applySettledState(
        snapshot: NavBackStackSnapshot,
        scene: NavPaneScene = calculatePaneScene(snapshot),
    ) {
        val retainedEntries = controller.retainedEntries()
        sessionStore.present(
            layerOrder = retainedEntries.map(NavEntry::id),
            visibleEntryIds = scene.visibleEntryIds,
            paneLayouts = scene.toPaneLayouts(),
        )
        reconcileOwners(
            scene = scene,
            retainedEntries = retainedEntries,
        )
    }

    private fun reconcileOwners(
        snapshot: NavBackStackSnapshot,
        retainedEntries: List<NavEntry> = controller.retainedEntries(),
    ) {
        reconcileOwners(
            scene = calculatePaneScene(snapshot),
            retainedEntries = retainedEntries,
        )
    }

    private fun reconcileOwners(
        scene: NavPaneScene,
        retainedEntries: List<NavEntry>,
    ) {
        ownerStore.reconcile(
            retainedEntries = retainedEntries,
            visibleEntryIds = scene.visibleEntryIds,
            interactiveEntryIds = scene.interactiveEntryIds,
            hostState = hostLifecycleState,
        )
    }

    private fun beginTransition(
        transaction: NavTransaction,
        committedSnapshot: NavBackStackSnapshot,
        backPreviewHandle: NavHostBackPreviewHandle?,
    ): NavHostTransition {
        check(activeTransitionRecord == null) {
            "A navigation transition must be terminal before another transition starts."
        }
        val committedEntries = controller.retainedEntries()
        val committedIds = committedEntries
            .mapTo(mutableSetOf(), NavEntry::id)
        val retainedEntries = buildList {
            addAll(committedEntries)
            transaction.mutation.removed.forEach { removedEntry ->
                if (removedEntry.id !in committedIds) {
                    add(removedEntry)
                }
            }
        }
        val outgoingEntry = transaction.before.top
        val incomingEntry = committedSnapshot.top
        val beforeScene = calculatePaneScene(transaction.before)
        val afterScene = calculatePaneScene(committedSnapshot)
        val transition = NavHostTransition(
            id = NavHostTransitionId(++nextTransitionId),
            command = transaction.command,
            before = transaction.before,
            after = committedSnapshot,
            mutation = transaction.mutation,
            outgoingEntry = outgoingEntry,
            incomingEntry = incomingEntry,
            beforeScene = beforeScene,
            afterScene = afterScene,
            retainedEntries = retainedEntries,
            visibleEntryIds = unionSceneEntryIds(beforeScene, afterScene),
            layerOrder = retainedEntries.map(NavEntry::id),
        )
        val active = ActiveNavHostTransition(transition)
        activeTransitionRecord = active
        try {
            applyTransitionState(transition)
            val completion = {
                completeTransition(transition.id)
                Unit
            }
            val handle = if (backPreviewHandle == null) {
                transitionDriver.start(
                    transition = transition,
                    onCompleted = completion,
                )
            } else {
                backPreviewHandle.commit(
                    transition = transition,
                    onCompleted = completion,
                )
            }
            if (activeTransitionRecord === active) {
                active.handle = handle
            }
        } catch (throwable: Throwable) {
            if (activeTransitionRecord === active) {
                runCatching {
                    finishActiveTransition(
                        active = active,
                        outcome = NavHostTransitionOutcome.Cancelled,
                        cancelDriver = false,
                    )
                }.exceptionOrNull()?.let(throwable::addSuppressed)
            }
            throw throwable
        }
        return transition
    }

    private fun applyTransitionState(transition: NavHostTransition) {
        sessionStore.present(
            layerOrder = transition.layerOrder,
            visibleEntryIds = transition.visibleEntryIds,
            paneLayouts = mergePaneLayouts(
                before = transition.beforeScene,
                after = transition.afterScene,
            ),
        )
        reconcileOwners(transition)
        // Outgoing pages stay visible for animation but pause rendering to avoid first-frame rebuilds.
        sessionStore.setRenderingActive(
            entryIds = transition.beforeScene.visibleEntryIds -
                transition.afterScene.visibleEntryIds,
            active = false,
        )
    }

    private fun reconcileOwners(transition: NavHostTransition) {
        ownerStore.reconcile(
            retainedEntries = transition.retainedEntries,
            visibleEntryIds = transition.visibleEntryIds,
            interactiveEntryIds = transition.afterScene.interactiveEntryIds,
            hostState = hostLifecycleState,
        )
    }

    private fun applyBackPreviewState(preview: NavHostBackPreview) {
        sessionStore.present(
            layerOrder = preview.layerOrder,
            visibleEntryIds = preview.visibleEntryIds,
            paneLayouts = mergePaneLayouts(
                before = preview.beforeScene,
                after = preview.afterScene,
            ),
        )
        reconcileOwners(preview)
    }

    private fun reconcileOwners(preview: NavHostBackPreview) {
        ownerStore.reconcile(
            retainedEntries = preview.retainedEntries,
            visibleEntryIds = preview.visibleEntryIds,
            interactiveEntryIds = preview.beforeScene.interactiveEntryIds,
            hostState = hostLifecycleState,
        )
    }

    private fun calculatePaneScene(snapshot: NavBackStackSnapshot): NavPaneScene {
        return paneStrategy.calculateValidated(snapshot, maxPaneCount)
    }

    private fun unionSceneEntryIds(
        before: NavPaneScene,
        after: NavPaneScene,
    ): Set<NavEntryId> {
        return linkedSetOf<NavEntryId>().apply {
            addAll(before.visibleEntryIds)
            addAll(after.visibleEntryIds)
        }
    }

    private fun mergePaneLayouts(
        before: NavPaneScene,
        after: NavPaneScene,
    ): Map<NavEntryId, NavPaneLayout> {
        return linkedMapOf<NavEntryId, NavPaneLayout>().apply {
            putAll(before.toPaneLayouts())
            putAll(after.toPaneLayouts())
        }
    }

    private fun NavPaneScene.toPaneLayouts(): Map<NavEntryId, NavPaneLayout> {
        return panes.associate { pane ->
            pane.entryId to NavPaneLayout(
                role = pane.role,
                paneCount = panes.size,
            )
        }
    }

    private fun terminateTransition(
        transitionId: NavHostTransitionId,
        outcome: NavHostTransitionOutcome,
        cancelDriver: Boolean,
    ): Boolean {
        requireMainThread()
        val active = activeTransitionRecord
            ?.takeIf { it.transition.id == transitionId }
            ?: return false
        if (executing) {
            finishActiveTransition(
                active = active,
                outcome = outcome,
                cancelDriver = cancelDriver,
            )
            return true
        }
        check(state == NavHostCoordinatorState.Attached) {
            "Navigation transitions can terminate only while attached; current=$state."
        }
        executing = true
        return try {
            finishActiveTransition(
                active = active,
                outcome = outcome,
                cancelDriver = cancelDriver,
            )
            drainQueuedCommandsWhileExecuting()
            true
        } catch (throwable: Throwable) {
            state = NavHostCoordinatorState.Failed
            queuedCommands.clear()
            throw throwable
        } finally {
            executing = false
        }
    }

    private fun redirectActiveTransition(
        preserveVisualState: Boolean,
    ) {
        val active = activeTransitionRecord ?: return
        finishActiveTransition(
            active = active,
            outcome = NavHostTransitionOutcome.Redirected,
            cancelDriver = !preserveVisualState,
            redirectDriver = preserveVisualState,
        )
    }

    private fun redirectActiveBackPreview(
        preserveVisualState: Boolean,
    ) {
        val active = activeBackPreviewRecord ?: return
        finishBackPreview(
            active = active,
            termination = if (preserveVisualState) {
                NavBackPreviewTermination.Redirect
            } else {
                NavBackPreviewTermination.Dispose
            },
        )
    }

    private fun finishBackPreview(
        active: ActiveNavHostBackPreview,
        termination: NavBackPreviewTermination,
    ) {
        check(activeBackPreviewRecord === active) {
            "Only the active predictive-back preview can reach a terminal state."
        }
        activeBackPreviewRecord = null
        val failures = mutableListOf<Throwable>()
        runCatching {
            when (termination) {
                NavBackPreviewTermination.Cancel -> active.handle?.cancel()
                NavBackPreviewTermination.Redirect -> active.handle?.redirect()
                NavBackPreviewTermination.Dispose -> active.handle?.dispose()
            }
        }.exceptionOrNull()?.let(failures::add)
        // Preview never commits the pure stack, so termination returns to the gesture-start snapshot.
        runCatching {
            applySettledState(active.preview.snapshot)
        }.exceptionOrNull()?.let(failures::add)
        failures.firstOrNull()?.let { first ->
            failures.drop(1).forEach(first::addSuppressed)
            throw first
        }
    }

    private fun finishActiveTransition(
        active: ActiveNavHostTransition,
        outcome: NavHostTransitionOutcome,
        cancelDriver: Boolean,
        redirectDriver: Boolean = false,
    ) {
        check(activeTransitionRecord === active) {
            "Only the active navigation transition can reach a terminal state."
        }
        activeTransitionRecord = null
        val failures = mutableListOf<Throwable>()
        check(!cancelDriver || !redirectDriver) {
            "A navigation transition cannot be cancelled and redirected simultaneously."
        }
        if (cancelDriver || redirectDriver) {
            runCatching {
                if (redirectDriver) {
                    active.handle?.redirect()
                } else {
                    active.handle?.cancel()
                }
            }.exceptionOrNull()?.let(failures::add)
        }
        // Remove outgoing sessions only after the committed transition terminates so they can animate.
        runCatching {
            removeSessions(
                active.transition.mutation.removed
                    .map(NavEntry::id)
                    .asReversed(),
            )
        }.exceptionOrNull()?.let(failures::add)
        runCatching {
            applySettledState(active.transition.after)
        }.exceptionOrNull()?.let(failures::add)
        lastTransitionResult = NavHostTransitionResult(
            transition = active.transition,
            outcome = outcome,
        )
        failures.firstOrNull()?.let { first ->
            failures.drop(1).forEach(first::addSuppressed)
            throw first
        }
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

    private fun refreshNewlyVisibleDestinations(
        beforeScene: NavPaneScene,
        afterScene: NavPaneScene,
        retainedEntries: List<NavEntry>,
        excludedEntryIds: Set<NavEntryId> = emptySet(),
    ): NavHostDestinationRefreshFailure? {
        val newlyVisibleEntryIds = afterScene.visibleEntryIds - beforeScene.visibleEntryIds -
            excludedEntryIds
        if (newlyVisibleEntryIds.isEmpty()) {
            return null
        }
        val entriesById = retainedEntries.associateBy(NavEntry::id)
        val currentLocalSnapshot = checkNotNull(localSnapshot)
        val currentContent = checkNotNull(destinationContent)
        afterScene.panes.forEach { pane ->
            val entryId = pane.entryId
            if (entryId !in newlyVisibleEntryIds) {
                return@forEach
            }
            val entry = entriesById[entryId]
                ?: return destinationRefreshFailure(
                    failedEntry = null,
                    cause = IllegalStateException(
                        "Newly visible destination $entryId is not retained.",
                    ),
                )
            val session = sessionStore.sessionOrNull(entryId)
                ?: return destinationRefreshFailure(
                    failedEntry = entry,
                    cause = IllegalStateException(
                        "Newly visible destination $entryId has no retained page session.",
                    ),
                )
            val report = runCatching {
                traceSection("VC.Nav.RefreshDestination") {
                    session.render(currentLocalSnapshot, currentContent)
                }
            }.getOrElse { throwable ->
                return destinationRefreshFailure(
                    failedEntry = entry,
                    frameReport = session.lastFrameReport,
                    cause = throwable,
                )
            }
            if (report?.status != RenderFrameStatus.Committed) {
                return destinationRefreshFailure(
                    failedEntry = entry,
                    frameReport = report,
                    cause = report?.failures?.firstOrNull()?.cause,
                )
            }
        }
        return null
    }

    private fun destinationRefreshFailure(
        failedEntry: NavEntry?,
        frameReport: RenderFrameReport? = null,
        cause: Throwable?,
    ): NavHostDestinationRefreshFailure {
        return NavHostDestinationRefreshFailure(
            failedEntry = failedEntry,
            frameReport = frameReport,
            cause = cause,
        )
    }

    private fun NavHostDestinationRefreshFailure.toNavigationFailure(
        command: NavCommand,
    ): NavHostNavigationResult.Failed {
        return NavHostNavigationResult.Failed(
            command = command,
            snapshot = controller.snapshot(),
            phase = NavHostFailurePhase.DestinationRefresh,
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

private class ActiveNavHostTransition(
    val transition: NavHostTransition,
    var handle: NavHostTransitionHandle? = null,
)

private class ActiveNavHostBackPreview(
    val preview: NavHostBackPreview,
    var handle: NavHostBackPreviewHandle? = null,
)

private enum class NavBackPreviewTermination {
    Cancel,
    Redirect,
    Dispose,
}

private inline fun <T> traceSection(
    name: String,
    block: () -> T,
): T {
    Trace.beginSection(name)
    return try {
        block()
    } finally {
        Trace.endSection()
    }
}

private const val MAX_REENTRANT_COMMANDS_PER_DRAIN = 64
