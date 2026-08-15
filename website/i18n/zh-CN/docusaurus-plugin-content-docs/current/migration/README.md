---
title: 从 Jetpack Compose 迁移
slug: /migration
translation_source: migration/README.md
translation_source_hash: e864863defa2349628b62d3231d2c4b17142a8b858cb00f32959f7d8d4eb49f3
translation_status: current
---

# 从 Jetpack Compose 迁移

ViewCompose 受到 Compose 启发，但不是 Compose 兼容层。成功迁移的目标是保留所有权、
生命周期和可观察行为，而不是替换名称相似的函数。在把页面迁移到原生 Android View
渲染器之前，先用本节识别语义缺口。

最后验证日期：**2026-08-12**

复核责任人：**Kernel、UI Foundation、Android Engine、Android 聚合层与 navigation 模块族的维护者**

## 已验证的源状态与目标状态

迁移目标是下面这组独立版本化的 ViewCompose 模块：

| 模块族 | 产物 | 已验证版本 |
| --- | --- | --- |
| 状态与组合 | `viewcompose-runtime`、`viewcompose-ui-foundation` | runtime `0.1.0-alpha02`；UI Foundation `0.1.0-alpha01` |
| UI 与渲染 | `viewcompose-ui-contract`、`viewcompose-renderer-android`、`viewcompose-constraintlayout-androidx` | contract `0.1.0-alpha03`；renderer/ConstraintLayout `0.1.0-alpha01` |
| Android 所有权 | `viewcompose-android`、`viewcompose-material3-android`、`viewcompose-host-android`、`viewcompose-lifecycle-androidx`、`viewcompose-viewmodel-androidx` | 聚合层/集成层 `0.1.0-alpha01`；host `0.1.0-alpha03` |
| 导航 | `viewcompose-navigation-core`、`viewcompose-navigation-android` | core `0.1.0-alpha02`；Android `0.1.0-alpha01` |

不可变的发布源码 revision 记录在
[`gradle/viewcompose-publishing.properties`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/gradle/viewcompose-publishing.properties)。

上游语义基线如下：

| 依赖族 | 版本 |
| --- | --- |
| Compose Runtime、UI 和 Foundation | `1.11.4` |
| Activity | `1.13.0` |
| Lifecycle | `2.11.0` |
| SavedState | `1.5.0` |
| Navigation 2 | `2.9.8` |
| Navigation 3 | `1.1.4` |

仓库内可执行对比基线仍为 Compose `1.7.8`、Activity `1.12.4`、Lifecycle `2.8.7` 和
Kotlin `2.0.21`，声明位置是
[`gradle/libs.versions.toml`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/gradle/libs.versions.toml)。
较新的上游语义由 Android 官方文档和发布说明确定；ViewCompose 行为则由本地源码、测试和
可编译样例确定。通过基于 `1.7.8` 的本地对比，不能证明与 `1.11.4` 语义等价。

本文不声明性能等价。未来任何性能对比都必须说明设备、构建模式、工作负载、预热、采样和
统计方法。

## 选择迁移路径

| 源代码关注点 | 从这里开始 | 实现前必须确定 |
| --- | --- | --- |
| 状态、重组、key、Effect 或可保存状态 | [状态、重组与恢复](compose-state-recomposition-and-restoration.md) | 状态所有者、重启边界、身份、Effect 提交点和恢复生命周期 |
| 布局、Modifier、density、local、inset 或 Android View 输出 | [布局、Modifier 与环境](compose-layout-modifier-and-environment.md) | 测量引擎、Modifier 折叠、逻辑边、local 失效和 inset 所有者 |
| Activity、Fragment、现有 View 宿主、生命周期、ViewModel 或 Android 互操作 | [宿主、生命周期与 Android 互操作](compose-host-lifecycle-and-android-interop.md) | 根所有者、销毁边界、已安装 owner、可重放工作和释放清理 |
| Navigation 2 或 Navigation 3 | [导航](compose-navigation.md) | 源导航模型、路由身份、owner 作用域、隐藏 session 策略和 Back 集成 |
| 图片加载 | [图片加载](image-loading.md) | source 类型、loader 所有权、request 策略和回收 View 释放 |
| Lazy 集合与 Pager | [Lazy 集合 Revision 与复用](lazy-collection-revision-and-reuse.md) | 语义 Revision、Mounted Tree 复用、互操作 Reset/Release，以及 TabRow/Pager 硬切 |

