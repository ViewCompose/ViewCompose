---
translation_source: modules/viewcompose-preview/README.md
translation_source_hash: 85683305ce1290bcdc00d78d29a952381bb3b5eafbc1dbd00c88472713e277ce
translation_status: current
---

# Preview 集成模块

`viewcompose-preview` 把 ViewCompose UI 代码接入开发期预览宿主。它提供静态 Layoutlib Runner 使用的
应用主题 Provider 契约、便捷的 Compose `AndroidView` 桥接，以及第一方预览目录和 Paparazzi 快照
测试设施。

## 产物与稳定性

```kotlin
dependencies {
    debugImplementation("com.viewcompose:viewcompose-preview:0.1.0-alpha03")
}
```

- 稳定性：**Alpha**。公开桥接与主题 Provider 契约在稳定版前可能随预览工具继续演进。
- 运行环境：Android API 24 及以上。
- 推荐作用域：debug、test 或专用 preview source set。应用 release 代码不需要 Compose 桥接和目录。
- 传递 API：preview-core 注解/协议、UI Foundation DSL 与主题类型，以及桥接需要的 Compose runtime、UI
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

## 真机 DSL 定位

这个可选制品还负责 Android Studio `Locate Device DSL` 动作的应用进程侧实现。在可调试进程中，
它提供中立的 Host 源码检查服务，并为符合条件的 Host/Page Session 保留有界源码候选。它不会观察
滚动、全局布局、绘制、触摸、Frame 或重组，也不会持续发布报告。

开发者点击该动作时，Android Studio 会发出一条受 `DUMP` 权限保护且带 32 字符 Nonce 的请求。
Receiver 在主线程对当前弱引用持有的 Session View 采样一次，随后按需在后台序列化，并将一份有界
响应原子写入应用私有缓存。IDE 只接受 Nonce、前台包名与存活进程均匹配的响应。报告仅包含 JVM
源码标识与 View 候选资格，不包含源码文本、VNode Tree、应用状态或用户数据。无效请求、服务缺失、
写入失败和 Session 释放都不能导致应用渲染失败。

本制品应只放在 `debugImplementation`、测试或专用 Tooling 配置中。除可调试进程与显式 IDE 请求
外，制品存在是启用功能所需的第三道门。零运行时持续开销与性能契约见
[ADR-0009](../../architecture/decisions/0009-development-tooling-isolation.md)。

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

桥接会从创建原生 View 的同一个 Container Context 安装 `AndroidResourceEnvironment`。Android 资源
查询函数因此会解析当前 Compose Preview 配置，Configuration Callback 也会推进普通 Android Host
使用的同一资源版本，而不是进入 Preview 专用解析器。

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
- 合并前运行 `qaPreview`。只有审阅渲染图片及其差异报告后才能录制变更基准；原因不明的差异属于
  回归，不能当作基准更新。
- Renderer 或 Provider 异常应作为预览失败暴露，不能用占位 UI 隐藏。
- 设备定位器变更必须证明空闲滚动期间写入次数为零、每个有效请求只产生一个响应、陈旧 Nonce 会被
  拒绝，且 Release Classpath 不包含定位器。

## 相关文档

- [Preview Core 模块](/modules/viewcompose-preview-core/)
- [Preview Runner 模块](/modules/viewcompose-preview-runner/)
- [Preview Gradle Plugin 模块](/modules/viewcompose-preview-gradle-plugin/)
- [源码文档与 API 注释规范](/project/api-documentation-quality/)

完整生成参考见
[`viewcompose-preview` API 树](https://docs.viewcompose.com/api/viewcompose-preview/current/)。

## 兼容性说明

`0.1.0-alpha03` 建立了原生/DSL 一致主题解析、可保留的 Compose 桥接会话、显式根节点访问重载，以及
共享目录/快照覆盖模型。静态预览协议兼容性仍由 preview-core 统一管理。
真机 DSL 定位器现在改为按请求运行，并完全归属于这个可选制品；Android Host 只保留中立的可空
检查端口。
