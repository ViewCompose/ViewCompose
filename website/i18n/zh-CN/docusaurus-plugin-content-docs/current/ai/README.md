---
title: AI 接入
slug: /ai
translation_source: ai/README.md
translation_source_hash: c382cb4c23ebaf34ebe31ff15314071eddc43f000830277cd728888ffc3f8d79
translation_status: current
---

# AI 接入

ViewCompose 为 AI Agent 提供机器可读 Reference、13 个本地 MCP 工具、6 个标准 Agent Skill
与可执行验证，使其能够查询真实 API 并验证生成代码，而不是猜测符号。Coding Client 负责模型、
Credential、对话和用户要求的源码修改；ViewCompose 负责确定性事实、本地工具、证据与安全边界。

:::warning 当前源码工具

`@viewcompose/ai-tooling` 目前从 ViewCompose Checkout 本地构建，尚未发布到 npm Registry。

:::

## 支持的客户端与证据

| 客户端 | 项目 MCP 配置 | Skill 根目录 |
| --- | --- | --- |
| Codex | `.codex/config.toml` | `.agents/skills` |
| Claude Code | `.mcp.json` | `.claude/skills` |
| Cursor | `.cursor/mcp.json` | `.agents/skills` |

CI 会验证确定性配置、安装后精确 Skill 字节、离线 Package 生命周期和两个 MCP Protocol 握手。
它不会登录专有客户端 Binary，因此下面仍保留客户端侧最终连接检查。其他兼容客户端可以手动适配
stdio Server，但暂不属于受支持矩阵。

证据等级是 `knowledge`、`static`、`compiled`、`rendered` 和 `compared`。找到符号不等于代码
已编译；编译通过不等于页面已渲染；渲染成功也不等于视觉一致。只保留实际返回的最高等级及其限制。

## 1. 构建并安装本地 Package

使用物理绝对路径。只有编译或 Preview 证据需要 JDK 21 与仓库要求的 Android SDK。

```bash
export VIEWCOMPOSE_SOURCE_ROOT="$(pwd -P)"
export VIEWCOMPOSE_AI_PREFIX="$VIEWCOMPOSE_SOURCE_ROOT/tools/ai/build/agent-install"

npm --prefix tools/ai run package:distribution
npm install --global --prefix "$VIEWCOMPOSE_AI_PREFIX" --offline --ignore-scripts \
  "$VIEWCOMPOSE_SOURCE_ROOT/tools/ai/build/distribution/viewcompose-ai-tooling-0.1.0.tgz"
export PATH="$VIEWCOMPOSE_AI_PREFIX/bin:$PATH"
```

Prefix 位于已忽略的工具自有 Build 目录；`PATH` 修改只影响当前 Shell。

## 2. 安装项目 Skill

在 Android Consumer Project 中运行：

```bash
export VIEWCOMPOSE_CONSUMER_ROOT="$(pwd -P)"

# 只选择一个客户端。
viewcompose-agent install-skills --client <codex|claude-code|cursor> \
  --project-root "$VIEWCOMPOSE_CONSUMER_ROOT"
```

安装器复制全部 6 个规范 `SKILL.md`。精确重复安装是幂等的；未知客户端、相对路径、符号链接或
不同的现有字节都会失败，绝不覆盖。

## 3. 添加 MCP 配置

生成但不自动写入所选项目配置片段：

```bash
viewcompose-agent config --client <codex|claude-code|cursor> \
  --source-root "$VIEWCOMPOSE_SOURCE_ROOT"
```

把输出合并到表格中的客户端配置路径，同时保留无关设置。

### Codex 客户端

运行 `codex mcp list`，再在 Consumer Project 中使用 `/mcp` 和 `/skills`。调用
`$viewcompose-api-reference` 做首次只读检查。官方资料：
[MCP](https://developers.openai.com/codex/mcp/)与
[Agent Skills](https://learn.chatgpt.com/docs/build-skills)。

### Claude Code 客户端

出现提示时批准项目 `.mcp.json`，运行 `claude mcp list` 和 `claude mcp get viewcompose`，
再使用 `/mcp` 与 `/viewcompose-api-reference`。官方资料：
[MCP](https://code.claude.com/docs/en/mcp)与
[Agent Skills](https://code.claude.com/docs/en/skills)。

### Cursor 客户端

打开 **Cursor Settings > Tools & MCP**，确认 `viewcompose`，检查 **Agent > Available Tools**，
再调用 `/viewcompose-api-reference`。官方资料：
[MCP](https://docs.cursor.com/context/model-context-protocol)与
[Agent Skills](https://cursor.com/docs/skills)。

## 4. 执行首次可验证请求

> 使用 ViewCompose 创建一个 Material 3 登录页面。先检索准确 API 和已编译 Sample，通过编译验证
> 生成的 Kotlin，并且只在目标已覆盖时进行渲染。

Agent 应先检索再编写，运行 `validate_code` 并报告实际证据。6 个 Skill 还覆盖页面创建、有界 XML
转换、只读 Review、布局调试与验证，不会额外授予项目写入权限。

## 故障排查与删除

| 现象 | 检查项 |
| --- | --- |
| 找不到 MCP 或 Skill | 重新生成并合并项目 Profile，检查表格中的 Skill Root，再重启或 Reload 客户端。 |
| 安装报告冲突 | 检查并显式协调现有文件；安装器绝不会覆盖不同字节。 |
| 编译或 Preview 失败 | 使用 JDK 21，并让 `VIEWCOMPOSE_SOURCE_ROOT` 指向物理 Checkout；`knowledge` 或 `static` 证据不证明编译通过。 |
| 出现 Credential 请求 | ViewCompose 不需要 Provider Credential，绝不要把它放入 MCP 参数或项目配置。 |

删除本地 Package：

```bash
npm uninstall --global --prefix "$VIEWCOMPOSE_AI_PREFIX" --offline --ignore-scripts \
  @viewcompose/ai-tooling
```

项目配置与 Skill 目录会刻意保留，以便人工检查。贡献者内部说明见
[本地 AI 工具契约](https://github.com/ViewCompose/ViewCompose/blob/main/tools/ai/README.md)和当前
[AI 可验证工具计划](../project/plans/ai-verifiable-development-tooling.md)。
