---
translation_source: modules/viewcompose-navigation-android/README.md
translation_source_hash: e6325de31dea0aab7ffebc29008a7f19bebf5ec8ef32582f33f0956beb521b05
translation_status: current
---

# Android Navigation 模块

`viewcompose-navigation-android` 把 `viewcompose-navigation-core` 状态挂载为原生 Android View 页面。
它负责目的地和图的生命周期边界、带作用域的 ViewModel Owner Lease、SavedStateRegistry
命名空间、受策略约束的子渲染会话、事务失败恢复、Android 系统返回与预测性返回、自适应 pane
布局，以及感知命令类型的 View motion。

应用仍使用 Activity 或 Window 作为最外层 Android 宿主，但单个页面不需要 Activity 或 Fragment。
平台无关返回栈仍位于 `viewcompose-navigation-core`；本模块是它的 Android 执行边界。

## 产物与稳定性

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="navigation-android-module-dependency" sample_id="module.navigation-android-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-navigation-android:0.1.0-alpha02")
}
```

- 稳定性：**Alpha**。宿主、转场和预测性返回契约在 Alpha 版本之间仍可能演进。
- 平台：Android 库，最低 SDK 跟随仓库 Android 策略。
- API 依赖包括 Navigation Core、Runtime、UI Contract 和 UI Foundation，因为它们的 Route、State、
  Node 与 Builder 类型构成公开 Navigation API。
- 实现依赖包括 Android Host、Lifecycle、ViewModel 集成和中立 Android Overlay 传输。Android
  Renderer 仅通过 Android Host 私有传递，并不是本产物的直接依赖。
- 该产物会传递引入 `viewcompose-navigation-core`；只需要平台无关模型时可单独依赖 core。

## Controller 与宿主

{/* compiled-region source="viewcompose-navigation-android/src/test/samples/com/viewcompose/navigation/samples/NavigationAndroidSamples.kt" region="navigation-android-host" sample_id="module.navigation-android-host" build_target=":viewcompose-navigation-android:compileDebugUnitTestKotlin" */}
```kotlin
fun UiTreeBuilder.AppNavigation() {
    val controller = rememberNavHostController(
        startDestination = NavRoute("home"),
    )
    NavHost(controller = controller) { entry ->
        when (entry.route.name) {
            "home" -> HomePage(controller)
            "details" -> DetailsPage(controller)
            else -> error("Unknown route ${entry.route.name}")
        }
    }
}
```

自定义 Overlay Transport 属于构造输入。普通渲染之间应保持 Factory 引用稳定，仅在必须重建
Transport 时推进显式 Key：

{/* compiled-region source="viewcompose-navigation-android/src/test/samples/com/viewcompose/navigation/samples/NavigationAndroidSamples.kt" region="navigation-android-host-construction" sample_id="module.navigation-android-host-construction" build_target=":viewcompose-navigation-android:compileDebugUnitTestKotlin" */}
```kotlin
fun UiTreeBuilder.customOverlayNavHostSample(
    controller: NavHostController,
    overlayHostFactory: (ViewGroup) -> OverlayHost,
    overlayFactoryVersion: Any,
) {
    NavHost(
        controller = controller,
        overlayHostFactory = overlayHostFactory,
        key = overlayFactoryVersion,
    ) { entry ->
        Text(entry.route.name)
    }
}
```

一个 `NavHostController` 同时只能连接一个活跃 `NavHost`。导航命令必须在主线程调用且要求宿主
已连接，确保 core 事务、目的地渲染、owner 生命周期和原生 View 层级共用同一个提交边界。

### 类型化命令

{/* compiled-region source="viewcompose-navigation-android/src/test/samples/com/viewcompose/navigation/samples/NavigationAndroidSamples.kt" region="navigation-android-typed-route" sample_id="module.navigation-android-typed-route" build_target=":viewcompose-navigation-android:compileDebugUnitTestKotlin" */}
```kotlin
data class ArticleRoute(val articleId: Long)

