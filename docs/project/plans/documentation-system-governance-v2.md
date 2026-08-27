---
draft: true
schema_version: 2
document_id: plan.documentation-governance-v2
doc_type: plan
owner:
  kind: project
  id: documentation-governance
version_lane: version-agnostic
capability_ids: []
artifact_ids: []
sample_ids: []
status: active
scope: Establish structured documentation ownership, discovery, debt ratchets, and generated Reference.
non_goals:
  - Weaken existing documentation, source-comment, release, or site gates.
baseline: Phase 1 freezes 733 reviewed findings in 311 exact debt records with no unbaselined issue.
ordered_work:
  - Add compiled report-only discovery and then activate no-new-debt gates.
  - Generate Reference ownership before broad content and sample migration.
completion:
  - Remove the debt baseline, enable strict gates, move durable conclusions, and archive the plan.
last_verified: 2026-08-27
next_action: Merge the Navigation ownership slice, then audit UI Foundation, Material 3, and One UI 7 ownership as the next Phase 3 boundary.
maven_release_changesets:
  - release/changes/20260826-governance-shadows-phase3.json
  - release/changes/20260826-governance-preview-tooling-phase3.json
  - release/changes/20260826-governance-third-party-integrations-phase3.json
  - release/changes/20260827-governance-animation-phase3.json
  - release/changes/20260827-governance-host-android-phase3.json
  - release/changes/20260827-governance-lifecycle-phase3.json
  - release/changes/20260827-governance-viewmodel-phase3.json
  - release/changes/20260827-governance-renderer-phase3.json
  - release/changes/20260827-governance-diagnostics-phase3.json
  - release/changes/20260827-governance-graphics-phase3.json
  - release/changes/20260827-governance-gesture-phase3.json
  - release/changes/20260827-governance-navigation-modules-phase3.json
---

# Documentation System Governance V2 and Capability Restructure Plan

## Status

Active. The assessment baseline was frozen on `main` at `4fd14c4a` on 2026-08-25. Phases 0A, 0B,
and 1 are complete. The first Phase 2 slice enforces the frozen baseline as a blocking no-new-debt
ratchet; the second automatically discovers Tutorial pages and validates type-aware sample markers
plus exact source-region identity in both languages; the third requires structural public
capability changes to own exact, immutable capability-impact records; and the fourth generates the
committed capability Reference and rejects stale output. The normative contract, compiled scanner,
precise production/public inventory, generated Reference, and exact reviewed debt baseline all run
from the quality included build. The first Phase 3 slice has also hard-cut Navigation delivery
history out of the active Guide, established focused Guide and Architecture owners, registered the
compiled Tutorial and Android host capability, and reduced the live report from 733 to 701 issues.
The second Phase 3 slice has now moved Theming ownership into one Architecture page and three
focused Guides, registered the Foundation and Material theme capabilities plus compiled task
samples, repaired the Navigation and Theming dependency-block verification path, and reduced the
report again to 693 issues.
The third slice has moved Text Input runtime ownership into Architecture, retained editing, IME,
rich-document, and Receive Content tasks in two coherent Guides, registered the three input DSL
symbols and six compiled regions, and reduced the report to 683 issues without changing framework
behavior.
The fourth slice has moved Lazy Collection Session, reuse, and renderer ownership into Architecture,
retained list, state-control, grid, pager, and performance tasks in one Guide plus two Tutorials,
registered six collection DSL entries and six compiled regions, and reduced the report to 668 issues
without changing framework behavior.
The next two slices separated Focus/Nested Scroll and Shadows application tasks from their runtime
contracts, reducing the report to 625 issues. The accepted Overlays hard cut now assigns 21 public
entries across six capability owners, registers eight compiled regions, and reduces the report to
590 issues without changing runtime behavior or public routes.
The accepted Image Loading hard cut now assigns the Foundation, Coil, and Glide entries to three
capabilities, registers nine sample decisions across the Guide, Migration, and module manuals, and
reduces the report to 571 issues without changing runtime behavior or public routes.
The accepted Modifier Architecture hard cut assigns 51 Modifier and Gesture entries to seven
capability owners, registers nine compiled sample decisions, removes the remaining handwritten API
inventory, and reduces the report to 516 issues. The accepted ConstraintLayout module hard cut now
assigns all 43 public DSL and helper entries to two capability owners, registers five compiled
module sample decisions, removes three resolved exceptions, and reduces the report to 468 issues.
The accepted Preview tooling hard cut now assigns all 62 public annotations, protocol, Gradle,
runner, worker, and integration entries to six capability owners, registers nine compiled sample
decisions, removes 27 resolved exceptions, and reduces the report to 390 issues.
The accepted third-party integration hard cut now assigns all 32 CameraX, Google Maps, Media3, and
legacy ExoPlayer entries to five capability owners, registers nine compiled sample decisions,
removes 14 resolved exceptions, and reduces the report to 345 issues.
The accepted Animation hard cut now assigns the seven previously orphaned Animation composition,
content, visibility, and layout-motion entries to one capability owner, registers 19 compiled or
explicitly non-executable sample decisions across two module manuals, one Tutorial, one Migration,
and two ADRs, removes 17 resolved exceptions, and reduces the report to 313 issues. Android native
animation interop remains intentionally outside this owner and will be handled with Host Android.
The accepted Host Android hard cut now assigns all 34 low-level container, native View, resource,
native animation, and native graphics entries to five capability owners, registers 12 compiled
sample decisions, removes 19 resolved exceptions, and reduces the report to 267 issues.
The accepted Lifecycle hard cut now assigns all 18 owner-boundary, Flow collection, lifecycle
effect, native View lifecycle, and saved-state entries to four capability owners, registers seven
compiled sample decisions, removes 10 resolved exceptions, and reduces the report to 243 issues.
The accepted ViewModel hard cut now assigns all five owner-boundary, store-resolution, and
saved-state entries to three capability owners, registers four compiled sample decisions, removes
six resolved exceptions, and reduces the report to 231 issues.
The accepted Renderer hard cut now assigns all 41 public transaction, reconciliation, diagnostics,
and decoration-backend entries to four capability owners, registers six compiled sample decisions,
repairs the scanner's modifier-form and Renderer-package coverage, removes two resolved exceptions,
and reduces the report to 228 issues.
The accepted Diagnostics hard cut now assigns 52 previously undiscovered Foundation diagnostic
contracts and five Diagnostics integration entries to four capability owners, registers 11 exact
sample decisions, normalizes shared generated-Reference ownership metadata, removes 12 resolved
exceptions, and reduces the report to 206 issues.
The accepted Graphics hard cut now assigns the six Canvas and drawing-Modifier entries to one
custom-drawing capability owner, registers six exact module sample decisions, removes five
resolved exceptions, and reduces the report to 192 issues.
The accepted Gesture hard cut now assigns the remaining controlled-toggle Modifier entry to the
existing gesture capability, registers seven exact module sample decisions, removes five resolved
exceptions, repairs one previously non-compiling RTL dp expression, and reduces the report to 182
issues.
The accepted Navigation module hard cut now registers seven exact Core and Android module sample
decisions, compiles the previously unregistered Core sample source set, removes five resolved
exceptions including the final taxonomy mismatch, and reduces the report to 172 issues.

This plan is process-first. Governance V2, machine-readable capability ownership, and a
no-new-debt gate must land before broad document movement or tutorial expansion. Existing debt may
enter one explicit ratchet baseline, but the baseline cannot grow, and touching an allowlisted page
must remove that page's repaired violations instead of preserving them.

Last verified: 2026-08-27.

Next action: merge the Navigation ownership slice, then audit UI Foundation, Material 3, and One UI
7 ownership as the next Phase 3 boundary. Pull-request gate scaling
Phases 3 through 5 are complete, so these scoped content
changes also supply the gate plan's Phase 6 shadow-observation corpus. Final Reference link closure
remains owned by this plan after the Phase 3 content owners move.

## Maven release changesets

- `release/changes/20260826-governance-shadows-phase3.json` records the already accepted
  documentation-region-only Shadow sample classification. Image Loading changes only non-published
  Tutorial sources, handwritten documentation, and governance records, so it adds no Changeset.
  The Modifier and ConstraintLayout slices have the same publication-neutral classification.
- `release/changes/20260826-governance-preview-tooling-phase3.json` explicitly ignores the five
  Preview artifacts detected because existing compiled sample sources gained documentation-region
  comments only; no production source, signature, bytecode behavior, publication metadata, or
  generated artifact content changed.
- `release/changes/20260826-governance-third-party-integrations-phase3.json` explicitly ignores the
  four integration artifacts detected because test-only compiled documentation samples gained
  exact regions or were reorganized for separate host/content examples. Test sources are excluded
  from published artifacts; production source, API, runtime behavior, publication metadata, and
  generated artifact content remain unchanged.
- `release/changes/20260827-governance-animation-phase3.json` explicitly ignores Animation Core and
  Animation because only test-only compiled documentation fixtures were added. Test sources are
  excluded from published artifacts; production source, API, runtime behavior, publication
  metadata, and generated artifact content remain unchanged.
- `release/changes/20260827-governance-host-android-phase3.json` explicitly ignores Host Android and
  the neutral Android aggregate because only test-only compiled documentation fixtures gained
  regions. Test sources are excluded from published artifacts; production source, API, runtime
  behavior, publication metadata, and generated artifact content remain unchanged.
- `release/changes/20260827-governance-lifecycle-phase3.json` explicitly ignores Lifecycle AndroidX
  because only test-only compiled documentation fixtures gained exact source regions. Test sources
  are excluded from published artifacts; production source, API, runtime behavior, publication
  metadata, and generated artifact content remain unchanged.
- `release/changes/20260827-governance-viewmodel-phase3.json` explicitly ignores ViewModel AndroidX
  because only test-only compiled documentation fixtures gained exact source regions and one test
  declaration was reordered. Test sources are excluded from published artifacts; production
  source, API, runtime behavior, publication metadata, and generated artifact content remain
  unchanged.
- `release/changes/20260827-governance-renderer-phase3.json` explicitly ignores Renderer Android
  because only test-only compiled documentation fixtures gained exact source regions and complete
  transaction execution. Test sources are excluded from published artifacts; production source,
  API, runtime behavior, publication metadata, and generated artifact content remain unchanged.
- `release/changes/20260827-governance-diagnostics-phase3.json` explicitly ignores UI Foundation
  and Diagnostics because only test-only compiled documentation fixtures gained exact regions or
  were separated into focused examples. Test sources are excluded from published artifacts;
  production source, API, runtime behavior, publication metadata, and generated artifact content
  remain unchanged.
- `release/changes/20260827-governance-graphics-phase3.json` explicitly ignores Graphics Core and
  Graphics because only test-only compiled documentation fixtures gained exact regions and
  equivalent sample bindings. Test sources are excluded from published artifacts; production
  source, API, runtime behavior, publication metadata, and generated artifact content remain
  unchanged.
- `release/changes/20260827-governance-gesture-phase3.json` explicitly ignores Gesture Core and
  Gesture because only test-only compiled documentation fixtures gained exact regions and sample
  bindings. Test sources are excluded from published artifacts; production source, API, runtime
  behavior, publication metadata, and generated artifact content remain unchanged.
- `release/changes/20260827-governance-navigation-modules-phase3.json` explicitly ignores
  Navigation Core and Navigation Android because the existing Core sample directory is registered
  only with the test source set and both modules gain exact test-only documentation regions. Test
  sources remain excluded from published artifacts; production source, API, runtime behavior,
  publication metadata, and generated artifact content remain unchanged.

## Release intent rationale

The completed Phase 2 governance-tooling slices change governance records, documentation markers,
workflows, and compiled repository-quality tooling only; they do not change a published artifact's
production source, publication inputs, or compiled API sample bodies.
`verifyViewComposeReleaseIntent` most recently confirmed zero release artifacts, two explicitly
ignored test-sample-only artifacts, and zero shared-path classifications against
`290b1fc07579eb1a2e168ca7c6e71f3836f5eee1`.
Governance, website tooling, and repository verification work can remain publication-neutral.
Any later phase that changes a published artifact's production source, publication inputs, or
compiled API samples must add its immutable Changeset in the same pull request and replace this
section's `- None.` entry with every Changeset owned by the active plan.

