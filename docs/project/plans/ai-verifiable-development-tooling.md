---
draft: true
schema_version: 2
document_id: plan.ai-verifiable-development-tooling
doc_type: plan
owner:
  kind: project
  id: ai-development-tooling
version_lane: version-agnostic
capability_ids: []
artifact_ids: []
sample_ids: []
status: active
scope: Make ViewCompose reliably discoverable, searchable, generatable, compilable, renderable, and diagnosable by coding agents through one versioned knowledge contract and isolated development-tooling pipeline.
non_goals:
  - Embed an AI model, provider SDK, credential, or network client in any ViewCompose runtime artifact or application process.
  - Claim unrestricted conversion fidelity for arbitrary Android XML, Jetpack Compose, screenshots, or Figma documents.
  - Treat generated code as correct merely because it parses, matches a screenshot, or passes a static symbol check.
  - Use runtime VNode or renderer internals as the interchange representation for migration and design tools.
  - Replace canonical KDoc, compiled samples, module manuals, migration guides, or capability governance with a parallel AI-only documentation system.
  - Market ViewCompose as AI-first before the accepted accuracy, safety, latency, compatibility, and reproducibility gates pass.
baseline: The 2026-08-29 audit found a strong generated capability Reference with 537 application-facing entries, 77 capabilities, 30 artifacts, one compiled sample for every capability, 185 compiled documentation regions, structured diagnostics, and a compilable Layoutlib Preview runner. It also found no versioned AI knowledge bundle, llms.txt, consumer Agent skills, MCP service, generated validator index, hermetic snippet compiler, tooling interchange representation, or conversion and visual-repair evaluation corpus.
ordered_work:
  - Freeze terminology, architecture, threat model, version lanes, evaluation corpus, metrics, and public capability dispositions before adding AI-facing tools.
  - Generate one deterministic, versioned AI Knowledge Bundle and compact llms.txt from canonical governance, signatures, KDoc, samples, and publication metadata.
  - Build isolated static, compile, render, diagnostic, and project-analysis foundations with structured results and adversarial safety tests.
  - Expose the accepted foundations through a local CLI, MCP server, and client-neutral Agent skills without duplicating framework knowledge.
  - Add a tooling-only Design IR and implement XML migration before bounded Jetpack Compose source migration.
  - Add prompt, screenshot, and Figma adapters only after code generation, compile, render, comparison, and repair are independently measurable.
  - Stabilize packaging, compatibility, security, documentation, release operations, and product claims against longitudinal evidence.
completion:
  - Every supported AI-facing answer can identify its ViewCompose artifact/version, canonical source fingerprint, symbols, samples, and validation evidence.
  - Generated snippets use real published APIs and pass the required isolated compilation gate; renderable UI also passes Preview diagnostics and declared semantic or visual checks.
  - MCP, CLI, skills, validators, converters, and model adapters remain downstream development tools with no inactive-path or release-runtime footprint.
  - XML, Compose, screenshot, and Figma paths share one explicit Design IR, preserve provenance and unsupported semantics, and never silently invent application behavior.
  - Accuracy, false-positive, latency, resource, privacy, and security thresholds are frozen before implementation and satisfied by reproducible CI or accepted device evidence.
  - All affected capability, API, sample, module, architecture, tooling, security, migration, release-intent, and localized documentation gates pass before archival.
last_verified: 2026-08-30
next_action: Implement an isolated attended in-memory Design IR executor and trusted-host terminal-outcome callback that consume the frozen grant exactly once while keeping source writes and public activation disabled.
maven_release_changesets:
  - release/changes/20260829-preview-worker-jvm21-resolution.json
---

# AI-Verifiable Development Tooling Plan

## Status

Active. The audit and Phase 0 contract/security freeze are complete. Phase 1 canonical knowledge
generation, hosted discovery, freshness gates, and full-site acceptance are complete. Phase 2
static validation, pinned compilation, Preview evidence, read-only project findings, and internal
CLI foundations are complete. Phase 3 deterministic Knowledge Bundle retrieval, its CLI surface,
dual-era stdio MCP, deterministic Preview layout diagnosis, and five foundational client-neutral Agent
workflows are complete. The reproducible local distribution, offline lifecycle, SPDX/license
inventory, installed compile example, and protocol compatibility gates complete the Phase 3
foundation.
Phase 4 now has a frozen typed Design IR v1 and a fail-closed Android XML v1 migration subset with
one supported golden and three explicit unsupported denominators. The bounded XML parser now meets
the frozen IR determinism, provenance, resource-preservation, and unsupported-honesty gates. The
IR-to-Kotlin generator now produces the exact login golden and passes the hermetic compiler. The
accepted converter is the ninth shared CLI/MCP tool, works in standalone generation and explicit
source-bound compile modes, ships in the reproducible offline package, and is orchestrated by the
sixth client-neutral consumer workflow. The next increment is now frozen as Android XML project
context v1: explicit-root resource and style resolution plus a read-only, bounded lexical call-site
inventory whose completeness is never claimed. That context is now integrated as an explicit
project input form of the shared CLI/MCP converter, and its styled golden passes the hermetic
compiler without changing standalone source input. Android XML layout v2 is now contract-frozen as
the next compatible subset for `FrameLayout`, `ImageView`, explicit image accessibility, drawable
bindings, image scaling, and visibility. Its parser, IR, generator, project composition, installed
CLI/MCP generation, and hermetic compile gates now pass. The following explicit-root layout
dependency contract is also frozen: it bounds default-layout selection, `include`/`merge`
expansion, dependency cycles, graph identity, and cross-file provenance before implementation. Its
resolver, project-context composition, CLI/MCP distribution, and hermetic compile gate now pass.
The generated-screen Preview contract is now also frozen and implemented: it binds generated
Kotlin, explicit preview values, one fixed configuration, the current-source compiler and renderer
lanes, and all accepted artifacts into a content-addressed request while denying inspected-project
build execution. The tool-owned harness, source-bound CLI/MCP render mode, exact artifact gate,
stable cache proof, and installed-package render denominator now pass. Exact embedded PNG bytes now
also become a tool-owned Android resource without any caller path, URL, inspected-project resource
read, or network access; the accepted XML v2 profile-card fixture compiles and renders through that
lane. The exact structured semantic and geometry comparison contract between the converter's Design
IR expectations and accepted render-tree evidence is now implemented. Both generated fixtures pass
the public `compared` evidence gate through the installed package. The provider-neutral screenshot
input, deterministic preprocessing, privacy, and evaluation boundary is now frozen before any
model-backed generation begins. The dependency-free preprocessor is now the tenth shared CLI/MCP
tool and reproduces the same privacy golden through the installed package. It still performs no UI
inference. The provider-neutral screenshot-to-Design-IR request/result, lineage, evidence,
uncertainty, and consent contract is frozen without selecting a provider. Its offline validator is
now the eleventh shared CLI/MCP tool: it deterministically reproduces preprocessing, reconstructs
the exact inference request, validates an externally produced result, and imports Design IR only
after every lineage, evidence, uncertainty, and authorization check passes. It performs no model or
provider execution and no network request. The typed human-resolution patch contract is now also
frozen: it binds every answer to the exact validated import, question, node, pixel region, required
action, reviewer, and review receipt; forbids executable expressions and guessed resources; and
derives code-generation eligibility only after all blocking questions, unsupported semantics, and
placeholder bindings reach zero. Its offline adapter is now the twelfth shared CLI/MCP tool. It
revalidates the imported inference identity, applies only component-compatible typed fields and
caller bindings, persists the complete accessibility review into Design IR, and reproduces the
resolved golden through the installed package with no provider, network, or answer execution. The
next screenshot-specific Kotlin generation contract is now frozen. Its checked-in golden maps the
typed state and event bindings to real public APIs, preserves every accessibility disposition in a
machine-checked report, and passes the hermetic compiler. The thirteenth shared CLI/MCP tool now
reproduces that source and report in generate mode and returns hermetic compiled evidence in compile
mode, including from the installed package. Provider selection remains a separate, explicitly
authorized decision. The source-bound screenshot generated-Preview contract is now implemented with
explicit state and fixed no-source callback bindings, exact rendered evidence, CLI/MCP parity, and
installed-package verification.

Last verified: 2026-08-30.

The screenshot semantic and structural comparison and the separate exact RGBA comparison are now
implemented. Pixel comparison admits only canonical, zero-redaction, full-viewport references
whose dimensions, density, font scale, locale, layout direction, color space, alpha mode,
orientation, system bars, and accepted semantic evidence exactly match the render. The original
16×24 inference wireframe is therefore not assigned a pixel score.

The bounded deterministic repair contract and its provider-offline internal orchestrator are now
implemented, including typed Design IR patch application, source-bound candidate evaluation, and
content-addressed structured candidate evidence, but automatic repair is not yet a public tool
mode. The reproduced cross-build persistent Preview worker isolation gap is now closed by binding
worker reuse to one exact build identity. Exact RGBA comparison now retains separate bounded
global mismatch bounds, deepest-containing Design IR node attribution, stable tie-breaking, and
explicit unassigned pixels without deriving a repair value from location. The internal v1 proposer
now consumes only two integrity-verified records from the same resolution and exact reference. It
may roll back exactly one localized `properties` value, and only to the typed value retained by a
strictly better baseline. The real `Hello` regression emits the frozen `Welcome` patch and that
patch passes the typed applier plus all six source-bound gates with zero mismatched pixels. Novel
repair inference and public CLI/MCP activation remain off until accepted baseline provenance and
explicit authorization are separately frozen.

That v1 authorization boundary is now frozen with two purpose-bound human attestations and exact
content-address binding. Its internal validator now reproduces all available evidence and proposal
bindings while fixing `executionAuthorized` to false; every execution mode remains off.
The following host-grant lifecycle now has an internal direct-callback adapter: only an explicitly
registered in-process host may authenticate both reviewer receipts, check revocation, and atomically
reserve one terminal repair attempt. A durable file-backed test host proves concurrent and
cross-instance replay denial. Production host integration, patch execution, and public activation
remain off. The terminal outcome contract now additionally freezes applied, failed, cancelled, and
indeterminate receipts after reservation; every state is terminal and non-retryable, while only an
exact committed application may expose content-addressed output identities.

## Maven release changesets

- `release/changes/20260829-preview-worker-jvm21-resolution.json`

## Release intent rationale

The planning, contracts, Knowledge Bundle, validator, analyzer, and compiler-harness slices do not
change a published artifact. Phase 2 Preview acceptance exposed and fixes one production
configuration mismatch in `viewcompose-preview-gradle-plugin`; its immutable Changeset classifies
that artifact as a fix. Later publication-relevant slices must add one Changeset per affected
published artifact, or record an explicit ignored disposition with a concrete reason.

## Objective

Make ViewCompose an Android UI framework that coding agents can use correctly because the framework
provides machine-readable truth, deterministic retrieval, bounded generation targets, executable
validation, render evidence, and structured repair inputs.

The product direction is **AI-verifiable development**, not an AI model embedded in the framework.
The framework owns facts and deterministic tools. The coding client owns model selection,
conversation, credentials, network access, and repair orchestration. A generated UI is acceptable
only when the evidence required by its lane passes.

The target interaction is:

```text
developer intent or existing UI source
  -> coding agent
  -> versioned ViewCompose knowledge retrieval
  -> bounded source or Design IR generation
  -> static validation
  -> hermetic compilation
  -> optional Preview render and diagnostics
  -> structured comparison and repair
  -> code plus evidence and explicit unsupported semantics
```

This plan deliberately puts the missing foundations ahead of MCP breadth, converters, and visual
generation. A protocol endpoint cannot compensate for stale knowledge, unverifiable snippets, an
unsafe executor, or an undefined measure of correctness.

## Baseline and audit interpretation

### Accepted foundations

The repository already has more reusable infrastructure than a new AI subsystem should recreate:

1. `website/src/data/capability-reference.json` contains 537 owned application-facing entries,
   grouped into 77 stable capabilities and 30 artifacts across 15 groups.
2. Every current capability points to a compiled-region sample. The broader documentation corpus
   contains 185 compiled regions and seven explicitly non-executable historical examples.
3. Governance V2 already connects symbols, `capability_id`, artifacts, samples, generated Reference,
   handwritten owners, public API impact, and release intent.
4. Strict API documentation policy requires canonical English KDoc/Javadoc and compiled Q3 samples
   for new or changed public/protected API.
5. `viewcompose-preview` can compile the native DSL, render through Layoutlib, emit PNG output,
   expose render-tree/source-location data, and return structured diagnostics.
6. Runtime and renderer diagnostics already model frame failure, recomposition reasons, node
   patches, lifecycle state, render trees, and bounded production aggregation.
7. ADR-0009 already requires concrete development tooling to live downstream, activate only for a
   debuggable artifact and explicit request, and own no recurring inactive-path work.

### Missing foundations

| Gap | Why it blocks later work | First owner |
| --- | --- | --- |
| No accepted evaluation corpus or thresholds | “AI works well” cannot be reproduced, compared, or release-gated | Phase 0 |
| No AI-tooling architecture and threat model | Compilation, project inspection, credentials, and file access have undefined boundaries | Phase 0 |
| No canonical versioned AI bundle | An MCP server or skill would scrape several sources and drift from published API truth | Phase 1 |
| No compact `llms.txt` discovery surface | Generic models cannot cheaply locate the canonical Reference, rules, or samples | Phase 1 |
| No generated validator index | Static checks would hand-maintain API names and repeat SceneView-style drift risk | Phase 2 |
| No hermetic snippet compiler | A symbol match cannot prove Kotlin type resolution, overload selection, resources, or dependencies | Phase 2 |
| No stable Preview tool adapter | Visual tools would bypass the existing renderer and diagnostics contract | Phase 2 |
| No consumer-facing MCP, CLI, or skills | Agents cannot retrieve or validate through a supported local protocol | Phase 3 |
| No tooling-only Design IR | XML, Compose, screenshot, Figma, and prompt paths would each invent a different mapping model | Phase 4 |
| No conversion or visual-repair corpus | Migration and screenshot claims would be anecdotal and easy to regress | Phases 4 and 5 |

Conclusion: the repository is **AI-ready in source quality**, but not yet **Agent-ready as a
supported development interface**. Phases 0 through 3 close that gap. Later generation features
must reuse those contracts rather than create parallel truth or validation paths.

## Architecture and safety invariants

### One canonical knowledge lineage

AI-facing data has one lineage:

```text
canonical KDoc and published signatures
  + Governance V2 capability records
  + compiled samples and handwritten rules
  + artifact publication and version metadata
  -> deterministic AI Knowledge Bundle generator
  -> manifest + symbols + capabilities + samples + rules
  -> compact llms.txt / optional full discovery document
  -> CLI, MCP, skills, validator, converters, and evaluations
```

`llms.txt`, an MCP resource, a search index, a mapping table, and validator declarations must not be
hand-maintained copies of the same API. Every generated artifact records its schema version,
framework version lane, artifact coordinates, source revision, and canonical `sourceFingerprint`.
CI regenerates and compares checked-in or packaged outputs and rejects freshness drift.

### Runtime and provider isolation

AI tooling lives under a downstream `tools/` boundary or a separately distributed development
artifact accepted during Phase 0. No runtime, renderer, UI Foundation, design-system, integration,
or application aggregate artifact may depend on MCP, model SDKs, network clients, parsers, image
comparison engines, or conversion code.

The framework does not select or call a model provider. Provider adapters, when Phase 5 needs them,
are optional downstream processes with explicit configuration, bring-your-own credentials, bounded
input disclosure, redaction, and no telemetry by default. Secrets never enter prompts, generated
files, diagnostics, caches, screenshots, or MCP logs.

### Validation is layered evidence

Validation modes are separate and explicit:

1. **Static:** parse the supported Kotlin shape; verify imports, artifacts, known symbols, removed
   names, rules, and dependency requirements against the generated bundle.
2. **Compile:** place only the submitted snippet and declared resources into a fixed harness pinned
   to an accepted JDK, Kotlin, AGP, Android SDK, and ViewCompose version lane; compile without
   executing the inspected project's build logic.
3. **Render:** invoke the accepted Preview runner adapter with bounded configuration, time, memory,
   output size, and diagnostics.
4. **Compare:** evaluate declared structure, text, resources, semantics, accessibility, geometry,
   and only then pixels or vision similarity where applicable.

Passing a shallower mode never implies a deeper mode passed. Results include mode, status, stable
diagnostic code, severity, source span, relevant artifact/version, suggested next action, elapsed
time, cache status, and truncated safe details.

### Untrusted source and project boundaries

- `validate_code` never evaluates arbitrary Gradle scripts, plugins, annotation processors, shell
  commands, or project tests. True compilation uses a tool-owned harness and dependency allowlist.
- `analyze_project` is read-only by default, requires an explicit normalized project root, rejects
  symlink escape and path traversal, observes file/count/size/time limits, ignores secrets and
  build output by policy, and returns findings rather than silently changing files.
- A later migration write mode produces a patch plan first. Applying changes remains an explicit
  client action and preserves unsupported regions rather than deleting them.
- Caches are content-addressed, version-scoped, bounded, evictable, and never mix results across
  tool schema, framework version, SDK, or configuration lanes.
- Network access is absent from the deterministic knowledge, validation, compilation, render, and
  conversion core. Optional provider or remote-asset access is a separately disclosed adapter.

### Design IR is tooling-owned

Conversion uses a tooling-only, versioned Design IR rather than runtime `VNode`, renderer nodes, or
Android `View` instances. The minimum model covers node kind, layout relationship, typed property,
modifier, resource reference, semantic role, event placeholder, state/visibility expression,
source provenance, confidence, unsupported source fragment, and stable identity.

The IR is intentionally more descriptive than the runtime tree: it must represent incomplete
source, design tokens, uncertain visual inference, resource and style indirection, and migration
work that cannot safely become executable behavior. IR schema compatibility is independent of
ViewCompose runtime compatibility.

## Scope and product lanes

| Lane | Included outcome | Explicit boundary |
| --- | --- | --- |
| Foundation MVP | Phases 0–3: knowledge bundle, `llms.txt`, validator/compile/render foundations, local CLI, MCP, and client-neutral skills | No automatic XML, Compose, screenshot, or Figma conversion claim |
| Migration MVP | Phase 4 XML subset through IR, code generation, compile/render verification, and migration report | Unsupported custom Views, data binding expressions, behavior, and call-site rewrites remain explicit |
| Source expansion | Phase 4 bounded Compose subset and deterministic ViewCompose analysis | No promise of arbitrary Kotlin or semantic parity |
| AI-native visual tooling | Phase 5 prompt/screenshot/Figma adapters plus measurable repair loop | No provider dependency in framework runtime and no pixel match as sole correctness proof |
| Stable product | Phase 6 compatibility, packaging, security, operations, longitudinal metrics, and reviewed product language | “AI-first” claim remains gated by evidence |

## Phase 0 — Contract, evaluation, and security freeze

### Purpose

Define what the system is, what it may access, how correctness is measured, and which contracts are
independently versioned before an implementation makes accidental decisions permanent.

### Deliverables

1. An ADR that fixes process isolation, canonical knowledge lineage, Design IR separation,
   provider neutrality, local-first operation, version lanes, cache boundaries, and the relationship
   to ADR-0009.
2. A threat model covering malicious snippets, Gradle/build-script execution, path traversal,
   symlink escape, dependency substitution, resource bombs, zip bombs, image bombs, prompt
   injection in project text, credential disclosure, cache poisoning, denial of service, and unsafe
   patch application.
3. Versioned schemas for the AI Knowledge Bundle, requests, structured diagnostics, evidence, and
   Design IR compatibility policy. Freeze names only after capability review.
4. A checked-in evaluation corpus with positive and negative fixtures for API retrieval,
   nonexistent APIs, overload/default selection, dependencies, lifecycle/resource/layout mistakes,
   compilable UI, failed compilation, Preview render, project analysis, and adversarial paths.
5. Separate future fixture sets for XML migration, Compose mapping, screenshot reconstruction, and
   Figma import, even though later phases activate their gates.
6. Metric definitions and initial thresholds for retrieval accuracy, fabricated-symbol rejection,
   compile success, diagnostic precision/recall, false positives, render success, semantic and
   visual similarity, latency, memory, cache effectiveness, and unsupported-case honesty.
7. Stable `capability_id`, Q level, applicable contract fields, API/documentation impact
   dispositions, and module ownership for every new public/protected or application-facing tooling
   surface.

### Acceptance gate

- Every metric names its corpus, denominator, version/configuration lane, threshold, command, and
  evidence owner; aggregate percentages cannot hide unsupported or failed categories.
- The threat model has automated negative-test owners and an explicit residual-risk decision.
- Tool, bundle, IR, and framework versions can vary independently without an unspecified fallback.
- No MCP or converter implementation begins until this phase is accepted.

### Acceptance evidence (2026-08-29)

Phase 0 is complete at source revision `1de3ceaa` plus this implementation slice. ADR-0009's AI
tooling invariants accept the canonical lineage, provider/runtime isolation, cumulative evidence,
untrusted execution, and Design IR boundary. `tools/ai/` freezes version, Q-level, metric, and
threat contracts in five JSON Schemas, five capability IDs, 17 metrics, 14 evaluation cases, and 11
fixture-backed cases.

The following fresh commands passed on macOS using Android Studio JBR 21.0.10:

```text
npm --prefix tools/ai run verify
./gradlew -p tools/viewcompose-quality-build test --console=plain
./gradlew verifyAiToolingContracts verifyDocumentationStructure \
  verifyDevelopmentToolingIsolation verifyViewComposeReleaseIntent --console=plain
```

The Node suite passed 4/4 tests. The compiled quality-build suite passed, proving
`verifyAiToolingContracts` is registered and owned by `qaQuick`. The combined root gate passed 22
tasks, verified 131 canonical English and 127 current Chinese documents, reported zero Governance
V2 issues, zero release artifacts, and no development-tooling isolation violation.

Comparison context: the baseline had no AI contract gate, schema, metric denominator, or adversarial
corpus; the accepted slice adds all four without a production artifact or release-runtime change.
Normalized runtime and accuracy change are not applicable because Phase 0 intentionally adds no
query, compiler, renderer adapter, converter, or model execution. Conclusion: **improved** contract,
security, and reproducibility readiness with **no material runtime change**. Limitations: the local
Schema validator implements only the frozen subset used by these contracts, future-phase fixtures
are contract denominators rather than passing implementation results, and no AI-facing product
capability is claimed yet. Next action: Phase 1 canonical knowledge generation.

## Phase 1 — Canonical AI Knowledge and discovery

### Purpose

Turn the repository's existing structured documentation into one deterministic, version-aware
machine contract instead of adding another handwritten API reference.

### Deliverables

1. Extend the canonical generator to emit a versioned bundle containing at least:
   - a manifest with schemas, framework/artifact versions, source revision, fingerprints, and
     compatibility;
   - exact application-facing symbol names, owners, declarations/signatures, defaults where
     deterministically available, artifacts, dependency coordinates, deprecation/removal data, and
     canonical source links;
   - capability summaries, rules, lifecycle/platform constraints, related documents, and compiled
     sample metadata plus source regions;
   - compact deterministic search fields and stable IDs suitable for local retrieval.
2. Publish a compact root discovery response at `/llms.txt` that identifies ViewCompose, supported
   version lanes, canonical documents, common rules, bundle locations, and tool entry points without
   duplicating the entire Reference.
3. Provide an optional fuller machine document or downloadable bundle for clients that cannot read
   structured resources. Its size and website budget are explicit and gated.
4. Add schema validation, deterministic ordering, duplicate/stale-link detection, generated-file
   freshness checks, source-fingerprint checks, and golden tests.
5. Add a human-readable guide explaining version selection, evidence levels, unsupported behavior,
   and how AI clients should cite retrieved capability and sample IDs.

### Acceptance gate

- Repeated generation from the same source is byte-for-byte identical.
- Every current capability resolves to a real artifact, canonical source, compiled sample, and
  supported version lane; removed or non-executable examples cannot appear as current copyable code.
- A source, API, sample, artifact, or governance change that should affect the bundle fails CI until
  regenerated.
- The hosted documentation size, link, language, and translation gates remain within accepted
  budgets.

### Acceptance evidence (2026-08-29)

Phase 1 is complete. Generator source revision
`7af858dca1aacf1241106db46021c65fcffa3715` produces bundle fingerprint
`ee1765176164201252fe4f3c0b9839a26ee1d87def028255ae2fc435c6594ec1`. The 1,169,945-byte local
bundle contains 30 artifacts, 77 capabilities, 537 symbols resolved to exact source declarations,
209 registered samples, and 10 reviewed rules. Its compact hosted `llms.txt` is 2,646 bytes; the
173,728-byte text fallback and structured JSON/JSONL stay outside the deployed site.

Fresh Android Studio JBR 21 verification passed:

```text
npm --prefix tools/ai run verify                         # 7/7 tests
npm --prefix tools/ai run verify:knowledge               # exact fingerprint/revision
./gradlew -p tools/viewcompose-quality-build test         # compiled task ownership tests
./gradlew verifyAiToolingContracts verifyAiKnowledgeBundle \
  verifyDocumentationStructure verifyDevelopmentToolingIsolation \
  verifyViewComposeReleaseIntent                         # 23 tasks
./gradlew verifyCompleteViewComposeApiDocs                # 6/6 groups, 9m 1s cold
npm --prefix website run build                            # 37.4s wrapper
```

The root gate reported zero Governance V2 issues, zero release artifacts, and no tooling-isolation
violation. The production site retained 133 immutable API versions and manuals, 133 Chinese
fallback routes, two search indexes, 526 audited pages, 30 redirects, 6.7/8.0 MiB JavaScript,
650/768 KiB maximum JavaScript, and 112/128 KiB CSS. Exact output was 491,946,739 bytes, including
49,175,846 non-API bytes against the unchanged 49,178,214.4-byte ceiling (2,368.4 bytes headroom).

Comparison context: a same-corpus candidate with a separate bilingual AI ADR route produced
49,553,310 non-API bytes. Consolidating its boundary into ADR-0009's machine-readable invariants
and the executable plan/contracts reduced output by 377,464 bytes (`-0.7617%`) without weakening
the decision or raising the ceiling. The result is **improved**
for deterministic AI discoverability and **no material runtime change** because only downstream
tooling and documentation changed. The hosted representation result is **mixed**: all public site
gates pass, but headroom is nearly exhausted.

