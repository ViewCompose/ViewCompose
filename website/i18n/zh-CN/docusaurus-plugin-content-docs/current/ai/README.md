---
title: AI 接入
slug: /ai
translation_source: ai/README.md
translation_source_hash: 08b48f1ad08c4f8c5088dab3d20e6a5da01fa203357c62faaff1ec7a74295b93
translation_status: current
---

# AI 接入

ViewCompose 把机器可读 API Reference、13 个本地 MCP 工具和 6 个 Agent Skill 作为一个
由不可变 GitHub Release 支持的精确版本 npm Package 发布。开发者只需一条命令，就能让 Codex、
Claude Code 或 Cursor 接入全新或已有 Android Project。即使要执行 Kotlin 编译和生成页面的
Preview 证据，也不要求全局安装、ViewCompose Checkout、本地构建 Package、Provider Key 或
手动编辑 MCP 配置。

Coding Client 仍然负责模型、Credential、对话和用户授权的源码修改。ViewCompose 只提供确定性的
框架事实、生成工具与明确的验证证据，既不内置也不连接模型 Provider。

## 一条命令完成安装

在 Android Project 的物理根目录执行以下任意一条命令：

```bash
npx --yes @viewcompose/ai-tooling@0.4.0 init --client codex
```

```bash
npx --yes @viewcompose/ai-tooling@0.4.0 init --client claude-code
```

```bash
npx --yes @viewcompose/ai-tooling@0.4.0 init --client cursor
```

`init` 会解析当前物理目录，在任何写入前检测精确 ViewCompose Dependency Vector，把已验证 Package
物化到 Content-addressed 用户 Cache，并让 MCP 只指向该持久副本而不是 npm 的临时 npx 目录；随后
执行与 `doctor` 相同的 Readiness 检查。它会保留无关设置，对精确内容重复执行时保持幂等；遇到无效
JSON、相对或符号链接路径、不兼容框架版本，以及配置或 Skill 冲突时，不会留下部分接入状态。自动化
仍可显式传入 `--project-root <physical-absolute-path>`。

| 客户端 | Project MCP 配置 | Skill 根目录 |
| --- | --- | --- |
| Codex | `.codex/config.toml` | `.agents/skills` |
| Claude Code | `.mcp.json` | `.claude/skills` |
| Cursor | `.cursor/mcp.json` | `.agents/skills` |

API 查询、生成、静态验证和 Project 分析只要求 Node.js 24.19.0 或更高版本。若要取得编译、
渲染和比对证据，还需要 JDK 17 或 21，以及 Android SDK Platform 36。Release 已携带 Gradle
9.3.1 Wrapper 与固定 Build Harness，用户无需安装 Gradle，也无需让现有 Project 的 AGP/Kotlin
版本与工具链对齐。Bootstrap 只写入 Project 接入面与操作系统用户 Cache；不要为此使用 `sudo`。

### `0.4.0` 的精确框架版本绑定

`init` 不会执行 Project Gradle Logic，而是读取 Project 中独立版本化的 `com.viewcompose`
Coordinate。它接受精确 Literal、默认 `libs.versions.toml` 中实际使用的 Entry，以及 Dependency
Lock Record；只选择 Artifact-version Profile 与所有已检测 Dependency 匹配的 Released Knowledge
Pack；并在安装 Skill 前把 Content-addressed Profile ID 写入 MCP Environment。后续检索、验证、
编译与 Generated Preview 都加载同一 Bundle。

不含 ViewCompose Dependency 的 Project 属于新项目，会选择该 Release 最新稳定 Profile。Dynamic、
互相冲突、不支持或其他无法解析的版本——包括存在 ViewCompose Import 却没有 Dependency Identity——
都会在任何 Project 写入前失败。工具不会静默修改框架 Dependency。首个 `0.4.0` Profile 表示当前
已发布 Artifact Vector；旧版本 Vector 只有在某个 Release 明确携带匹配 Profile 后才会升级。

## 确认安装状态

`init` 已经返回 Readiness 结果。以后需要重复诊断时，在 Project 根目录使用相同精确 Package
版本与客户端选项：

