package com.viewcompose.navigation

import androidx.annotation.MainThread
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavExecutionPlan
import com.viewcompose.navigation.core.NavHostLifecycleState
import com.viewcompose.navigation.core.NavSceneVisibility
import com.viewcompose.ui.foundation.RenderFrameReport
import com.viewcompose.ui.foundation.RenderFrameStatus
import com.viewcompose.ui.foundation.UiLocalSnapshot

/** Result of executing the pre-commit presentation instructions in one Core plan. */
internal sealed interface NavPlanPreparationResult {
    data class Ready(
        val createdPresentationEntryIds: List<NavEntryId>,
    ) : NavPlanPreparationResult

    data class Failed(
        val phase: NavHostFailurePhase,
        val failedEntry: NavEntry?,
        val frameReport: RenderFrameReport?,
        val cause: Throwable?,
    ) : NavPlanPreparationResult
}

/**
 * Executes typed Android effects from one Core navigation plan.
 *
 * This class owns ordering and failure cleanup, not policy: it never derives visibility, lifecycle,
 * retention, focus, transition, or rollback decisions from controller state.
 */
internal class AndroidNavExecutionPlanExecutor(
    private val ownerStore: NavEntryOwnerStore,
    private val sessionStore: NavDestinationSessionStore,
) {
    @MainThread
    fun prepare(
        plan: NavExecutionPlan,
        localSnapshot: UiLocalSnapshot,
        hostLifecycleState: NavHostLifecycleState,
        content: NavDestinationContent,
    ): NavPlanPreparationResult {
        val entriesById = plan.retainedEntries.associateBy(NavEntry::id)
        val addedEntryIds = plan.mutation
            ?.added
            .orEmpty()
            .mapTo(hashSetOf(), NavEntry::id)
        val createdPresentationEntryIds = mutableListOf<NavEntryId>()
        plan.preparePresentationEntryIds.forEach { entryId ->
            val entry = entriesById[entryId]
                ?: return preparationFailure(
                    plan = plan,
                    createdPresentationEntryIds = createdPresentationEntryIds,
                    phase = NavHostFailurePhase.DestinationRefresh,
                    failedEntry = null,
                    cause = IllegalStateException(
                        "Navigation plan prepares an unretained destination $entryId.",
                    ),
                )
            val preparesNewOwner = ownerStore.ownerOrNull(entryId) == null
            when (
                val preparation = sessionStore.prepare(
                    entry = entry,
                    localSnapshot = localSnapshot,
                    hostLifecycleState = hostLifecycleState,
                    content = content,
                )
            ) {
                is NavDestinationPreparation.Failed -> {
                    return preparationFailure(
                        plan = plan,
                        createdPresentationEntryIds = createdPresentationEntryIds,
                        phase = if (entryId in addedEntryIds || preparesNewOwner) {
                            NavHostFailurePhase.DestinationPreparation
                        } else {
                            NavHostFailurePhase.DestinationRefresh
                        },
                        failedEntry = entry,
                        frameReport = preparation.frameReport,
                        cause = preparation.cause,
                    )
                }

                is NavDestinationPreparation.Ready -> {
                    val candidate = preparation.candidate
                    try {
                        candidate.stage()
                        candidate.commit()
                        createdPresentationEntryIds += entryId
                    } catch (throwable: Throwable) {
                        rollbackCandidatePresentation(candidate)
                            ?.let(throwable::addSuppressed)
                        return preparationFailure(
                            plan = plan,
                            createdPresentationEntryIds = createdPresentationEntryIds,
                            phase = if (entryId in addedEntryIds || preparesNewOwner) {
                                NavHostFailurePhase.DestinationStage
                            } else {
                                NavHostFailurePhase.DestinationRefresh
                            },
                            failedEntry = entry,
                            frameReport = candidate.destinationSession.lastFrameReport,
                            cause = throwable,
                        )
                    }
                }
            }
        }

        plan.refreshPresentationEntryIds.forEach { entryId ->
            val entry = entriesById[entryId]
            val session = sessionStore.sessionOrNull(entryId)
            if (entry == null || session == null) {
                return preparationFailure(
                    plan = plan,
                    createdPresentationEntryIds = createdPresentationEntryIds,
                    phase = NavHostFailurePhase.DestinationRefresh,
                    failedEntry = entry,
                    cause = IllegalStateException(
                        "Navigation plan refreshes destination $entryId without a retained presentation.",
                    ),
                )
            }
            val report = runCatching {
                session.render(localSnapshot, content)
            }.getOrElse { throwable ->
                return preparationFailure(
                    plan = plan,
                    createdPresentationEntryIds = createdPresentationEntryIds,
                    phase = NavHostFailurePhase.DestinationRefresh,
                    failedEntry = entry,
                    frameReport = session.lastFrameReport,
                    cause = throwable,
                )
            }
            if (report?.status != RenderFrameStatus.Committed) {
                return preparationFailure(
                    plan = plan,
                    createdPresentationEntryIds = createdPresentationEntryIds,
                    phase = NavHostFailurePhase.DestinationRefresh,
                    failedEntry = entry,
                    frameReport = report,
                    cause = report?.failures?.firstOrNull()?.cause,
                )
            }
        }
        return NavPlanPreparationResult.Ready(createdPresentationEntryIds.toList())
    }

    /** Publishes presentation, interaction, destination-context, and lifecycle effects in order. */
    @MainThread
    fun publish(plan: NavExecutionPlan) {
        plan.disposeBeforeSceneEntryIds.forEach(sessionStore::disposePresentation)
        val visibleEntryIds = plan.scene.entries
            .filter { entry -> entry.visibility != NavSceneVisibility.Hidden }
            .mapTo(linkedSetOf()) { entry -> entry.entryId }
        sessionStore.present(
            layerOrder = plan.layerOrder,
            visibleEntryIds = visibleEntryIds,
            paneLayouts = plan.paneLayouts(),
        )
        sessionStore.applyInteraction(
            inputEntryIds = plan.inputEntryIds,
            accessibilityEntryIds = plan.accessibilityEntryIds,
        )
        ownerStore.execute(plan)
        plan.resultDelivery?.let(ownerStore::deliverResult)
        if (plan.pauseRenderingEntryIds.isNotEmpty()) {
            sessionStore.setRenderingActive(
                entryIds = plan.pauseRenderingEntryIds,
                active = false,
            )
        }
        plan.evictPresentationEntryIds.forEach(sessionStore::disposePresentation)
    }

    /** Applies only the owner/context/lifecycle slice after an outer host lifecycle change. */
    @MainThread
    fun publishLifecycle(plan: NavExecutionPlan) {
        ownerStore.execute(plan)
    }

    /** Executes permanent-removal cleanup after committed motion reaches a terminal state. */
    @MainThread
    fun terminalCleanup(plan: NavExecutionPlan) {
        collectFailures(plan.terminalCleanupEntryIds) { entryId ->
            sessionStore.remove(entryId)
        }
    }

    /** Reverses presentations and newly added owners created before the stack commit boundary. */
    @MainThread
    fun rollback(
        plan: NavExecutionPlan,
        createdPresentationEntryIds: List<NavEntryId>,
    ): List<Throwable> {
        val created = createdPresentationEntryIds.toSet()
        val ownerRollbackIds = plan.rollbackOwnerEntryIds.toSet()
        val failures = mutableListOf<Throwable>()
        plan.rollbackPresentationEntryIds.forEach { entryId ->
            if (entryId !in created) {
                return@forEach
            }
            runCatching {
                if (entryId in ownerRollbackIds) {
                    sessionStore.remove(entryId)
                } else {
                    sessionStore.disposePresentation(entryId)
                }
            }.exceptionOrNull()?.let(failures::add)
        }
        plan.rollbackOwnerEntryIds.forEach { entryId ->
            if (sessionStore.sessionOrNull(entryId) == null) {
                runCatching { ownerStore.remove(entryId) }
                    .exceptionOrNull()
                    ?.let(failures::add)
            }
        }
        return failures
    }

    private fun preparationFailure(
        plan: NavExecutionPlan,
        createdPresentationEntryIds: List<NavEntryId>,
        phase: NavHostFailurePhase,
        failedEntry: NavEntry?,
        frameReport: RenderFrameReport? = null,
        cause: Throwable?,
    ): NavPlanPreparationResult.Failed {
        val failures = rollback(plan, createdPresentationEntryIds)
        val effectiveCause = cause ?: failures.firstOrNull()
        if (effectiveCause != null) {
            failures.filterNot { failure -> failure === effectiveCause }
                .forEach(effectiveCause::addSuppressed)
        }
        return NavPlanPreparationResult.Failed(
            phase = phase,
            failedEntry = failedEntry,
            frameReport = frameReport,
            cause = effectiveCause,
        )
    }

    private fun rollbackCandidatePresentation(candidate: NavDestinationCandidate): Throwable? {
        return when (candidate.status) {
            NavDestinationCandidateStatus.Prepared,
            NavDestinationCandidateStatus.Staged,
            -> runCatching(candidate::rollback).exceptionOrNull()

            NavDestinationCandidateStatus.Committed -> runCatching {
                sessionStore.disposePresentation(candidate.entry.id)
            }.exceptionOrNull()

            NavDestinationCandidateStatus.RolledBack -> null
        }
    }

    private fun collectFailures(
        entryIds: List<NavEntryId>,
        operation: (NavEntryId) -> Unit,
    ) {
        val failures = mutableListOf<Throwable>()
        entryIds.forEach { entryId ->
            runCatching { operation(entryId) }
                .exceptionOrNull()
                ?.let(failures::add)
        }
        failures.firstOrNull()?.let { first ->
            failures.drop(1).forEach(first::addSuppressed)
            throw first
        }
    }
}

private fun NavExecutionPlan.paneLayouts(): Map<NavEntryId, NavPaneLayout> {
    val beforePanes = beforePaneScene.panes.associateBy { pane -> pane.entryId }
    val afterPanes = afterPaneScene.panes.associateBy { pane -> pane.entryId }
    return scene.entries
        .filter { entry -> entry.visibility != NavSceneVisibility.Hidden }
        .associate { entry ->
            val afterPane = afterPanes[entry.entryId]
            val pane = afterPane ?: checkNotNull(beforePanes[entry.entryId])
            entry.entryId to NavPaneLayout(
                role = pane.role,
                paneCount = if (afterPane == null) {
                    beforePaneScene.panes.size
                } else {
                    afterPaneScene.panes.size
                },
            )
        }
}
