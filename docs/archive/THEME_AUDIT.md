# 主题系统实现审计

> 审计日期：2026-03-04
> 审计人：Claude Opus 4.6
> 审计范围：主题定义、传播、消费、patch diff 全链路源码
> 前置阅读：[THEMING.md](../guides/theming.md)、[THEME_OVERRIDES.md](THEME_OVERRIDES.md)

---

## 0. 审计结论

> **实施状态**（更新于 2026-03-04）：审计发现的 7 个设计维度全部已处理。
>
> | # | 问题 | 实施状态 |
> |---|------|---------|
> | 1 | 组件样式不应住在 UiThemeTokens | ✅ 已完成 — `components` 已删除，Defaults 即时派生 (`62b072f`) |
> | 2 | props / spec 双轨冗余 | ✅ 已完成 — 样式属性已纳入 typed NodeSpec (`440f847`) |
> | 3 | UiInputColors 边界模糊 | ✅ 已完成 — 已从 theme 降级为 Defaults 内部派生 (`44b82b4`) |
> | 4 | Android 桥接遗漏 | ✅ 部分完成 — typography 和 uiMode 已桥接 (`78295b1`)，Dynamic Color 和 Shape 桥接待后续 |
> | 5 | Eager resolution 策略 | ✅ 无需改动（基准验证通过） |
> | 6 | 底层 View 冷门属性无逃生通道 | ✅ 已完成 — `Modifier.nativeView(key, configure)` (`00cde4f`) |
> | 7 | 主题 token 应聚焦跨组件最小公共集 | ✅ 已完成 — `UiThemeTokens` 精简为 4 字段 |

本文档基于对主题系统全部源码的逐行审计，识别出 **7 个设计维度** 的当前状态。

| # | 问题 | 严重度 | 是否已有文档描述 |
|---|------|--------|-----------------|
| 1 | 组件样式不应住在 UiThemeTokens | 高 | THEMING.md §6.1 已描述方向，本文补充代码级实证 |
| 2 | props / spec 双轨冗余导致样式变化无法走 Patch | 高 | **新发现** |
| 3 | UiInputColors 边界模糊 | 中 | THEMING.md §6.1 部分涉及，本文明确降级方案 |
| 4 | Android 桥接存在 4 项遗漏 | 中 | **新发现** |
| 5 | Eager resolution 策略 | 低 | 无需改动（基准验证） |
| 6 | 底层 View 冷门属性无逃生通道 | 高 | **新发现** |
| 7 | 主题 token 应聚焦于跨组件最小公共集 | 中 | **新发现** |

---

## 1. 组件样式不应住在 UiThemeTokens（✅ 已完成）

### 1.1 当前状态

```
UiThemeTokens
 ├─ colors: UiColors                 (8 个基础语义色)
 ├─ typography: UiTypography         (3 个字体配置)
 ├─ input: UiInputColors             (9 个输入域色)
 ├─ shapes: UiShapes                 (2 个圆角)
 ├─ controls: UiControlSizing        (4 组尺寸)
 ├─ components: UiComponentStyles    ← ~80 个预计算颜色值
 └─ interactions: UiInteractionColors (1 个交互色)
```

`components` 内含 9 个组件的全部颜色（`UiButtonStyles` 16 字段、`UiTextFieldStyles` 9 字段、`UiSegmentedControlStyles` 8 字段 ...），这些全部由 `UiComponentStyleDefaults.fromTheme(colors, input)` 从基础色预计算。

### 1.2 代码级证据

**rebaseComponentStyles 的维护成本：**

```
文件：ThemeRebase.kt
函数：rebaseComponentStyles()
行数：270 行
本质：对 ~80 个 Int 字段逐一调用 rebaseValue(current, old, new)
```

每新增一个组件需要：
1. `ComponentStyles.kt` → 新增 `UiXxxStyles` data class
2. `ComponentStyles.kt` → 在 `UiComponentStyles` 新增字段
3. `ThemeDefaults.kt` → 在 `UiComponentStyleDefaults.fromTheme()` 新增计算（~10 行）
4. `ThemeRebase.kt` → 在 `rebaseComponentStyles()` 新增映射（~20 行）