## Objective

Rebuild the documentation system so a new or changed public capability cannot merge without an
explicit user-facing owner, correct document type, unambiguous released-versus-next version state,
and verified sample evidence.

The completed system must:

1. distinguish user capabilities, public symbols, Modifiers, modules, documents, and samples;
2. make every public DSL and Modifier symbol traceable to one generated reference owner and one
   published module/version state;
3. require independently runnable tutorials only at the capability-family level rather than
   creating one tutorial per symbol;
4. keep task instructions in Guides, long-lived invariants in Architecture, temporary execution
   state in Plans, and exhaustive signature detail in generated Reference/API output;
5. discover all public pages and executable snippets automatically instead of protecting a fixed
   handwritten list;
6. compile every public executable example from one owned source region and reject silent Markdown
   drift;
7. prevent new documentation debt immediately while existing debt is repaired in bounded slices;
8. preserve current URLs through explicit redirects while content is split or moved; and
9. finish with no ratchet allowlist and all strict documentation gates enabled in `qaQuick` and
   pull-request CI.

## Baseline and evidence

### Existing strengths to preserve

1. [`documentation-governance.md`](../documentation-governance.md) already defines the intended
   high-level difference between Tutorial, Guide, Architecture, Migration, Module, API, and Project
   documentation.
2. Canonical English and required Simplified Chinese public pages are discovered and checked for
   language placement, parity, freshness, and reviewed fingerprints.
3. All registered published artifacts have strict KDoc/Javadoc generation, immutable source links,
   versioned API trees, and module manuals.
4. `verifyDslApiContracts` requires adjacent KDoc and compiled `@sample` references for the core
   `UiTreeBuilder` DSL surface.
5. The current capability tutorials use one focused Activity source per topic, declare Maven
   dependencies first, and are checked by `verifyTutorialSamples` against exact source regions in
   both locales.
6. Documentation structure, relative links, translation state, site scripts, route generation,
   search, accessibility for site-owned pages, and production budgets already have automated
   coverage.

These contracts remain authoritative during the restructure. A new gate supplements them; it does
not weaken or replace them.

### Confirmed failure classes

The 2026-08-25 read-only audit found four independent problems.

1. **Information architecture drift**
   - Nine current Guide pages do not consistently satisfy the task-oriented Guide contract.
   - `guides/navigation.md` contains feature-branch status, P0/P1 delivery plans, and remaining
     implementation scope.
   - `guides/theming.md`, `guides/text-input.md`, `guides/lazy-collections.md`, and
     `guides/shadows.md` are predominantly architecture specifications, API inventories, or
     maintainer contracts.
   - Fully autogenerated sidebars expose source-directory misclassification directly to readers.
2. **Capability discoverability and coverage gaps**
   - The core UI Foundation DSL contains 55 unique capitalized `UiTreeBuilder`/scope component
     names; current tutorials name only 11.
   - The non-app public production surface contains 74 unique `Modifier.*` names; tutorials name
     10 and Guides name 32. A name occurrence is only a lower-bound discovery signal and does not
     prove usable instruction.
   - Important unserved families include layout primitives, eager scrolling and flow layout,
     grids/pagers/tabs, content and image components, action/input controls, feedback components,
     and advanced Modifier groups.
3. **Hand-maintained reference drift**
   - `architecture/modifier.md` records 91 declarations and 77 unique names.
   - Re-running its own repository scan on the frozen baseline returns 95 declarations and 81
     unique names.
   - The page does not name current public `animateBounds`, `toggleDraggable`, `sharedElement`, or
     `sharedBounds` capabilities.
4. **Executable sample gaps**
   - The 12 registered capability-tutorial source regions pass their exact-source and Maven
     dependency gate.
   - Guides contain 27 Kotlin/Java fences, module manuals contain 98, and Architecture contains 8;
     none of those fences participates in the tutorial exact-source contract.
   - `guides/theming.md` contains a stale `setMaterial3UiContent(themeRefreshController = ...)`
     call even though the current host accepts `resourceRefreshController`, and the same page still
     describes a removed `UiColors.ripple` entry.

All current documentation, language, translation, tutorial-sample, and DSL-contract gates pass
despite these defects. Therefore green status currently proves repository consistency only for the
surfaces each task explicitly knows; it does not prove correct classification, complete capability
ownership, or freshness of every public code example.

## Scope

### In scope

1. normative Governance V2 contracts and contributor workflow;
2. a machine-readable capability and documentation-ownership manifest derived from public source
   declarations and publishing metadata;
3. document-type and version-lane front matter for active public pages;
4. automatic discovery of public pages, public DSL/Modifier symbols, and executable Markdown
   snippets;
5. report-only audit, bounded debt baseline, no-new-debt ratchet, and final strict gates;
6. generated DSL and Modifier reference navigation sourced from signatures, KDoc, samples, and
   publishing versions;
7. reclassification and focused splitting of Tutorials, Guides, Architecture, Module, Tooling,
   Migration, Project, and Plan content where the primary purpose is wrong;
8. compiled source ownership for public Guide, Module, Migration, Tooling, and executable
   Architecture snippets;
9. standalone capability tutorials and task guides prioritized from coverage gaps;
10. canonical English, reviewed Simplified Chinese mirrors, redirects, search, and public-site
    navigation for every changed page; and
11. documentation-specific tests, `qaQuick`, site verification, and release-impact review.

## Non-goals

1. changing framework behavior merely to make an existing example compile;
2. adding one Tutorial page for every public symbol;
3. duplicating generated API signatures in handwritten Architecture or Guide pages;
4. restoring the retired progressive multi-feature tutorial application or creating another large
   cross-feature sample that readers must understand as a prerequisite;
5. expanding the lower-priority second Compose-migration batch before the core Tutorial/Guide and
   DSL/Modifier coverage gaps close;
6. translating generated Dokka symbol trees;
7. redesigning framework modules, public APIs, or runtime architecture unless a separately approved
   implementation plan owns that work; and
8. weakening language, translation, link, accessibility, site-budget, API-documentation, or Maven
   release gates to make the restructure pass.

## Normative model to establish

### Capability identity and traceability

Every application-facing public DSL, Modifier, component, host, integration, or tooling entry must
resolve through one stable `capability_id`:

```text
public symbol
  -> capability_id
  -> owning Maven artifact
  -> released or next version state
  -> generated Reference/API owner
  -> compiled sample or documented low-risk exception
  -> Tutorial/Guide owner when users need a learning or task path
  -> Architecture owner when the capability has durable invariants
```

A capability may own several overloads or related symbols. A symbol may not silently belong to two
unrelated capability owners. Deleting, renaming, moving, or changing a capability must update the
same traceability record and any migration or redirect contract in one change.

### Document-type contract

Every active handwritten public page must declare a `doc_type` and the capability/module/version
metadata applicable to that type.

| Type | Required purpose | Required evidence | Forbidden primary content |
| --- | --- | --- | --- |
| `tutorial` | teach a beginner one capability to a working result | complete Maven dependencies, expected result, one focused compiled source, verification action | delivery plans, exhaustive API inventories, architecture history |
| `guide` | help an informed reader complete one concrete task | prerequisites, tested versions, compiled task example where applicable, success/failure checks | P0/P1 stages, feature-branch status, broad system specification |
| `architecture` | explain current invariants, boundaries, ownership, and trade-offs | implementation/test evidence and related ADRs | installation walkthroughs, temporary priorities, manually exhaustive symbol lists |
| `migration` | map an explicit source state to a target state | source/target versions and paired verified examples where applicable | generic API marketing or unsupported parity claims |
| `reference` | provide generated capability/signature lookup | source declaration, KDoc, module, version, and sample links | hand-maintained declaration counts or design history |
| `module` | describe one artifact's dependency, compatibility, entry points, and operational constraints | coordinate, version, dependency shape, minimal compiled use, generated API links | duplicated cross-module concept specifications |
| `tooling` | operate Preview, diagnostics, performance, IDE, or build tooling | supported versions, commands, artifacts, and interpreted evidence | framework runtime architecture without a tooling decision |
| `project` | govern repository maintenance and release work | owner, workflow, validation, and lifecycle | application usage instruction presented as current user guidance |
| `plan` | retain temporary multi-step execution state | scope, non-goals, baseline, ordered work, validation, completion, last verified, next action, Changesets | permanent user contract or unarchived completed conclusions |

Directory and `doc_type` must agree. Cross-purpose material links to a focused owner instead of
remaining in one combined page.

### Version lanes

Every public code-bearing page must declare exactly one lane:

1. `released`: examples resolve only immutable versions registered as released and must compile
   without relying on unreleased source;
2. `next`: examples compile against the current checkout's locally published artifacts and display
   a visible unreleased warning; or
3. `version-agnostic`: permitted only for prose with no version-sensitive executable API usage.

The same example cannot claim both released and next semantics. Moving an API between lanes is an
explicit documentation and release event, not an incidental coordinate edit.

### Sample classes

Every Kotlin or Java fence in active public Markdown must be one of:

1. `compiled-region`: copied exactly from one registered source region and compiled in the page's
   declared version lane;
2. `generated-signature`: emitted from source/API metadata and never hand-edited; or
3. `non-executable`: architecture pseudocode or intentionally incomplete fragments with a visible
   explanation and no copy-ready claim.

Unclassified fences fail. `non-executable` cannot be used in Tutorials, for installation examples,
or to bypass a missing dependency or stale API.

### Exception and debt policy

Every temporary exception requires a stable ID, exact file/symbol target, category, reason, owner,
creation date, removal condition, and optional expiry. Wildcards, directory-wide exceptions,
anonymous counts, and permanent “legacy” reasons are forbidden.

The initial report may generate one reviewed baseline. CI enforces:

1. no new exception target;
2. no increased violation count or widened scope;
3. no re-adding a removed exception;
4. no modification of an allowlisted page without repairing the touched category; and
5. complete deletion of the baseline before plan completion.

## Coordinated execution with pull-request gate scaling

This plan depends on
[`pull-request-gate-scaling-and-build-logic-modularization.md`](pull-request-gate-scaling-and-build-logic-modularization.md)
for compiled gate ownership, required-check orchestration, impact classification, and immutable
documentation caching. The cross-plan order is normative:

1. the pull-request gate plan freezes the accepted task graph and failure baseline;
2. this plan completes Phase 0A contract and schema work without adding a root-build scanner;
3. the pull-request gate plan creates the quality included build and extracts existing gates;
4. this plan completes Phase 0B, Phase 1, and Phase 2 directly in that included build;
5. the generated-reference engine from Phase 4 lands before the broad Phase 3 content moves;
6. the pull-request gate plan completes classification, API caching, and affected verification
   (completed on 2026-08-26);
7. this plan performs Phase 3 content reclassification, then Phases 5 and 6; and
8. those content pull requests supply the gate plan's observation corpus before both plans close.

This ordering advances the Phase 4 engine, model, and destination routes, not all final reference
content, ahead of Phase 3. Phase 3 may then replace handwritten inventories in one hard cut without
an interim missing owner. Phase 4 remains incomplete until the restructured pages and final links
are verified.

## Ordered work

### Phase 0A: Governance V2 contract and schema freeze

1. Update [`documentation-governance.md`](../documentation-governance.md) with the normative model
   above, including type-specific required fields, version lanes, sample classes, capability
   ownership, exceptions, and completion rules.
2. Update the documentation impact matrix so a public symbol change requires a structured
   capability-impact record, not only free-form review judgment.
3. Update [`workflow.md`](../workflow.md), root `AGENTS.md`, documentation-site operations, and
   authoring guidance with the same single source of truth and no duplicated standards.
4. Define the machine-readable schemas before choosing filenames or implementation language.
5. Define focused accepted and rejected contract fixtures. Their executable scanner harness belongs
   to Phase 0B after compiled quality ownership exists.

Exit condition: governance has an unambiguous rule for every failure class in the frozen baseline,
the rule states which part is machine-enforced versus reviewer-owned, and every schema has reviewed
positive and negative fixtures.

