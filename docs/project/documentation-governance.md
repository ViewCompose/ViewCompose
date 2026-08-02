# Documentation Governance

## Purpose

This document is the source of truth for ViewCompose documentation. It defines the information
architecture, ownership boundaries, update triggers, versioning model, and quality gates that
apply to maintainers, contributors, and AI agents.

ViewCompose has many independently published modules. Documentation is therefore part of each
module's public contract, not a repository-wide afterthought. A code change is incomplete when it
changes a public contract without updating the corresponding documentation in the same pull
request.

The documentation system must support five durable outcomes:

1. generated KDoc/Javadoc reference for every public artifact and released version;
2. current framework principles, architecture, and design decisions;
3. factual comparisons with Jetpack Compose and practical migration paths;
4. progressive tutorials and task-oriented guides backed by executable samples;
5. module-level documentation that can evolve and be released independently.

It must also remain searchable, linkable, version-aware, accessible, and mechanically verifiable.
The eventual site generator is an implementation detail and must not redefine these contracts.

## Information architecture

The source tree and future public site use the following content boundaries:

| Source location | Public purpose | Lifecycle |
| --- | --- | --- |
| Repository root | Landing pages, community governance, and `AGENTS.md` | Stable and intentionally small |
| `docs/README.md` | Canonical documentation index and root of the documentation link graph | Always current |
| `docs/getting-started/` | Installation, first UI, project setup, and the shortest successful path | Version-aware |
| `docs/tutorials/` | Guided, end-to-end learning paths | Version-aware and sample-backed |
| `docs/architecture/` | Current framework principles, runtime model, and architectural contracts | Updated with implementation |
| `docs/architecture/decisions/` | Accepted or superseded architecture decision records | Append-only decision history |
| `docs/guides/` | Goal-oriented how-to documentation spanning one or more modules | Updated with public behavior |
| `docs/migration/` | Compose comparison, Compose migration, and ViewCompose version migrations | Source/target versions explicit |
| `docs/modules/` | One curated entry point for every published artifact | Evolves with that artifact |
| `docs/tooling/` | Preview, diagnostics, benchmarks, IDE integration, and developer tools | Updated with tooling behavior |
| `docs/project/` | Contributor workflow, release, verification, roadmap, and governance | Updated with project process |
| `docs/project/plans/` | Active multi-step plans that must survive across sessions | Archived when complete |
| `docs/archive/` | Completed plans, audits, snapshots, and superseded documents | Historical; never current truth |
| `website/` | Docusaurus presentation, generated-data adapters, and local site tooling | Evolves with the hosted site |
| Generated API output | Per-artifact, per-version KDoc/Javadoc HTML | Generated during release; never edited or committed |

The currently reserved top-level categories may be introduced incrementally. New categories require
an information-architecture change here and an update to the structure verifier in the same pull
request.

The root Markdown allowlist is deliberately limited to:

- `README.md`
- `README.zh-CN.md`
- `CONTRIBUTING.md`
- `CODE_OF_CONDUCT.md`
- `THIRD_PARTY_NOTICES.md`
- `AGENTS.md`

Convenience is not sufficient justification for another root document.

## Content model

Every public page must have one primary purpose. Use this decision order:

1. **Tutorial:** teaches through a complete, ordered learning experience. The reader follows the
   author-provided path and reaches a working result.
2. **Guide:** helps an informed reader accomplish a concrete task. It may link to concepts and API
   reference instead of reteaching them.
3. **Architecture:** explains why the framework works as it does, its invariants, boundaries, and
   trade-offs. It describes the current design rather than a temporary implementation plan.
4. **Migration/comparison:** maps concepts or versions, calls out semantic differences, and gives a
   verifiable transition path.
5. **Module reference:** explains one artifact's role, dependency shape, supported environments,
   entry points, compatibility, and operational constraints.
