---
translation_source: modules/viewcompose-animation/README.md
translation_source_hash: 7341deba8283a2e7bdc6124275f826a93e32756608488a3a87c62735817e9ba5
translation_status: current
---

# Animation 模块

`viewcompose-animation` 把平台无关动画引擎集成到 ViewCompose State、组合 Effect、`Modifier`、
UI Node 发射与 Android View Renderer。它提供状态驱动值动画、命令式 Last-writer Mutation、
同步 Transition、无限 Channel、可见性/内容转场、测量尺寸动画和真实布局边界运动。

## 产物与稳定性

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="animation-module-dependency" sample_id="module.animation-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
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

{/* compiled-region source="viewcompose-animation/src/test/samples/com/viewcompose/animation/samples/AnimationSamples.kt" region="animation-target-as-state" sample_id="module.animation-target-as-state" build_target=":viewcompose-animation:compileDebugUnitTestKotlin" */}
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

{/* compiled-region source="viewcompose-animation/src/test/samples/com/viewcompose/animation/samples/AnimationSamples.kt" region="animation-animatable" sample_id="module.animation-animatable" build_target=":viewcompose-animation:compileDebugUnitTestKotlin" */}
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
        else -> Unit
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

`updateTransition(targetState)` 为多个派生 Channel 创建一个逻辑 Segment 和一条自主帧时间线。
每个 Channel 都会接收该 Segment 选定的稳定 `TransitionSegment<S>`，因此可以用类型安全方式选择
方向相关的 Timing，且每个 Segment 只求值一次：

{/* compiled-region source="viewcompose-animation/src/test/samples/com/viewcompose/animation/samples/AnimationSamples.kt" region="animation-transition" sample_id="module.animation-transition" build_target=":viewcompose-animation:compileDebugUnitTestKotlin" */}
```kotlin
val transition = updateTransition(
    targetState = if (expanded) PanelState.Expanded else PanelState.Collapsed,
    label = "panel",
)
val alpha = transition.animateFloat { state ->
    if (state == PanelState.Expanded) 1f else 0.6f
}
val height = transition.animateDp(
    transitionSpec = {
        if (isTransitioningTo(PanelState.Collapsed, PanelState.Expanded)) {
            spring(dampingRatio = 0.8f, stiffness = 240f)
        } else {
            tween(durationMillis = 180)
        }
    },
) { state ->
    if (state == PanelState.Expanded) 240.dp else 80.dp
}
```

首次组合停在初始目标。后续 Segment 开始时，每个 Channel 固定当前样本与新目标并注册时长。
完整已提交 Channel 集合中的最长时长决定 `currentState` 何时提交 `targetState`；较短 Channel
会 Clamp 在自己的终点。新增或移除调用位置都会重新计算该最大值，包括缩短总时长。Retarget
会取消旧自主 Effect，并让已有 Channel 从最新样本与保留的物理速度开始。Q3 `Transition`
通过原子 Snapshot 发布逻辑 State、Target、Running Flag、稳定 `segment` 与 Play Time。
`MutableTransitionState` 也通过同一边界镜像框架持有的 Current/Target/Idle 元组。

`animateValue(converter, transitionSpec, targetValueByState)` 是泛型 Q3 Channel。内置
`animateFloat`、`animateInt`、`animateColor` 与 `animateDp` 都委托给同一路径。类型化 Channel
的命名参数现已硬切为 `transitionSpec`；旧 `animationSpec` 名称没有兼容 Overload。无限 Spec
仍会在编译期被排除。

Gesture、Scrubber、Preview 或 Predictive Progress 需要持有控制权时，应绑定一个
`SeekableTransitionState<S>`，而不是调用 `updateTransition`：

{/* compiled-region source="viewcompose-animation/src/test/samples/com/viewcompose/animation/samples/AnimationSamples.kt" region="animation-seekable-transition" sample_id="module.animation-seekable-transition" build_target=":viewcompose-animation:compileDebugUnitTestKotlin" */}
```kotlin
val seekState = remember { SeekableTransitionState(PanelState.Collapsed) }
val transition = rememberTransition(seekState, label = "seekable panel")
val position = transition.animateValue(
    converter = pointConverter,
    transitionSpec = { tween(durationMillis = 600) },
) { state ->
    if (state == PanelState.Expanded) Point(96f, 32f) else Point(0f, 0f)
}

LaunchedEffect(command) {
    when (command) {
        Command.Preview -> seekState.seekTo(0.7f, PanelState.Expanded)
        Command.Commit -> seekState.animateTo(PanelState.Expanded)
        Command.Reset -> seekState.snapTo(PanelState.Collapsed)
        else -> Unit
    }
}
```

