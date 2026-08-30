---
title: AI 接入
slug: /ai
translation_source: ai/README.md
translation_source_hash: 01e29b4a44155398a5b1d126fe1348483a26cd370476b1e6cd6f9248472ddeed
translation_status: current
---

# AI 接入

ViewCompose 把机器可读 API Reference、13 个本地 MCP 工具和 6 个 Agent Skill 作为一个
可安装的 GitHub Release 发布。开发者只需两条命令，就能让 Codex、Claude Code 或 Cursor
接入全新或已有 Android Project。即使要执行 Kotlin 编译和生成页面的 Preview 证据，也不要求
ViewCompose Checkout、本地构建 Package、Provider Key 或手动编辑 MCP 配置。

Coding Client 仍然负责模型、Credential、对话和用户授权的源码修改。ViewCompose 只提供确定性的
框架事实、生成工具与明确的验证证据，既不内置也不连接模型 Provider。

## 两条命令完成安装

从 GitHub 安装精确版本：

```bash
npm install --global --ignore-scripts \
  https://github.com/ViewCompose/ViewCompose/releases/download/ai-tooling-v0.2.0/viewcompose-ai-tooling-0.2.0.tgz
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

API 查询、生成、静态验证和 Project 分析只要求 Node.js 24.19.0 或更高版本。若要取得编译、
渲染和比对证据，还需要 JDK 17 或 21，以及 Android SDK Platform 36。Release 已携带 Gradle
9.3.1 Wrapper 与固定 Build Harness，用户无需安装 Gradle，也无需让现有 Project 的 AGP/Kotlin
版本与工具链对齐。如果系统级 npm Prefix 不可写，建议使用 Node Version Manager；不要仅仅
为了安装工具而使用 `sudo`。

### `0.2.0` 的框架版本边界

Release `0.2.0` 不会检查或绑定已有 Project 中的 ViewCompose Dependency 版本。它的 Knowledge
Bundle 描述一个精确 `current-source` Revision，而深层证据 Harness 使用一组固定的已发布 Maven
Artifact。两类身份各自确定，但不能证明知识适用于任意旧 Project。不要仅因为 `0.2.0` 是最新工具
Release 就安装或升级到它，也不要把成功的静态查询视为版本兼容证明。

下一版升级契约会绑定框架版本：它不会执行 Project Gradle Logic，而是读取 Project 中独立版本化的
精确 `com.viewcompose` Coordinate；只选择 Released Knowledge Pack 与该 Artifact-version 子集
匹配的 Release；遇到无法解析、互相冲突或不支持的版本时保留现有接入。在该门禁发布前，面向已有
Project 的生成代码必须通过当前可用的 Compile Lane 并接受正常开发者 Review；编译仍不能证明每项
语义契约。

## 确认安装状态

在 Project 根目录使用相同的客户端选项：

```bash
viewcompose-agent doctor --client <codex|claude-code|cursor> \
  --project-root "$(pwd -P)"
