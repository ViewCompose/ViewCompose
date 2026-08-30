---
title: AI Integration
slug: /ai
schema_version: 2
document_id: tooling.ai-integration
doc_type: tooling
owner:
  kind: project
  id: ai-development-tooling
version_lane: next
capability_ids: []
artifact_ids: []
sample_ids: []
supported_versions:
  - Current-source @viewcompose/ai-tooling 0.1.0 local distribution
  - Node.js 24.19.0 or newer; JDK 21 or newer for compile and Preview evidence
  - MCP 2026-07-28 and 2025-11-25 over local stdio
  - Codex, Claude Code, and Cursor project profiles verified 2026-08-30
verification_commands:
  - npm --prefix tools/ai run verify:phase3-agent-clients
  - ./gradlew verifyAiAgentClients
  - ./gradlew verifyAiDistribution
lifecycle: Update when a supported client format, Skill path, package contract, tool set, or evidence boundary changes.
---

# AI Integration

ViewCompose gives AI agents a machine-readable reference, 13 local MCP tools, six standard Agent
Skills, and executable validation so they can retrieve real APIs and verify generated code instead
of guessing symbols. The coding client owns the model, credentials, conversation, and requested
source edits; ViewCompose owns deterministic facts, local tools, evidence, and safety boundaries.

:::warning Current-source tooling

`@viewcompose/ai-tooling` is currently built locally from a ViewCompose checkout and is not
published to an npm registry.

:::

## Supported clients and evidence

| Client | Project MCP configuration | Skill root |
| --- | --- | --- |
| Codex | `.codex/config.toml` | `.agents/skills` |
| Claude Code | `.mcp.json` | `.claude/skills` |
| Cursor | `.cursor/mcp.json` | `.agents/skills` |

CI verifies deterministic configuration, exact installed Skill bytes, offline package lifecycle,
and both MCP protocol handshakes. It does not authenticate proprietary client binaries, so final
client connection checks remain explicit below. Other compatible clients may adapt the stdio
server manually but are not yet in the supported matrix.

Evidence levels are `knowledge`, `static`, `compiled`, `rendered`, and `compared`. A found symbol is
not compiled code; compiled code is not a rendered screen; rendering is not visual parity. Retain
only the highest level actually returned and its limitations.

## 1. Build and install the local package

Use physical absolute paths. JDK 21 and the repository Android SDK are needed only for compile or
Preview evidence.

```bash
export VIEWCOMPOSE_SOURCE_ROOT="$(pwd -P)"
export VIEWCOMPOSE_AI_PREFIX="$VIEWCOMPOSE_SOURCE_ROOT/tools/ai/build/agent-install"

npm --prefix tools/ai run package:distribution
npm install --global --prefix "$VIEWCOMPOSE_AI_PREFIX" --offline --ignore-scripts \
  "$VIEWCOMPOSE_SOURCE_ROOT/tools/ai/build/distribution/viewcompose-ai-tooling-0.1.0.tgz"
export PATH="$VIEWCOMPOSE_AI_PREFIX/bin:$PATH"
```

The prefix is an ignored tool-owned build directory. The `PATH` update affects the current shell.

## 2. Install the project Skills

From the Android consumer project:

```bash
export VIEWCOMPOSE_CONSUMER_ROOT="$(pwd -P)"

# Choose exactly one client.
viewcompose-agent install-skills --client <codex|claude-code|cursor> \
  --project-root "$VIEWCOMPOSE_CONSUMER_ROOT"
```

The installer copies all six canonical `SKILL.md` files. Exact reinstalls are idempotent; unknown
clients, relative roots, symbolic links, and different existing bytes fail without overwrite.

## 3. Add the MCP configuration

Generate but do not automatically write the selected project fragment:

```bash
viewcompose-agent config --client <codex|claude-code|cursor> \
  --source-root "$VIEWCOMPOSE_SOURCE_ROOT"
```

Merge the output into the client's configuration path in the table, preserving unrelated settings.

### Codex

Run `codex mcp list`, then use `/mcp` and `/skills` in the consumer project. Invoke
`$viewcompose-api-reference` for a read-only first check. See the official
[MCP](https://developers.openai.com/codex/mcp/) and
[Agent Skills](https://learn.chatgpt.com/docs/build-skills) documentation.

### Claude Code

Approve the project `.mcp.json` when prompted, run `claude mcp list` and
`claude mcp get viewcompose`, then use `/mcp` and `/viewcompose-api-reference`. See the official
[MCP](https://code.claude.com/docs/en/mcp) and
[Agent Skills](https://code.claude.com/docs/en/skills) documentation.

### Cursor

Open **Cursor Settings > Tools & MCP**, confirm `viewcompose`, check **Agent > Available Tools**,
and invoke `/viewcompose-api-reference`. See the official
[MCP](https://docs.cursor.com/context/model-context-protocol) and
[Agent Skills](https://cursor.com/docs/skills) documentation.

## 4. Run a verified first request

> Use ViewCompose to create a Material 3 login screen. Retrieve the exact APIs and compiled samples
> first, validate the generated Kotlin by compilation, and render only if the target is covered.

The agent should retrieve before writing, run `validate_code`, and report the actual evidence. The
six Skills also cover screen creation, bounded XML conversion, read-only review, layout debugging,
and validation without granting extra project-write authority.

## Troubleshooting and removal

| Symptom | Check |
| --- | --- |
| MCP or Skill is missing | Regenerate and merge the project profile, check the table's Skill root, then restart or reload the client. |
| Installation reports a conflict | Review and explicitly reconcile the existing file; the installer never overwrites different bytes. |
| Compile or Preview fails | Use JDK 21 and keep `VIEWCOMPOSE_SOURCE_ROOT` on the physical checkout; `knowledge` or `static` evidence does not prove compilation. |
| A credential is requested | ViewCompose needs no provider credential. Never place one in MCP arguments or project configuration. |

Remove the local package with:

```bash
npm uninstall --global --prefix "$VIEWCOMPOSE_AI_PREFIX" --offline --ignore-scripts \
  @viewcompose/ai-tooling
```

This intentionally leaves project configuration and Skill folders for manual review. Contributor
internals are documented in the [local AI tooling contract](../../tools/ai/README.md) and the active
[AI-verifiable tooling plan](../project/plans/ai-verifiable-development-tooling.md).
