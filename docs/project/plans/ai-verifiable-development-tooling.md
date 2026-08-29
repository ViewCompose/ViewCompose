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
next_action: Complete Phase 0 by accepting the AI-tooling ADR, threat model, evaluation corpus, metric thresholds, bundle schema, version-lane rules, and capability-impact dispositions before implementing llms.txt, MCP, or converters.
maven_release_changesets: []
---

# AI-Verifiable Development Tooling Plan

## Status

Active. The repository and SceneView comparison audit is complete. No implementation phase has
started. Phase 0 contract and evaluation freeze is the only open implementation entry point.

Last verified: 2026-08-29.

Next action: accept the AI-tooling ADR, threat model, evaluation corpus, metric thresholds, bundle
schema, version-lane rules, and capability-impact dispositions before implementing `llms.txt`, MCP,
or converters.

## Maven release changesets

- None.

## Release intent rationale

This initial slice changes repository planning and current roadmap ownership only. It does not
change a published artifact's production source, publication inputs, or compiled API samples, so no
immutable Maven release Changeset is required. Later phases must add one Changeset per affected
published artifact, or record an explicit ignored disposition with a concrete reason, in the same
slice that introduces publication-relevant implementation.

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
| `render_code` | Render an already compilable supported snippet through the Preview adapter |
| `diagnose_layout` | Interpret render/layout tree and structured diagnostics using deterministic rules |
| `analyze_project` | Run the bounded, read-only Phase 2 project inventory and findings pipeline |

`generate_ui`, `debug_issue`, and automatic repair are initially client workflows over these
deterministic tools, not opaque model calls inside the server. This keeps providers replaceable and
makes every step inspectable. Conversion tools enter only with Phase 4 evidence.

### Additional deliverables

1. A local stdio MCP server with schema negotiation, capability discovery, version selection,
   structured errors, cancellation, progress, output limits, safe logging, and no default network
   listener.
2. A stable CLI over the same service/core for CI, debugging, and clients without MCP.
3. Client-neutral consumer skills for creating a screen, retrieving API, reviewing code, debugging
   layout, and validating before delivery. Contributor workflows remain separate from framework
   consumer workflows.
4. Thin documented adapters for supported coding agents. Repository `AGENTS.md` continues to govern
   contribution; provider-specific root files are not added merely as aliases.
5. Packaging, checksums, SBOM/license review, installation/uninstallation, protocol compatibility,
   offline operation, and a minimal end-to-end example in CI.

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
