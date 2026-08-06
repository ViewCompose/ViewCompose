---
translation_source: modules/viewcompose-host-android/README.md
translation_source_hash: 000b3d29f5a1a1161d8bd872103843a0730dffe352fa994d676643944ad7d2e9
translation_status: current
---

# Android 宿主引擎

`viewcompose-host-android` 是底层 Android View 宿主引擎，负责安装 renderer、管理保留式渲染
会话、按 Choreographer 帧调度失效、桥接 Android 状态与环境值、适配焦点/日志/Trace、发现可选
Android overlay host，并提供原生 View、动画和图形互操作。
它刻意不包含 Activity/Fragment 便捷入口、Material 主题解析、Lifecycle Local 或 ViewModel Local。

普通应用应优先依赖 [`viewcompose-android`](../viewcompose-android/README.md)。只有构建自定义容器
宿主，或脱离标准 Activity/Fragment 集成使用互操作 API 时，才直接依赖本模块。

## 构件与稳定性

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-host-android:0.1.0-alpha03")
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

`AndroidEnvironmentBridge.fromContext(context)` 会把 density、font scale、locale 与 layout
direction 映射为 `UiEnvironmentValues`。`AndroidOverlayHostDefaults.androidOrNoOp(root)` 执行可选
overlay 的 `ServiceLoader` 查找，Android 服务发现不会回流到 UI Foundation。

## 原生 View 事务契约

`AndroidView` 挂载平台 View，同时保留 renderer 的回滚语义：

```kotlin
AndroidView(
    factory = { context -> PlayerView(context) },
    update = { view -> configurePlayer(view as PlayerView, state) },
    key = playerId,
    onCommit = { view -> (view as PlayerView).play() },
    onRelease = { view -> (view as PlayerView).release() },
)
```

- `factory` 只在创建新原生节点时执行。
- `update`、`onReset` 与 `Modifier.nativeView` 必须是可重放配置。
- `onCommit` 只在整棵 View tree 提交后执行。
- `onRelease` 在正式移除或 session 释放后执行一次。

## 状态保存、调度与线程

`viewComposeSaveableStateRegistry(owner)` 把框架可保存状态绑定到 Android
`SavedStateRegistryOwner`。View 创建、协调、显式渲染与释放属于主线程工作。状态失效会合并到
下一次 Choreographer 帧，而显式 `RenderSession.render()` 保持同步执行。

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
