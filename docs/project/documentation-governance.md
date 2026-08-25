---
schema_version: 2
document_id: project.documentation-governance
doc_type: project
owner:
  kind: project
  id: documentation-governance
version_lane: version-agnostic
capability_ids: []
artifact_ids: []
sample_ids: []
workflow: Define the normative content, ownership, version, sample, and review contracts.
validation:
  - ./gradlew verifyDocumentationStructure
lifecycle: Update with every durable documentation-governance change and preserve history in plans or ADRs.
---

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
4. independently accessible capability tutorials and task-oriented guides backed by executable samples;
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

1. **Tutorial:** teaches a beginner to use one capability and reaches a working result. Each page
   is independently accessible; related tutorials are optional links, never required chapters.
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

## Governance V2 structured contract

Governance V2 adds stable identities and machine-readable records. Its rules are normative; one
reviewed baseline may contain existing violations, but new or touched content cannot add or retain
debt in the affected category. Deterministic discovery and the reviewed baseline are now frozen,
so `verifyDocumentationGovernanceV2` blocks unbaselined findings and every baseline count that is
not exact. The task is part of `verifyDocumentationStructure`, and therefore also runs in
`qaQuick` and documentation CI.

An exception record is not a general allowlist. Relative to the verification base, a pull request
may delete a resolved record or lower only its `violation_count` after reducing the same exact
target/category debt. Adding, copying, renaming, retargeting, widening, or re-adding an exception
fails. A reduced count must be recorded in the same change, and a zero count removes the record.

### Capability identity and ownership

Every application-facing DSL, Modifier, component, host, integration, or tooling entry resolves as
`symbol -> capability_id -> artifact/version -> generated Reference -> sample or exception ->`
applicable handwritten owners.

A capability groups only overloads or symbols representing one user decision. Its record contains
kind, responsible owner, one artifact and version state, exact symbols, generated Reference,
sample or exception, and applicable document owners. Moves, deprecations, and deletions update that
identity, impact, migration, and redirects together. Internal, test, Demo-only, generated, and
renderer-only declarations stay outside the application catalog.

### Document metadata and type requirements

Every canonical active handwritten public page declares stable document/type/owner identities, one
version lane, and explicit capability, artifact, and sample sets. Locale mirrors inherit that
record and add only translation metadata. Directory and `doc_type` must agree.

| `doc_type` | Machine-required metadata | Reviewer-owned purpose and evidence |
| --- | --- | --- |
| `tutorial` | capability/sample, released/next lane, result, verification | beginner reaches one working result |
| `guide` | capability, task, success/failure checks | completes one concrete task |
| `architecture` | invariants and implementation/test evidence | current boundaries, ownership, and trade-offs |
| `migration` | source and target states | verifiable transition and semantic risks |
| `reference` | generated marker and capability | source-derived lookup, no handwritten inventory |
| `module` | one artifact, coordinate, minimal compiled sample | artifact installation, compatibility, and constraints |
| `tooling` | supported versions and verification commands | operates development tooling |
| `project` | workflow, validation, lifecycle | governs repository maintenance |
| `plan` | temporary state, ordered work, completion, verification, next action, Changesets | moves durable conclusions before archival |

Cross-purpose material links to its focused owner. A mismatched page moves with locale-aware
redirects; changing `doc_type` alone does not restructure it.

### Version lanes

Every public code-bearing page or sample declares exactly one lane:

1. `released`: immutable registered Maven versions, with no current-source substitution;
2. `next`: locally published current-checkout artifacts plus a visible unreleased warning;
3. `version-agnostic`: no version-sensitive executable API usage.

Tutorials cannot be `version-agnostic`; a lane change is an explicit documentation/release event.

### Sample classes

Every public Kotlin/Java fence has one registered identity and class:

1. `compiled-region`: exact source region plus its lane-specific build target;
2. `generated-signature`: one source symbol and named generator, never hand-edited;
3. `non-executable`: incomplete architecture pseudocode with an adjacent explanation and reason.

`non-executable` is forbidden in Tutorials, installation, and copy-ready examples and cannot hide
stale APIs or missing dependencies. Registration, not syntax highlighting, classifies a fence.

### Public capability impact

Every public/protected add, change, move, deprecation, or deletion records artifact, symbol,
capability, change/breaking state, Q level, contract fields, and KDoc/module/sample/Reference/
Tutorial/Guide/Architecture/Migration/redirect dispositions. A disposition is `updated` with exact
targets or `not-applicable` with a concrete rationale. Moves/deletions require redirects; breaking
changes require migration. Public symbols cannot use free-form `No documentation impact`.

