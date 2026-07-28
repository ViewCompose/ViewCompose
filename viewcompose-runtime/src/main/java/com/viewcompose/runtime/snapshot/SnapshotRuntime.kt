package com.viewcompose.runtime

import com.viewcompose.runtime.observation.Observation
import com.viewcompose.runtime.state.SnapshotStateObject
import java.util.ArrayDeque
import java.util.TreeMap
import java.util.WeakHashMap

/**
 * 快照系统的单例运行时，负责线程局部快照上下文、写入暂存、提交合并和历史裁剪。
 * Singleton runtime for thread-local snapshot context, buffered writes, apply merging, and history pruning.
 *
 * 对外 API 保持在 Snapshot/MutableSnapshot；这里集中维护锁顺序和全局版本递增。
 * Public APIs stay on Snapshot/MutableSnapshot; this object centralizes lock ordering and global version increments.
 */
internal object SnapshotRuntime {
    private data class PendingValue(
        val value: Any?,
    )

    private sealed interface ApplyValue {
        data class Resolved(
            val value: Any?,
        ) : ApplyValue

        data object Conflict : ApplyValue
    }

    private data class SnapshotContext(
        val snapshot: Snapshot,
        val mutableSnapshot: MutableSnapshot?,
    )

    private val runtimeLock = Any()

    /**
     * 当前线程进入的快照栈，用于支持嵌套 enter 后恢复上一个上下文。
     * Snapshot stack entered by the current thread, allowing nested enter calls to restore the previous context.
     */
    private val contextStack = ThreadLocal<ArrayDeque<SnapshotContext>?>()

    /**
     * 活跃 readId 引用计数，最小 readId 决定状态历史能裁剪到哪里。
     * Reference counts for active readIds; the minimum readId defines how far state history can be pruned.
     */
    private val activeReadIds = TreeMap<Int, Int>()

    /**
     * 仍可能持有可裁剪历史的状态集合，使用弱引用避免运行时延长状态生命周期。
     * States that may still hold prunable history, weakly referenced so the runtime does not extend lifetimes.
     */
    private val statesPendingPrune = WeakHashMap<SnapshotStateObject, Unit>()
    private var globalSnapshotId: Int = 0
    private var nextSnapshotId: Int = 1

    /**
     * 返回当前全局提交版本。
     * Returns the current global commit version.
     */
    fun currentGlobalId(): Int = synchronized(runtimeLock) { globalSnapshotId }

    /**
     * 创建只读快照，并登记其 readId 以保护对应历史记录。
     * Creates a read-only snapshot and registers its readId to protect matching history records.
     */
    fun takeSnapshot(): Snapshot = synchronized(runtimeLock) {
        val readId = currentContextReadId() ?: globalSnapshotId
        registerSnapshotLocked(readId)
        Snapshot(readId = readId)
    }

    /**
     * 创建可变快照；嵌套创建时写入会先应用到父快照。
     * Creates a mutable snapshot; when nested, writes apply to the parent snapshot first.
     */
    fun takeMutableSnapshot(): MutableSnapshot {
        val parent = currentMutableSnapshot()
        return synchronized(runtimeLock) {
            val readId = parent?.readId ?: currentContextReadId() ?: globalSnapshotId
            registerSnapshotLocked(readId)
            MutableSnapshot(
                readId = readId,
                parent = parent,
                tokenId = nextSnapshotId++,
            )
        }
    }

    /**
     * 释放 readId 引用并尝试裁剪不再需要的历史记录。
     * Releases one readId reference and tries to prune history that is no longer needed.
     */
    fun disposeSnapshot(readId: Int) {
        synchronized(runtimeLock) {
            val count = activeReadIds[readId] ?: return
            if (count == 1) {
                activeReadIds.remove(readId)
            } else {
                activeReadIds[readId] = count - 1
            }
            prunePendingStatesLocked()
        }
    }

    /**
     * 在当前线程进入快照上下文并执行 block。
     * Enters a snapshot context on the current thread and runs block.
     */
    fun <R> enterSnapshot(
        snapshot: Snapshot,
        block: () -> R,
    ): R {
        snapshot.ensureActive()
        val stack = contextStack.get() ?: ArrayDeque<SnapshotContext>().also(contextStack::set)
        val mutable = snapshot as? MutableSnapshot
        stack.addLast(
            SnapshotContext(
                snapshot = snapshot,
                mutableSnapshot = mutable,
            ),
        )
        return try {
            block()
        } finally {
            stack.removeLast()
            if (stack.isEmpty()) {
                contextStack.remove()
            }
        }
    }

    /**
     * 当前读取 token 同时包含 readId 和本地写入版本，供派生状态判断缓存是否仍有效。
     * Current read token combines readId and local write version so derived state can validate its cache.
     */
    fun currentReadToken(): Long {
        val mutable = currentMutableSnapshot()
        val readId = mutable?.readId ?: currentReadId()
        val version = mutable?.localWriteVersion ?: 0
        return (readId.toLong() shl 32) or (version.toLong() and 0xFFFFFFFFL)
    }

