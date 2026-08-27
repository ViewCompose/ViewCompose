---
translation_source: modules/viewcompose-animation-core/README.md
translation_source_hash: 5689f89a45e7fc65867ff04046ecb3d0d29b73cf9aebb0d4107c63f03169ec3b
translation_status: current
---

# 动画核心

`viewcompose-animation-core` 是 ViewCompose 的平台中立计时与物理运动引擎。它定义不可变动画
Spec、Easing、值/速度转换、显式时间确定性采样、协程驱动 Frame Loop、底层 Last-writer 动画值
和共享 Transition Segment 协调。它不依赖 Android UI 或 Composition。

## 产物与稳定性

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="animation-core-module-dependency" sample_id="module.animation-core-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-animation-core:0.1.0-alpha04")
}
```

- 稳定性：**Alpha**。物理单位、取消、Bounds、结果、计时归一化、重复和 Transition Segment
  行为已经评审和测试；名称与上层 Composition 集成仍可能在 Alpha 版本之间演进。
- 平台：Kotlin/JVM，不依赖 Android Framework。
- `viewcompose-runtime` 以传递方式公开，因为公开 Clock 与动画 API 使用
  `MonotonicFrameClock`。Kotlin Coroutine 提供结构化取消。
- 应用通常通过 `viewcompose-animation` 传递获得本模块；自定义 Runtime、确定性采样、Preview
  工具或平台中立测试可以直接依赖它。

## Spec、物理与 Easing

`AnimationSpec` 是不持有 Clock、Coroutine 或值的不可变运动描述。`FiniteAnimationSpec` 把会
收敛的 Target Motion 与 `InfiniteRepeatableSpec` 分开；`DurationBasedAnimationSpec` 标识可以
放进有限或无限 Repeat 的 Spec。

- `tween`：固定时长、可选 Delay 与 `Easing` 曲线；
- `spring`：带 Damping、Stiffness、类型化初速度、平衡终止与安全上限的归一化质量物理运动，
  不拥有名义时长；
- `keyframes`：时间戳 Progress Checkpoint 之间的线性插值；
- `snap`：立即选择 Target；
- `repeatable`：有限次 Restart 或交替的时长型 Cycle；
- `infiniteRepeatable`：时长型 Cycle 持续到取消；
- `exponentialDecay`：带 Friction Multiplier 与安全上限的无 Target 速度衰减。

{/* compiled-region source="viewcompose-animation-core/src/test/samples/com/viewcompose/animation/core/samples/AnimationCoreSamples.kt" region="animation-core-specifications" sample_id="module.animation-core-specifications" build_target=":viewcompose-animation-core:compileTestKotlin" */}
```kotlin
val motion = repeatable(
    iterations = 2,
    animation = tween(
        durationMillis = 240,
        easing = EasingDefaults.FastOutSlowIn,
    ),
    repeatMode = RepeatMode.Reverse,
)

val physical = spring(
    dampingRatio = 0.72f,
    stiffness = 240f,
)
```

引擎把负 Delay 归一化为零，把非正的时长型 Interval 归一化为一毫秒。零次 Repeat 的时长为零
并停在 Start。Factory 保留请求值；Evaluator 构造负责归一化和一次性 Keyframe 排序。

`SpringSpec` 使用归一化质量 `1` 与 `ω₀ = sqrt(stiffness)` 求解
`x'' + 2ζω₀x' + ω₀²(x - target) = 0`。欠阻尼、临界阻尼与过阻尼分支使用 `Double` 中间值，
每帧从 Segment Start 采样，不从上一帧积分。成功稳定后发布精确 Target 与零速度。达到
`maxDurationMillis` 时保留物理采样并报告 `DurationLimitReached`；`durationMillis` 已移除。
Spring 的平衡时间依赖端点、速度与阈值，因此不能放进 Repeat。

`ExponentialDecaySpec` 使用 `λ = 4.2 × frictionMultiplier s⁻¹`。它在达到 Converter 派生速度
阈值、Bound 或安全上限时停止。它是平台中立模型，不会静默替换成 Android Spline Fling。

`EasingDefaults` 提供分配稳定的多项式曲线；`CubicBezierEasing` 对自定义控制点执行有界 x 轴
反解。时长型 Spec 的 Progress 会 Clamp 到 `0f..1f`；物理 Spring 值不会被 Progress Clamp，
因此可以 Overshoot。

## 语义 Motion Scheme 与 Reduced Motion

`MotionScheme` 组合 Fast/Default Effect、Spatial 和 Expressive Spatial Role，不命名组件或设计
系统。组件选择 `MotionRole`，不把原始参数复制进结构 Recipe。