### Exceptions and debt ratchet

An exception has a stable ID, exact file/symbol, category, reason, owner, creation date, removal
condition, count, and optional expiry; wildcards and permanent legacy buckets are forbidden. The
ratchet rejects new/wider/increased/re-added debt and requires touched allowlisted pages to repair
that category. Completion requires an empty baseline and strict local, PR, and deployment gates.

### Contract assets and enforcement boundary

The V2 manifest in `docs/project/contracts/` freezes schemas, fixtures, and record/task/report
locations. Documents use front matter; other records use plural subdirectories. Fixtures fail
closed; findings do not block until Phase 2.

Machines own shape, identity, discovery, uniqueness, source/lane/route consistency, ratchet, and
freshness; reviewers own cohesion, purpose, evidence, exception credibility, and rationale.
Automation never overrides review.

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

[`gradle/viewcompose-documentation-releases.properties`](../../gradle/viewcompose-documentation-releases.properties)
is the append-only registry of released artifact/version/source-revision triples. Current
publishing metadata must resolve to an exact registry entry; a released pair is never rewritten or
removed.

Every catalog row must link an available `docs/modules/<artifact-id>/README.md`; `Planned` is no
longer an accepted state for published artifacts. A new artifact must not receive its first public
release until that manual, its generated API tree, and its strict source-comment gate exist. The
module page owns the following information:

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
- source links that resolve to the release's immutable commit, never an arbitrary `main` revision.

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
adequate KDoc/Javadoc are incomplete even when compilation succeeds. New and changed public API
documentation, compiled samples, and affected module documentation must land in the same pull
request as the implementation; existing debt does not permit new debt.

The normative source-comment language, KDoc/Javadoc structure, declaration templates,
ViewCompose-specific contracts, quality levels, audit commands, and staged enforcement policy are
defined in the
[Source Documentation and API Comment Standard](api-documentation-quality.md).

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

Every tutorial teaches a beginner to use one capability and must be runnable when entered directly.
The page states the expected outcome, tested module versions, and verification action, but never
requires another tutorial to be completed first. Related pages may be suggested after the working
result.

Executable source is the truth for code samples:

- non-trivial samples live in a compiled sample/demo source set and are referenced from docs;
- every tutorial starts with one complete Maven dependency block, including any optional feature
  artifact such as `viewcompose-overlay-material3-android`; a reader must not discover a required dependency
  only after reaching the sample;
- standalone tutorial samples live under `samples/<name>`, resolve ViewCompose through published
  Maven coordinates, use only public APIs, compile from `qaQuick`, and run representative behavior
  checks from `qaFull`;
- prefer one self-contained source file per demonstrated capability. Do not grow one progressive
  sample into a cross-feature application that a reader must understand before using one feature;
- short inline snippets must be covered by a compilation test or copied from a compiled sample;
- snippets must use public APIs and published dependency coordinates;
- output screenshots identify device configuration, theme, font scale, locale, and relevant module
  versions;
- obsolete samples are fixed or removed in the same change that invalidates them.

The tutorial gate verifies exact source regions, complete dependency declarations in both locales,
and the absence of local `project(...)` dependencies in public tutorial sample modules. Do not
maintain large independent code blocks that can silently diverge from the repository.

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

The public URL contract is:

```text
/modules/<artifact-id>
/modules/<artifact-id>/<version>
/api/<artifact-id>/<version>/
/migration/...
/tutorials/...
```

The mutable module-manual route describes the current supported line. Versioned module manuals are
generated from the recorded release revision and remain English canonical snapshots under both
locale route trees. Changing this semantic URL shape requires an ADR and redirect plan.

Every published module records `module.<artifact>.sourceRevision` beside its independent version in
the publication metadata. The value is a full 40-character Git commit SHA whose module source is
byte-for-byte the source used to generate the reference. Release preparation therefore freezes the
module source and manual in one commit, then appends the immutable history entry and changes version
and revision metadata in a second, metadata-only commit. The frozen revision must remain reachable
from Git history. A module version must never be advanced without a matching history entry, or
without advancing the source revision when its source changed.

Selected-module API generation is an iteration aid only. Production deployment must run the
complete-history API verifier and production site build. Together they reconstruct every recorded
version from immutable Git sources, check every API and manual route, validate the mutable `current`
redirect and stable-only `latest` policy, enforce manifest parity, and require immutable API source
links.

