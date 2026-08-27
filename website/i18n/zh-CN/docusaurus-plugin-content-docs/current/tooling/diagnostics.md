---
translation_source: tooling/diagnostics.md
translation_source_hash: 2e193957769443583a044dbddbafb3f54f7edccc2e0d6bd73e10a28cdc619d66
translation_status: current
schema_version: 2
document_id: tooling.diagnostics
doc_type: tooling
owner:
  kind: project
  id: diagnostics
version_lane: released
capability_ids:
  - diagnostics.correlated-events
  - diagnostics.session-inspection
  - diagnostics.node-timing
  - diagnostics.failure-aggregation
  - renderer.diagnostics
artifact_ids:
  - viewcompose-ui-foundation
  - viewcompose-renderer-android
  - viewcompose-diagnostics
  - viewcompose-preview
sample_ids:
  - tooling.diagnostics-event-stream
  - tooling.diagnostics-session-inspection
  - tooling.diagnostics-node-timing
supported_versions:
  - UI Foundation、Android Renderer 与 Diagnostics 0.1.0-alpha01
  - Preview Android 集成 0.1.0-alpha04
  - ViewCompose Preview Android Studio 插件 1.1.0，适配 261.* 构建家族
verification_commands:
  - ./gradlew :viewcompose-ui-foundation:testDebugUnitTest :viewcompose-diagnostics:testDebugUnitTest
  - ./gradlew :samples:tutorials:assembleDebug
  - ./gradlew qaQuick
---

# ViewCompose 诊断

## 1. 关联事件入口

在 Host 或 Preview 根节点安装一个不可变 `RenderDiagnostics`。Navigation、Lazy、Pager 与
Overlay 子 Session 会继承该 Sink，并获得进程内 Session ID 与 Parent ID。底层嵌套 Session
若显式传入新的 Diagnostics，则会有意开启一棵新的关联树。

