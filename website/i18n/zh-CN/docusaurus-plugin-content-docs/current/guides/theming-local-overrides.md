---
translation_source: guides/theming-local-overrides.md
translation_source_hash: 3bc4d283876c03f470e052da320c50ef59c28264412be114487de887589ca78a
translation_status: current
---

# 为一个子树覆盖主题 token

当一个语义区域需要不同的颜色、Typography、Shape、Control Sizing、Interaction 或 Overlay 家族
时，使用 `UiThemeOverride`。它派生一个嵌套不可变主题快照，并在子树结束后恢复父级。完整
优先级与 Renderer 边界见[主题架构](../architecture/theming.md)。

## 只变换需要的家族

变换重载从当前家族开始，未指定的家族保留父级值：

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/ThemingGuideSamples.kt" region="theme-local-override" sample_id="guide.theming-local-override" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
fun UiTreeBuilder.AccentPanel() {
    UiThemeOverride(
        colors = { copy(primary = 0xFF6750A4.toInt()) },
        shapes = { copy(medium = large) },
    ) {
        Column {
            Text("Only this subtree uses the accent theme")
            Button("Continue", onClick = {})
        }
    }
}
```

应用已经持有完整替换家族时使用值重载。替换 `colors` 而不显式替换 `stateColors` 会重新派生
状态颜色，从而避免旧的 Pressed、Selected 或 Disabled 调色板泄漏到新方案。

## 选择正确的 Override 层级

`UiThemeOverride` 会改变子树内所有参与组件的语义默认值。组件自有 `XxxOverrides` Provider 只
改变一个组件家族的稀疏外观槽位。实例外观仍是最具体的值。

一个 Button Border、TextField Decoration 或 Control Interaction Layer 应使用组件 Override。
不属于框架主题的应用语义概念应使用应用自有 `uiLocalOf`。不要把受控状态、回调、键盘策略、
导航、生命周期、资源句柄或 Renderer 平台类型放进任一种外观模型。

Basic Primitive 接收完整的已解析 Style，不消费稀疏 Override。Renderer 接收已解析 NodeSpec 值，
不会直接读取 `UiThemeOverride`。

## 验证任务

通过 `./gradlew :samples:tutorials:compileDebugKotlin` 编译，然后验证一页中依次存在父主题、嵌套
Panel 和 Panel 后的 Sibling：

1. 修改父主题，确认 Panel 内继承的字段更新。
2. 确认 Panel 的 Primary Color 和 Medium Shape 保持覆盖。
3. 对 Panel 控件执行 Press、Focus、Select 和 Disable；状态颜色必须匹配覆盖后的语义调色板。
4. 确认 Panel 后的 Sibling 使用未变化的父快照。
5. 在 Panel 内增加组件作用域和实例 Override，确认优先级仍为实例、组件作用域、主题作用域。

Panel 主题泄漏到 Sibling、保留父级状态颜色，或通过外观对象改变行为，都会使任务失败。
