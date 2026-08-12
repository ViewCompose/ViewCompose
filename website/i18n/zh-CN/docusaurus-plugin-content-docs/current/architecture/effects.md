---
translation_source: architecture/effects.md
translation_source_hash: 8d2d6cba5f0ed92b7dc7118c9029f9c3c09625209560c6695325e2bba1162a35
translation_status: current
---

# 事务式 Effect 与结构化工作

ViewCompose Effect 把成功的声明式帧连接到命令式工作。它们属于渲染事务，而不是仅仅因为 DSL
函数被求值就运行的回调。

## 帧顺序

标准 `RenderSession` 会先准备组合并渲染候选 View Tree。渲染器让该树成为权威状态后，Runtime
提交 Remember 值，然后依次执行：

1. Committed Value 发布；
2. 退出的 Remember、Disposable 与 Launched Effect 生命周期；
3. 进入的 Remember、Disposable 与 Launched Effect 生命周期；
4. 按声明顺序执行 `SideEffect` 回调；
5. 执行渲染器所有的原生 `AndroidView.onCommit` 回调；
6. 协调 Overlay 并发布诊断。

原生树提交前发生错误会 Abort 候选。候选 Effect 不会启动，之前已提交的 Effect 继续活跃。上述
列表中发生的错误表示帧已经成为权威状态。Runtime 会按已提交帧失败报告，仍尝试每个独立同步
操作，并允许后续渲染继续进行。

同步 Remember 生命周期与 `SideEffect` 失败会保留原始 Throwable，并附加有界 Suppressed
Metadata，其中包含 Effect Kind、Operation、结构 Scope、Slot 和不持有原 Key 对象的摘要。
`RenderFailure` 提供 Host Frame ID。Debug Render Session 还会在这些回调占用至少一个 16 ms
帧时发出警告；Release Session 不执行计时工作。

## 选择 Effect

| 需求 | API | 生命周期 |
| --- | --- | --- |
| 每次成功帧后同步发布值 | `SideEffect { ... }` | 每次成功调用执行一次 |
| 只在显式身份变化时发布 | `SideEffect(key) { ... }` | 首次提交与 Key 变化 |
| 订阅或持有需要成对清理的资源 | `DisposableEffect(key) { ... }` | Key 或结构存在性 |
| 运行 Composition Scope 的挂起工作 | `LaunchedEffect(key) { ... }` | Key 或结构存在性 |
| 从事件回调启动工作 | `rememberCoroutineScope()` | Remember Scope 的结构存在性 |
| 让运行中的 Effect 指向最新回调或值 | `rememberUpdatedState(value)` | 稳定 Holder；候选值在 Commit 时发布 |
| 从挂起工作生产 Observable State | `produceState(...)` | 稳定 State Holder 与带 Key Producer |
| 把成对工作绑定到 Android Started/Resumed 状态 | Lifecycle 集成 Effect | Lifecycle State 与 Composition 存在性 |

`DisposableEffect` Setup 返回 `onDispose { ... }`，且至少需要一个 Key。每次成功 Setup 都会收到
一个终止 Cleanup。替换 Setup 前先运行 Cleanup。Setup 抛出异常时不存在 Cleanup；Cleanup 抛出
异常后也不会再次调用。

`LaunchedEffect` 用于因进入一个声明式身份而产生的工作。事件处理器应通过 Remember Scope 启动
工作，不要只是为了重启工作而把事件值塞进 Key。

## Key 与结构身份

Key 使用结构相等性比较。它们决定一个位置 Effect 是被保留还是替换，并不会让无关调用点获得
全局唯一身份。ViewCompose 没有 Compose Compiler 来生成调用点 Group。重复、条件插入或重排
Effect 的代码必须在结构边界使用稳定 `key(...)` Group。Lazy 容器还要求自己的稳定 Item Key。

无 Key 的 `SideEffect` 有意采用不同语义：每次成功调用后都会执行。只需在变化时同步发布，应
使用带 Key 重载。

## Rollback 与 `rememberUpdatedState`

`rememberUpdatedState` 使用一个稳定 Holder。在候选组合期间，来自该组合的读取会看到候选值，
因此声明代码保持一致。已经运行的 Effect 在帧提交前继续看到已提交值。Abort 会丢弃候选值；
成功提交会在任何退出或进入生命周期回调前发布它。