Phase 0A completed on 2026-08-25. The canonical governance page now owns capability identity,
document types, version lanes, sample classes, structured public capability impact, exact
exceptions, debt-ratchet completion, and the machine/reviewer boundary. The
[`contract-set.json`](../contracts/documentation-governance-v2/contract-set.json) manifest freezes
five standalone Draft 2020-12 schemas and one focused accepted/rejected pair for each. Record
filenames, discovery layout, scanner language, and executable validation remain deliberately
unselected until Phase 0B. Workflow, source-comment authoring, site operations, `AGENTS.md`, and
their public Chinese mirrors point to the canonical contract without duplicating it. No Gradle
task, workflow, branch-protection context, publishing input, or public API changed.

### Phase 0B: report-only scanner foundation

1. Implement the Phase 0A schema models, parsers, and fixtures in the compiled quality included
   build established by the coordinated pull-request gate plan.
2. Add tested report-only discovery for public DSL/Modifier declarations, public Markdown pages,
   executable code fences, locale mirrors, and publishing/version inputs.
3. Preserve all existing public task names and failures while the report remains non-blocking.
4. Reject any implementation that duplicates the scanner temporarily in the root build or in an
   uncompiled script fragment.

Exit condition: the scanner executes through compiled quality ownership, its fixtures pass in
isolation, and enabling report output does not change an existing blocking gate result.

Phase 0B completed on 2026-08-26. `reportDocumentationGovernanceV2` is owned only by the compiled
quality included build, validates all five accepted fixtures and rejects all five negative fixtures,
and writes byte-stable JSON without timestamps or absolute paths. Record placement is frozen at
`docs/project/records/documentation-governance-v2/`; document identity remains in canonical
Markdown front matter.

The first report discovered 254 broad DSL/Modifier candidates, 119 canonical active Markdown
pages, 198 Kotlin/Java fences, 116 Chinese locale mirrors, 38 publishing artifacts, and no record
files. It reported 299 non-blocking findings: 113 pages without V2 metadata and 186 fences without
an adjacent registration marker. This is discovery evidence, not the Phase 1 debt baseline:
Phase 0B deliberately includes app/sample candidates and uses conservative textual declaration and
marker recognition. Therefore the absolute inventory is useful, but change from a prior baseline is
not available and the debt conclusion is `inconclusive` until Phase 1 removes false positives and
freezes exact IDs. The compiled quality suite passed 33/33 tests, the documentation script suite
passed 52/52 tests, and repeated final-checkout report executions produced the same SHA-256
`c79c10f1ac6dc2e800dbda6fc9c4f91bb3f9946427e4030c6627d86959c2eb29`. The frozen task graphs
matched exactly: `qaQuick` 2,899/2,899, `qaPreview` 1,640/1,640, `qaFull` 3,098/3,098, and
`verifyDocumentationStructure` 4/4. The blocking-gate conclusion is therefore
`no material change`. A build-logic-invalidated `qaQuick` then passed in 7 minutes 30 seconds with
2,341 actionable tasks (2,261 executed and 80 up-to-date). Phase 1 accuracy work is the next action.

PR #164's first documentation build exposed that the durable governance prose crossed the existing
46.9 MiB non-API site ceiling. A same-corpus detached-parent build measured 49,175,724 B; the
initial branch representation measured 49,184,804 B, an increase of 9,080 B (0.0185%). The fix did
not raise the threshold or delete a public contract: Phase 0B execution evidence remains here,
while the public governance page now keeps only the durable enforcement rule. The final complete
site build measured 49,176,039 B, 315 B (0.00064%) above the parent and 2,175 B below the existing
limit, and completed in 43.2 seconds. The conclusion is `no material change`. The comparison
excludes `/api/**` exactly as the production non-API metric does; future public contract growth
still requires structural site-budget work rather than another prose-only threshold increase.

### Phase 1: complete discovery and report-only audits

1. Discover public/protected declarations from production sources without counting app, test,
   generated, internal, or retired surfaces as published capability.
2. Classify public `UiTreeBuilder`/scope DSLs, `Modifier.*` extensions, components, hosts,
   integrations, and tooling entries by artifact and source package.
3. Derive artifact versions and released/unpublished state from publishing metadata and the
   immutable documentation release registry.
4. Discover every active public page from the documentation and translation policies; do not keep
   a second handwritten page list.
5. Parse every executable Markdown fence and its source marker, language mirror, version lane, and
   dependency declaration.
6. Emit deterministic human-readable and machine-readable reports for orphan symbols, orphan
   pages, duplicate owners, unclassified fences, version conflicts, taxonomy mismatches, and stale
   generated output.
7. Commit one exact reviewed debt baseline with stable IDs and tests that reject broadening it.

Exit condition: repeated scans of an unchanged checkout are byte-for-byte stable, and every known
problem is either represented by an exact baseline ID or rejected as a scanner false positive with
a regression fixture.

Phase 1 completed on 2026-08-26. Discovery now reads the existing translation policy for the public
page set, the module catalog for artifact families, publishing metadata plus the immutable release
registry for version state, and only registered Maven artifacts' production `src/main` sources.
The Kotlin lexical scan ignores comments, strings, nested/local declarations, non-public entries,
Demo, samples, tests, generated output, internal packages, retired artifacts, and renderer-only
surfaces. It groups overloads by canonical symbol and classifies component, DSL, Modifier, host,
integration, and tooling entries by artifact and source package. Executable-fence records include
stable content identity, source marker, locale peer, version lane, and discovered dependency data.

The reviewed inventory contains 433 production entries, 116 policy-owned public pages, 198
Kotlin/Java fences, 116 locale mirrors, 38 publishing artifacts, and 311 exact baseline records.
All 733 findings resolve to a stable issue ID and one baseline ID: 112 missing-metadata findings,
433 orphan-symbol findings, 178 unclassified-sample findings, and 10 taxonomy mismatches comprising
two missing locale fences and eight differing locale fence bodies. All 311 baseline records match
their exact target/category count, and the unbaselined count is zero. Relative to Phase 0B, Demo and
sample declaration false positives fell from four to zero (-100%), public-page discovery fell from
119 broad active files to 116 policy-owned pages (-2.52%), and recognized sample markers reduced
unclassified fences from 186 to 178 (-4.30%). The total finding count rose from 299 to 733
(+145.15%) because Phase 1 newly audits production entry ownership and locale parity; this is an
expanded accurate inventory, not a content regression. The discovery-accuracy conclusion is
`improved`, while blocking-gate behavior remains `no material change` because the task is still
report-only.

The machine and human reports are deterministic build outputs; unit tests also prove that a wider
violation count is reported as `broadened`. The scanner is intentionally a compiled lexical parser,
not a Kotlin compiler frontend, and the repository currently has no Java production sources to
exercise. Stale generated-reference output remains a zero-result category until the Phase 4 engine
creates that output. Phase 2 is the next action and will turn unbaselined, widened, increased,
re-added, or invalid exceptions into blocking failures without changing the frozen baseline.
The compiled quality suite passed 33/33 tests. Two independently regenerated final-checkout reports
were byte-identical: machine JSON SHA-256
`b7ee75107d6275f43688199ce17e0b89db79112a2923c73b223e0d746e0491bd` and human report SHA-256
`dfdc8082ee87512758df9eb626476bf2d234b451e0800e9494a07403d1604d9f`. The documentation script
suite passed 52/52 tests, all 116 required translations were current, development-tooling isolation
passed, and the frozen task graphs remained exact: `qaQuick` 2,899/2,899, `qaPreview` 1,640/1,640,
`qaFull` 3,098/3,098, and `verifyDocumentationStructure` 4/4. The first full `qaQuick` attempt ran
for 8 minutes 18 seconds and reached `:samples:counter:mergeExtDexDebug` before D8 exhausted the
default 2 GiB Gradle heap. Repeating the same checkout with a one-run-only 4 GiB heap and four
workers passed in 47 seconds with 2,341 actionable tasks (209 executed and 2,132 up-to-date).
Because no repository memory setting changed and the repeated run passed the complete gate, the
implementation conclusion remains `improved`; the limitation is host-memory sensitivity during a
fully invalidated Android build, and Phase 2 remains the next action.

### Phase 2: no-new-debt gates

1. Add a public-API change gate: a new or changed public DSL/Modifier entry fails unless its
   capability owner, module/version lane, KDoc, sample classification, generated reference, and
   Tutorial/Guide/Architecture impact are resolved.
2. Replace the hardcoded tutorial page map with automatic page discovery plus type-specific sample
   registration. A new Tutorial that is not compiled must fail.
3. Reject unclassified executable fences and validate exact source-region identity in both locales.
4. Validate `doc_type`, allowed directory, required metadata, unique capability ownership, and
   version-lane consistency.
5. Generate reference indexes and inventory counts from source; fail when committed output or
   links are stale.
6. Require redirects for moved public routes and validate every locale route before merge.
7. Attach the ratcheted tasks to `verifyDocumentationStructure`, `qaQuick`, pull-request CI, and
   documentation deployment as appropriate.

Exit condition: representative negative fixtures prove that an undocumented `Modifier.foo`, an
uncompiled new Tutorial, a Guide containing Plan metadata, a stale named argument, an ambiguous
version lane, and an added debt exception all fail before content migration begins.

The first Phase 2 slice completed the baseline ratchet on 2026-08-26 without claiming the full
phase exit condition. The report-only task was hard-cut to `verifyDocumentationGovernanceV2`. It
still writes deterministic machine and human reports before failing, but now rejects every
unbaselined issue, non-exact baseline count, duplicated exception identity/target, added or
re-added exception, immutable target/category/rationale change, rename/copy, and count increase.
Deleting a resolved record is allowed; reducing a record is allowed only when its immutable
identity is unchanged, `violation_count` decreases, and the new count exactly matches discovery.
The verification base comes from an explicit CI revision with a full-history `origin/main`
fallback, so adding an exception cannot hide newly added debt.

`verifyDocumentationStructure` now owns the gate, which also places it in `qaQuick`, `qaFull`, and
documentation CI while leaving `qaPreview` unchanged. Relative to the immutable Phase 0 task-graph
comparator, `qaQuick` changed from 2,899 to 2,900 tasks (+1, +0.0345%), `qaFull` from 3,098 to 3,099
(+1, +0.0323%), and `verifyDocumentationStructure` from four to five (+1, +25%); the only added
path in each affected graph is `:verifyDocumentationGovernanceV2`. The conclusion is `improved`:
733 existing findings still match all 311 exact entries with zero unbaselined issue, while new or
widened debt has changed from non-blocking to blocking. The quality included build passed 35/35
tests, including monotonic reduction, immutable identity, addition/re-addition, deletion, widened
count, and unbaselined-debt negatives. Documentation verification passed 55/55 script tests and
116/116 current translations. The deterministic report hashes are
`6c9e7b91160a018c49fe98be662be26d904bb856f07b78e171f21a62a1101de1` for JSON and
`8c1a3f2d6736bb086bfedc5f11eb9e7a1ebdc6473835e62061dd9111b9857f6f` for text.
An implementation-invalidated `qaQuick` passed in 7 minutes 41 seconds with 2,342 actionable
tasks (2,262 executed and 80 up-to-date), exactly one more executed task than the Phase 1 run.
The first pull-request documentation build passed Governance V2 and complete API generation but
exposed 7,020.6 B (+0.0143%) of non-API site-budget debt. The same-corpus correction preserved the
active contract, canonicalized the shared social card, pruned its 761,036 B locale copy, and passed
the production build with 762,215.4 B (1.5499%) headroom. The owning site contract records the
absolute and normalized comparison; the conclusion is `improved`, the single local production
build is the limitation, and independent documentation CI is the next acceptance action.
The limitation of the first slice was that it enforced debt monotonicity only. The second slice
removes the handwritten Tutorial map: the gate recursively discovers canonical and localized
Tutorial pages, requires exactly one stable `tutorial-sample` registration per non-index page,
derives optional artifact requirements from that marker, and rejects duplicate sample IDs or
unregistered source inputs. The shared scanner now interprets `compiled-region`,
`generated-signature`, `non-executable`, `tutorial-sample`, and `paired-sample` according to their
type-specific fields. All registered compiled fences are checked against one unique delimited
source region in both the canonical page and language mirror; stale content, invalid fields,
forbidden Tutorial pseudocode, and source paths outside registered source-set inputs become
unbaselined taxonomy debt and fail the existing ratchet.

