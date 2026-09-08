package com.viewcompose.runtime

import com.viewcompose.runtime.observation.Observation
import com.viewcompose.runtime.observation.ObservationInvalidations
import com.viewcompose.runtime.state.SnapshotStateObject
import java.util.ArrayDeque
import java.util.TreeMap
import java.util.WeakHashMap

/**
 * Owns thread-local snapshot contexts, buffered writes, conflict resolution, and history pruning.
 *
 * Public behavior remains on [Snapshot] and [MutableSnapshot]. Centralizing the mutable global state
 * here keeps version allocation and lock ordering consistent.
 */
internal object SnapshotRuntime {
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

    /** Snapshot stack for the current thread, allowing nested entry to restore its parent context. */
    private val contextStack = ThreadLocal<ArrayDeque<SnapshotContext>?>()

    /** Active read-ID reference counts; the oldest ID defines the history retention boundary. */
    private val activeReadIds = TreeMap<Int, Int>()

    /** Weakly held states that may still have history eligible for pruning. */
    private val statesPendingPrune = WeakHashMap<SnapshotStateObject, Unit>()
    private var globalSnapshotId: Int = 0
    private var nextSnapshotId: Int = 1

    /** Returns the current global commit version while holding the runtime lock. */
    fun currentGlobalId(): Int = synchronized(runtimeLock) { globalSnapshotId }

    /** Creates a read-only snapshot and retains history visible from its read ID. */
    fun takeSnapshot(): Snapshot = synchronized(runtimeLock) {
        val readId = currentContextReadId() ?: globalSnapshotId
        val inherited = capturePendingValues()
        registerSnapshotLocked(readId)
        Snapshot(
            readId = readId,
            tokenId = if (inherited.isEmpty()) readId else nextSnapshotId++,
            inheritedValues = inherited,
        )
    }

    /** Creates a mutable snapshot whose nested writes apply to its parent before global state. */
    fun takeMutableSnapshot(): MutableSnapshot {
        val parent = currentMutableSnapshot()
        return synchronized(runtimeLock) {
            val readId = parent?.readId ?: currentContextReadId() ?: globalSnapshotId
            val inherited = capturePendingValues()
            registerSnapshotLocked(readId)
            MutableSnapshot(
                readId = readId,
                parent = parent,
                tokenId = nextSnapshotId++,
                inheritedValues = inherited,
            )
        }
    }

    /** Releases a read-ID reference and retries deferred history pruning. */
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

    /** Enters [snapshot] on the current thread for the synchronous duration of [block]. */
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

    /** Identifies a frozen read view and its local mutations without aliasing sibling snapshots. */
    fun currentReadToken(): Long {
        val snapshot = contextStack.get()?.peekLast()?.snapshot
        snapshot?.ensureActive()
        val identity = snapshot?.tokenId ?: currentGlobalId()
        val version = (snapshot as? MutableSnapshot)?.localWriteVersion ?: 0
        return (identity.toLong() shl 32) or (version.toLong() and 0xFFFFFFFFL)
    }

    /** Reads state from local writes, the frozen inherited view, or pinned global history. */
    fun readStateValue(state: SnapshotStateObject): Any? {
        val snapshot = contextStack.get()?.peekLast()?.snapshot
        if (snapshot != null) {
            snapshot.ensureActive()
            readPendingValue(snapshot, state)?.let { return it.value }
        }
        return state.readAny(currentReadId())
    }

    /** Buffers a state write, or creates and immediately applies an automatic mutable snapshot. */
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

    /** Applies [snapshot] to its parent buffer or to global state. */
    fun apply(snapshot: MutableSnapshot): SnapshotApplyResult {
        snapshot.ensureActive()
        return if (snapshot.parent != null) {
            applyToParent(snapshot)
        } else {
            applyToGlobal(snapshot)
        }
    }

