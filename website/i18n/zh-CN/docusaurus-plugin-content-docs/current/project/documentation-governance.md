---
translation_source: project/documentation-governance.md
translation_source_hash: e2e6b504b1a352bfc02fe1f50d639ef07d78a5c60bd4fb3feeb283805b809b08
translation_status: current
---

# 文档治理规范

## 目的

本文是 ViewCompose 文档的唯一真相源，定义信息架构、owner 边界、更新触发条件、版本模型和质量
门禁，适用于 maintainer、contributor 与 AI agent。

ViewCompose 包含许多独立发布模块，因此文档属于每个模块的公共契约。代码改变公共契约却未在
同一 PR 更新对应文档时，变更不完整。

文档系统必须长期支持：每个公开制品/版本的 KDoc/Javadoc；当前框架原理、架构和决策；与
Jetpack Compose 的事实比较和迁移路径；由可执行 sample 支撑且可独立进入的能力教程和 task-oriented guide；
以及可独立演进发布的模块文档。系统还必须可搜索、可链接、版本感知、可访问且可机械验证；站点
生成器只是实现细节，不能重定义契约。

## 信息架构

| 源位置 | 公共用途 | 生命周期 |
| --- | --- | --- |
| 仓库根目录 | Landing、社区治理和 `AGENTS.md` | 稳定且刻意精简 |
| `docs/README.md` | 权威文档索引与链接图根 | 始终最新 |
| `docs/getting-started/` | 安装、第一段 UI、项目设置和最短成功路径 | 版本感知 |
| `docs/tutorials/` | 端到端学习路径 | 版本感知且由 sample 支撑 |
| `docs/architecture/` | 当前框架原理、runtime 模型和架构契约 | 随实现更新 |
| `docs/architecture/decisions/` | 已接受或被替代的 ADR | 只追加历史 |
| `docs/guides/` | 跨一个或多个模块的目标导向指南 | 随公共行为更新 |
| `docs/migration/` | Compose 比较、Compose 迁移和 ViewCompose 版本迁移 | 明确源/目标版本 |
| `docs/modules/` | 每个发布制品一个人工维护入口 | 随制品演进 |
| `docs/tooling/` | Preview、诊断、benchmark、IDE 与开发工具 | 随工具行为更新 |
| `docs/project/` | 贡献、发布、验证、路线图和治理 | 随项目流程更新 |
| `docs/project/plans/` | 跨 Session 的有效多步骤计划 | 完成后归档 |
| `docs/archive/` | 完成计划、审计、快照和旧文档 | 历史证据，不是当前真相 |
| `website/` | Docusaurus 界面、生成 adapter 与站点工具 | 随站点演进 |
| 生成 API | 按制品/版本的 KDoc/Javadoc HTML | 发布时生成，不编辑、不提交 |

新增顶级目录需要在本文修改信息架构，并同步更新结构验证器。根 Markdown allowlist 仅包含
`README.md`、`README.zh-CN.md`、`CONTRIBUTING.md`、`CODE_OF_CONDUCT.md`、
`THIRD_PARTY_NOTICES.md` 和 `AGENTS.md`；便利性不足以新增根文档。

## 内容模型

每个公共页面只有一个主要目的，按以下顺序判断：

1. **教程**：教会新手使用一项能力并得到可运行结果；每页可以独立进入，相关教程只是可选链接，
   不能作为必修前置章节。
2. **指南**：帮助已有基础的读者完成具体任务，可链接概念与 API Reference。
3. **架构**：解释当前 invariant、边界与取舍，而非临时实施计划。
4. **迁移/比较**：映射概念或版本，说明语义差异并提供可验证路径。
5. **模块 Reference**：描述一个制品的角色、依赖、环境、入口、兼容和运维限制。
6. **API Reference**：从源码注释和签名生成，手写页只链接它。
7. **项目文档**：治理仓库维护，而非框架使用。

不得把教程、设计原因、完整 API 清单和 release note 混在一页；应拆成聚焦页面并互链。

