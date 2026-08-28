---
schema_version: 2
document_id: architecture.scene-derived-navigation-lifecycle-presentation-ownership
doc_type: architecture
slug: /architecture/decisions/scene-derived-navigation-lifecycle-and-presentation-ownership
owner:
  kind: capability
  id: navigation.host
version_lane: version-agnostic
capability_ids:
  - lifecycle.effects
  - lifecycle.flow-collection
  - lifecycle.owner-boundaries
  - navigation.host
  - navigation.scene-projection
  - viewmodel.scoped-owners
artifact_ids:
  - viewcompose-lifecycle-androidx
  - viewcompose-navigation-core
  - viewcompose-navigation-android
  - viewcompose-viewmodel-androidx
sample_ids: []
invariants:
  - 一套与宿主类型无关的 Lifecycle DSL 使用接口解析最近的 Activity、Fragment、Destination、Graph、Preview 或自定义容器 Owner。
  - Destination 有效生命周期取 Host、Scene 与 Entry 三重上限的最小值，导航 Presentation 则是独立的可观察契约。
  - 逻辑 Entry 所有权可跨原生 Presentation 销毁继续存在；永久移除时必须先销毁 Presentation，再销毁 Entry Owner。
  - 一份 Reducer 输出的计划统一拥有 Stack、Scene、Lifecycle、Presentation、Focus、Transition、Rollback 与终止清理决策。
evidence:
  - docs/project/plans/navigation-lifecycle-and-scene-evolution.md
  - docs/architecture/decisions/0023-retained-viewmodel-scope-ownership.md
  - viewcompose-navigation-core/src/test/kotlin/com/viewcompose/navigation/core/NavLifecyclePlannerTest.kt
  - viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/TransactionalNavHostCoordinatorTest.kt
  - viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavEntryOwnerStoreTest.kt
translation_source: architecture/decisions/0024-scene-derived-navigation-lifecycle-and-presentation-ownership.md
translation_source_hash: f621dacba0f433dfd1226e90b966cd374ef937b47f2877d15ed7d4b00d04fba2
translation_status: current
---

# ADR-0024：由 Scene 推导导航生命周期与 Presentation 所有权

- 状态：已接受
- 日期：2026-08-29

## 背景

ViewCompose 页面是在同一个 Activity 下承载的原生 `ViewGroup` Presentation，而不是每个
Destination 对应一个 Activity 或 Fragment。因此 Android 本身无法为页面提供完整生命周期。
Destination 可能在隐藏时仍 Attached，可能在 Transition 期间不拥有输入，可能在原生 View 被销毁后
仍作为逻辑 Entry 保留，也可能与其他可交互 Pane 同处一个已稳定 Scene。

现有导航运行时已经拥有事务式 Stack、Destination/Graph Owner、Saved State、ViewModel Scope
Lease、Predictive Back、Adaptive Pane 与 Rollback。但它的生命周期投影仍然过窄：Visible 与
Interactive ID Set 无法表达 Transition、Overlay、Focus、Layer 或 Presentation Retention 语义。
普通 Push/Pop 可能在 Motion 稳定前提升进入页面；已 Pop 的退出页面可能仍停留在 `STARTED`；
隐藏 Stack 会无界保留完整原生 Presentation。

AndroidX Lifecycle 仍是正确的资源阈值协议，但它不能表达页面正在 Covered、Entering、Exiting，
或参与 Predictive Preview。分别创建 Activity、Fragment、Navigation 专用 Lifecycle DSL 会复制同一
使用契约，并让嵌套宿主产生歧义。把 ViewModel 或 Saved State 生命周期绑定到原生 View Retention，
也会破坏 [ADR-0023](./0023-retained-viewmodel-scope-ownership.md) 已建立的共享 Retained Owner 边界。

这些决策跨越多个发布 Artifact、建立未来公开契约且难以逆转，因此必须在修改生产代码前记录 ADR。

## 决策

### 统一的 Lifecycle 使用接口

所有 Owner 边界下的应用 DSL 内容都使用同一组 API：

- `LocalLifecycleOwner.current`：可选的最近 Owner 查询
- `Lifecycle.currentStateAsState()`：可观察的声明式状态
- `LifecycleStartEffect` 与 `LifecycleResumeEffect`：成对资源工作
- `collectAsStateWithLifecycle`：按阈值控制的 Flow 收集
- `ProvideLifecycleOwner`：显式自定义边界

