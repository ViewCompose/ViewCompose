---
title: 确认删除并托管原生 Android View
sidebar_position: 5
translation_source: tutorials/task-list-overlays-and-android-views.md
translation_source_hash: 582e838e025a77a191e02a145f22c6d1002bee7deba4ac03e21777438d719f32
translation_status: current
---

# 确认删除并托管原生 Android View

本章会在保留列表/详情结构的前提下增加两个 Android 集成边界：用 Dialog Overlay 确认破坏性
操作，再用现有 `TextView` 显示由同一份可观察任务集合驱动的摘要。

## 将要构建的内容

- 为样例及导航目的地显式安装 `AndroidOverlayHost`；
- 以固定请求 key 标识的自定义 `Dialog`；
- 最终都回到应用状态的关闭与确认路径；
- 一个 `AndroidView`：factory 对每个已挂载 identity 执行一次，update 使用当前声明式输入更新。

## 前置条件与模块基线

请先完成[添加语义主题与列表详情导航](./task-list-theme-and-navigation.md)。本章新增
`viewcompose-overlay-android` `0.1.0-alpha01`；`AndroidView` 仍来自
`viewcompose-host-android` `0.1.0-alpha01`。

## 1. 安装 Android Overlay 实现

Overlay 产物可以通过 `ServiceLoader` 发现，但教程样例使用显式 factory，让运行时契约直接可见：

```kotlin
setUiContent(overlayHostFactory = ::AndroidOverlayHost) {
    TaskListCompleteScreen(latestRenderStats::get)
}
```

最终的导航宿主也会传入 `overlayHostFactory = ::AndroidOverlayHost`，因为每个目的地都在自己的
原生宿主容器里渲染。

## 2. 将原生 View 和 Dialog 绑定到状态

原生摘要从当前不可变列表派生。Dialog 接收已选任务，关闭时不执行删除，只有确认回调才会发布
删除结果。

{/* tutorial-sample source="samples/task-list/src/main/java/com/viewcompose/samples/tasklist/TaskListScreens.kt" region="task-list-overlay-interop" */}
```kotlin
private fun UiTreeBuilder.TaskListSummaryAndDeleteDialog(
    tasks: List<TaskItem>,
    pendingDelete: TaskItem?,
    onDismissDelete: () -> Unit,
    onConfirmDelete: (TaskItem) -> Unit,
) {
    val completedCount = tasks.count(TaskItem::completed)
    AndroidView(
        key = "task-native-summary",
        factory = { context -> TextView(context) },
        update = { view ->
            (view as TextView).text =
                "Native summary: $completedCount of ${tasks.size} complete"
        },
        modifier = Modifier.fillMaxWidth(),
    )

    Dialog(
        visible = pendingDelete != null,
        requestKey = "delete-task-dialog",
        onDismissRequest = onDismissDelete,
    ) {
        Surface(modifier = Modifier.fillMaxWidth()) {
            Column(
                spacing = 12.dp,
                modifier = Modifier.padding(20.dp),
            ) {
                Text(
                    text = "Delete ${pendingDelete?.title}?",
                    style = TextDefaults.titleMediumStyle(),
                )
                Row(spacing = 8.dp, modifier = Modifier.fillMaxWidth()) {
                    Button(
                        text = "Cancel",
                        variant = ButtonVariant.Outlined,
                        onClick = onDismissDelete,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        text = "Delete",
                        enabled = pendingDelete != null,
                        onClick = {
                            pendingDelete?.let(onConfirmDelete)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
```
{/* tutorial-sample-end */}

应用所有权应留在 `TextView` 外部。`factory` 创建原生对象，`update` 完整重绑依赖状态的属性。
如果 View 注册监听器或拥有其他资源，可用 `onReset` 和 `onRelease` 分别清理复用和最终释放时的
所有权。

`requestKey` 在多次渲染间标识同一个 Dialog。把 `pendingDelete` 设为 `null` 会移除请求；Overlay
宿主随后关闭原生窗口并释放子渲染会话。

## 3. 验证两条路径

运行最终样例，添加或完成一个任务，检查 `Native summary` 文本是否变化。打开删除 Dialog 并
取消，再次打开并确认。取消后任务必须保留，只有确认后才会消失。

```bash
./gradlew :samples:task-list:connectedDebugAndroidTest
```

设备测试会通过真实 Android View 验证原生摘要和确认删除行为。

## 继续构建应用

下一章将[为完成状态添加动画与有边界的手势](./task-list-animation-and-gestures.md)。
