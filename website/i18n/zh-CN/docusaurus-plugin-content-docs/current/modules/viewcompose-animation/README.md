---
translation_source: modules/viewcompose-animation/README.md
translation_source_hash: f9ef9e39916df54685721eb92826d80557989c57c25cdea06fc2a577bff6c68e
translation_status: current
---

# Animation 模块

`viewcompose-animation` 把平台无关动画引擎集成到 ViewCompose State、组合 Effect、`Modifier`、
UI Node 发射与 Android View Renderer。它提供状态驱动值动画、命令式 Last-writer Mutation、
同步 Transition、无限 Channel、可见性/内容转场和测量尺寸动画。

## 产物与稳定性

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-animation:0.1.0-alpha04")
}
```

- 稳定性：**Alpha**。State Ownership、取消、Retarget、内容保留和 Renderer 交接已有明确契约；
  API 有意小于 Compose Animation，并可能在 Alpha 版本间扩展。
- 平台：Android 库，最低 SDK 24。
- Animation Core、Runtime、UI Contract 和 UI Foundation 会被传递暴露，因为它们的 State、
  Clock、Modifier、单位和 Builder 类型出现在公开动画 API 中。
- `viewcompose-animation-core` 也可以脱离 Android UI Host 独立使用。
- Android `View` 属性动画 Interop 属于 `viewcompose-host-android`，不属于本模块。

## 组合动画环境

组合所有的 API 使用 `LocalMonotonicFrameClock` 提供帧时间戳，使用
`LocalAnimationCoroutineContext` 选择 Dispatcher 与其他 Context。动画 Context 不能包含
`Job`：`LaunchedEffect` 已提供结构化父 Job，额外 Job 会使取消脱离组合。

Frame Clock 或动画 Context 变化会重启相关 Effect。动画调用离开组合时会被取消。样本通过
ViewCompose 可观察 State 写入，并使读取方失效。

设计系统组件可以先解析语义 `MotionScheme`，再调用这些 API。Scheme 仍是 animation-core 的
不可变策略；`Animatable`、Target-as-state API 和 `Transition` 仍是唯一由组合所有的 Runner。
快速 Retarget 因而继续使用既有 Last-writer 取消与过期帧拒绝语义，不创建组件私有动画循环。

## Shape 转场与降级

`interpolateUiShape(start, end, fraction)` 只在对应 Corner 使用相同 Family，且 Size 同为 Absolute
或同为 Relative 时插值。结果会以 `UiShapeInterpolationMode.Compatible` 标记这条路径。Family
或 Size 类型不匹配时，中点前选择起点、中点及之后选择终点，并用 `DiscreteFallback` 供诊断归因。

该 Helper 不拥有 Clock、View 或 State。应通过 `Animatable`、`animateFloatAsState` 或 `Transition`
驱动有限进度。它有意不提供任意 Path Morph；无法证明几何兼容的组件会保留确定性静态/离散降级，
且不改变 Bounds、输入所有权或 Semantics。

## Target-as-state 动画

`animateFloatAsState`、`animateIntAsState`、`animateColorAsState`、`animateDpAsState` 和泛型
`animateValueAsState` 把变化目标转换成稳定的组合所有 `State<T>`：

```kotlin
val alpha = animateFloatAsState(
    targetValue = if (enabled) 1f else 0.5f,
    animationSpec = tween(durationMillis = 180),
)
```

首次组合立即暴露目标值。后续目标、规格、Converter、Clock 或 Context 变化会取消旧 Effect，
并从最后发布值重启。这组 API 没有命令式取消句柄或完成回调；需要命令、停止或 Mutation 仲裁时
应使用 `Animatable`。

所有 Target-as-state API 都接受 `FiniteAnimationSpec`；无限 Spec 会在编译期失败。它们共享
`AnimatableCore` Mutation 与物理采样，不持有第二套 Runner。整数样本向零截断。颜色按编码
ARGB Channel 插值，不执行 Gamma 校正。`UiDp` 插值密度无关
数字而不是解析像素，因此仅 Density 变化不会重启动画。自定义 Converter 必须保持稳定维度，
并应在组合间保持实例稳定。

## 命令式 Animatable

`Animatable<T, V>` 暴露 `value`、类型化 `velocity`、`targetValue`、`isRunning` 和稳定可观察
`asState`，并接受挂起命令：

```kotlin
val progress = rememberAnimatable(
    initialValue = 0f,
    converter = AnimationConverters.Float,
)

