---
translation_source: modules/viewcompose-renderer-android/README.md
translation_source_hash: ec128ab13f28fbaa51835336c909690053fa2f6fa0a80a5acba7e62ddf7c3e96
translation_status: current
---

# Android Renderer Engine 模块

`viewcompose-renderer-android` 是 ViewCompose 的 Android View 渲染引擎。它把不可变 VNode 快照与已
挂载树进行差分，创建并绑定原生 View，应用定向 patch，驱动 Lazy 容器和 Pager 状态，桥接
shape 与绘图命令，并提供渲染工作量、树结构、布局过程和源码工具链诊断。

应用通常通过 `viewcompose-android` 间接获得本模块。实现自定义 Android host、渲染器
诊断、平台装饰后端，或在脱离组件 DSL 的情况下测试差分逻辑时，可以直接依赖它。

本模块不负责 composition、应用生命周期、状态保存、导航、浮层窗口、图片解码或高级阴影的
具体光栅化。这些职责分别属于 `viewcompose-runtime`、`viewcompose-ui-foundation`、
`viewcompose-host-android` 和可选功能模块。

## 构件与稳定性

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-renderer-android:0.1.0-alpha01")
}
```

- 稳定性：**Alpha**。渲染扩展契约和诊断模型在 alpha 版本间可能变化。
- 平台：Android library，`minSdk 24`、`compileSdk 36`，Java 11 字节码。
- UI Contract 会被传递暴露，因为 Renderer 入口会接收并返回其 Node 与 Modifier 类型。
  Runtime、Text Core、Graphics Core、Gesture Core 与 Animation Core 保持为实现依赖。
  Animation Core 提供唯一物理尺寸 Solver；Renderer API 不公开这项依赖。
- Android 运行时依赖：AndroidX Core、AppCompat、RecyclerView、ConstraintLayout 与
  SwipeRefreshLayout；不依赖 Material Components 或 ViewPager2。
- 通用 Surface、圆角/切角/连续圆角和进度指示器使用引擎自有 Android 绘制实现，并只消费节点解析值。
- `SurfaceNodeProps` 使用缓存的 `UiShapeDrawable` 几何完成纯色或渐变 Fill、可选 Border 与 Ripple
  Mask。View Outline 与可选裁剪几何由一个无 Paint 且缓存 Bounds 的 Provider 提供，不再保留第二个
  完整 Drawable。连续圆角使用凸三次曲线路径；稳定绘制不会逐帧分配 Path、Shader、Drawable 或集合。
- 四角半径一致的 Rounded Rectangle 使用 Android 原生圆角矩形绘制和 Outline 操作，不保留
  `Path`；没有可见 Border 的 Surface 也不保留 Stroke Paint 或 Path。非对称圆角、Continuous
  Corner 与 Cut Corner 仍使用缓存的通用 Path，因此这个常见滚动快路径不会收窄 Shape、Gradient、
  Border、Ripple Mask 或裁剪行为。
- 引擎自有圆角使用圆弧绘制。Shape 边框会沿向内偏移半个线宽的路径居中绘制，保证轮廓完整落在
  逻辑 Drawable 边界内，包括组件在较大触控目标中居中较短可见 Surface 的情况。
- Button 可以请求比有效 View 触控目标更短的可见 Surface。引擎会在 View 内居中其背景、边框、
  涟漪和轮廓，同时不改变测量、命中测试或无障碍边界。显式 Background、Border、Corner Radius
  或 Shape Modifier 会关闭组件提供的内缩，保证应用样式优先。
- 通用交互 Surface 通过已解析 Modifier 接收 `UiInteractionIndication.StateLayer`。引擎在
  现有 Shape 遮罩和可见 Surface 内缩中映射按下、聚焦和悬停值，不选择语义角色或 Material
  透明度。SegmentedControl 与 NavigationBar 因拥有多个内部目标，通过 NodeSpec 接收完整的
  已选和未选状态层值。NavigationBar 会在完整 Item 目标的前景绘制各 Item 状态层，因此选中
  Indicator、Icon、Badge 和 Label 都不会遮住按下、聚焦或悬停反馈。当点击同步改变选择状态或
  主题颜色时，NavigationBar 与通用交互 Surface 都会原地更新已保留 Ripple 的颜色 Selector，
  而不是替换 Drawable，因此正在执行的释放动画仍然可见；这也覆盖基于 BasicSurface 的纯文案导航。
- 通用集合语义会映射为 AndroidX 无障碍集合元数据。父节点负责行列数量和选择基数，子节点负责
  逻辑位置和跨度；已有的 `selected` 与 `heading` 语义仍是 item 状态的唯一事实来源。
- 当前版本构建基线：Kotlin 2.0.21、Android Gradle Plugin 8.13.2。

## 渲染模型

```kotlin
var mounted = ViewTreeRenderer.renderInto(
    container = container,
    previous = emptyList(),
    nodes = firstFrame,
).mountedNodes

mounted = ViewTreeRenderer.renderInto(
    container = container,
    previous = mounted,
    nodes = nextFrame,
).mountedNodes