## 框架与模块边界

框架级文档解释 rendering、state、lifecycle、theme、navigation、preview、performance、migration
和 tutorial 等跨模块概念；模块级文档描述单个 Maven 制品独立演进时仍可使用的契约。

概念的实现位于某模块并不自动意味着它是模块文档；模块 setup、dependency、公共入口与兼容也
不能藏在宽泛框架指南。跨模块页要列出依赖模块，并在版本不一致时声明测试兼容集合。模块页应
链接共享概念，不复制它们。

## 发布模块文档契约

[`docs/modules/README.md`](../modules/README.md) 是权威制品目录。publishing properties 中每个
`module.<artifact>.version` 必须恰好有一行目录；新增、重命名、发布或退役制品时，同步改变目录、
publishing metadata、依赖文档和结构验证。

`gradle/viewcompose-documentation-releases.properties` 是只追加的制品/版本/source-revision
注册表。当前发布元数据必须精确匹配一条记录，已发布 pair 永不改写或删除。

每个已发布制品必须有 `docs/modules/<artifact-id>/README.md`，不能使用 `Planned`。首个发布前
必须具备手册、生成 API 树和 strict source-comment gate。模块页拥有：用途与 non-goal；Maven
coordinate/稳定性；平台分类；直接与传递 ViewCompose 依赖；环境支持；安装和最小示例；公共
入口；lifecycle/thread/state/performance/resource 约束；相关 guide/sample/module；当前线兼容与迁移。

更深页面放同目录并从 README 链接，不按 class 建页。内部/Demo 模块不能伪装成公开制品；停止
发布时保留最后版本页，并在 live 手册标记 retired 与替代路径。

## KDoc 与 Javadoc 契约

源码注释是权威 API Reference。站点提供全制品 landing、逐制品逐版本 API、每个制品最新稳定版
alias、模块页到对应 API 的链接，以及固定到不可变发布提交的源码链接。生成 HTML 只属于 build
output，不编辑或提交到 `docs/`。

public/protected API 按适用情况记录用途、参数单位/坐标/默认/范围、返回 owner/null、state owner/
lifecycle/dispose/thread、顺序/取消/错误、Android 限制、重要性能、`@sample`、`@throws` 与弃用替代。
不能承诺测试未保护的行为；新增或改变 API 必须在同一 PR 加注释、编译 sample 和模块文档。

具体规则见[源码文档与 API 注释规范](api-documentation-quality.md)。

## 架构与设计决策

当前架构页描述系统现在如何工作，invariant、dependency direction、owner boundary 或执行模型变化
时同 PR 更新。难以逆转、影响多模块、建立公共契约或拒绝合理替代方案时使用 ADR。

ADR 路径为 `docs/architecture/decisions/NNNN-short-title.md`，包含状态/日期、背景、决策、替代、
结果与取舍、影响模块/契约、验证与 rollout、前后决策链接。已接受 ADR 不重写历史；新 ADR
supersede 旧记录，同时当前架构页更新。临时步骤放 `docs/project/plans/`。

## Compose 比较与迁移

比较页是工程 Reference，不是营销 scorecard。必须列出 ViewCompose 模块版本和 Compose/AndroidX
baseline，比较语义、lifecycle、state owner、rendering、tooling、performance 和 platform integration，
区分支持/部分/刻意差异/不支持，给出契约、测试、benchmark 或上游证据，说明性能测量条件，提供
替代模式/风险，并记录 last verified 与 owner。

迁移页要定义 source/target。Breaking change 发布前需要模块迁移页或跨模块 guide；不能复制
Compose 文档，也不能把名称相似当作语义等价。

## 教程与 sample 质量

每篇教程教会新手使用一项能力，而且从该页直接进入也必须能够运行。页面声明预期结果、已验证
模块版本和验证动作，但不能要求先完成另一篇教程；得到可运行结果后可以推荐相关页面。

