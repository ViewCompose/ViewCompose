---
draft: true
schema_version: 2
document_id: plan.pull-request-gate-scaling
doc_type: plan
owner:
  kind: project
  id: quality-build
version_lane: version-agnostic
capability_ids: []
artifact_ids: []
sample_ids: []
status: active
scope: Scale pull-request gates and move repository quality logic into compiled ownership.
non_goals:
  - Weaken existing required checks or deployment integrity.
baseline: Phase 0 contracts and timing evidence are frozen at main revision 74f6d452.
ordered_work:
  - Create compiled ownership and parity.
  - Extract gates, classify impact, cache immutable API output, and verify affected modules.
completion:
  - Meet the accepted correctness and latency criteria and archive the plan.
last_verified: 2026-08-26
next_action: Continue Governance V2 content pull requests as hosted observations and optimize measured pipeline stages before considering website-stack changes.
maven_release_changesets:
  - release/changes/20260825-quality-build-phase1.json
  - release/changes/20260825-quality-build-phase2-architecture.json
  - release/changes/20260825-quality-build-phase2-purity.json
  - release/changes/20260825-quality-build-phase2-demo.json
  - release/changes/20260825-quality-build-phase2-sample-docs.json
  - release/changes/20260825-quality-build-phase2-documentation.json
  - release/changes/20260825-quality-build-phase2-device-benchmark.json
---

# Pull-request Gate Scaling and Build-logic Modularization Plan

## Status

Active. Phases 0 through 5 are complete. Phase 6 operational stabilization and hosted observation
are in progress.

Last verified: 2026-08-26.

Next action: continue Governance V2 content pull requests as hosted shadow observations and target
measured source/API pipeline stages before considering website-stack changes. Build-cache and
configuration-cache probes are complete but neither option is enabled; adoption requires evidence
beyond the accepted local samples. The Phase 0
task-graph fixtures remain the immutable pre-extraction comparator rather than being rewritten for
later intentional gates.

## Maven release changesets

- `release/changes/20260825-quality-build-phase1.json`
- `release/changes/20260825-quality-build-phase2-architecture.json`
- `release/changes/20260825-quality-build-phase2-purity.json`
- `release/changes/20260825-quality-build-phase2-demo.json`
- `release/changes/20260825-quality-build-phase2-sample-docs.json`
- `release/changes/20260825-quality-build-phase2-documentation.json`
- `release/changes/20260825-quality-build-phase2-device-benchmark.json`

## Release intent rationale

Phase 1 modifies shared root build inputs only to make the repository-only quality plugin available
and configure its explicit inputs. Phase 2's accepted extraction slices change the root build only
to replace thirty repository-only task bodies or command registrations with compiled build logic.
Their names and lifecycle
dependencies are unchanged. The Changesets classify shared build inputs and declare no artifact
release. The new Gradle types remain build implementation rather than published artifact APIs;
application-facing capability IDs, Q levels, compiled API samples, and module-manual changes are
not applicable. Phase 3 changes repository-only planning code and workflow orchestration; the
release-intent verifier detected zero release artifacts, ignored artifacts, or shared publication
paths, so it adds no Maven Changeset. Phase 4 changes repository-only documentation generation,
cache state, publishing-build task wiring, and CI orchestration without changing a published
artifact or publication input; its release-intent verification likewise detects zero release
artifacts, ignored artifacts, or shared publication paths, so it adds no Maven Changeset. Phase 5
changes only repository quality-build implementation, workflow orchestration, and workflow
documentation. It changes no published artifact, publication input, or compiled public sample;
release-intent verification again detects zero release artifacts, ignored artifacts, or shared
publication paths, so Phase 5 also adds no Maven Changeset.
Phase 6 cache-ownership and observation work changes only repository workflows and active project
documentation. It does not change a published artifact, publication input, or compiled public
sample, so it likewise adds no Maven Changeset.

## Objective

Reduce pull-request feedback time without weakening merge protection, and move repository quality
gate implementation out of the 2,582-line root `build.gradle.kts` into compiled, testable build
logic with explicit ownership.

The finished system must:

1. preserve the required `qaQuick` and `Build documentation` status contexts on every pull request;
2. select the smallest safe verification scope from the pull-request diff, while treating unknown
   or shared build inputs as full-verification changes;
3. reuse immutable API-documentation output only when an exact, verified fingerprint matches;
4. keep full verification on `main`, scheduled runs, explicit full requests, and sensitive tooling
   or release-history changes;
5. retain stable local entry points including `qaQuick`, `qaPreview`, `qaFull`, `qaRelease`, and
   `verifyDocumentationStructure`; and
6. leave the root build script as high-level configuration and orchestration rather than a home for
   file scanners, process control, policy parsers, and long `doLast` implementations.

## Baseline and problem statement

### Pull-request latency

