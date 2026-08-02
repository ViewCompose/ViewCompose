package com.viewcompose.runtime.composition

import com.viewcompose.runtime.observation.RuntimeObservation
import com.viewcompose.runtime.Snapshot

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
 */
class ComposerLite(
    private val slotTable: SlotTable = SlotTable(),
    private val invalidationQueue: InvalidationQueue = InvalidationQueue(),
    private val warningLogger: ((String) -> Unit)? = null,
    private val onInvalidated: (() -> Unit)? = null,
    private val localSnapshotInspector: ((Any?) -> List<CompositionLocalDiagnostic>)? = null,
    private val sourceCallSiteCollector: (() -> List<CompositionSourceCallSite>)? = null,
) {
    private val keyStack = mutableListOf<Any?>()
    private val warningKeys = HashSet<String>()
    private val pendingDisposableEffects = mutableListOf<() -> Unit>()
    private val pendingSideEffects = mutableListOf<() -> Unit>()
    private var currentScope: RecomposeScope = slotTable.root
    private var composing: Boolean = false
    private var activeAttempt: CompositionAttempt? = null
    private var explicitRootRequestPending: Boolean = false

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
    fun drainInvalidations(): List<RecomposeScope> = invalidationQueue.drainCompacted()

    /**
     * Marks the root dirty for the next composition attempt.
     *
     * Hosts use this for explicit refreshes or environment changes that are not represented by an
     * observed [State]. The request is preserved when a prepared composition aborts. This method
     * does not invoke the [onInvalidated] constructor callback because the caller already owns the
     * scheduling decision.
     */
    fun requestRootRecompose() {
        explicitRootRequestPending = true
        slotTable.root.markDirty()
    }

    /**
     * Composes [block], commits the runtime transaction, and returns its candidate value.
     *
     * This is equivalent to [prepareRoot] followed by [PreparedComposition.commit]. Committed
     * disposable and one-shot effects remain pending until [commitSideEffects] is called.
     *
     * @param T type of value produced by the root composition
     * @param block root content executed in a consistent read snapshot
     * @return the committed value produced by [block]
     * @throws IllegalStateException for re-entrant composition or when another prepared composition
     * has not been committed or aborted
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
     * @throws IllegalStateException for re-entrant composition or an unfinished prepared composition
     */
    fun <T> prepareRoot(
        collectDiagnostics: Boolean = false,
        block: () -> T,
    ): PreparedComposition<T> {
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
     * [signature] and the active [withKeys] stack identify the group at its sibling position. A
     * mismatch replaces that group and all following siblings at the drift point. Equal [inputs]
     * permit reuse while the scope is clean; changed inputs mark the scope dirty. When the body does
     * run, [reuseResult] may retain the previous result identity after comparing it with the newly
     * calculated value.
     *
     * @param T type of value cached for the group
     * @param signature stable structural identity at the current sibling position
     * @param inputs value compared with `equals` to detect explicit input changes
     * @param reuseResult optional predicate returning `true` to retain the previous result instance
     * @param block group body, receiving the opaque scope owned by this composer
     * @return the cached, newly calculated, or explicitly reused group result
     * @throws IllegalStateException when called outside an active composition attempt
     */
    fun <T> runGroup(
        signature: Any,
        inputs: Any? = RecomposeScope.NoInputs,
        reuseResult: ((previous: T, next: T) -> Boolean)? = null,
        block: (RecomposeScope) -> T,
    ): T {
        val parent = currentScope
        val index = parent.childCursor++
        val existing = parent.children.getOrNull(index)
        val scope = when {
            existing == null -> {
                val normalizedSignature = newGroupSignature(signature)
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
                    currentAttempt().newScopes += scope
                    currentAttempt().addReason(
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

            else -> {
                warnStructureDriftOnce(
                    key = "drift|${parent.signature}|$index",
                    message = "Composition structure drift at group index=$index; fallback to nearest ancestor subtree recomposition.",
                )
                while (parent.children.size > index) {
                    parent.children.removeAt(parent.children.lastIndex)
                }
                val normalizedSignature = newGroupSignature(signature)
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
                    currentAttempt().newScopes += scope
                    currentAttempt().addReason(scope, RecompositionReason.StructureChanged)
                }
            }
        }
        if (scope.latestInputs != inputs) {
            checkpointScope(scope)
            if (scope.composed) {
                currentAttempt().addReason(scope, RecompositionReason.InputsChanged)
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
     * @throws IllegalStateException when called outside an active composition attempt
     */
    fun <T> remember(
        keys: List<Any?>,
        calculation: () -> T,
    ): T {
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
        )
        if (existing != null) {
            scope.rememberSlots[index] = slot
        } else {
            scope.rememberSlots += slot
        }
        return value
    }

    /**
     * Registers a keyed effect in the next positional effect slot of the current scope.
     *
     * [effect] does not run during composition or [PreparedComposition.commit]. It runs when the host
     * next calls [commitSideEffects]. Equal combined keys retain the existing effect. Changed keys
     * dispose the old effect immediately before starting the replacement; leaving composition or
     * disposing the composer also invokes the active cleanup callback. Aborted candidates never
     * start and leave the committed effect unchanged.
     *
     * @param keys local keys appended after the active [withKeys] key stack
     * @param effect operation that starts the effect and optionally returns its cleanup callback
     * @throws IllegalStateException when called outside an active composition attempt
     */
    fun disposableEffect(
        keys: List<Any?>,
        effect: () -> (() -> Unit)?,
    ) {
        val scope = currentScope
        val scopedKeys = keyStack + keys
        val index = scope.effectCursor++
        val existing = scope.effectSlots.getOrNull(index)
        if (existing != null && existing.keys == scopedKeys) {
            return
        }
        currentAttempt().pendingDisposableEffects += commitEffect@{
            if (scope.disposed) return@commitEffect
            val current = scope.effectSlots.getOrNull(index)
            current?.onDispose?.also { onDispose ->
                current.onDispose = null
                onDispose()
            }
            val slot = RecomposeScope.DisposableEffectSlot(
                keys = scopedKeys,
                onDispose = effect(),
            )
            if (current != null) {
                scope.effectSlots[index] = slot
            } else {
                check(index == scope.effectSlots.size) {
                    "DisposableEffect slot order changed before commit."
                }
                scope.effectSlots += slot
            }
        }
    }

    /**
     * Registers [effect] to run once after the current composition commits.
     *
     * The operation is discarded if the prepared composition aborts and runs when the host next
     * calls [commitSideEffects].
     *
     * @param effect one-shot operation to execute after committed disposable-effect changes
     * @throws IllegalStateException when called outside an active composition attempt
     */
    fun sideEffect(effect: () -> Unit) {
        currentAttempt().pendingSideEffects += effect
    }

    /**
     * Returns a deterministic key for the next positional saveable slot in the current scope.
     *
     * The key combines the structural group path, local slot position, and a hash of active
     * [withKeys] values. Call order is therefore part of the saveable-state contract. Custom key
     * objects MUST keep equal values and stable `hashCode` results across host recreation.
     *
     * @return an opaque key stable for the same structure, slot position, and explicit keys
     * @throws IllegalStateException when no composition attempt is active
     */
    fun nextSaveableKey(): String {
        check(composing) {
            "Automatic rememberSaveable keys require an active composition."
        }
        val slot = currentScope.saveableCursor++
        val explicitKeyHash = stableHash(keyStack)
        return "auto:${currentScope.saveablePath}:$slot:${explicitKeyHash.toUInt().toString(16)}"
    }

    /**
     * Runs all effect operations queued by committed compositions.
     *
     * Disposable-effect replacements run before one-shot side effects, each in registration order.
     * Every operation is attempted even after a failure. The queue is cleared before execution, so
     * failed operations are not retried; the first failure is rethrown with later failures suppressed.
     */
    fun commitSideEffects() {
        if (pendingDisposableEffects.isEmpty() && pendingSideEffects.isEmpty()) return
        val disposableOperations = pendingDisposableEffects.toList()
        val sideEffectOperations = pendingSideEffects.toList()
        pendingDisposableEffects.clear()
        pendingSideEffects.clear()
        val failures = mutableListOf<Throwable>()
        disposableOperations.forEach { operation ->
            try {
                operation()
            } catch (error: Throwable) {
                failures += error
            }
        }
        sideEffectOperations.forEach { operation ->
            try {
                operation()
            } catch (error: Throwable) {
                failures += error
            }
        }
        failures.firstOrNull()?.let { first ->
            failures.drop(1).forEach(first::addSuppressed)
            throw first
        }
    }

    /**
     * Runs [block] with [keys] appended to the current positional-key namespace.
     *
     * The keys affect group signatures, [remember], [disposableEffect], and [nextSaveableKey]. The
     * previous key stack is restored when [block] returns or throws. An empty list executes [block]
     * without changing the namespace.
     *
     * @param T type of value returned by [block]
     * @param keys stable values that distinguish this nested composition path
     * @param block synchronous work executed with the extended key namespace
     * @return the value returned by [block]
     */
    fun <T> withKeys(
        keys: List<Any?>,
        block: () -> T,
    ): T {
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
     */
    fun dispose() {
        val failures = mutableListOf<Throwable>()
        activeAttempt?.let { attempt ->
            try {
                abortAttempt(attempt)
            } catch (error: Throwable) {
                failures += error
            }
        }
        pendingDisposableEffects.clear()
        pendingSideEffects.clear()
        invalidationQueue.clear()
        try {
            slotTable.dispose()
        } catch (error: Throwable) {
            failures += error
        }
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
        return checkNotNull(activeAttempt) {
            "Composition operation requires an active composition attempt."
        }
    }

    private fun commitAttempt(attempt: CompositionAttempt) {
        check(activeAttempt === attempt) {
            "Prepared composition is no longer active."
        }
        activeAttempt = null

        pendingDisposableEffects += attempt.pendingDisposableEffects
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
        val abandonedNewScopes = attempt.newScopes - attachedNewScopes
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

        attempt.checkpoints.forEach { (scope, checkpoint) ->
            if (scope.observation !== checkpoint.observation) {
                checkpoint.observation?.let { observation ->
                    cleanup(observation::dispose)
                }
            }
            val attached = scope in attachedScopes
            val sharedRememberSlots = minOf(
                checkpoint.rememberSlots.size,
                scope.rememberSlots.size,
            )
            repeat(sharedRememberSlots) { index ->
                val previous = checkpoint.rememberSlots[index].value
                val current = scope.rememberSlots[index].value
                if (previous !== current) {
                    (previous as? RememberObserver)?.let { observer ->
                        cleanup(observer::onForgotten)
                    }
                    if (attached) {
                        (current as? RememberObserver)?.let { observer ->
                            cleanup(observer::onRemembered)
                        }
                    }
                }
            }
            checkpoint.rememberSlots
                .drop(scope.rememberSlots.size)
                .forEach { slot ->
                    (slot.value as? RememberObserver)?.let { observer ->
                        cleanup(observer::onForgotten)
                    }
                }
            if (attached) {
                scope.rememberSlots
                    .drop(checkpoint.rememberSlots.size)
                    .forEach { slot ->
                        (slot.value as? RememberObserver)?.let { observer ->
                            cleanup(observer::onRemembered)
                        }
                    }
            }
            checkpoint.effectSlots
                .drop(scope.effectSlots.size)
                .forEach { slot ->
                    slot.onDispose?.also { onDispose ->
                        slot.onDispose = null
                        cleanup(onDispose)
                    }
                }
        }

        attachedNewScopes.forEach { scope ->
            scope.rememberSlots.forEach { slot ->
                (slot.value as? RememberObserver)?.let { observer ->
                    cleanup(observer::onRemembered)
                }
            }
        }

        abandonedNewRoots.forEach { scope ->
            cleanup(scope::abandonRecursively)
        }
        removedScopeRoots.forEach { scope ->
            cleanup(scope::disposeRecursively)
        }

        firstFailure?.let { throw it }
    }

    private fun abortAttempt(attempt: CompositionAttempt) {
        if (activeAttempt !== attempt) return
        activeAttempt = null
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

        attempt.checkpoints.forEach { (scope, checkpoint) ->
            scope.rememberSlots.forEachIndexed { index, slot ->
                val previous = checkpoint.rememberSlots.getOrNull(index)?.value
                if (slot.value !== previous) {
                    (slot.value as? RememberObserver)?.let { observer ->
                        cleanup(observer::onAbandoned)
                    }
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
        return "${parent.saveablePath}/$index:$signatureHash"
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
        val pendingDisposableEffects: MutableList<() -> Unit> = mutableListOf(),
        val pendingSideEffects: MutableList<() -> Unit> = mutableListOf(),
        val recomposedScopes: LinkedHashSet<RecomposeScope> = LinkedHashSet(),
        val skippedScopes: LinkedHashSet<RecomposeScope> = LinkedHashSet(),
        val reasons: LinkedHashMap<RecomposeScope, LinkedHashSet<RecompositionReason>> = LinkedHashMap(),
    ) {
        fun addReason(
            scope: RecomposeScope,
            reason: RecompositionReason,
        ) {
            if (!collectDiagnostics) return
            reasons.getOrPut(scope, ::LinkedHashSet) += reason
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
        private val onCommit: () -> Unit,
        private val onAbort: () -> Unit,
    ) {
        private var completed: Boolean = false

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
            check(!completed) {
                "Prepared composition is already completed."
            }
            completed = true
            onCommit()
        }

        /**
     * Rolls back the candidate scope tree and abandons newly remembered values.
     *
     * Aborting an already completed transaction is a no-op. State invalidations and explicit
     * root requests consumed by the attempt are restored for the next composition. Cleanup callback
     * failures propagate after rollback has begun and are not retried.
         */
        fun abort() {
            if (completed) return
            completed = true
            onAbort()
        }
    }

    private companion object {
        const val MAX_DIAGNOSTIC_SCOPES: Int = 500
        const val MAX_DIAGNOSTIC_SIGNATURE_LENGTH: Int = 160
    }
}