6. **API reference:** generated from source KDoc/Javadoc and signatures. Handwritten pages link to
   it instead of duplicating symbol inventories.
7. **Project documentation:** governs maintenance of the repository rather than use of the
   framework.

Do not combine tutorial, design rationale, exhaustive API listing, and release notes in one page.
Link between focused pages instead.

## Framework and module boundaries

ViewCompose documentation has two complementary layers:

- **Framework-level documentation** explains cross-module concepts and end-to-end workflows:
  rendering, state, lifecycle, themes, navigation, preview, performance, migration, and tutorials.
- **Module-level documentation** describes the contract owned by one Maven artifact. It must remain
  usable when that artifact advances independently of the rest of the repository.

A concept does not become module documentation merely because its implementation lives in one
module. Conversely, module-specific setup, dependency constraints, public entry points, and
compatibility must not be hidden inside a broad framework guide.

Cross-module pages must name the modules they rely on and state the tested compatibility set when
the modules do not share one version. Module pages link back to shared concepts instead of copying
them.

## Published module documentation contract

[`docs/modules/README.md`](../modules/README.md) is the canonical artifact catalog. Every
`module.<artifact>.version` entry in
[`gradle/viewcompose-publishing.properties`](../../gradle/viewcompose-publishing.properties) must
have exactly one catalog row. Adding, renaming, publishing, or retiring an artifact requires the
catalog, publishing metadata, dependency documentation, and structure verification to change
together.

During the initial site rollout, existing artifacts may remain marked `Planned` in the catalog. A
new artifact must not receive its first public release, and an existing artifact must not be
documented as stable, until it has `docs/modules/<artifact-id>/README.md`. That page owns the
following information:

1. purpose, audience, and non-goals;
2. Maven coordinate and stability level;
3. whether it is platform-neutral, Android-specific, tooling-only, or an integration;
4. direct and transitively supplied ViewCompose modules;
5. supported Android/JDK/Kotlin/Gradle environment where relevant;
6. installation and a minimal working usage example;
7. principal public entry points, linked to generated API reference;
8. lifecycle, threading, state ownership, performance, or resource constraints;
9. related guides, samples, and modules;
10. compatibility and migration notes for the current line.

Optional deeper pages live in the same directory and are linked from its `README.md`. Do not create
a page per class; class-level detail belongs in KDoc/Javadoc.

Internal or demo-only modules may be described in architecture or tooling documentation, but they
must not masquerade as publicly consumable artifacts. If an artifact stops publishing, preserve its
last versioned site pages and mark the live module page as retired with a replacement path.

## KDoc and Javadoc contract

Source comments are the canonical API reference. The hosted site must provide:

- an API landing page listing every published artifact;
- one generated API tree per artifact and released version;
- stable aliases for each artifact's latest stable API;
- links from every module page to the matching generated API tree;
- source links that resolve to the released tag, not an arbitrary `main` revision.

Generated HTML is build output. Never hand-edit it or commit it under `docs/`. Sources JARs and
Javadoc JARs published to Maven Central remain release artifacts, while the hosted API tree is the
browsable presentation of the same versioned source contract.

Every public or protected API must document information the signature cannot express, as
applicable:

- purpose and observable behavior;
- parameter units, coordinate spaces, defaults, and valid ranges;
- return-value ownership and nullability semantics;
- state ownership, lifecycle, disposal, and thread confinement;
- ordering, cancellation, error, and failure behavior;
- Android API-level or platform caveats;
- performance characteristics that affect correct use;
- `@sample` links for non-trivial usage;
- `@throws`, replacement, and deprecation guidance where relevant.

Documentation must not promise behavior that tests do not protect. Public API additions without
adequate KDoc/Javadoc are incomplete even when compilation succeeds.

## Architecture and design decisions

Current-state architecture pages describe how the system works now. Update them in the same pull
request as an invariant, dependency direction, ownership boundary, or execution model change.

