package com.viewcompose.navigation.core

import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashMap
import java.util.LinkedHashSet

/** Semantic phase reduced into one [NavExecutionPlan]. */
enum class NavExecutionPhase {
    /** No visual navigation operation is active and the candidate stack is authoritative. */
    Settled,

    /** A committed stack change is retaining its before and after presentations for motion. */
    Transition,

    /** An uncommitted system-Back gesture is previewing a prospective active stack. */
    PredictivePreview,
}

/**
 * Immutable, platform-neutral instructions for one navigation host decision.
 *
 * One plan keeps stack, scene, lifecycle, presentation, interaction, transition, rollback, and
 * terminal-cleanup conclusions on the same reducer boundary. Presentation lists contain entry
 * identities only; an Android adapter decides how those identities map to Views and render
 * sessions. [lifecycle] is ordered downward-before-upward by [NavLifecyclePlanner]. Continuous
 * animation or predictive-Back progress is deliberately absent.
 *
 * All collections are copied, retain deterministic iteration order, and are read-only. Planning is
 * linear in retained entries, graph depth, current owners, and presentations. A plan owns no live
 * transaction, platform object, callback, or mutable state and may be read from any thread. This is
 * an Alpha contract: consumers should exhaustively handle the current phase values but expect hard
 * cuts rather than compatibility aliases before a stable release.
 *
 * @sample com.viewcompose.navigation.core.samples.navigationExecutionPlanSample
 */
class NavExecutionPlan internal constructor(
    /** Reducer phase represented by this plan. */
    val phase: NavExecutionPhase,
    /** Active stack before this decision. */
    val before: NavBackStackSnapshot,
    /** Active stack after this decision, prospective during predictive preview. */
    val after: NavBackStackSnapshot,
    /** Complete retained-entry delta for a committed transition, otherwise `null`. */
    val mutation: NavStackMutation?,
    /** Complete content-and-overlay projection before this decision. */
    val beforeSceneLayout: NavSceneLayout,
    /** Complete content-and-overlay projection after this decision. */
    val afterSceneLayout: NavSceneLayout,
    retainedEntries: List<NavEntry>,
    /** Exact semantic scene consumed by destination context and lifecycle execution. */
    val scene: NavScene,
    /** Final owner targets and ordered lifecycle transitions for [scene]. */
    val lifecycle: NavLifecyclePlan,
    layerOrder: List<NavEntryId>,
    preparePresentationEntryIds: List<NavEntryId>,
    refreshPresentationEntryIds: List<NavEntryId>,
    retainPresentationEntryIds: List<NavEntryId>,
    evictPresentationEntryIds: List<NavEntryId>,
    disposeBeforeSceneEntryIds: List<NavEntryId>,
    pauseRenderingEntryIds: Set<NavEntryId>,
    inputEntryIds: Set<NavEntryId>,
    accessibilityEntryIds: Set<NavEntryId>,
    /** Core command handled by system Back after the decision, or `null` to delegate outward. */
    val systemBackCommand: NavCommand?,
    /** Result delivered once after this transition commits, or `null` for result-free decisions. */
    val resultDelivery: NavResultDelivery?,
    rollbackPresentationEntryIds: List<NavEntryId>,
    rollbackOwnerEntryIds: List<NavEntryId>,
    terminalCleanupEntryIds: List<NavEntryId>,
) {
    /** Entries whose logical owners participate in [scene], in bottom-to-top host order. */
    val retainedEntries: List<NavEntry> = immutableList(retainedEntries)

    /** Bottom-to-top native presentation order. */
    val layerOrder: List<NavEntryId> = immutableList(layerOrder)

    /** Missing visible presentations to create before publishing this plan. */
    val preparePresentationEntryIds: List<NavEntryId> = immutableList(
        preparePresentationEntryIds,
    )

    /** Existing newly-visible presentations to render before publishing this plan. */
    val refreshPresentationEntryIds: List<NavEntryId> = immutableList(
        refreshPresentationEntryIds,
    )

    /** Hidden presentations retained after applying the configured bound. */
    val retainPresentationEntryIds: List<NavEntryId> = immutableList(
        retainPresentationEntryIds,
    )

    /** Hidden presentations evicted oldest-first after scene publication. */
    val evictPresentationEntryIds: List<NavEntryId> = immutableList(
        evictPresentationEntryIds,
    )

    /** Permanently removed hidden presentations disposed before lifecycle destruction. */
    val disposeBeforeSceneEntryIds: List<NavEntryId> = immutableList(
        disposeBeforeSceneEntryIds,
    )

    /** Outgoing visible presentations whose frame-driven rendering pauses during motion. */
    val pauseRenderingEntryIds: Set<NavEntryId> = immutableSet(pauseRenderingEntryIds)

    /** Destinations eligible for input and descendant focus in the published scene. */
    val inputEntryIds: Set<NavEntryId> = immutableSet(inputEntryIds)

    /** Destinations exposed to accessibility services in the published scene. */
    val accessibilityEntryIds: Set<NavEntryId> = immutableSet(accessibilityEntryIds)

    /** Newly created presentations disposed newest-first when execution fails before commit. */
    val rollbackPresentationEntryIds: List<NavEntryId> = immutableList(
        rollbackPresentationEntryIds,
    )

    /** Newly added logical owners destroyed newest-first when execution rolls back. */
    val rollbackOwnerEntryIds: List<NavEntryId> = immutableList(rollbackOwnerEntryIds)

    /** Removed entries disposed and destroyed newest-first after committed motion terminates. */
    val terminalCleanupEntryIds: List<NavEntryId> = immutableList(terminalCleanupEntryIds)

    /** Whether this plan keeps system Back inside the navigation host. */
    val ownsSystemBack: Boolean
        get() = systemBackCommand != null
}

