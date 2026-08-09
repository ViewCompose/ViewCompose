---
translation_source: modules/viewcompose-overlay-oneui7-android/README.md
translation_source_hash: fc84cbb15e9839b9e6a7a2650faef653c9309119b8ba3de1dffdc8988928ddef
translation_status: current
---

# One UI 7 Android Overlay 适配器

`viewcompose-overlay-oneui7-android` 是 One UI Root 使用的显式、无 Material Android 呈现
Adapter。它把中立
[`viewcompose-overlay-android`](../viewcompose-overlay-android/README.md) 传输与
ViewCompose 自有的 One UI Snackbar、底部对话框 Presenter 组合起来；不会新增独立的
`setOneUi7UiContent` API，也不会改变 Android 根 Context 的构造方式。

## 产物与稳定性

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-overlay-oneui7-android:0.1.0-alpha01")
}
```

- 稳定性：**Alpha**。
- 平台：Android Library，`minSdk 24`、`compileSdk 36`、Java 11 Bytecode。
- API 依赖：UI Contract 与 UI Foundation，因为 `AndroidOverlayHost` 暴露共享 Overlay 契约和
  Attribution 模型。
- 实现依赖：Host Android、中立 Android Overlay 传输和 `viewcompose-oneui7`；禁止依赖 Google
  Material Components 与 AppCompat。

API 质量：`AndroidOverlayHost` 是 Q3 Root 集成 API。规范 KDoc 与可编译 Sample 定义 Window
Ownership、主线程约束、清理、Token 快照和 Attribution 契约。

## 显式 One UI Root 装配

One UI 不需要在原生 View 创建前解析特定主题的 Android `Context`，因此应用继续使用中立
`setUiContent`，只显式选择 Overlay Adapter：

```kotlin
private lateinit var overlayIntegrations: List<UiIntegrationAttribution>

val tokens = OneUi7ThemeDefaults.light()
setUiContent(
    overlayHostFactory = { root ->
        AndroidOverlayHost(root, tokens).also { host ->
            overlayIntegrations = host.integrationAttribution
        }
    },
) {
    OneUi7Theme(tokens, integrations = overlayIntegrations) {
        AppContent()
    }
}
```

把 Host 的 `integrationAttribution` 传给 `OneUi7Theme` 是有意的契约。未安装 Adapter 时，主题
会把 Overlay 能力报告为 `Unsupported`；安装后只升级当前 Root 实际拥有的能力。仅把产物放入
Classpath 不会改变行为。

## Presenter 行为

Adapter 保留中立 Android Dialog、PopupWindow、Toast、嵌套 Render Session 与 Session 清理，并
新增：

- 使用随高度变化的全圆角 Pill 外形、One UI Token 快照、24dp Window Margin、Action Target、
  无障碍感知 Timeout 和单次终止 Callback 的队列化原生 Snackbar；
- 使用 One UI Surface Geometry、Scrim、System Bar 处理、嵌套 ViewCompose 内容、外部/返回关闭
  策略与 Drag Handle 下滑关闭的底部 Dialog。

首个底部 Dialog Presenter 只有一个内在展开状态。它为了协议兼容接收
`skipPartiallyExpanded`，但不暴露中间半展开状态，因此该选项不会制造 Presenter 并未拥有的行为。

`integrationAttribution` 把 Dialog 与 Popup 报告为捕获的 One UI 内容，Snackbar 与 Modal Bottom
Sheet 报告为 `Equivalent`，Android Toast 是显式的 `Degraded` 平台 Fallback。所有 Root、Window、
Callback 与清理都限制在 Android 主线程，Host 不得超过已附着 Render Root 的生命周期。

## 相关文档

- [One UI 7 设计系统模块](../viewcompose-oneui7/README.md)
- [中立 Android Overlay](../viewcompose-overlay-android/README.md)
- [多设计系统架构](../../architecture/design-systems.md)
- [Overlay 指南](../../guides/overlays.md)

生成式参考位于
[`viewcompose-overlay-oneui7-android` API 目录](https://docs.viewcompose.com/api/viewcompose-overlay-oneui7-android/current/)。

## 兼容性说明

这是该 Adapter 的首个发布线。只使用中立 Dialog、Popup 与 Toast 的现有 One UI Root 仍然有效；
只有需要 One UI Snackbar 或底部 Dialog 呈现时，才添加本产物及显式 Host 装配。
