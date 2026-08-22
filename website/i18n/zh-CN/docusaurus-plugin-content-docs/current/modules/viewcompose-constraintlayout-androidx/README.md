---
translation_source: modules/viewcompose-constraintlayout-androidx/README.md
translation_source_hash: 138f9a00e5f290a505fbc74f52663c4cb9a2a9ce3c1289e546e56df946015d62
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
  并归档为 `docs/archive/constraintlayout-native-engine-hardening.md`。
  更广泛的能力对齐与优化已经在归档的
  [发版后扩展计划](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/constraintlayout-parity-performance-expansion.md)中完成。
  Demo 固定频率基线、Phase 0 契约冻结、Phase 1 分类协调、Phase 2 高价值能力对齐、Phase 3
  完整验收与 Phase 4 受控基准测试均已完成。该扩展线满足发版安全，但不宣称全帧性能胜利。
- 平台：Android 7.0（API 24）及以上。
- 可选：`viewcompose-ui-foundation` 不依赖该产物。
- UI Contract 与 UI Foundation 会被传递暴露，因为它们的 Modifier、单位和 Builder 类型
  出现在公开 DSL 中；Runtime 保持为实现依赖。
- 原生引擎：AndroidX ConstraintLayout `2.2.2` 及其 Guideline、Barrier、Flow、Group、
  Layer 与 Placeholder Helper。类型化 Grid 会展开为 Renderer 自有的行/列 Solver Proxy，
  不使用 AndroidX Grid 的 String Grammar；声明式 CircularFlow 会展开为普通 Circle Constraint，
  不创建 Helper View。

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
`start`/`end` 跟随 Layout Direction；`left`/`right` 保持物理方向；Top、Bottom、Baseline
也是物理/原生 Anchor。一个 Item 不能混用逻辑与物理水平 Link。Baseline Link 与
Top/Bottom 定位互斥；Circle 与所有 Edge/Baseline Link 互斥。`wrapBehaviorInParent`
可独立选择该 Item 是否参与 Parent 两个、一个或零个 Wrap-content 轴，同时不会把它从
Solver 或 Placement 中移除。

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
使用 `0f..1f`；Circular Angle 使用有限的 `0f..<360f` Android 顺时针约定：`0f` 位于
Center 上方，数值沿顺时针增加。

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

- Guideline 使用有限非负 dp Offset 或 `0f..1f` Parent Fraction。显式 Left/Right 变体保持
  物理固定，Start/End 变体在 RTL 中镜像。
- Barrier 使用 Margin 与 Gone-widget Policy 跟踪逻辑或物理极值。
- Chain 至少需要两个唯一成员，拥有成员在 Chain 轴上的 Anchor，校验有限正 Weight 与 Bias，
  并接受显式 Parent/Child/Guideline/Barrier Endpoint 及非负边界 Margin。Horizontal Endpoint
  必须全部使用逻辑平面或全部使用物理平面。
- 类型化 Grid 接受上限为 `50 x 50` 的固定或推导轴、正数行列 Weight、逻辑 Fill Orientation、
  dp Gap、类型化 Span 与 Skip。它拥有每个成员的两个定位轴；区域重叠或容量不足会拒绝完整候选。
- 声明式 CircularFlow 把显式 Child/Radius/Angle 值围绕一个 Child Center 分组。它拥有每个成员
  的 Circular Position，且不创建原生 Helper Identity。
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
Flow、Group、Layer、Placeholder View，以及 Grid 的零厚度行/列 Proxy；AndroidX 不再通过
`applyTo` 副作用创建无主 Helper。Grid 的语义 ID 只表示 Identity，不映射为 View；CircularFlow
既没有 Helper View，也没有生成的原生 ID。

已接受候选从干净的原生 Set 构建。Renderer 在应用前快照受影响的 ID、LayoutParams、Helper
成员关系、无障碍、Visibility 与 Transform。缺失引用、重复/冲突 ID、无效 Anchor Plane、
相互竞争的 Chain/Item 所有权、Helper 环和无效尺寸/范围都会拒绝完整候选。原生失败会恢复
此前的 Helper 注册表与 View 状态。诊断按图 Revision、Identity 和 Reason 结构化并保持有界；
无效 Link 不会被单独丢弃。

已接受 Graph 现在携带确定性 Topology/Scalar Fingerprint，以及原始 Child、环境、原生 ID 与 Helper
所有权输入。语义相同或仅 Content 变化的更新会在 Graph 编译前返回，不产生 Adapter 自有约束分配
或原生写入。Scalar 更新会保留未变 Helper 的实例与引用，不创建/删除 Helper，不克隆 Live
LayoutParams，并且最多请求一次 Layout。仅环境变化的更新只解析一次环境并保留 Topology 与 ID；
Topology 更新继续使用既有 Staged Commit 与完整回滚契约。这些分类属于实现行为，不是新的公开
DSL 控制项。

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
矩阵已闭合首发验收范围；发版后计划现在负责类型化 Grid、CircularFlow、更广泛的视觉验收和
最终性能对比。

