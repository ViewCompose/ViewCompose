---
title: 使用状态与布局构建任务清单
sidebar_position: 2
translation_source: tutorials/task-list-foundations.md
translation_source_hash: d01972b8ad85fc3d31edaa00001c27de907a0c8af1739cddbb5cc9309b3eaf38
translation_status: current
---

# 使用状态与布局构建任务清单

本教程会启动一个更贴近实际的应用，同时保持第一个教程中的最小计数器不变。你将建立一条任务
数据，用快照状态保留它，通过 `Column` 和 `Row` 排列标题与控件，并在复选框事件发生后更新
已有的 Android View 树。

参与编译的源码位于
[`samples/task-list`](https://github.com/ViewCompose/ViewCompose/tree/main/samples/task-list)。标记为
教程示例的代码块会由 `verifyTutorialSamples` 与该模块进行逐字校验。

## 将要构建的内容

任务清单第一阶段包含：

- `Task list` 标题和 `Open` 或 `Done` 摘要；
- 一条由不可变任务数据和可变快照状态驱动的复选框；
- 一个重置操作；
- 以原生 Android View 渲染的全屏带内边距布局。

预期结果：勾选任务后摘要变为 `Done`；重置后任务恢复为 `Open`，期间不重建 Activity。

## 前置条件与验证基线

请先完成[构建第一个应用](./getting-started.md)，也可以从任意能够调用 `setUiContent` 的
Kotlin Android 应用开始。构建仓库示例需要 Android SDK 36 和 JDK 17；示例使用
`minSdk = 24` 和 JVM target 11。

本教程最后验证于 2026-08-03，使用以下独立版本模块组合：

| 产物 | 版本 | 本章职责 |
| --- | --- | --- |
| `viewcompose-runtime` | `0.1.0-alpha01` | 快照状态与失效通知 |
| `viewcompose-ui-contract` | `0.1.0-alpha01` | Modifier、对齐和 `dp` 契约 |
| `viewcompose-widget-core` | `0.1.0-alpha01` | 布局、控件、文本、主题默认值和 `remember` |
| `viewcompose-host-android` | `0.1.0-alpha01` | Activity 生命周期、主题和原生 renderer 宿主 |

混用更新版本前，请检查[已发布模块目录](../modules/README.md)。

## 1. 创建任务模型

使用稳定 ID 表示身份、标题表示显示内容，并用不可变的完成标记记录状态：

{/* tutorial-sample source="samples/task-list/src/main/java/com/viewcompose/samples/tasklist/TaskListScreens.kt" region="task-item" */}
```kotlin
internal data class TaskItem(
    val id: Long,
    val title: String,
    val completed: Boolean = false,
)
```
{/* tutorial-sample-end */}

不可变模型让更新过程保持明确：创建副本，再通过可观察状态发布这个副本。下一章会把 ID 用作
Lazy 列表的 key。

## 2. 保留状态并排列界面

在模型旁添加下面这个参与编译的屏幕函数。完整源码文件包含全部 import；这里最重要的边界是
每个 widget 调用所使用的 `UiTreeBuilder` receiver。

{/* tutorial-sample source="samples/task-list/src/main/java/com/viewcompose/samples/tasklist/TaskListScreens.kt" region="task-list-foundations" */}
```kotlin
internal fun UiTreeBuilder.TaskListFoundationsScreen() {
    val task = remember {
        mutableStateOf(TaskItem(id = 1, title = "Read the tutorial"))
    }

    Column(
        spacing = 16.dp,
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Row(
            spacing = 12.dp,
            verticalAlignment = VerticalAlignment.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Task list",
                style = TextDefaults.titleLargeStyle(),
                modifier = Modifier.weight(1f),
            )
            Text(text = if (task.value.completed) "Done" else "Open")
        }
        Checkbox(
            text = task.value.title,
            checked = task.value.completed,
            onCheckedChange = { checked ->
                task.value = task.value.copy(completed = checked)
            },
        )
        Button(
            text = "Reset task",
            onClick = { task.value = task.value.copy(completed = false) },
        )
    }
}
```
{/* tutorial-sample-end */}

`remember` 会在当前 composition 位置保留状态 owner。读取 `task.value` 会把屏幕注册为观察者。
任意一个事件发布新模型后，框架会使已观察的 scope 失效，并只 patch 受影响的原生属性。

布局包含两个相互独立的方向：

- 外层 `Column` 纵向排列标题、任务和操作；
- 标题 `Row` 横向排列标题与状态，`weight(1f)` 让标题占据剩余宽度；
- `fillMaxSize`、`fillMaxWidth` 和 `padding` 是由原生 renderer 按顺序消费的 Modifier 元素。

## 3. 宿主化这一阶段

在已经使用 `setUiContent` 的 Activity 中，把其 content lambda 调用的屏幕函数替换为
`TaskListFoundationsScreen`。仓库应用中参与编译的 `MainActivity` 会运行最近完成的阶段，让
同一个 APK 随教程系列继续演进。按步骤学习本章时，可以暂时选择 foundations 函数。

## 4. 运行与验证

编译可执行源码，并检查文档中的代码副本：

```bash
./gradlew verifyTutorialSamples
./gradlew :samples:task-list:assembleDebug
```

从 Android Studio 运行 `:samples:task-list`，并在 `MainActivity` 中暂时选择
`TaskListFoundationsScreen`，然后验证：

1. 初始摘要为 `Open`；
2. 勾选 `Read the tutorial` 后变为 `Done`；
3. 点击 `Reset task` 后，复选框和摘要都恢复为未完成状态。

## 继续构建应用

下一步是[添加文本输入和带 key 的 Lazy 列表](./task-list-input-and-lists.md)。下一章会保留同一个
`TaskItem` 模型，并把单任务 owner 替换为可观察列表。
