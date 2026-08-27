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
translation_source_hash: 7f8a3756abf7b4c456e297b2fece06565b6f58cafc5736a4ffc5006d818376f4
translation_status: current
---

# 活动执行计划

本目录保存当前进行中、需要跨会话延续的多步骤工作。

## 活动计划

前两份计划组成一套协调执行方案。先冻结并拆出现有质量门禁，再增加治理 V2 扫描器；先建立生成型
reference 核心，再大规模移动内容；随后用文档迁移 PR 观察选择性 CI 的 rollout 结果。
计划详情是从本索引链接的仓库专属 draft，因为其中保存的是临时贡献者执行状态，而不是面向用户的
指南。它们不会进入公共站点、搜索索引或 sitemap。

- [PR 门禁扩展与构建逻辑模块化](https://github.com/ViewCompose/ViewCompose/blob/main/docs/project/plans/pull-request-gate-scaling-and-build-logic-modularization.md) —
  把根构建文件中 2,582 行的门禁实现拆为经过编译且可测试的构建逻辑，再引入保守的 PR 影响选择、
  经过验证的不可变 API 缓存和受影响模块验证，同时不削弱 required check 或 `main` 全量验证。
- [文档系统治理 V2 与能力重构](https://github.com/ViewCompose/ViewCompose/blob/main/docs/project/plans/documentation-system-governance-v2.md) —
  以流程优先方式修复能力 owner、文档类型与版本通道契约、完整的可执行 sample 发现、
  no-new-debt 棘轮、生成型 DSL/Modifier reference、信息架构以及按优先级建设独立 Tutorial/Guide。
- [Demo 发布后验证收尾](https://docs.viewcompose.com/project/plans/demo-post-release-verification-closeout/) —
  当前因硬件条件延迟：没有可用物理设备能够证明所需的 CPU、GPU 和显示管线稳定控制。其余阶段均已
  完成；只有在具备合格设备后，才恢复采集未改变的 collection-stress revision-3 scroll 基线。

已完成的架构、动画能力、设计系统、主题传播、原生控件、组件外观、Tutorial、语言一致性、迁移
sample、托管文档、版本保留和 Paging 集成计划均保存在
[归档](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/README.md)中。

新增计划前，请阅读[文档治理规范](../documentation-governance.md)。计划必须具有清晰的完成条件，
在实现期间持续更新，并在完成后移入
[`docs/archive/`](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/README.md)。

每份活动计划还必须恰好包含一个 `## Maven 发布 Changeset` 章节。在与发布相关的实现开始前声明
“无”；之后用一个 inline-code bullet 记录计划拥有的每个不可变 `release/changes/*.json` 文件。
如果某个活动计划关联的直接制品或依赖传播制品仍被选中，Maven Central 上传门禁会拒绝发布。
