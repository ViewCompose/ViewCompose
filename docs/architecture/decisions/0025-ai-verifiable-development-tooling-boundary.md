---
schema_version: 2
document_id: architecture.ai-verifiable-development-tooling-boundary
doc_type: architecture
slug: /architecture/decisions/ai-verifiable-development-tooling-boundary
owner:
  kind: project
  id: ai-development-tooling
version_lane: version-agnostic
capability_ids: []
artifact_ids: []
sample_ids: []
invariants:
  - AI-facing knowledge is generated from canonical signatures, Governance V2 records, compiled samples, documentation, and publication metadata; no transport owns a parallel API inventory.
  - Knowledge, static, compiled, rendered, and compared are distinct cumulative evidence levels, and a shallower result never claims a deeper status.
  - AI tooling, project inspection, compilation, conversion, model access, credentials, and caches remain outside ViewCompose runtime artifacts and the application process.
  - Untrusted snippets never execute inspected-project build logic; compilation uses a pinned tool-owned harness and project analysis is bounded and read-only by default.
  - Migration and visual inputs use a tooling-owned Design IR with provenance and unsupported semantics rather than runtime VNode or renderer state.
evidence:
  - tools/ai/contracts/versions.json
  - tools/ai/contracts/knowledge-bundle-manifest.schema.json
  - tools/ai/contracts/tool-envelope.schema.json
  - tools/ai/contracts/design-ir.schema.json
  - tools/ai/evaluation/metrics.json
  - tools/ai/evaluation/corpus.json
  - tools/ai/scripts/verify-phase0.mjs
  - docs/project/plans/ai-verifiable-development-tooling.md
  - ./gradlew verifyAiToolingContracts
  - ./gradlew verifyDevelopmentToolingIsolation
---

# ADR-0025: AI-verifiable development-tooling boundary

- Status: Accepted
- Date: 2026-08-29

## Context

ViewCompose already has canonical public signatures and KDoc, Governance V2 capability ownership,
compiled documentation samples, versioned artifact metadata, structured runtime diagnostics, and a
Layoutlib Preview runner. A coding agent can nevertheless hallucinate an API, select the wrong
artifact or version, misread a lifecycle rule, or return code that has never been compiled.

Adding only `llms.txt`, an MCP server, or provider-specific instructions would expose the same
failure through a new transport. A handwritten MCP inventory would also drift from the generated
Capability Reference. Treating a parser or symbol lookup as compilation would mislabel evidence,
while running an inspected project's Gradle build would execute arbitrary build scripts and plugins.

Migration and visual generation add a second class of risk. Android XML, Compose source,
screenshots, and Figma documents contain resource indirection, behavior, state, accessibility,
uncertainty, and unsupported content that runtime `VNode` cannot represent. A model-provider SDK or
credential inside framework runtime artifacts would also violate the five-layer dependency model
and ADR-0009's request-driven tooling isolation.

These boundaries affect every later tool and are expensive to change after clients depend on them.
They therefore require one accepted architecture decision and executable contracts before
implementation.

## Decision

### Canonical knowledge lineage

AI-facing knowledge has one lineage:

```text
published signatures and canonical KDoc
  + Governance V2 capability, document, sample, and artifact records
  + compiled sample source regions
  + publication and version metadata
  -> deterministic AI Knowledge Bundle
  -> llms.txt, local search, validators, CLI, MCP, skills, and evaluation
```

The bundle is generated; transports never scrape or maintain another public API list. Its manifest
records exact framework identity, source revision, capability fingerprint, generator version,
schema versions, file hashes, sizes, and counts. Generation is byte-for-byte deterministic for the
same inputs. Any input drift that should change the bundle blocks freshness verification until the
bundle is regenerated.

`llms.txt` is a compact discovery surface. It points to the exact version lanes, bundle, canonical
documentation, common invariants, samples, and tools; it is not an exhaustive duplicate of KDoc or
the Capability Reference.

### Independent version contracts

Four contract families evolve independently:

1. ViewCompose framework and artifact identity;
2. AI Knowledge Bundle schema and generator;
3. request/result tool envelope and transport adapters; and
4. tooling-only Design IR.

The evaluation corpus and metrics have their own schema because denominators and thresholds must
remain reproducible across tool upgrades. `current-source` means one exact Git revision. `released`
means exact coordinates and versions. Neither lane accepts `latest`, a branch name, or another
movable alias as identity.

Compatibility is exact-major. A consumer rejects an unsupported newer major and reports a stable
diagnostic. It does not guess fields, silently downgrade, or mix cache entries across schema,
framework, SDK, configuration, or generator lanes.

### Reserved capability ownership and Q levels

Phase 0 reserves these stable identities. A record enters Governance V2 only when application-facing
symbols exist, so the planning slice does not create placeholder symbols or samples.

