---
translation_source: architecture/decisions/0002-five-layer-runtime-module-architecture.md
translation_source_hash: 87cc9260dc0b914a86c2a57f3e48e59644e2c5f53eeec4f0330d66f6c736dd21
translation_status: current
---

# ADR-0002：五层运行时模块架构

## 状态与日期

已接受并实现——2026-08-06。

## 背景

ViewCompose 过去把多数必要运行时产物统称为 Foundation，其余归为可选能力或工具。这能避免
可选功能反向进入核心渲染链路，但 Foundation 逐渐同时包含平台无关 Kernel、声明式 UI、Android
View Renderer、Android Host、AndroidX 适配和 Material 行为，边界不再清楚。

这会带来三类问题：模块名无法表达真正职责；AndroidX 基础设施与 Material 设计系统被当成同一
类依赖；为了方便接入，设计系统行为可能未经架构决策就进入 Host 或 Renderer。

ViewCompose 的目标仍是 Android View，不借此宣称跨平台；分层目的是让确定性 Kernel、UI 语义、
Android 执行、设计策略和可替换集成可以独立测试与发布。

## 决策

### 1. 运行时代码采用五层职责

1. **Kernel**：确定性状态、不可变契约及文本、导航、动画、手势、图形策略；不依赖 Android、
   AndroidX、Material、Integration 或 Tooling。
2. **UI Foundation**：设计系统无关的声明树、Local、Effect、通用组件语义、token schema 与延迟
   内容契约。
3. **Android Engine**：Android View 创建、协调、绑定、宿主、调度、环境适配及明确的 Android/
   AndroidX 互操作。
4. **Design System**：具体视觉 token、主题解析、组件呈现默认值及设计系统平台适配；默认实现为
   Material 3。
5. **Integrations**：可选 AndroidX 或第三方适配；移除模块时只应失去相应集成能力。

Tooling 位于五层之外并只向下依赖。消费端 Aggregate 可以暴露经过审核的默认技术栈，但不拥有
新的运行时语义。

### 2. 依赖方向由门禁强制

低层不得依赖高层。Kernel 是共同基础；UI Foundation 与 Android Engine 使用 Kernel；Design
System 使用 UI Foundation；Integration 只能使用自身能力所需的下层或受控同层契约；任何运行时
层都不能依赖 Tooling。

每个模块必须登记所属层和允许依赖的目标层。`verifyModuleDependencyBoundaries` 会拒绝未分类模块、
逆向依赖、运行时到 Tooling 的依赖，以及未登记的 `viewcompose-*` 边。

### 3. AndroidX 与 Material 的架构含义不同

AndroidX 可以作为 Android Engine 基础设施，也可出现在明确命名的 AndroidX Integration。
Material 只能属于 Design System 或明确标记为 Material-backed 的 Integration。Material 类型与
资源不能进入 Kernel、UI Foundation 或 Android Engine 的公开契约。

### 4. Renderer 只接收已解析语义

UI 节点携带通用语义与已解析视觉值，Android Renderer 负责实现这些值，不选择 Material 默认值。
本次改造不会为了移动少量控件而开放通用 Binder SPI；新的 Renderer 扩展面必须单独证明使用场景、
生命周期、性能与冲突规则。

### 5. 易用性放在 Aggregate

`viewcompose-android` 是推荐的单依赖入口，显式组合 Host、UI Foundation、Material 3 和审核过的
AndroidX 集成。高级使用方可直接依赖底层模块；Host 与 Renderer 不得为了初学者路径反向依赖
Aggregate 或 Design System。

### 6. 产物名表达职责

Alpha 阶段名称不准确的产物一次性硬切重命名。平台无关能力 Kernel 可保留 `-core`；Android
Engine、AndroidX Integration 和 Material-backed Integration 必须在名称中表达所有权。不提供
旧坐标兼容 facade。

## 产物变更

- `viewcompose-widget-core` → `viewcompose-ui-foundation`
- `viewcompose-renderer` → `viewcompose-renderer-android`
- `viewcompose-navigation` → `viewcompose-navigation-android`
- `viewcompose-lifecycle` → `viewcompose-lifecycle-androidx`
- `viewcompose-viewmodel` → `viewcompose-viewmodel-androidx`
- `viewcompose-widget-constraintlayout` → `viewcompose-constraintlayout-androidx`
- `viewcompose-overlay-android` → `viewcompose-overlay-material3-android`
- 新增 `viewcompose-material3` 与 `viewcompose-android`

职责已经准确的 Kernel、能力 DSL、图片集成、阴影与 Tooling 名称保持不变。

## 未采用方案

仅补文档不能阻止 Gradle 依赖漂移；照搬 AndroidX/Material 仓库结构会按上游厂商而非框架职责拆分；
旧 Maven facade 会长期保留重复名称；为当前少量 Material 控件开放通用 Renderer Registry 会引入
生命周期、优先级、冲突、线程和性能等永久公共契约。因此这些方案均未采用。

## 后果与取舍

- 一次迁移会影响目录、Gradle path、Maven 坐标、示例、Tooling、发布元数据与文档。
- Android Engine 和 UI Foundation 的生产代码不再依赖 Material。
- Material 3 可以独立演进或替换，不改变 Kernel 与渲染事务语义。
- 普通应用通过 `viewcompose-android` 仍保持单依赖接入。
- UI Foundation 允许保留明确属于 Android-only 声明契约的 Android 类型，但不允许 Material 类型。
- 源码移动期间可短暂存在 split package；最终产物必须没有重复类，并使用审核过的资源 namespace。

## 验证与发布

改造必须通过 `verifyDesignSystemIsolation` 验证五层依赖门禁、Kernel 纯度、UI Foundation/Android Engine 的 Material 隔离、底层无
Material 宿主消费、仅依赖 `viewcompose-android` 的标准消费、POM 暴露契约、当前中英文文档、
`qaQuick`、`qaRelease` 与本地 Maven smoke。执行证据记录在
[五层硬切计划](https://github.com/ViewCompose/ViewCompose/blob/main/docs/project/plans/five-layer-module-architecture-hard-cut.md) 中。
