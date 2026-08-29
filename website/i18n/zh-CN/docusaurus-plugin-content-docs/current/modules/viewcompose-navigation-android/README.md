---
translation_source: modules/viewcompose-navigation-android/README.md
translation_source_hash: 6c8bc72620c7438f20c97298e9f3a4bbcd3c119a482e19d9c62920e77d270ac7
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

一个 `NavHostController` 同时只能连接一个活跃 `NavHost`。导航命令必须在主线程调用且要求宿主
已连接，确保 core 事务、目的地渲染、owner 生命周期和原生 View 层级共用同一个提交边界。

`NavHost` 为每个目的地保留一条逻辑 owner 记录，并仅在策略和可见性要求原生展示时创建子渲染
会话。隐藏 entry 始终保留 Lifecycle、ViewModel、Saved-state、Saveable-state、Route 和 Graph
Identity。没有展示实例的目的地通过 Pop、Stack 选择或历史、预测性返回、自适应 Pane 扩展而进入
可见集合前，会先使用最新捕获环境重建展示。重建失败会释放所有候选展示，并保留此前的 Stack 与
Scene。

每个目的地 Session 都会获得 `NavigationDestination` 诊断角色，以及随 `NavHost` Local 快照
捕获的父 Session ID。保留期间逻辑身份不变；失败候选会发出自己的终止序列，重建目的地则获得
新的 ID。恢复目的地 Local 时不能覆盖子 Session 所有者。

目的地闭包依赖不可观察值时应修改 `contentKey`。可观察状态会直接使所属目的地会话失效。
`key`、controller identity、lifecycle owner、调试 identity 或 overlay factory 的变化会重建
原生宿主，因为这些输入改变的是 ownership，而非普通内容。

默认嵌套 Overlay Factory 显式构造 `viewcompose-overlay-android`，不会按 Classpath 顺序发现
Material Backend。具名设计集成在目的地 Surface 需要额外 Presenter 时可以传入显式 Factory。

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

Phase 4 的真机对比使用一台运行 API 33 的 Pixel 4 XL 和合成的重型 13 层栈。
`DisposeWhenHidden` 保留 1 个 Presentation，进程 PSS 为 185,510 KiB；`RetainAll` 保留 13 个，
PSS 为 191,953 KiB。即 Presentation 少 12 个（92.3%），进程 PSS 少 6,443 KiB（3.4%）。同步
Pop 并重建的中位耗时从 13,318 us 增至 49,573 us，即增加 272.2%。另一轮带动画对比为每个策略
在 90 Hz 下采集 252 帧；两者 P95 均为 9 ms，超过 32 ms 的帧均为 0。结论为 **mixed**：有界
默认策略改善空闲资源所有权，并在本次稳定帧样本中为 **no material change**；实测重建昂贵的页面
可以选择 `Bounded` 或 `RetainAll`。这不是通用 Benchmark：它只使用一台设备、合成内容、进程级
PSS 与短时运行。下一步由 Phase 7 继续验证更广设备、泄漏和代表性负载。

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

## 目的地与图 Ownership

每个目的地 entry 都拥有独立 Android owner，其中包括：

- 受宿主和语义 Scene 限制的 Lifecycle，其中包括 Visibility、Interaction、Transition 与保留
  Entry Presence；
- 从共享 Lifecycle 2.11 Scoped-owner Provider 租用、仅在 Entry 离开所有保留状态后清理的
  ViewModelStore；
- 从 `NavRoute` 派生默认 SavedStateHandle 参数的 SavedStateRegistry；
- 页面独享的 ViewCompose saveable-state registry 命名空间。

Destination 内容会把该对象安装到 `LocalLifecycleOwner`、`LocalSavedStateRegistryOwner`、
`LocalViewModelStoreOwner` 与 ViewCompose Saveable-state Local。Graph 内容也通过相同四个边界
安装选中的 Graph Owner。Retained Hidden Destination 保留 Owner Identity 与持久数据，但获得受限
Lifecycle，因此 `LifecycleAndroidViewAdapter` 无需依赖物理移除就能让原生 View 进入非活跃状态。

普通或 Predictive 转场会为所有 Owner 协调冻结一份语义 Scene。可见参与者在终态稳定前不高于
`STARTED`。已 Pop 的离场 Entry 在退出 View 仍展示时限制为 `CREATED`，随后先释放 Session，
再让 Owner 到达 `DESTROYED`。Predictive 取消会恢复此前稳定的 Owner 状态；提交则进入同一套受限
Pop 转场。稳定的自适应 Pane 可以分别处于 `RESUMED`，但 Scene 变化期间所有可见 Pane 都限制为
`STARTED`。

Navigation Core 定义了 Overlay Layer Role，但本 Android Host 尚未公开通用 Overlay 导航 Scene。
独立 Overlay Host Transport 不能被解释为导航 Overlay 的 Lifecycle 集成。

同一个 route 连续 push 两次会创建两个 owner，不会共享页面状态。

