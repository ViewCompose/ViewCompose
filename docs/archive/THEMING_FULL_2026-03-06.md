# ViewCompose Theming

## 1. 文档定位

本文档定义 `ViewCompose` 的主题系统目标、分层模型、与 Android View / Compose 的边界，以及后续重构方向。

这份文档是主题系统的编码标准，后续新增控件和主题扩展都应以此为准。

它承担两件事：

1. 定义当前主题系统的架构和编码规范
2. 明确主题系统的分层模型、优先级规则和扩展约束

补充专题：

- 统一路线图见 [ROADMAP.md](../project/roadmap.md)
- 主题相关历史审计和拆分记录见 [docs/archive/README.md](README.md)

## 2. 结论

当前主题系统的 `API 形态` 和 `内部数据模型` 都已收敛到推荐形态：

- 有全局主题入口 `UiTheme(...)`
- 有局部主题入口 `UiThemeOverride(...)`
- 有统一上下文机制 `LocalValue / LocalContext`
- 有语义 token 和组件默认值体系
- 内部数据模型已精简为 4 字段：`colors`, `typography`, `shapes`, `controls`
- 组件默认值改为从 `Theme.colors` 即时派生，不再预计算
- 已有 `LocalTextStyle`、`LocalContentColor`、`ProvideTextStyle`、`ProvideContentColor` 等细粒度上下文
- 稀疏组件覆盖通过 `LocalValue<ButtonColorOverride?>` 等机制实现

简化结论：

> `UiTheme / UiThemeOverride` API 方向正确，内部实现已完成从”完整 resolved component tokens”到”语义主题 + 局部上下文 + 默认值按需计算 + 稀疏组件 override”的重构。

## 3. 已完成的调整

以下调整已在 Sprint A-F 中全部完成：

1. **删除 `UiThemeTokens.components`**：组件默认值不再存储在主题 token 中，改为 Defaults 对象从 `Theme.colors` 即时派生（commit `62b072f`）
2. **删除 `rebaseComponentStyles()` (270 行)**：不再需要手动级联组件字段（commit `62b072f`）
3. **降级 `UiInputColors` 和 `UiInteractionColors`**：从 theme 一级字段降为 Defaults 内部派生（commit `44b82b4`）
4. **Props 统一到 typed NodeSpec**：样式属性纳入 spec，字段级 patch 可覆盖颜色变化（commit `440f847`）
5. **新增 `Modifier.nativeView(key, configure)`**：冷门 View 属性逃生通道（commit `00cde4f`）
6. **Android 桥接补全**：typography 桥接和 uiMode 暗色自动检测（commit `78295b1`）
7. **新增细粒度 local**：`LocalTextStyle`、`ProvideTextStyle`、`ProvideContentColor` 公开 API（commit `44a51c9`）
8. **Theme.kt 拆分**：从 886 行拆为 5 个文件——`ThemeTokens.kt`、`ComponentStyles.kt`、`ThemeDefaults.kt`、`ThemeRebase.kt`、`Theme.kt`（commit `b79ee39`）

## 4. Compose 是如何解决这个问题的

Jetpack Compose 的做法可以概括成：

### 4.1 只把“基础语义主题”放在顶层

典型是：

- `colorScheme`
- `typography`
- `shapes`

### 4.2 用 `CompositionLocal` 承载局部上下文

例如：

- `LocalTextStyle`
- `LocalContentColor`
- `LocalIndication`

### 4.3 组件默认值不是提前整棵算死

而是组件在读取默认值时，按当前：

- `MaterialTheme`
- 对应 state
- 局部 locals

动态计算出自己的默认样式。

### 4.4 更细粒度局部复用依赖更小的 local

Compose 不会要求你每次为了局部文字风格调整都复制整套主题，而是更常用：

- `ProvideTextStyle`
- `LocalContentColor`
- `LocalIndication`

所以 Compose 的关键不是“有主题”，而是：

> 它把“语义主题”和“组件默认值解析”分开了，同时允许更细的局部上下文参与默认值计算。

