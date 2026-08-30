---
title: AI 接入
slug: /ai
translation_source: ai/README.md
translation_source_hash: 5db2d8fd2de9d1479ee0234fe7ff47213765f8126f8e42dd9ed54385b97c3b37
translation_status: current
---

# AI 接入

ViewCompose 把机器可读 API Reference、13 个本地 MCP 工具和 6 个 Agent Skill 作为一个
可安装的 GitHub Release 发布。开发者只需两条命令，就能让 Codex、Claude Code 或 Cursor
接入全新或已有 Android Project。Standalone Workflow 不要求 ViewCompose Checkout、本地构建
Package、Provider Key，也不要求手动编辑 MCP 配置。

Coding Client 仍然负责模型、Credential、对话和用户授权的源码修改。ViewCompose 只提供确定性的
框架事实、生成工具与明确的验证证据，既不内置也不连接模型 Provider。

## 两条命令完成安装

从 GitHub 安装精确版本：

```bash
npm install --global --ignore-scripts \
  https://github.com/ViewCompose/ViewCompose/releases/download/ai-tooling-v0.1.0/viewcompose-ai-tooling-0.1.0.tgz
```

然后在 Android Project 根目录执行，并选择一个客户端：

```bash
viewcompose-agent init --client <codex|claude-code|cursor> \
  --project-root "$(pwd -P)"
```

`init` 会以事务方式合并 `viewcompose` MCP Entry，并安装全部 6 个规范 Skill。它会保留无关设置；
对精确内容重复执行时保持幂等；遇到无效 JSON、相对路径、符号链接路径或配置与 Skill 冲突时会
拒绝覆盖。执行失败不会留下只安装了一部分的接入状态。

| 客户端 | Project MCP 配置 | Skill 根目录 |
| --- | --- | --- |
| Codex | `.codex/config.toml` | `.agents/skills` |
| Claude Code | `.mcp.json` | `.claude/skills` |
| Cursor | `.cursor/mcp.json` | `.agents/skills` |

Standalone Path 唯一的运行时前提是 Node.js 24.19.0 或更高版本。如果系统级 npm Prefix
不可写，建议使用 Node Version Manager；不要仅仅为了安装工具而使用 `sudo`。

## 确认安装状态

在 Project 根目录使用相同的客户端选项：

```bash
viewcompose-agent doctor --client <codex|claude-code|cursor> \
  --project-root "$(pwd -P)"
```

`standalone-ready` 表示 MCP Entry 与全部 Skill 都和已安装 Release 一致。报告还会把
`knowledgeAndGeneration` 与 `compilationPreviewAndLayout` 分开，因此不会把尚不可用的深层证据
误报为成功。

继续完成客户端侧连接检查：