**步骤 4 漏加不会有编译错误，只有运行时主题切换时组件颜色不跟随。** 这正是 REVIEW.md 所说的"静默 bug 孵化器"。

**Material 3 (Compose) 对比：**

```kotlin
// Compose：ColorScheme 只有 ~30 个语义色，没有 button/textField 专属字段
@Stable
class ColorScheme(
    val primary: Color,
    val onPrimary: Color,
    val surface: Color,
    ...
)

// 组件默认值在调用时即时派生
@Composable
fun ButtonDefaults.buttonColors(
    containerColor: Color = MaterialTheme.colorScheme.primary,     // 读基础色
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,     // 不读预存值
    ...
)
```

Compose 根本没有 `colorScheme.buttonPrimaryContainer` 这样的字段。

### 1.3 推荐做法

**删除 `UiThemeTokens.components`**，让 `ButtonDefaults.containerColor()` 等直接从 `Theme.colors` 即时派生。

```kotlin
// 之前（读预存值）
object ButtonDefaults {
    fun containerColor(variant: ButtonVariant, enabled: Boolean): Int =
        when (variant) {
            Primary -> if (enabled) Theme.components.button.primaryContainer
                       else Theme.components.button.primaryDisabledContainer
            ...
        }
}

// 之后（即时派生）
object ButtonDefaults {
    fun containerColor(variant: ButtonVariant, enabled: Boolean): Int =
        when (variant) {
            Primary -> if (enabled) Theme.colors.primary
                       else Theme.colors.divider
            ...
        }
}
```

**逐组件颜色覆盖** 用稀疏 `LocalValue` 承载：

```kotlin
data class ButtonColorOverride(
    val primaryContainer: Int? = null,
    val primaryContent: Int? = null,
    ...
)

private val LocalButtonColors = LocalValue<ButtonColorOverride?> { null }

object ButtonDefaults {
    fun containerColor(variant: ButtonVariant, enabled: Boolean): Int {
        val override = LocalContext.current(LocalButtonColors)
        return override?.primaryContainer ?: Theme.colors.primary
    }
}

// 使用侧
fun UiTreeBuilder.UiButtonColorOverride(
    override: ButtonColorOverride,
    content: UiTreeBuilder.() -> Unit,
) {
    LocalContext.provide(LocalButtonColors, override) { content() }
}
```

### 1.4 收益

- 删除 `rebaseComponentStyles()` 全部 270 行
- 删除 `UiComponentStyles` + 9 个 `UiXxxStyles` data class
- 删除 `UiComponentStyleDefaults.fromTheme()` 全部 80 行
- 新增组件不再需要碰 Theme / Rebase
- `UiThemeOverride` 的 `components` 参数可以删除

---

## 2. props / spec 双轨冗余（新发现）（✅ 已完成）

### 2.1 当前状态

每个 DSL 函数同时向 VNode 写入两份数据：

```
VNode
 ├─ props: Props          (Map<String, Any?>) → 样式属性（背景色、文字色、圆角、ripple ...）
 ├─ spec: NodeSpec?       (data class)        → 语义属性（text, enabled, checked ...）
 └─ modifier: Modifier    (链式修饰)          → 布局属性（padding, margin, size ...）
```

**属性分布现状：**

| 属性类别 | 存储位置 | 能否走 Patch |
|---------|---------|-------------|
| 文字内容、enabled、checked 等 | spec (data class) | 能 |
| 背景色、文字色、圆角、ripple、border | props (Map) | **不能** — 只能走 Rebind |
| padding、margin、size、visibility | modifier | 变化时走 Rebind |

### 2.2 代码级证据

**NodeBindingDiffer.plan() 的判断链：**

```kotlin
// 文件：NodeBindingDiffer.kt
fun plan(previous: VNode, next: VNode): NodeBindingPlan {
    if (previous.type != next.type) return Rebind
    if (previous.modifier != next.modifier) return Rebind
    // ↓ StyleSignature 从 props Map 抽取，任何样式属性变化 → 直接 Rebind
    if (readStyleSignature(previous) != readStyleSignature(next)) return Rebind
    // ↓ 只有 spec 字段差异才能产生 Patch
    if (previous.spec != null && previous.spec == next.spec) return Skip
    ...
}
```