## 5. 当前实现中已经成立的部分

当前项目里，这几部分设计仍然是正确的，应当保留：

### 5.1 `UiTheme(...)`

- 作为整套主题切换入口是合理的
- 它适合页面级、模块级、品牌级主题切换

### 5.2 `UiThemeOverride(...)`

- 作为局部主题 patch 入口是合理的
- 它适合 section 级、卡片级、局部组件组级覆盖

### 5.3 `Theme.xxx`

- 作为读取当前主题的统一入口是合理的
- 后续仍应保留 `Theme.colors / typography / shapes / controls`

### 5.4 `LocalValue / LocalContext`

- 当前 local 机制足以支撑 v1
- 主题、环境、局部上下文都应继续基于它

## 6. 推荐的主题分层

后续主题系统应按以下 4 层设计。

### 6.1 Semantic Theme Layer

这层只存”全局语义主题”，不存完整的组件默认值结果。

当前核心域（4 字段）：

- `colors`
- `typography`
- `shapes`
- `controls`

当前形态：

```kotlin
data class UiThemeTokens(
    val colors: UiColors,
    val typography: UiTypography,
    val shapes: UiShapes = UiShapeDefaults.default(),
    val controls: UiControlSizing = UiControlSizeDefaults.default(),
)
```

设计要求：

- 这层只描述语义，不描述具体控件的最终 look
- 覆盖 `primary`、`surface`、`controlCornerRadius` 时，组件默认值应自动重新跟随

### 6.2 Contextual Local Layer

这层用于表达”比整套主题更细的局部语义”。

当前已实现：

- `LocalTextStyle` / `ProvideTextStyle` — 子树默认文字样式
- `LocalContentColor` / `ProvideContentColor` — 子树默认内容色
- `Theme` — 全局主题上下文
- `Environment` — 环境上下文

后续可按需补齐：

- `LocalIndication`
- `LocalContentAlpha` 或等效机制

设计目标：

- 不要把所有局部复用都塞进 `UiThemeOverride`
- 对文本、图标、点击态这类跨组件默认值，优先考虑 local

### 6.3 Component Default Resolver Layer

这层是主题系统的真正关键。当前已改为从 `Theme.colors` 即时派生，不再读预计算 token。

例如：

```kotlin
object ButtonDefaults {
    fun containerColor(
        variant: ButtonVariant,
        enabled: Boolean,
    ): Int
}
```

其解析来源：

1. 当前 `Theme.colors`
2. 当前 `Theme.shapes`
3. 当前 `Theme.controls`
4. 当前局部 component override（`LocalValue<ButtonColorOverride?>`）
5. 当前局部 local（如 `ContentColor` / `LocalTextStyle`）

### 6.4 Sparse Component Override Layer

这层不是”完整组件样式结果”，而是”局部 patch”。当前已通过 `LocalValue<ButtonColorOverride?>` 等机制实现。

当前模型：

```kotlin
data class ButtonColorOverride(
    val primaryContainer: Int? = null,
    val primaryContent: Int? = null,
    val outlinedBorder: Int? = null,
)

private val LocalButtonColors = LocalValue<ButtonColorOverride?> { null }
```

使用侧：

```kotlin
fun UiTreeBuilder.UiButtonColorOverride(
    override: ButtonColorOverride,
    content: UiTreeBuilder.() -> Unit,
) {
    LocalContext.provide(LocalButtonColors, override) { content() }
}
```

好处：

- 只改一个字段，不需要重建完整组件 token
- 未覆盖字段自动回落到语义主题解析结果
- 局部主色改变后，未手动覆盖的按钮默认色仍可跟着变化

## 7. 推荐 API 方向

### 7.1 全局主题

继续保留：

```kotlin
UiTheme(
    tokens = appThemeTokens,
) {
    ...
}
```

职责：

- 整页换肤
- 品牌切换
- 暗色 / 亮色
- Android Theme Bridge 入口

### 7.2 局部主题

继续保留：

