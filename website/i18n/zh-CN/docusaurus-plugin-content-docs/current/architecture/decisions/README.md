---
schema_version: 2
document_id: architecture.decisions-index
doc_type: architecture
owner:
  kind: project
  id: architecture
version_lane: version-agnostic
capability_ids: []
artifact_ids: []
sample_ids: []
invariants:
  - 已接受决策保留原始理由；需要改变时以新的编号 ADR 明确取代，不通过重写隐藏历史取舍。
  - 改变当前行为的决策必须在同一变更中更新有效 Architecture 与 Module owner。
evidence:
  - docs/architecture/decisions/0001-hosted-documentation-platform.md through docs/architecture/decisions/0025-ai-verifiable-development-tooling-boundary.md
  - ./gradlew verifyDocumentationStructure
translation_source: architecture/decisions/README.md
translation_source_hash: 36bb542453fbf0b03553d128c45871cb2ae0686f2085e63bbf86658ad536f79b
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
- [ADR-0019：动画物理、过渡与检查所有权](./0019-animation-physics-transition-and-inspection-ownership.md)
- [ADR-0020：分离动画值域与速度域](./0020-separate-animation-value-and-velocity-domains.md)
- [ADR-0021：关联式渲染诊断所有权](./0021-correlated-render-diagnostics-ownership.md)
- [ADR-0022：开发工具内存安装](./0022-in-memory-development-tooling-installation.md)
- [ADR-0023：保留式 ViewModel 作用域所有权](./0023-retained-viewmodel-scope-ownership.md)
- [ADR-0024：由 Scene 推导导航生命周期与 Presentation 所有权](./0024-scene-derived-navigation-lifecycle-and-presentation-ownership.md)
- [ADR-0025：AI 可验证开发工具边界](./0025-ai-verifiable-development-tooling-boundary.md)

## 规则

1. 使用下一个四位编号和小写 kebab-case 标题。
2. 不得重写已接受记录来隐藏历史取舍。
3. 决策改变时新增 ADR，并明确声明替代此前记录。
4. 决策改变实现时，同步更新当前架构和模块文档。
