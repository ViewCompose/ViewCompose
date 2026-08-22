---
translation_source: architecture/decisions/README.md
translation_source_hash: b4e14a6e0a15d1e16a63128a22eb34d8cf6e9abf3f5cb21ab33e43b0bd5d4a18
translation_status: current
---

# 架构决策记录

架构决策记录保存难以逆转、影响多个模块或建立公共契约的决策。它们解释为什么选择某种设计；
当前架构页面则说明系统现在如何工作。

## 已接受的决策

- [ADR-0001：托管文档平台](./0001-hosted-documentation-platform.md)
- [ADR-0002：五层运行时模块架构](./0002-five-layer-runtime-module-architecture.md)
- [ADR-0003：公开包所有权与平台 Handle](./0003-public-package-ownership-and-platform-handles.md)
- [ADR-0004：设计系统解析边界](./0004-design-system-resolution-boundary.md)
- [ADR-0005：设计系统 Host 与组件 Backend 边界](./0005-design-system-host-and-component-backend-boundary.md)
- [ADR-0006：Root 作用域 Overlay Backend 选择](./0006-root-scoped-overlay-backend-selection.md)
- [ADR-0007：Host 所有的 Android 资源环境](./0007-host-owned-android-resource-environment.md)
- [ADR-0008：事务式 Effect 生命周期](./0008-transactional-effect-lifecycle.md)
- [ADR-0009：开发工具隔离与按请求检查](./0009-development-tooling-isolation.md)
- [ADR-0010：分层可保存状态所有权](./0010-hierarchical-saveable-state-ownership.md)
- [ADR-0011：预取 Session 激活边界](./0011-prefetched-session-activation-boundary.md)
- [ADR-0012：Lazy 集合的逻辑与物理所有权](./0012-lazy-collection-logical-and-physical-ownership.md)
- [ADR-0013：组件外观解析边界](./0013-component-appearance-resolution-boundary.md)
- [ADR-0014：渲染器中立的交互指示](./0014-renderer-neutral-interaction-indication.md)
- [ADR-0015：可观察属性事务](./0015-observed-property-transactions.md)
- [ADR-0016：ConstraintLayout 图与 Helper 所有权](./0016-constraintlayout-graph-and-helper-ownership.md)
- [ADR-0017：类型化 ConstraintLayout Helper 展开](./0017-typed-constraint-helper-expansion.md)
- [ADR-0018：焦点可见性与 Pager 选择权归属](./0018-focus-visibility-and-pager-selection-ownership.md)

## 规则

1. 使用下一个四位编号和小写 kebab-case 标题。
2. 不得重写已接受记录来隐藏历史取舍。
3. 决策改变时新增 ADR，并明确声明替代此前记录。
4. 决策改变实现时，同步更新当前架构和模块文档。