```bash
npx --yes @viewcompose/ai-tooling@0.4.0 doctor --client <codex|claude-code|cursor>
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

CI 会在全新 Linux、macOS 和 Windows Project 上验证真实 Package Bootstrap，覆盖带空格和非 ASCII
字符的路径、3 个客户端、集成诊断、幂等重复执行、清理 npx Cache 后的持久 MCP 启动、精确 Skill
字节、MCP 握手和卸载。它不会自动控制或登录专有客户端 Binary，因此上述检查仍是明确的用户步骤。

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

Release `0.4.0` 使用 Maven Central 中的精确 ViewCompose Artifact 编译生成 Kotlin，并渲染生成
页面。Package 内的 Content-addressed Harness 固定使用 Gradle 9.3.1、AGP 9.1.1、Kotlin
2.2.10、Android 36、JVM Target 11 和 Allowlist 中的 ViewCompose/Preview Coordinate。Consumer
Project 根目录只是只读授权边界：工具不会执行它的 Wrapper、Settings、Plugin、Task 或 Build
Script，也不会向 Project 写入文件。

第一次请求深层证据时，工具可能下载固定 Gradle Distribution 与 Maven Dependency；后续请求会
受到 5 分钟执行窗口的约束。后续请求会使用操作系统用户缓存目录中的完整性校验 Cache；当
Knowledge、Harness、源码与 Lane Fingerprint 不变时，兼容的工具升级也会保留同一 Execution Cache
Namespace。Package 安装本身仍然无脚本；首次 npx 或深层证据请求可能需要联网取得精确 Package、
Gradle Distribution 或 Maven Dependency，但 npm 清理临时 npx 文件后，持久且已验证的 Cache 仍可
继续使用。整个流程不需要模型 Provider 的网络访问。

`validate_code` 的 Compile Mode 接收有界 Kotlin Snippet。XML 与 Screenshot 生成工具只执行自己
确定性生成的源码，依次编译、渲染、重新打开精确 PNG 与 Render Tree，并在返回证据前附加布局诊断。
XML Render Mode 还会比对声明的语义与几何；符合资格的 Screenshot Reference 可以继续进行精确
RGBA 比对。直接调用 `render_preview` 和 `diagnose_layout` 仍只适用于另行 Allowlist 的固定 Target，
不能作为任意现有 Application Code 的证据。渲染现有 Application UI 属于后续需要单独隔离的能力。

## 升级或删除

用一条命令检查、下载并迁移到最新兼容的工具 Release：

```bash
npx --yes @viewcompose/ai-tooling@0.4.0 upgrade --client <codex|claude-code|cursor>
```

该命令先检测 Project 版本，并且只检查不可变的 `ai-tooling-v<semver>` Release。它会跳过框架
Profile 不匹配的较新 Release；校验所选 Release 的精确 3 Asset Inventory、受支持 Contract Major、
Sidecar Manifest、`SHA256SUMS`、Archive Size 与 SHA-256；再把 Package 安装到 Content-addressed
用户 Cache 目录。它绝不会跟随全局 `latest` Pointer。

旧 Package 会在升级器替换内容期间保持可用；事务只迁移精确受管理的 MCP Entry 与未修改的规范
Skill 字节。私有 Recovery Journal 会回滚被中断的迁移；存在用户编辑内容或未知 MCP Owner 时，
替换前就会停止。`no-compatible-update` 是成功的 No-op，不会修改现有接入或 Project 的框架
Dependency。精确版本 Bootstrap 会沿着已验证的受管理 MCP Entry 找到当前 Side-by-side Package，
从而诊断、升级或删除当前接入。

删除当前 Project 接入：

```bash
npx --yes @viewcompose/ai-tooling@0.4.0 uninstall --client <codex|claude-code|cursor>
```

该命令只删除精确的 ViewCompose MCP Entry 与规范 Skill 字节，无关客户端设置和文件会保留。
如果受管理内容被编辑，删除会停止并要求 Review，而不是删除用户内容。没有需要另行删除的全局
Package；Content-addressed Package Cache 会保留，用于完整性校验与兼容复用。

## 完整性与故障排查

[固定 GitHub Release](https://github.com/ViewCompose/ViewCompose/releases/tag/ai-tooling-v0.4.0)
包含 Tarball、`manifest.json` 与 `SHA256SUMS`。发布 Workflow 会构建 Package 两次、检查精确
Inventory 与 Offline 安装/删除生命周期，并为全部 3 个 Asset 创建 GitHub Artifact Attestation。
如需独立校验 Provenance，请参考 GitHub 的
[Artifact Attestation 校验指南](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/verify-artifact-attestations)。

npm 版本历史中还包含 `0.4.0-bootstrap.0`。它是一次性的、带 Provenance 的预发布 Package，
只用于先建立 npm Package Identity，以便绑定稳定版 GitHub Trusted Publisher。npm 在首次
创建 Package 时同时把该版本分配给 `latest` 与 `bootstrap`，并拒绝了经过身份验证的默认标签
删除请求；稳定版发布会把 `latest` 替换为精确的 `0.4.0`，随后删除 `bootstrap`。普通稳定
SemVer Range 不会选中该预发布版本，它也不是受支持的消费者入口。其临时 npm Token 与 GitHub
Secret 已在创建稳定标签前撤销。不要安装或调用该预发布版本，请使用上文记录的精确稳定
Selector `@viewcompose/ai-tooling@0.4.0`。

| 现象 | 处理方式 |
| --- | --- |
| `npx` 无法启动精确 Package | 确认 Node 版本不低于 24.19、可以访问 npm Registry，并使用字面量 `@viewcompose/ai-tooling@0.4.0`；不要替换为 `latest`。 |
| `doctor` 报告 `repair-required` | 只有现有文件未修改时才重新运行 `init`；否则先检查报告中的冲突。 |
| `doctor` 报告 `host-prerequisites-required` | 安装 JDK 17 或 21 与 Android SDK Platform 36 后重新运行 `doctor`；无需另装 Gradle。 |
| `upgrade` 返回 `no-compatible-update` | 保持当前接入不变。当前还没有已发布的工具 Release 为该 Project 提供精确框架 Profile；不要改为安装全局最新 Package。 |
| `upgrade` 无法解析 ViewCompose 版本或发现版本冲突 | 把 Dynamic 或间接声明替换为精确 Coordinate，或增加一致的 Dependency Lock。该命令会有意保持当前接入不变。 |
| `upgrade` 报告受管理配置或 Skill 已被修改 | 重试前先检查并保留用户改动。Upgrader 只替换 ViewCompose 曾安装的精确字节。 |
| 客户端未显示 MCP Server | 执行上面的客户端检查，按要求批准 Project 配置，再重启或 Reload 客户端。 |
| 编译或 Preview 报告 `VC-AI-PROJECT-ROOT-MISMATCH` | 从物理 Project 根目录运行 `init`，并确保 Agent Process 仍可访问该路径。 |
| 出现 Credential 请求 | 立即停止。ViewCompose 不需要模型 Provider Credential，也不会在 MCP 参数或 Project 配置中接收它。 |

贡献者内部说明见 [AI 工具契约](https://github.com/ViewCompose/ViewCompose/blob/main/tools/ai/README.md)
和当前 [AI 可验证工具计划](../project/plans/ai-verifiable-development-tooling.md)。
