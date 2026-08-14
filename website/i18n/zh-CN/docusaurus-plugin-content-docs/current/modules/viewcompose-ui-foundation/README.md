---
translation_source: modules/viewcompose-ui-foundation/README.md
translation_source_hash: c2dde3f5a27558654fc71a0ee65aaaa58cd0cd81f49a9f916cc4416688211ab6
translation_status: current
---

# UI Foundation 模块

`viewcompose-ui-foundation` 是 ViewCompose 面向 Android 的声明式 UI 层。它提供
`UiTreeBuilder` DSL、带主题的组件默认值、Composition Local 与环境传递、组合范围内的 Effect
与可保存状态、Lazy 容器 Scope、浮层声明，以及把声明式树连接到宿主所安装容器、引擎、焦点、
调度、日志与 Trace 契约的渲染器无关 Session 协调器。

开发可复用 ViewCompose 组件、自定义宿主、设计系统适配或浮层后端时，可以直接使用本模块。
Android 应用通常通过中立 `viewcompose-android` 聚合模块或具名
`viewcompose-material3-android` 聚合模块获得它。

本模块不实现 View 协调，不持有 Activity 或 Fragment 生命周期，不呈现平台 Dialog 和
Popup，不执行图片解码，也不提供可选的动画、手势、图形、阴影、导航或 ConstraintLayout
能力。这些职责分别保留在对应的独立模块中。