## Language policy

English is the canonical public documentation language so API and design contracts have one source
of truth. Simplified Chinese mirrors the same page paths under the `zh-CN` locale namespace. The
default English site is published at `/`; the Chinese site is published at `/zh-CN/`. Do not create
a second independent documentation tree or interleave complete English and Chinese copies within
one hosted page.

Generated KDoc/Javadoc and generated historical module-manual snapshots remain canonical English
reference. Chinese current module manuals, tutorials, and guides explain how to use those APIs but
do not duplicate the complete generated symbol tree or immutable snapshot history.

Every active handwritten page has one language per locale:

- files under `docs/` use English for titles, headings, and narrative prose;
- files under the `zh-CN` Markdown locale use Simplified Chinese for titles, headings, and
  narrative prose;
- code fences, commands, identifiers, URLs, and quoted UI literals preserve their exact source
  language; a foreign-language literal in prose must be formatted as inline code so it is
  distinguishable from misplaced narrative;
- temporary plans, historical archives, generated API pages, and immutable historical
  module-manual snapshots remain canonical English-only evidence.

Mixed-language narrative is a merge-blocking placement error, not translation debt. The
Markdown-aware language verifier ignores code and literal spans but checks every active canonical
page and every locale mirror.

### Canonical-first translation workflow

Every public documentation change follows this order:

1. update and verify the canonical English document;
2. update and review the Chinese mirror in the same pull request;
3. preserve code, identifiers, commands, URLs, and real UI literals exactly;
4. record the reviewed canonical fingerprint only after the Chinese meaning is current;
5. run `./gradlew verifyDocumentationStructure`, which owns the documentation script tests,
   language classifier, and translation freshness verifier, followed by the both-locale site build.

Translation work must not be deferred until English documentation is "finished". Documentation and
modules evolve independently, so translation is a continuous page-level workflow.

### Translation priority

Pages use the following enforcement tiers:

| Tier | Content | Merge requirement |
| --- | --- | --- |
| Required | every active handwritten public page, including architecture, guides, migration, module manuals, project operations, tooling, and tutorials | Chinese mirror must exist, use Chinese narrative, and match the canonical source fingerprint |
| English-only | generated API reference, immutable historical module-manual snapshots, temporary plans, historical archives, and internal evidence not published as user guidance | no Chinese mirror is required and the content must not be presented as reviewed localized prose |

The machine-readable required-page list lives with the website localization tooling. Adding,
moving, or removing a public page must update that list, the Chinese mirror, and verification in
the same pull request. A new page cannot rely on locale fallback as a temporary publishing state.

### Freshness contract

Every Chinese Markdown mirror records, in front matter:

- `translation_source`: canonical path relative to `docs/`;
- `translation_source_hash`: SHA-256 fingerprint of the canonical source reviewed by the
  translator;
- `translation_status`: `current` or `stale`.

A `current` translation must match the current canonical fingerprint. Required pages may never be
`stale`. Updating only the recorded hash without reviewing the translated meaning is a policy
violation.

The verification gate fails for missing or stale required pages, invalid source mappings,
wrong-language titles or narrative, dishonest status, and current translations whose fingerprint
no longer matches. Locale fallback is reserved for deliberately English-only generated or
historical content, not active handwritten public pages.

### Pull request and review rules

Every pull request that changes canonical public documentation must state one of:

- the Chinese mirror was updated and reviewed;
- the page is English-only under this policy;
- no user-visible language content changed.

Correctness and security fixes still update English first inside the change, but the reviewed
Chinese mirror is required before merge for public pages. Translation review checks technical
meaning, links, code samples, terminology, and locale-specific screenshots; it is not only a
fluency review.

Commands, front-matter fields, required-page configuration, and recovery steps are defined in the
[localization workflow](localization.md).

## Change impact matrix

Use this matrix before implementation and again during review:

