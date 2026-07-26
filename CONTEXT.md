# ViewCompose Context

## 1. 文档定位

这是 `ViewCompose` 的统一上下文入口。

目标是让人类和 AI 在最短路径内恢复真实项目状态，避免被历史快照文档干扰。

## 2. 推荐阅读顺序

当线程中断或需要快速恢复上下文时，按下面顺序阅读：

1. [WORKFLOW.md](/Users/gzq/AndroidStudioProjects/UIFramework/WORKFLOW.md)
2. [ARCHITECTURE.md](/Users/gzq/AndroidStudioProjects/UIFramework/ARCHITECTURE.md)
3. [ROADMAP.md](/Users/gzq/AndroidStudioProjects/UIFramework/ROADMAP.md)
4. [THEMING.md](/Users/gzq/AndroidStudioProjects/UIFramework/THEMING.md)
5. [MODIFIER.md](/Users/gzq/AndroidStudioProjects/UIFramework/MODIFIER.md)
6. [NODE_PROPS.md](/Users/gzq/AndroidStudioProjects/UIFramework/NODE_PROPS.md)
7. [PERFORMANCE.md](/Users/gzq/AndroidStudioProjects/UIFramework/PERFORMANCE.md)
8. [TEXT_INPUT.md](/Users/gzq/AndroidStudioProjects/UIFramework/TEXT_INPUT.md)
9. [LAZY_COLLECTIONS.md](/Users/gzq/AndroidStudioProjects/UIFramework/LAZY_COLLECTIONS.md)
10. [DIAGNOSTICS.md](/Users/gzq/AndroidStudioProjects/UIFramework/DIAGNOSTICS.md)
11. [SESSION_CONTAINER_CHECKLIST.md](/Users/gzq/AndroidStudioProjects/UIFramework/SESSION_CONTAINER_CHECKLIST.md)

## 3. 当前“有效文档”边界

根目录只保留下列“长期有效”文档作为主上下文：

1. `WORKFLOW.md`：协作与提交规则
2. `ARCHITECTURE.md`：模块职责、调用链、架构约束
3. `ROADMAP.md`：统一路线图（合并 widget/demo/overlay/ui testing）
4. `THEMING.md`：主题系统与局部覆盖规则
5. `MODIFIER.md`：Modifier/NodeSpec/Theme 边界
6. `NODE_PROPS.md`：NodeSpec-only 语义规范
7. `PERFORMANCE.md`：性能基线与优化主线
8. `TEXT_INPUT.md`：完整纯文本编辑模型与原生桥接边界
9. `LAZY_COLLECTIONS.md`：Lazy 状态、结构化条目与 RecyclerView 映射
10. `DIAGNOSTICS.md`：Render Tree、Patch、Local 与重组原因诊断
11. `SESSION_CONTAINER_CHECKLIST.md`：延迟 session 容器专项检查表

## 4. 历史文档策略

历史审计、阶段性规划、已完成改造计划统一迁移到：

- [docs/archive/README.md](/Users/gzq/AndroidStudioProjects/UIFramework/docs/archive/README.md)

这些文档用于追溯决策，不作为当前实现规范。

## 5. 使用约束

1. 新增能力或边界变更时，优先更新“有效文档”，不要在归档文档继续追加状态。
2. 如果改动已经覆盖文档中“当前问题/待办项”，代码与文档必须同一步或相邻提交完成。
3. 任何新线程恢复，都以当前仓库状态和上述有效文档为准，不以聊天历史为准。
