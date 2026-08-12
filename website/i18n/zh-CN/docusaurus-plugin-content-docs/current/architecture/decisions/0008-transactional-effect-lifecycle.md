---
translation_source: architecture/decisions/0008-transactional-effect-lifecycle.md
translation_source_hash: 4f7b798ce7083b567bcce39fa43a169fa615df8419803f476f7186f744a441f4
translation_status: current
---

# ADR-0008：事务式 Effect 生命周期

- 状态：已接受
- 日期：2026-08-12

## 背景

ViewCompose 使用 Android View 作为渲染引擎，并以 `ComposerLite` 作为不依赖编译器的组合
Runtime。首批 Effect API 的外形接近 Jetpack Compose，但内部保证并不统一：

- `RememberObserver` 回调在 prepared composition 提交期间运行，而 `DisposableEffect` 使用
  单独的 Slot 列表和更晚执行的队列；
- `rememberUpdatedState` 在候选组合期间写入普通 Snapshot State，因此被放弃的候选可能改变
  已提交 Effect 观察到的值；
- Disposable Cleanup 会在调用前被清空，但 Cleanup 抛出异常后，旧 Slot 可能保持原 Key 却不再
  活跃，而且没有明确的生命周期状态；
- 在既有 Scope 中创建、随后又随该 Scope 脱离的候选 Remember 值可能收到错误的终止回调；
- `rememberCoroutineScope` 额外插入局部 `SupervisorJob`，改变了名称所暗示的结构化 Scope 子任务
  失败传播；以及
- Effect 回调可能在 Provider Stack 返回后读取 Composition Local，并静默获得与声明位置无关的
  默认值。

这些是 Runtime 设计缺陷，不是 Demo 或 Material 独有行为。它们会影响资源观察、Lifecycle
Adapter、动画、设计系统、Saveable State、Overlay，以及未来所有跨帧持有工作的集成。

Compose Compiler 不能被当作实现细节引入。ViewCompose 因此不能依赖编译器生成的 Restart
Group、调用点身份、Changed Flag、稳定性推断、Composable 调用限制或 Movable Group 行为。若只
匹配 API 名称却不定义独立的事务契约，只会掩盖而不是解决这条边界。

## 决策

1. 一个 Remember Slot 生命周期状态机统一负责 `RememberObserver`、`DisposableEffect` 与
   `LaunchedEffect` 的转换。合法状态是 Pending、Active 和 Terminal；Runtime 在调用用户代码前
   先转换状态，因此即便回调抛出异常，终止回调也最多执行一次。
2. `DisposableEffect` 是 Remember 生命周期对象。移除独立的 Disposable Effect Slot 列表和
   Commit Queue。公开 API 要求一个或多个 Key，并且只能通过
   `DisposableEffectScope.onDispose` 返回 Cleanup。
3. 候选组合保持隔离。仅存在于候选中的 Remember 值会被 Abandon 而非 Forget；Abort 后已提交
   值继续活跃。`rememberUpdatedState` 使用 Composer 所有的 Committed Holder：组合可以读取候选
   值，既有 Effect 只有在成功提交后才会收到该值。
4. 已提交帧先发布 Remember 值和 Committed Holder，再执行生命周期回调。它会先执行全部退出
   生命周期，再执行任何进入生命周期，最后运行 `SideEffect`。所有同步操作串行执行；某项失败
   时仍尝试其他无关操作。第一个失败负责报告，后续失败作为 Suppressed Exception 附加。
5. 无 Key 的 `SideEffect` 在每次成功调用后运行。带 Key 的重载在首次提交及结构相等性判断发生
   变化时运行。候选 Abort 会丢弃两种形式。
6. `LaunchedEffect` 与 `rememberCoroutineScope` 使用 Render Session 的 Coroutine Context。
   `LaunchedEffect` 在替换或退出时取消 Job。Remember Coroutine Scope 持有普通子 `Job`；调用方
   Context 不能替换它。传入含 Job 的无效 Context 会得到失败 Scope，而不是脱离所有权。
7. Composition Lifetime、Render Visibility、Android Lifecycle 与 Process Lifetime 保持独立。
   UI Foundation 持有 Composition Effect；`viewcompose-lifecycle-androidx` 持有 Start/Resume
   成对 Effect 和 Lifecycle State 观察。保留一个隐藏 Session 不会自动暂停任意 Composition
   Coroutine。