该 State 只接受一个活动 `rememberTransition` Binding 和一个 Mutation Writer。`seekTo` 会在
接管所有权前校验有限 `0f..1f` Fraction，取消并 Join 旧命令，把 Fraction 映射到最长已提交
Channel 时长，并以零物理速度采样全部 Channel。Seek Target 变化时，当前 Channel 样本会冻结为
新起点。Channel 新增或移除时会保留归一化 Fraction，并按新的最大时长重新采样。命令会为已
接受 Segment 的 Channel 提供两次 Frame 提交机会；若仍为零 Channel，则使用协调器的一纳秒
Fallback，不会无限等待。

`animateTo` 会退出 Seeking，并从样本值启动唯一一条自主 Frame Loop；Seeking 不推导物理速度，
因此交接时初速度为零。新的 Seek、Animation 或 Snap 会先取消并 Join 旧调用方，再发布状态。
`snapTo` 不使用 Frame，而是把 Current State、Target State 与两个 Segment Endpoint 原子折叠到
同一个 Idle 值。移除 Binding 会取消活动 Writer，并把未完成视觉样本保留为 Seeking State。

Seek State 不持有 Coroutine Scope，不会自动 Save，也不负责提交或回滚 Navigation。Navigation
Owner 可以把 Predictive Back Progress 交给 `seekTo`，但 Back Stack Transaction 以及提交或
取消后的 `animateTo`/`snapTo` 选择仍由它负责。Label 仍是诊断元数据，不改变 Identity。

## 可选动画时间线检查 Port

`viewcompose-animation` 提供 Q3 `com.viewcompose.animation.tooling` 契约，供可选下游开发工具
使用。`AnimationTimelineSource` 提供已提交 `Transition` 的一份不可变有界 Snapshot；
`AnimationTimelineTooling` 及其 Lifecycle Registration 决定当前显式请求是否选中了该
Transition。运行时最多从冻结的进程级内存 Slot 读取一个 Provider。下游 Tooling 制品可以在
首个 Transition 读取端口前，通过 Q3 `installAnimationTimelineTooling` 集成 Hook 完成 Android
Component 初始化。同一实例重复安装是幂等的；多个不同的早期 Provider 会禁用该端口，晚于首次
读取的安装会被忽略。该路径不执行 Classpath 扫描、文件 I/O 或 Android Service 查找；缺失、
歧义、Provider 失败与 Disposal 失败仍作为诊断 No-op 处理。

Snapshot 包含进程生命周期 Transition Identity、有界 Label、安全逻辑 State Summary、Segment
Version/Time、Running/Idle/Interrupted 状态，以及最多 32 个已提交 Channel。内置 Float、可被
Float 精确表示的 Int、`UiDp` 与编码 ARGB Channel 会公开有界数值 Component。自定义 Value
Domain 会有意返回 `null`，不会保留应用对象或调用应用 Formatter。每个 Channel 会报告确定性
Runtime Name、有限 Spec Family、自身 Duration、安全 Velocity、Completion 与物理
`DurationLimitReached` Terminal Condition。

Animation 产物不含具体 Provider、Android Receiver、文件格式、Studio API、Thread、Poll 或
Frame Callback。没有可选 Provider 时不会创建 Source Projection，只承担不可变诊断 Identity
元数据，以及已接受 Transition Publication 上的 Nullable Registration Check。可选产物存在时
可以在首次请求前弱持有中立 Source，使已经组合的 Transition 仍可发现。具体 Receiver 会拒绝
不可调试进程；只有带 Nonce 的有界请求选中该 Identity 时才会 Snapshot、Serialize 或写入报告。
该 Port 严格只读，不支持远程 Seek 真机应用。

初始化边界由
[ADR-0022](../../architecture/decisions/0022-in-memory-development-tooling-installation.md) 定义。

## InfiniteTransition 无限动画

`rememberInfiniteTransition` 管理由 `animateFloat`、`animateInt`、`animateColor`、`animateDp` 或
泛型 `animateValue` 声明的持续重复 Channel：

