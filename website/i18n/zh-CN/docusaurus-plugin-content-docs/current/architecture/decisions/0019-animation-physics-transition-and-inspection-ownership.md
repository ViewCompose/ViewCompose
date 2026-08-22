---
translation_source: architecture/decisions/0019-animation-physics-transition-and-inspection-ownership.md
translation_source_hash: 5e24a7e7e6c4a601a68318f02916f446327a7deb78997a8f98734fd0fbdbcade
translation_status: current
---

# ADR-0019：动画物理、过渡与检查所有权

- 状态：已接受
- 日期：2026-08-22
- 替代：Alpha 动画版本中 `SpringSpec` 与 `spring` 的固定时长语义

## 背景

ViewCompose 已提供确定性时长采样、状态驱动动画、`Animatable`、共享 Timeline 的
`Transition`、Fade/Size 可见性过渡、纯 Alpha `Crossfade` 与测量尺寸动画。但当前
`SpringSpec` 是固定时长阻尼曲线，并把进度 Clamp 到 `0f..1f`；它没有物理速度、
Overshoot、平衡、Decay 或 Bounds。继续扩展这个模型，会让手势接力和中断依赖一个与行为
不符的名称。

后续阶段还需要统一 Outgoing Content、显式 Seek、Layout Bounds、共享视觉元素和按请求检查
的所有权。这些能力跨越平台中立动画引擎、Composition、Android Renderer、Navigation、
Preview 与 Studio 工具；分别实现会产生互相竞争的 Frame Loop、坐标系与 Lifecycle Owner。

上游语义对比基线是 2026-08-12 发布的稳定版 AndroidX Compose Animation `1.12.0`。
仓库可执行 Compose Fixture 保持 `1.7.8`，因为 Compose `1.12.0` 要求 compile SDK 37 与
AGP 9.2，而本仓库当前是 compile SDK 36 与 AGP 8.13.2。官方发布说明和 API Reference 是
语义证据；本地 Compose 执行证据明确较旧，不能证明 `1.12.0` 行为。

## 决策

### 单一物理引擎与硬切 Spring 契约

Phase 1 落地时删除 Alpha 版本的
`SpringSpec(dampingRatio, stiffness, durationMillis)` 与
`spring(dampingRatio, stiffness, durationMillis)`。不保留 Deprecated Overload、Alias、
被忽略的 `durationMillis` 或 Legacy Spring Model。需要固定区间的调用方使用 `tween`、
`keyframes` 或其他明确带时长的 Spec；新的 `SpringSpec` 与 `spring` 名称只表示物理结束。

平台中立引擎采用单位质量二阶系统：

`x'' + 2ζω₀x' + ω₀²(x - target) = 0`，其中 `ω₀ = sqrt(stiffness)`。

- 归一化质量恒为 `1`；
- `dampingRatio`（`ζ`）无量纲，必须有限且不小于零；
- `stiffness` 必须有限且大于零，单位为 `s⁻²`；
- 位置使用各 Converter Component 的 Domain Unit，速度使用该单位/秒；
- 欠阻尼、临界阻尼和过阻尼的解析解内部以 `Double` 计算，仅在 Vector/Domain 边界转换为
  `Float`；
- 每帧从 Segment Start State 与 Monotonic Play Time 采样，不从上一帧积分，因此跳帧和
  确定性 Clock 得到相同 Sample；
- 非单调 Frame Clock 属于契约失败，不能发布候选 Sample；
- 物理 Spec 使用经过校验的 `maxDurationMillis` 安全保护，默认 10,000，范围
  `1..60_000`；它不是动画时长。

硬切 `AnimationConverter<T>`，要求声明稳定 Vector Size、Domain Unit 下有限正数的默认
Visibility Threshold，以及写入 Destination Buffer 的转换。引擎为每次 Run 一次性分配并
复用 Position、Velocity、Threshold 与 Scratch Vector。Converter 可为 Sample 返回的不可变
Domain Value 分配对象，但不得迫使 Endpoint 或 Scratch Array 每帧分配。

只有所有 Vector Component 同时满足
`abs(value - target) <= visibilityThreshold` 与
`abs(velocity) <= visibilityThreshold / 0.016 seconds` 才达到平衡。成功 Target Animation
发布精确 Target 与零保留速度。触发安全时长时不 Snap 到 Target，而是以最后接受状态返回
`DurationLimitReached`，使错误或异常缓慢配置可观测。

结果模型为：