```

`project-bound-ready` 表示 MCP Entry 与全部 Skill 都和已安装 Release 一致，物理 Project 根目录
已绑定，并且已满足深层证据所需的 JDK/Android SDK 前提。报告会分别列出
`knowledgeAndGeneration`、`compilationPreviewAndLayout` 和 Host 前提，因此不会把不可用的证据
Lane 误报为成功。

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

安装后的 Project-bound Mode 支持：

- 精确 API、Component、Sample 与排序后的 Capability 检索；
- Kotlin 静态验证与基于已发布 Artifact 的编译验证，以及有界、只读的 Android Project 分析；
- 从粘贴的 XML 或显式限定的 Project Resource 生成 ViewCompose；
- 对 XML 生成页面执行编译、Preview 渲染、语义/几何比对与结构化布局诊断；
- Screenshot 预处理、Inference 验证与类型化 Resolution，以及 ViewCompose Kotlin 生成；
- 对 Screenshot 生成页面执行编译、Preview 渲染、语义比对，以及符合条件时的精确 Pixel 比对；
- API 查询、页面创建、XML 转换、Review、验证和布局调试共 6 个 Workflow；每个 Workflow
  只保留实际取得的证据等级。

证据等级依次为 `knowledge`、`static`、`compiled`、`rendered` 和 `compared`。静态结果不证明
编译通过，生成 Kotlin 也不证明页面已渲染或达到视觉一致。

可以在所选 Agent 中先尝试：

> 使用 ViewCompose 创建一个 Material 3 登录页面。编写前先检索准确 API 和已编译 Sample，
> 执行当前可用的全部验证 Lane，并报告已取得的证据等级以及不可用的更深层 Lane。

## 深层证据执行边界

Release `0.2.0` 使用 Maven Central 中的精确 ViewCompose Artifact 编译生成 Kotlin，并渲染生成
页面。Package 内的 Content-addressed Harness 固定使用 Gradle 9.3.1、AGP 9.1.1、Kotlin
2.2.10、Android 36、JVM Target 11 和 Allowlist 中的 ViewCompose/Preview Coordinate。Consumer
Project 根目录只是只读授权边界：工具不会执行它的 Wrapper、Settings、Plugin、Task 或 Build
Script，也不会向 Project 写入文件。

第一次请求深层证据时，工具可能下载固定 Gradle Distribution 与 Maven Dependency；后续请求会
使用操作系统用户缓存目录中的完整性校验 Cache。Package 安装本身仍然无脚本且支持 Offline；整个
流程也不需要模型 Provider 的网络访问。

`validate_code` 的 Compile Mode 接收有界 Kotlin Snippet。XML 与 Screenshot 生成工具只执行自己
确定性生成的源码，依次编译、渲染、重新打开精确 PNG 与 Render Tree，并在返回证据前附加布局诊断。
XML Render Mode 还会比对声明的语义与几何；符合资格的 Screenshot Reference 可以继续进行精确
RGBA 比对。直接调用 `render_preview` 和 `diagnose_layout` 仍只适用于另行 Allowlist 的固定 Target，
不能作为任意现有 Application Code 的证据。渲染现有 Application UI 属于后续需要单独隔离的能力。

## 升级或删除

`0.2.0` 没有自动且感知版本的升级命令。在新 Package 所属 Release 明确声明的框架 Profile 与
Project 的精确 ViewCompose Artifact 版本匹配前，不要直接替换。确需执行已经显式验证的迁移时，
先用当前已安装的 Executable 删除精确旧 Project 接入，再安装固定且兼容的 GitHub Release 并重新
执行 `init`。这样可以保留 Skill 与配置的冲突检测，但不能替代 Profile 匹配。

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

[固定 GitHub Release](https://github.com/ViewCompose/ViewCompose/releases/tag/ai-tooling-v0.2.0)
包含 Tarball、`manifest.json` 与 `SHA256SUMS`。发布 Workflow 会构建 Package 两次、检查精确
Inventory 与 Offline 安装/删除生命周期，并为全部 3 个 Asset 创建 GitHub Artifact Attestation。
如需独立校验 Provenance，请参考 GitHub 的
[Artifact Attestation 校验指南](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/verify-artifact-attestations)。

| 现象 | 处理方式 |
| --- | --- |
| 找不到 `viewcompose-agent` | 确认 Node 版本不低于 24.19，并且 npm 全局 Binary 目录已加入 `PATH`。 |
| `doctor` 报告 `repair-required` | 只有现有文件未修改时才重新运行 `init`；否则先检查报告中的冲突。 |
| `doctor` 报告 `host-prerequisites-required` | 安装 JDK 17 或 21 与 Android SDK Platform 36 后重新运行 `doctor`；无需另装 Gradle。 |
| 客户端未显示 MCP Server | 执行上面的客户端检查，按要求批准 Project 配置，再重启或 Reload 客户端。 |
| 编译或 Preview 报告 `VC-AI-PROJECT-ROOT-MISMATCH` | 从物理 Project 根目录运行 `init`，并确保 Agent Process 仍可访问该路径。 |
| 出现 Credential 请求 | 立即停止。ViewCompose 不需要模型 Provider Credential，也不会在 MCP 参数或 Project 配置中接收它。 |

贡献者内部说明见 [AI 工具契约](https://github.com/ViewCompose/ViewCompose/blob/main/tools/ai/README.md)
和当前 [AI 可验证工具计划](../project/plans/ai-verifiable-development-tooling.md)。
