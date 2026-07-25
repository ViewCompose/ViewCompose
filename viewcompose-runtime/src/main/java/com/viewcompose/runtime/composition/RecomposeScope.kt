package com.viewcompose.runtime.composition

import com.viewcompose.runtime.observation.Observation
import java.util.concurrent.atomic.AtomicLong

/**
 * Internal composition node for SlotTable-lite runtime.
 * Exposed as public for cross-module usage from widget-core.
 */
class RecomposeScope internal constructor(
    internal var signature: Any,
    internal val parent: RecomposeScope?,
    internal val saveablePath: String = parent?.saveablePath ?: "root",
) {
    internal val children: MutableList<RecomposeScope> = mutableListOf()
    internal val rememberSlots: MutableList<RememberSlot> = mutableListOf()
    internal val effectSlots: MutableList<DisposableEffectSlot> = mutableListOf()
    internal var observation: Observation? = null
    internal var cachedResult: Any? = Unset
    @Volatile
    internal var dirty: Boolean = true
    @Volatile
    internal var composing: Boolean = false
    internal var composed: Boolean = false
    @Volatile
    internal var disposed: Boolean = false
    internal var localSnapshot: Any? = null
    internal var latestInputs: Any? = NoInputs
    internal var childCursor: Int = 0
    internal var rememberCursor: Int = 0
    internal var effectCursor: Int = 0
    internal var saveableCursor: Int = 0
    private val invalidationVersion = AtomicLong(0L)

    internal fun beginCompose() {
        composing = true
        childCursor = 0
        rememberCursor = 0
        effectCursor = 0
        saveableCursor = 0
    }

    internal fun endCompose() {
        composing = false
    }

    internal fun trimAfterCompose() {
        while (children.size > childCursor) {
            children.removeAt(children.lastIndex)
        }
        while (effectSlots.size > effectCursor) {
            effectSlots.removeAt(effectSlots.lastIndex)
        }
        while (rememberSlots.size > rememberCursor) {
            rememberSlots.removeAt(rememberSlots.lastIndex)
        }
    }

    internal fun disposeRecursively() {
        if (disposed) return
        disposed = true
        val failures = mutableListOf<Throwable>()
        fun cleanup(block: () -> Unit) {
            try {
                block()
            } catch (error: Throwable) {
                failures += error
            }
        }
        observation?.let { currentObservation ->
            cleanup(currentObservation::dispose)
        }
        observation = null
        effectSlots.forEach { slot ->
            slot.onDispose?.let { onDispose ->
                cleanup(onDispose)
            }
        }
        effectSlots.clear()
        rememberSlots.forEach { slot ->
            (slot.value as? RememberObserver)?.let { observer ->
                cleanup(observer::onForgotten)
            }
        }
        rememberSlots.clear()
        children.forEach { child ->
            cleanup(child::disposeRecursively)
        }
        children.clear()
        cachedResult = Unset
        dirty = true
        composed = false
        localSnapshot = null
        latestInputs = NoInputs
        failures.firstOrNull()?.let { first ->
            failures.drop(1).forEach(first::addSuppressed)
            throw first
        }
    }

    internal fun abandonRecursively() {
        if (disposed) return
        disposed = true
        val failures = mutableListOf<Throwable>()
        fun cleanup(block: () -> Unit) {
            try {
                block()
            } catch (error: Throwable) {
                failures += error
            }
        }
        observation?.let { currentObservation ->
            cleanup(currentObservation::dispose)
        }
        observation = null
        effectSlots.clear()
        rememberSlots.forEach { slot ->
            (slot.value as? RememberObserver)?.let { observer ->
                cleanup(observer::onAbandoned)
            }
        }
        rememberSlots.clear()
        children.forEach { child ->
            cleanup(child::abandonRecursively)
        }
        children.clear()
        cachedResult = Unset
        dirty = true
        composed = false
        localSnapshot = null
        latestInputs = NoInputs
        failures.firstOrNull()?.let { first ->
            failures.drop(1).forEach(first::addSuppressed)
            throw first
        }
    }

    internal fun markDirty(): Boolean {
        if (disposed) return false
        if (dirty && !composing) return false
        invalidationVersion.incrementAndGet()
        dirty = true
        return true
    }

    internal fun currentInvalidationVersion(): Long = invalidationVersion.get()

    internal fun restoreInvalidationVersion(version: Long) {
        invalidationVersion.set(version)
    }

    internal fun clearDirtyIfUnchanged(version: Long) {
        if (invalidationVersion.get() != version) return
        dirty = false
        if (invalidationVersion.get() != version) {
            dirty = true
        }
    }

    internal fun markDirtyWithAncestors(): Boolean {
        var current: RecomposeScope? = this
        var changed = false
        while (current != null) {
            changed = current.markDirty() || changed
            current = current.parent
        }
        return changed
    }

    fun localSnapshotOrNull(): Any? = localSnapshot

    fun updateLocalSnapshot(snapshot: Any?) {
        localSnapshot = snapshot
    }

    internal data class RememberSlot(
        var keys: List<Any?>,
        var value: Any?,
    )

    internal data class DisposableEffectSlot(
        var keys: List<Any?>,
        var onDispose: (() -> Unit)?,
    )

    internal data class Checkpoint(
        val children: List<RecomposeScope>,
        val rememberSlots: List<RememberSlot>,
        val effectSlots: List<DisposableEffectSlot>,
        val observation: Observation?,
        val cachedResult: Any?,
        val dirty: Boolean,
        val composed: Boolean,
        val disposed: Boolean,
        val localSnapshot: Any?,
        val latestInputs: Any?,
        val childCursor: Int,
        val rememberCursor: Int,
        val effectCursor: Int,
        val saveableCursor: Int,
        val invalidationVersion: Long,
    )

    internal fun checkpoint(): Checkpoint = Checkpoint(
        children = children.toList(),
        rememberSlots = rememberSlots.toList(),
        effectSlots = effectSlots.toList(),
        observation = observation,
        cachedResult = cachedResult,
        dirty = dirty,
        composed = composed,
        disposed = disposed,
        localSnapshot = localSnapshot,
        latestInputs = latestInputs,
        childCursor = childCursor,
        rememberCursor = rememberCursor,
        effectCursor = effectCursor,
        saveableCursor = saveableCursor,
        invalidationVersion = currentInvalidationVersion(),
    )

    internal fun restore(checkpoint: Checkpoint) {
        children.clear()
        children += checkpoint.children
        rememberSlots.clear()
        rememberSlots += checkpoint.rememberSlots
        effectSlots.clear()
        effectSlots += checkpoint.effectSlots
        observation = checkpoint.observation
        cachedResult = checkpoint.cachedResult
        dirty = checkpoint.dirty
        composed = checkpoint.composed
        disposed = checkpoint.disposed
        localSnapshot = checkpoint.localSnapshot
        latestInputs = checkpoint.latestInputs
        childCursor = checkpoint.childCursor
        rememberCursor = checkpoint.rememberCursor
        effectCursor = checkpoint.effectCursor
        saveableCursor = checkpoint.saveableCursor
        restoreInvalidationVersion(checkpoint.invalidationVersion)
    }

    internal object Unset

    internal object NoInputs
}
