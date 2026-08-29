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
last_verified: 2026-08-29
next_action: Freeze the next XML migration increment for Android resource/style resolution and read-only call-site dependency inventory before widening the accepted element subset.
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
sixth client-neutral consumer workflow.

Last verified: 2026-08-29.

Next action: freeze the next XML migration increment for Android resource/style resolution and
read-only call-site dependency inventory before widening the accepted element subset.

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
