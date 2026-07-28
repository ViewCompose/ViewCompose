package com.viewcompose.runtime.state

import com.viewcompose.runtime.observation.Observation

/**
 * 快照运行时访问状态对象的内部桥接接口。
 * Internal bridge used by the snapshot runtime to access state objects.
 *
 * 实现需要维护按 snapshotId 排序的历史记录，并在提交后返回需要通知的观察者。
 * Implementations must maintain history ordered by snapshotId and expose observers to notify after commit.
 */
internal interface SnapshotStateObject {
    /**
     * 读取指定 readId 可见的值。
     * Reads the value visible at the specified readId.
     */
    fun readAny(readId: Int): Any?

    /**
     * 返回该状态最近一次提交的 snapshotId。
     * Returns the snapshotId of the latest committed value for this state.
     */
    fun latestSnapshotId(): Int

    /**
     * 使用状态自身策略判断两个值是否等价。
     * Uses the state's own policy to decide whether two values are equivalent.
     */
    fun equivalentAny(
        a: Any?,
        b: Any?,
    ): Boolean

    /**
     * 使用状态自身策略尝试合并并发写入。
     * Uses the state's own policy to attempt merging concurrent writes.
     */
    fun mergeAny(
        previous: Any?,
        current: Any?,
        applied: Any?,
    ): Any?

    /**
     * 将值提交为新的历史记录；返回 false 表示等价值无需提交。
     * Commits a value as a new history record; false means an equivalent value did not need committing.
     */
    fun commitAny(
        snapshotId: Int,
        value: Any?,
    ): Boolean

    /**
     * 裁剪不再被活跃快照读取的历史记录。
     * Prunes history records no longer needed by active snapshots.
     */
    fun pruneRecords(minActiveReadId: Int?): Boolean

    /**
     * 返回当前观察者快照，调用方在锁外执行失效通知。
     * Returns a snapshot of observers so callers can invalidate them outside locks.
     */
    fun snapshotObservers(): List<Observation>
}
