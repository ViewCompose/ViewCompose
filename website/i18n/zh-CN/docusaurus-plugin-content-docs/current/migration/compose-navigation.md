---
translation_source: migration/compose-navigation.md
translation_source_hash: fb69896d820e388d2c25347cfb3940f5cf08a9a4c3f86c94e261993257e9f323
translation_status: current
---

# 从 Compose Navigation 迁移到 ViewCompose

本文同时对比 ViewCompose 导航与 Jetpack Navigation 2、Navigation 3。Navigation 2 与
Navigation 3 的所有权模型不同，因此迁移时必须先确定实际来源，再映射 API 或生命周期行为。

- **来源状态：** Navigation 2.9.8 或 Navigation3 1.1.6，以及 Compose UI/Runtime 1.12.0、
  Activity 1.13.0、Lifecycle 2.11.0 和 SavedState 1.5.0。
- **目标状态：** `viewcompose-navigation-core` 0.1.0-alpha03、源码已登记的
  `viewcompose-navigation-kotlinx-serialization` 0.1.0-alpha01，以及
  `viewcompose-navigation-android`、`viewcompose-lifecycle-androidx` 和
  `viewcompose-viewmodel-androidx` 0.1.0-alpha02。
- **最后核验：** 2026-08-29。
- **重新核验负责人：** `viewcompose-navigation-core`、
  `viewcompose-navigation-kotlinx-serialization`、`viewcompose-navigation-android`、
  `viewcompose-lifecycle-androidx` 和 `viewcompose-viewmodel-androidx` 的维护者。

相关页面：[迁移总览](README.md) ·
[宿主、生命周期与 Android 互操作迁移](compose-host-lifecycle-and-android-interop.md)

## 验证模型

上游部分是对官方稳定版文档和发布说明的语义复核：