## 产物与稳定性

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-ui-foundation:0.1.0-alpha01")
}
```

- 稳定性：**Alpha**。Alpha 版本之间可能发生源码和二进制不兼容变更。
- 平台：Android Library，`minSdk 24`、`compileSdk 36`，Java 11 字节码。
- Runtime、Text Core 和 UI Contract 会被传递暴露，因为它们的 State、编辑、Modifier、单位、
  Node 与环境类型构成公开 Widget API。
- Kotlin Coroutines 会被传递暴露，因为 `CoroutineScope` 出现在组合 Effect API 中。
- 生产产物不依赖 AndroidX 或 Material Components。公开根包固定为
  `com.viewcompose.ui.foundation`，不保留已退役的 `com.viewcompose.widget.core`。
- ViewCompose 以 Android View 为目标，因此可以保留 Android-only 声明值；但原生 `ViewGroup`
  访问、Context 环境提取、焦点适配、日志和 Trace 属于 Android Engine。
- 本版本构建基线：Kotlin 2.0.21 与 Android Gradle Plugin 8.13.2。

## 最小组件示例

```kotlin
fun UiTreeBuilder.ProfileSummary(name: String, role: String) {
    UiTheme {
        Column(spacing = 8.dp) {
            Text(name, style = TextDefaults.titleMediumStyle())
            Text(role, color = TextDefaults.secondaryColor())
        }
    }
}
```

`UiTreeBuilder` 记录不可变 VNode。`UiTheme` 提供完整 Token 快照，每个发射的节点都会捕获
当前主题、密度、语言、布局方向，以及后续渲染器或子 Render Session 所需的其他 Local。

## 主要 API

- [`UiTreeBuilder`](https://docs.viewcompose.com/api/viewcompose-ui-foundation/0.1.0-alpha01/viewcompose-ui-foundation/com.viewcompose.ui.foundation/-ui-tree-builder/)
  及其组件函数构建声明式节点树，不会创建 Android View。其 Q3 底层 `emit` 边界把子内容闭包
  身份作为重组输入；编译样例 `emittedContentClosureSample` 展示直接构建自定义节点的方式。
- [`Theme` 与 `UiTheme`](https://docs.viewcompose.com/api/viewcompose-ui-foundation/0.1.0-alpha01/viewcompose-ui-foundation/com.viewcompose.ui.foundation/-theme/)
  暴露不可变的颜色、排版、形状、尺寸、交互与浮层 Token，但不选择具体设计系统。排版支持
  完整的 Display、Headline、Title、Body 与 Label 分级；形状支持 Extra Small、Small、Medium、
  Large、Extra Large 与 Full 角色。`UiInteractionTokens` 提供通用按下、聚焦和悬停透明度，
  具体值由设计系统适配器提供。
- `UiTokenProvenance` 是附着在 `UiThemeMetadata` 上的 Q2 非视觉来源快照。若
  `colors.primary` 这类精确路径没有独立记录，就继承所在家族来源，因此诊断可以区分框架默认、
  Android 主题或动态映射、具名静态 Token 与应用 Override，而不改变视觉解析。
- `UiDesignSystemAttribution`、`UiComponentAttribution` 与 `UiIntegrationAttribution` 是有界 Q2
  证据快照，不是 Recipe Registry。具名系统通过 Q3 `DesignSystemAttributionProvider` 提供 Recipe
  身份、中立 Backend、集成 Transport/Presenter、Conformance、能力路径和 Fallback；立即内容与已捕获的延迟内容都可从
  `DesignSystemDiagnostics.current` 读取同一个 Local。
- `UiButtonSizing` 把有效最小触控高度与可见 Surface 高度分开。中性主题和现有自定义主题中，
  每个可见高度默认等于对应的有效高度，因此维持原有渲染；设计系统适配器可以选择更小且居中
  的 Surface，而不缩小 View 或无障碍边界。
- `UiSwitchSizing` 是 Q2、设计系统中立的组合 Switch 可见几何契约，负责 Track、Thumb、Track
  内缩与 Label 间距。它有意不拥有有效触控目标；编译样例 `switchSizingTokenSample` 展示如何在
  独立的 `minimumInteractiveHeight` 策略中放置紧凑可见 Track。
- `BasicSurface` 是 Q3 设计系统中立基础组件。其 Q2 `BasicSurfaceStyle` 接受已经解析的纯色或
  渐变 Brush、逻辑 Shape、Border、裁剪、Elevation 与精确 Shadow，并把最小有效边界与可选的
  居中可见高度分开。设计系统在发射前选择这些值，Android Renderer 只接收中立的
  `SurfaceNodeProps` 快照。编译样例 `basicSurfaceSample` 展示了该契约。
- `BasicButton` 是建立在 `BasicSurface`、Row、Text 与 Icon 上的 Q3 动作组合。其 Q2
  `BasicButtonStyle` 只包含已经解析的几何、排版、内容与交互值。它不会发射原生 Button 节点，
  现有 `Button` API 则继续保留该兼容路径。编译样例 `basicButtonSample` 展示连续圆角动作。
- `UiControlSizing.minimumInteractiveHeight` 是 Checkbox、RadioButton、Switch 与 Slider 使用的
  设计系统无关有效高度策略。它的中性默认值是零，因此保留原生固有测量。设计系统可以提供
  正的最小值；组件会在调用方 Modifier 之前应用它，所以应用显式指定的精确高度仍具有最终权限。
- Checkbox、RadioButton、Switch 与 Slider 的启用态选中或激活默认颜色从
  `Theme.colors.primary` 解析。Slider 还从 `Theme.colors.secondaryContainer` 解析非激活轨道。
  AppCompat `controlActivated` 继续作为通用状态 Token 提供，但不会覆盖这些组件语义角色。
- Button 与 IconButton Defaults 会把自身启用态语义内容角色与 `UiInteractionTokens` 组合，
  发出已解析的按下、聚焦和悬停颜色。调用方可通过 `stateLayerColors` 替换完整集合；Button 的
  显式旧 `rippleColor` 重载会有意让所有活动状态继续使用同一个颜色。
- Chip、FAB、Extended FAB、可点击 Surface、Card、ListItem 和 DropdownMenuItem 通过内部
  Box/Row NodeSpec 字段复用同一内容角色解析。SegmentedControl 分别解析选中与未选中集合，
  因此切换选中项时也会切换交互角色；静态或禁用组合控件保持空的多状态契约。
- [`UiEnvironment`](https://docs.viewcompose.com/api/viewcompose-ui-foundation/0.1.0-alpha01/viewcompose-ui-foundation/com.viewcompose.ui.foundation/-environment/)
  与各类 Local Provider 为密度、语言、布局方向、内容颜色、文本样式、图片加载、焦点、帧时钟
  和宿主能力划定作用域。
- `Image`、`Icon`、[`ProvideImageLoader`](https://docs.viewcompose.com/api/viewcompose-ui-foundation/current/com.viewcompose.ui.foundation/-provide-image-loader.html)
  与 `UiImageRequestOptions` 暴露图片语义，但不选择 Coil、Glide 或其他解码器。子树可以安装
  一个 `UiImageLoader`，也可以不安装，让资源图片继续渲染。
- [`remember`、`produceState` 与 Effect](https://docs.viewcompose.com/api/viewcompose-ui-foundation/0.1.0-alpha01/viewcompose-ui-foundation/com.viewcompose.ui.foundation/)
  把平台无关组合 Runtime 与结构化协程和已提交副作用连接起来。`DisposableEffect` 与
  `LaunchedEffect` 必须提供 Key，Disposable Setup 必须以 `onDispose` 结束。无 Key 的
  `SideEffect` 在每次成功调用后运行，带 Key 重载只在首次提交和结构 Key 变化时发布。
- `CompositionEffectContext` 是 Q3 底层 Bridge，供实现额外同步或 Coroutine Effect Primitive
  的可选集成模块使用。它会标记 Callback，让任何 Local 读取直接失败，避免消费默认值或无关
  Provider；它不会捕获或恢复 Provider Stack，普通应用代码应使用标准 Effect API。
- [`rememberSaveable` 与 `SaveableStateRegistry`](https://docs.viewcompose.com/api/viewcompose-ui-foundation/0.1.0-alpha01/viewcompose-ui-foundation/com.viewcompose.ui.foundation/-saveable-state-registry/)
  通过事务式恢复让状态跨组合释放和宿主重建继续存活。
- `LazyColumn`、`LazyRow`、`LazyVerticalGrid` 与 Pager Page 声明使用显式 Q3 Revision 契约。批量
  Overload 接受 `contentRevision = { model.version }`；普通捕获值必须是可观察 State 或进入该
  Revision。Pager Page 也声明 `contentType`。`TabRow` 使用父组合中的 Eager Keyed Child，而非
  Lazy Item Session。
- [`RenderSession`](https://docs.viewcompose.com/api/viewcompose-ui-foundation/0.1.0-alpha01/viewcompose-ui-foundation/com.viewcompose.ui.foundation/-render-session/)
  为一个不透明 `RenderContainerHandle` 协调组合、渲染器协调、原生提交 Effect、浮层、诊断、
  失败恢复与释放。标准应用使用 `renderInto` 返回的 Host Android Session，不直接构造该协调器。
- `RenderSessionSourceTooling` 与 `RenderSessionSourceRegistration` 组成 Q3 可选平台诊断契约。
  只有平台主动启用时才捕获一条有限源码调用链，并跟踪 Root、Lazy Item 与 Pager Item Render
  Session 的活动/释放生命周期。编译样例 `renderSessionSourceToolingSample` 展示其 Adapter 生命周期。
- 浮层规格与 Host 定义平台无关的 Dialog、Popup、Bottom Sheet、Snackbar 和 Toast 标识、定位、
  排队、更新与关闭契约。

完整生成参考位于
[`viewcompose-ui-foundation` API 树](https://docs.viewcompose.com/api/viewcompose-ui-foundation/current/)。
由于当前版本仍为 Alpha，文档站不会提供稳定的 `latest` 别名。

## 状态、渲染与生命周期规则

`UiEnvironment` 现在会传递 `resourceRevision`，构建内容时可通过
`Environment.resourceRevision` 读取当前不可变值。Local 快照会为 Lazy Item、Pager、Overlay 与
Navigation Destination 保留该值。Android 资源解析与观察仍由 UI Foundation 之外的
`viewcompose-host-android` 负责，任何具名设计系统都不拥有这个中立 Local。

- `UiTreeBuilder` 是临时记录器。内容块返回后，不要持有它或再次调用捕获的 Builder；应持有状态
  与稳定 Key。
- ViewCompose 没有编译器转换，无法推断所有普通 Kotlin 捕获值。因此，新安装的发射内容闭包
  即使节点规格值相等也会重建该 Group；只有完全相同且被保留的闭包才能复用未失效的子结果。
  这项规则优先保证捕获值与子 Session 回调正确，不采用不安全的值相等子树跳过。
- 集合 Item Snapshot 而非 Callback 对象身份划分逻辑子提交。Key 与 Content/Environment Revision
  相等时不执行 Child Composition 或原生 Patch。变化的非 State 捕获值必须进入
  `contentRevision`；省略它就承诺该值对当前 Key 保持稳定。
- Lazy Item 与 Pager 子 Revision 只会在 `activate` 或 `render` 报告已 Commit 帧时推进。组合或
  原生树 Rollback 会保留逻辑 Session 并重试同一语义 Revision；帧 Commit 后的失败仍可观察，
  但不会撤销该帧。
- `remember` 与 Effect 需要活跃组合。位置标识跟随结构调用路径；内容可能移动时，应使用稳定
  `key` 分组和 Lazy Item Key。
- 候选 Effect 变化属于事务。组合或原生 Tree Render 失败不会启动候选工作，会保留已提交的
  Subscription 与 Job，并丢弃 `rememberUpdatedState` 候选发布。原生提交成功后，Committed
  Value 发布及全部退出生命周期回调先于进入回调，然后才执行 `SideEffect`、原生
  `AndroidView.onCommit`、Overlay 与诊断工作。
- `DisposableEffect` 的 Setup 与 Cleanup 都是同步且终止性的。Setup 抛出时不拥有 Cleanup，
  相同 Key 不会重试；Cleanup 抛出后不会再次调用。Runtime 仍会尝试其他独立生命周期回调。
- `LaunchedEffect` 继承 Render Session Coroutine Context，并要求显式重启身份。
  `rememberCoroutineScope` 用于事件回调并持有普通子 Job。传入包含 Job 的 Context 会返回失败
  Scope，而不会把工作从组合所有权中分离。
- Effect 回调应在声明时解析并捕获 `Theme`、Environment、Lifecycle 与 Host Capability。
  Provider Stack 不会在回调周围被隐式恢复；内置 Effect Scope 会用具名 Local Diagnostic
  拒绝 Local 读取，即使回调线程上存在另一个 Provider 也不会误读。Debug Render Session 会在
  同步 Callback 超过 16 ms 时发出警告。
- `rememberSaveable` 只在组合提交后注册 Provider。组合失败或被放弃时会释放已 Claim 的恢复值，
  让后续尝试仍能恢复它。
- 延迟子组合不会共享 Host Registry 的扁平 Provider Key 命名空间。Lazy、Pager 与 Overlay 按
  逻辑 Key Remember 分层子 Registry，在回收期间保留状态，并在 Keyed Reorder 时恢复且不串状态。
  Tab Child 使用父组合的 Keyed Saveable Namespace。并发视觉副本不拥有持久化权，不能覆盖逻辑
  子项的持久化状态。
- 从未激活的 Lazy 子 Session 可以为 RecyclerView Prefetch 保留 Prepared Composition 与已经构建
  的原生树。它与正常帧使用同一 Transaction，因此 Remember 激活、用户 Effect、原生 Commit
  Callback、Overlay 和诊断都会推迟到 Attach。State 失效会在 Activate 前放弃过期候选；Active
  缓存 Session 会保持生命周期直到 Recycle，不把 Viewport Detach 当作 Stop。
- Recycle 会在兼容 Mounted Tree 进入有界 Renderer 缓存前结束逻辑 Key Session。只有可 Reset
  原生树能跨 Key；淘汰会确定性 Release 原生资源。RecyclerView Pool 只保留空 Holder 外壳。
- `UiTheme` 只接收平台无关 Token。Android 资源观察属于 `viewcompose-host-android`；Material
  等具名设计系统只负责把 Host 产生的资源版本映射到自己的 Token 刷新策略。
- 现有三类排版构造仍保持简洁：省略的 Headline 角色从 Title 派生，省略的 Display 角色从
  Headline 派生。现有三级形状构造也继续有效：省略的 Extra Small/Extra Large 分别从
  Small/Large 派生，Full 默认是相对边界的胶囊形。这些只是兼容回退，不是 Material 数值；
  Material 应用会从 `viewcompose-material3` 获得具体比例。
- 每个 `RenderSession` 独占一个容器、其挂载节点、组合、协程 Scope 与 Session 范围浮层。应随
  宿主生命周期调用 `dispose`；释放后的 Session 不能再次使用。
- 源码工具默认关闭。已安装的 Adapter 会在首次构建树之前接受检查，接收成功构建产生的有界源码
  候选，只在原生树建立后注册，由 `setRenderingActive` 更新，并随 Session 释放。候选调用链允许
  平台在导航前移除共享 Scaffold 调用方。工具失败只属于诊断，不能成为应用渲染依赖。
- 组合准备和树渲染失败会保留上一帧。渲染器建立新原生树之后发生的失败，会按已提交帧失败
  报告，无法回滚该原生树。
- 浮层请求是声明式的，按 Render Session ID 与 Request Key 划分作用域。后续提交省略已有请求
  就会关闭它。平台呈现需要 `viewcompose-overlay-android`、
  `viewcompose-overlay-material3-android` 这类具名 Adapter，或自定义 `OverlayHost`。
- Lazy 容器 Key 必须稳定且唯一。`contentType` 只能分组结构兼容的原生树，
  `mountedTreeCacheSize` 限制每个容器保留的 Reset 物理呈现。Prefetch 与 Motion Policy 仍只是
  Renderer Hint，不能作为业务状态。
- 图片组件会把 source 身份和请求 options 保存在 `NodeSpec` 中。发射节点时读取 loader，因此
  更换 provider 是明确的渲染输入。渲染器会在启动新工作前替换旧工作，并在节点或 Session 离开
  挂载树时释放旧工作。因此 Resource 也能复用已安装 loader 的解码和变换能力；空 source 会
  选择 node fallback，而不会启动 loader。

构建 VNode 树时，线程由活跃组合上下文封闭。标准 Android Host 会在主线程串行化渲染、状态
回调、Effect 与平台操作。自定义 Host 必须保持相同的顺序与所有权保证。

## 相关文档

- [当前架构与模块边界](https://docs.viewcompose.com/zh-CN/architecture/overview)
- [状态与快照架构](https://docs.viewcompose.com/zh-CN/architecture/state-snapshots)
- [事务式 Effect 与结构化工作](https://docs.viewcompose.com/zh-CN/architecture/effects)
- [节点规格与渲染器注册](https://docs.viewcompose.com/zh-CN/architecture/node-spec)
- [Lazy 容器指南](https://docs.viewcompose.com/zh-CN/guides/lazy-collections)
- [主题与 Android 集成](https://docs.viewcompose.com/zh-CN/guides/theming)
- [图片加载指南](https://docs.viewcompose.com/zh-CN/guides/image-loading)
- [源码文档与 API 注释规范](https://docs.viewcompose.com/zh-CN/project/api-documentation-quality)

## 兼容性说明

`0.1.0-alpha01` 建立重命名后的 UI Foundation 坐标、`com.viewcompose.ui.foundation` 根包和
设计系统无关主题边界，并继续承载公共 Widget、Local、可保存状态、浮层与 Render Session
契约，但不提供遗留包别名。
不要把自动 Saveable Key、Session 标识、VNode 实现名称、回调实例、工具元数据或诊断结构
持久化为长期外部数据。即使应用组件源码仍能编译，契约变化也可能要求自定义渲染器与 Host
同步升级。

子组合 Saveable 所有权是
[ADR-0010](https://docs.viewcompose.com/zh-CN/architecture/decisions/0010-hierarchical-saveable-state-ownership)
定义的硬修正。缺陷扁平 Registry 命名空间写入的历史子项值无法安全识别逻辑 Owner，因此不做
迁移；根组合显式 Saved Key 与 Android Host Bundle 格式保持不变。每个延迟 Container Holder
会占用父结构 Scope 的一个自动 Saveable Slot，因此调用方不能把生成的自动 Key 当作持久兼容面。

Effect Runtime 的硬切要求 `DisposableEffect` 与 `LaunchedEffect` 至少提供一个 Key。
Disposable Setup 现在只能通过 `DisposableEffectScope.onDispose` 返回 Cleanup；迁移旧的
Lambda-return Cleanup 时，应让 `onDispose { ... }` 成为 Setup Block 的最后一个表达式。带 Key
的 `SideEffect` 是 ViewCompose 新增的只在变化时发布形式。Effect 生命周期、Rollback、Coroutine
Ownership 与 `rememberUpdatedState` 发布现在遵循
[事务式 Effect 与结构化工作](../../architecture/effects.md)中的事务契约。

`RenderSessionPlatformDiagnostics.sourceTooling`、`RenderSessionSourceTooling` 与
`RenderSessionSourceRegistration` 是新增的 Q3 工具 API。现有平台诊断使用默认空 Adapter，行为
不变。主动启用的自定义平台必须让注册状态受所属 Render Session 约束，同步消费有界候选调用链
列表，并在平台渲染线程调用。注册必须保持被动：可以弱引用容器，但不能安装持续的滚动、全局布局、
绘制、触摸、Frame 或重组观察器。实时检查必须由
[ADR-0009](../../architecture/decisions/0009-development-tooling-isolation.md)定义的显式工具请求触发。

完整 `UiTypography` 与 `UiShapes` 值契约属于 Alpha 版本线的源码和二进制变更。它们仍是不可变、
不包含生命周期或所有权协议的 Q2 值；直接构造保留源码默认值，但穷举解构、反射以及预编译调用方
必须针对对应版本重新构建。

`UiButtonSizing` 同样是 Q2 不可变值契约。新增的可见高度字段提供源码默认值，但对预编译的
直接构造调用和穷举解构属于二进制变更。`Button` 会把两类高度都解析进 `ButtonNodeProps`；
自定义渲染器必须遵守该契约，或明确说明其可见边界与有效边界仍保持一致。

`UiSwitchSizing` 是加入 `UiControlSizing` 的 Q2 不可变值契约，并提供源码默认值。它会改变
预编译直接构造调用与穷举解构的二进制兼容性。设计 Recipe 消费解析后的几何值；中立 Android
Renderer 不会因此获得 One UI 或其他具名设计系统分支。

`UiControlSizing.minimumInteractiveHeight` 是另一个 Q2 不可变值字段。它提供源码默认值，但对
预编译直接构造调用与穷举解构具有相同的二进制兼容影响。Checkbox、RadioButton、Switch 与
Slider 是 Q3 组件 API：它们先加入解析后的最小目标，再应用调用方 Modifier，从而保留显式的
精确布局决策。

Slider 新增的 `inactiveTrackColor` 参数属于 Q3 组件 API 变更。它提供主题化源码默认值并解析到
Q2 `SliderNodeProps` 快照中；预编译调用方与自定义渲染器必须随对应 Alpha 版本重新构建。

`UiInteractionTokens` 是 Q2 不可变主题值；将它加入 `UiThemeTokens` 后，预编译构造调用和穷举
解构会发生二进制变化。Button 与 IconButton 状态层参数属于 Q3 组件 API 变更。源码调用方会
获得语义化多状态默认值；曾显式提供旧 `rippleColor` 的 Button 调用方保留专用兼容重载。预编译
默认参数调用点必须随本次 Alpha 版本重新构建。

`BasicSurfaceStyle` 是 Q2 已解析值契约，`BasicSurface` 是 Q3 组件 API。`BasicSurface` 会在
已解析样式与行为之后追加调用方 Modifier：调用方 Surface Modifier 替换默认可见 Surface，
调用方 Elevation 优先，调用方 Shadow 绘制在样式 Shadow 之后。`Surface` 现通过该基础组件
解析原有默认值，保持公共源码 API，同时把 `NodeType.Surface` 的具体规格改为
`SurfaceNodeProps`。

`BasicButtonStyle` 是 Q2 已解析值契约，`BasicButton` 是 Q3 组合 API。它属于增量能力，不会改变
现有 `Button` 签名或原生 Renderer 行为。内部对比夹具现已使用该生产基础组件，证明两套独立
动作 Recipe，同时不向 UI Foundation 加入设计系统词汇。

`UiTokenProvenance`、`UiDesignSystemAttribution` 与 `UiComponentAttribution` 是 Q2 不可变
诊断契约，`DesignSystemAttributionProvider` 是 Q3 Provider API。`UiThemeMetadata` 新增的
Provenance 具有源码默认值，但改变二进制 Constructor、Copy 与 Component Surface，因此预编译
直接调用方必须重建。这些契约只保存稳定身份与已解析证据，不授权在 UI Foundation 或 Renderer
中加入 Recipe、Factory 或具名设计系统分支。
