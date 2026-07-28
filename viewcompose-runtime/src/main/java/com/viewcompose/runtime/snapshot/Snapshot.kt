package com.viewcompose.runtime

import java.util.concurrent.atomic.AtomicBoolean

/**
 * 快照应用结果，成功表示写入已进入目标快照或全局状态。
 * Result of applying a snapshot; success means writes reached the target snapshot or global state.
 */
sealed interface SnapshotApplyResult {
    data object Success : SnapshotApplyResult

    /**
     * 应用失败，conflictCount 表示无法通过策略合并的状态数量。
     * Apply failure; conflictCount is the number of states that policies could not merge.
     */
    data class Failure(
        val conflictCount: Int,
    ) : SnapshotApplyResult
}

/**
 * 可变快照应用到全局状态时发生不可合并冲突。
 * Thrown when a mutable snapshot cannot be merged into global state.
 */
class SnapshotApplyConflictException(
    message: String,
) : IllegalStateException(message)

/**
 * 只读快照，固定一个 readId 来提供一致的状态读取视图。
 * Read-only snapshot that pins a readId to provide a consistent state view.
 *
 * 快照必须 dispose/close，以便运行时裁剪不再需要的历史状态记录。
 * Snapshots must be disposed/closed so the runtime can prune obsolete historical state records.
 */
open class Snapshot internal constructor(
    internal val readId: Int,
) : AutoCloseable {
    private val disposed = AtomicBoolean(false)

    /**
     * 在当前线程进入该快照并执行 block，嵌套进入会在栈上恢复之前上下文。
     * Enters this snapshot on the current thread and runs block; nested entries restore the previous context.
     */
    fun <R> enter(block: () -> R): R {
        ensureActive()
        return SnapshotRuntime.enterSnapshot(this, block)
    }

    override fun close() {
        dispose()
    }

    open fun dispose() {
        if (disposed.compareAndSet(false, true)) {
            SnapshotRuntime.disposeSnapshot(readId)
        }
    }

    internal fun ensureActive() {
        check(!disposed.get()) { "Snapshot is disposed." }
    }

    companion object {
        /**
         * 捕获当前可见状态的一致只读快照。
         * Captures a consistent read-only snapshot of currently visible state.
         */
        fun takeSnapshot(): Snapshot = SnapshotRuntime.takeSnapshot()

        /**
         * 创建可写快照；调用方负责 apply 后 dispose。
         * Creates a writable snapshot; callers are responsible for applying and disposing it.
         */
        fun takeMutableSnapshot(): MutableSnapshot = SnapshotRuntime.takeMutableSnapshot()

        /**
         * 返回当前全局快照版本号。
         * Returns the current global snapshot version.
         */
        fun currentGlobalId(): Int = SnapshotRuntime.currentGlobalId()

        /**
         * 在可变快照中执行 block 并自动应用，冲突会抛出异常。
         * Runs block in a mutable snapshot and applies it automatically; conflicts are thrown.
         */
        fun <R> withMutableSnapshot(block: () -> R): R {
            val snapshot = takeMutableSnapshot()
            return try {
                val result = snapshot.enter(block)
                when (val applyResult = snapshot.apply()) {
                    SnapshotApplyResult.Success -> result
                    is SnapshotApplyResult.Failure -> {
                        throw SnapshotApplyConflictException(
                            "Snapshot apply failed with ${applyResult.conflictCount} conflict(s).",
                        )
                    }
                }
            } finally {
                snapshot.dispose()
            }
        }
    }
}

/**
 * 可变快照，暂存写入并在 apply 时合并到父快照或全局状态。
 * Mutable snapshot that buffers writes and merges them into its parent snapshot or global state on apply.
 */
class MutableSnapshot internal constructor(
    readId: Int,
    internal val parent: MutableSnapshot?,
    internal val tokenId: Int,
) : Snapshot(readId) {
    internal val writes = LinkedHashMap<com.viewcompose.runtime.state.SnapshotStateObject, Any?>()
    internal var localWriteVersion: Int = 0
    internal var applied: Boolean = false

    /**
     * 应用暂存写入；同一个可变快照只能成功应用一次。
     * Applies buffered writes; the same mutable snapshot can be successfully applied only once.
     */
    fun apply(): SnapshotApplyResult {
        ensureActive()
        check(!applied) { "Snapshot already applied." }
        return SnapshotRuntime.apply(this).also { result ->
            if (result is SnapshotApplyResult.Success) {
                applied = true
            }
        }
    }

    override fun dispose() {
        super.dispose()
        writes.clear()
    }
}