- [Navigation 2 返回栈](https://developer.android.com/guide/navigation/backstack)
- [Navigation 2 多返回栈](https://developer.android.com/guide/navigation/backstack/multi-back-stacks)
- [`NavBackStackEntry`](https://developer.android.com/reference/androidx/navigation/NavBackStackEntry)
- [Navigation 2 深层链接](https://developer.android.com/guide/navigation/design/deep-link)
- [Navigation 2 类型安全设计](https://developer.android.com/guide/navigation/design/type-safety)
- [Navigation 2 类型安全目的地](https://developer.android.com/guide/navigation/type-safe-destinations)
- [Navigation 2 程序化导航与结果](https://developer.android.com/guide/navigation/use-graph/programmatic)
- [Navigation 2 发布说明](https://developer.android.com/jetpack/androidx/releases/navigation)
- [Navigation 3 总览](https://developer.android.com/guide/navigation/navigation-3)
- [Navigation 3 基础](https://developer.android.com/guide/navigation/navigation-3/basics)
- [Navigation 3 保存状态](https://developer.android.com/guide/navigation/navigation-3/save-state)
- [Navigation 3 entry decorator](https://developer.android.com/guide/navigation/navigation-3/naventrydecorators)
- [Navigation 3 scene](https://developer.android.com/guide/navigation/navigation-3/scenes)
- [Navigation 3 多返回栈方案](https://developer.android.com/guide/navigation/navigation-3/recipes/multiple-backstacks)
- [Navigation 3 深层链接方案](https://developer.android.com/guide/navigation/navigation-3/recipes/deeplinks-basic)
- [Navigation3 1.1.6 发布说明](https://developer.android.com/jetpack/androidx/releases/navigation3)
- [Lifecycle 2.11 发布说明](https://developer.android.com/jetpack/androidx/releases/lifecycle)
- [Activity 1.13 发布说明](https://developer.android.com/jetpack/androidx/releases/activity)
- [NavigationEvent 发布说明](https://developer.android.com/jetpack/androidx/releases/navigationevent)

仓库的 Android 可执行基线是 Compose 1.7.8、Navigation 2.9.8、Activity 1.12.4、Lifecycle
2.11.0 和 Kotlin 2.2.10。下方成对样例会在两侧各编译一个 Navigation 2 controller、host、
route 和导航动作。所引用的 ViewCompose JVM、集成和设备测试确立了更广的本地行为。这些证据
不包含针对 Navigation3 1.1.6 的可执行对比，成对样例也不能证明完整 Navigation 2.9.8
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
| Graph 与类型化 route | Navigation 2 的类型安全 Route 会在 Graph 声明、导航和 Entry 解码之间复用可序列化 Route Type。Navigation 3 Key 由应用定义且通常可以保存；1.1.6 保留 Instance-key `entry` 注册优先于 Class-key 注册的规则。 | 一个 `NavRouteSpec<T>` 提供稳定 Identity 与编码。可选 Kotlinx Adapter 为扁平 Scalar Class/Object Schema 派生 Spec；Graph DSL、Android 命令与 `NavEntry.toRoute` 仍只存储封闭的 `NavRoute`/`NavValue`。 | Partially supported | `NavRouteSpec.kt`、`SerializableNavRouteSpec.kt`、两组定向测试与类型化 Host 测试覆盖生成的 Scalar Serializer；Custom `NavType`、Nested/Collection/Polymorphic Key 与 Navigation3 Instance/Class 优先级仍缺失。 |
| 返回栈操作 | Navigation 2 提供 `navigate`、`popBackStack`、`popUpTo` 和 `NavOptions`；Navigation 3 通过应用集合更新表示栈变化。 | Push、pop、replace、reset、栈选择和深层链接命令作为一个事务执行 prepare、render，随后 commit 或 rollback。 | Partially supported | [`NavBackStackController.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-navigation-core/src/main/kotlin/com/viewcompose/navigation/core/NavBackStackController.kt)、[`NavBackStackControllerTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-navigation-core/src/test/kotlin/com/viewcompose/navigation/core/NavBackStackControllerTest.kt)和 [`NavHostPublicApiTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostPublicApiTest.kt)。两阶段事务的保证强于 API 名称映射，但不包含 Navigation 2 的完整 `NavOptions` 能力面。 |
| Scene Execution Plan | Navigation 3 从应用 Back Stack State 派生 Scene，并通过 Decorator 组合 Entry 内容；Navigation 2 把大部分执行策略保留在 Controller 与 Navigator 实现内部。 | `NavExecutionReducer` 是公开、纯函数的 Q3 边界。Settled、Transition 与 Predictive Preview 输入会生成一份不可变 Plan，统一 Stack、Scene、Lifecycle、Presentation、Interaction、Back、Rollback 与 Cleanup；Android Host 从该 Plan 执行类型化 Effect。 | Intentionally different | `NavExecutionPlan.kt`、其可编译 Sample、Reducer Model Test 与 Android Coordinator Test。这提供更强的自定义 Executor 可检查性，但不等于 Navigation 3 开放的 Scene/Decorator 生态。 |
| Entry 与 graph owner | `NavBackStackEntry` 拥有生命周期、ViewModel 和保存状态。Lifecycle 2.11 增加了可继承父级 factory 与 `CreationExtras` 的 Navigation3 ViewModel decorator。 | 每个目的地和 graph 都有自己的 lifecycle、saved-state owner、ViewCompose saveable-state registry 和租赁的 ViewModelStore。owner 继承必需的 host 父级默认 Factory 与初始 extra，再替换自己的子级所有权与 route 默认值。 | Supported | [`NavEntryOwner.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavEntryOwner.kt)、[`NavGraphOwner.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavGraphOwner.kt)、[`NavEntryOwnerEnvironment.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavEntryOwnerEnvironment.kt)，以及 [`NavEntryOwnerTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavEntryOwnerTest.kt)与 [`NavHostPublicApiTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostPublicApiTest.kt)中的 Factory、extra、SavedStateHandle、目的地和 graph 覆盖。 |
| Scoped ViewModel 与多栈 | Lifecycle 2.11 可把 `ViewModelStoreProvider` 提升到 Navigation3 decorator 之上，让多个返回栈保留彼此隔离的 entry store。 | `NavHost` 在必需的父 owner 之下使用共享 `ViewModelScopeProvider`。保存的 host-scope 身份与 entry/graph 身份会让彼此隔离的 store 跨栈切换和配置重建保留；终态 pop、graph 删除和宿主正常移除会清理它们。 | Supported | [`NavHostRuntime.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavHostRuntime.kt)、[`NavEntryOwnerStore.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavEntryOwnerStore.kt)、[`NavEntryOwnerStoreTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavEntryOwnerStoreTest.kt)，以及 [`NavHostPublicApiTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostPublicApiTest.kt)中的同 route 保留栈隔离测试。导航只负责生命周期和身份协调，不再维护第二套 ViewModelStore 分配器。 |
| 目的地生命周期 | 导航 Entry 是 Lifecycle Owner；Navigation 3 Scene 可以呈现多个 Entry。 | `NavSceneEntry` 根据 Presence、Visibility、Interaction、Transition、Pane 与 Layer Role 推导 Scene 和 Entry Cap；Planner 应用 `min(host, scene, entry)`。Android Host 为普通与 Predictive 转场冻结 Scene，把可见参与者限制为 `STARTED`、已 Pop 的离场页面限制为 `CREATED`，并只在终态结束后 Resume 稳定的可交互 Pane。 | Supported | `NavScene.kt`、`NavLifecyclePlanner.kt`、转场与自适应 Coordinator 测试，以及 `NavigationBackDeviceTest.kt` 中定向真机 Lifecycle 测试。支持范围包括当前单 Pane 与多 Pane Host Scene；通用 Overlay 导航仍是独立的部分支持能力。 |
| Destination Presentation Context | Navigation 3 Entry 内容可以在自己的 Entry Scope 中观察 Scene Metadata；Compose 内容也会观察 Composition Local。 | `LocalNavDestinationContext` 提供稳定的 Per-entry Holder，其只读 Presentation 就是 Core Scene Entry 本身。它可跨原生 Presentation 释放/重建存活，按最近 Host 嵌套，并排除逐帧 Progress。AndroidX Lifecycle 仍是资源阈值 API。 | Supported | `NavDestinationContext.kt`、`NavEntryOwnerEnvironment.kt`，以及 Navigation Android 中针对 Holder、Local Capture、嵌套 Host、Pane、Overlay、移除和 Predictive Progress 的测试。即使 Context 可表达 Core Overlay Scene，通用 Overlay 导航仍未支持。 |
| 隐藏目的地 composition | 导航状态可以独立于 Compose 内容是否仍在 Composition 中而保留。Navigation 3 decorator 会保留 entry 状态。 | 逻辑 Entry Owner、ViewModel、Saved State 与 Saveable State 独立于原生展示存活。`DisposeWhenHidden` 是有界默认；也可显式全保留或按“最久未隐藏”保留正数上限。再次展示时会事务性重建缺失 Presentation。 | Supported | `NavPresentationRetentionPolicy.kt`、`NavDestinationSessionStore.kt`、Owner/Rebuild/LRU 单测，以及 `NavigationBackDeviceTest.kt` 中的真机 Identity 与资源数量覆盖。 |
| 多返回栈 | Navigation 2 使用保存/恢复选项；Navigation 3 记录了应用拥有多个列表的方案。 | 一个 `NavStackConfiguration` 拥有全部栈、选择历史和根 Back 行为。未选择栈的 Owner 保持存活，可选 Presentation 则遵循 Host Retention Policy。 | Intentionally different | [`NavStackConfiguration.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-navigation-core/src/main/kotlin/com/viewcompose/navigation/core/NavStackConfiguration.kt)、[`NavBackStackSetControllerTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-navigation-core/src/test/kotlin/com/viewcompose/navigation/core/NavBackStackSetControllerTest.kt)，以及 [`NavHostPublicApiTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostPublicApiTest.kt)中的多栈恢复覆盖。 |
| 深层链接 | Navigation 2 匹配 URI、action 和 MIME type。Navigation 3 提供把外部输入解析成应用 key 的方案。 | `NavDeepLinkRequest` 与 `NavDeepLink` 在不引入 Android 类型的前提下匹配严格 URI、action、MIME 或组合声明。Android 把 `Intent.data`、`action` 与 `type` 映射到同一解析器；嵌套 graph、launch mode、结构化拒绝、歧义拒绝与惰性额外 query 值继续受支持。 | Supported | [`NavDeepLink.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-core/src/main/kotlin/com/viewcompose/navigation/core/NavDeepLink.kt)、[`NavDeepLinkTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-core/src/test/kotlin/com/viewcompose/navigation/core/NavDeepLinkTest.kt)，以及 [`NavHostPublicApiTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostPublicApiTest.kt)中的 Intent/事务覆盖。ViewCompose 有意省略 Navigation 2 的 Android 专用 Builder 表面，但在 Core 中保留实质匹配能力。 |
| 返回结果 | Navigation 2 使用上一 Entry 的 `SavedStateHandle`；Navigation 3 使用应用自有状态。 | 带结果 Pop 是原子的；仍存活 Entry 持有可保存 FIFO Inbox，`NavResultEffect` 在 `RESUMED` 时消费。 | Supported | `NavResult.kt`、`NavResultInbox.kt` 和结果事务/Lifecycle 测试；不提供全局或跨栈总线。 |
| 保存、恢复与进程死亡 | Navigation 2 恢复控制器和 entry 状态；Navigation 3 恢复可保存 key 和 decorator 状态。二者都不会恢复存活的 ViewModel 实例。 | ViewCompose 保存完整的已配置栈集合、route value、entry 和 graph saved state、saveable value，以及私有 host-scope 身份。它只在配置重建期间通过父 store 保留存活 ViewModel；版本 4 快照会用新的 scope 身份迁移；损坏或结构无效的状态会被拒绝。 | Supported | [`NavHostSavedState.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavHostSavedState.kt)、[`NavHostSavedStateTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostSavedStateTest.kt)，以及 [`NavHostPublicApiTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostPublicApiTest.kt)中的恢复覆盖。存活的 View、ViewModel、Effect、动画和未提交事务都不会跨进程恢复。 |
| 系统 Back 与 Predictive Back | Navigation 2 Compose 集成 Predictive Back；Navigation 3 使用 NavigationEvent 与 Scene Transition。 | Android Host 从最近的 NavigationEvent Owner 事务性驱动 Predictive Start、Progress、Cancel 与 Commit，并以 Activity Back 作为兼容回退。 | Supported | `AndroidNavHostBackAdapter.kt`、直接/兼容路径测试与定向真机覆盖；两种输入共用一套事务式 Preview 与 Pop 状态机。 |
| 直接 NavigationEvent 集成 | Activity 和 Navigation3 公开 `NavigationEventDispatcher`、嵌套 dispatcher owner、测试工具与 Compose handler。Navigation3 使用 NavigationEvent 1.1.2，其中包括 Android Studio Preview inspection mode 下的 Predictive Back。 | `NavHost` 直接向最近的 View-tree Owner 注册，遵循生命周期和根节点委派，并使用官方 Dispatcher Fixture 测试；只有不存在直接 Owner 时才使用 Activity Back。 | Partially supported | 生产 Handler 保持内部实现，因为应用已提供官方 Owner 边界；仍缺少 Forward History、ViewCompose Dispatcher Facade 与 Android Studio Preview 输入。 |
| 自适应 Pane 与 Overlay | Navigation 3 Scene 可以选择一个或多个 Entry，并协调 Overlay 与 Transition。1.1.3 和 1.1.4 分别修复嵌套 Overlay 与含 Metadata 的 Popped Entry 动画缺陷。 | ViewCompose 已公开通用的平台无关语义 Scene Value，Host 仍采用最多三个最新 Pane Entry 的固定策略。Adaptive Pane、Lifecycle、Presentation、Focus 与 Back 已消费同一 Reducer Plan，但 Android Host 仍无通用 Overlay 导航 Surface。 | Partially supported | `NavScene.kt`、`NavExecutionPlan.kt`、Adaptive Host Test 与 Reducer Test。当前 Partial 的原因是 Overlay 与 Scene Strategy 广度，而不是普通或 Predictive 执行一致性。 |

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
route 目的地。`NavHost` 还要求这些宿主提供 `LocalViewModelStoreOwner`。自定义底层
`renderInto` 宿主必须显式提供该 owner。

因此，从 Navigation 2 Fragment 目的地迁移时，必须拆开此前由 Fragment 组合承担的职责：
route 身份、屏幕内容、生命周期收集、ViewModel 作用域、保存状态、结果交付和 Android 组件
集成。把 Fragment 放入 ViewCompose 渲染树，无法保留 Fragment 特有行为。

宿主暂存 controller 状态，尝试渲染原生树，并且只在渲染成功后提交导航事务。失败时保留此前
已提交的栈和已挂载目的地树。

隐藏 Presentation 生命周期是一项显式迁移决策。默认
`NavPresentationRetentionPolicy.DisposeWhenHidden` 最接近“保留导航状态并不要求存活
Composition”的模型：它保留 Destination/Graph Owner、ViewModel、SavedStateRegistry 和
`rememberSaveable` 值，同时释放原生 View Tree 与 Composition Effect。应用只应在选定正数缓存
上限后使用 `Bounded`，也只应在真机证据证明无界隐藏资源合理时使用 `RetainAll`。恢复后的 Host
只创建可见 Pane 集合；选择非活跃 Stack 时会先重建 Presentation，再发布该 Scene。

## Graph、route 与参数 {/* #graphs-routes-and-arguments */}

ViewCompose Graph、Destination、Entry 和 Stack 身份都是显式的。`NavRouteSpec<T>` 围绕一份
声明闭合应用使用路径：在 Graph 注册 Spec、用类型值导航，再用同一 Spec 解码 Entry。其 Callback
仍编码为 `NavValue` 基础类型集合，使 Controller Snapshot 保持确定且可保存。可选 Kotlinx
Adapter 可为扁平 Scalar Class/Object Schema 生成该 Codec；Structured Value 与任意 Navigation 3
应用 Key 仍不在契约内。

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
自己的 store/saved-state owner 和 route 默认值，并保留无关的 Application 与 DI extra。它们的
store 从任意 ViewCompose 子树也可使用的同一个 `ViewModelScopeProvider` 租赁。同 route entry
在保留栈中仍相互隔离。配置重建通过已保存的 host-scope 身份继续定位 lease；永久移除会发送
终态 clear，并阻止复活。

迁移自定义 ViewModel factory 之前，应同时在目的地和 graph 作用域验证所有必需的
`CreationExtras`、application 对象、默认参数和 `SavedStateHandle` 构造。仅存在
`ViewModelStoreOwner` 不足以作为证据。

## 生命周期与自适应 pane {/* #lifecycle-and-adaptive-panes */}

Lifecycle Planner 现在消费经过校验的 `NavScene`，不再接收彼此分离的 Retained、Visible 与
Interactive ID Set。每个 Destination 的目标都是 `min(host cap, scene cap, entry cap)`：

- 已保留的隐藏 entry 目标为 `CREATED`；
- 可见但不可交互的 entry 目标为 `STARTED`；
- 可交互 entry 目标为 `RESUMED`；
- 任何 entry 或 graph 都不能超过宿主生命周期。

先应用向下 transition，再应用向上 transition；永久删除的 entry 会在被揭示 entry 前进前
达到 `DESTROYED`。自适应 pane 可以让多个目的地同时可交互，因此可能有多个 entry 处于
`RESUMED`。从单栈顶 entry 模型迁移的代码不得把 `RESUMED` 当作目的地是唯一可见页面的证明。

普通或 Predictive Motion 期间，Android Host 会冻结一份语义 Scene。所有可见参与者都不可交互，
且不高于 `STARTED`；已 Pop 的离场 Destination 在退出展示释放前处于 `CREATED`。取消会恢复此前
稳定的 Scene，提交后也只有在终态稳定时才 Resume 进入 Destination。Destination DSL 与 Activity、
Fragment、Preview 和自定义容器内容使用同一个最近 `LocalLifecycleOwner` API，不需要导航专用
Lifecycle API。

Destination DSL 还可读取 `LocalNavDestinationContext.current` 以获得粗粒度 Presentation
语义。后续回调需要时，应在声明阶段捕获稳定 Holder；其 `entry` 身份与只读 Presentation State 可
跨原生 View 释放和重建存活。不要把资源激活从 AndroidX Lifecycle 搬到 Visibility 或 Transition
枚举上，也不要期望 Context 提供逐帧普通转场或 Predictive Progress。嵌套 Host 只在 Child
Destination 内容内发布 Child Holder，因此不存在全局 Current Page。

这是 Alpha 硬切。把原先两个 `NavLifecyclePlanner.plan` Overload 替换为接收 `entries` 与 `scene`
的唯一 Overload。为 Hidden、Pane、Transition、Overlay、Prepared、Exiting 与 Removed Role
构造 `NavSceneEntry`；不要在 Scene 旁边重建 Visible 与 Interactive Set。Android Host 已为当前
普通、Predictive 和自适应 Pane Scene 消费 Transition Role；不要根据 Core Layer 词汇推断已支持
通用 Overlay 导航。

ViewCompose 最多选择三个最新且符合条件的 pane entry。Navigation3 1.1.6 具有更通用的
scene 和 metadata 模型。其 1.1.3 嵌套 overlay 修复和 1.1.4 含 metadata lambda 的 popped
entry 修复属于上游可靠性变化，并不能证明 ViewCompose 支持任意 scene 或相同 overlay 动画
生命周期。

## 隐藏目的地保留 {/* #hidden-destination-retention */}

隐藏的 ViewCompose 目的地始终保留逻辑 Owner、ViewModel、Saved State 与 Saveable State，但
原生 Presentation 由 `NavPresentationRetentionPolicy` 独立控制。默认 `DisposeWhenHidden` 会在
转场稳定后释放 `RenderSession`、已挂载 View Tree 和 Composition Scope；页面 Lifecycle 仍保持
`CREATED`，再次可见前会事务性重建 Presentation。

`Bounded` 按正数上限保留最近隐藏的 Presentation，`RetainAll` 则是显式无界选择。无论使用哪种
策略，需要随页面可见或可交互阈值启停的业务工作仍应感知 Lifecycle；Presentation 释放只是资源
所有权边界，不替代 Lifecycle 协议。

## 多返回栈

`NavStackConfiguration` 声明栈集合、初始选择、选择历史行为和根 Back 策略。选择另一个栈时，
会保留上一个栈的 Entry 与 Owner，而 View 和 Session 是否存活由 Presentation Retention Policy
决定。因此，状态成本与原生展示成本可以分别约束。

Lifecycle 2.11 Navigation3 集成可以在多个栈显示之间提升 ViewModelStore provider，同时隔离
重复 key 的 store。ViewCompose 依赖自己的 stack 和 entry 身份。跨 tab 迁移重复 route key
之前，应验证两个 entry 获得不同 owner store、删除一个不会清除另一个，并验证保存/恢复保留
预期身份。

## 深层链接 {/* #deep-links */}

ViewCompose 深层链接使用一份平台无关请求承载 URI、action 与 MIME 数据。声明可以约束任一维度
或组合多个维度，且所有已声明约束都必须匹配。MIME 约束支持精确值与组件通配符；action 精确且
区分大小写。约束更多的声明优先，然后比较 URI 与 MIME 具体度；同分最佳匹配会被拒绝。URI 声明
可以指向嵌套 graph，并解码封闭的 `NavValue` 参数集合。

解析会在修改栈前拒绝格式错误的已提供字段、不可信 URI 组件、重复参数与歧义匹配。Android
宿主把 `Intent.data`、`Intent.action` 与 `Intent.type` 映射到同一请求，并忽略 extras 与
categories。

### 额外 query 参数

ViewCompose 容忍匹配 pattern 未声明的输入 query 参数，但这些参数完全不参与导航：不会进入
`NavRoute.arguments`、增加匹配具体度、消除原本的歧义、选择保留栈或覆盖调用方指定的 launch
mode。这样可兼容跟踪参数，同时防止未注册输入成为导航策略。若要求精确 key、签名或安全敏感
query 语义，应在路由前由应用验证完整 URL。

## 保存、恢复与进程死亡 {/* #save-restore-and-process-death */}

Android 宿主保存完整栈集合、当前栈、选择历史、route 参数、entry 与 graph saved-state registry、
ViewCompose saveable value，以及私有 host-scope 身份。Decoder 验证格式版本和 graph 结构；
版本 4 快照会用新的 scope 身份迁移。对于损坏或不兼容输入，采用 fail closed 并回退到安全初始
状态。当前防御性限制是最多解码 100 个栈和总计 10,000 个 entry。

配置重建在同一父 ViewModelStore 中继续使用保存的 scope 身份，因此会保留存活 ViewModel。
进程恢复不会复活 View 实例、ViewModel 实例、协程 Effect、动画、Predictive Back preview 或
未提交事务，而会从保存值重建 owner 和页面状态。应把进程死亡与配置变更、内存中的栈切换分别
测试。

## 系统 Back 与 Predictive Back {/* #system-back-and-predictive-back */}

当生命周期至少为 `STARTED` 且 Stack 可以 Pop 时，ViewCompose Android Adapter 会向最近的
`ViewTreeNavigationEventDispatcherOwner` 注册一个默认优先级 Handler。若该 Owner 不存在，则以
`OnBackPressedDispatcherOwner` 作为兼容回退；两条注册路径互斥。两者驱动同一套暂存的 Predictive
与普通 Pop 事务。Stop、Detach、禁用、Owner 替换和销毁都会先取消再注销，已取消手势迟到的终态
Callback 也不会变成一次普通 Back。位于根时会禁用 Handler，由 Dispatcher 选择下一个 Handler
或 Fallback。

Host 依赖 NavigationEvent 1.1.2，测试使用官方 Testing Dispatcher。它不会公开重复的 ViewCompose
Owner/Callback Facade、Forward-event History 或 Inspection-mode Preview Handler。Navigation3
1.1.6 保留 NavigationEvent 支持的 Preview；该 Preview 路径仍不在 ViewCompose 设备与 Adapter
证据内。

## 迁移路径

### 从 Navigation 2 迁移

1. 清点目的地类型，并隔离 Fragment 或 Activity 特有行为。
2. 仅使用受支持的 `NavValue` 参数类型翻译 graph 和 route 身份。
3. 把 `NavOptions`、`popUpTo`、single-top 和保存/恢复意图改写成显式的预期栈结果。
4. 映射目的地和 graph ViewModel 作用域，包括 factory、extras 和 saved-state 需求。
5. 显式配置多栈和根 Back 行为。
6. 把 URI、action、MIME 与组合规则迁移为 `NavDeepLink` 声明，并显式验证被拒绝和歧义的外部请求。
7. 验证事务回滚、进程死亡、系统 Back 和 predictive cancel。

### 从 Navigation 3 迁移

1. 决定哪些应用拥有的 entry key 变成 ViewCompose route、entry 和 stack 身份。
2. 用受支持的基础 route value 和 repository 查询替换任意 key 序列化。
3. 分别映射 decorator：saveable state、ViewModel store、lifecycle 和自定义 metadata 并不是
   一项 ViewCompose 能力。
4. 用受支持的 pane 策略替换通用 scene，或者把该 scene 记录为不支持。
5. 验证跨多栈重复 key，以及父级 factory/`CreationExtras` 传播。
6. 用一条受支持的事务式 controller 命令替换应用集合 mutation。
7. 使用 `NavHost` 已消费的最近官方 NavigationEvent Owner，不要再包一层重复 Owner；Forward
   History 与 Preview 专用输入仍由应用负责。

## 迁移风险与不支持行为

- 不支持 Activity 和 Fragment 目的地；它们只保留 Android 宿主角色。
- 可选 Kotlinx Adapter 支持生成的扁平 Scalar Route Serializer，但不支持任意 Nested、Collection、
  Polymorphic Route Object、Custom `NavType` 或 Navigation3 Key 优先级；不支持的 Wire Shape 使用
  显式 `NavRouteSpec<T>`。
- 已支持直接 Backward NavigationEvent 输入，但不支持 Forward History、ViewCompose Dispatcher
  Facade 与 Android Studio Preview 输入。
- 不支持通用 Navigation3 scene 策略与 metadata；pane 策略范围更窄。
- 隐藏会话保留 Effect 和原生 View，增加生命周期与内存风险。
- `NavHost` 缺少 `LocalViewModelStoreOwner` 时会直接失败；自定义 `renderInto` 宿主必须显式提供。
- 精确或签名 deep-link query 集合需要应用在路由前验证；未声明值默认可存在但完全不参与导航。
- NavigationEvent Preview 行为尚未验证；下述真机结果只覆盖文档所述 Android Host 与平台
  Back 路径。

## 重新核验要求

任何导航命令、entry 身份、graph 作用域、生命周期目标、深层链接规则、状态格式、pane 策略或
Back 集成发生变化时，都要重新核验本文。Navigation 2、Navigation3、Lifecycle、SavedState、
Activity 或 NavigationEvent 的稳定版基线发生变化时也一样。

最低本地证据包括 navigation-core controller、生命周期和深层链接测试；Android 宿主 owner、
saved-state、目的地会话和 Back adapter 测试；进程重建覆盖；以及已有文档记录的 API 35
Predictive Back 设备流程。上游部分需要重新执行官方语义复核。不得仅根据仓库 Compose 1.7.8
可执行依赖基线，推断已对齐 Navigation 2.9.8 或 Navigation3 1.1.6。
