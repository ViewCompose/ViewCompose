---
title: 迁移已收敛的 DSL 契约
translation_source: migration/dsl-contract-convergence.md
translation_source_hash: 7aeb2467c6fedff8efda94eb0ac513487cda585a1105cee0597b4ea4607b07f0
translation_status: current
---

# 迁移已收敛的 DSL 契约

本次 alpha 版本移除冗余组件别名和 Android 形态的交互字段，让每个组件只有一个语义事实来源，
并让通用反馈只有一条渲染器中立的执行路径。

## 交互反馈

`Box` 与 `Row` 是纯布局原语，不再接收 `rippleColor`，内部 `StateLayerBox` 与
`StateLayerRow` 伪组件也已删除。高层组件从当前设计系统解析按下、聚焦和悬停角色，并自动安装
`UiInteractionIndication.StateLayer`。禁用或不可交互组件不安装指示。

自定义交互 Surface 将 `Modifier.interactionIndication(...)` 与输入 Modifier 组合使用。指示值
完整且渲染器中立，只有 Android Renderer 会把它映射成 `RippleDrawable`。拥有多个内部目标的
原生后端控件（例如 SegmentedControl 与 NavigationBar）通过组件契约接收已选和未选状态层快照。

自定义渲染器必须消费 `InteractionIndicationModifierElement`，并穷举其 UI Contract 版本中的
全部 `UiInteractionIndication` 子类型。公开 NodeSpec 不提供旧单色回退。

主题构造也执行同一次硬切：从 `UiColors` 移除 `ripple`，从 `UiStateColors` 移除
`controlHighlight`，并通过 `UiThemeTokens.interactions` 配置按下、聚焦和悬停策略。框架
明暗默认主题保留原有状态层透明度；直接构造的自定义主题若未显式提供 `UiInteractionTokens`，
则使用文档约定的中立交互默认值。

## 组件别名

只选择现有 Variant 的别名按下表替换：

| 已移除 API | 替代方式 |
| --- | --- |
| `TextButton(...)` | `Button(..., variant = ButtonVariant.Text)` |
| `ElevatedCard(...)` | `Card(..., variant = CardVariant.Elevated)` |
| `OutlinedCard(...)` | `Card(..., variant = CardVariant.Outlined)` |

替代方式保留同一组件所有权、设计系统解析、稀疏 overrides 与无障碍行为，同时避免第二套发现和
维护入口。

## 文本输入 Profile

`PasswordField`、`EmailField`、`NumberField` 与 `TextArea` 曾重复 TextField，同时允许调用方
替换 Wrapper 用来区分自身的行为。现在统一使用带强制值契约的 `TextField`：

| 已移除 API | 替代值 |
| --- | --- |
| `PasswordField` | `inputProfile = TextFieldInputProfile.Password` |
| `EmailField` | `inputProfile = TextFieldInputProfile.Email` |
| `NumberField` | `inputProfile = TextFieldInputProfile.Number` |
| `TextArea` | `linePolicy = TextFieldLinePolicy.MultiLine(minLines, maxLines)` |

`TextFieldInputProfile` 组合键盘和 Autofill 语义；`TextFieldLinePolicy.SingleLine` 或已验证的
`MultiLine` 拥有可见行策略。外观仍归 `TextFieldOverrides`，可编辑内容、选区、Composition 与
Undo 历史仍归 `TextFieldState`。

## 动画命名

原 `AnimatedContent` 实现只执行 Alpha Cross-fade，因此改为只保留名称与契约一致的
`Crossfade`。nullable 目标状态仍受支持；该 API 不暗示尺寸变换、Slide 转场、Content Key 或
逐状态对 Transition Scope。

## 验证与源码样例

UI Foundation 的可编译样例覆盖布局原语、交互指示、类型化 TextField Profile、Variant 与集合
身份；Animation 可编译样例使用 `Crossfade`。`verifyDslApiContracts` 会拒绝公开
`rippleColor`、已移除别名，以及缺少完整参数 KDoc 和可编译样例引用的公开 `UiTreeBuilder`
DSL 入口；同一门禁也会拒绝重新引入公开 Ripple/Highlight 主题槽位。