{/* compiled-region source="viewcompose-animation/src/test/samples/com/viewcompose/animation/samples/AnimationSamples.kt" region="animation-infinite-transition" sample_id="module.animation-infinite-transition" build_target=":viewcompose-animation:compileDebugUnitTestKotlin" */}
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

`AnimatedVisibility` 在一个 Content 生命周期内协调 Alpha、实测 Reveal、按实测尺寸计算的
Slide、带轴心的视觉 Scale 与后代编排：

{/* compiled-region source="viewcompose-animation/src/test/samples/com/viewcompose/animation/samples/AnimationSamples.kt" region="animation-visibility" sample_id="module.animation-visibility" build_target=":viewcompose-animation:compileDebugUnitTestKotlin" */}
```kotlin
AnimatedVisibility(
    visible = showDetails,
    enter = fadeIn(tween(durationMillis = 160)) +
        slideInHorizontally(
            from = SlideDirection.Start,
            distanceFraction = 0.5f,
        ) +
        scaleIn(
            initialScale = 0.9f,
            transformOrigin = TransformOrigin(0f, 1f),
        ) +
        expandVertically(alignment = BoxAlignment.BottomStart),
    exit = shrinkVertically(alignment = BoxAlignment.TopEnd) +
        scaleOut(
            targetScale = 0.92f,
            transformOrigin = TransformOrigin(1f, 0f),
        ) +
        slideOutHorizontally(towards = SlideDirection.End) +
        fadeOut(tween(durationMillis = 120)),
) {
    Text("Parent transition running: ${transition.isRunning}")
    AnimatedEnterExit(
        enter = slideInVertically(from = SlideDirection.Down),
        exit = slideOutVertically(towards = SlideDirection.Up),
    ) {
        Text("Descendant shares the parent clock")
    }
}
```

首次组合为稳定态，不播放 Enter。后续 Exit 会为绘制保留内容，直到父级和后代的全部 Channel
完成，再移除内容子树；接受 Exit 目标时，保留子树会立即失去 Pointer、焦点和无障碍所有权。空
Host 会以零尺寸身份锚点继续挂载，因此后续可见性变化不会重建其后的无 Key 原生同级 View，也
不会截断这些 View 的按压态和焦点状态。被中断的 Enter/Exit 会让每个 Channel 从当前样本
Retarget。每个新 Segment 都使用实时归零后的 Play Time 采样，不会误用固定组合快照中上一
Segment 的结束时间。

`slideIn`/`slideOut` 接受完整实测宽度或高度的非负有限比例。逻辑 Start/End 按 Segment 开始时
捕获的布局方向解析，Up/Down 保持物理方向。Expand/Shrink 会固定声明的 `BoxAlignment` 边缘，
并把绘制裁剪到动态 Bounds；`scaleIn`/`scaleOut` 使用显式 `TransformOrigin`。Translation 与
视觉 Scale 不改变父级测量。当 Host 是 `Row` 或 `Column` 的直接 Child 时，周围 Item 间距会
跟随对应的宽度或高度 Reveal 进度，因此生命周期端点不会在单帧内突然插入或移除一整段间距。

Tree-builder 默认影响双轴；`RowScope` 默认影响宽度；`ColumnScope` 默认影响高度。Transition
的 `+` 会拼接 Element，重复 Channel 由最后一个适用的 Alpha、尺寸、Slide 或 Scale Element
决定。`AnimatedVisibilityScope.transition` 是所属 Boolean `Transition`；作用域内的
`AnimatedEnterExit` 会把后代 Channel 加入同一个协调器，不会启动第二条 Frame Loop。先应用
后代局部 Alpha/Translation/Scale/Reveal，再应用父级 Transform，最后应用父级裁剪。Motion
Policy 把有限 Spec 解析为 `snap` 时，显隐端点提交与 Exit 内容移除仍然正确。

Content Receiver 现在是 Q3 `AnimatedVisibilityScope`，不再是 `BoxScope`。这是为了让共享
Transition 所有权具备类型安全而进行的有意硬切。普通 Builder 调用仍可直接使用；依赖
`BoxScope.align` 的调用方需要显式发射 `Box`，并在其中应用 Child Alignment。Slide/Scale
Helper、Transition Element、Scope、后代 Host、Renderer Transport 与已编译
`richVisibilityTransitionsSample` 共同构成一个 Q3 API Family。

