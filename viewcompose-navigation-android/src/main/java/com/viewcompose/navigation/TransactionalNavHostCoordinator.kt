package com.viewcompose.navigation

import android.os.Looper
import android.os.Trace
import androidx.annotation.MainThread
import com.viewcompose.navigation.core.NavBackStackController
import com.viewcompose.navigation.core.NavBackStackSnapshot
import com.viewcompose.navigation.core.NavCommand
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavExecutionPlan
import com.viewcompose.navigation.core.NavExecutionReducer
import com.viewcompose.navigation.core.NavHostLifecycleState
import com.viewcompose.navigation.core.NavPaneStrategies
import com.viewcompose.navigation.core.NavPaneStrategy
import com.viewcompose.navigation.core.NavPreparation
import com.viewcompose.navigation.core.NavSceneLayout
import com.viewcompose.navigation.core.NavSceneStrategy
import com.viewcompose.navigation.core.NavStackSetSnapshot
import com.viewcompose.navigation.core.NavTransaction
import com.viewcompose.navigation.core.NavTransactionStatus
import com.viewcompose.navigation.core.resolveNavSceneLayout
import com.viewcompose.ui.foundation.RenderFrameReport
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
    initialSceneStrategies: List<NavSceneStrategy> = emptyList(),
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

    val ownsSystemBack: Boolean
        get() = currentExecutionPlan?.ownsSystemBack == true

    val systemBackCommand: NavCommand?
        get() = currentExecutionPlan?.systemBackCommand

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
    private var sceneStrategies = initialSceneStrategies.toList()
    private var paneStrategy = initialPaneStrategy
    private var maxPaneCount = initialMaxPaneCount
    private val planExecutor = AndroidNavExecutionPlanExecutor(ownerStore, sessionStore)
    private var currentExecutionPlan: NavExecutionPlan? = null

    init {
        calculateSceneLayout(controller.snapshot())
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
        var result: NavHostAttachmentResult
        val retainedEntries = controller.retainedEntries()
        try {
            val currentSnapshot = controller.snapshot()
            val plan = reduceSettledState(currentSnapshot)
            result = when (
                val preparation = planExecutor.prepare(
                    plan = plan,
                    localSnapshot = localSnapshot,
                    hostLifecycleState = hostLifecycleState,
                    content = content,
                )
            ) {
                is NavPlanPreparationResult.Ready -> {
                    planExecutor.publish(plan)
                    currentExecutionPlan = plan
                    state = NavHostCoordinatorState.Attached
                    NavHostAttachmentResult.Attached(currentSnapshot)
                }

                is NavPlanPreparationResult.Failed -> {
                    rollbackAttachment(retainedEntries, emptyList())
                    state = NavHostCoordinatorState.Detached
                    NavHostAttachmentResult.Failed(
                        entry = preparation.failedEntry ?: currentSnapshot.top,
                        phase = preparation.phase,
                        frameReport = preparation.frameReport,
                        cause = preparation.cause,
                    )
                }
            }
        } catch (throwable: Throwable) {
            rollbackAttachment(
                retainedEntries = retainedEntries,
                attachedEntryIds = sessionStore.presentationState().presentedEntryIds,
            )
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
    fun updateSceneProjection(
        sceneStrategies: List<NavSceneStrategy>,
        strategy: NavPaneStrategy,
        maxPaneCount: Int,
        onDestinationRefreshFailure: (NavHostDestinationRefreshFailure) -> Unit = {},
    ): NavSceneLayout {
        requireMainThread()
        check(state != NavHostCoordinatorState.Destroyed) {
            "A destroyed navigation host cannot change its scene projection."
        }
        val snapshot = controller.snapshot()
        val frozenSceneStrategies = sceneStrategies.toList()
        val layout = resolveNavSceneLayout(
            snapshot = snapshot,
            maxPaneCount = maxPaneCount,
            sceneStrategies = frozenSceneStrategies,
            paneStrategy = strategy,
        )
        if (
            frozenSceneStrategies == this.sceneStrategies &&
            strategy === paneStrategy &&
            maxPaneCount == this.maxPaneCount
        ) {
            return layout
        }
        if (state == NavHostCoordinatorState.Detached) {
            this.sceneStrategies = frozenSceneStrategies
            paneStrategy = strategy
            this.maxPaneCount = maxPaneCount
            return layout
        }
        if (
            state == NavHostCoordinatorState.Attached &&
            activeTransitionRecord == null &&
            activeBackPreviewRecord == null &&
            currentExecutionPlan?.afterSceneLayout == layout
        ) {
            // A new policy identity may affect future stacks without changing the current scene.
            this.sceneStrategies = frozenSceneStrategies
            paneStrategy = strategy
            this.maxPaneCount = maxPaneCount
            return layout
        }
        check(state == NavHostCoordinatorState.Attached) {
            "Navigation scenes can change only while detached or attached; current=$state."
        }
        check(!executing) {
            "Navigation scenes cannot change during another host operation."
        }
        executing = true
        val previousStrategy = paneStrategy
        val previousMaxPaneCount = this.maxPaneCount
        val previousScene = calculateSceneLayout(snapshot)
        val previousSceneStrategies = this.sceneStrategies
        var destinationRefreshFailure: NavHostDestinationRefreshFailure? = null
        val result = try {
            redirectActiveBackPreview(preserveVisualState = false)
            redirectActiveTransition(preserveVisualState = false)
            val plan = reduceSettledState(
                snapshot = snapshot,
                beforeScene = previousScene,
                afterScene = layout,
            )
            when (
                val preparation = planExecutor.prepare(
                    plan = plan,
                    localSnapshot = checkNotNull(localSnapshot),
                    hostLifecycleState = hostLifecycleState,
                    content = checkNotNull(destinationContent),
                )
            ) {
                is NavPlanPreparationResult.Ready -> {
                    this.sceneStrategies = frozenSceneStrategies
                    paneStrategy = strategy
                    this.maxPaneCount = maxPaneCount
                    planExecutor.publish(plan)
                    currentExecutionPlan = plan
                    drainQueuedCommandsWhileExecuting()
                    layout
                }

                is NavPlanPreparationResult.Failed -> {
                    destinationRefreshFailure = preparation.toDestinationRefreshFailure()
                    queuedCommands.clear()
                    previousScene
                }
            }
        } catch (throwable: Throwable) {
            this.sceneStrategies = previousSceneStrategies
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
            val stackState = controller.stackStateSnapshot()
            val currentSnapshot = stackState.activeStack
            val command = systemBackCommand ?: return null
            val afterSnapshot = when (command) {
                NavCommand.Pop -> NavBackStackSnapshot(
                    currentSnapshot.entries.dropLast(1),
                )

                is NavCommand.PopWithResult -> error(
                    "Predictive system Back cannot carry a navigation result.",
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
            val beforeScene = calculateSceneLayout(currentSnapshot)
            val afterScene = calculateSceneLayout(afterSnapshot)
            val presentationState = sessionStore.presentationState()
            val plan = NavExecutionReducer.predictivePreview(
                currentLifecycleStates = ownerStore.currentLifecycleStates(),
                stackState = stackState,
                prospectiveActiveStack = afterSnapshot,
                beforeSceneLayout = beforeScene,
                afterSceneLayout = afterScene,
                hostState = hostLifecycleState,
                presentedEntryIds = presentationState.presentedEntryIds,
                hiddenPresentationRecency = presentationState.hiddenEntryIdsOldestFirst,
                maxRetainedHiddenPresentations = sessionStore.maxRetainedHiddenPresentations,
                systemBackCommand = command,
            )
            when (
                val preparation = planExecutor.prepare(
                    plan = plan,
                    localSnapshot = checkNotNull(localSnapshot),
                    hostLifecycleState = hostLifecycleState,
                    content = checkNotNull(destinationContent),
                )
            ) {
                is NavPlanPreparationResult.Failed -> {
                    destinationRefreshFailure = preparation.toDestinationRefreshFailure()
                    queuedCommands.clear()
                    null
                }

                is NavPlanPreparationResult.Ready -> {
                    // Preview publishes the prospective scene while the pure stack remains unchanged.
                    val preview = NavHostBackPreview(
                        id = NavHostBackPreviewId(++nextBackPreviewId),
                        command = command,
                        snapshot = currentSnapshot,
                        outgoingEntry = currentSnapshot.top,
                        incomingEntry = afterSnapshot.top,
                        beforeScene = beforeScene,
                        afterScene = afterScene,
                        retainedEntries = plan.retainedEntries,
                        scene = plan.scene,
                        layerOrder = plan.layerOrder,
                    )
                    val active = ActiveNavHostBackPreview(preview, plan)
                    activeBackPreviewRecord = active
                    try {
                        planExecutor.publish(plan)
                        currentExecutionPlan = plan
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
    fun updatePresentationRetentionPolicy(policy: NavPresentationRetentionPolicy) {
        requireMainThread()
        check(state != NavHostCoordinatorState.Destroyed) {
            "A destroyed navigation host cannot change presentation retention."
        }
        check(!executing) {
            "Navigation presentation retention cannot change during another host operation."
        }
        val changed = sessionStore.updatePresentationRetentionPolicy(policy)
        if (!changed) {
            return
        }
        if (state == NavHostCoordinatorState.Attached) {
            val plan = reconcilePlan(checkNotNull(currentExecutionPlan))
            planExecutor.publish(plan)
            replaceCurrentPlan(plan)
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
                else -> calculateSceneLayout(controller.snapshot()).visibleEntryIds
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
            val plan = reconcilePlan(checkNotNull(currentExecutionPlan))
            planExecutor.publishLifecycle(plan)
            replaceCurrentPlan(plan)
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
    fun destroy(retainViewModelScopes: Boolean = false) {
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
                sessionStore.destroy(retainViewModelScopes)
            }.exceptionOrNull()?.let(failures::add)
        } finally {
            state = NavHostCoordinatorState.Destroyed
            executing = false
            currentExecutionPlan = null
        }
        failures.firstOrNull()?.let { first ->
            failures.drop(1).forEach(first::addSuppressed)
            throw first
        }
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
        val presentationState = sessionStore.presentationState()
        val plan = try {
            NavExecutionReducer.transition(
                currentLifecycleStates = ownerStore.currentLifecycleStates(),
                transaction = transaction,
                beforeSceneLayout = calculateSceneLayout(transaction.before),
                afterSceneLayout = calculateSceneLayout(transaction.after),
                hostState = hostLifecycleState,
                presentedEntryIds = presentationState.presentedEntryIds,
                hiddenPresentationRecency = presentationState.hiddenEntryIdsOldestFirst,
                maxRetainedHiddenPresentations = sessionStore.maxRetainedHiddenPresentations,
            )
        } catch (throwable: Throwable) {
            rollback(transaction)
            throw throwable
        }
        val preparation = traceSection("VC.Nav.PreparePresentations") {
            planExecutor.prepare(
                plan = plan,
                localSnapshot = checkNotNull(localSnapshot),
                hostLifecycleState = hostLifecycleState,
                content = checkNotNull(destinationContent),
            )
        }
        if (preparation is NavPlanPreparationResult.Failed) {
            rollback(transaction)
            return failedBeforeCommit(
                transaction = transaction,
                phase = preparation.phase,
                failedEntry = preparation.failedEntry,
                frameReport = preparation.frameReport,
                cause = preparation.cause,
            )
        }
        val createdPresentationEntryIds =
            (preparation as NavPlanPreparationResult.Ready).createdPresentationEntryIds

        // Commit the pure stack only after the new destination stages; remove the candidate on commit failure.
        val committedSnapshot = try {
            traceSection("VC.Nav.CommitStack") {
                transaction.commit()
            }
        } catch (throwable: Throwable) {
            planExecutor.rollback(plan, createdPresentationEntryIds)
                .forEach(throwable::addSuppressed)
            rollback(transaction)
            return failedBeforeCommit(
                transaction = transaction,
                phase = NavHostFailurePhase.StackCommit,
                failedEntry = transaction.mutation.added.singleOrNull(),
                cause = throwable,
            )
        }

        return try {
            val transition = traceSection("VC.Nav.BeginTransition") {
                beginTransition(
                    plan = plan,
                    command = transaction.command,
                    committedSnapshot = committedSnapshot,
                    backPreviewHandle = backPreviewHandle,
                )
            }
            NavHostNavigationResult.Committed(
                command = transaction.command,
                snapshot = committedSnapshot,
                mutation = transaction.mutation,
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
        scene: NavSceneLayout = calculateSceneLayout(snapshot),
    ) {
        val plan = reduceSettledState(snapshot, scene, scene)
        planExecutor.publish(plan)
        replaceCurrentPlan(plan)
    }

    private fun reduceSettledState(
        snapshot: NavBackStackSnapshot,
        beforeScene: NavSceneLayout = calculateSceneLayout(snapshot),
        afterScene: NavSceneLayout = beforeScene,
    ): NavExecutionPlan {
        val stackState = controller.stackStateSnapshot()
        check(stackState.activeStack == snapshot) {
            "A settled navigation plan must describe the committed active stack."
        }
        val presentationState = sessionStore.presentationState()
        val systemBackCommand = controller.systemBackCommand()
        return NavExecutionReducer.settled(
            currentLifecycleStates = ownerStore.currentLifecycleStates(),
            stackState = stackState,
            sceneLayout = afterScene,
            previousSceneLayout = beforeScene,
            hostState = hostLifecycleState,
            presentedEntryIds = presentationState.presentedEntryIds,
            hiddenPresentationRecency = presentationState.hiddenEntryIdsOldestFirst,
            maxRetainedHiddenPresentations = sessionStore.maxRetainedHiddenPresentations,
            systemBackCommand = systemBackCommand,
        )
    }

    private fun reconcilePlan(plan: NavExecutionPlan): NavExecutionPlan {
        val presentationState = sessionStore.presentationState()
        return NavExecutionReducer.reconcile(
            plan = plan,
            currentLifecycleStates = ownerStore.currentLifecycleStates(),
            hostState = hostLifecycleState,
            presentedEntryIds = presentationState.presentedEntryIds,
            hiddenPresentationRecency = presentationState.hiddenEntryIdsOldestFirst,
            maxRetainedHiddenPresentations = sessionStore.maxRetainedHiddenPresentations,
            systemBackCommand = plan.systemBackCommand,
        )
    }

    private fun replaceCurrentPlan(plan: NavExecutionPlan) {
        currentExecutionPlan = plan
        activeTransitionRecord?.plan = plan
        activeBackPreviewRecord?.plan = plan
    }

    private fun beginTransition(
        plan: NavExecutionPlan,
        command: NavCommand,
        committedSnapshot: NavBackStackSnapshot,
        backPreviewHandle: NavHostBackPreviewHandle?,
    ): NavHostTransition {
        check(activeTransitionRecord == null) {
            "A navigation transition must be terminal before another transition starts."
        }
        check(plan.after == committedSnapshot) {
            "The committed navigation stack must match its reducer plan."
        }
        val transition = NavHostTransition(
            id = NavHostTransitionId(++nextTransitionId),
            command = command,
            before = plan.before,
            after = committedSnapshot,
            mutation = checkNotNull(plan.mutation),
            outgoingEntry = plan.before.top,
            incomingEntry = committedSnapshot.top,
            beforeScene = plan.beforeSceneLayout,
            afterScene = plan.afterSceneLayout,
            retainedEntries = plan.retainedEntries,
            scene = plan.scene,
            layerOrder = plan.layerOrder,
        )
        val active = ActiveNavHostTransition(transition, plan)
        activeTransitionRecord = active
        try {
            planExecutor.publish(plan)
            currentExecutionPlan = plan
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

    private fun calculateSceneLayout(snapshot: NavBackStackSnapshot): NavSceneLayout {
        return resolveNavSceneLayout(
            snapshot = snapshot,
            maxPaneCount = maxPaneCount,
            sceneStrategies = sceneStrategies,
            paneStrategy = paneStrategy,
        )
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
        // Execute the reducer's permanent cleanup only after outgoing motion terminates.
        runCatching {
            planExecutor.terminalCleanup(active.plan)
        }.exceptionOrNull()?.let(failures::add)
        val settlementFailure = runCatching {
            applySettledState(active.transition.after)
        }.exceptionOrNull()
        // A result callback may inspect this record and enqueue navigation during reconciliation.
        // Publish it first; any drained command replaces it only when that later transition terminates.
        lastTransitionResult = NavHostTransitionResult(
            transition = active.transition,
            outcome = outcome,
        )
        if (settlementFailure == null) {
            runCatching {
                planExecutor.synchronizeResultConsumer(active.plan)
            }.exceptionOrNull()?.let(failures::add)
        } else {
            failures += settlementFailure
        }
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

    private fun rollback(transaction: NavTransaction) {
        if (transaction.status == NavTransactionStatus.Prepared) {
            runCatching(transaction::rollback)
        }
    }

    private fun rollbackAttachment(
        retainedEntries: List<NavEntry>,
        attachedEntryIds: List<NavEntryId>,
    ) {
        val failures = mutableListOf<Throwable>()
        attachedEntryIds.asReversed().forEach { entryId ->
            runCatching {
                sessionStore.remove(entryId)
            }.exceptionOrNull()?.let(failures::add)
        }
        retainedEntries.asReversed().forEach { entry ->
            runCatching {
                ownerStore.remove(entry.id)
            }.exceptionOrNull()?.let(failures::add)
        }
        retainedEntries
            .flatMap { entry -> entry.graphEntries.withIndex() }
            .distinctBy { indexed -> indexed.value.id }
            .sortedByDescending { indexed -> indexed.index }
            .forEach { indexed ->
                runCatching {
                    ownerStore.removeGraphOwner(indexed.value.id)
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
    var plan: NavExecutionPlan,
    var handle: NavHostTransitionHandle? = null,
)

private class ActiveNavHostBackPreview(
    val preview: NavHostBackPreview,
    var plan: NavExecutionPlan,
    var handle: NavHostBackPreviewHandle? = null,
)

private enum class NavBackPreviewTermination {
    Cancel,
    Redirect,
    Dispose,
}

private fun NavPlanPreparationResult.Failed.toDestinationRefreshFailure():
    NavHostDestinationRefreshFailure {
    return NavHostDestinationRefreshFailure(
        failedEntry = failedEntry,
        frameReport = frameReport,
        cause = cause,
    )
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