`NavHost` 要求最近的 `LocalViewModelStoreOwner`，不会创建私有兜底 Store。标准 Activity 和
Fragment `setUiContent` Host 会安装该 Owner；使用底层 `renderInto` 时则必须显式调用
`ProvideViewModelStoreOwner`。若父 Owner 实现 `HasDefaultViewModelProviderFactory`，子 Owner
会继承其默认 Factory 和初始 `CreationExtras`，再只替换 ViewModelStore Owner、Saved-state
Owner 以及 Route 或 Graph 默认参数，保留无关的 Application 与 DI Extra。父 Owner 身份变化会
重建原生 Host，因此保留栈不会混用两套父级 Provider 契约。

Controller 会把私有 Host Scope 身份与栈状态一并保存。在同一个保留式父 Store 下，以恢复后的
Controller 身份重建 Host 时，会重建 Destination Lifecycle 与 Saved-state Owner，但重新租用
相同 Entry/Graph ViewModelStore。正常移除 Host、永久 Pop、移除 Graph 或替换 Controller 都会
发出终态清理信号。这样 Android 展示生命周期与逻辑页面状态生命周期得以分离，也不再需要导航
专用 Store Allocator。

嵌套图实例拥有 `NavGraphOwner`。同一个图实例内的目的地共享 Lifecycle、ViewModelStore 和
SavedStateRegistry，直到最后一个后代离栈。之后再次进入同名图 route 会创建新 owner。

`LocalNavGraphOwnerScope.current` 暴露从根到叶的活跃 owner 链。需要以图而不是叶子目的地解析
生命周期、ViewModel 和 Saved State 时，使用 `ProvideNavGraphOwner(route)` 包裹子树。在目的地
内容之外调用或指定非活跃图会失败。

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

`rememberNavHostController` 使用当前 ViewCompose saveable-state registry，保存：

- 所有保留 stack、活跃 stack 和选择历史；
- 目的地及图实例 ID 与 route 参数；
- 目的地和图的 SavedStateRegistry Bundle；
- 每个页面或图拥有的 ViewCompose saveable 值。

待处理事务、运行动画、View、会话、LifecycleRegistry 实例和 ViewModelStore 内容不会序列化。
首次连接和恢复连接只物化可见 Pane 集合；保留的隐藏 Owner 会重建，但不会急切执行目的地内容。
配置重建可以通过父 Store 保留活跃 ViewModel；进程重建则会根据恢复后的状态输入创建新实例。

恢复采用防御式策略。未知版本、错误集合类型、过多 entry、配置不匹配或图层级变化都会丢弃不兼容
状态并重建初始状态，避免应用升级后把旧 Saved State 命名空间挂到另一个页面 owner。

当前格式还接受紧邻的 Version 4 快照：保留合法栈和 Destination 状态，同时分配新的 Host Scope
身份。进程或应用代码重启不会保留活跃父 Store，因此该迁移不会错误复用旧实例。

## Android 系统返回与预测性返回

`systemBackEnabled = true` 时，`NavHost` 仅在 controller 能消费返回时注册到最近的 AndroidX
Back dispatcher。活跃栈到根后遵循保留栈历史配置，否则继续向外层宿主或 Activity 分发。

支持预测性返回的平台上，手势进度会展示上一目的地，但不提交 core 栈。取消时通过弹簧回到已提交
状态；完成手势时使用与程序化 `popBackStack` 相同的事务和 owner 边界。程序化命令可以重定向
活跃 preview，并保留当前视觉 transform，从而连续衔接。
两个 Preview 页面都限制为 `STARTED`；提交后，已 Pop 页面在退出展示移除前限制为 `CREATED`，
只有稳定后才会 Resume 进入页面。

View detach、关闭系统返回或销毁宿主时会主动取消未完成 preview，因为 dispatcher 可能不再发送
终止回调。

## Motion 动效

`NavTransitionSpec` 只是视觉策略，不会改变导航状态或 ownership。它分别配置 push、pop、replace、
reset、stack selection、deep link 和 predictive Back motion。

`NavDestinationTransform` 组合 pane 比例位移、dp 位移、alpha 和 scale。几何与进入/离开 alpha
可拥有独立 duration、delay 和 `NavMotionEasing`。默认 push/pop 几何和 emphasized easing 对齐
当前 Android Activity motion；预测性返回对齐当前 WM Shell 跨 Activity 几何。测试或应用需要
禁用 motion 时使用 `NavTransitionSpec.None`。

View driver 会先绘制完整起始布局，再开始 motion；只变化 transform/alpha 时会临时把昂贵页面
层级提升到硬件 layer。被重定向的 motion 会保留当前视觉属性，后续命令不会跳回 identity 帧。

## 共享内容动效

`Modifier.sharedElement(SharedContentKey(...))` 与 `Modifier.sharedBounds(...)` 是由 `NavHost`
自动消费的 Q3 端点标记，不需要 `SharedTransitionLayout` 或动画 Scope。Key 只在一对
Outgoing/Incoming Destination 内有效；两棵树都必须对同一 Key 和 Mode 各声明一次才能配对。
缺失、重复、Mode 不匹配、Detach、零尺寸、Surface-backed 或超过预算的端点会按 Key 回退到普通
Destination Motion，绝不改变导航事务。

