---
translation_source: modules/viewcompose-animation-core/README.md
translation_source_hash: 7b0e14d7f310107783c86394f46f61ee650e0b931722c31854bfb0c90d754148
translation_status: current
---

# Animation Core 模块

`viewcompose-animation-core` 是 ViewCompose 动效的平台无关计时与采样引擎。它定义不可变动画
规格、Easing 与值转换、确定性时间线采样、协程驱动的帧循环、低层可变动画值，以及共享的
Transition Segment 协调。它不依赖 Android UI 或组合系统。

## 产物与稳定性

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-animation-core:0.1.0-alpha03")
}
```

- 稳定性：**Alpha**。时间归一化、重复、取消和 Transition Segment 行为已审查并测试；命名和
  高层组合集成在 Alpha 版本间仍可能演进。
- 平台：Kotlin/JVM，不依赖 Android Framework。
- `viewcompose-runtime` 会被传递暴露，因为 `MonotonicFrameClock` 出现在公开 Clock 与动画
  API 中。Kotlin 协程用于结构化取消。
- 应用通常通过 `viewcompose-animation` 间接获得它；自定义 Runtime、确定性采样、预览工具或
  平台无关测试可以直接依赖。

## 规格与 Easing

`AnimationSpec` 是不可变的“时间到进度”描述，不拥有 Clock、Coroutine 或值。内置系列包括：

- `tween`：固定时长、可选延迟与 `Easing` 曲线；
- `spring`：固定时长内确定且有界的阻尼振荡近似；
- `keyframes`：带时间戳的进度检查点，检查点间线性插值；
- `snap`：立即选择目标值；
- `repeatable`：有限次数的重启或交替循环；
- `infiniteRepeatable`：循环到驱动协程被取消。

```kotlin
val motion = repeatable(
    iterations = 2,
    animation = tween(
        durationMillis = 240,
        easing = EasingDefaults.FastOutSlowIn,
    ),
    repeatMode = RepeatMode.Reverse,
)
```

引擎把负延迟归一化为零，把非正有限时长归一化为一毫秒。零轮重复是例外：它的时长为零，
并保持起点值。Factory 会在不可变对象中保留调用方请求值；归一化发生在查询时长和采样时。

`EasingDefaults` 提供分配稳定的多项式曲线。`CubicBezierEasing` 支持自定义控制点，并对 x 轴
执行有界反解。Bézier x 坐标应保持在 `0f..1f`；构造函数不会拒绝非单调曲线。引擎会把最终
视觉进度限制在 `0f..1f`，包括 Spring 与 Easing 输出，因此当前 animation-core 不暴露视觉
过冲。

## 语义动效方案与减少动效

`MotionScheme` 提供五种不绑定组件或设计系统的语义时序角色：快速/默认效果、快速/默认空间
移动，以及强调空间移动。组件选择 `MotionRole`，不在结构 Recipe 中复制原始时长。这个不可变
方案不拥有 Clock 或动画 State，而是解析为已有 `AnimationSpec` 系列。

`ReducedMotionPolicy` 保持相同目标状态，同时把非必要移动替换为 `SnapSpec` 或缩短后的规格。
传达状态所必需的转场会缩短时长，而不会被隐藏。缩放会递归应用到 Tween 延迟、有界 Spring
时长、Keyframe 时长与检查点，以及 Repeat 的子规格。应用或集成根显式传入宿主的减少动效决定；
animation-core 不读取平台设置。

`MotionInterruptionPolicy.RetargetFromCurrent` 与 `viewcompose-animation` 的 Last-writer 行为一致。
`SnapToTarget` 是组件 Owner 策略：Owner 应立即选择目标，而不是启动 Runner。一个 Scheme 不会
启动相互竞争的循环。

## 确定性采样

`sampleAnimationValue` 在显式纳秒播放时间上求值，不拥有 Clock、Coroutine 或 State，因此是
Seek、测试、Transition Channel 与预览工具的首选原语：

```kotlin
val halfway = sampleAnimationValue(
    startValue = 20f,
    endValue = 100f,
    animationSpec = tween(durationMillis = 400, easing = EasingDefaults.Linear),
    converter = AnimationConverters.Float,
    playTimeNanos = 200_000_000L,
)
```

`animationDurationNanos` 会包含 Tween 延迟，Repeat 乘法会饱和而不是溢出，无限重复返回
`Long.MAX_VALUE`。`isAnimationFinished` 对无限重复始终返回 false。

每次采样都会通过 Converter 分配起点、终点和结果 Vector。对帧敏感的自定义 Runtime 应避免
不必要的包装分配，也不能把 Converter 用于阻塞或 I/O 工作。

## 值转换

`AnimationConverter<T>` 把领域值拆成独立插值的 `Float` 维度，再重建领域值。实现必须保持
稳定维度数、返回独立 Vector，并且不能保留传入 `fromVector` 的结果 Vector。

内置 Converter 覆盖 `Float`、`Int` 和打包 ARGB `Int`。整数重建向零截断。ARGB 按编码通道
独立插值，不执行 Gamma 校正，也不感知色彩空间。

```kotlin
data class Point(val x: Float, val y: Float)

