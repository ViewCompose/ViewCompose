package com.viewcompose.runtime.composition

import com.viewcompose.runtime.observation.Observation
import java.util.concurrent.atomic.AtomicLong

/**
 * SlotTable-lite 运行时的内部 composition 节点。
 * Internal composition node for the SlotTable-lite runtime.
 *
 * 该类型为了 widget-core 跨模块访问保持 public，但构造和状态仍由 runtime 控制。
 * This type is public for cross-module widget-core access, while construction and state remain runtime-controlled.
 */
class RecomposeScope internal constructor(
    internal var signature: Any,
    internal val parent: RecomposeScope?,
    internal val saveablePath: String = parent?.saveablePath ?: "root",
    internal val sourceCallSites: List<CompositionSourceCallSite> = emptyList(),
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

    /**
     * 开始一次 scope composition，并重置本轮 slot 游标。
     * Starts one scope composition and resets slot cursors for this pass.
     */
    internal fun beginCompose() {
        composing = true
        childCursor = 0
        rememberCursor = 0
        effectCursor = 0
        saveableCursor = 0
    }

    /**
     * 结束当前 scope composition。
     * Ends the current scope composition.
     */
    internal fun endCompose() {
        composing = false
    }

    /**
     * 裁剪本轮未访问到的子 scope、effect slot 和 remember slot。
     * Trims child scopes, effect slots, and remember slots not visited during this pass.
     */
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

    /**
     * 递归释放已提交 scope，触发 effect dispose 和 RememberObserver.onForgotten。
     * Recursively disposes a committed scope, invoking effect disposals and RememberObserver.onForgotten.
     */
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

    /**
     * 递归放弃未提交 scope，触发 RememberObserver.onAbandoned。
     * Recursively abandons an uncommitted scope, invoking RememberObserver.onAbandoned.
     */
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

    /**
     * 标记 scope 失效；返回 false 表示状态没有发生新的脏标记。
     * Marks the scope dirty; false means no new dirty marker was produced.
     */
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

    /**
     * 若 composition 期间没有新的失效版本，则清除 dirty 标记。
     * Clears the dirty marker when no newer invalidation version appeared during composition.
     */
    internal fun clearDirtyIfUnchanged(version: Long) {
        if (invalidationVersion.get() != version) return
        dirty = false
        if (invalidationVersion.get() != version) {
            dirty = true
        }
    }

    /**
     * 将当前 scope 及其祖先都标记为 dirty，用于结构级回退重组。
     * Marks this scope and all ancestors dirty for structure-level fallback recomposition.
     */
    internal fun markDirtyWithAncestors(): Boolean {
        var current: RecomposeScope? = this
        var changed = false
        while (current != null) {
            changed = current.markDirty() || changed
            current = current.parent
        }
        return changed
    }

    /**
     * 返回最近一次 composition local 快照，供诊断或宿主桥接读取。
     * Returns the latest composition-local snapshot for diagnostics or host bridging.
     */
    fun localSnapshotOrNull(): Any? = localSnapshot

    /**
     * 更新当前 scope 保存的 composition local 快照。
     * Updates the composition-local snapshot stored on this scope.
     */
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

    /**
     * 创建 scope 状态检查点，用于 prepared composition 失败时回滚。
     * Creates a scope-state checkpoint for rolling back failed prepared composition attempts.
     */
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

    /**
     * 从检查点恢复 scope 状态。
     * Restores scope state from a checkpoint.
     */
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