    /** Merges child writes into the parent buffer without invalidating global observers. */
    private fun applyToParent(snapshot: MutableSnapshot): SnapshotApplyResult {
        val parent = snapshot.parent ?: return SnapshotApplyResult.Success
        parent.ensureActive()
        var conflicts = 0
        val mergedWrites = LinkedHashMap<SnapshotStateObject, Any?>()
        for ((state, pending) in snapshot.writes) {
            val baseline = snapshot.inheritedValues[state]
            val previousValue = if (baseline != null) baseline.value else state.readAny(snapshot.readId)
            val appliedValue = readStateInSnapshot(parent, state)
            val resolved = resolveApplyValue(
                state = state,
                previousValue = previousValue,
                currentValue = pending.value,
                appliedValue = appliedValue,
                hasConcurrentChange = readPendingValue(parent, state)?.version != baseline?.version,
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
            writeInMutableSnapshot(parent, state, resolved)
        }
        snapshot.applied = true
        return SnapshotApplyResult.Success
    }

    /** Commits root writes to a new global version and invalidates observers outside the lock. */
    private fun applyToGlobal(snapshot: MutableSnapshot): SnapshotApplyResult {
        var conflicts = 0
        val resolvedWrites = LinkedHashMap<SnapshotStateObject, Any?>()
        val changedStates = mutableListOf<SnapshotStateObject>()
        val invalidations = LinkedHashSet<Observation>()
        synchronized(runtimeLock) {
            val appliedGlobalId = globalSnapshotId
            for ((state, pending) in snapshot.writes) {
                val previousValue = state.readAny(snapshot.readId)
                val appliedValue = state.readAny(appliedGlobalId)
                val resolved = resolveApplyValue(
                    state = state,
                    previousValue = previousValue,
                    currentValue = pending.value,
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
                snapshot.applied = true
                return SnapshotApplyResult.Success
            }
            val commitId = nextSnapshotId++
            for ((state, resolved) in resolvedWrites) {
                if (state.commitAny(commitId, resolved)) {
                    changedStates += state
                }
            }
            globalSnapshotId = commitId
            snapshot.applied = true
            changedStates.forEach { state ->
                invalidations += state.snapshotObservers()
                trackStateForPruningLocked(state)
            }
        }
        // Callbacks observe the published global view even if apply was invoked inside enter.
        // The caller's terminal or unrelated pending snapshot must not leak into notification work.
        val previousContext = contextStack.get()
        if (previousContext == null) {
            ObservationInvalidations.dispatch(invalidations)
        } else {
            contextStack.remove()
            try {
                ObservationInvalidations.dispatch(invalidations)
            } finally {
                contextStack.set(previousContext)
            }
        }
        return SnapshotApplyResult.Success
    }

    /**
     * Resolves one write during apply, accepting the candidate directly when no concurrent write exists.
     *
     * A `null` merge result denotes a conflict, so a policy cannot represent "successfully merged to
     * null" through this internal protocol.
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

    /** Writes into a mutable buffer; an equivalent value does not advance its local version. */
    private fun writeInMutableSnapshot(
        snapshot: MutableSnapshot,
        state: SnapshotStateObject,
        value: Any?,
    ) {
        snapshot.ensureActive()
        val current = readStateInSnapshot(snapshot, state)
        if (state.equivalentAny(current, value)) {
            return
        }
        snapshot.localWriteVersion += 1
        snapshot.writes[state] = SnapshotPendingValue(
            value,
            (snapshot.tokenId.toLong() shl 32) or (snapshot.localWriteVersion.toLong() and 0xFFFFFFFFL),
        )
    }

    /** Reads the current buffer, then frozen parent values, and finally the read-ID history. */
    private fun readStateInSnapshot(
        snapshot: MutableSnapshot,
        state: SnapshotStateObject,
    ): Any? {
        readPendingValue(snapshot, state)?.let { return it.value }
        return state.readAny(snapshot.readId)
    }

    /** Preserves an explicit buffered null as a hit without consulting a live parent. */
    private fun readPendingValue(
        snapshot: Snapshot,
        state: SnapshotStateObject,
    ): SnapshotPendingValue? = (snapshot as? MutableSnapshot)?.writes?.get(state)
        ?: snapshot.inheritedValues[state]

    /** Copies only pending values; committed history remains shared and pinned by read ID. */
    private fun capturePendingValues(): Map<SnapshotStateObject, SnapshotPendingValue> {
        val snapshot = contextStack.get()?.peekLast()?.snapshot ?: return emptyMap()
        snapshot.ensureActive()
        val writes = (snapshot as? MutableSnapshot)?.writes
        if (writes.isNullOrEmpty()) return snapshot.inheritedValues
        return LinkedHashMap(snapshot.inheritedValues).apply { putAll(writes) }
    }

    /** Returns the current thread's read ID, or the global version outside a snapshot. */
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

    /** Tracks [state] only while it still retains history beyond the active-snapshot boundary. */
    private fun trackStateForPruningLocked(state: SnapshotStateObject) {
        if (state.pruneRecords(activeReadIds.firstKeyOrNull())) {
            statesPendingPrune[state] = Unit
        } else {
            statesPendingPrune.remove(state)
        }
    }

    /** Retries pruning so the weak set retains only states protected by active read IDs. */
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
