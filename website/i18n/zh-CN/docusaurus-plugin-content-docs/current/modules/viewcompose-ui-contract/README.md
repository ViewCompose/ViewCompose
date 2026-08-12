---
translation_source: modules/viewcompose-ui-contract/README.md
translation_source_hash: db5fb6e65ad0f5a1c05bc2b081717dfce0dd340928c0b71ff65344a6e85ef76a
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
    implementation("com.viewcompose:viewcompose-ui-contract:0.1.0-alpha03")
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
  定义不可变树内容与渲染器分派。
- [`NodeSpec`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha03/viewcompose-ui-contract/com.viewcompose.ui.node.spec/-node-spec/)
  及其具体属性快照定义渲染器支持的输入。
- `TextNodeProps` 只携带一份权威 `TextDocument`；`ButtonNodeProps` 与 `ToggleNodeProps`
  携带可空的纯文本 `String` 标签。可变或平台特有的 `CharSequence` 实现只在平台 Renderer
  边界转换。
- [`Modifier`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha03/viewcompose-ui-contract/com.viewcompose.ui.modifier/-modifier/)
  携带有序的布局、绘制、交互、语义、焦点与 Parent Data 元素。
- [`UiEnvironmentValues`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha03/viewcompose-ui-contract/com.viewcompose.ui.environment/-ui-environment-values/)
  捕获子树的密度、语言标签与逻辑布局方向。
- [`LazyListState`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha03/viewcompose-ui-contract/com.viewcompose.ui.state/-lazy-list-state/)
  与 Pager 状态把平台滚动能力桥接到可观察的 Runtime 状态。
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
- Button、IconButton、Box、Row 与 SegmentedControl 状态层在启用目标处于活动状态时使用
  “按下优先于聚焦、聚焦优先于悬停”的顺序；非活动态和禁用态保持透明。`stateLayerColors`
  为空时，为直接发射者和旧自定义 Renderer 保留原有的单值 `rippleColor` 契约。
  SegmentedControl 会分别携带选中与未选中集合，因为二者使用不同的语义内容角色。
- `SliderNodeProps.trackColor` 表示当前值之前（含当前值）的激活轨道，`inactiveTrackColor`
  表示其余轨道。渲染器必须绑定这两个已解析颜色，不得再从平台主题恢复任一轨道颜色。
- Modifier 顺序具有语义。布局与 Parent Data 收集、视觉装饰、输入、Semantics 与绘制阶段会
  按各自阶段规则消费有序元素；调整顺序可能改变行为。
- 集合语义使用逻辑索引。RTL 可以反转物理排布，但不会改变行列元数据或回调身份。集合子项的
  Heading 与 Selected 元数据来自同一份 `SemanticsConfiguration` 字段，避免重复持有状态。
- 每个 VNode 子树都捕获 `UiEnvironmentValues`。渲染器必须使用捕获值，不能改用无关的进程
  全局密度、语言或方向状态。
- `LazyListState`、Pager 状态、焦点请求器与嵌套滚动分发器只连接一个当前渲染器 Connector。
  替换或释放时，宿主必须断开旧 Connector。
- 状态与 Connector 命令按所属渲染器线程封闭。Android 集成使用主线程；除非具体契约另有
  说明，回调都会同步执行。
- `UiNodeTooling.withFirstSourceCapture` 在每个作用域最多观察一个有效节点，并且最多分配一次
  堆栈。嵌套作用域各自独立观察。回调在发出节点的线程同步执行；回调失败会在恢复作用域的
  ThreadLocal 状态后继续抛出。
- `UiNodeTooling.withSourceCandidateCapture` 最多采样 64 次有效发射并保留 32 条不同调用链。只有
  代码块成功返回且捕获状态恢复后才会回调；构建失败或没有节点时不会报告候选。
- `UiSourceSessionRole` 没有渲染或应用状态语义。Host 与 Renderer 只为独立渲染的容器分配它；
  工具可以跳过 `Content` Session，让页面导航保持准确并限制源码捕获开销。
- `AndroidViewNodeProps.update` 与 `onReset` 是可重放的事务回调。一次性外部动作应放在
  `onCommit`，资源清理应放在 `onRelease`。
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
忽略不支持的优化，但不能因此改变声明内容。

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
