---
translation_source: architecture/lifecycle-and-saved-state.md
translation_source_hash: e551c2242d25e21c7a9b814805e1e8edc99f5d41174f6bbcb62af6531bc7f084
translation_status: current
---

# Lifecycle 与 SavedState

## 1. 目标

本文定义宿主生命周期、生命周期感知 Flow、已提交 Android View 和 `rememberSaveable` 的提交/
恢复边界。

核心原则：

1. 未提交的组合不能永久消费恢复值。
2. 宿主保存发生在组合准备期间，也不能丢失已 claim 的值。
3. 独立子组合不能共享一个扁平 Provider Key 命名空间。
4. 生命周期快速切换时，同一个 Flow 最多只有一个活跃 collector。
5. 已销毁宿主不能创建新的渲染会话或 SavedState 绑定。
6. Renderer 拥有的 View 在事务提交前不能观察或发布外部 Owner。

## 2. 宿主生命周期

`ComponentActivity.setUiContent` 的会话绑定到 Activity 生命周期；
`Fragment.setUiContent` 的会话绑定到 Fragment View Lifecycle，并在 View 销毁时释放。Activity
内容的 Lifecycle 与 SavedState Owner 都是 Activity。Fragment 内容则有意使用 Fragment View
Owner 作为 Lifecycle Owner、Fragment 作为 SavedState Owner，使 View 工作在 `onDestroyView`
结束，同时让兼容的 SDK State 可以跨 View 重建保留。

Activity/Fragment 入口与自动 Owner 安装属于 `viewcompose-android`。`viewcompose-host-android`
负责底层 Session、Scheduler、`renderInto` 与 Android SavedState 桥接，不得重新承载
Activity/Fragment 便利 API。生命周期感知收集与 ViewModel 访问继续位于各自命名的 AndroidX
集成模块。

边界规则：

1. 重复调用 `setUiContent` 会先释放旧会话。
2. `ON_DESTROY` 释放会话、组合副作用、协程和平台资源。
3. 对 `DESTROYED` 宿主调用 `setUiContent` 立即失败，不创建半绑定会话。
4. `LifecycleBoundDisposer` 绑定到已经销毁的 owner 时立即执行释放。

## 3. 生命周期感知 Flow

`collectAsStateWithLifecycle` 只接受：

- `CREATED`
- `STARTED`
- `RESUMED`

`INITIALIZED` 与 `DESTROYED` 不能作为活跃阈值。

实现使用 `repeatOnLifecycle` 的串行取消/重启语义：前一个 collector 的取消和
`finally` 清理完成后，下一次 collector 才能进入，避免快速 `STOP -> START` 产生并发收集。
组合释放会取消整个结构化收集作用域。

## 4. 已提交 Android View 的 Owner 协同

可复用原生 View 集成使用类型安全 Android Host Adapter 管理事务所有权，并使用 AndroidX
Lifecycle 集成协调 Owner。可重放的 View Create 与 Update 在 Renderer Apply 期间运行；
Lifecycle 和 SavedState Binding 只从 Post-commit Hook 开始。因此被放弃或回滚的候选对象不能
观察 Owner、消费恢复的 SDK State，也不能发布 Provider。

一个 View 最多持有一个 Lifecycle Binding。首次 Attach 按 Android 事件顺序 Catch-up 到捕获
Owner 的当前 State。Owner 替换会先完成旧 View 侧的下降序列并移除 Observer，再执行新 Commit
工作与上升 Catch-up。Retained Navigation Destination 提供受限的 Destination Owner，而不是
Activity Owner，因此隐藏内容不会仅因 View 仍挂载就继续运行 Media Surface、Map 工作或 Camera
Capture。Reset、最终 Release、Owner Destroy 和 Callback Failure 都执行有界、一次性的清理。

Lifecycle Adapter 只协调 View 侧事件，不控制应用拥有的播放、权限、凭据、Lifecycle State 或
SDK 对象所有权。Host 会记录 Adapter Lifecycle Mode 用于诊断，但不会自行安装 Observer。

## 5. rememberSaveable 恢复事务

恢复分为四步：

1. 组合 prepare 时按稳定 key `claimRestored`。
2. claim 中的值参与恢复，但仍包含在 `performSave()` 快照中。
3. 组合 commit 后注册 provider，再提交 claim。
4. 组合 abort 或新值被 abandoned 时释放 claim，后续重试仍可恢复同一值。

因此，组合异常、renderer apply 回滚、保存与渲染交错都不会提前丢失恢复值。

`rememberSaveable(inputs...)` 的 input 变化仍表示有意重置：旧 holder 只在提交阶段
退出，新 holder 同步接管 provider，最终保存替换后的值。

## 6. 子组合所有权

Host Registry 是根持久化边界，不是所有嵌套 `RenderSession` 的全局 Key 命名空间。创建延迟子组合
的框架容器会在父组合中 Remember 一个 State Holder。Holder 按稳定 Lazy Item、Pager Page、Tab
或 Overlay Surface 身份分别提供子 Registry。

自动与显式 `rememberSaveable` Key 只在收到的子 Registry 内有效。嵌套容器会在该 Registry 内继续
创建 Holder，因此所有权跟随组合树：

```text
host owner
└── container holder
    ├── logical item A registry
    │   └── nested container holder
    └── logical item B registry
```

回收会关闭 Item Registry Lease，并在 Holder 中保留其 Saved Map；相同逻辑 Key 再次 Attach 时
恢复。Keyed Reorder 因而让逻辑状态跟随 Key，而不是跟随 View Holder 位置。Renderer 并发创建的
Presentation 副本可以恢复 Owner 当前 Snapshot，但不会成为第二个持久化 Owner。

Holder 本身通过父 Registry 的常规事务保存。失败父帧不能发布候选子所有权；失败子帧会保留此前
的 Provider 与恢复值 Claim。硬切原因与兼容边界参见
[ADR-0010](./decisions/0010-hierarchical-saveable-state-ownership.md)。

## 7. Android Bundle 边界

Android host 保存：

- `null`
- Bundle 可保存的平台值
- 递归 `List`
- String-keyed `Map`

恢复时会安装宿主 class loader。未知格式版本整体忽略；单个损坏 entry 被隔离，
不会阻止其余有效 entry 恢复。

瞬时系统会话不属于 SavedState：IME composition、撤销历史、进行中的手势和动画不会恢复。

拥有 Bundle Payload 的 SDK View 只在 Adapter Commit 后注册一个 Provider。其稳定 Provider Key
受最近 SavedState Owner 约束，并与 Renderer Reconciliation Identity 分离。SDK 集成拥有
Payload Schema 和正数 Format Version；框架拥有注册顺序、Restored Value 一次性消费、防御性
Bundle Copy、替换与清理。Format 不匹配或损坏的嵌套 SDK Payload 会被当作无 State，不会使其他
Provider 失效。后续 Commit 只替换 Saver，保证 Host 保存时总是读取最近已提交的 View。

## 8. 验证

核心回归覆盖：

1. nullable、嵌套集合和自定义 Saver
2. composition abort 后重试恢复
3. in-flight claim 期间宿主保存
4. 快速生命周期停止/重启的 collector 串行性
5. destroyed owner
6. 使用相同自动与显式 Key 的兄弟和嵌套子组合
7. Keyed 子项回收、重排、Host 重建与并发 Presentation 副本
8. 未知 Bundle 版本和单 Entry 损坏隔离
9. Android View Commit 后 Lifecycle Catch-up、串行 Owner 替换与 Callback Failure
10. Retained Destination Lifecycle 限制、SDK Bundle 重建、Format 隔离与 Provider 清理