```kotlin
enum class AnimationEndReason {
    Finished,
    BoundReached,
    DurationLimitReached,
}

data class AnimationVelocity<T>(val valuePerSecond: T)

data class AnimationState<T>(
    val value: T,
    val velocity: AnimationVelocity<T>,
    val playTimeNanos: Long,
)

data class AnimationResult<T>(
    val endState: AnimationState<T>,
    val endReason: AnimationEndReason,
)
```

`Interrupted` 刻意不作为 End Reason。新的 Last-writer Mutation 或外部协程取消会抛出
`CancellationException`，保留最后原子发布的 Value/Velocity，且被取消调用不返回结果。
替代物理动画默认以该速度开始，除非调用方提供显式 Initial Velocity。`snapTo` 与 `stop`
发布零保留速度。Callback、Converter 或 Clock 失败在保留最后提交 Sample 权威性的同时向上
传播。

Bounds 使用 Converter Domain 的 Lower/Upper Value，并在每次 Mutation 时转换一次。每个
Lower Component 必须不大于 Upper Component。越界 Sample 在发布前 Clamp，整个 Run 以
`BoundReached` 结束并发布零保留速度。Idle 时更新 Bounds 会立即 Clamp；Running 时更新会
加入同一 Mutation Transaction 并由下一 Sample 观察。外部永远看不到越界值。

### Decay 与 Gesture 接力

第一种 Decay 是平台中立的指数衰减：

`v(t) = v₀e⁻λᵗ`、`x(t) = x₀ + (v₀ / λ)(1 - e⁻λᵗ)`，且
`λ = 4.2 × frictionMultiplier s⁻¹`。

`frictionMultiplier` 必须有限且大于零。所有 Component 的绝对速度达到 Converter 派生
速度阈值、碰到 Bound 或触发最大时长保护时结束。Android Spline Fling 不会静默替代该方程；
未来 Density/平台相关 Decay 必须使用不同名称。

Gesture Owner 把平台像素/秒一次性转换为目标 Converter Unit，并把类型化速度传给
`animateDecay` 或 `animateTo`。Gesture Owner 在移交前完成 RTL 符号和 Axis Projection；
动画引擎不读取 Pointer Event，也不猜测 Density、Layout Direction 或 Nested Scroll
Ownership。

### Motion 策略与时长缩放

`MotionScheme` 保持类型中立 Role Policy。Scale 为零时所有 Motion Role 解析为 `snap`。
正数 Duration Scale 正常缩放带时长 Spec；对于物理 Spring，时间 Scale `s` 把 Stiffness
解析为 `stiffness / s²`，保留 Damping Ratio 与 Threshold。Decay Friction 解析为
`friction / s`。最大时长保护同倍率缩放并保持公开校验范围。物理求解不获得虚构名义时长。

### Content 与 Visibility Transition 代数

`Crossfade` 保持小型纯 Alpha 契约。`AnimatedContent` 所有 Keyed Replacement、按 Pair 的
`ContentTransform`、可选 `SizeTransform` 与 `AnimatedContentScope`。
`AnimatedVisibility` 增加 Slide、Scale、Transform Origin、所属 Scope 与后代
`animateEnterExit`，但不增加另一个自主 Frame Loop。

公共代数固定如下：

1. `+` 保留声明顺序；一个 Transition 中 Alpha、Size、Slide 或 Scale Channel 重复时，
   最后声明的同类 Channel 胜出。
2. 父级与后代 Alpha 相乘；Translation 完成 RTL 解析后相加；Scale 围绕各层声明的
   Transform Origin 相乘；父级 Clip 最后应用。
3. Outgoing 与 Incoming Content 在同一 Incoming Parent Constraints 下测量。没有
   Size Transform 时，Container 取当前 Child 最大尺寸；Size Transform 插值 Container
   Size 并显式声明 Clipping。
4. 默认 Incoming 绘制在 Outgoing 之上；`targetContentZIndex` 可选择另一有限顺序。
   Z 相等时保留声明顺序。
5. `contentKey` 定义子树 Identity。Key 相等时 Patch 保留子树，不做内容替换 Transition。
   两个不等 State 返回同一 Key 就表示同一 Identity，不走 Collision Fallback。
6. 最多保留两棵完整 Content 子树。A-to-B-to-C 中断时，当前 Incoming B 从已采样视觉状态
   成为 Outgoing，A 释放一次，C Enter；这既限制内存又保留最近传达的 Target。
7. Replacement Transaction Commit 时，Focus、Pointer Input 与 Accessibility Ownership
   移交 Incoming。Outgoing 可继续渲染，但不可聚焦、不可点击并对 Accessibility 隐藏。
