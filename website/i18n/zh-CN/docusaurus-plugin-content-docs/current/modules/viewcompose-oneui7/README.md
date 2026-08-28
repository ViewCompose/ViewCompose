---
translation_source: modules/viewcompose-oneui7/README.md
translation_source_hash: 2e17da4b9d5bbdb1b5d543b5aa183519dbc4aa84c96caa6728425e62109449ff
translation_status: current
---

# One UI 7 五组件 Alpha

`viewcompose-oneui7` 是 ViewCompose 第一个公开的非 Material Design System 产物。它提供静态
Light/Dark Token 快照，以及有意限定范围的 Button、Surface/Card、Switch、TextField 和纯文字
NavigationBar 组件集；视觉方向参考 Samsung 公开的 One UI 7 设计指南。

这是 ViewCompose 独立实现。Samsung 并未发布、赞助或认可本产物，其中数值是 ViewCompose
基于公开指南作出的解释，不是 Samsung 内部 Design Token。它也不是完整的 One UI 组件库。

## 产物与稳定性

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="oneui7-module-dependency" sample_id="module.oneui7-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-oneui7:0.1.0-alpha02")
}
```

- 稳定性：**Alpha**。
- 平台：Android library，`minSdk 24`、`compileSdk 36`，Java 11 字节码。
- API 依赖：`viewcompose-ui-foundation`，会传递提供给使用方。
- Runtime 依赖：`viewcompose-animation` 与 `viewcompose-gesture`，会传递解析；One UI 7 的公开
  Signature 不暴露其中类型。
- Material 依赖：无。
- 参考基线：One UI 7 与 Samsung Developer One UI 指南，固定并复核于 2026-08-09。
- 公开组件集标识：`one-ui-7-five-component-alpha`。

参考基线来自 Samsung 公开的
[One UI 7 设计说明](https://design.samsung.com/global/contents/one-ui-7/)和
[One UI 组件指南](https://developer.samsung.com/one-ui/index.html)。Navigation 依据公开的
[底部导航指南](https://developer.samsung.com/one-ui/comp/bottom-navigation.html)实现纯文字
Destination。这些链接只定义视觉参考，不代表框架会复现所有 One UI 组件或 Samsung 私有数值。

## 公开指南审计

2026-08-09 的审计将已公开的精确数值与视觉解释值分开记录：

| 范围 | Samsung 公开依据 | ViewCompose 结果 |
| --- | --- | --- |
| Contained Button 形状 | 公开 Button Drawable 使用 `18dp` 圆角 | Medium Button 与 Field 形状 Token 为 `18dp`；48dp 触控目标内放置 36dp 可视 Button |
| Button 强调层级 | Flat、灰色 Contained、彩色 Contained | `Flat`、`Neutral`、`Primary` 分别对应低、中、高强调 |
| Primary Button 颜色 | Light 为 `#0072DE`，Dark 为 `#3E91FF` | 静态 Primary Action 角色采用这两个值 |
| Activated Control 颜色 | Light/Dark 都为 `#3E91FF` | Switch Checked Track 通过 `stateColors.controlActivated` 解析 |
| Switch 几何 | 没有完整公开的精确尺寸 | 可覆盖解释值采用 `44dp` × `24dp` Track、`18dp` Thumb、`3dp` 内缩，并单独保留 `48dp` 有效目标 |
| 屏幕水平边距 | 至少 `24dp` | 验证 Fixture 使用 `24dp`；实际页面布局仍由应用负责 |
| Bottom Navigation | 纯文字，通常少于四项、最多五项，不通过 Swipe 切换 | Selected Item 使用文字加下划线，不使用 Material 风格 Pill |
| Snackbar | 短时反馈，可在右侧提供 Action | 由显式 One UI Android Overlay Adapter 提供，并采用随高度变化的全圆角 Pill 外形 |

Samsung 没有公开本 Alpha 所用 Surface/Card Padding、Switch Bounds、TextField Padding、完整
Typography Scale 或 One UI 7 Overlay Chrome 的精确值。这些仍是 ViewCompose 自有解释值，需要通过
截图验收，文档不会把它们表述成 Samsung Token。

组件 Geometry 现在会从调用方提供的 Snapshot 解析。复制并覆盖
`tokens.controls.button`、`tokens.controls.textField`、`tokens.controls.navigationBar`、
`tokens.controls.switch`、
`tokens.shapes.medium` 或 `tokens.stateColors.controlActivated` 时，对应组件会真实变化，不再被组件
内部硬编码值遮蔽。

## 最小接入

在使用这些组件的内容根部安装一份完整、不可变的快照：

{/* compiled-region source="viewcompose-oneui7/src/test/samples/com/viewcompose/oneui7/samples/OneUi7Samples.kt" region="oneui7-module-theme" sample_id="module.oneui7-theme" build_target=":viewcompose-oneui7:compileDebugUnitTestKotlin" */}
```kotlin
fun UiTreeBuilder.oneUi7MinimalSample() {
    OneUi7Theme(tokens = OneUi7ThemeDefaults.light()) {
        OneUi7Button(text = "Continue", onClick = {})
    }
}
```

确定性 Dark 快照使用 `OneUi7ThemeDefaults.dark()`。调用方可以复制任一 `UiThemeTokens` 并替换
语义角色后再提供。切换 Design System 时，应使用新的 Provider 快照替换根内容与 Session，不要
原地修改活动快照。中立的 `viewcompose-android` Host 不安装任何设计系统，因此应用显式依赖本产物
并安装 `OneUi7Theme` 时不会再经过隐式 Material Context。