需要在调用外设置 `targetState` 并观察 `currentState` 或 `isIdle` 时，使用
`MutableTransitionState<Boolean>`。一个对象只能绑定一个活动 Host。本版本中，在 Host 首次组合
前改变目标不会播放首次 Enter；需要该动效时，应先组合隐藏态，再改变目标。

## AnimatedContent 与 Crossfade

`AnimatedContent` 是按 Key 完整替换内容的 API。它的类型化 Transition Scope 会为已接受的
Initial/Target 状态对选择一份 `ContentTransform`，并可组合 Fade、按实测 Item 尺寸计算的
Slide、Scale Origin、绘制顺序与可选 `SizeTransform`：

{/* compiled-region source="viewcompose-animation/src/test/samples/com/viewcompose/animation/samples/AnimationSamples.kt" region="animation-content" sample_id="module.animation-content" build_target=":viewcompose-animation:compileDebugUnitTestKotlin" */}
```kotlin
AnimatedContent(
    targetState = page,
    contentKey = { it.id },
    transitionSpec = {
        val forward = targetState.index > initialState.index
        val enter = fadeIn() + slideIntoContainer(
            from = if (forward) ContentSlideDirection.End else ContentSlideDirection.Start,
            distanceFraction = 0.35f,
        ) + scaleIn(initialScale = 0.96f)
        val exit = fadeOut() + slideOutOfContainer(
            towards = if (forward) ContentSlideDirection.Start else ContentSlideDirection.End,
            distanceFraction = 0.2f,
        )
        (enter togetherWith exit) using SizeTransform(clip = true)
    },
) { state ->
    Page(state)
}
```

`contentKey` 是子树 Identity。相同 Key 只 Patch 一棵保留树，不选择替换 Transition。不同 Key
最多保留一棵 Outgoing 与一棵 Incoming 完整树；A→B→C 中断会从 B 最后提交的视觉样本提升 B，
只释放一次 A，并保留 B 的 Keyed 后代 State。nullable Key 与 State 遵守同一规则。

两棵树使用同一组 Incoming 父约束测量。非空 `SizeTransform` 从最后提交的 Host 尺寸插值到
Incoming 尺寸并控制裁剪；`null` 使用当前 Child 最大尺寸。Slide 距离是参与 Item 实测轴尺寸的
非负有限比例，Start/End 根据该 Segment 捕获的布局方向解析。按 Callback 计算 Offset 仍未支持；
通用 Visibility Slide/Scale 与后代编排由上文独立的 `AnimatedVisibility` Family 提供。

替换事务提交后，只有 Incoming Content 拥有 Pointer Input、焦点遍历和无障碍能力。Outgoing
Content 在全部 Channel 停稳前只参与绘制。变化请求只会在一棵候选树成功提交后接受，因此
Renderer 失败不会发布候选 Identity、焦点所有权、后代 Effect 或几何。Host Dispose 会取消共享
Frame Loop，并只释放一次全部保留树。

`Crossfade` 保留为更小的纯 Alpha 契约。转场时它会以最后提交状态与最新目标调用 Content，叠放
两棵 Fill-size 子树，并在旧内容透明后移除它。中途到达的新目标会在现有进度上替换 Incoming。
不需要 Content Key、实测尺寸、逐状态对 Transform、Slide、Scale 或显式交互转移时使用它。

## animateContentSize 与原生布局成本

`Modifier.animateContentSize` 把有限 Core Spec 序列化到 Renderer。Renderer 在目标 Node 外
插入一个合成原生 Host，并把父布局 Element 移到 Host。时长 Spec 使用 Android
`ValueAnimator`；物理 Spring 使用共享 Animation Core Solver，并在 Retarget 时保留宽高速度：

{/* compiled-region source="viewcompose-animation/src/test/samples/com/viewcompose/animation/samples/AnimationSamples.kt" region="animation-content-size" sample_id="module.animation-content-size" build_target=":viewcompose-animation:compileDebugUnitTestKotlin" */}
```kotlin
Column(
    modifier = Modifier.animateContentSize(
        spring(dampingRatio = 0.75f, stiffness = 240f),
    ),
) {
    // Content whose measured size changes.
}
```