`StyleSignature` 是从 props 中提取的 13 个样式字段组成的 data class：

```kotlin
private data class StyleSignature(
    val textColor: Int?, val textSizeSp: Int?, val alpha: Float?,
    val backgroundColor: Int?, val borderWidth: Int?, val borderColor: Int?,
    val cornerRadius: Int?, val rippleColor: Int?, val minHeight: Int?,
    val paddingLeft: Int?, val paddingTop: Int?, val paddingRight: Int?,
    val paddingBottom: Int?,
)
```

**后果：** 任何颜色变化（包括主题切换引发的背景色、文字色变化）都只能走 Rebind，永远无法走字段级 Patch。当前 13 个节点的 field-level patch 能力 **仅对非样式字段有效**。

### 2.3 根因

`props` 是历史路径，`spec` 是后来引入的。当前处于过渡态——新的 typed spec 覆盖了语义字段，但样式字段仍然只存在于 untyped props Map 中。

### 2.4 推荐做法

**将样式属性纳入 typed spec，最终删除 Props。**

```kotlin
// 之前
data class ButtonNodeProps(
    val text: CharSequence?,
    val enabled: Boolean,
    val iconTint: Int,
    ...
) : NodeSpec

// 之后 — 统一全部属性
data class ButtonNodeProps(
    // 语义
    val text: CharSequence?,
    val enabled: Boolean,
    val onClick: (() -> Unit)?,
    // 样式（以前只在 props 里）
    val textColor: Int,
    val backgroundColor: Int,
    val borderColor: Int,
    val cornerRadius: Int,
    val rippleColor: Int,
    val textSizeSp: Int,
    val minHeight: Int,
    // 图标
    val leadingIcon: ImageSource.Resource?,
    val trailingIcon: ImageSource.Resource?,
    val iconTint: Int,
    val iconSize: Int,
    val iconSpacing: Int,
) : NodeSpec
```

**Differ 简化为：**

```kotlin
fun plan(previous: VNode, next: VNode): NodeBindingPlan {
    if (previous.type != next.type) return Rebind
    if (previous.modifier != next.modifier) return Rebind
    if (previous.spec == next.spec) return Skip
    // 所有属性（包括颜色）都能走字段级 Patch
    return tryPatch(previous.spec, next.spec) ?: Rebind
}
```

**Patch applier 覆盖样式字段：**

```kotlin
fun applyButtonPatch(view: Button, patch: ButtonNodePatch) {
    if (patch.previous.text != patch.next.text) view.text = patch.next.text
    if (patch.previous.backgroundColor != patch.next.backgroundColor) {
        updateBackgroundColor(view, patch.next.backgroundColor)  // 只更新背景
    }
    if (patch.previous.textColor != patch.next.textColor) {
        view.setTextColor(patch.next.textColor)  // 只更新文字色
    }
    ...
}
```

### 2.5 迁移路径

1. **Phase 1**：新组件只用 spec，不写 props
2. **Phase 2**：逐组件将样式字段从 props 迁入 spec
3. **Phase 3**：删除 `Props`、`TypedPropKeys`、`StyleSignature`，`ViewModifierApplier` 从 spec 读样式

### 2.6 收益

- 样式变化（包括主题切换）能走字段级 Patch，减少 Rebind
- VNode 体积减小（不再重复存储）
- 删除 `Props`、`TypedPropKeys`、`StyleSignature` 相关代码
- Binder 只有一条代码路径（从 spec 读），不再有 spec/props 双路径分支

---

## 3. UiInputColors 边界模糊（✅ 已降级）

### 3.1 当前状态

`UiInputColors` 的全部 9 个字段都由 `UiInputDefaults.fromColors(colors)` 从 `UiColors` 派生，但它被提升为 `UiThemeTokens` 的一级字段。

