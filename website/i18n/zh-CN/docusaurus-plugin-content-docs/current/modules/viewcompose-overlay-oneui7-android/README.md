---
translation_source: modules/viewcompose-overlay-oneui7-android/README.md
translation_source_hash: c450c4972f24c091ce4df38e511de67d3fadea730dccd1b62d5d3db492898572
translation_status: current
---

# One UI 7 Android Overlay 适配器

`viewcompose-overlay-oneui7-android` 是 One UI Root 使用的显式、无 Material Android 呈现
Adapter。它把中立
[`viewcompose-overlay-android`](../viewcompose-overlay-android/README.md) 传输与
ViewCompose 自有的 One UI Snackbar、底部对话框 Presenter 组合起来；不会新增独立的
`setOneUi7UiContent` API，也不会改变 Android 根 Context 的构造方式。

## 产物与稳定性

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="overlay-oneui7-dependency" sample_id="module.overlay-oneui7-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-oneui7:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-overlay-oneui7-android:0.1.0-alpha01")
}
```

下方显式装配会调用 `OneUi7Theme`、`OneUi7ThemeDefaults` 和 One UI 组件，因此应用源码需要直接
声明 `viewcompose-oneui7`。Overlay Adapter 仍把该模块作为 Runtime 实现依赖，Host 构造函数只暴露
UI Foundation 的共享 Token 类型；仅使用 Host 默认 Token 快照的代码不需要引用 One UI API。

- 稳定性：**Alpha**。
- 平台：Android Library，`minSdk 24`、`compileSdk 36`、Java 11 Bytecode。
- API 依赖：UI Contract 与 UI Foundation，因为 `AndroidOverlayHost` 暴露共享 Overlay 契约和
  Attribution 模型。
- 实现依赖：Host Android、中立 Android Overlay 传输、Android Renderer Shape Bridge 和
  `viewcompose-oneui7`；禁止依赖 Google Material Components 与 AppCompat。

API 质量：`AndroidOverlayHost` 是 Q3 Root 集成 API。规范 KDoc 与可编译 Sample 定义 Window
Ownership、主线程约束、清理、Token 快照和 Attribution 契约。

## 显式 One UI Root 装配

One UI 不需要在原生 View 创建前解析特定主题的 Android `Context`，因此应用继续使用中立
`setUiContent`，并传入显式 `overlayHostFactory`，用当前 `OneUi7ThemeDefaults` Token 快照构造
本模块的 `AndroidOverlayHost`。把 Host 的 `integrationAttribution` 传回 `OneUi7Theme`；未安装
Adapter 时，主题会把 Overlay 能力报告为 `Unsupported`，安装后只升级当前 Root 实际拥有的能力。
仅把产物放入 Classpath 不会改变行为。

## Presenter 行为

Adapter 保留中立 Android Dialog、PopupWindow、Toast、嵌套 Render Session 与 Session 清理，并
新增：

- 使用随高度变化的全圆角 Pill 外形、One UI Token 快照、24dp Window Margin、Action Target、
  无障碍感知 Timeout 和单次终止 Callback 的队列化原生 Snackbar；
- 使用 One UI Surface Geometry、Scrim、System Bar 处理、嵌套 ViewCompose 内容、外部/返回关闭
  策略与 Drag Handle 下滑关闭的底部 Dialog。

首个底部 Dialog Presenter 只有一个内在展开状态。它为了协议兼容接收
`skipPartiallyExpanded`，但不暴露中间半展开状态，因此该选项不会制造 Presenter 并未拥有的行为。

底部 Dialog 会在首次展示与每次变化的同 Key 更新时应用 Foundation 已解析的完整外观快照。容器
颜色与逻辑 Shape 会替换初始 One UI Fallback Chrome；Scrim 为零时清除过时的 Dim Flag；导航栏
策略会区分精确颜色与恢复已捕获平台默认值及 Android Q+ Contrast Enforcement。Adapter 的 Margin、
Drag Handle 与手势仍属于 One UI 自有呈现细节。

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
