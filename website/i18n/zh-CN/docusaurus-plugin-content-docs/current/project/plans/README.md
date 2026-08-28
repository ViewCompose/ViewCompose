---
schema_version: 2
document_id: project.plan-index
doc_type: project
owner:
  kind: project
  id: planning
version_lane: version-agnostic
capability_ids: []
artifact_ids: []
sample_ids: []
workflow: 登记跨会话活动执行计划，并在归档前保留其阻断发布的 Changeset 所有权。
validation:
  - ./gradlew verifyDocumentationStructure verifyViewComposeReleaseIntent
lifecycle: 执行计划启动、状态变化、阻塞、完成或移入归档时更新。
translation_source: project/plans/README.md
translation_source_hash: c6dac046f9acd5abdcb2278182b5b2a23392788fa5f7c16703931fa262eee4ca
translation_status: current
---

# 活动执行计划

本目录保存当前进行中、需要跨会话延续的多步骤工作。

## 活动计划

计划详情是从本索引链接的仓库专属 draft，因为其中保存的是临时贡献者执行状态，而不是面向用户的
指南。它们不会进入公共站点、搜索索引或 sitemap。

- [PR 门禁扩展与构建逻辑模块化](https://github.com/ViewCompose/ViewCompose/blob/main/docs/project/plans/pull-request-gate-scaling-and-build-logic-modularization.md) —
  把根构建文件中 2,582 行的门禁实现拆为经过编译且可测试的构建逻辑，再引入保守的 PR 影响选择、
  经过验证的不可变 API 缓存和受影响模块验证，同时不削弱 required check 或 `main` 全量验证。
- [Demo 发布后验证收尾](https://docs.viewcompose.com/project/plans/demo-post-release-verification-closeout/) —
  当前因硬件条件延迟：没有可用物理设备能够证明所需的 CPU、GPU 和显示管线稳定控制。其余阶段均已
  完成；只有在具备合格设备后，才恢复采集未改变的 collection-stress revision-3 scroll 基线。
- [AndroidX ViewModel 最优架构与 Compose 能力对齐](https://github.com/ViewCompose/ViewCompose/blob/main/docs/project/plans/viewmodel-androidx-optimal-architecture-and-compose-parity.md) —
  硬切冗余或有缺陷的 Alpha 契约，建立唯一的通用保留型作用域 owner 机制，补齐重要的 Android
  Compose 能力缺口，并用生命周期、清理、恢复、宿主和导航契约覆盖替代浅层 ViewModel 证据。
- [导航生命周期与 Scene 演进](https://github.com/ViewCompose/ViewCompose/blob/main/docs/project/plans/navigation-lifecycle-and-scene-evolution.md) —
  统一不同宿主中的 Lifecycle DSL 消费方式，修正 destination 生命周期投影，分离保留型 entry
  所有权和原生 presentation 生命周期，并让 Scene、overlay、焦点、转场及有界保留策略收敛到
  唯一的事务化导航计划。

已完成的文档治理、架构、动画能力、设计系统、主题传播、原生控件、组件外观、Tutorial、语言
一致性、迁移 sample、托管文档、版本保留和 Paging 集成计划均保存在
[归档](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/README.md)中。

新增计划前，请阅读[文档治理规范](../documentation-governance.md)。计划必须具有清晰的完成条件，
在实现期间持续更新，并在完成后移入
[`docs/archive/`](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/README.md)。

每份活动计划还必须恰好包含一个 `## Maven 发布 Changeset` 章节。在与发布相关的实现开始前声明
“无”；之后用一个 inline-code bullet 记录计划拥有的每个不可变 `release/changes/*.json` 文件。
如果某个活动计划关联的直接制品或依赖传播制品仍被选中，Maven Central 上传门禁会拒绝发布。
