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
  - npm @viewcompose/ai-tooling 0.4.1 with GitHub Release ai-tooling-v0.4.1 provenance
  - Node.js 24.19.0 or newer
  - JDK 17 or 21 and Android SDK 36 for compiled, rendered, and compared evidence
  - MCP 2026-07-28 and 2025-11-25 over local stdio
  - Codex, Claude Code, and Cursor project profiles verified 2026-08-31
verification_commands:
  - npm --prefix tools/ai run verify:bootstrap-adoption
  - npm --prefix tools/ai run verify:phase3-agent-clients
  - ./gradlew verifyAiDistribution
  - ./gradlew verifyAiToolingRelease
lifecycle: Update when a supported client format, Skill path, package release, tool set, or evidence boundary changes.
---

# AI Integration

ViewCompose ships a machine-readable API reference, 13 local MCP tools, and six Agent Skills as one
exact-version npm package backed by an immutable GitHub Release. A developer can connect Codex,
Claude Code, or Cursor to a new or existing Android project with one command. No global install,
ViewCompose checkout, package build, provider key, or manual MCP configuration edit is required,
including for Kotlin compilation and generated-screen Preview evidence.

The coding client still owns the model, credentials, conversation, and user-authorized source
changes. ViewCompose supplies deterministic framework facts, generation tools, and explicit
validation evidence; it never embeds or contacts a model provider.

## Install in one command

Run exactly one of these commands from the physical root of the Android project:

```bash
npx --yes @viewcompose/ai-tooling@0.4.1 init --client codex
```

```bash
npx --yes @viewcompose/ai-tooling@0.4.1 init --client claude-code
```

```bash
npx --yes @viewcompose/ai-tooling@0.4.1 init --client cursor
```

`init` transactionally merges the `viewcompose` MCP entry and installs all six canonical Skills.
It resolves the physical current directory, detects the exact ViewCompose dependency vector before
writing, materializes the verified package into a content-addressed user cache, and points MCP only
at that durable copy—not npm's temporary npx directory. It then runs the same readiness checks as
`doctor`. Unrelated settings are preserved; exact re-entry is idempotent; invalid JSON, relative or
symbolic-link roots, incompatible framework versions, and conflicting configuration or Skill bytes
fail without leaving a partial integration. Automation may still pass
`--project-root <physical-absolute-path>` explicitly.

| Client | Project MCP configuration | Skill root |
| --- | --- | --- |
| Codex | `.codex/config.toml` | `.agents/skills` |
| Claude Code | `.mcp.json` | `.claude/skills` |
| Cursor | `.cursor/mcp.json` | `.agents/skills` |

Node.js 24.19.0 or newer is sufficient for reference, generation, static validation, and project
analysis. Compiled, rendered, and compared evidence additionally requires JDK 17 or 21 and Android
SDK platform 36. The release includes its own Gradle 9.3.1 wrapper and fixed build harness, so users
do not install Gradle or align their project's AGP/Kotlin versions. The bootstrap writes only to the
project integration surfaces and the operating system's user cache; never use `sudo` for it.

### Exact framework-version binding in `0.4.1`

`init` reads the project's independently versioned `com.viewcompose` coordinates without executing
project Gradle logic. It accepts exact literals, used entries from the default
`libs.versions.toml`, and dependency lock records. It selects only a released Knowledge Pack whose
Artifact-version profile matches every detected dependency, then writes the content-addressed
profile ID into the MCP environment before installing Skills. Retrieval, validation, compilation,
and generated Preview all load that same bundle.

A project without a ViewCompose dependency is a new-project case and selects the Release's newest
stable profile. Dynamic, conflicting, unsupported, or otherwise unresolved versions—including a
ViewCompose import without dependency identity—fail before any project write. The tool does not
silently change framework dependencies. The first `0.4.1` profile represents the current published
Artifact vector; an older version vector remains unchanged until a Release explicitly carries its
matching profile.

