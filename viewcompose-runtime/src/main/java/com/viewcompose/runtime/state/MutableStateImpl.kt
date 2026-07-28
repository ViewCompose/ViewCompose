package com.viewcompose.runtime.state

import com.viewcompose.runtime.MutableState
import com.viewcompose.runtime.SnapshotMutationPolicy
import com.viewcompose.runtime.SnapshotRuntime
import com.viewcompose.runtime.observation.ObservableState
import com.viewcompose.runtime.observation.Observation
import com.viewcompose.runtime.observation.RuntimeObservation

/**
 * MutableState 的快照化实现，使用链表保存历史记录以支持旧 readId 读取。
 * Snapshot-backed MutableState implementation that stores history records in a linked list for old readIds.
 *
 * recordLock 保护值历史，observerLock 保护观察者集合，避免提交通知时持有状态锁。
 * recordLock protects value history, while observerLock protects observers so commit notification avoids state locks.
 */
internal class MutableStateImpl<T>(
    initialValue: T,
    private val policy: SnapshotMutationPolicy<T>,
) : MutableState<T>, ObservableState, SnapshotStateObject {
    private val recordLock = Any()
    private val observerLock = Any()
    private val observers = LinkedHashSet<Observation>()
    private var head: StateRecord<T> = StateRecord(
        snapshotId = 0,
        value = initialValue,
        next = null,
    )

    override var value: T
        get() {
            RuntimeObservation.recordRead(this)
            @Suppress("UNCHECKED_CAST")
            return SnapshotRuntime.readStateValue(this) as T
        }
        set(value) {
            SnapshotRuntime.writeStateValue(this, value)
        }

    override fun addObserver(observer: Observation) {
        synchronized(observerLock) {
            observers += observer
        }
    }

    override fun removeObserver(observer: Observation) {
        synchronized(observerLock) {
            observers -= observer
        }
    }

    override fun readAny(readId: Int): Any? = synchronized(recordLock) {
        readRecordLocked(readId).value
    }

    override fun latestSnapshotId(): Int = synchronized(recordLock) {
        head.snapshotId
    }

    override fun equivalentAny(
        a: Any?,
        b: Any?,
    ): Boolean {
        @Suppress("UNCHECKED_CAST")
        return policy.equivalent(a as T, b as T)
    }

    override fun mergeAny(
        previous: Any?,
        current: Any?,
        applied: Any?,
    ): Any? {
        @Suppress("UNCHECKED_CAST")
        return policy.merge(
            previous = previous as T,
            current = current as T,
            applied = applied as T,
        )
    }

    override fun commitAny(
        snapshotId: Int,
        value: Any?,
    ): Boolean = synchronized(recordLock) {
        @Suppress("UNCHECKED_CAST")
        val next = value as T
        val applied = head.value
        if (policy.equivalent(applied, next)) {
            return@synchronized false
        }
        head = StateRecord(
            snapshotId = snapshotId,
            value = next,
            next = head,
        )
        true
    }

    override fun pruneRecords(minActiveReadId: Int?): Boolean = synchronized(recordLock) {
        head.next ?: return@synchronized false
        if (minActiveReadId == null) {
            head.next = null
            return@synchronized false
        }
        var oldestRequired = head
        while (oldestRequired.snapshotId > minActiveReadId) {
            oldestRequired = oldestRequired.next ?: break
        }
        oldestRequired.next = null
        head.next != null
    }

    override fun snapshotObservers(): List<Observation> = synchronized(observerLock) {
        observers.toList()
    }

    internal fun recordCount(): Int = synchronized(recordLock) {
        var count = 0
        var current: StateRecord<T>? = head
        while (current != null) {
            count += 1
            current = current.next
        }
        count
        }

    /**
     * 根据 readId 查找最近且不晚于该版本的记录。
     * Finds the newest record whose snapshotId is not newer than the readId.
     */
    private fun readRecordLocked(readId: Int): StateRecord<T> {
        var current: StateRecord<T>? = head
        while (current != null) {
            if (current.snapshotId <= readId) {
                return current
            }
            current = current.next
        }
        var oldest = head
        var cursor = head.next
        while (cursor != null) {
            oldest = cursor
            cursor = cursor.next
        }
        return oldest
    }

    /**
     * 单个状态值版本记录，head 始终指向最新提交。
     * Version record for one state value; head always points to the latest commit.
     */
    private data class StateRecord<T>(
        val snapshotId: Int,
        val value: T,
        var next: StateRecord<T>?,
    )
}