LaunchedEffect(command) {
    when (command) {
        Command.Open -> progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 220f),
        )
        Command.Close -> progress.animateDecay(AnimationVelocity(-2.4f))
        Command.Stop -> progress.stop()
    }
}
```

每个 `animateTo`、`animateDecay`、`snapTo` 和 `stop` 都是一项 Mutation。来自其他 Coroutine
Job 的新 Mutation 会取消旧 Job，过期帧会被 Mutation Identity 拒绝。省略可空的
`initialVelocity` 时，物理 `animateTo` 从同一个原子值/速度 Snapshot Retarget；显式
`AnimationVelocity<V>` 只替换其中捕获的速度。无效替代请求会在 Mutation 所有权变化前被拒绝，
因此不会取消当前动画。`snapTo` 立即发布；`stop` 保留当前值；两者都把速度重置为零。取消和失败
保留最新样本，并把目标重置为该值。正常结束返回 `AnimationResult<T, V>`，原因是
`Finished`、`BoundReached` 或
`DurationLimitReached`；取消仍抛出异常。
Q3 `Animatable` 契约会一起发布 Frame-driven Animation 开始时的 Target/Running，并一起发布
完成时保留的 Target/Idle；逐帧 Sample 仍是独立的 Value Commit。`snapTo` 与 `stop` 则只发布
一次原子 Final Idle Snapshot，不产生瞬时 Running State。无效构造或 Snap 输入会在任何状态或
Mutation 所有权改变前失败。

`updateBounds(lowerBound, upperBound)` 安装各分量闭区间 Value Bound。运行中的 Spring 或 Decay
会在发布前 Clamp Crossing Sample、把速度清零并返回 `BoundReached`。Idle Update 或之后的
`snapTo` 会立即 Clamp。Density、RTL 与 Gesture Axis 转换仍由构造 `V` 的调用方负责。

`rememberAnimatable` 只在首次创建时使用 `initialValue`。Converter 变化会创建新实例；只改变
`initialValue` 不会重置。当前 Frame Clock 每轮组合都会重新绑定。直接构造可以传显式 Clock；
没有 Clock 时只能使用 `snapTo` 和 `stop`，`animateTo` 会报告配置错误。

## 共享状态 Transition

`updateTransition(targetState)` 为多个派生 Channel 创建一个逻辑 Segment 和一条帧时间线：

```kotlin
val transition = updateTransition(
    targetState = if (expanded) PanelState.Expanded else PanelState.Collapsed,
    label = "panel",
)
val alpha = transition.animateFloat { state ->
    if (state == PanelState.Expanded) 1f else 0.6f
}
val height = transition.animateDp(
    animationSpec = { spring(dampingRatio = 0.8f, stiffness = 240f) },
) { state ->
    if (state == PanelState.Expanded) 240.dp else 80.dp
}
```

首次组合停在初始目标。后续 Segment 开始时，每个 Channel 固定当前样本与新目标并注册时长。
最长 Channel 决定 `currentState` 何时提交 `targetState`；短 Channel 会停在自己的终点。
Retarget 会取消旧帧 Effect，并让已有 Channel 从最新样本开始。Q3 `Transition` 在每次 Target
或 Frame 更新时，通过一次 Snapshot Transaction 发布逻辑 State、Target、Running Flag、
Segment Identity、Endpoint 与 Play Time。`MutableTransitionState` 也通过同一原子边界镜像
框架持有的 Current/Target/Idle 元组。

Channel Factory 当前不接收 Segment 对象；它为 Channel 提供一个规格，并把逻辑状态映射为
`Float`、`Int`、打包 ARGB 或 `UiDp`。Label 为未来诊断保留，不改变 Identity。

## InfiniteTransition 无限动画

`rememberInfiniteTransition` 管理由 `animateFloat`、`animateInt`、`animateColor`、`animateDp` 或
泛型 `animateValue` 声明的持续重复 Channel：

```kotlin
val pulse = rememberInfiniteTransition(label = "pulse")
val scale = pulse.animateFloat(
    initialValue = 0.9f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 600),
        repeatMode = RepeatMode.Reverse,
    ),
)
```

每个调用位置拥有自己的 State 与 Effect。Reverse 在 Cycle 间交换端点；Restart 重新发布初值。
相同端点不会等待帧。端点、规格、Clock 或 Context 变化会从新传入的初值重启，而不是从旧样本
继续。只改变自定义 Converter 不是重启 Key，因此 Converter 实例必须保持稳定。

无限 Channel 会运行到离开组合。对仍处于组合中的屏外或不可见内容应避免使用；不需要持续动效
时优先使用有限状态驱动动画。

## AnimatedVisibility 可见性动画

`AnimatedVisibility` 在控制内容生命周期的同时组合 Alpha 与测量尺寸 Channel：

```kotlin
AnimatedVisibility(
    visible = showDetails,
    enter = fadeIn(tween(durationMillis = 160)) + expandVertically(),
    exit = shrinkVertically() + fadeOut(tween(durationMillis = 120)),
) {
    Text("Details")
}
```

首次组合为稳定态，不播放 Enter。后续 Exit 会保留内容到所有 Channel 完成，再移除内容子树。
空 Host 会以零尺寸身份锚点继续挂载，因此后续可见性变化不会重建其后的无 Key 原生同级 View，
也不会截断这些 View 的按压态和焦点状态。被中断的 Enter/Exit 从当前 Alpha 与尺寸样本 Retarget。
每个新 Segment 都使用实时归零后的 Play Time 采样，不会误用固定组合快照中上一 Segment 的结束
时间。尺寸动画会按动态 Bounds 裁剪内容。当 Host 是 `Row` 或 `Column` 的直接 Child 时，周围的
Item 间距会跟随对应的宽度或高度进度，因此生命周期端点不会在单帧内突然插入或移除一整段间距。

Tree-builder 默认影响双轴；`RowScope` 默认影响宽度；`ColumnScope` 默认影响高度。Transition
的 `+` 会拼接 Element，重复 Channel 由最后一个适用 Fade 或尺寸 Element 决定。

需要在调用外设置 `targetState` 并观察 `currentState` 或 `isIdle` 时，使用
`MutableTransitionState<Boolean>`。一个对象只能绑定一个活动 Host。本版本中，在 Host 首次组合
前改变目标不会播放首次 Enter；需要该动效时，应先组合隐藏态，再改变目标。

## Crossfade 转场

`Crossfade` 实现仅 Alpha 的转场。转场时，它会分别以最后提交状态和最新目标调用
Content，把两个 Fill-size 子树叠放，并在旧内容透明后通过组合后 Side Effect 移除旧内容。
不同内容 Identity 需要独立后代 State 时，应以逻辑状态加 Key。

Fade 中途到达新目标时，Incoming Content 会在现有进度上替换，而不是从零重启；最后提交状态
仍为 Outgoing。nullable 状态通过显式 Displayed State Wrapper 保留，生命周期与非空状态一致。

原 `AnimatedContent` 名称会承诺比实现更广的转场语义，因此已移除。`Crossfade` 不提供 Content
Key、Transition Scope、尺寸变换、Slide 动效或逐状态对规格。

## animateContentSize 与原生布局成本

`Modifier.animateContentSize` 把有限 Core Spec 序列化到 Renderer。Renderer 在目标 Node 外
插入一个合成原生 Host，并把父布局 Element 移到 Host。时长 Spec 使用 Android
`ValueAnimator`；物理 Spring 使用共享 Animation Core Solver，并在 Retarget 时保留宽高速度：

```kotlin
Column(
    modifier = Modifier.animateContentSize(
        spring(dampingRatio = 0.75f, stiffness = 240f),
    ),
) {
    // 测量尺寸会变化的内容。
}
```

首次测量直接应用。后续变化从进行中尺寸 Retarget，父约束仍会限制结果。每帧都会请求 Android
Layout，Wrapper 还会增加一层 View；不要无差别应用到大型列表。无限尺寸 Spec 会在编译期被
拒绝，因为 Layout Animation 必须收敛。

内置 Easing 与 Cubic Bézier 控制点可以跨 Renderer 边界。未知自定义 Easing 会降级为
`FastOutSlowIn`。一个 Modifier Chain 中存在多个 `animateContentSize` 时，最后一个规格生效。

## 测试

- 命令式动画与取消测试使用确定性 Frame Clock；
- 分别断言物理结束原因、终止速度、Bounds、Decay 方向与快速 Retarget 速度连续性；
- 分开验证首次组合与后续目标变化；
- 验证完成前 Retarget，确保过期 Job 无法发布；
- Transition 在同一组合 Pass 声明所有 Channel，并断言最长时长控制逻辑完成；
- 验证可见性内容保留到 Exit 终帧；
- 分开测试兼容与不兼容 Shape 转场，包括降级归因；
- Wrapper 位置、约束或 Modifier 路由相关的尺寸动画应在 Android Renderer 测试；Animation 模块
  单元测试只验证契约序列化。

## 相关文档

- [Animation Core 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-animation-core)
- [Runtime 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-runtime)
- [UI Foundation 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-ui-foundation)
- [Renderer 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-renderer-android)
- [架构概览](https://docs.viewcompose.com/zh-CN/architecture/overview)
- [源码文档与 API 注释规范](https://docs.viewcompose.com/zh-CN/project/api-documentation-quality)

完整生成式参考位于
[`viewcompose-animation` API 目录](https://docs.viewcompose.com/api/viewcompose-animation/current/)。

## 兼容性说明

Phase 1 Alpha 硬切固定时长 Spring 与单值域 `Animatable<T>` Surface。调用方改用物理
`spring`、`Animatable<T, V>`、类型化速度、Decay、Bounds 和结构化结果。
`animateContentSize` 共享同一物理 Solver，也不再接受无限 Spec；不存在 Deprecated 兼容
Overload。共享时长 Transition、持续 Channel、感知 Exit 的可见性生命周期和纯 Alpha 内容替换
保留既有所有权。相似 API 名称不代表完整 Jetpack Compose Animation 对齐；以上差异仍是公开契约。
