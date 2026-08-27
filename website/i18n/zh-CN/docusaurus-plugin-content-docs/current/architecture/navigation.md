---
translation_source: architecture/navigation.md
translation_source_hash: fd3fb569c2baecee1e9cf7da5145b9dd0d19299aa063ad706b5984c35bb029cf
translation_status: current
---

# 导航运行时架构

## 1. 所有权边界

ViewCompose 导航使用 Activity 或 Window 作为最外层 Android Host，但 Destination 是框架持有
的页面，而不是 Activity 或 Fragment。能力分布在两个已发布产物中：

- `viewcompose-navigation-core` 持有平台无关的 Route、Graph、保留栈、事务、Lifecycle Plan、
  Deep Link 和 Pane Scene 模型；
- `viewcompose-navigation-android` 持有 Destination 与 Graph 的 Android Owner、子
  RenderSession、原生 View 展示、SavedState 编码、系统与 Predictive Back，以及视觉 Motion。

这一分层让状态机不依赖 Android 所有权，同时让原生 Host 在唯一位置协调栈状态、渲染、
Lifecycle 和 View 层级变化。

## 2. 事务边界

导航采用两阶段操作。Core `prepare` 计算不可变候选状态和 Entry Mutation，但不会发布。
Android Host 准备 Destination Owner 和子 RenderSession，在暂存原生容器中完成渲染，随后才
提交 Core 事务。准备失败会回滚候选，并保留旧栈、可见 Scene 和 Owner。

一个 Controller 同时只能存在一个已准备事务。渲染、Lifecycle 移动或视觉 Motion 期间收到的
重入命令，会在当前操作到达终态后串行执行。因此 `NavResult.Queued` 表示等待中的已接受任务，
而不是已提交完成。

栈提交后，视觉 Motion 可以完成、取消或重定向，但不能撤销应用状态。所有视觉终态都会收敛
到已提交目标。提交后 Effect 失败会以 `stackCommitted = true` 上报；Host 不会假装旧栈仍是
权威状态。

## 3. Destination 与 Graph 身份

每个 Destination Entry 都持有稳定的子 RenderSession、Lifecycle、ViewModelStore、
SavedStateRegistry namespace 和 ViewCompose saveable-state namespace。连续两次 Push 相同
Route 会产生两个 Entry 身份。隐藏的保留 Entry 继续保持身份和状态，但帧驱动工作受 Lifecycle
限制，页面重新可见前才恢复渲染。

每个嵌套 Graph 实例都有独立 `NavGraphOwner` 身份。同一 Graph 实例的后代共享 Lifecycle、
SavedState 和 ViewModel，直到最后一个保留后代被移除。以后再次进入同名 Graph Route 会创建
新 Owner。只有渲染 Destination 内容时才能访问从根到叶的 Graph 链，不能借此在活动 Host
之外制造所有权。

最近的父级 ViewModelStore Owner 提供默认 Factory 和 CreationExtras。子导航 Owner 只替换
Store Owner、SavedState Owner 以及 Route 或 Graph 参数。父级 Owner 身份变化会重建原生
Host，防止保留 Entry 混用两套 Provider 契约。

## 4. Lifecycle 投影

Host 把已提交导航状态和 Pane 状态投影为 Android Lifecycle，并受最外层 Host Lifecycle 限制：

| 角色 | 目标状态 |
| --- | --- |
| 可交互的稳定 Destination 及其 Graph 路径 | `RESUMED` |
| 可见的转场参与者 | `STARTED` |
| 隐藏的保留 Destination 或 Graph | `CREATED` |
| 提交前已准备的候选 | 不高于 `CREATED` |
| 永久移除的 Destination 或 Graph | `DESTROYED` |

生命周期先向下再向上变更，因此单 Pane Host 不会短暂拥有两个 `RESUMED` Destination。通过
校验的多 Pane Scene 可以有意让多个叶子 Destination 及其共享 Graph 路径进入 `RESUMED`。
已销毁的 Entry 和 Graph 身份不能重新引入。

## 5. 恢复边界

remember 的 Controller 会持久化已提交栈、Route 参数、Destination 与 Graph 身份、选择历史、
Destination 与 Graph 的 SavedStateRegistry Bundle，以及 ViewCompose saveable 值。它不会
序列化 View、RenderSession、LifecycleRegistry 实例、ViewModelStore 内容、待处理事务或运行中
动画。

恢复会校验格式限制、栈配置、Route 是否存在、叶子解析和 Graph 层级。不兼容或格式错误的
状态会被丢弃，改用配置的初始状态。失败关闭可以防止应用升级后把旧 SavedState 或 ViewModel
namespace 绑定到另一个 Destination。

## 6. 返回与视觉 Motion

只有活动 Controller 可以消费返回时，系统返回才会参与。Predictive Back 在已提交 Entry 上
创建预览，但不改变 Core 栈。取消时恢复稳定 Scene，完成时执行普通 Pop 事务。Detach、禁用
Back 或销毁 Host 都会取消未结束的预览，因为平台 Dispatcher 可能不再提供终态回调。

`NavTransitionSpec` 和 Shared Content 捕获只是展示策略。它们在提交后作用于已经拥有的
Destination Root，不持有页面或 Session，也不能接收输入或无障碍焦点。捕获失败只降级对应
视觉配对，不改变导航状态。

## 7. 证据与验证

不变量边界有三个层次的覆盖：

- Navigation Core 测试覆盖两阶段事务、确定性保留栈、Graph 校验、严格 Deep Link、
  Lifecycle Plan 和 Pane Scene 校验。
- Navigation Android 测试覆盖候选回滚、保留 Owner 身份、Lifecycle 顺序、SavedState 兼容、
  队列命令、转场重定向和 Predictive Back。
- 可编译的[导航教程](../tutorials/navigation.md)和[可上线 Host 指南](../guides/navigation.md)
  提供公开首个成功路径和人工验收路径。

运行 `./gradlew :viewcompose-navigation-core:test :viewcompose-navigation-android:testDebugUnitTest`
执行确定性架构测试。只有指南中的真实返回、重建、Predictive Back 和失败路径也通过后，才能
接受设备行为。
