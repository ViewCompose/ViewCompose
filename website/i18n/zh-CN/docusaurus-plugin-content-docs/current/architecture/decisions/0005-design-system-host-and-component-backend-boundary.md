---
translation_source: architecture/decisions/0005-design-system-host-and-component-backend-boundary.md
translation_source_hash: fcde3d316a01f97d3e0418625a39c50a3240b179c9875f51e0735cb614baad5c
translation_status: current
---

# ADR-0005：设计系统 Host 与组件 Backend 边界

## 状态与日期

2026-08-09 已接受。

## 背景与约束因素

[ADR-0004](./0004-design-system-resolution-boundary.md) 已确立 Foundation Token、Component Recipe
和已解析 Renderer Contract 相互分离，并且 Android Renderer 不能识别当前设计系统。第一版多设计
实现证明，这个边界足以承载值与组件结构，但也暴露出两个必须明确决策的边界。

第一，Android View 可能在构造时从 `Context` 读取 Style Attribute。当前 `viewcompose-android`
便捷 Root 即使内容提供 One UI 或其他 Token Bundle，仍会解析 Material Themed Context 并暴露
Material Policy 类型。在 Composition 内提供不同 Token，无法撤销已由该 Context 选定的原生默认值。

第二，原生 Android Widget 提供了重要的编辑、手势、焦点、滚动和无障碍行为，但其视觉 Geometry
无法统一适配所有无关设计系统。永远使用原生控件无法获得高保真；替换所有控件又会重复成熟行为，
带来巨大的维护成本。

架构必须继续以 Android View 为执行引擎，保持 Material 第一方体验，允许非 Material 系统实现
高保真，同时不能在只有一个消费者时过早冻结通用扩展 API。

## 决策

### 中立 Host 与两阶段设计系统安装

底层 Android Host 和通用命名的 Host 入口必须保持设计系统无关。设计系统安装拆为两个契约：

1. 具名 Android 设计系统适配器可以在 Root View 创建前解析最终 Themed `Context`、Resource、
   Dynamic Color Policy、Configuration 与 Capability。
2. Composition Root 提供一个包含 Token、Recipe、Motion、Capability/fallback 策略和诊断来源的
   不可变设计系统快照。

中立 Host 接收已解析的平台环境并挂载内容。它不选择 Material、不暴露 Material Policy 类型，
也不会隐式把每个 Root 包在 Material Context 中。Overlay 与延迟 Session 使用同一快照。运行时
设计系统切换通过替换 Root/Session 完成，不就地修改存活的 Identity。

`viewcompose-android` 将收敛到中立便捷入口。Material 专属便捷能力移到 Material 具名模块或
兼容 Facade 后面。迁移必须先刻画公开 API 和生成的 Maven Dependency，再在收益合理时保持源码
兼容。过渡期间禁止新增 Material 耦合。

第一次拆分保持显式和内部化。在第二个设计系统也需要改变 Android Context 构造，并独立证明相同
生命周期和解析契约前，ViewCompose 不公开通用 Host Theme/Plugin SPI。

### 组件 Backend 阶梯

每个组件在三种正式策略中选择：

1. Android 已拥有高成本编辑、选择、滚动、无障碍或输入行为时，保留原生行为内核；
2. 共享 View、Gesture 与 Semantics 可以表达所需结构和状态机时，使用设计系统自有 DSL Composite；
3. 可复用 Drawing、Layout、Clip 或有测量证据的性能要求必须使用一个 Renderer 自有 View 时，
   使用设计无关 Custom View。

设计专属 Android 实现或外部控件保留在具名 Integration，通过中立 `AndroidView` 边界挂载。只有
两个独立消费者证明无名称的已解析契约以及生命周期、回滚、无障碍和性能行为后，才可提升到通用
Renderer。

替换原生控件前必须证明行为对等，静止状态截图保真并不充分。扩展更多自定义组件目录前，先实现
共享交互基础。

### Material 所有权与模块名称

Material 3 是第一方参考设计系统，不是渲染底座。Android XML Theme 映射、Dynamic Color、
Material Recipe/Component 与可选 Material Components Widget 集成保留在 Material 具名模块。
通用 Node 不映射为 Material Widget。

Artifact `viewcompose-material3` 与 `viewcompose-oneui7` 保持现有名称。只有真实存在依赖、平台、
发布或 Release Ownership 边界时才拆分模块，并使用 `-android` 等能力/平台后缀。不会为每个 Artifact
插入通用的 `design` 或 `theme` 单词。

## 备选方案

### 保留 Material-first 聚合，仅依赖内层 Token Override

不作为目标架构。原生 View 可以在内层 Token 出现之前消费外层 Material Context，因此非 Material
组件树会意外继承 Material Color、Shape、Overlay 与 Widget Behavior。