The current inventory remains 433 production entries, 116 public pages, 198 Kotlin/Java fences,
116 locale mirrors, 733 findings, and 311 exact baseline records. Twenty fences are registered—12
Tutorial regions and eight migration comparison regions—and all 20 canonical plus 20 mirrored
source comparisons have zero classification violation. The remaining 178 historical unclassified
fences stay frozen rather than being reclassified by assertion; Phase 5 removes that debt through
owned compiled-source migration. Automatic discovery and exact source identity are therefore
`improved`, while the historical issue count has `no material change`. Complete capability-impact
semantics, generated Reference freshness, route-change checks, and migration of the frozen fence
debt remain later slices. Public API capability-impact ownership is the next action.

The second-slice quality included build passed 36/36 tests. Negative coverage proves that a nested
new Tutorial without one compiled registration fails automatic discovery and that stale localized
fence content fails exact source identity. `verifyTutorialSamples`,
`verifyDocumentationStructure`, `verifyDevelopmentToolingIsolation`, and release-intent validation
all passed; the documentation script suite passed 55/55 tests and all 116 required translations
were current. Two independently forced Governance V2 reports were byte-identical: machine JSON
SHA-256 `fc1d37497983376ff35c5d036cc4fa9eb21d6182f6da385d6b368f1cb94bb5fa` and human report
SHA-256 `8c1a3f2d6736bb086bfedc5f11eb9e7a1ebdc6473835e62061dd9111b9857f6f`.
The final implementation-invalidated `qaQuick` passed in 10 minutes 22 seconds with 2,342
actionable tasks (2,246 executed and 96 up-to-date). Against the first-slice acceptance run,
actionable task count was unchanged (0%), executed work fell by 16 tasks (-0.71%), while wall time
rose by 161 seconds (+34.92%). The opposing work/time directions and uncontrolled host load/cache
make build-timing impact `inconclusive`; the task-graph conclusion is `no material change`, and the
functional gate conclusion remains `improved`. One local host run is the limitation and
pull-request CI is the next independent acceptance step.

The third Phase 2 slice hard-cuts structural capability impact from a review-only statement to a
blocking comparison with the exact pull-request base. The shared production scanner fingerprints
the changed application-facing DSL/scope, `Modifier`, component, host, integration, and tooling
overload sets, including annotations, default values, visibility, deprecation state, artifact, and
symbol identity. It classifies additions, signature changes, deprecations, deletions, and
unambiguous symbol moves while treating a source-file-only move as neutral. Every detected change
must match exactly one capability-impact record newly added by the same pull request; historical
impact records are immutable and stale/pre-created records fail.

The matched impact must reference one valid capability owner for the artifact and current symbol,
and that capability must resolve a valid sample record belonging to the same capability. Duplicate
record IDs, missing/mismatched owners, missing/mismatched samples, and an impact that does not
classify exactly one detected change are blocking. Negative coverage proves that an undocumented
new `Modifier.foo` fails, an overload addition is classified as `changed` rather than a new
capability, a changed signature passes only with exact owner/sample evidence, and mutating a merged
impact record fails. Body-only behavior changes and low-level public types outside the capability
inventory remain reviewer-owned detection boundaries, not exemptions from the impact contract.
Generated Reference ownership/freshness is the next Phase 2 slice.

The quality included build passed 41/41 tests, the documentation script suite passed 55/55 tests,
and all 116 required Chinese translations were current. The real repository scan retained 433
production entries and all 733 historical findings matched the same 311 exact exceptions, with
zero structural public API change and zero new violation in this tooling-only pull request. Two
forced report runs were byte-identical: machine JSON SHA-256
`f97f72207ed941452fe14eab61839eb51a52eb5b2b9469921314436e549ac793` and human report SHA-256
`9e4585315638f99953cda4d61bc32e99f757856617d534a2597886faa427343c`.
An all-tasks-rerun `qaQuick` passed in 7 minutes 47 seconds with 2,342/2,342 tasks executed, and
release-intent verification reported zero changed, ignored, or shared release classifications
against `ead2b998b5daa4457ea9e37c8e50da3c41d663b7`. The actionable task count is unchanged from the
preceding slice (0%); wall time fell by 155 seconds (-24.92%) despite 96 more tasks being executed,
so host/cache effects make timing `inconclusive`. The task-graph conclusion is `no material change`
and structural change enforcement is `improved`. One local host remains the limitation, and
pull-request CI is the independent acceptance step.

The fourth Phase 2 slice and early Phase 4 engine hard-cut the application-facing Reference to the
same compiled Governance V2 model. `updateDocumentationCapabilityReference` intentionally writes
one committed normalized artifact table and capability-group tree; ordinary verification derives
the expected bytes independently and reports unbaselined `stale-generated-output` instead of
auto-healing drift. The `/reference/` page consumes that tree for both locales, supports in-page
symbol/artifact/namespace search and one-group-at-a-time browsing, and links each entry to its
versioned API/KDoc root and module manual. Modifier Architecture no longer carries a second scan,
handwritten count, public/internal mixture, or exhaustive table.

The source inventory contains 433 unique application-facing entries across 29 artifacts and 15
exclusive groups. All 433 entries appear exactly once, internal/renderer-only entries remain
excluded, and one aggregate source fingerprint makes overload/signature drift observable without
shipping raw hashes to the browser. Structured capability ownership remains 0/433 because this
slice does not relabel the frozen 433 `orphan-symbol` findings by assertion; exact capability,
sample, and related-document links therefore remain an explicit Phase 3/4 migration limitation.
The historical baseline is unchanged at 733 findings and 311 exact exceptions with zero
unbaselined issue. Relative to the previous zero-result stale-output category and handwritten
Modifier inventory, discovery and deployable navigation are `improved`; final Reference ownership
coverage is not yet complete.

The first complete site build exposed that a denormalized 469,689-byte catalog raised non-API
output to 47.3 MiB against the 46.9 MiB ceiling. Normalizing version/routes into one artifact table,
omitting absent migration fields, retaining one aggregate source fingerprint, and initially
rendering one selected group reduced the committed data to 134,072 bytes (-71.46%). The repeated
production build passed at 412.2 MiB total, 46.4/46.9 MiB non-API, 7.7/8.0 MiB JavaScript, 650/768
KiB largest JavaScript asset, and 35.0/120 seconds. This is an `improved` deployability result; its
limitation is one local optimized build and only 0.5 MiB of non-API headroom, so pull-request
documentation CI remains the independent check and later capability-record growth must preserve
the same budget.

The compiled quality suite passed 41/41 tests, including deterministic generation, exactly-one
group membership, internal exclusion, fresh-output acceptance, and stale-output rejection.
Documentation scripts passed 55/55, all 116 required Chinese mirrors were current, type-checking
passed, and the production build audited 440 site pages. The final Governance reports retained
deterministic SHA-256 values
`5cab3d87e8fc3c559524b482aec5bb55aae5c908871acd645f38ca671016d64f` for JSON and
`af47ac08c68add3dfe31a167fa8b391b376378fca8c79e22d679c04de4b507da` for text. The next
coordinated action is pull-request gate Phase 3 classification; this plan resumes broad content
reclassification and final per-entry link closure afterward.

The first final `qaQuick` attempt fully invalidated Android and Dokka work under the repository's
default 2 GiB Gradle heap, then stopped when garbage collection thrashed; no verification task had
reported a functional failure. Repeating the same checkout with a one-run-only 4 GiB heap and four
workers passed all 2,342 actionable tasks in 2 minutes 30 seconds (278 executed and 2,064
up-to-date). Release-intent verification independently reported zero release artifacts, zero
ignored artifacts, and zero shared-path classifications. The functional conclusion is `improved`
and accepted; build-time impact is `inconclusive` because the successful retry reused substantial
work and changed the process memory limit. The default-heap sensitivity of a fully invalidated
local build remains the host limitation, and no repository memory setting changed in this slice.

### Phase 3: information-architecture restructure

Apply the document-type contract without changing framework behavior.

Accepted Navigation slice on 2026-08-26:

- retained `/guides/navigation` as the stable route but replaced its mixed Stage 1–10 delivery,
  architecture, and acceptance content with the focused production-host task;
- moved transaction, destination/graph ownership, lifecycle, restoration, Back, and motion
  invariants to `docs/architecture/navigation.md`, and preserved the completed temporary sequence
  in `docs/archive/system-navigation-delivery-history.md`;
- registered `navigation.host`, its 21 Android application-facing symbols, and the
  `tutorial.navigation` compiled region, then regenerated the shared Capability Reference;
- removed 11 resolved exception records. The report changed from 733 to 701 issues: missing
  metadata 112 to 110, orphan symbols 433 to 412, taxonomy mismatches 10 to 7, and unclassified
  samples 178 to 172. Duplicate owners, orphan documents, version conflicts, stale output,
  unbaselined debt, and blocking violations remained zero;
- `:samples:tutorials:compileDebugKotlin`, Navigation Core tests, Navigation Android unit tests,
  Governance V2, documentation structure, 70 documentation script tests, and all 117 reviewed
  Chinese mirrors passed. The conclusion is `improved`: content and ownership debt decreased with
  no framework behavior change. The limitation is that this documentation-only slice reused the
  existing device behavior evidence; it makes no new device or performance claim. The next action
  was the equivalent Theming hard cut.

Accepted Theming slice on 2026-08-26:

- retained `/guides/theming` as the stable route and narrowed it to the application-owned
  System/Light/Dark mode-switching task; added focused dynamic-color/resource-refresh and local
  subtree-override Guides;
- moved token families, precedence, design-system isolation, renderer resolution, Android resource
  lifecycle, and diagnostic invariants to `docs/architecture/theming.md`, while dependency and
  conformance matrices remain in their owning module manuals;
- registered `theme.foundation` and `theme.material3`, their three application-facing DSL symbols,
  four focused task/tutorial regions, and two compiled Gradle dependency regions. The latter also
  repairs the Navigation tutorial dependency regression exposed by the complete tutorial gate;
- removed 6 resolved exception records. The report changed from 701 to 693 issues: missing
  metadata 110 to 108, orphan symbols 412 to 409, and unclassified samples 172 to 169. Taxonomy
  mismatches remained 7, while duplicate owners, orphan documents, version conflicts, stale
  generated output, unbaselined debt, and blocking violations remained zero;
- Tutorial compilation and exact-region verification, UI Foundation tests, Material 3 tests,
  Material 3 Android unit tests, Governance V2, documentation structure, 70 documentation script
  tests, release intent, and all 120 reviewed Chinese mirrors passed. The complete bilingual site
  audited 448 pages and passed its 60.0-second build budget; the surrounding local npm lifecycle
  completed in 82.73 seconds. The conclusion is `improved`: ownership and sample debt decreased
  with no framework or release-artifact change. The limitation is that the task samples are
  compile-time documentation evidence and make no new device or visual-conformance claim. The next
  action is the Text Input hard cut.

Accepted Text Input slice on 2026-08-26:

- retained `/guides/text-input` as the stable editing route, added one coherent rich/received-content
  Guide, and kept editing, IME action, rich-document, and Receive Content tasks independently
  enterable by heading and compiled example;
- moved document/offset ownership, edit/history transactions, native bridge feedback suppression,
  Receive Content normalization, persistence, and platform-verification boundaries to
  `docs/architecture/text-input.md`; dependency and compatibility detail remains in Text Core and
  UI Foundation module manuals;
- registered `text.input`, its `BasicTextField`, `TextField`, and `SearchBar` DSL entries, four task
  regions, the Tutorial region, and one compiled dependency region. Removed 6 resolved exception
  records. The report changed from 693 to 683 issues: missing metadata 108 to 106, orphan symbols
  409 to 406, and unclassified samples 169 to 164. Taxonomy mismatches remained 7; duplicate
  owners, orphan documents, version conflicts, stale generated output, unbaselined debt, and
  blocking violations remained zero;
- the first four-page representation exceeded the unchanged 46.9 MiB non-API site limit by 67,722
  bytes. Consolidating adjacent editing/IME and rich/Receive Content tasks into two Guides retained
  the four task boundaries and reduced generated output by 161,958 bytes to 49,083,978 bytes,
  leaving 94,236 bytes of budget. The final bilingual build audited 452 pages and completed the
  Docusaurus wrapper in 60.0 seconds;