8. 所有父级/后代 Exit Channel 完成后才移除。Host Detach 取消 Segment 并只释放两棵子树
   一次。Candidate Apply 失败时，先前提交的 Pair、Identity Map、Focus Owner 与 Effect
   仍是权威。
9. 现有 `AnimatedVisibility` 首次组合规则不变：初始内容直接以请求的 Visible Endpoint
   渲染，不自动播放 Enter。

### Seek 所有权

`SeekableTransitionState<S>` 恰好只有 Autonomous 或 Externally Seeking 两种互斥模式。
`seekTo` 在发布 Seek 前取消并 Join 自主 Frame Loop；`animateTo` 离开 Seek Mode，从当前
Sample Value 启动唯一自主 Loop。同一 Transition 永远不会同时有 Seek Writer 与 Frame-loop
Writer。

- `fraction` 必须有限且位于 `0f..1f`；非法输入抛出 `IllegalArgumentException`，不 Coerce
  也不发布；
- Normalized Fraction 映射到最长 Registered Channel Duration，较短 Channel Clamp 到自身
  Terminal Sample；
- Seek 中 Retarget 会冻结当前 Sample Channel Value 为新 Start，并把 Fraction 重置为零；
- Seek 不代表真实时间输入速度，因此物理速度为零；有 Gesture 速度的调用方必须显式交给
  Autonomous Continuation；
- `snapTo` 原子提交 Current/Target State，并令 Fraction 为零且没有 Frame Loop；
- Seek State 不可保存；恢复逻辑 Application/Navigation State，并在 Endpoint 重建视觉；
- Predictive Back 仍由 Navigation 所有。Adapter 可驱动 Seek State，但动画对象不能提交或
  回滚 Navigation Stack。

### Bounds 与 Android Layout 所有权

`Modifier.animateBounds` 使用完成逻辑 Start/End 与 RTL 解析后、直接 ViewCompose Layout
Parent 的本地物理像素坐标系。它动画真实 Measure/Layout Rectangle，不只是 Draw
Translation，因此 Hit Test 与 Accessibility Bounds 每个提交帧都与可见矩形一致。

Constraint 或 Target Topology 变化时，每个受影响 Node 使用一次 Lookahead 风格 Candidate
Measure。纯 Property Frame 复用 Target，不重复 Measure。Parent、Scroll、Density、
Layout Direction 或 Constraint 变化时，从当前提交 Rectangle Retarget 到新 Parent 坐标系
中的 Target。跨 Owner Boundary Reparent 会结束本地 Bounds Motion，并启动目的地普通 Enter。

Android Renderer 在一个 Candidate Transaction 中 Stage Measure、Layout、Hit Geometry、
Accessibility Geometry 与 Animation Ownership。Apply 失败时保留旧 Rectangle 与 Target。
拥有 Input 或 Accessibility 的 Node 不允许用 Visual-only Translation 降级。

### 共享视觉运动

`SharedTransitionLayout` 创建一个限定于 Composition Owner 的 Key Namespace；与 Navigation
一起使用时还限定于一个 Navigation Session。相同 Key 的一个 Source 和一个 Target 才形成
Pair。多个 Source/Target、缺少 Peer、Root 未 Place 或坐标已 Detach 时使用普通本地
Enter/Exit，不猜测 Winner，也不保留 Overlay。

匹配内容在 Root-owned Overlay 中渲染并插值 Bounds。Shared Element 渲染 Target 表示；
Shared Bounds 可分别保留 Outgoing/Incoming 表示。逻辑 Target Destination 独占 Pointer、
Focus 与 Accessibility，Overlay 不可交互并对 Accessibility 隐藏。

Destination/Session Disposal、Cancellation、Navigation Rollback、Configuration Change 或
Renderer Transaction 失败都只释放 Overlay 与 Pair Record 一次。Key 不跨 Window、Activity、
Process 或 Navigation Session 配对；跨 Session 共享过渡需要新的 ADR。

### 按请求检查与受控 Preview Seek

Runtime-neutral Inspection Model 位于 `viewcompose-preview-core`。具体 App-process 激活仍在
可选 Preview 产物中并遵守 ADR-0009：必须同时存在产物、可调试进程与有效显式请求。
Core Animation 与生产 Animation 产物不含 Socket、File Watcher、Polling Loop、Studio Class
或 Always-on Registry。

一个带 Nonce 的请求只生成一份有界不可变 Snapshot，包含 Transition Label、State、Channel
Kind、Duration、Play Time、Value、Velocity、Bounds 与 Terminal Reason。限制为 1,000 Node、
256 Channel、1 MiB 编码输出和 100 ms 请求生命周期。Malformed、Oversized、Expired、
Duplicate 或 Stale Request Fail Closed，并释放请求所有状态。

