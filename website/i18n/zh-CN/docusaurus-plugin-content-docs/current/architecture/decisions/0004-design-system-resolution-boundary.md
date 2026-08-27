---
translation_source: architecture/decisions/0004-design-system-resolution-boundary.md
translation_source_hash: fab4d1c1609d6c31474747147f669fd8c97cdd1886fefbf2a56ec7c72a40b9ba
translation_status: current
---

# ADR-0004：设计系统解析边界

## 状态与日期

于 2026-08-07 接受。

## 背景与约束

ViewCompose 需要在同一个 Android View Runtime 上支持高保真 Material、One UI、Cupertino 风格
和产品自有界面。这些系统的差异可能是组件结构，而不只是颜色和圆角。扩大单个
`UiThemeTokens` Snapshot 会耦合无关组件词汇；在 Android Renderer 中按设计系统分支，则会
逆转模块依赖方向，并迫使渲染基础设施感知产品策略。

框架仍需共享昂贵的行为与渲染基础设施：State 与组合、原生文本编辑、Target Bounds、Semantics、
Shape、Effect、动画所有权、View Reconcile 和诊断。Runtime 切换与降级选择也需要在 Lazy Content
和 Overlay 间保持同一份一致 Snapshot。

## 决策

ViewCompose 把设计系统解析分为三类数据边界：

1. Foundation Token 是颜色、排版、密度、Shape、Elevation 与 Motion 等不可变可复用语义，不含
   组件 Factory、Callback、Android Resource 或设计系统 Identity。
2. 强类型组件 Recipe 由具体设计系统模块所有。Recipe 选择已解析值或设计系统自有结构，并具有
   适用于 Local Snapshot 的稳定值 Identity。ViewCompose 不发布一个通用 Recipe 大集合。
3. 共享 Basic 原语或设计系统 Composite 发射设计系统无关的 `NodeSpec`。Android Renderer 执行
   已解析的几何、交互、Semantics、Effect 和降级策略，但不识别来源设计系统。

`BasicSurface` 是第一个共享已解析边界；`BasicButton` 证明中立 Action Composite 可行。
`BasicTextField` 保留为原生编辑核心，装饰由设计系统所有。共享 Basic Toggle 暂缓，直到 Drag、
无障碍、状态恢复与设备证据证明存在共同的行为契约。结构不同的导航仍是所属设计系统 Composite。

Motion 策略是独立不可变数据。`MotionScheme` 解析语义角色与减少动效替换，实际执行继续复用已有
生命周期所有的 `Animatable` 与 `Transition`。兼容 Shape 参数可以插值；不兼容几何报告离散/静态
降级。任意 Path Morph 和第二套组件私有动画 Runner 不进入共享契约。

Runtime 切换以一份不可变设计系统 Bundle 替换 Root/Session，不要求原地修改活动 Bundle。
Overlay 与延迟内容捕获和所属 Root 相同的已解析 Snapshot。

## 备选方案

- 在 `UiThemeTokens` 中加入全部组件样式：拒绝，因为它形成跨系统 Union，并让无关 Token 变化
  使整个主题 Snapshot 失效。
- 在 Android Renderer 中加入 `when (designSystem)`：拒绝，因为它逆转依赖所有权，并把渲染
  基础设施耦合到具名产品策略。
- 优先发布 Renderer/Plugin Registry：暂拒，直到两个独立系统证明 Generic Node、Surface、
  Canvas、Graphics 或自定义 View Backend 无法安全表达所需传输。
- 强迫所有组件使用同一 Basic 继承体系：拒绝，因为导航、TextField 装饰、Switch 交互和 Slot
  顺序可能具有本质不同的结构与状态机。

## 结果与取舍

- 新设计系统可以共享 Runtime 与 Renderer，而无需在自身模块边界以下增加具名分支。
- 部分组件会有意重复小型组合结构，从而保持自身公开词汇一致，而不是形成通用 Union。
- 设计系统 Provider 必须在发射前解析完整 Recipe 并保持值不可变；这比在组件深处读取全局默认值
  更显式。
- Root Replacement 可能重建原生 View。它换取原子策略切换并避免 Overlay 混用新旧状态；只有
  契约允许时才保留调用方所有的 Saveable State。
- 高保真 Effect 会携带显式 Exact、Equivalent、Degraded 或 Unsupported 结果。降级可以减少装饰，
  但不能改变输入、Semantics、Bounds 或目标状态。

## 受影响模块与公开契约

- `viewcompose-ui-contract`：仅承载已解析几何、Effect、Bounds、State Layer、Semantics 与诊断。
- `viewcompose-ui-foundation`：中立 Basic 原语与可复用 Foundation Token 访问。
- `viewcompose-animation-core` 与 `viewcompose-animation`：语义 Motion 解析、共享生命周期所有权和
  兼容 Shape 插值。
- `viewcompose-renderer-android`：通用执行与能力探测，不拥有设计系统策略。
- `viewcompose-material3` 与未来具名设计系统 Artifact：Token、Recipe、Composite、Conformance 声明
  和集成映射。
- `viewcompose-host-android` 与 Aggregate：安装 Root/Session，不在通用 Host 内选择设计系统。

## 验证与推广

每个共享契约必须先通过一套内部、视觉差异明显的五组件 Fixture：Button、Switch、TextField、
NavigationBar 与 Surface/Card。测试覆盖 Local Snapshot 传播、保留 Patch、无障碍与输入、兼容及
降级几何、减少动效、切换、Overlay 和设计系统隔离。公开非 Material Artifact 还要求设备/模拟器
截图、性能对比、模块文档、可编译 Sample 和不可变 Maven Changeset。代表性模拟器无法复现的
硬件 OEM 验收继续作为 Release Gate。

## 相关决策

- [ADR-0002：五层运行时模块架构](./0002-five-layer-runtime-module-architecture.md)
- [ADR-0003：公开包所有权与平台 Handle](./0003-public-package-ownership-and-platform-handles.md)