/**
 * Reduces settled, committed-transition, and predictive-preview inputs into [NavExecutionPlan].
 *
 * The three entry points express different transaction preconditions but delegate to one pure
 * implementation. Inputs and outputs contain navigation values only. The reducer never commits or
 * rolls back [NavTransaction], mutates a lifecycle owner, creates a presentation, changes focus,
 * registers Back callbacks, or starts motion.
 *
 * `maxRetainedHiddenPresentations` is `null` for unbounded explicit retention or a non-negative
 * bound otherwise. `presentedEntryIds` uses native creation order, while
 * `hiddenPresentationRecency` is oldest-first. The reducer protects every visible or transitioning
 * presentation from eviction and returns deterministic rollback and terminal cleanup order.
 * Calls are side-effect free, allocate one immutable plan plus bounded collection copies, may run on
 * any thread, and require callers not to mutate input collections concurrently. Validation fails
 * before a plan is returned, so retrying with corrected immutable inputs has no rollback work.
 * This is an Alpha projection API and may hard-cut invalid or redundant plan fields before stable.
 *
 * @sample com.viewcompose.navigation.core.samples.navigationExecutionPlanSample
 */
object NavExecutionReducer {
    /**
     * Plans an authoritative settled scene for [stackState].
     *
     * @param currentLifecycleStates host-applied destination and graph-owner states
     * @param stackState complete committed retained-stack state
     * @param sceneLayout validated active-stack content-and-overlay projection
     * @param previousSceneLayout prior projection when a settled host changes layout
     * @param hostState outer host lifecycle cap
     * @param presentedEntryIds current presentation identities in creation order
     * @param hiddenPresentationRecency hidden presentation identities, oldest first
     * @param maxRetainedHiddenPresentations `null` for unbounded retention or a non-negative limit
     * @param systemBackCommand command handled by system Back, or `null` to delegate outward
     * @return one immutable settled execution plan; no input or owner is mutated
     * @throws IllegalArgumentException when scene, owner, or presentation identities conflict
     */
    fun settled(
        currentLifecycleStates: Map<NavEntryId, NavEntryLifecycleState>,
        stackState: NavStackSetSnapshot,
        sceneLayout: NavSceneLayout,
        previousSceneLayout: NavSceneLayout = sceneLayout,
        hostState: NavHostLifecycleState,
        presentedEntryIds: List<NavEntryId> = emptyList(),
        hiddenPresentationRecency: List<NavEntryId> = emptyList(),
        maxRetainedHiddenPresentations: Int? = 0,
        systemBackCommand: NavCommand? = null,
    ): NavExecutionPlan {
        validateSystemBackCommand(systemBackCommand, stackState.activeStack)
        return reduce(
            phase = NavExecutionPhase.Settled,
            before = stackState.activeStack,
            after = stackState.activeStack,
            mutation = null,
            committedEntries = stackState.allEntries,
            currentLifecycleStates = currentLifecycleStates,
            beforeSceneLayout = previousSceneLayout,
            afterSceneLayout = sceneLayout,
            hostState = hostState,
            presentedEntryIds = presentedEntryIds,
            hiddenPresentationRecency = hiddenPresentationRecency,
            maxRetainedHiddenPresentations = maxRetainedHiddenPresentations,
            systemBackCommand = systemBackCommand,
            resultDelivery = null,
        )
    }

