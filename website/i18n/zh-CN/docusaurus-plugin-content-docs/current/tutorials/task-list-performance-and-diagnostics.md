---
title: 调整集合复用并检查渲染诊断
sidebar_position: 7
translation_source: tutorials/task-list-performance-and-diagnostics.md
translation_source_hash: de5050262504aab9d3910be58e1ed53ff1b178d636019ff3d2d7d190e2fbbb1f
translation_status: current
---

# 调整集合复用并检查渲染诊断

最后一章会让性能决策变得明确且可测量。你将保留稳定 item identity 与 content type，设置有边界的
预取/缓存提示，让兼容容器选择共享原生池，并在不制造反馈循环的前提下采样不可变渲染计数。

## 前置条件与模块基线

请先完成[为完成状态添加动画与有边界的手势](./task-list-animation-and-gestures.md)。策略类型来自
`viewcompose-ui-contract`，公开诊断类型来自 `viewcompose-widget-core`；样例验证版本均为
`0.1.0-alpha01`。renderer 内部诊断类型不是应用 API。

## 1. 把语义 identity 与调优分开

完整屏幕仍会声明：

```kotlin
LazyColumn(
    items = tasks.value,
    key = TaskItem::id,
    contentType = { "task" },
    prefetchPolicy = TaskListPrefetchPolicy,
    reusePolicy = TaskListReusePolicy,
) { task ->
    AnimatedTaskRow(/* ... */)
}
```

`key` 和 `contentType` 是正确性与兼容性输入。预取、原生 View 缓存数量和共享池是性能提示：
renderer 可以限制这些值，而改变它们绝不能改变任务内容或所有权。

## 2. 在可观察回调之外采样诊断

Activity 会把每个 `onRenderStats` 快照存入 `AtomicReference`。该回调不写 ViewCompose 状态，因此
不会再调度一帧。只有用户请求检查时，操作回调才把最新快照复制到可观察显示文本中。

{/* tutorial-sample source="samples/task-list/src/main/java/com/viewcompose/samples/tasklist/TaskListScreens.kt" region="task-list-performance-diagnostics" */}
```kotlin
private val TaskListPrefetchPolicy = LazyLayoutPrefetchPolicy(
    initialPrefetchItemCount = 4,
    itemViewCacheSize = 4,
)
private val TaskListReusePolicy = CollectionReusePolicy(sharePool = true)

private fun UiTreeBuilder.RenderDiagnosticsControl(
    latestStats: () -> RenderStats,
    diagnostics: String,
    onDiagnosticsChange: (String) -> Unit,
) {
    Button(
        text = "Sample render stats",
        variant = ButtonVariant.Outlined,
        onClick = {
            val stats = latestStats()
            onDiagnosticsChange(
                "Render stats: ${stats.inserts} inserts, " +
                    "${stats.reuses} reuses, ${stats.patchedNodes} patches",
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(text = diagnostics)
}
```
{/* tutorial-sample-end */}

`inserts`、`reuses` 和 `patchedNodes` 描述的是宿主的一帧，不是耗时指标。Activity 根回调测量根
渲染会话；导航目的地使用自己的会话，并以 `TaskListNavigation` tag 输出调试诊断。因此记录测量
结果时必须说明宿主和操作范围。

## 3. 执行可重复检查

构建并安装同一个 debug 变体，然后重复固定序列：启动、采样初始帧、完成 `Read the tutorial`、
添加一项，再次采样。保存任何计数时，都应同时记录设备型号、Android 版本、构建类型和交互序列。

```bash
./gradlew :samples:task-list:assembleDebug
adb logcat -s TaskListHost TaskListNavigation
./gradlew :samples:task-list:connectedDebugAndroidTest
```

耗时、分配与帧节奏应使用 Android Studio profiler 或 benchmark。渲染计数可以解释 reconciliation
工作，但不能替代这些测量。

## 系列成果

同一个 `:samples:task-list` 应用现在覆盖状态与布局、输入与 Lazy 集合、语义主题与导航、Overlay
与原生 View、动画与手势，以及有边界的性能诊断。`qaQuick` 会编译每个文档阶段并核对精确片段；
`qaFull` 会在已连接 Android 目标上运行最终行为测试。