用户在做 `UiThemeOverride` 时面对 7 个维度，其中 `input` 和 `components` 实际上是 `colors` 的衍生品。

### 3.2 推荐做法

如果问题 1 落地（组件样式从 theme 移出），`UiInputColors` 也应降级——变成 `InputControlDefaults` 内部的即时派生，不再是 Theme 一级字段。

```kotlin
// 终态（✅ 已实现，interactions 也已降级）
data class UiThemeTokens(
    val colors: UiColors,
    val typography: UiTypography,
    val shapes: UiShapes,
    val controls: UiControlSizing,
)
```

Theme 从 7 个维度精简到 4 个，每个维度职责正交：

| 维度 | 职责 |
|------|------|
| colors | 全局语义色 |
| typography | 字体配置 |
| shapes | 圆角 |
| controls | 组件尺寸 |

### 3.3 收益

- `UiThemeOverride` 参数更少，心智负担更低
- 删除 `rebaseInputColors()` 和 `rebaseInteractionColors()`
- `UiThemeTokens.override()` 函数从 60 行简化到 ~15 行

---

## 4. Android 桥接遗漏（新发现）（✅ Typography + uiMode 已完成）

### 4.1 当前状态

`AndroidThemeBridge.fromContext()` 通过 `Context.obtainStyledAttributes()` 读取 Material/AppCompat 主题属性，映射到 `UiThemeTokens`。

```
已桥接：
 ✓ UiColors（8 个语义色，逐一映射到 android.R.attr / material R.attr）
 ✓ UiInputColors（9 个输入域色，映射到 Material 3 属性）
 ✓ UiInteractionColors（从 textPrimary 派生）

未桥接：
 ✗ Typography — 直接用 UiThemeDefaults.light().typography（硬编码 24/16/14sp）
 ✗ Shapes — 直接用 UiShapeDefaults.default()（硬编码 20/14dp）
 ✗ 夜间模式感知 — 未读取 Configuration.uiMode
 ✗ Dynamic Color (Material You) — Android 12+ wallpaper-based palette 未桥接
```

### 4.2 具体代码位置

```kotlin
// 文件：AndroidThemeBridge.kt → ThemeTokenMapper.fromThemeColors()
return UiThemeTokens(
    colors = UiColors(
        background = readColor(android.R.attr.colorBackground) ?: fallback.background,
        ...
    ),
    typography = UiThemeDefaults.light().typography,  // ← 硬编码，未桥接
)
```

### 4.3 推荐做法

| 优先级 | 项目 | 说明 |
|--------|------|------|
| P1 | Typography 桥接 | 读取 `textAppearanceBody1/2`、`textAppearanceHeadline` 的 `textSize` 属性 |
| P1 | uiMode 自动 light/dark | 读取 `Configuration.uiMode and UI_MODE_NIGHT_MASK`，自动选择 light/dark 预设 |
| P2 | Dynamic Color | 使用 `DynamicColors.wrapContextThemeOverlay()` 或 `dynamicLightColorScheme()` |
| P3 | Shape 桥接 | 读取 `shapeAppearanceCornerSmall/Medium/Large` 的 `cornerSize` 属性 |

### 4.4 Typography 桥接示例

```kotlin
// 建议新增
private fun Context.resolveTextSizeSp(attr: Int): Int? {
    val typedArray = obtainStyledAttributes(intArrayOf(attr))
    return typedArray.use {
        if (!it.hasValue(0)) null
        else {
            val px = it.getDimensionPixelSize(0, 0)
            (px / resources.displayMetrics.scaledDensity).toInt()
        }
    }
}

// 在 ThemeTokenMapper 中
typography = UiTypography(
    title = UiTextStyle(
        fontSizeSp = readTextSize(android.R.attr.textAppearanceHeadline6) ?: 24.sp,
    ),
    body = UiTextStyle(
        fontSizeSp = readTextSize(com.google.android.material.R.attr.textAppearanceBodyLarge) ?: 16.sp,
    ),
    label = UiTextStyle(
        fontSizeSp = readTextSize(com.google.android.material.R.attr.textAppearanceLabelMedium) ?: 14.sp,
    ),
)
```

