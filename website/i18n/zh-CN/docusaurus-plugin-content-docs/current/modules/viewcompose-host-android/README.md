---
translation_source: modules/viewcompose-host-android/README.md
translation_source_hash: 6eb5cba3a803cf912e860c1d8bdd1e327d1fc3b3c1bbb56e1514d7fa90c07b4b
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

```kotlin
val session = renderInto(container) {
    CustomSurface()
}

session.setRenderingActive(false)
session.render()
session.dispose()
```

该底层入口不会自动提供 Lifecycle、ViewModel、saved state、environment、theme 或 frame-clock
Local。自定义宿主必须自行管理这些 Provider，并在放弃容器前释放会话。一个容器只能有一个
mounted-tree 所有者。
Dispose 幂等且为终态：之后由调用方发起的 `render` 或 `setRenderingActive` 会抛出
`IllegalStateException`。Android Runtime 内已经排队的帧回调会被取消或忽略，无法在释放后渲染。

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

## 可选源码检查边界

Host 会对中立的 `RenderSessionSourceTooling` 端口执行一次进程级 `ServiceLoader` 查找。没有
Provider 是正常的生产配置，并稳定表现为 no-op。发现多个 Provider 或查找失败时会禁用源码检查并
记录诊断，不会改变渲染。Host 不包含设备定位协议、Android Component、报告写入器、View Tree
Listener 或持续检查生命周期。

真机 DSL 导航由下游可选制品 `viewcompose-preview` 实现。要启用该功能，应通过
`debugImplementation` 引入它。该制品存在于可调试进程时，可以通过中立端口保留首次成功
Host/Page Frame 中的有界源码候选。只有 Android Studio 发出显式请求后，才会检查实时可见性并写入
私有报告。滚动、布局、Rendering Active 变化与 Session 释放都不会发布报告。此所有权遵循
[ADR-0009](../../architecture/decisions/0009-development-tooling-isolation.md)。

## 原生 View 事务契约

`AndroidView` 挂载平台 View，同时保留 renderer 的回滚语义：

```kotlin
AndroidView(
    factory = { context -> PlayerView(context) },
    update = { view -> configurePlayer(view as PlayerView, state) },
    key = playerId,
    onReset = { view -> resetPlayer(view as PlayerView) },
    onCommit = { view -> (view as PlayerView).play() },
    onRelease = { view -> (view as PlayerView).release() },
)
```

- `factory` 只在创建新原生节点时执行。
- `update`、`onReset` 与 `Modifier.nativeView` 必须是可重放配置。
- `onReset` 允许节点在 Lazy Key 之间复用 Mounted Tree。它只在旧逻辑 Session、Effect 与
  Saveable Lease 全部结束后、新 Key 绑定前运行。
- `onCommit` 只在整棵 View tree 提交后执行。
- `onRelease` 在已创建 View 被永久放弃时执行一次，包括候选回滚、正式移除、不可复用 Session
  释放或复用缓存最终淘汰。省略 `onReset` 会阻止该 Mounted Tree 跨 Key。

## 状态保存、调度与线程

`viewComposeSaveableStateRegistry(owner)` 把框架可保存状态绑定到 Android
`SavedStateRegistryOwner`。View 创建、协调、显式渲染与释放属于主线程工作。状态失效会合并到
下一次 Choreographer 帧，而显式 `RenderSession.render()` 在终态释放前保持同步执行。

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
设备源码检查已移出本制品。`renderInto` 签名不变；自定义平台可以继续使用默认 `null` 端口。
需要 `Locate Device DSL` 的应用应在 Debug 配置中保留 `viewcompose-preview`，Release 构建不会
携带定位实现。