val converter = object : AnimationConverter<Point> {
    override fun toVector(value: Point) = floatArrayOf(value.x, value.y)

    override fun fromVector(vector: FloatArray) = Point(vector[0], vector[1])
}
```

端点 Converter 维度不一致时，采样使用起点 Vector 的大小。缺少的终点维度保持对应起点维度，
额外终点维度被忽略。这只是恢复行为，维度不一致仍应视为 Converter 缺陷。

## 帧驱动执行与取消

`runAnimation` 等待 `MonotonicFrameClock`，并在调用方协程中把每个样本传给 `onValue`。有限
动画完成后会在帧循环外再发布一次精确终点，因此终点可能被观察两次。无限规格只通过取消退出。

取消不会强制写入目标值。根据 Frame Clock 行为，取消可能以协程取消异常传播，也可能在回调间
被观察并报告为 `AnimationRunResult.Cancelled`。Frame Clock 与回调异常原样传播。回调位于帧
路径中，必须保持短小。

`AnimatableCore` 保存最新样本，但有意不提供 Mutex、Mutation Priority 或 Coroutine Scope。
并发 `animateTo` 与 `snapTo` 会互相覆盖。高层代码必须串行化 Mutation，或在 Retarget 前取消并
等待旧 Job。取消后保留最后已发布的值。

`viewcompose-animation` 提供多数应用应使用的组合感知、Last-writer 语义 API。只有调用方已经
拥有结构化并发和 Frame Clock 时，才应直接使用 `AnimatableCore`。

## 多通道 Transition 协调

`TransitionCore<S>` 在多个动画 Channel 间协调逻辑端点和一条时间线。Transition Owner 按以下
顺序调用：

1. 目标状态变化时调用 `updateTarget`；
2. 每个 Channel 通过 `registerDuration` 注册归一化时长；
3. 使用 `updatePlayTime` 推进共享 Segment；
4. 时间达到最大时长时提交目标，或调用 `finishRunningSegment`。

最长 Channel 决定 Segment 时长，短 Channel 在自己的 Sampler 中提前稳定。运行中 Retarget 时，
下一逻辑 Segment 从旧目标开始，而不是从各 Channel 当前采样值开始；视觉连续性由高层 Channel
Owner 保证。`TransitionCore` 非线程安全，也不会启动或取消工作。

## 测试自定义动画代码

- 用 `sampleAnimationValue` 精确断言边界、延迟、重复和 Reverse Cycle；
- 测试 `runAnimation` 或 `AnimatableCore` 时提供确定性假 `MonotonicFrameClock`；
- 验证到达目标前取消，且终点不会被强制发布；
- 验证自定义 Converter 往返、稳定维度、缺失数据策略与数值精度；
- Transition 应先注册 Channel 再推进时间，并显式测试 Segment 中途 Retarget。

模块测试覆盖 Tween 完成与延迟、Reverse Repeat 终态、无限动画帧节拍、取消、ARGB 往返、最大
Channel 时长、Transition Retarget、语义角色解析与确定性减少动效替换。

## 相关文档

- [Runtime 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-runtime)
- [架构概览](https://docs.viewcompose.com/zh-CN/architecture/overview)
- [源码文档与 API 注释规范](https://docs.viewcompose.com/zh-CN/project/api-documentation-quality)
- [项目路线图](https://docs.viewcompose.com/zh-CN/project/roadmap)

完整生成式参考位于
[`viewcompose-animation-core` API 目录](https://docs.viewcompose.com/api/viewcompose-animation-core/current/)。

## 兼容性说明

`0.1.0-alpha03` 建立了有限时间归一化、Restart 与 Reverse Repeat、Frame Clock 驱动取消、逐维
Converter 和共享 Transition Segment 计时契约。这些契约有意保持平台无关；Android Interop
属于宿主模块，组合 Ownership 属于 `viewcompose-animation`。