首版仅支持单 Window Snapshot Motion。`sharedElement` 把 Source Snapshot 移动到 Target Bounds；
`sharedBounds` 沿同一路径移动 Bounds，并交叉淡化 Source/Target Snapshot。Snapshot 按 Outgoing
Tree 的稳定顺序绘制在不可交互的 Host Overlay 中，单次转场最多使用两个 Host Area 的像素。
Incoming Destination 始终拥有 Input 与 Accessibility。成功 Commit 时，已聚焦 Source 可把焦点
转给可聚焦 Target；取消则恢复 Source。完成、取消、重定向、Host 销毁、捕获失败与 Session 释放
都会且只会清除一次 Snapshot 并恢复端点状态。Predictive Back 让同一 Overlay 跟随手势进度，
Commit 时从该 Fraction 继续，但 Overlay 不获得 Stack Commit 权限。

## 自适应 pane

`NavPanePolicy.Single` 在所有宽度下保持单页面全屏宿主。`Adaptive` 会在每个 pane 都满足最小宽度
时展示最多三个最新 entry。决定 pane 数之前会扣除 `paneSpacingDp`。

宽度变化会复用已提交返回栈、目的地会话和 owner，只在重新计算原生 child bounds 前刷新新进入
pane scene 的保留 entry。布局方向会把 primary 到 tertiary 映射为 LTR 或 RTL 下正确的物理顺序。

## 深链与保留栈

String、Android `Uri` 和 `ACTION_VIEW Intent` 入口都使用同一个严格图解析器。匹配结果会转换为
原子命令，同时更新和选中声明的目标 stack。`NavDeepLinkResult.Navigated` 仍包含 `NavResult`，
因此 URI 匹配成功不会与渲染或提交成功混淆。

多个 Tab 应声明一份 `NavStackConfiguration`，并与共享 graph 一起 remember。不要为每个 Tab 创建
一个 controller，也不要在应用字段中镜像活跃 stack；controller 已负责保留每个 stack 和选择历史。

## 相关文档

- [Navigation Core 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-navigation-core)
- [完整导航指南](https://docs.viewcompose.com/zh-CN/guides/navigation)
- [生命周期与 Saved State 架构](https://docs.viewcompose.com/zh-CN/architecture/lifecycle-and-saved-state)
- [Session 容器架构](https://docs.viewcompose.com/zh-CN/architecture/session-containers)
- [源码文档与 API 注释规范](https://docs.viewcompose.com/zh-CN/project/api-documentation-quality)

完整生成参考位于
[`viewcompose-navigation-android` API 树](https://docs.viewcompose.com/api/viewcompose-navigation-android/current/)。

## 共享 Scoped Owner 验收

对比基线是 148 项 Navigation Android 测试，其 ViewModelStore 由导航自行分配，Activity 与
Fragment Owner 直接注入。Phase 3 的 Clean Run 通过 151/151 项 Navigation Android 测试和
21/21 项 Android Aggregate Host Case，无 Skip、Failure 或 Error。新增三项导航契约分别覆盖
缺少 Owner 时失败、Host 重建时保留 ViewModel Identity，以及 Version 4 状态迁移；Aggregate
Host 的源码测试方法由 10 增至 11 项，并区分 Activity ViewTree 发现与 Fragment 显式 Owner
优先级。

结论为 **improved**。导航现已共享通用 Lifecycle 2.11 Provider，可跨配置重建保留 ViewModel，
在逻辑移除时清理，且不再私有分配 ViewModelStore。这些结果属于 JVM/Robolectric 证据；没有测量
真机内存、进程终止、帧耗时或 OEM 生命周期顺序，因此这些维度仍为 **inconclusive**。Phase 4
将继续删除独立 SavedStateHandle Holder；单独的导航 Lifecycle/Scene Plan 仍负责设备、内存、
Presentation Retention 与 Transition Projection 验收。

## 转场 Lifecycle 验收

此前 151 项 Navigation Android 基线把进入页面过早设为 `RESUMED`，并把已 Pop 离场页面保留为
`STARTED`。替换为冻结语义 Scene 断言后，新鲜执行仍为 151/151 全部通过；普通、Predictive、
重定向、Host Cap 与自适应转场的定向子集为 20/20。

随后，App 设备 Harness 在 API 33 的 Pixel 4 XL 真机上通过 2/2 项定向用例。新用例读取
Destination DSL 内捕获的最近 `LocalLifecycleOwner`，验证 Push、Predictive Preview、取消、提交、
已 Pop 离场、销毁与终态 Resume；配套旧用例使用真实原生 View 验证 Predictive Progress 与取消。
这是 **improved** 的 Lifecycle 结果，当前支持的 Host Scene 已不存在提前 Resume。通用导航 Overlay、
API 34 平台边缘手势分发、内存、泄漏与性能结果仍为 **inconclusive**；下一计划阶段负责
Presentation Retention Policy 及其设备测量。

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