- **Codex：**运行 `codex mcp list`，再检查 `/mcp` 与 `/skills`；首次调用使用
  `$viewcompose-api-reference`。官方资料：[MCP](https://developers.openai.com/codex/mcp/)与
  [Agent Skills](https://learn.chatgpt.com/docs/build-skills)。
- **Claude Code：**如有提示，批准 Project `.mcp.json`，运行 `claude mcp list` 与
  `claude mcp get viewcompose`，再检查 `/mcp`；首次调用使用
  `/viewcompose-api-reference`。官方资料：[MCP](https://code.claude.com/docs/en/mcp)与
  [Skills](https://code.claude.com/docs/en/skills)。
- **Cursor：**打开 **Cursor Settings > Tools & MCP**，确认 `viewcompose`，检查
  **Agent > Available Tools**，再首次调用 `/viewcompose-api-reference`。官方资料：
  [MCP](https://docs.cursor.com/context/model-context-protocol)与
  [Skills](https://cursor.com/docs/skills)。

CI 会验证生成的客户端配置、事务生命周期、精确 Skill 字节、安装后的 Package 和两个 MCP
Protocol 握手。它不会自动控制或登录专有客户端 Binary，因此上述检查仍是明确的用户步骤。

## 无需 ViewCompose 源码即可使用的能力

Standalone Mode 支持：

- 精确 API、Component、Sample 与排序后的 Capability 检索；
- Kotlin 静态验证，以及有界、只读的 Android Project 分析；
- 从粘贴的 XML 或显式限定的 Project Resource 生成 ViewCompose；
- Screenshot 预处理、Inference 验证与类型化 Resolution，以及 ViewCompose Kotlin 生成；
- API 查询、页面创建、XML 转换、Review、验证和布局调试共 6 个 Workflow；每个 Workflow
  只保留实际取得的证据等级。

证据等级依次为 `knowledge`、`static`、`compiled`、`rendered` 和 `compared`。静态结果不证明
编译通过，生成 Kotlin 也不证明页面已渲染或达到视觉一致。

可以在所选 Agent 中先尝试：

> 使用 ViewCompose 创建一个 Material 3 登录页面。编写前先检索准确 API 和已编译 Sample，
> 执行当前可用的全部验证 Lane，并报告已取得的证据等级以及不可用的更深层 Lane。

## 当前编译、Preview 与布局诊断边界

在 Release `0.1.0` 中，Compile Mode `validate_code`、Preview 渲染和渲染后的布局诊断仍然针对
匹配的 ViewCompose Source Checkout 执行。它们需要 JDK 21、仓库固定的 Android/Gradle Lane
与精确的 Knowledge Bundle Revision。这是增强模式，不是安装或使用 Standalone 工具的前提。

如果已有该 Checkout，可以把 Standalone 接入替换为 Source-bound 接入：

```bash
viewcompose-agent uninstall --client <codex|claude-code|cursor> \
  --project-root "$(pwd -P)"
viewcompose-agent init --client <codex|claude-code|cursor> \
  --project-root "$(pwd -P)" \
  --source-root <physical-absolute-viewcompose-source-root>
```

下一条工具边界会在用户显式授权的 Consumer Project 中，使用已发布 ViewCompose Maven Artifact
执行编译、Preview 与布局诊断。在该契约及其 Smoke Project 通过前，Release 会以
`VC-AI-SOURCE-ROOT-MISMATCH` 关闭失败，不会静默把静态证据升级为更深层证据。

## 升级或删除

更换 Package 版本前，先用当前已安装的 Executable 删除精确的旧 Project 接入，再安装新的固定
GitHub Release 并重新执行 `init`。这样在 Skill 或配置变化时仍能保留冲突检测。

删除当前 Project 接入：

```bash
viewcompose-agent uninstall --client <codex|claude-code|cursor> \
  --project-root "$(pwd -P)"
```

该命令只删除精确的 ViewCompose MCP Entry 与规范 Skill 字节，无关客户端设置和文件会保留。
如果受管理内容被编辑，删除会停止并要求 Review，而不是删除用户内容。全局 Package 单独删除：

```bash
npm uninstall --global @viewcompose/ai-tooling
```

## 完整性与故障排查

[固定 GitHub Release](https://github.com/ViewCompose/ViewCompose/releases/tag/ai-tooling-v0.1.0)
包含 Tarball、`manifest.json` 与 `SHA256SUMS`。发布 Workflow 会构建 Package 两次、检查精确
Inventory 与 Offline 安装/删除生命周期，并为全部 3 个 Asset 创建 GitHub Artifact Attestation。
如需独立校验 Provenance，请参考 GitHub 的
[Artifact Attestation 校验指南](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/verify-artifact-attestations)。

| 现象 | 处理方式 |
| --- | --- |
| 找不到 `viewcompose-agent` | 确认 Node 版本不低于 24.19，并且 npm 全局 Binary 目录已加入 `PATH`。 |
| `doctor` 报告 `repair-required` | 只有现有文件未修改时才重新运行 `init`；否则先检查报告中的冲突。 |
| 客户端未显示 MCP Server | 执行上面的客户端检查，按要求批准 Project 配置，再重启或 Reload 客户端。 |
| 编译或 Preview 报告 `VC-AI-SOURCE-ROOT-MISMATCH` | 保留真实的静态证据继续工作，或者显式安装 Source-bound 增强模式。 |
| 出现 Credential 请求 | 立即停止。ViewCompose 不需要模型 Provider Credential，也不会在 MCP 参数或 Project 配置中接收它。 |

贡献者内部说明见 [AI 工具契约](https://github.com/ViewCompose/ViewCompose/blob/main/tools/ai/README.md)
和当前 [AI 可验证工具计划](../project/plans/ai-verifiable-development-tooling.md)。
