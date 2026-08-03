---
title: 为完成状态添加动画与有边界的手势
sidebar_position: 6
translation_source: tutorials/task-list-animation-and-gestures.md
translation_source_hash: 5a7b3f8ae349a5db67de7015e80f611743b1475e78b83ddc46b4d00fd63e49b0
translation_status: current
---

# 为完成状态添加动画与有边界的手势

本章会增强任务行的表达能力，同时保留确定性的操作路径。完成标签根据可观察状态进入或退出；
点击任务行会切换状态，长按则请求删除。可见按钮提供相同操作，以支持无障碍、可发现性、自动化
测试以及不支持长按的指针环境。

## 前置条件与模块基线

请先完成[确认删除并托管原生 Android View](./task-list-overlays-and-android-views.md)。本章新增
`viewcompose-animation` 和 `viewcompose-gesture`，样例验证版本均为 `0.1.0-alpha01`。

## 1. 对状态做动画，而不是命令式操作 View

`AnimatedVisibility` 管理临时的过渡宿主。应用只管理语义 `completed` 值；退出过渡执行期间内容
仍保持挂载，动画结束后才会移除。

## 2. 让手势与控件共享操作

任务行把手势和按钮事件转发给同一组回调。长按会打开上一章的删除 Dialog，不会直接执行破坏性
操作。

{/* tutorial-sample source="samples/task-list/src/main/java/com/viewcompose/samples/tasklist/TaskListScreens.kt" region="task-list-animation-gestures" */}
```kotlin
private fun UiTreeBuilder.AnimatedTaskRow(
    task: TaskItem,
    onToggle: (TaskItem) -> Unit,
    onRequestDelete: (TaskItem) -> Unit,
    onOpenDetails: (TaskItem) -> Unit,
) {
    Surface(
        key = task.id,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onToggle(task) },
                onLongClick = { onRequestDelete(task) },
            ),
    ) {
        Column(spacing = 8.dp, modifier = Modifier.padding(12.dp)) {
            Text(
                text = task.title,
                style = TextDefaults.titleMediumStyle(),
            )
            AnimatedVisibility(visible = task.completed) {
                Text(text = "Completed", color = Theme.colors.primary)
            }
            Button(
                text = if (task.completed) "Reopen ${task.title}" else "Complete ${task.title}",
                onClick = { onToggle(task) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(spacing = 8.dp, modifier = Modifier.fillMaxWidth()) {
                Button(
                    text = "Details ${task.title}",
                    variant = ButtonVariant.Outlined,
                    onClick = { onOpenDetails(task) },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    text = "Delete ${task.title}",
                    variant = ButtonVariant.Outlined,
                    onClick = { onRequestDelete(task) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
```
{/* tutorial-sample-end */}

`combinedClickable` 把计时、移动 slop、竞争和回调顺序交给 renderer。识别过程需要持续时，应保持
key 和回调稳定。任务行点击与显式完成按钮有意调用同一个更新函数；嵌套控件会先于任务行识别器
消费自己的手势。

稳定任务 ID 同时用于 `Surface` 和外层 `LazyColumn`。因此不可变任务记录被替换时，带动画的任务
行仍可保留 identity。

## 3. 验证中断与回退行为

在完成标签进入或退出时快速切换任务，最终可见状态必须收敛到最新任务值。长按任务行打开删除
确认并关闭，然后用可见删除按钮打开同一请求。使用显式详情按钮，确认导航不会依赖任务行手势。

```bash
./gradlew verifyTutorialSamples
./gradlew :samples:task-list:assembleDebug
```

## 继续构建应用

最后一章将[设置集合复用策略并检查渲染诊断](./task-list-performance-and-diagnostics.md)。