{/* compiled-region source="viewcompose-ui-foundation/src/test/samples/com/viewcompose/ui/foundation/samples/RenderSessionToolingSamples.kt" region="diagnostics-correlated-events" sample_id="tooling.diagnostics-event-stream" build_target=":viewcompose-ui-foundation:compileDebugUnitTestKotlin" */}
```kotlin
fun renderDiagnosticsEventSample(): RenderDiagnostics {
    return RenderDiagnostics(
        collection = RenderDiagnosticCollection(
            lifecycle = true,
            failures = true,
            frameLevel = RenderFrameDiagnosticLevel.Stats,
        ),
        sink = { event ->
            when (event) {
                is RenderFrameCompleted -> println(event.stats)
                is RenderFailureObserved -> println(event.failure.phase)
                is RenderSessionStarted,
                is RenderSessionActivityChanged,
                is RenderSessionEnded,
                -> println(event.context)
            }
        },
    )
}
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

## 7. 关联真机 Inspector

通过 `debugImplementation` 引入 `viewcompose-preview`，让可调试应用保持前台，然后选择
`Inspect Device Diagnostics`。单一 Inspector 会硬切替代原来的 Source、Highlight、Clear 和 Timing
动作。Session Tree 会保留 Parent/Child Role，并区分最近已提交帧、最近已完成尝试和最近失败。失败
只展示类型化 Phase、Recovery、可选 Android View Operation 与有界异常二进制类名；原始异常、
Message、Cause、Stack、Key 和应用内容不会跨越 Tooling 边界。

同一个已选 Session 拥有 Source Candidate、Mounted Node 和有限耗时三个视图。Source、Node 与
Timing Record 都能跳转到当前项目内的有界调用位置。每个可导航行都会显示导航动作实际打开的、
解析后的业务项目位置；内部框架栈帧既不会被显示为目标，也不会替代业务位置打开。组件提供稳定的
`viewcompose.deviceDiagnostics.*` 自动化 Role；Demo 则保留刷新、高亮替换、耗时动作、可见耗时状态
与确定性八帧 Fixture 的稳定 Tag。

### 按请求高亮 Mounted Node

请求一份当前 Mounted Tree 快照，选择声明式节点，再使用 `Highlight node` 或 `Clear highlight`。
选中节点真实 Android View 的裁剪后边界最长显示五秒。

Token 不透明、只在当前进程和快照内有效，不包含应用 Key。新快照、节点替换、View 被另一逻辑 Owner
复用、Session 释放或进程重启都会使其失效。响应会区分 Selected、Partially Clipped、Missing、
Stale、Recycled、Hidden、Fully Clipped、Synthetic/Unsupported、Ended、Rejected 与 Cleared。
Bounds 同时提供屏幕坐标和全局可见裁剪矩形。

请求最多访问 2,048 个 Mounted Node，返回 512 个、深度不超过 64；只保留弱 Native Target，序列化
结果不超过 256 KiB。未激活时不会遍历、读取几何、修改 Overlay、写报告或安装 Listener。激活后的
Overlay 不可交互，不能触发重组、应用 Callback、Focus 或 Accessibility Focus 变化，不拦截输入，
也不修改布局。`Diagnostics → Renderer` 页面提供唯一 AndroidView 目标与替换动作，便于确定性人工验收。

### 有限逐节点耗时

在所选 Session 中使用 `Capture timing`，再触发待排查的交互。`Diagnostics → Renderer` 页面提供
`Run 8-frame timing workload`；可见计数器会从 `0/8` 推进到 `8/8`，人工验收无需依赖不可见状态变化。

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

### Lazy Session 的实机使用规则

启动 `Capture timing` 会立即请求一个结构帧，让所选 Session 生成有界记录。除非待测交互本身触发
了该帧，否则应把第一帧视为 Capture 初始化，不能把其中的阶段耗时标记成后续手势或更新。Capture
只跟随所选逻辑 Session。Lazy Key 离开 Viewport 并结束 Session 后，新出现的 Key 会获得新的
Session ID；即使 RecyclerView 复用了同一个物理 Holder 或 Mounted Presentation 也不例外。

排查冷 Lazy List 工作时，选择精确的存活 Parent Session，再使用 **Capture next LazyItem**。同一
进程最多拥有一个 Arm。它最多等待十秒单调时间，匹配 Parent 完全相同、Role 为 `LazyItem` 且
Session ID 高于 Arm 时下限的 Child，然后采集一个完整帧。Registration 发生在匹配 Child 首帧之前，
因此 Timing 会附着到已经进入 Preparation 的帧，不会另行强制结构 Render。结束原因明确区分
Matched、Duration Limit、Parent Ended、Superseded 与 Capture Rejected。Arm 不接收或序列化应用
Key、Node Content、Callback、Source String 或 Native Object。

每个 Session 行还包含一个不透明的进程内 Physical-container Token，Arm 会报告匹配 Token。不同
Logical Session ID 使用同一个 Token 能证明物理 Holder 复用，但 Token 不是 Selector 或稳定身份。
归因前必须核对所选 Host 的业务源码和匹配节点类型；同一前台进程可能存在另一个合法 Host。
`Session ended` 仍是身份归属证据，不是空白性能结果。Measure/Layout/Draw、RenderThread、GPU 与
Buffer Queue 的归属仍必须使用 Perfetto 或其他平台 Profiler。

`performance.list@5` 实机复检遵守了这项规则。重复 Capture 解析到作者编写的 LazyItem 源码，并在
已选 Item 帧内把 Text 直接 Binding 排在首位；Host Capture 则显示真实纯滚动区间没有执行受支持
阶段。Future-item Capture 随后观测到冷 Logical Session 的首个受支持帧。连续十二次匹配中，不同
Logical Session ID 重复使用 Physical Token `9`、`8` 和 `13`，证明工作负载已经复用 Holder。
匹配的平台 Trace 把冷 Direct Render 放在 `RV Scroll` 而不是 `RV Prefetch` 下，并保留了有限计时器
之外的 Input、Traversal 与 RenderThread 工作。这次升级可执行但不完整：它消除了 Future Session
和 Holder Creation 两项歧义，也改变了下一项源码决策，但没有任何实测生产候选关闭 Release 尾部。

## 8. Demo 检查器

`Diagnostics -> 渲染器` 提供 Render Tree、Patch 时间线、重组原因、CompositionLocal 浏览器与
聚合指标、Mounted-node 高亮 Fixture 与显式八帧耗时工作负载。跨 Session 关联、生产聚合、真实
View 边界高亮、有限逐节点耗时与关联 Studio Inspector 已经实现；性能与发布收尾仍由有效的
同机空闲/请求性能、弱生命周期所有权、优化 Release 排除与隔离 Maven 消费也已闭环，且没有
可接受的回退。执行记录保留在
[已归档的诊断关联、检查与生产可观测性计划](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/diagnostics-correlation-inspection-observability.md)。

## 9. 剩余扩展契约

[ADR-0021](https://docs.viewcompose.com/architecture/decisions/0021-correlated-render-diagnostics-ownership)
冻结 Phase 1 及其有界 Future-session 扩展；只关心 Failure 的 Sink 不激活 Frame 明细。可选
`viewcompose-diagnostics` 负责生产聚合，`viewcompose-preview` 负责按请求工作的关联 Inspector、
高亮、Selected-session Timing 与一次性 Future-LazyItem Timing。当前列表尾部计划负责该扩展的
No-regression 与 Release 隔离验收。未来的持续观察器、新耗时域或更广设备契约必须重新建立可归因
计划，并继续遵守 ADR-0009 的非激活路径与 Release 隔离规则。

`./gradlew verifyDemoReleaseToolingApk` 会构建优化后的 Demo Release APK，并在任意打包条目中拒绝
设备请求 Action、v7 报告路径、Receiver、Service 注册或具体 Inspection 类。`qaQuick` 除了检查
Release Runtime 依赖图，也会执行这项制品级门禁。
