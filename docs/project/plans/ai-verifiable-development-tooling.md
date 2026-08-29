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
next_action: Freeze the provider-neutral screenshot-to-Design-IR request, preprocessing, privacy, and evaluation boundary while keeping model/provider execution outside the deterministic tooling core.
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
the public `compared` evidence gate through the installed package. The next foundational boundary is
provider-neutral screenshot input and preprocessing before any model-backed generation begins.

Last verified: 2026-08-30.

Next action: freeze screenshot-to-Design-IR input, preprocessing, privacy, and evaluation contracts
before adding any provider-backed generation or repair adapter.

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