- 非平凡 sample 位于可编译 sample/Demo source set 并由文档引用；
- 每篇教程开头都放置完整 Maven 依赖块，包括 `viewcompose-overlay-android` 等可选能力产物；
  不能让读者到示例中途才发现缺少依赖；
- 独立教程示例放在 `samples/<name>`，通过已发布 Maven 坐标解析 ViewCompose，只使用 public API，
  由 `qaQuick` 编译并由 `qaFull` 运行代表性行为验证；
- 每项能力优先使用一个自包含源码文件；禁止把渐进式 sample 扩张成学习单项功能前必须先理解的
  跨功能大应用；
- 短 inline snippet 有 compilation test 或复制自可编译 sample；
- sample 使用 public API 与已发布坐标；
- screenshot 标识设备、theme、font scale、locale 和模块版本；
- 使 sample 失效的同一变更中修复或删除。

教程门禁会核对精确源码区域、两种语言中的完整依赖声明，并禁止公共教程 sample 使用本地
`project(...)` 依赖。不得维护会静默漂移的大段独立代码块。

## 版本与 URL 稳定性

独立模块版本意味着不存在全局文档版本。框架概念、教程与指南描述当前支持集合，并按需显示模块
兼容矩阵；模块手册有稳定制品路径和逐发布 snapshot；API Reference 按制品/版本发布；`latest`
只指最新稳定版；不可变版本页永久保留；移动页面时提供 redirect。

```text
/modules/<artifact-id>
/modules/<artifact-id>/<version>
/api/<artifact-id>/<version>/
/migration/...
/tutorials/...
```

无版本手册描述当前支持线，版本化手册从登记 revision 生成，并在两个 locale route tree 下作为
权威英文历史 snapshot。改变 URL 语义需要 ADR 与 redirect plan。

每个模块版本旁登记完整 40 位 `sourceRevision`，源文件与 Reference 使用的发布源码逐字节一致。
发布先在一个提交冻结源码与手册，再在仅元数据提交追加历史记录并改变版本/revision。冻结提交
必须可达；源码变化或版本前进时必须有匹配 history/revision。

生产部署运行完整历史 API verifier 和生产站点构建，从不可变 Git source 重建所有记录版本，
检查 API/手册 route、`current`、稳定 `latest`、manifest 与 source link。模块子集只用于迭代。

## 语言策略

英文是权威公共语言；简体中文在 `zh-CN` namespace 以相同路径镜像。默认站点为 `/`，中文为
`/zh-CN/`。不得建立第二套独立真相，也不得在一个页面交错完整中英文副本。

生成 KDoc/Javadoc 与历史模块手册 snapshot 保持权威英文。每个有效手写页面按 locale 只使用一种
叙述语言：`docs/` 标题和正文为英文；`zh-CN` 镜像标题和正文为简体中文。代码块、命令、标识符、
URL 和真实 UI literal 原样保留；叙述中的外语 literal 用行内代码标记。临时计划、历史归档、
生成 API 和不可变历史手册只保留英文。

混合语言叙述是阻断合并的放置错误，不是翻译债务。Markdown-aware verifier 忽略代码和 literal，
但扫描所有有效权威页和 locale 镜像。

### 权威源优先工作流

每次公共文档变更：先更新并验证英文；同 PR 更新审阅中文；精确保留技术字面量；中文语义已同步
后才记录英文指纹；最后运行语言分类、翻译新鲜度和双 locale 构建。不得等英文“完成后”批量补翻译。

### 覆盖层级

| 层级 | 内容 | 合并要求 |
| --- | --- | --- |
| 必需 | 所有有效手写公共页，包括架构、指南、迁移、模块手册、项目运维、工具和教程 | 中文镜像存在、叙述为中文并匹配英文指纹 |
| 仅英文 | 生成 API、不可变历史手册、临时计划、归档和非用户指南内部证据 | 不要求中文，且不得冒充已审阅本地化正文 |

机器列表位于网站本地化工具。新增、移动或删除公共页面时，必须同时改列表、中文镜像和验证；
不得把 locale fallback 作为临时发布状态。