- Tutorial compilation and exact-region verification, Governance V2, documentation structure, 70
  documentation script tests, and all 122 reviewed Chinese mirrors passed. The conclusion is
  `improved`: ownership and sample debt decreased, the final representation satisfies the existing
  site budget, and no framework or release artifact changed. The limitation is that IME, autofill,
  accessibility, hardware-keyboard, and stylus checklists reuse existing device evidence rather
  than making a new device-conformance claim. The next action is the Lazy Collections hard cut.

Accepted Lazy Collections slice on 2026-08-26:

- retained `/guides/lazy-collections` as the stable task route and focused it on container choice,
  list state control, adaptive grids, pager/tab coordination, and the ordinary List versus immutable
  snapshot decision; retained the independently runnable first-list and measured-performance
  Tutorials;
- moved logical key/Session identity, revision, immutable-snapshot, prefetch activation, mounted-tree
  reuse, saveable-state, pager, and Android renderer boundaries to
  `docs/architecture/lazy-collections.md`; module-specific API detail remains in the UI Foundation
  manual and Paging loading policy remains in its integration manual;
- registered `lazy.collections`, its `LazyColumn`, `LazyRow`, `LazyVerticalGrid`,
  `HorizontalPager`, `VerticalPager`, and `TabRow` entries, three Guide regions, two Tutorial
  regions, and one shared compiled dependency region. Removed 7 resolved exception records. The
  report changed from 683 to 668 issues: missing metadata 106 to 103, orphan symbols 406 to 400,
  and unclassified samples 164 to 158. Taxonomy mismatches remained 7; duplicate owners, orphan
  documents, version conflicts, stale generated output, unbaselined debt, and blocking violations
  remained zero;
- the first representation exceeded the unchanged 46.9 MiB non-API site limit by 9,033 bytes.
  Removing a duplicated vertical-pager code branch while retaining its shared contract, then
  consolidating module-owned detail, reduced generated output to 49,168,958 bytes and left 9,256
  bytes of budget. The final bilingual build audited 454 pages and completed Docusaurus in `36.6 s`;
- Tutorial and Guide compilation, exact-region ownership, Governance V2, documentation structure,
  documentation script tests, language and translation review, generated Reference freshness, and
  the complete bilingual site passed. The conclusion is `improved`: ownership and sample debt
  decreased, every task remains independently enterable, and no framework or release artifact
  changed. The limitation is that compile-time examples make no new device, accessibility, focus,
  or scrolling-performance claim. The next action is the Focus and Nested Scroll hard cut.

Accepted Focus and Nested Scroll slice on 2026-08-26:

- retained `/guides/focus-and-input` and `/guides/nested-scroll` as stable routes, narrowed them to
  focus/key/restoration/editor-visibility and ancestor-scroll/custom-dispatch tasks, and moved
  propagation, connector ownership, AndroidView boundaries, AndroidX mapping, edge arbitration,
  and legacy fling constraints into the existing Modifier Architecture route;
- registered `focus.input` for seven UI Contract focus/key Modifier entries and `nested.scroll` for
  the Gesture `nestedScroll` entry, plus two compiled Guide regions. Three document metadata debts,
  three executable-fence debts, and all eight selected orphan symbols were repaired. Five fully
  resolved exception records were removed, while the two shared source-file exceptions were
  narrowed from 12 to 5 and from 7 to 6 remaining symbols;
- the report changed from 668 to 654 issues: missing metadata 103 to 100, orphan symbols 400 to 392,
  and unclassified samples 158 to 155. Taxonomy mismatches remained 7; duplicate owners, orphan
  documents, version conflicts, stale generated output, unbaselined debt, and blocking violations
  remained zero;
- the first complete site representation exceeded the unchanged 46.9 MiB non-API limit by 21,103
  bytes. Removing the duplicate Pager code display while retaining its task contract and
  consolidating architecture prose reduced output to 49,165,583 bytes, leaving 12,631 bytes. The
  corrected bilingual build audited 454 pages and completed Docusaurus in `51.7 s`;
- both compiled regions, exact ownership, Governance V2, language and translation review,
  generated Reference freshness, and the complete bilingual site passed. A complete `qaQuick`
  verification after cleaning generated outputs also passed 2,342 actionable tasks in `9 min 26 s`
  (2,143 executed and 199 up-to-date). The conclusion is `improved`: user tasks and runtime
  contracts now have distinct owners, debt decreased, and no framework or release artifact
  changed. The complete run is cold-build completeness evidence, not a gate-speed comparison; the
  slice reuses existing unit, instrumentation, and device evidence and makes no new focus,
  keyboard, or scroll-performance claim. The next action is the Shadows hard cut.

Accepted Shadows slice on 2026-08-26:

- retained `/guides/shadows` as the stable application task, reduced it to exact outer/inner shadow
  construction, layout-space, raster-stability, backend-enablement, and manual-verification steps,
  kept drawing-plane ownership in Modifier Architecture, and concentrated installation, shape
  resolution, cache, replay policy, diagnostics, compatibility, and benchmark contracts in the
  Shadow Android module manual;
- registered `shadow.modifiers` for four UI Contract Modifier entries and
  `shadow.android-backend` for all 19 Shadow Android integration entries, plus one Guide and two
  module compiled regions. Eleven resolved exception records were removed and the shared Modifier
  source-file exception narrowed from 14 to 10 remaining symbols;
- the report changed from 654 to 625 issues: missing metadata 100 to 98, orphan symbols 392 to 369,
  and unclassified samples 155 to 151. Taxonomy mismatches remained 7; duplicate owners, orphan
  documents, version conflicts, stale generated output, unbaselined debt, and blocking violations
  remained zero;
- the first bilingual build completed English output but rejected two Chinese links whose relative
  paths were one level too deep. Correcting those links produced 49,136,607 non-API bytes, leaving
  41,607 bytes under the unchanged limit after acceptance evidence was included. The complete build
  audited 454 pages and completed Docusaurus in `42.9 s`;
- all three compiled regions, exact ownership, Governance V2, documentation structure, language
  and translation review, TypeScript, generated Reference freshness, accessibility, and the
  complete bilingual site passed. A warm complete `qaQuick` also passed 2,342 actionable tasks in
  `34 s` (186 executed and 2,156 up-to-date); this is completeness evidence, not a gate-speed
  comparison. The conclusion is `improved`: runtime and application contracts now have distinct
  owners and debt and generated size decreased. Release intent explicitly ignores
  `viewcompose-shadow-android` because its compiled API sample changed only by adding region-boundary
  comments; no framework API, behavior, metadata, or published artifact changed. The limitation is
  that this documentation cut reuses the existing shadow
  screenshot, unit, device, and benchmark evidence and makes no new fidelity or performance claim.
  The next action is the Overlays hard cut.

Accepted Overlays slice on 2026-08-26:

- retained the Tutorial, Guide, ADR, and three module-manual routes; narrowed the Guide to bottom
  sheet, anchored-menu, transient-feedback, and backend-verification tasks; and concentrated
  root selection, transport/session, attribution, fallback, presenter, and compatibility contracts
  in ADR-0006 and the owning module manuals;
- registered six capability owners for 21 UI Foundation, UI Contract, Host Android, neutral Android,
  Material 3, and One UI 7 entries, plus the existing Tutorial Activity and seven new compiled
  regions. Twenty-three resolved exception records were removed; the two shared source-file
  exceptions narrowed from 5 to 4 and from 7 to 2 remaining symbols;
- the report changed from 625 to 590 issues: missing metadata 98 to 92, orphan symbols 369 to 348,
  and unclassified samples 151 to 143. Taxonomy mismatches remained 7; duplicate owners, orphan
  documents, version conflicts, stale generated output, unbaselined debt, and blocking violations
  remained zero;
- the complete bilingual site retained 454 audited pages, produced 49,142,652 non-API bytes, left
  35,562 bytes under the unchanged 46.9 MiB limit, and completed Docusaurus in `33.4 s`. Complete
  `qaQuick` passed 2,342 actionable tasks in `30 s` (183 executed and 2,159 up-to-date);
- the conclusion is `improved`: task guidance and durable contracts now have distinct owners,
  exact sample ownership increased, and governance debt decreased without a route, framework,
  public API, or published-artifact change. This documentation slice reuses existing overlay unit
  and platform behavior evidence and makes no new visual-fidelity or performance claim. The next
  action is the Image Loading metadata and compiled-region hard cut.

Accepted Image Loading slice on 2026-08-26:

- retained the existing Guide, Migration, Coil, and Glide routes; kept task and lifecycle guidance
  in the Guide, source-breaking transition guidance in Migration, and dependency/compatibility
  boundaries in the module manuals;
- registered `image.foundation`, `image.coil`, and `image.glide` for four application-facing
  entries, plus four Guide regions, two current Migration regions, one explicitly non-executable
  removed-API baseline, and two compiled dependency regions. Twelve resolved exception records
  were removed and the shared content-widget exception narrowed from 4 to 3 symbols;
- the report changed from 590 to 571 issues: missing metadata 92 to 88, orphan symbols 348 to 344,
  taxonomy mismatches 7 to 6, and unclassified samples 143 to 133. Duplicate owners, orphan
  documents, version conflicts, stale generated output, unbaselined debt, and blocking violations
  remained zero;
- Tutorial compilation, exact bilingual region identity, Governance V2, documentation structure,
  language and translation review, complete API reuse, release intent, and the complete bilingual
  site passed. The site audited 454 pages, produced 49,151,753 non-API bytes, left 26,461 bytes
  under the unchanged 46.9 MiB limit, and completed Docusaurus in `34.6 s`. Complete `qaQuick`
  passed 2,342 actionable tasks in `32 s` (189 executed and 2,153 up-to-date);
- the conclusion is `improved`: all copy-ready image examples now have one compiled source owner,
  the Chinese adapter example is byte-identical to canonical code, and governance debt decreased
  without changing framework API, behavior, routes, or published artifacts. This slice reuses
  existing adapter and renderer behavior evidence and makes no new device, network, or performance
  claim. The next action is Modifier Architecture and final Reference link closure.

Accepted Modifier Architecture slice on 2026-08-26:

- retained the existing Architecture and two Tutorial routes, replaced the duplicated handwritten
  Modifier inventory with a concise ownership/runtime contract, and kept exhaustive discovery in
  the source-generated Capability Reference;
- registered seven capability owners for 51 UI Contract and Gesture Modifier entries, plus the two
  existing Tutorial Activity regions, two compiled dependency regions, and five architecture-owned
  compiled regions. Eleven resolved exception records were removed;
- the report changed from 571 to 516 issues: missing metadata 88 to 86, orphan symbols 344 to 293,
  and unclassified samples 133 to 131. Taxonomy mismatches remained 6; duplicate owners, orphan
  documents, version conflicts, stale generated output, unbaselined debt, and blocking violations
  remained zero;
- Tutorial compilation, exact bilingual region identity, Governance V2, documentation structure,
  language and translation review, complete API reuse, release intent, and the complete bilingual
  site passed. The site audited 454 pages, produced 49,095,993 non-API bytes, left 82,221 bytes
  under the unchanged 46.9 MiB limit, and completed Docusaurus in `28.0 s`. Complete `qaQuick`
  passed 2,342 actionable tasks in `18 s` (178 executed and 2,164 up-to-date);
- the conclusion is `improved`: exact structured ownership increased, duplicated prose and
  generated site size decreased, and every migrated capability now has one compiled sample
  decision without changing framework API, runtime behavior, routes, or published artifacts.
  Release intent found zero affected artifacts. The limitation is that this documentation slice
  reuses existing contract and renderer tests and makes no new device, visual, or performance
  claim. The next action is module, migration, tooling, and remaining executable-example ownership.

Accepted ConstraintLayout module slice on 2026-08-26:

- retained the existing module route, replaced repeated chronological phase logs with one current
  installation, DSL, ownership, reconciliation, evidence, and migration contract, and kept
  completed implementation history in the existing ADR, performance, and archive owners;
- registered `constraintlayout.core` and `constraintlayout.helpers` for all 43 public DSL and helper
  entries, plus five compiled dependency, inline-constraint, dimension, `ConstraintSet`, and helper
  sample decisions. Three resolved exception records were removed;