Use an architecture decision record (ADR) when a decision is costly to reverse, affects multiple
modules, establishes a public contract, or deliberately rejects a plausible alternative. ADR files
use `docs/architecture/decisions/NNNN-short-title.md` and contain:

1. status and decision date;
2. context and forces;
3. decision;
4. alternatives considered;
5. consequences and trade-offs;
6. affected modules and public contracts;
7. validation and rollout requirements;
8. links to superseded or superseding decisions.

Accepted ADRs are not rewritten to make history look current. A new decision supersedes the old
record, while current architecture pages are updated to reflect the new truth. Temporary execution
steps belong in `docs/project/plans/`, not in an ADR.

## Compose comparison and migration

Compose comparison pages are engineering references, not marketing scorecards. Every comparison
must:

1. name the ViewCompose module versions and Compose/AndroidX baseline used;
2. compare semantics, lifecycle, state ownership, rendering, tooling, performance, and platform
   integration where relevant;
3. distinguish supported, partially supported, intentionally different, and unsupported behavior;
4. link claims to public contracts, tests, benchmarks, or upstream Android documentation;
5. state measurement conditions for performance claims;
6. provide replacement patterns and explicit migration risks;
7. include a last-verified baseline and an owner for re-verification.

Migration pages must define source and target states. Breaking ViewCompose changes require a
module-local migration page or a cross-module migration guide before release. Avoid copying Compose
documentation or presenting API name similarity as semantic equivalence.

## Tutorial and sample quality

Tutorials progress from the shortest successful setup to realistic application structure. Each
tutorial must state prerequisites, expected outcome, module versions, and the commands or actions
used to verify completion.

Executable source is the truth for code samples:

- non-trivial samples live in a compiled sample/demo source set and are referenced from docs;
- short inline snippets must be covered by a compilation test or copied from a compiled sample;
- snippets must use public APIs and published dependency coordinates;
- output screenshots identify device configuration, theme, font scale, locale, and relevant module
  versions;
- obsolete samples are fixed or removed in the same change that invalidates them.

Do not maintain large independent code blocks that can silently diverge from the repository.

## Versioning and URL stability

Independent module versions rule out a single global documentation version. The hosted system must
use this model:

- framework concepts, tutorials, and guides describe the current supported release set and display
  a module compatibility matrix where needed;
- module manuals are published under a stable artifact path and snapshot at each artifact release;
- API reference is published per artifact and version;
- `latest` aliases resolve only to the latest stable release, never an alpha or snapshot by
  accident;
- immutable versioned pages remain available after a later release;
- renamed or moved public pages receive redirects; public URLs are not silently reused for a
  different subject.

The planned URL contract is generator-neutral:

```text
/modules/<artifact-id>/
/modules/<artifact-id>/<version>/
/api/<artifact-id>/<version>/
/migration/...
/tutorials/...
```

Exact deployment mechanics will be decided with the hosting system, but changing this semantic URL
shape requires an ADR and redirect plan.

## Language policy

English is the canonical public documentation language so API and design contracts have one source
of truth. Chinese documentation may mirror the same page paths under a locale namespace. A
translation must link to its canonical page and record the canonical revision or release it
matches.

Correctness fixes update English first and should update translations in the same pull request when
practical. A lagging translation must display that status; it must never silently override newer
canonical behavior. Do not interleave full English and Chinese copies within one future hosted page.
Existing mixed-language pages may migrate incrementally when their content is next materially
updated.

## Change impact matrix

Use this matrix before implementation and again during review:

| Change | Required documentation impact |
| --- | --- |
| New or changed public symbol | KDoc/Javadoc; module page; sample for non-trivial use |
| New published module | Publishing metadata; module catalog; module `README`; API reference pipeline; dependency guide |
| Dependency or compatibility change | Module page and affected cross-module compatibility matrix |
| Behavior/default/lifecycle change | Owning module page plus relevant guide/tutorial; migration note if users must act |
| Architecture or ownership change | Current architecture page; ADR when the decision meets ADR criteria |
| Compose parity or divergence change | Comparison matrix and migration guidance |
| Tooling or preview change | Tooling page, supported-version statement, and screenshots where useful |
| Breaking change or deprecation | KDoc/Javadoc replacement; migration page; release notes; versioned docs preserved |
| Bug fix that corrects documented behavior | Correct the active page and add regression evidence |
| Internal refactor with no contract impact | Explicit `No documentation impact` rationale in the pull request |

`No documentation impact` is a reviewed conclusion, not a default checkbox. A pull request must
state which row applies.

## Naming, links, and assets

Active documents use lowercase kebab-case names, except directory indexes named `README.md` and ADR
numeric prefixes. Use descriptive stable names instead of dates unless the date is part of a
versioned record.

Links between active repository documents are relative. Historical evidence under `docs/archive/`
is excluded from the hosted site and may use its canonical GitHub URL when linked from a public
page. Never commit `file://` links, local absolute paths, or links to uncommitted generated output.
When a document moves, update incoming links and add a hosted redirect in the same release.

Images and diagrams must have meaningful alternative text. Prefer text, tables, and Mermaid for
maintainable technical diagrams. Store indispensable binary assets near the owning documentation
area with stable lowercase names and document how screenshots can be reproduced. Do not use an
image as the sole description of an API or workflow.

Every active document must be reachable from `docs/README.md` through section indexes. It does not
need to be linked directly from the root index. Archive files are represented by the archive index
and are excluded from active coverage.

## Document lifecycle

Before creating a page, search for an existing source of truth. Update it instead of creating a
second roadmap, architecture overview, module manual, or current-status page.

An active plan is appropriate only when work crosses multiple meaningful steps or sessions. Plans
under `docs/project/plans/` record status, scope, non-goals, baseline, completion criteria, ordered
steps, validation, last verified date, and next action. When complete, durable conclusions move to
active documentation and the plan moves to `docs/archive/`.

Deprecated public documentation remains available until its supported release line reaches end of
life. Mark it as deprecated, link its replacement, and preserve versioned URLs. Repository-only
documents that no longer represent a supported contract move to the archive.

## AI-assisted maintenance

AI agents must:

1. start at `docs/README.md`, then read the owning module page and relevant active documents;
2. inspect code and tests as evidence before repeating a behavioral claim;
3. apply the change impact matrix before editing code and before handoff;
4. update stale active documentation rather than treating it as infallible or creating a parallel
   explanation;
5. ignore `docs/archive/` during normal context recovery unless historical reasoning is needed;
6. keep temporary notes and generated API HTML out of the repository;
7. preserve module boundaries, version baselines, link reachability, and the canonical-language
   rule;
8. update indexes and incoming links when adding, moving, or deleting content;
9. run the documentation gates before handoff and report any validation that could not run.

The root `AGENTS.md` contains the shortest machine-discoverable version of these rules and points
back here. This document remains authoritative.

## Review and automated gates

Every documentation review verifies:

- the page has one primary content purpose and the correct owner;
- framework-level and module-level concerns are separated without duplicated truth;
- version, compatibility, and stability statements match publishing metadata;
- behavior claims match code, tests, or cited measurements;
- public symbols have sufficient source documentation;
- samples compile and screenshots identify their environment;
- new public modules are present in the artifact catalog;
- all active pages are reachable from `docs/README.md`;
- links are relative and resolve;
- completed plans are archived only after durable conclusions move to active docs;
- localization status is honest;
- `./gradlew verifyDocumentationStructure` passes.

The structure gate is included in `qaQuick`. The documentation workflow additionally generates the
complete versioned Dokka catalog, type-checks Docusaurus, enforces production-build link checking,
and deploys only from `main`. Sample compilation and accessibility automation remain production
hardening work. Generated output and deployment credentials must remain outside the repository.
