---
translation_source: modules/viewcompose-ui-foundation/README.md
translation_source_hash: d5044dc73bcd509e6d02d12667835f1fb857496464081c42c9a01f16b50499c8
translation_status: current
---

# UI Foundation 模块

`viewcompose-ui-foundation` 是 ViewCompose 面向 Android 的声明式 UI 层。它提供
`UiTreeBuilder` DSL、带主题的组件默认值、Composition Local 与环境传递、组合范围内的 Effect
与可保存状态、Lazy 容器 Scope、浮层声明，以及把声明式树连接到宿主所安装容器、引擎、焦点、
调度、日志与 Trace 契约的渲染器无关 Session 协调器。

开发可复用 ViewCompose 组件、自定义宿主、设计系统适配或浮层后端时，可以直接使用本模块。
Android 应用通常通过 `viewcompose-android` 获得它，同时取得标准引擎与 Material 3 适配。

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
  及其组件函数构建声明式节点树，不会创建 Android View。
- [`Theme` 与 `UiTheme`](https://docs.viewcompose.com/api/viewcompose-ui-foundation/0.1.0-alpha01/viewcompose-ui-foundation/com.viewcompose.ui.foundation/-theme/)
  暴露不可变的颜色、排版、形状、尺寸与浮层 Token，但不选择具体设计系统。排版支持完整的
  Display、Headline、Title、Body 与 Label 分级；形状支持 Extra Small、Small、Medium、Large、
  Extra Large 与 Full 角色，具体值由设计系统适配器提供。
- `UiButtonSizing` 把有效最小触控高度与可见 Surface 高度分开。中性主题和现有自定义主题中，
  每个可见高度默认等于对应的有效高度，因此维持原有渲染；设计系统适配器可以选择更小且居中
  的 Surface，而不缩小 View 或无障碍边界。
- [`UiEnvironment`](https://docs.viewcompose.com/api/viewcompose-ui-foundation/0.1.0-alpha01/viewcompose-ui-foundation/com.viewcompose.ui.foundation/-environment/)
  与各类 Local Provider 为密度、语言、布局方向、内容颜色、文本样式、图片加载、焦点、帧时钟
  和宿主能力划定作用域。
- `Image`、`Icon`、[`ProvideImageLoader`](https://docs.viewcompose.com/api/viewcompose-ui-foundation/current/com.viewcompose.ui.foundation/-provide-image-loader.html)
  与 `UiImageRequestOptions` 暴露图片语义，但不选择 Coil、Glide 或其他解码器。子树可以安装
  一个 `UiImageLoader`，也可以不安装，让资源图片继续渲染。
- [`remember`、`produceState` 与 Effect](https://docs.viewcompose.com/api/viewcompose-ui-foundation/0.1.0-alpha01/viewcompose-ui-foundation/com.viewcompose.ui.foundation/)
  把平台无关组合 Runtime 与结构化协程和已提交副作用连接起来。
- [`rememberSaveable` 与 `SaveableStateRegistry`](https://docs.viewcompose.com/api/viewcompose-ui-foundation/0.1.0-alpha01/viewcompose-ui-foundation/com.viewcompose.ui.foundation/-saveable-state-registry/)
  通过事务式恢复让状态跨组合释放和宿主重建继续存活。
- [`RenderSession`](https://docs.viewcompose.com/api/viewcompose-ui-foundation/0.1.0-alpha01/viewcompose-ui-foundation/com.viewcompose.ui.foundation/-render-session/)
  为一个不透明 `RenderContainerHandle` 协调组合、渲染器协调、原生提交 Effect、浮层、诊断、
  失败恢复与释放。标准应用使用 `renderInto` 返回的 Host Android Session，不直接构造该协调器。
- 浮层规格与 Host 定义平台无关的 Dialog、Popup、Bottom Sheet、Snackbar 和 Toast 标识、定位、
  排队、更新与关闭契约。

完整生成参考位于
[`viewcompose-ui-foundation` API 树](https://docs.viewcompose.com/api/viewcompose-ui-foundation/current/)。
由于当前版本仍为 Alpha，文档站不会提供稳定的 `latest` 别名。

## 状态、渲染与生命周期规则

- `UiTreeBuilder` 是临时记录器。内容块返回后，不要持有它或再次调用捕获的 Builder；应持有状态
  与稳定 Key。
- `remember` 与 Effect 需要活跃组合。位置标识跟随结构调用路径；内容可能移动时，应使用稳定
  `key` 分组和 Lazy Item Key。
- `rememberSaveable` 只在组合提交后注册 Provider。组合失败或被放弃时会释放已 Claim 的恢复值，
  让后续尝试仍能恢复它。
- `UiTheme` 只接收平台无关 Token。Material Android 资源解析、Configuration 观察和主动刷新属于
  `viewcompose-material3`。
- 现有三类排版构造仍保持简洁：省略的 Headline 角色从 Title 派生，省略的 Display 角色从
  Headline 派生。现有三级形状构造也继续有效：省略的 Extra Small/Extra Large 分别从
  Small/Large 派生，Full 默认是相对边界的胶囊形。这些只是兼容回退，不是 Material 数值；
  Material 应用会从 `viewcompose-material3` 获得具体比例。
- 每个 `RenderSession` 独占一个容器、其挂载节点、组合、协程 Scope 与 Session 范围浮层。应随
  宿主生命周期调用 `dispose`；释放后的 Session 不能再次使用。
- 组合准备和树渲染失败会保留上一帧。渲染器建立新原生树之后发生的失败，会按已提交帧失败
  报告，无法回滚该原生树。
- 浮层请求是声明式的，按 Render Session ID 与 Request Key 划分作用域。后续提交省略已有请求
  就会关闭它。平台呈现需要 `viewcompose-overlay-material3-android` 或自定义 `OverlayHost`。
- Lazy 容器 Key 必须稳定且唯一。复用、预取与动效 Policy 是渲染器提示，不能作为业务状态。
- 图片组件会把 source 身份和请求 options 保存在 `NodeSpec` 中。发射节点时读取 loader，因此
  更换 provider 是明确的渲染输入。渲染器会在启动新工作前替换旧工作，并在节点或 Session 离开
  挂载树时释放旧工作。因此 Resource 也能复用已安装 loader 的解码和变换能力；空 source 会
  选择 node fallback，而不会启动 loader。

构建 VNode 树时，线程由活跃组合上下文封闭。标准 Android Host 会在主线程串行化渲染、状态
回调、Effect 与平台操作。自定义 Host 必须保持相同的顺序与所有权保证。

## 相关文档

- [当前架构与模块边界](https://docs.viewcompose.com/zh-CN/architecture/overview)
- [状态与快照架构](https://docs.viewcompose.com/zh-CN/architecture/state-snapshots)
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

完整 `UiTypography` 与 `UiShapes` 值契约属于 Alpha 版本线的源码和二进制变更。它们仍是不可变、
不包含生命周期或所有权协议的 Q2 值；直接构造保留源码默认值，但穷举解构、反射以及预编译调用方
必须针对对应版本重新构建。

`UiButtonSizing` 同样是 Q2 不可变值契约。新增的可见高度字段提供源码默认值，但对预编译的
直接构造调用和穷举解构属于二进制变更。`Button` 会把两类高度都解析进 `ButtonNodeProps`；
自定义渲染器必须遵守该契约，或明确说明其可见边界与有效边界仍保持一致。