- the report changed from 516 to 468 issues: missing metadata 86 to 85, orphan symbols 293 to 250,
  and unclassified samples 131 to 127. Taxonomy mismatches remained 6; duplicate owners, orphan
  documents, version conflicts, stale generated output, unbaselined debt, and blocking violations
  remained zero;
- Tutorial compilation, bilingual region identity, Governance V2, documentation structure,
  language and translation review, complete API reuse, release intent, and the complete bilingual
  site passed. The final evidence-bearing site audited 454 pages, produced 48,982,759 non-API
  bytes, left 195,455 bytes under the unchanged 46.9 MiB limit, and completed Docusaurus in
  `25.2 s`. Complete `qaQuick` passed 2,342 actionable tasks in `22.3 s` (189 executed and 2,153
  up-to-date);
- the conclusion is `improved`: all public ConstraintLayout entries now have exact generated
  ownership and compiled module examples, repeated manual content and generated site size
  decreased, and no framework API, runtime behavior, route, or published artifact changed. The
  slice reuses the existing unit, instrumentation, device, visual, and performance evidence and
  makes no new runtime or performance claim. The next action is Preview tooling ownership.

Accepted Preview tooling slice on 2026-08-26:

- retained the existing tooling and five module routes, separated the task-oriented install,
  entry, matrix, device, and snapshot workflow from the Core, Gradle, Runner, Worker Host, and
  application-process integration ownership contracts, and removed repeated protocol and device
  inspector prose;
- registered six capability owners for all 62 public annotations, protocol, Gradle, runner,
  worker, and integration entries, plus nine compiled installation, entry, matrix, protocol,
  render, worker, theme-provider, and Compose-bridge sample decisions. Twenty-seven resolved
  exception records were removed;
- the report changed from 468 to 390 issues: missing metadata 85 to 79, orphan symbols 250 to 188,
  and unclassified samples 127 to 117. Taxonomy mismatches remained 6; duplicate owners, orphan
  documents, version conflicts, stale generated output, unbaselined debt, and blocking violations
  remained zero;
- all seven affected sample compilation targets, `qaPreview`, bilingual region identity,
  Governance V2, documentation structure, language and translation review, complete API reuse,
  release intent, and the complete bilingual site passed. The final evidence-bearing site audited
  454 pages, produced 48,647,612 non-API bytes, left 530,602 bytes under the unchanged 46.9 MiB
  limit, and completed Docusaurus in `27.8 s`. Complete `qaQuick` passed 2,342 actionable tasks in
  `59.1 s` (194 executed and 2,148 up-to-date);
- the conclusion is `improved`: exact ownership and compiled workflow coverage increased while
  repeated prose and generated output decreased. No production API, runtime behavior, route, or
  published artifact changed; release intent found zero release artifacts and explicitly ignored
  five artifacts whose test samples gained comments only. This slice reuses existing protocol,
  runner, device-diagnostic, and Paparazzi evidence and makes no new device, visual, or performance
  claim. The next action is third-party integration module ownership.

Accepted third-party integration slice on 2026-08-26:

- retained the CameraX, Google Maps, Media3, and legacy ExoPlayer module routes and their verified
  lifecycle/resource contracts. Google Maps ownership is deliberately split between native View
  configuration/lifecycle and keyed Marker/Polyline content; the other three modules each have one
  cohesive integration capability owner;
- registered five capability owners for all 32 public entries and nine compiled dependency,
  CameraX preview, Google Maps View/content, Media3 player, and legacy ExoPlayer sample decisions.
  Fourteen resolved metadata, symbol, taxonomy, and sample exception records were removed;
- the report changed from 390 to 345 issues: missing metadata 79 to 75, orphan symbols 188 to 156,
  taxonomy mismatches 6 to 5, and unclassified samples 117 to 109. Duplicate owners, orphan
  documents, version conflicts, stale generated output, unbaselined debt, and blocking violations
  remained zero;
- all five affected sample compilation targets, all four module unit-test suites, Governance V2,
  documentation structure, language and translation review, complete API reuse, release intent,
  and the complete bilingual site passed. The evidence-bearing site audited 454 pages, produced
  48,715,878 non-API bytes, left 462,336 bytes under the unchanged 46.9 MiB limit, and completed
  Docusaurus in `27.4 s`. This is 68,266 bytes (`0.1403%`) above the Preview slice because four
  module metadata contracts and full compiled samples are now emitted. Complete `qaQuick` passed
  2,359 actionable tasks in `59 s` (207 executed and 2,152 up-to-date);
- the conclusion is `improved`: all four integration families now have exact generated ownership,
  independently compiled installation/usage samples, and identical bilingual code while remaining
  well inside the unchanged site budget. Release intent found zero release artifacts and explicitly
  ignored four test-sample-only artifacts. No production API, runtime behavior, route, or published
  artifact changed. This slice reuses the existing module, device, visual, and third-party SDK
  evidence and makes no new runtime, network, power, or performance claim. The next action is
  remaining module and executable-sample ownership.

Accepted Animation ownership slice on 2026-08-27:

- retained the existing Animation Core, Animation, Tutorial, Migration, and ADR routes while
  separating platform-neutral engine contracts, composition-owned motion, historical migration
  source, and architectural type vocabulary through structured document metadata;
- registered one capability owner for all seven previously orphaned `AnimatedVisibility`,
  `AnimatedContent`, `Crossfade`, `animateContentSize`, and `animateBounds` public entries, plus 16
  exact compiled regions and three explicitly non-executable historical/architectural sample
  decisions. Seventeen resolved metadata, symbol, taxonomy, and sample exception records were
  removed;
- the report changed from 345 to 313 issues: missing metadata 75 to 69, orphan symbols 156 to 149,
  taxonomy mismatches 5 to 4, and unclassified samples 109 to 91. Duplicate owners, orphan
  documents, version conflicts, stale generated output, unbaselined debt, and blocking violations
  remained zero;
- Animation Core and Animation unit tests, all three affected compiled-sample targets, Governance
  V2, documentation structure, language and translation review, complete API reuse, release
  intent, and the complete bilingual site passed. The final evidence-bearing site audited 454
  pages, produced 48,745,500 non-API bytes, left 432,714 bytes under the unchanged 46.9 MiB limit,
  and completed Docusaurus in `25.3 s`. This is 29,622 bytes (`0.0608%`) above the third-party
  slice and `7.6%` faster locally, which is `no material change` for one content-only sample.
  Complete `qaQuick` passed 2,342 actionable tasks in `30 s` (211 executed and 2,131 up-to-date);
- the overall conclusion is `improved`: Animation ownership, bilingual executable coverage, and
  explicit historical-sample classification increased while routes, framework API, runtime
  behavior, and published artifacts remained unchanged. Release intent found zero release
  artifacts and explicitly ignored two test-sample-only artifacts. The slice reuses existing unit,
  renderer, device, visual, and performance evidence and makes no new runtime, motion-fidelity, or
  performance claim. Native Android animation interop remains with Host Android, which is the next
  Phase 3 audit boundary.

Accepted Host Android ownership slice on 2026-08-27:

- retained the existing Host Android, neutral Android aggregate, Android View Tutorial, host
  migration, and resource-environment ADR routes while splitting ownership into low-level
  container, native View transaction, Android resources, native animation, and native graphics
  capabilities. Lifecycle, ViewModel, and Renderer remain separate audit boundaries;
- registered five capability owners for all 34 previously orphaned entries and 12 exact compiled
  dependency, host entry, native View, resource, animation, graphics, Tutorial, and migration
  sample decisions. Nineteen resolved metadata, symbol, and sample exception records were removed;
- the report changed from 313 to 267 issues: missing metadata 69 to 64, orphan symbols 149 to 115,
  and unclassified samples 91 to 84. Taxonomy mismatches remained 4; duplicate owners, orphan
  documents, version conflicts, stale generated output, unbaselined debt, and blocking violations
  remained zero;
- Host Android and neutral Android unit tests, all four affected compiled-sample targets,
  migration and Tutorial marker verification, Governance V2, documentation structure, language
  and translation review, complete API reuse, release intent, and the complete bilingual site
  passed. The final evidence-bearing site audited 454 pages, produced 48,856,817 non-API bytes,
  left 321,397 bytes under the unchanged 46.9 MiB limit, and completed Docusaurus in `48.8 s`.
  This is 111,317 bytes (`0.2284%`) above the Animation slice and is `no material change` for five
  capability records plus complete bilingual samples. The local build-time comparison is
  `inconclusive` because cache and process state differed, but remains below the `120 s` limit.
  Complete `qaQuick` passed 2,342 actionable tasks in `19 s` (183 executed and 2,159 up-to-date);
- the overall conclusion is `improved`: all low-level Android host families now have exact
  generated ownership and compiled bilingual examples without changing public API, runtime
  behavior, routes, or published artifacts. Release intent found zero release artifacts and
  explicitly ignored two test-sample-only artifacts. This slice reuses existing unit, device,
  visual, resource, and performance evidence and makes no new runtime, visual, power, or
  performance claim. The next action is Lifecycle ownership.

Accepted Lifecycle ownership slice on 2026-08-27:

- retained the existing Lifecycle AndroidX module, neutral Android aggregate, lifecycle and saved
  state Architecture, transactional-effect ADR, and host migration routes while splitting
  ownership into owner boundaries, Flow collection, paired lifecycle effects, and committed native
  View lifecycle/saved-state integration. ViewModel and Renderer remain separate audit boundaries;
- registered four capability owners for all 18 previously orphaned public entries and seven exact
  compiled dependency, owner-boundary, collection, effect, lifecycle-adapter, and saved-state sample
  decisions. Ten resolved metadata, symbol, and sample exception records were removed;
- the report changed from 267 to 243 issues: missing metadata 64 to 61, orphan symbols 115 to 97,
  and unclassified samples 84 to 81. Taxonomy mismatches remained 4; duplicate owners, orphan
  documents, version conflicts, stale generated output, unbaselined debt, and blocking violations
  remained zero;
- Lifecycle AndroidX unit tests, both affected compiled-sample targets, Governance V2,
  documentation structure, language and translation review, complete API reuse, release intent,
  and the complete bilingual site passed. The final evidence-bearing site audited 454 pages,
  produced 49,039,723 non-API bytes, left 138,491 bytes under the unchanged 46.9 MiB limit, and
  completed Docusaurus in `30.8 s`. This is 182,906 bytes (`0.3744%`) above the Host Android slice
  and is `no material change` for four capability records plus complete bilingual samples. The
  local build-time comparison is `inconclusive` because cache and process state differed, but the
  absolute result remains below the `120 s` limit. Complete `qaQuick` passed 2,342 actionable tasks
  in `20 s` (185 executed and 2,157 up-to-date);
- the overall conclusion is `improved`: all Lifecycle integration families now have exact generated
  ownership and compiled bilingual examples without changing public API, runtime behavior, routes,
  or published artifacts. Release intent found zero release artifacts and explicitly ignored the
  one test-sample-only Lifecycle artifact. This slice reuses existing unit, device, lifecycle,
  saved-state, and performance evidence and makes no new runtime, visual, power, or performance
  claim. The next action is ViewModel ownership.

Accepted ViewModel ownership slice on 2026-08-27:

- retained the existing ViewModel AndroidX module, neutral Android aggregate, navigation
  Architecture, and host migration routes while splitting ownership into store-owner boundaries,
  AndroidX provider/factory/extras resolution, and `SavedStateHandle` integration. Renderer remains
  a separate audit boundary;
- registered three capability owners for all five previously orphaned public entries and four exact
  compiled regions covering dependency, owner boundary, store resolution with keyed instances, and
  saved state. Six resolved metadata, symbol, bilingual-drift, and sample exception records were
  removed;
- the report changed from 243 to 231 issues: missing metadata 61 to 60, orphan symbols 97 to 92,
  taxonomy mismatches 4 to 2, and unclassified samples 81 to 77. Duplicate owners, orphan
  documents, version conflicts, stale generated output, unbaselined debt, and blocking violations
  remained zero;
- ViewModel AndroidX unit tests, both affected compiled-sample targets, Governance V2,
  documentation structure, language and translation review, complete API reuse, release intent,
  and the complete bilingual site passed. The final evidence-bearing site audited 454 pages,
  produced 49,068,528 non-API bytes, left 109,686 bytes under the unchanged 46.9 MiB limit, and
  completed Docusaurus in `33.9 s`. This is 28,805 bytes (`0.0587%`) above the Lifecycle slice and
  is `no material change` after replacing duplicate handwritten snippets with four exact compiled
  regions. The local build-time comparison is `inconclusive` because cache and process state
  differed, but the absolute result remains below the `120 s` limit. Complete `qaQuick` passed
  2,342 actionable tasks in `20 s` (185 executed and 2,157 up-to-date);
