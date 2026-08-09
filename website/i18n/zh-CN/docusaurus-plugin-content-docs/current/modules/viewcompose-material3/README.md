---
translation_source: modules/viewcompose-material3/README.md
translation_source_hash: b7fa72ffc865cab559a2b0d6e9bb620006c695c2c07bba37ef6d5aad6e33d5aa
translation_status: current
---

# Material 3 主题适配模块

`viewcompose-material3` 是 Android 上 Google Material 3 的设计系统层。它把 Material 主题颜色、
排版和形状读取为平台无关的 `UiThemeTokens`，解析动态色 Context，并在配置变化或主动主题变化后
刷新 token。

它还拥有一个有界 Material 压力切片，覆盖 Surface/Card、Button、Switch、TextField 与
NavigationBar。这些 API 会把具名 Recipe 解析成共享 Basic 基础组件、原生行为内核或中立
Renderer 节点；本模块不参与 View 协调，也不会把通用节点映射为 Material Components 控件。
因此 Android Engine 可以完全脱离 Material Components；只有本模块与明确基于 Material 的
集成模块持有该依赖。

## 构件与稳定性

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-material3:0.1.0-alpha01")
}
```

- 稳定性：**Alpha**。
- 平台：Android library，`minSdk 24`、`compileSdk 36`，Java 11 字节码。
- API 依赖：`viewcompose-ui-foundation`。
- 实现依赖：Material Components、AppCompat 与 AndroidX Core。
- 基线：Material Components `1.13.0` 中的标准、非 Expressive Material 3。

## 主题解析

`Material3ThemeBridge.resolveContext` 创建根 View 与 Overlay 必须共享的稳定主题 Context。
`Material3Theme` 提供映射后的 token，并在挂载期间观察 Android 配置变化。调用
`Context.setTheme` 等不会触发配置变化的主动资源修改后，应在主线程调用
`Material3ThemeRefreshController.refresh()`。

```kotlin
val resolved = Material3ThemeBridge.resolveContext(
    context = activity,
    dynamicColorPolicy = Material3DynamicColorPolicy.UseIfAvailable,
)

