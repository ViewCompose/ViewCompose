# NodeSpec-Only Spec

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

## 4. 新节点接入清单

新增第一方节点必须完成：

1. 定义节点 `NodeSpec`。
2. DSL 参数映射到 `NodeSpec`（必要时配合 modifier 元数据）。
3. renderer binder/patch 对应实现。
4. 单元测试覆盖：结构稳定、字段变化、交互变化。
5. demo 验证路径与必要 instrumentation。

## 5. 扩展路径（业务/第三方）

扩展也必须走 spec-only：

1. 自定义 `NodeSpec`
2. 自定义 binder/patch
3. 不允许通过动态 map 透传语义

## 6. 防回归机制

1. 单测已覆盖 `requireSpec<T>()` 的严格读取与失败提示。
2. 静态守卫测试扫描 framework 主源码，拦截 `Props` 体系回流。
3. 架构/流程文档将 NodeSpec-only 作为 review 必查项。

## 7. 关联文档

1. [overview.md](overview.md)
2. [workflow.md](../project/workflow.md)
3. [modifier.md](modifier.md)