    /**
     * Plans one still-prepared [transaction] through committed visual transition and cleanup.
     *
     * The transaction must remain `Prepared`; this function reads its immutable candidate state but
     * never changes status. Android prepares the returned presentation identities, commits the stack,
     * and only then publishes the returned scene and lifecycle effects.
     *
     * @param currentLifecycleStates host-applied destination and graph-owner states
     * @param transaction prepared Core transaction whose candidate state supplies the stack delta
     * @param beforeSceneLayout validated projection for the transaction's current active stack
     * @param afterSceneLayout validated projection for the transaction's candidate active stack
     * @param hostState outer host lifecycle cap
     * @param presentedEntryIds current presentation identities in creation order
     * @param hiddenPresentationRecency hidden presentation identities, oldest first
     * @param maxRetainedHiddenPresentations `null` for unbounded retention or a non-negative limit
     * @return one immutable pre-commit, transition, rollback, and terminal-cleanup plan
     * @throws IllegalStateException when [transaction] is already terminal
     * @throws IllegalArgumentException when scene, owner, mutation, or presentation identities conflict
     */
    fun transition(
        currentLifecycleStates: Map<NavEntryId, NavEntryLifecycleState>,
        transaction: NavTransaction,
        beforeSceneLayout: NavSceneLayout,
        afterSceneLayout: NavSceneLayout,
        hostState: NavHostLifecycleState,
        presentedEntryIds: List<NavEntryId> = emptyList(),
        hiddenPresentationRecency: List<NavEntryId> = emptyList(),
        maxRetainedHiddenPresentations: Int? = 0,
    ): NavExecutionPlan {
        check(transaction.status == NavTransactionStatus.Prepared) {
            "Only a prepared navigation transaction can be reduced."
        }
        return reduce(
            phase = NavExecutionPhase.Transition,
            before = transaction.before,
            after = transaction.after,
            mutation = transaction.mutation,
            committedEntries = transaction.afterState.allEntries,
            currentLifecycleStates = currentLifecycleStates,
            beforeSceneLayout = beforeSceneLayout,
            afterSceneLayout = afterSceneLayout,
            hostState = hostState,
            presentedEntryIds = presentedEntryIds,
            hiddenPresentationRecency = hiddenPresentationRecency,
            maxRetainedHiddenPresentations = maxRetainedHiddenPresentations,
            systemBackCommand = transaction.owner.systemBackCommand(transaction.afterState),
            resultDelivery = (transaction.command as? NavCommand.PopWithResult)?.let { command ->
                NavResultDelivery(
                    transactionId = transaction.transactionId,
                    targetEntryId = transaction.after.top.id,
                    payload = command.result,
                )
            },
        )
    }

