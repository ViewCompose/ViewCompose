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
  - GitHub Release @viewcompose/ai-tooling 0.3.0 at ai-tooling-v0.3.0
  - Node.js 24.19.0 or newer
  - JDK 17 or 21 and Android SDK 36 for compiled, rendered, and compared evidence
  - MCP 2026-07-28 and 2025-11-25 over local stdio
  - Codex, Claude Code, and Cursor project profiles verified 2026-08-31
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
or manual MCP configuration edit is required, including for Kotlin compilation and generated-screen
Preview evidence.

The coding client still owns the model, credentials, conversation, and user-authorized source
changes. ViewCompose supplies deterministic framework facts, generation tools, and explicit
validation evidence; it never embeds or contacts a model provider.

## Install in two commands

Install the exact release from GitHub:

```bash
npm install --global --ignore-scripts \
  https://github.com/ViewCompose/ViewCompose/releases/download/ai-tooling-v0.3.0/viewcompose-ai-tooling-0.3.0.tgz
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

Node.js 24.19.0 or newer is sufficient for reference, generation, static validation, and project
analysis. Compiled, rendered, and compared evidence additionally requires JDK 17 or 21 and Android
SDK platform 36. The release includes its own Gradle 9.3.1 wrapper and fixed build harness, so users
do not install Gradle or align their project's AGP/Kotlin versions. A Node version manager is
recommended when the system-wide npm prefix is not writable; do not use `sudo` merely to install the
tooling.

### Exact framework-version binding in `0.3.0`

`init` reads the project's independently versioned `com.viewcompose` coordinates without executing
project Gradle logic. It accepts exact literals, used entries from the default
`libs.versions.toml`, and dependency lock records. It selects only a released Knowledge Pack whose
Artifact-version profile matches every detected dependency, then writes the content-addressed
profile ID into the MCP environment before installing Skills. Retrieval, validation, compilation,
and generated Preview all load that same bundle.

A project without a ViewCompose dependency is a new-project case and selects the Release's newest
stable profile. Dynamic, conflicting, unsupported, or otherwise unresolved versions—including a
ViewCompose import without dependency identity—fail before any project write. The tool does not
silently change framework dependencies. The first `0.3.0` profile represents the current published
Artifact vector; an older version vector remains unchanged until a Release explicitly carries its
matching profile.

## Confirm the installation

Run the same client choice from the project root:

```bash
viewcompose-agent doctor --client <codex|claude-code|cursor> \
  --project-root "$(pwd -P)"
```

`project-bound-ready` means the MCP entry and every Skill match the installed release, the physical
project root is bound, and the JDK/Android SDK prerequisites for deep evidence are available. The
report separates `knowledgeAndGeneration`, `compilationPreviewAndLayout`, and host prerequisites, so
an unavailable evidence lane is never reported as successful.

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

The installed project-bound mode supports:

- exact API, component, sample, and ranked capability retrieval;
- static and released-artifact Kotlin validation, plus bounded read-only Android project analysis;
- Android XML-to-ViewCompose generation from pasted XML or explicitly scoped project resources;
- compilation, Preview rendering, semantic/geometry comparison, and structured layout diagnosis for
  generated XML screens;
- screenshot preprocessing, inference validation and typed resolution, and ViewCompose Kotlin
  generation, plus compilation, Preview rendering, semantic comparison, and eligible exact-pixel
  comparison;
- the six workflows for API lookup, screen creation, XML conversion, review, validation, and layout
  debugging, with each workflow retaining the evidence level it actually obtained.

Evidence levels are `knowledge`, `static`, `compiled`, `rendered`, and `compared`. A static result
does not prove compilation, and generated Kotlin does not prove rendering or visual parity.

Try this first request in the selected Agent:

> Use ViewCompose to create a Material 3 login screen. Retrieve the exact APIs and compiled samples
> before writing, run every validation lane currently available, and report the achieved evidence
> level and any unavailable deeper lane.

## Deep evidence execution boundary

Release `0.3.0` compiles generated Kotlin and renders generated screens against exact ViewCompose
artifacts from Maven Central. A packaged content-addressed harness owns Gradle 9.3.1, AGP 9.1.1,
Kotlin 2.2.10, Android 36, JVM target 11, and the allowlisted ViewCompose/Preview coordinates. The
consumer project root is a read-only authorization boundary: the tooling does not execute its
wrapper, settings, plugins, tasks, or build scripts and does not add files to the project.

The first deep-evidence request may download the pinned Gradle distribution and Maven dependencies.
It remains bounded by a five-minute execution window. Later requests use the integrity-checked cache
under the operating system's user cache directory, and compatible tooling upgrades retain the same
execution-cache namespace when the Knowledge, Harness, source, and lane fingerprints are unchanged.
Package installation itself remains script-free and offline-capable; model-provider network access
is never required.

`validate_code` compile mode accepts bounded Kotlin snippets. XML and screenshot generation tools
own their generated source, compile it, render it, reopen the exact PNG and render tree, and attach
layout diagnosis before returning evidence. XML render mode additionally compares declared
semantics and geometry; an eligible screenshot reference can add exact RGBA comparison. Direct
`render_preview` and `diagnose_layout` remain limited to a separately allowlisted fixed target and
must not be presented as evidence for arbitrary existing application code. Existing application UI
rendering is a later, explicitly isolated capability.

## Upgrade or remove

Check, download, and migrate to the newest compatible tooling Release with one command:

```bash
viewcompose-agent upgrade --client <codex|claude-code|cursor> \
  --project-root "$(pwd -P)"
