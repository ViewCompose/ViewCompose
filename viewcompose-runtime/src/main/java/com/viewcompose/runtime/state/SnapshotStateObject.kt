package com.viewcompose.runtime.state

import com.viewcompose.runtime.observation.Observation

internal interface SnapshotStateObject {
    fun readAny(readId: Int): Any?

    fun latestSnapshotId(): Int

    fun equivalentAny(
        a: Any?,
        b: Any?,
    ): Boolean

    fun mergeAny(
        previous: Any?,
        current: Any?,
        applied: Any?,
    ): Any?

    fun commitAny(
        snapshotId: Int,
        value: Any?,
    ): Boolean

    fun pruneRecords(minActiveReadId: Int?): Boolean

    fun snapshotObservers(): List<Observation>
}
