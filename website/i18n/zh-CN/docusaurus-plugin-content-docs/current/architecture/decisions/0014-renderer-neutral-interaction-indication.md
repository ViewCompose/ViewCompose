---
translation_source: architecture/decisions/0014-renderer-neutral-interaction-indication.md
translation_source_hash: ff305802ff2c761f060e824253d8b0abdab86582d9d9b06b3e7b69e3bab65e19
translation_status: current
---

# ADR-0014：渲染器中立的交互指示

- 状态：已接受
- 日期：2026-08-15
- 扩展：[ADR-0013](./0013-component-appearance-resolution-boundary.md)

## 背景

ViewCompose 的布局原语和组件 `NodeSpec` 曾暴露 Android 形态的 `rippleColor` 属性。部分组件
同时又暴露完整的按下、聚焦和悬停状态层，导致同一反馈存在两条优先级路径。
`StateLayerBox` 与 `StateLayerRow` 还把交互反馈伪装成独立布局原语，尽管它们并不改变测量
或放置。

这会把 Android `RippleDrawable` 的执行模型泄露到 UI Contract，扩大简单布局 DSL，并让
本可通过有序 Modifier 外观增量更新的反馈变化触发 `NodeSpec` 变化和重新绑定。单一按下色
也无法表达原生复合控件中已选与未选目标的差异。

## 决策

UI Contract 提供密封、不可变的 `UiInteractionIndication` 值，以及有序的
`Modifier.interactionIndication` 元素。当前 `StateLayer` 子类型携带完整
`UiStateLayerColors`；Android Renderer 将其映射为平台状态和遮罩 drawable。新增指示子类型
必须经过明确的兼容性决策，因为渲染器必须处理其依赖版本中的全部子类型。

`Box`、`Row` 和其他布局原语只负责测量、放置、子作用域与调用方 Modifier，绝不接收交互
颜色。高层组件先解析语义角色、启用策略、稀疏 overrides 和当前设计系统 recipe，再安装
指示。`BasicSurfaceStyle` 可以携带完整的已解析指示，但面向应用的稀疏 overrides 仍按
ADR-0013 归组件所有。

主题边界遵循同一模型。`UiColors` 只包含语义颜色角色，`UiStateColors` 只包含持久组件状态，
两者都不再暴露 Android 形态的 `ripple`/`controlHighlight` 槽位。`UiInteractionTokens`
统一拥有按下、聚焦和悬停透明度。设计系统适配器可以在内部读取平台 Highlight，但进入
UI Foundation 前必须将它解析为中立交互策略或显式组件指示。

拥有多个内部交互目标的原生后端组件，在自己的 `NodeSpec` 中携带完整类型值。因此分段控件
和导航目的地保留已选与未选状态层快照。TabRow 发出 eager keyed 子 Box，每个子项通过普通
Modifier 路径拥有自己的指示。

禁用或不可交互的高层组件不安装指示。只有自定义低层节点本身可交互时，缺失指示才可交给
渲染器选择回退。Android ripple 颜色、状态列表构造、遮罩和 drawable 生命周期全部保留为
渲染器私有细节。

这是 alpha 阶段的硬切：直接移除 `rippleColor`、`UiColors.ripple`、
`UiStateColors.controlHighlight`、`StateLayerBox` 和 `StateLayerRow`，指示变化使用仅
Modifier 的 patch，不再重新绑定逻辑节点。

## 结果

- 布局 API 保持精简，并可移植到 Android 之外的渲染器。
- 设计系统解析同一份完整反馈契约，Material 3 不会成为框架策略。
- 主题快照不能在有效交互透明度策略之外再携带无人消费的 Android Highlight 槽位。
- 按下、聚焦和悬停优先级可明确测试。
- 原生多目标控件可分别渲染已选与未选反馈。
- 自定义组件通过一个有序 Modifier 使用反馈，无需制造伪布局原语。
- 自定义渲染器必须穷举其 UI Contract 版本提供的指示类型。
- Modifier 相等性使指示变化 patch 已保留 View，而不重组布局语义或重建节点。

## 未采用的方案

### 保留 `rippleColor` 兼容回退

不采用，因为它保留两个事实来源，也无法表达聚焦、悬停和分目标角色。

### 在每个可点击 `NodeSpec` 上放置反馈字段

不采用，因为通用外观属于有序 Modifier 通道，否则每个组件都需要专用 binder 与 differ
逻辑。

### 保留 `StateLayerBox` 与 `StateLayerRow`

不采用，因为状态层绘制并不定义不同的测量、放置、状态或生命周期原语。

### 让某一套设计系统拥有指示策略

不采用，因为 Material 3、One UI 7、应用设计系统和未来 recipe 都是同一中立契约之上的
平级实现。

## 验证

契约要求覆盖相等性与 Modifier 顺序、按下/聚焦/悬停状态列表、禁用时缺失、已保留 View
仅 Modifier patch、原生已选/未选目标、多设计系统编译、API dump、可编译示例，并通过源码
门禁拒绝公开 `rippleColor` 和交互伪布局 API。
