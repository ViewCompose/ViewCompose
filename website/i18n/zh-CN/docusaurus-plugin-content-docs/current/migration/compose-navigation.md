---
translation_source: migration/compose-navigation.md
translation_source_hash: 5675ed5d2cbf87062ddc067b647a33884aa1c0171b50b869bcd255376301adb4
translation_status: current
---

# 从 Compose Navigation 迁移到 ViewCompose

本文同时对比 ViewCompose 导航与 Jetpack Navigation 2、Navigation 3。Navigation 2 与
Navigation 3 的所有权模型不同，因此迁移时必须先确定实际来源，再映射 API 或生命周期行为。

- **来源状态：** Navigation 2.9.8 或 Navigation3 1.1.5，以及 Compose UI/Runtime 1.12.0、
  Activity 1.13.0、Lifecycle 2.11.0 和 SavedState 1.5.0。
- **目标状态：** `viewcompose-navigation-core` 0.1.0-alpha03，以及
  `viewcompose-navigation-android`、`viewcompose-lifecycle-androidx` 和
  `viewcompose-viewmodel-androidx` 0.1.0-alpha02。
- **最后核验：** 2026-08-27。
- **重新核验负责人：** `viewcompose-navigation-core`、`viewcompose-navigation-android`、
  `viewcompose-lifecycle-androidx` 和 `viewcompose-viewmodel-androidx` 的维护者。

相关页面：[迁移总览](README.md) ·
[宿主、生命周期与 Android 互操作迁移](compose-host-lifecycle-and-android-interop.md)

## 验证模型

上游部分是对官方稳定版文档和发布说明的语义复核：