Limitations: this bundle supports only exact `current-source`; released-version bundles, static
validation, compilation, rendering, project analysis, CLI/MCP, conversion, and model adapters do
not exist yet. KDoc summaries are emitted only when deterministic source adjacency is available,
and registered non-executable samples remain evidence records rather than copyable code. The next
phase must preserve the bundle as its only symbol source and must recover site headroom before
publishing another large route.

## Phase 2 — Validation, compilation, render, and analysis foundations

### Purpose

Create the evidence-producing core before exposing a broad protocol surface.

### Deliverables

1. A generated validator index derived from the Phase 1 bundle, not a handwritten symbol list.
2. Deterministic static rules for unknown/removed APIs, missing artifacts or imports, invalid common
   nesting, modifier misuse, units, lifecycle/effect hazards, accessibility, touch targets,
   resource use, View retention, unnecessary View creation, and bounded performance risks. Each
   rule has a stable code, severity, documented scope, positive/negative fixtures, and false-positive
   budget.
3. A hermetic Kotlin/Android snippet compiler with pinned toolchain lanes, dependency allowlist,
   resource fixture support, content-addressed cache, time/memory/output limits, cancellation, and
   normalized compiler diagnostics.
4. A Preview adapter that accepts a compiled target and bounded device/theme/locale/font-scale
   configuration, then returns PNG, layout/render tree, source locations, structured diagnostics,
   and evidence metadata.
5. A read-only project analyzer that inventories ViewCompose versions, artifacts, imports,
   capability usage, migrations, deprecated/unknown names, samples, and configuration without
   executing project build logic.
6. One local internal CLI used by tests and later transports. The validation core does not depend
   on MCP types.

### Implementation evidence — static and project-safety slice

The first Phase 2 slice now derives a 537-entry validator index directly from the accepted Phase 1
bundle and returns the frozen tool-result envelope. It rejects governed symbols used through an
unavailable import or receiver, requires an explicit `contentDescription` decision for the
ViewCompose `Image` component, masks Kotlin strings and nested comments before rule matching, and
keeps source spans stable. It deliberately does not infer that a supporting public type is absent
only because that type lacks an independent Governance V2 capability entry.

The read-only analyzer accepts one canonical absolute root, rejects path traversal, symbolic links,
requested build execution, and limits beyond fixed hard caps. It excludes common build outputs and
secret-bearing files, bounds file count, bytes, depth, time, and response data, and never executes
the inspected project's Gradle logic. `verifyAiStaticTooling` runs the Phase 2 static/security
corpus from `qaQuick`.

On 2026-08-29, Node 25.6.0 completed 20/20 AI-tooling unit tests in 1.25 seconds and the separate
Phase 2 runner passed 5/5 currently applicable static, project-analysis, and security corpus cases.
The compiled quality-build plugin suite and root `verifyAiStaticTooling`,
`verifyAiToolingContracts`, `verifyAiKnowledgeBundle`, and
`verifyDevelopmentToolingIsolation` tasks also passed. The normalized pass rate is 100%; no prior
Phase 2 implementation existed, so a latency or accuracy delta is not applicable. The conclusion
is **improved** deterministic rejection and project-safety evidence with **no material runtime
change**, because the implementation and gate remain downstream tooling.

Limitations: the static slice is not a Kotlin type checker, its initial rule family is intentionally
narrower than the complete deliverable list, and the project analyzer does not yet resolve
dependencies or produce migration findings. At this slice boundary, compilation, rendering,
cancellation, cache behavior, and internal CLI evidence were pending; the compiler slice below now
closes the applicable compilation, cancellation, and cache requirements. Static rules expand only
when labeled positive and negative fixtures preserve the frozen false-positive budget.

### Implementation evidence — pinned compiler slice

The second Phase 2 slice adds a non-published `:tools:ai-compiler-harness` Android library and a
provider-neutral adapter for the fixed
`current-source/jdk-21/agp-9.1.1/kotlin-2.2.10/android-36/jvm-11` lane. Requests cannot select a
Gradle task, project, script, dependency coordinate, repository, or output directory. The current
allowlist contains only `viewcompose-ui-foundation`; Gradle runs offline with bounded heap, workers,
time, and captured output after CI explicitly resolves that fixed classpath. Static validation must
pass before the process starts, and only successful compilation with bounded, re-fingerprinted class
files advances evidence from `static` to `compiled`.

The content-addressed request key includes source, sorted artifacts, compiler lane, and the accepted
Knowledge Bundle fingerprint. Inputs are create-once, concurrent identical requests use one lock,
and a cache hit is accepted only when its record and current class-file fingerprint agree. Stable
outcomes cover invalid selections, lane mismatch, compiler diagnostics, timeout, cancellation,
captured-output limits, missing/unsafe output, start failure, concurrent work, and poisoned inputs or
caches without exposing the tool-owned absolute request path.

The canonical compiled sample contains test-source helpers that intentionally rely on module friend
paths, so the compile corpus uses a consumer-form extraction of its `ProfileSummary` example instead
of weakening the harness into a test-module compiler. A cold accepted run completed in 11,775 ms and
produced two class files totaling 3,575 bytes with fingerprint
`9877c5a41372f6a77423071dc79cad680daa6febb3f7621cf2b1d755d9481acb`; an integrity-checked repeat
returned the same fingerprint in 5--12 ms. Node 25.6.0 passed 29/29 tooling tests, including real
child-process output, timeout, and cancellation enforcement plus selection, traversal, symbolic-link
output, cache tampering, concurrent-request, and normalized-diagnostic cases. The independent
compiler corpus passed 1/1. This is **improved** executable type-resolution evidence with **no
material runtime change** because the harness is non-published, downstream tooling and is absent
from application dependency graphs.

Limitations: this slice compiles one Kotlin file against UI Foundation and does not yet support
Android resource fixtures or the remaining artifact combinations. A cold result is local macOS/JBR
21 evidence rather than a cross-host latency distribution. At this slice boundary, the next action
was to adapt the existing Layoutlib Preview protocol; the Preview slice below now closes that work.
Both paths still need one internal CLI before compiler lanes widen.

### Implementation evidence — Preview adapter slice

The third Phase 2 slice adapts the existing protocol instead of adding a second renderer. Its first
fixed lane discovers the compiled `samples.counter.CounterPreview` target and selects only a variant
whose declared theme, locale, viewport, density, font scale, and layout direction exactly match the
bounded request. The request cannot choose a project, task, source path, worker class, dependency,
repository, or output path. Both discovery and render use fixed offline Gradle plans on JDK 21; the
result records the Preview compiler lane and
`current-source/preview-protocol-1/paparazzi-2.0.0-alpha05/layoutlib-16.2.1` render lane.

`rendered` evidence requires protocol, module, build fingerprint, entry point, source containment,
response correlation, and exact descriptor/variant identity. Image and tree paths must remain in the
canonical content-addressed directory with no symbolic-link segment. The adapter bounds catalog,
response, PNG, tree, and process output independently; verifies PNG signature/chunk structure and
dimensions; parses the render tree; hashes both artifacts into one output fingerprint; maps
structured Preview diagnostics without absolute paths; and re-runs all artifact checks on a cache
hit. Malformed or replaced cache output fails closed.

The accepted current-source render produced the inspected 1,079 x 2,339 PNG of 25,755 bytes and a
121,271-byte render tree with zero diagnostics. Its image/tree fingerprint is
`bb7eba4f51d1aa4f788b0991b7c8635815d6943c374978b685f92619420841d0`; repeated integrity-checked
corpus runs returned the same fingerprint in 9,809--11,481 ms including isolated Gradle discovery. The
worker response itself reported 220 ms render duration after a 2,315 ms Layoutlib setup. Node 25.6.0
passed 34/34 tooling tests, including target/configuration rejection, inherited-property selection,
source escape, symbolic-link artifacts, cache replacement, timeout, cancellation, and structured
worker-failure normalization. The render corpus passed 1/1. This is **improved** executable visual
evidence. Full `qaPreview` passed 1,216 tasks (363 executed and 853 up-to-date), and documentation,
translation, release-intent, and development-tooling-isolation gates passed. There is **no material
application-runtime change** because the adapter remains downstream and the Preview process is
activated only by an explicit tooling request.

Limitations: the current allowlist contains one target with its light/dark variants and `en-US`
configuration, not arbitrary application builds or a visual-comparison claim. The local macOS/JBR
21 measurements are not a cross-host latency distribution, and a matching render does not prove
interaction behavior. The internal CLI and project-analysis closeout below reuse this fixed lane;
later phases may widen it only with independent corpus evidence.

### Implementation evidence — project findings and internal CLI closeout

The final Phase 2 slice expands the read-only analyzer from inventory signals into bounded facts
derived from the accepted Knowledge Bundle. It recognizes exact ViewCompose Maven and project
coordinates, declared/current-bundle versions, governed and supporting imports, owning artifacts,
capability usage, Android SDK declarations, Preview sources, and Android XML or Jetpack Compose
migration candidates. Unknown namespaces and artifacts, imports whose owning artifact was not
declared in the inspected files, and exact dependency versions outside the current-source bundle
produce stable warnings. Direct secret targets and malformed exclusion policies now fail before
traversal. The analyzer still never invokes Gradle, resolves a plugin, follows a symbolic link,
writes source, or treats a regex-derived candidate as a proven migration.

The provider-neutral internal CLI reads one frozen request envelope from stdin, requires the exact
Knowledge Bundle lane and identity, propagates mandatory input/output/time limits, and dispatches
the same static validator, compiler, Preview, and project analyzer used by their direct corpus
runners. Stdout contains one schema-validated result only; malformed envelopes fail on stderr
without partial JSON. Unsupported tools and framework drift fail before adapter invocation. This is
an internal parity seam, not yet a supported MCP or public distribution contract.

On 2026-08-29, Node 25.6.0 passed 43/43 tooling tests in 1.30 seconds, including internal CLI
process-boundary, identity, dispatch, limit, and malformed-envelope cases plus the expanded project
facts and secret-target cases. The independent static/security corpus passed 5/5, compiler corpus
passed 1/1, and render corpus passed 1/1 through the same adapters. The exact CLI also returned
schema-valid static, compiled, rendered, and project-analysis results in the fixed current-source
lane. Repository documentation, release-intent, quality-build, development-tooling-isolation, and
Phase 2 gates passed; root `qaQuick` completed 2,271 tasks (1,200 executed and 1,071 up-to-date) in
6 minutes 40 seconds. Compared with the inventory-only slice, analyzable framework facts expanded
without changing the fixed traversal or process-execution boundary; the conclusion is **improved**
project evidence and transport consistency with **no material runtime change**.

Limitations: Gradle/TOML discovery is deliberately syntax-bounded and does not resolve version
catalog aliases, convention plugins, transitive dependencies, arbitrary expressions, or released
Knowledge Bundles. Supporting imports without their own governed symbol remain facts rather than
invented API entries. The CLI is repository-internal, has no packaging or compatibility promise,
and exposes no retrieval or MCP transport yet. Compiler resource fixtures, additional artifact
lanes, arbitrary Preview targets, deprecation/removal findings, richer typed analysis, and
cross-host performance distributions remain future work. The next action is Phase 3 deterministic
retrieval over this accepted core, followed by CLI/MCP parity rather than duplicated transport
logic.

### Acceptance gate

- The fabricated-API corpus is rejected at the frozen threshold, while valid compiled samples have
  no unexplained static false positives.
- Every “valid” compile result comes from the hermetic compiler; parser-only or symbol-only success
  is never labeled compiled.
- Golden render fixtures reproduce declared output and diagnostics for every supported lane.
- Adversarial project, path, dependency, resource, timeout, cancellation, and cache tests pass.
- Release artifacts and an inactive application process show zero AI-tooling dependency and no
  recurring work, verified under ADR-0009.

## Phase 3 — CLI, MCP, and Agent workflows

### Purpose

Expose the proven local capabilities through interoperable interfaces while keeping one core and
one knowledge source.

### Initial MCP surface

| Tool or resource | Contract |
| --- | --- |
| `get_api_reference` | Resolve exact symbol/capability/artifact/version facts and canonical links |
| `get_component_reference` | Return one component's parameters, defaults, modifiers, rules, sample, and dependency requirements |
| `search_component` | Deterministic local search with stable ranked results and explicit version filter |
| `get_sample` | Return a compiled source region plus build target, imports, artifacts, capability ID, and fingerprint |
| `validate_code` | Run requested static and/or hermetic compile modes and return structured diagnostics/evidence |
| `render_preview` | Render one allowlisted compiled target through the Preview adapter |
| `diagnose_layout` | Interpret render/layout tree and structured diagnostics using deterministic rules |
| `analyze_project` | Run the bounded, read-only Phase 2 project inventory and findings pipeline |

`generate_ui`, `debug_issue`, and automatic repair are initially client workflows over these
deterministic tools, not opaque model calls inside the server. This keeps providers replaceable and
makes every step inspectable. Conversion tools enter only with Phase 4 evidence.

Before implementing `diagnose_layout`, the Phase 3 corpus freezes one accepted Preview protocol v1
snapshot containing non-expected partial clipping and intentional text ellipsis. The associated
`layout.diagnosis-exactness` metric requires an exact stable-code match ratio of 1.00. The tool may
interpret renderer-produced facts, but it may not infer geometry from source or silently add
model-derived findings to that denominator.

Before publishing consumer skills, the corpus also freezes five distinct workflows for exact API
reference, screen creation, review, layout debugging, and delivery validation. Their required and
conditional tools, minimum and maximum evidence, mutation authority, shared stop condition, and
exact `current-source` selection form the denominator for `workflow.contract-completeness`, whose
required exact-match ratio is 1.00.

Before packaging, the corpus freezes one dependency-free local npm distribution with eight tools,
five skills, two explicit executable modes, SHA-256 archive and file integrity, SPDX 2.3 and MIT
license inventory, repeat-build identity, offline installation and uninstallation, and installed
stdio checks for every supported modern and legacy MCP version. Compile and Preview execution must
remain source-bound and require an explicit matching ViewCompose checkout; packaging cannot relabel
their evidence as standalone. The four distribution metrics require zero archive mismatches and
exact lifecycle, inventory, and protocol ratios of 1.00.

### Additional deliverables

1. A local stdio MCP server with per-request version metadata, capability discovery, explicit
   version selection, legacy-client compatibility, structured errors, cancellation, progress,
   output limits, safe logging, and no default network listener.
2. A stable CLI over the same service/core for CI, debugging, and clients without MCP.
3. Client-neutral consumer skills for creating a screen, retrieving API, reviewing code, debugging
   layout, and validating before delivery. Contributor workflows remain separate from framework
   consumer workflows.
4. Thin documented adapters for supported coding agents. Repository `AGENTS.md` continues to govern
   contribution; provider-specific root files are not added merely as aliases.
5. Packaging, checksums, SBOM/license review, installation/uninstallation, protocol compatibility,
   offline operation, and a minimal end-to-end example in CI.

### Implementation evidence — deterministic retrieval and CLI slice

The first Phase 3 slice adds one shared retrieval core over the accepted Knowledge Bundle. Before
building indexes, it verifies the exact seven-file manifest set, every byte count and SHA-256,
parsed record counts, and the aggregate bundle fingerprint. It exposes fixed input schemas for
`get_api_reference`, `get_component_reference`, `search_component`, and `get_sample`; the internal
CLI dispatches those same functions through the Phase 0 request/result envelope. No retrieval path
reads canonical source outside the bundle or performs network, Gradle, model, or project work.

Exact API retrieval distinguishes symbol, capability, and artifact identities while preserving the
artifact's current published version separately from a capability's recorded version state.
Component retrieval parses overload parameters and defaults, requires explicit disambiguation for
receiver families, attaches artifact/capability ownership, includes the declared compiled or
non-executable sample, and labels signature-derived rule applicability. Sample retrieval never
presents an architecture outline as compilable code. Ranked search supports bounded artifact,
artifact-version, capability, and kind filters, stable lexical scoring, and deterministic tie
breaks in the exact `current-source` lane.

On 2026-08-29, Node 25.6.0 passed 52/52 AI-tooling tests in 1.42 seconds. The two frozen retrieval
cases both passed: the remembered stale `Column` package still resolved the governed current symbol
at rank 1, and the layout intent resolved `modifier.layout` at rank 1. Top-five recall was 1.00
against the frozen 0.95 threshold; exact-symbol reciprocal rank was 1.00 against the 1.00 threshold.
The compiled quality-build suite and root `verifyAiRetrieval` task passed, and the gate is now owned
by `qaQuick`; the root lifecycle completed 2,272 tasks (2,182 executed and 90 up-to-date) in 11
minutes 57 seconds after the local incremental cache was invalidated. Compared with the bundle-only
baseline, retrieval changed from unavailable to deterministic, integrity-checked, and measurable;
the conclusion is **improved** Agent-facing knowledge access with **no material runtime change**
because the code remains downstream tooling.

Limitations: this first ranker is lexical and primarily serves canonical English names and terms;
it does not claim fuzzy, multilingual, embedding, or model-semantic retrieval. Only the exact
`current-source` bundle is selectable. Rule applicability is labeled as general, component, or
signature-derived rather than inferred as typed program behavior. The CLI is still internal and
unpackaged; at this slice, MCP transport parity and client workflows remained pending. The MCP
slice below closes the transport-parity requirement without changing the ranker's stated limits.

### Implementation evidence — dual-era stdio MCP slice

The second Phase 3 slice freezes a local stdio contract and one seven-tool catalog shared by the
CLI and MCP. It implements the current MCP `2026-07-28` stateless model: `server/discover` reports
the supported versions and fixed capabilities, while every modern request independently declares
its protocol version and client capabilities. It also supports the exact `2025-11-25` legacy
`initialize`/`initialized` lifecycle for clients still migrating, but never infers a downgrade or
uses legacy connection state for modern requests. The public render tool is accurately named
`render_preview` because this slice selects an allowlisted compiled Preview target; it does not
claim arbitrary snippet rendering.

Each newline-delimited stdio call creates the same immutable provider-neutral request consumed by
the internal CLI. Results are returned unchanged as MCP structured content and serialized text;
semantic parity excludes only elapsed wall-clock measurement. The server keeps a deterministic
tool order, JSON Schema 2020-12 input contracts, stable protocol versus actionable tool errors,
opt-in bounded progress, cancellation propagation into compiler/Preview child processes, and the
MCP rule that a cancelled call emits no later response. It rejects messages above 4 MiB, limits
concurrent calls to four, bounds tool output to 1 MiB before transport duplication, writes only MCP
JSON to stdout, logs no request content, and opens no network listener.

On 2026-08-29, Node 25.6.0 passed 65/65 AI-tooling tests in 1.35 seconds, including modern discovery,
unsupported-version recovery, deterministic listing, legacy lifecycle, CLI/MCP parity, tool versus
protocol errors, progress, cancellation, external abort propagation, catalog bounds, and an actual
stdio subprocess. The standalone Phase 3 MCP corpus reported seven tools and zero semantic
mismatches for `modifier.layout`; the compiled quality-build suite and root `verifyAiMcp` gate also
passed. The root `qaQuick` lifecycle executed the new gate and completed 2,273 tasks (2,183 executed
and 90 up-to-date) in 6 minutes 42 seconds. Compared with the retrieval-only baseline, a local Agent
can now discover and invoke the same accepted core over two explicit protocol eras with no semantic
fork; the conclusion is **improved** interoperability with **no material runtime change** because
the server remains downstream development tooling.

Limitations at this slice: only stdio was supported; HTTP, authentication, subscriptions,
resources/prompts, released-version Knowledge Bundles, installable packaging, checksums/SBOM, and
client adapters were not claimed. The seven-tool list intentionally omitted `diagnose_layout`
until the following slice could consume accepted Preview tree evidence. `generate_ui`, repair, and
conversion remained inspectable client workflows or later phases, not opaque server-side model
calls.

### Implementation evidence — deterministic layout diagnosis slice

The third Phase 3 slice adds `diagnose_layout` as the eighth shared CLI/MCP tool. A request selects
the same fixed Preview target and bounded configuration as `render_preview`; it cannot provide a
Gradle task, arbitrary file, render tree, image, or project path. The adapter renders or accepts a
verified cache entry, derives the only valid content-addressed tree path from repository-owned
target metadata, and then rechecks every path segment, byte count, SHA-256, render lane, target,
variant, and output identity before interpretation. Cache mutation between render and diagnosis
therefore fails closed.

The interpreter maps only Preview protocol v1 facts already measured after Android layout:
zero-size nodes, partial or full clipping, intentional container clipping, text ellipsis, clipped
text content, bounds, metrics, node identity, and matching source call sites. It does not inspect
pixels or source code, apply model judgment, or invent overlap, accessibility, touch-target, or
design-intent findings. Unknown kinds and malformed geometry are rejected instead of guessed. At
most 100 findings are returned with an explicit truncation diagnostic; a clean result means only
that the renderer emitted no structured layout diagnostic or warning.

On 2026-08-29, Node 25.6.0 passed 71/71 AI-tooling tests in 1.33 seconds. The frozen clipping and
ellipsis fixture produced both expected stable codes in deterministic severity/geometry order,
for an exact-match ratio of 1.00 against the required 1.00 threshold. The MCP parity verifier
reported eight tools and zero semantic mismatches. An end-to-end call over the real Counter target
revalidated the existing render cache, preserved output fingerprint
`bb7eba4f51d1aa4f788b0991b7c8635815d6943c374978b685f92619420841d0`, and returned a clean result
with zero layout findings. The compiled quality-build suite passed all tests in 10 seconds. The root
`qaQuick` lifecycle executed the new gate and completed 2,274 tasks (2,184 executed and 90
up-to-date) in 6 minutes 39 seconds. Compared with raw `render_preview` output, Agents now receive
bounded, source-aware repair facts without parsing renderer internals; the conclusion is
**improved** layout debuggability with **no material runtime change** because the implementation
remains isolated downstream tooling.

Limitations: only one allowlisted Counter Preview target and one labeled layout-diagnosis fixture
are accepted today. The tool reports renderer-owned layout facts, not arbitrary snippet rendering,
pixel comparison, accessibility conformance, overlap detection, or automatic repair. At this
slice, the next action was to publish the client-neutral consumer workflows implemented below.

### Implementation evidence — client-neutral consumer workflow slice

The fourth Phase 3 slice publishes five independently installable `SKILL.md` entrypoints for exact
API reference, screen creation, review, layout debugging, and delivery validation. They orchestrate
the accepted eight-tool core instead of copying framework APIs into prompt text. A machine-readable
manifest freezes each workflow's required and conditional tools, minimum and maximum evidence,
mutation policy, exact version selection, and repeated-diagnostic stop condition against the
pre-implementation corpus.

The entrypoints keep retrieval separate from proof: screen creation requires hermetic compilation;
review remains read-only unless a fix is also requested; layout debugging can use only an
allowlisted Preview that covers the affected code; and validation cannot turn a static pass into a
compiled or rendered claim. No provider-specific metadata or root alias was added, and no skill
grants project writes beyond the user's request. The deterministic gate rejects unknown tools,
evidence upgrades above rendered, manifest or folder drift, symbolic-link/path escape, oversized
entrypoints, missing safety boundaries, local absolute paths, and provider-specific instructions.

On 2026-08-29, the skill-creator structural validator accepted all five entrypoints. Node 25.6.0
passed 73/73 AI-tooling tests in 1.30 seconds, and the frozen workflow gate matched 5/5 contracts
for an exact-match ratio of 1.00 against the required 1.00 threshold. The compiled quality-build
suite passed all tests in 8 seconds. The root `qaQuick` lifecycle executed the new gate and
completed 2,275 tasks (2,185 executed and 90 up-to-date) in 6 minutes 49 seconds. Compared with ad
hoc prompting, the repository now provides bounded, evidence-aware consumer procedures that
preserve review versus mutation authority; the conclusion is **improved** workflow reproducibility
with **no material runtime change** because the skills and verifier remain downstream tooling.

Limitations: deterministic structure checks prove contract presence, not that every model/client
will follow the workflow correctly. No provider adapter or provider-specific behavior claim is made;
the entrypoints remain portable protocol-level workflows. At this slice, the next action was the
packaging and compatibility implementation recorded below.

### Implementation evidence — reproducible distribution and compatibility slice

The fifth Phase 3 slice packages `@viewcompose/ai-tooling` version `0.1.0` as a local npm tarball
with no runtime dependency. Its exact 34-file allowlist contains the eight-tool CLI/MCP core, five
skills, immutable Knowledge Bundle, two required schemas, MIT license, deterministic distribution
metadata, SPDX 2.3 package record, and reviewed empty third-party runtime inventory. The packager
rejects symbolic links, non-regular inputs, path escape, file-set drift, dependency drift, broad
output roots, and disagreement between staged and npm-packed file lists. Every file receives a
SHA-256 record; the archive and external manifest receive an unsigned `SHA256SUMS` sidecar.

The installed executables resolve npm-created symbolic links before entering the CLI or stdio
server. Retrieval, static validation, and project analysis are standalone. Compile and Preview
remain explicitly source-bound through `VIEWCOMPOSE_SOURCE_ROOT`, the pinned JDK/Android/Gradle
lane, and the existing evidence contracts. A configured checkout must contain regular wrapper and
settings files plus the exact Knowledge Bundle source revision in its Git ancestry; mismatch fails
before Gradle, and the package never upgrades those modes implicitly. The verification lifecycle
builds twice, compares full archive bytes, installs from the local tarball with npm offline mode and
an unreachable registry, revalidates every installed byte, retrieves `Column` and its compiled
sample, rejects one mismatched checkout, compiles the frozen UI Foundation example, exercises
modern `2026-07-28` and legacy `2025-11-25` stdio discovery, uninstalls offline, and checks that
package and binary paths are absent.

