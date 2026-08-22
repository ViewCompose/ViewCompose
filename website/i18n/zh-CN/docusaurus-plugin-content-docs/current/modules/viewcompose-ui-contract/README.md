---
translation_source: modules/viewcompose-ui-contract/README.md
translation_source_hash: 029e123a7ae971c26772cf62897b346184948c4dedd96e8a7fe2cb8e1affc4ed
translation_status: current
---

# UI 契约

`viewcompose-ui-contract` 定义 ViewCompose DSL 模块与渲染器共享的平台无关模型：不可变虚拟
节点与节点规格、有序 Modifier、环境值、布局单位、交互契约，以及连接渲染器的 Lazy 容器和
Pager 状态。

开发自定义渲染器、宿主桥接、工具集成，或者需要在可复用 API 中暴露 ViewCompose 契约类型
时，可以直接使用本模块。普通应用 UI 通常通过 `viewcompose-ui-foundation` 传递获得它。

本模块不负责组合 DSL 树、创建 Android `View`、协调节点、调度帧，也不负责 Android 生命周期
和状态保存集成。这些职责分别属于 Runtime、Widget、Renderer 与 Host 模块。

## 产物与稳定性

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-ui-contract:0.1.0-alpha04")
}
```

- 稳定性：**Alpha**。Alpha 版本之间可能发生源码和二进制不兼容变更。
- 平台：Kotlin/JVM，使用 Java 11 工具链编译；不依赖 Android SDK 或 AndroidX。
- 传递暴露的契约族：来自 `viewcompose-text-core` 的平台无关文本/编辑模型，以及来自
  `viewcompose-graphics-core` 的绘图模型；二者都出现在公开 UI Contract 签名中。
- `viewcompose-runtime` 保持为实现依赖。
- 本版本构建基线：Kotlin 2.0.21。

## 最小契约示例

```kotlin
val gap = VNode(
    type = NodeType.Spacer,
    key = "content-gap",
    spec = EmptyNodeSpec,
    modifier = Modifier
        .size(24.dp)
        .testTag("content-gap"),
)
```

这会创建一个与渲染器无关的语义节点。兼容渲染器负责解析 `NodeType.Spacer`、验证其
`EmptyNodeSpec`、按顺序解释 Modifier 链，并持有为该节点创建的所有原生对象。

## 主要 API

- [`VNode` 与 `NodeType`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha03/viewcompose-ui-contract/com.viewcompose.ui.node/-v-node/)
  定义不可变树内容与渲染器分派。Q3 `VNode.observedPropertyId` 是仅供完整帧发布精确 Renderer
  Target 使用的不透明 Session 身份；直接构造 VNode 时保持 `null`，它不会替代语义 Key，也不
  参与普通内容语义。
- [`NodeSpec`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha03/viewcompose-ui-contract/com.viewcompose.ui.node.spec/-node-spec/)
  及其具体属性快照定义渲染器支持的输入。
- `TextNodeProps` 只携带一份权威 `TextDocument`；`ButtonNodeProps` 与 `ToggleNodeProps`
  携带可空的纯文本 `String` 标签。可变或平台特有的 `CharSequence` 实现只在平台 Renderer
  边界转换。
- [`Modifier`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha03/viewcompose-ui-contract/com.viewcompose.ui.modifier/-modifier/)
  携带有序的布局、绘制、交互、语义、焦点与 Parent Data 元素。
- `paddingRelative`、`marginRelative`、`offsetRelative`、
  `systemBarsInsetsPaddingRelative` 与 `imeInsetsPaddingRelative` 是 Q3 坐标和 Android 边界
  契约。其逻辑 start/end 根据每个 VNode 捕获的布局方向解析；原 API 保持物理 left/right 语义。
  已编译的 `relativeLayoutModifierSample` 展示完整 API 族。
- [`UiEnvironmentValues`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha03/viewcompose-ui-contract/com.viewcompose.ui.environment/-ui-environment-values/)
  捕获子树的密度、语言标签与逻辑布局方向。
- [`LazyListState`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha03/viewcompose-ui-contract/com.viewcompose.ui.state/-lazy-list-state/)、
  Q3 `ScrollState` 与 Q3 `PagerState` 分别把 Lazy、Eager 与页面式平台滚动桥接为不可变可观察
  Snapshot。Connector 只允许一个活动 Renderer Owner，替换或释放时断开，并明确区分立即命令与
  动画命令。
- Q3 `GridCells.Fixed` 与 `GridCells.Adaptive` 定义物理列数计算，不暴露 Android LayoutManager。
  Q3 `GridItemSpan.Single`、`Fixed` 与 `FullLine` 在自适应网格改变列数后仍保持语义；编译样例
  `gridPolicySample` 覆盖该策略模型。
- `maxWidth`、`maxHeight` 与 `aspectRatio` 是通过 `NodeType.LayoutConstraintHost` 实现的可移植
  测量 Modifier。自定义 Renderer 必须用一个测量边界约束完整节点，遵守父级传入的精确约束，
  其余情况下应用声明的最大值，并在可行区间内保持请求的宽高比。
- Q3 ConstraintLayout 传输契约为每个轴使用一个互斥的 `ConstraintDimension` 值，以
  `ConstraintMatchMode` 表达 spread/wrap/percent 行为，并使用正数类型化 `ConstraintRatio`
  与单一 Baseline Link。逻辑 Start/End 与物理 Left/Right Anchor 保持独立；
  `ConstraintWrapBehavior` 按轴选择 Wrap-parent 贡献。Chain Transport 携带类型化 Boundary
  Target 与 Margin。类型化 Grid 携带有界 Axis、Weight、Gap、Span 与 Skip；声明式 CircularFlow
  则携带显式 Center/Radius/Angle 值，不要求 Helper View。该 Transport 不依赖 Android，不包含
  `match_parent`、独立尺寸标志、原始 Ratio 语法或 AndroidX Grid String Grammar。跨节点的
  Identity、Reference、Ownership、Topology 与 Range 错误会在平台 Renderer 边界拒绝完整候选，
  而不是弱化单条 Link。
- `NavigationBarItem` 与 `SegmentedControlItem` 必须提供显式且唯一的逻辑 Key。非空集合的
  NodeSpec 要求选中索引位于范围内，空集合使用 `-1`；Navigation Badge 是可空的非负值。
- `LazyListItem` 是 Q3、Renderer 中立的 Snapshot/Session 契约。逻辑相等由 Key、`contentType`、
  调用方 `contentRevision`、框架 `environmentRevision`、Kind 与 Span 构成，Callback 身份被明确
  排除。Key 与 Revision 相等时完全跳过 Session；Revision 变化只更新该 Session，而
  `contentType` 变化会终止旧 Session 并要求完整重建呈现。Q3
  `prepare` → `activate` → `render` → `disposeForReuse`/`dispose` 协议允许 Renderer 构建对外静默
  的候选、结束 Key 所有状态，并只转移已 Reset 的物理呈现。编译样例
  `lazyListItemSessionUpdateSample` 展示此生命周期。`activate` 与 `render` 的 Boolean 结果只在
  已安装内容 Commit 后推进语义 Revision；Rollback 返回 `false` 并保持可重试。
- [`FocusRequester`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha03/viewcompose-ui-contract/com.viewcompose.ui.focus/-focus-requester/)
  与 [`NestedScrollDispatcher`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha03/viewcompose-ui-contract/com.viewcompose.ui.gesture/-nested-scroll-dispatcher/)
  为焦点和嵌套滚动定义明确的渲染器连接边界。
- `UiStateLayerColors` 携带已经解析的按下、聚焦和悬停 ARGB 值，不把设计系统角色或透明度策略
  写入 Renderer 契约。
- `SemanticsCollectionInfo` 与 `SemanticsCollectionItemInfo` 是 Q3 不可变快照，用于描述集合的
  逻辑维度、选择策略和子项位置。自定义 Tab、Navigation、SegmentedControl、List 与 Grid
  可以借此保留平台无障碍位置播报，同时不把设计系统写入 Renderer。
- `SurfaceNodeProps` 是 `NodeType.Surface` 的 Q2 已解析契约。它携带 Graphics Core Brush、
  逻辑 Shape、Border、状态层、有效最小尺寸、可选的居中可见高度和裁剪策略，不携带设计系统标识。
- `UiNodeTooling.withFirstSourceCapture` 是 Q3 同步工具边界，会报告代码块发出的首个有效节点的
  由近及远源码调用链。它不同于完整预览捕获，不会分配节点 ID，也不会在已发出的树上保留元数据。
- `UiNodeTooling.withSourceCandidateCapture` 是对应的 Q3 页面源码工具边界。它会在一次成功的树
  构建中保留有界的首批与最近源码链，让工具区分共享 Scaffold 外壳和 content DSL，同时不标注
  VNode 树。
- `UiSourceSessionContainerHandle` 是 Q2 纯工具用途的 Renderer 容器标记。其 `Host`、`Page` 与
  `Content` 角色让源码导航把 Pager 目标视为页面边界，又不会让更深的普通 Lazy 行替代所属页面。
- [`ImageSource`](https://docs.viewcompose.com/api/viewcompose-ui-contract/current/com.viewcompose.ui.node.media/-image-source/)、
  [`UiImageRequest`](https://docs.viewcompose.com/api/viewcompose-ui-contract/current/com.viewcompose.ui.node.media/-ui-image-request/)
  与 [`UiImageLoader`](https://docs.viewcompose.com/api/viewcompose-ui-contract/current/com.viewcompose.ui.node.media/-ui-image-loader/)
  定义平台无关的图片来源、请求策略、平台目标和可释放加载句柄。
- Unit、Shape、Graphics、按键输入、手势、Semantics 与 Tooling 包共同组成 ViewCompose 模块
  使用的平台无关词汇体系。

完整生成参考位于
[`viewcompose-ui-contract` API 树](https://docs.viewcompose.com/api/viewcompose-ui-contract/current/)。
由于当前版本仍为 Alpha，文档站不会提供稳定的 `latest` 别名。

## 契约与生命周期规则

`UiEnvironmentValues.resourceRevision` 是由 Host 发布、单调递增的失效标识；它不是语义配置模型，
也不是持久化版本。VNode 会像捕获密度、语言和布局方向一样捕获它，因此限定符变化后，即使整数
资源 ID 相等，Renderer 仍能重新绑定资源属性。`UiImageRequest.resourceRevision` 会把同一标识传给
第一方图片 Loader；默认值 `0` 保持非 Android 与自定义 Host 的确定性。

- `VNode.type` 与 `VNode.spec` 是注册表层面的配对。为了保持构造轻量，创建节点时不会验证
  兼容性；渲染器必须确定性地拒绝不支持的配对。
- 节点规格是不可变渲染快照。回调可以捕获可变应用状态，但规格本身不能充当原生对象持有者。
- 每个文本规格只允许一种内容表示。`TextNodeProps.document` 同时承载纯文本和富文本，Button
  与 Toggle 标签则使用不可变纯文本字符串。Android Renderer 可以在原生 View 绑定前即时创建
  `Spannable` 或其他 `CharSequence`，但不得把该平台值保存在 VNode 中，也不得用它参与结构相等判断。
- `ButtonNodeProps.minHeight` 表示有效的最小 View 与语义触控高度，`visualHeight` 表示请求的
  居中 Surface 高度。渲染器必须把非法可见高度限制在有效边界内，并保证应用显式 Surface
  Modifier 的优先级。
- `SurfaceNodeProps.minimumWidth` 与 `minimumHeight` 定义有效布局、输入、焦点和语义边界。
  可空的 `visualHeight` 只影响 Fill、Border、Ripple、Shape Outline 与默认裁剪。显式调用方
  Surface Modifier 保持最终权限，并关闭该可见内缩。纯色和渐变 Brush 坐标在 Surface 本地
  像素空间解析。
- `UiInteractionIndication.StateLayer` 通过 `Modifier.interactionIndication` 携带完整且渲染器
  中立的按下、聚焦和悬停颜色。Box、Row、Surface、Button 与 IconButton NodeSpec 不再包含
  Ripple 或平行状态层字段。拥有多个原生内部目标的 SegmentedControl 和 NavigationBar NodeSpec
  分别携带已选与未选集合。非活动或禁用的高层组件不安装指示。
- `SliderNodeProps.trackColor` 表示当前值之前（含当前值）的激活轨道，`inactiveTrackColor`
  表示其余轨道。渲染器必须绑定这两个已解析颜色，不得再从平台主题恢复任一轨道颜色。
- Modifier 顺序具有语义。布局与 Parent Data 收集、视觉装饰、输入、Semantics 与绘制阶段会
  按各自阶段规则消费有序元素；调整顺序可能改变行为。
- Padding、Margin、Offset 或单一 Inset 类型的物理与相对声明共享一个解析槽位，同一族中后声明
  的值会整体替换先声明的值。相对水平 Offset 的正值朝逻辑 end 移动；其他相对 start/end 值都在
  Renderer Bind 时根据 VNode 捕获的 `UiLayoutDirection` 映射。
- 集合语义使用逻辑索引。RTL 可以反转物理排布，但不会改变行列元数据或回调身份。集合子项的
  Heading 与 Selected 元数据来自同一份 `SemanticsConfiguration` 字段，避免重复持有状态。
- 每个 VNode 子树都捕获 `UiEnvironmentValues`。渲染器必须使用捕获值，不能改用无关的进程
  全局密度、语言或方向状态。
- `LazyListState`、`ScrollState`、`PagerState`、焦点请求器与嵌套滚动分发器只连接一个当前
  Renderer Connector。替换或释放时，Host 必须断开旧 Connector。Eager 横向偏移与全部页面索引
  在 RTL 中仍使用逻辑顺序。
- `PagerStateSnapshot` 分别发布当前页、已停稳页与目标页。受控 `currentPage` 在重建后仍是唯一
  权威来源；`onPageChanged` 是停稳到 Idle 后的事件，不是声明绑定期间的 `onPageSelected` 回显。
  页面索引在 RTL 下仍保持逻辑顺序；`offscreenPageLimit` 只接受 `-1` 或正数，禁用用户滚动会
  同时阻止指针和无障碍翻页，但不会阻止 Renderer 命令。
- 垂直集合 NodeSpec 不包含焦点跟随策略。焦点后代可见性是真实滚动所有者的 Renderer 不变量，
  Pager 则始终只是离散选择所有者。
- `GridCells.Adaptive` 根据当前内部宽度、间距、密度与配置重新计算物理列数，同时保留 Keyed
  逻辑 Session。`GridItemSpan.FullLine` 按当前列数解析；Foundation 会把 `Fixed(1)` 规范化为
  `Single`。
- Renderer 保留 `LazyListItem` Session 时，若 Key 和两个 Revision 相等，必须忽略新的 Strategy 或
  Payload。任一 Revision 改变时，用最新 Item Payload 调用保留的 Declaration Strategy，并持续
  Render 该逻辑 Session，直到内容报告成功 Commit。Key 不同则始终创建不同逻辑 Session；兼容物理
  呈现只能在旧 State 与 Effect Dispose 后转移。Typed Declaration 可以让全部 Item Snapshot 共享
  一个 `LazyListItemSessionStrategy`；Strategy 只能同步消费当前 Item，不得保留它。
- 状态与 Connector 命令按所属渲染器线程封闭。Android 集成使用主线程；除非具体契约另有
  说明，回调都会同步执行。
- 不存在任何工具捕获 Scope 时，每次 VNode 发射只执行一次 Atomic 非活动检查，不读取工具
  ThreadLocal，也不分配 Stack Trace。捕获激活后继续遵守下述同步且有界的行为。
- `UiNodeTooling.withFirstSourceCapture` 在每个作用域最多观察一个有效节点，并且最多分配一次
  堆栈。嵌套作用域各自独立观察。回调在发出节点的线程同步执行；回调失败会在恢复作用域的
  ThreadLocal 状态后继续抛出。
- `UiNodeTooling.withSourceCandidateCapture` 最多采样 64 次有效发射并保留 32 条不同调用链。只有
  代码块成功返回且捕获状态恢复后才会回调；构建失败或没有节点时不会报告候选。
- `UiSourceSessionRole` 没有渲染或应用状态语义。Host 与 Renderer 只为独立渲染的容器分配它；
  工具可以跳过 `Content` Session，让页面导航保持准确并限制源码捕获开销。
- `AndroidViewNodeProps.update` 与 `onReset` 是可重放的事务回调。一次性外部动作应放在
  `onCommit`，资源清理应放在 `onRelease`。Release 是一次性的永久放弃清理，也覆盖未提交的
  回滚候选节点。
- 包含 `AndroidView` 的 Mounted Tree 只有在每个互操作节点都声明 `onReset` 时才能跨逻辑 Key。
  Renderer 在旧 Session Dispose 后、新 Key Bind 前调用 Reset；最终缓存淘汰恰好调用一次
  `onRelease`。
- 图片加载是可选能力。`UiImageLoader` 由调用方所有，在所属 UI 线程执行，并为已经启动的工作
  返回句柄。渲染器负责为挂载的图片 View 替换和释放句柄；loader 在释放后不得继续持有该 View。
- `ImageSource.Url` 仅接受绝对 HTTP(S) URL；`ImageSource.Uri` 接受使用其他 loader 支持 scheme
  的绝对 URI。`UiImageDecodeSize.Fixed` 使用正数 `UiDp` 边界。Renderer 会把捕获的
  `UiDensity` 放入 `UiImageRequest`，适配器据此把逻辑边界转换为平台像素，但不会改变布局尺寸。
- `ImageSource.Model` 要求调用方提供稳定 key。它的相等判断和诊断文本不能依赖原始 model
  payload，这样适配器可以接受任意平台 model，同时不把它们泄漏到日志或持久化数据中。
- `UiImageRequestExtension` 以具体运行时类型和 `stableKey` 共同标识。适配器忽略不归自己所有的
  扩展类型；加载行为变化时，调用方必须更新 key。

集合预取、原生缓存规模、动效与共享池参数是渲染器优化提示，而不是语义状态。平台可以限制或
忽略不支持的优化，但不能因此改变声明内容。Prefetch Prepare 不能发布已提交工作；即使平台忽略
该优化，首次 Activate 仍是生命周期边界。

## 相关文档

- [节点规格与渲染器注册](https://docs.viewcompose.com/zh-CN/architecture/node-spec)
- [Modifier 架构](https://docs.viewcompose.com/zh-CN/architecture/modifier)
- [当前架构与模块边界](https://docs.viewcompose.com/zh-CN/architecture/overview)
- [Lazy 容器指南](https://docs.viewcompose.com/zh-CN/guides/lazy-collections)
- [焦点与输入指南](https://docs.viewcompose.com/zh-CN/guides/focus-and-input)
- [嵌套滚动指南](https://docs.viewcompose.com/zh-CN/guides/nested-scroll)
- [图片加载指南](https://docs.viewcompose.com/zh-CN/guides/image-loading)
- [源码文档与 API 注释规范](https://docs.viewcompose.com/zh-CN/project/api-documentation-quality)

## 兼容性说明

`0.1.0-alpha03` 首次建立公共渲染器契约。即使应用 DSL 源码没有变化，新增 `NodeType`、具体
`NodeSpec` 或 Modifier 元素也可能要求渲染器同步升级。自定义渲染器应对未知契约明确失败，
也不应把枚举序号、密封子类型名称、工具元数据、原生 View 标识或回调实例持久化为长期外部
数据。

五个相对布局 Modifier 元素是新增的 Q3 契约，但自定义 Renderer 必须识别它们，应用代码才能
安全使用对应 DSL。Renderer 必须只根据 VNode 环境解析 start/end，保留旧元素的物理语义，并在
每一族的物理与相对形式之间执行“后声明者覆盖”规则。

原生控件契约收敛属于 Alpha 硬切。旧命令式 Pager State 与固定整数网格契约已删除：调用方改用
不可变 `PagerStateSnapshot`、`GridCells` 与 `GridItemSpan`。`ScrollableColumnNodeProps` 与
`ScrollableRowNodeProps` 现携带 `ScrollState` 和 `userScrollEnabled`；Slider Snapshot 新增步长
与交互边界回调；下拉刷新新增 `enabled`；Navigation 与 Segmented Item 新增显式 Key 和 Enabled
状态；无实际行为的 Progress `enabled` 字段已删除。预编译 NodeSpec 直接构造点与自定义 Renderer
必须重新构建并完整实现新契约。

`MaxWidthModifierElement`、`MaxHeightModifierElement`、`AspectRatioModifierElement`、
`LayoutConstraintHostNodeProps` 与 `NodeType.LayoutConstraintHost` 是新增源码 API，但也扩展了
Renderer 注册表。自定义 Renderer 必须先识别全部契约，应用才能使用这些 Modifier；静默忽略
测量 Host 会破坏正确性。

Phase 2 ConstraintLayout Transport 为不可变 Data Class Constructor 与 Helper Enum 增加了物理
Anchor、Parent-wrap Policy、Chain Boundary、Grid 与 CircularFlow。源码默认值保持此前的逻辑
Parent 行为，但预编译 Direct Constructor 与自定义 Renderer 必须重新构建。Renderer 不得把
Physical Edge 静默当作 Logical Edge，不得锚定到仅表示 Identity 的 Grid/CircularFlow 声明，
也不得局部应用无效 Ownership Graph。

新增 `LazyListItemSession.prepare` 与 `activate` 是 Q3 生命周期硬切。Kotlin 源码实现可以继承安全
默认值，但接口 JVM 形状已经变化，因此预编译自定义 Session 与 Renderer 必须重新构建。覆写
Prepare 来构建原生内容时，必须推迟全部 Commit Bound Callback，并支持 Activate 前 Dispose。

把 `LazyListItemSession.activate` 与 `render` 改为返回 Commit 成功状态，补全了该 Q3 硬切。自定义
实现必须对 Rollback 返回 `false`，让相同 Submission Revision 仍可重试；预编译 Session 与
Renderer 必须重新构建。

新增 `ButtonNodeProps.visualHeight` 属于 Q2 不可变快照契约变更。源码默认值等于 `minHeight`，
但预编译的构造调用点和自定义渲染器仍必须随对应 Alpha 版本重新构建。

新增 `SliderNodeProps.inactiveTrackColor` 同样属于 Q2 不可变快照契约变更。源码默认值等于
`trackColor`，因此直接源码构造仍保持简洁；预编译构造调用点与自定义渲染器仍必须随对应
Alpha 版本重新构建。

`UiStateLayerColors` 是 Q2 不可变已解析颜色值。为 `ButtonNodeProps`、`IconButtonNodeProps`、
`BoxNodeProps`、`RowNodeProps` 和 `SegmentedControlNodeProps` 增加可空字段后，源码构造与单色
Renderer 回退保持不变，但二进制构造契约发生变化。预编译直接构造调用点和自定义 Renderer
必须随对应 Alpha 版本重新构建。

`SurfaceNodeProps` 取代 `NodeType.Surface` 原先使用的 `BoxNodeProps`，并作为 Q2 不可变快照。
自定义 Renderer 必须增加新的类型/规格配对并重新构建预编译调用方。新增
`UiCornerFamily.Continuous` 与 `UiShape.continuous` 扩展了 Q2 Shape 契约；穷举 Enum 的使用方
必须处理新 Family，或明确采用其文档化的圆角回退。

`SemanticsCollectionInfo` 与 `SemanticsCollectionItemInfo` 新增 Q3 平台无关集合元数据。
`SemanticsConfiguration` 增加可空字段会改变其二进制构造契约，因此预编译调用方和自定义
Renderer 必须重新构建。支持无障碍的 Renderer 应同时映射父集合与子项位置快照；缺少映射会
丢失位置播报，但不得改变布局、输入或选择回调。

`UiNodeTooling.withFirstSourceCapture` 是新增的 Q3 工具 API。它不会改变 VNode 相等性或普通渲染
元数据，但使用方必须把回调视为同步边界，避免阻塞、重入渲染，或把调用链保留为应用状态。

`UiNodeTooling.withSourceCandidateCapture` 同样是新增的 Q3 工具 API。它不会改变普通 VNode
身份或元数据；嵌套候选列表及采样边界仅供工具使用，不是应用持久化格式。

`UiSourceSessionContainerHandle` 与 `UiSourceSessionRole` 是新增的 Q2 工具契约。现有
`RenderContainerHandle` 实现继续有效；缺少该标记时，页面级源码工具必须采用文档化回退或不捕获。

承载文本的 NodeSpec 系列现在强制使用不可变、平台无关的 payload。直接构造
`TextNodeProps` 的调用方必须把 `text = label` 替换为
`document = TextDocument.plain(label)`；富文本继续传入已有 `TextDocument`。
`ButtonNodeProps.text` 与 `ToggleNodeProps.text` 从 `CharSequence?` 收紧为 `String?`。
公开 `Text`、`RichText`、`Button`、`Checkbox`、`RadioButton` 与 `Switch` DSL 的签名和渲染
行为保持不变。对于直接 NodeSpec 构造方和自定义 Renderer，这属于源码与二进制不兼容的 Q2
快照契约变更；它们必须重新构建，并把所有 Android `CharSequence` 转换放到最终原生绑定边界。

新增 Q3 `VNode.observedPropertyId` 会扩展公开 Data Class 的构造器与 Component 形状。源码默认值
让直接构造仍保持简洁，但预编译构造点、解构调用点与自定义 Renderer 必须随本次 Alpha 版本
重新构建。支持 Observed Transaction 的自定义 Renderer 要为每个非空 Identity 发布唯一精确
Target；不支持该能力的 Renderer 在其他路径可以忽略这项可空元数据。
