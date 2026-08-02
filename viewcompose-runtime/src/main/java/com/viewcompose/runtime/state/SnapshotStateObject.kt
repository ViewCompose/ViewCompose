package com.viewcompose.runtime.state

import com.viewcompose.runtime.observation.Observation

/**
 * Internal bridge through which the snapshot runtime reads and commits a state object.
 *
 * Implementations maintain history ordered by snapshot ID and expose an observer snapshot that the
 * runtime can invalidate after releasing its commit lock.
 */
internal interface SnapshotStateObject {
    /** Reads the value visible at [readId]. */
    fun readAny(readId: Int): Any?

    /** Returns the snapshot ID of this object's latest committed value. */
    fun latestSnapshotId(): Int

    /** Uses the object's mutation policy to decide whether [a] and [b] are equivalent. */
    fun equivalentAny(
        a: Any?,
        b: Any?,
    ): Boolean

    /** Uses the object's mutation policy to merge concurrent snapshot values. */
    fun mergeAny(
        previous: Any?,
        current: Any?,
        applied: Any?,
    ): Any?

    /** Commits a history record, returning `false` when [value] is equivalent to the current value. */
    fun commitAny(
        snapshotId: Int,
        value: Any?,
    ): Boolean

    /** Prunes records no longer required by a snapshot at or after [minActiveReadId]. */
    fun pruneRecords(minActiveReadId: Int?): Boolean

    /** Returns a stable observer copy that callers may invalidate without holding state locks. */
    fun snapshotObservers(): List<Observation>
}
