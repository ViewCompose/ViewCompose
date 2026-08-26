---
translation_source: modules/viewcompose-constraintlayout-androidx/README.md
translation_source_hash: 7d91b0b8d3667110998d82dd233d43544a379a6c1dc0f24caaa07208aec02bbb
translation_status: current
---

# AndroidX ConstraintLayout 集成

`viewcompose-constraintlayout-androidx` 提供声明式 ConstraintLayout 节点、类型安全的子项约束、
不可变 Constraint Set 与 AndroidX 支持的虚拟 Helper。公开包为
`com.viewcompose.constraintlayout`；Artifact 后缀用于标识 AndroidX 后端。

## Artifact 与稳定性

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="constraintlayout-dependency" sample_id="module.constraintlayout-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-constraintlayout-androidx:0.1.0-alpha01")
}
```

- 稳定性：**Alpha**。破坏源码兼容的类型化 DSL 与原子 Renderer 契约已经建立；后续 Alpha
  可以增加能力，但不得恢复局部图应用或字符串 Helper Grammar。
- 平台：Android 7.0（API 24）及以上；AndroidX ConstraintLayout `2.2.2`。
- 依赖边界：UI Contract 与 UI Foundation 是 API 依赖，因为公开 API 使用其 Modifier、Unit 与
  Builder 类型。Runtime 和 AndroidX 是实现依赖。
- 可选性：UI Foundation 不依赖本模块；仅在需要原生约束求解器或 Helper 时引入。

## 行内约束与类型化 Scope

在 `ConstraintLayout` 内创建 Reference，通过 `constrainAs` 关联子项，再在约束 Scope 中连接类型
安全的 Anchor：

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/ConstraintLayoutModuleSamples.kt" region="constraintlayout-inline" sample_id="module.constraintlayout-inline" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
ConstraintLayout {
    val (title, body) = createRefs("title", "body")
    Text(
        "Title",
        modifier = Modifier.constrainAs(title) {
            startToStart(parent)
            topToTop(parent)
        },
    )
    Text(
        "Body",
        modifier = Modifier.constrainAs(body) {
            startToStart(title)
            topToBottom(title, margin = 8.dp)
        },
    )
}
```

Reference 是当前布局内非空的身份标识。重复 Child ID、Helper ID 或 Child/Helper 冲突会拒绝完整
候选图。同一来源 Anchor 的后声明值替换先声明值。Start/End 是逻辑方向；Left/Right、Top/Bottom
与 Baseline 是物理方向。同一 Item 不能混合逻辑与物理水平链接；Baseline 排斥 Top/Bottom，Circle
定位排斥全部 Edge 或 Baseline 链接。

`ConstraintLayoutScope` 与 `ConstraintConstrainScope` 是专用 `@UiDslMarker` Receiver。水平 Helper
只实现水平 Target 类型，垂直 Helper 只实现垂直 Target 类型，普通 Child Reference 实现所有适用
平面。因此跨轴链接和从嵌套 Scope 泄漏的 Helper 调用会在 Kotlin 编译期失败。Content 执行完成
后，保留的 Scope 不再有效。

## 尺寸与定位

宽高使用一套互斥的代数：

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/ConstraintLayoutModuleSamples.kt" region="constraintlayout-dimensions" sample_id="module.constraintlayout-dimensions" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
width = ConstraintDimension.MatchConstraints(
    mode = ConstraintMatchMode.Percent(0.6f),
    min = 120.dp,
    max = 360.dp,
)
height = ConstraintDimension.Fixed(180.dp)
ratio = ConstraintRatio(width = 16f, height = 9f, constrainedSide = ConstraintRatioSide.Width)
```

可用尺寸为 `WrapContent`、`ConstrainedWrapContent`、`Fixed` 与
`MatchConstraints(Spread|Wrap|Percent, min, max)`。边界和百分比会立即校验。类型化 Ratio 要求正的
有限项，且至少一个轴为 Match Constraint。Bias 和 Guideline Fraction 使用 `0f..1f`；Circle
Angle 使用有限的 Android 顺时针范围 `0f..<360f`。`wrapBehaviorInParent` 独立控制对父级每个
Wrap-content 轴的贡献。

## 可复用 Constraint Set

`constraintSet` 构建不可变图而不发射 UI，然后传给 `ConstraintLayout(constraintSet = set)`。行内
约束与 Helper 在其后合并；相同 Constraint ID 或同类型 Helper ID 由行内声明覆盖。同一来源的
重复项与跨类型 Helper 冲突会在原生变更前失败。

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/ConstraintLayoutModuleSamples.kt" region="constraintlayout-set" sample_id="module.constraintlayout-set" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
val set = constraintSet {
    val (title, body) = createRefs("title", "body")
    constrain(title) {
        startToStart(parent)
        topToTop(parent)
    }
    constrain(body) {
        startToStart(title)
        topToBottom(title, margin = 8.dp)
    }
}
```

