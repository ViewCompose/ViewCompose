---
translation_source: modules/viewcompose-material3/README.md
translation_source_hash: 421ac965fc7c0664d0611f23823d84ff54fbf6f7f27eb53f3af50ba5a3f68c11
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