首次测量直接应用。后续变化从进行中尺寸 Retarget，父约束仍会限制结果。每帧都会请求 Android
Layout，Wrapper 还会增加一层 View；不要无差别应用到大型列表。无限尺寸 Spec 会在编译期被
拒绝，因为 Layout Animation 必须收敛。

内置 Easing 与 Cubic Bézier 控制点可以跨 Renderer 边界。未知自定义 Easing 会降级为
`FastOutSlowIn`。一个 Modifier Chain 中存在多个 `animateContentSize` 时，最后一个规格生效。

## animateBounds 与真实布局几何

`Modifier.animateBounds` 会在节点的直接 ViewCompose 布局父级中，对逻辑 Start/End 与 RTL
解析后的真实位置和尺寸进行动画。每一帧都会提交真实 Android 矩形，而不是只应用绘制平移或缩放，
因此可见区域、Pointer、Focus 与 Accessibility 几何保持一致：

{/* compiled-region source="viewcompose-animation/src/test/samples/com/viewcompose/animation/samples/AnimationSamples.kt" region="animation-bounds" sample_id="module.animation-bounds" build_target=":viewcompose-animation:compileDebugUnitTestKotlin" */}
```kotlin
Button(
    text = "Move and resize",
    onClick = onTargetClick,
    modifier = Modifier
        .width(if (expanded) 204.dp else 152.dp)
        .height(if (expanded) 58.dp else 48.dp)
        .align(if (expanded) BoxAlignment.BottomEnd else BoxAlignment.BottomStart)
        .animateBounds(tween(durationMillis = 900)),
)
```

首次接受的布局直接稳定在端点。目标变化只执行一次目标测量；Duration Spec 从当前矩形以零速度
Retarget，物理 Spring 则保留四条边的采样速度。属性帧会复用目标测量。父级滚动移动完整局部坐标系；
Reparent 会结束旧 Owner 的运动，并让目标 Owner 从稳定布局开始。Detach 和 Lazy Item 跨 Owner
复用也会在下一次布局前取消并清空旧运动。

Renderer 会把同一 Chain 上的尺寸、Margin、Parent Data、Alignment、Offset、Visibility 与
z-index 提升到一个透明外层 Host；Drawing、Content、Input、Focus 与 Semantics 仍留在 Child。
重复 `animateBounds` 采用最后一个规格。同时在一个节点使用 `animateBounds` 与
`animateContentSize` 会在原生 Mutation 前被拒绝，因为二者都会拥有尺寸。Host 会按采样矩形裁剪
内容，并增加一层原生 View。需要禁用布局运动时，应使用 `snap()` 或解析为 Snap 的 Motion Policy。

## 测试

- 命令式动画与取消测试使用确定性 Frame Clock；
- 分别断言物理结束原因、终止速度、Bounds、Decay 方向与快速 Retarget 速度连续性；
- 分开验证首次组合与后续目标变化；
- 验证完成前 Retarget，确保过期 Job 无法发布；
- Transition 在同一组合 Pass 声明所有 Channel，并断言最长时长控制逻辑完成；
- 验证可见性内容保留到 Exit 终帧；
- 验证 Animated Content 的相同/不同 Key、nullable Target、RTL Slide、中点中断、移除 Effect、
  Input/Focus/Accessibility 转移、Rollback 与 Host Dispose；
- 分开测试兼容与不兼容 Shape 转场，包括降级归因；
- Wrapper 位置、约束或 Modifier 路由相关的尺寸动画应在 Android Renderer 测试；Animation 模块
  单元测试只验证契约序列化。
- Bounds 动画需验证真实父级放置、RTL、运行中 Retarget、Detach/Reuse、Input 与 Accessibility
  几何、Rollback 和目标测量次数；仅做视觉平移不是可接受的替代方案。

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
`animateContentSize` 共享同一物理 Solver，也不再接受无限 Spec。新增的 `animateBounds` 只作用于
直接父级局部坐标，暂不提供 Shared 或跨 Owner 视觉转场。不存在 Deprecated 兼容 Overload。
共享时长 Transition、持续 Channel、感知 Exit 的可见性生命周期和纯 Alpha 内容替换保留既有
所有权。相似 API 名称不代表完整 Jetpack Compose Animation 对齐；以上差异仍是公开契约。