On 2026-08-29, both clean builds produced the same 236,152-byte archive with SHA-256
`286d97e2f88b9827b45f6bca9c7c2f79c9eb63e859b8bb112009b61835a0eb70`. The offline lifecycle,
SPDX/license inventory, and both installed protocol lanes each matched their complete frozen
denominator for exact-match ratios of 1.00; the installed compile example returned fingerprint
`9877c5a41372f6a77423071dc79cad680daa6febb3f7621cf2b1d755d9481acb`. Node 25.6.0 passed
75/75 AI-tooling tests in 1.40 seconds, the compiled quality-build suite passed all tests in 8
seconds, and the combined tooling, distribution, documentation, isolation, and release-intent gates
passed 23 tasks (9 executed and 14 up-to-date) in 35 seconds. Compared with the source-tree-only
baseline, the result is **improved** distribution reproducibility and interoperability with **no
material runtime change** because the complete package and installation gate remain downstream
development tooling. The first root lifecycle attempt exhausted the local disk after 2,214 tasks
and 7 minutes 47 seconds rather than reporting a code failure. After deleting only reproducible
worktree outputs and three incomplete Gradle transforms, the incremental retry passed all 2,276
tasks (325 executed and 1,951 up-to-date) in 3 minutes 50 seconds. After adding the source-checkout
identity rejection, the final lifecycle rerun again passed all 2,276 tasks (263 executed and 2,013
up-to-date) in 3 minutes 51 seconds; disk capacity is therefore an environmental limitation and the
final successful rerun is the accepted root evidence.

Limitations: the artifact is local and unpublished; `SHA256SUMS` is not signed. The evidence covers
Node 25.6.0 on macOS, not every engine-compatible Node release, Windows npm shims, public-registry
installation, upgrade migration, package signing, vulnerability-feed review, or branded client UI.
Compile and Preview still require the matching source checkout and prepared offline toolchain. The
installed end-to-end example proves retrieval, sample lineage, and compilation; installed rendering
continues to rely on the separately accepted source-bound Preview gate. Phase 4 starts with the
Design IR and XML migration contract freeze.

### Acceptance gate

- CLI and MCP produce semantically identical results for the same request and bundle fingerprint.
- The evaluation corpus proves retrieval, validation, compile, render, cancellation, and error
  behavior across every supported client/version lane.
- A clean sample project can ask an agent for a Material 3 screen, retrieve real APIs and samples,
  compile it, render when supported, repair failures, and deliver evidence without a fabricated API.
- The Foundation MVP is not declared complete until security, packaging, documentation, and
  release-runtime isolation gates pass.

## Phase 4 — Design IR, XML migration, Compose mapping, and analysis

### Purpose

Build deterministic migration value on top of the accepted knowledge and validation loop. Android
XML is first because it has explicit structure and resources and addresses an existing migration
need; automatic Compose conversion follows only after explicit semantic mappings exist.

### Contract freeze — Design IR v1 and Android XML layout v1

The 2026-08-29 Phase 4 contract freeze replaces open property bags with ordered typed IR fields for
literals, resources, dimensions, layout dimensions, enums, caller bindings, and preserved
expressions. Every source has a SHA-256 identity; every emitted node requires a source identity,
source span, confidence, and mapping decision. Unsupported fragments require a stable diagnostic,
preserved source, localization, and an explicit blocked or preserved disposition. Node IDs, field
names, modifier kinds, and modifier argument names are unique within their declared scopes.

The first XML subset is intentionally smaller than the eventual Phase 4B target. It accepts only
`LinearLayout`, `TextView`, `EditText`, and `Button`; fixed layout dimensions, one all-edge integer
`dp` padding, literal or unqualified string resources, four input types, and Android IDs. String
resources become explicit caller `String` parameters and remain named in the migration report;
editable state becomes a caller-owned `TextFieldState`. An absent click listener stays absent.
This keeps the first generated source inside the accepted Foundation compiler harness without
inventing an Android resource environment or application behavior.

Custom Views, Data Binding, unknown elements or attributes, unsupported values or namespaces,
`DOCTYPE`/entities, duplicate IDs, malformed XML, and resource-limit violations fail closed with no
Kotlin output. XML-only input cannot establish ViewBinding references or imperative call-site
listeners, so every successful result must carry that review limitation. The frozen denominator is
one four-node login golden with three string resources and one caller state binding, plus custom
View, Data Binding, and unknown-attribute rejection fixtures. Phase 4 begins with 27 total metrics,
22 evaluation cases, 19 fixture-backed cases, and four XML source fixtures; implementation may not
widen this subset silently.

### Contract freeze — Android XML project context v1

The second Phase 4 contract adds project evidence without weakening the accepted XML source subset.
Callers must provide one canonical project root, one project-relative layout, ordered explicit
resource roots, and ordered Kotlin or Java source roots. The resolver remains read-only, offline,
rejects symbolic links and root escape, never executes inspected-project Gradle logic, and never
chooses a variant implicitly. Only default `values` definitions select generation evidence;
qualified definitions are inventory-only. String identities remain preserved with their default
literals recorded as evidence, while finite non-negative `dp`, `sp`, and `px` dimensions may be
resolved without density conversion.

Style support is bounded to explicit unqualified `@style/name` references and explicit parents,
with a maximum 16-entry chain. Inline attributes override the selected style, which overrides its
nearest parent. Only attributes already owned by Android XML layout v1 are accepted. Cycles,
implicit dotted parents, theme attributes, aliases, package/framework resources, missing default
definitions, duplicate same-precedence definitions, formatted strings, plurals, arrays, markup,
and XLIFF fail closed. This resolves reusable declarations without pretending to reproduce AGP
resource merging or Android runtime selection.

The companion call-site inventory scans only declared Kotlin and Java roots and returns stable
locations plus snippet fingerprints for exact layout, ID, and resource symbols; ViewBinding naming,
listener registration, imperative mutation, and adapter assignment remain explicit candidates when
lexical evidence cannot prove ownership. Raw source is not returned. Coverage is always
`bounded-lexical` and completeness is always `not-proven`, so dynamic, reflective, generated,
excluded, and semantically linked code remains mandatory human/agent review work.

The frozen denominator adds one supported five-file project with four resources, two effective
styles, and seven call-site findings, plus style-cycle and theme-attribute rejection projects. The
public context example is byte-equivalent to the supported golden, and every input file, layout,
and source-line finding carries a SHA-256 identity. Phase 4 now has six schemas, 30 metrics, 25
evaluation cases, 22 fixture-backed cases, four base XML fixtures, and three project-context
fixtures. This is a contract-only **improvement** in measurable migration coverage with **no
material runtime or supported-tool behavior change** until the isolated resolver is implemented.

On 2026-08-29, Node 25.6.0 passed all 91 existing AI-tooling tests with the expanded contract, and
the compiled root contract, distribution, and documentation gates passed 21 actionable tasks. Two
package builds remained byte-identical after adding the project-context schema: the 40-file,
250,839-byte archive has SHA-256
`9167e42d60c77c7474e0e72479a01caa460c781d432b080dbf4c601a7882e1a7` and 1,441,250
declared file bytes. The installed offline lifecycle, both MCP eras, the independent compile
example, and the already accepted XML conversion still pass. This is **improved** distributable
contract visibility with **no material behavior change**; the archive remains local, unsigned, and
unpublished, and the new schema does not imply an implemented resolver.

### Implementation evidence — isolated Android XML project context

The accepted resolver canonicalizes one absolute project root and only normalized project-relative
layout, resource-root, and source-root paths. It rejects missing paths, root escape, symbolic links,
unsafe resource XML, style cycles, theme attributes, duplicate same-precedence resources, and
qualified-only resources before returning context. Traversal, file bytes, definitions, style depth,
call sites, and elapsed time all use frozen ceilings. Resource and source discovery is deterministic;
the context fingerprint covers every scanned layout, values file, and Kotlin or Java file in path
order. No project build, plugin, generated source, network client, or Android resource merger runs.

The resolver parses default string and dimension evidence, resolves explicit style-parent chains,
applies inline-over-style precedence, and rewrites only an internal XML copy. Removing the `style`
attribute uses whitespace preservation, inherited attributes are inserted without adding lines, and
dimension references become their bounded literal values; source-node line provenance therefore
remains stable. String references remain resources. The resolved styled-login source then passed the
existing XML-to-Design-IR converter as a vertical `Column`, retained its title string resource, and
introduced no new element mapping.

The read-only source inventory reports exact `R.layout`, `R.id`, and resource references separately
from candidate ViewBinding, listener, and mutation ownership. Each result records path, one-based
position, evidence kind, confidence, migration action, and a hash of the trimmed source line; raw
source is excluded from the context. Dynamic, reflective, generated, excluded, or semantically
related references remain unproven by design.

On 2026-08-29, Node 25.6.0 passed 96/96 AI-tooling tests. The dedicated project-context gate matched
1/1 deterministic golden with four resources, two styles, and seven call sites, plus 2/2
fail-closed unsupported projects. The compiled quality-build suite and new root lifecycle task
passed 18 actionable tasks (6 executed and 12 up-to-date) in 46 seconds. Compared with the frozen
contract, this is **improved** executable project evidence with **no material runtime or supported
CLI/MCP behavior change**. The result still does not reproduce AGP variant merging, resolve themes,
prove call-site completeness, or modify application files. At that slice boundary, styled
compilation and installed project-aware conversion remained pending; the next evidence section
records their completion.

### Implementation evidence — project-aware XML conversion

`convert_xml_to_viewcompose` now accepts exactly one of two schema-selected inputs. Source input
retains the accepted `source`, logical `path`, and explicit `generate` or `compile` behavior.
Project input requires an absolute `projectRoot`, project-relative `layoutPath`, ordered explicit
`resourceRoots`, optional ordered `sourceRoots`, and the same explicit mode. Mixing both forms or
omitting either form fails schema validation. CLI and both MCP protocol eras continue to share the
same catalog and dispatcher.

Before Design IR conversion, project input runs the accepted resolver, uses only its internal
style-expanded XML, and returns its schema-validated context as evidence. The migration report now
records the context fingerprint, resource/style/call-site counts, `not-proven` completeness, and
the complete bounded call-site inventory without raw source. The outer tool may own a 120-second
compile request while its project scan is independently tightened to the frozen 10-second maximum;
the installed modern MCP test caught and fixed this boundary composition before acceptance.

The supported project generates the exact frozen `StyledLoginView` Kotlin golden with three string
parameters, one caller-owned `TextFieldState`, inherited `16.dp` padding, preserved IDs, and seven
review call sites. The dedicated JDK 21 gate matched 1/1 deterministic context and Kotlin golden,
four resources, two styles, seven call sites, 1/1 hermetic compile, and 2/2 fail-closed unsupported
projects. Its identities are context
`f635c856eab177a37aa29f1eb14bd096ca76e8dc0e3a99892574b00f2c90a14e`, Kotlin
`8698ad4f919b8dbbaf92fc2487972d54c599706a0c5024acd192aa9fd741f4fe`, and classes
`e30210ebcf946e11e4b47327504999bed20c918e164713d1cf0102544cc97987`.

On 2026-08-29, Node 25.6.0 passed 100/100 AI-tooling tests and the full Phase 0 contract verifier.
The root project-context and documentation-structure gates passed 20 actionable tasks (6 executed
and 14 up-to-date). The XML consumer Skill now selects explicit project evidence when the scoped
layout is available, preserves standalone pasted-source input, and reports `not-proven` call-site
completeness; its independent validator passed and the workflow gate retained 6/6 exact contracts.
Two clean distribution builds were byte-identical: the 41-file, 261,076-byte
archive has SHA-256
`2118765a51bcd05450e1f0a0a759f1a55521509f79e926e9183b3a7f599d4cf8` and 1,485,644
declared file bytes. Its offline install/uninstall, SPDX/license inventory, both MCP eras,
standalone generation, installed explicit-project generation, and existing compiled examples all
passed.

Compared with the isolated resolver, this is **improved** end-to-end migration evidence and
consumer interoperability with **no material runtime change** because all work remains in the
downstream package and no published Android artifact changed. Limitations remain explicit: the
subset does not emulate AGP variants or resource merging, resolve themes or qualified defaults,
prove call-site completeness, edit host source, render the generated project screen, or establish
visual/accessibility parity. At that slice boundary the next action was a new basic container,
image, accessibility, and resource contract; the following section records that freeze before
implementation.

### Contract freeze — Android XML layout v2

The next compatible XML subset is frozen separately from `android-xml-layout-v1`, so the accepted
login denominator and existing gates remain immutable while implementation is pending. Layout v2
adds only `FrameLayout` mapped to ordered-overlay `Box`, `ImageView` mapped to `Image`, and the common
`android:visibility` attribute. `FrameLayout` accepts the already bounded all-edge padding.
`ImageView` accepts one unqualified `@drawable/name`, one of `fitCenter`, `centerCrop`, `fitXY`, or
`centerInside`, and a content description that is a literal, an unqualified string resource, or
explicit `@null` for decorative content.

Drawable identity is preserved as a caller-owned `ImageSource` parameter instead of inventing an
Android `R` class inside the hermetic compiler. String identities remain caller-owned `String`
parameters. Visible nodes omit a redundant modifier; `invisible` and `gone` map to ViewCompose's
native visibility modifier. An `ImageView` that omits `android:contentDescription` fails closed with
`VC-AI-XML-ACCESSIBILITY-REQUIRED`; the converter may not silently choose decorative semantics.
Unknown scale types, qualified/package resources, source selectors, tint, layout gravity, and
style-supplied v2 attributes remain outside this increment.

The frozen positive denominator is a three-node profile card with one `FrameLayout`, one cropped
image, one gone text node, one drawable binding, two string bindings, exact Design IR v1 provenance,
and exact intended Kotlin. The negative denominator is an image with a drawable source but no
accessibility decision. This expands Phase 4 to 27 evaluation cases, 24 fixture-backed cases, four
base XML v1 fixtures, two XML v2 fixtures, and three project-context fixtures while retaining the
same 30 metrics.

On 2026-08-29, Node 25.6.0 passed 100/100 AI-tooling tests and the expanded Phase 0 verifier. The
compiled root contract and documentation-structure gates passed 20 actionable tasks (6 executed
and 14 up-to-date). Compared with the previous denominator, this is **improved** measurable basic
container, image, accessibility, and resource coverage with **no material tool or runtime behavior
change**: the v2 fixtures and intended goldens are contract evidence only, are not in the installed
runtime package, and are not yet accepted by the parser or generator. The next action is exact
implementation plus hermetic compilation; no broader XML feature may bypass that boundary.

### Implementation evidence — Android XML layout v2

The dependency-free parser now maps `FrameLayout` to ordered-overlay `Box`, maps `ImageView` to
`Image`, and applies non-visible Android visibility as ViewCompose's native visibility modifier.
`@drawable/name` remains a typed IR resource and becomes a caller-owned `ImageSource`; it never
becomes a fabricated numeric resource ID. The four accepted scale types normalize to typed IR and
the exact `ImageContentScale` values. `visible` is omitted, while `invisible` and `gone` retain
distinct layout behavior.

Accessibility is an input contract, not a post-generation lint suggestion. A non-empty literal or
string resource becomes the `Image` content-description argument and image semantic role. Explicit
`@null` remains a decorative image with no image semantics. A missing or empty description returns
`VC-AI-XML-ACCESSIBILITY-REQUIRED`, preserves the localized source fragment, and emits no Kotlin.
Project mode composes the same v2 mapping with explicit resource roots: a temporary project fixture
resolved both string resources, preserved the drawable identity without pretending to merge it,
returned `not-proven` call-site completeness, and generated the same typed function.

On 2026-08-29, Node 25.6.0 passed 106/106 AI-tooling tests. The Design IR gate matched 2/2 schema
goldens, 2/2 deterministic outputs, 7/7 provenance-complete nodes, 2/2 resource denominators, and
4/4 fail-closed unsupported fixtures. The XML gate matched 2/2 Kotlin goldens, 2/2 resource reports,
and 2/2 hermetic compiles. The profile-card Kotlin fingerprint is
`15b15098e92b62bc9730ab7b3f2bde7715596f22069490a18b1e7830ff92ad35`; its class fingerprint is
`6020181fabf964e19c54c2a9a6ff8034657cb89ec338f48c9de25a41b9af04d4`.

The installed distribution generated v2 through both CLI and modern MCP and compiled it through
the matching source checkout. Two clean builds remained byte-identical: the 41-file,
262,894-byte archive has SHA-256
`f1d2724d17073ce6804ec21b40951b73dc68cd12244546c0d1e70514576e8fab` and 1,494,896
declared file bytes. The combined Design IR, XML compile, distribution, and documentation gates
passed 22 actionable tasks (8 executed and 14 up-to-date).

Compared with the contract-only denominator, this is **improved** executable container, image,
accessibility, and resource fidelity with **no material Android runtime change** because only the
downstream tooling package changed. Limitations remain explicit: no visual or device parity is yet
claimed; source selectors, tint, gravity, qualified drawables, style-supplied v2 attributes,
includes, merge roots, and Android resource merging remain unsupported. The next foundational gap
is a frozen explicit-root layout dependency graph for bounded `include`/`merge` expansion; the
resolver must not traverse layout dependencies before that contract exists.

### Contract freeze — Android XML layout dependencies v1

The project-only dependency contract now freezes the missing boundary before any resolver follows
an `include`. Callers must provide one project root, the root layout path, and ordered explicit
resource roots. Only unqualified `@layout/name` references in default `layout/` directories are
selectable; qualified directories remain inventory evidence, the first declared resource root wins,
and duplicate candidates at the same precedence fail closed. The resolver remains read-only,
offline, rejects symbolic links, and never executes Gradle, AGP, resource merging, or automatic
variant selection.

An `include` accepts only its `layout` attribute and preserves an ordinary included root. A `merge`
root is valid only when reached through an `include`; it splices its ordered children into that
position and accepts no semantic attribute beyond the Android namespace declaration. Source-only
conversion containing an `include`, standalone `merge`, missing layouts, cycles, unsupported
include attributes, or any dependency ceiling violation returns a stable fail-closed diagnostic and
no generated Kotlin. The ceilings are 64 layout files, 16 include levels, 256 dependency edges, and
1 MiB of expanded input.

The schema records the selected path and SHA-256 for every layout, ordered include edges with their
original one-based source positions, explicit selection completeness, and a canonical graph
fingerprint. The positive denominator is a three-file screen: its root includes one ordinary
`FrameLayout` profile header and one `merge` action group. Its intended expanded result contains six
IR nodes and preserves one drawable and four string resources. The negative project denominator is
a two-file cycle; the existing screen is also the source-only rejection denominator. This expands
Phase 4 to seven schemas, 32 metrics, 30 evaluation cases, 27 fixture-backed cases, and three frozen
layout-dependency fixtures while retaining four base XML fixtures, two XML v2 fixtures, and three
project-context fixtures.

On 2026-08-30, Node 25.6.0 passed the expanded Phase 0 contract verifier with exact schema, fixture,
edge-position, file-fingerprint, graph-fingerprint, diagnostic, execution-boundary, and ceiling
checks. The schema also entered the installed offline distribution: two clean 42-file package builds
were byte-identical, with a 263,198-byte archive, 1,497,969 declared file bytes, and archive SHA-256
`3425c259291fac19754c15feaf578a56877deb74c1be4ff27eff50b3453fc482`. Offline install/uninstall,
SPDX/license inventory, both MCP protocol versions, the existing compiled sample, and both XML
compile lanes passed under JDK 21. The repository contract and documentation-structure gates passed
20 actionable tasks (6 executed and 14 up-to-date).

Compared with the v2 implementation denominator, this is **improved** measurable project layout
dependency coverage with **no material conversion or Android runtime behavior change**: the graph,
fixtures, metrics, and installed schema freeze intended behavior only. The next action is exact
resolver and expansion implementation plus hermetic compilation; no layout traversal may bypass
this graph contract.

### Implementation evidence — Android XML layout dependencies v1

The project converter now resolves one deterministic dependency graph before mapping any include.
It selects only default `layout/name.xml` files from ordered explicit resource roots, rejects every
symbolic-link segment, hashes each selected raw file, and traverses no qualified directory. The
first root containing an included layout wins. Ordinary included roots remain ordered nodes with
their namespace declaration removed from the expanded internal tree; included `merge` roots splice
their children at the original edge. Cycles, missing layouts, include overrides, standalone merge,
invalid or exceeded limits, and source-only includes fail before Kotlin generation.

Expansion does not synthesize a concatenated source file. Each parsed node carries its originating
project-relative path and source, so the six-node positive fixture retains exact provenance from
`screen.xml`, `profile_header.xml`, and `profile_actions.xml`. Duplicate IDs are checked after the
cross-file tree is assembled. Project context scans all three selected layouts for referenced
strings and IDs, while the migration report preserves one drawable and four string bindings. The
returned result includes both the schema-validated graph and its project-context evidence; no raw
application source enters either public evidence object. The second bounded expansion pass consumes
only the project resolver's in-memory, source-owned style/dimension results, so accepted v1 style
and string resolution also composes across an included layout without changing the raw graph
fingerprints or executing Android resource tooling.

On 2026-08-30, the dedicated gate matched 1/1 dependency graph, 1/1 exact Kotlin golden, 1/1
include/merge expansion with complete cross-file provenance, 1/1 resource denominator, and 2/2
fail-closed contract inputs. The generated Kotlin fingerprint is
`ac1ecc66785420b08c4bcb2c1486e49f3c651730a71c554c967f9e052c6ff6b8`; JDK 21 hermetic compilation
produced class fingerprint
`0da92c36e83b7f81d73dce57942f7378778b939cfed74052d2f46be43de330c8`.
Node 25.6.0 passed 115/115 AI-tooling tests, including first-root precedence, missing layout,
unsupported include override, standalone merge, symbolic-link, cycle, and runtime-ceiling cases.

The installed CLI generated and compiled the same dependency fixture, and modern MCP returned the
same two-edge graph without exceeding its frozen four-request concurrency ceiling. Two clean local
package builds remained identical: the 43-file, 266,988-byte archive has 1,520,468 declared file
bytes and SHA-256
`d1df410e100eb397681153c327ae5ab5ec105aa8d37780bd787dcf0e525908fd`. Offline lifecycle,
SPDX/license inventory, both MCP protocol versions, and all four installed compile denominators
passed. The quality-build plugin suite passed, and `verifyAiXmlLayoutDependencies` is now an owned
`qaQuick` dependency; its root execution passed 15 actionable tasks (1 executed and 14 up-to-date).
The final complete Design IR/project-context/layout-dependency/base-v2 XML stack passed 18
actionable tasks (4 executed and 14 up-to-date). After bounded cleanup of this worktree's
reconstructible build outputs recovered space for npm's atomic uninstall, the final distribution,
layout-dependency, documentation, and tooling-isolation run passed 22 actionable tasks (8 executed
and 14 up-to-date).

Compared with the graph-contract-only denominator, this is **improved** executable multi-layout
migration evidence with **no material Android runtime change**, because traversal, expansion,
generation, and compilation remain downstream tooling. Limitations remain explicit: style-supplied
v2 image/visibility fields are not an accepted subset, qualified layouts and Android resource
merging remain inventory-only, and compilation does not prove pixels, interaction, accessibility
behavior, or host call-site replacement. The next foundational gap is a generated screen Preview
lane that can render converter output without executing the inspected project build.

### Contract freeze — source-bound generated XML Preview v1

The next migration boundary is now explicit before converter output enters Layoutlib. Render mode
may accept only Kotlin produced by the same successful XML conversion. Its exact bytes, generator
function, artifact set, declared bindings, framework bundle, compiler lane, renderer lane, and one
frozen Preview configuration become a schema-validated, content-addressed request. A source or
function mismatch fails before Gradle runs. Callers cannot submit arbitrary Kotlin, a Gradle task,
a dependency coordinate, a build script, an output directory, or an inspected-project path.

The Preview wrapper is deterministic and has one public-static-compatible
`UiTreeBuilder.GeneratedXmlPreview()` entry point. Every generator-reported parameter must have
exactly one ordered binding with the same parameter, source identity, and type. V1 supports exact
`String` values and fresh `TextFieldState` values with explicit initial text. Missing, extra, or
duplicate bindings fail closed. `ImageSource` remains explicitly unsupported until an isolated,
offline asset-staging contract exists; the harness never fabricates a numeric resource ID, reads
an inspected project's resources, or downloads an image.

The tool-owned `:tools:ai-preview-harness` is the only accepted execution owner. It is fixed to JDK
21, AGP 9.1.1, Kotlin 2.2.10, Android 37/JVM 11 compilation, Preview protocol 1, Paparazzi
2.0.0-alpha05, Layoutlib 16.2.1, a 411 dp auto-height light `en-US`/LTR configuration, offline
dependency resolution, and one concurrent request. Evidence must progress through compilation to
`rendered`, reopen and verify both `preview.png` and `render-tree.json`, and return request,
generated-source, wrapper, PNG, tree, and aggregate output fingerprints without public absolute
paths.

The frozen positive denominator binds the existing four-parameter login Kotlin golden to an exact
816-byte wrapper. Its canonical request fingerprint is
`8b2d5460fea40ee539fc5aba01af5cac97d59002476c17b744fb7baa1144d063`, and the wrapper fingerprint
is `8d4ff9932ada6621a05b486a22d410d79a674db787c29ac22b1e7e4e0dcf8821`. Three negative
denominators preserve a missing state binding, an unsupported image binding, and forbidden caller
build-task selection. Phase 4 therefore has eight schemas, 35 metrics, 34 evaluation cases, 31
fixture-backed cases, and four generated-Preview fixtures in addition to the accepted XML,
project-context, and layout-dependency denominators.

On 2026-08-30, Node 25.6.0 passed the expanded Phase 0 verifier with exact schema, request,
generated-Kotlin, framework-bundle, configuration, lane, binding-set, wrapper, diagnostic, ceiling,
and isolation checks; all 115 Node AI-tooling tests remained green. The request schema entered two
byte-identical local package builds: the 44-file, 267,773-byte archive has 1,525,913 declared file
bytes and SHA-256
`c794cbe42ebc9a01427fdf82e63189990b0c36ec41e80e06f28e1be23460cf2e`.

This is **improved** measurable render readiness with **no material converter, Preview, or Android
runtime behavior change**: the schema and fixtures intentionally make no render success claim until
the harness produces accepted PNG/tree evidence. The next action is the fixed harness plus
`convert_xml_to_viewcompose` render mode; no alternate module or inspected-project build may
satisfy this contract.

### Implementation evidence — source-bound generated XML Preview

