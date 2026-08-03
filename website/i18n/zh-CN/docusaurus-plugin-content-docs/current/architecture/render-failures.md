---
translation_source: architecture/render-failures.md
translation_source_hash: 495189e68fc0a6d7b03862a5d30635a98819b5184675aa71a3e1cfd50c276c1c
translation_status: current
---

# 渲染失败与 Android 互操作副作用

`RenderSession` 让渲染失败保持可观测，同时不会把可恢复的帧失败变成进程崩溃。`renderInto`
和 `setUiContent` 都接受 `onRenderFailure`：

```kotlin
val session = renderInto(
    container = root,
    onRenderFailure = { failure ->
        report(
            phase = failure.phase,
            recovery = failure.recovery,
            frameId = failure.frameId,
            operation = failure.operation,
            cause = failure.cause,
        )
    },
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
- commit、副作用、overlay、诊断和原生 commit 失败报告 `FrameCommitted`。这些失败发生在
  新 View 树已经成为权威结果之后；各回调相互隔离，一个失败不会阻止其余回调执行。
- 组合协程失败报告 `FrameUnchanged`。
- dispose 失败报告 `SessionDisposed`，其余节点和宿主仍继续清理。

`RenderFailureOperation` 和 `nodeKey` 可以识别 `AndroidView` 的 factory、update、reset、
commit 与 release 失败，无需解析异常消息。

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
        // 节点移除提交或 Session 销毁后执行一次性资源释放。
        view.player = null
    },
)
```

规则如下：

1. `factory`、`update`、`onReset` 和 `Modifier.nativeView` 属于渲染事务。后续绑定失败并
   恢复旧节点时，`update` 可能再次执行；这些回调必须幂等，且只能修改所提供的 View。
2. 网络写入、分析打点、数据库写入、服务调用等不可重放外部副作用放入 `onCommit`。只有
   完整递归 View 树事务提交后才发布这些回调；回滚候选永远不会发布或执行它们。
3. `onRelease` 用于资源清理，不是通用 commit effect。挂载节点成功移除或 Session dispose
   时至多执行一次。
4. 原生平台状态无法通用克隆。回滚保证覆盖框架拥有的树结构和旧 View 配置重放，不覆盖第三方
   View 内部隐藏的任意状态。
