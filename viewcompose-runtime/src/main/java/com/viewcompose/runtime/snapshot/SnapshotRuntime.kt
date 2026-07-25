package com.viewcompose.runtime

import com.viewcompose.runtime.observation.Observation
import com.viewcompose.runtime.state.SnapshotStateObject
import java.util.ArrayDeque
import java.util.TreeMap
import java.util.WeakHashMap

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
    private val contextStack = ThreadLocal<ArrayDeque<SnapshotContext>?>()
    private val activeReadIds = TreeMap<Int, Int>()
    private val statesPendingPrune = WeakHashMap<SnapshotStateObject, Unit>()
    private var globalSnapshotId: Int = 0
    private var nextSnapshotId: Int = 1

    fun currentGlobalId(): Int = synchronized(runtimeLock) { globalSnapshotId }

    fun takeSnapshot(): Snapshot = synchronized(runtimeLock) {
        val readId = currentContextReadId() ?: globalSnapshotId
        registerSnapshotLocked(readId)
        Snapshot(readId = readId)
    }

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

    fun currentReadToken(): Long {
        val mutable = currentMutableSnapshot()
        val readId = mutable?.readId ?: currentReadId()
        val version = mutable?.localWriteVersion ?: 0
        return (readId.toLong() shl 32) or (version.toLong() and 0xFFFFFFFFL)
    }

    fun readStateValue(state: SnapshotStateObject): Any? {
        val mutable = currentMutableSnapshot()
        if (mutable != null) {
            readPendingValue(mutable, state)?.let { return it.value }
        }
        return state.readAny(currentReadId())
    }

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

    fun apply(snapshot: MutableSnapshot): SnapshotApplyResult {
        snapshot.ensureActive()
        return if (snapshot.parent != null) {
            applyToParent(snapshot)
        } else {
            applyToGlobal(snapshot)
        }
    }

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

    private fun readStateInSnapshot(
        snapshot: MutableSnapshot,
        state: SnapshotStateObject,
    ): Any? {
        readPendingValue(snapshot, state)?.let { return it.value }
        return state.readAny(snapshot.readId)
    }

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

    private fun trackStateForPruningLocked(state: SnapshotStateObject) {
        if (state.pruneRecords(activeReadIds.firstKeyOrNull())) {
            statesPendingPrune[state] = Unit
        } else {
            statesPendingPrune.remove(state)
        }
    }

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