    /**
     * Plans an uncommitted predictive preview from [stackState] to [prospectiveActiveStack].
     *
     * [stackState] remains the authoritative logical state. The prospective stack may select a
     * retained stack or omit the current top for a pop preview, but every previewed destination must
     * already be retained. Rollback returns to the `before` pane scene without logical cleanup.
     *
     * @param currentLifecycleStates host-applied destination and graph-owner states
     * @param stackState authoritative committed retained-stack state
     * @param prospectiveActiveStack uncommitted active stack shown at full preview progress
     * @param beforeSceneLayout validated projection for the committed active stack
     * @param afterSceneLayout validated projection for the prospective active stack
     * @param hostState outer host lifecycle cap
     * @param presentedEntryIds current presentation identities in creation order
     * @param hiddenPresentationRecency hidden presentation identities, oldest first
     * @param maxRetainedHiddenPresentations `null` for unbounded retention or a non-negative limit
     * @param systemBackCommand command whose uncommitted result is being previewed
     * @return one immutable preview and rollback-to-settled execution plan
     * @throws IllegalArgumentException when the prospective stack or presentation state conflicts
     * with retained navigation identity
     */
    fun predictivePreview(
        currentLifecycleStates: Map<NavEntryId, NavEntryLifecycleState>,
        stackState: NavStackSetSnapshot,
        prospectiveActiveStack: NavBackStackSnapshot,
        beforeSceneLayout: NavSceneLayout,
        afterSceneLayout: NavSceneLayout,
        hostState: NavHostLifecycleState,
        presentedEntryIds: List<NavEntryId> = emptyList(),
        hiddenPresentationRecency: List<NavEntryId> = emptyList(),
        maxRetainedHiddenPresentations: Int? = 0,
        systemBackCommand: NavCommand,
    ): NavExecutionPlan {
        val retainedEntryIds = stackState.allEntries.mapTo(hashSetOf(), NavEntry::id)
        require(prospectiveActiveStack.entries.all { entry -> entry.id in retainedEntryIds }) {
            "A predictive navigation preview can reference only retained destinations."
        }
        val expectedProspectiveStack = when (systemBackCommand) {
            NavCommand.Pop -> {
                require(stackState.activeStack.entries.size > 1) {
                    "A predictive pop preview requires a destination below the active top."
                }
                NavBackStackSnapshot(stackState.activeStack.entries.dropLast(1))
            }

            NavCommand.PopStackHistory -> {
                require(stackState.activeStack.entries.size == 1) {
                    "A predictive stack-history preview requires the active stack root."
                }
                val previousStackId = requireNotNull(stackState.selectionHistory.lastOrNull()) {
                    "A predictive stack-history preview requires selection history."
                }
                checkNotNull(stackState[previousStackId])
            }

            is NavCommand.PopWithResult -> throw IllegalArgumentException(
                "Predictive system Back cannot carry an application navigation result.",
            )

            is NavCommand.Push,
            is NavCommand.ReplaceTop,
            is NavCommand.Reset,
            is NavCommand.SelectStack,
            is NavCommand.OpenDeepLink,
            -> throw IllegalArgumentException(
                "Predictive navigation requires a Back command, not $systemBackCommand.",
            )
        }
        require(prospectiveActiveStack == expectedProspectiveStack) {
            "The predictive navigation stack must equal the supplied Back command's result."
        }
        return reduce(
            phase = NavExecutionPhase.PredictivePreview,
            before = stackState.activeStack,
            after = prospectiveActiveStack,
            mutation = null,
            committedEntries = stackState.allEntries,
            currentLifecycleStates = currentLifecycleStates,
            beforeSceneLayout = beforeSceneLayout,
            afterSceneLayout = afterSceneLayout,
            hostState = hostState,
            presentedEntryIds = presentedEntryIds,
            hiddenPresentationRecency = hiddenPresentationRecency,
            maxRetainedHiddenPresentations = maxRetainedHiddenPresentations,
            systemBackCommand = systemBackCommand,
            resultDelivery = null,
        )
    }

    /**
     * Re-reduces [plan] after host lifecycle, presentation inventory, retention, or Back ownership
     * changes without inventing a second scene decision.
     *
     * Stack, pane, mutation, phase, and semantic scene inputs are retained. The returned lifecycle
     * transitions start from [currentLifecycleStates], and presentation retention is recalculated
     * from the supplied current inventory. This operation is pure and does not replay frame progress.
     *
     * @param plan prior immutable decision whose stack, pane, phase, and mutation remain authoritative
     * @param currentLifecycleStates currently applied destination and graph-owner states
     * @param hostState latest outer host lifecycle cap
     * @param presentedEntryIds latest presentation identities in creation order
     * @param hiddenPresentationRecency latest hidden presentation identities, oldest first
     * @param maxRetainedHiddenPresentations `null` for unbounded retention or a non-negative limit
     * @param systemBackCommand latest system-Back command, defaulting to the prior plan
     * @return a new immutable plan; [plan] and every input collection remain unchanged
     * @throws IllegalArgumentException when owner or presentation identity conflicts with [plan]
     */
    fun reconcile(
        plan: NavExecutionPlan,
        currentLifecycleStates: Map<NavEntryId, NavEntryLifecycleState>,
        hostState: NavHostLifecycleState,
        presentedEntryIds: List<NavEntryId>,
        hiddenPresentationRecency: List<NavEntryId>,
        maxRetainedHiddenPresentations: Int?,
        systemBackCommand: NavCommand? = plan.systemBackCommand,
    ): NavExecutionPlan {
        val removedIds = plan.mutation
            ?.removed
            .orEmpty()
            .mapTo(hashSetOf(), NavEntry::id)
        val committedEntries = if (plan.phase == NavExecutionPhase.Transition) {
            plan.retainedEntries.filterNot { entry -> entry.id in removedIds }
        } else {
            plan.retainedEntries
        }
        return reduce(
            phase = plan.phase,
            before = plan.before,
            after = plan.after,
            mutation = plan.mutation,
            committedEntries = committedEntries,
            currentLifecycleStates = currentLifecycleStates,
            beforeSceneLayout = plan.beforeSceneLayout,
            afterSceneLayout = plan.afterSceneLayout,
            hostState = hostState,
            presentedEntryIds = presentedEntryIds,
            hiddenPresentationRecency = hiddenPresentationRecency,
            maxRetainedHiddenPresentations = maxRetainedHiddenPresentations,
            systemBackCommand = systemBackCommand,
            resultDelivery = plan.resultDelivery,
        )
    }

