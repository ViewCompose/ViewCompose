---
translation_source: modules/viewcompose-ui-contract/README.md
translation_source_hash: 9e44da3fdde9f713fb99c77d113017ac85bcf35a05001c83fe131a528a328dd2
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
- [`Modifier`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha03/viewcompose-ui-contract/com.viewcompose.ui.modifier/-modifier/)
  携带有序的布局、绘制、交互、语义、焦点与 Parent Data 元素。
- [`UiEnvironmentValues`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha03/viewcompose-ui-contract/com.viewcompose.ui.environment/-ui-environment-values/)
  捕获子树的密度、语言标签与逻辑布局方向。
- [`LazyListState`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha03/viewcompose-ui-contract/com.viewcompose.ui.state/-lazy-list-state/)
  与 Pager 状态把平台滚动能力桥接到可观察的 Runtime 状态。
- [`FocusRequester`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha03/viewcompose-ui-contract/com.viewcompose.ui.focus/-focus-requester/)
  与 [`NestedScrollDispatcher`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha03/viewcompose-ui-contract/com.viewcompose.ui.gesture/-nested-scroll-dispatcher/)
  为焦点和嵌套滚动定义明确的渲染器连接边界。
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

- `VNode.type` 与 `VNode.spec` 是注册表层面的配对。为了保持构造轻量，创建节点时不会验证
  兼容性；渲染器必须确定性地拒绝不支持的配对。
- 节点规格是不可变渲染快照。回调可以捕获可变应用状态，但规格本身不能充当原生对象持有者。
- `ButtonNodeProps.minHeight` 表示有效的最小 View 与语义触控高度，`visualHeight` 表示请求的
  居中 Surface 高度。渲染器必须把非法可见高度限制在有效边界内，并保证应用显式 Surface
  Modifier 的优先级。
- Modifier 顺序具有语义。布局与 Parent Data 收集、视觉装饰、输入、Semantics 与绘制阶段会
  按各自阶段规则消费有序元素；调整顺序可能改变行为。
- 每个 VNode 子树都捕获 `UiEnvironmentValues`。渲染器必须使用捕获值，不能改用无关的进程
  全局密度、语言或方向状态。
- `LazyListState`、Pager 状态、焦点请求器与嵌套滚动分发器只连接一个当前渲染器 Connector。
  替换或释放时，宿主必须断开旧 Connector。
- 状态与 Connector 命令按所属渲染器线程封闭。Android 集成使用主线程；除非具体契约另有
  说明，回调都会同步执行。
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