The accepted implementation adds one downstream Android application harness,
`:tools:ai-preview-harness`. A generated-screen request is converted into two immutable Kotlin
files under a content-addressed tool-owned directory: the converter's exact output and the
deterministic zero-argument Preview wrapper. The harness validates the request key and exact file
inventory before its debug source set is configured. The adapter alone selects the fixed discovery
and render tasks, aggregate current-source framework dependency, Preview worker, target owner,
method, configuration, and lanes; public CLI/MCP requests cannot select a task, dependency, build
script, project output, or arbitrary Kotlin source.

`convert_xml_to_viewcompose` now exposes `render` beside `generate` and `compile` for both standalone
source and explicit-project inputs. It first runs the same parser and generator, then requires an
ordered explicit binding for every generator-reported parameter. Exact `String` and fresh
`TextFieldState` values enter the wrapper; missing, extra, duplicate, reordered, source-mismatched,
type-mismatched, and image bindings fail before Gradle. The result keeps the migration IR, Kotlin,
report, and provenance while upgrading evidence only when compilation, discovery, Layoutlib render,
artifact reopening, and hash verification all succeed.

On 2026-08-30, the login denominator compiled and rendered at 411 dp, density 2.625, `en-US`, LTR,
and light theme into a 1,079 by 2,339 px, 38,919-byte PNG. Visual inspection showed the expected
title, text field hint, and sign-in button without clipping or corruption. The 202,604-byte render
tree reported five virtual and five mounted nodes, depth three, the expected title/action text, and
no warnings or layout diagnostics. The exact evidence is:

- request: `8b2d5460fea40ee539fc5aba01af5cac97d59002476c17b744fb7baa1144d063`;
- generated Kotlin: `6c4f6dafef9e0b4808eefab440d14e331b1a3b55bc8becff7a05d3669cc73be1`;
- wrapper: `8d4ff9932ada6621a05b486a22d410d79a674db787c29ac22b1e7e4e0dcf8821`;
- PNG: `e1efebaffa1efc19052a3fb1be33a8aa3fd670073a6330e976cd1be4082bb7fe`;
- render tree: `d0373c8499b9d46f9cafa98a04c6f30d41a8ec69743a5ada35496ba0e2e05e85`;
- aggregate render output: `6d2c8a5296db8cc95e5201092e40532f371f1d95621acd7bad343c913b4b9bab`.

The dedicated gate then reproduced 1/1 exact render, 1/1 second-request cache hit with the same
output, and 3/3 fail-closed missing-binding, image-binding, and caller-build-selection inputs. Node
25.6.0 passed 125/125 AI-tooling tests. The quality-build plugin suite passed, and root
`verifyAiGeneratedPreview` passed 15 actionable tasks (1 executed and 14 up-to-date). The packaged
CLI invoked the same render path from an offline isolated installation; two clean package builds
were byte-identical. The 45-file archive is 273,531 bytes, contains 1,550,545 declared file bytes,
and has SHA-256
`47a711ae59e1cd4bc03e29a521884ad53262ec4227006ecb984a7c160efe7742`. Offline installation and
uninstallation, SPDX/license inventory, both MCP protocol versions, four installed compile
denominators, and the generated Preview render all passed. The final combined generated-Preview,
documentation structure and translation, development-tooling isolation, and release-intent run
passed 22 actionable tasks (8 executed and 14 up-to-date).

Compared with compile-only XML migration, this is **improved** executable visual evidence with
**no material Android runtime behavior change** because the harness, adapter, CLI/MCP path, and
quality gate remain downstream development tooling. The result does not prove pixel parity against
the original XML, interaction, state restoration, accessibility traversal, alternate
configurations, or inspected-application integration. `ImageSource` was deliberately blocked,
which made bounded offline asset staging the next foundational increment rather than a broader
prompt or screenshot generator. The following contract and implementation close that gap.

### Contract freeze — isolated embedded PNG asset staging

The first generated-Preview asset lane is now frozen without broadening project or network access.
An `ImageSource` binding may carry only canonical RFC 4648 base64 for exact `image/png` bytes plus
the decoded byte count, SHA-256, and IHDR width and height. It accepts no filesystem path, URL, URI,
Android resource ID, project `R` symbol, XML/vector drawable, alternate media type, or loader model.
The converter and harness therefore cannot silently substitute an inspected project's drawable or
invent an asset when the binding is absent.

Before staging, the adapter must re-decode and re-encode canonical base64, verify byte count and
SHA-256, parse bounded PNG chunks, validate every CRC, require exactly one leading IHDR and terminal
IEND, and match the declared dimensions. Each image is limited to 524,288 decoded bytes and 1,024
by 1,024 pixels; one request permits at most 16 unique assets, 1,048,576 total asset bytes, and 256
chunks per PNG. Identical bytes deduplicate by full SHA-256. Accepted bytes are written once beneath
the request's immutable `res/drawable` directory as `vc_ai_<full-sha256>.png`; the deterministic
wrapper alone maps that generated resource through
`ImageSource.Resource(R.drawable.<resourceName>)`.

The contract-positive denominator uses the existing XML v2 `ProfileCardView` golden with one
70-byte, 1 by 1 px PNG. Its asset SHA-256 is
`4ff6ab670a58c14270e034e2090d9a432caa263a14e0a25785386b0c12f880b5`, canonical request
fingerprint is `1d81d2ed9db84ee022d806042cd883c426f4fe0061aa65c757ef0de3a91225f6`, generated Kotlin
fingerprint remains `15b15098e92b62bc9730ab7b3f2bde7715596f22069490a18b1e7830ff92ad35`, and the 917-byte
wrapper fingerprint is `461d7c9e7b9898b9b9f7373775fa10c8a180097664627b442d36a8b2abd2a4b2`.
The negative denominator preserves the same generator binding without bytes and requires
`VC-AI-PREVIEW-ASSET-MISSING` before Gradle execution.

Phase 0 now contains eight schemas, 36 metrics, 35 evaluation cases, 32 fixture-backed cases, and
five generated-Preview fixtures. The embedded asset is schema-valid, byte/hash/dimension exact,
CRC-valid, bounded, and source-matched. At contract freeze, the existing login render remained the
only implemented positive render. Node 25.6.0 passed 125/125 AI-tooling tests. Two clean
package builds remained byte-identical; the 45-file, 273,818-byte archive contains 1,551,859
declared file bytes and has SHA-256
`4522557fafe2351627371a169540e0572fdebd265f32a91b244cdf0cdbe68362`. Its offline lifecycle,
SPDX/license inventory, both MCP protocol versions, four installed compile denominators, and the
previous login Preview render all remained green.

The contract-only result was **improved** safety and measurability with
**no material Android runtime or accepted render behavior change**. Current limitations are
intentional: only embedded PNG is frozen, the contract does not read application resources or prove
the source XML's original pixels, and the profile-card request remained static unsupported evidence
until the following tool-owned resource staging and real Layoutlib gate passed.

### Implementation evidence — isolated embedded PNG asset staging

The generated Preview adapter now canonicalizes every embedded asset field, verifies the complete
bounded PNG contract, deduplicates exact bytes by full SHA-256, and persists assets with create-only
semantics under the request's content-addressed `res/drawable` directory. Existing bytes are
reopened and compared before reuse. Unexpected request-root, resource-root, drawable, or input
entries and any file or directory symbolic link produce cache-poison evidence before Gradle. Raw
base64 never enters the public result; only resource name, byte count, hash, and dimensions remain.

The harness mounts that request-owned directory as its debug Android resource source and validates
the exact directory and filename grammar before compilation. The wrapper imports only the harness
`R` class and constructs `ImageSource.Resource`; no image loader, inspected-project resource table,
project build logic, filesystem path, URI, or network client is added. Image-bearing requests carry
the exact `image.foundation` and drawing capability identities, while text-only login requests keep
their prior narrower capability set.

On 2026-08-30, the profile-card request compiled and rendered on the pinned lane. Its 1 by 1 px red
fixture expanded through the declared 96 dp cropped image region without corruption; the screenshot
was 1,079 by 2,339 px and 15,217 bytes. The 120,988-byte render tree contained three virtual and
three mounted nodes at depth two, preserved `Profile photo` as the image content description and
`Available` as the hidden text node, and reported no warnings or layout diagnostics. Exact accepted
evidence is:

- build: `76b256d15f1801358b009127e50467c5936af8b99714f6895e06dddef7a7b990`;
- aggregate output: `31fb45a13a4d35badee2cf61ce7760a0540b60ed2e0def2d3e3910cfdb4268f5`;
- PNG: `bb130675ac0de5df6ad6ff93ded020cbe93704a80030301da3a2d57a56b9cd3f`;
- render tree: `58bbd8da9df6295da2419dc85bf4c7d4636419f8022237740b694966763b31e9`.

The dedicated gate now reproduces 2/2 exact generated renders, 2/2 second-request cache hits, and
3/3 fail-closed missing binding, missing asset, and caller build-selection inputs. Node 25.6.0
passes 128/128 tests, including exact asset request/wrapper/resource planning, public schema
acceptance with path rejection, CRC tampering, immutable resource reuse, raw-byte redaction, and
symbolic-link cache poisoning.

The installed-package lifecycle exercises both generated screens through the same public CLI. Two
clean builds produced the same 45-file, 275,681-byte archive with 1,559,691 declared file bytes and
SHA-256 `11ce6376f6c0d5df91b74b3e0756200c222c9e2680752075793a4badb6f2d607`.
Offline installation, uninstall cleanup, SPDX inventory, both MCP protocol eras, compilation
fixtures, the login render, and the image render all passed. The quality-build plugin suite passed;
the combined generated-Preview, documentation-structure, development-tooling-isolation, and
release-intent root gate passed 22 actionable tasks, with eight executed and 14 up-to-date.

Compared with the text-only Preview lane, this is **improved** executable image evidence with
**no material Android runtime behavior change**: all new work remains in the downstream adapter,
harness, package, and quality gate. It still accepts only embedded PNG, not XML/vector drawables,
JPEG/WebP, arbitrary files, remote images, or application resource merging. It proves the generated
screen and its declared semantics, not pixel parity against the original XML or complete
accessibility behavior. The next foundational step is an exact semantic and geometry comparison
contract over Design IR and render-tree evidence before screenshot-driven generation or repair.

### Contract freeze — exact generated layout comparison

The first comparison contract is now frozen around evidence already owned by the XML conversion
request. Callers cannot submit a replacement Design IR, render tree, policy, artifact path, or
threshold. The comparator must reopen the exact content-addressed render tree, verify its SHA-256
and aggregate Preview identity, reject symbolic links, and compare it only with the canonical
compact fingerprint of the Design IR generated in the same request. A passing conversion advances
from `rendered` to `compared`; any mismatch retains only `rendered` evidence.

Node identity is exact and intentionally narrow. One leading `id:` is removed from a Design IR ID;
all other IDs are preserved and must resolve to exactly one authored virtual-node key. The only
v1 semantic-host exception is the current one-child `Column` wrapper around `TextField`; its
keyless child must have the same bounds. Kinds, parents, child order, visible text, content
descriptions, declared roles, and visibility are separate exact checks. String values resolve only
through the exact Preview binding source. Placeholder rendering absent from the tree, state and
event behavior, focus traversal, complete accessibility behavior, style, typography, and pixels
remain explicit non-claims.

Geometry uses integer render-tree coordinates in the accepted screenshot viewport. Declared dp is
rounded to the nearest pixel at the frozen density, with zero tolerance. V1 checks only applicable
exact dimensions, root and padded-child match-parent spans, observable uniform padding anchors,
containment, and vertical sibling order. Wrap-content records its observed bounds without claiming
a target size; `GONE` requires zero or absent visible bounds and makes size comparison
not-applicable. No aggregate score can hide a failed identity, structure, semantic, or geometry
check.

The two frozen denominators bind directly to the accepted login and profile-card renders. Login
maps 4/4 Design IR nodes and freezes 32 required checks, including the one allowlisted text-field
wrapper. Profile card maps 3/3 nodes and freezes 24 required checks plus one hidden-geometry
non-applicable result. Changed IR, changed render bytes or fingerprint, duplicate keys, kind drift,
and one-pixel exact-dimension drift are named failure denominators.

On 2026-08-30, Node 25.6.0 passed 128/128 tooling tests. Phase 0 now verifies nine schemas, 38
metrics, 37 cases, 34 fixture-backed cases, and two layout-comparison fixtures. Two clean package
builds produced the same 46-file, 276,927-byte archive with 1,567,175 declared file bytes and
SHA-256 `c45ff5c2431944f5501ee53428f21e055b882f017288a3203116ca7501a58a26`.
The offline lifecycle, installed compilation fixtures, both installed generated renders, SPDX
inventory, and both MCP protocol eras remained green.

This contract-only slice is **improved** comparison safety and measurability with **no material
Android runtime or accepted render behavior change**. It does not yet emit a comparison result or
upgrade the public conversion evidence. The next step is the bounded comparator, exact golden
results for both screens, and corruption, ambiguity, semantic, structure, and one-pixel failure
tests before any screenshot, prompt, or repair adapter is considered.

### Implementation evidence — exact generated layout comparison

The comparator now reopens the accepted render-tree artifact inside the configured ViewCompose
source root, rejects absolute or escaping paths and every symbolic-link segment, and verifies the
declared byte count and SHA-256 before parsing. It bounds Design IR, virtual, native, depth, check,
finding, and artifact denominators. Virtual node IDs, authored keys, native node IDs, bounds,
visibility, properties, and child arrays are validated before mapping; duplicate node identities,
unknown kinds, extra authored keys, unsupported synthetic nodes, and ambiguous mappings fail
closed.

Every comparison result keeps four separate check categories. Design IR IDs normalize to exact
authored keys, parent and child order are preserved, observable string resources resolve only from
the matching explicit Preview binding, and roles and visibility remain exact. Geometry derives
integer bounds from the accepted native tree, converts dp with the frozen density, accounts for
parent padding in match-parent and containment checks, and never assigns a target size to
wrap-content. The sole `TextField` wrapper exception requires one keyless semantic child and equal
identity/semantic bounds. `GONE` geometry is non-applicable only after both zero bounds and absent or
zero visible bounds are proven.

On the real pinned Layoutlib lane, the login input Design IR fingerprint is
`a938f6c0bd8333e195414353766d7e577bbcab0584c219cf4d123869192964d4`; all 4/4 nodes and
32/32 required checks pass, producing comparison fingerprint
`470b4e23384479ff29528fe311058618b6ace6536465aeaf08bb477a10cc737d`. The profile-card input
Design IR fingerprint is `8a860b20a34b87d0eae3918f12d1968e3653e0fe46da0cceffa68f70e9c25b09`;
all 3/3 nodes and 24/24 required checks pass, with one hidden-geometry non-applicable check,
producing comparison fingerprint
`6be3406d341e7e208501b95d1a42bfe15633f928c3b8cdc5cdc0d9ac6474752c`.
Both repeated conversions hit the existing render cache without weakening comparison.

Unit denominators independently fail a one-pixel exact-dp drift, changed text, authored child-order
drift, duplicate key ambiguity, changed render-tree identity, and symbolic-link evidence. Existing
missing binding, missing asset, and caller build-selection inputs remain 3/3 fail-closed before
comparison. Node 25.6.0 passes 135/135 tooling tests, and Phase 0 continues to verify nine schemas,
38 metrics, 37 cases, 34 fixture-backed cases, and two implemented comparison fixtures.

The public CLI/MCP conversion now advances to `compared` only on a complete pass. A mismatch returns
reason-coded findings while preserving the accepted render fingerprint and only `rendered`
evidence; the comparison fingerprint becomes the outer result identity only after success. The
installed-package lifecycle exercised both exact comparisons. Two clean builds produced the same
47-file, 283,631-byte archive with 1,596,570 declared file bytes and SHA-256
`b109ee20fbde9e2f891b1b414e15a63ae7a59d38f923a48633ba5dee90a90bcc`.
The quality-build plugin suite passed, and the combined generated-comparison,
documentation-structure, development-tooling-isolation, and release-intent root gate passed 22
actionable tasks, with eight executed and 14 up-to-date.

Compared with render-only evidence, this is **improved** semantic and geometry verification with
**no material Android runtime behavior change**: the implementation is a downstream, read-only
development tool over artifacts the Preview adapter already accepted. It does not establish
placeholder rendering, state/event behavior, full accessibility, style, typography, pixels, touch
targets, alternate configurations, or source-XML screenshot parity. Those limitations remain
separate denominators rather than being hidden by the passing deterministic checks.

### Contract freeze — deterministic screenshot preprocessing

The first Phase 5 input boundary is now frozen without adding a model, provider SDK, credential,
network call, or provider-specific request shape. Screenshot preprocessing v1 accepts only an
embedded canonical-base64 PNG with exact byte count, SHA-256, and dimensions. The caller must
declare density, font scale, locale, layout direction, sRGB color space, straight alpha, upright
orientation, system-bar insets, and a source-image-pixel crop. Paths, URLs, URIs, credentials,
provider transfer, persistence, and content-bearing logs remain schema-invalid.

Processing is deterministic and ordered: verify, decode, crop, apply explicit caller redactions in
cropped-output pixel coordinates, then encode. Version 1 accepts only non-interlaced 8-bit RGBA PNG,
bounds compressed input and output to 1.25 MiB, dimensions to 4,096 px, decoded data to 16 MiB, PNG
chunks to 256, and redactions to 64. It performs no resize or automatic system-bar/sensitive-content
inference. Canonical output contains only `IHDR`, `IDAT`, and `IEND`, uses PNG filter 0 and zlib level
9, and carries the output-byte SHA-256 plus key-order-independent canonical request and result
fingerprints. The 1.25 MiB image ceiling and 2,000,000-byte tool-result ceiling keep the duplicated
structured/text MCP response below the frozen 4 MiB stdio message limit with 194,304 bytes of
headroom.

The accepted 4×4 privacy-grid input is 112 bytes with SHA-256
`ff96bfc58337301e15ff1515d39a2653a855a46ef74e50f8884889cd28f21cc0`. Cropping the full image
and replacing the explicit central 2×2 rectangle with opaque black produces the exact 106-byte PNG
with SHA-256 `201c08259fb2891c57c3f85e0f9e1157ad9df9ae8303c4f8d679735cf2850b99`, request
fingerprint `e9db4c486dbcaa59cd214b557cca19fb4878f66eb668f9b94cbd14a4ca6dd77f`, and result
fingerprint `74d3e3190dca4157d07cefd51f9a3a809094dad93785cef3c327f566a6e832b1`. The contract verifier
checks canonical base64, PNG signature/chunks/CRC, image format, bounded inflate size, every PNG
filter reconstruction, crop/redaction bounds, exact output pixels, transformation order, privacy
record, and both fingerprints. Separate fixtures keep absolute-path input and provider transfer
schema-invalid with reason-coded expected diagnostics.

Node 25.6.0 remains at 135/135 passing tooling tests. Phase 0 now verifies 10 schemas, 41 metrics,
40 cases, 37 fixture-backed cases, and three screenshot-preprocessing fixtures. The installed
package lifecycle still passes 2/2 reproducible builds, offline installation/uninstallation,
SPDX/license inventory, both MCP protocol eras, compiled migrations, and both exact generated-layout
comparisons. Relative to the preceding comparison slice, the package adds one contract file: file
count increases from 47 to 48 (+2.13%), declared bytes from 1,596,570 to 1,604,880 (+0.52%), and
archive bytes from 283,631 to 284,567 (+0.33%). The new archive SHA-256 is
`ffe2c17ada8c13267047ca6b01a47f9a5387441afc8570f9ca1375c150ae22a1`. The quality-build
plugin test suite passed, and the combined AI-contract, installed-distribution,
documentation-structure, development-tooling-isolation, and release-intent root gate passed 23
actionable tasks, with nine executed and 14 up-to-date.

This is **improved** input integrity, privacy, and evaluation measurability with **no material
execution or Android runtime behavior change**. At this contract-only point, no screenshot had been
converted to Design IR, compiled, rendered, compared, repaired, or sent to a provider. The following
implementation closes only deterministic preprocessing.

### Implementation evidence — deterministic screenshot preprocessing

`prepare_screenshot` is now the tenth public CLI/MCP tool. Its dependency-free Node adapter parses
PNG chunks in memory, verifies canonical base64, bytes, SHA-256, ordering, critical-chunk support,
CRC, dimensions, bounded decompression, complete zlib consumption, color/animation semantics, and
zero-or-one valid sRGB intent, then reverses filter types 0–4. Embedded profiles, conflicting color
chunks, auxiliary transparency, and APNG are rejected rather than silently flattened. It applies
the declared source crop, fills only explicit cropped-output redaction rectangles
with opaque black, and re-encodes a metadata-free `IHDR`/`IDAT`/`IEND` PNG with filter 0 and zlib
level 9. It never accepts a file locator, opens a project, writes an image/cache, calls a network,
or transfers content to a provider.

Node 25.6.0 passes 143/143 tooling tests. The focused acceptance gate reproduces 1/1 golden,
3/3 repeated/key-order-independent runs, 2/2 privacy denials, 1/1 changed-identity denial, and 1/1
cancellation. Unit denominators additionally exercise all five PNG filters, ancillary metadata
stripping, changed CRC, unsupported color type/profile, APNG semantics, out-of-bounds crop, and
out-of-bounds redaction.
Phase 0 remains at 10 schemas, 41 metrics, 40 cases, 37 fixture-backed cases, and three implemented
screenshot-preprocessing fixtures.

The installed package reproduces result fingerprint
`74d3e3190dca4157d07cefd51f9a3a809094dad93785cef3c327f566a6e832b1` through both the CLI and
the preferred MCP protocol while the legacy protocol lists the same ten-tool catalog. The complete
offline lifecycle still passes 2/2 reproducible builds, SPDX/license inventory, compilation and
render comparisons, install, and uninstall. Relative to the contract-only slice, package file count
increases from 48 to 50 (+4.17%), declared bytes from 1,604,880 to 1,627,459 (+1.41%), and archive
bytes from 284,567 to 290,571 (+2.11%). The implemented archive SHA-256 is
`b158057876bfb3d756038c4f0d525464df32dec85b00d142be982b8f4bd61968`. The quality-build
plugin suite passed. The combined AI-contract, screenshot, installed-distribution,
documentation-structure, development-tooling-isolation, and release-intent root gate passed 24
actionable tasks, with 10 executed and 14 up-to-date.

This is **improved** deterministic screenshot integrity, redaction, transport safety, and installed
tool usability with **no material Android runtime behavior change**. It does not infer nodes, text,
resources, semantics, state, behavior, or confidence; generate Kotlin; render/compare a reconstructed
screen; call a model; or authorize provider transfer. The next action is a provider-neutral
screenshot-to-Design-IR request/result and consent contract that keeps those uncertainties visible.

### Contract freeze — screenshot-to-Design-IR inference

Screenshot-to-Design-IR inference v1 now freezes the provider-neutral request, result, evidence,
uncertainty, and authorization boundary without exposing a public tool or selecting a model. The
request accepts only the exact canonical PNG, preprocessing request fingerprint, and preprocessing
output fingerprint produced by `prepare_screenshot`. Its authorization binds the reviewed input to
that exact output fingerprint. A changed lineage identity fails even when the mutated request still
conforms to JSON Schema; caller paths and credentials remain outside the accepted shape.

The offline human-reviewed golden begins with a 16×24, 130-byte preprocessed PNG whose SHA-256 is
`db28e5a95b48fcbdde009f078295db924a48fde252ed5205a266b187b980f6d3` and whose preprocessing
result fingerprint is `58c45a3ce39b74fc9585132ac912fb8c915ac0a0334f4151f4cd1b1f51a87bb3`. The inference request
fingerprint is `f789490fa61fa8d6a74e546b8defa536a78c9cebc83a123ba70da9967030a62b`. It yields four Design IR
nodes and exactly four evidence records. Every node owns one in-bounds pixel rectangle, and its
Design IR `sourceId`/`sourceSpan` must match the screenshot SHA-256 and exact
`pixels:x,y,width,height` evidence region. The Design IR fingerprint is
`585b3d1761cc47f9718ff48e09216899faa470ca662e4e98ad705c8686109b5a`.

Confidence remains dimension-specific for asset, content, geometry, semantics, structure, and
style; version 1 defines no aggregate score. The golden deliberately preserves six unsupported
semantics and six blocking questions for text, field purpose/state/behavior, button
label/behavior, and accessibility. Unknown values use placeholder bindings, all unsupported
entries remain blocked, all questions forbid invented defaults, and code generation stays false.
The incomplete result fingerprint is
`4bd30960cccdfe3b9a4402293b3739a3238a25fcef12fb2911c595a3df7a66c0`.

Human-golden authorization performs zero provider transfers, zero network requests, zero input or
output persistence, and metadata-only logging. The future provider-adapter shape requires an
explicit provider ID, an exact-input consent receipt, the approved `screenshot-to-design-ir`
purpose, completed retention review, immutable model and provider request/response identities, and
no raw request or response persistence. This schema permits a future authorized adapter contract;
the current execution contract keeps provider selection and execution false. Dedicated invalid
denominators reject provider transfer without consent, any credential-shaped input, and a changed
preprocessing output fingerprint.

Node 25.6.0 passes 144/144 tooling tests. The focused preprocessing gate now reproduces 2/2
goldens, 5/5 repeated or key-order-independent runs, 2/2 privacy denials, 1/1 changed-identity
denial, and 1/1 cancellation. The inference gate accepts 1/1 human golden, verifies 4/4
node/evidence records and all six blocking questions, rejects 3/3 failure denominators, and records
zero provider executions and zero network requests. Phase 0 verifies 11 schemas, 45 metrics, 45
cases, 42 fixture-backed cases, four screenshot-preprocessing fixtures, and four screenshot-
inference fixtures. The fixed generated-Preview lane remains green at 2/2 exact renders, 2/2 stable
cache hits, and 3/3 unsafe or unsupported failures.

