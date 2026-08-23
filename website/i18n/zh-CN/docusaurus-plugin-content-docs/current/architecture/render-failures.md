---
translation_source: architecture/render-failures.md
translation_source_hash: 74ed46211167b96c55a2201f1d68e7b0298f25eba67deb6f9868b72c00056e15
translation_status: current
---

# 渲染失败与 Android 互操作副作用

`RenderSession` 让渲染失败保持可观测，同时不会把可恢复的帧失败变成进程崩溃。根 Host 接受一套
关联的 `RenderDiagnostics` Sink：

```kotlin
val session = renderInto(
    container = root,
    diagnostics = RenderDiagnostics(
        collection = RenderDiagnosticCollection(
            lifecycle = false,
            failures = true,
            frameLevel = RenderFrameDiagnosticLevel.None,
        ),
        sink = { event ->
            if (event is RenderFailureObserved) report(event.context, event.failure)
        },
    ),
) {
    App()
}
```

宿主 `RenderSession` 还提供 `lastRenderFailure` 和 `lastFrameReport`。帧报告为 `Committed`
或 `RolledBack`，包含该帧观察到的所有同步失败。组合协程的异步失败单独报告，不会重写已经
完成的帧报告。

## 恢复保证

- `CompositionPrepare` 和 `ViewTreeRender` 失败会中止候选组合并报告
  `PreviousFrameRestored`。渲染器尽力恢复此前 VNode 绑定、已挂载子节点、布局参数和 View
  顺序，并释放本轮新插入节点。
- `ObservedPropertyPrepare` 报告 `FrameUnchanged`：任何原生修改发生前都会放弃候选值和依赖
  Guard。`ObservedPropertyRender` 报告 `PreviousFrameRestored`：Renderer 会先校验完整的精确
  Target Batch，任一 Patch 失败时把此前所有 Target 重新绑定到已提交 VNode。
  `ObservedPropertyCommit` 报告 `FrameCommitted`，因为原生属性此时已经成为权威结果；依赖提交
  失败保持可观测，不会静默退化成整树渲染。
- commit、副作用、overlay 和原生 commit 失败报告 `FrameCommitted`。这些失败发生在
  新 View 树已经成为权威结果之后；一个失败不会阻止其余操作执行。Remembered
  激活抛错后保持 Pending，并由后续成功的 Composition Commit 重试；成功的兄弟不会重复激活，
  而成功前移除会 Abandon 该 Pending 值。
- 组合协程失败报告 `FrameUnchanged`。
- dispose 失败报告 `SessionDisposed`，其余节点和宿主仍继续清理。
- Diagnostics Sink 抛错时，本 Session 会保存 `DiagnosticsSink` Failure 并禁用该 Sink；它不会
  改写权威 Frame Report，也不会递归发布 Failure Event。

`RenderFailureOperation` 和 `nodeKey` 可以识别 `AndroidView` 的 factory、update、reset、
commit 与 release 失败，无需解析异常消息。

## 可选的有界生产聚合

需要统计重复故障的应用，可以把可选 `viewcompose-diagnostics` 产物中的
`BoundedRenderFailureAggregator` 安装为仅故障根 Sink。默认指纹只保留 Phase、Recovery、
可选 Android View Operation、直接异常二进制类型，以及最多三个仅含类名和方法名的
`com.viewcompose.*` 栈帧。它不会保留消息、Cause 链、应用栈帧、文件与行号、`nodeKey` 或原始
`Throwable`。

聚合器默认在 15 分钟单调时间窗口中保留 64 个不同指纹；有效硬范围分别为 `1..128` 和 1 分钟至
24 小时。容量已满时淘汰最久未更新的指纹，并同时报告丢失观察数和被淘汰条目数。窗口只会在记录或
Snapshot 时过期，不存在定时器、存储、传输、厂商 SDK 或进程全局 Sink。Snapshot 是由应用持有的
不可变值。应在同步 Sink 投递之外导出，避免网络或持久化阻塞 Render Session。

精确的脱敏、同步、重置和计数器契约见
[诊断模块手册](https://docs.viewcompose.com/zh-CN/modules/viewcompose-diagnostics)。

## AndroidView 副作用边界

`AndroidView` 有两类刻意区分的更新路径：

```kotlin
AndroidView(
    key = playerId,
    factory = { context -> PlayerView(context) },
    update = { view ->
        // 只配置可安全重放的 View 状态。
        view.isEnabled = enabled
    },
    onReset = { view ->
        // View 重新绑定前执行可安全重放的清理。
        view.player = null
    },
    onCommit = { view ->
        // 不可重放的外部动作，仅在树事务成功后运行。
        analytics.recordPlayerAttached(playerId)
    },
    onRelease = { view ->
        // 任何永久放弃之后执行一次性资源释放。
        view.player = null
    },
)
```

规则如下：

1. `factory`、`update`、`onReset` 和 `Modifier.nativeView` 属于渲染事务。后续绑定失败并
   恢复旧节点时，`update` 可能再次执行；这些回调必须幂等，且只能修改所提供的 View。
2. 网络写入、分析打点、数据库写入、服务调用等不可重放外部副作用放入 `onCommit`。只有
   完整递归 View 树事务提交后才发布这些回调；回滚候选永远不会发布或执行它们。
3. `onRelease` 用于资源清理，不是通用 commit effect。已创建节点永久放弃时至多执行一次，
   包括候选回滚、成功移除、复用缓存最终淘汰或 Session Dispose。
4. 原生平台状态无法通用克隆。回滚保证覆盖框架拥有的树结构和旧 View 配置重放，不覆盖第三方
   View 内部隐藏的任意状态。
