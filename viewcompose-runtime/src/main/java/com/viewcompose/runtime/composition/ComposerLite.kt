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

    fun hasPendingInvalidations(): Boolean = invalidationQueue.isNotEmpty()

    fun drainInvalidations(): List<RecomposeScope> = invalidationQueue.drainCompacted()

    fun requestRootRecompose() {
        slotTable.root.markDirty()
    }

    fun <T> composeRoot(block: () -> T): T {
        if (composing) {
            error("Re-entrant composeRoot() is not supported.")
        }
        composing = true
        pendingDisposableEffects.clear()
        pendingSideEffects.clear()
        drainInvalidations()
        val root = slotTable.root
        val previous = currentScope
        currentScope = root
        val snapshot = Snapshot.takeSnapshot()
        return try {
            snapshot.enter {
                composeScope(
                    scope = root,
                    block = { block() },
                )
            }
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
                    parent.children.removeAt(parent.children.lastIndex).disposeRecursively()
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
        pendingDisposableEffects += commitEffect@{
            if (scope.disposed) return@commitEffect
            val current = scope.effectSlots.getOrNull(index)
            current?.onDispose?.invoke()
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
        pendingSideEffects += effect
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
        scope.observation?.dispose()
        val (result, nextObservation) = try {
            RuntimeObservation.observeReads(
                onInvalidated = {
                    if (scope.disposed) return@observeReads
                    scope.markDirtyWithAncestors()
                    invalidationQueue.enqueue(scope)
                    onInvalidated?.invoke()
                },
            ) {
                block()
            }
        } catch (error: Throwable) {
            scope.markDirty()
            throw error
        }
        scope.observation = nextObservation
        scope.cachedResult = result
        scope.clearDirtyIfUnchanged(invalidationVersion)
        scope.composed = true
        scope.trimAfterCompose()
        return result
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
}