The complete offline distribution lifecycle passes 2/2 reproducible builds, installation and
uninstallation, SPDX/license inventory, both MCP protocol eras, compilation, and both exact layout
comparisons. Relative to the implemented preprocessor slice, the package adds the frozen inference
schema and updates its existing README contract: file count increases from 50 to 51 (+2.00%),
declared bytes from 1,627,459 to
1,643,944 (+1.01%), and archive bytes from 290,571 to 292,512 (+0.67%). The archive SHA-256 is
`535eb785b54b117db47ea2f38adca14d0048ab86f791675727b71aac06357d72`. The quality-build plugin
suite passed. The combined AI-contract, preprocessing, inference, installed-distribution,
documentation-structure, development-tooling-isolation, and release-intent root gate passed 25
actionable tasks, with 11 executed and 14 up-to-date.

This is **improved** lineage integrity, evidence completeness, uncertainty honesty, consent safety,
and evaluation measurability with **no material Android runtime behavior change**. It is still a
reviewed contract fixture, not a claim that pixels prove text, behavior, state, resources,
accessibility, or production visual fidelity. It does not call a model, generate Kotlin, compile a
reconstructed screen, render or compare that screen, or authorize any provider. The next action is
an offline validator/import adapter that can accept externally produced results only after all
frozen schema, lineage, evidence, uncertainty, and authorization checks pass.

### Implementation evidence — offline screenshot inference validation

`validate_screenshot_inference` is now the eleventh public CLI/MCP tool and remains an entirely
offline import boundary. Its input contains the original preprocessing request, a compact inference
declaration, and an externally produced raw result. The adapter reruns `prepare_screenshot` and
reconstructs the full inference request internally instead of accepting a caller-supplied duplicate
of the preprocessed PNG. It verifies both request and result schemas, the accepted framework bundle
and lane, canonical request/result/Design-IR fingerprints, exact preprocessing lineage, every
node/evidence/source-region relationship, dimension-specific confidence, blocking questions,
forbidden defaults, unsupported semantics, summary counts, code-generation status, and producer
authorization before returning imported Design IR. Behavior, executable expressions, resolved
resource bindings, changed lineage, missing evidence, out-of-bounds regions, and malformed consent
fail closed with stable diagnostics.

The human-reviewed golden imports deterministically twice with validation fingerprint
`556c13d133d63e34fa81d1c04df3bee938509c5ced1d244ccf2366d48cb6e845`. The focused gate also
accepts one externally produced provider-provenance result only when its immutable provider/model
identities and consent receipt bind to the exact preprocessed input. This proves import validation,
not provider operation: the adapter performs zero provider executions and zero network requests.
Credential-shaped input, missing consent, and changed preprocessing lineage supply 3/3 explicit
failure denominators. The imported golden remains incomplete with six blocking questions and
`codeGenerationAllowed` false; successful validation never upgrades uncertain pixels into behavior,
state, resource, accessibility, or production-fidelity claims.

Node 25.6.0 passes 153/153 tooling tests. Phase 0 verifies 11 schemas, 46 metrics, 45 cases, 42
fixture-backed cases, four screenshot-preprocessing fixtures, and four screenshot-inference
fixtures. The installed package returns the same validation fingerprint through both the CLI and
preferred MCP protocol, retains both supported MCP protocol eras, and passes the complete 2/2
reproducible-build and offline install/uninstall lifecycle. Relative to the contract-only inference
slice, package file count increases from 51 to 53 (+3.92%), declared bytes from 1,643,944 to
1,672,552 (+1.74%), and archive bytes from 292,512 to 298,393 (+2.01%). The archive SHA-256 is
`4769a85cd7e65ef7d4747c31b2e5344634aff514c2e8cab8f2c859cb51ea1933`. The quality-build
plugin suite passed. The combined AI-contract, preprocessing, inference, installed-distribution,
documentation-structure, development-tooling-isolation, and release-intent root gate passed 25
actionable tasks, with 11 executed and 14 up-to-date.

This is **improved** deterministic import integrity, consent enforcement, unsupported-case honesty,
and installed-tool parity with **no material Android runtime behavior change**. It neither calls a
model nor makes the incomplete IR compilable. The next action is a typed, human-supplied resolution
patch that can answer only the exact blocking questions, preserve provenance, forbid arbitrary
executable content, and derive code-generation eligibility mechanically before any provider is
selected.

### Contract freeze — typed screenshot inference resolution

Screenshot inference resolution v1 now freezes the provider-independent boundary between one
validated, incomplete inference import and a Design IR that may enter a future generator. The
request carries the exact validation, inference-result, and input-Design-IR fingerprints. Its human
authorization binds reviewer identity, an immutable review receipt, the exact validation
fingerprint, completed source inspection, and the `resolve-screenshot-inference` purpose while
keeping provider execution, network access, and content-bearing logs false. Every answer must match
one imported question's ID, optional node, pixel region, category, and required action; duplicate,
unknown, or missing answers cannot silently resolve anything.

The answer surface is intentionally typed and data-only. Content answers may update only properties
or state with a literal string, one bounded Android input profile, or a resolved caller-owned
binding. Behavior answers may name only `click`, `focus-change`, or `keyboard-action` callback
bindings and cannot contain callback source. Accessibility review must cover every imported node
exactly once with an explicit role, label source, traversal index, and decorative decision.
Expressions, guessed resources, arbitrary executable source, and provider credentials do not fit
the schema. An unsupported semantic may disappear only through its question-bound resolution
record and the same review receipt.

The exact wireframe golden answers all six imported blocking questions: title and button labels,
input purpose/state, field keyboard behavior, button click ownership, and the four-node
accessibility review. It adds two resolved caller event bindings and persists all 14 explicit
accessibility fields—including two semantic roles—while preserving all four nodes, kinds,
hierarchy, screenshot source identity, and pixel provenance. The
resolved Design IR fingerprint is
`6137e04205c3bec89b5e4480e0448e45c6ab55905a0ea3d9fdc55ef2b3e52603`; the resolution request
fingerprint is `c2712d96b7f1e821e18c0952dcd31becafb48eea0df848e2983efb319dd3fea6`, and the result
fingerprint is `61426e6904d9ffbdf1b29ec77fd8e6e0ee345494a0aad3b18028781f20ef981a`. The result has zero
remaining questions, unsupported semantics, and placeholder bindings, so
`codeGenerationAllowed` is mechanically true. That flag is eligibility only: this contract makes no
compilation, render, pixel, visual-parity, or production-behavior claim.

The focused gate passes 1/1 golden, 6/6 typed answers, 6/6 resolved unsupported semantics, 2/2
event bindings, 14/14 accessibility fields, and 3/3 fail-closed denominators for missing coverage,
expression injection, and changed validation lineage. It performs zero provider executions and zero
network requests. Node 25.6.0 passes 154/154 tooling tests. Phase 0 now verifies 12 schemas, 48
metrics, 49 cases, 46 fixture-backed cases, and four screenshot-resolution fixtures in addition to
the earlier screenshot evidence. The complete offline distribution retains its installed CLI/MCP,
compile, render-comparison, SPDX, and 2/2 reproducible-build lifecycle. Relative to the implemented
inference validator, package file count increases from 53 to 54 (+1.89%), declared bytes from
1,672,552 to 1,686,032 (+0.81%), and archive bytes from 298,393 to 299,922 (+0.51%). The archive
SHA-256 is `0a8b6ee752687c9b3b590d57ef82a6a149118ef0012abcba2890bee52cb672dd`.
The quality-build plugin suite passed. The combined AI-contract, screenshot preprocessing,
inference, resolution, installed-distribution, documentation-structure,
development-tooling-isolation, and release-intent root gate passed 26 actionable tasks, with 12
executed and 14 up-to-date.

This is **improved** resolution provenance, executable-content safety, accessibility review
coverage, and code-generation-gate measurability with **no material Android runtime behavior
change**. The resolution is still a frozen fixture rather than a public mutation tool. The next
action is an offline adapter that reproduces this patch from the validated import, exposes it through
the shared CLI/MCP package, and preserves the same fail-closed boundary before screenshot-specific
Kotlin generation begins.

### Implementation evidence — typed screenshot inference resolution

`resolve_screenshot_inference` is now the twelfth public CLI/MCP tool. It accepts only the unchanged
data returned by `validate_screenshot_inference` plus the frozen human-resolution request. Before
mutation it recomputes the validation and input-Design-IR fingerprints and verifies the request,
authorization, inference result, Design IR, question set, and summary share one lineage. It then
requires exact answer coverage and matches every answer to the imported question's node, category,
required action, and pixel rectangle. The adapter applies only component-compatible text,
text-field, button, caller-state, and caller-event decisions. It never evaluates a string as code,
loads a resource, calls a provider, opens a network connection, or accepts callback source.

The implementation review found that the contract-only golden retained accessibility roles but not
the equally explicit label-source, traversal, and decorative decisions. The result was hardened
before the public adapter was accepted: all four fields now live in each applicable node's Design IR
semantics, producing 14/14 persisted accessibility fields. The adapter also preserves the original
document ID, four-node hierarchy, kinds, screenshot source identity, and pixel provenance. It
reproduces the exact resolved Design IR fingerprint
`6137e04205c3bec89b5e4480e0448e45c6ab55905a0ea3d9fdc55ef2b3e52603` and result fingerprint
`61426e6904d9ffbdf1b29ec77fd8e6e0ee345494a0aad3b18028781f20ef981a` twice. Missing answers,
expression injection, and changed validation lineage return their exact three fail-closed
diagnostics; additional unit denominators cover moved pixel regions, component-incompatible fields,
partial accessibility review, and cancellation.

Node 25.6.0 passes 163/163 tooling tests. Phase 0 remains at 12 schemas, 48 metrics, 49 cases, 46
fixture-backed cases, and four screenshot-resolution fixtures. Direct CLI and MCP results are
semantically identical, and the installed package reproduces the same resolved fingerprint through
both supported MCP protocol eras. The complete distribution still passes 2/2 reproducible builds,
offline install/uninstall, SPDX/license inventory, compilation, and exact generated-layout
comparisons. Relative to the contract-only resolution slice, package file count increases from 54
to 56 (+3.70%), declared bytes from 1,686,032 to 1,711,367 (+1.50%), and archive bytes from 299,922
to 305,305 (+1.79%). The archive SHA-256 is
`12a9148c1992b163d9861202176ed2f32c35af96a2f3c6eaf450d73500e8c7a6`. The quality-build
plugin suite passed. The combined AI-contract, screenshot preprocessing, inference, resolution,
installed-distribution, documentation-structure, development-tooling-isolation, and release-intent
root gate passed 26 actionable tasks, with 12 executed and 14 up-to-date.

This is **improved** deterministic resolution, review-evidence preservation, executable-content
safety, and installed transport parity with **no material Android runtime behavior change**. The
resolved IR is eligible for generation but has not yet produced, compiled, rendered, or visually
compared screenshot-derived Kotlin. The next action is to freeze and implement a screenshot-specific
IR-to-Kotlin mapping for its typed state, event, and accessibility bindings, using hermetic
compilation as the first acceptance boundary.

### Contract freeze — screenshot Design IR to Kotlin and hermetic compilation

Screenshot Kotlin generation v1 now freezes the first executable boundary after typed resolution.
The request binds the exact resolution-result fingerprint
`61426e6904d9ffbdf1b29ec77fd8e6e0ee345494a0aad3b18028781f20ef981a` and resolved Design IR
fingerprint `6137e04205c3bec89b5e4480e0448e45c6ab55905a0ea3d9fdc55ef2b3e52603` and requires resolved
status, `codeGenerationAllowed = true`, zero remaining questions, zero unsupported semantics, and
zero placeholder bindings. Expressions, resources, callback source, project build execution, and
network access are outside the accepted surface.

The mapping is separate from the XML generator because screenshot resolution owns typed behavior
and accessibility review that the bounded XML subset deliberately rejects. The four-node golden
maps `emailState` to a caller-owned `TextFieldState`, `onEmailSubmit` to
`(TextFieldImeAction) -> Boolean`, and `onContinue` to `() -> Unit`. It emits real `Column`, `Text`,
`TextField`, and `Button` calls with stable node keys and `TextFieldInputProfile.Email`. The exact
Kotlin fingerprint is `5812c3ccbd0a6f30a0cc4c3ff4e71453006745d5dd76e63e153b2501131252e9`.

Every reviewed node also has one report record for role, label source, traversal index, decorative
status, and emission disposition. `Button` and `TextField` roles use component defaults; visible
text and the field placeholder carry labels. ViewCompose currently exposes no public
`traversalIndex` modifier, so the contract requires the reviewed ascending order to equal generated
hierarchy order and records that no explicit modifier was emitted. This is a deliberate honesty
boundary, not a fabricated API or a claim that all Android accessibility services traverse every
configuration identically. The report fingerprint is
`51c09b75e1a8bec953191e50388795c61fff6c45841de1f7832e050d2824752d`.

The dedicated JDK 21/Kotlin 2.3.10 source lane passes 1/1 golden compile with class-output
fingerprint `7f42dcfd35573559c8c4c2bc62047a57085e01f4c78f2625299349b00440ae67`.
The contract gate also passes 4/4 node mappings, 1/1 state binding, 2/2 event bindings, 4/4
accessibility records, and 3/3 fail-closed denominators for ineligible resolution, changed lineage,
and an unsupported event. Phase 0 now verifies 13 schemas, 51 metrics, 53 cases, 50 fixture-backed
cases, and four screenshot-generation fixtures. The previously implemented screenshot-resolution
gate and the new generation gate are both dependencies of `qaQuick`; this closes the lifecycle gap
where the resolution task existed but was not part of that aggregate.

The complete offline distribution still passes 2/2 reproducible builds, one offline
install/uninstall lifecycle, SPDX/license inventory, both MCP protocol eras, the installed compiler,
and the existing generated-layout comparison denominators. Only the generation schema is shipped at
this contract-only step; no unimplemented tool is advertised. Relative to the implemented
resolution slice, package file count increases from 56 to 57 (+1.79%), declared bytes from
1,711,367 to 1,720,941 (+0.56%), and archive bytes from 305,305 to 306,601 (+0.42%). The archive
SHA-256 is `336a81e18c666241b4fadae770bb4fcac0ec3bb14f002b7ac188bec79f2ebede`.
The quality-build plugin suite, documentation structure, development-tooling isolation, and release
intent gates pass. The combined AI-contract, screenshot preprocessing, inference, resolution,
generation, installed-distribution, documentation-structure, development-tooling-isolation, and
release-intent root gate passes 27 actionable tasks, with 13 executed and 14 up-to-date.

This is **improved** executable-contract precision and compile evidence with **no material Android
runtime behavior change**. It proves only that the frozen source is schema-valid, lineage-bound,
deterministic by bytes, and compilable against the accepted artifact. It does not yet prove a
generator can reproduce the source, expose it through CLI/MCP, render it, match the screenshot, or
behave correctly in an application. The next action is to implement the frozen generator and a
bounded generate/compile tool before any render or visual-comparison claim.

### Implementation evidence — screenshot Kotlin generation and compilation

`generate_screenshot_viewcompose` is now the thirteenth public CLI/MCP tool. It accepts the complete
resolved result plus the generation request; callers cannot replace the resolved IR with an
unbound object or select a package, function body, callback source, classpath, capability, compiler,
Gradle task, project path, model, or provider. The implementation revalidates the resolution and
Design IR schemas, recomputes both fingerprints, confirms the request identity and mechanical
eligibility, and accepts only the four frozen component kinds and their exact typed fields.

The generator allocates deterministic Kotlin identifiers, keeps state parameters before event
parameters, merges repeated compatible caller bindings, rejects signature conflicts, and escapes
Kotlin string templates. It reproduces the exact source fingerprint
`5812c3ccbd0a6f30a0cc4c3ff4e71453006745d5dd76e63e153b2501131252e9` and report fingerprint
`51c09b75e1a8bec953191e50388795c61fff6c45841de1f7832e050d2824752d` twice. The same mapper
also enforces the complete accessibility review and rejects traversal that cannot be represented by
the generated hierarchy order. Generate mode returns `static` evidence; compile mode alone invokes
the existing fixed UI Foundation compiler and reproduces class-output fingerprint
`7f42dcfd35573559c8c4c2bc62047a57085e01f4c78f2625299349b00440ae67`.

Node 25.6.0 passes 174/174 tooling tests. The focused gate passes 4/4 nodes, 1/1 state binding, 2/2
event bindings, 4/4 accessibility records, 3/3 fail-closed denominators, 2/2 deterministic
generations, and 1/1 hermetic compile. Phase 0 remains at 13 schemas, 51 metrics, 53 cases, 50
fixture-backed cases, and four screenshot-generation fixtures. Direct CLI and MCP generate results
are semantically identical. The installed package reproduces both the exact Kotlin fingerprint and
the exact compiled class fingerprint; both supported MCP eras list the thirteen-tool catalog, and
the modern protocol reproduces generation through the installed server.

The complete distribution passes 2/2 reproducible builds, offline install/uninstall, SPDX/license
inventory, installed compilation, and the existing generated-layout comparison denominators.
Relative to the contract-only slice, package file count increases from 57 to 60 (+5.26%), declared
bytes from 1,720,941 to 1,752,068 (+1.81%), and archive bytes from 306,601 to 312,952 (+2.07%). The
archive SHA-256 is `749ae2ca07a8cd269326b673ab9e7ad62517431721bf9be62571b3e92374e236`.
The quality-build plugin suite passes. The combined AI-contract, screenshot preprocessing,
inference, resolution, generation, installed-distribution, documentation-structure,
development-tooling-isolation, and release-intent root gate passes 27 actionable tasks, with 13
executed and 14 up-to-date.

This is **improved** deterministic source generation, typed behavior binding, accessibility
disposition preservation, hermetic compile evidence, and installed transport parity with **no
material Android runtime behavior change**. The result is compilable code, not a render or visual
parity claim. The following contract freezes explicit Preview values for caller state and callbacks
before the source enters the existing isolated harness.

### Contract freeze — source-bound screenshot generated Preview

Screenshot generated Preview v1 now freezes the next boundary after hermetic Kotlin compilation.
The request carries `sourceKind: "screenshot"` plus the exact resolution result, resolved Design
IR, generation request, generation report, generated Kotlin, framework bundle, configuration, and
compiler/render lane lineage. It uses a dedicated `tools.ai.GeneratedScreenshotPreview` identity,
`UiTreeBuilder.GeneratedScreenshotPreview()` wrapper, `Generated Screenshot ·` annotation prefix,
and `AI/Screenshot` group so screenshot evidence cannot be mislabeled or cached as XML evidence.

Every state and event parameter reported by the generator must have one binding in the same order
with the same parameter, source, and type. `TextFieldState` accepts explicit initial text.
`() -> Unit` and `(Boolean) -> Unit` map only to fixed no-op callbacks, while
`(TextFieldImeAction) -> Boolean` maps to an explicit Boolean return. None accepts lambda source,
expressions, project code, a build task, dependency, path, provider, or network selection. The
four-node wireframe golden therefore produces `TextFieldState()`, `{ _ -> false }`, and `{ }`
without executing caller content. Its request fingerprint is
`3bd5fe6b172856fd4e45cb30d8d301968f14353a549057c7e87041b30352b77c`; its 811-byte wrapper
fingerprint is `7b0d004f650248f2108e960385efa7e9a324acc600bfcd142f71c4a8b8d5c65b`.

The contract gate passes 1/1 exact wrapper and 3/3 fail-closed callback-source, missing-callback,
and wrong-callback-kind denominators. Phase 0 now verifies 13 schemas, 53 metrics, 57 cases, 54
fixture-backed cases, and four screenshot-Preview fixtures. Node 25.6.0 passes 175/175 AI-tooling
tests. The quality-build plugin suite passes, and `verifyAiScreenshotRender` is a `qaQuick`
dependency even at this contract-only stage so future activation cannot bypass the aggregate.

The schema update remains inside the existing 60-file offline package. Relative to screenshot
generation implementation, declared bytes increase from 1,752,068 to 1,754,433 (+0.13%) and archive
bytes from 312,952 to 313,203 (+0.08%); file count is unchanged. The archive SHA-256 is
`8f9b8037d603e2c0aea533eb937a488bb24ddf0e2a31fb81e20832ab603dbdfa`.
The distribution gate passes 2/2 reproducible builds, offline install/uninstall, SPDX/license
inventory, both MCP protocol eras, all installed compile denominators, and both existing XML
generated-layout comparisons. The combined screenshot-Preview, distribution, documentation,
tooling-isolation, and release-intent gate passes 23 actionable tasks, with 9 executed and 14
up-to-date.
No published ViewCompose artifact, public/protected API, Android runtime, provider boundary, or
application-process behavior changes, so this slice requires no Maven release changeset or module
manual update. The changed active plan and tooling README own the documentation impact.

This is **improved** render-contract precision and callback-source safety with **no material runtime
behavior change**. The wrapper has not yet been compiled or rendered, so the contract makes no PNG,
render-tree, semantic, geometry, or pixel claim. The next action is to implement this frozen profile
in `generate_screenshot_viewcompose` render mode, reproduce exact rendered evidence through the
installed package, and only then bind the result to semantic comparison.

### Implementation evidence — source-bound screenshot generated Preview

`generate_screenshot_viewcompose` now exposes `render` beside `generate` and `compile`. Render mode
requires the exact resolved result, a render-specific generation request, and explicit bounded
Preview bindings. It regenerates Kotlin and its report first, then passes only those tool-owned
bytes and values into the existing content-addressed `:tools:ai-preview-harness`. The shared Preview
adapter recognizes the screenshot report separately from XML, preserves every existing XML request
and wrapper fingerprint, and selects only `tools.ai.GeneratedScreenshotPreview`. The public tool
schema requires `previewBindings` only for render mode; CLI and MCP return the same result shape.

The callback implementation is deliberately non-executable input. `() -> Unit` becomes `{ }`,
`(Boolean) -> Unit` becomes `{ _ -> }`, and `(TextFieldImeAction) -> Boolean` becomes a lambda with
one explicit Boolean result. An extra callback source/value field, a missing callback, or a wrong
callback kind fails before Gradle. The adapter still accepts no inspected-project task, dependency,
build script, project path, output path, provider, credential, or network operation. Render mode's
generation request and report fingerprints are
`17a785a25672a8a2a2998618dab80015081347e29c601201638666bf8ec4f068` and
`c62b30e811ad8c68f7ef454f441bd52744ea49b9238c49816513787294ed16ea`.

Under the fixed 411 dp, density 2.625, `en-US`, LTR, light configuration, the generated wireframe
compiled and rendered into a 1,079 by 2,339 px, 30,984-byte PNG. Visual inspection showed the
expected `Welcome` title, `Email address` field placeholder, and `Continue` button without clipping
or corruption. The 203,290-byte render tree contains five virtual and five mounted nodes at depth
three, the expected observable title/action text, and zero warnings or layout diagnostics. Exact
evidence is:

- build: `2a92748798bad30d22e6a1a2160f7bebccfe58f9dcf19b4b9f7be6c90b471512`;
- aggregate render: `ba78a4047cad992e43b801a6b93a632a72543f383521172364d69b28fccf5076`;
- PNG: `072787b8fa78026425577e7159494b9841850c4366ac1aa62010b4342919e5fd`;
- render tree: `5228e401662349d9142cf695c42e21805c7c332ac36bc09334a32251d2f27000`.

The dedicated gate reproduces 1/1 exact render, 1/1 stable cache hit, and 3/3 fail-closed unsafe
bindings. Node 25.6.0 passes 180/180 AI-tooling tests. Phase 0 verifies 13 schemas, 54 metrics, 57
cases, 54 fixture-backed cases, and four screenshot-Preview fixtures. The installed CLI reproduces
the exact rendered fingerprint, while shared CLI/MCP render requests retain transport parity.

The 60-file offline package has 1,761,601 declared bytes and a 314,713-byte archive, SHA-256
`555f3faae7561d953896a729380bb0978a111a31e9a0d2559a9074f546d3c602`. Relative to the
contract-only package, declared bytes increase by 7,168 (+0.41%) and archive bytes by 1,510 (+0.48%)
with no runtime dependency added. The distribution gate passes 2/2 reproducible builds, offline
install/uninstall, SPDX/license inventory, two MCP protocol eras, all installed compile lanes, the
new installed screenshot render, and both prior XML comparisons.
The quality-build plugin suite passes. The combined screenshot-Preview, distribution,
documentation-structure, development-tooling-isolation, and release-intent gate passes 23
actionable tasks, with 9 executed and 14 up-to-date. No published artifact or public/protected API
changed, so the contract slice's no-Maven-changeset and no-module-manual disposition remains valid.

This is **improved** source-bound render evidence, callback safety, cache determinism, and installed
transport coverage with **no material Android runtime behavior change**. Limitations remain
explicit: this acceptance covers one configuration and confirms render integrity plus a human
sanity inspection; it does not yet prove semantic/geometry agreement with Design IR, accessibility
runtime behavior, interaction, responsive variants, or pixel similarity to the input screenshot.
The next action is to bind this accepted tree to an exact semantic and geometry comparison before
adding any pixel metric or repair loop.

### Contract freeze — screenshot semantic and structural geometry comparison

Screenshot layout comparison v1 freezes the exact boundary between accepted screenshot rendering
and any visual claim. It reuses the existing schema-validated generated-layout comparator but binds
its inputs to the resolved screenshot Design IR fingerprint
`6137e04205c3bec89b5e4480e0448e45c6ab55905a0ea3d9fdc55ef2b3e52603`, generated Preview request
fingerprint `3bd5fe6b172856fd4e45cb30d8d301968f14353a549057c7e87041b30352b77c`, aggregate render
fingerprint `ba78a4047cad992e43b801a6b93a632a72543f383521172364d69b28fccf5076`, and render-tree
fingerprint `5228e401662349d9142cf695c42e21805c7c332ac36bc09334a32251d2f27000`. Callers cannot replace
the Design IR, render tree, comparison policy, project build, task, dependency, path, provider, or
network boundary.

The frozen positive denominator maps all four authored nodes and requires 27/27 checks: exact node
keys; declared parent, child, and sibling order; `Column`, `Text`, `TextField`, and `Button` kinds;
the field and button roles; visibility; exact `Welcome` and `Continue` text; containment; vertical
order; and the allowlisted single-child text-field wrapper with equal wrapper and semantic-host
bounds. It uses zero tolerance for facts that the Design IR actually declares and has no aggregate
similarity score. The expected comparison fingerprint is
`ad5831b8af7895b85f84651e23284555a54911696868f70c70829974f7a50f31`. Separate semantic-text
and sibling-order mutations must downgrade evidence to `rendered` with category-specific
diagnostics.

