---
translation_source: modules/viewcompose-host-android/README.md
translation_source_hash: b48ae09c6dc7cfdc77895b3702cfad99b9b7141f631a734fbf7008b37d54d30e
translation_status: current
---

# Android 宿主引擎

`viewcompose-host-android` 是底层 Android View 宿主引擎，负责安装 renderer、管理保留式渲染
会话、按 Choreographer 帧调度失效、桥接 Android 状态与环境值、适配焦点/日志/Trace、为自定义
底层 Host 提供中立 Overlay 发现，并提供原生 View、动画和图形互操作。
它刻意不包含 Activity/Fragment 便捷入口、Material 主题解析、Lifecycle Local 或 ViewModel Local。

普通应用应优先依赖 [`viewcompose-android`](../viewcompose-android/README.md)。只有构建自定义容器
宿主，或脱离标准 Activity/Fragment 集成使用互操作 API 时，才直接依赖本模块。

## 构件与稳定性

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="host-android-module-dependency" sample_id="module.host-android-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-host-android:0.1.0-alpha04")
}
```

- 稳定性：**Alpha**。
- 平台：Android library，`minSdk 24`、`compileSdk 36`，Java 11 字节码。
- API 依赖：Runtime、UI Contract、UI Foundation，以及公开签名使用的 AndroidX Lifecycle 与
  AndroidX SavedState。
- 私有实现依赖：Android Renderer、Android coroutines、ConstraintLayout 与 DynamicAnimation。
- 本模块不依赖 Material Components。

本模块独占 `com.viewcompose.host.android`。Activity 与 Fragment 组合根使用
`com.viewcompose.android`，不会再静默扩张底层 Host 包。

## 自定义容器宿主

`renderInto(container)` 会安装 Android 引擎，并在返回前提交第一帧：

{/* compiled-region source="viewcompose-host-android/src/test/samples/com/viewcompose/host/android/samples/HostAndroidSamples.kt" region="host-render-into" sample_id="module.host-android-render-into" build_target=":viewcompose-host-android:compileDebugUnitTestKotlin" */}
```kotlin
fun renderIntoSample(container: ViewGroup) {
    val diagnostics = RenderDiagnostics(
        collection = RenderDiagnosticCollection(
            frameLevel = RenderFrameDiagnosticLevel.Stats,
        ),
        sink = { event -> println(event) },
    )
    val session = renderInto(container, diagnostics = diagnostics) {
        Text("Custom host")
    }
    session.setRenderingActive(false)
    session.render()
    session.dispose()
    check(runCatching(session::render).exceptionOrNull() is IllegalStateException)
}
```

传入 `diagnostics = RenderDiagnostics(...)` 会开启一棵关联诊断树。底层 `role` 与
`parentLocalSnapshot` 是独立渲染子 Session 的 Q3 集成控制；普通自定义 Root 保持默认 `Host`
角色且不传 Parent Snapshot。`debug` 只控制日志与慢操作告警，不控制诊断收集。

该底层入口不会自动提供 Lifecycle、ViewModel、saved state、environment、theme 或 frame-clock
Local。自定义宿主必须自行管理这些 Provider，并在放弃容器前释放会话。一个容器只能有一个
mounted-tree 所有者。
Dispose 幂等且为终态：之后由调用方发起的 `render` 或 `setRenderingActive` 会抛出
`IllegalStateException`。Android Runtime 内已经排队的帧回调会被取消或忽略，无法在释放后渲染。

帧对齐 Runtime 在 UI 线程调度热路径使用专用内部回调，不再经过通用捕获函数。跨线程请求仍只
投递一个有界 `Runnable`；同线程请求与 Choreographer 分发不会逐帧创建回调包装。

`AndroidEnvironmentBridge.fromContext(context)` 会把 density、font scale、locale 与 layout
direction 映射为 `UiEnvironmentValues`。`AndroidOverlayHostDefaults.androidOrNoOp(root)` 执行可选
中立 Overlay 的 `ServiceLoader` 查找，Android 服务发现不会回流到 UI Foundation。零个 Provider
时回退 no-op，多个时因 Classpath 顺序不得选择设计系统而失败。标准 Activity/Fragment Root 使用
显式 Factory，不走该发现路径。

需要 Android 资源的自定义 Host 应安装 `AndroidResourceEnvironment(context)`。Provider 内的内容
可以调用 `stringResource`、格式化字符串、`pluralStringResource`、`colorResource`、逻辑/像素尺寸、
Boolean/Integer 以及字符串/整数数组查询。`LocalAndroidContext.current` 与
`LocalAndroidResources.current` 是非常用 API 的受限逃生口；没有 Provider 时会抛出包含安装方式的
错误。

Provider 在挂载期间观察 Android Configuration Callback，重新发布密度、字体比例、语言、方向与
单调递增的资源版本，并随组合释放注销。稳定 Context Wrapper 被替换，或其他主动资源修改没有产生
Callback 时，每个 Host 使用一个 `AndroidResourceRefreshController`。调用、Callback 与释放都属于
主线程；资源结果是同步快照，不得在 Session 之外持有 Provider 的 Context 或 Resources。

{/* compiled-region source="viewcompose-host-android/src/test/samples/com/viewcompose/host/android/samples/HostAndroidSamples.kt" region="host-android-resources" sample_id="module.host-android-resources" build_target=":viewcompose-host-android:compileDebugUnitTestKotlin" */}
```kotlin
fun androidResourceEnvironmentSample(
    builder: UiTreeBuilder,
    context: Context,
    titleResource: Int,
) {
    builder.AndroidResourceEnvironment(context) {
        Text(stringResource(titleResource))
    }
}
```

## 可选 Session 检查边界

Host 从进程级内存 Slot 读取中立的 `RenderSessionInspectionTooling` 端口。没有 Provider 是正常
的生产配置，并在首个 Session 读取时冻结为稳定 No-op。下游 Tooling 制品可以在任何 Render
Session 启动前，通过 Q3 `installRenderSessionInspectionTooling` 集成 Hook 完成 Android
Component 初始化。同一实例重复安装是幂等的；多个不同的早期 Provider 会禁用该端口，晚于首次
读取的安装会被忽略。安装与首次读取均受同步保护，不执行 Classpath 扫描、文件 I/O 或 Android
Service 查找。Host 不包含设备定位协议、Android Component、报告写入器、View Tree Listener 或
持续检查生命周期。

真机 DSL 导航由下游可选制品 `viewcompose-preview` 实现。要启用该功能，应通过
`debugImplementation` 引入它。该制品存在于可调试进程时，可以通过中立端口保留首次成功
Host、Navigation Destination 或 Pager Page Frame 中的有界源码候选。
`RenderSessionInspectionPolicy` 会在不启用组合期源码捕获的前提下跟踪 Lazy Item、Overlay 与
Preview Session，因此按请求的节点检查可以到达真实 Child Owner，又不会在高频路径捕获 Stack。
Report 使用 Runtime Trace ID、Parent ID 与 Role，不再生成第二套仅源码身份。

同一注册还会收到 `RenderSessionNodeInspection`；在 Render Owner 之外，它只弱引用 Session State。
只有显式请求调用它时，才会进入 `CoreRenderEngine.inspectMountedNodes`；随后
`AndroidCoreRenderEngine` 执行有界的当前树遍历并返回弱 Native Target。没有 Provider 时不会创建
检查状态，也不会更新 Mounted-node 引用。只有 Android Studio 发出显式请求后，才会读取实时可见性、
Mounted Node 并写入私有报告。滚动、布局、Rendering Active 变化与 Session 释放都不会发布报告，
Host 也不持有 Overlay 或 IDE 协议。此所有权遵循
[ADR-0009](../../architecture/decisions/0009-development-tooling-isolation.md)。
无发现式初始化机制由
[ADR-0022](../../architecture/decisions/0022-in-memory-development-tooling-installation.md) 固定。

同一 Registration 还会收到中立的 Q3 `RenderSessionTimingInspection` Control。下游显式请求可以启动
一次有限的 Composition/Reconciliation/Binding Capture；Android Host 只负责把同步
`CoreRenderTimingCollector` 映射到 Android Renderer，不拥有协议、Poller、Report 或 Studio UI。
Engine 会让 Composition Node Identity 贯穿 Reconciliation 与 Binding；仅 Renderer 的节点则获得
不透明、当前 Capture 内有效的 Fallback。没有可选 Tooling 制品和显式请求时，普通 Host 渲染执行
零次逐节点时钟读取，也不保留 Timing History。

## 原生 View 事务契约

可复用集成应实现类型安全的 `AndroidViewAdapter<V, S>` 契约。Adapter 类与
`constructionKey` 标识构造敏感状态，`key` 则继续标识逻辑内容：

{/* compiled-region source="viewcompose-host-android/src/test/samples/com/viewcompose/host/android/samples/HostAndroidSamples.kt" region="host-android-view-adapter" sample_id="module.host-android-view-adapter" build_target=":viewcompose-host-android:compileDebugUnitTestKotlin" */}
```kotlin
fun typedAndroidViewAdapterSample(builder: UiTreeBuilder) {
    builder.AndroidView(
        adapter = NativeLabelAdapter,
        state = "Typed native label",
        key = "label",
        constructionKey = "default-text-appearance",
        modifier = Modifier.nativeView(key = "enabled") { view ->
            view.isEnabled = true
        },
    )
}