---

## 5. Eager Resolution 策略（无需改动）

### 5.1 现状

所有颜色在 tree-build 时解析为 `Int`，VNode 不保留语义信息（如"这是 primary 色"）。

### 5.2 基准验证

| 场景 | P50 | 帧预算 |
|------|-----|--------|
| themeSwitch | 2.3ms | 16.6ms |
| chapterSwitch | 2.2ms | 16.6ms |

均远低于帧预算。

### 5.3 结论

引入 `ColorRef`（语义间接层）会增加每个节点的内存和 resolve 开销，ROI 不合理。**保持 eager resolution，不做改动。**

但如果问题 2（props→spec 统一）落地，样式变化也能走 Patch 后，theme switch 的代价会进一步降低——因为只需 Patch 颜色字段，不必 Rebind 整个节点。

---

## 6. 底层 View 冷门属性无逃生通道（新发现）（✅ `Modifier.nativeView` 已实现）

### 6.1 问题

框架基于 Android View 渲染，但内建组件（Text, Button, Row, Column, Box, Image, TextField, Slider ...）**完全封住了底层 View**。使用者无法设置任何未被框架映射的 View 属性。

**当前被封住的常见属性示例：**

| 属性 | Android API | 框架是否映射 |
|------|-------------|-------------|
| contentDescription | `view.contentDescription` | 仅 Image/IconButton 有，其它组件无 |
| importantForAccessibility | `view.importantForAccessibility` | 未映射 |
| clipChildren | `viewGroup.clipChildren` | 未映射 |
| clipToPadding | `viewGroup.clipToPadding` | 未映射 |
| elevation | `view.elevation` | 未映射（zIndex 只映射 `translationZ`） |
| transitionName | `view.transitionName` | 未映射 |
| focusable | `view.isFocusable` | 未映射 |
| stateListAnimator | `view.stateListAnimator` | 未映射 |
| layoutDirection | `view.layoutDirection` | 未映射 |
| tooltipText | `view.tooltipText` | 未映射 |

### 6.2 当前仅有的出路

唯一可用的是 `AndroidView(factory, update)` —— 但它要求使用者 **从零创建并完全管理 View**，放弃框架的主题默认值、binding、reconciliation 和 patch 优化。

```kotlin
// 当前：想给 Button 加 contentDescription，只能放弃 Button DSL
AndroidView(
    factory = { context -> android.widget.Button(context) },
    update = { view ->
        (view as android.widget.Button).text = "Submit"
        view.contentDescription = "Submit the form"
        // 手动设置所有样式 ... 完全脱离框架主题体系
    },
)
```

这不是"逃生通道"，而是"逃离框架"。

### 6.3 推荐做法：`Modifier.nativeView(key, configure)`

新增一个 `ModifierElement`，在正常 binding 完成后对底层 View 执行用户回调。

**API 设计：**

```kotlin
// Modifier 扩展
fun Modifier.nativeView(
    key: Any = Unit,
    configure: (View) -> Unit,
): Modifier

// 使用示例
Button(
    text = "Submit",
    modifier = Modifier.nativeView(key = "a11y") { view ->
        view.contentDescription = "Submit the form"
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    },
)

Row(
    modifier = Modifier.nativeView(key = "clip") { view ->
        (view as? ViewGroup)?.clipChildren = false
    },
) { ... }
```

**核心设计决策：**

**1) 为什么用 Modifier 而不是 DSL 参数？**

- 不需要改任何现有 DSL 函数签名
- 与其它 Modifier 自然组合
- 符合"通用视觉属性走 Modifier"的既有原则（THEMING.md §9.1）
- 任何节点类型都能用，无需逐组件适配

**2) 为什么需要 `key` 参数？**

`Modifier` 相等性直接影响 Differ 的 Patch/Skip/Rebind 判断（`NodeBindingDiffer.plan()` 第 29 行：`if (previous.modifier != next.modifier) return Rebind`）。