| Capability ID | Activation | Initial Q level | Applicable contract focus |
| --- | --- | --- | --- |
| `tooling.ai-knowledge` | Phase 1 | Q3 | version, lineage, freshness, determinism, dependencies, limits |
| `tooling.ai-validation` | Phase 2 | Q3 | version, threading, execution, security, diagnostics, limits, performance |
| `tooling.ai-protocol` | Phase 3 | Q3 | version, transport, compatibility, cancellation, security, limits, errors |
| `tooling.ai-migration` | Phase 4 | Q3 | source/target versions, preservation, unsupported semantics, provenance, verification |
| `tooling.ai-visual-generation` | Phase 5 | Q2 | provider, privacy, assets, configuration, comparison, repair, limits |

Visual generation begins at Q2 because model and perception variance prevents a stable Q3 claim.
It can move to Q3 only after Phase 6 longitudinal evidence freezes supported providers,
configurations, tolerances, fallbacks, and compatibility. The other capabilities target Q3 at
activation because a supported tool must already have deterministic contracts and executable
fixtures rather than ship as an unverifiable public endpoint.

### Process, dependency, and provider isolation

Concrete AI tooling is a downstream `tools/` process or separately distributed development
artifact. Runtime, UI Foundation, Android Engine, design-system, integration, and application
aggregate artifacts cannot depend on it. The release classpath contains no MCP library, parser,
compiler harness, conversion engine, model SDK, network client, or AI cache.

AI tooling never installs an application-process observer or receiver merely because a dependency
is present. Preview rendering may use the existing explicit debug/test tool protocol, but activation
still follows ADR-0009 and ADR-0022. The inactive release path has zero AI-owned I/O, traversal,
serialization, network, threads, listeners, callbacks, report writes, or recurring work.

ViewCompose does not choose a model provider. Provider adapters are optional downstream processes
activated only by an explicit request. Credentials remain in the client's secret mechanism and are
never added to requests, source, generated code, screenshots, diagnostics, caches, logs, or
evidence. Deterministic knowledge, static validation, compilation, rendering, conversion, and
comparison remain usable without a provider or network.

### Cumulative evidence levels

Every tool result declares one of these levels:

| Level | Required evidence |
| --- | --- |
| `knowledge` | exact bundle fingerprint, framework identity, capability/symbol/sample lineage |
| `static` | knowledge plus deterministic parser/index/rule diagnostics |
| `compiled` | static plus successful pinned tool-owned Kotlin/Android compilation |
| `rendered` | compiled plus Preview runner configuration, output fingerprint, tree, and diagnostics |
| `compared` | rendered plus named structural, text, semantic, geometry, asset, and optional perceptual checks |

Evidence is cumulative. A parser, symbol match, or generated string cannot report `compiled`.
Compilation alone cannot report `rendered`. Pixel or vision similarity cannot override compilation,
semantics, accessibility, unsupported-content, or security failure.

Results use stable `VC-AI-*` diagnostic codes, severity, bounded safe message, next action, source
span when applicable, artifact/capability identity, elapsed time, cache status, truncation state,
and the deepest evidence that actually passed.

### Untrusted compilation and project inspection

`validate_code` places only submitted source and declared bounded resources into a tool-owned
harness pinned to accepted JDK, Kotlin, AGP, Android SDK, dependency coordinates, and ViewCompose
identity. The allowlist excludes inspected-project plugins, settings, repositories, init scripts,
annotation processors, build services, tasks, tests, and shell commands.

`analyze_project` is read-only by default. The caller supplies one normalized root. The analyzer
rejects path traversal and symlink escape, ignores secret and output patterns, observes file count,
byte, depth, time, and output limits, and treats source text as untrusted data rather than agent
instructions. A migration write path must first return a bounded patch plan with source-to-output
mapping. The client applies it explicitly.

Content-addressed caches are bounded, evictable, and scoped by every contract and environment lane.
Cancellation and timeout stop subprocesses and descendants, close files, discard partial cache
entries, and return stable failure evidence.

### Tooling-owned Design IR

XML, Compose, prompt, screenshot, and Figma inputs converge on a versioned Design IR owned by the
development tool. It represents node kind, layout relationship, typed properties, modifiers,
resources, semantics, event placeholders, state/visibility expressions, source provenance,
confidence, unsupported source, and stable identity.

The IR is not runtime `VNode`, a renderer node, or an Android `View`. Those types represent an
already executable tree and cannot preserve incomplete source, behavior placeholders, design
tokens, uncertainty, or unsupported fragments. Runtime compatibility therefore does not imply IR
compatibility, and IR changes do not add runtime dependencies.

Code generation retains unsupported fragments and provenance, then uses the same static, compile,
render, and comparison pipeline. It never silently drops a listener, expression, custom node,
resource, state/effect contract, or uncertain visual decision.

### Frozen Phase 0 metrics

`tools/ai/evaluation/metrics.json` is the machine-readable authority. Its initial gates include:

- zero nondeterministic or stale knowledge files and complete resolution of canonical capabilities;
- top-five retrieval recall of at least 0.95 and reciprocal rank 1 for exact symbol queries;
- rejection of every curated fabricated/removed symbol;
- per-rule precision at least 0.95 and recall at least 0.90;
- 100% compile and render success for declared supported fixtures;
- 100% fail-closed behavior for adversarial security fixtures and zero release-runtime occurrence;
- zero semantic mismatch between CLI and MCP over the same core request;
- 100% compile success for the declared supported XML subset and 100% reporting of labeled
  unsupported migration semantics;