This contract explicitly does **not** compare the `Email address` placeholder because the accepted
render-tree properties do not expose it. The resolved screenshot IR also declares no dp size or
padding modifiers, so containment and order are real runtime geometry evidence but not a claim that
rendered nodes match the screenshot's source pixel regions or exact source geometry. Pixel or
perceptual similarity, style, color, typography, draw order, accessibility traversal, state
mutation, event execution, focus, interaction behavior, and responsive configurations remain
outside the denominator.

The contract-only gate verifies 1/1 positive denominator and 2/2 fail-closed mutations. Phase 0 now
contains 13 schemas, 57 metrics, 60 cases, 57 fixture-backed cases, and three screenshot-comparison
fixtures; Node 25.6.0 passes 181/181 AI-tooling tests. `verifyAiScreenshotComparison` is part of
`qaQuick`, but the public tool still exposes only `generate`, `compile`, and `render`: activation is
intentionally frozen as `publicCompareMode = false` and `implementation = false` until the adapter
can reproduce this exact result.

The offline package remains at 60 files and has 1,762,156 declared bytes plus a 314,886-byte
archive, SHA-256 `bcae69502515df08617a5a2b1b92e8086d0df43e5699dbb8276711fc24a471e8`. Relative to the
screenshot-render implementation, the tooling README adds 555 declared bytes (+0.03%) and 173
archive bytes (+0.05%); no runtime dependency or executable contract file enters the package. The
distribution gate passes 2/2 reproducible builds, offline install/uninstall, license inventory,
both MCP protocol eras, all prior installed screenshot and XML compile/render denominators, and
both XML layout comparisons. The focused quality-build suite passes seven tasks, and the combined
comparison, documentation, development-tooling-isolation, and release-intent gate passes 22
actionable tasks, with eight executed and 14 up-to-date.

No published ViewCompose artifact, public/protected API, Android runtime, application process, or
provider boundary changes, so this slice needs no Maven release changeset or module-manual update.
This is **improved** comparison precision and claim honesty with **no material runtime behavior
change**. The next action is to implement a source-bound `compare` mode, reproduce the exact
comparison through CLI, MCP, and the installed package, and keep pixel metrics separate.

### Implementation evidence — screenshot semantic and structural geometry comparison

`generate_screenshot_viewcompose` now exposes `compare` beside `generate`, `compile`, and `render`.
It regenerates the screenshot-derived Kotlin and report from the exact resolved result, enters the
same source-free generated Preview profile with the same explicit state/callback bindings, and
invokes comparison only after rendering succeeds. The dispatcher supplies no independent Design
IR, render tree, or comparison policy: comparison receives the Design IR already accepted by
generation plus the Preview data and evidence returned in that request. Render failure returns no
comparison; comparison failure preserves the aggregate render fingerprint and `rendered` evidence;
only 27/27 passing checks publish the comparison fingerprint and `compared` evidence.

The compare-specific generation request and report fingerprints are
`c27e01b9980e5667ee526c22541d0eb4ccc59affd2004473453b36ec19c3bd9b` and
`2e1014bdfee846643799f3e75e7c7d68f6e62cd957aec3d81f264185fda86c35`. The accepted result maps
4/4 Design IR nodes, passes 27/27 required checks with zero failures or not-applicable checks, and
reproduces comparison fingerprint
`ad5831b8af7895b85f84651e23284555a54911696868f70c70829974f7a50f31`. A second complete call
revalidates the exact PNG and render-tree artifacts through a stable cache hit. The semantic-text
and sibling-order mutations each return their frozen diagnostic and remain at `rendered` evidence.

Node 25.6.0 passes 185/185 AI-tooling tests, including adapter evidence upgrade/downgrade, public
argument schema, shared dispatcher, and direct CLI/MCP semantic parity. Phase 0 remains at 13
schemas, 57 metrics, 60 cases, 57 fixture-backed cases, and three screenshot-comparison fixtures.
The dedicated Gradle gate reproduces 1/1 exact comparison, 1/1 cache hit, and 2/2 fail-closed
mutations. The distribution contract classifies `generate_screenshot_viewcompose:compare` as
source-bound, so installation may not silently fall back to the package's own source tree.

The offline package remains at 60 files with no runtime dependency. It now contains 1,763,721
declared bytes and a 315,363-byte archive, SHA-256
`cb5057892826b402cf4cadbf65495cf86573fa0dce5f1ae0d0f65000681b64cc`. Relative to the frozen
comparison contract, declared bytes increase by 1,565 (+0.09%) and archive bytes by 477 (+0.15%).
The installed CLI reproduces the exact comparison, and the installed modern MCP path completes the
same source-bound compare request; both protocol eras retain the same thirteen-tool catalog. The
distribution gate passes 2/2 reproducible builds, offline install/uninstall, license inventory, all
prior compile/render/compare denominators, and the new installed screenshot comparison in 1 minute
7 seconds (15 actionable tasks, one executed and 14 up-to-date).

The first installed MCP comparison correctly failed with `VC-AI-PREVIEW-START-FAILED` because the
new source-bound call had not passed an explicit source root into the installed server process. The
distribution verifier now supplies `VIEWCOMPOSE_SOURCE_ROOT` for that call instead of allowing an
implicit package-directory fallback; the repeated installed MCP comparison then passed. The final
combined screenshot render, comparison, distribution, documentation, development-tooling
isolation, and release-intent gate passes 24 actionable tasks, with ten executed and 14 up-to-date.

The evidence boundary is unchanged from the frozen contract: placeholder text, source screenshot
regions, exact source geometry, pixels, style, color, typography, draw order, accessibility
traversal, state/event behavior, focus, interaction, and responsive configurations remain
unclaimed. This is **improved** closed-loop semantic and structural validation with **no material
Android runtime behavior change**. The next action is to define a separate pixel/perceptual metric
and bounded repair contract without weakening these exact checks.

### Contract freeze — screenshot pixel-reference eligibility and exact metrics

Screenshot pixel comparison v1 freezes a separate gate after semantic and structural comparison.
It does not reinterpret the original inference image as a visual golden. A reference is eligible
only when its screenshot preprocessing request and result reproduce exactly, contain no redaction,
cover the full rendered viewport, and match the accepted render in width, height, density, font
scale, locale, layout direction, `sRGB` color space, straight alpha, upright orientation, zero
system-bar insets, and crop coordinates. A passing semantic comparison from the same render is
mandatory. Callers cannot supply a comparison policy or artifact path.

The accepted infrastructure reference is the 1079×2339 rendered PNG re-entered through canonical
screenshot preprocessing at density 2.625, font scale 1, `en-US`, and LTR. Its preprocessing
request fingerprint is
`06ded39bf3588193305ba1574c43ca3a6b6d0ff9c4cd19ec3e12eb75afdefefd`; its canonical result and
PNG fingerprints are `7a4b4458c215ed139191c0c85fe5f47d31b9c8b6a1db9f48f4d82806e4eb05c1` and
`5d909bb84a6ac002f44ce0e1e0e6cf16dfce5f53ad742d6c91c66b8077fbb7a5`. The render PNG retains
fingerprint `072787b8fa78026425577e7159494b9841850c4366ac1aa62010b4342919e5fd`; differing encoded PNG
bytes are permitted only because preprocessing deterministically strips metadata and re-encodes
the same RGBA image. The implementation denominator is 2,523,781 pixels with zero dimension or
channel tolerance. It will report exact pixel ratio, mismatched pixels, RGBA mean absolute error,
RGBA root mean square error, and maximum channel delta separately; no aggregate similarity score
exists.

The old 16×24 inference wireframe is explicitly ineligible because its viewport and density differ
and one user-declared redaction is present. Missing semantic evidence and changed reference output
identity are separate fail-closed denominators. The gate verifies 1/1 eligible reference and 3/3
ineligible cases, while `publicPixelCompareMode = false` and `implementation = false` prevent the
contract from being mistaken for executed pixel evidence. Perceptual similarity, cross-device or
cross-renderer equivalence, font equivalence, motion, interactions, design intent, aesthetic
quality, and automatic repair remain unclaimed.

Phase 0 now verifies 14 schemas, 60 metrics, 64 cases, 61 fixture-backed cases, and four
screenshot-pixel fixtures. Node 25.6.0 passes 186/186 AI-tooling tests. The new
`verifyAiScreenshotPixelComparison` task is part of `qaQuick`. The offline package ships the result
schema as its 61st file and has no new runtime dependency. It contains 1,770,597 declared bytes and
a 316,305-byte archive, SHA-256
`41dd31d8630e5f7c022b960010b9ffbdd252c8ad1d4fe1d268f0ac7c2514d209`. Relative to the semantic
comparison implementation, this adds one schema file, 6,876 declared bytes (+0.39%), and 942
archive bytes (+0.30%).

The distribution gate passes 2/2 reproducible builds, offline install/uninstall, SPDX/license
inventory, both MCP protocol eras, and every prior installed screenshot and XML denominator. The
focused quality-build suite passes seven tasks. The combined pixel-contract, distribution,
documentation, development-tooling-isolation, and release-intent gate passes 23 actionable tasks,
with nine executed and 14 up-to-date.

No published ViewCompose artifact, public/protected API, Android runtime, application process, or
provider boundary changes, so this slice needs no Maven changeset or module-manual update. This is
**improved** visual-claim integrity and eligibility coverage with **no material Android runtime
behavior change**. The next action is to implement the frozen exact comparator, prove decoded RGBA
identity and mismatches through CLI/MCP and the installed package, and only then freeze bounded
repair behavior.

### Implementation evidence — exact screenshot RGBA comparison

`generate_screenshot_viewcompose` now exposes `compare-pixels` after `compare`. The mode requires
the exact canonical preprocessing request/result pair in addition to the resolved screenshot result
and explicit Preview bindings. It regenerates and source-binds the same Kotlin, renders in the fixed
Preview lane, and must first reproduce all 27/27 semantic and structural checks. Pixel comparison is
never reached when rendering or semantic comparison fails.

The comparator reproduces the reference preprocessing result, rejects any changed lineage or
redaction, and requires exact viewport, density, font scale, locale, layout direction, `sRGB`,
straight alpha, orientation, system-bar, and crop identity. It then reopens only the contained
regular rendered PNG, rejects symbolic links and changed bytes, and uses the same strict bounded
non-interlaced 8-bit RGBA decoder as screenshot preprocessing. The preprocessing-compatible limit
is 1,310,720 compressed bytes, 16 MiB decoded bytes, and 4,194,304 pixels per image. Cancellation is
checked before and during reference reproduction, decoding, artifact reads, and channel comparison.

The accepted 1079×2339 denominator compares 2,523,781 pixels and 10,095,124 RGBA channels. All
pixels match at zero tolerance: exact pixel ratio 1, zero mismatched pixels, zero RGBA mean absolute
error, zero RGBA root mean square error, and zero maximum channel delta. The comparison fingerprint
is `5ac4341b880376f4f7c4e54c316a115d5d2ba448b8502d4cafdc76a50c875c5b`. The pixel-specific
generation request and report fingerprints are
`7dca8567dfc551fc1ea3e708535b361a783ec805c466ce8655d1a657ab5d6a8b` and
`98599de109dcc98ff978326bf9a906dc9b131549f2dce665cc04639adce61c78`. A second end-to-end run
revalidates the content-addressed artifacts through a stable cache hit.

Four fail-closed denominators remain separate: the configuration-mismatched redacted wireframe,
missing semantic evidence, changed canonical reference identity, and one red-channel unit changed
in the render. The last case reports exactly one mismatched pixel and maximum channel delta 1. It
does not collapse that result into a perceptual or aggregate score. Pixel mismatch preserves the
accepted render fingerprint and `rendered` evidence; only an exact pass publishes the pixel
comparison fingerprint at `compared` evidence.

Phase 0 remains at 14 schemas and 60 metrics and now contains 65 cases, 62 fixture-backed cases,
and five screenshot-pixel fixtures. Node 25.6.0 passes 194/194 AI-tooling tests. The dedicated gate
reproduces 1/1 exact comparison, 1/1 cache hit, and 4/4 fail-closed denominators. Installed CLI and
modern MCP calls reproduce the same pixel fingerprint after explicit
`VIEWCOMPOSE_SOURCE_ROOT` binding, while both MCP protocol eras retain the same thirteen-tool
catalog.

The dependency-free offline package now contains 62 files and 1,789,505 declared bytes; its
320,125-byte archive has SHA-256
`b58ad3bad5b58e96e00b1ed819f017496fd9c0d8c5d24a6685ffac7fdf107eb3`. Relative to the frozen
pixel contract, this adds the comparator as one file, 18,908 declared bytes (+1.07%), and 3,820
archive bytes (+1.21%). It adds no runtime dependency or provider boundary.

The first combined distribution run exposed a verifier-only timeout classification gap:
`compare-pixels` still received the 10-second static-request budget and correctly returned
`VC-AI-PIXEL-CANCELLED` when a cache replay exceeded it. The verifier now classifies
`compare-pixels` with the existing source-bound `compile`/`render`/`compare` 120-second budget.
The repeated distribution gate passes 2/2 reproducible builds, offline install/uninstall,
SPDX/license inventory, both MCP protocol eras, and every prior plus exact-pixel installed
denominator. The focused quality-build suite passes seven tasks, with two executed and five
up-to-date. The final combined pixel, distribution, documentation, development-tooling-isolation,
and release-intent gate passes 23 actionable tasks, with nine executed and 14 up-to-date.

No published ViewCompose artifact, public/protected API, Android runtime, or application process
changes, so this slice needs no Maven changeset or module-manual update. This is **improved** exact
visual evidence, artifact integrity, cancellation, installed transport coverage, and claim honesty
with **no material Android runtime behavior change**. Perceptual similarity, cross-device or
cross-renderer equivalence, font equivalence, design intent, aesthetic quality, interaction,
motion, and repair remain unclaimed. The next action is to freeze a bounded repair contract whose
iterations cannot bypass compilation, semantic, structural, exact-pixel, or safety failures.

### Contract evidence — bounded screenshot repair

The provider-offline screenshot repair contract is now frozen before implementation. It permits at
most five reason-coded attempts over typed Design IR patches derived from the accepted resolved
result; it does not accept caller-supplied Kotlin, arbitrary project source edits, provider calls,
network access, symbolic-link traversal, inspected-project build logic, automatic threshold
relaxation, or reference mutation. No public `repair` mode is exposed at this stage.

Every candidate is evaluated in fixed `safety` → `compilation` → `render` → `semantics` →
`structure` → `exact-pixels` order. The first failing gate owns the repair reason and prevents
later gates from running. Candidate acceptance requires strict improvement at that gate, while
every previously passed deterministic gate must remain passed. Repeated candidate or change
fingerprints terminate as oscillation; a regression terminates rather than silently rolling
forward. Pixel evidence is kept as separate exact counts and cannot override any earlier failure or
become an aggregate score.

The result schema retains initial, attempted, and final candidate fingerprints; gate evidence;
reason-coded change fingerprints; accepted/rejected dispositions; termination reason; and a safe
`incomplete`, `blocked`, or `cancelled` result when convergence is not established. The frozen
zero-iteration golden converges because all six gates already pass and has repair fingerprint
`a6f92b031f387d30eea9d52ed84b91182149751dfb72e8603d5a4de1ba99d9ee`. Five fail-closed
denominators cover a pixel mismatch with no eligible typed change, semantic regression, candidate
oscillation, exhaustion at five iterations, and an initial safety failure.

Phase 0 now verifies 15 schemas, 64 metrics, 71 cases, 68 fixture-backed cases, and six screenshot
repair fixtures. The focused contract gate reproduces 1/1 zero-iteration convergence and 5/5
fail-closed stops, while Node 25.6.0 passes 195/195 AI-tooling tests. The dependency-free offline
package now contains 63 files and 1,798,448 declared
bytes; its 321,255-byte archive has SHA-256
`46930ae893be74549e98073715b5249b6d783a4b809e22ee349b4c611e07fcba`. Relative to the exact-pixel
implementation package, the schema-only distribution addition is one file, 8,943 declared bytes
(+0.50%), and 1,130 archive bytes (+0.35%), with no runtime dependency or provider boundary. The
combined repair, Phase 0, reproducible distribution, documentation, development-tooling-isolation,
and release-intent gate passes 24 actionable tasks, with 12 executed and 12 up-to-date.

No published ViewCompose artifact, public/protected API, Android runtime, or application process
changes, so this contract slice needs no Maven changeset or module-manual update. This is
**improved** repair measurability, failure honesty, and resource/safety bounding with **no material
Android runtime behavior change**. Automatic repair, arbitrary source mutation, accessibility
completeness, perceptual similarity, interaction, animation, design intent, and universal
convergence remain unclaimed. The next action is to implement the frozen deterministic orchestrator
and reproduce all contract outcomes before considering a public tool mode.

### Implementation evidence — bounded screenshot repair orchestrator

The packaged provider-offline repair core now executes the frozen state machine without exposing a
new CLI or MCP mode. It accepts one schema-valid initial candidate, a typed patch producer, and a
deterministic candidate evaluator. Patch input is limited to `replace-field`,
`replace-modifier-argument`, `replace-node-kind`, and `reorder-children` operations over stable node
IDs. It rejects expression values, unknown or duplicate operation targets, more than 64 operations,
more than 262,144 encoded patch or candidate bytes, more than 10,000 non-pixel checks, and any
changed or malformed patch/evaluation fingerprint before accepting evidence.

The orchestrator short-circuits an initial safety failure and then owns a maximum of five attempts.
It propagates cancellation before and after both injected boundaries; records each accepted or
rejected attempt; rejects repeated change, candidate, or Design IR fingerprints as oscillation;
rejects any regression of a previously passed gate; and accepts a candidate only when it strictly
improves the first failing gate. For exact pixels, strict improvement means equal compared-pixel
denominator, fewer mismatched pixels, and no larger maximum channel delta. A non-improving candidate
is retained only as rejected evidence and returns `incomplete`; the prior accepted candidate remains
the final result.

The implementation reproduces the exact zero-iteration golden fingerprint
`a6f92b031f387d30eea9d52ed84b91182149751dfb72e8603d5a4de1ba99d9ee` and all five frozen
fail-closed outcomes. Additional tests cover one-iteration exact convergence, a valid but
non-improving candidate, executable and duplicate patch rejection, cancellation, and a schema-valid
blocked result for invalid initial evidence. Proposal and evaluation are deliberately still
internal injected boundaries: the current public tool cannot trigger them, and no provider,
credential, network client, arbitrary Kotlin source, project source mutation, or inspected-project
build selection was added.

The dependency-free offline package now contains 64 files and 1,816,541 declared bytes; its
325,028-byte archive has SHA-256
`499415ece0b68487f78b58b17f91154ef59b817e923d4ae11f3e397274d72fb5`. Relative to the frozen
repair-contract package, the internal orchestrator and its packaged documentation add one file,
18,093 declared bytes (+1.01%), and 3,773 archive bytes (+1.17%). It adds no runtime dependency or
provider boundary. Node 25.6.0 passes 206/206 AI-tooling tests. The combined repair, Phase 0,
reproducible distribution, documentation, development-tooling-isolation, and release-intent gate
passes 24 actionable tasks, with 12 executed and 12 up-to-date.

No published ViewCompose artifact, public/protected API, Android runtime, or application process
changes, so this implementation slice needs no Maven changeset or module-manual update. This is
**improved** deterministic repair control, evidence retention, cancellation, and safety with **no
material Android runtime behavior change**. A compile/render evaluator, repair policy that can
derive an eligible patch from structured findings, public repair mode,
perceptual comparison, and accessibility/interaction completeness remain unclaimed. The next action
is to connect the repaired candidate to existing source-bound compile, render, and comparison lanes
before any public activation.

### Implementation evidence — typed Design IR repair patches

The repair core now owns deterministic application of its four typed patch operations rather than
delegating mutation to an arbitrary source callback. Every request binds one immutable patch to the
exact canonical fingerprint of a resolved screenshot Design IR. The applier first validates the
complete IR, requires `source.kind: screenshot`, rejects unresolved/unsupported entries and any
expression value, bounds the tree to 1,000 nodes and depth 64, and rejects duplicate node IDs or
field names before cloning the candidate.

`replace-field` may replace only an existing `properties`, `semantics`, or `state` value;
`replace-modifier-argument` requires the exact existing modifier index and argument;
`replace-node-kind` accepts only the seven currently generated component kinds; and
`reorder-children` must be an exact permutation of the target's existing child IDs. Missing targets,
non-permutations, executable values, changed lineage, duplicate operation targets, and no-op values
fail before an output identity is published. The caller's IR is never mutated. The final candidate
is schema-validated again and exposes only canonical input/output Design IR fingerprints, immutable
change fingerprint, operation count, changed logical paths, and a compact output fingerprint.

The accepted title-text fixture has change fingerprint
`b1a8fb0a331181bd5cbc93230e7a8cf288163ed4285e4a876ee64c39ad231371`, repaired Design IR
fingerprint `442747e46f1a1bd35b0e4c5107a0b04d2962203819183cf4193ff1e37b46107d`, and output
fingerprint `ea77e571ae5977da628cdb40f12d83f664c2ed43b9375c42743ecd574098c219`. The focused gate
reproduces it twice and retains the original accepted IR unchanged. Seven applier tests cover all
four operations, deterministic replay, missing targets, invalid permutations, no-op changes,
executable content, changed lineage, unsupported input, and cancellation.

Phase 0 remains at 15 schemas and 64 metrics and now contains 72 cases, 69 fixture-backed cases,
and seven screenshot-repair fixtures. The focused repair gate now reproduces 1/1 zero-iteration
convergence, 1/1 typed patch golden, and 5/5 fail-closed orchestration denominators. Node 25.6.0
passes 213/213 AI-tooling tests.

The dependency-free offline package now contains 65 files and 1,824,726 declared bytes; its
326,747-byte archive has SHA-256
`5a617743fd1a71c605e445cddbdf28ad957d620e3615517d9541f7d214bda60d`. Relative to the internal
orchestrator package, the patch applier and packaged documentation add one file, 8,185 declared
bytes (+0.45%), and 1,719 archive bytes (+0.53%), with no runtime dependency or provider boundary.

The first combined acceptance run completed every patch, orchestration, and Phase 0 denominator but
the installed CLI's unrelated frozen screenshot Preview replay returned one
`VC-AI-PREVIEW-BUILD-FAILED`. An immediate full `verifyAiDistribution --rerun-tasks` replay with no
source change passed 2/2 reproducible builds, offline install/uninstall, SPDX/license inventory,
both MCP protocol versions, and all installed compile/render/comparison flows. The initial result is
therefore **inconclusive** transient build-environment evidence rather than a repair regression; the
clean rerun is accepted for this slice. Limitation and next action: if the Preview build failure
recurs, retain its worker log and add a dedicated reproducible denominator instead of masking it with
automatic retry.

No published ViewCompose artifact, public/protected API, Android runtime, or application process
changes, so this slice needs no Maven changeset or module-manual update. This is **improved** typed
mutation integrity, lineage, determinism, and failure localization with **no material Android runtime
behavior change**. Compilation, rendering, semantic/structural/pixel evaluation of the repaired
candidate, finding-to-patch policy, and public activation remain unclaimed. The next action is to
bind patched Design IR candidates to the existing source-bound evidence lanes.

### Contract correction — independent screenshot render gate

Before connecting the evaluator, the repair result contract advances from v1 to v2 to correct one
evidence-classification gap: generated Kotlin may compile while its source-bound Android Preview
still fails to render. Render is therefore an independent gate between `compilation` and
`semantics`; a render failure owns its own reason and short-circuits semantic, structural, and
pixel acceptance. The candidate patch format remains v1 because its typed mutation surface did not
change.

The corrected zero-iteration golden now records all six gates and has repair fingerprint
`54e68f7a8129bcf1da26053917a6ad769f71e32729ac416ea792f3d5fec610cb`. The focused verifier
reproduces 1/1 convergence, 1/1 typed patch, and 5/5 fail-closed outcomes; Phase 0 remains at 15
schemas, 64 metrics, 72 cases, 69 fixture-backed cases, and seven repair fixtures; and Node 25.6.0
passes 213/213 tests. The full offline distribution gate passes 2/2 byte-reproducible builds,
install/uninstall, license/SBOM inventory, both installed MCP protocol lanes, and every installed
compile/render/comparison flow. Documentation, development-tooling isolation, and release-intent
verification also pass in the same 23-task acceptance run.

The dependency-free package remains at 65 files and increases by 716 declared bytes to 1,825,442
bytes. Its 326,775-byte archive has SHA-256
`fd3d2ca0b1b1c47bc3faf8e3ffd3a51e91d4b2e36c25c961d01957889a899dc7`, 28 archive bytes above
the typed-patch baseline. No public ViewCompose API, published Android artifact, runtime path,
provider boundary, or release changeset is involved. This is **improved** failure localization and
claim accuracy with **no material Android runtime behavior change**. The next action remains the
deterministic candidate evaluator over the now-complete ordered gate set.

### Implementation evidence — source-bound screenshot repair candidate evaluation

The packaged candidate evaluator now connects the typed patch applier to the existing hermetic
Kotlin compiler, generated-Preview adapter, semantic/structural comparator, and zero-tolerance RGBA
comparator. It rebuilds the ephemeral resolution and generation lineage from the patched Design IR
instead of accepting caller-selected fingerprints. Compilation and Preview rendering are separate
gates: successful generation or compilation cannot upgrade a render failure. The accepted render is
then categorized into 12 semantic and 15 identity/structure/geometry checks; pixels run only after
both categories pass. A small binding factory adapts this evaluator directly to the orchestrator's
existing `evaluatePatch` boundary while candidate proposal remains injected and non-public.

The real source-bound denominator evaluates two candidates under the same 411 dp, density 2.625,
`en-US`, LTR, light configuration and the same immutable 1079×2339 reference. The unchanged
candidate passes all six gates, compiles to fingerprint
`7f42dcfd35573559c8c4c2bc62047a57085e01f4c78f2625299349b00440ae67`, renders to
`ba78a4047cad992e43b801a6b93a632a72543f383521172364d69b28fccf5076`, and retains the exact
2,523,781-pixel pass. The typed `Welcome` → `Hello` candidate also passes safety, compilation,
rendering, all 12 semantic checks, and all 15 structural checks, but correctly fails exact pixels:
5,102 of 2,523,781 pixels differ (0.2022%) with maximum channel delta 217. Its candidate evaluation
fingerprint is `8f0a65ef59dfe39b42aa25342994ae22cdbb5cede1cffcfaa0d6cadfa95586d9`.