Material3Theme(resolvedTheme = resolved) {
    Content()
}
```

Material 应用通过具名的 `viewcompose-material3-android` 集成自动获得这套生命周期。底层集成仍可
自行解析 Context 并显式安装 `Material3Theme`。

`Material3Theme(tokens = ...)` 可以从静态 Token 提供同一套 Recipe 与诊断作用域，而不读取
Android 资源。两个重载都会通过 `DesignSystemDiagnostics` 导出
`Material3Reference.recipeSet` 与相同的五家族 Backend/Conformance 归因。

## 公开组件压力切片

| 入口 | Recipe/Backend 边界 | 当前一致性 |
| --- | --- | --- |
| `Material3Surface`、`Material3Card` | Material Recipe 解析到共享 `BasicSurface` | Exact |
| `Material3Button` | Material Variant Recipe 解析到共享 `BasicButton` | Exact |
| `Material3Switch` | Material 颜色/排版覆盖原生 Android Switch 行为内核 | Equivalent |
| `Material3TextField` | 原生 Android 编辑内核外包裹 Material 装饰 | Equivalent |
| `Material3NavigationBar` | Material 选择 Recipe 覆盖中立 Navigation Renderer | Equivalent |

Material 与 One UI 的公开词汇有意保持差异；两者只共享中立执行与诊断契约，不引入 Union 组件
API 或全局 Recipe Bundle。

## Token 基线与回退

当没有 Android 主题 Context，或 Android 主题缺少单个属性时，
`Material3ThemeDefaults.light()` 与 `Material3ThemeDefaults.dark()` 会提供确定性的 Material 3
快照。每份快照都包含：

- 适配器所需的完整 Material 配色，包括表面容器、反色、轮廓以及容器内容角色；
- Display、Headline、Title、Body 和 Label 共 15 个标准排版角色；
- Extra Small、Small、Medium、Large、Extra Large 与 Full 六级形状角色；
- Button、TextField、SegmentedControl、ProgressIndicator、FAB、Search 与 Badge 采用的标准尺寸
  配置，以及原生紧凑输入控件的有效目标尺寸；
- 标准交互透明度：按下 `0.10`、聚焦 `0.10`、悬停 `0.08`。

标准 Button 尺寸配置中，Compact 与 Medium 使用 48dp 有效触控目标和居中的 40dp 可见容器，
Large 使用 56dp 目标和 48dp 可见容器。这是由 UI Foundation 设计系统无关尺寸契约消费的
Token 选择；Material 适配器不参与 Android 命中测试或 View 绘制。

Button 与 IconButton Defaults 会把这些交互透明度与各 Variant 的启用态内容角色组合后再发出
NodeSpec。例如 Primary Button 使用 `onPrimary`，Tonal Button 使用
`onSecondaryContainer`。适配器不生成选择器，Android Renderer 也不知道 Material 角色名。

Checkbox、RadioButton、Switch 与 Slider 使用 48dp 最小有效高度。它们的原生指示器、Thumb、
Track 与 Label 几何仍由平台渲染并保持居中；应用显式指定的精确高度或更严格的父容器约束仍会
生效。这项策略通过 UI Foundation 的中性控件尺寸 Token 表达，而不是在 Android Renderer 中
添加 Material 分支。

这些控件的启用态选中颜色由 UI Foundation 解析为 Material `primary` 角色，不使用 AppCompat
`colorControlActivated` 桥接值；Slider 的非激活轨道使用 `secondaryContainer`。Bridge 仍会
暴露旧状态色供应用显式使用，但这些值不会替换组件语义角色。

Android Bridge 会用当前主题中存在的值替换快照内容。它读取全部 15 个 Material Text
Appearance 和五个绝对 `shapeAppearanceCorner*` 角色；旧 Android Large/Medium/Small Text
Appearance 继续作为 Title/Body/Label 家族回退。缺失的 Display 和 Headline 会保留完整 Material
静态快照，不会折叠到旧字号，也不会退回 UI Foundation 的中性默认值。

`UiThemeMetadata.provenance` 会把基础生产者记录为
`viewcompose-material3/android-xml`、`viewcompose-material3/android-dynamic` 或
`viewcompose-material3/static`。压力切片消费的每个颜色、状态色、排版、形状、控件、交互和浮层
路径都能解析有效来源：存在的 Android 属性标记为 Android Theme 或 Dynamic；缺失值继续标记为
具名静态 Material 回退，其来源为 `FrameworkDefault`；`UiThemeOverride` 只标记应用实际替换的
Token 家族。完整静态快照同样报告 `FrameworkDefault`，不会再把第一方默认值误标成应用自定义值。

`UiDesignSystemAttribution.integrations` 记录 Overlay Transport 与逐类型 Presenter。Material
Dialog/Popup 内容使用捕获的 Material Local，Snackbar 与 Modal Bottom Sheet 报告 Material
Components Adapter，Android Toast 是显式 Degraded 平台 Fallback。

本适配器不会把 Material 策略放进 Android Renderer。组件默认值在 NodeSpec 进入 Renderer
之前，已在 UI Foundation 中解析为语义角色。Button 的可见/有效高度分离会由尺寸 Token 与
NodeSpec 明确表达；原生紧凑输入控件的目标策略同样由 UI Foundation 消费。组合式 Chip 的
目标/Surface 分离、TextField 浮动 Label/Focus 结构，以及 Switch/Slider 精确可见几何不由
Token Bridge 自动提供。当前具名 TextField 与 Switch 有意保留原生行为内核并报告 Equivalent；
进一步视觉替换必须通过 Phase 12 的行为与无障碍门禁。

## 相关文档

- [主题指南](../../guides/theming.md)
- [UI Foundation](../viewcompose-ui-foundation/README.md)
- [Material 3 Android 集成](../viewcompose-material3-android/README.md)
- [中立 Android 聚合模块](../viewcompose-android/README.md)
- [五层架构](../../architecture/decisions/0002-five-layer-runtime-module-architecture.md)

完整生成参考位于
[`viewcompose-material3` API 树](https://docs.viewcompose.com/api/viewcompose-material3/current/)。

## 兼容性说明

本模块从 `0.1.0-alpha01` 开始。原先位于 UI Foundation 的 Android Theme Bridge 类型已重命名为
`Material3*` API 家族并迁移到这里，不提供兼容别名。
当前 Alpha 版本线还增加了完整形状和排版角色以及公开静态 Material 3 回退；穷举构造或解构
相关 UI Foundation Data Class 的使用方，需要随对应 Alpha 版本同步更新。

Android 主题没有暴露一套完整的逐状态透明度族，因此标准交互透明度配置会在 Android 主题映射
期间保留。应用可以替换通用 `UiInteractionTokens` 或组件已解析的 `stateLayerColors`，无需依赖
Material API。

静态 `Material3Theme` 重载与六个具名压力切片入口属于增量 Q3 API；相应 Enum 与
`Material3Reference` 是 Q2 身份/值契约。它们不暴露 Material 控件类型，也不改变通用 UI
Foundation 组件签名。