该 API 用于更新长生命周期 Effect 捕获的值。普通 UI 数据应直接读取其源 `State`；若在发射的
UI 中读取该 Holder，Committed Value 发布时可能产生一次后续失效。

## Coroutine 所有权

每个 `RenderSession` 在平台安装的 Coroutine Context 中持有一个 Supervisor Root。因此某个子
任务失败不会销毁整个 Session 的独立 Composition Scope。`LaunchedEffect` 在该 Root 下持有一个
Job。Remember Coroutine Scope 持有普通子 Job，所以失败和取消在该 Scope 内保持结构化，而
不是被另一个 Supervisor 隐藏。

替换 Key 或离开 Composition 会请求取消。Cancellation Cleanup 可以挂起；Runtime 保证先请求
取消再启动替换工作，但不保证任意 Non-cancellable Cleanup 已同步结束。释放 Session 时，会先
取消 Session Coroutine Root，再释放已挂载 View 和 Composition 资源。

Render 不活跃不是 Coroutine Pause 信号。必须在低于 `STARTED` 或 `RESUMED` 时停止的工作，应
使用 `viewcompose-lifecycle-androidx`。

## Local 与 Android Capability

声明 Effect 时先解析 Composition Scope 值，然后捕获结果：

```kotlin
val theme = Theme.current
val window = activity.window

SideEffect(theme, window) {
    window.applyWindowAppearance(theme)
}
```

不要第一次在 Effect 回调内部读取 `Theme.current`、`Environment` 或其他仅在 Context 中有效的
Local。此时 Provider Stack 已经返回，异步回调还可能超出其生命周期。ViewCompose 不会隐式
保留并重新安装每个 Local。延迟子组合会走专用的 Captured Local Snapshot 路径。内置同步与
Coroutine Effect Scope 会使用 Local Diagnostic Name 拒绝 Local 读取，即使回调线程上恰好有
另一个无关 Provider 处于活跃状态也不会读取它。

Android Resource、Activity/Window Capability、Lifecycle Ownership 与具名 Design System
Token 是独立契约。选择其中一项不表示其他项一定存在。

## 把 Snapshot State 转换为 Flow

`snapshotFlow { query() }` 是 Cold Flow，并为每个 Collector 创建独立的 Snapshot Read
Observation。它会发出初始 Query 结果，在读取依赖变化后重新运行，条件读取变化时替换依赖集，
合并待处理失效，并抑制结构相等的结果。取消或计算失败会从所有已观察 State 分离。

计算运行次数可能多于 Emission 次数，因此必须幂等且无副作用。计算在 Collector Coroutine
而不是 State Writing Thread 中运行。Snapshot Collection 仍不受支持；集合值需要加入该观察
模型时，应把不可变集合存入 `MutableState`。

## Compose 对比边界

只要 ViewCompose 能保护对应行为，上述可观察生命周期、Key、Rollback 与串行规则就会有意
对齐。以下 Compose 能力依赖其 Compiler/Runtime 协议，不能从相似 API 名称推导：

- 编译器生成的 Restart 与 Replaceable Group；
- 自动调用点身份与 Changed Flag；
- 稳定性推断与 Smart Skipping；
- 对 Composable 和非 Composable 调用的编译期限制；
- 任意兄弟重排的完整 Movable Group 语义；以及
- 相同的 Recomposer Apply Dispatcher、Frame Clock、Tooling Metadata 或 Stack Trace。

ViewCompose 在这些边界使用显式 DSL Group、Runtime 校验、稳定 Key 与诊断。迁移时应保留 Effect
所有权和事务语义，不应假设源码名称相同就表示完全等价。

## 相关文档

- [State 与 Snapshot 架构](./state-snapshots.md)
- [渲染失败与 Android 互操作 Effect](./render-failures.md)
- [Lifecycle 与 SavedState](./lifecycle-and-saved-state.md)
- [Compose State 与重组迁移](../migration/compose-state-recomposition-and-restoration.md)
- [ADR-0008：事务式 Effect 生命周期](./decisions/0008-transactional-effect-lifecycle.md)