Activity 与 Fragment Host 发布系统拥有的 Lifecycle；Navigation Destination 与 Graph 发布框架拥有
且受限的 Lifecycle；Preview 与自定义容器发布其显式 Owner。消费者 API 不按 Host 类型分支。
ViewCompose 不会新增 `ActivityLifecycleEffect`、`FragmentLifecycleEffect`、
`NavPageLifecycleEffect`、公开 `PageLifecycleOwner`，或 Navigation 私有的 Flow Collector。

核心实现保持统一，场景 Adapter 保持独立：Activity/Fragment 跟随平台 Callback，Navigation 根据
Scene 投影，自定义容器提供自己的 Owner。所有嵌套或延迟 Composition 都捕获并解析最近边界。

### Lifecycle 是三重上限投影

Entry 的有效 Android Lifecycle 只由一条纯规则推导：

```text
effective entry lifecycle = min(host cap, scene cap, entry cap)
```

在应用 Host Cap 之前，接受的矩阵如下：

| Destination 条件 | Scene Cap | Entry Cap | 有效目标 |
| --- | --- | --- | --- |
| Commit 前的 Prepared Candidate | `CREATED` | `CREATED` | `CREATED` |
| Retained Hidden Entry | `CREATED` | `RESUMED` | `CREATED` |
| Settled、Visible 且 Interactive 的 Entry | `RESUMED` | `RESUMED` | `RESUMED` |
| Forward 或 Back Transition 参与者 | `STARTED` | `RESUMED` | `STARTED` |
| 被 Overlay 覆盖的 Entry | `STARTED` | `RESUMED` | `STARTED` |
| Settled 的顶层 Overlay | `RESUMED` | `RESUMED` | `RESUMED` |
| Overlay 下方被覆盖的 Entry | `STARTED` | `RESUMED` | `STARTED` |
| 已 Pop 但仍在执行退出动画的 Entry | 最高 `STARTED` | `CREATED` | `CREATED` |
| 永久移除的 Entry | 不适用 | `DESTROYED` | `DESTROYED` |

Active Transition Scene 中不能出现 `RESUMED` Destination。已 Pop 的 Exiting Destination 已经不在
Retained Navigation State 中，因此不能高于 `CREATED`。只有 Settled Scene 的 Pane Policy 允许多个
Entry 同时交互时，它们才能同时处于 `RESUMED`。Graph Owner 从 Retained Descendant 推导 Cap，
同时保持 Child-down 与 Parent-up 顺序。已销毁 Identity 不得复活。

### Presentation 状态不是 Lifecycle 状态

Navigation 为每个 Entry 发布一份粗粒度且稳定的 Presentation Snapshot。冻结的语义字段包括：
Visibility（`Hidden`、`Visible`、`Covered`）、Interaction（`Interactive`、
`NonInteractive`）、Transition Phase（`Prepared`、`Entering`、`Settled`、`Exiting`、
`PredictivePreview`）、Pane Role，以及 Content/Overlay Layer Role。平台中立的 Value Family 由
Navigation Core 拥有，并直接用于 Scene Projection；Android 不再创建第二套 Enum Model。

Android 的公开边界只有一个 `LocalNavDestinationContext`。其 `NavDestinationContext` 持有稳定的
`NavEntry` Identity 与可观察的 `NavDestinationPresentation`。同一 Retained Entry 的 Holder 可跨
Presentation 销毁与重建继续存在。捕获的 Local 保存 Holder，而不是一次性的 Enum Snapshot。
由于嵌套 Host、Overlay 与 Pane 可以同时公开多个 Destination，因此不存在全局 Current Page 查询。

粗粒度 Presentation 变化可以使 Destination Content 失效。逐帧 Transition 或 Predictive Progress
被明确排除；需要连续 Motion 的内容使用独立 Opt-in Motion API，避免普通页面每帧重组。

### Entry 与 Presentation 生命周期相互独立