The documentation-only pull request
[#151](https://github.com/ViewCompose/ViewCompose/pull/151) provides a representative accepted
baseline:

| Required path | Job duration | Dominant step | Dominant duration |
| --- | ---: | --- | ---: |
| CI | `22 min 54 s` | `Run qaQuick` | `22 min 20 s` |
| Documentation | `25 min 37 s` | complete versioned API reference | `21 min 04 s` |
| Preview, currently non-required | `8 min 19 s` | `Run qaPreview` | `7 min 40 s` |

The required critical path was therefore `25 min 37 s` for a pull request that changed three
documentation files and no production or test source.

The current `qaQuick --dry-run` graph expands to 2,899 tasks, including 463 compile, 498 test, 107
publication, and 153 lint task-name matches. `qaQuick` publishes every registered
artifact to the local repository even when the pull request does not change code.

### Documentation reconstruction

The documentation registry currently represents 38 artifacts, 100 artifact/version entries, and
five immutable source revisions. A complete local API assembly occupies approximately `422 MiB`
across 26,803 files.

`website/scripts/assemble-versioned-api-docs.mjs` deletes the entire generated API tree, groups
entries by source revision, and processes those revisions serially. Every revision receives a new
temporary checkout and a separate `--no-daemon` Gradle/Dokka invocation. This is the correct
deployment integrity path, but repeating it from zero for a documentation-only pull request does
not add evidence when the release registry, frozen revisions, maintained documentation tooling,
and unpublished-module inputs are unchanged.

Language and translation verification also runs through `verifyDocumentationStructure`, the
website `typecheck` lifecycle, and the website `prebuild` lifecycle. Fresh runners therefore repeat
the same source checks before the production site build.

### Build-logic concentration

The root `build.gradle.kts` is 2,582 lines and directly registers more than thirty repository gates
and entry points. Its long task bodies currently own several unrelated responsibilities:

| Responsibility | Representative gates |
| --- | --- |
| module architecture | package roots, namespaces, dependency layers, design-system isolation |
| runtime purity | runtime, navigation, gesture, graphics, Preview core/runner/plugin/host boundaries |
| Demo policy | release-tooling APK, selectors, localization resources, visible copy |
| public documentation | scripts, language, translations, structure, DSL API contracts, samples |
| device and benchmark tooling | connected-device preflight, report scripts, benchmark aliases |
| aggregation | `qaQuick`, `qaPreview`, `qaFull`, and release/benchmark entry points |

This structure makes a policy change hard to unit-test without configuring the complete Android
repository, hides ownership, and increases the chance that a latency optimization accidentally
changes a correctness contract.

## Scope boundary

### In scope

- compiled and testable ownership for root quality-gate implementation;
- stable task aliases and parity checks while logic moves;
- pull-request change classification with conservative full-gate fallback;
- affected-artifact and reverse-dependent verification planning;
- job-level conditional execution that always reports required contexts;
- exact-fingerprint caching and cache-miss fallback for immutable API history;
- removal of redundant documentation verification and overlapping Gradle cache providers;
- timing, cache-hit, task-selection, and fallback telemetry; and
- current English and Simplified Chinese workflow/documentation-site guidance.

### Out of scope

- weakening an existing module boundary, purity rule, release-intent rule, documentation rule, or
  snapshot acceptance rule;
- removing full `main` verification or making deployment depend on partial API history;
- trusting filename globs without tested default-full behavior;
- introducing a remote build-cache service or paid CI infrastructure in the first rollout;
- changing Maven coordinates, artifact APIs, runtime behavior, or benchmark workloads; and
- treating cache restoration as evidence without verifying its fingerprint and output manifest.

## Target architecture

### Compiled quality build

Create a non-published `tools/viewcompose-quality-build` included build, following the repository's
existing compiled `tools/viewcompose-publishing-build` precedent. It owns task types, parsers,
verification policies, diff planning, and unit fixtures. The root project applies one quality-root
plugin and supplies only repository-specific declarations that cannot be derived safely.

The intended ownership is:

| Owner | Responsibility |
| --- | --- |
| root `build.gradle.kts` | plugins, concise module declarations, high-level aliases, exceptional wiring |
| quality included build | gate task types, policy models, scanners, failure messages, affected-task planning |
| publishing included build | Maven metadata, dependency contracts, release intent, release planning and upload |
| `website/scripts` | site generation, localization, rendered-site and immutable-output verification |
| GitHub workflows | runner setup, invoking a generated verification plan, required-result aggregation |

Do not replace the monolith with uncompiled `apply(from = ...)` fragments. Extraction is complete
only when the moved logic has typed inputs/outputs, deterministic fixtures, and isolated tests.

### Stable verification facade

Existing documented task names remain compatibility entry points. Their implementation delegates
to typed tasks or lifecycle tasks from the quality included build. GitHub branch protection keeps
the exact `qaQuick` and `Build documentation` context names.

Required workflows continue to trigger for every pull request. A classification job may skip
expensive child jobs, but a final `always()` aggregation job must fail when any selected child gate
fails and succeed only when all required selected work succeeds. Workflow-level path filtering is
not allowed for required checks because an omitted workflow can leave the context pending.

### Change classification

The classifier compares the reviewed pull-request base with the tested head and emits a
machine-readable plan and human-readable job summary.

| Change class | Minimum pull-request verification |
| --- | --- |
| documentation prose and reviewed locale mirrors only | structure, language, translation, site type-check/build using verified API history |
| website presentation or site-quality tooling | documentation source gates, website script tests, type-check, production site build |
| one module's tests or implementation | owning module plus required dependency and reverse-dependent compile/test closure |
| published production source or compiled samples | affected closure, release intent, API documentation audit for affected artifacts |
| Preview/rendering/snapshot inputs | affected closure plus `qaPreview` |
| release registry or documentation-history tooling | complete historical API reconstruction and production site build |
| root/included build, version catalog, settings, classifier, workflow, or unknown path | complete current `qaQuick`; complete documentation gate when its inputs may be affected |
| `main`, scheduled, manual full request, or explicit full override | complete gates regardless of diff classification |

Draft pull requests may use the same light classification path, but becoming ready for review must
trigger the selected merge-grade gates for the current SHA. The rollout must prove that an older
draft success cannot satisfy a newer required execution before draft deferral is enabled.

### Immutable API cache

The cache fingerprint must cover at least:

- the documentation release registry and publishing metadata;
- every selected immutable source revision;
- current maintained publishing/documentation build logic and dependency-contract projection;
- Gradle wrapper, Java/Dokka-relevant versions, and API assembly/verification scripts; and
- working-tree source and build inputs for unpublished artifacts.

The workflow must pin the complete Java and Node distribution versions covered by the fingerprint.
Floating major selectors are forbidden because two runners may otherwise produce different
generator fingerprints for the same repository inputs.

Pull requests restore only exact default-branch entries and never write a broadly reusable cache.
After restoration, the existing complete manifest, route, alias, and source-link verification still
runs. A missing, corrupt, partial, or mismatched entry falls back automatically to full generation.
Cache misses may generate per revision with bounded parallelism and save independently reusable
revision outputs.

## Coordinated execution with Documentation Governance V2

This plan supplies the compiled ownership and pull-request execution model required by
[`documentation-system-governance-v2.md`](documentation-system-governance-v2.md). The two plans
must not be implemented independently or completed one after the other. Use this dependency order:

1. complete this plan's Phase 0 before any new documentation gate changes the accepted task graph;
2. freeze the Governance V2 normative contract and schemas, but defer its report-only scanner;
3. complete this plan's Phases 1 and 2 so existing gates have compiled, parity-tested ownership;
4. implement Governance V2 discovery, debt-baseline, and no-new-debt gates directly in the quality
   included build;
5. establish the generated DSL/Modifier reference core before broad document movement;
6. complete this plan's Phases 3 through 5 against the stabilized Governance V2 gate families and
   generated-reference inputs; and
7. use the subsequent documentation restructure, sample migration, and tutorial pull requests as
   the observation corpus for this plan's Phase 6.

The only intentional overlap is Phase 6 observation. Implementation ownership remains serialized:
the root build must not receive a temporary Governance V2 scanner that will later be extracted.

Governance V2 Phase 2 activates its compiled no-new-debt task directly in the quality included
build. Relative to the frozen Phase 0 comparator, this intentionally adds only
`:verifyDocumentationGovernanceV2` to `verifyDocumentationStructure`, `qaQuick`, and `qaFull`;
`qaPreview` is unchanged. This is the planned step 4 transition, not extraction drift, so the
historical Phase 0 fixtures remain unchanged and the current deltas are interpreted in the
Governance V2 plan.

## Execution plan

| Phase | Deliverable | Completion gate | Status |
| --- | --- | --- | --- |
| 0 | baseline, task-graph manifest, required-check contract, failure fixtures | reviewed baseline and reproducible evidence checked in | Complete |
| 1 | compiled quality-build skeleton and parity harness | old and delegated task graphs/failures match | Complete |
| 2 | extraction of architecture, purity, Demo, documentation, device, and benchmark gates | root script contains no long verifier bodies; isolated tests pass | Complete |
| 3 | conservative PR classifier and required-result facade | synthetic diff matrix and branch-protection scenarios pass | Complete |
| 4 | immutable API cache and deduplicated site verification | hit, miss, corruption, tooling-change, and unpublished-source cases pass | Complete |
| 5 | affected-module `qaQuick` execution and selective `qaPreview` | dependency/reverse-dependency golden graph and full fallback pass | Complete |
| 6 | cache-provider cleanup, CI observation, budgets, documentation, and archive | latency and correctness acceptance criteria hold for observation window | In progress |

### Phase 0: freeze contracts and collect evidence

1. Record the required branch-protection contexts, workflow triggers, task names, task dependencies,
   representative failure messages, and documentation deployment requirements.
2. Capture `qaQuick`, `qaPreview`, and documentation timings for at least ten recent successful
   pull-request runs, separating queue, setup, execution, and post-job time.
3. Persist machine-readable dry-run task graphs for `qaQuick`, `qaPreview`, `qaFull`, and
   `verifyDocumentationStructure` as test fixtures rather than generated release output.
4. Add failing fixtures for every extracted file scanner and policy parser before moving it.
5. Define the full-fallback path list and make an unknown path a tested full-gate case.

Phase 0 completed on 2026-08-25. Its reproducible assets live under
[`tools/viewcompose-quality-build/phase0`](../../../tools/viewcompose-quality-build/phase0):

1. [`required-check-contract.json`](../../../tools/viewcompose-quality-build/phase0/fixtures/required-check-contract.json)
   records the exact `qaQuick` and `Build documentation` branch-protection contexts, workflow
   triggers, stable entry points, and `main`-only deployment contract. `qaPreview` remains observed
   but non-required.
2. The checked-in dry-run graphs contain 2,899 tasks for `qaQuick`, 1,640 for `qaPreview`, 3,098
   for `qaFull`, and four for `verifyDocumentationStructure`. The capture script regenerates them
   through the repository wrapper, and fixture tests reject missing targets or inconsistent counts.
3. [`gate-failure-fixtures.json`](../../../tools/viewcompose-quality-build/phase0/fixtures/gate-failure-fixtures.json)
   gives every file scanner or policy parser scheduled for extraction one concrete failing input and
   its stable failure header. Existing device and performance-tool regression contracts are also
   identified so Phase 1 can build the parity harness without inventing new behavior.
4. [`full-fallback-paths.json`](../../../tools/viewcompose-quality-build/phase0/fixtures/full-fallback-paths.json)
   freezes sensitive shared inputs and verifies that an unrecognized future path selects the full
   gate. It is a safety contract for Phase 3, not active change-selection logic.
5. The timing corpus uses ten recent successful pull-request heads with paired CI and Documentation
   runs. It records queue, setup, execution, post-job, and total time from GitHub timestamps. The
   required critical-path P50 was `24 min 08 s`; nearest-rank P95 was `44 min 49 s`. The P95 total
   includes one `19 min 37 s` `qaQuick` queue delay. Removing queue effects, required execution P50
   was `22 min 43 s` and P95 was `24 min 41 s`.

Interpretation: the baseline confirms a material execution-cost problem in both required gates,
while the total-latency tail is mixed because hosted-runner/concurrency queueing can dominate an
individual sample. Phase 6 comparisons must therefore report both execution and end-to-end
critical paths rather than attributing queue variation to build changes. Ten samples make
nearest-rank P95 equal to the maximum observation, so this corpus is an accepted Phase 0 comparator,
not a stable long-term service-level estimate. The next action is the coordinated Governance V2
Phase 0A schema freeze, followed by this plan's compiled Phase 1 ownership and old/new parity.

### Phase 1: create compiled quality ownership

1. Add `tools/viewcompose-quality-build` with its own settings, build, plugin marker, unit tests, and
   wrapper-independent use through the repository wrapper.
2. Introduce typed inputs for repository root, module catalog, source sets, policy files, and output
   reports; avoid hidden `rootDir` traversal during configuration.
3. Add a parity harness that can execute old and new implementations against identical fixture
   repositories and compare success, normalized diagnostics, and selected paths.
4. Apply the plugin without changing which root tasks run; keep the old body available until the
   corresponding parity slice passes.

Phase 1 completed on 2026-08-25. `tools/viewcompose-quality-build` is now a standalone included
Kotlin build with a plugin marker, explicit repository/catalog/source/policy/report properties,
five isolated tests, a deterministic configuration report, and an environment-normalizing parity
harness that compares success, diagnostics, and selected paths. The root build supplies 44 existing
source roots, one module catalog, four policy inputs, and one build-owned report directory; the
plugin performs no source traversal during configuration and no new task joins a lifecycle gate.

On the same local checkout, fresh dry runs after applying the plugin matched every ordered task in
the Phase 0 fixtures: `qaQuick` 2,899/2,899, `qaPreview` 1,640/1,640, `qaFull` 3,098/3,098, and
`verifyDocumentationStructure` 4/4. The normalized task-set change is zero, so the result is **no
material change** to active task selection. This comparison does not yet prove individual gate-body
or CI timing parity because no gate body moved; those become mandatory per-slice evidence in Phase
2. Next, extract the module/package/namespace/dependency and platform-layer family through this
compiled owner and delete the accepted legacy body in the same pull request.

### Phase 2: extract gate families without behavior changes

Move one family per reviewable change in this order:

1. module/package/namespace/dependency and platform-layer rules;
2. runtime and Preview purity/boundary rules;
3. Demo tooling, selectors, and localization rules;
4. migration, tutorials, documentation structure, language/translation wiring, and DSL contracts;
5. connected-device and benchmark/report tooling; and
6. lifecycle aggregation tasks and stable aliases.

Each move must preserve task names, inputs, ordering, failure semantics, and the dry-run graph. The
root build script target is at most 600 lines, with no repository-scanning `doLast` body and no
embedded process-control implementation. A temporary increase is allowed only while one parity
slice contains both implementations; the old body is deleted in the same slice that accepts parity.

The first family completed on 2026-08-25 as one hard cut. `verifyModulePackageRoots`,
`verifyAndroidModuleNamespaces`, `verifyModuleDependencyBoundaries`,
`verifyDesignSystemIsolation`, and `verifyUiFoundationPlatformBoundary` now belong to typed tasks
in `tools/viewcompose-quality-build`; the five root-script bodies were deleted in the same change.
The consuming build supplies canonical package ownership, retired roots, JVM-module membership,
runtime/tooling classifications, allowed layer directions, settings, module build files, source
roots, and a deterministic dependency-declaration snapshot. Task actions no longer query Gradle
projects.

Six architecture tests cover the five frozen failure cases plus the legacy duplicate diagnostic
for a neutral module that depends on Material 3. They compare success, normalized diagnostics, and
selected paths through the Phase 1 parity harness. The pre-extraction fixture audit also corrected
`foundation-androidx-dependency`: that input belonged to design-system isolation and could never
fail the named platform-boundary scanner. Its replacement imports `android.view.View`, which is an
actual forbidden input for the unchanged platform contract.

The compiled included-build check passed all 11 tests, and all five extracted tasks passed against
the current repository. The complete local `qaQuick` acceptance then passed 2,341 actionable tasks
in `10 min 50 s`. Fresh dry runs matched every ordered Phase 0 task exactly: `qaQuick` 2,899/2,899,
`qaPreview` 1,640/1,640, `qaFull` 3,098/3,098, and
`verifyDocumentationStructure` 4/4. The normalized task-set and ordering change is zero, so the
result is **no material change** to active task selection or accepted gate behavior. This evidence
covers frozen negative fixtures and the current valid repository; it does not enumerate every
hypothetical Gradle syntax, and the single local duration is completeness evidence rather than a
performance comparison.

The second family completed on 2026-08-25 as another hard cut. `verifyRuntimePurity`,
`verifyGestureCorePurity`, `verifyGraphicsCorePurity`, `verifyNavigationCorePurity`,
`verifyPreviewCorePurity`, `verifyPreviewRunnerBoundary`,
`verifyPreviewGradlePluginBoundary`, and `verifyPreviewWorkerHostBoundary` now share one typed
compiled source-boundary task and one pure verifier. Each registration declares its exact source
directory, optional build file, forbidden import prefixes, build markers, and frozen diagnostic
header; task actions do not access a Gradle project or discover repository paths. The eight old
root task bodies were deleted in the same change, reducing the root script from 2,171 to 1,846
lines.

The new parity suite exercises all eight frozen source failures and four build-script marker
groups, including multi-marker ordering for the Preview runner and worker host. The included build
passed all 13 tests, the eight extracted tasks passed against the current repository, and the Phase
0 fixture contract remained valid. The complete local `qaQuick` acceptance passed 2,341 actionable
tasks in `11 min 49 s`. Fresh dry runs again matched every ordered task exactly: `qaQuick`
2,899/2,899, `qaPreview` 1,640/1,640, `qaFull` 3,098/3,098, and
`verifyDocumentationStructure` 4/4. The result is **no material change** to selected work or gate
behavior. This evidence preserves the legacy literal import and build-marker contract; it does not
claim semantic parsing of arbitrary Gradle syntax, and timings are not performance evidence. The
next extraction family is Demo tooling, selectors, and localization.

The third family completed on 2026-08-25 as a hard cut. `verifyDevelopmentToolingIsolation`,
`verifyDemoReleaseToolingApk`, `verifyDemoAutomationSelectors`,
`verifyDemoLocalizationResources`, and `verifyDemoLocalizedVisibleCopy` now belong to five typed
compiled tasks and pure verifiers. The consuming build supplies runtime/tooling source roots, the
Demo build file, a resolved release-component snapshot, the release APK, automation sources,
locale resource directories, and the explicit migrated-source allowlist. Task actions do not query
Gradle projects or discover inputs outside those declarations. The five old root bodies were
deleted in the same change, reducing the root script from 1,846 to 1,415 lines.

Five parity tests preserve the frozen multi-source tooling diagnostic, release-archive marker,
visible-text selector count, localization key/format contract, and visible-copy line diagnostic;
the plugin fixture also proves all five stable task names resolve to their intended compiled task
types. Before acceptance, the Phase 0 tooling, APK, and selector inputs were corrected because
their original `PreviewWorkerHost` and duplicate-`testTag` examples were not rejected by the
unchanged literal scanners; the replacements now exercise `DeviceDslSource`,
`DeviceDslSourceRequestReceiver`, and `By.text` respectively. The included build passed all 18
tests. All five extracted gates passed against the current repository; the optimized Demo release
APK build and marker scan completed successfully in
`3 min 25 s`, and a forced rerun of the other four gates completed in `11 s`. The Phase 0 fixture
suite passed all six tests, while fresh dry runs matched every ordered task exactly: `qaQuick`
2,899/2,899, `qaPreview` 1,640/1,640, `qaFull` 3,098/3,098, and
`verifyDocumentationStructure` 4/4. The normalized task-set and ordering change is zero, so the
result is **no material change** to selected work or accepted gate behavior. The complete local
`qaQuick` acceptance passed 2,341 actionable tasks in `5 min 57 s` on the warmed checkout. Archive
scanning still reads the complete optimized APK and the scanners intentionally preserve literal
marker/regex semantics; the local duration is completeness evidence and this slice does not claim
an incremental speedup or semantic parsing. The next extraction family is migration, tutorial,
documentation, language/translation, and DSL-contract gates.

The migration/tutorial subfamily of the fourth extraction family completed on 2026-08-25 as a
hard cut. `verifyMigrationPairedSamples` and `verifyTutorialSamples` now belong to typed compiled
tasks and pure verifiers. Their registrations declare every compiled sample, canonical and Chinese
page, sample build file, publishing-properties file, page-to-region contract, documentation root,
and expected Maven artifact. The tutorial task now also declares both `getting-started.md` pages
that the legacy action read without registering as Gradle inputs. The two old root bodies and their
configuration-time policy models were deleted in the same change, reducing the root script from
1,415 to 1,009 lines.

Three parity tests preserve migration snippet-drift diagnostics, the tutorial `project()`
dependency failure, tutorial snippet drift, and all selected inputs including `getting-started.md`;
the plugin fixture proves both stable task names resolve to their compiled types. The Phase 0
migration input was corrected because its original `docs/migration/state.md` path was outside the
configured page map and could not reach the legacy scanner; the replacement omits pairs from an
actual governed page. The included build passed all 21 tests, and forced execution of both gates
plus their compiled sample prerequisites passed 152 tasks in `54 s`. Fresh dry-run and complete
graphs again matched every ordered Phase 0 task exactly: `qaQuick` 2,899/2,899, `qaPreview`
1,640/1,640, `qaFull` 3,098/3,098, and `verifyDocumentationStructure` 4/4. The normalized task-set
and ordering change is zero. The complete local `qaQuick` acceptance passed 2,341 actionable tasks
(`2,132` executed and `209` up-to-date) in `6 min 48 s` on the warmed checkout. The result is **no
material change** to selected work or accepted gate behavior. Adding the missing Gradle inputs is a
correctness improvement, while task selection and validation semantics remain unchanged. The local
duration is completeness evidence rather than a speedup claim because caches, daemon state, and
machine load were not controlled. The next subfamily is documentation structure,
language/translation-script wiring, and DSL contracts.

The remaining documentation-governance subfamily completed on 2026-08-25 as a hard cut.
`verifyDocumentationStructure` and `verifyDslApiContracts` now use typed tasks and pure verifiers;
`verifyDocumentationScripts`, `verifyDocumentLanguages`, and `verifyDocumentationTranslations`
retain their existing commands and inputs under compiled plugin registration. The structure task
now explicitly declares root Markdown, active documentation, checked links, catalog metadata, and
top-level directory names that its legacy action read without declaring. All five root
registrations and implementations were deleted, reducing the root script from 1,009 to 546 lines.

Three parity tests preserve the frozen broken-link, missing-KDoc, and renderer-detail diagnostics
and selected inputs. The plugin fixture proves all five stable task names have compiled ownership.
The website wiring regression test now follows that ownership, retains all three structure-task
dependencies, and rejects a root-script re-registration. The included build passed all 24 tests;
the documentation script suite passed all 52 tests; language and translation validation accepted
119 canonical documents and 116 current Chinese mirrors; and forced execution of the complete
structure and DSL family passed 19 tasks in `22 s`. Fresh dry-run graphs again matched every
ordered Phase 0 task exactly: `qaQuick` 2,899/2,899, `qaPreview` 1,640/1,640, `qaFull`
3,098/3,098, and `verifyDocumentationStructure` 4/4. The normalized task-set and ordering change is
zero. The complete local `qaQuick` acceptance passed 2,341 actionable tasks (`2,261` executed and
`80` up-to-date) in `7 min 26 s` on the warmed checkout. The result is **no material change** to
selected work or accepted gate behavior. The additional structure inputs improve Gradle
correctness without changing validation semantics. The local duration is completeness evidence
rather than a speedup claim because caches, daemon state, and machine load were not controlled. The
next extraction family is connected-device and benchmark/report tooling.

The fifth Phase 2 extraction family completed on 2026-08-25 as a hard cut.
`verifyConnectedAndroidDeviceReady` now owns ADB process control in a typed task and delegates
device-list selection plus boot, display, and keyguard interpretation to pure compiled logic.
`benchmarkComparisonReport`, `testBenchmarkComparisonTool`,
`testPagingMacrobenchmarkSummaryTool`, and `testDeviceDiagnosticsRequestMeasurementTool` retain
their commands and stable names under compiled plugin ownership, with their Python implementation,
test, and policy files declared as inputs. Connected Debug Android test wiring moved with the
preflight task. The five root registrations and the device process-control body were deleted,
reducing the root script from 546 to 359 lines.

Five focused tests preserve multiple-device selection, explicit serial selection, offline-device
diagnostics, the Android 7 authoritative-keyguard-field precedence workaround, and combined state
failure ordering. The included build passed all 29 tests. The three Python suites passed 30 tests
through 17 Gradle tasks in `25 s`. A connected Pixel 4 XL selected through `ANDROID_SERIAL` passed
the real online, boot-complete, awake, and unlocked preflight through 15 tasks in `7 s`. This did
not run connected UI tests or a benchmark workload; it verifies only the migrated preflight, while
multi-device and legacy-policy branches remain deterministic unit evidence. Fresh dry-run graphs
again matched every ordered Phase 0 task exactly: `qaQuick` 2,899/2,899, `qaPreview` 1,640/1,640,
`qaFull` 3,098/3,098, and `verifyDocumentationStructure` 4/4. The normalized task-set and ordering
change is zero. The complete local `qaQuick` acceptance passed 2,341 actionable tasks (`2,261`
executed and `80` up-to-date) in `8 min 26 s` on the warmed checkout. The result is **no material
change** to selected work or accepted gate behavior. The local duration is completeness evidence
rather than a speedup claim because caches, daemon state, and machine load were not controlled.
The next extraction family is lifecycle aggregation tasks and stable aliases.

The sixth and final Phase 2 extraction family completed on 2026-08-25 as a hard cut. `qaQuick`,
`qaPreview`, `qaFull`, `qaRelease`, `benchmarkRelease`, and `benchmarkCompare` now belong to the
compiled quality plugin together with the complete `qaQuick` compile/test path list. The ordering
contract that keeps every Maven-coordinate sample task after `publishViewComposeToLocalRepository`
also moved under compiled ownership and remains lazy because the publishing plugin is applied
after the quality plugin. All six root registrations, the path list, and root ordering wiring were
deleted, reducing the root script from 359 to 182 lines. The Phase 2 target is therefore met: the
root script is below 600 lines and contains neither lifecycle task registrations nor embedded
scanner/process-control bodies.

Two focused lifecycle tests preserve stable task metadata, every direct dependency, unique
`qaQuick` paths, lazy included-build test ownership, and both existing and future Maven sample task
ordering. The included build passed all 31 tests. Fresh dry-run graphs again matched every ordered
Phase 0 task exactly: `qaQuick` 2,899/2,899, `qaPreview` 1,640/1,640, `qaFull` 3,098/3,098, and
`verifyDocumentationStructure` 4/4. A detached comparison against the pre-cut `main` revision also
matched the previously unfrozen stable aliases exactly: `qaRelease` 1,255/1,255,
`benchmarkRelease` 1,198/1,198, and `benchmarkCompare` 1,200/1,200. The normalized task-set and
ordering change is zero. Final acceptance also migrated the documentation-wiring regression from
the deleted root registration to compiled lifecycle ownership; all 52 documentation script tests
then passed and the test now rejects any root `qaQuick` re-registration. One subsequent
`qaQuick` attempt reached Android sample packaging before `:samples:counter:packageDebug` reported
an undetailed incremental-packager failure. A focused rerun rebuilt that task successfully, and the
complete final `qaQuick` passed 2,341 actionable tasks (`179` executed and `2,162` up-to-date) in
`16 s`. The result is **no material change** to selected work or accepted lifecycle behavior. This
highly warmed post-retry duration is completeness evidence only, not a speedup measurement. The
next action is Phase 3's conservative pull-request classifier and required-result facade.

### Phase 3: classify pull-request impact

The previously blocking documentation inputs stabilized on 2026-08-26. The compiled quality build
now owns the exact no-new-debt, Tutorial/sample-region, public capability-impact, and generated
Reference freshness gates. Phase 3 can classify these as one explicit documentation-governance
family instead of chasing a changing root task body or a second handwritten Reference inventory.

1. Add a standalone planning entry point that can run from the quality included build without
   configuring all Android projects.
2. Emit selected gate families, directly affected artifacts, dependency closure, reverse-dependent
   closure, reasons, and whether full fallback was selected.
3. Test documentation-only, website-only, module test, module production, sample, Preview, release,
   root-build, deleted/renamed file, large diff, and unknown-path cases.
4. Keep required workflows present for every pull request and add one `always()` result facade per
   required context.
5. Provide manual full override and visible summaries explaining every skipped and selected gate.

Phase 3 completed locally on 2026-08-26. `planPullRequestImpact` is a compiled CLI launched through
the repository wrapper with `-p tools/viewcompose-quality-build`; the accepted real-history probe
configured only four included-build tasks and completed in `734 ms`, without configuring an Android
project. It classified the 21-file Phase 2/Governance merge as full because the diff contained the
quality build and `website/docusaurus.config.ts`, exactly matching the frozen sensitive-input
policy. The plan JSON and Markdown summary contain ten gate families, workflow booleans, direct
artifacts, dependency and reverse-dependent closures, full-fallback reasons, and bounded visible
path reasons. Unknown paths, non-PR events, empty diffs, more than 300 changed paths, and the
`full-verification` label are explicit complete-gate fallbacks.

A second real-history probe compared the 11-file Paging lifecycle commit
`bbd25b448d0a3c85275c119897e5f3d47269451e` with its parent. It reported
`viewcompose-paging-androidx` as the one direct artifact, ten transitive dependencies, and zero
reverse dependents from the current canonical contract. It also selected full fallback solely
because the compliant change contained `release/changes/20260825-paging-mediated-lifecycle.json`.
This is the intentional Phase 3 limit: the frozen `release/**` safety policy remains broader than
append-only Changeset ownership. Phase 5 must prove any split between Changesets and sensitive
release registry/tooling through shadow full-gate comparison before selective module execution can
use the narrower closure for production changes.

The isolated quality build passed all 53 tests in `5 s`; the 12 new classifier tests cover
documentation, website, module test, module production and non-source inputs, sample, Preview,
release, root build, delete, both rename endpoints, large diff, empty diff, manual override,
non-PR events, unknown paths, deterministic serialization, exact artifact ownership, dependency
and reverse-dependent closure, and real Git rename/delete parsing. Documentation scripts passed
56/56, including a repository-wiring test that freezes the `qaQuick` and `Build documentation`
context names, classifier-owned child selection, `always()` facades, skipped-child acceptance, and
selected-child failure propagation. English and Simplified Chinese workflow/site operations now
state that `qaPreview` is visible but non-required, resolving the previous documentation versus
branch-protection mismatch earlier than Phase 6.

Complete selected-work acceptance ran `qaQuick` and `qaPreview` together with a one-run 4 GiB heap
and four workers. It passed 2,344 actionable tasks (`2,264` executed and `80` up-to-date) in
`6 min 44 s`. The production site also passed in `52.2 s`: `412.2 MiB` total,
`46.4/46.9 MiB` non-API, `7.7/8.0 MiB` JavaScript, `650/768 KiB` largest JavaScript,
`112/128 KiB` CSS, 100 immutable API versions, 100 module manuals, 100 Chinese fallback routes,
and 440 audited site pages. The functional conclusion is **improved** because documentation-only
and website-only diffs can now omit Android child work without weakening a required context, while
all current full-fallback work still passes. The measured local duration is **inconclusive** as a
latency comparison because it combines two gates on a warmed checkout, uses four workers instead
of CI's two, and does not exercise hosted-runner queueing or facade scheduling. Phase 6 will use at
least ten live samples per change class; the immediate next action is Phase 4 immutable API reuse.

### Phase 4: reuse immutable documentation safely

1. Add a deterministic fingerprint and manifest for complete API output.
2. Restore exact default-branch output before generation; verify it before the site consumes it.
3. Split immutable output by source revision or another measured boundary so one changed revision
   does not invalidate unrelated history.
4. On cache miss, generate with bounded parallelism selected from measured memory use; never run
   unbounded concurrent Gradle/Dokka processes on one hosted runner.
5. Run language, translation, catalog generation, type-check, and prebuild source validation once
   per job while preserving their individual test coverage.
6. Keep every `main` deployment on the complete verifier and complete production site build.

Local acceptance on 2026-08-26 exercised all five immutable source revisions and 100 registered
artifact/version entries. A cold cache generated all five groups in `411.7 s`, with the complete
Gradle verifier finishing in `6 min 58 s`; an unchanged run verified 26,096 files, reused all five
groups in `2.1 s`, and finished in `5.42 s` (`98.7%` lower). A deliberately changed generated HTML
file invalidated and regenerated only its source-revision group. Unit fixtures also cover a missing
file, extra file, symbolic link, malformed or path-traversing manifest, newly added revision,
generator-tooling drift, pruning, and the main-only save decision. A local process-tree snapshot
reached approximately `1.75 GiB` RSS during the 31-entry group, so the accepted CI concurrency is
`1`; the snapshot is not a hosted-runner peak measurement. The conclusion is **improved** locally.
The phase remains open until a successful complete `main` child saves the cache and a subsequent
hosted child reports an exact verified hit. The first complete `main` child assembled five groups in
`1139.4 s`, completed its API step in `21 min`, saved a `39.3 MB` cache, and deployed. After cache
index propagation, an exact rerun restored it in `7 s`, reused `5/5` groups with zero generation or
invalid groups in `5.7 s`, and completed the API step in `2 min 9 s` (`89.8%` lower). That run then
exposed a separate cold-path side-effect dependency: the versioned-manual generator assumed API
reconstruction had already fetched unreachable frozen revisions. The generator now resolves each
unique exact SHA itself before reading snapshots. The first correction run also proved that
floating JDK major selection was unsound: Temurin `17.0.20+1` differed from the seed's
`17.0.20+8`, so the actual-runtime
fingerprint correctly forced a cold `1175.9 s` reconstruction. Catalog generation and the complete
site build passed, but this did not exercise the hot path. Documentation CI now pins Temurin
`17.0.20+8` and Node `24.19.0`. The pinned hosted rerun restored the exact `cb67…/ab01…` seed in
approximately `4 s`, reused `5/5` groups with zero generation or invalid groups in `5.5 s`, and
completed the API step in approximately `1 min 58 s`. Versioned manual generation then completed
in approximately `1 s` without cold API reconstruction, and the complete production site job
passed in `6 min 33 s`. The conclusion is **improved** and Phase 4 is complete.

### Phase 5: execute affected Gradle verification

1. Reuse the publication ownership model for published paths and add explicit ownership for tests,
   Demo, benchmarks, samples, integration tests, and shared tooling.
2. Derive affected tasks from the current project dependency graph, not from a hand-maintained list
   of presumed callers.
3. Run compile/test/API-audit work for the selected closure and local publication only where a
   Maven-coordinate consumer requires it.
4. Run `qaPreview` only for Preview, snapshot, renderer, graphics, or declared transitive inputs.
5. Compare selective and full outcomes during a shadow period; any selective success/full failure
   is a classifier defect that blocks rollout and becomes a regression fixture.

Phase 5 implementation was locally accepted on 2026-08-26 against the real historical Paging
change `c6a3a2bc8949c2583323208522b2c93d05faede3..bbd25b448d0a3c85275c119897e5f3d47269451e`.
The classifier scoped its 11 changed paths to one direct artifact and ten dependency artifacts,
with no reverse dependents, and treated the newly added Changeset as append-only intent. The root
build independently reconstructed the configured project graph, matched both supplied closures,
and passed 39 selected task paths in `2 min 6 s`; Gradle reported 215 actionable tasks, with 188
executed and 27 up-to-date. The same checkout then passed complete `qaQuick` in `8 min 5 s`; Gradle
reported 2,342 actionable tasks, with 2,096 executed and 246 up-to-date. The candidate duration was
`74.0%` lower in this comparison, so the local execution-work conclusion is **improved** and the
correctness conclusion is matching success. Limitations: this is one developer-machine sample,
the candidate ran before and partially warmed the complete gate, and both runs inherited local
caches. Therefore hosted latency remains **inconclusive** until the Phase 6 corpus is collected;
the next action is to merge the shadow infrastructure and observe scoped pull requests without
removing the complete required-path comparison.

Pull request #173 supplied the hosted full-fallback acceptance on 2026-08-26. Because it changed
the classifier, quality build, and CI workflow, the planner selected complete fallback and the
affected candidate was skipped exactly as required. Complete `qaQuick` passed in `19 min 37 s`,
`qaPreview` passed in `8 min 41 s`, and complete documentation work passed in `5 min 12 s`; every
required facade also passed and the pull request merged as `a7e94e8e`. Against the immediately
preceding accepted pull-request baseline, `qaQuick` changed from `19 min 39 s` to `19 min 37 s`
(`-0.17%`) and `qaPreview` from `8 min 42 s` to `8 min 41 s` (`-0.19%`), both **no material
change**. Documentation changed from `6 min 6 s` to `5 min 12 s` (`-14.8%`), but immutable-cache
state and changed inputs differ, so its latency conclusion is **inconclusive**. This proves the
hosted full-fallback and result-facade path without weakening existing checks. Together with the
local real-history scoped comparison and graph-closure fixtures, the correctness conclusion is
**improved** and Phase 5 is complete. The single full-fallback sample is the limitation; scoped
hosted latency and correctness frequency remain Phase 6 observations, not Phase 5 rollout claims.

### Phase 6: stabilize operations and close the plan

1. Remove `cache: gradle` from JDK setup when `gradle/actions/setup-gradle` owns Gradle User Home
   caching; keep pull-request caches read-only and default-branch writes deliberate.
2. Benchmark Gradle build-cache enablement separately. Adopt it only after cacheability, correctness,
   size, hit rate, and eviction behavior are measured.
3. Consider configuration cache only after the extracted typed tasks are compatible; it is not a
   substitute for impact selection or immutable API caching.
4. Observe at least ten successful runs in every enabled change class and record P50, P95, cache hit
   rate, full-fallback rate, and correctness findings in the owning active workflow/site documents.
5. Update remaining English and Simplified Chinese documentation, move durable decisions out of
   this plan, and archive it. The `qaPreview` required-check documentation/configuration mismatch
   was already resolved in Phase 3.

The first Phase 6 slice assigns Gradle User Home caching exclusively to
`gradle/actions/setup-gradle`. It removes every `actions/setup-java` `cache: gradle` declaration
from CI, documentation, Preview, and Demo APK jobs and makes every `setup-gradle` use explicitly
read-only outside the repository default branch. This preserves deliberate default-branch writes,
prevents pull-request cache writes, and removes two cache implementations from the same Gradle
home. Workflow regression coverage rejects either duplicate ownership or an implicit write policy.
Build-cache and configuration-cache adoption remain separate measured decisions rather than being
bundled into the ownership cleanup.

Pull request #174 accepted the cache-ownership slice on hosted runners. Complete `qaQuick` passed
in `18 min 43 s`, `qaPreview` in `6 min 52 s`, documentation work in `5 min 10 s`, and the Demo APK
build in `5 min 15 s`; every result facade passed and the pull request merged as `9666f669`.
Relative to #173, `qaQuick` changed by `-4.59%`, documentation by `-0.64%`, and `qaPreview` by
`-20.9%`. Correctness is **improved** because one cache owner covered every workflow without a
failure. The timing conclusion is **mixed**: `qaQuick` and documentation show no material change,
while the Preview reduction is substantial but cannot be attributed to cache ownership from one
sample with different restored state. The next action is observation rather than further cache-
owner changes.

A separate 2026-08-26 local Build Cache probe used an isolated Gradle home and the accepted
historical Paging candidate: 39 selected task paths over one direct and ten dependency artifacts.
The dependency/compiler warm-up took `6 min 8.45 s` and was excluded. After `clean`, the
`--no-build-cache` baseline passed in `80.88 s` with 198 executed and 17 up-to-date tasks. The
initial `--build-cache` seed passed in `63.79 s` (`-21.1%`) with 164 executed, 34 from-cache, and 17
up-to-date tasks. Two clean restores passed in `6.04 s` and `5.45 s` (`-92.5%` and `-93.3%`), each
with 90 executed, 108 from-cache, and 17 up-to-date tasks; `50.2%` of actionable tasks were restored.
The isolated cache contained 82 files totaling `9.9 MiB`, and every run produced the same successful
candidate result. The local conclusion is **improved**, but hosted portability, default-branch
population, pull-request restore rate, aggregate size, and eviction behavior remain unmeasured.
Build Cache therefore stays disabled on required paths; a future hosted shadow must meet all of
those criteria before adoption.

The same isolated candidate accepted Configuration Cache with problems set to fail. Its first hot-
output invocation stored an entry in `4.78 s`; an identical invocation reused it in `1.29 s`
(`-73.0%`) with the same successful result. This proves local compatibility for `qaAffected`, not
an end-to-end build improvement. Current CI invokes `qaAffected` only once and then requests the
different `qaQuick` graph, while no encrypted cross-job configuration-cache transport is configured.
The current-path conclusion is **no material change**, so Configuration Cache also stays disabled.
The limitation is that complete `qaQuick` was not probed; there is no reason to expand that probe
until an actual reusable CI invocation exists.

Pull request #176 supplied the first Phase 3 documentation-content observation after cache
ownership stabilized. Its successful documentation child completed in `5 min 21 s`, compared with
`5 min 10 s` for #174 (`+3.55%`), which is **no material change** from one differently restored
sample. Within #176, source and translation verification took `72 s`, complete versioned API
generation and verification took `112 s`, catalog generation `7 s`, type checking `2 s`, and the
final Docusaurus build `53 s`. The website build was therefore `16.5%` of child wall time and was
not the dominant stage. The first #176 attempt failed on a moved Navigation anchor and the strict
link gate rejected it; the corrected rerun passed and auto-merged. Correctness is **improved**
because the required gate caught a real route defect, while latency remains **inconclusive** until
the required change-class corpus exists.

Pull request #177 supplied the second successful documentation-content observation. The classifier
selected documentation governance, documentation site, and samples without full fallback, found no
published artifact, and skipped API documentation, Demo, device/benchmark, integration, module,
Preview, and release-intent families. The affected candidate passed `1,176` actionable tasks in
`6 min 22 s`; the complete shadow passed `2,342` actionable tasks in `10 min 9 s`. Candidate scope
was `49.8%` smaller and its observed duration `37.3%` lower, while both paths reached the same
successful conclusion and produced no correctness finding. The complete shadow reused outputs from
the candidate, so this duration comparison is conservative evidence, not an isolated cold-build
speedup. The serial shadow made the complete `qaQuick` child last `17 min 32 s`; the result is
**mixed**: selection is materially narrower and correct, but observation mode deliberately keeps
the old total work on the critical path. The documentation child completed in `4 min 47 s`, down
`10.6%` from #176; its final Docusaurus build was `46 s` (`16.0%` of child wall time). Keep shadow
comparison active until the required corpus is complete, then remove the complete shadow only if
every change class retains zero missed gate or artifact impact.

Pull request #178 supplied the third successful documentation-content observation. The classifier
again selected documentation governance, documentation site, and samples without full fallback or
a published-artifact impact. The affected candidate passed `1,176` actionable tasks in `5 min 34 s`;
the complete shadow passed `2,342` actionable tasks in `9 min 34 s`. Candidate scope was `49.8%`
smaller and observed duration was `41.8%` lower, with the same successful result and no correctness
finding. Because the complete shadow reused candidate outputs, the comparison remains conservative.
Serial observation made the child last `15 min 55 s`; the conclusion is **mixed** for critical-path
latency and **improved** for selected scope with preserved correctness. The documentation child
completed in `4 min 34 s`, down `4.5%` from #177. Source and translation verification took `65 s`,
complete API generation and verification `82 s`, catalog generation `1 s`, type checking `2 s`, and
Docusaurus `42 s` (`15.3%` of child wall time). Three content samples remain far below the required
ten-per-class corpus, so shadow comparison stays active.

Pull request #179 supplied the fourth successful documentation-content observation. The affected
candidate again selected `1,176` actionable tasks and passed in `5 min 32 s`; the complete shadow
passed `2,342` actionable tasks in `9 min 8 s`. Candidate scope was `49.8%` smaller and observed
duration was `39.4%` lower, with the same successful conclusion and no correctness finding. The
complete shadow reused candidate outputs, so the comparison remains conservative. Serial
observation made the `qaQuick` work child last `15 min 31 s`; the result is **mixed** for current
critical-path latency and **improved** for selected scope with preserved correctness. The
documentation child completed in `5 min 12 s`; source and translation verification took `77 s`,
complete API generation and verification `115 s`, catalog generation `1 s`, type checking `2 s`,
and Docusaurus `53 s` (`17.0%` of child wall time). Four content samples remain below the required
ten-per-class corpus, so keep shadow comparison active and continue collecting the same fields.

The current website stack already uses Docusaurus `3.10.2`, React `19.2.8`, Node `24.19.0`, and npm
`11.8.0`. Replacing or broadly upgrading it is not accepted as a latency action from this evidence:
it would not reduce source verification, Gradle startup, Android SDK preparation, or immutable API
work. The next optimization candidates are verified API-cache reuse, fewer duplicate Gradle
configurations/scans, setup and dependency caching, and avoiding complete-history regeneration
when the immutable fingerprint proves it unnecessary. A later website-stack proposal must measure
the complete documentation child and demonstrate a material end-to-end improvement, not only a
faster isolated Docusaurus invocation.

## Validation matrix

| Area | Required evidence |
| --- | --- |
| extraction parity | old/new fixture results, diagnostics, and dry-run task dependencies match |
| classifier | golden diff matrix; rename/delete/shared/unknown inputs select safe scope |
| affected modules | direct, dependency, and reverse-dependent closures match project graph fixtures |
| required checks | skipped child work still reports one final context; selected failure blocks merge |
| API cache | exact hit accepted; miss/corruption/tooling drift/unpublished-source drift regenerate |
| documentation | structure, scripts, languages, translations, type-check, both-locale build and budgets pass |
| main/deployment | full `qaQuick`, complete API history, complete site, artifact upload and deploy path unchanged |
| performance | timing corpus records queue, setup, execution, cache restore/generation, and total critical path |

## Acceptance criteria

The plan is complete only when all of the following hold:

1. The root `build.gradle.kts` is no more than 600 lines and contains no long verifier body,
   repository scanner, policy parser, or `adb` process implementation.
2. Extracted quality logic is compiled, has isolated unit/fixture coverage, and is owned by a
   documented included build.
3. Existing local task names and required GitHub status contexts remain stable or are migrated in
   one reviewed branch-protection change with no pending-check window.
4. Unknown and shared inputs default to full verification, and `main`, scheduled, manual full, and
   sensitive release/documentation-tooling runs remain complete.
5. No shadow comparison records a selective success followed by a full-gate correctness failure.
6. After warm-up, at least ten documentation-only pull requests achieve required critical-path P50
   no greater than 8 minutes and P95 no greater than 12 minutes.
7. Affected-module pull requests improve required critical-path P50 by at least 40% against the
   Phase 0 comparable baseline without increasing full-main P95 by more than 10%.
8. Eligible documentation pull requests achieve at least a 90% exact immutable-API cache hit rate
   after warm-up; every miss remains a correct full-generation fallback.
9. Full documentation deployment still verifies all registered immutable entries, aliases, source
   links, manuals, locales, accessibility rules, and site budgets.
10. Accepted timing evidence is interpreted in the active development-workflow and documentation-
    site documents with absolute results, normalized change, conclusion, limitations, and next
    action before this plan moves to `docs/archive/`.

## Risks and controls

| Risk | Control |
| --- | --- |
| path classifier misses a dependency | default-full unknown policy, graph-derived closure, shadow full runs |
| required workflow is omitted and remains pending | never path-filter required workflow; always report final facade |
| cache hides broken historical output | exact fingerprint, manifest verification, corruption tests, full fallback |
| extraction changes failure behavior | family-by-family parity fixtures before deleting old implementation |
| parallel Dokka exhausts memory | measured bounded concurrency and per-revision isolation |
| cache churn makes builds slower | one cache owner, PR read-only mode, size/hit/eviction telemetry |
| root script shrinks but complexity merely moves | compiled task types, explicit inputs/outputs, unit tests, ownership docs |
| stale PR base invalidates selective evidence | require up-to-date merge evidence or merge-queue equivalent before rollout |

## Documentation impact

Implementation changes the repository development workflow, documentation-site operations, and
possibly release-intent guidance. Update at least:

- `docs/project/workflow.md` and its Simplified Chinese mirror;
- `docs/project/documentation-site.md` and its Simplified Chinese mirror;
- `docs/project/documentation-governance.md` only if the normative gate contract changes; and
- this plan after every accepted phase, including timing evidence and the next action.

No framework, module, or public API documentation changes are required for the planning-only step.
