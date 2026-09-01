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
  - npm @viewcompose/ai-tooling 0.7.0 release candidate for ai-tooling-v0.7.0
  - Node.js 24.19.0 or newer
  - JDK 17 or 21 and Android SDK 36 for compiled, rendered, and compared evidence
  - MCP 2026-07-28 and 2025-11-25 over local stdio
  - Codex, Claude Code, and Cursor project profiles verified 2026-09-01
verification_commands:
  - npm --prefix tools/ai run verify:bootstrap-adoption
  - npm --prefix tools/ai run verify:project-analysis
  - npm --prefix tools/ai run verify:phase3-agent-clients
  - ./gradlew verifyAiProjectAnalysis
  - ./gradlew verifyAiDistribution
  - ./gradlew verifyAiToolingRelease
lifecycle: Update when a supported client format, Skill path, package release, tool set, or evidence boundary changes.
---

# AI Integration

ViewCompose ships a machine-readable API reference, 15 local MCP tools, and eight Agent Skills as one
exact-version npm package backed by an immutable GitHub Release. A developer can connect Codex,
Claude Code, or Cursor to a new or existing Android project with one command. No global install,
ViewCompose checkout, package build, provider key, or manual MCP configuration edit is required,
including for Kotlin compilation and generated-screen Preview evidence.

The coding client still owns the model, credentials, conversation, and user-authorized source
changes. ViewCompose supplies deterministic framework facts, generation tools, and explicit
validation evidence; it never embeds or contacts a model provider.

## Install in one command

Release candidate `0.7.0` is frozen for publication. Use its exact selector after the immutable
GitHub Release becomes public so the installed tools, Skills, Knowledge Pack,
and framework profile remain one verified version; do not replace it with a floating selector.

Run exactly one of these commands from the physical root of the Android project:

```bash
npx --yes @viewcompose/ai-tooling@0.7.0 init --client codex
```

```bash
npx --yes @viewcompose/ai-tooling@0.7.0 init --client claude-code
```

```bash
npx --yes @viewcompose/ai-tooling@0.7.0 init --client cursor
```

`init` transactionally merges the `viewcompose` MCP entry and installs all eight canonical Skills.
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

### Exact framework-version binding in `0.7.0`

`init` reads the project's independently versioned `com.viewcompose` coordinates without executing
project Gradle logic. It accepts exact literals, used entries from the default
`libs.versions.toml`, and dependency lock records. It selects only a released Knowledge Pack whose
Artifact-version profile matches every detected dependency, then writes the content-addressed
profile ID into the MCP environment before installing Skills. Retrieval, validation, compilation,
and generated Preview all load that same bundle.

A project without a ViewCompose dependency is a new-project case and selects the Release's newest
stable profile. Dynamic, conflicting, unsupported, or otherwise unresolved versions—including a
ViewCompose import without dependency identity—fail before any project write. The tool does not
silently change framework dependencies. The `0.7.0` profile represents the current published
Artifact vector; an older version vector remains unchanged until a Release explicitly carries its
matching profile.

## Confirm the installation

`init` already returns the readiness result. To repeat the diagnosis later, run the same exact
package version and client choice from the project root:

```bash
npx --yes @viewcompose/ai-tooling@0.7.0 doctor --client <codex|claude-code|cursor>
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
- offline Figma export inspection, deterministic ViewCompose Kotlin and redistributable PNG
  generation, compilation, Preview rendering, and bounded structure/semantics/geometry/asset
  comparison;
- attended screenshot repair preparation for one exact generated Kotlin literal, with a separate
  terminal-only apply/recovery/rollback host;
- the eight workflows for API lookup, screen creation, XML conversion, Figma import, screenshot
  repair, review, validation, and layout debugging, with each workflow retaining the evidence
  level it actually obtained.

Evidence levels are `knowledge`, `static`, `compiled`, `rendered`, and `compared`. A static result
does not prove compilation, and generated Kotlin does not prove rendering or visual parity.

## Attended screenshot repair

Release candidate `0.7.0` adds `prepare_screenshot_repair`, the
`viewcompose-repair-screenshot` Skill, and the separate `viewcompose-repair` executable. The Agent
uses MCP to reproduce baseline/current six-gate evidence, derive one strictly improving rollback
proposal, and store one inert content-addressed request. MCP does not write project source.

The released edit subset changes exactly one generated Kotlin literal `text` or `hint` property.
It rejects whole-file replacement, raw patches, imports, declarations, callbacks, arbitrary source,
multiple files, profile or root drift, symlinks, hard links, and any preimage/span/candidate/diff
mismatch. Before preparing a request, all safety, compilation, Preview, semantic, structural, and
eligible exact-pixel gates must pass for the proposed candidate.

Ask the Agent to use `$viewcompose-repair-screenshot`. It will return a request fingerprint and the
review command:

```bash
viewcompose-repair show <request-fingerprint> --pretty
```

After reviewing the complete diff and evidence, apply it from the same physical project root:

```bash
viewcompose-repair apply <request-fingerprint> --pretty
```

The command requires the controlling terminal to type the exact displayed suffix. There is no
`--yes`, stdin, environment-variable, token, reusable grant, or MCP route for that confirmation.
Recovery bytes and a hash-chained journal live in an owner-only operating-system user-state
directory outside the project. The host performs a no-follow beneath-root atomic replacement,
durably synchronizes it, rereads the committed bytes, and reruns the five post-apply evidence
categories.

If the process is interrupted, run `viewcompose-repair recover <request-fingerprint> --pretty`.
Recovery never guesses or silently rolls back: it reports unchanged preimage, reconciles the exact
candidate, or stops on conflict. A failed post-apply validation leaves the candidate in place so a
later user edit is not overwritten. Only an explicit
`viewcompose-repair rollback <request-fingerprint> --pretty` with a second terminal confirmation
may restore the recovery copy, and it refuses any file that no longer exactly equals the candidate.

The attended host currently requires a POSIX controlling terminal, JDK 17 or 21, and a filesystem
where secure directory-handle-relative atomic replacement and durable directory synchronization
can be proven. Unsupported hosts fail without a source write. It does not commit, push, open a pull
request, repair arbitrary Kotlin, or defend against an actor that already controls the user's OS
account and terminal.

## Offline Figma to ViewCompose

Release `0.6.0` adds the public `convert_figma_to_viewcompose` tool and
`viewcompose-import-figma` Skill. The tool accepts one self-contained
`viewcompose-figma-export/1` JSON document supplied by the caller. ViewCompose does not log into
Figma, accept an access token, fetch a URL, run plugin data, or contact a model/provider. The first
release deliberately does not include a Figma plugin, Figma REST client, or `.fig` parser: an
organization that produces this normalized export must use a separately reviewed offline adapter
and give the resulting JSON to the Agent.

After installing the exact package, attach or otherwise make that JSON available inside the
project session and ask the Agent:

> Use `$viewcompose-import-figma` to inspect this offline Figma export. Continue to generation only
> if the complete mapping audit allows it, verify the generated result when the host is ready, and
> report every unsupported property and evidence limitation before proposing project writes.

The Skill follows one fail-closed sequence:

1. `inspect` validates strict JSON, declared privacy/redaction, selected graph completeness,
   component and variant lineage, token aliases, fonts, asset ownership and redistribution,
   canonical base64, media signatures, byte counts, SHA-256 identities, and safe relative paths.
   The result includes Design IR v2, a decision for every declared render fact, complete fact and
   asset coverage, and no embedded asset bytes.
2. `generate` is available only when the audit has no error-level unsupported decision. It returns
   content-addressed virtual Kotlin and resource files; it does not write the consumer project.
3. `verify` compiles the generated Kotlin against the exact released Maven profile, renders the
   fixed Preview, and compares the accepted render tree with the mapped Design IR. Project
   initialization must already report the deep-evidence lane as ready.

The first generation subset supports exactly one selected root; non-wrapping Row, Column, and Box
structure; Text using declared generic system fonts; solid colors; and explicitly accessible,
redistributable PNG images. Multiple roots, custom fonts, wrapping, effects, prototype
interactions, active content, URLs, undeclared facts or assets, unsafe paths, vectors, and JPEG/WebP
emission remain blocked or inspect-only.

Verification reports categories independently. Structure, semantics, geometry, and assets can pass
in `0.6.0`; style remains `incomplete`, while pixel and perceptual categories are
`not-applicable` because the import does not accept a trusted Figma reference render. Therefore a
successful `compared` result is bounded render-tree evidence, not Figma visual parity. Integrating
the returned virtual files remains a user-authorized Agent action, and conflicts must be reviewed
instead of overwritten.

## Versioned project analysis

Release `0.5.0` upgrades the existing `analyze_project` MCP tool without adding a competing alias.
The tool remains bounded and read-only: it does not execute the project wrapper, Gradle settings,
plugins, tasks, compiler extensions, application code, or source writes. Its existing inventory and
diagnostic fields remain available, while `data.analysis` adds the exact framework profile, scan
coverage, applicable rule catalog, immutable corpus-quality snapshot, typed findings, suppression
audit, and unsupported-syntax records.

The first public catalog contains only five high-confidence rules:

- unknown imports under the reserved `com.viewcompose` namespace;
- unknown `com.viewcompose` Artifact coordinates;
- an exact governed import without its owning Artifact declaration in the scanned scope;
- a literal ViewCompose version that differs from the selected exact framework profile; and
- an exact, unaliased ViewCompose `Image` call without an explicit `contentDescription` decision.

Every enabled rule has a stable ID and version, source span, mechanism, evidence, safe suggestion,
framework applicability, categorical `high` confidence, and independent precision/recall
denominators. The frozen corpus currently measures 25 positive and 50 eligible negative
opportunities per rule: 125/125 positives were detected, 0/250 eligible negatives produced a false
finding, and 25/25 deliberately unsupported opportunities remained explicit. The accepted result is
100% observed precision and recall inside the documented lexical boundary; it is not a claim about
arbitrary Kotlin semantics.

Aliases, star imports, custom wrappers, dynamic dependency expressions, malformed calls, and
type/control/data-flow questions are reported as unsupported rather than silently treated as safe.
Lifecycle pairing, touch-target size, Modifier ordering, unit/theme preferences, AndroidView commit
semantics, structural simplification, recomposition, allocation, and performance findings remain
disabled until a maintained AST or semantic layer can support them.

Only the Image rule is suppressible. A suppression is rule-scoped, requires a non-empty reason, and
is consumed by the next analyzable Image construct:

{/* non-executable sample_id="ai.project-analysis-suppression" reason="The intentionally incomplete Image call demonstrates the analyzer finding and must not be copied as valid UI source." visible_explanation="This diagnostic-only snippet deliberately omits contentDescription so the suppression contract is visible." */}
```kotlin
// viewcompose-ai:suppress-next VC-AI-A11Y-IMAGE-DESCRIPTION -- legacy wrapper records decoration
Image(source = divider)
```

Suppressed findings remain in `data.analysis.findings` with their reason and directive span, but are
not projected into legacy diagnostics. Dependency, profile, path, execution, timeout, and other
integrity findings cannot be suppressed.

Try this first request in the selected Agent:

> Use ViewCompose to create a Material 3 login screen. Retrieve the exact APIs and compiled samples
> before writing, run every validation lane currently available, and report the achieved evidence
> level and any unavailable deeper lane.

## Deep evidence execution boundary

Release `0.6.0` compiles generated Kotlin and renders generated screens against exact ViewCompose
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
npx --yes @viewcompose/ai-tooling@0.7.0 upgrade --client <codex|claude-code|cursor>
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
npx --yes @viewcompose/ai-tooling@0.7.0 uninstall --client <codex|claude-code|cursor>
```