- [Navigation 2 返回栈](https://developer.android.com/guide/navigation/backstack)
- [Navigation 2 多返回栈](https://developer.android.com/guide/navigation/backstack/multi-back-stacks)
- [`NavBackStackEntry`](https://developer.android.com/reference/androidx/navigation/NavBackStackEntry)
- [Navigation 2 深层链接](https://developer.android.com/guide/navigation/design/deep-link)
- [Navigation 2 发布说明](https://developer.android.com/jetpack/androidx/releases/navigation)
- [Navigation 3 总览](https://developer.android.com/guide/navigation/navigation-3)
- [Navigation 3 基础](https://developer.android.com/guide/navigation/navigation-3/basics)
- [Navigation 3 保存状态](https://developer.android.com/guide/navigation/navigation-3/save-state)
- [Navigation 3 entry decorator](https://developer.android.com/guide/navigation/navigation-3/naventrydecorators)
- [Navigation 3 scene](https://developer.android.com/guide/navigation/navigation-3/scenes)
- [Navigation 3 多返回栈方案](https://developer.android.com/guide/navigation/navigation-3/recipes/multiple-backstacks)
- [Navigation 3 深层链接方案](https://developer.android.com/guide/navigation/navigation-3/recipes/deeplinks-basic)
- [Navigation3 1.1.5 发布说明](https://developer.android.com/jetpack/androidx/releases/navigation3)
- [Lifecycle 2.11 发布说明](https://developer.android.com/jetpack/androidx/releases/lifecycle)
- [Activity 1.13 发布说明](https://developer.android.com/jetpack/androidx/releases/activity)
- [NavigationEvent 发布说明](https://developer.android.com/jetpack/androidx/releases/navigationevent)

仓库的 Android 可执行基线是 Compose 1.7.8、Navigation 2.9.8、Activity 1.12.4、Lifecycle
2.8.7 和 Kotlin 2.2.10。下方成对样例会在两侧各编译一个 Navigation 2 controller、host、
route 和导航动作。所引用的 ViewCompose JVM、集成和设备测试确立了更广的本地行为。这些证据
不包含针对 Navigation3 1.1.5 的可执行对比，成对样例也不能证明完整 Navigation 2.9.8
能力面等价。因此，只要这些版本发生变化，Navigation 2 与 Navigation 3 陈述仍必须根据官方
来源重新复核。

ViewCompose 实现分为平台无关的[导航核心](../modules/viewcompose-navigation-core/README.md)
和 Android [导航宿主](../modules/viewcompose-navigation-android/README.md)。面向任务的
[导航指南](https://docs.viewcompose.com/guides/navigation)是补充证据，但如果指南与可执行行为
冲突，应以源码和测试为准。

## 可编译的 Navigation 2 起点

下面是 Navigation 2 来源迁移的可执行 route 级起点。两个片段都从
`:samples:compose-migration` 提取；`qaQuick` 会编译它们，并验证文档与源码完全一致。

Compose Navigation 2 源码：

{/* paired-sample source="samples/compose-migration/src/main/java/com/viewcompose/samples/migration/navigation/ComposeNavigationSample.kt" region="compose-navigation" */}
```kotlin
@Composable
fun ComposeNavigationSample() {
    val controller = rememberNavController()

    NavHost(
        navController = controller,
        startDestination = "home",
    ) {
        composable("home") {
            BasicText(
                text = "Open details",
                modifier = Modifier.clickable {
                    controller.navigate("details")
                },
            )
        }
        composable("details") {
            BasicText("Details")
        }
    }
}
```
{/* paired-sample-end */}

ViewCompose 目标：

{/* paired-sample source="samples/compose-migration/src/main/java/com/viewcompose/samples/migration/navigation/ViewComposeNavigationSample.kt" region="viewcompose-navigation-android" */}
```kotlin
fun UiTreeBuilder.ViewComposeNavigationSample() {
    val controller = rememberNavHostController(
        startDestination = NavRoute("home"),
    )

    NavHost(controller = controller) { entry ->
        when (entry.route.name) {
            "home" -> Button(
                text = "Open details",
                onClick = {
                    controller.navigate(NavRoute("details"))
                },
            )
            "details" -> Text("Details")
            else -> error("Unknown route ${entry.route.name}")
        }
    }
}
```
{/* paired-sample-end */}

这组对照只证明最小的 controller 所有单栈流程。它不覆盖类型化 route、`NavOptions`、深层链接、
owner 传播、多返回栈、恢复、Predictive Back，也不覆盖任何 Navigation 3 scene/decorator 行为。

## 能力矩阵

状态值仅使用 **Supported**、**Partially supported**、**Intentionally different** 和
**Unsupported**。

| 概念 | Navigation 2 / Navigation 3 行为 | ViewCompose 行为 | 状态 | 本地证据与验证说明 |
| --- | --- | --- | --- | --- |
| 导航所有权 | Navigation 2 以库拥有的 `NavController` 为中心。Navigation 3 通常向 `NavDisplay` 暴露应用拥有的返回栈集合。 | `NavBackStackController` 拥有不可变的单栈或多栈快照，并向 Android 宿主公开已 prepare 的 transition。 | Intentionally different | [`NavBackStackController.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-navigation-core/src/main/kotlin/com/viewcompose/navigation/core/NavBackStackController.kt)和 [`NavHostRuntime.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavHostRuntime.kt)。它把类似 Navigation 2 的控制器所有权，与更接近 Navigation 3 的显式快照和 pane 概念组合在一起。 |
| 宿主与目的地类型 | Navigation 2 支持 Compose、Fragment、Activity 和自定义目的地。Navigation 3 通过 `NavDisplay` 渲染 entry 内容。 | `NavHost` 渲染由框架管理的原生 View 会话。Activity 或 Fragment 是宿主 owner，不是目的地类型。 | Intentionally different | [`NavHostDsl.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavHostDsl.kt)和 [`NavDestinationSessionStore.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavDestinationSessionStore.kt)。未实现直接 Fragment 或 Activity 目的地。 |
| Graph 与类型化 route | Navigation 2 支持 graph 以及类型化或可序列化 route。Navigation 3 key 由应用定义，且通常可以保存；1.1.5 规定 instance-key `entry` 注册优先于 class-key 注册。 | Graph 和目的地身份是显式的，但 route 参数仅使用封闭的 `NavValue` 集合：null、string、int、long、boolean、float 和 double。 | Partially supported | [`NavigationModel.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-navigation-core/src/main/kotlin/com/viewcompose/navigation/core/NavigationModel.kt)、[`NavBackStackControllerTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-navigation-core/src/test/kotlin/com/viewcompose/navigation/core/NavBackStackControllerTest.kt)中的 graph 测试，以及 [`NavHostPublicApiTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostPublicApiTest.kt)中的公共 graph 覆盖。没有编译器生成的 route 序列化，也没有需要保留的 Navigation3 instance/class 注册优先级。 |
| 返回栈操作 | Navigation 2 提供 `navigate`、`popBackStack`、`popUpTo` 和 `NavOptions`；Navigation 3 通过应用集合更新表示栈变化。 | Push、pop、replace、reset、栈选择和深层链接命令作为一个事务执行 prepare、render，随后 commit 或 rollback。 | Partially supported | [`NavBackStackController.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-navigation-core/src/main/kotlin/com/viewcompose/navigation/core/NavBackStackController.kt)、[`NavBackStackControllerTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-navigation-core/src/test/kotlin/com/viewcompose/navigation/core/NavBackStackControllerTest.kt)和 [`NavHostPublicApiTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostPublicApiTest.kt)。两阶段事务的保证强于 API 名称映射，但不包含 Navigation 2 的完整 `NavOptions` 能力面。 |
| Entry 与 graph owner | `NavBackStackEntry` 拥有生命周期、ViewModel 和保存状态。Lifecycle 2.11 增加了可继承父级 factory 与 `CreationExtras` 的 Navigation3 ViewModel decorator。 | 每个目的地和 graph 都有自己的 lifecycle、ViewModelStore、saved-state owner 和 ViewCompose saveable-state registry。owner 继承 host 父级的默认 Factory 与初始 extra，再替换自己的子级所有权与 route 默认值。 | Supported | [`NavEntryOwner.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavEntryOwner.kt)、[`NavGraphOwner.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavGraphOwner.kt)、[`NavEntryOwnerEnvironment.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavEntryOwnerEnvironment.kt)，以及 [`NavEntryOwnerTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavEntryOwnerTest.kt)与 [`NavHostPublicApiTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostPublicApiTest.kt)中的 Factory、extra、SavedStateHandle、目的地和 graph 覆盖。 |
| Scoped ViewModel 与多栈 | Lifecycle 2.11 可把 `ViewModelStoreProvider` 提升到 Navigation3 decorator 之上，让多个返回栈保留彼此隔离的 entry store。 | owner store 按 ViewCompose entry 和 graph 身份，在控制器管理的栈集合间保留。 | Supported | [`NavEntryOwnerStore.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavEntryOwnerStore.kt)、[`NavEntryOwnerStoreTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavEntryOwnerStoreTest.kt)，以及 [`NavHostPublicApiTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostPublicApiTest.kt)中的同 route 保留栈隔离测试。 |
| 目的地生命周期 | 导航 entry 是 lifecycle owner；Navigation 3 scene 可以呈现多个 entry。 | 已保留的隐藏 entry 目标为 `CREATED`，可见但不可交互 entry 目标为 `STARTED`，可交互 entry 目标为 `RESUMED`，并受宿主生命周期上限约束。多个 pane entry 可以同时处于 `RESUMED`。 | Intentionally different | [`NavLifecyclePlanner.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-navigation-core/src/main/kotlin/com/viewcompose/navigation/core/NavLifecyclePlanner.kt)和 [`NavLifecyclePlannerTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-navigation-core/src/test/kotlin/com/viewcompose/navigation/core/NavLifecyclePlannerTest.kt)。迁移代码不得假设只有一个 resumed entry。 |
| 隐藏目的地 composition | 导航状态可以独立于 Compose 内容是否仍在 Composition 中而保留。Navigation 3 decorator 会保留 entry 状态。 | 隐藏目的地保留其 `RenderSession`；帧驱动渲染会禁用，但 composition 作用域的协程和 Effect 仍由存活会话拥有。 | Partially supported | [`NavDestinationSessionStore.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavDestinationSessionStore.kt)、[`RenderSession.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/session/RenderSession.kt)和 [`NavDestinationSessionStoreTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavDestinationSessionStoreTest.kt)。隐藏不等于已释放。 |
| 多返回栈 | Navigation 2 使用保存/恢复选项；Navigation 3 记录了应用拥有多个列表的方案。 | 一个 `NavStackConfiguration` 拥有全部栈、选择历史和根 Back 行为。未选择栈的会话与 owner 仍保持存活。 | Intentionally different | [`NavStackConfiguration.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-navigation-core/src/main/kotlin/com/viewcompose/navigation/core/NavStackConfiguration.kt)、[`NavBackStackSetControllerTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-navigation-core/src/test/kotlin/com/viewcompose/navigation/core/NavBackStackSetControllerTest.kt)，以及 [`NavHostPublicApiTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostPublicApiTest.kt)中的多栈恢复覆盖。 |
| 深层链接 | Navigation 2 匹配 URI、action 和 MIME type。Navigation 3 提供把外部输入解析成应用 key 的方案。 | ViewCompose 解析严格的绝对 URI pattern 和 Android `ACTION_VIEW` 输入，支持嵌套 graph 与调用方指定的 launch mode，拒绝歧义匹配，且不匹配任意 action 或 MIME type。输入中的额外 query 参数可存在，但不能影响 route 参数或导航策略。 | Partially supported | [`NavDeepLink.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-core/src/main/kotlin/com/viewcompose/navigation/core/NavDeepLink.kt)、[`NavDeepLinkTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-core/src/test/kotlin/com/viewcompose/navigation/core/NavDeepLinkTest.kt)，以及 [`NavHostPublicApiTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostPublicApiTest.kt)中的公共 host 策略覆盖。部分支持来自刻意缩小的 action/MIME 能力面，而不是未解决的 query 契约。 |
| 保存、恢复与进程死亡 | Navigation 2 恢复控制器和 entry 状态；Navigation 3 恢复可保存 key 和 decorator 状态。二者都不会恢复 View、ViewModel 等运行时实例。 | ViewCompose 保存完整的已配置栈集合、route value、entry 和 graph saved state，以及 saveable value。它拒绝损坏、不兼容或结构无效的快照，并安全回退。 | Supported | [`NavHostSavedState.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavHostSavedState.kt)、[`NavHostSavedStateTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostSavedStateTest.kt)，以及 [`NavHostPublicApiTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostPublicApiTest.kt)中的恢复覆盖。进程恢复不会恢复 View、ViewModel、Effect、动画或未提交事务等运行时实例。 |
| 系统 Back 与 Predictive Back | Navigation 2 Compose 集成 Predictive Back。Navigation 3 使用 NavigationEvent 和 scene transition。Activity 1.13 在 NavigationEvent 之上继续兼容 `OnBackPressedDispatcher`。 | Android 宿主注册 `OnBackPressedCallback`，并通过事务式 preview driver 支持 predictive start、progress、cancel 和 commit。 | Supported | [`AndroidNavHostBackAdapter.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/AndroidNavHostBackAdapter.kt)、[`AndroidNavHostBackAdapterTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/AndroidNavHostBackAdapterTest.kt)，以及[导航指南](https://docs.viewcompose.com/guides/navigation)引用的设备流程。本文未重新运行设备证据。 |
| 直接 NavigationEvent 集成 | Activity 和 Navigation3 公开 `NavigationEventDispatcher`、嵌套 dispatcher owner、测试工具与 Compose handler。Navigation3 1.1.3 使用 NavigationEvent 1.1.2，其中包括 Android Studio Preview inspection mode 下的 Predictive Back。 | ViewCompose 使用兼容的 Activity `OnBackPressedDispatcher` 路径，但没有直接 NavigationEvent callback、dispatcher owner、forward event、测试或 Preview 集成。 | Unsupported | 未找到对应的 ViewCompose 公共 API。Activity 在 NavigationEvent 之上实现了 `OnBackPressedDispatcher`，所以现有 Back 行为仍受支持；此处 Unsupported 仅指直接集成。 |
| 自适应 pane 与 overlay | Navigation 3 scene 可以选择一个或多个 entry，并协调 overlay 与 transition。1.1.3 和 1.1.4 分别修复嵌套 overlay 与含 metadata 的 popped entry 动画缺陷。 | ViewCompose 对最新的最多三个 pane entry 使用固定策略和事务式 preview/commit 行为；它没有公开通用 Navigation3 scene 策略或 metadata 模型。 | Partially supported | [`NavLifecyclePlanner.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-navigation-core/src/main/kotlin/com/viewcompose/navigation/core/NavLifecyclePlanner.kt)、宿主运行时和生命周期规划器测试。Navigation3 1.1.4 修复改变了上游对比基线，但不能证明 ViewCompose 具有等价行为。 |

## 选择来源导航模型 {/* #choosing-the-source-navigation-model */}

修改代码前，先确定来源模型：

- **Navigation 2 来源：** 库拥有 `NavController`、graph、目的地和返回栈。先映射 graph 和
  controller 行为，再用 ViewCompose 原生页面内容替换 Compose、Fragment 或 Activity
  目的地内容。
- **Navigation 3 来源：** 应用状态拥有 entry key，`NavDisplay` 派生 scene。先映射 key
  序列化、scene 选择、decorator 与状态所有权，再把数据移入 ViewCompose 控制器拥有的栈集合。

ViewCompose 不是二者中任何一个的直接替代实现。其控制器拥有的不可变快照类似 Navigation 2
的所有权，而显式 entry 身份、保留会话与 pane 选择则与 Navigation 3 概念部分重叠。

## 宿主与目的地架构

`NavHost` 把目的地挂载为原生 View 支持的 `RenderSession` 实例。最近的宿主生命周期限制每个
entry 和 graph 的生命周期上限。Activity 和 Fragment 集成负责提供外层宿主；它们不是
route 目的地。

因此，从 Navigation 2 Fragment 目的地迁移时，必须拆开此前由 Fragment 组合承担的职责：
route 身份、屏幕内容、生命周期收集、ViewModel 作用域、保存状态、结果交付和 Android 组件
集成。把 Fragment 放入 ViewCompose 渲染树，无法保留 Fragment 特有行为。

宿主暂存 controller 状态，尝试渲染原生树，并且只在渲染成功后提交导航事务。失败时保留此前
已提交的栈和已挂载目的地树。

## Graph、route 与参数 {/* #graphs-routes-and-arguments */}

ViewCompose graph、destination、entry 和 stack 身份都是显式的。Route 参数限于 `NavValue`
基础类型集合。这让控制器快照保持确定且可保存，但比 Navigation 2 类型化序列化和任意
Navigation 3 应用 key 更窄。

优先使用稳定标识符和基础 route value。导航后，从 repository 或 ViewModel 加载复杂领域
对象，不要把它们序列化到 route 中。迁移还必须定义未知 route、错误 value 和 graph 结构
变化如何失败；ViewCompose 恢复和深层链接路径有意采用 fail closed。

## 返回栈事务

ViewCompose 导航变化包含 prepare、render 和 commit 阶段。Push、pop、replace、reset、栈
选择和深层链接处理只有在宿主渲染成功后才成为权威状态。Rollback 会恢复此前的 controller
快照和原生树。

不要机械翻译 Navigation 2 `NavOptions`。记录每个 `popUpTo`、inclusive 标志、single-top
规则、状态保存选项和恢复选项的预期结果，再用现有 ViewCompose 命令和栈配置表达该结果。
如果没有公共命令可表达相同结果，应把这项 route 操作归类为该迁移不支持，而不是组合多次
非原子 mutation。

## Entry 与 graph 所有权 {/* #entry-and-graph-ownership */}

每个 ViewCompose 目的地 entry 都有 lifecycle、ViewModelStore、saved-state 和
saveable-state 所有权。嵌套 graph 有独立 owner 作用域。永久删除 entry 或 graph 会把其
生命周期移到 `DESTROYED` 并清除 ViewModelStore；保留在隐藏栈中不会如此。

Lifecycle 2.11 提高了上游对齐基准。`ViewModelStoreProvider` 支持任意 UI 作用域，
`ViewModelStoreNavEntryDecorator` 可以继承父级 owner 的默认 factory 和 `CreationExtras`。
把 provider 提升到外层的 overload 支持多返回栈，而不会过早清除 sibling store。
ViewCompose 的目的地和 graph owner 会继承最近 host owner 的默认 Factory 与初始 extra，再替换
自己的 store/saved-state owner 和 route 默认值，并保留无关的 Application 与 DI extra。同 route
entry 在保留栈中仍相互隔离。ViewCompose 在这些导航边界之外仍没有任意 UI scoped provider。

迁移自定义 ViewModel factory 之前，应同时在目的地和 graph 作用域验证所有必需的
`CreationExtras`、application 对象、默认参数和 `SavedStateHandle` 构造。仅存在
`ViewModelStoreOwner` 不足以作为证据。

## 生命周期与自适应 pane {/* #lifecycle-and-adaptive-panes */}

生命周期规划器根据宿主状态、保留状态、可见性和交互性计算目标：

- 已保留的隐藏 entry 目标为 `CREATED`；
- 可见但不可交互的 entry 目标为 `STARTED`；
- 可交互 entry 目标为 `RESUMED`；
- 任何 entry 或 graph 都不能超过宿主生命周期。

先应用向下 transition，再应用向上 transition；永久删除的 entry 会在被揭示 entry 前进前
达到 `DESTROYED`。自适应 pane 可以让多个目的地同时可交互，因此可能有多个 entry 处于
`RESUMED`。从单栈顶 entry 模型迁移的代码不得把 `RESUMED` 当作目的地是唯一可见页面的证明。

ViewCompose 最多选择三个最新且符合条件的 pane entry。Navigation3 1.1.5 具有更通用的
scene 和 metadata 模型。其 1.1.3 嵌套 overlay 修复和 1.1.4 含 metadata lambda 的 popped
entry 修复属于上游可靠性变化，并不能证明 ViewCompose 支持任意 scene 或相同 overlay 动画
生命周期。

## 隐藏目的地保留 {/* #hidden-destination-retention */}

隐藏的 ViewCompose 目的地会保留其 `RenderSession`、owner、已挂载 View 树和 composition
协程作用域。宿主把帧驱动渲染设为 inactive 并隐藏根 View，但仅仅隐藏不会释放 composition，
也不会取消 composition 作用域工作。

任何需要在页面隐藏时停止的工作都必须感知生命周期，并在低于所需目的地生命周期状态时停止。
不要只依赖 composition 释放，因为只有在 entry 被永久删除、graph 被销毁或宿主被拆除时才会
释放。这与“Compose 内容离开 Composition、只保留 saveable entry 状态”的迁移代码存在实质
差异。

## 多返回栈

`NavStackConfiguration` 声明栈集合、初始选择、选择历史行为和根 Back 策略。选择另一个栈时，
会保留上一个栈的 entry、owner、View 和会话。因此，状态成本来自存活对象保留，而不只是序列化
返回栈。

Lifecycle 2.11 Navigation3 集成可以在多个栈显示之间提升 ViewModelStore provider，同时隔离
重复 key 的 store。ViewCompose 依赖自己的 stack 和 entry 身份。跨 tab 迁移重复 route key
之前，应验证两个 entry 获得不同 owner store、删除一个不会清除另一个，并验证保存/恢复保留
预期身份。

## 深层链接 {/* #deep-links */}

ViewCompose 深层链接使用严格的绝对 URI pattern，并可以指向嵌套 graph。解析会拒绝错误输入、
不可信 URI 组件、重复参数和歧义匹配。Android 宿主通过 `ACTION_VIEW` 接收受支持的外部 route。
Navigation 2 action 和 MIME type 匹配没有直接对应能力。

### 额外 query 参数

ViewCompose 容忍匹配 pattern 未声明的输入 query 参数，但这些参数完全不参与导航：不会进入
`NavRoute.arguments`、增加匹配具体度、消除原本的歧义、选择保留栈或覆盖调用方指定的 launch
mode。这样可兼容跟踪参数，同时防止未注册输入成为导航策略。若要求精确 key、签名或安全敏感
query 语义，应在路由前由应用验证完整 URL。

## 保存、恢复与进程死亡 {/* #save-restore-and-process-death */}

Android 宿主保存完整栈集合、当前栈、选择历史、route 参数、entry 与 graph saved-state registry，
以及 ViewCompose saveable value。Decoder 验证格式版本和 graph 结构；对于损坏或不兼容输入，
采用 fail closed 并回退到安全初始状态。当前防御性限制是最多解码 100 个栈和总计 10,000 个
entry。

进程恢复不会复活 View 实例、ViewModel 实例、协程 Effect、动画、Predictive Back preview 或
未提交事务，而会从保存值重建 owner 和页面状态。应把进程死亡与配置变更、内存中的栈切换分别
测试。

## 系统 Back 与 Predictive Back {/* #system-back-and-predictive-back */}

ViewCompose Android adapter 向 `OnBackPressedDispatcher` 注册，根据 controller 是否能处理
Back 更新 enabled 状态，并通过暂存的导航事务驱动 predictive start、progress、cancel 与
commit。Activity 1.13 仍兼容此 API，因为 Activity dispatcher 构建在 NavigationEvent 之上。

但仍不支持直接 NavigationEvent 集成。ViewCompose 不公开
`NavigationEventDispatcherOwner`、`NavigationEventCallback`、forward-event fallback、
官方 NavigationEvent 测试 fake 或 inspection-mode Preview handler。新的集成工作应遵循
Activity 建议优先采用 NavigationEvent，而不是假设旧 adapter 是最终抽象。

Navigation3 1.1.3 把 NavigationEvent 依赖更新为 1.1.2，从而在 Android Studio Preview
inspection mode 中启用 Predictive Back；Navigation3 1.1.5 保留该行为。ViewCompose 设备流程
和 adapter 测试并未验证 Preview 支持。编写本文时没有重新运行导航指南引用的 API 35 设备流程，
所以设备状态沿用仓库既有证据，而不是新的执行结果。

## 迁移路径

### 从 Navigation 2 迁移

1. 清点目的地类型，并隔离 Fragment 或 Activity 特有行为。
2. 仅使用受支持的 `NavValue` 参数类型翻译 graph 和 route 身份。
3. 把 `NavOptions`、`popUpTo`、single-top 和保存/恢复意图改写成显式的预期栈结果。
4. 映射目的地和 graph ViewModel 作用域，包括 factory、extras 和 saved-state 需求。
5. 显式配置多栈和根 Back 行为。
6. 围绕受支持 URI pattern 与 `ACTION_VIEW` 重建深层链接；替换 action/MIME 规则。
7. 验证事务回滚、进程死亡、系统 Back 和 predictive cancel。

### 从 Navigation 3 迁移

1. 决定哪些应用拥有的 entry key 变成 ViewCompose route、entry 和 stack 身份。
2. 用受支持的基础 route value 和 repository 查询替换任意 key 序列化。
3. 分别映射 decorator：saveable state、ViewModel store、lifecycle 和自定义 metadata 并不是
   一项 ViewCompose 能力。
4. 用受支持的 pane 策略替换通用 scene，或者把该 scene 记录为不支持。
5. 验证跨多栈重复 key，以及父级 factory/`CreationExtras` 传播。
6. 用一条受支持的事务式 controller 命令替换应用集合 mutation。
7. 在 ViewCompose 集成出现前，把直接 NavigationEvent 和 Preview 依赖留在 ViewCompose 外部。

## 迁移风险与不支持行为

- 不支持 Activity 和 Fragment 目的地；它们只保留 Android 宿主角色。
- 不支持任意可序列化 route 对象，也不对齐编译器生成的类型化 route。
- 不支持 Navigation 2 action 和 MIME 深层链接匹配。
- 不支持直接 NavigationEvent dispatcher owner、callback、forward event、测试和 Preview API。
- 不支持通用 Navigation3 scene 策略与 metadata；pane 策略范围更窄。
- 隐藏会话保留 Effect 和原生 View，增加生命周期与内存风险。
- 任意非导航 UI scope 仍需要应用自行提供 owner 边界。
- 精确或签名 deep-link query 集合需要应用在路由前验证；未声明值默认可存在但完全不参与导航。
- 本文未重新运行 Predictive Back 设备证据，也未验证 NavigationEvent Preview 行为。

## 重新核验要求

任何导航命令、entry 身份、graph 作用域、生命周期目标、深层链接规则、状态格式、pane 策略或
Back 集成发生变化时，都要重新核验本文。Navigation 2、Navigation3、Lifecycle、SavedState、
Activity 或 NavigationEvent 的稳定版基线发生变化时也一样。

最低本地证据包括 navigation-core controller、生命周期和深层链接测试；Android 宿主 owner、
saved-state、目的地会话和 Back adapter 测试；进程重建覆盖；以及已有文档记录的 API 35
Predictive Back 设备流程。上游部分需要重新执行官方语义复核。不得仅根据仓库 Compose 1.7.8
可执行依赖基线，推断已对齐 Navigation 2.9.8 或 Navigation3 1.1.5。
