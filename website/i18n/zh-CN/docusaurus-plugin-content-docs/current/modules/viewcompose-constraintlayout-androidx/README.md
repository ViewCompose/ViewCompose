---
translation_source: modules/viewcompose-constraintlayout-androidx/README.md
translation_source_hash: 0a197764213a6f60d6931d1738fca4ff1732e6609bf195be423320954e0c13dc
translation_status: current
---

# AndroidX ConstraintLayout 集成模块

`viewcompose-constraintlayout-androidx` 为 ViewCompose 提供声明式 ConstraintLayout 节点、
子项约束 Modifier、可复用 Constraint Set 和 AndroidX Virtual Helper。

公开 API 根包为 `com.viewcompose.constraintlayout`。Maven 后缀用于表达 AndroidX 后端，
不保留已退役的 `com.viewcompose.widget.constraintlayout` 分类。

## 产物与稳定性

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-constraintlayout-androidx:0.1.0-alpha01")
}
```

- 稳定性：**Alpha**。当前源码已经包含首发 API 与 Renderer 硬切；Robolectric、真机、
  Demo、AndroidX `2.2.2`、性能安全、文档与仓库发版门禁均已通过，首发加固计划已经完成
  并归档。
  更广泛的能力对齐与优化由独立的
  [发版后扩展计划](https://docs.viewcompose.com/project/plans/constraintlayout-parity-performance-expansion)负责；
  在首版发布并完成 Tag 前，该计划保持无 Changeset 状态。
- 平台：Android 7.0（API 24）及以上。
- 可选：`viewcompose-ui-foundation` 不依赖该产物。
- UI Contract 与 UI Foundation 会被传递暴露，因为它们的 Modifier、单位和 Builder 类型
  出现在公开 DSL 中；Runtime 保持为实现依赖。
- 原生引擎：AndroidX ConstraintLayout `2.2.2` 及其 Guideline、Barrier、Flow、Group、
  Layer 与 Placeholder Helper。

## 内联约束

在 `ConstraintLayout` 内创建 Reference，用 `Modifier.constrainAs` 绑定子项，并在约束
Scope 中连接 Source Anchor：

```kotlin
ConstraintLayout {
    val (title, body) = createRefs("title", "body")
    Text("Title", Modifier.constrainAs(title) {
        startToStart(parent)
        topToTop(parent)
    })
    Text("Body", Modifier.constrainAs(body) {
        startToStart(title)
        topToBottom(title, margin = 8.dp)
    })
}
```

Reference 是一个 Layout 内的非空 String Identity。重复 Child ID、Helper ID 或
Child/Helper 冲突会拒绝完整候选。同一 Source Anchor 被重复设置时，后者替换前者。
`start`/`end` 跟随 Layout Direction；Top、Bottom、Baseline 是物理/原生 Anchor。
Baseline Link 与 Top/Bottom 定位互斥；Circle 与所有 Edge/Baseline Link 互斥。

`ConstraintLayoutScope` 现在是专用的 `@UiDslMarker` Receiver，不再是 `UiTreeBuilder` 的
Type Alias。它仍然提供所有普通 Widget，但 Helper 声明直接归当前 Layout 所有；进入嵌套的
Layout Scope 后，外层 ConstraintLayout Receiver 会被隐藏。Scope 会在 Content 完成后冻结
Helper Spec，公开行为不再依赖 Thread-local Collector 或发射后仍可变的 Helper Payload。

Anchor Target 按能力拆分。逻辑 Start/End API 只接受
`ConstraintHorizontalAnchorTarget`；Top/Bottom API 只接受
`ConstraintVerticalAnchorTarget`；Baseline-to-baseline 只接受
`ConstraintBaselineAnchorTarget`。普通 Child Reference 同时实现三个平面；Start/End
Guideline 或 Barrier 只实现水平平面，Top/Bottom Guideline 或 Barrier 只实现垂直平面；Group
与 Layer 返回仅表示 Identity 的 Helper Reference。因此跨轴 Link 会在 Kotlin 编译阶段失败，
而不是推迟到整图预检。

## 尺寸与定位

Width 与 Height 使用一套互斥的代数类型：

```kotlin
width = ConstraintDimension.MatchConstraints(
    mode = ConstraintMatchMode.Percent(0.6f),
    min = 120.dp,
    max = 360.dp,
)
height = ConstraintDimension.Fixed(180.dp)
ratio = ConstraintRatio(width = 16f, height = 9f, constrainedSide = ConstraintRatioSide.Width)
```

可用尺寸包括 `WrapContent`、`ConstrainedWrapContent`、`Fixed` 和
`MatchConstraints(Spread|Wrap|Percent, min, max)`。边界和百分比会立即校验；契约不再包含
`MatchParent`、独立 min/max/percent/constrained 字段或原始 Ratio String。类型化 Ratio
要求宽高项为有限正数，且至少一个轴使用 Match Constraint。Bias 与 Guideline Percentage
使用 `0f..1f`；Circular Angle 使用有限的 `0f..<360f` Android 顺时针约定。

## 可复用 Constraint Set

`constraintSet { ... }` 构建不发射 UI 的不可变 `ConstraintSetSpec`，再传给
`ConstraintLayout(constraintSet = set)`。Inline Constraint 与 Helper 随后合并；同一
Constraint ID 或同类型 Helper ID 冲突时 Inline 优先。同一来源内的重复 Constraint/Helper
会立即失败；跨 Helper 类型的 ID 冲突会在整图预检时拒绝。

可复用条目使用同一个类型化 Reference 声明约束与 Link：

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

已移除的 `constrain(id: String)` Builder Overload 不会再与单独创建的 Reference 发生漂移。
`Modifier.constrain(id, ...)` 继续作为 Inline Child 的显式 XML 迁移捷径；
`Modifier.constrainAs(ref, ...)` 是基于 Reference 的写法。

## Virtual Helper 虚拟辅助对象

- Guideline 使用有限非负 dp Offset 或 `0f..1f` Parent Fraction。
- Barrier 使用 Margin 与 Gone-widget Policy 跟踪逻辑/物理极值。
- Chain 至少需要两个唯一成员，拥有成员在 Chain 轴上的 Anchor，并校验有限正 Weight 与 Bias。
- Flow 映射 Orientation、Wrap、Style、Bias、Alignment、Gap、Padding 与最大换行数。
- Group 传播 Visibility 与 Elevation。
- Layer 传播 Visibility、Elevation、Rotation、Scale、Translation 与可选 Pivot。
- Placeholder 承载一个引用 Child，并定义空状态 Visibility。

Inline Helper 函数只存在于 `ConstraintLayoutScope`，无关 Builder 无法调用它们。Reusable
版本使用 `ConstraintSetBuilder`。Barrier、Flow、Group 与 Layer 必须包含 Layout-local
Reference。Flow 与 Placeholder 是可约束图节点，因此可复用 Set 可以约束其 Helper
Reference；Guideline、Barrier、Group 与 Layer 不能作为普通 Constraint Item Source。

## 原生协调与失败

Native Container 会合并 Rebuild Request，并在修改前预检完整合并图。Child 与 Helper String
ID 映射为稳定 Android View ID。一个注册表负责创建、复用、换型和删除 Guideline、Barrier、
Flow、Group、Layer 与 Placeholder View；AndroidX 不再通过 `applyTo` 副作用创建无主 Helper。

已接受候选从干净的原生 Set 构建。Renderer 在应用前快照受影响的 ID、LayoutParams、Helper
成员关系、无障碍、Visibility 与 Transform。缺失引用、重复/冲突 ID、无效 Anchor Plane、
相互竞争的 Chain/Item 所有权、Helper 环和无效尺寸/范围都会拒绝完整候选。原生失败会恢复
此前的 Helper 注册表与 View 状态。诊断按图 Revision、Identity 和 Reason 结构化并保持有界；
无效 Link 不会被单独丢弃。

该事务遵循 [ADR-0016](../../architecture/decisions/0016-constraintlayout-graph-and-helper-ownership.md)。
2026-08-18 使用缓存 ConstraintLayout `2.2.1` 的 API 35 Robolectric 聚焦运行通过了 16/16 条
Renderer 测试。在相同 Harness 下，Trailing Barrier Control 在 ID Index 和 Direction 修复前是
预期 `125 px`、实际 `0 px`，修复后精确为 `125 px`，坐标误差从 `125 px` 降到 0；结论为
**improved**。1,000 次 Helper 换型的每次迭代都保持恰好一个受管 Helper 和两个总 Child。
该运行还覆盖六种 Helper 换型、Layer Transform/删除/Detach/Reattach、Placeholder 释放、无效
候选状态保留、注入原生提交失败后的回滚与有效重试，以及每种 Helper 声明换序时稳定的原生
Identity。这属于聚焦正确性证据，不是发版验收：它使用手工 Classpath 与 `2.2.1`，生成 ID 在
压力场景会产生 Robolectric 专属的资源名查询诊断。后续 Gradle 8.13 运行实际解析
ConstraintLayout `2.2.2` 与 Core `1.1.2`，通过 75/75 条 UI Contract、11/11 条 DSL 和
451/451 条 Renderer 测试，其中包含 12 条 Graph 与 16 条 ConstraintLayout 聚焦用例；
`verifyDocumentationStructure` 也通过。正式 JVM 兼容性结论仍为 **improved**。后续真机和性能
矩阵已闭合首发验收范围；分类更新快速路径、Grid、CircularFlow 和更广泛的能力对齐继续留在
发版后计划。

2026-08-19 的 DSL Safety 后续运行通过 17/17 条 ConstraintLayout 模块测试：12 条行为测试和
5 条 Kotlin 2.0.21 Compiler Fixture。合法的类型化 Axis/Reference Sample 可以编译；把垂直
Helper 用作水平 Target、把水平 Helper 用作垂直 Target、在嵌套 Column 内泄漏调用外层
ConstraintLayout Helper，以及向 ConstraintSet 传入 String 条目，都会按契约编译失败。此前
通用 Target/Type Alias Surface 会接受这四种无效写法，因此编译安全结论为 **improved**。
同一次运行还证明嵌套 Helper Snapshot 相互独立，且保留到 Content 之外的 Scope 会拒绝延迟
声明。`verifyDslApiContracts`、UI Foundation Scoped-container Sample、Demo 编译与 Preview
编译也已通过；下方真机与性能验收为这份源码契约证据提供补充。

2026-08-19 在 Samsung SM-G991B / Android 13 上进行的聚焦真机复验，在浅色主题、LTR、字体
缩放 1.0 下接受了修订后的 Guideline/Barrier Fixture。Barrier Marker 中心从短文案的
`596 px` 移到长文案的 `782 px`，绝对位移 `186 px`（占 1080 px 屏宽的 17.2%）；可见的
55% Guideline 保持固定，完整 Marker 始终位于容器内。精确几何 Instrumentation 通过 1/1，
无 Warning 的 Demo APK 构建通过，过滤日志未出现 App Fatal、ConstraintSet、Renderer Layout
或 Helper Layer 失败。聚焦视觉/几何结论为 **improved**；其后继续完成了下方完整矩阵。

随后，2026-08-19 的完整真机验收在已 Root 的 Xiaomi MI 6 / Android 9 上通过 3/3 条
Instrumentation Test。它在浅色/LTR/字体缩放 1.0 与深色/RTL/字体缩放 1.3 两组配置中覆盖
完整保留 Helper Surface，断言 Guideline、Barrier、Flow、Group、Layer 与 Placeholder 的精确
原生效果，并完成 100 次 Retained-helper 加 100 次 Virtual-helper 状态切换，Child/Helper 数量
始终恒定。人工复核了 9 张截图；聚焦 Guideline/Barrier、全部 Barrier Direction、单列 Flow、
隐藏 Group、Layer Transform 与 Placeholder Transfer 在两组配置中都保持可读且未越出容器。
过滤日志未出现非预期 ConstraintSet、Helper Layer、Renderer Layout 或 Fatal 条目。

2026-08-20 在完成最终 Demo 本地化和 Benchmark Harness 编辑后，归档候选重新构建并覆盖安装到
同一台 Xiaomi 真机。应用 APK
`0bd034432282130b9c7c99f0fe9d0120699d113ff3e288936a3b9562f3e09673` 与测试 APK
`2c555fafe3dbdbd96c0bbbd43401179d115483ca601ee01c3c813739b4bc26d3` 用 `195.759 s` 再次
通过同一组 3/3 首发真机测试。这确认最终构建仍保持已接受的精确几何、Virtual Helper、
有界 Registry 和无警告行为，结论为 **no material change**。本次没有新增 OEM/API 点，也不
替代此前 9 张截图的人工复核，因此仍保留相同的单设备局限。

RTL 验收在修复前暴露了一处 Android 9 生命周期缺陷：Container 从 LTR 切换到 RTL 后，保留的
Programmatic Helper 可能继续持有此前解析的 LTR Direction，导致 AndroidX 无法镜像逻辑
Guideline Begin/End。Renderer 现在会在应用 Graph 前把每个 Retained Helper 的
`layoutDirection` 与 Container 同步。没有该同步时 Transition 回归会失败，加入同步后得到精确
镜像几何；真机正确性结论为 **improved**。该证据覆盖一台 API 28 真机与两组高风险配置，不能
代表全部受支持 OEM/API 组合。

最终 Root 首发性能矩阵在 10/50/100 Node 下对比了硬切前 ViewCompose APK、Candidate 与 Direct
Android Views 的 Stable-content、Scalar、Helper 和 Topology 动作。8 个动作的两侧 ViewCompose
稳定；4 个在一次相邻复测后仍为 `inconclusive`。稳定的 Frame P50/P95 与 Median Peak Heap 均无
回退，修正后的 Android-Views 归一化 `--enforce` 门禁通过。Renderer 还从回滚快照捕获中移除了
O(n²) Child Index 查询，并在没有 Content Overlay 被释放时跳过完全相同的第二份快照；此前失败
的稳定 topology-50 P50 从 `7.076 ms` 降到 `6.162 ms`，Baseline 为 `6.304 ms`。首发性能安全
结论为 **no material change**。完整绝对值、归一化变化、CV、局限与协议见
[性能工具](../../tooling/performance.md#245-constraintlayout-首发性能安全)。

## Alpha 源码迁移

| 已移除的 Alpha 源码 | 替代方式 |
| --- | --- |
| `ConstraintDimension.FillToConstraints` | `ConstraintDimension.MatchConstraints()` |
| `ConstraintDimension.MatchParent` | 相对 Anchor 加 `MatchConstraints()` |
| `widthMin` / `widthMax` | `width = MatchConstraints(min = ..., max = ...)` |
| `heightMin` / `heightMax` | `height = MatchConstraints(min = ..., max = ...)` |
| `widthPercent` / `heightPercent` | `MatchConstraints(mode = ConstraintMatchMode.Percent(...))` |
| `constrainedWidth` / `constrainedHeight` | `ConstraintDimension.ConstrainedWrapContent` |
| `dimensionRatio = "W,16:9"` | `ratio = ConstraintRatio(16f, 9f, ConstraintRatioSide.Width)` |
| 同时声明 Circle 与 Edge | 把 Circle 和 Edge 定位拆到不同的 Constraint-set 状态 |
| 同一 Builder 中重复 Constraint/Helper ID | 每个 ID 只声明一次；仅用 Inline-over-set 优先级表达有意的状态覆盖 |
| 把 `ConstraintLayoutScope` 当作 `UiTreeBuilder` Alias | 使用 `ConstraintLayout { ... }` 提供的专用 Receiver；不要保留或自行构造它 |
| 所有 Helper 共用一个通用 Anchor Target 类型 | Start/End 只连接水平 Target，Top/Bottom 只连接垂直 Target，Baseline 只连接具备 Baseline 的 Child |
| `constraintSet { constrain(ref.id) { ... } }` | `constraintSet { constrain(ref) { ... } }` |

变更后的公开 Surface 属于 Q3：传输不变量、默认值、失败时机、DSL Scope、合并优先级、
Native Mapping 与替代 Sample 都是契约字段。不提供 Deprecated Compatibility Alias 或原始
AndroidX Escape Hatch。

## 性能建议

- Graph 稳定而 Child Content 变化时复用 Constraint Set。
- 保持 Reference ID 与 Helper 类型稳定，以复用生成的 View ID 与实例。
- 不需要约束求解时使用更简单 Container；ConstraintLayout 会引入 Solver Pass。
- 避免由高频 State 重建大型 Helper Graph。

已接受的 10/50/100 Node 首发矩阵证明：相对硬切前 ViewCompose 源码，所有稳定行均为
**no material change**；它不构成性能领先结论。Direct Android Views 仍明显更快，尤其是 P95。
不得把该 Adapter 描述为 ViewCompose 中最快的 Layout Path。发版后扩展计划负责分类 Scalar/
Topology 快速路径、多设备复验以及未来任何性能领先声明。

## 相关文档

- [UI Foundation 模块](../viewcompose-ui-foundation/README.md)
- [Renderer 模块](../viewcompose-renderer-android/README.md)
- [UI Contract 模块](../viewcompose-ui-contract/README.md)
- [源码文档与 API 注释规范](../../project/api-documentation-quality.md)

完整生成参考位于
[`viewcompose-constraintlayout-androidx` API 树](https://docs.viewcompose.com/api/viewcompose-constraintlayout-androidx/current/)。

## 兼容性说明

首发硬切之前的源码快照使用基于 Warning 的局部恢复与分裂的 Helper 所有权。当前源码有意
打破这些行为，不提供第二套 Constraint Solver 或 Compatibility Engine。首发计划仍负责验收
与发布闭环；发版后扩展计划独立负责优化、更广泛的能力对齐和性能领先证据。