交互反馈由快照中的 `UiInteractionTokens` 与各 One UI Recipe 的语义内容角色定义。适配器不使用
平行的 `UiColors.ripple` 槽位；应用通过替换 `tokens.interactions` 自定义按下、聚焦和悬停策略。

完整的可编译示例使用调用方持有的状态覆盖全部五个组件家族，见
[`OneUi7Samples.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-oneui7/src/test/samples/com/viewcompose/oneui7/samples/OneUi7Samples.kt)。

## 公开组件集

| 入口 | 实现边界 | Alpha 一致性 |
| --- | --- | --- |
| `OneUi7Button` | 共享 `BasicButton` 加 One UI 7 Alpha Recipe 值 | Equivalent |
| `OneUi7Surface` | 共享 `BasicSurface` 加自有 Surface Recipe | Equivalent |
| `OneUi7Switch` | Design System 自有、Renderer 无关的组合组件 | Equivalent |
| `OneUi7TextField` | 原生 Android 编辑内核外包裹自有装饰 | Equivalent |
| `OneUi7NavigationBar` | Design System 自有纯文字 Destination 组合组件 | Equivalent |

`OneUi7ThemeDefaults`、`OneUi7Theme`、`OneUi7ButtonVariant`、`OneUi7NavigationItem` 与
`OneUi7Reference` 构成其余公开接入和诊断 API。生成的参考文档位于
[`viewcompose-oneui7` API 页面](https://docs.viewcompose.com/api/viewcompose-oneui7/current/)。

`OneUi7Theme` 还会提供共享的 `UiDesignSystemAttribution` 快照。诊断会为全部五个家族报告静态
`viewcompose-oneui7/static` Token 生产者、Recipe 身份、中立 Backend、Equivalent 一致性与能力
路径。在应用显式覆盖之前，快照来源报告为 `FrameworkDefault`。这些元数据只作为证据；私有强类型
Recipe 仍保持独立，One UI 策略不会进入 UI Foundation 或 Renderer。

## 行为与降级契约

- Button 与 Switch 的有效触控目标至少为 48dp；Navigation Destination 在 68dp Bar 内提供
  52dp 目标。
- 默认 Switch 在 48dp 有效目标中使用 24dp 高的紧凑可见 Track。Track、Thumb、内缩与 Label
  间距仍是可覆盖的解释尺寸 Token，不会被表述成 Samsung 已公开的精确 Token。
- Switch 与 NavigationBar 状态归调用方所有；Callback 只请求替换状态，不会修改调用方数据。
  Switch 支持整行 Click 与有界 Follow-finger Drag，并按位置/速度 Settle；取消会回滚状态，RTL
  会镜像物理移动方向。松手后的 Settle 会从最后一个可见拖动位置继续，不会从端点重新开始。
- TextField 保留 ViewCompose 的原生 Android 编辑内核，继续承担 IME、选区、组合区、Autofill、
  Accessibility 与 Saved State 行为。
- RTL 只反转 Destination 的视觉顺序，不改变调用方 Index 或 Key。
- NavigationBar 暴露一个单选 Accessibility 行，以及每个 Destination 的逻辑列。Android 因而
  无需 One UI Renderer 分支即可播报集合位置；RTL 反转物理排布时，逻辑位置仍保持稳定。
- Backdrop Blur 不属于此 Alpha 公开 API。需要该装饰的产品必须采用不透明或半透明着色 Surface
  降级；内容、输入与 Semantics 不得依赖 Blur 是否可用。
- Shape Morph 不属于本组件集。框架的 Shape Transition 契约遇到不兼容 Shape 时，可以选择离散
  或静态终点。

本模块只拥有 Token 与组件策略，不安装 Android Window 行为、System Bar 策略、Renderer 分支或
可变全局注册表。可选的
[`viewcompose-overlay-oneui7-android`](../viewcompose-overlay-oneui7-android/README.md) 产物拥有 One UI
Snackbar 与 Modal Bottom Sheet Presenter。主题默认 Attribution 会把这些可选能力保持为
`Unsupported`；只有显式安装的 Adapter 才提供 Root 自有 Attribution 列表，把 Presenter 连同
中立 Android Dialog/Popup Transport 和降级 Android Toast Fallback 升级为实际结果。两条路径都
不会报告或选择 Material Fallback。

## 验证与限制

单元测试与 Gesture 契约测试保护 Light/Dark 快照、组件结构、参数校验、真实触摸 Click 分发、
受控 Drag 边界/Settle/取消、松手连续性与 Callback 行为。Demo 设置页中的
`Verify One UI 7 five-component alpha` 入口会覆盖 Light/LTR/1.0 与 Dark/RTL/1.3，并导出包含
Token 来源、组件集标识与一致性标签的确定性截图。API 35 模拟器证据覆盖状态变化、Disabled、
原生文本编辑、RTL 顺序与截图锚点。

Pixel 与 Samsung 真机截图验收仍是发布负责人门禁。Alpha 版本不得描述为跨 Android API/OEM
像素完全一致、完整 One UI 支持，或五个入口之外 Samsung 平台组件的替代品。

## 相关文档

- [主题运行时架构](../../architecture/theming.md)
- [UI Foundation](../viewcompose-ui-foundation/README.md)
- [One UI 7 Android Overlay Adapter](../viewcompose-overlay-oneui7-android/README.md)
- [架构总览](../../architecture/overview.md)
- [Design System 解析边界](../../architecture/decisions/0004-design-system-resolution-boundary.md)