The first exploratory patched run reused pre-existing Preview build output and reported 3,345
changed pixels. After deleting only the ignored, reproducible Preview harness build directory, a
cold rebuild reported 5,102; a second explicit cold rebuild and the Gradle task then reproduced
5,102 and every render/comparison fingerprint exactly. The cache-context result is therefore
**inconclusive** and is not accepted as a golden; the two matching cold rebuilds are the accepted
evidence. Limitation and next action: if clean rebuild evidence drifts again, preserve both render
artifacts and expand the content address to the missing build or environment input before accepting
another value.

Phase 0 now verifies 15 schemas, 64 metrics, 73 cases, 70 fixture-backed cases, and nine screenshot
repair denominators. Node 25.6.0 passes 226/226 tests, including one evaluator-bound orchestration
iteration, unavailable pixel evidence, cancellation inside injected boundaries, and all compile,
render, comparison, integrity, and distribution unit paths. The dedicated Gradle gates pass 2/2
source-bound candidates; the offline distribution gate passes 2/2 byte-reproducible package builds,
install/uninstall, SPDX/license inventory, both installed MCP protocol eras, and every prior
installed compile/render/comparison flow.

The dependency-free offline package now contains 66 files and 1,841,152 declared bytes. Its
330,190-byte archive has SHA-256
`12887e65602e31d4097281d8aa26776687fe2c6b02ff19d8a122dbd4cc1b7857`. Relative to the render-gate
correction baseline, the evaluator adds one file, 15,710 declared bytes (+0.86%), and 3,415 archive
bytes (+1.05%), with no runtime dependency or provider boundary. No published ViewCompose artifact,
public/protected API, Android runtime, or application process changes, so no Maven changeset or
module-manual update is required. This is **improved** repair evidence fidelity, failure
localization, cache honesty, and orchestration readiness with **no material Android runtime behavior
change**. Automatic finding-to-patch policy, arbitrary source repair, and public repair activation
remain unclaimed; the next action is a bounded deterministic proposer over the accepted structured
findings.

### Implementation evidence — content-addressed screenshot repair candidates

The candidate evaluator now retains one bounded evidence record for every schema-valid candidate
that reaches source generation. The v1 record binds the base and candidate resolution identities,
input and candidate Design IR identities, optional typed-change identity, complete six-gate
evaluation, immutable candidate Design IR, gate-specific diagnostic codes, structured layout
comparison, and structured exact-pixel comparison. It excludes generated Kotlin and PNG bytes,
has a 16 MiB internal ceiling, and is fingerprinted over canonical JSON. A session stores evidence
by candidate fingerprint and returns defensive clones, so a proposer can inspect deterministic
findings without mutating accepted evaluation state. Safety failures before a valid candidate
identity retain no partial evidence.

The real source-bound denominator reproduces two complete records. The unchanged exact candidate
has evidence fingerprint
`9325dcf8955a3edc492226a8b45da4825eaa08d132e15f7f142597d6a58fccec`, no diagnostic codes,
and all six gates pass. The typed `Welcome` → `Hello` candidate has evidence fingerprint
`26ff69bf21775b201d840668b5facf1d0041b553083bcd113008e769c157aa3b`; compilation, rendering,
12 semantic checks, and 15 structural checks pass, while its sole retained diagnostic is
`VC-AI-PIXEL-MISMATCH` for the frozen 5,102-of-2,523,781 exact-pixel difference. The dedicated
verifier independently validates the record schema and every nested Design IR, candidate
evaluation, layout comparison, and pixel comparison contract before recomputing the evidence
fingerprint and lineage.

Phase 0 now verifies 16 schemas, 64 metrics, 73 cases, 70 fixture-backed cases, and nine screenshot
repair denominators. Node 25.6.0 passes 228/228 AI-tooling tests, including immutable session lookup,
content-address recomputation, raw-source/image exclusion, compile/render short-circuiting, and all
prior orchestration denominators. The dependency-free package now contains 67 files and 1,848,041
declared bytes; its 331,563-byte archive has SHA-256
`d239b6c00a8210e12e906f2c003e71a726378d8089b27e5b179d0ce03430910c`. Relative to the
source-bound evaluator package, the evidence contract and implementation add one file, 6,889
declared bytes (+0.37%), and 1,373 archive bytes (+0.42%), with no runtime dependency or provider
boundary.

The candidate-specific Gradle gates pass 2/2 source-bound candidates, and the dedicated Phase 4
generated-Preview gate independently passes 2/2 exact cold renders plus 2/2 stable cache hits.
However, two full Gradle distribution replays and one direct distribution replay consistently
produced a different, still schema-valid and semantically exact XML Preview only after the same
persistent worker had rendered the screenshot target: output
`e4d6eabbe698970fd2faac2f3ff0b4363c4221bdff29c2965d107c6927a8f4f1`, PNG
`ccd9e8a1a8cb0ff3ff98dce4f1e7eda2f771eb98a44aa9fcfb6279dfc0d4b343`, and render tree
`03298986d5e5519227183a649d8ebe4ebd07e71a1e60f1d600ee685e83015929`. Removing only that
request's ignored render cache and running XML Preview in a cold worker restored the accepted
`6d2c8a5296db8cc95e5201092e40532f371f1d95621acd7bad343c913b4b9bab` output exactly. This
acceptance result is therefore **mixed**: candidate evidence is reproducible, while the combined
installed sequence exposes a pre-existing cross-build worker-isolation defect. The worker
compatibility fingerprint currently omits the build-manifest input fingerprint; the next action is
to bind those identities and add a screenshot-to-XML switch denominator before rerunning the full
distribution gate.

No published ViewCompose artifact, public/protected API, Android runtime, or application process
changes, so no Maven changeset or module-manual update is required. This is **improved** candidate
traceability, structured finding availability, immutable session state, and claim accuracy with
**no material Android runtime behavior change**. The record is internal and does not prove a repair
policy, perceptual equivalence, arbitrary source repair, or public convergence. The next action is
to close the cross-build Preview worker isolation gap, then implement a bounded deterministic
proposer that consumes only this accepted evidence and emits eligible typed Design IR patches.

### Implementation correction — exact-build Preview worker isolation

The repeated installed-distribution failure was a real cross-build isolation defect rather than a
candidate-evidence regression. Running the screenshot target before the XML target in one
persistent Preview worker produced a schema-valid, semantically exact XML comparison but changed
the rendered output to
`e4d6eabbe698970fd2faac2f3ff0b4363c4221bdff29c2965d107c6927a8f4f1`, its PNG to
`ccd9e8a1a8cb0ff3ff98dce4f1e7eda2f771eb98a44aa9fcfb6279dfc0d4b343`, and its render tree to
`03298986d5e5519227183a649d8ebe4ebd07e71a1e60f1d600ee685e83015929`. Removing only the XML
render cache and starting a cold worker restored the accepted output
`6d2c8a5296db8cc95e5201092e40532f371f1d95621acd7bad343c913b4b9bab`. The failure was therefore
history-dependent pixel and render-tree evidence, not a stale golden.

The Gradle plugin previously derived worker compatibility only from the narrow Layoutlib
environment and render-runtime identities. It now also includes the complete build-manifest input
fingerprint. Project bytecode or resource changes therefore retire the persistent worker before a
new render, while identical-build batches and repeated requests may still reuse it. The child class
loader remains fresh for every command; the stronger process key additionally contains AndroidX
and Layoutlib caches that live outside that loader. A focused unit denominator proves sensitivity
to each of build input, Layoutlib environment, and render runtime identity.

From an empty Preview harness, the full installed distribution now passes 2/2 byte-reproducible
package builds, offline install/uninstall, SPDX/license inventory, both MCP protocol eras, the
screenshot compile/render/layout/exact-pixel lanes, and then the XML, image XML, and layout-
dependency compile/render/comparison lanes. In particular, XML after screenshot again returns the
accepted comparison fingerprint
`470b4e23384479ff29528fe311058618b6ace6536465aeaf08bb477a10cc737d`. The 23 non-TestKit plugin
tests pass. Two complete 24-test plugin-suite attempts reached the functional TestKit case but
failed while writing Gradle lock/result files after the host volume exhausted its remaining space;
those attempts are **inconclusive** and are not treated as functional evidence. The cold installed
distribution is the accepted end-to-end cross-build denominator.

This correction changes production source in the published
`viewcompose-preview-gradle-plugin`, so
`release/changes/20260829-preview-worker-jvm21-resolution.json` classifies it as a fix and the owning
English/Chinese module manuals define the new reuse boundary. The result is **mixed** operationally:
cross-build determinism and evidence integrity are improved, while a changed build now pays cold
Layoutlib setup instead of risking process-cache contamination. Application runtime behavior is
unchanged. Any later attempt to recover cross-build warm reuse must first pass the same cold-start,
screenshot-to-XML pixel and render-tree denominator. The next action returns to the bounded
deterministic screenshot repair proposer.

### Implementation evidence — exact pixel localization and Design IR attribution

The exact RGBA comparator now emits a separate screenshot pixel-localization v1 result without
changing the existing pixel-comparison v1 fingerprint or replacing its independent metrics. The
same bounded pixel traversal records the global mismatch rectangle and assigns each changed pixel
to the deepest mapped Design IR node whose render bounds contain it. Coordinates use left/top
inclusive and right/bottom exclusive viewport bounds; equal-depth overlaps use stable node-ID
ordering, and pixels outside all mapped nodes remain in a separate unassigned denominator. The
result binds the exact pixel-comparison fingerprint and is content-addressed over canonical JSON.
It contains no generated source or image bytes and derives no repair value from location.

The real 1079×2339 source-bound denominator again evaluates the unchanged candidate and the typed
`Welcome` → `Hello` candidate under density 2.625, `en-US`, LTR, and the light theme. The unchanged
candidate remains an exact 2,523,781-pixel pass with localization fingerprint
`214c69da3a51a1ad521d3e605c681ab8d42e3787526fe95703b7399c80042716`. The changed candidate
has 3,345 mismatched pixels (0.1325%) with maximum channel delta 217 and localization fingerprint
`05ee59a64778fb9ca3727aa81cc6b27965ceb6cd4de86b906e558d493db28433`. Its global mismatch
rectangle is `(1, 8, 198 × 37)`: 2,267 pixels are attributed to `wireframe-title` within
`(1, 8, 111 × 37)`, 1,078 spill into the containing `wireframe-root` within
`(112, 18, 87 × 27)`, and zero are unassigned. The candidate evidence fingerprints are
`ce8555c98b3febf00cdd23978da5c5af685efcddb17c0f2110b229ec26a7605a` for the exact input and
`e0bd2617d05017bf9fa864139ecc03535b35a3b8b7bbbf491c28884be0c60068` for the changed input.

The prior 5,102-pixel result belongs to the earlier Preview worker/build context. After exact-build
worker isolation and fresh local Gradle state, the current 3,345-pixel result was reproduced once
with existing outputs and again after deleting only the ignored Preview harness build directory.
The absolute difference is -1,757 pixels (-34.44%), but it is **not** interpreted as a visual
improvement because the evidence context changed; the new pair of matching runs establishes the
current golden. One intervening attempt failed at the render gate with `No space left on device`.
It was rejected as **inconclusive** host-capacity evidence, sufficient space was restored by
removing only stale Gradle 8.13 daemon logs, and the same empty-harness run then passed. Host volume
headroom remains an operational limitation for future clean render lanes.

Node 25.6.0 passes 230/230 AI-tooling tests, including exact localization, one-pixel attribution,
deepest-node overlap, and explicit unassigned-pixel cases. The dedicated pixel gate passes 1/1
exact result, 1/1 stable cache replay, and 4/4 fail-closed denominators. The candidate gate passes
2/2 real candidates in both warm and empty-harness runs. The full installed distribution passes
2/2 byte-reproducible package builds, offline install/uninstall, SPDX/license inventory, both MCP
protocol eras, the packaged screenshot localization path, and all subsequent XML compile/render/
comparison flows. The dependency-free package now contains 68 files and 1,857,971 declared bytes;
its 333,603-byte archive has SHA-256
`d610f4f5af7b78469a24c4fde7b18928c9d96ed908ddbfb9afc8164e9d694795`. Relative to the prior
candidate-evidence package, localization adds one file, 9,930 declared bytes (+0.54%), and 2,040
archive bytes (+0.62%), with no runtime dependency or provider boundary.

This result is **improved** visual-failure localization, candidate traceability, deterministic
ownership, and installed-tool parity with **no material Android runtime behavior change**. It does
not prove that an attributed node caused every changed pixel, that a mismatch rectangle determines
a valid modifier/value patch, or that exact pixels express perceptual or design quality. The next
action at this slice boundary was to define the bounded proposer eligibility policy implemented
below and emit only typed Design IR patches that can be verified by the existing six-gate loop.

### Contract evidence — rollback-only screenshot repair proposer

The screenshot repair proposal v1 contract freezes the first evidence-to-patch boundary without
pretending that pixel localization supplies a target value. It accepts two complete,
integrity-verified candidate evidence records: a current candidate that passes safety through
structure but fails exact pixels, and a prior baseline with the same lineage and pixel denominator
that has strictly fewer mismatches and no larger maximum channel delta. The records must differ in
exactly one existing non-expression `properties` field, and the current localization must attribute
at least one mismatched pixel to that node. Only the exact typed baseline value may become one
`replace-field` operation. Caller targets, OCR/vision guesses, modifier and structure changes,
behavior/state/semantics changes, multiple differences, and novel mismatches have no eligible
proposal. Public repair activation remains false, and every eventual patch must re-enter the typed
applier and complete six-gate evaluator.

The frozen real denominator maps the accepted `Hello` regression back to the exact baseline
`Welcome` value with change fingerprint
`7a126542aa952fc46f0859d530d72c8fd7e93d268c696e5b514e4cc2c3f9f945` and proposal
fingerprint `47bffb223b1503cb603f77840ea46ec9ae375bc7efa5637c5a3635adbcecce68`. Six no-change
denominators cover an already exact candidate, an earlier failed gate, a non-improving baseline,
multiple differences, an unlocalized changed node, and localization without a baseline value
difference. Two invalid denominators cover evidence-integrity and lineage drift, and cancellation
has its own result. The contract verifier passes 1/1 supported rollback, 6/6 no-change, 2/2 invalid,
and 1/1 cancelled cases while explicitly reporting `implementation: false` at the contract-only
boundary.

Phase 0 now verifies 18 schemas, and Node 25.6.0 passes 231/231 AI-tooling tests. The
dependency-free package contains 69 files and 1,863,534 declared bytes; its 334,488-byte archive
has SHA-256 `3facf1c0273e6a4a4cf309f9c66d9c8679abdf9c29e1bac3bed0e394e541d549`. Relative to the
localization package, the frozen proposal schema and guidance add one file, 5,563 declared bytes
(+0.30%), and 885 archive bytes (+0.27%), with no runtime dependency or provider boundary. This is
**improved** repair-scope honesty, deterministic rollback eligibility, and fail-closed coverage with
**no material Android runtime behavior change**. At that contract-only boundary it did not yet
prove executable proposal output or end-to-end rollback convergence; the implementation evidence
below closes exactly those two claims.

### Implementation evidence — deterministic single-property regression rollback

The internal screenshot repair proposer now verifies each input against the candidate-evidence,
repair-evaluation, Design IR, layout-comparison, pixel-comparison, localization, and proposal
schemas before considering a change. It reproduces canonical evidence, Design IR, localization,
and proposal identities; reproduces compact evaluation, layout, pixel, and patch identities; binds
the retained layout nodes and paths back to the exact Design IR; reconciles layout check totals,
pixel denominators, node attributions, and unassigned pixels; and requires the same base resolution,
input Design IR, pixel-reference request/output/PNG, viewport, and interpretation on both records.
Malformed, oversized, internally inconsistent, cross-lineage, and cancelled input fails closed.

Eligibility is deliberately not a general visual-repair heuristic. The current candidate must pass
the first five ordered gates and fail exact pixels. The baseline must pass the same first five gates,
use the same exact reference and compared-pixel count, contain strictly fewer mismatched pixels,
and have no larger maximum channel delta. A bounded 1,000-node, depth-64 comparison permits exactly
one existing non-expression field in `properties` to differ; replacing that current value with the
baseline value must make the complete canonical Design IR equal to the baseline. The changed node
must also own at least one current mismatched pixel. The proposer then seals one `replace-field`
operation and runs it through the existing typed patch validator and applier before publishing the
proposal. No pixel, OCR, vision, model, caller target, aggregate score, or network path supplies the
value.

The real source-bound 1079×2339 denominator reproduces the exact baseline evidence fingerprint
`ce8555c98b3febf00cdd23978da5c5af685efcddb17c0f2110b229ec26a7605a` and the `Hello`
regression evidence fingerprint
`e0bd2617d05017bf9fa864139ecc03535b35a3b8b7bbbf491c28884be0c60068`. The proposer emits
only `wireframe-title.properties.text = Welcome`, with change fingerprint
`7a126542aa952fc46f0859d530d72c8fd7e93d268c696e5b514e4cc2c3f9f945` and proposal
fingerprint `47bffb223b1503cb603f77840ea46ec9ae375bc7efa5637c5a3635adbcecce68`. Rebased onto the
current `Hello` resolution, that emitted patch passes safety, compilation, render, all 12 semantic
checks, all 15 structural checks, and the complete 2,523,781-pixel comparison, reducing the current
3,345 mismatches to zero. The rollback evaluation fingerprint is
`020019c2483980dcbcd3d6c3ca5148228d6330a46f6ca9dc48d4acc849ffc7f3`; its complete evidence
fingerprint is `f655efb37838921c557fe0455a0424a311ed9847af3e7de273ed805236d8263c`.

The focused suite covers deterministic proposal replay, typed-applier equality, exact candidates,
earlier-gate short circuits, non-improving baselines, multiple property differences, unlocalized
changes, localization without a baseline value difference, evidence-integrity drift, base-lineage
drift, exact-reference drift, and cancellation. The dedicated real verifier passes 1/1 supported
rollback, 6/6 no-change, 2/2 invalid, and 1/1 cancelled contract denominators, then evaluates three
real candidates and proves the emitted rollback through all six gates. Public repair activation
remains false.

Node 25.6.0 passes 238/238 AI-tooling tests, and Phase 0 remains at 18 schemas, 64 metrics,
73 cases, 70 fixture-backed cases, and nine screenshot-repair fixtures. The dependency-free offline
package now contains 70 files and 1,886,105 declared bytes; its 338,952-byte archive has SHA-256
`83ad316c9fba96da952c6fef195b3a949b3247405bb859f23af8a795e256c619`. Relative to the
contract-only package, the internal proposer adds one file, 22,571 declared bytes (+1.21%), and
4,464 archive bytes (+1.33%), with no runtime dependency, provider, network, or public tool mode.

This is **improved** deterministic rollback capability, evidence integrity, and end-to-end repair
verification with **no material Android runtime behavior change**. It proves only recovery of one
known single-property regression against an accepted better baseline. It does not prove how a
baseline becomes trusted, that arbitrary localized pixels reveal causality, novel value inference,
perceptual equivalence, or safe unattended repair. The next prerequisite is an explicit contract
for accepted baseline provenance and human authorization before any CLI/MCP repair workflow can
bind this internal proposer to the orchestrator.

### Contract evidence — human baseline acceptance and rollback authorization

Screenshot repair authorization v1 freezes the trust handoff that must precede any executable
repair workflow. One baseline-acceptance attestation binds an identified reviewer and receipt to the
exact baseline evidence fingerprint, a 40- or 64-hex immutable Git commit, an exact-evidence-only
scope, and completed visual and semantic review. A separate repair-approval attestation binds an
identified approver and receipt to the exact current candidate evidence, proposal, and typed change
fingerprints for one application with unattended execution disabled. The enclosing record also
binds baseline/current Design IR identities and the canonical exact pixel-reference identity.

The policy denies credentials, provider calls, network access, non-metadata logs, authorization
reuse, and more than one application. It deliberately treats reviewer trust and pre-application
revocation as host responsibilities. Receipt values are purpose-bound opaque content addresses,
not signatures: v1 does not authenticate a person or receipt, decide that a source revision or
baseline is trustworthy, or turn a successful proposal into authorization. Public repair mode and
execution authorization both remain false.

The frozen real record accepts baseline evidence
`ce8555c98b3febf00cdd23978da5c5af685efcddb17c0f2110b229ec26a7605a` at source revision
`a2faf25dc206b428936a42b3d0872007371592b3`, approves current evidence
`e0bd2617d05017bf9fa864139ecc03535b35a3b8b7bbbf491c28884be0c60068`, proposal
`47bffb223b1503cb603f77840ea46ec9ae375bc7efa5637c5a3635adbcecce68`, and change
`7a126542aa952fc46f0859d530d72c8fd7e93d268c696e5b514e4cc2c3f9f945`, and binds exact-reference
fingerprint `43673bdc72302871a3d4106704a2cf17357f0c1a459ee8bf8892749275859064`. Its authorization
fingerprint is `ba359be06ef055db9ca32d7724dfe256b2d53a44aacbdec0f781d5825343cb46`.

The contract verifier passes 1/1 human-attested record, 10/10 invalid denominators, and 1/1
cancelled denominator. Invalid classes freeze baseline, candidate, proposal, change, and exact
pixel-reference lineage drift; movable source revisions; missing reviewers; unattended execution;
authorization-integrity drift; and credential-shaped fields. Phase 0 now verifies 19 schemas, and
Node 25.6.0 passes 239/239 AI-tooling tests. The dependency-free package contains 71 files and
1,891,779 declared bytes; its 339,834-byte archive has SHA-256
`6a5ad34cf5b9ad18cfda2f10ef365b44ccc3c9b877fddb3d5055e24695953d1e`. Relative to the proposer
package, the authorization contract adds one file, 5,674 declared bytes (+0.30%), and 882 archive
bytes (+0.26%), with no runtime dependency or public tool mode.

This is **improved** trust-boundary clarity and exact authorization lineage with **no material
Android runtime behavior change**. At the contract-only boundary it was not authenticated identity
infrastructure or an executable repair grant; the implementation below closes deterministic
validation while deliberately leaving both limitations in place.

### Implementation evidence — exact repair authorization validation

The packaged internal validator now consumes the complete baseline evidence, current evidence,
proposal, and authorization record. It validates the authorization and proposal schemas, byte
ceiling, typed patch, canonical fingerprints, immutable source-revision syntax, and distinct
purpose-bound receipts. It then re-runs the bounded proposer over the supplied evidence. Only an
exactly reproduced proposal may proceed to binding checks for baseline/current evidence, their
Design IR identities, canonical exact-reference identity, proposal, typed change, both reviewer
attestations, single-application limit, and unattended-execution denial.

The result is separately content-addressed and distinguishes `validated`, `invalid`, and
`cancelled`. Even a validated result carries `executionAuthorized: false`, external reviewer trust,
and unclaimed receipt authentication. The validator never applies the patch, contacts a provider or
network, accepts credentials, authenticates a person, checks host revocation state, or consumes an
authorization. Thus deterministic structural validation cannot silently become an execution grant.

The real source-bound gate evaluates the exact baseline and `Hello` regression, reproduces proposal
`47bffb223b1503cb603f77840ea46ec9ae375bc7efa5637c5a3635adbcecce68`, validates authorization
`ba359be06ef055db9ca32d7724dfe256b2d53a44aacbdec0f781d5825343cb46`, and emits validation
fingerprint `8c60e3e4ffc772f44df9e7408b4fd21ba0bd578cfb537558f57d0626594db957` with execution disabled.
The same run executes all 10 invalid mutations and one pre-validation cancellation: evidence,
proposal, change, and pixel-reference drift stay distinct from schema and authorization-integrity
failures.

Node 25.6.0 passes 243/243 AI-tooling tests, including deterministic validation replay, schema-valid
result identity, integrity versus lineage classification, ineligible proposal rejection, and
cancellation before proposal reproduction. Phase 0 remains at 19 schemas. The dependency-free
offline package now contains 72 files and 1,904,153 declared bytes; its 341,652-byte archive has
SHA-256 `5d155668d634a10da8a18cd843e5146f321c710043492c405edd82b1a4b3c649`. Relative to the
contract package, the validator adds one file, 12,374 declared bytes (+0.65%), and 1,818 archive
bytes (+0.53%), with no runtime dependency or public tool mode.

This is **improved** authorization integrity, proposal reproducibility, and claim separation with
**no material Android runtime behavior change**. Reviewer authentication, receipt revocation,
cross-process single-use consumption, patch execution, and public CLI/MCP activation remain
unclaimed. At that implementation boundary, the next prerequisite was the host interface that
would authenticate and consume a validated attestation exactly once; the contract below now freezes
that interface without implementing it.

### Contract evidence — trusted host repair grant lifecycle

Screenshot repair host grant v1 freezes that dynamic trust interface as a content-addressed request
and a separate host decision. The request binds validation, authorization, baseline/current
evidence, candidate Design IR, exact pixel reference, proposal, typed change, and immutable baseline
source revision identities. It also requires a named trust domain, out-of-band credential transport,
fingerprint-only logs, no tool-owned provider or network call, attended execution, and no public
tool mode.

A structurally granted decision must arrive through `trusted-host-callback-only`. It contains two
purpose-distinct authenticated principals and review receipts, two active revocation checks made
immediately before reservation, and one durable `atomic-single-use-reservation`. Attempt number and
maximum attempts are both one; reuse, retry after failure, caller-supplied decisions, credential
input, and unattended execution are all forbidden. The exact validation, authorization, candidate
evidence, proposal, change, and target Design IR identities are rebound into the grant. Reserving an
attempt is terminal even when a later patch application fails, avoiding a check-then-write reuse
window.