## Confirm the installation

`init` already returns the readiness result. To repeat the diagnosis later, run the same exact
package version and client choice from the project root:

```bash
npx --yes @viewcompose/ai-tooling@0.4.1 doctor --client <codex|claude-code|cursor>
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

CI verifies the real packaged bootstrap on fresh Linux, macOS, and Windows projects, including paths
with spaces and non-ASCII characters, all three clients, integrated diagnosis, idempotent re-entry,
npx-cache removal, durable MCP launch, exact Skill bytes, MCP handshake, and uninstall. It does not
automate or authenticate proprietary client binaries, so the checks above remain visible user steps.

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

Release `0.4.1` compiles generated Kotlin and renders generated screens against exact ViewCompose
artifacts from Maven Central. A packaged content-addressed harness owns Gradle 9.3.1, AGP 9.1.1,
Kotlin 2.2.10, Android 36, JVM target 11, and the allowlisted ViewCompose/Preview coordinates. The
consumer project root is a read-only authorization boundary: the tooling does not execute its
wrapper, settings, plugins, tasks, or build scripts and does not add files to the project.

The first deep-evidence request may download the pinned Gradle distribution and Maven dependencies.
It remains bounded by a five-minute execution window. Later requests use the integrity-checked cache
under the operating system's user cache directory, and compatible tooling upgrades retain the same
execution-cache namespace when the Knowledge, Harness, source, and lane fingerprints are unchanged.
Package installation is script-free. The first npx or deep-evidence request can require network
access for the exact package, Gradle distribution, or Maven dependencies; the durable verified
cache remains usable after npm removes its temporary npx files. Model-provider network access is
never required.

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
npx --yes @viewcompose/ai-tooling@0.4.1 upgrade --client <codex|claude-code|cursor>
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
dependencies. The exact-version bootstrap follows the verified managed MCP entry to diagnose,
upgrade, or remove the active side-by-side package.

Remove the current project integration:

```bash
npx --yes @viewcompose/ai-tooling@0.4.1 uninstall --client <codex|claude-code|cursor>
```

The command removes only the exact ViewCompose MCP entry and canonical Skill bytes; unrelated client
settings and files remain. If either managed surface was edited, removal fails for review rather
than deleting user content. No global package exists to remove; the content-addressed package cache
is retained for integrity and compatible reuse.

## Integrity and troubleshooting

The [pinned GitHub Release](https://github.com/ViewCompose/ViewCompose/releases/tag/ai-tooling-v0.4.1)
contains the tarball, `manifest.json`, and `SHA256SUMS`. Its workflow builds the package twice,
checks the exact inventory and offline install/uninstall lifecycle, and creates GitHub Artifact
Attestations for all three assets. See GitHub's
[artifact attestation verification guide](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/verify-artifact-attestations)
for an optional independent provenance check.

The npm version history also contains `0.4.0-bootstrap.0`. It is a one-time, provenance-bearing
prerelease used only to create the package identity required before npm could bind the stable
GitHub Trusted Publisher. npm assigned the first package version to `latest` as well as
`bootstrap`, and rejected authenticated attempts to remove that default intermediate tag. Stable
`0.4.0` replaced `latest`, but public acceptance found that npm could not infer a default executable
from its three named binaries. Release `0.4.1` adds the package-name `ai-tooling` alias for the same
transactional Agent entry point; after its public verification, the `bootstrap` tag is removed.
Both earlier versions remain immutable audit history, and `0.4.0-bootstrap.0` remains excluded from
ordinary stable semver ranges. The temporary npm token and GitHub secret were revoked before either
stable tag. Use the exact stable `@viewcompose/ai-tooling@0.4.1` selector documented above.

| Symptom | Action |
| --- | --- |
| `npx` cannot start the exact package | Confirm Node 24.19 or newer, npm registry access, and the literal `@viewcompose/ai-tooling@0.4.1` selector. Do not substitute `latest`. |
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
