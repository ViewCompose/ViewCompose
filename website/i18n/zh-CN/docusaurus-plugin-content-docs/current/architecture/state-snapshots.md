---
translation_source: architecture/state-snapshots.md
translation_source_hash: bebfba26a47a9f6643f463a4e7e32f3b1e6f6e6d78087bdd41405b9236826604
translation_status: current
---

# ViewCompose 状态快照

## 1. 文档定位

本文档定义 `viewcompose-runtime` 状态系统的 snapshot 语义与使用约束。

目标：

1. 统一 `MutableState` 的一致性读写语义
2. 明确并发写入冲突处理规则
3. 防止后续演进回退到“直接赋值 + 无事务”模型

## 2. 公开 API

1. `mutableStateOf(value, policy)`
   默认策略：`structuralEqualityPolicy()`
2. `SnapshotMutationPolicy<T>`
   - `equivalent(a, b)`：判定是否视为无变化
   - `merge(previous, current, applied)`：并发冲突合并，返回 `null` 代表无法合并
3. `Snapshot`
   - `takeSnapshot()`
   - `takeMutableSnapshot()`
   - `withMutableSnapshot { ... }`
   - `currentGlobalId()`
4. `MutableSnapshot`
   - `enter { ... }`
   - `apply()`
   - `dispose()`

## 3. 核心语义

1. `MutableState` 基于 MVCC `StateRecord` 记录链实现，读取按 `readId` 选择可见版本。
2. `state.value = x` 在无显式 snapshot 上下文时，内部走 autocommit 事务（`takeMutableSnapshot + apply`）。
3. `MutableSnapshot.apply()` 为串行发布：
   - 无冲突：直接提交
   - 有冲突：走 `policy.merge(previous, current, applied)`
   - merge 失败：`apply()` 返回 `Failure`
4. 读快照隔离：`Snapshot.takeSnapshot().enter { ... }` 始终读取该快照可见版本，不受后续全局提交影响。
5. `ComposerLite` 每轮 compose 在一致性读快照中执行；同一轮内读取结果不漂移。
6. Runtime 跟踪活动 snapshot 的 `readId`；提交时保留活动读者所需版本，snapshot 释放后裁剪不再可见的历史记录。

## 4. 并发与冲突约束

1. 冲突判定以状态记录版本为准：目标状态在事务 `readId` 后产生新记录时视为并发写入。
2. `equivalent(a, b)` 只负责判断一次赋值是否产生新记录，不用于推断事务是否并发。
3. 冲突默认不覆盖；仅当 `merge` 提供可合并值时才可提交。
4. 未提供 merge 能力（默认 policy）时，冲突应失败，由上层重试策略决定下一步。

## 5. 开发约束

1. 禁止在 runtime 新增绕过 snapshot 的状态写入路径。
2. 新状态容器若接入 `RuntimeObservation`，必须实现 snapshot 可见性语义。
3. 修改策略或冲突语义时，必须同步补齐并发事务单测与 compose 一致性单测。
4. `Snapshot`/`MutableSnapshot` 使用完成后必须调用 `dispose()` 或通过 `use` 关闭，避免长期保留历史版本。

## 6. 关联文档

1. [架构总览](overview.md)
2. [性能指南](../tooling/performance.md)
3. [开发流程](../project/workflow.md)
