package com.viewcompose.runtime

import com.viewcompose.runtime.observation.RuntimeObservation
import com.viewcompose.runtime.state.DerivedStateImpl
import com.viewcompose.runtime.state.MutableStateImpl

/**
 * Exposes a value whose reads participate in snapshot consistency and runtime observation.
 *
 * Reading [value] inside [RuntimeObservation.observeReads] subscribes the returned observation to
 * future invalidations from this state. Reading inside [Snapshot.enter] returns the version visible
 * to that snapshot.
 *
 * @param T type of value exposed by this state
 */
interface State<T> {
    /**
     * Returns the value visible in the current snapshot context.
     *
     * The read is registered with the active runtime observation, if one exists.
     */
    val value: T
}

/**
 * Exposes snapshot-managed state that can be read and updated.
 *
 * Assigning [value] inside [MutableSnapshot.enter] buffers the update in that snapshot. Assigning
 * outside a mutable snapshot creates and applies an automatic mutable snapshot. Equivalent values,
 * as defined by the state's [SnapshotMutationPolicy], do not create a new global version or notify
 * observations.
 *
 * @param T type of value stored by this state
 */
interface MutableState<T> : State<T> {
    /**
     * Returns or updates the value visible in the current snapshot context.
     *
     * A write may throw [SnapshotApplyConflictException] when an automatic snapshot encounters an
     * unmergeable concurrent update. Observation callbacks run on the thread that applies the
     * successful write.
     */
    override var value: T
}

/**
 * Creates snapshot-managed mutable state initialized with [value].
 *
 * [policy] controls whether a write changes the state and whether concurrent snapshot updates can
 * be merged. The returned state participates in [Snapshot] reads and [RuntimeObservation]
 * subscriptions.
 *
 * @sample com.viewcompose.runtime.samples.mutableStateSample
 * @param T type of value stored by the state
 * @param value initial value visible at the current global snapshot
 * @param policy equivalence and concurrent-merge policy used for every update
 * @return a new independently owned mutable state
 */
fun <T> mutableStateOf(
    value: T,
    policy: SnapshotMutationPolicy<T> = structuralEqualityPolicy(),
): MutableState<T> = MutableStateImpl(
    initialValue = value,
    policy = policy,
)

/**
 * Creates read-only state that computes [block] lazily and observes the states read by it.
 *
 * The first [State.value] read evaluates [block] and caches its result for the current snapshot read
 * token. Dependency invalidation marks the derived state dirty and invalidates its observers; the
 * next read recomputes the value. Equal derived results are not suppressed. The returned state is
 * intended for thread-confined composition use and does not synchronize concurrent reads.
 *
 * @sample com.viewcompose.runtime.samples.derivedStateSample
 * @param T type of value produced by the calculation
 * @param block calculation whose snapshot-state reads become dependencies
 * @return a lazily evaluated read-only state
 */
fun <T> derivedStateOf(block: () -> T): State<T> = DerivedStateImpl(block)