private object NativeLabelAdapter : AndroidViewAdapter<TextView, String> {
    override val reusePolicy: AndroidViewReusePolicy = AndroidViewReusePolicy.Resettable

    override fun create(scope: AndroidViewCreateScope): TextView = TextView(scope.context)

    override fun update(scope: AndroidViewUpdateScope<TextView>, state: String) {
        scope.view.text = state
    }

    override fun onReset(
        scope: AndroidViewResetScope<TextView>,
        reason: AndroidViewResetReason,
    ) {
        scope.view.text = null
    }
}
```

- `create`、`update`、Reset、Commit 与 Release 都在 Android 主线程同步执行。Create 接收
  Renderer 提供的带主题 `Context`；Create、Update、Reset 与 Commit Scope 还会暴露 VNode
  捕获的不可变 `UiEnvironmentValues`。
- `state` 仍由调用方持有。`update` 应用完整的可重放配置，并可能在回滚时再次运行。普通的同身份
  更新绝不会调用 `onReset`。
- Adapter 实现类或 `constructionKey` 变化时，Renderer 会创建并更新一个尚未挂载的候选 View。
  失败只释放候选并保留已提交 View；成功则原子替换，并恰好释放一次被替换 View。
- `AndroidViewReusePolicy.Resettable` 允许节点在 Lazy Key 之间复用 Mounted Tree。
  `onReset(..., MountedTreeReuse)` 只在旧逻辑 Session、Effect 与 Saveable Lease 全部结束后、
  新 Key 的 Update 前运行。默认 `Never` 会阻止包含该节点的 Mounted Tree 跨 Key。
- `onCommit` 只在完整 Composition 事务提交后执行。`onRelease` 在已创建 View 被永久放弃时
  执行一次，包括候选回滚、正式替换或移除、不可复用 Session 释放或复用缓存最终淘汰。
- `lifecycleMode` 是有界诊断元数据。原始 Adapter 报告 `None`，AndroidX 集成 Adapter 报告
  `AdapterManaged`。Host 只记录该值，不会因此安装 Owner Observer 或改变事务顺序。

基于 Callback 的 `AndroidView(factory, update, ...)` 重载仍是底层逃生路径，并委托相同的类型化
事务路径。其尾部 `constructionKey` 具有相同替换语义；提供 `onReset` 也只表示允许跨 Key 的
Mounted Tree 复用。

## 原生动画与图形互操作

`AndroidAnimationInterop` 可以启动平台 Animator，而不会把其生命周期转移给组合动画引擎。
`MotionLayoutView` 承载 AndroidX `MotionLayout`，`Modifier.androidAnimation` 则在绑定阶段应用可
重放的原生动画属性：

{/* compiled-region source="viewcompose-host-android/src/test/samples/com/viewcompose/host/android/samples/HostAndroidSamples.kt" region="host-android-animation" sample_id="module.host-android-animation" build_target=":viewcompose-host-android:compileDebugUnitTestKotlin" */}
```kotlin
fun platformAnimationInteropSample(target: View) =
    AndroidAnimationInterop.startObjectAnimator(
        target,
        "alpha",
        0f,
        1f,
        durationMillis = 180L,
    )

