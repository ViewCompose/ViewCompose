---
translation_source: modules/viewcompose-overlay-material3-android/README.md
translation_source_hash: 5e86f2c99e16ea3a8cd8304a0159c6507f2cea01ba14322bf699f3181e33c58a
translation_status: current
---

# Material 3 Android Overlay 适配器

`viewcompose-overlay-material3-android` 是 ViewCompose 的窄型 Material 呈现 Adapter。它向中立
[`viewcompose-overlay-android`](../viewcompose-overlay-android/README.md) 传输层提供 Material
Components Snackbar 与模态 Bottom Sheet Presenter。它不负责通用 Dialog、PopupWindow、Toast、
锚点定位、嵌套渲染容器或服务发现。

## 产物与稳定性

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-overlay-material3-android:0.1.0-alpha01")
}
```

- 稳定性：**Alpha**。
- API 依赖：UI Contract 与 UI Foundation。
- 实现依赖：中立 Android Overlay、Host Android、Android Renderer Shape Bridge、AppCompat 与
  Material Components。
- 普通 Material 应用通过 `viewcompose-material3-android` 传递获得本产物。

API 质量：Material `AndroidOverlayHost` 仍是 Q3 Root 集成 API。其公开 Attribution 快照是诊断
证据，不是可变 Presenter Registry。

## 显式 Material 装配

`com.viewcompose.overlay.material3.android.host.AndroidOverlayHost(rootView)` 组合：

- 中立 Android Dialog、PopupWindow、Toast、嵌套 Session 与清理行为；
- Material `Snackbar` 呈现和终止回调映射；
- Material `BottomSheetDialog` 呈现与行为；
- Material Adapter 已验收的 24dp Dialog Window Inset。

本 Adapter 不注册 `AndroidOverlayHostFactoryProvider`。`setMaterial3UiContent` 显式选择
Material，因此仅把本产物放入 One UI 或中立应用的 Classpath 不会改变 Overlay 行为。

`integrationAttribution` 会分别报告中立 Transport 与 Material Presenter。Dialog 和 Popup
内容保留声明时捕获的 Material Token/Recipe 快照；Snackbar 与模态 Bottom Sheet 报告为
`Equivalent`，Android Toast 仍是显式 `Degraded` 平台 Fallback。

Material Snackbar 会把 Action、Timeout、Swipe、Replacement 与通用平台终止事件映射到 UI
Foundation 队列。Material Modal Bottom Sheet 在同 Key 更新时保留 Dialog 与嵌套 Surface，应用
Foundation 已解析的完整容器色、内容角色、逻辑 Shape、Scrim、Expansion Policy 与“精确颜色/
平台默认值”Navigation Bar 策略，而不接管 Session Ownership。每次变化的同 Key 更新都会重新应用
完整快照；`skipPartiallyExpanded` 从 `true` 改为 `false` 时会清除旧 Material `skipCollapsed`
策略，不保留陈旧行为。

逻辑 Shape 由中立 Android Renderer Bridge 转换，本 Adapter 不解释 Material 专用 Shape。
恢复 `PlatformDefault` 时，还会恢复精确颜色曾关闭的 Android Q+ Contrast Enforcement。

所有 Root、Window、Presenter、Callback 与清理工作都在 Android 主线程执行；Adapter 不得超过
Root View 的 Window 生命周期。

## 相关文档

- [中立 Android Overlay](../viewcompose-overlay-android/README.md)
- [Material 3 Android 聚合包](../viewcompose-material3-android/README.md)
- [Overlay 架构决策](../../architecture/decisions/0006-root-scoped-overlay-backend-selection.md)
- [Overlay 指南](../../guides/overlays.md)

生成式参考位于
[`viewcompose-overlay-material3-android` API 目录](https://docs.viewcompose.com/api/viewcompose-overlay-material3-android/current/)。

## 兼容性说明

初始 Alpha 通过 `ServiceLoader` 注册完整 Material Host，并同时实现通用 Android 传输。当前硬切
移除该注册，把通用传输迁入恢复后的 `viewcompose-overlay-android`。需要 Material 行为的自定义
Host 必须显式构造本 Adapter。
