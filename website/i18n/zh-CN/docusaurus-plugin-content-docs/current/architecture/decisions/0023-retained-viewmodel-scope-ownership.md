---
translation_source: architecture/decisions/0023-retained-viewmodel-scope-ownership.md
translation_source_hash: f28dbc6937a7777c95db19eb34ef1a1d6d8d4708359117fbe1209ee26b466b9a
translation_status: current
---

# ADR-0023：保留式 ViewModel 作用域所有权

- 状态：已接受
- 日期：2026-08-29

## 背景

本决策落地前，ViewCompose 能够解析 Activity、Fragment、导航条目与导航图作用域的 ViewModel，
但没有面向 Pager 页面、Tab、Lazy Item、Overlay 或应用容器的通用子作用域能力。导航通过专用
`NavEntryOwnerStore` 补偿这一缺口；与此同时，`viewModel()` 还会在 Composition 中 Remember
已解析实例，尽管 `ViewModelStore` 已经拥有这份身份。独立 `savedStateHandle()` Helper 又引入
第二套 Holder 模型，没有复用 ViewModel 构造与 `CreationExtras`。

AndroidX Lifecycle 2.11 新增
[`ViewModelStoreProvider`](https://developer.android.com/reference/kotlin/androidx/lifecycle/viewmodel/ViewModelStoreProvider)：
子 Store 通过父 Store 跨配置变更保留，Reference Token 可在退出动画或其他临时消费者存在时延迟
最终清理。但这项底层契约不知道 ViewCompose 候选 Composition 是提交还是回滚，不知道 Render
缺席是临时还是永久，也不知道哪个导航事件意味着终止。因此，直接复制其 Compose Adapter 仍不足以
覆盖 ViewCompose 的 Prepared Composition、Delayed Session 与 Retained Stack 模型。

## 决策

### 一个 Store 与一个作用域 Provider Core

1. 把可执行 AndroidX Lifecycle 基线升级到 2.11。`ViewModelStoreProvider` 是唯一的子 Store
   分配与引用计数原语；ViewCompose 不实现平行的子 Store Map。
2. `ViewModelStore` 是 ViewModel 实例的唯一缓存。每次 Composition 调用实际执行时，
   `viewModel()` 都会查询 Provider，绝不 Remember 已解析 ViewModel 实例。
3. 新公共 API 家族的稳定 Capability 身份为 `viewmodel.scoped-owners`。首批声明落地时才加入其
   Capability Record，因为 Governance Record 描述当前已编译清单，不能预创建。
4. 模块自有的 `ViewModelScopeProvider` 在 AndroidX Provider 外补充 ViewCompose Commit、
   Rollback、禁止复活与终止释放状态。`ViewModelStoreOwnerLease` 是导航和自定义 Retained
   Container 共同使用的引用持有句柄。关闭 Lease 只结束一次使用，不代表逻辑作用域永久移除。

Wrapper 会先对 Provider 与 Child Identity 分别建立 Namespace，再交给 AndroidX。私有的 Provider
与 Child Metadata ViewModel 位于 AndroidX 管理的 Marker Store 和 Child Store 中，因此即使 Facade
在配置重建后重新创建，Commit、Terminal 与禁止复活状态仍能延续，同时不会引入第二套 Child Store
Map。Metadata 只弱引用活跃的 Lifecycle 与 Saved State Owner，并在最后一个 Lease 关闭时释放这些
引用。AndroidX 始终是 Child Store 唯一的分配器与引用计数器。

5. `rememberViewModelScopeProvider` 是 Composition Adapter：把 Provider 生命周期绑定到保留式
   父 `ViewModelStoreOwner`、父 `LifecycleOwner` 与调用方提供的稳定 Provider Key。
   `rememberViewModelStoreOwner` 是 Child Adapter。现有 `ProvideViewModelStoreOwner` 仍是唯一的
   Local 发布 API。
6. Core 统一，场景 Adapter 分开：普通 DSL Content Remember Owner 并发布；导航取得 Lease，
   驱动条目/图生命周期与最终 Clear；自定义 Retained Container 直接获取和关闭 Lease。Pager、
   Tab、Overlay 与 Lazy Content 不再各自增加平行 Provider API。

### 身份、引用与移除协议

1. Provider Key 与 Child Key 都是调用方或容器持有的非空稳定值。调用位置、集合位置、自增计数器
   和引用不稳定对象都不是 Durable Identity。ViewCompose 刻意不公开按位置自动生成身份的保留式
   Provider Overload。
2. 同一个父 Store 内相等的 Provider Key 共享 Provider State；相等 Child Key 仅在该 Provider
   内共享一个 Owner；不同 Provider Key 下的相等 Child Key 彼此隔离。
3. 准备 Composition Binding 时，在应用代码使用 Owner 前取得 Reference；Commit 后 Binding
   才持久有效。Abort 会释放候选 Reference，并清理仅由失败候选创建的 Scope；不得清理已提交
   Scope，也不得消费其 Restored State。
4. 临时不渲染只关闭 Reference，不请求移除。逻辑 Destination、Item、Page、Tab 或 Overlay 永久
   移除时，所属容器只调用一次 `clear(key)`。活跃 Lease 会延迟底层 Clear；请求移除后，在最后一份
  旧 Lease 关闭前，为同一身份申请新 Lease 会失败。之后复用同一 Key 会创建新 Scope，而不是复活
   已移除 Store。
5. 父 Lifecycle 尚未处于 `DESTROYED` 时，Provider Subtree 正常移除会请求 Provider 全量终止
   清理，包括父 Lifecycle 到达 `CREATED` 前的移除。父 Lifecycle 已为 `DESTROYED` 时不请求：
   配置重建必须恢复共享 Provider State，而正常 Finish 由父 Store 自身完成清理。清空父 Store
   始终是最终安全边界。
6. Provider 创建、Lease 操作、ViewModel 查询与 Clear 都限制在 Android 主线程。它们仅执行有界
   内存 Map、Provider 与引用计数操作，不执行 I/O、阻塞、调度或全局发现。

### Factory、Extras、Saved State 与 Lifecycle

1. Child Owner 默认继承父 Factory 与初始 `CreationExtras`；Provider 首次创建时可以显式覆盖。
   Scoped Default Arguments 按 AndroidX 规则优先于 Extras 中已有的默认参数。后续 Recomposition
   不会修改既有 Provider 的创建策略。
2. 仅当 Child Owner 委托给有效 `SavedStateRegistryOwner` 时才启用 Saved State。标准 Activity、
   Fragment、Navigation 与 Preview 的组合 Owner 可直接解析；Owner 分离的自定义边界必须显式
   传入 Saved State Owner。
3. Provider 必须有父 `LifecycleOwner`，通常与父 Store Owner 是同一对象。自定义分离边界需显式
   传入。Owner 缺失、已经无效或不一致时直接失败，不回退到 Activity、静态 Registry 或 Root Store。
4. Navigation 继续拥有 Route Arguments、Entry/Graph `LifecycleRegistry` 转换、Saved State
   Registry Namespace、Transition Retention、Stack Retention 与最终 Pop；Destination/Graph Store
   改由 `ViewModelScopeProvider` 提供，并在等价测试通过后删除独立 Store 分配策略。

### ViewModel 创建与状态互操作

1. 只有 `null` 选择 AndroidX 按 Class 派生的 ViewModel Key。所有非空字符串（包括空字符串和仅
   空白字符串）都是显式 Key，保持原值传递。
2. 保留现有 Reified 与 `KClass` Factory/Extras Overload；新增两种 Initializer Overload：Reified
   形式与 `KClass` 形式，`CreationExtras.() -> VM` Initializer 接收已解析 Owner 的默认 Extras。
   所有 Overload 委托给同一个只查询 Store 的内部 Resolver。
3. 已无别名删除 `savedStateHandle()` 与 `SavedStateHandleHolderViewModel`。业务状态在 ViewModel
   Constructor 或 Initializer 内通过 `CreationExtras.createSavedStateHandle()` 获取 Handle。
4. 不增加 `SavedStateHandle` 到 ViewCompose Snapshot State 的 Adapter。UI 专属状态使用
   `rememberSaveable`；ViewModel 业务状态使用 `SavedStateHandle.getMutableStateFlow()`，并通过
   现有 State Collection 集成观察。这样保持一个可写 Owner 与一条恢复路径，不为 API 对称性制造
   两个 Source of Truth。
5. Released `viewmodel.saved-state` Capability Record 仅作为 Immutable Deletion Impact Record
   所需的 alpha01 历史身份保留。当前 Generated Reference Entry 从已编译声明派生，不暴露两个已
   删除 Symbol；该记录不是兼容 API，也不是另一条所有权路径。

## 冻结的公共接口

实现阶段可以增加 Kotlin/JVM 必需的 Overload Annotation，但不会改变以下消费端角色：

- `ViewModelScopeProvider.acquireOwner(key, savedStateRegistryOwner)` 返回
  `ViewModelStoreOwnerLease`；`clear(key)` 与 `clearAll()` 提供终止信号。
- `ViewModelStoreOwnerLease` 实现 `AutoCloseable`，并通过只读 `owner` 公开其
  `ViewModelStoreOwner`。
- `rememberViewModelScopeProvider(key, parentOwner, lifecycleOwner, defaultArgs,
  defaultCreationExtras, defaultFactory)` 返回一个 `ViewModelScopeProvider`。`parentOwner` 默认
  使用 `LocalViewModelStoreOwner.current`，`lifecycleOwner` 默认对父 Owner 做类型转换，Factory 与
  Extras 默认使用父契约。
- `rememberViewModelStoreOwner(key, provider, savedStateRegistryOwner)` 返回
  `ViewModelStoreOwner`。当当前 Local Owner 实现 `SavedStateRegistryOwner` 时，默认以其作为 Saved
  State Owner。

`acquireOwner` 面向 Navigation 与 Retained Container Engine。普通 DSL Content 使用两个
`remember` 函数与 `ProvideViewModelStoreOwner`，无需手动保存 Lease。`clear` 与 `clearAll` 是
终止信号，不是可见性回调。

## 考虑过的替代方案

### 只公开 AndroidX `ViewModelStoreProvider`

拒绝。Raw Provider 无法区分失败 ViewCompose Candidate 新建的 Owner 与已提交 Owner，无法在最终
移除后拒绝复活，也无法把 Provider 清理绑定到 ViewCompose 的父 Lifecycle 规则。它保留为内部
Storage Primitive，而不是完整公共契约。

### 为 Navigation、Pager、Lazy Item 与 Overlay 分别建立 Owner Store

拒绝。这些策略的差异仅在 Lifecycle Input 与 Terminal Event。分离 Store 会复制现有导航专用实现，
放大恢复缺陷，并使统一 Reference/Removal 测试矩阵无法保护所有容器。

### Content 一离开 Composition 就清理 Child

拒绝。Exit Animation、Retained Navigation Stack、Pager Offscreen Limit、Lazy Reuse 与 Delayed
Rendering 都让可见期短于逻辑所有权。Reference Release 与 Terminal Removal 必须是两个事件。

### 在进程全局 Registry 中保留 Provider

拒绝。它会超出 Activity 与 Fragment Owner 生命周期，无法建模 Process Restoration，泄漏应用
Key，并绕过 AndroidX 通过配置保留的父 Store。

### 保留 Blank Key 与独立 Handle 兼容

拒绝。制品仍处 Alpha，两个路径都会固化错误所有权。空或空白 Key 是有效的 AndroidX 显式身份；
公共 Handle-only Holder 重复 ViewModel Constructor/Factory 模型，并占用应用可见 Store Key。

## 后果

- ViewCompose 获得实质性的 Lifecycle 2.11 Scoped Owner 能力，无需依赖 Compose，也不复制其按位置
  派生 Persistent Identity 的做法。
- AndroidX State 外增加一个 Provider 与轻量 Lease 对象。之所以接受 Wrapper 复杂度，是因为它
  保护 Raw Adapter 无法表达的 Prepared Composition Rollback、Terminal Clear、Delayed Reference
  与 No-resurrection 行为。
- Navigation 已在 Entry、Graph、Multi-stack、Restoration、Transition 与 Cleanup 等价测试通过后
  一次性迁移。它仍负责 Identity/Lifecycle 协调，但不再分配独立 ViewModelStore。
- 使用 Blank Key 或 Standalone SavedStateHandle Helper 的应用会遇到带明确迁移说明的编译期或
  行为 Breaking Change；不提供 Deprecated 兼容窗口。

## 验证与落地

1. Phase 1 验证 Store-only Lookup、Null/Non-null Key、Factory/Extras 优先级、Initializer Failure、
   `onCleared` 与 Clear 后重新查询。
2. Phase 2 通过 20 项聚焦 Scoped Owner 契约验证 Provider 共享/隔离、Commit/Abort、多 Lease、
   临时缺席、Terminal Clear、禁止复活、配置重建、Provider Disposal、Saved State Default、
   Pager/Lazy/Overlay 重排与 Lifecycle 边界诊断；结合 Phase 1 的解析覆盖，所属模块全部 44 项
   测试通过。
3. Phase 3 通过 151/151 项 Navigation Android 测试和 21/21 项 Aggregate Host Case。Navigation
   现在从共享 Provider 租用 Entry/Graph Store，以保存的 Host Scope Identity 跨配置重建保留，
   并在永久移除时清理。Activity Host 发现已安装的 ViewTree Owner；显式 Fragment Owner 压过
   生命周期更短的 View Owner；嵌套显式 Provider 仍拥有最高优先级；`renderInto` 继续不安装
   Owner。相较 148 项导航基线，Ownership 与 Retention 结论为 **improved**。真机进程终止、内存
   与性能证据仍为 **inconclusive**。
4. Phase 4 的 Constructor/默认 Factory 与 Initializer 两项进程式恢复契约均通过，所属模块
   45/45 项测试全部通过。Holder API 与保留 Key 已不存在；`rememberSaveable` 只持有 UI 状态，
   每份可变 `SavedStateHandle` Flow 只由一个业务 ViewModel 持有。结论为 **improved**。JVM 恢复
   不能替代真机进程终止旅程，后者在 Phase 5 前仍为 **inconclusive**。
5. Phase 5 新增七项负向与删除 Guard 后，所属模块 52/52 项测试全部通过；受影响层干净运行通过
   276/276 项测试，仓库 `qaQuick qaPreview` 完成全部 2270 个任务。在 Android 9/API 28 的
   Xiaomi MI 6 上，两条 Debug 进程终止旅程均改变 PID，并精确保留归一化后的 Activity Root 与
   多 Stack 导航状态。结论为 **improved**。单台设备不能证明 Release 模式、内存、性能或平台
   矩阵行为；这些维度仍为 **inconclusive**。
6. 每个 Phase 同步落地 Q3 KDoc、Compiled Sample、Capability Impact Record、Module/Migration 文档、
   Immutable Release Intent，以及带解释的 Focused Test Evidence。