fun UiTreeBuilder.motionLayoutInteropSample() {
    MotionLayoutView(
        factory = { context -> MotionLayout(context) },
        update = { layout -> layout.progress = 0f },
        modifier = Modifier.androidAnimation(key = "settled-alpha") { view ->
            view.alpha = 1f
        },
    )
}
```

`AndroidGraphicsInterop` 提供受 API 级别约束的平台 `RenderEffect` 边界，
`Modifier.androidGraphics` 通过同一保留式 View 绑定路径应用原生图形状态：

{/* compiled-region source="viewcompose-host-android/src/test/samples/com/viewcompose/host/android/samples/HostAndroidSamples.kt" region="host-android-graphics" sample_id="module.host-android-graphics" build_target=":viewcompose-host-android:compileDebugUnitTestKotlin" */}
```kotlin
fun platformGraphicsInteropSample(target: View): Modifier {
    val effect = AndroidGraphicsInterop.createBlurEffect(radiusX = 12f, radiusY = 12f)
    AndroidGraphicsInterop.applyRenderEffect(target, effect)
    return Modifier.androidGraphics(key = "native-graphics") { view ->
        view.alpha = 1f
    }
}
```

## 状态保存、调度与线程

`viewComposeSaveableStateRegistry(owner)` 把框架可保存状态绑定到 Android
`SavedStateRegistryOwner`。View 创建、协调、显式渲染与释放属于主线程工作。状态失效会合并到
下一次 Choreographer 帧，而显式 `RenderSession.render()` 在终态释放前保持同步执行。

安装的 `AndroidCoreRenderEngine` 还会把 UI Foundation 的 Q3 Observed-property SPI 转换为精确
Android Renderer Target。Property-only Frame 会保持 Mounted Root List 与 Target Map 稳定，
校验每个 Target 仍属于已提交 Frame，并只返回 Commit Effect、Failure 与可选诊断。外来或陈旧
Target 会直接失败，不会触发整树渲染。当 Lazy Presentation 跨 Renderer 创建的 Host Wrapper
更换逻辑 Owner 时，Engine 只在同步 Render 期间传递该 Transaction Marker，并在 `finally` 中
清除；Wrapper 因而不能隐藏 Key 所有的 State Replacement，也不能在失败后保留 Transfer 状态。

## 相关文档

- [五层架构](../../architecture/decisions/0002-five-layer-runtime-module-architecture.md)
- [架构概览](../../architecture/overview.md)
- [渲染失败语义](../../architecture/render-failures.md)
- [Android 聚合模块](../viewcompose-android/README.md)

完整生成参考位于
[`viewcompose-host-android` API 树](https://docs.viewcompose.com/api/viewcompose-host-android/current/)。

## 兼容性说明

五层架构硬切后，Activity 与 Fragment 的 `setUiContent` 扩展迁移到 `viewcompose-android`，本底层
模块不保留兼容 facade。
`0.1.0-alpha04` 把 Overlay Service Discovery 收窄为单个中立 Provider；标准 Root 显式选择
Backend，重复 Provider 属于配置错误。
设备源码检查已移出本制品。Alpha `renderInto` 硬切用一个 `RenderDiagnostics` 配置替代三个
Render Callback，并增加类型化 Role/Parent 集成输入；自定义平台可以继续使用默认 `null` 端口。
需要 `Inspect Device Diagnostics` 的应用应在 Debug 配置中保留 `viewcompose-preview`，Release 构建
不会携带设备 Inspector 实现。
