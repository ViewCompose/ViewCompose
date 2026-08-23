---
translation_source: tooling/diagnostics.md
translation_source_hash: d21f28eb3e9e533f95f496d2449fb55da26591bcb108500f5b7a9c9060fd98b5
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

## 6. 有界生产故障聚合

可选 `viewcompose-diagnostics` 产物现已提供 `BoundedRenderFailureAggregator`。把 Lifecycle
关闭、Failure 打开并把 Frame Level 设为 `None`，即可统计重复结构化故障，而不会激活 Frame Tree
或调试检查。固定隐私指纹会排除消息、应用栈帧、文件与行号、原始 Key、View 文本、Local 值、Cause
和原始 `Throwable`。

聚合由应用持有且线程安全，默认容量为 64 个指纹，硬上限 128。默认 15 分钟单调时间窗口只在记录
或 Snapshot 时惰性过期；不可变 Snapshot 的计数器会报告最久未更新淘汰和计数饱和。存储、用户同意、
调度、上传、厂商元数据和下游失败策略都保留给应用。详见
[模块手册](https://docs.viewcompose.com/zh-CN/modules/viewcompose-diagnostics)。

## 7. 按请求高亮 Mounted Node

通过 `debugImplementation` 引入 `viewcompose-preview`，让可调试应用保持前台，然后选择
**Tools → Highlight Device DSL Node**。Studio 会先选择一个关联后的可见 Session，请求一份有界的
当前 Mounted Tree 快照，再列出声明式节点。选中后会绘制该节点真实 Android View 的裁剪后边界，
最长保留五秒；**Tools → Clear Device DSL Highlight** 可立即清除。

Token 不透明、只在当前进程和快照内有效，不包含应用 Key。新快照、节点替换、View 被另一逻辑 Owner
复用、Session 释放或进程重启都会使其失效。响应会区分 Selected、Partially Clipped、Missing、
Stale、Recycled、Hidden、Fully Clipped、Synthetic/Unsupported、Ended、Rejected 与 Cleared。
Bounds 同时提供屏幕坐标和全局可见裁剪矩形。

请求最多访问 2,048 个 Mounted Node，返回 512 个、深度不超过 64；只保留弱 Native Target，序列化
结果不超过 256 KiB。未激活时不会遍历、读取几何、修改 Overlay、写报告或安装 Listener。激活后的
Overlay 不可交互，不能触发重组、应用 Callback、Focus 或 Accessibility Focus 变化，不拦截输入，
也不修改布局。`Diagnostics → Renderer` 页面提供唯一 AndroidView 目标与替换动作，便于确定性人工验收。

## 8. 有限逐节点耗时

通过 `debugImplementation` 引入 `viewcompose-preview`，让可调试应用保持前台，然后选择
**Tools → Inspect Device Node Timing**。Studio 会选择一个关联后的可见 Session，启动一次显式采样，
并等待开发者触发待排查的交互。`Diagnostics → Renderer` 页面提供 **Run 8-frame timing workload**；
可见计数器会从 `0/8` 推进到 `8/8`，人工验收无需依赖不可见的状态变化。

每次采集最多经过八次已完成 Frame Attempt 或两秒单调时间后自动停止。它只记录实际执行的组合
Scope、Renderer Reconciliation 与直接 Native Binding。组合和 Reconciliation 同时报告 Inclusive
与 Self Duration，Binding 报告 Direct Duration。一个不透明、仅当前 Capture 有效的 Node Token
连接各阶段，不公开应用 Key；被跳过的 Scope 不调用计时接口，也不读取时钟。

响应每帧最多保留 64 个节点，总计 512 条聚合记录，深度不超过 32；最多保留 128 个不同字符串，
每个不超过 256 字符，JSON 总量不超过 256 KiB。结果会报告 Attempted/Retained Clock Read、空计时对
开销估计、Unsupported Domain、Drop、Truncation、Completion 与结束原因。Studio 只按可相加的
Self/Direct 记录排序，避免重复累加父级 Inclusive 时间。首版契约明确不包含 Measure/Layout/Draw、
GPU、RenderThread、SurfaceFlinger、图片解码、网络、数据库和外部 SDK；这些领域应使用平台 Profiler。

同一进程只能有一个活动 Capture。普通渲染不提供 Collector，因此逐节点时钟读取、计时记录分配、
报告写入、轮询与持续观察都为零。耗时结果是诊断证据，不是 Frame-time Benchmark；插桩开销和有限
样本量都会作为显式限制保留。

## 9. Demo 检查器

`Diagnostics -> 渲染器` 提供 Render Tree、Patch 时间线、重组原因、CompositionLocal 浏览器与
聚合指标、Mounted-node 高亮 Fixture 与显式八帧耗时工作负载。跨 Session 关联、生产聚合、真实
View 边界高亮和有限逐节点耗时已经实现；最终 Inspector 与性能收尾仍由有效的
[诊断关联、检查与生产可观测性计划](https://docs.viewcompose.com/project/plans/diagnostics-correlation-inspection-observability)负责。

## 10. 剩余扩展契约

[ADR-0021](https://docs.viewcompose.com/architecture/decisions/0021-correlated-render-diagnostics-ownership)
冻结 Phase 1；只关心 Failure 的 Sink 不激活 Frame 明细。可选 `viewcompose-diagnostics`
现已负责生产聚合，`viewcompose-preview` 已交付按请求高亮与有限耗时。有效计划负责 Inspector、
性能、设备矩阵与发布收尾。
