package com.viewcompose.runtime

import com.viewcompose.runtime.state.DerivedStateImpl
import com.viewcompose.runtime.state.MutableStateImpl

/**
 * 只读状态接口，读取 value 时会被当前观察上下文记录。
 * Read-only state contract; reading value is recorded by the current observation context.
 */
interface State<T> {
    val value: T
}

/**
 * 可写状态接口，写入 value 会进入当前可变快照或自动创建一次全局提交。
 * Writable state contract; assigning value writes into the current mutable snapshot or an automatic global commit.
 */
interface MutableState<T> : State<T> {
    override var value: T
}

/**
 * 创建受快照系统管理的可变状态。
 * Creates mutable state managed by the snapshot system.
 *
 * policy 决定相等性判断和并发快照冲突合并策略。
 * policy controls equality checks and concurrent snapshot conflict merging.
 */
fun <T> mutableStateOf(
    value: T,
    policy: SnapshotMutationPolicy<T> = structuralEqualityPolicy(),
): MutableState<T> = MutableStateImpl(
    initialValue = value,
    policy = policy,
)

/**
 * 创建按需计算并追踪依赖读取的派生状态。
 * Creates derived state that computes lazily and tracks state reads as dependencies.
 */
fun <T> derivedStateOf(block: () -> T): State<T> = DerivedStateImpl(block)