fun typedRouteNavigationSample(
    controller: NavHostController,
    destination: NavRouteSpec<ArticleRoute>,
): ArticleRoute {
    controller.navigate(destination, ArticleRoute(articleId = 42L))
    return controller.snapshot.top.toRoute(destination)
}
```

Graph 声明、`navigate`、`replaceTop`、`reset` 与 `NavEntry.toRoute` 共用同一个
`NavRouteSpec<T>`。编码会在主线程且 Host 事务开始前完成，因此 Encoder 异常不会改变 Stack、
Render Tree、Owner Lifecycle 或 Result Inbox。Controller 和 Saved-state Adapter 仍只接收
`NavRoute`，不会保留存活 Route 对象或 Callback。

`NavHost` 分离保留逻辑 Owner 与原生 Presentation。Scene 发布前，会用最新环境重建缺失的可见
Presentation；失败则释放候选并保留已提交 Stack。不可观察内容输入变化时修改 `contentKey`；
Ownership 输入或 Overlay Factory 变化时修改 Host `key`。默认 Overlay Factory 显式使用
`viewcompose-overlay-android`，不会依赖 Classpath 发现。

## Destination Context 上下文

只有在声明最近 Destination 的内容时，`LocalNavDestinationContext.current` 才非空。稳定的
`NavDestinationContext` 会公开精确的 `NavEntry` 身份和只读
`State<NavDestinationPresentation>`。`NavDestinationPresentation` 是 Navigation Core
`NavSceneEntry` 的源码别名，因此 Visibility、Interaction、Transition Phase、Pane Role 和
Content/Overlay Layer Role 不会与 Lifecycle 规划所用 Scene 分叉。

{/* compiled-region source="viewcompose-navigation-android/src/test/samples/com/viewcompose/navigation/samples/NavigationAndroidSamples.kt" region="navigation-android-destination-context" sample_id="module.navigation-android-destination-context" build_target=":viewcompose-navigation-android:compileDebugUnitTestKotlin" */}
```kotlin
fun UiTreeBuilder.destinationContextSample(controller: NavHostController) {
    NavHost(controller = controller) { entry ->
        val presentation = checkNotNull(LocalNavDestinationContext.current).presentation.value
        Text("${entry.route.name}: ${presentation.visibility}, ${presentation.paneRole}")
    }
}
```

后续回调需要 Destination 身份时，应在 DSL 声明阶段捕获 Context。同一 Retained Entry 释放
Presentation 后仍保留它，永久移除后则停止更新。资源阈值使用 AndroidX Lifecycle；Presentation
仅用于粗粒度可见性、Pane 与 Transition UI。嵌套 Host 提供最近 Context，不存在全局 Current Page
查询。

## 向上一页返回结果

{/* compiled-region source="viewcompose-navigation-android/src/test/samples/com/viewcompose/navigation/samples/NavigationAndroidSamples.kt" region="navigation-android-results" sample_id="module.navigation-android-results" build_target=":viewcompose-navigation-android:compileDebugUnitTestKotlin" */}
```kotlin
val SelectedItemResult = NavResultKey.text("catalog.selection")

fun UiTreeBuilder.observeSelectedItem(onSelected: (String) -> Unit) {
    NavResultEffect(SelectedItemResult, onSelected)
}

fun returnSelectedItem(controller: NavHostController, itemId: String): NavResult =
    controller.popBackStack(SelectedItemResult, itemId)
