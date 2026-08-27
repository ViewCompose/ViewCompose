---
translation_source: modules/viewcompose-diagnostics/README.md
translation_source_hash: 130f3781459df08730fb7d6ff50d21f1aa262c454240da48700bcd6aea494112
translation_status: current
schema_version: 2
document_id: module.viewcompose-diagnostics
doc_type: module
owner:
  kind: module
  id: viewcompose-diagnostics
version_lane: released
capability_ids:
  - diagnostics.failure-aggregation
artifact_ids:
  - viewcompose-diagnostics
sample_ids:
  - module.diagnostics-dependency
  - module.diagnostics-failure-aggregation
  - module.diagnostics-snapshot-export
coordinate: com.viewcompose:viewcompose-diagnostics:0.1.0-alpha01
minimal_usage_sample_id: module.diagnostics-dependency
---

# 诊断模块

`viewcompose-diagnostics` 是 ViewCompose 面向渲染故障的可选生产可观测层。它消费
`viewcompose-ui-foundation` 所拥有的关联故障事件，并将其转换为有界、脱敏且不可变的摘要。
本模块不包含遥测厂商、数据库、Worker、Manifest 组件、网络客户端、文件写入器、调试检查器、
View 遍历或进程全局 Sink。

## 产物与稳定性

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="diagnostics-module-dependency" sample_id="module.diagnostics-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-diagnostics:0.1.0-alpha01")
}
```

- 稳定性：**Alpha**。隐私、基数、同步和重置契约已经过审查与测试；名称在 Alpha 版本之间仍可能变化。
- 平台：Android Library，`minSdk 24`、`compileSdk 36`，Java 11 字节码。聚合代码本身不使用
  Android Framework API；AAR 形态来自它对 `viewcompose-ui-foundation` 的公开依赖。
- `viewcompose-ui-foundation` 会被传递暴露，因为 `RenderDiagnostics`、
  `RenderFailureObserved` 和 `RenderDiagnosticContext` 出现在公共 API 中。
- 公共根包为 `com.viewcompose.diagnostics`。

## 仅故障安装

创建一个由应用持有的聚合器，并把它作为根诊断 Sink：

{/* compiled-region source="viewcompose-diagnostics/src/test/samples/com/viewcompose/diagnostics/samples/RenderFailureAggregationSamples.kt" region="diagnostics-failure-aggregation" sample_id="module.diagnostics-failure-aggregation" build_target=":viewcompose-diagnostics:compileDebugUnitTestKotlin" */}
```kotlin
fun boundedFailureAggregationSample(
    install: (RenderDiagnostics) -> Unit,
): BoundedRenderFailureAggregator {
    val aggregator = BoundedRenderFailureAggregator()
    val diagnostics = RenderDiagnostics(
        collection = RenderDiagnosticCollection(
            lifecycle = false,
            failures = true,
            frameLevel = RenderFrameDiagnosticLevel.None,
        ),
        sink = aggregator,
    )
    install(diagnostics)
    return aggregator
}
```

该配置只收集结构化故障。它不会构建帧统计或树、激活 Preview 工具、遍历已挂载 View、读取逐节点时钟、
安装定时器或创建后台 Worker。未构造并安装聚合器时，它不占用任何运行时路径。传给聚合器的非故障
事件会在读取时钟或分配聚合记录之前直接返回。

同一聚合器可以接收 Host、导航、Lazy、Pager 和 Overlay Session 继承产生的事件。来自不同 Session
线程的调用会被同步；除了聚合器获取锁的顺序外，不定义跨 Session 全序。

## 脱敏与指纹身份

`RenderFailureFingerprint` 只包含：

1. `RenderFailurePhase` 和 `RenderFailureRecovery`；
2. 可选的 `RenderFailureOperation`；
3. 直接异常的二进制类型，截断至 256 个 UTF-16 代码单元；
4. 最多三个 `com.viewcompose.*` 栈位置，并且只保留截断后的二进制类名和方法名。

它排除异常消息与 Cause、应用栈帧、文件名、行号、完整堆栈、`nodeKey`、View 文本、Composition
Local 值、URL、媒体、凭据、任意 `toString()` 输出和原始 `Throwable`。不可变结果在结构上已经安全，
但应用仍需负责用户同意、保留周期、账户关联，以及导出时自行拼接的任何额外元数据。

## 窗口、容量与丢失报告

`BoundedRenderFailureAggregator` 默认保留 64 个不同指纹，有效范围为 `1..128`。默认单调时间窗口
为 15 分钟，有效范围为 1 分钟至 24 小时。窗口在首次记录或查询时开始，只会在下一次记录、
`snapshot` 或 `snapshotAndReset` 时过期；不存在定时器。

每个保留的 `RenderFailureAggregate` 会公开进程内窗口 ID、饱和计数、首次和最近一次接收的单调
时间、指纹，以及最新的安全诊断上下文。Snapshot 中的条目按“最久未更新到最近更新”排序。容量已满
时，新指纹会淘汰最久未更新的条目。`droppedFailureCount` 会增加被淘汰条目的计数，或增加计数饱和后
无法表示的观察次数；`evictedFingerprintCount` 统计被淘汰的不同条目数。所有计数器都在
`Long.MAX_VALUE` 饱和。

`snapshot()` 返回防御性不可变副本并保留当前窗口。`snapshotAndReset()` 返回同类副本，然后原子地
打开一个空的新窗口。两个操作都不会改变任何存活的 `RenderSessionTraceId`；窗口 ID 与 Trace ID 是
两个独立的进程内概念。

## 导出与故障隔离

Snapshot 和重置都是同步内存操作。应在渲染 Sink 投递之外执行导出，并把慢 I/O 放到应用持有的调度
与背压之后：

{/* compiled-region source="viewcompose-diagnostics/src/test/samples/com/viewcompose/diagnostics/samples/RenderFailureAggregationSamples.kt" region="diagnostics-snapshot-export" sample_id="module.diagnostics-snapshot-export" build_target=":viewcompose-diagnostics:compileDebugUnitTestKotlin" */}
```kotlin
fun exportFailureAggregationSnapshotSample(
    aggregator: BoundedRenderFailureAggregator,
    forward: (RenderFailureAggregationSnapshot) -> Unit,
) {
    val completedWindow = aggregator.snapshotAndReset()
    forward(completedWindow)
}
```

本模块永远不会调用导出器，因此导出失败无法递归发布渲染故障。根 Sink 抛出异常仍遵循 Foundation
契约：Session 会记录本地 `DiagnosticsSink` 故障并禁用该 Sink，而不会改变渲染恢复结果。应用若从
一个根 Sink 分发给多个下游消费者，必须自行隔离它们，并决定失败导出器应该重试、禁用还是通过独立
通道报告。

## API 质量与验证

`BoundedRenderFailureAggregator`、`record`、`snapshot` 和 `snapshotAndReset` 是 Q3 API。
编译样例展示仅故障安装以及转发不可变 Snapshot。指纹、栈帧、聚合记录与 Snapshot 值是 Q2 不可变
输出契约。

聚焦测试覆盖构造参数边界、被忽略事件零工作、脱敏、去重、确定性的最久未更新淘汰、计数饱和、
窗口过期、重置不可变性、1,000 个高基数指纹、多 Session 并发发布、销毁分类、导出故障隔离，以及
应用持有的进程重置。本模块不执行 Android UI 工作，因此 Phase 2 不增加可视化 Demo 验收页面。
设备节点高亮和计时仍是相互独立、按请求激活的 Preview 能力；完整跨模块证据保留在
[已归档的诊断计划](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/diagnostics-correlation-inspection-observability.md)。
