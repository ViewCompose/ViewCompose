---
translation_source: modules/viewcompose-gesture-core/README.md
translation_source_hash: 6f23038c71596d3eb47f6c304b19f6f008e310775b52be2c9fb594d047b59bac
translation_status: current
---

# Gesture Core 模块

`viewcompose-gesture-core` 是 ViewCompose 手势识别的平台无关策略层。它把 Renderer 提供的
指针距离、速度、Touch Slop 与锚点转换成轴锁定、Transform 激活、Swipe 方向和锚点收敛目标。
它不拥有指针流、Android `MotionEvent`、协程、可变手势状态或 View。

## 产物与稳定性

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="gesture-core-module-dependency" sample_id="module.gesture-core-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-gesture-core:0.1.0-alpha04")
}
```

- 稳定性：**Alpha**。当前阈值顺序和锚点选择行为已经审查并测试；策略命名与高层手势集成在
  Alpha 版本间仍可能演进。
- 平台：Kotlin/JVM，不依赖 Android Framework。
- UI Contract 会被传递暴露，因为共享的方向与 Swipe 值出现在公开策略签名中。
- 应用通常通过 `viewcompose-gesture` 间接获得它；自定义 Renderer、确定性策略测试或非 Android
  指针集成可以直接依赖。

## 职责边界

Gesture Core 有意保持为一组同步且确定的函数。Renderer 仍须收集 Pointer ID、累计移动、归一化
Transform Motion、获取平台 Touch Slop 与 Fling 速度、解析布局方向、仲裁竞争的识别器并传递取消。
把这些职责留在模块外，Android 渲染、预览和单元测试就能共享完全相同的策略判断。

多数距离使用物理像素，因为 Android 以像素提供 Slop 和速度。Core 不转换 dp、不拒绝所有非有限
输入，也不推断 Zoom、Rotation 是否能与 Pan 比较。应在 Renderer 边界归一化输入。

## 轴锁定与 Transform 激活

`resolveLockAxis` 会等待累计移动达到 Touch Slop。固定方向还要求本轴移动不小于垂直轴。
Free 方向选择移动更大的轴；完全相等时选择 Horizontal。

{/* compiled-region source="viewcompose-gesture-core/src/test/samples/com/viewcompose/gesture/core/samples/GestureCoreSamples.kt" region="gesture-core-axis-lock" sample_id="module.gesture-core-axis-lock" build_target=":viewcompose-gesture-core:compileTestKotlin" */}
```kotlin
val axis = resolveLockAxis(
    dx = 18f,
    dy = 6f,
    orientation = GestureOrientation.Free,
    touchSlop = 8f,
)
```

`shouldActivateTransform` 是更小的阈值原语。归一化后的 Pan、Zoom 或 Rotation Motion 任一严格
大于 Slop 时激活；等于 Slop 仍不激活。调用方必须先把这些不同物理量转换为可比较的 Motion。

## Swipe 完成策略

`resolveSwipeDecision` 让终止速度优先于拖拽距离。速度低于 Fling 阈值时，距离必须达到“两倍
Touch Slop”和“两锚点间距的 35%”中的较大者。Horizontal 正向移动产生逻辑
Start-to-End，而非物理向右契约，RTL 解析留给 Renderer。

两个阈值都未命中且两个锚点都存在时，投影位置会收敛到更近端点；距离相等时选择 Min。锚点对
不完整时返回 `SwipeDecision.None`。该策略适合简单的双端点 Swipe；多锚点拖动应使用 Anchored
策略。

## 锚点拖动策略

锚点列表必须非空、有限且严格递增。自定义边界可以调用 `requireValidAnchorsPx`；所有公开的
Anchored 解析函数都会在内部验证。

`resolveAnchoredSettleTarget` 先把最接近手势起始位置的锚点作为 Segment 起点，再按顺序执行：

1. 速度达到有效 Fling 阈值时，按速度符号移动一个锚点；
2. 距离达到配置阈值时，按拖动方向移动一个锚点；
3. 否则选择最接近最终视觉位置的锚点。

距离阈值取 `touchSlop * slopMultiplier` 和相邻 Segment 长度乘 `segmentFraction` 的较大值。
`AnchoredThresholdPolicy` 可以替换平台 Fling 阈值，便于 Renderer 特化和确定性测试。移动会在
端点处截断；一次 Settle 不会跨越多个锚点。

{/* compiled-region source="viewcompose-gesture-core/src/test/samples/com/viewcompose/gesture/core/samples/GestureCoreSamples.kt" region="gesture-core-anchored-settle" sample_id="module.gesture-core-anchored-settle" build_target=":viewcompose-gesture-core:compileTestKotlin" */}
```kotlin
val result = resolveAnchoredSettleTarget(
    anchorsPx = listOf(0f, 160f, 320f),
    startOffsetPx = 160f,
    currentOffsetPx = 190f,
    velocityPxPerSecond = 1_200f,
    touchSlopPx = 8f,
    minFlingVelocityPxPerSecond = 600f,
)
```

锚点变化时，`resolveAnchoredOffsetOnAnchorUpdate` 首先保留与当前语义值关联的精确 Offset，然后
回退到最接近当前视觉 Offset 的锚点，最后回退到第一个锚点。这样，尺寸变化或重新计算锚点时，
旧值仍可表达就不会悄悄改变语义状态。

## 测试自定义手势集成

- 测试每个 Slop 或速度阈值的略低、相等和略高值。
- 测试主轴相等，以及两种布局方向下的逻辑 Horizontal 方向。
- 测试锚点验证、端点截断、等距选择和锚点集替换。
- 指针取消和识别器竞争测试应放在 Renderer 或 Gesture DSL 模块；Gesture Core 不拥有事件流。
- 集成测试应提供物理像素值，使策略阈值与运行时平台值一致。

模块测试覆盖 Horizontal、Vertical 与 Free 锁定、Transform 激活、Swipe 的速度和距离优先级、
最近端点收敛、锚点验证、Anchored 的 Fling、距离与最近选择，以及锚点更新时的位置保留。

## 相关文档

- [UI Contract 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-ui-contract)
- [架构概览](https://docs.viewcompose.com/zh-CN/architecture/overview)
- [源码文档与 API 注释规范](https://docs.viewcompose.com/zh-CN/project/api-documentation-quality)
- [项目路线图](https://docs.viewcompose.com/zh-CN/project/roadmap)

完整生成参考位于
[`viewcompose-gesture-core` API 树](https://docs.viewcompose.com/api/viewcompose-gesture-core/current/)。

## 兼容性说明

`0.1.0-alpha04` 建立速度优先于距离、逻辑 Horizontal Swipe 方向、相邻锚点移动、严格锚点顺序和
语义 Offset 保留契约。指针派发、可变状态、组合所有权与 Android 事件集成属于
`viewcompose-gesture` 和 Renderer。
