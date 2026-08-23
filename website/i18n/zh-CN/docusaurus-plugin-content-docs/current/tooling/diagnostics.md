---
translation_source: tooling/diagnostics.md
translation_source_hash: d7393da019c93d432fde2fce4a9c4dfcdb03aaf860c2854b100a2be68037fc5f
translation_status: current
---

# ViewCompose 诊断

## 1. 关联事件入口

在 Host 或 Preview 根节点安装一个不可变 `RenderDiagnostics`。Navigation、Lazy、Pager 与
Overlay 子 Session 会继承该 Sink，并获得进程内 Session ID 与 Parent ID。底层嵌套 Session
若显式传入新的 Diagnostics，则会有意开启一棵新的关联树。

```kotlin
val diagnostics = RenderDiagnostics(
    collection = RenderDiagnosticCollection(
        lifecycle = true,
        failures = true,
        frameLevel = RenderFrameDiagnosticLevel.Tree,
    ),
    sink = { event ->
        when (event) {
            is RenderFrameCompleted -> inspect(event.context, event.report, event.tree)
            is RenderFailureObserved -> report(event.context, event.failure)
            else -> recordLifecycle(event)
        }
    },
)

val session = renderInto(container = root, diagnostics = diagnostics) { App() }
```

`None` 不构建 Renderer 计数或树明细；`Stats` 只构建聚合计数；`Tree` 还会构建有界的树、
Patch、Warning 与 Composition 诊断。`debug` 只控制日志与慢操作告警，不选择事件收集等级。

`RenderTreeResult` 当前包含：

1. `stats / structure / warnings`：聚合绑定、树规模和告警。
2. `tree`：renderer 实际消费的节点树，保留节点类型、key 和父子关系。
3. `patches`：本帧按执行顺序记录 `Insert / Remove / Rebind / Patch / SkipSelf / SkipSubtree`，并标记父 key、位置、移动和 patch 类型。
4. `composition`：失效、重组、跳过 scope 数量，以及每个 scope 的路径、签名、重组原因和 Local 快照。

## 2. 重组原因

运行时区分以下原因：

1. `InitialComposition`
2. `StateInvalidation`
3. `AncestorInvalidation`
4. `InputsChanged`
5. `ExplicitRequest`
6. `StructureChanged`

scope 诊断上限为 500 条，签名会截断，避免诊断本身随页面规模无界增长。

## 3. CompositionLocal 诊断

`uiLocalOf(debugName = ..., debugValueFormatter = ...)` 可提供稳定名称和安全摘要。框架内置 Theme、Environment、LifecycleOwner、SavedState、ContentColor 等核心 Local 已命名。

默认摘要规则只直接展示字符串、数值、布尔、字符和枚举；其他对象只展示类型名，不调用任意业务对象的 `toString()`。敏感业务值应通过自定义 formatter 主动裁剪，或不提供 formatter。

## 4. 顺序与失败隔离

订阅 Lifecycle 时，Start 一定是首个事件。Failure 在恢复结论明确后发布；每次同步尝试都会在
`lastFrameReport` 成为权威结果后发布一个 `RenderFrameCompleted`。Activity 事件只表示真实
状态切换；End 在清理完成后发布并且是终态。成功的候选准备在激活前保持静默；准备失败只发布
Start、Failure、回滚 Frame 与 End。

Sink 调用是同步的，并在单个 Session 内串行。重入当前 Session 会立即失败。Sink 抛错时，框架
会记录平台日志，把错误保存为本 Session 的 `DiagnosticsSink` Failure，并禁用该 Sink；它不能
改变 Frame Report、替换原始恢复结论或递归发布事件。

## 5. 从旧 Callback 迁移

Alpha API 一次性移除了 `onRenderStats`、`onRenderResult` 与 `onRenderFailure`。Stats 改从
`RenderFrameCompleted.stats` 读取，Tree 改从 `RenderFrameCompleted.tree` 读取，Failure 改从
`RenderFailureObserved.failure` 读取。没有 Deprecated Overload 或仅 Result Local 适配层。
不需要事件流时仍可直接查询 `lastFrameReport` 与 `lastRenderFailure`。

## 6. Demo 检查器

`Diagnostics -> 渲染器` 当前提供：

1. Render Tree 列表
2. Patch 时间线
3. 重组原因与 scope 计数
4. CompositionLocal 浏览器
5. 原有 render/layout 聚合指标

跨 RenderSession 关联已经实现。当前仍不包含真实 View 边界高亮和逐节点耗时。这些能力以及
有界的生产失败聚合已经拆分到有效的
[诊断关联、检查与生产可观测性计划](https://docs.viewcompose.com/project/plans/diagnostics-correlation-inspection-observability)。

## 7. 剩余扩展契约

[ADR-0021](https://docs.viewcompose.com/architecture/decisions/0021-correlated-render-diagnostics-ownership)
冻结了已经实现的 Phase 1 边界。Host、Preview、Navigation、Lazy、Pager 与 Overlay Session
现已共享一套身份模型；只关心 Failure 的 Sink 不会激活 Stats 或 Tree Collection。生产聚合位于可选
`viewcompose-diagnostics` Artifact；高亮与耗时继续按照 ADR-0009 保持在
`viewcompose-preview` 中，并且只按请求激活。

有效计划继续负责生产聚合、高亮、耗时与 Inspector 收尾。
