package com.viewcompose.runtime.composition

import com.viewcompose.runtime.Snapshot
import com.viewcompose.runtime.State
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.runtime.observation.RuntimeObservation

/**
 * Coordinates transactional, group-based incremental composition without compiler-generated flags.
 *
 * [runGroup] builds a positional scope tree in [slotTable]. A committed group result is reused when
 * its explicit inputs are equal and none of its observed state has invalidated the scope. State
 * invalidations are coalesced in [invalidationQueue]. Composition runs in a pinned read [Snapshot]
 * and [prepareRoot] lets a host commit or roll back runtime changes together with another mutable
 * tree.
 *
 * Composer instances are thread-confined. Calls that compose, commit, abort, run effects, or dispose
 * an instance MUST be serialized by its owner.
 *
 * @sample com.viewcompose.runtime.samples.composerLiteSample
 * @param slotTable scope/slot tree owned by this composer
 * @param invalidationQueue queue that coalesces state-driven scope invalidations
 * @param warningLogger optional sink for structural-drift warnings, emitted once per drift location
 * @param onInvalidated optional callback invoked when a clean scope first becomes dirty; repeated
 * invalidations before the next composition are coalesced
 * @param localSnapshotInspector optional formatter for opaque local snapshots included in diagnostics
 * @param sourceCallSiteCollector optional collector invoked only when a new scope is created
 * @param synchronousEffectWarningThresholdNanos optional non-negative duration after which a
 * synchronous remember-lifecycle or side-effect callback reports through [warningLogger]
 * @param effectFrameIdProvider optional host callback returning the current frame identifier for
 * effect failure and slow-callback diagnostics; it is invoked only while dispatching a callback
 */
