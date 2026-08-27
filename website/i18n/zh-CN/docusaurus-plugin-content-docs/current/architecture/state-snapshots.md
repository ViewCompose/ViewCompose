---
translation_source: architecture/state-snapshots.md
translation_source_hash: 56ea76e79319041f8442714b031133f82d8aed6fa46f5e5410ef2ad66f88eb5c
translation_status: current
---

# ViewCompose 状态快照

## 1. 文档定位

本文档定义 `viewcompose-runtime` 状态系统，以及 `viewcompose-ui-contract` 发布的 Renderer 连接型
状态 Owner 的 Snapshot 语义与使用约束。

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
5. `RuntimeObservation`
   - `observeReads(onInvalidated) { ... }`：创建一个可独立释放的依赖 Owner；
   - `prepareReplacement(previous) { ... }`：读取候选依赖，并返回显式 `commit`/`abort`
     替换事务。
6. Renderer 连接型状态
   - `LazyListState`：虚拟化 Item 位置与布局信息；
   - `ScrollState`：Eager Container 的逻辑偏移、范围、Viewport、运动与命令；
   - `PagerState`：当前页、已停稳页、目标页、偏移、页数、运动、能力与命令。

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
7. 一次成功的全局 Apply 会按稳定顺序去重受影响的 `Observation`，并在释放 Runtime 与 State
   Lock 后，最多在 Apply 线程调用每个 Observation 一次。不同 Apply 不会合并；冲突或无操作
   Apply 不发送失效通知。
8. 构成一个公开逻辑元组的框架字段必须使用一次现有 Mutable Snapshot Transaction。
   `synchronized` 等 Writer 串行化手段不能让多个独立 Commit 对 Snapshot Reader 原子可见。
9. Renderer 连接型 State 通过普通 `MutableState` 发布一个不可变 Snapshot。相等 Snapshot 不会
   使 Observation 或 Listener 失效。
10. 同一时间只允许一个活动 Connector。替换时先捕获旧 Connector 的最新 Snapshot、清除其
    Listener，再连接新 Connector。释放时断开连接；陈旧命令不得到达已放弃的原生 View。
11. `ScrollState.scrollTo` 会保留 Detach 目标，并在新 Eager Host Attach 后应用；
    `animateScrollTo` 在 Detach 时无操作。`PagerState` 命令在 Detach 时无操作，因为受控 Pager
    声明在重建后仍是权威来源。
12. Eager 横向偏移与 Pager 索引在 RTL 中使用逻辑顺序。原生物理位置属于 Renderer 细节，不能
    泄漏到可移植 Snapshot。
13. Observation 依赖替换会保留与已提交依赖集合共有的订阅。候选独有依赖会临时订阅同一个
    `Observation`；即使一次 Apply 同时修改旧依赖与候选依赖，也能保留最多一次的 Callback
    Identity。`commit` 在没有失效空窗的情况下切换权威集合，`abort` 则只释放候选新增项。同一
    时间只能 Prepare 一个替换，且每个替换都必须执行一个终止操作。

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
5. 新增多个框架可观察字段时，必须先判断它们是一个 Invariant 还是独立事件。只把同一 Invariant
   的写入放进 `Snapshot.withMutableSnapshot`，并用失效 Callback 读取完整元组进行测试。
6. State Connector 变更必须覆盖替换、释放、相等 Snapshot、Pending Command 与逻辑 RTL 测试。
   State 对象不得持有 Android View 或执行平台配置。
7. 框架事务重新计算长期存在的 Observed Reader 时，必须使用 Prepared Dependency Replacement。
   每个成功帧都释放并重建全部订阅，既会引入竞态风险，也会在热路径产生重复工作。

## 6. 关联文档

1. [架构总览](overview.md)
2. [性能指南](../tooling/performance.md)
3. [开发流程](../project/workflow.md)
