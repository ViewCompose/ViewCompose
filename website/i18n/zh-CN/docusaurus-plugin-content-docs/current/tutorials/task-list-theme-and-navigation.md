---
title: 添加语义主题与列表详情导航
sidebar_position: 4
translation_source: tutorials/task-list-theme-and-navigation.md
translation_source_hash: 529e1a906046a8e93245ac7f6a19823a279a97c8b4f09398b0d09c961b04a6be
translation_status: current
---

# 添加语义主题与列表详情导航

本章会把任务清单变成一个包含两个目的地的应用。你将使用 Android 宿主提供的语义 token
设置样式，保留框架导航 controller，把类型化任务 ID 传给详情目的地，并通过框架管理的返回栈
回到列表。

代码在
[`samples/task-list`](https://github.com/ViewCompose/ViewCompose/tree/main/samples/task-list) 中参与
编译。样例的最终 Activity 会运行后续阶段，但本屏幕会一直参与编译，方便你在学习本章时切换使用。

## 将要构建的内容

- 任务列表和任务详情两个目的地；
- 带显式起始路由的 `rememberNavHostController`；
- 类型化的 `NavValue.LongValue` 路由参数；
- 由 Android 宿主主题解析的语义背景色和主色；
- 通过 `popBackStack` 和系统返回键实现的框架返回行为。

## 前置条件与模块基线

请先完成[添加任务输入和带 key 的 Lazy 列表](./task-list-input-and-lists.md)。本章沿用 Android
SDK 36、`minSdk = 24`、JDK 17 和 JVM target 11 的基线，最后验证日期为 2026-08-03。

| 产物 | 版本 | 本章职责 |
| --- | --- | --- |
| `viewcompose-navigation-core` | `0.1.0-alpha01` | 路由、类型化参数和返回栈模型 |
| `viewcompose-navigation` | `0.1.0-alpha01` | 可记忆 controller 与 Android `NavHost` |
| `viewcompose-widget-core` | `0.1.0-alpha01` | 语义 `Theme` token 与目的地组件 |
| `viewcompose-host-android` | `0.1.0-alpha01` | 生命周期、保存状态、返回键和 Android 主题 owner |

混用更新版本前，请检查[已发布模块目录](../modules/README.md)。

## 1. 创建路由与 controller

编译阶段把任务集合放在目的地之外，因此两个目的地会观察同一个状态 owner。
`rememberNavHostController` 通过宿主的可保存状态注册表恢复返回栈。每次详情请求只携带稳定的
任务 ID，而不会复制可变化的任务对象。

{/* tutorial-sample source="samples/task-list/src/main/java/com/viewcompose/samples/tasklist/TaskListScreens.kt" region="task-list-theme-navigation" */}
```kotlin
internal fun UiTreeBuilder.TaskListThemeNavigationScreen() {
    val tasks = remember {
        mutableStateOf(
            listOf(
                TaskItem(id = 1, title = "Read the tutorial"),
                TaskItem(id = 2, title = "Run the sample", completed = true),
            ),
        )
    }
    val controller = rememberNavHostController(
        startDestination = NavRoute(TASKS_ROUTE),
    )

    NavHost(
        controller = controller,
        debug = true,
        debugTag = "TaskListNavigation",
        modifier = Modifier
            .fillMaxSize()
            .backgroundColor(Theme.colors.background),
    ) { entry ->
        when (entry.route.name) {
            TASKS_ROUTE -> TaskListNavigationHome(
                tasks = tasks.value,
                controller = controller,
            )
            TASK_DETAILS_ROUTE -> {
                val taskId = (entry.route[TASK_ID_ARGUMENT] as? NavValue.LongValue)?.value
                TaskDetailsScreen(
                    task = tasks.value.firstOrNull { it.id == taskId },
                    onBack = controller::popBackStack,
                )
            }
            else -> error("Unknown task-list route ${entry.route.name}")
        }
    }
}
```
{/* tutorial-sample-end */}

只应从这个 `NavHost` 已挂载的内容中调用 `navigate`；未附着的 controller 会拒绝命令。详情
目的地根据 ID 读取当前任务，因此任务已删除或不可用时会显示明确的回退状态，而不是过期路由数据。

## 2. 使用语义 token

`setUiContent` 会解析 Activity 主题并把它提供给声明式树。读取 `Theme.colors.background` 和
`Theme.colors.primary`，可让屏幕跟随深浅色模式和宿主的动态颜色策略。不要把解析后的颜色整数
复制到长期应用状态中；应在组合期间读取 token，这样配置或主题刷新才能使界面失效重绘。

路由宿主为目的地管理独立的原生容器。声明 `NavHost` 时捕获的 Local（包括当前主题）会传播到
目的地渲染会话。

## 3. 验证导航行为

临时在 `MainActivity` 中调用 `TaskListThemeNavigationScreen()`，然后运行样例。打开任务，检查
详情页的标题和状态，并分别使用 `Back to tasks` 和系统返回键。列表目的地及任务状态应继续由
导航宿主管理，而不是作为无关 Activity 重新创建。

```bash
./gradlew verifyTutorialSamples
./gradlew :samples:task-list:assembleDebug
```

## 继续构建应用

下一章将[通过 Overlay 确认删除并加入原生 Android View](./task-list-overlays-and-android-views.md)。
