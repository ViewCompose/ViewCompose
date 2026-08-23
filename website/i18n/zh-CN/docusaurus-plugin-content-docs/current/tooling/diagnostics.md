---
translation_source: tooling/diagnostics.md
translation_source_hash: e199ccaa8fb688c6844beb3c97e8f88457cbbe000c7f3b337bcdbc8db5d6053f
translation_status: current
---

# ViewCompose 诊断

## 1. 数据入口

宿主通过 `onRenderResult` 获取结构化 `RenderTreeResult`。诊断只在 `debug = true`、注册 `onRenderStats` 或注册 `onRenderResult` 时收集，普通发布路径不构造树快照和逐节点 patch 明细。

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

## 4. Demo 检查器

`Diagnostics -> 渲染器` 当前提供：

1. Render Tree 列表
2. Patch 时间线
3. 重组原因与 scope 计数
4. CompositionLocal 浏览器
5. 原有 render/layout 聚合指标

当前仍不包含真实 View 边界高亮、跨 RenderSession 关联图和逐节点耗时。这些能力以及有界的
生产失败聚合已经拆分到有效的
[诊断关联、检查与生产可观测性计划](https://docs.viewcompose.com/project/plans/diagnostics-correlation-inspection-observability)。

## 5. 已接受的扩展契约

[ADR-0021](https://docs.viewcompose.com/architecture/decisions/0021-correlated-render-diagnostics-ownership)
冻结了下一步实现边界。当前三个 Callback 将一起硬切删除，并由一套进程内、可关联 Parent 的
`RenderDiagnostics` Event Sink 替代。Host、Preview、Navigation、Lazy、Pager 与 Overlay Session
将共享一套身份模型；只关心 Failure 的 Sink 不会激活 Stats 或 Tree Collection。生产聚合位于可选
`viewcompose-diagnostics` Artifact；高亮与耗时继续按照 ADR-0009 保持在
`viewcompose-preview` 中，并且只按请求激活。

本节记录已接受的设计，不代表已经发布的行为。在有效计划的 Phase 1 合并前，第 1 节描述的 Callback
与 Collection Trigger 仍是当前 API。