The checked-in request fingerprint is
`ab8134e2be383dbe8c2b376aceb172d2132f0268e0c4870999a682c9fc660dbd`; its synthetic granted
decision fingerprint is
`8f5953ee7fec99c15d446d3adb1877ef1dd95a2ff5dbffbab27de119d6974c2e`. The word
`synthetic` is material: a JSON file cannot authenticate its own host provenance. The contract
explicitly gives no authority to a decision loaded from a file, stdin, CLI argument, MCP argument,
or network payload. Only a future trusted callback boundary can supply such authority.

At contract-freeze commit
`a9e06c168746015902acb029a319075ee13bb53d`, the verifier passed 1/1 structurally valid synthetic
grant, 17/17 invalid denominators,
4/4 denied decisions, and 1/1 cancelled decision. It keeps authentication failure, revocation,
already-consumed state, policy denial, integrity drift, lineage drift, and malformed input distinct.
Phase 0 now verifies 20 schemas, and Node 25.6.0 passes 244/244 AI-tooling tests. The
dependency-free offline package contains 73 files and 1,916,933 declared bytes; its 343,045-byte
archive has SHA-256 `684f8991f1d4e9856dd099c170f818b3d20b9514d9edc0e0bf4bd3270c5dda25`.
Relative to the authorization-validator package, the host-grant contract adds one file, 12,780
declared bytes (+0.67%), and 1,393 archive bytes (+0.41%), with no runtime dependency or public tool
mode.

This is **improved** dynamic trust-boundary precision and fail-closed single-use semantics with
**no material Android runtime behavior change**. It does not implement or locally verify host
identity, authentication receipts, revocation checks, durable reservation, patch application,
failure recovery, or public execution. At this boundary the next prerequisite was an isolated
callback adapter plus a deterministic durable test host; the implementation below supplies those
two pieces without creating an executor.

### Implementation evidence — isolated trusted-host grant adapter

The packaged internal adapter accepts exactly one structurally validated authorization result and
its exact authorization record. It revalidates both schemas and content addresses, binds reviewer,
receipt, evidence, proposal, source-revision, reference, change, and Design IR identities, and then
builds the frozen host-grant request. The host reservation callback is retained in a private
process-local registry behind an immutable handle. Serializing that handle preserves only the trust
domain label and loses callback authority; extra `decision` input is rejected before the callback.
No file, stdin, CLI, MCP, or network-supplied decision can enter the trusted path.

The returned host decision is accepted only after schema, byte ceiling, content address, trust
domain, purpose-distinct principal and review-receipt, active revocation, unique host-proof receipt,
atomic reservation, attended-use, and complete repair-lineage checks pass. Host exceptions become
non-authorizing `host-failed` decisions. Cancellation before the callback makes no reservation;
cancellation or validation failure after the callback never retains its grant and does not imply
that a host reservation can be reused.

The deterministic file-backed test host creates one mode-`0600` reservation record with exclusive
creation and synchronizes it before returning the synthetic grant. Two concurrent requests produce
exactly one grant and one `already-consumed` denial. Reopening the same store through a new host
instance also denies replay. Separate tests reject serialized handles, caller-injected decisions,
validly rehashed lineage drift, changed validation identity, host failure, and cancellation before
and after the callback. The contract verifier now passes 1/1 synthetic grant, 17/17 invalid, 5/5
denied, and 1/1 cancelled denominators; its adapter replay reports one direct-callback grant, zero
replayed grants, and zero accepted serialized decisions.

Node 25.6.0 passes 251/251 AI-tooling tests, and Phase 0 remains at 20 schemas. The dependency-free
offline package contains 74 files and 1,928,701 declared bytes; its 345,328-byte archive has SHA-256
`c7981315ccf7760baee42d9fdc9619eaf9b6fa188bf8ff86dc66bc956d6d1425`. Relative to the
contract package, the adapter adds one file, 11,768 declared bytes (+0.61%), and 2,283 archive bytes
(+0.67%), with no runtime dependency or public tool mode.

This is **improved** callback isolation, decision integrity, and single-use replay resistance with
**no material Android runtime behavior change**. The test host demonstrates storage semantics; it
does not authenticate real people or constitute a production host. No patch is applied, no source
or Design IR is persisted, no execution receipt exists, and public CLI/MCP repair remains disabled.
At this implementation boundary, the next prerequisite was a terminal outcome contract covering
success, failure, cancellation, and unknown effects after reservation; the contract below supplies
that boundary without adding an executor.

### Contract evidence — terminal execution outcomes and receipts

Screenshot repair execution outcome v1 freezes the boundary after a trusted host has atomically
reserved one authorization. Each outcome binds the exact host-grant decision and request,
authorization, proposal, typed change, input Design IR, reservation receipt, and trust domain. Its
attempt record is always consumed, attempt one of one, attended, terminal, non-reusable, and
non-retryable. The executor profile is restricted to a typed in-memory Design IR patch with no
persistent source write, caller-supplied outcome, public mode, credential input, provider call,
tool network access, or content-bearing log.

Four disjoint schema branches keep effect claims honest. `applied` requires `committed` plus exact
result Design IR and patch-output fingerprints and is the only output-bearing state. `failed` and
`cancelled` require `not-committed` and null output identities. `indeterminate` requires `unknown`
and covers the crash window where an effect cannot be proved; it is still terminal and cannot be
executed again. Every branch carries a host-issued terminal receipt bound to the same trust domain
and reservation, and the outcome receipt must differ from the reservation receipt.

The frozen verifier passes 4/4 terminal outcomes and 24/24 invalid mutations. It separately rejects
fingerprint drift; every grant, request, authorization, proposal, change, Design IR, reservation,
and trust-domain mismatch; second or non-terminal attempts; retry or unattended flags; source-write,
public, or caller-outcome activation; applied/failed/cancelled/indeterminate effect mismatches;
receipt issuer or reservation drift; receipt reuse; and raw Design IR output. Exactly 1/1 outcome
exposes output fingerprints and 0/0 outcomes are retryable.

Node 25.6.0 passes 252/252 AI-tooling tests, and Phase 0 verifies 21 schemas. The dependency-free
offline package contains 75 files and 1,939,636 declared bytes; its 346,442-byte archive has SHA-256
`5f25a417c4c85c020f7f2e499319fb99449eb28a55fa4cd4de3dc34bd8d337e6`. Relative to the host-adapter
package, the contract schema and packaged explanation add one file, 10,935 declared bytes (+0.57%),
and 1,114 archive bytes (+0.32%), with no runtime dependency or executable registration.

This is **improved** terminal-state completeness, crash-window honesty, and replay resistance with
**no material Android runtime behavior change**. It does not implement an executor, authenticate a
production outcome receipt, make effect and receipt persistence atomic, recover an indeterminate
attempt, write application source, or activate CLI/MCP repair. The next prerequisite is an isolated
attended in-memory executor plus trusted-host terminal callback that implement this exact contract.

### Implementation evidence — bounded XML to Design IR

The first Phase 4 implementation uses a dependency-free scanner and tree builder rather than
executing Android resource tooling or application Gradle code. It enforces the frozen byte, depth,
node, attribute, and unsupported-fragment ceilings; accepts only a bounded repository-relative
source identity; checks tag matching and duplicate attributes; and rejects `DOCTYPE`, declared
entities, CDATA, unsupported processing instructions, unknown namespaces, duplicate Android IDs,
and malformed input before any generation claim. Attribute values are parsed into typed IR values,
not retained as an untyped property bag.

On 2026-08-29, Node 25.6.0 passed 81/81 AI-tooling tests in 1.33 seconds. The dedicated Phase 4 gate
matched 1/1 schema golden, 1/1 repeated deterministic conversion, 4/4 complete node provenance,
1/1 resource-preservation denominator, and 3/3 unsupported fixtures. The compiled quality-build
suite plus root `verifyAiDesignIr` passed 18 tasks (4 executed and 14 up-to-date) in 14 seconds.
Compared with the contract-only baseline, the result is **improved** deterministic migration and
unsupported-source localization with **no material runtime change** because the parser and gate are
downstream development tooling and execute no application code.

Limitations: this evidence proves only IR conversion for the four-element XML v1 subset. It does
not yet generate Kotlin, compile a converted result, render a migrated layout, inspect call sites,
resolve styles/resources, or support `include`, `merge`, `FrameLayout`, ConstraintLayout, lists,
custom Views, Data Binding, or behavior. The next action is deterministic IR-to-Kotlin generation
with the existing hermetic compiler as its acceptance boundary.

### Implementation evidence — deterministic Kotlin generation and compilation

The second Phase 4 implementation validates Design IR v1 again before generation, accepts only the
normalized five target node kinds, and rejects unknown properties, semantics, state, events,
modifiers, expressions, or resource types. It emits sorted imports, escaped Kotlin literals,
deterministically deconflicted parameter identifiers, stable keys, caller `String` resource
bindings, caller-owned `TextFieldState`, and a migration report that always requires resource,
state/restoration, ViewBinding, listener, adapter, and imperative-mutation review. Blocked IR never
receives Kotlin output.

The first real compile correctly rejected an assumed
`com.viewcompose.ui.foundation.TextFieldState` import. The generator was corrected to the canonical
`com.viewcompose.text.TextFieldState` declaration and compiled again; this demonstrates why
generation is not accepted on string comparison alone. The accepted cache-miss compile completed
in 10.85 seconds, produced two class files totaling 5,484 bytes, and returned class fingerprint
`f46767ea9e87195cc74237a2cac1b230dbe76fa94cc9107caf134dcedc9518cd`. The deterministic Kotlin
fingerprint is `6c4f6dafef9e0b4808eefab440d14e331b1a3b55bc8becff7a05d3669cc73be1`.

On 2026-08-29, Node 25.6.0 passed 86/86 AI-tooling tests in 1.34 seconds. The dedicated XML gate
matched 1/1 Kotlin golden, 1/1 resource migration report, and 1/1 hermetic compile. The compiled
quality-build suite plus both Phase 4 root tasks passed 19 tasks (7 executed and 12 up-to-date) in
32 seconds. Compared with IR-only conversion, the result is **improved** executable fidelity with
**no material runtime change** because generation and compilation remain downstream tooling.

Limitations: the generated function deliberately accepts resolved strings instead of directly
calling Android `stringResource`; the migration report makes that host-boundary work explicit.
Compilation proves API and type correctness in the frozen artifact lane, not runtime rendering,
resource resolution, call-site completeness, visual parity, or behavior. The accepted core is not
an application rewrite and does not remove XML or modify call sites.

### Implementation evidence — CLI, MCP, distribution, and migration workflow

The accepted converter now enters the same immutable request/result envelope as every other AI
tool. `convert_xml_to_viewcompose` requires callers to choose `generate` or `compile`: generation
remains dependency-free and standalone, while compilation requires the exact source checkout and
the existing hermetic compiler. CLI and MCP share one dispatcher and catalog. The installed modern
MCP lane executes the frozen conversion rather than merely listing its schema; legacy discovery
still returns the same ordered nine-tool catalog without implicit downgrade.

The source-identity gate rejects a mismatched checkout before Gradle. The accepted offline
lifecycle then uses the matching checkout to compile the generated login function and returns class
fingerprint `f46767ea9e87195cc74237a2cac1b230dbe76fa94cc9107caf134dcedc9518cd`.
The `viewcompose-convert-xml` consumer workflow requires generation review, compile evidence, final
code validation after integration, and explicit ownership of resources, state, listeners,
ViewBinding, and imperative call sites. It raises no automatic-conversion claim for unsupported
source.

On 2026-08-29, Node 25.6.0 passed 91/91 AI-tooling tests in 1.37 seconds and the client-neutral
workflow gate matched 6/6 exact contracts. Two clean package builds produced the same 39-file,
249,646-byte archive with SHA-256
`7b7c8c9f6a108effd992e30cb4ede0b256f0a84ea62663fb2f568cf00d6ea57b`. The offline
install/uninstall lifecycle, SPDX/license inventory, modern and legacy MCP versions, standalone XML
generation, mismatched-checkout rejection, and real XML compilation each met their complete frozen
denominator.

Compared with the source-only generator, the result is **improved** consumer interoperability and
compile-backed migration evidence with **no material runtime change** because the converter,
protocol adapters, skills, and distribution remain downstream development tooling. The evidence is
still limited to one supported four-node layout, three unsupported fixture classes, macOS, and the
local unpublished npm artifact. It does not prove application call-site completeness, resource
resolution, runtime behavior, rendering, visual parity, accessibility, Windows installation, or a
public-registry lifecycle. The current user-facing boundary and accepted evidence are documented in
[`tools/ai/README.md`](../../../tools/ai/README.md), which is already linked from the canonical
documentation index.

A candidate dedicated bilingual migration route passed generation, version-history routes, site
shell, and the 528-page accessibility audit but produced 49,373,569 non-API bytes, 195,354.6 bytes
above the unchanged 49,178,214.4-byte ceiling. The candidate was rejected instead of raising the
budget or duplicating the local tooling contract. This is **no material change** to public site
behavior and preserves the existing capacity ratchet; a future dedicated route must first recover
at least that measured headroom structurally. Consolidating the owning site-operations contract
then reduced the route-free 49,195,449-byte attempt by 24,110 bytes to 49,171,339, leaving 6,875.4
bytes under the unchanged ceiling. The 526-page bilingual build, accessibility audit, 133 immutable
API/manual routes, both search indexes, and all site budgets passed; accepted warm retries took
34.2–59.8 seconds. The accepted
representation is **improved** while XML-tool behavior remains **no material change**.

Repository-wide acceptance was initially **inconclusive** because the clean `qaQuick` run exhausted
the local volume after 2,059 actionable tasks (2,042 executed and 17 up-to-date). The failing
Preview Gradle plugin functional test reported `No space left on device` while writing its nested
build cache; its isolated retry then passed all 23 actionable tasks. A first incremental root retry
again exhausted the volume while serializing `viewcompose-preview` test results, so the resulting
`EOFException` and 25 temporary-directory failures were classified as the same environmental
failure rather than framework regressions. Only reproducible worktree outputs, Docusaurus caches,
the lockfile-reconstructible `website/node_modules` tree, and the corrupted test-task output were
removed. The recovered `viewcompose-preview` suite passed all 171 actionable tasks, and the final
root `qaQuick` passed all 2,278 actionable tasks (362 executed and 1,916 up-to-date) in 3 minutes.
The accepted conclusion is **no material regression** across the repository gate; low local disk
capacity remains an evidence limitation and should be provisioned before the next clean lifecycle.

### Phase 4A: Design IR and code generation

1. Freeze the tooling-only IR schema and provenance/unsupported representation from Phase 0.
2. Implement deterministic IR validation, normalization, stable serialization, and ViewCompose code
   generation using the current knowledge bundle.
3. Preserve stable resource references, IDs/test tags, semantic roles, event placeholders, source
   spans, and generation decisions.
4. Require generated Kotlin to pass Phase 2 compilation; renderable fixtures also pass Preview
   diagnostics before success.

### Phase 4B: XML to ViewCompose

1. Parse layout XML, includes, merge roots, style/theme references, dimensions, strings, colors,
   drawables, IDs, layout parameters, common containers, ConstraintLayout relations, visibility,
   content descriptions, and supported state selectors into IR.
2. Inventory call-site dependencies such as ViewBinding references, listeners, adapters, custom
   Views, data-binding expressions, and imperative mutations. Do not invent replacements for
   behavior that source analysis cannot establish.
3. Return ViewCompose code, required dependencies/imports/resources, an unsupported-semantics report,
   call-site migration checklist, source-to-output mapping, compile/render evidence, and optional
   explicit patch plan.
4. Add structural, resource-preservation, compile, render, accessibility, and selected screenshot
   goldens for a versioned XML fixture corpus.

The initial supported subset prioritizes `LinearLayout`, `FrameLayout`, common ConstraintLayout
relationships, `TextView`, `ImageView`, `Button`, simple lists, Material controls, and resource
references. Recycler adapters, arbitrary custom Views, data binding, animations, and behavior-heavy
screens remain unsupported until separately evaluated.

### Phase 4C: Compose mapping and deterministic analysis

1. Publish a versioned semantic mapping table for supported Compose concepts, including differences
   in layout, modifier ordering, state, effects, lifecycle, lazy content, navigation, theming,
   resources, accessibility, and Android interop.
2. Deliver guidance/skills before an automatic converter so agents can use Compose familiarity
   without pretending APIs are identical.
3. If activated by corpus evidence, parse a bounded Kotlin/Compose subset into IR with compiler or
   AST-backed semantics rather than regex replacement. Unsupported Kotlin, receiver ambiguity,
   custom composables, state ownership, and side effects remain explicit.
4. Expand `analyze_viewcompose` rules over typed structure and diagnostics for nesting, duplicate or
   conflicting modifiers, state-driven View recreation, lifecycle/resource leakage, accessibility,
   touch target, unit, theme, and performance risks.

### Acceptance gate

- Supported XML and Compose fixtures preserve declared structure, resources, semantics, and
  behavior placeholders at the frozen thresholds; every output compiles.
- Unsupported input is localized and reported. The tool never silently drops a custom node,
  expression, listener, resource, or state/effect contract.
- Generated output remains readable and reviewable; minimized line count or visual similarity alone
  cannot win the evaluation.
- `convert_xml_to_viewcompose` is added to CLI/MCP only after Phase 4B acceptance;
  `convert_compose_to_viewcompose` is added only after Phase 4C acceptance.

## Phase 5 — Prompt, screenshot, and Figma visual loop

### Purpose

Add AI-native input adapters only after deterministic IR, code generation, compilation, rendering,
and comparison are independently trustworthy.

### Deliverables

1. A provider-neutral request and response boundary that converts prompt, screenshot, or Figma
   design data into Design IR with per-node provenance, confidence, and unresolved questions.
2. Screenshot preprocessing and bounded image handling for density, crop, system bars, font scale,
   color space, transparency, and sensitive-content redaction.
3. A Figma adapter that preserves frames, auto layout, components/variants, variables/tokens,
   typography, assets, constraints, and explicit access provenance without importing provider
   credentials into the core.
4. An evaluation order that checks IR validity, compilation, render diagnostics, text/content,
   resources, semantics/accessibility, layout tree and geometry, then perceptual or pixel similarity.
5. A bounded repair loop with maximum iterations, reason-coded changes, before/after evidence,
   convergence/oscillation detection, and a safe incomplete result when the threshold is not met.
6. Human-reviewed phone/tablet, light/dark, locale, RTL, density, and font-scale corpus lanes with
   privacy and asset-license records.

### Acceptance gate

- A screenshot or Figma similarity score cannot override compile, semantic, accessibility,
  unsupported-content, or safety failure.
- Evaluation separates text/content correctness, structure, geometry, style, assets, and pixels so a
  single aggregate score cannot hide a product defect.
- Provider-offline deterministic stages remain fully usable; optional provider calls are explicit,
  cancelable, redactable, auditable, and excluded from default logs and caches.
- `generate_ui` enters MCP only after its orchestration and evidence contract is stable; it reports
  which stages and provider-dependent operations actually ran.

## Phase 6 — Stabilization, distribution, and evidence-gated positioning

### Purpose

Turn accepted experiments into a maintainable product rather than leaving a demo server tied to
repository internals.

### Deliverables

1. Stable tool/bundle/IR/protocol compatibility policy, deprecation window, migration tests, and
   released-version fixture matrix.
2. Reproducible packages, signed checksums, dependency and license inventory, vulnerability review,
   update/uninstall path, support matrix, and offline installation documentation.
3. Performance and reliability budgets for cold start, query, compile, render, conversion, memory,
   cache, cancellation, concurrent clients, and long-running server cleanup.
4. Privacy, security, logging, retention, disclosure, incident, and external-contribution policy for
   prompts, screenshots, design documents, source, diagnostics, and caches.
5. Public setup, tutorials, troubleshooting, API/tool reference, migration guides, limitations,
   release notes, and current Chinese mirrors for every active public page.
6. Longitudinal evaluation reports comparing the same corpus, versions, configurations, and model
   conditions. Model-dependent and deterministic results remain separate.

### Acceptance gate

- Supported released ViewCompose lanes reproduce their indexed API, samples, compile, render, and
  conversion results after tool upgrades.
- Security, resource, compatibility, and reliability gates run in CI or a documented scheduled
  environment with named owners and triage policy.
- Product language distinguishes “AI-ready,” “Agent-ready,” “AI-assisted,” and “AI-native.” The
  stronger “AI-first” statement is used only after the accepted longitudinal thresholds pass and
  limitations remain visible.

## Evaluation and verification matrix

Phase 0 freezes exact thresholds; later phases must at minimum own these gates:

| Concern | Required evidence | Earliest phase |
| --- | --- | --- |
| Knowledge freshness | Deterministic regeneration, schema validation, fingerprint drift, stable IDs, broken-link and removed-symbol tests | 1 |
| Retrieval correctness | Versioned positive/negative queries with top-k relevance and exact artifact/capability attribution | 1 |
| API hallucination | Fabricated, removed, wrong-artifact, wrong-overload, wrong-default, and wrong-version fixtures | 2 |
| Static diagnostic quality | Rule-level precision/recall, false-positive budget, severity/source-span goldens | 2 |
| Compilation | Pinned clean/failed snippets, dependency/resource lanes, normalized diagnostics, cache isolation | 2 |
| Render and diagnostics | Phone/tablet, theme, locale, RTL, font scale, failure, timeout, cancellation, and deterministic output lanes | 2 |
| Project safety | Traversal, symlink, secret, output-size, file-count, prompt-injection text, cancellation, and read-only tests | 2 |
| Runtime isolation | Dependency graph, release packaging, startup/hot-path, network, thread, allocation, and ADR-0009 gates | 2 onward |
| Protocol parity | CLI/MCP schema and semantic parity, compatibility negotiation, malformed request, output limits | 3 |
| Agent workflow | End-to-end retrieve/generate/compile/render/repair fixtures with step evidence | 3 |
| XML migration | Structural/resource/call-site preservation, unsupported honesty, compile, render, accessibility, screenshot goldens | 4 |
| Compose migration | Semantic mapping coverage, compiler/AST fixture support, state/effect differences, unsupported honesty | 4 |
| Visual generation | Content, semantics, structure, geometry, style, assets, pixel/perceptual, repair convergence, human review | 5 |
| Operations | Packaging, upgrade, compatibility, security, privacy, licenses, cleanup, concurrency, longitudinal reports | 6 |

Accepted evidence must record comparison context, absolute results, normalized change, conclusion,
limitations, and next action. Raw benchmark or evaluation output does not close a phase.

## Metric contract candidates for Phase 0

Phase 0 may adjust exact values, but it must explicitly accept or replace these candidate release
gates rather than leaving them qualitative:

1. zero stale or nondeterministic knowledge-bundle output for a supported source revision;
2. every indexed current capability has a valid artifact, signature source, compiled sample, and
   canonical link;
3. all deliberately fabricated or removed public symbols in the curated corpus are rejected before
   delivery, with no unexplained rejection of the canonical compiled samples;
4. every result labeled “compiled” is reproduced by the hermetic compiler in the declared lane;
5. every result labeled “rendered” includes a successful compile, configuration, runner version,
   output fingerprint, and diagnostics summary;
6. all path escape, arbitrary build execution, secret-read, resource-limit, timeout, cancellation,
   and cache-cross-lane adversarial fixtures fail closed;
7. zero AI-tooling dependency, provider SDK, background worker, network request, or recurring work in
   supported release-runtime artifacts when the tooling is absent;
8. supported converter fixtures compile at 100%, while preservation, unsupported-case, diagnostic,
   semantic, visual, latency, and resource thresholds remain separately visible;
9. model-dependent metrics report provider/model/configuration/date and cannot substitute for
   deterministic retrieval, compile, render, security, or compatibility gates.

## Documentation and ownership contract

Each phase updates the relevant active documents in the same implementation slice:

- architecture and ADRs own boundaries, isolation, versioning, trust, and IR decisions;
- `docs/tooling/` owns installation, operation, security, validation modes, MCP/CLI reference,
  troubleshooting, and evidence interpretation;
- `docs/modules/` owns any separately published artifact's dependency, API, compatibility, and
  operational contract;
- `docs/migration/` owns XML and Compose supported subsets, semantic differences, generated output,
  call-site work, and unsupported cases;
- compiled samples own copyable code; generated AI data references them rather than duplicating
  uncompiled snippets;
- this plan and the unified roadmap own current phase/status and next action until archival;
- every active handwritten public English page receives a current Simplified Chinese mirror in the
  same change. Temporary plan details remain English-only under repository policy.

New or changed public/protected API must complete capability identity, structured impact
dispositions, Q level, applicable contract fields, canonical KDoc/Javadoc, compiled Q3 samples,
owning-module documentation, public API dumps, and immutable Changesets before merge. A tooling-only
change still records why published artifacts have no documentation or release impact.

## Ordering and parallelism constraints

1. Phase 0 blocks all implementation phases.
2. Phase 1 is the only knowledge source for Phases 2–6.
3. Phase 2 must accept static, compile, render, project safety, and runtime-isolation foundations
   before Phase 3 exposes them as supported public tools.
4. Phase 3 Foundation MVP may ship before conversion, but its evaluation and compatibility
   contracts cannot be weakened to accelerate Phase 4.
5. Phase 4A Design IR and generator block XML and Compose converters. XML is implemented and
   accepted before automatic Compose conversion.
6. Phase 5 may prototype fixture adapters after Phase 4A, but no supported visual tool ships until
   Phase 2 render evidence and Phase 4 generator evidence pass.
7. Phase 6 begins operational hardening during Phase 3 and closes only after every activated lane
   has longitudinal evidence. Unactivated later lanes do not block a deliberately scoped earlier
   MVP.

Within a phase, schema/test fixtures, deterministic implementation, documentation, and security
work may proceed in parallel only after their owning contract is frozen. Work does not bypass an
earlier acceptance gate by publishing an “experimental” alias on a supported path.

## Completion and archival

This plan is complete only when every activated lane has passed its declared gate, all published
and tool schemas have compatibility evidence, the active roadmap reflects the stable state, release
intent is closed, and no unresolved phase is hidden behind aggregate metrics.

If work intentionally stops after the Foundation or Migration MVP, split the unactivated later
lanes into a newly accepted plan, state the deferred triggers in the roadmap, and archive this plan
with the completed evidence. Do not keep a permanently active umbrella plan or convert incomplete
future ideas into implied current support.