### 新鲜度契约

每个中文 mirror front matter 记录相对 `docs/` 的 `translation_source`、英文 SHA-256
`translation_source_hash` 和 `current`/`stale` 状态。`current` 必须匹配实际审阅源；必需页面不得
`stale`；只改 hash 而不审阅中文语义属于违规。

门禁拒绝缺失/过期必需页、错误映射、错误语言标题/正文、虚假状态和落后指纹。fallback 只用于
明确仅英文的生成或历史内容。

### PR 与审查

改变公共英文文档的 PR 必须声明中文已更新审阅、页面依政策仅英文，或无用户可见语言变化。
正确性/安全修复在变更内部仍先改英文，但公共页合并前必须完成中文。审查技术语义、链接、sample、
术语和 locale screenshot，不只审查流畅度。具体命令见[本地化工作流](localization.md)。

## 变更影响矩阵

| 变更 | 必需文档影响 |
| --- | --- |
| 新增/改变 public symbol | KDoc/Javadoc、模块页、非平凡 sample |
| 新发布模块 | publishing metadata、目录、模块 README、API pipeline、dependency guide |
| 依赖/兼容变化 | 模块页和跨模块兼容矩阵 |
| 行为/默认/lifecycle 变化 | 模块页与相关 guide/tutorial；需要用户行动时加 migration |
| 架构/owner 变化 | 当前架构页；满足条件时 ADR |
| Compose parity/divergence | 比较矩阵与迁移 guidance |
| Tooling/Preview | tooling 页、支持版本、必要 screenshot |
| Breaking/deprecation | 替代 KDoc、migration、release note、保留版本页 |
| 修正已记录行为的 bug | 修正有效页并加 regression evidence |
| 无契约影响的内部 refactor | PR 中写明 `No documentation impact` 理由 |

`No documentation impact` 是审查结论，不是默认 checkbox。

## 命名、链接与资产

有效文档使用小写 kebab-case，目录索引 `README.md` 和 ADR 数字前缀除外。有效文档互链使用相对
路径；archive 可从公共页链接权威 GitHub URL。不得提交 `file://`、本地绝对路径或未提交生成
输出链接。移动文档时同 release 更新入链并加 hosted redirect。

图片/图表必须有有意义 alt；优先文本、table 与 Mermaid。必要 binary 放在所属文档附近并记录
screenshot 复现方式。图片不能成为 API/流程唯一说明。所有有效页必须从 `docs/README.md` 通过
section index 可达；archive 由 archive index 代表。

## 文档生命周期

建页前先找既有真相源，不能创建第二份 roadmap、architecture overview、module manual 或 status。
只有跨多个实质步骤/Session 时使用 active plan；计划记录状态、范围、non-goal、baseline、完成条件、
步骤、验证、last verified 和 next action。完成后把长期结论移到有效文档，再移入 archive。

deprecated 公共文档保留到支持 release line EOL，标记 deprecated、链接替代并保留版本 URL；
不再代表支持契约的仓库文档移到 archive。

## AI 辅助维护

AI agent 必须从 `docs/README.md` 开始，阅读所属模块与有效文档；用代码/测试验证行为；编辑前后
应用影响矩阵；修正错误有效文档而非新建平行说明；通常不从 archive 恢复上下文；不提交临时
note/生成 HTML；保持模块边界、版本、链接和权威语言；新增/移动/删除内容时更新 index；交付前
运行文档门禁并报告无法运行项。根 `AGENTS.md` 是最短入口，本文保持权威。

## 审查与自动门禁

审查页面目的/owner、框架与模块边界、版本/兼容/稳定性、行为证据、源码注释、sample、模块目录、
README 可达性、相对链接、计划归档、本地化诚实性和目录语言。`verifyDocumentationStructure` 必须
通过且已包含在 `qaQuick`。文档工作流还生成完整版本 Dokka/手册目录、type-check Docusaurus、
严格检查生产链接与站点自有页无障碍，并只从 `main` 部署；生成输出和凭据不入库。