class ComposerLite(
    private val slotTable: SlotTable = SlotTable(),
    private val invalidationQueue: InvalidationQueue = InvalidationQueue(),
    private val warningLogger: ((String) -> Unit)? = null,
    private val onInvalidated: (() -> Unit)? = null,
    private val localSnapshotInspector: ((Any?) -> List<CompositionLocalDiagnostic>)? = null,
    private val sourceCallSiteCollector: (() -> List<CompositionSourceCallSite>)? = null,
    private val synchronousEffectWarningThresholdNanos: Long? = null,
    private val effectFrameIdProvider: (() -> Long?)? = null,
) {
    private val keyStack = mutableListOf<Any?>()
    private val warningKeys = HashSet<String>()
    private val pendingSideEffects = mutableListOf<PendingSideEffect>()
    private val pendingRememberActivations = LinkedHashSet<RecomposeScope.RememberLifecycle>()
    private var currentScope: RecomposeScope = slotTable.root
    private var composing: Boolean = false
    private var activeAttempt: CompositionAttempt? = null
    private var explicitRootRequestPending: Boolean = false
    private var dispatchingCallbacks: Boolean = false
    private var disposed: Boolean = false
    @Volatile
    private var ownerThread: Thread? = null

    init {
        require(
            synchronousEffectWarningThresholdNanos == null ||
                synchronousEffectWarningThresholdNanos >= 0L,
        ) {
            "synchronousEffectWarningThresholdNanos must be non-negative when specified."
        }
    }

    /**
     * Returns whether state-driven scope invalidations are waiting in the queue.
     *
     * An explicit [requestRootRecompose] is tracked separately and does not make this property
     * return `true` unless a state-driven invalidation is also queued.
     *
     * @return `true` when [drainInvalidations] can drain at least one scope
     */
    fun hasPendingInvalidations(): Boolean = invalidationQueue.isNotEmpty()

    /**
     * Removes and returns queued state invalidations after ancestor compaction.
     *
     * Draining does not make scopes clean; the next root composition consumes their dirty state.
     *
     * @return invalidated scopes in effective insertion order, excluding covered descendants
     */
    fun drainInvalidations(): List<RecomposeScope> {
        requireOwnerThread("drainInvalidations")
        return invalidationQueue.drainCompacted()
    }

    /**
     * Marks the root dirty for the next composition attempt.
     *
     * Hosts use this for explicit refreshes or environment changes that are not represented by an
     * observed [State]. The request is preserved when a prepared composition aborts. This method
     * does not invoke the [onInvalidated] constructor callback because the caller already owns the
     * scheduling decision.
     */
    fun requestRootRecompose() {
        requireOwnerThread("requestRootRecompose")
        check(!disposed) {
            "ComposerLite is disposed and cannot accept a root recomposition request."
        }
        explicitRootRequestPending = true
        slotTable.root.markDirty()
    }

    /**
     * Composes [block], commits the runtime transaction, and returns its candidate value.
     *
     * This is equivalent to [prepareRoot] followed by [PreparedComposition.commit]. Committed
     * one-shot effects remain pending until [commitSideEffects] is called.
     *
     * @param T type of value produced by the root composition
     * @param block root content executed in a consistent read snapshot
     * @return the committed value produced by [block]
     * @throws IllegalStateException when the composer is disposed, the caller is not its owner
     * thread, composition is re-entrant, or another prepared composition remains active
     */
    fun <T> composeRoot(block: () -> T): T {
        val prepared = prepareRoot(block = block)
        prepared.commit()
        return prepared.value
    }

    /**
     * Composes a candidate root result without finalizing scope, observation, or effect changes.
     *
     * The block runs inside a pinned read snapshot. A host that applies the result to another mutable
     * tree calls [PreparedComposition.commit] only after that apply succeeds, or
     * [PreparedComposition.abort] when it fails. A thrown block automatically rolls back before the
     * exception is rethrown. Only one prepared composition may be active for this composer.
     *
     * @param T type of candidate value produced by the root composition
     * @param collectDiagnostics whether to record bounded scope decisions for this attempt
     * @param block root content used to prepare the candidate transaction
     * @return an owned prepared composition that MUST be committed or aborted
     * @throws IllegalStateException when the composer is disposed, the caller is not its owner
     * thread, composition is re-entrant, or another prepared composition remains active
     */
    fun <T> prepareRoot(
        collectDiagnostics: Boolean = false,
        block: () -> T,
    ): PreparedComposition<T> {
        requireOwnerThread("prepareRoot")
        check(!disposed) {
            "ComposerLite is disposed and cannot compose again."
        }
        check(!dispatchingCallbacks) {
            "Re-entrant composition from a commit or effect callback is not supported."
        }
        if (composing) {
            error("Re-entrant composeRoot() is not supported.")
        }
        check(activeAttempt == null) {
            "A prepared composition must be committed or aborted before composing again."
        }
        composing = true
        val drainedInvalidations = drainInvalidations()
        val rootWasComposed = slotTable.root.composed
        val attempt = CompositionAttempt(
            drainedInvalidations = drainedInvalidations,
            collectDiagnostics = collectDiagnostics,
            explicitRootRequest = explicitRootRequestPending,
        )
        explicitRootRequestPending = false
        if (collectDiagnostics) {
            drainedInvalidations.forEach { scope ->
                attempt.addReason(scope, RecompositionReason.StateInvalidation)
                var ancestor = scope.parent
                while (ancestor != null) {
                    attempt.addReason(ancestor, RecompositionReason.AncestorInvalidation)
                    ancestor = ancestor.parent
                }
            }
            when {
                !rootWasComposed -> {
                    attempt.addReason(slotTable.root, RecompositionReason.InitialComposition)
                }

                attempt.explicitRootRequest -> {
                    attempt.addReason(slotTable.root, RecompositionReason.ExplicitRequest)
                }
            }
        }
        activeAttempt = attempt
        val root = slotTable.root
        val previous = currentScope
        currentScope = root
        val snapshot = Snapshot.takeSnapshot()
        return try {
            val result = snapshot.enter {
                composeScope(
                    scope = root,
                    block = { block() },
                )
            }
            PreparedComposition(
                value = result,
                diagnostics = buildDiagnostics(attempt),
                isCompleted = { attempt.completed },
                onCommit = { commitAttempt(attempt) },
                onAbort = { abortAttempt(attempt) },
            )
        } catch (error: Throwable) {
            try {
                abortAttempt(attempt)
            } catch (abortError: Throwable) {
                error.addSuppressed(abortError)
            }
            throw error
        } finally {
            snapshot.dispose()
            currentScope = previous
            composing = false
        }
    }

    /**
     * Runs or reuses one positional group in the current composition scope.
     *
     * [signature] and the active [withKeys] stack identify the group at its sibling position. Keyed
     * groups may move among siblings without losing their scope identity. Their effective identity
     * MUST be unique among siblings in one composition pass; duplicates fail the attempt before any
     * scope can alias another logical item. An unkeyed mismatch replaces that group and all following
     * siblings at the drift point. Equal [inputs] permit reuse while the scope is clean; changed
     * inputs mark the scope dirty. When the body does run, [reuseResult] may retain the previous
     * result identity after comparing it with the newly calculated value.
     *
     * @param T type of value cached for the group
     * @param signature stable structural identity at the current sibling position
     * @param inputs value compared with `equals` to detect explicit input changes
     * @param reuseResult optional predicate returning `true` to retain the previous result instance
     * @param block group body, receiving the opaque scope owned by this composer
     * @return the cached, newly calculated, or explicitly reused group result
     * @sample com.viewcompose.runtime.samples.keyedGroupMovementSample
     * @throws IllegalArgumentException when the effective keyed identity duplicates an earlier
     * sibling in the same pass
     * @throws IllegalStateException when called outside the actively executing composition block or
     * from a thread other than the composer's owner
     */
    fun <T> runGroup(
        signature: Any,
        inputs: Any? = RecomposeScope.NoInputs,
        reuseResult: ((previous: T, next: T) -> Boolean)? = null,
        block: (RecomposeScope) -> T,
    ): T {
        val attempt = currentAttempt()
        val parent = currentScope
        val index = parent.childCursor++
        val normalizedSignature = newGroupSignature(signature)
        if (keyStack.isNotEmpty()) {
            require(parent.children.take(index).none { it.signature == normalizedSignature }) {
                "Duplicate effective keyed group identity among siblings. " +
                    "Each withKeys namespace and group signature pair must be unique."
            }
        }
        val existing = parent.children.getOrNull(index)
        val movableIndex = if (existing != null && keyStack.isNotEmpty()) {
            (index + 1 until parent.children.size).firstOrNull { candidateIndex ->
                groupSignatureMatches(
                    existing = parent.children[candidateIndex].signature,
                    signature = signature,
                )
            }
        } else {
            null
        }
        val scope = when {
            existing == null -> {
                RecomposeScope(
                    signature = normalizedSignature,
                    parent = parent,
                    saveablePath = childSaveablePath(
                        parent = parent,
                        index = index,
                        signature = normalizedSignature,
                    ),
                    sourceCallSites = sourceCallSiteCollector?.invoke().orEmpty(),
                ).also { scope ->
                    parent.children += scope
                    attempt.newScopes += scope
                    attempt.addReason(
                        scope,
                        if (parent.composed) {
                            RecompositionReason.StructureChanged
                        } else {
                            RecompositionReason.InitialComposition
                        },
                    )
                }
            }

            groupSignatureMatches(
                existing = existing.signature,
                signature = signature,
            ) -> existing

            movableIndex != null -> {
                checkpointScope(parent)
                parent.children.removeAt(movableIndex).also { moved ->
                    parent.children.add(index, moved)
                    attempt.addReason(moved, RecompositionReason.StructureChanged)
                }
            }

            keyStack.isNotEmpty() -> {
                RecomposeScope(
                    signature = normalizedSignature,
                    parent = parent,
                    saveablePath = childSaveablePath(
                        parent = parent,
                        index = index,
                        signature = normalizedSignature,
                    ),
                    sourceCallSites = sourceCallSiteCollector?.invoke().orEmpty(),
                ).also { inserted ->
                    checkpointScope(parent)
                    parent.children.add(index, inserted)
                    attempt.newScopes += inserted
                    attempt.addReason(inserted, RecompositionReason.StructureChanged)
                }
            }

            else -> {
                warnStructureDriftOnce(
                    key = "drift|${parent.signature}|$index",
                    message = "Composition structure drift at group index=$index; fallback to nearest ancestor subtree recomposition.",
                )
                while (parent.children.size > index) {
                    parent.children.removeAt(parent.children.lastIndex)
                }
                RecomposeScope(
                    signature = normalizedSignature,
                    parent = parent,
                    saveablePath = childSaveablePath(
                        parent = parent,
                        index = index,
                        signature = normalizedSignature,
                    ),
                    sourceCallSites = sourceCallSiteCollector?.invoke().orEmpty(),
                ).also { scope ->
                    parent.children += scope
                    attempt.newScopes += scope
                    attempt.addReason(scope, RecompositionReason.StructureChanged)
                }
            }
        }
        if (scope.latestInputs != inputs) {
            checkpointScope(scope)
            if (scope.composed) {
                attempt.addReason(scope, RecompositionReason.InputsChanged)
            }
            scope.latestInputs = inputs
            scope.markDirty()
        }
        val previous = currentScope
        currentScope = scope
        return try {
            composeScope(
                scope = scope,
                reuseResult = reuseResult,
                block = { block(scope) },
            )
        } finally {
            currentScope = previous
        }
    }

    /**
     * Returns a value retained in the next positional remember slot of the current scope.
     *
     * [calculation] runs when no slot exists or when the combined [withKeys] and [keys] list changes
     * by structural equality. Slot changes are transactional: abort restores the previously
     * committed value. Values implementing [RememberObserver] receive lifecycle callbacks when the
     * prepared composition commits or aborts.
     *
     * @param T type of value retained by the slot
     * @param keys local keys appended after the active [withKeys] key stack
     * @param calculation factory invoked when the slot cannot be reused
     * @return the committed retained value or the current attempt's candidate value
     * @throws IllegalStateException when called outside the actively executing composition block
     * or from a thread other than the composer's owner
     */
    fun <T> remember(
        keys: List<Any?>,
        calculation: () -> T,
    ): T {
        currentAttempt()
        val scope = currentScope
        val scopedKeys = keyStack + keys
        val index = scope.rememberCursor++
        val existing = scope.rememberSlots.getOrNull(index)
        if (existing != null && existing.keys == scopedKeys) {
            @Suppress("UNCHECKED_CAST")
            return existing.value as T
        }
        val value = calculation()
        val slot = RecomposeScope.RememberSlot(
            keys = scopedKeys,
            value = value,
            lifecycle = RecomposeScope.RememberLifecycle(
                observer = value as? RememberObserver,
                diagnostic = EffectDiagnostic(
                    kind = value.rememberDiagnosticKind(),
                    scopePath = scope.saveablePath,
                    slot = index,
                    keySummary = scopedKeys.toEffectKeySummary(),
                ),
                warningLogger = warningLogger,
                warningThresholdNanos = synchronousEffectWarningThresholdNanos,
                frameIdProvider = effectFrameIdProvider,
            ),
        )
        if (existing != null) {
            scope.rememberSlots[index] = slot
        } else {
            scope.rememberSlots += slot
        }
        return value
    }

    /**
     * Returns one positional state holder whose candidate value publishes only on commit.
     *
     * Reads made by the current composition thread see [newValue] immediately. Readers outside that
     * candidate, including previously committed effects, continue to see the committed value until
     * [PreparedComposition.commit]. Abort discards the candidate value. Publication happens before
     * outgoing or incoming [RememberObserver] callbacks.
     *
     * This low-level method exists so higher composition integrations can implement an updated-state
     * API without leaking candidate writes through a global mutable snapshot.
     *
     * @param T type of value exposed by the holder
     * @param newValue value visible to the current candidate and published on commit
     * @return the stable positional state holder
     * @throws IllegalStateException when called outside an active composition attempt
     */
    fun <T> rememberUpdatedState(newValue: T): State<T> {
        val holder = remember(keys = emptyList()) {
            CommitAwareState(
                composer = this,
                initialValue = newValue,
            )
        }
        val attempt = currentAttempt()
        holder.prepare(
            attempt = attempt,
            value = newValue,
        )
        attempt.pendingStateUpdates += holder
        return holder
    }

    /**
     * Registers [effect] to run once after the current composition commits.
     *
     * The operation is discarded if the prepared composition aborts and runs when the host next
     * calls [commitSideEffects].
     *
     * @param effect one-shot operation to execute after committed remember lifecycle changes
     * @throws IllegalStateException when called outside the actively executing composition block
     * or from a thread other than the composer's owner
     */
    fun sideEffect(effect: () -> Unit) {
        val attempt = currentAttempt()
        val scope = currentScope
        val slot = scope.sideEffectCursor++
        attempt.pendingSideEffects += PendingSideEffect(
            effect = effect,
            diagnostic = EffectDiagnostic(
                kind = "SideEffect",
                scopePath = scope.saveablePath,
                slot = slot,
                keySummary = keyStack.toEffectKeySummary(),
            ),
            warningLogger = warningLogger,
            warningThresholdNanos = synchronousEffectWarningThresholdNanos,
            frameIdProvider = effectFrameIdProvider,
        )
    }

    /**
     * Returns a deterministic key for the next positional saveable slot in the current scope.
     *
     * The key combines the structural group path, local slot position, and a hash of active
     * [withKeys] values. Call order is therefore part of the saveable-state contract. Custom key
     * objects MUST keep equal values and stable, collision-free `hashCode` results across host
     * recreation. A collision between simultaneously composed unequal keyed siblings fails before
     * either can register the same saveable provider identity.
     *
     * @return an opaque key stable for the same structure, slot position, and explicit keys
     * @throws IllegalStateException when called outside the actively executing composition block
     * or from a thread other than the composer's owner
     */
    fun nextSaveableKey(): String {
        currentAttempt()
        validateSaveablePathIdentity()
        val slot = currentScope.saveableCursor++
        val explicitKeyHash = stableHash(keyStack)
        return "auto:${currentScope.saveablePath}:$slot:${explicitKeyHash.toUInt().toString(16)}"
    }

    /**
     * Returns a deterministic registry key for a caller-supplied saveable-state identity.
     *
     * Explicit identities remain unique within the current restart-group and [withKeys] namespace,
     * so equal names may safely appear in distinct keyed siblings. The root namespace retains the
     * historical `user:<key>` form. Callers must still keep [explicitKey] unique inside one scope.
     *
     * @sample com.viewcompose.runtime.samples.scopedExplicitSaveableKeySample
     * @param explicitKey non-blank application identity validated by the higher-level saveable API
     * @return opaque registry key stable while the logical keyed scope remains active
     * @throws IllegalStateException when called outside an active composition attempt
     * @throws IllegalArgumentException when unequal keyed siblings produce the same saveable path
     */
    fun scopedExplicitSaveableKey(explicitKey: String): String {
        currentAttempt()
        validateSaveablePathIdentity()
        val scopePath = currentScope.saveablePath
        if (scopePath == "root" && keyStack.isEmpty()) {
            return "user:$explicitKey"
        }
        val explicitKeyHash = stableHash(keyStack).toUInt().toString(16)
        return "user:$scopePath:$explicitKeyHash:$explicitKey"
    }

    /**
     * Runs all effect operations queued by committed compositions.
     *
     * Remember lifecycle callbacks have already completed during [PreparedComposition.commit].
     * Every operation is attempted even after a failure. The queue is cleared before execution, so
     * failed operations are not retried; the first failure is rethrown with later failures
     * suppressed.
     *
     * @throws IllegalStateException when called re-entrantly or from a thread other than the
     * composer's owner
     */
    fun commitSideEffects() {
        requireOwnerThread("commitSideEffects")
        check(!dispatchingCallbacks) {
            "Re-entrant side-effect dispatch is not supported."
        }
        if (pendingSideEffects.isEmpty()) return
        val sideEffectOperations = pendingSideEffects.toList()
        pendingSideEffects.clear()
        val failures = mutableListOf<Throwable>()
        dispatchingCallbacks = true
        try {
            sideEffectOperations.forEach { operation ->
                try {
                    operation.run()
                } catch (error: Throwable) {
                    failures += error
                }
            }
        } finally {
            dispatchingCallbacks = false
        }
        failures.firstOrNull()?.let { first ->
            failures.drop(1).forEach(first::addSuppressed)
            throw first
        }
    }

    /**
     * Runs [block] with [keys] appended to the current positional-key namespace.
     *
     * The keys affect group signatures, [remember], and [nextSaveableKey]. The
     * previous key stack is restored when [block] returns or throws. An empty list executes [block]
     * without changing the namespace.
     *
     * @param T type of value returned by [block]
     * @param keys stable values that distinguish this nested composition path
     * @param block synchronous work executed with the extended key namespace
     * @return the value returned by [block]
     * @throws IllegalStateException when called outside the actively executing composition block
     * or from a thread other than the composer's owner
     */
    fun <T> withKeys(
        keys: List<Any?>,
        block: () -> T,
    ): T {
        currentAttempt()
        if (keys.isEmpty()) {
            return block()
        }
        val start = keyStack.size
        keyStack.addAll(keys)
        return try {
            block()
        } finally {
            while (keyStack.size > start) {
                keyStack.removeAt(keyStack.lastIndex)
            }
        }
    }

    /**
     * Disposes every scope, observation, remembered value, active effect, and pending operation.
     *
     * An active prepared composition is aborted first. Pending effects that never started are
     * discarded. Cleanup continues after failures and rethrows the first failure with later failures
     * suppressed. Disposal is terminal; the instance MUST NOT be composed again.
     *
     * @throws IllegalStateException when called re-entrantly or from a thread other than the
     * composer's owner
     */
    fun dispose() {
        requireOwnerThread("dispose")
        if (disposed) return
        check(!dispatchingCallbacks) {
            "ComposerLite cannot be disposed from one of its commit or effect callbacks."
        }
        disposed = true
        val failures = mutableListOf<Throwable>()
        activeAttempt?.let { attempt ->
            try {
                abortAttempt(attempt)
            } catch (error: Throwable) {
                failures += error
            }
        }
        pendingSideEffects.clear()
        invalidationQueue.clear()
        dispatchingCallbacks = true
        try {
            try {
                slotTable.dispose()
            } catch (error: Throwable) {
                failures += error
            }
        } finally {
            dispatchingCallbacks = false
        }
        pendingRememberActivations.clear()
        failures.firstOrNull()?.let { first ->
            failures.drop(1).forEach(first::addSuppressed)
            throw first
        }
    }

    private fun <T> composeScope(
        scope: RecomposeScope,
        reuseResult: ((previous: T, next: T) -> Boolean)? = null,
        block: () -> T,
    ): T {
        val hasCached = scope.cachedResult !== RecomposeScope.Unset
        if (!scope.dirty && scope.composed && hasCached) {
            if (currentAttempt().collectDiagnostics) {
                currentAttempt().skippedScopes += scope
            }
            @Suppress("UNCHECKED_CAST")
            return scope.cachedResult as T
        }
        if (currentAttempt().collectDiagnostics) {
            currentAttempt().recomposedScopes += scope
            if (!scope.composed) {
                currentAttempt().addReason(scope, RecompositionReason.InitialComposition)
            }
        }
        checkpointScope(scope)
        val invalidationVersion = scope.currentInvalidationVersion()
        scope.beginCompose()
        return try {
            val (result, nextObservation) = RuntimeObservation.observeReads(
                onInvalidated = {
                    if (scope.disposed) return@observeReads
                    val newlyInvalidated = scope.markDirtyWithAncestors()
                    invalidationQueue.enqueue(scope)
                    if (newlyInvalidated) {
                        onInvalidated?.invoke()
                    }
                },
            ) {
                block()
            }
            val previousResult = scope.cachedResult
            val reusableResult = if (
                previousResult !== RecomposeScope.Unset &&
                reuseResult != null
            ) {
                @Suppress("UNCHECKED_CAST")
                reuseResult(previousResult as T, result)
            } else {
                false
            }
            val finalResult = if (reusableResult) {
                @Suppress("UNCHECKED_CAST")
                previousResult as T
            } else {
                result
            }
            scope.observation = nextObservation
            scope.cachedResult = finalResult
            scope.clearDirtyIfUnchanged(invalidationVersion)
            scope.composed = true
            scope.trimAfterCompose()
            finalResult
        } finally {
            scope.endCompose()
        }
    }

    private fun checkpointScope(scope: RecomposeScope) {
        val attempt = currentAttempt()
        if (scope in attempt.newScopes) return
        attempt.checkpoints.getOrPut(scope, scope::checkpoint)
    }

    private fun currentAttempt(): CompositionAttempt {
        requireOwnerThread("composition operation")
        check(composing) {
            "Composition operation requires an actively executing composition block."
        }
        return checkNotNull(activeAttempt) {
            "Composition operation requires an active composition attempt."
        }
    }

    private fun commitAttempt(attempt: CompositionAttempt) {
        requireOwnerThread("commit")
        check(!dispatchingCallbacks) {
            "Re-entrant prepared-composition commit is not supported."
        }
        dispatchingCallbacks = true
        try {
            commitAttemptWhileDispatching(attempt)
        } finally {
            dispatchingCallbacks = false
        }
    }

    private fun commitAttemptWhileDispatching(attempt: CompositionAttempt) {
        check(activeAttempt === attempt) {
            "Prepared composition is no longer active."
        }
        activeAttempt = null
        attempt.completed = true

        pendingSideEffects += attempt.pendingSideEffects
        val attachedScopes = attempt.checkpoints.keys
            .filterTo(LinkedHashSet(), ::isAttachedToRoot)
        val attachedNewScopes = attempt.newScopes
            .filterTo(LinkedHashSet(), ::isAttachedToRoot)
        val removedScopeCandidates = LinkedHashSet<RecomposeScope>()
        attempt.checkpoints.forEach { (scope, checkpoint) ->
            checkpoint.children.forEach { previousChild ->
                if (previousChild !in scope.children) {
                    removedScopeCandidates += previousChild
                }
            }
        }
        val removedScopeRoots = removedScopeCandidates.filter { scope ->
            var ancestor = scope.parent
            var nestedBelowRemovedScope = false
            while (ancestor != null) {
                if (ancestor in removedScopeCandidates) {
                    nestedBelowRemovedScope = true
                    break
                }
                ancestor = ancestor.parent
            }
            !nestedBelowRemovedScope
        }
        val abandonedNewScopes = attempt.newScopes.filterTo(LinkedHashSet()) { scope ->
            scope !in attachedNewScopes
        }
        val abandonedNewRoots = abandonedNewScopes.filter { scope ->
            scope.parent !in abandonedNewScopes
        }

        var firstFailure: Throwable? = null
        fun cleanup(block: () -> Unit) {
            try {
                block()
            } catch (error: Throwable) {
                if (firstFailure == null) {
                    firstFailure = error
                } else {
                    firstFailure?.addSuppressed(error)
                }
            }
        }

        fun activate(lifecycle: RecomposeScope.RememberLifecycle) {
            cleanup(lifecycle::activate)
            if (lifecycle.isPending) {
                pendingRememberActivations += lifecycle
            } else {
                pendingRememberActivations -= lifecycle
            }
        }

        attempt.pendingStateUpdates.forEach { update ->
            cleanup {
                update.commit(attempt)
            }
        }

        // Dispose replaced committed values before any candidate value becomes active.
        attempt.checkpoints.forEach { (scope, checkpoint) ->
            if (scope.observation !== checkpoint.observation) {
                checkpoint.observation?.let { observation ->
                    cleanup(observation::dispose)
                }
            }
            checkpoint.rememberSlots.forEachIndexed { index, previousSlot ->
                val currentSlot = scope.rememberSlots.getOrNull(index)
                if (currentSlot !== previousSlot) {
                    cleanup(previousSlot.lifecycle::leave)
                }
            }
        }

        abandonedNewRoots.forEach { scope ->
            cleanup(scope::abandonRecursively)
        }
        removedScopeRoots.forEach { scope ->
            cleanup(scope::disposeRecursively)
        }

        pendingRememberActivations.removeAll(RecomposeScope.RememberLifecycle::isTerminal)
        pendingRememberActivations.toList().forEach(::activate)

        // Only values that remain attached after all outgoing cleanup are allowed to enter.
        attempt.checkpoints.forEach { (scope, checkpoint) ->
            if (scope !in attachedScopes) return@forEach
            scope.rememberSlots.forEachIndexed { index, currentSlot ->
                val previousSlot = checkpoint.rememberSlots.getOrNull(index)
                if (currentSlot !== previousSlot) {
                    activate(currentSlot.lifecycle)
                }
            }
        }
        attachedNewScopes.forEach { scope ->
            scope.rememberSlots.forEach { slot ->
                activate(slot.lifecycle)
            }
        }

        firstFailure?.let { throw it }
    }

    private fun abortAttempt(attempt: CompositionAttempt) {
        requireOwnerThread("abort")
        check(!dispatchingCallbacks) {
            "Re-entrant prepared-composition abort is not supported."
        }
        dispatchingCallbacks = true
        try {
            abortAttemptWhileDispatching(attempt)
        } finally {
            dispatchingCallbacks = false
        }
    }

    private fun abortAttemptWhileDispatching(attempt: CompositionAttempt) {
        if (activeAttempt !== attempt) return
        activeAttempt = null
        attempt.completed = true
        if (attempt.explicitRootRequest) {
            explicitRootRequestPending = true
        }

        val queuedDuringAttempt = invalidationQueue.drainAll()
        val invalidatedExistingScopes = attempt.checkpoints
            .filter { (scope, checkpoint) ->
                scope.currentInvalidationVersion() != checkpoint.invalidationVersion
            }
            .keys
        val newScopeRoots = attempt.newScopes.filter { scope ->
            scope.parent !in attempt.newScopes
        }

        val failures = mutableListOf<Throwable>()
        fun cleanup(block: () -> Unit) {
            try {
                block()
            } catch (error: Throwable) {
                failures += error
            }
        }

        attempt.pendingStateUpdates.forEach { update ->
            cleanup {
                update.abort(attempt)
            }
        }
        attempt.checkpoints.forEach { (scope, checkpoint) ->
            scope.rememberSlots.forEachIndexed { index, slot ->
                val previousSlot = checkpoint.rememberSlots.getOrNull(index)
                if (slot !== previousSlot) {
                    cleanup(slot.lifecycle::leave)
                }
            }
        }
        attempt.checkpoints.forEach { (scope, checkpoint) ->
            if (scope.observation !== checkpoint.observation) {
                scope.observation?.let { observation ->
                    cleanup(observation::dispose)
                }
            }
            scope.restore(checkpoint)
        }
        newScopeRoots.forEach { scope ->
            cleanup(scope::abandonRecursively)
        }

        (attempt.drainedInvalidations + queuedDuringAttempt)
            .distinct()
            .filterNot(RecomposeScope::disposed)
            .forEach(invalidationQueue::enqueue)
        invalidatedExistingScopes.forEach { scope ->
            scope.markDirtyWithAncestors()
            invalidationQueue.enqueue(scope)
        }
        failures.firstOrNull()?.let { first ->
            failures.drop(1).forEach(first::addSuppressed)
            throw first
        }
    }

    private fun isAttachedToRoot(scope: RecomposeScope): Boolean {
        if (scope.disposed) return false
        var current = scope
        while (true) {
            val parent = current.parent ?: return current === slotTable.root
            if (current !in parent.children) return false
            current = parent
        }
    }

    @Synchronized
    private fun requireOwnerThread(operation: String) {
        val current = Thread.currentThread()
        val owner = ownerThread
        if (owner == null) {
            ownerThread = current
            return
        }
        check(owner === current) {
            "ComposerLite.$operation must run on its owner thread '${owner.name}', but was called " +
                "from '${current.name}'."
        }
    }

    private fun warnStructureDriftOnce(
        key: String,
        message: String,
    ) {
        if (!warningKeys.add(key)) return
        warningLogger?.invoke(message)
    }

    private fun childSaveablePath(
        parent: RecomposeScope,
        index: Int,
        signature: GroupSignature,
    ): String {
        val signatureHash = stableHash(signature).toUInt().toString(16)
        val segment = if (signature.keyStack.isEmpty()) {
            "$index:$signatureHash"
        } else {
            "key:$signatureHash"
        }
        return "${parent.saveablePath}/$segment"
    }

    private fun newGroupSignature(signature: Any): GroupSignature {
        return GroupSignature(
            keyStack = if (keyStack.isEmpty()) emptyList() else keyStack.toList(),
            signature = signature,
        )
    }

    private fun groupSignatureMatches(
        existing: Any,
        signature: Any,
    ): Boolean {
        val group = existing as? GroupSignature ?: return false
        if (group.signature != signature || group.keyStack.size != keyStack.size) {
            return false
        }
        return group.keyStack.indices.all { index ->
            group.keyStack[index] == keyStack[index]
        }
    }

    private fun stableHash(value: Any?): Int {
        return when (value) {
            null -> 0
            is Iterable<*> -> value.fold(1) { result, item ->
                31 * result + stableHash(item)
            }
            is Array<*> -> value.fold(1) { result, item ->
                31 * result + stableHash(item)
            }
            else -> value.hashCode()
        }
    }

    /** Rejects a hash collision before unequal logical keys can address one saveable namespace. */
    private fun validateSaveablePathIdentity() {
        var scope: RecomposeScope? = currentScope
        while (scope?.parent != null) {
            val current = scope
            val collision = current.parent.children.firstOrNull { sibling ->
                sibling !== current &&
                    sibling.saveablePath == current.saveablePath &&
                    sibling.signature != current.signature
            }
            require(collision == null) {
                val currentKeys = (current.signature as? GroupSignature)?.keyStack.orEmpty()
                val collidingKeys = (checkNotNull(collision).signature as? GroupSignature)
                    ?.keyStack
                    .orEmpty()
                "Unequal keyed groups produced the same saveable path hash: " +
                    "$currentKeys and $collidingKeys. Custom keys used with rememberSaveable " +
                    "must provide stable, collision-free hashCode values."
            }
            scope = current.parent
        }
    }

    private fun buildDiagnostics(attempt: CompositionAttempt): CompositionDiagnostics {
        if (!attempt.collectDiagnostics) {
            return CompositionDiagnostics()
        }
        val scopes = (attempt.recomposedScopes + attempt.skippedScopes)
            .distinct()
            .take(MAX_DIAGNOSTIC_SCOPES)
            .map { scope ->
                RecomposeScopeDiagnostic(
                    path = scope.saveablePath,
                    signature = scope.signature.toDiagnosticString(),
                    depth = scope.depth(),
                    reasons = attempt.reasons[scope].orEmpty(),
                    recomposed = scope in attempt.recomposedScopes,
                    skipped = scope in attempt.skippedScopes,
                    locals = localSnapshotInspector
                        ?.let { inspector ->
                            runCatching {
                                inspector(scope.localSnapshotOrNull())
                            }.getOrDefault(emptyList())
                        }
                        .orEmpty(),
                    sourceCallSites = scope.sourceCallSites,
                )
            }
        return CompositionDiagnostics(
            invalidatedScopeCount = attempt.drainedInvalidations.size,
            recomposedScopeCount = attempt.recomposedScopes.size,
            skippedScopeCount = attempt.skippedScopes.size,
            scopes = scopes,
        )
    }

    private fun Any.toDiagnosticString(): String {
        val raw = toString()
        return if (raw.length <= MAX_DIAGNOSTIC_SIGNATURE_LENGTH) {
            raw
        } else {
            raw.take(MAX_DIAGNOSTIC_SIGNATURE_LENGTH - 1) + "…"
        }
    }

    private fun RecomposeScope.depth(): Int {
        var depth = 0
        var current = parent
        while (current != null) {
            depth += 1
            current = current.parent
        }
        return depth
    }

    private data class GroupSignature(
        val keyStack: List<Any?>,
        val signature: Any,
    )

    private class CompositionAttempt(
        val drainedInvalidations: List<RecomposeScope>,
        val collectDiagnostics: Boolean,
        val explicitRootRequest: Boolean,
        val checkpoints: LinkedHashMap<RecomposeScope, RecomposeScope.Checkpoint> = LinkedHashMap(),
        val newScopes: LinkedHashSet<RecomposeScope> = LinkedHashSet(),
        val pendingStateUpdates: LinkedHashSet<CommitAwareState<*>> = LinkedHashSet(),
        val pendingSideEffects: MutableList<PendingSideEffect> = mutableListOf(),
        val recomposedScopes: LinkedHashSet<RecomposeScope> = LinkedHashSet(),
        val skippedScopes: LinkedHashSet<RecomposeScope> = LinkedHashSet(),
        val reasons: LinkedHashMap<RecomposeScope, LinkedHashSet<RecompositionReason>> = LinkedHashMap(),
        var completed: Boolean = false,
    ) {
        fun addReason(
            scope: RecomposeScope,
            reason: RecompositionReason,
        ) {
            if (!collectDiagnostics) return
            reasons.getOrPut(scope, ::LinkedHashSet) += reason
        }
    }

    /** State holder that keeps one candidate update isolated until its owning attempt commits. */
    private class CommitAwareState<T>(
        private val composer: ComposerLite,
        initialValue: T,
    ) : State<T> {
        private val committedState = mutableStateOf(initialValue)
        private var candidateAttempt: CompositionAttempt? = null
        private var candidateThread: Thread? = null
        private var candidateValue: Any? = NoCandidate

        override val value: T
            get() {
                val committedValue = committedState.value
                if (
                    composer.composing &&
                    composer.activeAttempt === candidateAttempt &&
                    candidateThread === Thread.currentThread()
                ) {
                    @Suppress("UNCHECKED_CAST")
                    return candidateValue as T
                }
                return committedValue
            }

        fun prepare(
            attempt: CompositionAttempt,
            value: T,
        ) {
            check(candidateAttempt == null || candidateAttempt === attempt) {
                "Committed state holder is already owned by another composition attempt."
            }
            candidateAttempt = attempt
            candidateThread = Thread.currentThread()
            candidateValue = value
        }

        fun commit(attempt: CompositionAttempt) {
            if (candidateAttempt !== attempt) return
            @Suppress("UNCHECKED_CAST")
            val value = candidateValue as T
            clearCandidate()
            committedState.value = value
        }

        fun abort(attempt: CompositionAttempt) {
            if (candidateAttempt === attempt) {
                clearCandidate()
            }
        }

        private fun clearCandidate() {
            candidateAttempt = null
            candidateThread = null
            candidateValue = NoCandidate
        }

        private object NoCandidate
    }

    /** Owns diagnostic metadata for one committed one-shot callback without retaining its keys. */
    private class PendingSideEffect(
        private val effect: () -> Unit,
        private val diagnostic: EffectDiagnostic,
        private val warningLogger: ((String) -> Unit)?,
        private val warningThresholdNanos: Long?,
        private val frameIdProvider: (() -> Long?)?,
    ) {
        fun run() {
            runSynchronousEffectOperation(
                diagnostic = diagnostic,
                operation = "run",
                warningLogger = warningLogger,
                warningThresholdNanos = warningThresholdNanos,
                frameIdProvider = frameIdProvider,
                block = effect,
            )
        }
    }

    /**
     * Owns one candidate composition transaction until it is committed or aborted.
     *
     * The owning [ComposerLite] cannot start another composition while this transaction remains
     * active. Instances are thread-confined to their composer. Committing finalizes scope and
     * remember lifecycles but leaves effects queued for [ComposerLite.commitSideEffects].
     *
     * @param T type of candidate root value
     */
    class PreparedComposition<T> internal constructor(
        /** Candidate root value produced before the transaction is committed. */
        val value: T,
        /** Bounded diagnostics captured for this attempt, or an empty summary when disabled. */
        val diagnostics: CompositionDiagnostics,
        private val isCompleted: () -> Boolean,
        private val onCommit: () -> Unit,
        private val onAbort: () -> Unit,
    ) {
        /**
         * Commits scope, observation, and remember changes to the owning composer.
         *
         * Lifecycle callback failures propagate after the runtime transaction has become committed;
         * the transaction cannot be retried or aborted afterward.
         *
         * @throws IllegalStateException if this transaction was already committed or aborted, or is
         * no longer the composer's active transaction
         */
        fun commit() {
            check(!isCompleted()) {
                "Prepared composition is already completed."
            }
            onCommit()
        }

        /**
         * Rolls back the candidate scope tree and abandons newly remembered values.
         *
         * Aborting an already completed transaction is a no-op. State invalidations and explicit
         * root requests consumed by the attempt are restored for the next composition. Cleanup
         * callback failures propagate after rollback has begun and are not retried.
         */
        fun abort() {
            if (isCompleted()) return
            onAbort()
        }
    }

    private companion object {
        const val MAX_DIAGNOSTIC_SCOPES: Int = 500
        const val MAX_DIAGNOSTIC_SIGNATURE_LENGTH: Int = 160
    }
}
