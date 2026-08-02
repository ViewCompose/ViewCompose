package com.viewcompose.runtime

/**
 * Defines value equivalence and concurrent-update merging for one [MutableState].
 *
 * Implementations SHOULD be deterministic and side-effect free. A policy may be called from any
 * thread that writes or applies its state, including while the snapshot runtime serializes a global
 * commit; it MUST NOT block or call back into snapshot mutation APIs.
 *
 * @sample com.viewcompose.runtime.samples.snapshotMutationPolicySample
 * @param T type of value compared and merged by this policy
 */
interface SnapshotMutationPolicy<T> {
    /**
     * Returns whether [a] and [b] represent the same observable state value.
     *
     * A `true` result suppresses the write, global-version advance, and observation invalidation.
     *
     * @param a value currently visible in the target snapshot
     * @param b candidate replacement value
     * @return `true` when replacing [a] with [b] has no observable effect
     */
    fun equivalent(
        a: T,
        b: T,
    ): Boolean

    /**
     * Returns a value that merges a snapshot update with a concurrent destination update.
     *
     * [previous] is the value visible when the applying snapshot was taken, [current] is the value
     * written by that snapshot, and [applied] is the value already present in the destination. A
     * `null` result reports an unmergeable conflict. Consequently, this API cannot express a
     * successful merge whose resulting value is `null`.
     *
     * @param previous value originally observed by the applying snapshot
     * @param current value written inside the applying snapshot
     * @param applied value currently committed in the destination snapshot or global state
     * @return the merged value, or `null` when the conflict cannot be resolved
     */
    fun merge(
        previous: T,
        current: T,
        applied: T,
    ): T?
}

/**
 * Returns the shared policy that compares values with Kotlin structural equality (`==`).
 *
 * The policy does not merge concurrent writes.
 *
 * @param T type of value compared by the policy
 * @return a stateless structural-equality policy
 */
@Suppress("UNCHECKED_CAST")
fun <T> structuralEqualityPolicy(): SnapshotMutationPolicy<T> = StructuralEqualityPolicy as SnapshotMutationPolicy<T>

/**
 * Returns the shared policy that compares values by reference identity (`===`).
 *
 * The policy does not merge concurrent writes.
 *
 * @param T type of value compared by the policy
 * @return a stateless reference-equality policy
 */
@Suppress("UNCHECKED_CAST")
fun <T> referentialEqualityPolicy(): SnapshotMutationPolicy<T> = ReferentialEqualityPolicy as SnapshotMutationPolicy<T>

/**
 * Returns the shared policy that treats every assignment as an observable change.
 *
 * The policy does not merge concurrent writes. Use it when repeating an equal value must still
 * advance the snapshot and invalidate observations.
 *
 * @param T type of value accepted by the policy
 * @return a stateless policy that never reports equivalent values
 */
@Suppress("UNCHECKED_CAST")
fun <T> neverEqualPolicy(): SnapshotMutationPolicy<T> = NeverEqualPolicy as SnapshotMutationPolicy<T>

private object StructuralEqualityPolicy : SnapshotMutationPolicy<Any?> {
    override fun equivalent(
        a: Any?,
        b: Any?,
    ): Boolean = a == b

    override fun merge(
        previous: Any?,
        current: Any?,
        applied: Any?,
    ): Any? = null
}

private object ReferentialEqualityPolicy : SnapshotMutationPolicy<Any?> {
    override fun equivalent(
        a: Any?,
        b: Any?,
    ): Boolean = a === b

    override fun merge(
        previous: Any?,
        current: Any?,
        applied: Any?,
    ): Any? = null
}

private object NeverEqualPolicy : SnapshotMutationPolicy<Any?> {
    override fun equivalent(
        a: Any?,
        b: Any?,
    ): Boolean = false

    override fun merge(
        previous: Any?,
        current: Any?,
        applied: Any?,
    ): Any? = null
}