```

已提交 Pop 会把值写入仍存活 Entry 的可保存 FIFO Inbox。`NavResultEffect` 在 Destination 到达
`RESUMED` 后至多消费一次；显式确认或重试应使用 `NavDestinationContext.results`。Key 仅属于
本地 Entry，不是全局或跨栈总线。

## 展示保留策略

`NavPresentationRetentionPolicy` 独立于 Entry Ownership 控制原生展示生命周期。
`DisposeWhenHidden` 是默认策略：转场稳定后，每个完全隐藏页面的子 `RenderSession` 和 View Tree
都会释放，而 Entry Owner 仍保留在 `CREATED`。`RetainAll` 是显式的无界选择，只应在实测证明
Surface 重建代价足以抵消内存、Effect、Focus、Accessibility 与原生资源成本时使用。`Bounded`
保留正数上限的隐藏展示，并按确定性的“最久未隐藏”顺序淘汰。可见 Pane、普通转场参与者和预测性
转场参与者都不计入该上限。

{/* compiled-region source="viewcompose-navigation-android/src/test/samples/com/viewcompose/navigation/samples/NavigationAndroidSamples.kt" region="navigation-android-presentation-retention" sample_id="module.navigation-android-presentation-retention" build_target=":viewcompose-navigation-android:compileDebugUnitTestKotlin" */}
```kotlin
fun UiTreeBuilder.BoundedPresentationNavigation(controller: NavHostController) {
    NavHost(
        controller = controller,
        presentationRetentionPolicy = NavPresentationRetentionPolicy.Bounded(
            maxHiddenPresentations = 2,
        ),
    ) { entry ->
        Text(entry.route.name)
    }
}
```

修改现有 Host 的策略不会重建 Host 或任何 Entry Owner。收紧上限会立即释放超限的隐藏展示；放宽
策略只影响之后创建或隐藏的展示，不会急切构建当前不可见页面。首次连接、配置恢复连接和进程恢复
连接都只物化当前可见 Pane 集合，即使选择 `RetainAll` 也是如此。

Retention 权衡与证据解释由[导航架构](../../architecture/navigation.md)维护。

## 命令结果与重入

Controller 命令返回 `NavResult`：

- `Committed` 报告宿主已应用的状态和 entry owner 差异；
- `NoChange` 报告合法但已经生效的命令；
- `Queued` 表示转场或回调正在执行，命令稍后串行运行；
- `Failed` 报告结构化渲染或提交上下文。

目的地回调可能在另一轮渲染、生命周期更新或 motion 完成期间同步导航。宿主会把重入命令排队，
只在当前操作达到终态后继续执行。因此 queued 结果不代表完成；应观察
`controller.navigationState` 获取最终提交的多栈状态。

Controller 提供即时不可变 `snapshot` 和 `stackState`，以及可观察 `navigationState`。Tab 选中
状态应从 `activeStackId` 派生，不要维护第二份状态源。

## 类型化 Plan 执行

Navigation Core Reducer 是 Lifecycle、Retention、Input、Accessibility 与 Back 策略的唯一来源。
Android Executor 在 Commit 前准备 Presentation，随后发布 Plan 指定的 Scene 与有序 Effect；
Rollback 和终态清理使用 Plan ID，而不检查 View。应用通常只使用 `NavHost`；Reducer 是测试和
自定义 Executor 的 Q3 边界。

## 目的地与图 Ownership

每个 Destination Entry 独立拥有 Lifecycle、ViewModelStore、SavedStateRegistry、
SavedStateHandle 默认参数和 Saveable State；Graph 实例为其后代持有同类 Scope。隐藏保留会维持
这些身份并限制 Lifecycle。Transition 参与者最高为 `STARTED`，已 Pop Entry 在 Presentation
释放前为 `CREATED`，只有永久移除才进入 `DESTROYED`；重复 Route 仍创建不同 Owner。

`NavHost` 要求最近的 `LocalViewModelStoreOwner` 并继承默认 Factory 与 `CreationExtras`；底层
`renderInto` 调用者必须显式提供。持久化 Host-scope ID 允许配置重建继续租用相同 Entry/Graph
Store，永久移除则清理它们。在 Destination 内容内使用 `ProvideNavGraphOwner(route)` 选择活跃
Graph Scope。通用 Overlay 导航生命周期尚不支持，Overlay Transport 本身不会创建该 Scene。

## 失败与回滚

Android 宿主保持 navigation core 的两阶段保证：先准备新目的地会话和 owner，再 stage 到 View
层级，然后提交纯栈状态，最后运行提交副作用。失败由 `NavFailurePhase` 分类。

`NavFailure.stackCommitted` 区分不可逆栈边界前后的失败。提交前失败会移除候选会话和 owner，
并回滚 core 事务；提交后失败保留已提交状态并报告副作用问题，不会假装旧栈仍是事实来源。

保留页面在显示前刷新失败时，会以 `DestinationRefresh` 和 `stackCommitted = false` 报告。
此前的 stack、pane scene、可见 View、owner 与会话继续有效，预测性返回 preview 或 pane 扩展
不会发布。

可向 `NavHost` 传入 `onFailure` 处理日志、降级或测试。未处理失败会抛出 `NavHostException`，
其中保留原始 cause、失败 entry 和 renderer frame report。

## 保存、恢复与进程死亡

Saveable Registry 会持久化 Stack、历史、Entry/Graph ID 与 Route、Owner Bundle 和 Saveable
值，不会序列化待处理工作、View、Session、Lifecycle 对象或 ViewModel 内容。恢复连接只物化可见
Pane。版本、结构、上限、配置或 Graph 层级无效时会 Fail Closed 到初始状态；相邻 Version 4 格式
会以新的 Host-scope ID 恢复。

## Android 系统返回与预测性返回

`NavHost` 仅在能消费 Back 时向最近的 AndroidX Dispatcher 注册，并在活跃根使用保留 Stack 历史。
Predictive Preview 不提交 Stack：取消恢复稳定 Scene，完成进入普通 Pop 事务，命令重定向从当前
视觉状态继续。Preview Owner 最高为 `STARTED`；Detach、关闭或销毁会取消未完成 Preview。

## Motion 动效

`NavTransitionSpec` 是覆盖所有命令和 Predictive Back 的纯视觉策略。
`NavDestinationTransform` 组合 Pane/dp 位移、Alpha、Scale、Timing 与 Easing；`None` 禁用 Motion。
Driver 会先布局端点、为 Transform 临时使用硬件 Layer，并从当前视觉属性重定向，不改变 Stack 或
Owner 语义。

## 共享内容动效

`sharedElement` 与 `sharedBounds` 是在单个 Destination Pair 内按 Key 与 Mode 唯一匹配的 Q3 标记。
无效、Detach、Surface-backed 或超预算端点按 Key 回退，不影响导航。单 Window 实现会在不可交互
Overlay 中执行有界 Snapshot 动画，保持 Incoming Input/Accessibility Ownership，且只清理一次；
Predictive Back 驱动同一视觉层，但不会获得 Commit 权限。

## 自适应 pane

`NavPanePolicy.Single` 在所有宽度下保持单页面全屏宿主。`Adaptive` 会在每个 pane 都满足最小宽度
时展示最多三个最新 entry。决定 pane 数之前会扣除 `paneSpacingDp`。

宽度变化会复用已提交返回栈、目的地会话和 owner，只在重新计算原生 child bounds 前刷新新进入
pane scene 的保留 entry。布局方向会把 primary 到 tertiary 映射为 LTR 或 RTL 下正确的物理顺序。

## 深链与保留栈

{/* compiled-region source="viewcompose-navigation-android/src/test/samples/com/viewcompose/navigation/samples/NavigationAndroidSamples.kt" region="navigation-android-deep-link" sample_id="module.navigation-android-deep-link" build_target=":viewcompose-navigation-android:compileDebugUnitTestKotlin" */}
```kotlin
fun navigateSharedImageRequest(controller: NavHostController): NavDeepLinkResult {
    return controller.navigateDeepLink(
        NavDeepLinkRequest(
            action = Intent.ACTION_SEND,
            mimeType = "image/png",
        ),
    )
}

