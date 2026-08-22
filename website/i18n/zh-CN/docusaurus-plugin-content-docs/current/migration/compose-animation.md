---
translation_source: migration/compose-animation.md
translation_source_hash: dc492d26b0db388d65727e42a71e3b802a5e2fc5bb00f71496cd9e70e24e575e
translation_status: current
---

# 迁移 Compose 动画

本文对比 Jetpack Compose Animation、当前 ViewCompose 动画版本和已经接受的扩展契约。
它不是源码兼容承诺。只有生命周期、计时、几何与中断规则一致时，相似名称才表示同一概念。

最后验证日期：**2026-08-22**

复核责任人：**`viewcompose-animation-core`、`viewcompose-animation`、Android Renderer、
Navigation、Preview 与 Studio 工具的维护者**

## 基线与证据限制

当前 ViewCompose 目标为：

| 产物 | 版本 | 当前职责 |
| --- | --- | --- |
| `viewcompose-animation-core` | `0.1.0-alpha04` | 平台中立的时长采样、Converter、运动策略与 Transition 协调 |
| `viewcompose-animation` | `0.1.0-alpha04` | 组合所有的状态动画、Transition、可见性、Crossfade 与内容尺寸动画 |

上游稳定语义基线是 2026-08-12 发布的 Compose Animation `1.12.0`。验证依据为官方
[Compose Animation 发布说明](https://developer.android.com/jetpack/androidx/releases/compose-animation)、
[animation-core API 参考](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/package-summary)、
[`Animatable` 参考](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/Animatable)、
[`SeekableTransitionState` 参考](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/SeekableTransitionState)
及[共享元素指南](https://developer.android.com/develop/ui/compose/animation/shared-elements)。

仓库中的可执行 Compose 依赖仍为 `1.7.8`。Compose `1.12.0` 还要求 compile SDK 37 和
AGP 9.2，而本仓库当前使用 compile SDK 36 与 AGP 8.13.2。因此：

1. Android 官方文档确定 `1.12.0` 的语义对比；
2. 仓库源码、单测、可编译 Sample、Demo、Preview 与设备测试确定当前 ViewCompose 行为；
3. 本地 Compose `1.7.8` Fixture 不能证明 `1.12.0` 语义等价。

[ADR-0019](../architecture/decisions/0019-animation-physics-transition-and-inspection-ownership.md)
冻结了目标架构与 API 质量等级。下表标记为 **Planned（已规划）** 的内容是已接受设计，
不是可用 API；在对应阶段实现、写入所属模块文档并发布前，应用迁移仍应视其为不支持。

## 能力矩阵 {/* #capability-matrix */}

| 关注点 | Compose `1.12.0` 语义 | 当前 ViewCompose 状态 | 迁移决策 |
| --- | --- | --- | --- |
| Tween、Keyframes、Snap、Repeat | 时长与重复 Spec | **Supported（支持）**，但 Keyframe/Repeat Surface 更窄 | 移植前复核尚不支持的 Start Offset、Spline Keyframe 与 Path Easing |
| 物理 Spring | 基于阈值求解，并提供值与速度 | **Unsupported（不支持）**；当前 `SpringSpec` 是会 Clamp 的固定时长近似 | 不要把 Compose Spring 参数移入当前 API；Phase 1 会硬切近似模型 |
| `Animatable` 变更所有权 | 后一个变更取消前一调用；完成返回结果状态 | **Partially supported（部分支持）**；已有 Last Writer 与取消，但没有速度、结果、Decay 与 Bounds | 保留结构化协程所有权；需要手势速度或结束原因时等待 Phase 1 |
| Decay 与 Fling 接力 | Decay Spec 和速度延续 | **Unsupported（不支持）** | Phase 1 前继续让现有 Gesture/平台 Owner 持有 Fling |
| Target-as-state 动画 | 状态驱动的类型化动画 | **Supported（支持）**泛型值、Float、Int、编码 ARGB 与 `UiDp` | 颜色按编码 Channel 插值，不感知色彩空间 |
| `Transition` | 共享状态 Segment、泛型 Channel 与 Seek | **Partially supported（部分支持）**；已有一个自主 Timeline 与四种内置 Channel | Phase 4 增加公开泛型、Segment-aware 与 Seekable 控制 |
| `AnimatedVisibility` | Enter/Exit 代数、Slide、Scale 与后代编排 | **Partially supported（部分支持）**；已有 Fade 与测量尺寸行为 | Phase 3 增加 Slide、Scale、Transform Origin、Scope 与后代 Enter/Exit |
| `AnimatedContent` | Keyed 出入内容替换与 Content Transform | **Unsupported（不支持）**；ViewCompose 已把旧的纯 Alpha Surface 明确改名为 `Crossfade` | 仅需 Alpha 替换时使用 `Crossfade`；Phase 2 负责完整替换 |
| 内容尺寸动画 | Layout 尺寸变化 | **Supported（支持）**，使用 Android Renderer Wrapper | 重新验证父级 Constraint 与 Wrapper 位置；Phase 5 不替代这个小 API |
| Bounds 动画 | 跨 Layout 坐标变化的位置与尺寸 | **Unsupported（不支持）** | Phase 5 增加真实 Layout 几何；不要用 Draw Translation 模拟可交互 Bounds |
| 共享元素/Bounds | Scope 内配对与 Overlay 运动 | **Unsupported（不支持）** | Phase 6 增加单 Session 配对及 Navigation 集成；跨 Window 仍排除 |
| Timeline 检查与 Seek | 工具可观察并控制合格动画状态 | **Unsupported（不支持）** | Phase 7 增加按请求 Preview 工具；生产产物保持不活跃且无依赖 |

## Spring 硬切 {/* #the-spring-hard-cut */}

当前 API 接受名义时长：

```kotlin
spring(
    dampingRatio = 0.8f,
    stiffness = 250f,
    durationMillis = 550,
)
```

它不具备 Compose Spring 语义。当前实现把规范化时间映射到阻尼曲线，把进度 Clamp 到
`0f..1f`，并在指定时长结束；它无法表达真实 Overshoot、速度、平衡或手势延续。

Phase 1 会删除该签名，不保留 Deprecated Overload 或 Alias。迁移必须二选一：

- 产品行为拥有精确时长时，使用 `tween(durationMillis = ..., easing = ...)` 或
  `keyframes(...)`；
- 需要物理平衡、Overshoot、中断速度与阈值结束时，使用新的
  `spring(dampingRatio = ..., stiffness = ...)`。

只删除 `durationMillis` 不是机械迁移。旧参数进入的是另一套方程，因此必须针对 Phase 1
物理引擎重新调参。硬切审计覆盖直接构造 `SpringSpec`、`MotionScheme` Role、
`animateContentSize`、`AnimatableCore`、`Animatable`、target-as-state、Transition Channel、
Demo 与自定义 Design System。

## 变更与结果映射

当前 ViewCompose `Animatable` 已采用 Last-mutation-wins：不同 Job 的新变更会取消旧调用，
过期帧不能发布，取消后保留最后接受值。Phase 1 保持该方向，并增加速度、Decay、Bounds 与
结构化成功结束结果。

与简单结果枚举不同的已接受规则包括：

- 取消仍抛出 `CancellationException`，不会返回 `Interrupted` 结果；
- 正常抵达 Target、碰到 Bound、触发物理安全时长分别返回 `Finished`、`BoundReached`
  或 `DurationLimitReached`；
- 替代物理动画延续保留速度，除非调用方显式传入另一初速度；
- `snapTo` 与 `stop` 最终保留零速度；
- 成功抵达 Target 会发布精确 Target 与零保留速度。

Gesture 代码以 Converter Domain 的单位/秒移交速度。Density 转换、RTL 符号、Axis 投影与
Nested Scroll 决策属于 Gesture Owner，不属于动画引擎。

## Transition 与内容所有权

Compose 迁移应保留以下所有权，而不是只替换调用名：

- `Crossfade` 保持纯 Alpha，最多保留 Outgoing 与 Incoming 两份内容；
- 完整 `AnimatedContent` 以 `contentKey` 定义子树 Identity，在相同父 Constraint 下测量
  两个 Child，把 Focus/Input/Accessibility 所有权移交给已提交 Incoming 子树，并在全部
  Exit Channel 完成后释放 Outgoing 内容；
- A-to-B-to-C 替换把已采样 B 提升为 Outgoing 并释放 A，从而最多保留两棵完整子树；
- 重复 Transform Channel 取该类型最后一次声明；父级/后代 Alpha 相乘，Translation 在
  RTL 解析后相加，Scale 围绕各自声明 Origin 相乘；
- Renderer Apply 失败不能发布候选 Identity、Focus、Geometry、Effect 或 Release。

Seekable Transition 只有一个 Writer。`seekTo` 在发布有限 `0f..1f` Fraction 前取消并 Join
自主动画。Seek 不制造速度；`animateTo` 可从已采样值以显式 Gesture 速度继续。
Seek State 不可保存，Predictive Back 的 Commit/Rollback 仍由 Navigation 所有。

## Layout 与共享运动映射

Phase 5 Bounds 动画使用完成 RTL 解析后的直接 ViewCompose Layout Parent 物理像素坐标系。
Renderer 会真正 Layout 当前动画矩形，使 Drawing、Hit Test 与 Accessibility Bounds 一致；
纯 Draw Offset 不等价。

Phase 6 把 Shared Key 限定在一个 `SharedTransitionLayout` 与一个 Navigation Session 中。
恰好一个 Source 与一个 Target 才能配对。重复/缺失 Peer、坐标已 Detach 或 Root 未 Place 时，
回退到普通本地 Enter/Exit。Target Destination 独占 Input、Focus 与 Accessibility，
非交互 Overlay 只负责渲染共享视觉。Key 不跨 Window、Activity、Process 或 Session 配对。

## 性能与验证基线

`AnimationPerformanceBenchmark` 在 Phase 1 前冻结四项 Revision-1 Workload：

| 场景 | 隔离的当前行为 | 测量动作 |
| --- | --- | --- |
| `animation.specs@1` | Float、Int、编码 Color 与 `UiDp` 的固定时长 Spring | 选择 Spring 后执行四次前进/返回 Target 往返 |
| `animation.content@1` | 纯 Alpha `Crossfade` | 四次前进/返回内容替换 |
| `animation.content-size@1` | Wrapper 支撑的测量尺寸动画 | 四次 Expand/Collapse 往返 |
| `animation.transition@1` | 同步多 Channel Transition | 四次前进/返回状态 Segment |

全部采用五次 `CompilationMode.None` 迭代、R8/资源收缩的不可调试 Target、测量外五秒启动
稳定期、Accessibility Action、完整动画稳定窗口，并记录 Frame CPU 与进程 Peak Memory。
正式对比要求同一真机、固定 CPU/GPU/Interconnect 策略、相同 Workload Revision、Build Mode、
刷新率与起始 Thermal 状态，且 run-P50 变异系数不超过 `0.15`。

Phase 0 的 Xiaomi MI 6 / API 28 运行接收了全部四项绝对基线；各迭代帧数完全一致，Run-P50 CV
为 `0.010..0.075`。该批次没有 Candidate 或同轮 Compose Control，因此归一化结论为
`inconclusive`，不能作为性能改善声明。精确 Percentile、Heap、APK 身份、温度局限和下一步记录在
[性能文档第 2.4.8 节](../tooling/performance.md#248-animation-revision-1-pre-physics-baseline)。

冻结门禁不只检查视觉：Frame CPU P50 仅在同时超过 5% 与 0.3 ms 时失败，P95 仅在同时
超过 10% 与 0.8 ms 时失败；Peak Process Memory 仅在同时超过 10% 与 1,024 KiB 时失败。
引擎 Vector 与 Scratch Buffer 每次 Run 只分配一次；内容保留、额外 Measure、Overlay 释放与
非活跃工具也有结构 Counter。没有解释过的同设备对比，Raw Output 不能关闭阶段。

## 迁移顺序

1. 盘点所有当前 `SpringSpec`、`spring`、`AnimationSpec`、Converter、`Animatable`、
   Transition、Visibility、`Crossfade` 与 `animateContentSize` 使用。
2. 把每项动画分类为精确时长、物理、Decay、Keyed Replacement、Visibility、Seek、Bounds
   或 Shared Motion。不要仅因某能力当前可用就用它编码另一类别。
3. 只在现有 Alpha 与 Duration API 的文档语义足够时保留它们。物理与共享运动迁移等待
   所属 Phase 发布。
4. Phase 1 落地时，在同一个改动中硬切所有 Duration-Spring 调用，并按物理方程、速度、
   阈值、Reduced Motion 与结束结果重新调参。
5. 按页面所用能力验证 Cancellation、Rapid Retarget、Host Detach、Renderer Failure、RTL、
   Focus、Input、Accessibility 与 Reduced Motion。
6. 修改 Frame、Measure、Retention、Overlay 或 Tooling 路径前后，运行匹配 Revision 的真机
   Benchmark。

迁移不会把 Compose Runtime 或 Animation 依赖引入 ViewCompose 生产产物。
