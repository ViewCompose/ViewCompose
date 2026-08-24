---
translation_source: modules/viewcompose-preview/README.md
translation_source_hash: 078b02804179e2f187275df9fc0464c7cc515c50a2157190951f4752971994b9
translation_status: current
---

# Preview 集成模块

`viewcompose-preview` 把 ViewCompose UI 代码接入开发期预览宿主。它提供静态 Layoutlib Runner 使用的
应用主题 Provider 契约、便捷的 Compose `AndroidView` 桥接，以及第一方预览目录和 Paparazzi 快照
测试设施。

## 产物与稳定性

```kotlin
dependencies {
    debugImplementation("com.viewcompose:viewcompose-preview:0.1.0-alpha04")
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

## 真机关联诊断

这个可选制品负责 Android Studio 单一 `Inspect Device Diagnostics` 动作的应用进程侧实现。Alpha
版本会硬切删除原来的 Locate、Highlight、Clear 与 Timing 动作，不保留兼容入口。在可调试进程中，它会为 Host、Navigation
Destination 与 Pager Page Session 保留有界源码候选，并为全部受支持的 Logical Session Role
登记弱持有、仅按请求工作的 Mounted-node Inspector。Lazy Item、Overlay 与 Preview Session 因而
无需组合期 Source Stack Capture 也能被选中。协议 v7 携带与运行时诊断相同的进程内 Trace ID、可选 Parent ID 和类型化
角色，以及按请求读取的 Rendering Activity、最近已提交帧、最近已完成尝试和最近失败安全摘要。
摘要只读取 Session 已保留的状态，不包含原始异常、Message、Cause、Stack、应用 Key、Node Content
或 Native Object。它不会持续发布报告，也不会观察滚动、全局布局、绘制、触摸、Frame 或重组。

源码定位会发送一条受 `DUMP` 权限保护的源码请求。高亮会先选择可见 Session，再请求一份 Mounted
Tree 快照：最多访问 2,048 个节点、返回 512 个节点、深度不超过 64。每个保留节点都会获得新的不透明
Token。响应会排除应用 Key、View 文本、Semantics、State、Local 值和任意 `toString()` 输出。
Synthetic Renderer Host 会被明确报告，但不能作为应用内容选中。

选中节点后，Receiver 在主线程解析其当前弱引用 Android View，记录完整屏幕边界与全局可见裁剪边界，
并绘制一个进程内唯一、不可交互的 Overlay。部分裁剪会被明确报告；Missing、Stale、Recycled、
Hidden、Fully Clipped、Unsupported、Ended Session 与 Rejected 请求都会 Fail Closed。目标替换、
显式清除、View Detach、Session 释放或五秒超时都会移除 Overlay。它不会触发重组、应用 Callback、
Focus 或 Accessibility Focus 变化，不拦截输入，也不修改 LayoutParams。

每份响应都会回显 1--128 字符的 ASCII Nonce，按需序列化至最多 256 KiB，并原子写入应用私有缓存。
IDE 只接受 Operation、Nonce、前台包名与存活进程均匹配的响应。无效请求、服务缺失、Writer/Overlay
失败和 Session 释放都不能导致应用渲染失败。

Inspector 会为已选 Session 在开发者触发工作负载期间启动一次有限请求。协议
v7 携带实际执行的 Composition、Reconciliation 与 Direct-binding Aggregate，包括不透明且仅当前
Capture 有效的 Node Token、Inclusive/Self 或 Direct 语义、时钟读取数、空计时对开销、Drop、
Truncation、Unsupported Domain 与结束原因。同一进程只接受一个活动 Capture；它最多在八个已完成
Frame Attempt 或两秒后停止，每帧最多保留 64 个计时节点，总计 512 条 Aggregate、深度 32，并复用
已有的有界 Source Metadata，而不会在计时路径抓取 Stack Trace。

普通渲染不提供 Collector：显式请求前，制品不会执行逐节点时钟读取、分配计时记录、轮询或写报告。
Measure/Layout/Draw、GPU、RenderThread、SurfaceFlinger、解码、网络、数据库与外部 SDK 不属于首版
契约。`Diagnostics → Renderer` Demo Fixture 提供可见的八帧工作负载，使人工验收能同时确认 UI
进度与终态报告。

本制品应只放在 `debugImplementation`、测试或专用 Tooling 配置中。除可调试进程与显式 IDE 请求
外，制品存在是启用功能所需的第三道门。零运行时持续开销与性能契约见
[ADR-0009](../../architecture/decisions/0009-development-tooling-isolation.md)。

## 真机动画时间线检查器

同一个 Debug-scoped 制品也是 `AnimationTimelineTooling` 唯一的应用进程实现。Provider 可以在
首次请求前弱注册已提交 Transition Source，使已经组合的 Transition 仍可发现，但不会保留应用
Value、安装 Listener 或读取 Snapshot。Receiver 会拒绝不可调试进程。Android Studio 的
**Inspect Device Animation Timeline** 动作会先发送一次
Discovery Request，让开发者选择 Transition，再开启一次 500 ms Capture；最多保留 64 个不同
Sample、每个 Sample 32 个 Channel，响应最多 256 KiB。

报告包含有界 Transition Label、隐私安全的逻辑 State Summary、Segment Time、不一致的 Channel
Duration、Spec Family、安全数值与 Velocity、物理 Terminal Condition，以及 Interruption/
Retarget Sample。自定义应用 Value 显示为 Unsupported/Private；实现不会调用它们的 `toString`。
每个响应都必须匹配 Request Nonce、前台 Package、存活 Process、Request Mode 与所选 Identity。
Missing、Busy、Stale、Malformed、Oversized、Disposed 与 Writer Failure 都会 Fail Closed，且不
改变应用状态。

真机检查严格只读。Studio Dialog 会明确说明：控制只允许 Synthetic/Interactive Preview Content
持有 `SeekableTransitionState` 并调用公开 `seekTo` API。设备 Receiver 没有 Mutation Command，
也不能写入 Transition 私有字段。没有有效请求时，Transition Publication 只执行 Provider 的有界
Selected-identity Check，不生成 Sample 或报告。

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
ViewCompose 渲染；主题、调试配置、Overlay 后端、诊断配置或容器变化时重建会话；离开 Compose
组合时释放。内容不能移除根节点，也不能在组合之外持有它。

桥接会从创建原生 View 的同一个 Container Context 安装 `AndroidResourceEnvironment`。Android 资源
查询函数因此会解析当前 Compose Preview 配置，Configuration Callback 也会推进普通 Android Host
使用的同一资源版本，而不是进入 Preview 专用解析器。

`ViewComposePreviewOptions` 只选择亮/暗 `UiThemeDefaults` 和可选的关联式
`RenderDiagnostics` 根。交互式与静态 Preview Session 都使用 `Preview` 角色。该选项刻意保持
精简；静态预览配置矩阵由 preview-core 负责。

## 目录与快照覆盖

内部目录按组件、输入、容器、集合、导航、反馈、Modifier、动画、手势和图形组织代表性场景。
参数化 Compose Preview 和 Paparazzi 快照共享同一套 Spec，守卫测试保证 ID、分组、标题唯一，并和
声明的覆盖目标列表一致。

`animation-layout-bounds` 目录项固定了位置、尺寸和组合运动的稳定起点矩形。经过人工复核的
浅色主题 Golden 保护初始几何与样式；交互式 Spec 随后可以切换真实 Bounds 端点，而无需引入独立的
Preview-only Renderer 路径。

`navigation-shared-content-endpoints` 目录项使用生产 Modifier Transport 渲染紧凑/展开 Bounds
端点和 Source/Target Element Marker。静态 Golden 检查端点几何与样式；真实跨 Session Progress、
取消和清理由 Demo/设备 Navigation Fixture 拥有，不创建 Preview-only Coordinator。

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
- 设备 Inspector 变更必须证明空闲滚动期间写入次数为零、每个有效请求只产生一个响应、陈旧 Nonce
  会被拒绝、关联摘要满足隐私边界，且 Release Classpath 不包含 Inspector。

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
真机源码定位、关联摘要、节点高亮与耗时都按请求运行，并完全归属于这个可选制品；Android Host 只保留
中立、可空的 Session Inspection 端口。协议 v7 硬切旧版报告，在已有 Operation 校验、Request-scoped
不透明 Node Token、有界节点快照、结构化高亮状态、裁剪边界、有限 Timing 与显式清除基础上，加入安全
的最近帧/失败摘要和统一 Inspector 契约。