- the overall conclusion is `improved`: ViewModel owner, provider, key, factory/extras, and
  saved-state contracts now have exact generated ownership and compiled bilingual examples without
  changing public API, runtime behavior, routes, or published artifacts. Release intent found zero
  release artifacts and explicitly ignored the one test-sample-only ViewModel artifact. This slice
  reuses existing unit, navigation, saved-state, and performance evidence and makes no new runtime,
  visual, power, or performance claim. The next action is Renderer ownership.

Accepted Renderer ownership slice on 2026-08-27:

- retained the existing Renderer Android module route while separating structural tree
  transactions, keyed reconciliation, diagnostics/timing tooling, and platform decoration backend
  ownership. Application diagnostics remain a separate audit boundary;
- hard-cut the public inventory scanner to include Renderer application packages and public
  `abstract`, `open`, and `fun interface` declarations. The generated inventory increased from 433
  to 479 entries: 41 previously absent Renderer declarations plus five previously omitted
  Lifecycle, Overlay Host, and Preview modifier-form declarations. The corrected existing owners
  now include those five declarations instead of leaving scanner blind spots;
- registered four capability owners for all 41 Renderer entries and six exact compiled dependency,
  tree-transaction, observed-property, reconciliation, timing, and decoration-backend sample
  decisions. The tree-transaction example now commits both frames before disposal, so a custom
  host cannot copy an incomplete transaction sequence. Two resolved metadata and sample exception
  records were removed;
- the report changed from 231 to 228 issues: missing metadata 60 to 59 and unclassified samples 77
  to 75. Orphan symbols remained 92 and taxonomy mismatches remained 2; duplicate owners, orphan
  documents, version conflicts, stale generated output, unbaselined debt, and blocking violations
  remained zero despite the 46-entry scanner expansion;
- Renderer Android unit tests, the affected Tutorial compilation, the quality-tool unit suite,
  Governance V2, documentation structure, language and translation review, complete API reuse,
  release intent, and the complete bilingual site passed. The evidence-bearing site audited 454
  pages, produced 49,149,638 non-API bytes, left 28,576 bytes under the unchanged 46.9 MiB limit,
  and completed Docusaurus in `24.7 s`. This is 81,110 bytes (`0.1653%`) above the ViewModel slice
  and is `no material change` for four capability records, 46 corrected generated entries, and six
  compiled sample decisions. The absolute build time remains below the `120 s` limit, but the
  narrow size headroom means the next slice must avoid duplicated prose or reduce generated output
  instead of increasing the budget. Complete `qaQuick` passed 2,342 actionable tasks in `8 min
  17 s` (2,089 executed and 253 up-to-date). The duration comparison with prior warm runs is
  `inconclusive` because this run regenerated and locally published most module documentation
  artifacts;
- the overall conclusion is `improved`: the complete Renderer extension surface now has exact
  generated ownership, compiled transaction examples, and scanner coverage without changing
  public API, production runtime behavior, routes, or published artifacts. Release intent found
  zero release artifacts and explicitly ignored the one test-sample-only Renderer artifact. This
  slice reuses existing unit, renderer, device, visual, and performance evidence and makes no new
  runtime, visual, power, or performance claim. The next action is Diagnostics ownership.

Accepted Diagnostics ownership slice on 2026-08-27:

- retained the Diagnostics module, Tooling, Tutorial, render-failure Architecture, and correlated
  diagnostics ADR routes while separating event correlation, session inspection, node timing, and
  production failure aggregation into four capability owners;
- hard-cut the scanner to inventory an exact allowlist of 52 public Foundation diagnostic
  contracts instead of treating the entire Foundation module as application-facing. The generated
  inventory increased from 479 to 531 entries, and all 52 newly visible entries received ownership
  in the same slice; the existing Diagnostics module contributes five aggregate and export entries;
- registered 11 exact dependency, event-stream, inspection, timing, aggregation, export, Android
  host, and ADR sample decisions. Twelve resolved metadata, taxonomy, symbol, bilingual-drift, and
  sample exceptions were removed;
- the report changed from 228 to 206 issues: missing metadata 59 to 54, orphan symbols 92 to 87,
  taxonomy mismatches 2 to 1, and unclassified samples 75 to 64. Duplicate owners, orphan
  documents, version conflicts, stale generated output, unbaselined debt, and blocking violations
  remained zero despite the 52-entry scanner expansion;
- normalized generated Reference ownership data once per capability instead of repeating the same
  reference ID, sample, and related-document objects on every entry. The catalog schema advanced
  from 1 to 2 and its committed size fell from 418,872 to 224,059 bytes, a reduction of 194,813
  bytes (`46.5078%`), even though the inventory gained 52 entries. The website consumer resolves
  entries through the normalized capability map, and a focused generator test protects the shape;
- UI Foundation and Diagnostics unit tests, the affected Tutorial compilation, the quality-tool
  unit suite, Governance V2, documentation structure, complete API reuse, release intent, and the
  complete bilingual site passed. The site audited 454 pages and 30 redirects, produced 48,945,157
  non-API bytes, and left 233,057 bytes under the unchanged 46.9 MiB limit. This is 204,481 bytes
  (`0.4160%`) below the Renderer slice and is `improved`. Docusaurus completed in `43.3 s`; the
  duration comparison with the Renderer slice is `inconclusive` because cache and process state
  differed, while the absolute result remains below the `120 s` limit. Complete `qaQuick` passed
  2,342 actionable tasks in `9 min 23 s` (2,075 executed and 267 up-to-date); this is completeness
  evidence rather than a speed comparison because it republished most module documentation;
- the overall conclusion is `improved`: diagnostics ownership is complete, scanner blindness is
  removed without widening unrelated Foundation APIs, and generated Reference growth is bounded
  without changing public API, production runtime behavior, routes, or published artifacts.
  Release intent found zero release artifacts and explicitly ignored the two test-sample-only
  artifacts. This slice reuses existing unit, device, diagnostic, and performance evidence and
  makes no new runtime, visual, power, or performance claim. The next action is Graphics Core and
  Graphics Android ownership.

Accepted Graphics ownership slice on 2026-08-27:

- retained the Graphics Core and Graphics module routes and treated their platform-neutral command
  model plus composition-facing Canvas and drawing Modifiers as one custom-drawing capability
  family, matching the established Animation/Animation Core ownership pattern;
- registered one capability owner for all six previously orphaned `Canvas`, `drawBehind`,
  `drawWithContent`, `drawWithCache`, `draw`, and `drawCache` entries. Six exact dependency, path,
  scene, Canvas, and cache sample decisions now back both module manuals, and the Graphics
  compatibility note now names the current `0.1.0-alpha04` line;
- removed five resolved metadata, symbol, and sample exception records. The report changed from 206
  to 192 issues: missing metadata 54 to 52, orphan symbols 87 to 81, and unclassified samples 64 to
  58. Taxonomy mismatches remained 1; duplicate owners, orphan documents, version conflicts, stale
  generated output, unbaselined debt, and blocking violations remained zero;
- the generated Reference retained 531 entries while its normalized catalog grew by only 970 bytes,
  from 224,059 to 225,029 bytes (`0.4329%`), for the new shared capability metadata. The complete
  bilingual site audited 454 pages and 30 redirects, produced 48,948,648 non-API bytes, and left
  229,566 bytes under the unchanged 46.9 MiB limit. This is 3,491 bytes (`0.0071%`) above the
  Diagnostics slice and is `no material change`. Docusaurus completed in `67.0 s`; the duration
  comparison is `inconclusive` because cache and process state differed, while the absolute result
  remains below the `120 s` limit;
- Graphics Core and Graphics unit tests, Tutorial compilation, exact bilingual region identity,
  Governance V2, documentation structure, release intent, and the complete bilingual site passed.
  Complete `qaQuick` then passed 2,342 actionable tasks in `1 min 35 s` (185 executed and 2,157
  up-to-date); this is completeness evidence rather than a speed comparison because the warm local
  task graph reused previously produced module and site outputs;
- the overall conclusion is `improved`: every application-facing Graphics entry and every public
  module fence now has exact generated ownership without changing public API, production runtime
  behavior, routes, or published artifacts. Release intent found zero release artifacts and
  explicitly ignored the two test-sample-only Graphics artifacts. This slice reuses existing unit,
  renderer, device, visual, and performance evidence and makes no new runtime, visual, power, or
  performance claim. The next action is Gesture Core and Gesture Android ownership.

Accepted Gesture ownership slice on 2026-08-27:

- retained the Gesture Core and Gesture module routes and treated the platform-neutral policy
  kernel as the lower-level implementation of the existing `gesture.modifiers` capability. The
  independent `nested.scroll` capability remains owned by the Gesture module;
- added the previously orphaned `toggleDraggable` entry to `gesture.modifiers` and registered seven
  exact dependency, axis-lock, anchored-settle, combined-click, anchored-drag, and controlled-toggle
  sample decisions. Compiling the controlled-toggle fence exposed the old invalid `-20.dp`
  expression; `(-20).dp` now preserves the intended RTL offset with valid Kotlin syntax. Both
  compatibility notes now name the current `0.1.0-alpha04` line;
- removed five resolved metadata, symbol, and sample exception records. The report changed from 192
  to 182 issues: missing metadata 52 to 50, orphan symbols 81 to 80, and unclassified samples 58 to
  51. The one pre-existing Navigation Core taxonomy mismatch remained; duplicate owners, orphan
  documents, version conflicts, stale generated output, unbaselined debt, and blocking violations
  remained zero;
- the generated Reference retained 531 entries while its normalized catalog grew by only 558 bytes,
  from 225,029 to 225,587 bytes (`0.2480%`), for the additional symbol and module ownership links.
  The complete bilingual site audited 454 pages and 30 redirects, produced 48,951,832 non-API
  bytes, and left 226,382 bytes under the unchanged 46.9 MiB limit. This is 3,184 bytes (`0.0065%`)
  above the Graphics slice and is `no material change`. Docusaurus completed in `27.9 s`; the
  duration comparison is `inconclusive` because cache and process state differed, while the
  absolute result remains below the `120 s` limit;
- Gesture Core and Gesture unit tests, Tutorial compilation, exact bilingual region identity,
  Governance V2, documentation structure, release intent, and the complete bilingual site passed.
  Complete `qaQuick` then passed 2,342 actionable tasks in `37 s` (186 executed and 2,156
  up-to-date); this is completeness evidence rather than a speed comparison because the warm local
  task graph reused previously produced module and site outputs;
- the overall conclusion is `improved`: every discovered Gesture entry and both public module pages
  now have exact generated ownership without changing public API, production runtime behavior,
  routes, or published artifacts. Release intent found zero release artifacts and explicitly
  ignored the two test-sample-only Gesture artifacts. This slice reuses existing unit, renderer,
  device, visual, and performance evidence and makes no new runtime, visual, power, or performance
  claim. The next action is Navigation Core and Navigation Android ownership.

Accepted Navigation module ownership slice on 2026-08-27:

- retained the Navigation Core and Navigation Android module routes and treated the platform-neutral
  graph, transaction, retained-stack, and deep-link model as the lower-level implementation of the
  existing Android-owned `navigation.host` integration capability;
- registered seven exact dependency, graph, two-phase transaction, retained-stack, deep-link, and
  Android-host sample decisions. Navigation Core's pre-existing `src/test/samples` directory was
  not part of its test source set, so the hard cut now makes `compileTestKotlin` own those samples
  instead of accepting an uncompiled fixture. The Core compatibility note now names the current
  `0.1.0-alpha03` line;
- removed five resolved metadata, taxonomy, and sample exception records. The report changed from
  182 to 172 issues: missing metadata 50 to 48 and unclassified samples 51 to 44; orphan symbols
  remained 80. The final taxonomy mismatch fell from 1 to 0, and duplicate owners, orphan
  documents, version conflicts, stale generated output, unbaselined debt, and blocking violations
  remained zero;
