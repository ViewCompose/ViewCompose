---
translation_source: architecture/decisions/0013-component-appearance-resolution-boundary.md
translation_source_hash: 9d7a8eceffa8633f22889e4e800824223b32fc36596be204ebb14a8cb2e4c688
translation_status: current
---

# ADR-0013：组件外观解析边界

- 状态：已接受
- 日期：2026-08-15
- 替代：现由[局部主题 Override](../../guides/theming-local-overrides.md)取代的组件局部覆盖建议

## 背景

ViewCompose 需要两种不同的外观契约。应用代码需要一个小巧的逃生口，用于极少数组件或局部子树
偏离设计系统 Recipe 的场景；设计系统实现则需要在调用中立 `Basic*` 原语前得到完整且不依赖主题
的值快照。把两类需求都做成组件直接参数，会让高层 DSL 随每个低频视觉槽持续膨胀；把两类需求
都做成完整 Style，又会迫使应用重建设计系统 Recipe，并削弱设计系统边界。

现有组件颜色 Provider 只覆盖了部分外观。嵌套 Provider 会整体替换稀疏 Patch，而不是逐字段合并；
声明出的部分 TextField 错误容器槽从未被消费；一个只有四个字段的输入控件模型还混合了 Checkbox、
Switch、RadioButton 和 Slider 完全不同的状态。一些高层组件同时暴露了很长的低频视觉参数列表，
而 `BasicTextField` 暴露的是分散的已解析字段，而不是一份完整编辑核心 Style。

## 决策

### 高层组件使用稀疏 Overrides

每个高层组件族可以提供一个强类型 `XxxOverrides`，承载低频外观差异。每个字段都是可选项；除非
组件需要用显式三态包装区分“有意设置为 `null`”与“继承”，否则 `null` 表示未指定。

组件实例和作用域 Provider 接收同一种稀疏模型。解析严格按以下顺序逐字段执行：

1. 实例 Overrides；
2. 最近的匹配作用域 Provider；
3. 逐字段合并后的外层匹配 Provider；
4. 组件 Defaults 或当前设计系统 Recipe；
5. 语义主题 Token。

内层稀疏 Provider 不会丢弃未覆盖的外层字段。Provider 使用具名 UI Local，Diagnostics 会显示组件族
名称，而不是分配顺序名称。

Overrides 只包含外观：颜色、排版、Shape、Border、视觉尺寸和状态层。受控状态、Callback、Enabled、
Selection、导航策略、图片加载、键盘行为、Lifecycle、复用和资源所有权仍是明确的组件契约。经常变化
的语义内容和高频定制的主要值，也可以在有助于常规调用清晰度时保留为直接参数。

### Basic 原语使用完整的已解析 Style

负责组合或绑定设计系统中立视觉的 `Basic*` 原语接收完整 `BasicXxxStyle`。Style 不包含可空继承
标记、Variant 身份、Theme 查询或设计系统策略，并且必须在进入原语前完成解析。同一个组件函数
不会同时暴露完整 Style 与稀疏 Overrides。

设计系统模块拥有自己的强类型 Recipe，并直接构造完整 Basic Style。Foundation 不定义全局通用
Recipe Bundle 或 Registry；Android Renderer 仍只接收已解析的 `NodeSpec` 值。

最终链路是：

`Theme -> 设计系统 Recipe 或 Foundation Defaults -> 作用域 Overrides -> 实例 Overrides -> 已解析 Style/NodeSpec -> Renderer`

### API 与兼容策略

这是一次有意的硬切。旧 `*ColorOverride` 与 `Provide*Colors` API 会被替换，不会与新模型并存。
低频外观参数进入组件强类型 Overrides，非外观参数继续保留为直接参数。`BasicTextField` 用
`BasicTextFieldStyle` 替换分散的外观参数。

稀疏 Overrides 是 Q2 不可变契约。作用域 Provider 和受影响的高层组件属于 Q3，因为嵌套与优先级
会影响整个子树。完整 Basic Style 属于 Q2，Basic 原语继续属于 Q3。每个硬切阶段必须同时交付
规范英文 KDoc、可编译 Sample、模块文档与确定性测试。

## 后果

- 常规组件调用保持精简，少量局部偏差只有一个强类型逃生口。
- 嵌套作用域可以稳定组合，不需要把 `UiThemeOverride` 变成组件矩阵。
- 输入控件状态色不会在无关的原生控件族之间泄漏。
- Basic 原语为 Material 3、One UI 和未来设计系统提供确定性输入。
- 新增低频外观槽只修改一个 Overrides 模型，不再影响每个普通调用点。
- 框架仍无法推断任意非 State 捕获值是否变化；本决策不改变 Lazy 内容的显式 Revision 契约。
- Overrides 字段较多可以接受，因为它不会污染主组件签名；不能为了减少参数就把行为或生命周期
  字段移入其中。
- 普通/扩展 FAB、顶部/底部 AppBar、Badge、AlertDialog 与 Modal Bottom Sheet 现在都遵循该边界。
  普通/扩展 FAB、顶部/底部 AppBar 分别使用独立类型，因为它们的几何与内容角色不可互换。
- TopAppBar 分别持有导航与操作槽的内容色作用域，BottomAppBar 持有 Row 内容色作用域；子级
  IconButton 的实例 Overrides 仍具有最终优先级。
- Modal Bottom Sheet 在跨越 Overlay Session 边界前解析为一份不可变请求快照。所有 Presenter
  都在首次展示与同 Key 更新时应用完整快照，包括封闭的“精确颜色/恢复平台默认值”导航栏策略。
- Scaffold 与原始 Dialog 有意不提供外观 Overrides。Scaffold 暴露的是页面 Surface 的主要布局
  输入，原始 Dialog 是带调用方自有内容的生命周期/定位协议；为二者制造稀疏视觉 Patch 会模糊
  所有权。

## 被否决的方案

### 把每个视觉属性都直接放在组件上

低频逃生口会主导 API 发现过程，并让常规 DSL 随组件保真度持续膨胀，因此否决。

### 给高层应用组件传完整 Style

调用方会为了一个小差异被迫重建设计系统 Recipe 和 Disabled/Error 状态，因此否决。

### 使用一个全局组件 Style Registry

它会把无关设计系统策略集中到 Foundation，削弱模块所有权，并让扩展与诊断依赖非强类型查询，
因此否决。

### 用 Modifier 编码组件外观

组件语义槽和状态相关值不是通用 Layout、Drawing、Input 或 Semantics 操作。Modifier 顺序会掩盖
优先级，也无法清晰表达完整组件解析，因此否决。

### 在新 Overrides 旁继续保留专用颜色 Provider

两条竞争的优先级路径会保留现有歧义并扩大一倍兼容面，因此否决。

## 验证

每个迁移组件族必须覆盖：空 Patch 回落、实例优先级、嵌套逐字段合并、Provider 恢复、适用的
Enabled/Disabled/Error 状态解析和环境变化。Basic Style 必须有直接 NodeSpec 映射测试，并证明
Basic 原语不读取 Theme 或 Local。设计系统与 Demo 编译证明下游迁移；API Dump 与文档门禁保护
硬切后的公开表面。

依赖 Overlay 的组件还必须测试 Presenter 的同 Key 外观更新与可逆平台策略。只测试协议对象相等性
并不充分，因为 Window Flag、原生容器 Shape/Color 与恢复系统栏策略都属于 Presenter 自有副作用。