    /**
     * 读取状态值，优先返回当前可变快照或父快照中的暂存写入。
     * Reads a state value, preferring buffered writes from the current mutable snapshot or its parents.
     */
    fun readStateValue(state: SnapshotStateObject): Any? {
        val mutable = currentMutableSnapshot()
        if (mutable != null) {
            readPendingValue(mutable, state)?.let { return it.value }
        }
        return state.readAny(currentReadId())
    }

    /**
     * 写入状态值；无显式可变快照时创建一次自动可变快照并立即提交。
     * Writes a state value; without an explicit mutable snapshot, creates and immediately applies an automatic one.
     */
    fun writeStateValue(
        state: SnapshotStateObject,
        value: Any?,
    ) {
        val mutable = currentMutableSnapshot()
        if (mutable != null) {
            writeInMutableSnapshot(
                snapshot = mutable,
                state = state,
                value = value,
            )
            return
        }
        val auto = takeMutableSnapshot()
        try {
            auto.enter {
                writeInMutableSnapshot(
                    snapshot = auto,
                    state = state,
                    value = value,
                )
            }
            when (val result = auto.apply()) {
                SnapshotApplyResult.Success -> Unit
                is SnapshotApplyResult.Failure -> {
                    throw SnapshotApplyConflictException(
                        "Snapshot apply failed with ${result.conflictCount} conflict(s).",
                    )
                }
            }
        } finally {
            auto.dispose()
        }
    }

    /**
     * 将可变快照应用到父快照或全局状态。
     * Applies a mutable snapshot to its parent snapshot or to global state.
     */
    fun apply(snapshot: MutableSnapshot): SnapshotApplyResult {
        snapshot.ensureActive()
        return if (snapshot.parent != null) {
            applyToParent(snapshot)
        } else {
            applyToGlobal(snapshot)
        }
    }

    /**
     * 将子快照写入合并到父快照暂存区，不触发全局观察者失效。
     * Merges child snapshot writes into the parent buffer without invalidating global observers.
     */
    private fun applyToParent(snapshot: MutableSnapshot): SnapshotApplyResult {
        val parent = snapshot.parent ?: return SnapshotApplyResult.Success
        var conflicts = 0
        val mergedWrites = LinkedHashMap<SnapshotStateObject, Any?>()
        for ((state, currentValue) in snapshot.writes) {
            val previousValue = state.readAny(snapshot.readId)
            val appliedValue = readStateInSnapshot(parent, state)
            val resolved = resolveApplyValue(
                state = state,
                previousValue = previousValue,
                currentValue = currentValue,
                appliedValue = appliedValue,
                hasConcurrentChange = parent.writes.containsKey(state),
            )
            when (resolved) {
                ApplyValue.Conflict -> {
                    conflicts += 1
                    continue
                }

                is ApplyValue.Resolved -> mergedWrites[state] = resolved.value
            }
        }
        if (conflicts > 0) {
            return SnapshotApplyResult.Failure(conflictCount = conflicts)
        }
        for ((state, resolved) in mergedWrites) {
            val parentCurrent = readStateInSnapshot(parent, state)
            if (!state.equivalentAny(parentCurrent, resolved)) {
                parent.writes[state] = resolved
                parent.localWriteVersion += 1
            }
        }
        return SnapshotApplyResult.Success
    }

    /**
     * 将根可变快照写入提交到全局版本，并在锁外通知观察者。
     * Commits root mutable snapshot writes to a new global version and notifies observers outside the lock.
     */
    private fun applyToGlobal(snapshot: MutableSnapshot): SnapshotApplyResult {
        var conflicts = 0
        val resolvedWrites = LinkedHashMap<SnapshotStateObject, Any?>()
        val changedStates = mutableListOf<SnapshotStateObject>()
        val invalidations = mutableListOf<Observation>()
        synchronized(runtimeLock) {
            val appliedGlobalId = globalSnapshotId
            for ((state, currentValue) in snapshot.writes) {
                val previousValue = state.readAny(snapshot.readId)
                val appliedValue = state.readAny(appliedGlobalId)
                val resolved = resolveApplyValue(
                    state = state,
                    previousValue = previousValue,
                    currentValue = currentValue,
                    appliedValue = appliedValue,
                    hasConcurrentChange = state.latestSnapshotId() > snapshot.readId,
                )
                when (resolved) {
                    ApplyValue.Conflict -> {
                        conflicts += 1
                        continue
                    }

                    is ApplyValue.Resolved -> resolvedWrites[state] = resolved.value
                }
            }
            if (conflicts > 0) {
                return SnapshotApplyResult.Failure(conflictCount = conflicts)
            }
            if (resolvedWrites.isEmpty()) {
                return SnapshotApplyResult.Success
            }
            val commitId = nextSnapshotId++
            for ((state, resolved) in resolvedWrites) {
                if (state.commitAny(commitId, resolved)) {
                    changedStates += state
                }
            }
            globalSnapshotId = commitId
            changedStates.forEach { state ->
                invalidations += state.snapshotObservers()
                trackStateForPruningLocked(state)
            }
        }
        invalidations.forEach { observer -> observer.invalidate() }
        return SnapshotApplyResult.Success
    }