仅请求创建的 Synthetic Preview Session 允许受控 Seek。工具不能接管 Live App-owned
Transition。请求结束或替代时恢复普通 Preview Ownership 并释放 Synthetic Seek State。
本 ADR 就是 ADR-0009 要求的后续决策；除非未来提出 Live-process Mutation，否则无需另一份
工具 ADR。

### 公开 API 质量与归属

下列公开 API Family 全部为 Q3。内部 Solver、Vector Scratch Pool、Overlay Record 与 Request
Codec 为 Q0，不能出现在可编译 Sample 中。

| Phase | 公开 API Family | 所有者 | 可编译 Sample 与最低测试类别 | 兼容性 |
| --- | --- | --- | --- | --- |
| 1 | 变更后的 `SpringSpec`、`spring`、`AnimationConverter`、Duration Query/Sampling Entry | `viewcompose-animation-core` | 物理 Spring/Threshold Sample；解析解、Clock、数值、非法输入、分配测试 | 硬切 |
| 1 | `DecayAnimationSpec`、`ExponentialDecaySpec`、`exponentialDecay`、`AnimationVelocity`、`AnimationState`、`AnimationResult`、`AnimationEndReason` | `viewcompose-animation-core` | Decay/Result Sample；速度、Bounds、End Reason 测试 | 除替换 `AnimationRunResult` 外为新增 |
| 1 | 变更后的 `AnimatableCore.animateTo`、`animateDecay`、`updateBounds`、`velocity` | `viewcompose-animation-core` | Imperative Core Sample；取消与并发测试 | 硬切 |
| 1 | 变更后的 `Animatable.animateTo`、`animateDecay`、`updateBounds`、`velocity` | `viewcompose-animation` | Composition Sample；Last Writer、Lifecycle、Snapshot 测试 | 硬切 |
| 2 | `ContentTransform`、`SizeTransform`、`AnimatedContentTransitionScope`、`AnimatedContentScope`、`AnimatedContent` | `viewcompose-animation` | Keyed Replacement Sample；Identity、Measure、Focus、Rollback、设备测试 | 新增 |
| 3 | Slide/Scale Transition Factory、`AnimatedVisibilityScope`、`animateEnterExit` | `viewcompose-animation` | 组合 Visibility Sample；代数、RTL、释放、设备测试 | 新增 |
| 4 | `TransitionSegment`、泛型 `Transition.animateValue`、Segment-aware Channel Overload、`SeekableTransitionState`、Seekable `rememberTransition` | `viewcompose-animation` | Segment/Seek Sample；Ownership、Range、Retarget、Predictive Back Adapter 测试 | 新增；删除内部 Segment Helper |
| 5 | `Modifier.animateBounds`、Bounds Scope/Configuration | `viewcompose-animation` | Bounds Sample；坐标、Remeasure、Input、Accessibility、Rollback 设备测试 | 新增 |
| 6 | `SharedTransitionLayout`、Shared Key/State/Scope、`sharedElement`、`sharedBounds`、Resize/Bounds Transform | `viewcompose-animation`，Navigation Adapter 位于 `viewcompose-navigation-android` | Navigation Shared-motion Sample；Pairing、Overlay、Lifecycle、Rollback、Accessibility 设备测试 | 新增 |
| 7 | 不可变 Animation Inspection Request/Response 与 Snapshot Type | `viewcompose-preview-core` | Protocol Sample；Codec、Limit、Stale Request、Privacy 测试 | 可选新增 |
| 7 | Preview Animation Inspection/Seek Client Surface | `viewcompose-preview` 与 Studio Plugin | Preview-only Sample；Activation、Isolation、Request Lifetime、Plugin UI 测试 | 可选新增 |

每个实现 PR 都要同时提供规范英文 API 文档、可编译 Q3 Sample、所属模块文档、兼容说明，
以及仓库策略要求的生产产物 Changeset。

### 已冻结验证 Fixture 与预算

物理动画前的 Macrobenchmark Fixture 为 `AnimationPerformanceBenchmark`，包含四项 Revision-1
Workload：`animation.specs` 固定时长 Spring Value Channel、`animation.content` Crossfade、
`animation.content-size` 测量尺寸运动、`animation.transition` 同步 Channel。每项使用 R8/资源
收缩不可调试 Target、`CompilationMode.None`、五次迭代、测量外五秒启动稳定期、
Accessibility Action 与每次迭代四次完整前进/返回往返。仅在固定 CPU/GPU/Interconnect
Policy、平台可提供 Thermal Status 时以 `NONE`/`LIGHT` 开始、Workload Revision 不变且
run-P50 CV 不超过 `0.15` 时接受结果。对于没有平台 Thermal-status Service 的 API 29 以下
参考设备，改为记录每个方法的电池温度范围、要求 AndroidX Thermal-throttle Sleep 为零，并且
仍在 Clock 漂移或 Timing 不稳定时失败关闭。

