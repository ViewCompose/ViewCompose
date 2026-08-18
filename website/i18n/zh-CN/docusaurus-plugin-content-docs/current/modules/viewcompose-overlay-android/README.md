---
translation_source: modules/viewcompose-overlay-android/README.md
translation_source_hash: 2152c104619c26364cbe1c3d6c75e5f39243a1e637febf8699ef647fb5fbab9b
translation_status: current
---

# 中立 Android Overlay 集成

`viewcompose-overlay-android` 是 ViewCompose 不依赖 Material 的 Android Overlay 传输层。它把 UI
Foundation 请求映射到 Android `Dialog`、`PopupWindow` 与 `Toast`，负责嵌套 Overlay 渲染容器及
Root/Session 清理，并为必须由设计系统持有的行为提供窄 Presenter 插槽。

本产物不依赖 Material Components、AppCompat、`viewcompose-material3` 或
`viewcompose-oneui7`。它是 `viewcompose-android` 与 `viewcompose-navigation-android` 的默认
Overlay Runtime。

## 产物与稳定性

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-overlay-android:0.1.0-alpha04")
}
```

- 稳定性：**Alpha**。`0.1.0-alpha04` 在 `alpha03` 之后有意恢复该坐标，并采用不兼容的中立
  传输语义。
- 平台：Android Library，`minSdk 24`、`compileSdk 36`、Java 11 Bytecode。
- API 依赖：UI Contract 与 UI Foundation，因为公开 API 暴露其容器和 Overlay 契约。
- 实现依赖：Host Android；不允许依赖具名设计系统。

应用通常从 `viewcompose-android` 传递获得本产物。只有自定义底层 Host 或设计系统 Adapter
才应直接依赖它。

API 质量：`AndroidOverlayHost` 与 Factory Provider 是 Q3 Root 集成 API；
`asOverlayRenderContainerHandle` 是 Q2 底层容器 Adapter。规范 KDoc 与可编译 Sample 定义其
生命周期、线程、Ownership 和 Fallback 契约。

## Root 作用域 Host

`AndroidOverlayHost(rootView)` 为单个 Root 提供：

- 声明式 Dialog 对应的 Android `Dialog`；
- Popup 对应的 `PopupWindow`、锚点观察与溢出定位；
- Android `Toast` 及近似队列完成；
- Snackbar 与模态 Bottom Sheet 的显式 Unsupported Presenter；
- 具名设计集成可注入的可选 Presenter。

Host 不发现设计系统，也不会替换成 Material 控件。`integrationAttribution` 会为每类 Overlay
报告 Transport、Presenter、Conformance 与 Fallback；自定义 Presenter 在所属设计 Adapter
发布更具体证据之前标记为未验证。

`PopupWindow` 是传输与定位边界，不是视觉 Surface。它的平台 Elevation 固定为零，因此
`DropdownMenu` 等 Popup 内容只由自身声明 Shape 与 Elevation。没有声明 Elevation 的通用
`Popup` 内容也不会隐式获得矩形 Window 阴影。

当 Popup 内容包含使用原生 Elevation 的后代时，传输层会按照最高有效 Elevation 预留透明视觉
外扩区，并关闭 Window 内祖先容器的裁剪。锚点对齐、溢出计算和 Offset 仍以语义内容矩形为准；
扩大的平台 Window 只负责容纳内容自有的阴影。启用点击外部关闭时，透明外扩区中的按下仍按内容
外部操作处理。

每个已附着 Render Root 创建一个 Host。提交和清理必须在主线程执行。清理 Session 只关闭该
Session 的 Surface，移除监听，并在释放平台 Window 前销毁嵌套渲染 Session。

## 嵌套渲染容器

`asOverlayRenderContainerHandle()` 把 Overlay 所有的 Android `ViewGroup` 适配为嵌套
ViewCompose Render Session，而不向 UI Foundation 泄漏 Android 类型。Overlay Owner 必须在
永久 Detach 容器前销毁嵌套 Session。

Dialog 与 Popup 内容保留声明时捕获的 Composition Local 快照，因此延迟 Surface 不会读取后来
变化的进程级设计身份。

## 底层发现

Host Android 仍为自定义 Host 提供 `AndroidOverlayHostDefaults.androidOrNoOp(rootView)`。
Java `ServiceLoader` 只允许从本产物发现一个中立 Provider：零个时回退 no-op 并记录诊断，多个
时确定性失败。标准 Activity、Fragment 与 Navigation Root 不通过发现来选择设计行为。

## 相关文档

- [Overlay 架构决策](../../architecture/decisions/0006-root-scoped-overlay-backend-selection.md)
- [Overlay 指南](../../guides/overlays.md)
- [UI Foundation 模块](../viewcompose-ui-foundation/README.md)
- [Material 3 Overlay Adapter](../viewcompose-overlay-material3-android/README.md)
- [One UI 7 Overlay Adapter](../viewcompose-overlay-oneui7-android/README.md)

生成式参考位于
[`viewcompose-overlay-android` API 目录](https://docs.viewcompose.com/api/viewcompose-overlay-android/current/)。

## 兼容性说明

截至 `0.1.0-alpha03`，该坐标混合了通用 Android 传输与 Material 呈现。五层迁移曾将其退役并
替换为 `viewcompose-overlay-material3-android`。`alpha04` 不保留转发壳，直接恢复为唯一中立
Android 传输。Material Snackbar 与 Bottom Sheet 现在必须通过显式 Material Adapter 或
`viewcompose-material3-android` 获得。