一个 Retained Entry Record 拥有 Route Identity、Destination 或 Graph Owner、Saved/Saveable
State、ViewModel Scope Lease、Destination Context Holder，以及可选的原生 Presentation。销毁
Presentation 会结束其 Child `RenderSession`、View Tree、Effect、Focus、Accessibility State 与原生
资源，但不会清理 Entry Owner、将其标记为 `DESTROYED`，或改变 Context Identity。

公开 `NavPresentationRetentionPolicy` Family 支持三种显式行为：

- Entry 完全隐藏时销毁 Presentation
- 对应用已经证明昂贵的 Surface 显式保留 Presentation
- 以正数上限保留有界 LRU 集合，并执行确定性 Eviction

安全默认值不由偏好决定。Phase 4 根据已接受的真机内存、重建耗时与 Settled Frame 证据选择。
默认策略不得无界。永久移除 Entry 时，先销毁 Presentation，再销毁 Owner 并终止清理 ViewModel。
配置或进程恢复不会重建任何 Live View、Effect、Animation 或 Candidate Transaction。

### 一个 Reducer 拥有导航决策

Core 将演进为一个纯 Reducer。它输出的不可变 Execution Plan 同时包含 Stack Mutation、Scene/Layer
Projection、Entry/Graph Lifecycle Target、Presentation Create/Refresh/Retain/Evict/Dispose、
Focus/Input、Back Ownership、Transition Effect、Rollback 与终止清理。Android Executor 负责
`LifecycleRegistry`、View、Focus、Back Dispatch 与 Animation，不独立重建策略。

Commit 前失败不会发布 Candidate Stack 或 Destination Context；Commit 后失败只走一条有文档的
终止恢复路径。旧的 Visible/Interactive-only Projection 与命令式并行决策会在替代 Slice 中删除，
新旧状态机绝不并行运行。

### Capability 与公开契约冻结

在声明变化前冻结以下稳定 Capability Identity：

| Capability ID | Artifact Owner | 公开职责 | 质量等级与契约字段 |
| --- | --- | --- | --- |
| `navigation.scene-projection` | `viewcompose-navigation-core` | 不可变 Scene、Entry Presentation Value、Lifecycle Cap 与 Reducer Projection | Q3；Behavior、Inputs、Outputs、State、Lifecycle、Failure、Performance、Compatibility |
| `navigation.presentation-retention` | `viewcompose-navigation-android` | `NavPresentationRetentionPolicy` 与 Host 策略选择 | Q3；Behavior、Inputs、Outputs、State、Lifecycle、Concurrency、Failure、Android、Performance、Compatibility |
| `navigation.destination-context` | `viewcompose-navigation-android` | `LocalNavDestinationContext`、稳定 Context Holder 与可观察粗粒度 Presentation | Q3；Behavior、Outputs、State、Lifecycle、Concurrency、Android、Performance、Compatibility |

Governance Capability Record 描述已编译 Inventory，因此会与第一批公开声明一起新增，而不会由本
ADR 预先创建。每个声明 Slice 都必须为每个 Changed Symbol 添加结构化 Impact Record、Canonical
English KDoc、Compiled Q3 Sample、Generated Reference Input、所属 Module/Architecture 文档、
Migration Disposition、Locale Mirror 与不可变 Changeset。

Lifecycle DSL 工作继续归属 `lifecycle.owner-boundaries`、`lifecycle.effects` 与
`lifecycle.flow-collection`，不增加 Navigation Alias。如果 Phase 1 无需修改公开声明即可关闭 Race
与 Host Matrix，就不人为增加 API。当前公开 Visible/Interactive Lifecycle Planner Surface 将在
`navigation.scene-projection` 下执行 Breaking Alpha Hard Cut，不保留 Deprecated Bridge 或双重投影。

## 已考虑的替代方案

### 每个页面使用一个 Activity

不作为框架导航模型。Activity 提供完整平台 Lifecycle，但无法在不回到系统组件边界的情况下表达
Host 内 Pane、Overlay、Retained Stack、Shared Element Ownership 与事务式子渲染。应用仍可在本
模块之外选择 Multi-Activity 架构。

### 使用 Fragment 作为页面抽象

不作为必需依赖。Fragment 提供成熟 Owner 集成，但会在现有 ViewCompose Transaction Engine 外再
增加 Manager、Transaction Model、View Lifecycle、Saved State Model 与 Restoration Protocol。
ViewCompose 直接实现等价 Owner 契约，同时继续支持 Fragment 作为外层 Host。

