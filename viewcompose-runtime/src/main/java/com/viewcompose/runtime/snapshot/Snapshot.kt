package com.viewcompose.runtime

import java.util.concurrent.atomic.AtomicBoolean

sealed interface SnapshotApplyResult {
    data object Success : SnapshotApplyResult

    data class Failure(
        val conflictCount: Int,
    ) : SnapshotApplyResult
}

class SnapshotApplyConflictException(
    message: String,
) : IllegalStateException(message)

open class Snapshot internal constructor(
    internal val readId: Int,
) : AutoCloseable {
    private val disposed = AtomicBoolean(false)

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
        fun takeSnapshot(): Snapshot = SnapshotRuntime.takeSnapshot()

        fun takeMutableSnapshot(): MutableSnapshot = SnapshotRuntime.takeMutableSnapshot()

        fun currentGlobalId(): Int = SnapshotRuntime.currentGlobalId()

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

class MutableSnapshot internal constructor(
    readId: Int,
    internal val parent: MutableSnapshot?,
    internal val tokenId: Int,
) : Snapshot(readId) {
    internal val writes = LinkedHashMap<com.viewcompose.runtime.state.SnapshotStateObject, Any?>()
    internal var localWriteVersion: Int = 0
    internal var applied: Boolean = false

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