8. Effect Lambda 在声明期间捕获已经解析的 Local 值。Runtime 不会围绕任意同步或异步工作重新
   安装整个 Local Stack。被标记的 Effect Callback 不能读取 Local，因而不会误用默认值或线程上
   恰好活跃的无关 Provider；延迟子组合继续使用显式 Local Snapshot。
9. Effect API 均为 Q3。KDoc 与可编译样例必须定义 Key 比较、位置身份、阶段顺序、Rollback、
   Cancellation、Dispatcher/线程所有权、Cleanup、失败行为，以及不依赖编译器的结构限制。
10. ViewCompose 承诺自身测试保护的行为，而不是相同的 Compose 内部实现。固定参数数量的重载
    改善源码易用性，但无法创造编译器生成的身份或 Skip 语义。

## 公开 API 与模块影响

- `viewcompose-runtime` 强化 `ComposerLite` 的 Remember 生命周期和 Committed Value 行为，并从
  Alpha 公开面移除低层 Disposable Slot API。
- `viewcompose-ui-foundation` 硬切 `DisposableEffect`，增加带 Key 的 `SideEffect` 重载，对齐
  Coroutine 所有权，并改变 `rememberUpdatedState` 的实现契约。
- `viewcompose-lifecycle-androidx` 增加 Q3 成对 Lifecycle Effect 与 Lifecycle State 观察。
- 第一方集成在同一变更中迁移；不会用 Deprecated 兼容层保留不安全的 Disposable Cleanup
  形式。

## 后果

- 失败的 View Tree 候选不再能够更新已提交帧所读取的回调。
- Disposable 与 Launched Effect 会获得与其他 Remember 资源一致的 Pending/Active/Terminal
  记账，包括复杂结构变化后的正确 Abandon。
- Cleanup 与替换顺序更容易推理和测试。回调抛出的异常可以被观察，但不能导致重复 Cleanup，
  也不能阻止尝试其他无关转换。
- 使用者必须把每个 `DisposableEffect` 迁移到显式 Key 与 `onDispose`。
- `rememberCoroutineScope` 启动的子任务可能使其 Remember Scope 失败。Session 层
  Supervisor 仍隔离不同 Composition Scope，同时保留局部结构化所有权。
- 精确的 Compose Compiler 行为仍不可用。位置身份仍依赖 ViewCompose 结构 Group 与显式
  `key`。

## 被拒绝的替代方案

### 保留独立的 `DisposableEffect` Runtime Slot 类型

拒绝，因为它重复了 Remember 资源本就需要的身份、Rollback、移除、异常和释放逻辑。这种重复
已经导致顺序差异与无效状态。

### 通过普通 `SideEffect` 更新 `rememberUpdatedState`

拒绝，因为生命周期回调（包括新启动的 Coroutine）先于 `SideEffect` 运行。新 Coroutine 在首次
执行时可能读到旧值。

### 在长生命周期 Mutable Snapshot 中执行组合

本次变更拒绝，因为在原生树提交后应用任意组合写入会引入 Snapshot Conflict，并把组合写入语义
扩大到 Effect 缺陷以外。Committed Holder 可以解决所需隔离，无需把每次组合都变成可变事务。

### 围绕每个 Effect 回调重新安装全部 Composition Local

拒绝，因为异步工作可能超出声明 Stack，Local 可能包含 Host Scope 对象，隐式捕获还会保留过期
Context。显式值捕获与专用 Local Snapshot 可以让所有权保持可见。

### Render 不活跃时暂停全部 Effect

拒绝，因为 Render Visibility 不等于 Android Lifecycle。后台同步、导航保留与离屏停止的 Owner
需要不同策略。Lifecycle Bound 工作应使用 Lifecycle 集成 API。

## 验证与落地

实现遵循有效的
[事务式 Effect Runtime 收敛计划](https://docs.viewcompose.com/project/plans/effect-runtime-convergence)。
保留该决策要求：覆盖每个生命周期转换和阶段的故障注入测试、公开 API 样例、第一方迁移、模块与
Compose 迁移文档、中文镜像、不可变 Release Changeset，以及仓库 Quick/Full 质量门禁。
