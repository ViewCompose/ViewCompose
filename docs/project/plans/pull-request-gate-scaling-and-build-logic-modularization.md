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
last_verified: 2026-08-25
next_action: Create the compiled quality-build included build and parity harness.
maven_release_changesets: []
---

# Pull-request Gate Scaling and Build-logic Modularization Plan

## Status

Active. Phase 0 is complete. The current required-check, task-graph, failure, fallback, and timing
contracts are frozen without changing active Gradle or CI behavior.

Last verified: 2026-08-25.

Next action: create the Phase 1 compiled quality-build included build and parity harness without
changing any existing task name, dependency, failure condition, or branch-protection context. The
coordinated Documentation Governance V2 Phase 0A contract and schema freeze is complete.

## Maven release changesets

- None.

## Release intent rationale

This planning change modifies only active project documentation and owns no published-artifact
production source, publication input, or compiled API sample. Implementation will modify shared
root build inputs and therefore must add one immutable `release/changes/<unique>.json` file with an
explicit `shared` classification unless it also changes a published artifact. Replace `None` above
with every Changeset owned by this plan as soon as the first such implementation change lands.

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

## Execution plan

| Phase | Deliverable | Completion gate | Status |
| --- | --- | --- | --- |
| 0 | baseline, task-graph manifest, required-check contract, failure fixtures | reviewed baseline and reproducible evidence checked in | Complete |
| 1 | compiled quality-build skeleton and parity harness | old and delegated task graphs/failures match | Not started |
| 2 | extraction of architecture, purity, Demo, documentation, device, and benchmark gates | root script contains no long verifier bodies; isolated tests pass | Not started |
| 3 | conservative PR classifier and required-result facade | synthetic diff matrix and branch-protection scenarios pass | Not started |
| 4 | immutable API cache and deduplicated site verification | hit, miss, corruption, tooling-change, and unpublished-source cases pass | Not started |
| 5 | affected-module `qaQuick` execution and selective `qaPreview` | dependency/reverse-dependency golden graph and full fallback pass | Not started |
| 6 | cache-provider cleanup, CI observation, budgets, documentation, and archive | latency and correctness acceptance criteria hold for observation window | Not started |

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

### Phase 3: classify pull-request impact

1. Add a standalone planning entry point that can run from the quality included build without
   configuring all Android projects.
2. Emit selected gate families, directly affected artifacts, dependency closure, reverse-dependent
   closure, reasons, and whether full fallback was selected.
3. Test documentation-only, website-only, module test, module production, sample, Preview, release,
   root-build, deleted/renamed file, large diff, and unknown-path cases.
4. Keep required workflows present for every pull request and add one `always()` result facade per
   required context.
5. Provide manual full override and visible summaries explaining every skipped and selected gate.

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

### Phase 6: stabilize operations and close the plan

1. Remove `cache: gradle` from JDK setup when `gradle/actions/setup-gradle` owns Gradle User Home
   caching; keep pull-request caches read-only and default-branch writes deliberate.
2. Benchmark Gradle build-cache enablement separately. Adopt it only after cacheability, correctness,
   size, hit rate, and eviction behavior are measured.
3. Consider configuration cache only after the extracted typed tasks are compatible; it is not a
   substitute for impact selection or immutable API caching.
4. Observe at least ten successful runs in every enabled change class and record P50, P95, cache hit
   rate, full-fallback rate, and correctness findings in the owning active workflow/site documents.
5. Update English and Simplified Chinese documentation, resolve the `qaPreview` required-check
   documentation/configuration mismatch, move durable decisions out of this plan, and archive it.

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