一个边界跨越多个关注点时，需要阅读多份页面。例如，导航目的地中的
`rememberSaveable` 同时受状态/恢复契约和导航所有权契约约束。

## 统一能力矩阵

下面的矩阵用于做粗粒度迁移决策。具体契约和证据以链接页面为准。所有页面对状态术语使用
同一含义：

- **Supported（支持）**——存在迁移所需的行为，并有仓库证据支撑。
- **Partially supported（部分支持）**——主要用例存在，但重要 API 或语义边界更窄或不同。
- **Intentionally different（刻意不同）**——ViewCompose 有意采用另一种所有权或执行模型，
  代码必须重新设计。
- **Unsupported（不支持）**——当前版本没有对应的公开能力。

| 领域 | 能力 | 状态 | 迁移决策 | 详情 |
| --- | --- | --- | --- | --- |
| 状态 | 可变状态、变更策略和读取观察 | **Supported（支持）** | 保留状态所有权；不要依赖 Compose 的回调次数或线程。 | [状态](compose-state-recomposition-and-restoration.md#mutable-state-and-mutation-policies) |
| 状态 | 派生状态和快照事务 | **Partially supported（部分支持）** | 检查相等结果抑制、嵌套、冲突和线程规则。 | [状态](compose-state-recomposition-and-restoration.md#derived-state-and-invalidation-differences) |
| 状态 | 快照集合和 `snapshotFlow` | **Partially supported（部分支持）** | 已提供 `snapshotFlow`；快照集合仍需使用 `MutableState` 中的不可变值。 | [状态](compose-state-recomposition-and-restoration.md#snapshots-atomic-updates-and-conflicts) |
| 组合 | 编译器生成的重启、稳定性和 strong skipping | **Intentionally different（刻意不同）** | 选择显式 ViewCompose group，并把读取放到最小更新边界。 | [重组](compose-state-recomposition-and-restoration.md#recomposition-without-the-compose-compiler) |
| 组合 | 位置 remember 和 keyed identity | **Partially supported（部分支持）** | 保持调用顺序稳定；不要依赖普通 keyed 兄弟节点在重排时移动。 | [身份](compose-state-recomposition-and-restoration.md#remembered-identity-keys-and-reordering) |
| Effect | `SideEffect`、`DisposableEffect`、`LaunchedEffect` 和 `produceState` | **Supported（支持）** | 把外部工作移到已提交 Effect，并显式处理失败清理。 | [Effect](compose-state-recomposition-and-restoration.md#effects-and-committed-frame-boundaries) |
| 恢复 | `rememberSaveable`、Saver 和宿主恢复 | **Partially supported（部分支持）** | 优先使用自动 key、保持值精简，并为自定义宿主显式安装服务。 | [恢复](compose-state-recomposition-and-restoration.md#saveable-state-and-saver-migration) |
| 布局 | 内置容器、尺寸、fill 和 parent data | **Partially supported（部分支持）** | 按 Android View 测量和 LayoutParams 重新验证行为。 | [布局](compose-layout-modifier-and-environment.md#two-layout-engines-compose-constraints-and-android-views) |
| 布局 | 通用自定义测量 | **Unsupported（不支持）** | 使用内置容器、ConstraintLayout 或由生命周期所有的 Android `ViewGroup`。 | [自定义测量](compose-layout-modifier-and-environment.md#two-layout-engines-compose-constraints-and-android-views) |
| Modifier | padding、margin、顺序和渲染器折叠 | **Intentionally different（刻意不同）** | 规范化链，并应用各 Modifier 家族的既定解析规则。 | [Modifier 折叠](compose-layout-modifier-and-environment.md#modifier-ordering-folding-and-equality) |
| Modifier | 结构相等性和渲染器复用 | **Supported（支持）** | 使用具有语义的稳定 key；新的回调对象不一定是更新信号。 | [Modifier 相等性](compose-layout-modifier-and-environment.md#modifier-ordering-folding-and-equality) |
| Modifier | 应用自定义 `Modifier.Node` 生命周期 | **Unsupported（不支持）** | 使用受支持 Modifier、互操作或经过审查的 UI-contract 与 renderer 能力。 | [Modifier.Node](compose-layout-modifier-and-environment.md#why-modifiernode-does-not-migrate-directly) |
| 环境 | density 和 font scale | **Supported（支持）** | 保留逻辑 dp/sp 值，只在渲染器边界转换。 | [环境](compose-layout-modifier-and-environment.md#density-locales-and-layout-direction) |
| 环境 | locale、布局方向以及逻辑/物理边 | **Supported（支持）** | start/end 意图使用相对 API，明确 left/right 行为才使用物理 API，并测试 RTL 输出。 | [环境](compose-layout-modifier-and-environment.md#density-locales-and-layout-direction) |
| 环境 | 用 `UiLocal` 替代 `CompositionLocal` | **Intentionally different（刻意不同）** | 用可观察状态支撑变化的 local；只读取 local 不会让读取者失效。 | [UiLocal](compose-layout-modifier-and-environment.md#uilocal-versus-compositionlocal) |
| Insets | 系统栏、IME 和嵌套消费 | **Partially supported（部分支持）** | 每条边指定一个所有者，并验证 View/ViewCompose 混合处理。 | [Insets](compose-layout-modifier-and-environment.md#system-bar-and-ime-insets) |
| 互操作 | ViewCompose `AndroidView` 回调生命周期 | **Intentionally different（刻意不同）** | 分离可重放 update/reset、事务后 commit 和永久 release 清理。 | [Android View 互操作](compose-host-lifecycle-and-android-interop.md#android-view-interop-callback-mapping) |
| 宿主 | Activity 和 Fragment 根 | **Partially supported（部分支持）** | 考虑内部所有的 session，以及 Fragment owner 与销毁时机不一致。 | [标准宿主](compose-host-lifecycle-and-android-interop.md#choosing-a-host-entry-point) |
| 宿主 | 现有容器中的 `renderInto` | **Partially supported（部分支持）** | 安装所有必需 owner，并显式销毁返回的 session。 | [自定义宿主](compose-host-lifecycle-and-android-interop.md#rendering-into-an-existing-view-hierarchy) |
| 所有权 | 通用 UI 作用域 ViewModel 和继承的 `CreationExtras` | **Partially supported（部分支持）** | 验证目的地/图的 factory 输入；当前没有任意子树 provider。 | [Owner](compose-host-lifecycle-and-android-interop.md#lifecycle-viewmodel-and-saved-state-owners) |
| Session | 显式渲染、帧调度和终结性销毁 | **Intentionally different（刻意不同）** | 把 `RenderSession` 视为组合、原生树、overlay 和清理的所有者。 | [Session](compose-host-lifecycle-and-android-interop.md#session-frame-effect-and-disposal-semantics) |
| 互操作 | 直接 ViewBinding 和树内 Fragment API | **Unsupported（不支持）** | 把 Fragment 所有权留在渲染树外，并显式管理 XML inflate。 | [不支持的互操作](compose-host-lifecycle-and-android-interop.md#unsupported-direct-interop) |
| 导航 | controller、目的地和多栈所有权 | **Intentionally different（刻意不同）** | 迁移目标状态转换，不要迁移 Navigation 2 或 3 的 API 名称。 | [导航模型](compose-navigation.md#choosing-the-source-navigation-model) |
| 导航 | 图、类型化路由和栈操作 | **Partially supported（部分支持）** | 使用受支持的基础 `NavValue` 参数和单个事务命令。 | [路由与事务](compose-navigation.md#graphs-routes-and-arguments) |
| 导航 | entry/graph owner 和 Lifecycle 2.11 factory 继承 | **Supported（支持）** | 保留继承的父级 Factory/extra，并隔离重复 route 的栈 owner。 | [Entry 所有权](compose-navigation.md#entry-and-graph-ownership) |
| 导航 | 目的地生命周期和自适应 pane | **Intentionally different（刻意不同）** | 允许多个 resumed entry；不要从 `RESUMED` 推断唯一可见性。 | [生命周期](compose-navigation.md#lifecycle-and-adaptive-panes) |
| 导航 | 隐藏目的地保留组合 | **Partially supported（部分支持）** | 让后台工作感知生命周期；隐藏 session 会保留 Effect 和原生 View。 | [保留](compose-navigation.md#hidden-destination-retention) |
| 导航 | 深链 | **Partially supported（部分支持）** | 替换 action/MIME 规则；未声明 query 值可存在，但不能影响导航策略。 | [深链](compose-navigation.md#deep-links) |
| 导航 | 保存/恢复、系统 Back 和 Predictive Back | **Supported（支持）** | 恢复后重建存活对象，并在发布流程中保留设备验证。 | [恢复与 Back](compose-navigation.md#save-restore-and-process-death) |
| 导航 | 直接 NavigationEvent 集成 | **Unsupported（不支持）** | 把 dispatcher-owner、forward event、测试 fake 和 Preview 需求留在 ViewCompose 外。 | [NavigationEvent](compose-navigation.md#system-back-and-predictive-back) |

## 迁移顺序

1. 记录源 Compose、Activity、Lifecycle、SavedState 和 Navigation 版本。
2. 修改 UI 声明前，盘点状态、生命周期、ViewModel、导航和持久数据的所有者。
3. 标记编译器重启边界、布局测量假设、Modifier 顺序、逻辑边、local 和 inset 所有权。
4. 用上面的矩阵分类每项必需能力。实现前先停止并重新设计所有不支持的依赖。
5. 每次迁移一个可独立测试的页面或子树。不要在没有独立行为断言时，同时重写宿主、导航
   模型和持久化。
6. 编译目标代码，并按页面需求验证重组、配置重建、进程重建、RTL、inset、Android View
   回滚、Back 和生命周期行为。
7. 列出的任一上游或 ViewCompose 版本变化后，重新执行对比。

## 可执行迁移基准

文档片段不能成为第二份事实来源。请使用仓库中这些可编译样例：

- `:samples:compose-migration` 模块包含四篇详细迁移文档嵌入的状态、布局/环境、宿主/Android
  互操作和 Navigation 2 成对片段；
- [计数器应用](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/samples/counter/src/main/java/com/viewcompose/samples/counter/MainActivity.kt)
  组合了 Activity 宿主、remember 可变状态、View 布局、Modifier 和输入；
- [runtime 样例](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-runtime/src/test/samples/com/viewcompose/runtime/samples/RuntimeSamples.kt)
  覆盖可变与派生状态、快照事务、策略、观察和组合；
- [UI Foundation 样例](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/test/samples/com/viewcompose/ui/foundation/samples/WidgetCoreSamples.kt)
  覆盖可保存状态 registry 和主题所有权；
- [Android 宿主样例](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-android/src/test/samples/com/viewcompose/android/samples/AndroidEntrySamples.kt)
  覆盖 Activity、Fragment、自定义容器和 Android View 宿主；
- [navigation-core 样例](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-navigation-core/src/test/samples/com/viewcompose/navigation/core/samples/NavigationCoreSamples.kt)
  覆盖图、深链、事务和生命周期规划；
- [Android 导航样例](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/test/samples/com/viewcompose/navigation/samples/NavigationAndroidSamples.kt)
  覆盖 remember 宿主、controller 操作和 motion 配置。

根 `qaQuick` 任务会编译这些样例源码集或使用它们的测试。它还会运行
`verifyMigrationPairedSamples`，拒绝中英文页面中缺失、额外、乱序或过期的成对片段。仅设备
可验证的恢复和 Predictive Back 证据仍由[英文导航指南](https://docs.viewcompose.com/guides/navigation)
链接的流程治理。

## 已知契约缺口

在源码文档、实现和可执行证据就以下问题达成一致前，不要提高能力状态：

- 导航指南与深链测试对额外未注册 query key 的行为存在冲突。
- 相等结果与嵌套派生状态以及只读快照嵌套需要专项回归覆盖。
- 重复 size/padding、嵌套 inset 消费和 native-view 回调身份需要更广的可执行覆盖。
- Lifecycle `2.11.0` 任意 UI 作用域和完整 parent factory/`CreationExtras` 继承没有
  ViewCompose 等价证据。
- 通用的非导航进程死亡认证和最新 Predictive Back 设备运行仍比完整语义基线更窄。

复核必须先检查上游官方文档，再检查不可变 ViewCompose 源码契约、测试、可编译样例和适用的
设备流程。签名匹配或 API 名称相似永远不足以证明语义等价。
