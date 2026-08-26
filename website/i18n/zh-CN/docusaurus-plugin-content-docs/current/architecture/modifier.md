---
translation_source: architecture/modifier.md
translation_source_hash: e72da34b0855514655e6650719b1788317368e763ed62b466672885edce9389f
translation_status: current
---

# Modifier 架构

## 1. 文档定位

本文档负责 `Modifier`、带作用域的 Parent Data、组件 `NodeSpec` 与 `Theme/Defaults` 之间的边界
和共用运行时规则。公开符号的完整清单由生成的
[能力参考](https://docs.viewcompose.com/reference/)负责；本文不再维护第二份清单。

## 2. 所有权模型

按顺序选择第一个匹配的层：

1. `Modifier` 负责可应用于大多数节点的稳定外层修饰和行为，包括布局、外观、绘制、可见性、
   交互、焦点、语义、测试、手势、共享内容、嵌套滚动、阴影和布局动画。
2. 类型化父级 Scope 负责仅对该父级有意义的数据，例如 `RowScope/ColumnScope.weight`、
   Scope Alignment 或 ConstraintLayout 子项约束。
3. 组件参数和 `NodeSpec` 负责组件语义，例如文本样式、图片 Content Scale、按钮 Variant 和
   TextField 编辑状态。
4. `Theme`、设计系统 Recipe 与 `Defaults` 提供默认值；Renderer 只消费解析后的值，不创建业务
   默认值。

集合复用和动画仍是容器策略。焦点编辑器可见性由最近的真实滚动所有者负责。Pager 只负责离散
页面选择，因此可能被 IME 遮挡的页面需要提供页内滚动。

## 3. 运行时契约

### 3.1 顺序、布局与外观

`Modifier` 是以 `Modifier` 为起点的不可变有序链，Renderer 解析时必须保留顺序。完整的物理/
相对间距族、背景和 Shape 族、可见性、点击处理以及其他单值属性，后声明值按各自契约替换先声明
值；`zIndex` 等累积属性继续遵循其显式累积契约。

物理 Padding、Margin、Offset 和 Inset Selector 在 RTL 下也不改变含义。对应的 `Relative` 形式
在每次 Bind 时根据 VNode 捕获的方向解析 Start/End。Renderer 只有一个原生 Padding 写入者：
容器 Content Padding、已解析 Modifier Padding 与选定 Insets 会先合成，再更新 View。

最大尺寸与宽高比是可移植约束，不是原始 Android Setter。Android Renderer 会把它们折叠到包住
完整节点的单个合成布局边界。父级传入的 Exact Constraint 保持权威；非有限、非正数或互相矛盾
的声明边界会在渲染前失败。Constraint Parent Data 只对 ConstraintLayout 子项有意义。

Drawable 背景优先于 Packed Color，并通过 View Context 遵循资源限定符。Shape 与旧 Corner
Radius 按链顺序互相替换。裁剪、平台 Elevation、精确阴影与兄弟绘制顺序彼此独立。

### 3.2 绘制、交互、语义与共享内容

绘制 Callback 按 Modifier 顺序执行。Behind Callback 位于被包装内容之前；Content-aware
Callback 决定是否以及何时透传内容；Cache Builder 使用 Renderer 拥有的缓存。Visibility 独立
控制绘制与布局参与，不依赖是否注册 Draw Callback。

Interaction Indication 只描述视觉反馈，不会让节点自动获得 Clickable 或 Enabled 语义。高层
组件先解析设计系统反馈，再安装该值。无障碍状态通过 Renderer-neutral Semantics 传递，逻辑集合
索引不会因 RTL 改变。`testTag` 是诊断标识，不是全局唯一的应用 Key。

共享内容 Marker 发布端点标识与模式。配对、快照和回退属于支持共享内容的 Host；在其他 Host 中，
Marker 不产生视觉效果。

### 3.3 焦点、按键、手势与嵌套滚动

UI Contract 拥有标准化焦点、按键、手势与嵌套滚动值；UI Foundation 暴露 Session 服务；Gesture
提供识别器描述；Android Renderer 附加已挂载目标，上层不保留 View。

Preview Key 从根传播到目标，未消费 Key 再从目标向根冒泡；显式焦点目标优先于原生搜索。Gesture
Modifier 描述 Pointer、Click、Drag、Anchored Drag、Transform 与仲裁策略；Renderer 负责平台
计时、Slop、Pointer Stream、Velocity、取消与 Callback 分发。

Nested Scroll 的 Pre 阶段从外向内，Post 阶段从内向外，每个有限结果都受剩余输入约束。框架
Scroller 与实现 Android Nested Scrolling 的原生 Child 可直接加入该链；其他 `AndroidView`
需要已附加的 Dispatcher。旧式原生 Fling 只能报告 Boolean 消费，而 ViewCompose 链会保留精确的
部分 Velocity。

### 3.4 精确阴影路由

UI Contract 拥有 Renderer-neutral 阴影层和顺序；Android Renderer 拥有前后 Decoration Plane，
但不依赖具体栅格后端。可选 Shadow Android 产物解析 Shape 与 Density、栅格化图层并回放。该产物
不存在时，精确阴影请求为 No-op，也不会影响布局、输入、Elevation 或 `zIndex`。

应用与后端细节参见[高级阴影指南](../guides/shadows.md)和
[Shadow Android 模块手册](../modules/viewcompose-shadow-android/README.md)。

## 4. 生成式能力所有权

源码扫描器从已发布生产源码集中发现面向应用的 Public/Protected DSL、Modifier、组件、Host、
集成与工具入口。每个入口必须且只能解析到一个 Capability、Artifact/Version State、生成 Reference
Owner、Sample Decision、Module Manual 与版本化 API Root。Internal、测试、Demo、生成代码和仅
Renderer 使用的辅助项不会进入目录。

Website 与治理门禁消费同一份已提交模型。使用
`./gradlew updateDocumentationCapabilityReference` 刷新；陈旧输出、重复所有权或新增孤儿项都会
使校验失败。原始签名和 KDoc/Javadoc 仍由
[版本化 API Reference](https://docs.viewcompose.com/api/)负责。

## 5. 硬边界与变更门禁

不要把组件语义放进通用 `Modifier`，不要把父级专用数据放进全局 `Modifier`，不要在 Renderer
内写主题默认值，也不要把第一方持久契约放回无类型动态 Map。不要在生成式 Reference 旁维护第二
份手写符号清单。

Modifier 边界变更必须同步本文档，覆盖受影响的 Contract 与 Renderer 路径，提供对应 Q Level
要求的可编译 Q3 Sample 和公开文档；行为涉及视觉或交互时，还需要 Demo 或设备证据。流程参见
[开发工作流](../project/workflow.md)。

## 6. 关联文档

1. [布局与 Modifier 教程](../tutorials/layouts-and-modifiers.md)
2. [手势教程](../tutorials/gestures.md)
3. [焦点与输入](../guides/focus-and-input.md)
4. [嵌套滚动](../guides/nested-scroll.md)
5. [NodeSpec 架构](node-spec.md)
6. [主题运行时架构](theming.md)