如果 `NativeViewElement` 用 lambda 做 equals，每次 tree-build 都会创建新 lambda → Modifier 永远不等 → 永远 Rebind → 完全丧失 Patch 能力。

解决方式：**只用 `key` 比较相等性，忽略 lambda**。

```kotlin
class NativeViewElement(
    val stableKey: Any,
    val configure: (View) -> Unit,
) : ModifierElement {
    override fun equals(other: Any?): Boolean =
        other is NativeViewElement && stableKey == other.stableKey
    override fun hashCode(): Int = stableKey.hashCode()
}
```

- key 不变 → Modifier 相等性不受影响 → 不阻止 Patch/Skip
- key 变化 → Modifier 不等 → Rebind → configure 重新执行
- 对于真正的冷门属性（accessibility、clip、transition），key 几乎不会变

**3) configure 何时被调用？**

```
ViewModifierApplier.bindView()
  → applyModifier()              // 正常 modifier 流程
  → NodeViewBinderRegistry.bind() // 正常 binder 流程
  → applyNativeViewConfig()       // ← 新增：最后执行 configure lambda
```

在所有框架 binding 完成 **之后** 执行，所以用户可以覆盖框架设置的任何属性。

**4) Patch 路径下 configure 是否执行？**

当 Differ 决定走 Patch（spec 字段变化但 modifier 没变）时，只调用 `applyPatch()`，不调用 `bindView()`。**所以 configure 不会在 Patch 路径执行。** 这对冷门属性完全正确——它们不随状态变化，只需在 Rebind 时设置一次。

**5) 与 AndroidView 的分工**

| 场景 | 推荐方案 |
|------|---------|
| 给框架组件加 1-2 个冷门 View 属性 | `Modifier.nativeView` |
| 使用框架未包装的第三方 View（如 MapView, WebView） | `AndroidView(factory, update)` |
| 完全自定义渲染逻辑 | `AndroidView(factory, update)` |

### 6.4 不应滥用 nativeView 的属性

如果某个属性频繁变化、或多数组件都需要，它应该被提升为正式的 Modifier 或 spec 字段，而不是走 nativeView。判断标准：

| 条件 | 正确做法 |
|------|---------|
| 多数组件都需要 | 提升为 Modifier（如 padding、alpha 已有） |
| 随状态高频变化 | 提升为 spec 字段（参与 Patch） |
| 极少组件、极少场景、不随状态变化 | `Modifier.nativeView` |
| 非框架包装的 View | `AndroidView` |

---

## 7. 主题 token 应聚焦于跨组件最小公共集（新发现）

### 7.1 当前 token 面积审计

| 分类 | 数据结构 | 叶子字段数 |
|------|---------|-----------|
| 基础色 | `UiColors` | 8 |
| 输入域色 | `UiInputColors` | 9 |
| 圆角 | `UiShapes` | 2 |
| 字体 | `UiTypography` + `UiTextStyle` | 3 |
| 交互反馈 | `UiInteractionColors` | 1 |
| 组件尺寸 | `UiControlSizing` + 4 个子结构 | 30 |
| 组件颜色 | `UiComponentStyles` + 9 个子结构 | **55** |
| **合计** | | **108** |

对比 Compose Material 3 的 `ColorScheme`：49 个色彩字段。但 Compose 不在 `ColorScheme` 中存储组件级颜色——组件颜色由 `ButtonDefaults.buttonColors()` 等即时派生。

### 7.2 核心原则

**主题只存放"跨组件的最小公共集"——即改变一个 token 会影响多个组件的那些属性。组件专属属性不应进入主题。**

三层模型：

```
┌─────────────────────────────────────────────────────────┐
│  Tier 1: Theme（跨组件语义 token）                        │
│  改 primary → 所有 Primary 按钮、SegmentedControl 指示器、│
│              ProgressIndicator、TabPager 指示器都跟随      │
│  → UiColors, UiTypography, UiShapes, UiControlSizing,    │
│    UiInteractionColors                                    │
├─────────────────────────────────────────────────────────┤
│  Tier 2: Component Defaults + Overrides（组件级派生）      │
│  ButtonDefaults.containerColor() 从 Theme.colors 即时计算  │
│  需要局部覆盖时用 LocalValue<ButtonColorOverride?>         │
│  → 组件专属颜色、变体矩阵、disabled 态                     │
├─────────────────────────────────────────────────────────┤
│  Tier 3: View Escape Hatch（冷门 View 属性）              │
│  Modifier.nativeView { } 或 AndroidView { }              │
│  → accessibility、clipChildren、elevation、transitionName │
└─────────────────────────────────────────────────────────┘
```

