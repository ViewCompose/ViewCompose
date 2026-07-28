package com.viewcompose.runtime

/**
 * 快照状态的变更策略，定义“是否真的变化”和“并发写入能否合并”。
 * Mutation policy for snapshot state, defining real change detection and concurrent-write merging.
 */
interface SnapshotMutationPolicy<T> {
    /**
     * 判断两个值是否等价，等价写入不会触发提交或观察者失效。
     * Returns whether two values are equivalent; equivalent writes do not commit or invalidate observers.
     */
    fun equivalent(
        a: T,
        b: T,
    ): Boolean

    /**
     * 尝试合并并发快照写入；返回 null 表示冲突无法自动解决。
     * Attempts to merge concurrent snapshot writes; returning null means the conflict is unresolved.
     */
    fun merge(
        previous: T,
        current: T,
        applied: T,
    ): T?
}

/**
 * 使用 `==` 判断等价性的默认策略。
 * Default policy that uses `==` for equivalence.
 */
@Suppress("UNCHECKED_CAST")
fun <T> structuralEqualityPolicy(): SnapshotMutationPolicy<T> = StructuralEqualityPolicy as SnapshotMutationPolicy<T>

/**
 * 使用引用相等 `===` 判断等价性的策略。
 * Policy that uses referential equality `===` for equivalence.
 */
@Suppress("UNCHECKED_CAST")
fun <T> referentialEqualityPolicy(): SnapshotMutationPolicy<T> = ReferentialEqualityPolicy as SnapshotMutationPolicy<T>

/**
 * 永远认为写入发生变化的策略，适合事件型或强制刷新场景。
 * Policy that treats every write as a change, useful for event-like state or forced refreshes.
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
