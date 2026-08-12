---
translation_source: migration/compose-layout-modifier-and-environment.md
translation_source_hash: ed7dd2ca78ff3768028b0ca64433ca63cd1cededf2575ed4df288ee8263b2a2c
translation_status: current
---

# 迁移 Compose 布局、Modifier 与环境代码

本文对比 Jetpack Compose 与 ViewCompose 的布局、Modifier 和 CompositionLocal 语义。它是一份
工程迁移参考，而不是 API 名称对等表。语法相似并不表示测量、生命周期、失效或 Android 集成
行为等价。

## 基线、状态术语与验证日期

| 基线 | 版本 | 用途 |
| --- | --- | --- |
| ViewCompose 目标模块 | runtime `0.1.0-alpha02`；UI Contract 与 Host `0.1.0-alpha03`；UI Foundation 与 Renderer `0.1.0-alpha01` | 本迁移指南的目标版本 |
| Compose Runtime、UI 与 Foundation | `1.11.4` stable | 上游语义参考 |
| 仓库 Compose 依赖 | `1.7.8` | 本仓库中的可执行对照基线 |
| 仓库 Kotlin 工具链 | `2.0.21` | 对照代码的编译基线 |

上游基线由 AndroidX 官方的 [Compose Runtime](https://developer.android.com/jetpack/androidx/releases/compose-runtime)、
[Compose UI](https://developer.android.com/jetpack/androidx/releases/compose-ui) 和
[Compose Foundation](https://developer.android.com/jetpack/androidx/releases/compose-foundation)
发布说明确认。仓库基线声明在固定 revision 的
[`gradle/libs.versions.toml`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/gradle/libs.versions.toml)
第 3 行和第 22 行。

本文严格使用四种能力状态：

- **Supported**：迁移目标保护相关可观察行为，尽管名称或实现细节可能不同。
- **Partially supported**：存在可行替代方案，但 Compose 契约的重要部分缺失或范围更窄。
- **Intentionally different**：ViewCompose 提供刻意设计的替代契约；代码需要重新设计，而不是简单改名。
- **Unsupported**：在已验证基线中不存在公开等价能力。

最后验证日期：**2026-08-06**。

复核负责人：**ViewCompose UI Contract、UI Foundation 与 Android Renderer 维护者**。

## 证据模型

本对比包含两层不能混为一谈的证据：

1. **官方语义复核**使用 Android Developers API 文档、行为指南和 Compose 1.11.4 的
   AndroidX 发布说明。这些来源定义本文描述的上游行为。
2. **本地可执行证据**使用上面这组独立版本化的 ViewCompose 目标源码契约和仓库测试。仓库的
   Compose 1.7.8 依赖可用于编译对照，但不能用来否定 Compose 1.11.4 已记录的语义变化。

本文不声明性能等价。本次复核没有为 Compose 布局节点与 Android View 建立可比较的基准测试条件。

## 可编译的成对起点

下面的对照在两侧都保留一个水平布局、一条有序 Modifier 链和一个作用域环境值。代码片段从
已编译的 `:samples:compose-migration` 模块提取，并由 `qaQuick` 检查是否与源码完全一致。

Compose 源码：

{/* paired-sample source="samples/compose-migration/src/main/java/com/viewcompose/samples/migration/layout/ComposeLayoutSample.kt" region="compose-layout" */}
```kotlin
private val LocalContentPadding = compositionLocalOf { 8.dp }

@Composable
fun ComposeProfileRow(name: String) {
    CompositionLocalProvider(LocalContentPadding provides 16.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(LocalContentPadding.current),
        ) {
            BasicText(name)
        }
    }
}
```
{/* paired-sample-end */}

ViewCompose 目标：

{/* paired-sample source="samples/compose-migration/src/main/java/com/viewcompose/samples/migration/layout/ViewComposeLayoutSample.kt" region="viewcompose-layout" */}
```kotlin
private val LocalContentPadding = uiLocalOf { 8.dp }

fun UiTreeBuilder.ViewComposeProfileRow(name: String) {
    ProvideLocal(LocalContentPadding, 16.dp) {
        Row(
            verticalAlignment = VerticalAlignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(UiLocals.current(LocalContentPadding)),
        ) {
            Text(name)
        }
    }
}
```
{/* paired-sample-end */}

相似的代码结构不表示引擎等价。Compose 测量布局节点并跟踪 `CompositionLocal` 读取；
ViewCompose 渲染 Android View、按 renderer 规则折叠 Modifier 元素，并把 `UiLocal` 当作作用域
查询，而不是失效订阅。

## 能力矩阵

| 概念 | Compose 1.11.4 行为 | ViewCompose 已验证版本集合行为 | 状态 | 必需的迁移动作 |
| --- | --- | --- | --- | --- |
| 内置布局容器 | `Row`、`Column`、`Box` 和 Foundation 布局在 `Constraints` 下测量 Compose 布局节点。 | `Row`、`Column`、`Box`、流式布局、滚动容器和 ConstraintLayout 发出 VNode，并最终成为 Android `ViewGroup` 实现。 | Partially supported | 在原生 View 实现上重新检查默认值、溢出、裁剪、weight 和固有尺寸假设。 |
| 自定义测量 | `Layout`、`MeasurePolicy` 和布局 Modifier 节点允许应用代码测量并放置 Compose 子项。普通测量中每个子项只能测量一次。 | 未发现公开的通用测量策略、measurable/placeable 契约或布局 Modifier。自定义多子项测量需要 renderer 扩展，或通过互操作托管 Android `ViewGroup`。 | Unsupported | 围绕内置容器或具有生命周期所有权的 Android View 实现重新设计自定义 Compose 布局。 |
| 尺寸与填充 | 布局 Modifier 会变换或约束 Modifier 链；`size` 仍受传入约束限制，支持相关重载时 fill API 可接受比例。 | 精确 dp 尺寸会成为像素 LayoutParams；fill 辅助方法会成为 `MATCH_PARENT`。轴向专用的 width 或 height 相对 `size` 具有固定 renderer 优先级。 | Partially supported | 迁移最终测量契约，而不只是函数名。审查受约束尺寸、比例填充、required size 和固有尺寸行为。 |
| Padding 与 margin | 每个布局 Modifier 都在 Modifier 链中的当前位置参与处理。Compose 通常通过布局结构或 padding 表达外部空间，而不是 margin 属性。 | Padding 是原生 View 内容内边距。Margin 是显式的原生父级 LayoutParams 数据。重复 padding 或 margin 元素会解析为该类型的最后一个元素。 | Intentionally different | 将重复 padding 规范化为单个预期值，并明确判断原外层 padding 应属于父级结构、View padding 还是 ViewCompose margin。 |
| 作用域父数据 | `RowScope.weight`、`ColumnScope.weight`、对齐和 `BoxScope.matchParentSize` 等作用域安全 Modifier 向兼容的直接父级提供数据。 | `RowScope` 和 `ColumnScope` 提供 weight 与交叉轴对齐；`BoxScope` 提供对齐。错误使用父数据会产生警告。没有已验证的 `matchParentSize` 等价能力。 | Partially supported | 仅在匹配容器的直接子项上使用作用域 Modifier。重新设计 `matchParentSize`；不要直接替换为 `fillMaxSize`。 |
| Constraint 父数据 | Compose ConstraintLayout 在自己的测量模型中消费 layout ID 和 constraint 父数据。 | 可选 ConstraintLayout 模块把 layout ID 和 constraint spec 映射为 AndroidX ConstraintLayout LayoutParams 与 ConstraintSet 操作。 | Partially supported | 针对 AndroidX View 实现重新验证尺寸、baseline、RTL 锚点和依赖环。 |
| Modifier 顺序 | Modifier 元素形成有序包装链；顺序可以改变测量、绘制、输入、焦点和语义。 | 源码链有序，但 renderer 会将其折叠为分阶段值。许多重复值采用后者覆盖前者，部分冲突使用固定优先级，z-index 会相加，draw 或 shadow 分组保留顺序。 | Intentionally different | 迁移前，按照 ViewCompose 解析规则为每条非平凡 Modifier 链分类。 |
| Modifier 相等性与更新 | `ModifierNodeElement` 通过相等性决定是否更新已有 `Modifier.Node`。 | Modifier 链按有序元素序列进行结构比较。相等的链可使 renderer diff 跳过子树。`NativeViewElement` 的相等性只使用稳定 key，忽略回调身份。 | Supported | 当原生配置的语义发生变化时使用会变化的 key，不要依赖新的 lambda 实例强制更新。 |
| 自定义 `Modifier.Node` 生命周期 | 公开节点 API 提供 create/update、attach/detach、失效、local 读取，以及专门的布局、绘制、输入或语义节点接口。 | `ModifierElement` 是 renderer 标记，不是应用生命周期节点。内置元素由已知 renderer 分支解释。不存在自定义节点 attach/detach 或能力接口的公开等价物。 | Unsupported | 使用受支持 Modifier、可重放的 `nativeView`、具备事务语义的 `AndroidView`，或经过评审的 renderer 功能。不要从应用代码发布 renderer 无法识别的元素。 |
| Density 与字体缩放 | `LocalDensity` 为布局和绘制代码提供 dp/sp 转换。 | `UiDensity` 会被捕获进每个 VNode 环境。Android host 从资源读取 density 和 font scale；renderer 在原生边界转换单位。 | Supported | 在声明中保留逻辑 dp/sp，不要跨新的环境快照保留已转换像素。 |
| 布局方向与 locale | CompositionLocal 提供布局方向和 locale 数据；逻辑 start/end API 根据该环境解析。 | 方向和 locale 列表会被捕获到 VNode。Renderer 应用原生 View 方向和 TextView locale，但 padding、margin、offset 与 inset 边参数使用物理 left/right。 | Partially supported | 审查所有 start/end 假设并执行真实 RTL 布局检查；不要把 Compose 逻辑边直接翻译成 ViewCompose 物理边。 |
| CompositionLocal 传播 | `compositionLocalOf` 跟踪读取点；提供值发生变化时使读取者失效。`staticCompositionLocalOf` 以更大粒度使 provider 内容失效。 | `UiLocal` 在构建树时使用线程作用域 map。Emit 会把完整 local 快照作为输入比较，但读取 `UiLocals.current` 本身不会登记失效依赖。 | Intentionally different | 用 ViewCompose state 或其他 host 失效来源承载会变化的 local 值。把 local 读取视为作用域值查询，而不是观察。 |
| 延迟内容 local | Lazy 和其他 subcompose 内容通过拥有它的 Compose composition 观察 local。 | Lazy、pager、tab、overlay 和 navigation session 会显式捕获不透明 local 快照，并在延迟内容渲染时恢复。快照变化会参与 content token 或 session 更新。 | Supported | 保持稳定的 item/page key 与 content token，让容器刷新捕获的快照，而不是保留 builder。 |
| 系统栏与 IME inset | Inset padding 感知布局、参与自动嵌套消费、避免重复应用已消费部分，并跟随 IME 更新和动画。 | 系统栏与 IME Modifier 在目标 View 上安装 AndroidX listener，并把选中的物理边加到基础 padding。嵌套 ViewCompose Modifier 不交换已消费 inset 状态；同一 View 上的系统栏与 IME 值会相加。 | Partially supported | 为 inset 指定明确的所有者层级，避免祖先和后代重复应用，并避免把 `adjustResize` 与重复的 IME padding 组合。 |
| Android 输出与 View 互操作 | Compose 通常渲染 Compose 节点；`AndroidView` 嵌入平台 View，并提供 factory/update 以及可选的复用/释放回调。 | 所有第一方节点最终都成为 Android View。ViewCompose `AndroidView` 增加事务回滚和事务后提交语义；`nativeView` 对已挂载 View 应用可重放配置。 | Intentionally different | 将可重复配置、一次性工作和清理分开，分别放入 update/native 配置、`onCommit` 和 `onRelease`。 |

## 两种布局引擎：Compose Constraints 与 Android Views {/* #two-layout-engines-compose-constraints-and-android-views */}

Compose 布局是一套节点协议。父级传递约束，子项报告测量尺寸，父级再放置得到的 placeable。
官方[自定义布局文档](https://developer.android.com/develop/ui/compose/layouts/custom)还定义了普通
布局的单次测量规则和公开的 `Layout` 逃生通道。

ViewCompose 会先构建不可变 VNode。Android renderer 随后创建原生 widget 和容器。例如，Text
会成为 `TextView`，Row 和 Column 会成为设置了方向的 `DeclarativeLinearLayout`，Box 会成为
`DeclarativeBoxLayout`。完整映射见固定 revision 的
[`ViewNodeFactory.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/main/java/com/viewcompose/renderer/view/tree/binder/core/ViewNodeFactory.kt)
第 55–126 行。Row 和 Column 保留 Android `LinearLayout` 测量，并在原生放置阶段实现声明式
arrangement；参见
[`DeclarativeLinearLayout.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/main/java/com/viewcompose/renderer/view/container/layout/DeclarativeLinearLayout.kt)
第 21–92 行。

因此，Compose 自定义 `Layout` 无法翻译成普通 ViewCompose 组件。请选择以下边界之一：

- 使用内置 ViewCompose 容器表达结果；
- 当 constraint 父数据足够时使用可选 ConstraintLayout 模块；
- 当应用专属测量不可或缺时，通过 `AndroidView` 托管自定义 Android `ViewGroup`；或
- 当该行为属于可复用框架契约时，提出有文档支撑的 renderer 功能。

后两种选择并不等同于接收 Compose `Measurable`。Android measure spec、LayoutParams、
request-layout 传播和平台 View 状态仍是权威行为。

## Size、padding、margin 与 fill 语义

Compose 会让布局 Modifier 作为有序参与者在约束传播中依次处理。上游模型以官方
[约束与 Modifier 顺序指南](https://developer.android.com/develop/ui/compose/layouts/constraints-modifiers)
为准。

ViewCompose 通过原生 LayoutParams 解析尺寸。感知父级的优先级如下：

1. ConstraintLayout dimension（如果存在）；
2. 轴向专用的 `width` 或 `height` Modifier；
3. `size` 对应轴的值；
4. renderer 根据节点和父级确定的默认值。

该优先级实现在固定 revision 的
[`ViewLayoutParamsFactory.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/main/java/com/viewcompose/renderer/view/tree/pipeline/ViewLayoutParamsFactory.kt)
第 73–99 行。精确 dp 尺寸使用 VNode 捕获的 density 转换。Fill 辅助方法映射为 Android
`MATCH_PARENT`；它们不会保留 Compose 的所有比例或固有尺寸选项。

Padding 与 margin 有不同的原生落点：

- padding 成为已挂载 View 的内容内边距；
- margin 成为父级 LayoutParams 上的物理 left、top、right 和 bottom 值；
- offset 成为 View translation，不改变兄弟节点的测量或放置；
- minimum width 和 height 成为 View 最小尺寸。

重复 padding 不会创建嵌套布局层。Resolver 只保留最后一个 padding 元素。重复 margin 也遵循
同一规则。因此迁移时应先规范化 Compose Modifier 链，并通过容器结构保留预期的内外边界。

公开尺寸与边缘契约见固定 revision 的
[`ModifierLayoutExtensions.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-ui-contract/src/main/kotlin/com/viewcompose/ui/modifier/ModifierLayoutExtensions.kt)
第 6–187 行和第 189–290 行。原生 LayoutParams 应用见
[`ViewLayoutParamsFactory.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/main/java/com/viewcompose/renderer/view/tree/pipeline/ViewLayoutParamsFactory.kt)
第 91–149 行和第 168–192 行。

## Row、Column、Box 与作用域父数据

两个框架都使用 receiver scope，使常见父数据靠近兼容父级。Compose 行为和
`matchParentSize` 区别记录在 [Compose modifiers](https://developer.android.com/develop/ui/compose/modifiers#scope-safety)
中。

ViewCompose 提供以下受支持作用域操作：

| Scope | 支持的父数据 | 原生落点 |
| --- | --- | --- |
| `RowScope` | 正数 `weight`；垂直 `align` | 横向 LinearLayout weight 与子项 gravity |
| `ColumnScope` | 正数 `weight`；水平 `align` | 纵向 LinearLayout weight 与子项 gravity |
| `BoxScope` | Box `align` | FrameLayout 子项 gravity |

声明和正数 weight 校验见固定 revision 的
[`LayoutScopes.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/dsl/LayoutScopes.kt)
第 12–96 行。父数据校验见
[`ModifierParentDataValidator.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/main/java/com/viewcompose/renderer/layout/ModifierParentDataValidator.kt)
第 28–97 行。

这些 receiver scope 暴露的操作是受支持的应用 API，但作用域本身并不是完整的运行时类型安全
屏障。renderer 集成仍可看到契约元素类，不兼容父级只会产生去重后的警告，而不会导致渲染
失败。应把每个作用域 Modifier 都视为直接子项数据。

本文刻意把 Compose `BoxScope.matchParentSize` 标为 Unsupported。Compose 使用它匹配 Box
的最终尺寸，但不会让该子项决定 Box 尺寸。ViewCompose `fillMaxSize` 映射为 `MATCH_PARENT`，
不得把它记录为等价替代方案。

## ConstraintLayout 父数据

可选 ConstraintLayout 模块以父数据形式提供 layout ID 和 constraint item spec。Android
renderer 通过 AndroidX ConstraintLayout 消费这些值。固定尺寸从子项捕获的环境转换；
fill-to-constraints 使用 Android ConstraintLayout 的零尺寸约定。

契约元素定义在固定 revision 的
[`ModifierElementsLayout.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-ui-contract/src/main/kotlin/com/viewcompose/ui/modifier/ModifierElementsLayout.kt)
第 117–150 行。感知父级的转换实现在
[`ViewLayoutParamsFactory.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/main/java/com/viewcompose/renderer/view/tree/pipeline/ViewLayoutParamsFactory.kt)
第 91–98 行和第 247–255 行。

这是可行迁移路径，不是 Compose ConstraintLayout 对等性的证明。应根据 ViewCompose 模块契约
重新检查 ConstraintSet 合并、baseline 连接、逻辑 start/end 锚点、循环依赖和 dimension 默认值。

## Modifier 顺序、折叠与相等性 {/* #modifier-ordering-folding-and-equality */}

两种 Modifier 都是不可变有序链。ViewCompose 追加元素时不会修改 receiver，并对得到的序列
执行结构比较；参见固定 revision 的
[`Modifier.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-ui-contract/src/main/kotlin/com/viewcompose/ui/modifier/Modifier.kt)
第 3–56 行。

关键区别在执行方式。Compose 布局和行为节点会保留其在包装节点链中的位置。ViewCompose 会把
元素折叠成 `ResolvedModifiers` 快照，再由不同 renderer 阶段消费。已验证的折叠规则包括：

| Modifier 关系 | ViewCompose 规则 |
| --- | --- |
| 重复的标量或单槽位元素 | 同类型中靠后的元素通常替换靠前的值。 |
| `shape` 与旧版 `cornerRadius` | 两者互斥；链中靠后的一个会清除靠前的一个。 |
| 重复 `zIndex` | 数值相加。 |
| Draw 与高级 shadow 分组 | 分组保留声明顺序。 |
| 轴向 `width`/`height` 与 `size` | 轴向专用值通过固定 LayoutParams 优先级胜出，与跨类型链顺序无关。 |
| `graphicsLayer` 与简单 alpha、offset 或 clip | 提供 graphics-layer 值时，它具有固定 renderer 优先级。 |
| 系统栏与 IME inset padding | 两者都会保留，选中的物理边会相加。 |

折叠逻辑实现在固定 revision 的
[`ResolvedModifiers.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/main/java/com/viewcompose/renderer/modifier/ResolvedModifiers.kt)
第 72–172 行。不要根据一个 Modifier 家族推断另一个家族的规则。

相等性还会驱动复用。当节点、环境、spec、children 和 Modifier 输入保持等价时，
`NodeBindingDiffer` 可以跳过子树。环境或 Modifier 变化会导致 rebind；参见固定 revision 的
[`NodeBindingDiffer.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/main/java/com/viewcompose/renderer/view/tree/binder/core/NodeBindingDiffer.kt)
第 22–75 行。

`NativeViewElement` 是特殊情况。它的相等性和 hash code 只使用 `stableKey`，刻意忽略回调
身份。该契约位于固定 revision 的
[`ModifierElementsInteraction.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-ui-contract/src/main/kotlin/com/viewcompose/ui/modifier/ModifierElementsInteraction.kt)
第 220–249 行。使用相同 key 的新 lambda 不是更新信号。原生操作语义发生变化时应更改 key，
或让另一个可观察节点输入使绑定失效。

## 为什么 Modifier.Node 不能直接迁移 {/* #why-modifiernode-does-not-migrate-directly */}

Compose 推荐用 `Modifier.Node` 实现自定义 Modifier 行为。其公开模型包括不可变 element、
保留的 node、create/update、attach/detach、自动或显式失效、CompositionLocal 访问，以及专门的
节点接口。上游参考为[创建自定义 Modifier](https://developer.android.com/develop/ui/compose/custom-modifiers)
和 [`Modifier.Node` API](https://developer.android.com/reference/kotlin/androidx/compose/ui/Modifier.Node)。

ViewCompose 没有等价的公开生命周期节点协议。`ModifierElement` 是 renderer 所理解契约的标记；
参见固定 revision 的
[`Modifier.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-ui-contract/src/main/kotlin/com/viewcompose/ui/modifier/Modifier.kt)
第 59–65 行。应用自定义实现不会被发现为自定义行为。

请根据所有权选择以下替代方案：

- 框架定义行为使用受支持的 ViewCompose Modifier；
- 已挂载 View 的可重复配置使用 `nativeView`；
- 应用代码拥有原生 View 及其释放生命周期时使用 `AndroidView`；
- 新的可复用 Modifier 能力应通过有文档支撑的 UI-contract 与 renderer 变更实现。

无法识别的 `ModifierElement` 不是安全扩展点。`nativeView` 也不是通用节点生命周期：它没有
attach/detach 回调，而且配置可能在回滚时重放。

## Density、locale 与布局方向 {/* #density-locales-and-layout-direction */}

Compose 通过平台 CompositionLocal 暴露 density 与逻辑方向。官方
[Compose 平台 local 参考](https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/package-summary)
定义了 `LocalDensity`、`LocalLayoutDirection` 和 locale 相关 local。

ViewCompose 会在每个发出的 VNode 上捕获不可变 `UiEnvironmentValues`，其中包含：

- `UiDensity`，包括 density 和 font scale；
- 有序 `UiLocaleList`；
- `UiLayoutDirection`；
- Host 所有的 `resourceRevision`，用于在 Configuration 或主动资源变化后重新绑定相等的 Android
  资源 ID。

快照契约要求平台 Configuration 变化后生成新树。标准 Android Host 会通过资源环境自动调度新树；
自定义 Host 必须显式发布新环境。参见固定 revision 的
[`UiEnvironmentValues.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-ui-contract/src/main/kotlin/com/viewcompose/ui/environment/UiEnvironmentValues.kt)
第 92–112 行。Android bridge 在
[`AndroidEnvironmentBridge.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-host-android/src/main/java/com/viewcompose/host/android/environment/AndroidEnvironmentBridge.kt)
第 15–29 行读取资源和 configuration。单位转换定义在
[`UiUnits.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-ui-contract/src/main/kotlin/com/viewcompose/ui/unit/UiUnits.kt)
第 157–223 行。

绑定时，renderer 把环境存到 View、应用原生布局方向并设置 TextView locale。该边界位于固定
revision 的
[`ViewModifierApplier.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/main/java/com/viewcompose/renderer/view/tree/binder/core/ViewModifierApplier.kt)
第 41–55 行。环境变化会强制完整节点 rebind，而不是只执行视觉 patch。

Modifier API 边界的方向支持并不完整。Row、Column、Box 和 Constraint 对齐类型可以表达逻辑
start/end 行为，但以下 API 使用物理边：

- padding 与 margin：left 和 right；
- offset：正 x 向右移动；
- 系统栏或 IME 边选择：left 和 right。

每次迁移非对称水平空间都必须明确 RTL 决策。Compose 中的 `start` 不能静默变为 `left`。

## UiLocal 与 CompositionLocal {/* #uilocal-versus-compositionlocal */}

Compose 区分会追踪读取的 `compositionLocalOf` 与宽粒度的 `staticCompositionLocalOf` 失效。
上游行为记录在[使用 CompositionLocal 在本地确定数据作用域](https://developer.android.com/develop/ui/compose/compositionlocal)
中。

ViewCompose `UiLocal` 是一个类型化句柄，指向构建 VNode 树时使用的线程作用域不可变 map。
`ProvideLocal` 为嵌套 block 安装值，block 结束后恢复之前的 map。`ProvideLocals` 为多个绑定
执行相同操作。实现位于固定 revision 的
[`UiLocals.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/context/UiLocals.kt)
第 3–103 行，以及
[`LocalValue.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/context/LocalValue.kt)
第 35–120 行。

关键迁移规则是：`UiLocals.current(local)` 是查询，不是观察。它不会把调用点登记为依赖读取者。
相反，`UiTreeBuilder.emit` 会把完整当前 local 快照作为一个 composition 输入捕获。当其他失效
已经触发 composition 时，如果该快照不同，节点 group 会重建。参见固定 revision 的
[`UiTreeBuilder.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/dsl/UiTreeBuilder.kt)
第 66–124 行和第 192–214 行。

因此：

- 把会变化的源数据存入 ViewCompose state，而不是只放入普通 provided object；
- 不要期望修改相等 local 值内部的可变字段会调度渲染；
- 优先使用具有有效相等性的不可变 local 值；
- local 快照变化可能比 Compose 受跟踪 local 读取使更多工作失效；
- 自定义 host 必须在所属 renderer 线程上串行构建树。

## 延迟内容与 local 快照

Lazy collection、pager、tab、overlay 和 navigation 可以在声明作用域返回后渲染内容。
ViewCompose 会为这些边界显式保存 local。

对于 lazy list，`LazyItemCollector` 会捕获 `LocalSnapshot`、把它包含进有效 content token、
使用该快照创建子 session，并在更新时同时刷新快照和内容闭包。参见固定 revision 的
[`LazyCollectionScope.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/dsl/collection/LazyCollectionScope.kt)
第 147–193 行，以及
[`WidgetLazyListItemSession.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/session/WidgetLazyListItemSession.kt)
第 8–72 行。

这会在 holder 复用期间保留嵌套 local 值，但不会移除调用方的身份责任。Key 必须稳定且唯一。
当 local 快照之外捕获的业务值发生变化时，content token 必须变化。内容 block 返回后，不要保留
或调用 `UiTreeBuilder`。

## 系统栏与 IME inset {/* #system-bar-and-ime-insets */}

Compose inset padding 会在布局期间使用当前 inset 值，并向嵌套 Modifier 传达已消费部分。
官方 [inset UI 指南](https://developer.android.com/develop/ui/compose/system/insets-ui)解释了嵌套
消费、尺寸 Modifier 与 IME 动画行为。

ViewCompose 提供两个聚焦的 Modifier：

- `systemBarsInsetsPadding`，用于选择物理系统栏边；
- `imeInsetsPadding`，默认选择物理 bottom 边。

Renderer 会安装 AndroidX `WindowInsetsCompat` listener、记录基础 padding，并加上选中的 inset
像素。移除两个 Modifier 后会恢复基础 padding 并移除 listener。实现位于固定 revision 的
[`ModifierInsetsApplier.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/main/java/com/viewcompose/renderer/view/tree/binder/core/modifier/ModifierInsetsApplier.kt)
第 11–128 行。

与 Compose 不同，该 listener 原样返回传入 inset，不会传达祖先 ViewCompose Modifier 已应用的
数量。因此嵌套 ViewCompose inset-padding Modifier 可能再次添加同一个 inset。同一 View 上选择的
系统栏和 IME padding 也会相加，而不是由共享消费模型抵扣。

迁移规则：

1. 尽可能为每个 inset 边选择唯一所有者。
2. 检查原生祖先和嵌入 View 是否有自己的 inset 处理。
3. 除非刻意验证最终位移，否则不要把 Activity `adjustResize` 与重复的 `imeInsetsPadding` 组合。
4. 在真实托管页面上测试手势导航、三键导航、横屏、RTL、display cutout 和一次 IME 过渡。
5. 不要声明 Compose 嵌套消费或同帧布局对等性。

当前单元测试只保护 Modifier 默认值和解析，不覆盖真实 WindowInsets 分发、嵌套消费或动画。
该限制记录在下方可执行证据中。

## Android View 输出与互操作

Compose 通常渲染自己的 UI 节点，并把 `AndroidView` 作为互操作边界。官方
[在 Compose 中使用 View 指南](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/views-in-compose)
定义了 factory、update、reuse、reset 和 release 行为。

ViewCompose 在根本上不同：每个第一方 VNode 都会成为 Android View。其 `AndroidView` API
仍是应用创建 View 的独立所有权边界，并具有事务感知生命周期：

| 回调 | ViewCompose 契约 |
| --- | --- |
| `factory` | 仅当 reconciliation 需要新的原生节点时运行。 |
| `update` | 在插入、patch 或回滚期间执行可重复配置。 |
| `onReset` | 保留的 View 重新绑定前执行可选的可重复 reset。 |
| `onCommit` | 仅在完整 View 树事务提交后发布一次性工作。 |
| `onRelease` | 每当已创建 View 被永久放弃时执行一次性清理，包括已提交移除、session disposal 或未提交候选项回滚。 |

公开契约位于固定 revision 的
[`AndroidInteropDsl.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-host-android/src/main/java/com/viewcompose/host/android/AndroidInteropDsl.kt)
第 11–82 行。挂载和 commit 调度位于
[`ViewTreePatchPipeline.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/main/java/com/viewcompose/renderer/view/tree/pipeline/ViewTreePatchPipeline.kt)
第 527–579 行。

`update`、`onReset` 和 `nativeView` 不得启动不可重复的外部工作。失败帧可能恢复之前已提交的
原生树并重放配置。只在成功后运行的操作应放入 `onCommit`，所拥有资源的清理应放入
`onRelease`。Renderer 测试会释放新创建的回滚候选项，尽管当前公开 `AndroidView` 文案只提到
已提交移除和 session disposal。在公开契约与实现对齐之前，应把 `onRelease` 视为任何永久放弃的
已创建 View 的清理回调。

## 迁移检查清单

1. 记录来源 Compose 版本和目标 ViewCompose 模块的精确版本。
2. 将每个布局分类为内置布局、基于 constraint 的布局或自定义测量布局。
3. 先替换布局行为，再翻译视觉 Modifier 名称。
4. 根据 ViewCompose 折叠规则规范化重复 size、padding、margin、graphics-layer 和 draw 元素。
5. 仅在匹配 scope 的直接子项上使用父数据 Modifier，并重新设计 `matchParentSize` 用法。
6. 映射物理 Modifier 参数前，识别每个逻辑 start/end 边。
7. 把会变化的 provided 值移到 ViewCompose state 后面；不要依赖 `UiLocal` 读取追踪。
8. 跨 View 与 ViewCompose 边界显式分配系统栏和 IME inset 所有权。
9. 分离 Android View 可重放配置、提交后工作和释放清理。
10. 声明迁移完成前，为测量、RTL、local 更新、延迟 session、inset 分发和互操作回滚添加行为测试。

## 源码与可执行证据

以下本地证据保护本文中的声明：

- Modifier 不可变性、结构相等性、声明顺序、draw 顺序和父数据构造：固定 revision 的
  [`ModifierContractTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-ui-contract/src/test/kotlin/com/viewcompose/ui/modifier/ModifierContractTest.kt)，
  第 20–49、85–117 和 170–212 行。
- Modifier 折叠、z-index 相加、有序 shadow 与 ConstraintLayout 父数据：固定 revision 的
  [`ResolvedModifiersTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/modifier/ResolvedModifiersTest.kt)，
  第 38–47、82–129 和 165–205 行。
- 兼容与不兼容的作用域父数据：固定 revision 的
  [`ModifierParentDataValidatorTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/layout/ModifierParentDataValidatorTest.kt)，
  第 31–159 行。
- 结构 Modifier 相等性与环境驱动的 renderer rebind：固定 revision 的
  [`NodeBindingDifferTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/view/tree/NodeBindingDifferTest.kt)，
  第 115–141 行。
- Density、locale、direction、嵌套环境值及其向 VNode 的捕获：固定 revision 的
  [`EnvironmentTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/context/EnvironmentTest.kt)，
  第 15–68 行。
- 感知 density 的 ConstraintLayout 解析：固定 revision 的
  [`DeclarativeConstraintLayoutEnvironmentTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/view/container/DeclarativeConstraintLayoutEnvironmentTest.kt)，
  第 21–79 行。
- 嵌套 `UiLocal` 提供、恢复和显式快照恢复：固定 revision 的
  [`BusinessLocalApiTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/context/BusinessLocalApiTest.kt)，
  第 13–103 行。
- Local 快照稳定性与环境驱动的子树替换：固定 revision 的
  [`SubtreeRecompositionTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/runtime/SubtreeRecompositionTest.kt)，
  第 59–123 行。
- 随捕获 local 变化的延迟 lazy、pager 与 tab content token：固定 revision 的
  [`LazyContentLocalPropagationTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/context/LazyContentLocalPropagationTest.kt)，
  第 16–90 行。
- Insets Modifier 默认值与共存：固定 revision 的
  [`InsetsPaddingModifierTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/modifier/InsetsPaddingModifierTest.kt)，
  第 14–48 行。该测试**不**覆盖真实分发、嵌套、消费或动画。
- 原生 Modifier 稳定 key 相等性：固定 revision 的
  [`NativeViewElementTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/modifier/NativeViewElementTest.kt)，
  第 14–55 行。
- AndroidView 回滚、commit 发布和 release 失败隔离：固定 revision 的
  [`ViewTreeRenderTransactionTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/view/tree/ViewTreeRenderTransactionTest.kt)，
  第 330–341 行和第 393–470 行。

现有已编译 API sample 覆盖 Modifier 链构造和 AndroidView 互操作，但当前没有已编译迁移 sample
演示 Compose 自定义布局替代方案或真实嵌套 WindowInsets 行为。本文刻意避免嵌入第二份未编译的
事实来源。

## 已知缺口与复核触发条件

以下缺口仍是迁移契约的一部分：

- 没有公开的自定义测量或 `Modifier.Node` 等价能力；
- 没有已验证的 `BoxScope.matchParentSize` 等价能力；
- 没有 tracked 与 static 两种 `UiLocal` 变体；
- 通用 padding、margin、offset 或 inset 边选择没有逻辑 start/end 变体；
- 没有嵌套 inset 消费协议；
- 没有端到端 WindowInsets 动画或 View/ViewCompose 混合消费测试；
- 公开 `AndroidView` release 文案尚未覆盖 renderer 测试所保护的回滚候选行为。

发生以下任何事件时，负责人必须复核本文：

1. Compose Runtime、UI 或 Foundation 提升所选语义基线。
2. 仓库 Compose 或 Kotlin 可执行基线变化。
3. 公开布局、父数据、Modifier、环境、local、inset 或 AndroidView 契约变化。
4. Renderer 修改 Modifier 折叠、LayoutParams 优先级、环境 rebind 或原生事务行为。
5. 新的已编译迁移 sample 或 instrumentation 测试关闭了任一已记录缺口。

复核时必须先检查官方上游文档，再检查当前 ViewCompose 源码和测试。仓库能针对旧版 Compose
artifact 构建通过，并不足以证明较新的上游语义契约没有变化。