fun navigateSharedImageIntent(
    controller: NavHostController,
    intent: Intent,
): NavDeepLinkResult {
    return controller.navigateDeepLink(intent)
}
```

Request、URI 与 Intent 入口共用严格 Core Resolver；Intent 只映射 `data`、`action` 和 `type`。
Match 会原子更新并选中目标 Stack，嵌套 `NavResult` 仍保留 Render/Commit 失败。多 Tab 应共享一个
Remembered Controller 和 `NavStackConfiguration`，不要镜像 Active-stack State。

## 相关文档

- [Navigation Core 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-navigation-core)
- [完整导航指南](https://docs.viewcompose.com/zh-CN/guides/navigation)
- [生命周期与 Saved State 架构](https://docs.viewcompose.com/zh-CN/architecture/lifecycle-and-saved-state)
- [Session 容器架构](https://docs.viewcompose.com/zh-CN/architecture/session-containers)
- [源码文档与 API 注释规范](https://docs.viewcompose.com/zh-CN/project/api-documentation-quality)

完整生成参考位于
[`viewcompose-navigation-android` API 树](https://docs.viewcompose.com/api/viewcompose-navigation-android/current/)。

## 兼容性说明

`0.1.0-alpha01` 确立了一个 controller 对一个 host 的连接、主线程串行命令、目的地和图 ownership、
防御式进程死亡恢复、预测性返回 preview、对齐 Android 的原生 View motion，以及最多三个自适应
pane。请通过 `rememberNavHostController` 持久化 controller 状态，不要在宿主之外保留 Android
owner 或 session 对象。

Lifecycle 2.11 硬切要求 `NavHost` 位于 `LocalViewModelStoreOwner` 下。现有 Activity 与 Fragment
`setUiContent` 集成已满足要求；自定义 `renderInto` Host 必须补充
`ProvideViewModelStoreOwner`，不会保留隐式 Root Store 或兼容别名。

类型化 Shared-content Marker 是新增 Q3 UI Contract API，但只有发布稳定端点 Tag 的 Renderer 与
本 Navigation Host 实现组合时才产生动效；旧版或自定义 Renderer 可以把 Marker 视为无效。
跨 Window、跨 Activity、跨 Process、Live Content、Shape Morph 与任意 Surface-backed Capture
在本 Alpha 中有意不支持，并回退到普通 Destination Motion。