| Change | Required documentation impact |
| --- | --- |
| New or changed public/protected symbol | Structured capability-impact record; capability owner; KDoc/Javadoc; module page; generated Reference; sample or exact exception; explicit Tutorial/Guide/Architecture/Migration/redirect dispositions |
| New published module | Publishing metadata; module catalog; module `README`; API reference pipeline; dependency guide |
| Published artifact source or release-input change | Immutable per-PR Changeset; owning module/API documentation as applicable; deterministic release-plan validation |
| Dependency or compatibility change | Module page and affected cross-module compatibility matrix |
| Behavior/default/lifecycle change | Owning module page plus relevant guide/tutorial; migration note if users must act |
| Architecture or ownership change | Current architecture page; ADR when the decision meets ADR criteria |
| Compose parity or divergence change | Comparison matrix and migration guidance |
| Tooling or preview change | Tooling page, supported-version statement, and screenshots where useful |
| Breaking change or deprecation | KDoc/Javadoc replacement; migration page; release notes; versioned docs preserved |
| Bug fix that corrects documented behavior | Correct the active page and add regression evidence |
| Test or benchmark changes a durable conclusion | Owning active page with comparison context, interpreted result, limitations, and next action |
| Internal refactor with no contract impact | Explicit `No documentation impact` rationale in the pull request |

`No documentation impact` is a reviewed conclusion, not a default checkbox. A pull request must
state which row applies.

## Test evidence and conclusion closure

Running a test or preserving raw benchmark output is evidence collection, not documentation
closure. When accepted evidence validates, rejects, or materially qualifies a durable claim, the
same change or acceptance step must update the active document that owns that claim. A plan may
retain commands and raw artifacts, but it cannot be completed or archived while the interpreted
result exists only in a pull-request comment, chat, local report, or archive entry.

The durable conclusion records:

1. the scenario or contract under test, workload revision, implementation revision, build mode,
   device or environment, and controls needed to reproduce a valid comparison;
2. absolute control/before and candidate/after results for every decision metric, plus a normalized
   delta when the samples are comparable;
3. exactly one primary classification: `improved`, `regressed`, `mixed`, `no material change`, or
   `inconclusive`;
4. median or steady-state behavior separately from tail behavior and any important absolute budget
   risk, so a favorable average cannot conceal a user-visible regression;
5. stability checks, rejected evidence, limitations, the decision supported by the result, and the
   next action for any remaining gap.

Classification follows the owning specification's thresholds and noise floor. Opposing decision
metrics are `mixed`; unstable, mismatched, underpowered, or otherwise invalid evidence is
`inconclusive` and must not be reported as an improvement or regression. Authors must not select
only favorable metrics or compare incompatible environments. When accepted evidence does not
change any durable claim, the pull request or plan records that rationale explicitly instead of
silently omitting documentation impact.

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

Every active plan also contains exactly one `## Maven release changesets` section. Use one
`- None.` entry until the plan owns a production Changeset; once implementation adds immutable
`release/changes/*.json` files, list each repository-relative path as its own inline-code bullet.
Before a public Maven Central upload, the publishing gate derives the direct and
dependency-propagated artifact scope of those Changesets and rejects any selected artifact still
owned by an active plan. Planning, metadata preparation, and local publication remain available
before this acceptance boundary. A completed plan must move to the archive, update both plan
indexes, and preserve its final evidence before the related Central upload can proceed.

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
9. close accepted test and benchmark evidence into an interpreted conclusion in the owning active
   document rather than leaving only raw output;
10. run the documentation gates before handoff and report any validation that could not run.
11. add one immutable per-PR Changeset for publication-relevant artifact changes and never infer or
    hand-write reverse-dependency release impact outside the release planner.

The root `AGENTS.md` contains the shortest machine-discoverable version of these rules and points
back here. This document remains authoritative.

## Review and automated gates

Every documentation review verifies:

- the page has one primary content purpose and the correct owner;
- framework-level and module-level concerns are separated without duplicated truth;
- version, compatibility, and stability statements match publishing metadata;
- behavior claims match code, tests, or cited measurements;
- accepted test and benchmark evidence has an interpreted conclusion, comparison context,
  limitations, and next action in its owning active document;
- public symbols have sufficient source documentation;
- samples compile and screenshots identify their environment;
- new public modules are present in the artifact catalog;
- all active pages are reachable from `docs/README.md`;
- links are relative and resolve;
- completed plans are archived only after durable conclusions move to active docs;
- localization status is honest;
- canonical and localized titles and narrative match their directory language;
- `./gradlew verifyDocumentationStructure` passes.

The structure gate is included in `qaQuick`. The documentation workflow additionally generates the
complete versioned Dokka and module-manual catalog, type-checks Docusaurus, enforces
production-build link and site-owned-page accessibility checks, and deploys only from `main`.
Generated output and deployment credentials must remain outside the repository.