### 从 View Attached 与 Visibility 推导一切

不可行。Attached View 可能隐藏、被覆盖、被保留、在 Pop 后退出，或不拥有交互。View 状态无法
安全决定资源阈值与逻辑所有权。

### 新增 Navigation 专用 Lifecycle Callback

不可行。它会复制 AndroidX Lifecycle 使用接口、让嵌套产生歧义，并且仍需要另一份状态表达
Presentation 语义。

### 保留所有隐藏 View Tree

不作为默认值。深 Stack 与 Multi-stack 会把逻辑 Retention 变成无界原生内存 Retention，而
ViewModel 与 Saveable State Identity 并不需要 Live Presentation。

### 所有隐藏 View 立即销毁

不作为唯一策略。它能限制内存，但可能让昂贵 Surface 承受不可接受的重建与 Frame 成本。策略族与
测量后默认值保留选择能力，同时不把它耦合到 Entry Ownership。

## 后果

- ViewCompose 承担原本由 Activity/Fragment 提供的虚拟页面 Lifecycle 正确性；Lifecycle、
  Restoration、Leak 与 Device Matrix 是发布要求，不是可选测试。
- 应用无论处于哪种 Host 都只使用一套 Lifecycle DSL；只有需要 Navigation 语义时才读取独立
  Destination Presentation Context。
- 原生 Presentation 内存可以独立于 ViewModel、Saved State 与 Route Identity 受到限制，代价是
  事务式重建机制与显式策略证据。
- Navigation Core 获得更丰富的公开 State 与 Reducer 边界。接受 Alpha Planner Hard Cut，避免围绕
  已知错误 Lifecycle Ordering 永久保留兼容层。
- Coordinator 与 Transition Driver 将成为同一 Plan 的 Executor，而不再是竞争状态机。迁移按有限
  Phase 完成，但任何已合并 Phase 都不得保留两个 Authoritative Path。

## 受影响模块与契约

- `viewcompose-lifecycle-androidx` 拥有 Host-neutral 使用机制及 Race/Failure 测试，不依赖 Navigation。
- `viewcompose-navigation-core` 拥有 Scene 语义、Lifecycle Cap 与纯 Reducer Output，不包含 Android
  或 View 类型。
- `viewcompose-navigation-android` 拥有 Entry/Graph `LifecycleRegistry` 应用、Context 发布、
  Presentation Retention、View Hierarchy、Focus、Back 与 Animation Execution。
- `viewcompose-viewmodel-androidx` 继续作为唯一 Retained Child-store Provider；Navigation 在
  Presentation 销毁期间保持 Entry Lease。
- `viewcompose-android` 继续安装 Activity/Fragment Owner，不创建 Navigation Page Owner。

## 验证与落地

1. Phase 1 在 Activity、Fragment、Destination、Graph、Preview 与自定义边界验证现有 Lifecycle
   DSL，包括 Commit Race、Replacement、Failure、Rapid Event 与 Disposal。
2. Phase 2 引入 Core Scene/Cap Model 与 Exhaustive/Property Projection Test，并在同一 Breaking Slice
   删除 Visible/Interactive-only Planning。
3. Phase 3 在 Android 应用已接受的 Transition、Overlay、Pane、Host Cap、Graph Order、Focus 与
   Terminal Lifecycle Matrix。
4. Phase 4 分离 Presentation、实现三种策略，并只在解释 Device Memory、Restoration、Recreation、
   Leak 与 Frame 证据后选择默认值。
5. Phase 5 发布 Destination Context 与 Compiled Q3 Sample，并验证稳定 Holder Identity、Delayed
   Capture、Presentation Recreation、Nested Host、Pane、Overlay 与 Removal。
6. Phase 6 收敛 Reducer/Executor 并删除被替代的命令编排。
7. Phase 7 与 Phase 8 关闭 Typed Route/Ecosystem Disposition、Coverage、Device、Performance、
   Documentation、Release 与 Archive 门禁。

有效的
[Navigation Lifecycle 与 Scene 演进计划](../../../../../../../docs/project/plans/navigation-lifecycle-and-scene-evolution.md)
拥有阶段顺序与验收证据。当前 Architecture 与 Module Manual 只在相应实现行为落地时更新。
