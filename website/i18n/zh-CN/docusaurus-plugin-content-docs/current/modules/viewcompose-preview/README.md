---
translation_source: modules/viewcompose-preview/README.md
translation_source_hash: 056798cde593de8ed5d226b102c59982b3b4fa79c1a1b895fa4685f1d4062b42
translation_status: current
---

# Preview Integration

`viewcompose-preview` 把 ViewCompose UI 代码接入开发期预览宿主。它提供静态 Layoutlib Runner 使用的
应用主题 Provider 契约、便捷的 Compose `AndroidView` 桥接，以及第一方预览目录和 Paparazzi 快照
测试设施。

## 产物与稳定性

```kotlin
dependencies {
    debugImplementation("com.viewcompose:viewcompose-preview:0.1.0-alpha01")
}
```

- 稳定性：**Alpha**。公开桥接与主题 Provider 契约在稳定版前可能随预览工具继续演进。
- 运行环境：Android API 24 及以上。
- 推荐作用域：debug、test 或专用 preview source set。应用 release 代码不需要 Compose 桥接和目录。
- 传递 API：preview-core 注解/协议、widget-core DSL 与主题类型，以及桥接需要的 Compose runtime、UI
  和 preview 注解。

## 选择预览路径

ViewCompose 提供两条互补路径：

1. 原生静态预览路径使用 `@ViewComposePreview`、Gradle 插件、隔离 Layoutlib Worker 和
   `PreviewThemeProvider`。它生成确定性的 PNG 与诊断产物，是生产主题一致性、源码跳转、布局诊断、
   全部预览和 CI 渲染的权威路径。
2. `ViewComposePreview` 与 `ViewComposePreviewWithRoot` 通过 `AndroidView` 把 ViewCompose 渲染会话
   嵌入 Compose Preview。它们适合沿用 Compose 预览界面，但使用 `UiThemeDefaults` 而不是应用主题
   Provider，也不会导出静态 Runner 的诊断产物。

两个同名 API 位于不同包：静态注解在 `com.viewcompose.preview.tooling`，Compose 桥接函数在
`com.viewcompose.preview`。

## 应用主题 Provider

实现 `PreviewThemeProvider`，并在被预览模块中使用 `@ViewComposePreviewThemeProvider` 标记唯一一个
实现。Provider 收到的 Context 已包含请求的 density、字体比例、视口、语言、方向和夜间模式限定符。
它返回一个 `PreviewThemeResolution`，其中包含：

- 用于创建原生根节点和 Android View 的带主题 Context；
- 安装在 ViewCompose DSL 树外层的匹配 `UiThemeTokens`。

把两者放在同一次解析中，可以防止原生 View 和 DSL 组件悄悄使用不同主题。Provider 应保持无状态、
保留传入配置、避免依赖机器特有的动态输入，并且不能持有 Context。Worker 可以实例化 Kotlin object
或 public 无参类。

## Compose Preview 桥接

`ViewComposePreview` 用于不依赖根节点的 DSL 内容；`ViewComposePreviewWithRoot` 为互操作锚点提供桥接
持有的 Android `ViewGroup`；`ViewComposePreviewHost` 是还可传入 Overlay 后端的底层形式。

桥接会记住一个 Android 根节点和渲染会话。仅内容发生 Compose 重组时复用会话并请求一次新的
ViewCompose 渲染；主题、调试配置、Overlay 后端或容器变化时重建会话；离开 Compose 组合时释放。
内容不能移除根节点，也不能在组合之外持有它。

`ViewComposePreviewOptions` 只选择亮/暗 `UiThemeDefaults` 和可选渲染诊断。它刻意保持精简；静态
预览配置矩阵由 preview-core 负责。

## 目录与快照覆盖

内部目录按组件、输入、容器、集合、导航、反馈、Modifier、动画、手势和图形组织代表性场景。
参数化 Compose Preview 和 Paparazzi 快照共享同一套 Spec，守卫测试保证 ID、分组、标题唯一，并和
声明的覆盖目标列表一致。

目录类型是内部测试设施，不是公开组件图库 API。新模块或视觉契约需要回归覆盖时应扩展目录，但应用
示例仍应放在 Demo 和用户文档中。

## 测试与扩展规则

- 生产主题和源码诊断验收优先使用静态 Runner。
- 除非应用运行时确实使用桥接，否则不要把 Compose 桥接依赖放进 release 配置。
- 在亮/暗、语言、RTL、density 和字体比例变体中测试主题 Provider。
- 不能持有 Provider Context 或 `ViewComposePreviewWithRoot` 提供的根节点。
- 每个目录 Spec 使用稳定且唯一的 ID；修改 ID 会重命名快照历史。
- 新视觉领域同时增加覆盖守卫条目和 Paparazzi 快照。
- Renderer 或 Provider 异常应作为预览失败暴露，不能用占位 UI 隐藏。

## 相关文档

- [Preview Core 模块](/modules/viewcompose-preview-core/)
- [Preview Runner 模块](/modules/viewcompose-preview-runner/)
- [Preview Gradle Plugin 模块](/modules/viewcompose-preview-gradle-plugin/)
- [源码文档与 API 注释规范](/project/api-documentation-quality/)

完整生成参考见
[`viewcompose-preview` API 树](https://docs.viewcompose.com/api/viewcompose-preview/current/)。

## 兼容性说明

`0.1.0-alpha01` 建立了原生/DSL 一致主题解析、可保留的 Compose 桥接会话、显式根节点访问重载，以及
共享目录/快照覆盖模型。静态预览协议兼容性仍由 preview-core 统一管理。