引用式行内 Child 使用 `Modifier.constrainAs(reference)`。`Modifier.constrain(id, ...)` 仍是显式
XML 迁移快捷方式；已删除的字符串 Constraint-set Builder 不会恢复。

## 类型化虚拟 Helper

Helper 在当前布局或可复用 Set 中声明，并共享同一套类型化 Reference：

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/ConstraintLayoutModuleSamples.kt" region="constraintlayout-helpers" sample_id="module.constraintlayout-helpers" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
ConstraintLayout {
    val (hero, metric, status, center, orbit) = createRefs(
        "hero", "metric", "status", "center", "orbit",
    )
    val start = createGuidelineFromStart(0.1f)
    createGrid(
        hero,
        metric,
        status,
        rows = 2,
        columns = 2,
        orientation = ConstraintGridOrientation.Horizontal,
        spans = listOf(ConstraintGridSpan(hero, index = 0, columnSpan = 2)),
        skips = listOf(ConstraintGridSkip(index = 2)),
    )
    createCircularFlow(
        center,
        ConstraintCircularFlowItem(orbit, radius = 48.dp, angle = 90f),
    )
    Text("Hero", modifier = Modifier.constrainAs(hero) { startToStart(start) })
    Text("Metric", modifier = Modifier.constrainAs(metric) {})
    Text("Status", modifier = Modifier.constrainAs(status) {})
    Text("Center", modifier = Modifier.constrainAs(center) {})
    Text("Orbit", modifier = Modifier.constrainAs(orbit) {})
}
```

- Guideline 支持物理或逻辑 Offset/Fraction；Barrier 支持全部逻辑和物理方向、Margin 与 Gone-widget
  Policy。
- Chain 至少需要两个唯一 Member 和类型化边界；正数 Weight、Bias、Margin 与水平逻辑/物理一致性
  会在渲染前校验。
- 类型化 Grid 上限为 `50 x 50`，支持固定/推导轴、Weight、Gap、Orientation、Span 和 Skip；它
  展开为 Renderer 拥有的求解 Proxy，不使用 AndroidX Grid 的字符串 Grammar。
- CircularFlow 把类型化 Radius/Angle Item 展开为普通 Circle Constraint，不创建 Helper View。
  Flow、Group、Layer 与 Placeholder 映射到受管 AndroidX Helper。
- Flow 和 Placeholder 是可约束图节点；Guideline、Barrier、Group 与 Layer 只暴露自身实际支持的
  Target 或 Identity 平面。

## 原生所有权与失败行为

Renderer 在变更前预检完整合并图。一个 Registry 拥有稳定原生 ID，以及全部受管 Guideline、
Barrier、Flow、Group、Layer、Placeholder 与 Grid Proxy View。已接受候选从干净的原生 Set 应用；
快照覆盖受影响 ID、LayoutParams、Helper Membership、Accessibility、Visibility 与 Transform。

缺失 Reference、重复或冲突 ID、非法 Anchor Plane、竞争 Item/Helper Ownership、Helper Cycle 与
非法 Dimension 会拒绝完整候选。原生异常会恢复先前 Registry 与 View 状态。诊断按 Graph
Revision、Identity 和 Reason 结构化限界；非法链接不会被逐条静默丢弃。

Equal 与 Content-only 更新会在 Graph Compilation 前返回。Scalar 更新保留 Helper Instance 与
ID，不克隆实时 LayoutParams，并至多请求一次布局。Environment 更新只解析一次环境并保留拓扑；
Topology 更新使用完整的分阶段 Commit 和 Rollback。这些分类属于 Renderer 行为，不是公开调优
Flag。

## 已接受的正确性与性能证据

最终 JVM 运行通过 75/75 UI Contract、11/11 模块 DSL 和 451/451 Renderer 测试。设备验收在
API 24 与 API 33 通过 8/8 聚焦用例，保留先前 API-28 的 3/3 Helper Matrix 与 200 次切换压力
验证，并审查 12/12 个覆盖尺寸、方向、主题、布局方向和字体缩放的 Pairwise 截图，未发现重叠、
裁剪或 Helper Artifact。正确性、类型安全、回滚、生命周期与配置结论为 **improved**。限制是两个
聚焦的物理 OEM/API 点加模拟器覆盖，以及 Pairwise 集合而非全部视觉笛卡尔积。

受控 Released/Candidate/Direct-AndroidX Matrix 覆盖 10/50/100 节点的 Stable、Scalar、Helper
与 Topology 变更。七组可比较结果通过全部 Timing 与 Peak-heap 回退门禁；五组在唯一允许的复测
后仍为 `inconclusive`。Direct AndroidX 在十二个 Candidate Action 的 P95 和其中十一个的 P50
更快。发布安全结论为 **no material change**，不是整帧性能领先。应保留 Zero-work 与
Bounded-write Fast Path；只有生产 Reconciliation 变化时才重新运行受控 Matrix。完整绝对结果、
归一化变化、CV、温控与限制见
[性能工具文档](../../tooling/performance.md#247-constraintlayout-phase-4-controlled-matrix)。

## 性能指引

- 拓扑稳定且只改变 Content 或 Scalar 时复用 Constraint Set。
- 保持 Reference ID 与 Helper Kind 稳定，以复用原生 ID 和 Helper Instance。
- 约束没有价值时优先使用简单容器；ConstraintLayout 始终需要一次 Solver Pass。
- 避免从快速变化的状态反复重建大型 Helper Graph。
- 在没有稳定证据证明自动分类路径不足前，不增加公开优化模式。

## Alpha 源码迁移

| 已删除的 Alpha 源码 | 替代方式 |
| --- | --- |
| `ConstraintDimension.FillToConstraints` / `MatchParent` | Opposing Anchor 加 `ConstraintDimension.MatchConstraints()` |
| 独立 Min/Max/Percent/Constrained 字段 | `Fixed`、`ConstrainedWrapContent` 或一个类型化 `MatchConstraints(...)` 值 |
| 原始 Ratio 字符串 | `ConstraintRatio(width, height, constrainedSide)` |
| Circle 与 Edge Constraint 混用 | 拆分为不同 Constraint-set State |
| 重复 Constraint/Helper ID | 只声明一次；仅在有意 Overlay 时使用 Inline-over-set 优先级 |
| `ConstraintLayoutScope` 作为 `UiTreeBuilder` Alias | 使用 `ConstraintLayout` 提供的专用 Receiver |
| 一个通用 Helper Target 类型 | 使用已声明的水平、垂直、Baseline 或 Identity-only Reference |
| `constraintSet { constrain(ref.id) }` | `constraintSet { constrain(ref) }` |
| AndroidX Grid 字符串 Span/Skip | `ConstraintGridSpan` 与 `ConstraintGridSkip` |
| 把逻辑 Start/End 当作固定屏幕边缘 | 物理 Left/Right Anchor、Guideline、Barrier 和 Chain Side |

这是有意的源码硬切。没有 Deprecated 兼容 Alias、局部链接恢复、原始 AndroidX Helper Grammar
或第二套约束求解器。

## 关联文档

- [约束图与 Helper 所有权 ADR](../../architecture/decisions/0016-constraintlayout-graph-and-helper-ownership.md)
- [类型化 Helper 扩展 ADR](../../architecture/decisions/0017-typed-constraint-helper-expansion.md)
- [Modifier 架构](../../architecture/modifier.md)
- [性能工具文档](../../tooling/performance.md#247-constraintlayout-phase-4-controlled-matrix)
- [首发强化记录](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/constraintlayout-native-engine-hardening.md)
- [能力与性能扩展记录](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/constraintlayout-parity-performance-expansion.md)
- [生成式 API Reference](https://docs.viewcompose.com/api/viewcompose-constraintlayout-androidx/current/)