The command removes only the exact ViewCompose MCP entry and canonical Skill bytes; unrelated client
settings and files remain. If either managed surface was edited, removal fails for review rather
than deleting user content. No global package exists to remove; the content-addressed package cache
is retained for integrity and compatible reuse.

## Integrity and troubleshooting

The [pinned GitHub Release](https://github.com/ViewCompose/ViewCompose/releases/tag/ai-tooling-v0.6.0)
contains the tarball, `manifest.json`, and `SHA256SUMS`. Its workflow builds the package twice,
checks the exact inventory and offline install/uninstall lifecycle, and creates GitHub Artifact
Attestations for all three assets. See GitHub's
[artifact attestation verification guide](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/verify-artifact-attestations)
for an optional independent provenance check.

Public `0.6.0` acceptance completed on 2026-09-01. Protected
[run `33498765977`](https://github.com/ViewCompose/ViewCompose/actions/runs/33498765977)
published the package from merge and tag commit
`67f99e12c02b36671843a6eb09546178c2760518` through the `ai-tooling-release` environment and GitHub
OIDC Trusted Publisher in 10 minutes 24 seconds. npm exposes `latest -> 0.6.0`; its provenance
predicate is SLSA v1 and its integrity is
`sha512-R3+kHFNVUqfUr1n2EHPmM+L2107DLux35TRGSxBdCenFAqV0dznzUXRcGsbKjjFlRXbrtwJ9ZPMEUy6XgMwwRQ==`.
The immutable Release contains exactly the 663,115-byte tarball, 35,817-byte `manifest.json`, and
179-byte `SHA256SUMS`. The tarball SHA-256 is
`de4b36df76ab842df18e0449967542b23de017104828700070caedb0e0671934`; all 3/3 assets passed the
published checksums and GitHub attestation verification.

In one repository-external Android project on macOS, the literal public selector completed
`init`, `doctor`, and `uninstall` for Codex, Claude Code, and Cursor. Every client reached
`project-bound-ready`, installed 7/7 exact Skills, and removed only its managed configuration and
21/21 total Skill copies. The installed Figma CLI and MCP reproduced 39/39 declared facts and 1/1
declared asset during inspection. Generation returned deterministic Kotlin and PNG files; real
released-Maven compilation and Preview rendering passed, followed by structure 9/9, semantics 8/8,
geometry 8/8, and assets 1/1. Style was explicitly `incomplete`, while pixel and perceptual evidence
were `not-applicable` because no trusted Figma reference render was accepted.

Relative to `0.5.0`, the public package adds one tool (13 to 14), one Skill (6 to 7), and one offline
Figma workflow (0 to 1); Android runtime artifacts are unchanged. The interpreted conclusion is
**improved** provider-neutral design import with **no material Android runtime behavior change** and
no visual-parity claim. The external run covered one normalized Figma export and one macOS host and
did not launch proprietary Agent binaries; hosted CI separately verifies native onboarding on
Linux, macOS, and Windows. Direct Figma login, plugin/REST/`.fig` import, custom-font and unsupported
effect generation, style or pixel parity, and source writes remain outside this release. Detailed
denominators and the next Wave D action are retained in the
[active AI tooling plan](../project/plans/ai-verifiable-development-tooling.md).

Public `0.5.0` acceptance completed on 2026-09-01. Protected
[run `33486262197`](https://github.com/ViewCompose/ViewCompose/actions/runs/33486262197)
published the package from exact tag commit
`99894e8220de78421c428a80b1d0f2b01c0f0f24` through the `ai-tooling-release` environment in
9 minutes 14 seconds. At that acceptance point, npm exposed `latest -> 0.5.0`; `0.6.0` now
supersedes that tag. The `0.5.0` SLSA v1 provenance names
`ViewCompose/ViewCompose`, `.github/workflows/ai-tooling-release.yml`,
`refs/tags/ai-tooling-v0.5.0`, the GitHub-hosted builder, and that exact run. The 637,133-byte
tarball has SHA-256 `a19e1c5680f34d744e313926af7d9081f51ea97e3ace64b6c732527d7104da04` and npm
integrity
`sha512-ffUtj1NwYZWx9JhlJEsw30AE+ZeQIDuMb1WaJ3r4CaOqzu1Y6F6EwO3NBIMSs6NkgSDrsLmi8JWGJ1GijwRSmg==`.
All 3/3 GitHub assets passed checksum and attestation verification.

Repository-external projects using the literal public selector reached `project-bound-ready` for
Codex, Claude Code, and Cursor, installed 6/6 Skills per client, then removed only their managed
configuration and 18/18 total Skill copies. The durable npm-installed analyzer returned schema v1,
an exact released-profile match, static evidence, and the expected categorical-high
`VC-AI-A11Y-IMAGE-DESCRIPTION` finding from a deliberately incomplete Image call. This is
**improved** analyzer evidence relative to `0.4.1`, with the one-command onboarding contract
unchanged. The public reproduction used one macOS host and did not launch proprietary Agent
binaries; hosted CI separately verifies native bootstrap behavior on Linux, macOS, and Windows.
Analyzer claims remain limited to the documented lexical boundary. Detailed denominators,
limitations, and the interpreted conclusion are retained in the
[active AI tooling plan](../project/plans/ai-verifiable-development-tooling.md).

The npm version history also contains `0.4.0-bootstrap.0`. It is a one-time, provenance-bearing
prerelease used only to create the package identity required before npm could bind the stable
GitHub Trusted Publisher. npm assigned the first package version to `latest` as well as
`bootstrap`, and rejected authenticated attempts to remove that default intermediate tag. Stable
`0.4.0` replaced `latest`, but public acceptance found that npm could not infer a default executable
from its three named binaries. Release `0.4.1` adds the package-name `ai-tooling` alias for the same
transactional Agent entry point. Public verification passed, and the `bootstrap` tag has been
removed. The earlier versions remain immutable audit history, `0.4.0` is deprecated with an
actionable `0.4.1` replacement, and `0.4.0-bootstrap.0` remains excluded from ordinary stable semver
ranges. The temporary npm token and GitHub secret were revoked before any stable tag.
Release `0.5.0` retains that onboarding correction and adds the versioned high-confidence project
analysis contract described above. Public `0.6.0` retains that analyzer and adds the offline Figma
contract described above. Release candidate `0.7.0` adds the attended repair contract; after its
immutable publication, use the exact `@viewcompose/ai-tooling@0.7.0` selector.

| Symptom | Action |
| --- | --- |
| `npx` cannot start the exact package | Confirm Node 24.19 or newer, npm registry access, and the literal published `@viewcompose/ai-tooling@0.7.0` selector after its Release is public. Do not substitute `latest`. |
| `doctor` reports `repair-required` | Run `init` again only if the existing files are unchanged; otherwise review the reported conflict. |
| `doctor` reports `host-prerequisites-required` | Install JDK 17 or 21 and Android SDK platform 36, then rerun `doctor`; Gradle itself is included. |
| `upgrade` returns `no-compatible-update` | Keep the current integration. No published tooling Release contains an exact framework profile for this project yet. Do not install a global-latest package as a workaround. |
| `upgrade` cannot resolve or finds conflicting ViewCompose versions | Replace dynamic or indirect declarations with exact coordinates, or add consistent dependency locks. The command intentionally leaves the current integration unchanged. |
| `upgrade` reports changed managed configuration or Skills | Review and preserve the user edits before retrying. The upgrader replaces only the exact bytes installed by ViewCompose. |
| The client does not show the MCP server | Run the client-specific check above, approve project configuration when required, then restart or reload the client. |
| Compile or Preview reports `VC-AI-PROJECT-ROOT-MISMATCH` | Run `init` from the physical project root and keep that project path available to the Agent process. |
| Figma inspection reports an unsupported mapping | Review the complete mapping ledger and correct or simplify the offline export. Do not delete the unsupported fact or ask the Agent to guess it. |
| Figma `verify` reports style `incomplete` or pixels `not-applicable` | This is the released evidence boundary, not a host failure. Review the generated Preview visually; do not claim Figma parity. |
| A credential is requested | Stop. ViewCompose needs no model-provider credential and never accepts one in MCP arguments or project configuration. |

Contributor internals are documented in the [AI tooling contract](../../tools/ai/README.md) and the
active [AI-verifiable tooling plan](../project/plans/ai-verifiable-development-tooling.md).
