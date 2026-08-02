package com.viewcompose.runtime

import java.util.concurrent.atomic.AtomicBoolean

/** Reports whether every buffered write from a [MutableSnapshot] was applied atomically. */
sealed interface SnapshotApplyResult {
    /** Indicates that all buffered writes were applied, or that the snapshot contained no changes. */
    data object Success : SnapshotApplyResult

    /**
     * Reports that no writes were applied because one or more concurrent changes could not merge.
     *
     * @property conflictCount number of state objects whose mutation policies returned `null` from
     * [SnapshotMutationPolicy.merge]
     */
    data class Failure(
        val conflictCount: Int,
    ) : SnapshotApplyResult
}

/**
 * Indicates that an automatic mutable-snapshot transaction encountered unmergeable changes.
 *
 * Explicit [MutableSnapshot.apply] calls report the same condition as [SnapshotApplyResult.Failure]
 * instead of throwing this exception.
 *
 * @param message detail message exposed by [IllegalStateException]
 */
class SnapshotApplyConflictException(
    message: String,
) : IllegalStateException(message)

/**
 * Pins a consistent, read-only view of snapshot-managed state until it is disposed.
 *
 * [enter] installs this snapshot in a thread-local context, so all state reads in the block observe
 * the version captured when the snapshot was taken. Nested entries restore the previous context.
 * The snapshot retains historical state records needed by its version; callers MUST invoke [close]
 * or [dispose], preferably with `use`, when reads are complete.
 *
 * A snapshot does not make arbitrary work thread-safe. Do not dispose an instance while an [enter]
 * block is active, and serialize concurrent use of the same instance.
 */
open class Snapshot internal constructor(
    internal val readId: Int,
) : AutoCloseable {
    private val disposed = AtomicBoolean(false)

    /**
     * Runs [block] with this snapshot as the current read context on the calling thread.
     *
     * The previous snapshot context is restored even when [block] throws. Writes made without an
     * explicitly entered [MutableSnapshot] still use an automatic global transaction and do not
     * change the values read through this pinned snapshot.
     *
     * @param R type of value returned by [block]
     * @param block synchronous work executed under this snapshot
     * @return the value returned by [block]
     * @throws IllegalStateException if this snapshot has already been disposed
     */
    fun <R> enter(block: () -> R): R {
        ensureActive()
        return SnapshotRuntime.enterSnapshot(this, block)
    }

    /** Releases this snapshot when used through [AutoCloseable]. */
    override fun close() {
        dispose()
    }

    /**
     * Releases the pinned read version and allows obsolete state history to be pruned.
     *
     * Disposal is idempotent. An instance cannot be entered again after disposal.
     */
    open fun dispose() {
        if (disposed.compareAndSet(false, true)) {
            SnapshotRuntime.disposeSnapshot(readId)
        }
    }

    internal fun ensureActive() {
        check(!disposed.get()) { "Snapshot is disposed." }
    }

    /** Provides factories and transaction helpers for the process-wide snapshot runtime. */
    companion object {
        /**
         * Captures a read-only snapshot of the state visible in the current context.
         *
         * A snapshot created inside another snapshot inherits that context's read version. The
         * caller owns the result and MUST close or dispose it.
         *
         * @return a new active snapshot pinned to the current visible version
         */
        fun takeSnapshot(): Snapshot = SnapshotRuntime.takeSnapshot()

        /**
         * Creates a mutable snapshot from the state visible in the current context.
         *
         * When called inside another [MutableSnapshot], the new snapshot becomes its child and a
         * successful [MutableSnapshot.apply] merges writes into the parent buffer. Otherwise, apply
         * targets global state. The caller owns the result and MUST dispose it after applying or
         * abandoning its writes.
         *
         * @return a new active mutable snapshot with no buffered writes
         */
        fun takeMutableSnapshot(): MutableSnapshot = SnapshotRuntime.takeMutableSnapshot()

        /**
         * Returns the identifier of the latest globally committed snapshot version.
         *
         * Identifiers increase monotonically for the lifetime of the runtime but are not guaranteed
         * to be contiguous. Use this value for diagnostics and cache validation, not persistence.
         *
         * @return the current process-local global snapshot identifier
         */
        fun currentGlobalId(): Int = SnapshotRuntime.currentGlobalId()

        /**
         * Runs [block] in a new mutable snapshot and applies all writes on successful return.
         *
         * When called inside a mutable snapshot, the transaction applies to the parent buffer;
         * otherwise it applies to global state. If [block] throws, no apply is attempted. The
         * temporary snapshot is disposed in every outcome.
         *
         * @sample com.viewcompose.runtime.samples.mutableSnapshotSample
         * @param R type of value returned by [block]
         * @param block transaction body whose state writes are buffered
         * @return the value returned by [block] after a successful apply
         * @throws SnapshotApplyConflictException if concurrent writes cannot be merged
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
 * Buffers state writes and applies them atomically to a parent snapshot or global state.
 *
 * Reads first observe this snapshot's buffered writes, then parent buffers, then the version pinned
 * at creation. [apply] may be retried after [SnapshotApplyResult.Failure], but a successful apply is
 * terminal; do not enter or write through the snapshot afterward. The caller MUST [dispose] the
 * snapshot whether it is applied or abandoned.
 *
 * Mutable snapshots are not safe for concurrent entry or mutation. Conflicting destination writes
 * are resolved independently through each state's [SnapshotMutationPolicy].
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
     * Applies every buffered write to this snapshot's parent buffer or to global state.
     *
     * The operation is atomic: a [SnapshotApplyResult.Failure] leaves the destination unchanged and
     * allows the caller to adjust or retry the active snapshot. A successful result, including an
     * empty apply, prevents any subsequent apply call.
     *
     * @return [SnapshotApplyResult.Success] when all writes apply, or
     * [SnapshotApplyResult.Failure] with the number of unmergeable state objects
     * @throws IllegalStateException if this snapshot is disposed or already applied successfully
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

    /** Discards buffered writes and releases the pinned read version. */
    override fun dispose() {
        super.dispose()
        writes.clear()
    }
}