    /**
     * 解析一次快照写入应用结果；无并发变化时直接采用当前值。
     * Resolves one snapshot write during apply; without concurrent change, the current value is accepted directly.
     *
     * merge 返回 null 被视为冲突，因此策略无法表达“合并为 null”的结果。
     * A null merge result means conflict, so policies cannot represent "merged to null" here.
     */
    private fun resolveApplyValue(
        state: SnapshotStateObject,
        previousValue: Any?,
        currentValue: Any?,
        appliedValue: Any?,
        hasConcurrentChange: Boolean,
    ): ApplyValue {
        if (!hasConcurrentChange) {
            return ApplyValue.Resolved(currentValue)
        }
        val merged = state.mergeAny(
            previous = previousValue,
            current = currentValue,
            applied = appliedValue,
        )
        return if (merged != null) {
            ApplyValue.Resolved(merged)
        } else {
            ApplyValue.Conflict
        }
    }

    /**
     * 写入当前可变快照暂存区；等价值不会增加本地写入版本。
     * Writes into the current mutable snapshot buffer; equivalent values do not advance the local write version.
     */
    private fun writeInMutableSnapshot(
        snapshot: MutableSnapshot,
        state: SnapshotStateObject,
        value: Any?,
    ) {
        val current = readStateInSnapshot(snapshot, state)
        if (state.equivalentAny(current, value)) {
            return
        }
        snapshot.writes[state] = value
        snapshot.localWriteVersion += 1
    }

    /**
     * 按“当前快照暂存 -> 父快照暂存 -> readId 历史”的顺序读取值。
     * Reads in the order of current snapshot buffer, parent snapshot buffers, then readId history.
     */
    private fun readStateInSnapshot(
        snapshot: MutableSnapshot,
        state: SnapshotStateObject,
    ): Any? {
        readPendingValue(snapshot, state)?.let { return it.value }
        return state.readAny(snapshot.readId)
    }

    /**
     * 在可变快照链上查找暂存值，显式写入 null 也必须被识别为命中。
     * Looks up buffered values through the mutable snapshot chain; explicit null writes must count as hits.
     */
    private fun readPendingValue(
        snapshot: MutableSnapshot,
        state: SnapshotStateObject,
    ): PendingValue? {
        if (snapshot.writes.containsKey(state)) {
            return PendingValue(snapshot.writes[state])
        }
        val parent = snapshot.parent
        return if (parent != null) {
            readPendingValue(parent, state)
        } else {
            null
        }
    }

    /**
     * 返回当前线程可见的 readId；未进入快照时使用全局版本。
     * Returns the readId visible to the current thread; falls back to the global version outside snapshots.
     */
    private fun currentReadId(): Int {
        currentContextReadId()?.let { return it }
        return synchronized(runtimeLock) { globalSnapshotId }
    }

    private fun currentContextReadId(): Int? {
        val stack = contextStack.get()
        if (stack != null && stack.isNotEmpty()) {
            return stack.last().snapshot.readId
        }
        return null
    }

    private fun currentMutableSnapshot(): MutableSnapshot? {
        val stack = contextStack.get() ?: return null
        if (stack.isEmpty()) return null
        return stack.last().mutableSnapshot
    }

    private fun registerSnapshotLocked(readId: Int) {
        activeReadIds[readId] = (activeReadIds[readId] ?: 0) + 1
    }

    /**
     * 记录可能仍需裁剪的状态；返回 false 表示该状态已经无多余历史。
     * Tracks states that may still need pruning; false means the state no longer has extra history.
     */
    private fun trackStateForPruningLocked(state: SnapshotStateObject) {
        if (state.pruneRecords(activeReadIds.firstKeyOrNull())) {
            statesPendingPrune[state] = Unit
        } else {
            statesPendingPrune.remove(state)
        }
    }

    /**
     * 在快照释放后重新尝试裁剪挂起状态，直到集合中只剩仍受活跃 readId 保护的状态。
     * Retries pruning pending states after snapshot disposal until only states protected by active readIds remain.
     */
    private fun prunePendingStatesLocked() {
        if (statesPendingPrune.isEmpty()) return
        val minActiveReadId = activeReadIds.firstKeyOrNull()
        val iterator = statesPendingPrune.keys.iterator()
        while (iterator.hasNext()) {
            val state = iterator.next()
            if (!state.pruneRecords(minActiveReadId)) {
                iterator.remove()
            }
        }
    }

    private fun TreeMap<Int, Int>.firstKeyOrNull(): Int? {
        return if (isEmpty()) null else firstKey()
    }
}