2026-08-21 的 Phase 1 运行通过全部 459 条 Renderer 测试。具名用例覆盖 1,000 次 Equal 提交、
Content-only Child 替换、Scalar Helper 保留、单次环境解析，以及注入 Topology 失败后的有效重试。
结构计数器证明已接受 Equal 输入的 Compiler、Environment、原生提交、Helper 写入、Layout
Request 与 Adapter Allocation 均为 0；Scalar 用例不创建/删除 Helper，不克隆 Live LayoutParams，
并且最多提交一次。计数器与激活入口均为 Internal 且 Container-local，因此没有新增公开 API 文档
字段或未启用的 Global Observer。Release Intent、Development Tooling Isolation 与文档门禁均通过。

2026-08-21 的 Phase 2 聚焦 JVM 运行通过六条冻结的 `CL-P2-*` Renderer 用例。Chain 测试覆盖
Parent/Child/Guideline/Barrier 的精确 Boundary、Margin、物理 Placement 与逻辑 LTR/RTL 镜像。
Baseline 普通/Gone Margin 与 Direct AndroidX Control 一致，无效重试会保留已接受 Graph。四种
Parent-wrap Policy 都产生精确的双轴尺寸。Physical Link 与 Guideline 在 RTL-to-LTR 环境更新中
保持固定，而 Logical Start 会镜像。Weighted Grid 产生精确 Span/Skip 几何；重叠候选被拒绝时
五个保留 Proxy 不变，1,000 次替换期间 Proxy 数量始终位于 `0..5`。CircularFlow 与 AndroidX
Circle 坐标一致，会原子拒绝竞争的 Direct Ownership，并在 1,000 次替换中始终不持有 Helper
Identity。相对不包含这些 Phase 2 契约的已发布源码，这份能力与失败安全证据结论为
**improved**。它属于 JVM/API-35 Robolectric 证据；Phase 3 仍负责完整 Device、Screenshot、
Configuration 与 Lifecycle Matrix，Phase 4 仍负责性能结论。

同一份 2026-08-21 Candidate 在已 Root 的 Xiaomi MI 6 / Android 9 上用 `15.674 s` 通过 4/4 条
`ConstraintLayoutReleaseDeviceTest`。新增 Phase 2 用例验证 Grid Span/Skip 的精确顺序，其中包含
四个 Content Child 与五个生成的 Row/Column Proxy；同时验证四个 CircularFlow Member 位于
`78 dp` 半径的四个正方向，且不存在 Helper View。其余三条用例继续覆盖此前的浅色/LTR/字体缩放
1.0、深色/RTL/字体缩放 1.3 Helper Matrix 与 200 次快速状态切换；结构化 Diagnostics 未报告
非预期 Warning。人工复核两张聚焦中文 Demo 截图，确认加权 `2 x 3` Grid 与四成员
CircularFlow 清晰可读、没有裁切、重叠或 Helper Artifact。按进程过滤的日志未出现
`UIConstraintLayout`、`ConstraintSet`、Renderer、Helper Layer 或 Fatal 条目。相对已发布源码，
真机能力与失败安全结论为 **improved**。该证据只覆盖一个 OEM/API 点和聚焦视觉样本，不能替代
完整 Phase 3 Screenshot/Lifecycle Matrix，也不提供 Phase 4 性能结论。