后续 Phase 在同一设备与 Clock Policy 上对比最接近且未变的 Revision-1 Workload。Shared
Motion 还使用带 Revision 的 Navigation Benchmark。Tooling 使用可调试的成对 Inactive/
Requested Fixture，因为 Release Benchmark 无法观察可选 Debug Tooling。

| 预算 | 接受规则 |
| --- | --- |
| Frame CPU | P50 仅在同时超过 `5%` 与 `0.3 ms` 时失败；P95 仅在同时超过 `10%` 与 `0.8 ms` 时失败 |
| Peak Process Memory | 仅在同时超过 `10%` 与 `1,024 KiB` 时失败；Phase-specific Retained-tree Counter 也必须通过 |
| Engine Allocation | Position、Velocity、Threshold 与 Scratch Vector 每次 Run 分配一次；内置 Scalar Sampling 每帧新增零个引擎所有对象 |
| Retained Content | 最多两棵完整 `AnimatedContent` 子树，每个匹配 Shared Pair 最多一个 Overlay 表示 |
| Measurement | 每个受影响 Node/Target Invalidation 最多一次额外 Target Measure；纯 Property Frame 额外 Measure 为零 |
| Inactive Tooling | Registration、Poll、Report Write、Request-owned Object 与 Recurring Hot-path Work 全为零 |
| Requested Tooling | 不超过 1,000 Node/256 Channel/1 MiB/100 ms 请求边界；不得摊销进 Inactive Result |

不稳定 Run、Workload 改变、Clock Policy 不匹配或缺少 Counter 都是 `inconclusive`，不是通过。
跨过预算的 Regression 会阻止或收窄该 Phase；不允许重复运行直至得到有利 Sample。

## 影响

- Phase 1 会有意对 Alpha Animation 产物造成源码与二进制破坏。现有
  `spring(durationMillis = ...)` 必须选择物理 `spring(...)` 或 Duration Spec。
- 物理 State、Velocity、Decay、Bounds、确定性 Sampling 与 Result 只有一个平台中立 Owner。
  Android Gesture/Layout 代码只能适配单位，不能实现另一 Solver。
- Content、Visibility、Seek、Bounds、Shared Motion 与 Tooling 按依赖顺序构建，并复用一个
  Transition Coordinator，而不是并行 Frame Loop。
- Android View Renderer 继续负责提交 Geometry、Hit Test、Accessibility、Overlay 与 Rollback。
- 仅在语义一致时沿用 Compose 命名；ViewCompose 保持自己的 Transaction、Navigation 与
  Optional-tooling 边界。

## 被拒绝的方案

### 以 Legacy Overload 保留 Duration Spring

拒绝。两个结束模型不同的 `spring` Factory 会让 Code Review 与 Motion Policy Resolution
产生歧义；Deprecated 或 Ignored `durationMillis` 会保留错误心智模型，把失败推迟到运行时。

### 在规范化进度上补 Velocity

拒绝。Progress Derivative Velocity 在 Retarget、Clamp 与 Converter Dimension 间不稳定，
无法正确支持 Decay 或 Gesture Handoff。

### 每个 Feature 各自拥有 Frame Loop

拒绝。Content、Visibility、Seeking、Bounds、Navigation 与 Tooling 会竞相发布关联 State，
不能共享原子 Segment Completion 与 Cancellation。

### 只用 Draw Translation 动画 Bounds

拒绝。可见 Geometry 会与 Android Hit Test、Accessibility 不一致。

### 保留全局 Shared-key 或 Animation Registry

拒绝。它会配对无关 Session、保留 View/Destination，并在普通帧制造工作。Scope Coordinator
与 Request-owned Inspection 无需全局生命周期即可满足需求。

## 验证

Phase 0 的接受条件是：四项 Revision-1 Benchmark Method 可编译并生成稳定 Root-controlled
Baseline；仓库文档与翻译门禁通过；拟议 Q3 清单保持无实现。之后每个 Phase 在进入下一阶段前，
都必须满足其 API 表条目、相关确定性与设备矩阵、同设备性能预算、事务 Rollback、Lifecycle
Release、Reduced Motion 行为和 Changeset 要求。