### 将所有通用控件映射为 Material Components Widget

拒绝。这样虽然能获得强 Material 集成，却会让 Renderer、Host Context、Dependency Metadata、
Widget Version Behavior 和非 Material 保真都依赖 Material。

### 将所有组件实现为自定义 View

拒绝。Android View 足以绘制外观，但框架会无谓接管每个控件的文本编辑、选择、滚动、输入仲裁、
无障碍与 OEM/API 兼容。

### 强制所有设计系统使用同一个公开组件层级

拒绝。这会形成并集 API，并把无关 Slot Model 与 State Machine 强塞进一个契约。共享必须基于已证明
的语义相同，而不是名称相似。

### 立即公开通用设计系统或 Host Plugin Registry

延后。一个 Material Context Adapter 加静态非 Material Token，还不足以证明持久公开 SPI。显式
Composition 与具名 Integration Module 更容易修改。

### 重命名所有设计 Artifact，加入 `design` 或 `theme`

拒绝。现有名称已经表达设计系统 Identity；新增单词不表示依赖或平台边界，只会带来 Maven 迁移。

## 后果与权衡

- 非 Material 系统可以构造原生 View，而不会意外继承 Material Context 默认值。
- Material 设置在内部会更显式，同时 Material 具名便捷 API 仍可保持很小的应用接入成本。
- Host 重构会影响公开 Overload 与 Dependency Metadata，因此实施前需要 Source/API 和 Maven
  Baseline，并保持可逆兼容层。
- 不同系统会有少量有意重复的组件结构，行为、Primitive 与 Renderer 执行继续共享。
- Backend 改为按组件和证据选择；一个设计系统混合 Native Core、Composite 与 Custom View 并不
  破坏架构一致性。
- 暂不公开 Adapter SPI 会限制推测性灵活度，但可避免冻结只为单一消费者设计的抽象。
- Material Components 仍可在保留价值明确时使用，但只能位于具名 Integration 后，并且不能泄漏
  具体 Widget 类型。

## 受影响模块与公开契约

- `viewcompose-host-android`：继续作为中立挂载与平台安装 Kernel。
- `viewcompose-android`：从隐式 Material 便捷聚合向中立入口迁移；兼容策略由实现 Baseline 决定。
- `viewcompose-material3`：拥有 Theme/Context 解析、Dynamic Color、Recipe、Component 与保留的
  Material 专属 Android 集成。
- `viewcompose-oneui7` 与未来设计系统模块：拥有自身词汇和组件，只消费中立基础与执行契约。
- `viewcompose-ui-foundation`：拥有可复用 Basic 原语和 Interaction/Semantic Contract，而不是具名
  Component Policy。
- `viewcompose-renderer-android`：拥有 Android View 执行与中立 Custom View，不选择设计系统。
- `viewcompose-ui-contract`：只接收由多个独立消费者证明的稳定、无名称执行语义。

任何公开或 Protected API 变化都必须在同一实现变更中补齐 Q 级文档、可编译 Sample、模块手册、
兼容证据与 Release Changeset。

## 验证与落地

实施顺序保证架构工作优先于组件工作：

1. 冻结架构标准，并增加依赖/源码审计；
2. 记录当前公开 Host Signature、Maven Metadata、Context/Token Provenance、原生 Widget Default、
   截图与性能 Baseline；
3. 拆分平台 Context 解析与 Composition Policy Provision，且不改变 Renderer Contract；
4. 从中立 Host 入口移除 Material 类型和隐式 Material Context 选择；只有兼容收益大于成本时才
   保留 Compatibility Facade；
5. 验证 Material 与 One UI Root、Overlay、Lazy/Navigation Session、XML/Static/Application Token
   Provenance、进程/配置重建，以及 API 24/31/35/当前版本行为；
6. 盘点每个已映射组件的 Backend，先补齐共享行为缺口，再替换更多原生控件；
7. 只有 Isolation、Behavior、Accessibility、Screenshot 与 Performance 门禁通过后，才扩展设计
   系统组件目录。

如果 Host 重构破坏 Root/Session 一致性、批准范围外的公开兼容、Maven 依赖易用性或测量到的启动/
Patch 行为，则回退。回退应恢复 Adapter 接线，不能把 Material 知识引入 Renderer 或 UI Foundation。

## 关联决策

- [ADR-0002：五层运行时模块架构](./0002-five-layer-runtime-module-architecture.md)
- [ADR-0003：公开包所有权与平台 Handle](./0003-public-package-ownership-and-platform-handles.md)
- [ADR-0004：设计系统解析边界](./0004-design-system-resolution-boundary.md)
- [多设计系统架构与接入标准](../design-systems.md)