ViewTreeRenderer.disposeMounted(container, mounted)
```

已挂载节点列表是所有权令牌，不是可有可无的缓存。host 必须把上一次成功帧返回的原始根节点
传回同一个容器和渲染器。具有稳定 key 的同级节点在重排后仍可保留原生 View；无 key 节点
只会在相同 index 和类型下复用，防止平台状态在外观相似的 item 之间静默转移。

渲染在结构变更阶段具有事务性。差分、View 创建或绑定失败时，流水线恢复上一棵 View 树并
重新抛出错误。Android View 生命周期回调和延迟释放在结构提交后执行；由于新的可见树此时
不能安全回滚，其失败会被隔离在 `RenderTreeResult.commitFailures` 中。

完整 Animated Content Replacement 使用一个专用测量 Host 和最多两个专用 Item Host。两个
Item 接收同一组父 Constraint。测量 Host 从最后提交尺寸插值到 Incoming 尺寸，Segment Retarget
时捕获当前尺寸，应用逻辑 Alignment，并可选择裁剪动态 Bounds。Item Host 应用基于实测尺寸的
Translation、Scale Origin、Alpha 与 Reveal Clip。非活动 Outgoing Host 只参与绘制：它拒绝
Pointer 与 Key Dispatch，不提供 Focusable，清除保留 Focus，并使用
`IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS`。这些绑定全部进入普通 Renderer Rollback
事务，因此后续 Node 失败会一起恢复之前的尺寸、视觉与交互 Owner。

真实 Bounds 动画会用一个透明 `DeclarativeAnimatedBoundsHostLayout` 包住完整的 Parent Data 与
布局链。Parent 只布局一次已接受 Target；Host 保留此前物理矩形，并在属性帧提交采样后的
left/top/right/bottom，不重新测量 Child。Child 仍是 Drawing、Input、Focus 与 Accessibility
Owner，因此平台几何会跟随可见矩形。Duration Retarget 从当前采样值以零速度重新开始；物理
Retarget 保留四条边的速度；重复 Target Layout 不会重启 Writer。Renderer Rollback 会同时
恢复此前的 Host Spec 与 Parent Layout Input。Detach 和跨 Owner 的 Reusable-tree Reset 会在
Adoption 前显式清理 Bounds 与 Content-size Animation State，即使平台没有回调 Detach 也是如此。

ConstraintLayout 协调会先编译完整不可变候选，并在接触原生 View 前拒绝无效 ID、Reference、
Anchor Plane、Helper 依赖、所有权冲突、尺寸与范围。一个注册表拥有 Guideline、Barrier、Flow、
Group、Layer、Placeholder，以及类型化 Grid 有界行/列 Solver Proxy 的稳定 ID、实例、类型变化、
引用和删除。Grid 的语义 Identity 不是原生 View；声明式 CircularFlow 会编译为普通 Per-child
Circle Constraint，不拥有 Helper View 或生成 ID。已接受候选从干净的原生 Set 应用；原生提交
失败会恢复此前的 Helper 注册表、LayoutParams、运行时属性、环境与已接受图。AndroidX `2.2.2`
把已应用 `ConstraintSet` 复制到 `LayoutParams` 时会遗漏 Baseline Margin 与物理 Gone Margin，
因此 Renderer 会在测量前恢复这些精确字段，并重置已移除值，防止其泄漏到下一 Graph。
Group/Layer/Placeholder 效果是叠加在可恢复 Child 运行时属性之上的 Overlay，不会成为下一候选的
事实来源。发版后协调路径还会按 Container 缓存已接受的原始 Spec、Semantic/Resolved Graph、
环境、Topology/Scalar Fingerprint、原生 ID 与 Helper 所有权，并在提交前把更新分类为 No-op、
Content-only、Scalar、Environment 或 Topology。Equal/Content-only 请求会跳过 Graph 编译、环境
解析、原生提交、Helper 写入、Adapter Layout Request 与 Adapter 自有分配；Scalar 请求保留未变
Helper 的实例和引用，不创建/删除 Helper，不克隆 Live LayoutParams，并且最多发出一次 Adapter
Layout Request。可选结构计数器是 Container-local 且仅供内部测试使用；未启用时不拥有 Global
Observer 或周期工作。完整契约记录在
[ADR-0016](../../architecture/decisions/0016-constraintlayout-graph-and-helper-ownership.md)。
2026-08-18 的 API 35 离线聚焦运行通过了 16/16 条 ConstraintLayout Renderer 回归，覆盖原先为
`0 px`、修复后精确为 `125 px` 的 Barrier 几何、拒绝候选时的状态保留与后续有效重试、Group
Overlay 恢复、注入原生提交失败后的回滚与有效重试、Layer 与 Placeholder 释放、Layer
Detach/Reattach 回调所有权、Density 变化、1,000 次 Helper 换型期间恒定的所有权，以及同一 ID
在六种 Helper 间的换型；每种 Helper 的两个声明反转顺序后也会保留相同原生实例。结论为
**improved**。缓存 ConstraintLayout `2.2.1` 与手工 Robolectric Classpath 仅保留为最初缺陷复现
证据；后续 Gradle 8.13 运行实际解析 ConstraintLayout `2.2.2` 与 Core `1.1.2`，并通过全部
451 条 Renderer 测试，其中包含 12 条 Graph 与 16 条 ConstraintLayout 聚焦用例。因此
`2.2.2` JVM 兼容性限制已解除。随后在已 Root 的 Xiaomi MI 6 / Android 9 上完成的矩阵通过
3/3 条测试，覆盖浅色/LTR/字体缩放 1.0、深色/RTL/字体缩放 1.3、全部六种保留 Helper、
200 次 Helper 状态切换，以及 9 张人工复核截图，期间没有非预期 Renderer/Helper Warning。
该轮验收还发现并关闭一处 Android 9 环境切换缺陷：Retained Programmatic Helper 现在会在
Graph Apply 前把 `layoutDirection` 与 Container 同步，使 AndroidX 在 LTR 切换 RTL 后正确解析
逻辑 Guideline Begin/End。Transition 回归与精确镜像真机几何将 Renderer 结论归类为
**improved**。最终 Root 10/50/100 Node 矩阵随后通过了修正后的 Android-Views 归一化纵向门禁，
稳定行没有 Timing 或 Peak Heap 回退。门禁过程中，Renderer 从回滚快照捕获中移除了 O(n²)
Child Index 查询，并在没有 Group/Layer/Placeholder Content Overlay 被释放时跳过重复快照；
topology-50 P50 从失败的 `7.076 ms` 变为 `6.162 ms`，Baseline 为 `6.304 ms`。性能安全结论为
**no material change**；4 个不稳定动作保持 `inconclusive`，Direct Android Views 仍具有明显
P95 优势。更广泛的跨 OEM/API 与性能领先工作属于发版后限制，而不是已观察到的首发正确性缺陷。

2026-08-21 的 Phase 1 验收通过全部 459 条 Renderer 测试，其中包含具名 No-op、Content-only、
Scalar、Environment 与 Topology/Rollback 用例。Equal-input 压力用例在接受 Graph 后执行 1,000 次
分类，Compiler、Environment、原生提交、Helper 写入、Adapter Layout Request 与 Adapter
Allocation Batch 均为 0。Changeset、Release Intent、Development Tooling Isolation 和文档门禁均
通过。固定频率 50 Node 全帧预检仍为 **inconclusive**：Released Baseline 的 Stable/Scalar
Run-P50 CV 是 `0.181`/`0.261`，Scalar 相邻复测仍为 `0.244`；Candidate Stable/Scalar CV 是
`0.212`/`0.143`。只有 Candidate Scalar Arm 达到 `0.15`，因此结果不支持纵向 Timing 结论；在
声称端到端收益前，Phase 4 矩阵必须重新复验。

最终 Revision 6 Phase 4 矩阵取代了该短预检。12 个 Released-to-Candidate Pair 中有 7 个的两条
ViewCompose Arm 均稳定，并通过全部 Direct-normalized P50/P95 与 Peak-heap Regression 行；另有
5 个在唯一允许的成对复测后仍为 `inconclusive`。稳定行把全帧结果归类为
**no material change**：它证明发版安全，但既不是优化胜利，也不是性能领先。12 个 Candidate
Action 的 P95 全部由 Direct AndroidX 更快；P50 则有 11 个由 Direct 更快。这个结果不否定上面的
精确结构计数器；它把这些计数器的声明范围限定为避免 Adapter 工作与有界 Mutation。协议、绝对值、
归一化、局限与下一步记录在
[ViewCompose 性能](../../tooling/performance.md#247-constraintlayout-phase-4-controlled-matrix)。

2026-08-21 的 Phase 2 聚焦 API-35 Robolectric 验收通过全部六条冻结的 `CL-P2-*` Renderer
用例。精确 Bounds 覆盖逻辑 LTR/RTL 与物理坐标下的 Parent、Child、Guideline、Barrier Chain
Boundary；Baseline 普通/Gone Margin 与 Direct AndroidX Control 一致；四种 Parent-wrap Policy
产生各自文档化轴；Physical Link 在环境方向更新时保持固定。Weighted Grid 的 `2 x 3` Graph
恰好保留五个 Proxy；重叠候选会回滚且不改变几何，1,000 次 Add/Remove 替换始终有界。
CircularFlow 与 AndroidX Angle/Radius 几何一致，会拒绝竞争的 Direct Ownership，并在 1,000 次
替换中保持零 Helper Identity。相对已发布 Renderer，结论为 **improved**：它新增冻结的 Phase 2
Transport 原子支持，并关闭 AndroidX Baseline/Physical Margin Copy 遗漏。该证据不是性能对比，
且覆盖 Robolectric 而不是后续 Phase 3 Device/OEM Matrix。

同一份 2026-08-21 Candidate 在已 Root 的 Xiaomi MI 6 / Android 9 上用 `15.674 s` 通过 4/4 条
`ConstraintLayoutReleaseDeviceTest`。新增 Phase 2 用例验证 Weighted Grid Span/Skip 的精确顺序，
并确认生成的 Row/Column Proxy 恰为五个；同时验证四条 Direct CircularFlow Circle Constraint
位于 `78 dp` 半径的四个正方向，且不存在 Helper View。保留 Helper Matrix 仍在浅色/LTR/字体缩放
1.0 与深色/RTL/字体缩放 1.3 下通过，包括 200 次快速状态切换；结构化 Diagnostics 未出现
非预期 Warning。人工复核两张聚焦中文 Demo 截图，未发现重叠、裁切或 Helper Artifact；按进程
过滤的日志也未出现 `UIConstraintLayout`、`ConstraintSet`、Renderer、Helper Layer 或 Fatal 条目。
相对已发布 Renderer，这份真机能力与 Lifecycle 结果为 **improved**。它只覆盖一个 OEM/API 点与
聚焦视觉样本，不代表完整 Phase 3 Configuration/Screenshot Matrix，也不是 Phase 4 性能证据。

2026-08-21 的 Phase 3 验收新增了 App 自有的单一用途 Fixture 与 Mounted-scene Diagnostics，
没有修改 Renderer Production Source、公开 API 或 Inactive-path Work。12/12 张经人工复核的
Paparazzi Snapshot 采用 Pairwise/Orthogonal 组合，覆盖 Phone/Tablet、Portrait/Landscape、
Light/Dark、LTR/RTL，以及 `1.0`、`1.3`、`2.0` 字体缩放；未发现重叠、裁切、几何语义不明确
或方向/主题缺陷。Phase 2/3 合并设备套件在 API 24 以 `16.45 s` 通过 8/8，在 API 36 的最终
聚焦运行中通过 8/8，并在 Google Pixel 4 XL / Android 13（API 33）实体机上以 `26.442 s`
通过 8/8。测试断言精确或容差有界的 Grid、CircularFlow、普通/Gone Margin、四种 Parent-wrap
Policy、Anchor、Dimension、Bias、Direction 与 Chain Geometry，并覆盖 Reorder、Key Reuse、
Detach/Reattach、Density/Direction Recreation、Rejection Rollback 与有效 Retry。未观察到
非预期的 `UIConstraintLayout`、`ConstraintSet` 或未捕获 AndroidX Warning；刻意构造的无效
Candidate 只产生一条有界 Rejection，随后成功恢复。相对只完成 Phase 2 的验收，Renderer 的
配置、视觉、生命周期与 API 兼容性置信度为 **improved**。由于 Renderer 行为没有改变，也没有
运行受控 Timing 对比，性能结论为 **no material change**。局限包括：最终实体机点只有
Google/API 33，另有此前 Xiaomi/API 28 证据；视觉矩阵是 12 个 Pairwise Case，而不是全部
48 个 Cartesian Combination；尚未运行降温后的 Direct-native/Released-baseline/Candidate
Matrix。Phase 4 负责该基准与最终指导。

## 主要 API

- [`ViewTreeRenderer`](https://docs.viewcompose.com/api/viewcompose-renderer-android/0.1.0-alpha01/viewcompose-renderer-android/com.viewcompose.renderer.view.tree/-view-tree-renderer/)
  管理 VNode 到 View 的事务渲染与释放边界。
- Q3 `ViewTreeRenderer.patchObservedProperties` 接受非空、Target 唯一的精确 Mounted Batch。
  它先校验 Property-only Invariant，复用普通 Binder Differ，但跳过 Tree Wrapping 与 Child
  Reconciliation；任一 Patch 失败时回滚此前全部原生绑定。`ObservedPropertyRenderResult`
  刻意不携带替换后的 Mounted Root。
- [`ChildReconciler`](https://docs.viewcompose.com/api/viewcompose-renderer-android/0.1.0-alpha01/viewcompose-renderer-android/com.viewcompose.renderer.reconcile/-child-reconciler/)
  在不修改平台状态的前提下生成插入、复用和移除计划。
- [`LazyListDiff`](https://docs.viewcompose.com/api/viewcompose-renderer-android/0.1.0-alpha01/viewcompose-renderer-android/com.viewcompose.renderer.reconcile/-lazy-list-diff/)
  把稳定 Lazy item key 转换成有序 RecyclerView 更新；身份缺失或有歧义时会主动退化为全量刷新。
- Eager Scroll Container 使用一个 Renderer Connector 发布逻辑偏移、范围、Viewport、方向与运动，
  并在布局前保留 Pending Command。Pager Container 使用框架自有 RecyclerView、
  LinearLayoutManager、PagerSnapHelper 和一个停稳状态协调器完成观察与 Callback 去重。空闲
  重布局不是页面选择，也不会清除当前页焦点。纵向 Eager Container 嵌套在同轴且不支持
  Nested Scrolling 的 Parent 中时，
  只在自身仍能消费当前方向期间保留 Pointer Stream，并在对应滚动边界把 Stream 交还 Parent；禁用
  用户滚动时绝不会保留 Parent Stream。
- Adaptive Grid 会根据可用内部宽度与密度重新计算 `GridLayoutManager.spanCount`，不替换 Adapter
  或 Keyed Session。Span Lookup 按当前列数解析 `FullLine`，并安全限制固定 Span。
- 最大尺寸与宽高比 Modifier 会在完整映射节点外安装一个合成测量 Host。该 Host 是 Renderer
  所有的基础设施，不是语义 Child，也不会产生第二个逻辑 Session。
- Animated Content Size Node 会安装一个合成测量 Host。时长契约使用保留的 Android
  `ValueAnimator`；物理 Spring 契约使用 Animation Core 解析式 Solver，在 Retarget 时保留宽高
  速度，并仍在每个接受的平台 Frame 请求一次 Layout。Keyframe 只在创建 Animator 时排序一次。
- `RenderTreeResult`、`RenderStats`、`RenderStructureStats`、patch 记录和布局过程采样提供不可变
  诊断数据，供 demo、预览工具和性能测试使用。
- Q3 `RenderTreeTimingCollector` 是一次有限请求使用的可选同步端口。整树 Reconciliation 会报告
  嵌套节点区间，Binding 报告直接 Native-property Work；Observed-property Patch 使用同一 Binding
  边界。Renderer 会保留已有 Composition Identity，只为活动请求中遇到的 Renderer-created Node
  分配不透明负 Fallback。Collector 为 `null` 时，逐节点时钟读取、Identity 分配与计时记录工作都为零。
- [`AndroidViewDecorationBackend`](https://docs.viewcompose.com/api/viewcompose-renderer-android/0.1.0-alpha01/viewcompose-renderer-android/com.viewcompose.renderer.decoration/-android-view-decoration-backend/)
  是高级阴影等普通 View 状态无法表达的效果的可选 SPI。没有后端时，装饰请求走空操作路径，
  也不会加载阴影实现。
- `AndroidUiShapeDrawables.solid` 是供已经持有原生容器的下游 Platform Presenter 使用的 Q2 Android
  边界。它把不可变逻辑 `UiShape`、ARGB 颜色、捕获的布局方向与密度转换为调用方新持有且感知边界
  的 Drawable；Renderer 不负责语义主题查询或 Presenter 生命周期。
- `ViewDecorationHostLayout` 和 `DecorationChildDrawingOrder` 支持自定义绘制平面与声明式
  `zIndex`，无需为每个 child 再包一层 View。
- `ViewNodeToolingRegistry` 仅在工具元数据存在时，以弱引用方式关联 View 与源码信息；普通
  渲染不会额外持有源码对象。
- Renderer 自有子容器不携带诊断身份。UI Foundation 在创建 Pager 或 Lazy 子项时分配新的逻辑
  Session ID 与类型化角色，因此复用容器不会转移所有权。可选源码工具只捕获符合条件的运行时角色。
- 图片节点在存在 loader 时，会把 `UiImageRequest` 绑定到注入的 `UiImageLoader`。渲染器把可
  释放句柄存放在挂载的 `ImageView` 上；等价 request 会保留已有句柄和已加载 drawable。Request
  变化时，渲染器先释放旧工作，再应用 placeholder 并启动替换工作；移除、回滚和 Session 释放
  时也会清理句柄。Request 会携带节点捕获的密度，使适配器解析固定 `UiDp` 解码边界时与布局
  保持一致。没有适配器时 Resource source 仍可直接渲染；空 source 直接绑定 fallback。即使支持
  装饰效果的布局 host 会允许子 View 越界绘制阴影等效果，图片内容仍始终裁剪在 `ImageView`
  的 padding 边界内。

完整生成参考位于
[`viewcompose-renderer-android` API 树](https://docs.viewcompose.com/api/viewcompose-renderer-android/current/)。
当前版本仍为 alpha，因此文档站不会提供稳定的 `latest` 别名。

## 身份与 patch 规则

- 有 key 的 child 只有在 key 与 `NodeType` 同时一致时才能复用上一轮 payload。同级 key 必须
  稳定且唯一。
- 无 key 的 child 只复用相同 index 和类型上的 payload。因此，无 key 的有状态内容重排属于
  语义替换，而不是 move。
- Lazy 列表精确差分要求每个 Item 都有唯一且非空的 Key。重复 Key 或无效 Table Update 会在发布前
  拒绝候选，并保持已经安装的 Adapter Snapshot 不变。
- Lazy Adapter 会先消费 Table 中已校验的直接 Range Update，再通知 RecyclerView。未提供直接更新的
  有限 Table 进入兼容路径：Key 顺序相同时无需运行
  `DiffUtil`，而是批量发送相邻原生变更；相同大小的循环排列会选择移动次数较少的左移或右移
  序列；其他结构变化仍使用 AndroidX Diff。逻辑 Item Session 仍同步消费变化的 Revision。
  Item 动画关闭时，纯语义更新因此不会产生冗余 RecyclerView Bind；若直接 Session Commit
  返回 false 或抛出异常，只会为对应的已 Attach 位置补发一次 Payload 重试。抛出的失败会在其余
  已 Attach Holder 都完成尝试且 Sticky 元数据追上已发布快照后再向上传播。通知规划绝不改变
  Key 所有权、Content Type 兼容边界或失败恢复语义。
- Horizontal 与 Vertical Pager Holder 会在复用期间保留 `Page` 源码 Session 角色。RecyclerView
  行与 Tab Item 保持 `Content`；该角色不影响 Key、差分、测量、可见性或回调。
- Lazy Item 的 `contentRevision` 与框架捕获的 `environmentRevision` 是标识和 Type 之外仅有的内容
  失效输入。Key 与 Revision 相等时完全跳过 Item Composition 与原生 Patch，即使父层创建了新的
  Strategy 或 Payload。Revision 变化会让 Item 的共享 Strategy 安装最新 Payload，并只 Render 该
  Item；调用方必须使用可观察 State，或把每个变化普通捕获值放入 `contentRevision`。即使 Key 与
  Revision 不变，只要 `contentType` 改变，也会终止旧 Child Session 并完整重建原生呈现。Holder
  Create 与 Update 会直接调用 Strategy，Bind 路径不会分配 Item 专属 Callback Adapter。
- Detach 且从未 Activate 的 Lazy Holder 可以在 RecyclerView Prefetch 中 Prepare 子 Composition
  与原生 View 树，但不会提交 Remember Lifecycle、Effect、原生 Commit 工作、Overlay 或诊断。
  首次 Attach 会直接 Activate 有效 Prepared Frame；如果被观察 State 已变化，则改为渲染当前
  状态。Active 的 Detach Holder 会暂存新 Submission 并在 Reattach 时渲染。低层 Key 重复时
  使用保守 Reload 路径；公开 DSL 拒绝缺失或重复 Key，Renderer 绝不会通过 First Match 查询猜测。
- 发布新 Item Table 时，会立即终止 Key 已删除或 Kind/Content Type 已不兼容的每个 Detached
  Cache Holder。Holder 会在 Dispose 前移出 Registry 所有权，因此 RecyclerView 后续 Recycle
  不会二次 Dispose 同一个逻辑 Session。Page Drop 的所有权清理由此不再依赖旧 Item 提交更新时
  是否恰好处于 Attach；Key 仍保留且物理结构兼容的 Holder 会继续暂存 Revision，直到重新 Attach。
- Lazy Adapter 把唯一 Key 的位置查询委托给已接受的 `LazyItemTable`。已 Attach 或重新 Attach 的
  Holder 因而无需扫描 Item Table 即可解析稳定 Key。Payload Bind 只有在 Holder 已提交完全相同的 Item
  快照实例和完全相同的 Submission Revision 时才能跳过 Session 路由；仅 Revision 相等并不足够。
  这条确认规则可以防止队列中的 RecyclerView 通知把较早的逻辑提交误判为当前提交。
- Renderer 分配的 Stable ID 可处理冲突，并且只为 RecyclerView 实际查询的 Key 延迟分配；下一个
  Table 不再包含的已查询 Key 会被释放。Renderer 不会枚举紧凑 Table 来预建 Key 或 Stable ID
  Array。可选 Sticky Header 元数据同样避免扫描紧凑 Table；没有该元数据的 Table 承诺不含 Sticky
  Item。紧凑 Registry 在 Mounted Adapter 生命周期内保持 View Type 身份，且不创建 `Pair` Key 或
  装箱 ID。由于 `contentType` 是有限的物理兼容分类，一个已挂载容器最多
  接受 1,024 种不同的 kind/type 组合；更大的历史会在无界增长前被拒绝。
- Lazy List 与 Pager Holder 会在 Holder 生命周期内缓存 Container Handle，并直接调用专用 Session
  Host 与 Declaration 共享的 Item Strategy。原生复用仍按 Key 切换逻辑 Session 所有权；该调整只
  移除 Callback Wrapper 分配，不会合并物理与逻辑身份。
- Pager 稳定 ID 使用 Renderer 分配值而不是 key hash。Pager View Type 按不兼容的
  `contentType`/kind 组合划分；带 key 的移动只刷新归属唯一且已变化的 Holder，每个公开 Page 声明
  都必须提供唯一稳定 Key。除非调用方显式指定正数 Limit，否则由 RecyclerView 默认缓存策略管理
  离屏驻留。
  已接受的 Pager Submission 即使 Page Snapshot 不变也必须应用 `currentPage`；页面内容 Diff 绝不能
  阻断目标页面选择。
- 定向 patch 和子树跳过只是优化。只有每个直接 child 都是组合所复用的完全相同 VNode 实例时，
  才能跳过完整原生子树；新构建但值相等的 child 仍需调和，因为嵌套 Session 回调可能已变化。
  自定义 host 不得从 patch 记录或诊断计数推断业务状态。
- 当 Type、Environment 与 NodeSpec 均未变化时，Modifier 变化会走仅 Modifier Patch。该路径保留
  原生 View 与语义 Node 绑定，复用现有的分族 Modifier 差分，并继续调和 Child。纯视觉变化会
  保留现有 LayoutParams；布局或 Parent Data 变化才会替换参数。`NativeViewElement.stableKey`
  变化时会重新执行其配置，而 AndroidView 的 Update、Reset、Commit 与 Release Callback 均不
  会被触发。诊断会把该路径记为定向 Patch，Detail 为 `ModifierOnly`。
- AndroidView 协调会分离逻辑身份与构造身份。普通的同构造身份更新会重新绑定保留 View，且不
  调用 Reset。构造身份变化时，Renderer 会先创建并绑定一个尚未挂载的候选节点，再在结构提交时
  交换节点，并恰好释放一次被替换 View。候选或后续 Sibling 失败时会恢复旧 Container，且只释放
  候选。有限 Patch Detail 会报告 Adapter 名称、构造代次、复用策略、Lifecycle Mode 及本次是否
  替换，不会保留原生 View。Lifecycle Mode 只供观察；Renderer 不安装或驱动 Owner。
- Shared-content Modifier 解析会把完整类型化端点 Element 写入一个稳定 Keyed View Tag，并在
  Modifier 消失或 View 复用时清除。Renderer 不执行 Tree Scan、Bitmap Capture、Overlay
  Allocation、Animation Scheduling 或 Navigation；这些有界的转场期操作由消费它的 Android
  Navigation Host 拥有。
- 物理 Padding、Margin、Offset 与 Inset 选择器保持 left/right 语义。对应 `Relative` API 会在
  每次 Bind 或环境重绑时根据 VNode 捕获的布局方向映射逻辑 start/end。同一族中后声明的物理或
  相对值会整体替换先声明值。正 `offsetRelative.horizontal` 朝逻辑 end 平移且不改变测量。
- Gesture 分发会保留尚未判定的 Pointer Stream，直到识别出 Drag。若 Stream 结束时没有被 Gesture
  消费，保留目标会收到一次普通 Click；已识别的 Drag 会消费 Stream 并抑制该 Click。
- Renderer 所有的 eager 与 lazy 滚动容器只会在自身能够消费相应方向位移时保留轴向一致的
  Pointer Stream。容器会释放交叉轴位移，并在对应逻辑边缘把位移交给祖先。垂直 child 位于顶部
  且祖先是启用、空闲的 `PullToRefresh` 时，会把初始向下拖动交给刷新 Host 完成阈值手势。
- `FlowColumn` 使用相同的可用交叉轴宽度测量每个 child，已经完成的列不会缩减后续列的可用宽度；
  `FlowRow` 对 child 高度执行对称规则。自然 Flow 内容仍可超出受限交叉轴，但不会仅因前面行列
  已占用空间而被压缩。
- Button Surface 内缩变化会参与定向样式 Patch，不得因此重建原生 View 或改变其有效测量目标。
- Basic Surface 使用相同的有效/可见边界模型。Surface 快照变化会对保留的
  `DeclarativeBoxLayout` 执行中立重绑定；调用方 Background、Border 或 Shape Modifier 会移除
  组件提供的可见内缩，并占满有效边界。
- 引擎创建的 Box 与 Surface 容器不执行 XML 属性解析。没有显式 `BoxScope.align` 的子项会在
  LayoutParams 中保留继承内容对齐标记，因此内容对齐 Patch 只更新这些子项，不再在每次布局时
  扫描全部子项；显式对齐的子项保持不变。
- Indication Modifier 变化只执行 Modifier Binding。Surface 结构变化只重建已保留 View 中
  受影响的 Drawable；仅颜色变化的 Indication Patch 会原地更新已保留 `RippleDrawable` 的
  Selector，因此同步的选中状态 Patch 不会中断快速点击的释放动画。SegmentedControl 只重建
  受影响的内部背景；NavigationBar 会保留各 Item 的前景 Ripple，并更新其已选或未选状态层颜色。
  按下优先于聚焦和悬停，聚焦优先于悬停；非活动或禁用的高层目标不安装 Indication。Android
  单值 Ripple 回退只保留在低层 Renderer 私有实现中。
- Slider 绑定使用渲染器中性的 `AppCompatSeekBar` 子类，因为平台控件可能在 `AT_MOST` 测量
  规格下忽略 `minimumHeight`。它会遵守已声明的最小值，同时让应用或父容器的精确高度保持
  最终权限；Android Renderer 不解释任何 Material 策略或 Token。
- 原生 Switch 与 Slider 绑定通过 `SRC_IN` 应用每个已解析 Tint，从而保留平台或 OEM Drawable
  遮罩。Slider 分别持有激活轨道、非激活轨道和 Thumb Tint，定向 Patch 可在不重建 View 的
  情况下更新非激活轨道。当受控 Callback 接受原生 Switch 已提交的值时，定向 Patch 不会再次
  写入相同值，因此平台或 OEM 的 Thumb Transition 可以继续执行。在独立且经过测试的自定义控件
  契约被接受前，平台 Drawable 几何及其内建覆盖率仍具有最终权限。
- 集合行列索引是从零开始的逻辑位置。Android 在 RTL 中反向排列后代时，Renderer 不得反转这些
  索引。选中态和标题态读取 item 已有的语义字段，防止组件通过重复契约暴露相互矛盾的无障碍状态。
- Text 对齐只更新 Gravity 的水平位。FlowRow、FlowColumn 与 TabRow 在 RTL 中镜像物理排布，
  同时保留逻辑 Callback 与无障碍索引。
- NavigationBar、SegmentedControl 与 TabRow 会发布单选父集合元数据和 Item 位置。Keyed
  Navigation/Segment View 可以在同一容器内复用，但 Label 或 Index 绝不充当逻辑身份。
  Density 或布局方向变化时，SegmentedControl 会重建内部 Shape Drawable，避免解析后的圆角继续
  持有过期环境。

## Android host 与线程规则

每个 VNode 绑定都包含捕获的资源版本。版本变化时，即使 NodeSpec 和资源 ID 相等，也会执行正常的
完整重绑。直接 Drawable/Icon 资源会从节点当前 Context 再次解析；只要 Source、Placeholder、
Error 或 Fallback 使用资源，规范化图片请求就会把版本传给 Adapter。纯远端请求保留普通请求标识。

文本节点未显式设置 `lineHeightSp` 时，会保留原生 View 的行距参数，而不是复用在旧字号下捕获的
固定像素行高。因此，自然行高会在 View 复用和环境重绑期间随已解析字体、字号与字体缩放变化；
显式 `lineHeightSp` 仍具有最终权限。

纯文本 `TextDocument` 会直接绑定其已有的 `String`。富文本文档仍会物化
`SpannableString`，因此 span 应用继续隔离在富文本节点中，普通 Text patch 则避免一次
多余的平台包装分配。

对于 Lazy 集合，Renderer 统一持有一份合成后的原生 Padding：逻辑 `contentPadding`、已解析的
物理或相对 Modifier Padding 与选定的系统栏/IME Insets 按边相加。所有逻辑 start/end 都根据
捕获的布局方向解析。方向变化同时改变相对 Inset 选择器时，Renderer 会立即使用可用的 Root
Insets；若尚不可用，则先清除旧物理边贡献直至 Android 分发新快照，绝不带着旧边多渲染一帧。

- 渲染、释放、View 绑定、Pager 更新和装饰回调都限制在 UI 线程。
- 一个容器只有一个已挂载树所有者。不得在容器或 render session 之间共享 mounted node。
- `collectDiagnostics = false` 会省略结构、Patch 与 Warning 快照；
  `collectStatistics = false` 还会省略聚合绑定计数。Host 渲染会从
  `RenderFrameDiagnosticLevel` 独立映射这两个开关，因此 `Stats` 不会构建诊断树。
- Lazy Prefetch 工作受 RecyclerView Deadline 控制。冷启动 Activate 同时包含 Commit 与 Effect
  工作，因此只提供保守的启动上界；首次 Detach Prepare 会用权威的准备成本替换该估计。估计会
  保留昂贵样本，并只在仍可执行 Prepare 时通过后续更便宜的样本缓慢衰减。一次超预算的权威样本
  会在 Adapter 释放前禁止该 Content Type 的后续推测准备，使其回到 Staging 而不再拉长 Fling
  尾部。这能把有界工作提前到 Attach 之前，但不保证完成准备。
- `LayoutPassTracker` 是进程级可选能力。它会为受监控过程增加单调时钟读取和同步聚合开销，
  应用于有限时间的诊断，而不是持续生产遥测。
- `AndroidViewDecorationRuntime.install` 是进程级操作。应在应用初始化时安装后端；现有 View
  只有在下一次绑定装饰请求时才会切换。
- 装饰 host 不增加 per-child wrapper。无装饰的常见路径只经过一次分支就委托给普通 View
  绘制；有装饰的 child 仅为实际申请的绘制平面执行索引后的后端分发。
- `Row` 与 `Column` 会把直接子级的 Animated Visibility Host 视为渐进式间距参与者。主轴 Item
  间距随 Host 的测量尺寸 Channel 一起展开和收起；中间 Host 完全折叠时，稳定同级元素之间原有
  的间距仍会保留。
- `DeclarativeAnimatedVisibilityHostLayout` 会以完整尺寸测量每个直接 Child，向父级报告动画
  Reveal 尺寸，并根据完整 Host 测量而不是第一个 Child 计算逻辑 Slide 比例与 Transform Pivot。
  它先应用 Reveal Alignment 与裁剪，再应用原生视觉 Scale/Translation。父级和后代 Host 是普通
  嵌套原生 Layer，因此后代局部 Transform 会先于父级 Layer 组合，不增加 Renderer Frame Owner。
- 接受 Exit 时，Visibility Host 会在移除保留绘制内容之前先变为 Inactive。Host 会阻止 Pointer、
  Hover、Key、焦点遍历与无障碍事件所有权，清除后代焦点，并在反向时恢复参与。Renderer Patch
  失败会一起恢复 Alpha、Reveal、Slide、Scale、Pivot、Alignment、裁剪与 Active 所有权。
- 可见性进入稳定隐藏态后，空 Host 仍会作为零尺寸的调和身份锚点挂载。其内容子树已经移除，
  但稳定的 Host 会让后续无 Key 同级元素在可见性切换间保留原生 View 身份与交互状态。
- Animated Size Host 会在 Detach 时取消活动 Animator。首次测量直接应用；后续 Target 从当前显示
  尺寸开始。物理 Retarget 携带最后采样的每秒像素速度；时长 Spec Retarget 会重置速度。父级测量
  Constraint 在每帧保持权威。

## 相关文档

- [当前架构与模块边界](https://docs.viewcompose.com/zh-CN/architecture/overview)
- [VNode 与渲染器注册](https://docs.viewcompose.com/zh-CN/architecture/node-spec)
- [渲染失败与提交语义](https://docs.viewcompose.com/zh-CN/architecture/render-failures)
- [Lazy 容器指南](https://docs.viewcompose.com/zh-CN/guides/lazy-collections)
- [阴影与装饰指南](https://docs.viewcompose.com/zh-CN/guides/shadows)
- [图片加载指南](https://docs.viewcompose.com/zh-CN/guides/image-loading)
- [源码文档与 API 注释规范](https://docs.viewcompose.com/zh-CN/project/api-documentation-quality)

## 兼容性说明

`0.1.0-alpha01` 建立重命名后不依赖 Material 的 Android Renderer 坐标，并继续承载差分、
原生绑定、诊断、工具关联和装饰后端契约。不要把
mounted node、patch 记录、诊断树对象、不透明 Lazy content token 或 View tag 作为外部长久
数据持久化。即使应用 DSL 源码仍能编译，自定义 host 和装饰后端也必须随渲染器契约变化升级。

发布 Shared-content 端点 Tag 是新增 Q3 UI Contract Marker 对应的 Renderer 内部行为。独立版本
的自定义 Host 必须同时使用文档化的稳定 Tag Key 与类型化 Element；只解释 String Value 或在复用
后继续保留 Tag 都违反契约。

为 Renderer 自有子容器 Handle 增加纯工具用途的页面/内容角色，是基于新增 UI Contract 标记的
内部行为变化。渲染输出与公开 Renderer 签名不变；自定义 Renderer 可以在其子 Session 表示页面
边界时采用同一标记。

Renderer 的多状态路径实现通用 UI Contract，并非 Material 功能。采用 `UiStateLayerColors` 的
自定义 Renderer 必须保留启用态优先级与透明非活动态；收到空值的 Renderer 可以继续使用其已有
单色兼容路径。

消费集合语义的自定义 Renderer 必须保留逻辑行列顺序，并把 item 跨度、选中态和标题态映射为
等价的平台无障碍元数据。alpha 阶段尚未识别这些可空集合字段的 Renderer 可以忽略它们，但其
无障碍输出将无法播报集合位置。

相对 Modifier API 族只能根据每个 VNode 环境解析。Renderer 分支必须同时升级折叠、LayoutParams、
平移与 Inset 选择路径；从进程 Configuration 映射或重新解释旧物理元素都会违反公共 UI Contract。

原生控件契约收敛删除 Renderer 本地 Pager State，直接消费 Q3 UI Contract State。Renderer 分支
必须在 Scroll/Pager Connector 替换和释放时断开连接，只在 Idle 停稳后发送 Pager Callback，遵守
`userScrollEnabled`，实现 Slider 交互阶段与步长，在禁用刷新时保留后代输入，并映射新的 Keyed
选择 Item 语义。Grid Policy 与 Layout Constraint Host 同样要求升级注册和测量；不得把它们当作
可忽略 Hint。

Q3 `LazyItemTable` 硬切改变了 Lazy NodeSpec 的二进制契约。Renderer 分支必须消费索引查询、原子
拒绝无效声明操作、在不依赖应用 Key Hash 的前提下保留 Stable ID，并在紧凑 Table 提供直接更新时
避免完整枚举。有限 Table 仍可请求通用 Diff。新增第二个 Adapter、Paging 专属 Diff Owner 或完整
位置 Placeholder Array 都会违反 Renderer 所有权。

Animation Phase 1 Alpha 新增 `viewcompose-animation-core` 实现依赖，并把 Animated Size Transport
硬切为有限 Spec。自定义 Renderer 分支必须通过同一或等价 Solver 消费物理
Damping/Stiffness/Safety Guard，在 Spring Retarget 时保留速度，并拒绝无限 Layout Motion。
继续在 `spring` 名称下使用旧固定时长阻尼 Interpolator 不兼容。
