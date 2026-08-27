---
translation_source: architecture/decisions/0003-public-package-ownership-and-platform-handles.md
translation_source_hash: 0d918f2808b009db69d59bc766a9bfc6cec3485cf68e4f02c0f08ad85d4fb227
translation_status: current
---

# ADR-0003：公开包所有权与平台 Handle

## 状态与日期

已接受并实现——2026-08-06。Overlay 包决策已于 2026-08-09 被 ADR-0006 修订。

## 背景

五层架构硬切已经重命名职责不准确的 Maven 产物，但实现审计发现部分 Kotlin 根包仍表达旧模块
拓扑：`com.viewcompose.widget.core`、`com.viewcompose.widget.constraintlayout` 和泛化的
`com.viewcompose.overlay.android`。新的应用聚合包还向 `com.viewcompose.host.android` 提供 API，
导致两个独立发布产物共同拥有一个公开包。

当时通用 Overlay 坐标已经退役，全部 Android Overlay 实现暂时归属 Material。ADR-0006 后续重新
启用了该坐标与 `com.viewcompose.overlay.android`，由其独占不依赖 Material 的 Android 传输。
这项修订保持单一 Owner 规则不变，同时纠正 Transport 与 Presenter 的职责拆分。

UI Foundation 还通过 Android `ViewGroup`、`Log`、`Trace` 和具体焦点管理器协调组合。虽然这没有
产生向上的 Gradle 依赖，却让 Android 执行所有权变得不可见，也违背 UI Foundation/Android
Engine 边界。ViewCompose 仍以 Android View 为目标；本决策讨论明确所有权，不宣称跨平台。

## 决策

1. 每个发布产物拥有一个规范公开根包。不同产物可以使用嵌套领域根包，但不得拥有相同根包，也
   不得向对方根包贡献源码。
2. UI Foundation 独占 `com.viewcompose.ui.foundation`，不为已退役的 `widget.core` 提供源码或
   兼容 facade。
3. `viewcompose-android` 独占 `com.viewcompose.android`；`viewcompose-host-android` 独占
   `com.viewcompose.host.android`。标准 `setUiContent` 和底层 `renderInto` 因而具有清晰不同的
   import 边界。
4. ConstraintLayout 独占 `com.viewcompose.constraintlayout`。Maven 保留 `-androidx` 后缀表达后端，
   源码包不再编码旧 Widget 分类。
5. Material-backed overlay 独占 `com.viewcompose.overlay.material3.android`，在 artifact 与 package
   身份中都明确设计系统。经 ADR-0006 修订后，重新启用的中立 Transport 独占
   `com.viewcompose.overlay.android`；两个根包表达不同职责，不共享源码。
6. Maven artifact 名表达能力及分发/后端；Kotlin package 表达稳定 API 领域。如果后端后缀不区分
   公开语义，就不机械复制到 package。
7. ViewCompose 以 Android 为目标，因此 UI Foundation 可以包含 Android-only 声明值；但 Session
   只能通过 `RenderContainerHandle`、Engine、焦点、调度、日志与 Trace 契约协调。Host Android
   安装并实现这些契约，并且只有它能把根 Handle 解包为 `ViewGroup`。
8. Android 环境提取与可选 Android overlay 服务发现属于 Host Android。UI Foundation 只消费
   已解析的 `UiEnvironmentValues`，并保留通用 overlay 协议与 no-op 实现。
9. Android namespace 必须等于规范根包。完成后的架构迁移不保留永久 namespace 例外。

## 已评估的替代方案

- **把每个 Maven 后缀复制到每个 Kotlin package。** 拒绝：这会给 lifecycle、viewmodel 等稳定
  能力包带来低收益源码破坏，却不会澄清公开语义。
- **保留旧包保证源码兼容。** 拒绝：替代产物尚未首次发布，且已授权的硬切明确排除转发 facade。
- **把完整组合协调器迁移到 Host Android。** 拒绝：Lazy Child 与 overlay Session 仍需要同一套
  组合机制，这会引入禁止的 UI Foundation 到 Host 依赖。不透明平台 Handle 可以保持依赖反转，
  同时让 Host Android 独占原生执行。
- **禁止 UI Foundation 中所有 `android.*` 类型。** 拒绝：框架没有跨平台目标，部分声明值有意
  采用 Android 类型。门禁只针对执行和适配类型。

## 影响

- 包迁移会破坏源码兼容，但发生在替代产物首次 Maven 发布之前。
- Import 现在可以明确区分应用聚合入口、底层 Host、通用 UI、AndroidX ConstraintLayout 集成、
  中立 Android Overlay Transport 和 Material 3 Overlay Presenter Adapter。
- 自定义平台安装器除 Render Engine、协程上下文和调度 Runtime 外，还必须提供焦点与诊断适配。
- UI Foundation 生产源码不再导入 Android Context、View/ViewGroup、Log、Trace 或 LocaleList；
  仍允许窄范围 Android-only 声明类型。
- Service descriptor 使用 Host Android Provider 契约，可选平台发现不再扩大 UI Foundation 职责。

## 受影响模块与公开契约

- `viewcompose-ui-foundation`
- `viewcompose-host-android`
- `viewcompose-android`
- `viewcompose-constraintlayout-androidx`
- `viewcompose-overlay-android`
- `viewcompose-overlay-material3-android`
- 导入这些公开根包的下游集成、Sample、Preview Host 与编译 API 文档

## 验证与发布

- `verifyModulePackageRoots` 阻断已退役的 Widget 根包、前缀边界错误、重复 Owner、被其他最长已登记包前缀
  认领的声明，以及遗留 Service descriptor。
- `verifyAndroidModuleNamespaces` 要求 namespace 与根包完全一致，不再使用 override map。
- `verifyUiFoundationPlatformBoundary` 阻断 UI Foundation 中的 Android 执行与适配 import。
- 完整 quick、release、publication 与 documentation 门禁前，先运行 Session、Host、Overlay、
  Navigation、Aggregate 与 ConstraintLayout 聚焦测试。
- 任何受影响替代产物进入 Maven staging 前，正常发布流程必须先归档已完成的硬切计划。

## 与此前决策的关系

本记录细化但不替代 [ADR-0002](./0002-five-layer-runtime-module-architecture.md)。ADR-0002 定义
五层运行时职责；本记录定义公开包与平台执行契约如何让这些职责可观察、可执行门禁。
[ADR-0006](./0006-root-scoped-overlay-backend-selection.md) 只替代本记录中关于退役中立 Overlay 包的
决定；公开包独占所有权规则保持不变。