### 7.3 当前问题：Tier 2 的 55 个 token 被错放到了 Tier 1

`UiComponentStyles`（55 个字段）全部是从 `UiColors` 和 `UiInputColors` 派生的组件级颜色。它们属于 Tier 2，但当前住在 Tier 1 的 `UiThemeTokens` 里。

这导致了：
- `rebaseComponentStyles()` 270 行（因为 Tier 1 变了要手动级联到 55 个 Tier 2 字段）
- 新增组件必须碰 Theme（本不该碰）
- `UiThemeOverride` 暴露了 `components` 参数（用户几乎不需要在 theme 层覆盖具体组件色）

### 7.4 清理后的终态（✅ 已实现）

应用本文 §1（组件样式移出）+ §3（UiInputColors 降级）后：

```kotlin
data class UiThemeTokens(
    val colors: UiColors,              // 8 个跨组件基础色
    val typography: UiTypography,      // 3 个字体配置
    val shapes: UiShapes,              // 2 个圆角
    val controls: UiControlSizing,     // 30 个尺寸（compact/medium/large × 4 组件）
)
// 总计：43 个 token（interactions 也已降级）
```

**43 个 token，每个都是跨组件的**——改变任一个都会影响多个组件。没有组件专属字段混入。

### 7.5 UiControlSizing 的合理性

30 个尺寸 token 看起来多，但 compact/medium/large 三级尺寸是跨组件的全局设计语言（改变 `controls.button.mediumHeight` 影响所有 Medium 按钮），属于 Tier 1。

**但需注意**：`UiSegmentedControlSizing` 的 compact/large 两级目前在产品代码中 **从未使用**（仅使用 medium）。如果确认不需要，可以精简掉 6 个 token（44→38）。

### 7.6 什么不应进入 Theme

| 不应进入 Theme 的属性 | 正确归属 |
|---------------------|---------|
| 按钮各变体颜色（primary/secondary/tonal/outlined × enabled/disabled） | Tier 2：`ButtonDefaults` 即时派生 |
| TextField 各变体容器色 | Tier 2：`TextFieldDefaults` 即时派生 |
| Checkbox / Switch / Radio 控件色 | Tier 2：`InputControlDefaults` 即时派生 |
| 单个组件的 contentDescription | Tier 3：spec 字段或 `Modifier.nativeView` |
| clipChildren、elevation、transitionName | Tier 3：`Modifier.nativeView` |
| 第三方 View 特有属性 | Tier 3：`AndroidView` |

---

## 8. 综合演进路线

按 ROI 排序，与 THEMING.md §11 的 Phase 对齐：

| 序号 | 改动 | 对应 THEMING.md Phase | 规模 | 核心收益 | 状态 |
|------|------|----------------------|------|---------|------|
| **1** | 组件样式从 Theme 移出 → Defaults 即时派生 + 稀疏 LocalValue 覆盖 | Phase B | 中 | 删 270 行 rebase，消除漏字段风险，新组件零 theme 改动 | ✅ 已完成 |
| **2** | props → spec 统一 → 样式属性纳入 typed spec | 无对应（新发现） | 大 | 样式变化走 Patch，删 Props/TypedPropKeys/StyleSignature | ✅ 已完成 |
| **3** | UiInputColors 降级 → Defaults 内部即时派生 | Phase B 延伸 | 小 | Theme 7→4 维度，删 rebaseInputColors/rebaseInteractionColors | ✅ 已完成 |
| **4** | 新增 `Modifier.nativeView(key, configure)` | 无对应（新发现） | 小 | 冷门 View 属性有逃生通道，不再被迫用 AndroidView 重写整个组件 | ✅ 已完成 |
| **5** | Android 桥接补全 → typography + uiMode | 无对应（新发现） | 小 | 宿主集成开箱即用 | ✅ 已完成 |
| **6** | 引入更细 local（LocalTextStyle, ProvideContentColor） | Phase C | 小 | 减少 UiThemeOverride 职责膨胀 | ✅ 已完成 |
| **7** | Eager resolution 不动 | — | 0 | 基准已验证 2.3ms，无需优化 | ✅ 无需改动 |