`ReducedMotionPolicy` 保持相同逻辑 Target，同时把非必要运动替换成 `SnapSpec` 或缩短后的
Spec。缩放递归应用到 Tween Delay、Keyframe 时长/Checkpoint 和 Repeat Child。物理 Spring 的
时间比例 `s` 会把 Stiffness 解析为 `stiffness / s²` 并缩放安全上限，不会发明名义时长。
应用显式提供 Host Reduced-motion 决策；Animation Core 不读取平台设置。

`MotionInterruptionPolicy.RetargetFromCurrent` 对应 `AnimatableCore` Last-writer 行为；
`SnapToTarget` 仍是组件 Owner 指令，不会创建第二条引擎 Loop。

## 确定性采样与物理状态

`TargetAnimation<T, V>` 一次转换端点、速度、阈值和 Scratch Storage，之后按显式纳秒时间返回
不可变 `AnimationState<T, V>`。它不持有 Clock 或可变所有权，适用于 Seek、测试、Transition
Channel、Renderer Adapter 与 Preview 工具：

{/* compiled-region source="viewcompose-animation-core/src/test/samples/com/viewcompose/animation/core/samples/AnimationCoreSamples.kt" region="animation-core-sampling" sample_id="module.animation-core-sampling" build_target=":viewcompose-animation-core:compileTestKotlin" */}
```kotlin
val animation = TargetAnimation(
    initialValue = 20f,
    targetValue = 100f,
    animationSpec = tween(durationMillis = 400, easing = EasingDefaults.Linear),
    converter = AnimationConverters.Float,
)
val halfway = animation.stateAt(200_000_000L)
```

对时长型 Spec，`durationNanos` 包含 Delay 与饱和的 Repeat 乘法；对 Spring，它解析第一个满足
平衡条件的一毫秒采样。`DecayAnimation<T, V>` 为无 Target Motion 提供相同显式时间模型，并
公开其无界渐近 Target。Evaluator 复用 Array 且非线程安全，一个 Owner 必须串行采样。

`AnimationState` 携带值、类型化速度和 Segment 相对 Play Time。`AnimationResult` 携带终止状态
以及 `Finished`、`BoundReached` 或 `DurationLimitReached`。协程中断不是正常结束原因。

## 分离值域与速度域

`AnimationConverter<T, V>` 把动画值域与切向/速度域分开，并写入调用方持有的 Buffer。
实现必须声明一个稳定正数 `vectorSize`、`zeroVelocity` 和有限正数
`visibilityThreshold`；所有转换使用同一维度，且不能保留传入 Array。

内置映射是 `Float`/`Float`、`Int`/`Float` 和打包 ARGB `Int`/`ArgbChannels`。分离值域可以
保留整数小数速度，以及有符号 Alpha/Red/Green/Blue 变化率。整数重建向零截断。ARGB 值按编码
Channel 插值，不执行 Gamma 或色彩空间校正。

{/* compiled-region source="viewcompose-animation-core/src/test/samples/com/viewcompose/animation/core/samples/AnimationCoreSamples.kt" region="animation-core-converter" sample_id="module.animation-core-converter" build_target=":viewcompose-animation-core:compileTestKotlin" */}
```kotlin
data class Point(val x: Float, val y: Float)

val converter = object : AnimationConverter<Point, Point> {
    override val vectorSize = 2
    override val zeroVelocity = Point(0f, 0f)
    override val visibilityThreshold = Point(0.01f, 0.01f)

    override fun convertToVector(value: Point, destination: FloatArray) {
        destination[0] = value.x
        destination[1] = value.y
    }

    override fun convertFromVector(vector: FloatArray) = Point(vector[0], vector[1])

    override fun convertVelocityToVector(velocity: Point, destination: FloatArray) =
        convertToVector(velocity, destination)

    override fun convertVelocityFromVector(vector: FloatArray) = convertFromVector(vector)
}
```

转换不完整、值非有限、阈值非正、维度不兼容或零速度无效时，会在发布前失败。端点、位置、
速度、阈值和 Scratch Vector 每个 Evaluator 只分配一次并复用。自定义 Converter 可以为 Sample
返回不可变 Domain Value，但必须保持确定性、非阻塞和分配审慎。若推导出的 Spring Sample 或
Decay Target 无法在 Converter 的 Vector Domain 中保持有限，也会在发布前失败，绝不会被解释为
已经平衡。

## Frame 执行、Mutation 与 Bounds

`runAnimation` 等待 `MonotonicFrameClock`，在调用方协程上发布 `AnimationState`，并返回
`AnimationResult`。`runDecayAnimation` 使用相同契约。非单调时间戳会在候选 Sample 发布前失败。
取消始终向外传播，绝不强制 Target；Clock、Callback 与 Converter 失败保持原样传播。