    private fun reduce(
        phase: NavExecutionPhase,
        before: NavBackStackSnapshot,
        after: NavBackStackSnapshot,
        mutation: NavStackMutation?,
        committedEntries: List<NavEntry>,
        currentLifecycleStates: Map<NavEntryId, NavEntryLifecycleState>,
        beforeSceneLayout: NavSceneLayout,
        afterSceneLayout: NavSceneLayout,
        hostState: NavHostLifecycleState,
        presentedEntryIds: List<NavEntryId>,
        hiddenPresentationRecency: List<NavEntryId>,
        maxRetainedHiddenPresentations: Int?,
        systemBackCommand: NavCommand?,
        resultDelivery: NavResultDelivery?,
    ): NavExecutionPlan {
        validateNavSceneLayout(before, beforeSceneLayout, label = "before")
        validateNavSceneLayout(after, afterSceneLayout, label = "after")
        require(presentedEntryIds.distinct().size == presentedEntryIds.size) {
            "Presented navigation identities must be unique."
        }
        require(hiddenPresentationRecency.distinct().size == hiddenPresentationRecency.size) {
            "Hidden navigation presentation recency must be unique."
        }
        require(hiddenPresentationRecency.all(presentedEntryIds::contains)) {
            "Hidden navigation presentation recency must reference presented destinations."
        }
        require(maxRetainedHiddenPresentations == null || maxRetainedHiddenPresentations >= 0) {
            "The hidden navigation presentation bound must be non-negative or null."
        }
        require((phase == NavExecutionPhase.Transition) == (mutation != null)) {
            "Only a committed transition plan may contain a stack mutation."
        }
        require(resultDelivery == null || phase == NavExecutionPhase.Transition) {
            "Only a committed transition plan may deliver a navigation result."
        }
        require(resultDelivery == null || resultDelivery.targetEntryId == after.top.id) {
            "A navigation result must target the surviving active-stack top."
        }
        validateSystemBackCommand(
            command = systemBackCommand,
            activeStack = if (phase == NavExecutionPhase.PredictivePreview) before else after,
        )

        val beforeVisible = beforeSceneLayout.visibleEntryIds
        val afterVisible = afterSceneLayout.visibleEntryIds
        val visibleEntryIds = when (phase) {
            NavExecutionPhase.Settled -> afterVisible
            NavExecutionPhase.Transition,
            NavExecutionPhase.PredictivePreview,
            -> linkedSetOf<NavEntryId>().apply {
                addAll(beforeVisible)
                addAll(afterVisible)
            }
        }
        val committedEntryIds = committedEntries.mapTo(linkedSetOf(), NavEntry::id)
        require(committedEntryIds.size == committedEntries.size) {
            "Committed navigation entries must have unique identities."
        }
        val exitingEntryIds = if (phase == NavExecutionPhase.Transition) {
            checkNotNull(mutation).removed
                .mapTo(linkedSetOf(), NavEntry::id)
                .intersect(visibleEntryIds)
        } else {
            emptySet()
        }
        val retainedEntries = buildList {
            addAll(committedEntries)
            mutation?.removed.orEmpty().forEach { removedEntry ->
                if (removedEntry.id !in committedEntryIds && removedEntry.id in exitingEntryIds) {
                    add(removedEntry)
                }
            }
        }
        val retainedEntryIds = retainedEntries.mapTo(linkedSetOf(), NavEntry::id)
        require(visibleEntryIds.all(retainedEntryIds::contains)) {
            "Every visible navigation destination must participate in the execution plan."
        }

        val paneRoles = LinkedHashMap<NavEntryId, NavPaneRole>().apply {
            beforeSceneLayout.contentPaneScene.panes.forEach { pane ->
                put(pane.entryId, pane.role)
            }
            afterSceneLayout.contentPaneScene.panes.forEach { pane ->
                put(pane.entryId, pane.role)
            }
        }
        val overlayEntryIds = when (phase) {
            NavExecutionPhase.Settled -> LinkedHashSet(afterSceneLayout.overlayEntryIds)
            NavExecutionPhase.Transition,
            NavExecutionPhase.PredictivePreview,
            -> LinkedHashSet<NavEntryId>().apply {
                addAll(beforeSceneLayout.overlayEntryIds)
                addAll(afterSceneLayout.overlayEntryIds)
            }
        }
        val orderedVisibleOverlayEntryIds = LinkedHashSet<NavEntryId>().apply {
            beforeSceneLayout.overlayEntryIds.forEach { entryId ->
                if (entryId in visibleEntryIds) add(entryId)
            }
            afterSceneLayout.overlayEntryIds.forEach { entryId ->
                if (entryId in visibleEntryIds) add(entryId)
            }
        }
        val topVisibleOverlayEntryId = orderedVisibleOverlayEntryIds.lastOrNull()
        val transitionPhases = when (phase) {
            NavExecutionPhase.Settled -> emptyMap()
            NavExecutionPhase.PredictivePreview -> visibleEntryIds.associateWith {
                NavSceneTransitionPhase.PredictivePreview
            }
            NavExecutionPhase.Transition -> buildMap {
                (beforeVisible - afterVisible).forEach { entryId ->
                    put(entryId, NavSceneTransitionPhase.Exiting)
                }
                (afterVisible - beforeVisible).forEach { entryId ->
                    put(entryId, NavSceneTransitionPhase.Entering)
                }
            }
        }
        val interactiveEntryIds = if (phase == NavExecutionPhase.Settled) {
            afterSceneLayout.interactiveEntryIds
        } else {
            emptySet()
        }
        val orderedSceneEntries = buildList {
            retainedEntries.filterTo(this) { entry -> entry.id !in overlayEntryIds }
            orderedVisibleOverlayEntryIds.forEach { entryId ->
                add(retainedEntries.first { entry -> entry.id == entryId })
            }
        }
        val scene = NavScene(
            orderedSceneEntries.map { entry ->
                val visible = entry.id in visibleEntryIds
                val layerRole = if (entry.id in overlayEntryIds) {
                    NavSceneLayerRole.Overlay
                } else {
                    NavSceneLayerRole.Content
                }
                NavSceneEntry(
                    entryId = entry.id,
                    presence = if (entry.id in exitingEntryIds) {
                        NavEntryPresence.Exiting
                    } else {
                        NavEntryPresence.Retained
                    },
                    visibility = when {
                        !visible -> NavSceneVisibility.Hidden
                        topVisibleOverlayEntryId != null &&
                            entry.id != topVisibleOverlayEntryId -> NavSceneVisibility.Covered
                        else -> NavSceneVisibility.Visible
                    },
                    interaction = if (entry.id in interactiveEntryIds) {
                        NavSceneInteraction.Interactive
                    } else {
                        NavSceneInteraction.NonInteractive
                    },
                    transitionPhase = transitionPhases[entry.id]
                        ?: NavSceneTransitionPhase.Settled,
                    paneRole = if (visible && layerRole == NavSceneLayerRole.Content) {
                        checkNotNull(paneRoles[entry.id])
                    } else {
                        null
                    },
                    layerRole = layerRole,
                )
            },
        )
        val lifecycle = NavLifecyclePlanner.plan(
            currentStates = currentLifecycleStates,
            entries = retainedEntries,
            scene = scene,
            hostState = hostState,
        )

        val layerOrder = orderedSceneEntries.map(NavEntry::id)
        val disposeBeforeSceneEntryIds = mutation
            ?.removed
            .orEmpty()
            .map(NavEntry::id)
            .filterNot(retainedEntryIds::contains)
            .asReversed()
        val candidatePresentedEntryIds = presentedEntryIds
            .filterNot(disposeBeforeSceneEntryIds::contains)
            .toMutableList()
        val preparePresentationEntryIds = layerOrder.filter { entryId ->
            entryId in visibleEntryIds && entryId !in candidatePresentedEntryIds
        }
        candidatePresentedEntryIds += preparePresentationEntryIds
        val refreshPresentationEntryIds = layerOrder.filter { entryId ->
            entryId in (afterVisible - beforeVisible) &&
                entryId in presentedEntryIds &&
                entryId !in preparePresentationEntryIds
        }
        val updatedHiddenRecency = hiddenPresentationRecency
            .filterTo(mutableListOf()) { entryId ->
                entryId in candidatePresentedEntryIds && entryId !in visibleEntryIds
            }
        candidatePresentedEntryIds.forEach { entryId ->
            if (entryId !in visibleEntryIds && entryId !in updatedHiddenRecency) {
                updatedHiddenRecency += entryId
            }
        }
        val evictionCount = maxRetainedHiddenPresentations
            ?.let { maximum -> (updatedHiddenRecency.size - maximum).coerceAtLeast(0) }
            ?: 0
        val evictPresentationEntryIds = updatedHiddenRecency.take(evictionCount)
        val retainPresentationEntryIds = updatedHiddenRecency.drop(evictionCount)
        val pauseRenderingEntryIds = if (phase == NavExecutionPhase.Transition) {
            beforeVisible - afterVisible
        } else {
            emptySet()
        }
        val accessibilityEntryIds = when (phase) {
            NavExecutionPhase.Settled,
            NavExecutionPhase.Transition,
            -> afterSceneLayout.interactiveEntryIds
            NavExecutionPhase.PredictivePreview -> beforeSceneLayout.interactiveEntryIds
        }
        val rollbackOwnerEntryIds = mutation
            ?.added
            .orEmpty()
            .map(NavEntry::id)
            .asReversed()
        val terminalCleanupEntryIds = mutation
            ?.removed
            .orEmpty()
            .map(NavEntry::id)
            .asReversed()
        return NavExecutionPlan(
            phase = phase,
            before = before,
            after = after,
            mutation = mutation,
            beforeSceneLayout = beforeSceneLayout,
            afterSceneLayout = afterSceneLayout,
            retainedEntries = retainedEntries,
            scene = scene,
            lifecycle = lifecycle,
            layerOrder = layerOrder,
            preparePresentationEntryIds = preparePresentationEntryIds,
            refreshPresentationEntryIds = refreshPresentationEntryIds,
            retainPresentationEntryIds = retainPresentationEntryIds,
            evictPresentationEntryIds = evictPresentationEntryIds,
            disposeBeforeSceneEntryIds = disposeBeforeSceneEntryIds,
            pauseRenderingEntryIds = pauseRenderingEntryIds,
            inputEntryIds = interactiveEntryIds,
            accessibilityEntryIds = accessibilityEntryIds,
            systemBackCommand = systemBackCommand,
            resultDelivery = resultDelivery,
            rollbackPresentationEntryIds = preparePresentationEntryIds.asReversed(),
            rollbackOwnerEntryIds = rollbackOwnerEntryIds,
            terminalCleanupEntryIds = terminalCleanupEntryIds,
        )
    }

    private fun validateSystemBackCommand(
        command: NavCommand?,
        activeStack: NavBackStackSnapshot,
    ) {
        when (command) {
            null -> Unit
            NavCommand.Pop -> require(activeStack.entries.size > 1) {
                "System Back cannot pop an active stack root."
            }
            NavCommand.PopStackHistory -> require(activeStack.entries.size == 1) {
                "System Back can select stack history only from an active stack root."
            }
            is NavCommand.PopWithResult -> throw IllegalArgumentException(
                "System Back cannot carry an application navigation result.",
            )
            is NavCommand.Push,
            is NavCommand.ReplaceTop,
            is NavCommand.Reset,
            is NavCommand.SelectStack,
            is NavCommand.OpenDeepLink,
            -> throw IllegalArgumentException(
                "System Back requires Pop or PopStackHistory, not $command.",
            )
        }
    }
}

private fun <T> immutableList(values: List<T>): List<T> {
    return Collections.unmodifiableList(ArrayList(values))
}

private fun <T> immutableSet(values: Set<T>): Set<T> {
    return Collections.unmodifiableSet(LinkedHashSet(values))
}