```

The command detects the project versions first and inspects only immutable `ai-tooling-v<semver>`
Releases. It skips newer Releases whose framework profiles do not match, verifies the selected
Release's exact three-Asset inventory, supported contract majors, sidecar Manifest, `SHA256SUMS`,
archive size, and SHA-256, and installs the Package into a content-addressed user-cache directory.
It never follows a global `latest` pointer.

The old Package remains available while the upgrader replaces only the exact managed MCP entry and
unchanged canonical Skill bytes. A private recovery journal rolls back an interrupted migration;
user-edited content or an unknown MCP owner stops before replacement. `no-compatible-update` is a
successful no-op: it does not change the installed integration or the project's framework
dependencies. The globally installed bootstrap command can diagnose, upgrade, or remove the active
side-by-side Package by following its verified managed MCP entry, so every later compatible upgrade
uses the same command.

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

The [pinned GitHub Release](https://github.com/ViewCompose/ViewCompose/releases/tag/ai-tooling-v0.3.0)
contains the tarball, `manifest.json`, and `SHA256SUMS`. Its workflow builds the package twice,
checks the exact inventory and offline install/uninstall lifecycle, and creates GitHub Artifact
Attestations for all three assets. See GitHub's
[artifact attestation verification guide](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/verify-artifact-attestations)
for an optional independent provenance check.

| Symptom | Action |
| --- | --- |
| `viewcompose-agent` is not found | Confirm Node 24.19 or newer and that npm's global binary directory is on `PATH`. |
| `doctor` reports `repair-required` | Run `init` again only if the existing files are unchanged; otherwise review the reported conflict. |
| `doctor` reports `host-prerequisites-required` | Install JDK 17 or 21 and Android SDK platform 36, then rerun `doctor`; Gradle itself is included. |
| `upgrade` returns `no-compatible-update` | Keep the current integration. No published tooling Release contains an exact framework profile for this project yet. Do not install a global-latest package as a workaround. |
| `upgrade` cannot resolve or finds conflicting ViewCompose versions | Replace dynamic or indirect declarations with exact coordinates, or add consistent dependency locks. The command intentionally leaves the current integration unchanged. |
| `upgrade` reports changed managed configuration or Skills | Review and preserve the user edits before retrying. The upgrader replaces only the exact bytes installed by ViewCompose. |
| The client does not show the MCP server | Run the client-specific check above, approve project configuration when required, then restart or reload the client. |
| Compile or Preview reports `VC-AI-PROJECT-ROOT-MISMATCH` | Run `init` from the physical project root and keep that project path available to the Agent process. |
| A credential is requested | Stop. ViewCompose needs no model-provider credential and never accepts one in MCP arguments or project configuration. |

Contributor internals are documented in the [AI tooling contract](../../tools/ai/README.md) and the
active [AI-verifiable tooling plan](../project/plans/ai-verifiable-development-tooling.md).