- the generated Reference retained 531 entries while its normalized catalog grew by only 372 bytes,
  from 225,587 to 225,959 bytes (`0.1649%`), for the two module ownership links. The complete
  bilingual site audited 454 pages and 30 redirects, produced 48,957,011 non-API bytes, and left
  221,203 bytes under the unchanged 46.9 MiB limit. This is 5,179 bytes (`0.0106%`) above the
  Gesture slice and is `no material change`. Docusaurus completed in `32.0 s`; the duration
  comparison is `inconclusive` because cache and process state differed, while the absolute result
  remains below the `120 s` limit;
- Navigation Core samples, Navigation Android unit-test samples, Tutorial compilation, exact
  bilingual region identity, Governance V2, documentation structure, language and translation
  review, generated Reference freshness, release intent, and the complete bilingual site passed.
  Complete `qaQuick` then passed 2,342 actionable tasks in `1 min 5 s` (187 executed and 2,155
  up-to-date); this is completeness evidence rather than a speed comparison because the warm local
  task graph reused previously produced publication, module, and site outputs;
- the overall conclusion is `improved`: all seven public module fences now have exact compiled
  ownership, the final locale taxonomy mismatch is removed, and no public API, production runtime
  behavior, route, or published artifact changed. Release intent explicitly ignores the two
  test-sample-only Navigation artifacts. This slice reuses existing navigation unit, Android host,
  predictive-Back, adaptive-pane, and device evidence and makes no new runtime, visual, power, or
  performance claim. The next action is UI Foundation, Material 3, and One UI 7 ownership.

| Current area | Required target |
| --- | --- |
| Navigation Guide | archive temporary delivery stages; move transaction/lifecycle invariants to Architecture; replace with focused navigation task Guides |
| Theming Guide | move token/default/design-system ownership to Architecture; retain focused dynamic-color, mode-switching, and local-override tasks as Guides |
| Text Input Guide | move state/bridge/persistence invariants to Architecture; retain editing, rich text, Receive Content, and IME tasks as Guides |
| Lazy Collections Guide | move session/reuse/renderer invariants to Architecture; retain list, grid, pager, state-control, and performance tasks as Guides/Tutorials |
| Focus and Nested Scroll | separate user tasks from propagation, ownership, and Android mapping contracts |
| Shadows | separate application recipes from drawing backend, cache, performance, and diagnostic contracts |
| Overlays | retain concrete presentation tasks; move backend/session invariants to their architecture or module owners |
| Image Loading | preserve as the closest current Guide baseline; add compiled-region ownership and version metadata |
| Modifier Architecture | retain ordering, phase, ownership, and renderer-boundary design; remove the handwritten exhaustive inventory in favor of generated Reference |

1. Add explicit public navigation for Tutorials, Guides, Architecture, Reference, Modules, API,
   Migration, Tooling, and Project content rather than relying on accidental folder order.
2. Preserve every moved route through tested locale-aware redirects.
3. Keep each page focused and link across types rather than copying truth.
4. Update canonical English first, then review the complete Simplified Chinese mirror; never update
   only a translation fingerprint.

Exit condition: every active public page passes the type schema, has one primary owner and purpose,
and no Plan content remains in user-facing Guide or Architecture pages.

### Phase 4: generated DSL and Modifier capability reference

The engine, source-derived model, and destination routes in this phase execute before the broad
Phase 3 content moves under the coordinated order above. Final link closure and phase acceptance
remain after the restructured owners exist.

1. Generate a reference catalog grouped by capability rather than source filename:
   - tree construction and layout;
   - size, constraints, padding, margin, insets, and positioning;
   - appearance, shape, visibility, layer, drawing, and shadows;
   - click, focus, key input, semantics, testing, and overlay anchoring;
   - gesture, nested scroll, animation, shared content, constraints, and Android interop;
   - content, action, input, collection, feedback, navigation, design-system, and integration DSLs.
2. For each entry, derive symbol, overloads, artifact, namespace, released/next state, KDoc, sample,
   module manual, and related Tutorial/Guide/Architecture links.
3. Keep internal and renderer-only extensions out of the application-facing catalog while allowing
   an explicitly separate contributor view when useful.
4. Generate counts from the same model used by the gate; do not commit independent handwritten
   totals.
5. Validate English and Chinese presentation without duplicating the generated API symbol tree.

Exit condition: every public application-facing DSL and Modifier entry has exactly one catalog
owner, zero handwritten inventory drift remains, and adding/removing a source declaration updates
or fails the generated catalog deterministically.

### Phase 5: executable-example migration

1. Correct confirmed stale examples only through owned compiled source, starting with the Material
   theme refresh path and removed ripple terminology.
2. Move Guide, Module, Migration, Tooling, and copy-ready Architecture snippets into small source
   files grouped by capability and version lane.
3. Reuse existing Q3 `@sample` sources where their audience and completeness match; do not create a
   second implementation merely to satisfy Markdown.
4. Keep one focused runnable file per tutorial capability and small reusable compiled regions for
   task/reference examples.
5. Compile released examples only against immutable registered Maven versions and next examples
   against current locally published artifacts with a visible warning.
6. Add representative behavior tests where compilation cannot prove lifecycle, interaction,
   overlay, state restoration, or renderer behavior.
7. Remove the corresponding debt entry immediately when each page migrates.

Exit condition: every active public executable fence is discovered, classified, source-owned,
version-correct, and verified in both locales; only explicitly justified architecture pseudocode
remains non-executable.

### Phase 6: capability tutorials and task-guide coverage

Prioritize current ViewCompose use before the lower-priority second Compose-migration batch.

1. Layout and Modifier families:
   - `Box`, scoped alignment/weight, `Spacer`, `Divider`, Flow, eager scrolling, Scaffold, Card, and
     ListItem;
   - sizing/constraints, physical and relative spacing, insets, shape/background/border, layer and
     visibility, focus/semantics/testing, drawing/shadows, animation/shared content, and interop.
2. Collections:
   - `LazyRow`, `LazyVerticalGrid`, pager, tabs, state control, revision/reuse, and task-focused
     performance guidance.
3. Content and controls:
   - Image/Icon/RichText/Badge;
   - IconButton, Chip, FAB variants, SegmentedControl, Checkbox, RadioButton, Slider, and SearchBar.
4. Feedback and overlays:
   - progress, Snackbar/Toast, Popup/Tooltip/Dropdown, AlertDialog, and ModalBottomSheet.
5. Optional integrations:
   - route installation and task guidance through the owning module for ConstraintLayout, Paging,
     image loaders, media, maps, and camera rather than adding unnecessary beginner Tutorials.

Every new Tutorial remains independently enterable, declares complete Maven Central dependencies
at the top, uses one focused source file, states the expected result and verification action, and
does not require another chapter. Related pages are optional links after the working result.

Exit condition: every capability family has an appropriate learning or task path, the coverage
report has no unexplained high-value gap, and no tutorial grows into a progressive cross-feature
application.

### Phase 7: strict closeout and archive

1. Remove all repaired IDs from the ratchet baseline and prove the baseline is empty.
2. Delete temporary report-only or compatibility paths that are not part of the permanent gate.
3. Run the full documentation, API, sample, site, translation, route, and relevant behavior suites.
4. Move durable conclusions into current governance, architecture, reference, tutorial, guide,
   module, tooling, and workflow owners.
5. Reassess Maven release intent and list every owned immutable Changeset if any phase crossed a
   publication boundary.
6. Archive this plan and update both active and archive indexes only after every completion
   criterion is satisfied.

## Pull-request slicing

The work should remain reviewable and keep the no-new-debt gate ahead of content churn.

1. Governance V2 schema and report-only scanner with regression fixtures.
2. Reviewed debt baseline and blocking no-new-debt ratchet.
3. Automatic tutorial/page discovery and all-code-fence classification gate.
4. Public API/capability ownership and generated Reference gate.
5. Navigation and Theming reclassification with redirects and compiled samples.
6. Text Input, Lazy Collections, Focus, Nested Scroll, Shadows, and Overlays reclassification.
7. Module, Migration, Tooling, and remaining executable-example migration.
8. Layout/Modifier and collection tutorial expansion.
9. Content, controls, feedback, and optional-integration coverage.
10. Empty-baseline strict activation, durable-document closure, and plan archival.

Slices may be divided further, but no content-restructure pull request may precede the blocking
no-new-debt gate, and no later slice may widen the baseline to make progress appear green.

## Hard-cut rules

1. Do not maintain public API or Modifier counts by hand.
2. Do not add an executable Markdown block without source ownership and version-lane verification.
3. Do not mark incomplete copy-ready code as pseudocode to bypass compilation.
4. Do not place delivery stages, branch status, current priorities, or remaining implementation
   scope in Guides or Architecture.
5. Do not duplicate cross-module concepts in module manuals or duplicate generated signatures in
   handwritten pages.
6. Do not require one Tutorial per public symbol; require complete traceability and capability-family
   learning coverage.
7. Do not restore or create a progressive multi-feature tutorial application.
8. Do not expose a next-only API under a released Maven dependency without an explicit version-lane
   transition.
9. Do not use translation fingerprints, broad allowlists, or false `No documentation impact`
   conclusions to bypass semantic review.
10. Do not move or remove a public route without a tested English and Chinese redirect.
11. Do not weaken existing documentation, source-comment, site, release, or quality gates.

## Validation matrix

| Area | Required evidence |
| --- | --- |
| Governance | schema fixtures cover every document type, version lane, sample class, exception field, and ownership failure |
| Public capability | source scan excludes internal/demo/test surfaces; every public DSL/Modifier resolves to one artifact, capability, reference owner, and sample decision |
| Change detection | adding, changing, moving, or deleting representative public symbols fails until the structured documentation impact is resolved |
| Information architecture | every active page has one allowed `doc_type`, required metadata, one primary purpose, and an index/sidebar owner |
| Samples | every executable fence is automatically discovered, exact-source checked in both locales, and compiled in released or next mode |
| Tutorials | every discovered Tutorial declares complete Maven dependencies first, one focused sample, expected result, and verification action |
| Versions | released examples resolve immutable registered versions; next examples use current local publication and display their status |
| Reference | generated DSL/Modifier catalog is deterministic, complete, source-derived, link-valid, and free of handwritten totals |
| Ratchet | exact baseline IDs cannot grow, widen, or return after removal; completion requires an empty baseline |
| Routes/locales | changed pages have reviewed Chinese mirrors, current fingerprints, strict links/anchors, and tested redirects |
| Site | search, accessibility, output-size, JavaScript/CSS, API-tree, and build-time budgets remain within approved limits |
| Repository | affected unit/behavior tests, `verifyDocumentationStructure`, generated API checks, `qaQuick`, release-intent verification, and relevant site builds pass |

## Completion criteria

This plan completes only when all of the following are true:

1. Governance V2 is the active source of truth and defines capability ownership, document types,
   version lanes, sample classes, exceptions, and review responsibility without conflicting legacy
   rules.
2. Every active public page is automatically discovered, correctly typed, indexed, language-valid,
   and owned by one primary purpose.
3. Every application-facing public DSL and Modifier symbol resolves to one generated reference
   owner, artifact/version state, and sample decision; no orphan or duplicate capability remains.
4. Every active public executable Kotlin/Java block is source-owned, exact-region verified in both
   locales, and compiled in its declared lane; every remaining non-executable block is explicitly
   justified architecture pseudocode.
5. Navigation, Theming, Text Input, Lazy Collections, Focus, Nested Scroll, Shadows, Overlays, and
   Modifier content have been split or retained according to the new type contract, with redirects
   preserving public URLs.
6. Core DSL and Modifier capability families have independently enterable tutorials or focused task
   Guides where users need them, while generated Reference/API pages own exhaustive symbol detail.
7. The second Compose-migration batch has not displaced higher-priority Tutorial/Guide coverage and
   is either still explicitly deferred or separately planned after this baseline closes.
8. The no-new-debt baseline is empty and removed; all permanent gates run in blocking strict mode.
9. Existing language, translation, KDoc/Dokka, link, route, accessibility, search, budget, release,
   and Maven sample contracts remain green.
10. Durable conclusions are in current owner documents, publication impact is correctly classified,
    this plan is moved to `docs/archive/`, and both plan indexes are updated.