### 8.1 建议跳过的中间态

REVIEW.md Sprint 3 的 #10（设计 UiComponentOverrides sparse model）→ #11（迁移 ButtonDefaults）→ #12（扩展到 TextFieldDefaults 等）→ #13（删 rebaseComponentStyles）是一个 4 步渐进迁移。

**建议跳过中间态，直接执行：**

1. 让 `ButtonDefaults` 等全部改为从 `Theme.colors` 即时派生
2. 同时引入稀疏 `LocalValue` 覆盖
3. 一步删除 `components` 字段和全部 rebase 代码

理由：中间态（先迁移到 sparse override 模型，再切换 Defaults）会引入两轮 API 变化，增加迁移成本。直接到位更简洁。

### 8.2 步骤依赖关系

步骤 1（组件样式移出）和步骤 2（props→spec 统一）**相互独立**，可以并行推进。但如果想最大化 Patch 收益，应先做 2 再做 1——因为做完 2 之后，即使不做 1，样式颜色变化已经能走 Patch。

推荐顺序：**2 → 1 → 3 → 4 → 5 → 6**

---

## 9. 与现有文档的关系

| 文档 | 本文档的关系 |
|------|-------------|
| [THEMING.md](../guides/theming.md) | THEMING.md 定义方向，本文补充代码级实证和新发现 |
| [THEME_OVERRIDES.md](THEME_OVERRIDES.md) | THEME_OVERRIDES.md 定义 override API 方向，本文的步骤 1 是其落地路径 |
| [REVIEW.md](REVIEW.md) Sprint 3 | 本文建议跳过 Sprint 3 的 #10-#12 中间态，直接到位 |
| [PERFORMANCE.md](../tooling/performance.md) Phase 2 | 本文步骤 2（props→spec 统一）直接扩大 Phase 2 patch 覆盖范围 |
| [NODE_PROPS.md](../architecture/node-spec.md) | 本文步骤 2 会改变 NodeSpec 的字段范围，需同步更新 |
| [MODIFIER.md](../architecture/modifier.md) | 本文步骤 4（Modifier.nativeView）需同步更新 Modifier 文档 |

---

## 10. 验证标准

每个步骤落地后的验证方式：

| 步骤 | 验证标准 | 验证结果 |
|------|---------|---------|
| 1 | `rebaseComponentStyles` 被删除且不存在；`./gradlew :viewcompose-widget-core:compileDebugKotlin` 通过；`UiThemeOverride(colors = ...)` 后 Button 颜色自动跟随 | ✅ 通过 |
| 2 | `Props` class 不再被引用；`./gradlew :viewcompose-renderer:test` 全通过；Diagnostics Renderer 页面在 Patch Stress 后 patched 计数覆盖颜色字段变化 | ✅ 通过 |
| 3 | `UiThemeTokens` 只有 4 个字段；`rebaseInputColors` 被删除 | ✅ 通过 |
| 4 | `Button(modifier = Modifier.nativeView("test") { it.contentDescription = "x" })` 能编译且运行时 View 上 contentDescription 生效；不影响 Patch 性能 | ✅ 通过 |
| 5 | 在使用 Material 3 主题的宿主 app 中，`UiTheme(androidContext = this)` 自动获取正确的字体大小和 light/dark 模式 | ✅ 通过 |
| 6 | 在不使用 `UiThemeOverride` 的情况下，可以通过 `ProvideContentColor` 改变子树内所有 Text 的默认颜色 | ✅ 通过 |