```kotlin
UiThemeOverride(
    colors = { copy(primary = brandRed) },
    shapes = { copy(controlCornerRadius = 24.dp) },
) {
    ...
}
```

职责：

- section 级语义覆盖
- 卡片区块主题变化
- 页面局部品牌化

### 7.3 组件默认值局部覆盖

保留这个能力，但内部应改成 patch 语义。

推荐长期形态：

```kotlin
UiThemeOverride(
    componentOverrides = {
        copy(
            button = button.copy(
                primaryContainer = brandRed,
            ),
        )
    },
) {
    ...
}
```

核心语义：

- 这是“对组件默认值的局部补丁”
- 不是“手工重建一整棵组件样式树”

### 7.4 更细的上下文能力

对于下面这些场景，优先考虑 local，而不是继续膨胀 `UiThemeOverride`：

- 一段文本局部继承某个文字风格
- 一个区域里的图标默认颜色一致
- 某个区域统一替换点击态

## 8. 优先级规则

主题相关优先级应固定为：

1. 组件显式参数 / `Modifier`
2. 局部 component override
3. 局部 semantic theme override
4. 当前全局 `UiTheme`
5. 框架默认值

说明：

- 显式传参永远优先
- component override 只覆盖当前组件默认值
- semantic override 负责改变默认推导来源

## 9. 和控件属性设计的关系

后续控件设计必须和主题设计一起看，不能分开演进。

约束如下：

### 9.1 通用视觉属性优先走 `Modifier`

例如：

- `background`
- `border`
- `cornerRadius`
- `alpha`
- `visibility`
- `padding`
- `margin`

### 9.2 组件常用视觉语义优先走 `variant / size / state`

不要给每个控件都暴露大量裸视觉参数。

优先提供：

- `variant`
- `size`
- `enabled`
- `checked`
- `isError`
- `selected`

### 9.3 主题负责默认值，不负责替代所有显式样式

主题系统的职责是：

- 给出默认值
- 给出统一语义
- 给出局部复用

它不应该替代：

- 单个组件的特殊展示
- 少数实验性视觉
- 所有 Android 原生 setter

## 10. Android Theme Bridge 的边界

这部分方向仍然正确：

- Android `Theme` 是桥，不是主模型
- 它负责从 `Context` 推导 `UiThemeTokens`
- 它不应直接控制 DSL 行为

后续即使内部主题结构重构，也不影响这个原则。

## 11. 重构完成记录

以下 Phase 已全部完成：

### Phase A ✅

- 保留现有 `UiTheme / UiThemeOverride` API
- 新文档冻结新的语义边界

### Phase B ✅

- `UiThemeTokens.components` 已删除，组件颜色改为 Defaults 即时派生
- `ButtonDefaults / TextFieldDefaults / CheckboxDefaults` 等已改为从 `Theme.colors` 动态解析
- 稀疏覆盖通过 `LocalValue<XxxColorOverride?>` 实现

### Phase C ✅

- `LocalTextStyle` / `ProvideTextStyle` 已实现
- `LocalContentColor` / `ProvideContentColor` 已实现（公开 API）
- `LocalIndication` 可按需后续补充

### Phase D ✅

- 旧的”完整组件 token 树”已彻底清理
- `rebaseComponentStyles()`（270 行）已删除
- `UiInputColors` / `UiInteractionColors` 已从 theme 降级
- `ThemeRebase.kt` 从 93 行简化为 15 行（只做 `copy()`）
- 文档与实现完全对齐

## 12. 当前判断

如果只问一句：

> 现在这套全局主题 + 局部复用设计是否合理？

当前判断是：

- `方向合理`
- `API 合理`
- `内部模型已完成重构`

如果只问一句：

> 后期是否容易扩展？

当前判断是：

- 4 字段 `UiThemeTokens` + 即时派生 Defaults + 稀疏 LocalValue 覆盖，结构清晰
- 新增控件无需修改 Theme / Rebase，只需新增 Defaults 对象
- 局部上下文通过 `ProvideTextStyle` / `ProvideContentColor` 等轻量 local 承载，不膨胀 `UiThemeOverride`
