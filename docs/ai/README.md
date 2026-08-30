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
  - GitHub Release @viewcompose/ai-tooling 0.1.0 at ai-tooling-v0.1.0
  - Node.js 24.19.0 or newer
  - MCP 2026-07-28 and 2025-11-25 over local stdio
  - Codex, Claude Code, and Cursor project profiles verified 2026-08-30
verification_commands:
  - npm --prefix tools/ai run verify:phase3-agent-clients
  - ./gradlew verifyAiDistribution
  - ./gradlew verifyAiToolingRelease
lifecycle: Update when a supported client format, Skill path, package release, tool set, or evidence boundary changes.
---

# AI Integration

ViewCompose ships a machine-readable API reference, 13 local MCP tools, and six Agent Skills as one
installable GitHub Release. A developer can connect Codex, Claude Code, or Cursor to a new or
existing Android project with two commands. No ViewCompose checkout, package build, provider key,
or manual MCP configuration edit is required for the standalone workflow.

The coding client still owns the model, credentials, conversation, and user-authorized source
changes. ViewCompose supplies deterministic framework facts, generation tools, and explicit
validation evidence; it never embeds or contacts a model provider.

## Install in two commands

Install the exact release from GitHub:

```bash
npm install --global --ignore-scripts \
  https://github.com/ViewCompose/ViewCompose/releases/download/ai-tooling-v0.1.0/viewcompose-ai-tooling-0.1.0.tgz
```

Then run this from the root of the Android project and choose one client:

```bash
viewcompose-agent init --client <codex|claude-code|cursor> \
  --project-root "$(pwd -P)"
```

`init` transactionally merges the `viewcompose` MCP entry and installs all six canonical Skills.
It preserves unrelated settings, is idempotent for exact installed content, and refuses invalid
JSON, relative or symbolic-link roots, and conflicting configuration or Skill bytes. A failure does
not leave a partially installed integration.

| Client | Project MCP configuration | Skill root |
| --- | --- | --- |
| Codex | `.codex/config.toml` | `.agents/skills` |
| Claude Code | `.mcp.json` | `.claude/skills` |
| Cursor | `.cursor/mcp.json` | `.agents/skills` |

Node.js 24.19.0 or newer is the only runtime prerequisite for this standalone path. A Node version
manager is recommended when the system-wide npm prefix is not writable; do not use `sudo` merely to
install the tooling.

## Confirm the installation

Run the same client choice from the project root:

```bash
viewcompose-agent doctor --client <codex|claude-code|cursor> \
  --project-root "$(pwd -P)"
```

`standalone-ready` means the MCP entry and every Skill match the installed release. The report also
separates `knowledgeAndGeneration` from `compilationPreviewAndLayout`, so an unavailable deeper
evidence lane is never reported as successful.

Complete the client-side connection check:

- **Codex:** run `codex mcp list`, then inspect `/mcp` and `/skills`; start with
  `$viewcompose-api-reference`. See the official [MCP](https://developers.openai.com/codex/mcp/)
  and [Agent Skills](https://learn.chatgpt.com/docs/build-skills) documentation.
- **Claude Code:** approve the project `.mcp.json` if prompted, run `claude mcp list` and
  `claude mcp get viewcompose`, then inspect `/mcp`; start with
  `/viewcompose-api-reference`. See the official [MCP](https://code.claude.com/docs/en/mcp) and
  [Skills](https://code.claude.com/docs/en/skills) documentation.
- **Cursor:** open **Cursor Settings > Tools & MCP**, confirm `viewcompose`, inspect
  **Agent > Available Tools**, and start with `/viewcompose-api-reference`. See the official
  [MCP](https://docs.cursor.com/context/model-context-protocol) and
  [Skills](https://cursor.com/docs/skills) documentation.

CI verifies generated client configuration, transactional lifecycle behavior, exact Skill bytes,
the installed package, and both MCP protocol handshakes. It does not automate or authenticate
proprietary client binaries, so the checks above remain visible user steps.

## What works without ViewCompose source

Standalone mode supports:

- exact API, component, sample, and ranked capability retrieval;
- static Kotlin validation and bounded read-only Android project analysis;
- Android XML-to-ViewCompose generation from pasted XML or explicitly scoped project resources;
- screenshot preprocessing, inference validation and typed resolution, and ViewCompose Kotlin
  generation;
- the six workflows for API lookup, screen creation, XML conversion, review, validation, and layout
  debugging, with each workflow retaining the evidence level it actually obtained.

Evidence levels are `knowledge`, `static`, `compiled`, `rendered`, and `compared`. A static result
does not prove compilation, and generated Kotlin does not prove rendering or visual parity.

Try this first request in the selected Agent:

> Use ViewCompose to create a Material 3 login screen. Retrieve the exact APIs and compiled samples
> before writing, run every validation lane currently available, and report the achieved evidence
> level and any unavailable deeper lane.

## Current compile, Preview, and layout boundary

In release `0.1.0`, compile-mode `validate_code`, Preview rendering, and rendered layout diagnosis
still execute against a matching ViewCompose source checkout. They require JDK 21, the repository's
pinned Android/Gradle lane, and the exact Knowledge Bundle revision. This is an enhancement mode,
not a prerequisite for installing or using the standalone tools.

If that checkout is available, replace a standalone integration with a source-bound one:

```bash
viewcompose-agent uninstall --client <codex|claude-code|cursor> \
  --project-root "$(pwd -P)"
viewcompose-agent init --client <codex|claude-code|cursor> \
  --project-root "$(pwd -P)" \
  --source-root <physical-absolute-viewcompose-source-root>
```

The next tooling boundary will run compilation, Preview, and layout diagnosis in an explicitly
authorized consumer project using released ViewCompose Maven artifacts. Until that contract and its
smoke projects pass, the release fails closed with `VC-AI-SOURCE-ROOT-MISMATCH` instead of silently
upgrading static evidence.

## Upgrade or remove

Before changing package versions, use the currently installed executable to remove the exact old
project integration. Then install the new pinned GitHub Release and run `init` again. This preserves
conflict detection across Skill and configuration changes.

Remove the current project integration:

```bash
viewcompose-agent uninstall --client <codex|claude-code|cursor> \
  --project-root "$(pwd -P)"
```

The command removes only the exact ViewCompose MCP entry and canonical Skill bytes; unrelated client
settings and files remain. If either managed surface was edited, removal fails for review rather
than deleting user content. Remove the global package separately:

```bash
npm uninstall --global @viewcompose/ai-tooling
```

## Integrity and troubleshooting

The [pinned GitHub Release](https://github.com/ViewCompose/ViewCompose/releases/tag/ai-tooling-v0.1.0)
contains the tarball, `manifest.json`, and `SHA256SUMS`. Its workflow builds the package twice,
checks the exact inventory and offline install/uninstall lifecycle, and creates GitHub Artifact
Attestations for all three assets. See GitHub's
[artifact attestation verification guide](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/verify-artifact-attestations)
for an optional independent provenance check.

| Symptom | Action |
| --- | --- |
| `viewcompose-agent` is not found | Confirm Node 24.19 or newer and that npm's global binary directory is on `PATH`. |
| `doctor` reports `repair-required` | Run `init` again only if the existing files are unchanged; otherwise review the reported conflict. |
| The client does not show the MCP server | Run the client-specific check above, approve project configuration when required, then restart or reload the client. |
| Compile or Preview reports `VC-AI-SOURCE-ROOT-MISMATCH` | Continue with honest static evidence, or explicitly install the source-bound enhancement mode. |
| A credential is requested | Stop. ViewCompose needs no model-provider credential and never accepts one in MCP arguments or project configuration. |

Contributor internals are documented in the [AI tooling contract](../../tools/ai/README.md) and the
active [AI-verifiable tooling plan](../project/plans/ai-verifiable-development-tooling.md).
