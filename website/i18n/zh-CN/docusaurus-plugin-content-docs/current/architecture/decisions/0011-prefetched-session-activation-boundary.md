---
translation_source: architecture/decisions/0011-prefetched-session-activation-boundary.md
translation_source_hash: b51df7ab59c3876e525b40ae265ab165a16748fbfd7e8e44a2789149ef4ff5e5
translation_status: current
---

# ADR-0011：预取 Session 激活边界

- 状态：已接受
- 日期：2026-08-13

## 背景

Android `RecyclerView` 可以在 Holder Attach 前完成创建和绑定，但 ViewCompose Lazy Item 过去在
此阶段只暂存不可变 Item 快照。首次 Attach 会在同一个 Fling 帧内创建子 `RenderSession`、组合
VNode 树、创建并绑定 Android View，再提交 Effect。页面把多个 View 较重的区域拆成独立组合项
后，Composition 与 View Inflation 会直接进入 `LinearLayoutManager.layoutChunk`，即使启用了
RecyclerView Prefetch，长时间 Fling 仍会出现可见卡顿。

在 Detach Bind 阶段调用现有 `render` 并不正确。成功 Render 就是一次已提交帧：Remember
Observer 会激活，`SideEffect` 与原生 Commit Callback 会执行，Overlay 会发布，协程 Effect 也
可能启动。Prefetch 是可丢弃的推测工作，Holder 可能永远不可见，因此不能越过 Commit 边界。

## 决策

1. `LazyListItemSession` 采用 Q3 三阶段生命周期：可选 `prepare`、一次 `activate`、零次或多次
   Active `render`，最后是终止性的 `dispose`。ADR-0011 采纳时，`prepare` 默认不做工作，
   `activate` 委托给 `render`。ADR-0012 随后把两个提交操作硬切为返回已安装 Revision 是否提交；
   自定义 Session 现在必须实现显式 Boolean Commit 契约。
2. Detach 且从未激活的 RecyclerView Holder 可以调用 `prepare`。标准 Widget Session 会组合候选
   VNode 树并建立原生 View 树，但保留 Prepared Composition Transaction，不执行 Commit。
3. Prepare 不运行 Remember 激活、`SideEffect`、`DisposableEffect`、`LaunchedEffect`、原生
   `AndroidView.onCommit`、Overlay 发布或已提交帧诊断回调。Prepared Holder 在 Attach 前被回收
   时，会放弃组合并释放原生树，不启动候选 Effect。
4. 首次 Attach 调用 `activate`。如果 Prepare 后没有被观察状态发生变化，Activate 会提交保留的
   Composition，然后保持原有 Effect、原生 Commit、Overlay 和诊断顺序，不重建已经准备的原生树。
5. Composition 处于 Prepared 状态时，State Read 仍然被观察。相关 State 在 Attach 前变化时，
   Activate 会放弃过期候选并同步渲染最新状态，过期 Effect 不会启动。
6. Session 激活后，在普通 RecyclerView Detach/Cache 期间继续保持 Active，直到 Holder Recycle
   或 Container Dispose。Detach 不是通用应用生命周期信号，本次变更不引入可暂停 Effect。
   Active 的 Detach Holder 收到新提交时只暂存，在再次 Attach 时渲染。
7. Submission Revision 与 Identity 规则继续作为权威。Prepare 不会把修订标记为 Committed，重复
   Revision 不会重复 Prepare 或 Activate，替换候选会释放旧的未提交 Session。
8. Prefetch 是优化而非语义保证，RecyclerView 可以在 Deadline 不足时拒绝工作。如果一组连贯的
   静态 Fixture 没有 Item 级回收收益，应用和 Demo 仍不应把它拆成很多昂贵独立 Session。

## 公开 API 与兼容性影响

本决策采纳时，`LazyListItemSession.prepare` 与 `LazyListItemSession.activate` 是
`viewcompose-ui-contract` 新增的 Q3 生命周期方法。自定义 Renderer 可以选择支持原生树 Prepare，
但 Prepare 必须不产生外部可观察的已提交工作，`dispose` 也必须能在 Activate 前安全调用。
ADR-0012 随后让 `activate` 与 `render` 返回 Boolean Commit 成功状态；这项有意的硬切保证回滚后
仍可重试，而不会错误推进 Item Revision。

标准 Android Renderer 与 UI Foundation 集成完整采用该协议。ADR-0012 后续硬切了 Item Revision、
逻辑 Session 与物理树的所有权规则；新规则替代本文最初的刷新假设，但保留本文定义的
Prepared-Activation 边界。

## 后果

- RecyclerView 相邻预取可以把 Composition 与 Android View 创建提前到 Fling 新 Item Attach 帧之前。
- 推测 Holder 不会发布业务 Effect 或 Overlay。
- Prepared Transaction 会临时持有候选 Composition 和原生树，直到 Activate、Replace 或 Recycle；
  因而 Cache 与 Prefetch Hint 仍是有界资源控制。
- 有状态 Item 在 Prepare 与 Attach 之间输入变化时仍保持正确。
- 已激活缓存项延续既有生命周期语义，不会在 Viewport 边缘反复停止和重启协程或 Disposable 工作。

## 被否决的替代方案

### Detach Bind 时正常 Render

这会为推测内容提交 Effect，让永不 Attach 的 Item 产生外部可观察行为，因此否决。

### 增加仅 Debug 或仅 Demo 的性能开关

昂贵 Attach 路径是生产 Renderer 生命周期缺陷，因此否决。受影响 Demo Fixture 仍应合并粒度，但
不能替代框架修正。

### Holder Detach 时暂停全部 Effect

`DisposableEffect`、任意 Remember Observer 与 Coroutine Scope 并不天然可暂停；RecyclerView
Detach 也会发生在正常缓存与布局波动中，不等同逻辑移除或 Host Lifecycle Stop，因此否决。

### 提交 Composition，只推迟具名 Effect Primitive

任意 `RememberObserver` 实现和未来 Commit Callback 都可能绕过不完整白名单。保留既有 Composition
Transaction 才能维持唯一权威 Commit 边界，因此否决。

## 验证

架构要求确定性覆盖：

1. Prepare 后 Attach 激活，且不发生第二次原生 Render；
2. Activate 前不运行 Remember、Side、原生 Commit、Overlay 或诊断工作；
3. Prepare 与 Attach 间 State 失效会放弃过期候选；
4. Replace、重复 Revision、Attach 前 Recycle，以及 Active Detach 后 Reattach；
5. ADR-0012 替代后，Key 与 Revision 不变时的行为；
6. 使用 `FrameTimingMetric` 对 Diagnostics Theme 执行到达列表底部再返回顶部的大力度、长时间
   Fling，而不是只在页面顶部短滑。
