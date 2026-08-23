---
translation_source: tooling/diagnostics.md
translation_source_hash: 87501ce4c5dbd1e1f8b7df526895604cbea797d087562e5b1cc9d8c7db774c10
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
状态切换，终态 End 在清理后发布；候选准备在激活前保持静默。投递按 Session 同步串行，重入立即
失败；Sink 抛错会被记录并禁用，但不改变恢复结论，也不会递归发布。

## 5. 从旧 Callback 迁移

Alpha API 无适配层地移除了三个 Callback 与仅 Result Local。Stats/Tree 从
`RenderFrameCompleted` 读取，Failure 从 `RenderFailureObserved` 读取；不需要事件流时直接查询
`lastFrameReport` / `lastRenderFailure`。

## 6. Demo 检查器

`Diagnostics -> 渲染器` 提供 Render Tree、Patch 时间线、重组原因、CompositionLocal 浏览器与
聚合指标。跨 Session 关联已实现；真实 View 边界高亮、逐节点耗时和有界生产失败聚合仍由有效的
[诊断关联、检查与生产可观测性计划](https://docs.viewcompose.com/project/plans/diagnostics-correlation-inspection-observability)。

## 7. 剩余扩展契约

[ADR-0021](https://docs.viewcompose.com/architecture/decisions/0021-correlated-render-diagnostics-ownership)
冻结 Phase 1；只关心 Failure 的 Sink 不激活 Frame 明细。可选 `viewcompose-diagnostics`
负责生产聚合，`viewcompose-preview` 让高亮与耗时保持按请求激活。有效计划负责交付与 Inspector 收尾。
