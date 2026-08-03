---
title: 添加任务输入和带 key 的 Lazy 列表
sidebar_position: 3
translation_source: tutorials/task-list-input-and-lists.md
translation_source_hash: 9910a1cde6e0e6eaac0c5a7b66dc14839198a6938daae155f29ec842a7860a64
translation_status: current
---

# 添加任务输入和带 key 的 Lazy 列表

本教程会把第一个任务清单屏幕演进成可用的集合。你将为文本输入提供独立的状态 owner，追加
不可变任务记录，用稳定 key 渲染它们，并在不原地修改列表的前提下更新其中一条记录。

该屏幕在
[`samples/task-list`](https://github.com/ViewCompose/ViewCompose/tree/main/samples/task-list) 中参与
编译，由样例的 `MainActivity` 运行，并通过 `TaskListAppTest` 在真实 Android View 上执行交互。

## 将要构建的内容

第二阶段会加入：

- 一个 `TextField` 和 `Add task` 操作；
- 使用确定性 ID 的可观察不可变列表；
- 从当前列表计算的完成进度摘要；
- 使用稳定 key 和统一 content type 的纵向滚动 `LazyColumn`；
- 通过复选框事件只替换被选中任务记录的更新路径。

预期结果：输入非空标题会启用操作；添加后输入框清空并显示新行；勾选任意行都会更新完成进度。

## 前置条件与验证基线

请先完成[使用状态与布局构建任务清单](./task-list-foundations.md)。本章沿用 Android SDK 36、
`minSdk = 24`、JDK 17 和 JVM target 11 的基线，最后验证于 2026-08-03。

| 产物 | 版本 | 本章职责 |
| --- | --- | --- |
| `viewcompose-runtime` | `0.1.0-alpha01` | 可观察列表、ID 和派生读取 |
| `viewcompose-text-core` | `0.1.0-alpha01` | 可编辑文档与光标状态 |
| `viewcompose-ui-contract` | `0.1.0-alpha01` | Modifier 和 Lazy 集合契约 |
| `viewcompose-widget-core` | `0.1.0-alpha01` | `TextField`、`LazyColumn`、`Checkbox` 和状态辅助函数 |
| `viewcompose-host-android` | `0.1.0-alpha01` | Android 输入、生命周期和原生 renderer 宿主 |

混用更新版本前，请检查[已发布模块目录](../modules/README.md)。

## 1. 用集合与输入状态替换单任务

保留上一章的 `TaskItem` 模型，并把屏幕函数替换为参与编译的第二阶段：

{/* tutorial-sample source="samples/task-list/src/main/java/com/viewcompose/samples/tasklist/TaskListScreens.kt" region="task-list-input" */}
```kotlin
internal fun UiTreeBuilder.TaskListInputScreen() {
    val tasks = remember {
        mutableStateOf(
            listOf(
                TaskItem(id = 1, title = "Read the tutorial"),
                TaskItem(id = 2, title = "Run the sample", completed = true),
            ),
        )
    }
    val nextId = remember { mutableStateOf(3L) }
    val newTask = rememberTextFieldState()
    val completedCount = tasks.value.count(TaskItem::completed)

    Column(
        spacing = 12.dp,
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(text = "Task list", style = TextDefaults.titleLargeStyle())
        Text(text = "$completedCount of ${tasks.value.size} complete")
        TextField(
            state = newTask,
            hint = "New task",
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            text = "Add task",
            enabled = newTask.text.isNotBlank(),
            onClick = {
                val title = newTask.text.trim()
                if (title.isNotEmpty()) {
                    tasks.value = tasks.value + TaskItem(nextId.value, title)
                    nextId.value += 1
                    newTask.clearText()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        LazyColumn(
            items = tasks.value,
            key = TaskItem::id,
            contentType = { "task" },
            spacing = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { task ->
            Checkbox(
                text = task.title,
                checked = task.completed,
                onCheckedChange = { checked ->
                    tasks.value = tasks.value.map { current ->
                        if (current.id == task.id) current.copy(completed = checked) else current
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
```
{/* tutorial-sample-end */}

三个状态 owner 分别承担不同职责：

- `tasks` 发布由摘要和列表共同观察的不可变集合；
- `nextId` 为当前 composition 中加入的记录提供稳定身份；
- `newTask` 持有文本、选区、IME composition 和编辑历史，因此 `TextField` 不需要单独的
  字符串回调。

`completedCount` 的计算成本很低，每次已观察的任务列表变化时都会重新计算。对于高成本计算，
可以把同样的读取放进 `derivedStateOf`。

## 2. 保留 Lazy item 身份

结构化 `LazyColumn` 重载要求传入 `key`。插入任务或改变完成状态后，`TaskItem::id` 让协调
过程可以把新的不可变记录与已有原生行对应起来。当 item 可能插入、删除或重排时，不要使用
列表下标作为 key。

所有行都具有同样的结构，因此 `contentType = { "task" }` 声明了统一的复用类型。如果集合混合
结构不同的标题、控件和数据行，应当为它们提供不同 content type。

完成状态处理器会映射列表，并只复制 ID 匹配的记录。发布新列表才会使观察者失效；在不可见的
可变集合上进行原地修改会绕过这条状态边界。

## 3. 发布任务前验证输入

字段为空时操作处于禁用状态，事件内部还会再次去除标题两端空格。即使输入在两个 frame 之间
发生变化，也能保持验证正确。`clearText()` 会更新同一个 `TextFieldState`，因此已挂载的
Android 编辑器与声明式 owner 始终同步。

如果数据必须在 Activity 重建或进程恢复后保留，应把临时列表和 ID owner 替换为由
`rememberSaveable`、ViewModel 或持久存储支撑的应用状态 holder。本章有意保持本地 owner，
专注讲解集合更新路径。

## 4. 运行自动与手动验证

编译应用和 instrumentation 源码：

```bash
./gradlew verifyTutorialSamples
./gradlew :samples:task-list:assembleDebug
./gradlew :samples:task-list:compileDebugAndroidTestKotlin
```

在已连接设备或模拟器上运行行为测试：

```bash
./gradlew :samples:task-list:connectedDebugAndroidTest
```

测试会输入 `Write a device test` 并添加任务，验证摘要从 `1 of 2` 变为 `1 of 3`，然后完成
第一条任务并期望看到 `2 of 3 complete`。

手动验证还应覆盖只包含空白字符的标题：添加操作应保持禁用，也不应出现空行。

## 下一步

后续任务清单章节会加入语义主题和列表/详情导航。在该章节合入之前，请把
[主题指南](https://docs.viewcompose.com/guides/theming)和
[导航指南](https://docs.viewcompose.com/guides/navigation)作为参考，而不是当前教程的步骤。
