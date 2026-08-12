---
translation_source: architecture/node-spec.md
translation_source_hash: 830f43351b453d6193a904bc050dc60766f2c2e7e649837823ab8ccef0551df3
translation_status: current
---

# NodeSpec 单轨规范

## 1. 文档定位

本文档定义 `ViewCompose` 的节点语义边界：仅允许 `NodeSpec`，不再存在 `Props` 双轨。

目标：

1. 保证渲染链路语义稳定、可推导、可测试
2. 防止动态字段回流导致的 patch/skip 语义退化
3. 为新增节点提供统一接入模板

历史阶段文档见：

- [NODE_PROPS_FULL_2026-03-06.md](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/NODE_PROPS_FULL_2026-03-06.md)

## 2. 当前硬边界

1. `VNode` 只包含非空 `spec: NodeSpec`，不再包含 `props`。
2. `UiTreeBuilder.emit/emitResolved` 必传 `spec`，不再接受 `props` 参数。
3. renderer 主链路只允许读取 `NodeSpec + ResolvedModifiers`。
4. 锚点等附加元数据必须通过 modifier 元素传递（如 `Modifier.overlayAnchor(...)`）。
5. 禁止新增 `Props/TypedPropKeys/PropKeys/node.props` 代码路径。

## 3. 语义分工

1. 组件语义字段：进入 `NodeSpec`。
2. 通用视觉与交互修饰：进入 `Modifier`。
3. 主题默认值：由 `Theme -> Defaults` 解析后注入 `NodeSpec/Modifier`。

## 4. 值准入边界

`NodeSpec` 值会参与 VNode 相等判断、Patch 规划、子树跳过、诊断与失败渲染回滚。因此语义
payload 必须不可变、可按结构比较且平台无关。不能仅仅因为原生 View Setter 接受某个类型，
就把 Android Framework 对象或可变接口类型保存在规格中。

文本明确遵循这条规则：

1. `TextNodeProps` 对纯文本与富文本都只保留一份权威 `TextDocument`。
2. `ButtonNodeProps` 与 `ToggleNodeProps` 使用可空 `String` 标签。
3. Android `CharSequence`、`Spanned`、`Spannable` 与 `Editable` 只存在于 Renderer 互操作代码，
   并在最终原生绑定或输入边界转换。

该分层可以避免可变 Span 与按身份比较的平台值导致未变化的 VNode 被误判为变化，或变化后的
值因错误原因被判定为相等。

## 5. 已解析 Surface 边界

`NodeType.Surface` 与 `SurfaceNodeProps` 配对，不再使用通用 `BoxNodeProps`。设计系统组件在
发射前解析 Brush、Shape、Border、交互颜色、有效尺寸、可选可见高度和裁剪策略。Android
Renderer 只执行这些值，不接收设计系统标识或语义 Token 角色。

通用调用方 Modifier 仍按顺序追加在已解析 Surface 之后。调用方 Background、Border、Corner
或 Shape 会替换组件提供的可见 Surface，并使用完整有效边界。Basic 组件可以通过普通有序
Modifier 契约提供精确 Shadow 与 Elevation，因为 Renderer 已经以通用方式执行它们。

## 6. 新节点接入清单

新增第一方节点必须完成：

1. 定义节点 `NodeSpec`。
2. 使用不可变、可按结构比较且平台无关的语义字段。
3. DSL 参数映射到 `NodeSpec`（必要时配合 modifier 元数据）。
4. renderer binder/patch 对应实现。
5. 单元测试覆盖：结构稳定、字段变化、交互变化。
6. demo 验证路径与必要 instrumentation。

## 7. 扩展路径（业务/第三方）

扩展也必须走 spec-only：

1. 自定义 `NodeSpec`
2. 自定义 binder/patch
3. 不允许通过动态 map 透传语义

## 8. 防回归机制

1. 单测已覆盖 `requireSpec<T>()` 的严格读取与失败提示。
2. 静态守卫测试扫描 framework 主源码，拦截 `Props` 体系回流。
3. 架构/流程文档将 NodeSpec-only 作为 review 必查项。

## 9. 关联文档

1. [架构总览](overview.md)
2. [开发流程](../project/workflow.md)
3. [Modifier 模型](modifier.md)
