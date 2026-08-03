---
title: ViewCompose 文档
slug: /documentation
translation_source: README.md
translation_source_hash: be300aa6dcc144345bb107a645174cedb60568601fad5d52fbf14fb1619ceb8d
translation_status: current
---

# ViewCompose 文档

这里是 ViewCompose 的权威文档入口。文档同时面向人工阅读和 AI 辅助维护，也是已经发布的
GitHub 托管文档站点的内容边界。

仓库当前状态和下面列出的有效文档才是权威信息。`archive/` 下的文件仅作为历史记录。

## 选择阅读路径

| 目标 | 建议入口 |
| --- | --- |
| 构建第一个应用 | [构建第一个应用](./tutorials/getting-started.md) |
| 逐步构建真实应用 | [任务清单状态与布局](./tutorials/task-list-foundations.md) → [输入与 Lazy 集合](./tutorials/task-list-input-and-lists.md) → [主题与导航](./tutorials/task-list-theme-and-navigation.md) → [Overlay 与 Android View](./tutorials/task-list-overlays-and-android-views.md) → [动画与手势](./tutorials/task-list-animation-and-gestures.md) → [性能与诊断](./tutorials/task-list-performance-and-diagnostics.md) |
| 理解框架 | [架构总览](https://docs.viewcompose.com/architecture/overview) → [Modifier 模型](https://docs.viewcompose.com/architecture/modifier) → [NodeSpec 模型](https://docs.viewcompose.com/architecture/node-spec) |
| 从 Jetpack Compose 迁移 | [Compose 迁移总览](./migration/README.md) → 按状态、布局、宿主或导航选择迁移路径 |
| 选择或维护已发布产物 | [已发布模块目录](./modules/README.md) → 对应模块手册 |
| 使用某项能力 | 从下面的[指南](#指南)中选择对应主题 |
| 使用预览或进行性能工作 | [预览](./tooling/preview.md) → [诊断](https://docs.viewcompose.com/tooling/diagnostics) → [性能](https://docs.viewcompose.com/tooling/performance) |
| 参与贡献 | [开发流程](https://docs.viewcompose.com/project/workflow) → [文档治理规范](https://docs.viewcompose.com/project/documentation-governance) |
| 准备发布 | [发布流程](https://docs.viewcompose.com/project/publishing) → [能力验证](https://docs.viewcompose.com/project/capability-verification) |
| 恢复项目上下文 | 阅读[路线图](https://docs.viewcompose.com/project/roadmap)和对应领域的有效文档，不要从归档计划开始 |

## 架构

长期有效的契约、边界和运行时语义：

- [架构总览](https://docs.viewcompose.com/architecture/overview)
- [架构决策](https://docs.viewcompose.com/architecture/decisions)
- [Modifier 模型](https://docs.viewcompose.com/architecture/modifier)
- [NodeSpec 模型](https://docs.viewcompose.com/architecture/node-spec)
- [状态快照](https://docs.viewcompose.com/architecture/state-snapshots)
- [生命周期和 SavedState](https://docs.viewcompose.com/architecture/lifecycle-and-saved-state)
- [渲染失败](https://docs.viewcompose.com/architecture/render-failures)
- [Session 容器](https://docs.viewcompose.com/architecture/session-containers)

## 教程

由仓库内可编译示例支撑的端到端学习路径：

- [构建第一个应用](./tutorials/getting-started.md)——安装已发布模块，并通过一个 Activity
  构建由原生 View 渲染的计数器。
- [使用状态与布局构建任务清单](./tutorials/task-list-foundations.md)——通过不可变数据、快照状态、
  布局、Modifier 和事件启动渐进式应用。
- [添加任务输入和带 key 的 Lazy 列表](./tutorials/task-list-input-and-lists.md)——在同一个应用中
  加入可编辑文本、不可变集合更新、稳定 key 和设备测试。
- [添加语义主题与列表详情导航](./tutorials/task-list-theme-and-navigation.md)——加入宿主解析 token、
  类型化路由和框架管理的返回栈。
- [确认删除并托管原生 Android View](./tutorials/task-list-overlays-and-android-views.md)——集成
  自定义 Dialog Overlay 和由状态驱动的原生 `TextView`。
- [为完成状态添加动画与有边界的手势](./tutorials/task-list-animation-and-gestures.md)——让动画和
  任务行手势复用同一组确定性应用操作。
- [调整集合复用并检查渲染诊断](./tutorials/task-list-performance-and-diagnostics.md)——明确集合
  性能提示，并在不产生渲染循环的情况下采样不可变宿主计数。

## 指南

功能行为和平台集成：

- [主题](https://docs.viewcompose.com/guides/theming)
- [文本输入](https://docs.viewcompose.com/guides/text-input)
- [Lazy 集合](https://docs.viewcompose.com/guides/lazy-collections)
- [焦点和输入](https://docs.viewcompose.com/guides/focus-and-input)
- [嵌套滚动](https://docs.viewcompose.com/guides/nested-scroll)
- [导航](https://docs.viewcompose.com/guides/navigation)
- [浮层](https://docs.viewcompose.com/guides/overlays)
- [阴影](https://docs.viewcompose.com/guides/shadows)

## 从 Jetpack Compose 迁移

明确标注源版本和目标版本的语义对比与迁移路径：

- [Compose 迁移总览和统一能力矩阵](./migration/README.md)
- [状态、重组与保存恢复](./migration/compose-state-recomposition-and-restoration.md)
- [布局、Modifier 与环境](./migration/compose-layout-modifier-and-environment.md)
- [宿主、生命周期与 Android 互操作](./migration/compose-host-lifecycle-and-android-interop.md)
- [Navigation 2 与 Navigation 3](./migration/compose-navigation.md)

## 已发布模块

[已发布模块目录](./modules/README.md)与 Maven 发布元数据保持同步。每个已发布产物都在
`docs/modules/<artifact-id>/` 下提供独立手册，并可随对应产物独立演进。

## 工具

开发期工具、检查和性能能力：

- [预览](./tooling/preview.md)
- [诊断](https://docs.viewcompose.com/tooling/diagnostics)
- [性能](https://docs.viewcompose.com/tooling/performance)

## 项目维护

当前流程、发布和规划信息：

- [开发流程](https://docs.viewcompose.com/project/workflow)
- [文档治理规范](https://docs.viewcompose.com/project/documentation-governance)
- [本地化工作流](./project/localization.md)
- [源码文档与 API 注释规范](https://docs.viewcompose.com/project/api-documentation-quality)
- [文档站点运维](https://docs.viewcompose.com/project/documentation-site)
- [发布流程](https://docs.viewcompose.com/project/publishing)
- [路线图](https://docs.viewcompose.com/project/roadmap)
- [能力验证](https://docs.viewcompose.com/project/capability-verification)
- [有效执行计划](https://docs.viewcompose.com/project/plans)

## 文档规则

1. 仓库根目录只保留入口页和社区治理文件。
2. 区分跨模块概念与单个产物的依赖、兼容性和 API 契约。
3. 公开 API 变化必须同步更新 KDoc/Javadoc 和对应模块手册。
4. 每个代码 PR 都要应用文档影响矩阵；选择“无文档影响”时必须说明理由。
5. 跨会话执行计划放在 `docs/project/plans/`，完成后移动到 `docs/archive/`。
6. 使用仓库相对链接，禁止提交本地绝对路径。
7. 每份有效文档都必须能从本索引沿链接访问。
8. 不得把归档文档当作当前需求。
9. 提交文档前运行 `./gradlew verifyDocumentationStructure`；`qaQuick` 也包含该检查。
10. 修改已翻译的公开内容时遵循[本地化工作流](./project/localization.md)，不得在未审阅
    语义的情况下只刷新翻译指纹。

完整契约、命名规则、生命周期和审查清单位于
[文档治理规范](https://docs.viewcompose.com/project/documentation-governance)。