`AnimatableCore<T, V>` 是唯一 Last-mutation-wins Owner。`animateTo`、`animateDecay`、
`snapTo` 与 `stop` 会取消旧调用方、拒绝过期 Sample，并原子发布值和速度。省略 `animateTo`
的初速度时，会在同一个 Mutation Snapshot 中捕获保留值和速度；时长型 Spec 忽略该速度。
候选目标动画和候选 Decay 都会在所有权改变前完成校验，因此无效替代请求不会影响当前有效
Mutation。Owner 构造会在公开状态前验证初始值、Vector 维度、零速度与可见阈值。
`snapTo` 与 `stop` 都以一次原子 Idle State Commit 替代旧 Mutation、保留零速度，且不公开
瞬时 Running State；无效 Snap 不会改变当前有效 Mutation。

`updateBounds` 安装 Converter Domain 的闭区间上下界。Crossing Sample 在发布前 Clamp，整个
Mutation 以 `BoundReached` 终止并发布零速度。Idle Bound Update 与之后的 `snapTo` 立即 Clamp。
任一分量上下界反转时失败，且不修改已接受状态。

`viewcompose-animation` 提供 Composition Clock 绑定和大多数应用应使用的 Facade。Core Owner
刻意不持有 Scope 或 Frame Clock。

## 多 Channel Transition 协调

`TransitionCore<S>` 在多个 Channel 之间协调逻辑端点和一条 Timeline。Transition Owner 更新
Target、注册各 Channel Duration、推进共享 Play Time，并在最长 Channel 完成时提交 Target。
较短 Channel 在自身 Evaluator 中稳定。动态 Owner 会在重新计算完整已提交 Channel 集合后调用
`replaceDuration`，因此新增或移除 Channel 都可以增长或缩短共享时长。

`seekToPlayTime` 可以在当前 Segment 上显式向前或向后采样，并能在终点 Seek 后重新激活该
Segment。`restartRunningSegment` 为未完成的上层样本继续自主运行时创建新的 Timing Identity。
`snapTo` 把 Current State、Target State 与两个 Segment Endpoint 原子折叠成一个 Idle Snapshot。
这些都是协调 Primitive，而不是相互竞争的 Owner：上层必须把它们与 Frame Loop 串行化，并负责
保存 Channel Value 与 Velocity。

`TransitionCore` 非线程安全，也不启动或取消任务。物理 Channel 注册解析后的平衡时长，因此
Transition State 仍会等全部 Channel 稳定后提交。`viewcompose-animation` 提供 Q3 泛型 Channel、
稳定 Segment API、归一化 Seek 所有权、取消/Join 策略、Composition Binding 与原子可观察发布。

## 测试自定义动画代码

- 使用 `TargetAnimation.stateAt` 断言精确 Boundary、Delay、Repeat、Spring、速度与 Reverse。
- 为 `runAnimation`、Decay 与 `AnimatableCore` 提供确定性 `MonotonicFrameClock`。
- 验证 Target 完成前的取消，确保不会强制终止状态。
- 验证自定义值/速度往返、稳定维度、阈值、零速度与数值精度。
- 覆盖欠阻尼、临界阻尼、过阻尼、安全上限、Bounds、快速 Retarget 与 Decay。
- 推进共享 Segment 前注册全部 Transition Channel；动态 Channel 被移除时测试完整集合时长替换。
- 测试显式 Seek Endpoint、反向 Seek、Restart Identity、Snap 折叠，以及上层 Owner 的单 Writer
  串行化。

模块测试覆盖这些物理分支、Overshoot、结构化结果、有符号 ARGB 速度、Converter 失败、
Reduced Motion、Transition Retarget、动态时长、显式 Seek/Restart 与 Snap 行为。

## 相关文档

- [ADR-0019：动画物理与所有权](../../architecture/decisions/0019-animation-physics-transition-and-inspection-ownership.md)
- [ADR-0020：分离动画值域与速度域](../../architecture/decisions/0020-separate-animation-value-and-velocity-domains.md)
- [动画模块](../viewcompose-animation/README.md)
- [Runtime 模块](../viewcompose-runtime/README.md)
- [源码文档与 API 注释标准](https://docs.viewcompose.com/project/api-documentation-quality)

完整生成参考位于
[`viewcompose-animation-core` API Tree](https://docs.viewcompose.com/api/viewcompose-animation-core/current/)。

## 兼容性说明

Phase 1 Alpha 硬切旧的带时长 Spring 和单值域 Converter/Result Surface。精确 Interval 使用时长
Spec，平衡 Motion 使用物理 `spring`，值与速度类型不同时使用 `AnimationConverter<T, V>`。
不存在 Deprecated Duration-Spring、单参数 Converter 或同域 `Animatable` Adapter。Android
Interop 属于 Host 模块，Composition 所有权属于 `viewcompose-animation`。