2026-08-21 的 Phase 3 验收新增了可发现、单一用途的 Grid、CircularFlow、Gone Margin、
Parent-wrap、Chain 与 Helper Lifecycle Demo Fixture，以及仅属于 App 的 Mounted-scene
Diagnostics。12/12 张经人工复核的 Paparazzi Snapshot 采用 Pairwise/Orthogonal 组合，覆盖
Phone/Tablet、Portrait/Landscape、Light/Dark、LTR/RTL，以及 `1.0`、`1.3`、`2.0` 字体缩放；
未发现重叠、裁切、语义不明确的 Fixture 或方向/主题缺陷。Phase 2/3 合并设备套件在 API 24
以 `16.45 s` 通过 8/8，在 API 36 的最终聚焦运行中通过 8/8，并在 Google Pixel 4 XL /
Android 13（API 33）实体机上以 `26.442 s` 通过 8/8。精确原生几何覆盖 Grid
Orientation/Span/Skip、CircularFlow、普通/Gone Margin、四种 Parent-wrap Policy、Anchor、
Dimension、Bias、逻辑/物理方向，以及 Fixed/Weighted Chain。生命周期覆盖 Child Reorder、
Key Reuse、Detach/Reattach、Density/Direction Recreation、Rejected-candidate Rollback、有效
Retry，以及保留的 200 次真机 Toggle 和 Phase 2 的 1,000 次替换压力测试。未出现非预期的
`UIConstraintLayout`、`ConstraintSet` 或未捕获 AndroidX Warning；唯一预期 Rejection 保持
有界，并在其后成功恢复。相对只完成 Phase 2 的验收，配置、视觉、生命周期与 API 兼容性
置信度为 **improved**。本阶段没有修改已发布 Renderer 行为，也没有运行受控 Timing 对比，
因此 Phase 3 性能结论为 **no material change**。局限包括：最终实体机点只有 Google/API 33，
另有此前 Xiaomi/API 28 证据；视觉矩阵是 12 个 Pairwise Case，而不是全部 48 个 Cartesian
Combination；尚未运行降温后的 Direct-native/Released-baseline/Candidate Matrix。Phase 4
负责该基准与最终发版指导。

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
| AndroidX Grid String Span/Skip | 在 `createGrid(...)` 中使用类型化 `ConstraintGridSpan` 与 `ConstraintGridSkip` |
| 把逻辑 Start/End 当作固定屏幕边缘 | 使用显式 Left/Right Anchor、Guideline、Barrier 与物理 Chain Side |

变更后的公开 Surface 属于 Q3：传输不变量、默认值、失败时机、DSL Scope、合并优先级、
Native Mapping 与替代 Sample 都是契约字段。不提供 Deprecated Compatibility Alias 或原始
AndroidX Escape Hatch。

## 性能建议

- Graph 稳定而 Child Content 变化时复用 Constraint Set。
- 保持 Reference ID 与 Helper 类型稳定，以复用生成的 View ID 与实例。
- 稳定的 Equal/Content-only 提交会自动进入分类快速路径，不需要公开优化开关。
- 不需要约束求解时使用更简单 Container；ConstraintLayout 会引入 Solver Pass。
- 避免由高频 State 重建大型 Helper Graph。

Revision 6 Phase 4 矩阵在 10/50/100 Node 上对比 Released、Candidate 与 Direct AndroidX，覆盖
Stable、Scalar、Helper 与 Topology 更新。7 个 Longitudinal Pair 的两条 ViewCompose Arm 均稳定，
并通过全部 Timing 与 Peak-heap Regression 门禁；另有 5 个在唯一允许的成对复测后仍为
`inconclusive`。发版安全结论是 **no material change**，不是全帧优化胜利。12 个 Candidate
Action 的 P95 全部由 Direct Android Views 更快；P50 则有 11 个由 Direct 更快。Helper-100 的
Candidate P50 方向相反，但 P95 仍更慢，因此它是 Mixed，而不是性能领先证据。Phase 1 分类快速
路径继续由精确的零工作与有界写入计数证明，而不是由 Frame-time 声明证明。不得把该 Adapter
描述为 ViewCompose 中最快的 Layout Path，也不得通过重复运行选择有利样本。受控协议、绝对值、
局限与下一步见 [ViewCompose 性能](../../tooling/performance.md#247-constraintlayout-phase-4-controlled-matrix)。

Direct AndroidX Demo Fixture 会为每个 Benchmark Node 绘制可见填充。在 Xiaomi/API-28 参考设备上，
人工复核确认 10 Node 为单行、50 Node 为 `5 x 10`、100 Node 为 `10 x 10`，之后才开始采集 Timing。
每个 Fixture 都提供 Update、Reset 与 Node-count Control；如果页面只有 Control 可见或 Cell 透明，
该结果不能作为 Benchmark 证据。聚焦 App Test 还要求每种 Workload 都存在 10 个可见且不是 Barrier
的 Node。

## 相关文档

- [UI Foundation 模块](../viewcompose-ui-foundation/README.md)
- [Renderer 模块](../viewcompose-renderer-android/README.md)
- [UI Contract 模块](../viewcompose-ui-contract/README.md)
- [源码文档与 API 注释规范](../../project/api-documentation-quality.md)

完整生成参考位于
[`viewcompose-constraintlayout-androidx` API 树](https://docs.viewcompose.com/api/viewcompose-constraintlayout-androidx/current/)。

## 兼容性说明

首发硬切之前的源码快照使用基于 Warning 的局部恢复与分裂的 Helper 所有权。当前源码有意
打破这些行为，不提供第二套 Constraint Solver 或 Compatibility Engine。首发与发版后计划均已
归档；当前契约与性能边界由本模块手册，以及所链接的有效架构、迁移和工具文档负责。