- exact declared visual semantics, at least 0.98 of geometry nodes within the frozen tolerance, and
  no more than five non-oscillating repair iterations.

Every metric names its denominator through stable corpus case IDs, direction, threshold, unit, and
environment. Model-dependent reports additionally record provider, model, configuration, and date;
they cannot replace deterministic gates.

## Threat model and required controls

| Threat | Required control | Failure behavior |
| --- | --- | --- |
| Fabricated, removed, or wrong-version API | Generated bundle lineage, static index, hermetic compile | reject with symbol/artifact/version diagnostic |
| Malicious Gradle/settings/plugin or annotation processor | never execute project build logic; fixed harness and allowlist | reject the operation before process creation |
| Path traversal or symlink escape | canonical root containment and per-segment symlink policy | reject before reading or writing |
| Secret, signing key, local properties, or credential disclosure | default exclusions, content redaction, bounded safe diagnostics, no secret logging | omit content and report redacted finding |
| Prompt injection inside source or design text | treat inspected content as data; deterministic operations ignore embedded instructions | preserve text as source data only |
| Dependency substitution or remote repository drift | exact coordinates, checksums/locks, repository allowlist, offline deterministic core | fail closed on unresolved or mismatched dependency |
| Source, resource, zip, XML, or image bomb | byte/count/depth/dimension/decode/time/output limits | cancel and return limit diagnostic without partial cache |
| Hung or forked compiler/render process | deadline, process-tree cancellation, isolated output, cleanup verification | terminate descendants and discard partial evidence |
| Cache poisoning or cross-version reuse | content address plus schema/framework/toolchain/configuration namespace | miss or evict; never reuse ambiguous entry |
| Unsafe patch application | read-only default, explicit patch plan, root containment, preimage fingerprint | refuse changed/escaping target and preserve source |
| Credential-bearing provider request or log | explicit adapter, BYO secret channel, redaction, no telemetry by default | block request or omit sensitive field |
| Pixel-perfect but semantically wrong output | cumulative evidence and separate semantic/accessibility gates | comparison fails regardless of visual score |

Residual model risks include nondeterminism, provider retention outside ViewCompose control, visual
ambiguity, and source-license constraints. Provider adapters must disclose them and may remain
experimental even when deterministic stages are stable.

## Consequences

### Positive

- Every client consumes one versioned source of framework truth.
- MCP, CLI, skills, and future adapters share one core rather than reimplement validation.
- Compilation and rendering produce evidence that can drive bounded repair.
- Runtime artifacts remain provider-neutral and carry no AI-tooling footprint.
- Unsupported migration and visual semantics remain reviewable instead of disappearing.
- Accuracy and product claims have reproducible denominators before implementation.

### Costs

- Phase 0 and knowledge generation delay visible MCP features.
- Hermetic Android compilation and Preview rendering require toolchain packaging, cache, resource,
  cancellation, and compatibility work.
- Strict version identity rejects convenient movable aliases.
- Corpus and metric maintenance becomes a required part of every supported capability change.
- Visual features remain Q2 until longitudinal evidence supports a narrower stable contract.

## Rejected alternatives

### Handwrite llms.txt and MCP API lists

Rejected because duplicate inventories drift from signatures, Governance V2, samples, artifacts,
and releases. Only compact prose rules without a canonical structured owner may remain handwritten.

### Let the MCP server call a preferred model

Rejected because it couples credentials, network, cost, privacy, and provider lifecycle to framework
tooling and makes deterministic evaluation impossible. The client orchestrates models.

### Validate by parsing or symbol lookup only

Rejected because Kotlin overloads, receivers, types, resources, dependencies, and compiler behavior
cannot be proven statically. Static validation remains useful but cannot claim compilation.

### Run the user's Gradle project for true compilation

Rejected because build scripts and plugins are executable code with the user's permissions. The
tool-owned harness is the only supported compilation executor.

### Reuse runtime VNode as conversion IR

Rejected because executable runtime nodes cannot faithfully represent provenance, uncertainty,
resources, styles, event placeholders, unsupported source, and model confidence.

### Accept screenshot similarity as correctness

Rejected because pixels can hide missing text semantics, accessibility, behavior, resources,
unsupported content, or invalid code.

## Verification

The Phase 0 gate is:

```text
./gradlew verifyAiToolingContracts
```

It validates contract schema identities and majors, example envelopes and IR, reserved capability
IDs and Q levels, version-lane immutability, metric definitions, unique case IDs, metric
denominators, canonical capability references, fixture containment, required category coverage, and
positive/negative diagnostic expectations. It is part of `qaQuick`.

Later phases add their implementation gates without weakening this contract. Any accepted change to
the architecture receives a new ADR or an explicitly compatible schema revision; this accepted
decision is not silently rewritten around an implementation shortcut.
