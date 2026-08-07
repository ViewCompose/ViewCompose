---
translation_source: modules/viewcompose-material3/README.md
translation_source_hash: 9045607e7177e31a6dfc3bc8e6a56896aca652ecf33c77e1413c273790b23373
translation_status: current
---

# Material 3 主题适配模块

`viewcompose-material3` 是 Android 上 Google Material 3 的设计系统层。它把 Material 主题颜色、
排版和形状读取为平台无关的 `UiThemeTokens`，解析动态色 Context，并在配置变化或主动主题变化后
刷新 token。

它不渲染核心控件，也不参与 View 协调。因此 Android Engine 可以完全脱离 Material Components；
只有本适配模块与明确基于 Material 的集成模块持有该依赖。

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

标准应用通过 `viewcompose-android` 自动获得这套生命周期。

## Token 基线与回退

当没有 Android 主题 Context，或 Android 主题缺少单个属性时，
`Material3ThemeDefaults.light()` 与 `Material3ThemeDefaults.dark()` 会提供确定性的 Material 3
快照。每份快照都包含：

- 适配器所需的完整 Material 配色，包括表面容器、反色、轮廓以及容器内容角色；
- Display、Headline、Title、Body 和 Label 共 15 个标准排版角色；
- Extra Small、Small、Medium、Large、Extra Large 与 Full 六级形状角色；
- Button、TextField、SegmentedControl、ProgressIndicator、FAB、Search 与 Badge 采用的标准尺寸
  配置。

Android Bridge 会用当前主题中存在的值替换快照内容。它读取全部 15 个 Material Text
Appearance 和五个绝对 `shapeAppearanceCorner*` 角色；旧 Android Large/Medium/Small Text
Appearance 继续作为 Title/Body/Label 家族回退。缺失的 Display 和 Headline 会保留完整 Material
静态快照，不会折叠到旧字号，也不会退回 UI Foundation 的中性默认值。

本适配器不会把 Material 策略放进 Android Renderer。组件默认值在 NodeSpec 进入 Renderer
之前，已在 UI Foundation 中解析为语义角色。触控区域扩展、TextField 浮动 Label/Focus 结构，
以及 Switch/Slider 精确几何不由 Token Bridge 自动提供，必须作为独立组件工作进行测试。

## 相关文档

- [主题指南](../../guides/theming.md)
- [UI Foundation](../viewcompose-ui-foundation/README.md)
- [Android 聚合模块](../viewcompose-android/README.md)
- [五层架构](../../architecture/decisions/0002-five-layer-runtime-module-architecture.md)

完整生成参考位于
[`viewcompose-material3` API 树](https://docs.viewcompose.com/api/viewcompose-material3/current/)。

## 兼容性说明

本模块从 `0.1.0-alpha01` 开始。原先位于 UI Foundation 的 Android Theme Bridge 类型已重命名为
`Material3*` API 家族并迁移到这里，不提供兼容别名。
当前 Alpha 版本线还增加了完整形状和排版角色以及公开静态 Material 3 回退；穷举构造或解构
相关 UI Foundation Data Class 的使用方，需要随对应 Alpha 版本同步更新。
