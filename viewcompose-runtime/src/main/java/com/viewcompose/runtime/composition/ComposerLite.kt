package com.viewcompose.runtime.composition

import com.viewcompose.runtime.observation.RuntimeObservation
import com.viewcompose.runtime.Snapshot

/**
 * SlotTable-lite composer for node-group incremental recomposition.
 *
 * This runtime intentionally does not depend on compiler-generated stability/changed flags.
 */
class ComposerLite(
    private val slotTable: SlotTable = SlotTable(),
    private val invalidationQueue: InvalidationQueue = InvalidationQueue(),
    private val warningLogger: ((String) -> Unit)? = null,
    private val onInvalidated: (() -> Unit)? = null,
) {
    private val keyStack = mutableListOf<Any?>()
    private val warningKeys = HashSet<String>()
    private val pendingDisposableEffects = mutableListOf<() -> Unit>()
    private val pendingSideEffects = mutableListOf<() -> Unit>()
    private var currentScope: RecomposeScope = slotTable.root
    private var composing: Boolean = false
    private var activeAttempt: CompositionAttempt? = null

    fun hasPendingInvalidations(): Boolean = invalidationQueue.isNotEmpty()

    fun drainInvalidations(): List<RecomposeScope> = invalidationQueue.drainCompacted()

    fun requestRootRecompose() {
        slotTable.root.markDirty()
    }

    fun <T> composeRoot(block: () -> T): T {
        val prepared = prepareRoot(block)
        prepared.commit()
        return prepared.value
    }

    /**
     * Composes a candidate result without committing slot-table or observer changes.
     *
     * Hosts that apply the result to another mutable tree should call [PreparedComposition.commit]
     * only after that apply succeeds, or [PreparedComposition.abort] when it fails.
     */
    fun <T> prepareRoot(block: () -> T): PreparedComposition<T> {
        if (composing) {
            error("Re-entrant composeRoot() is not supported.")
        }
        check(activeAttempt == null) {
            "A prepared composition must be committed or aborted before composing again."
        }
        composing = true
        val attempt = CompositionAttempt(
            checkpoints = checkpointScopes(),
            drainedInvalidations = drainInvalidations(),
        )
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
                onCommit = { commitAttempt(attempt) },
                onAbort = { abortAttempt(attempt) },
            )
        } catch (error: Throwable) {
            abortAttempt(attempt)
            throw error
        } finally {
            snapshot.dispose()
            currentScope = previous
            composing = false
        }
    }

    fun <T> runGroup(
        signature: Any,
        inputs: List<Any?> = emptyList(),
        block: (RecomposeScope) -> T,
    ): T {
        val parent = currentScope
        val normalizedSignature = GroupSignature(
            keyStack = keyStack.toList(),
            signature = signature,
        )
        val index = parent.childCursor++
        val existing = parent.children.getOrNull(index)
        val scope = when {
            existing == null -> RecomposeScope(
                signature = normalizedSignature,
                parent = parent,
                saveablePath = childSaveablePath(
                    parent = parent,
                    index = index,
                    signature = normalizedSignature,
                ),
            ).also(parent.children::add)

            existing.signature == normalizedSignature -> existing

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
                ).also(parent.children::add)
            }
        }
        if (scope.latestInputs != inputs) {
            scope.latestInputs = inputs
            scope.markDirty()
        }
        val previous = currentScope
        currentScope = scope
        return try {
            composeScope(
                scope = scope,
                block = { block(scope) },
            )
        } finally {
            currentScope = previous
        }
    }

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

    fun sideEffect(effect: () -> Unit) {
        currentAttempt().pendingSideEffects += effect
    }

    /**
     * Returns a deterministic key for the next saveable slot in the current composition scope.
     *
     * The key is based on the node-group path, the local saveable slot position, and any explicit
     * [withKeys] values. Callers that provide custom key objects must keep their `hashCode` stable
     * across host recreation.
     */
    fun nextSaveableKey(): String {
        check(composing) {
            "Automatic rememberSaveable keys require an active composition."
        }
        val slot = currentScope.saveableCursor++
        val explicitKeyHash = stableHash(keyStack)
        return "auto:${currentScope.saveablePath}:$slot:${explicitKeyHash.toUInt().toString(16)}"
    }

    fun commitSideEffects() {
        if (pendingDisposableEffects.isEmpty() && pendingSideEffects.isEmpty()) return
        val disposableOperations = pendingDisposableEffects.toList()
        val sideEffectOperations = pendingSideEffects.toList()
        pendingDisposableEffects.clear()
        pendingSideEffects.clear()
        disposableOperations.forEach { operation ->
            operation()
        }
        sideEffectOperations.forEach { operation ->
            operation()
        }
    }

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

    fun dispose() {
        activeAttempt?.let(::abortAttempt)
        pendingDisposableEffects.clear()
        pendingSideEffects.clear()
        invalidationQueue.clear()
        slotTable.dispose()
    }

    private fun <T> composeScope(
        scope: RecomposeScope,
        block: () -> T,
    ): T {
        val hasCached = scope.cachedResult !== RecomposeScope.Unset
        if (!scope.dirty && scope.composed && hasCached) {
            @Suppress("UNCHECKED_CAST")
            return scope.cachedResult as T
        }
        val invalidationVersion = scope.currentInvalidationVersion()
        scope.beginCompose()
        val (result, nextObservation) = RuntimeObservation.observeReads(
            onInvalidated = {
                if (scope.disposed) return@observeReads
                scope.markDirtyWithAncestors()
                invalidationQueue.enqueue(scope)
                onInvalidated?.invoke()
            },
        ) {
            block()
        }
        scope.observation = nextObservation
        scope.cachedResult = result
        scope.clearDirtyIfUnchanged(invalidationVersion)
        scope.composed = true
        scope.trimAfterCompose()
        return result
    }

    private fun checkpointScopes(): LinkedHashMap<RecomposeScope, RecomposeScope.Checkpoint> {
        val checkpoints = LinkedHashMap<RecomposeScope, RecomposeScope.Checkpoint>()

        fun visit(scope: RecomposeScope) {
            checkpoints[scope] = scope.checkpoint()
            scope.children.forEach(::visit)
        }

        visit(slotTable.root)
        return checkpoints
    }

    private fun currentScopes(): LinkedHashSet<RecomposeScope> {
        val scopes = LinkedHashSet<RecomposeScope>()

        fun visit(scope: RecomposeScope) {
            if (!scopes.add(scope)) return
            scope.children.forEach(::visit)
        }

        visit(slotTable.root)
        return scopes
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

        val finalScopes = currentScopes()
        pendingDisposableEffects += attempt.pendingDisposableEffects
        pendingSideEffects += attempt.pendingSideEffects

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
            if (scope !in finalScopes) return@forEach
            if (scope.observation !== checkpoint.observation) {
                checkpoint.observation?.dispose()
            }
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
                    (current as? RememberObserver)?.let { observer ->
                        cleanup(observer::onRemembered)
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
            scope.rememberSlots
                .drop(checkpoint.rememberSlots.size)
                .forEach { slot ->
                    (slot.value as? RememberObserver)?.let { observer ->
                        cleanup(observer::onRemembered)
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

        (finalScopes - attempt.checkpoints.keys).forEach { scope ->
            scope.rememberSlots.forEach { slot ->
                (slot.value as? RememberObserver)?.let { observer ->
                    cleanup(observer::onRemembered)
                }
            }
        }

        val removedScopes = attempt.checkpoints.keys - finalScopes
        removedScopes
            .filter { scope -> scope.parent !in removedScopes }
            .forEach { scope ->
                cleanup(scope::disposeRecursively)
            }

        firstFailure?.let { throw it }
    }

    private fun abortAttempt(attempt: CompositionAttempt) {
        if (activeAttempt !== attempt) return
        activeAttempt = null

        val scopesBeforeRestore = currentScopes()
        val queuedDuringAttempt = invalidationQueue.drainAll()
        val invalidatedExistingScopes = attempt.checkpoints
            .filter { (scope, checkpoint) ->
                scope.currentInvalidationVersion() != checkpoint.invalidationVersion
            }
            .keys
        val newScopeRoots = scopesBeforeRestore
            .filter { scope ->
                scope !in attempt.checkpoints && scope.parent in attempt.checkpoints
            }

        scopesBeforeRestore.forEach { scope ->
            val checkpoint = attempt.checkpoints[scope] ?: return@forEach
            scope.rememberSlots.forEachIndexed { index, slot ->
                val previous = checkpoint.rememberSlots.getOrNull(index)?.value
                if (slot.value !== previous) {
                    (slot.value as? RememberObserver)?.onAbandoned()
                }
            }
        }
        attempt.checkpoints.forEach { (scope, checkpoint) ->
            if (scope.observation !== checkpoint.observation) {
                scope.observation?.dispose()
            }
            scope.restore(checkpoint)
        }
        newScopeRoots.forEach(RecomposeScope::abandonRecursively)

        (attempt.drainedInvalidations + queuedDuringAttempt)
            .distinct()
            .filterNot(RecomposeScope::disposed)
            .forEach(invalidationQueue::enqueue)
        invalidatedExistingScopes.forEach { scope ->
            scope.markDirtyWithAncestors()
            invalidationQueue.enqueue(scope)
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

    private data class GroupSignature(
        val keyStack: List<Any?>,
        val signature: Any,
    )

    private class CompositionAttempt(
        val checkpoints: LinkedHashMap<RecomposeScope, RecomposeScope.Checkpoint>,
        val drainedInvalidations: List<RecomposeScope>,
        val pendingDisposableEffects: MutableList<() -> Unit> = mutableListOf(),
        val pendingSideEffects: MutableList<() -> Unit> = mutableListOf(),
    )

    class PreparedComposition<T> internal constructor(
        val value: T,
        private val onCommit: () -> Unit,
        private val onAbort: () -> Unit,
    ) {
        private var completed: Boolean = false

        fun commit() {
            check(!completed) {
                "Prepared composition is already completed."
            }
            completed = true
            onCommit()
        }

        fun abort() {
            if (completed) return
            completed = true
            onAbort()
        }
    }
}
