---
schema_version: 2
document_id: project.development-workflow
doc_type: project
owner:
  kind: project
  id: development-workflow
version_lane: version-agnostic
capability_ids: []
artifact_ids: []
sample_ids: []
workflow: Define the repository development, validation, pull-request, and handoff sequence.
validation:
  - ./gradlew qaQuick
lifecycle: Update whenever repository development or required-check behavior changes.
---

# Development Workflow

## 1. Scope

This document defines the current ViewCompose collaboration workflow. It addresses two recurring
problems: multi-phase work accumulates unrelated changes, and interrupted tasks need a reliable way
to recover context. Follow this workflow unless a task explicitly requires a different process.

## 2. Small commits

Commit each independently verifiable step as soon as it is complete. A small step has one
describable goal, one validation path, and does not depend on bundling unrelated work.

Examples include a focused plan, a minimal host abstraction, one independent bug fix, one test
group, one Demo page, or one instrumentation regression.

Do not combine unrelated bug fixes or leave planning, broad implementation, and multiple test
groups uncommitted in the worktree for an extended period.

### 2.1 Foundational-instability preemption

An unstable foundational contract, ownership model, or core implementation takes the highest
priority over release-window convenience, the current plan phase, and additive roadmap work. Once
evidence confirms that the design itself is unsound, stop dependent expansion, replace it at the
owning layer, migrate callers, and remove the old API, transport, fallback, and compatibility
branches in one hard cut. Do not defer a confirmed foundational correction to a later phase. A
timing guard, deprecated no-op field, caller-specific wrapper, or parallel legacy path is not an
acceptable substitute for correcting the model. Record the evidence, migration, and release impact
explicitly.

## 3. Documentation synchronization

Before implementation, classify documentation impact with the
[change-impact matrix](./documentation-governance.md#change-impact-matrix). A PR lists every KDoc,
Javadoc, module manual, or cross-module document updated. A `No documentation impact` result states
why public API, behavior, architecture, compatibility, and maintenance workflow are unchanged.

Before adding or changing a public/protected API, assign its Q level and satisfy every applicable
parameter, return, state, lifecycle, threading, failure, and platform contract in the same PR under
the [Source Documentation and API Comment Standard](./api-documentation-quality.md). A Q3 API also
ships a compiled `@sample`. Existing documentation debt cannot justify new debt.

The same change supplies a structured capability-impact record conforming to the Governance V2
[public capability impact contract](./documentation-governance.md#public-capability-impact). Resolve
the stable capability owner and every KDoc, module, sample, Reference, Tutorial, Guide,
Architecture, Migration, and redirect disposition before implementation. Add one immutable record
under `docs/project/records/documentation-governance-v2/impacts/` for each detected structural
capability change. The compiled gate compares it with the exact pull-request base and rejects
missing, reused, stale, duplicated, or mismatched impact ownership.

Update documentation before or together with:

1. a new capability direction;
2. an architecture boundary change;
3. a test strategy;
4. a Demo module plan;
5. a host or container semantic change;
6. resolution of recorded debt, architecture work, or roadmap items.

Start with the owning current document, such as the
[architecture overview](../architecture/overview.md), [roadmap](./roadmap.md),
[theme architecture](../architecture/theming.md), [Modifier architecture](../architecture/modifier.md), or
[NodeSpec specification](../architecture/node-spec.md).

When code resolves a documented problem, update that document in the same or immediately adjacent
commit. Current-problem, remaining-work, and next-step sections cannot lag the implementation.

### 3.1 Release intent for independently published modules

Every pull request must classify Maven release impact before merge. When automatic ownership finds
publication-relevant source, module build metadata, or compiled API sample changes, add one new
immutable `release/changes/<unique>.json` file for the pull request. Use `breaking`, `feature`, or
`fix` for a direct artifact change. Use `ignored` only with a concrete reason when the detected
path does not change the published contract or artifact. Never write `dependency`; the release
planner derives reverse-dependency propagation from the current Gradle project graph.

Test-only, Demo, benchmark, and handwritten documentation changes are release-neutral by default.
Shared root build inputs must either declare the affected artifacts or record a concrete no-release
classification because path ownership alone is insufficient. Changesets are append-only after
merge and remain in the repository as the audit trail. Squash and rebase workflows do not alter
this contract: release intent belongs to the pull request, not each intermediate commit.

Run `./gradlew verifyViewComposeReleaseIntent` locally. It is part of `qaQuick`; CI compares the PR
to its exact base SHA. Release owners use `planViewComposeRelease` and
`prepareViewComposeRelease` as defined in [Publishing](publishing.md#deterministic-independent-release-planning).

## 4. Tests and Demo assets

An implemented capability normally includes, in order, unit tests, a Demo scenario, and required
Demo UI tests. If one cannot be delivered, state what is missing and why in the commit or PR.

### 4.1 Completion commands

1. Fast gate: `./gradlew qaQuick`
2. Preview snapshot gate: `./gradlew qaPreview`
3. Full gate: `./gradlew qaFull`

`qaQuick` compiles core modules, runs unit tests, and enforces the canonical documentation gate,
including language placement and reviewed translation fingerprints. `qaPreview` runs
`:viewcompose-preview:verifyPaparazziDebug` as an independent visual CI check. A visual change may
update committed baselines only after the generated images and differences have been reviewed;
never record an unexplained mismatch merely to make the gate pass.

Run repository Gradle gates on JDK 21. The current build baseline is Gradle 9.3.1, Android Gradle
Plugin 9.1.1, and Kotlin 2.2.10. Published runtime libraries still target Java 11 bytecode. The
repository temporarily retains the explicit Kotlin Android plugin and legacy Android DSL through
`android.builtInKotlin=false` and `android.newDsl=false`; remove both only in one coordinated
convention-plugin/publication migration because AGP 10 removes that opt-out. Preview compiles
against SDK 37. One repository-owned test resource deliberately pins every Android Robolectric
suite to its supported SDK 35 runtime; that test pin does not lower any production compile SDK.

Pull-request workflows first run the standalone `planPullRequestImpact` entry point from
`tools/viewcompose-quality-build`; this configures only that included build, not the Android
multi-project build. The classifier reads the exact base-to-head Git diff, the publishing artifact
catalog, the dependency contract, and the frozen full-fallback policy. Its JSON and job summary list
selected gate families, direct artifacts and non-published projects, transitive dependencies,
reverse dependents, reasons, workflow selection, and full-fallback status. Documentation and
website-only changes can skip Android child work. Module production changes select release,
API-documentation, documentation, module, and any graph-reachable Preview gates; module test, Demo,
sample, integration, and benchmark paths retain their explicit families and project ownership.
More than 300 changed paths, an unknown path, a sensitive shared input, a non-pull-request event,
an empty diff, or the `full-verification` pull-request label selects every current workflow.

An added `release/changes/*.json` file is classified as append-only release intent and can accompany
an otherwise scoped production change. Modifying, deleting, copying, or renaming a Changeset still
selects complete verification, as do release registries and release tooling. This is a hard
separation between immutable intent additions and mutable release infrastructure; it is not a
general relaxation of `release/**`.

The branch-protection contexts remain exactly `qaQuick` and `Build documentation`; `qaPreview` is
visible but is not currently a required context. Each visible context is an `always()` result facade:
it succeeds for intentionally skipped child work only when classification succeeded and selected
false, and it fails when planning fails or selected work does not succeed. Required workflows still
trigger for every pull request, so path filtering cannot leave a required context pending. Every
`main` or manual run selects complete verification; documentation deployment remains possible only
after the complete documentation child and its facade both succeed.

The selected documentation child plans a generator fingerprint and a complete immutable-history
fingerprint before restoring generated API candidates. Pull requests never save this cache; a
successful `main` child is its only writer. A restored key is only a hint: per-source-revision
entry sets and every file size/SHA-256 digest must verify before reuse, while a stale or corrupt
group is deleted and regenerated. The job summary reports hit, partial, miss, recovery, reused and
generated group counts, invalid groups, bounded parallelism, and assembly duration. Source,
language, and translation checks run once through `verifyDocumentationStructure`; CI generates the
site catalog once, then calls the prepared type-check and build entry points to avoid repeating npm
prebuild hooks.

The classifier owns a typed `qaQuick` execution mode in addition to the stable `qa_quick` workflow
selection. `skip` omits Android work, `complete` runs only full `qaQuick`, `affected-with-shadow`
runs `qaAffected` followed by full `qaQuick`, and `affected` runs only `qaAffected`. The workflow
validates the selected mode and both step outcomes before its required `qaQuick` facade can pass;
an unsupported mode or an unexpected executed/skipped outcome fails closed.

A scoped candidate uses a 4 GiB Gradle heap and at most two workers. The root build independently
reconstructs the current
`api`/`implementation`/`compileOnly`/`runtimeOnly` project graph, rejects any classifier closure
drift, and selects compile plus unit-test tasks from the configured projects rather than an artifact
task list. Demo, sample, integration-test, and benchmark ownership selects its project-specific
tasks; local Maven publication is included only for sample consumers. Documentation and Preview
remain owned by their independently visible workflows.

The no-shadow `affected` mode is deliberately narrow. One accepted class requires exactly
documentation-governance, documentation-site, and Tutorial-sample gate ownership,
`:samples:tutorials` as the sole non-published project, no published artifact, and no full-fallback
reason. The second requires the exact documentation-governance, documentation-site,
module-verification, Preview, release-intent, and sample families plus at least one published-module
`src/test/samples` source. Its remaining paths are limited to documentation, current Chinese
mirrors, the generated capability catalog, append-only Changesets, Tutorial main sources, and an
optional Counter debug Preview source. Module production, build scripts, ordinary tests, and
deleted or renamed code stay `affected-with-shadow`, where both candidate and complete gates must
succeed. `main`, manual runs, and every full-fallback pull request use `complete`. Demo,
integration, benchmark, shared-input, unknown, and every unaccepted module class also retain the
complete comparison. This does not change the project-wide local Gradle default.

The 2026-08-26 local acceptance used one real historical Paging diff. `qaAffected` selected 39 task
paths across one direct and ten dependency artifacts and passed in `2 min 6 s` (215 actionable;
188 executed and 27 up-to-date). Complete `qaQuick` then passed in `8 min 5 s` (2,342 actionable;
2,096 executed and 246 up-to-date), so candidate duration was `74.0%` lower and the local
execution-work conclusion is **improved**. Both outcomes matched. This is not rollout latency
proof: it is one developer-machine sample, both paths inherited local caches, and the candidate ran
first and partially warmed the complete gate. Hosted latency therefore remains **inconclusive**;
keep the complete shadow until the Phase 6 change-class corpus satisfies the plan's observation
and correctness criteria.

Pull request #173 supplied the first hosted full-fallback acceptance for this implementation. The
candidate correctly skipped, complete `qaQuick` passed in `19 min 37 s`, `qaPreview` in `8 min 41
s`, and documentation work in `5 min 12 s`; all facades passed. Relative to the immediately
preceding accepted pull request, `qaQuick` changed by `-0.17%` and `qaPreview` by `-0.19%`, both
**no material change**. Documentation changed by `-14.8%`, but different immutable-cache state and
inputs make that latency result **inconclusive**. One full-fallback sample proves the behavior but
not a distribution; the next action remains collection of scoped change-class observations.

Eleven comparable hosted documentation/Tutorial-sample pull requests (#177, #178, #179, #180,
#182, #183, #184, #185, #203, #204, and #205) supplied the accepted no-shadow corpus. Each selected
1,176 actionable candidate tasks, selected no published artifact, and reached the same successful
result as the following 2,342-task complete shadow. Reconstructing the required critical path as
the maximum of candidate completion from job start and the parallel documentation-child duration
gives nearest-rank P50 `6 min 22 s` and P95 `7 min 17 s`; both satisfy the `8 min`/`12 min`
thresholds. All 11 documentation children restored and verified `5/5` immutable API groups with
zero generation or invalid group, a `100%` exact-hit rate. The scope and cache conclusions are
**improved**, and correctness is **no material change** with zero divergence. At acceptance time,
the observed post-cut timing was still **inconclusive** because it was reconstructed from shadow
runs; an actual critical path was still required. This evidence enabled the first exact `affected`
class.

Pull request #225 supplied that class's first real post-cut no-shadow run. Its five-task candidate
covered only `:samples:tutorials`, passed in `6 min 9 s`, and reached the required facade in
`8 min 12 s` from workflow creation; complete work was skipped. This is `72.9%` below the Phase 0
execution P50 and `66.0%` below its end-to-end P50. Scope and latency are **improved** and
correctness is **no material change**. One run does not establish a distribution; nine more
naturally eligible successful runs are required before evaluating this class's P50/P95.

Eleven hosted module-documentation/compiled-sample pull requests (#186, #187, #188, #189, #190,
#191, #194, #195, #198, #199, and #200) supplied the second accepted no-shadow corpus. Each changed
only documentation/governance records, compiled module `src/test/samples`, bounded Tutorial or
Counter sample sources, an append-only Changeset, Chinese mirrors, and the generated capability
catalog. All affected candidates and following complete shadows succeeded with zero divergence.
The reconstructed no-shadow execution path has nearest-rank P50 `8 min 5 s` and P95 `10 min 4 s`;
end-to-end P50 is `9 min 13 s` and P95 is `11 min 18 s`. Execution P50 is `64.4%` below the Phase 0
`22 min 43 s` comparator. All eleven documentation children reused `5/5` immutable API groups with
zero generation or invalid group. A separate eleven-run successful `main` corpus has complete
`qaQuick` job P95 `20 min 41 s`, `16.2%` below Phase 0, so the full path did not regress. Scope,
cache reuse, and latency are **improved**; correctness is **no material change**. Pull request #196
remains `affected-with-shadow` because it changed a module build script, and production source,
ordinary tests, code deletion/rename, sensitive tooling, and unrecognized paths remain outside this
hard cut. At rollout, the first eligible hosted run was still required to record actual post-cut
timing.

Pull request #226 supplied that class's first real post-cut no-shadow run. Its 68 selected tasks
covered 23 published artifacts and `:samples:tutorials`, passed in `7 min 48 s`, and reached the
required facade in `9 min 42 s` from workflow creation; complete work was skipped. The execution
and end-to-end paths are `65.7%` and `59.8%` below Phase 0. Scope and latency are **improved** and
correctness is **no material change** because the selected closure, Preview, documentation, and
facades all passed. The merged `main` revision then passed complete `qaQuick` in `15 min 23 s` with
the work job at `16 min 38 s`, `19.6%` below the accepted full-main P95; the full path therefore
shows **no material change** in safety. One run does not establish a distribution; nine more
naturally eligible successful runs are required before evaluating this class's P50/P95.

The first post-cut control window (#219--#223) produced no eligible no-shadow `affected` run. #219
changed only the active plan and correctly selected `skip`; the `qaQuick` facade completed `70 s`
after workflow creation and the documentation facade completed in `6 min 29 s`, `24 s` (`-5.8%`)
below the comparable #216 skip observation, a **no material change** latency result with matching
success. #220--#223 correctly selected `complete` for shared quality/site tooling, publishing build
logic, and release or documentation-history metadata. Their complete Gradle `qaQuick` steps were
`13 min 2 s`, `16 min 53 s`, `16 min 11 s`, and `15 min 2 s`; nearest-rank P50/P95 are
`15 min 2 s`/`16 min 53 s`, `33.8%`/`31.6%` below the Phase 0 execution comparator. Required
end-to-end critical paths were `15 min 15 s`, `24 min 33 s`, `18 min 3 s`, and `16 min 55 s`, so
P50/P95 are `16 min 55 s`/`24 min 33 s`, `29.9%`/`45.2%` below Phase 0. Every gate and required
facade passed. Safety remains **no material change** and the small heterogeneous complete-path
sample is **improved** relative to Phase 0; actual no-shadow latency remains **inconclusive** because
the window contains no target-class run. These five same-day, release-heavy changes are control
evidence rather than a representative distribution. Wait for a naturally eligible documentation/
Tutorial-sample or module-documentation/compiled-sample pull request to extend each post-cut corpus;
do not alter samples only to produce an observation.

`gradle/actions/setup-gradle` is the sole owner of Gradle User Home caching in every workflow that
invokes Gradle. `actions/setup-java` installs the required JDK but does not separately cache
Gradle. Every `setup-gradle` use explicitly sets `cache-read-only` for any ref other than the
repository default branch: pull requests and non-default branches can restore entries, while only
default-branch jobs may write them. Do not add a parallel `actions/cache` Gradle-home entry or
restore `setup-java` `cache: gradle`; optional Gradle build-cache and configuration-cache adoption
requires separate measured acceptance.

The first isolated Paging candidate probe found Build Cache promising but not ready for required
CI. With dependencies prewarmed and outputs cleaned, the no-cache baseline passed in `80.88 s`;
clean cache restores passed in `6.04 s` and `5.45 s` (`-92.5%` and `-93.3%`) with 108 of 215
actionable tasks restored from a `9.9 MiB` cache. The result was correct on every run, so the local
conclusion is **improved**. Hosted portability, main-to-PR reuse, aggregate size, and eviction are
unmeasured, so Build Cache remains disabled. Configuration Cache also remains disabled: the same
candidate stored and reused locally in `4.78 s` and `1.29 s` (`-73.0%`), but current CI has no
second identical invocation and no configured encrypted cross-job transport. Its current-path
conclusion is **no material change**. Revisit either option only through a separate hosted shadow;
do not combine adoption with unrelated gate or documentation work.

`qaFull` adds the application, Counter sample, and tutorial connected tests to `qaQuick`. Every
repository `connectedDebugAndroidTest` entry first runs `verifyConnectedAndroidDeviceReady`. The
preflight requires exactly one online device unless `ANDROID_SERIAL` selects one, completed boot,
an awake display, and no showing keyguard. It deliberately does not bypass a secure lock screen:
wake and unlock the selected device before retrying. Before marking a capability complete,
`qaFull` normally passes; a missing device or temporary exemption is recorded in the roadmap with
scope and deadline.

### 4.2 Remote Demo APK

Maintainers can run the `Demo APK` workflow from the GitHub Actions page when an installable build
is needed away from a development machine. The workflow builds the debug-signed `app` APK from the
selected Git ref and uploads it with a SHA-256 checksum and build metadata for 14 days.
The artifact is intended for manual framework verification and is not a release package.

## 5. Code ownership and placement

Choose the owning module and directory before creating a file. Do not flatten new code into the
nearest directory or mix platform, DSL, runtime, and Demo responsibilities.

Decide in this order:

1. layer and module ownership: Kernel, UI Foundation, Android Engine, Design System, Integrations,
   aggregate, tooling, or app;
2. directory ownership, for example `context/`, `dsl/`, `runtime/`, `view/`, or `defaults/`;
3. file name.

Read the relevant architecture and neighboring module code before implementation. If working code
has an obviously wrong home, correct the structure in the current change. Module and directory
ownership are required review items.

### 5.1 Anti-flattening

1. A source directory should contain at most 12 files; split by responsibility above that size.
2. Split by domain or component family, not author or temporary phase.
3. Directory movement updates the architecture directory baseline in the same change.
4. Directory movement does not change public API by default. A required package/API change is a
   separate commit with migration guidance.

### 5.2 Environment sources

1. Host environment semantics come from `viewcompose-ui-foundation/context/Environment` and
   `UiEnvironment`.
2. Android extraction enters `UiEnvironmentValues` through `AndroidEnvironmentBridge`.
3. Renderer does not create another semantic channel; it uses only internal platform conversion in
   `viewcompose-renderer-android/view/DimensionUtils.kt`.
4. Renderer containers do not add private density caches or dp/sp conversion helpers.
5. Correct existing divergence and update documentation in the same step.

#### 5.2.1 Lifecycle and ViewModel APIs

1. `collectAsState/collectAsStateWithLifecycle` belongs to `:viewcompose-lifecycle-androidx` under
   `com.viewcompose.lifecycle`.
2. `viewModel/savedStateHandle` belongs to `:viewcompose-viewmodel-androidx` under
   `com.viewcompose.viewmodel`.
3. Default host Local injection belongs to the `viewcompose-host-android` bridge and is not
   duplicated in those modules.

### 5.3 Root-scoped integration assembly

Normal application roots select integrations explicitly. SPI discovery is reserved for low-level
neutral extensions, and reflection is a separately reviewed last resort.

1. Activity, Fragment, navigation, and named design-system roots construct their root-scoped
   overlay host explicitly; classpath order may not select a design system.
2. `AndroidOverlayHostFactoryProvider + ServiceLoader` remains only for custom low-level hosts and
   discovers exactly one neutral `viewcompose-overlay-android` provider. Material and One UI
   adapters register no whole-host provider.
3. Optional decoration uses `AndroidViewDecorationBackend + ServiceLoader`; renderer and host do
   not depend on the shadow implementation, and absence is a no-op.
4. Temporary reflection includes architecture documentation, contract tests, and a removal plan.

### 5.4 Local API consistency

1. Public Local APIs are `uiLocalOf`, `UiLocals.current`, `ProvideLocal`, and `ProvideLocals`.
2. Do not add specialized `ProvideXxx` wrappers.
3. Local mechanism changes add snapshot, Lazy, and overlay propagation regression.
4. Converge an old specialized wrapper in the same cycle and update documentation.

### 5.5 NodeSpec-only semantics

1. New semantic fields belong only in `NodeSpec` or `Modifier`, never dynamic `Props`.
2. Do not add or restore `Props/TypedPropKeys/PropKeys/node.props`.
3. A renderer binder reads an explicit spec and cannot silently fall back to a default spec.
4. Additional metadata uses a modifier element or explicit spec field, never an implicit map.
5. Update [NodeSpec-only](../architecture/node-spec.md) and its guards.

### 5.6 Node-group recomposition stability

1. Keep sibling `emit` group keys and order stable; use explicit keys for loops and conditionals.
2. If stability is impossible, accept nearest-stable-ancestor fallback and test it.
3. Structure drift remains observable; do not suppress warnings or exceptions.
4. Changed `emit(spec/modifier)` inputs mark the group dirty.
5. Add a Runtime/UI Foundation test for group reuse and fallback.

### 5.7 Snapshot consistency

1. `MutableState` writes use explicit `MutableSnapshot` or autocommit; no bypass path is allowed.
2. Mutation equivalence and concurrent merging use `SnapshotMutationPolicy`, not scattered caller
   comparisons.
3. Test no-conflict, merge-success, and merge-failure cases.
4. Test read consistency within one composition pass.
5. Update [State snapshots](../architecture/state-snapshots.md) with semantic changes.
6. During composition, a value written to mirror state and read back cannot control coroutine
   launch, scheduling, or version selection. Read the live kernel field and add regression coverage.

### 5.8 Composition transactions and structured coroutines

1. Composition uses prepare/commit/abort; renderer failure cannot commit slots, observations, or
   effects.
2. `DisposableEffect`, `SideEffect`, and `LaunchedEffect` start only after commit.
3. A failed candidate calls `RememberObserver.onAbandoned`, not `onForgotten`.
4. Application-visible async work belongs to the RenderSession composition Job; no independent
   `CoroutineScope(SupervisorJob())` root.
5. A custom context can override non-Job elements only; a supplied Job fails fast.
6. Coroutine tests cover key restart, conditional removal, failed composition, Session disposal,
   and child failure isolation.
7. Renderer transaction tests cover sibling and recursive failure, new-node release, old View order,
   and binding restoration.
8. `AndroidView.update/onReset/nativeView` is replay-safe and changes only its View. External
   non-replayable work uses post-transaction `onCommit`.
9. Transaction journals scale with touched scopes and mutated nodes, not the whole tree.
10. Fast-path changes verify stable VNode/List identity, SkipSubtree without child traversal, and no
    deep structure statistics while diagnostics are off.
11. Duplicate-invalidation optimization preserves next-frame work created during composition and
    merges repeated same-frame writes.
12. Ordinary Kotlin captures inside `RecomposeBoundary` are declared through `inputs`; snapshot
    state is observed directly.
13. Every new failure path maps to a structured `RenderFailure` stage/recovery result and does not
    block later commit callbacks or cleanup.

### 5.9 Frame-aligned scheduling

1. State invalidation uses `FrameAlignedRenderDispatcher + Choreographer`, not `container.post`.
2. `RenderSession.render()` remains immediate unless architecture and tests change first.
3. Dispatcher tests cover same-frame merge, cancellation, reentrant next-frame work, and
   cross-thread deduplication.
4. Instrumentation that waits for UI idle also waits at least one frame.
5. Session disposal guarantees no delayed render afterward.

### 5.10 Renderer registration single source

1. `NodeType -> binder`, `NodeViewPatch -> applier`, and `NodeSpec -> patch factory` mappings live
   only in `NodeBinderDescriptors`.
2. Do not add parallel maps in `NodeViewBinderRegistry` or `NodeBindingDiffer`.
3. Add a descriptor before binder/patch code for a new node.
4. Run descriptor guards after changes.
5. `NodeBinder*.kt` lives under `view/tree/binder/core/descriptor/`, not the core root.
6. Restore a regressed directory and add a structural guard in the same commit.

### 5.11 Module dependencies

1. Register every runtime module exactly once as Kernel, UI Foundation, Android Engine, Design
   System, Integration, or the explicit consumer aggregate; register Tooling separately.
2. Dependencies may point to the same or a lower allowed layer only. Tooling never enters a
   published runtime dependency, and no `viewcompose-*` module depends on `app`.
3. UI Foundation production sources cannot import Renderer, AndroidX, or Material APIs. UI Contract
   production sources cannot import `android.*` or `androidx.*`.
4. Neutral `ComponentActivity/Fragment.setUiContent` lives in `viewcompose-android`; named
   `setMaterial3UiContent` lives in `viewcompose-material3-android`; `renderInto` and
   `AndroidView/nativeView` remain in the low-level `viewcompose-host-android` engine.
5. Material theme policy lives in `viewcompose-material3`, while Material Activity/Fragment and
   presentation wiring lives only in explicitly named integrations. UI Foundation, Renderer
   Android, Host Android, and the neutral Android aggregate cannot import or depend on Material.
6. `verifyModuleDependencyBoundaries` and `verifyDesignSystemIsolation` in `qaQuick` are
   non-waivable gates. Guard tests enforce these boundaries; review convention alone is
   insufficient.
7. Classify each published dependency by consumer exposure, not by implementation convenience:
   public/protected signature types and intentional entry-point aggregates use `api`; dependencies
   that are fully private to the implementation use `implementation`. A caller-owned platform
   integration is the only exception and must be named in the module manual and external-consumer
   test; it cannot be inferred from an existing `implementation` declaration.
8. A normal application declares the aggregate and optional-feature artifacts it uses. Do not
    document lower-layer coordinates as mandatory workarounds for incomplete Maven metadata.
9. Add every direct ViewCompose publication edge to
    [`gradle/viewcompose-dependency-contracts.properties`](https://github.com/ViewCompose/ViewCompose/blob/main/gradle/viewcompose-dependency-contracts.properties)
    in the same change. `verifyViewComposeDependencyContracts` rejects drift between that contract
    and Gradle declarations.
10. A new or changed entry point must include a minimal external-consumer compile test. Published
    repository inspection must preserve `api` as Maven compile scope and `implementation` as runtime
    scope before release.
11. Dependency exposure changes are publication-input changes: update the owning module manual and
    add immutable release intent in the same pull request.
12. Before first Central publication, a repository Maven sample may use a new coordinate only when
    its gate first publishes the current checkout to `build/maven-repository` and then consumes the
    generated POM. After publication, repeat the installation verification from a clean checkout
    without the generated repository.

### 5.12 Development tooling isolation

Development tooling that can execute inside an application process follows
[ADR-0009](../architecture/decisions/0009-development-tooling-isolation.md):

1. Concrete preview, inspector, source-navigation, and IDE transport implementations live only in
   modules classified as Tooling. Runtime modules may expose nullable neutral ports but cannot own
   a concrete tooling protocol, report writer, request receiver, or IDE lifecycle.
2. Activation requires all three conditions: optional tooling artifact present, debuggable process,
   and a valid explicit tool request. Never interpret `debuggable` as permission for continuous
   observation.
3. Inactive tooling installs no listener on scroll, global layout, draw, touch, animation frame, or
   recomposition; performs no View traversal, stack capture, serialization, or file I/O; and owns no
   eagerly started worker. Any narrowly justified exception needs an ADR, allowlist entry, and
   same-device benchmark evidence.
4. Prefer one nonce-bearing request and one response snapshot over continuously refreshed reports.
   Validate nonce, process, package, size bounds, lifecycle cleanup, stale response rejection, and
   failure isolation in deterministic tests.
5. Runtime changes run `verifyDevelopmentToolingIsolation`. Tooling that observes a hot path also
   compares the same debug build, device, workload, refresh rate, and thermal state. Idle scrolling
   must produce zero tooling writes.
6. Pull requests explicitly answer whether application-process tooling code changed, why runtime
   ownership remains neutral, how release classpath exclusion is proven, and what inactive-path
   evidence was collected.

### 5.13 One package root per module

1. Each module has one package prefix across main, test, and androidTest.
2. Android namespace matches that root, except the documented ui-contract exception.
3. Lifecycle and ViewModel public packages remain `com.viewcompose.lifecycle` and
   `com.viewcompose.viewmodel` in their owning modules.
4. `verifyModulePackageRoots` and `verifyAndroidModuleNamespaces` are non-waivable `qaQuick` gates.

### 5.14 Runtime purity and coverage

1. runtime remains a Kotlin/JVM module.
2. runtime production source cannot import Android/AndroidX or depend on `androidx.core.ktx`.
3. `verifyRuntimePurity` blocks violations in `qaQuick`.
4. Policy, snapshot, observation, invalidation, and composer branch changes add unit tests.

### 5.15 Host session and diagnostics

1. Android frame clock and dispatcher implementation lives in host-android; UI Foundation retains
   only the `RenderSessionRuntime` contract/provider.
2. Aggregate `setUiContent` and Engine `renderInto` expose core `RenderStats/RenderTreeResult`, never
   renderer implementation types.
3. Lazy-item and overlay child sessions use the session contract rather than constructing a
   platform implementation directly.
4. Guards cover renderer-type leakage and provider-missing no-op fallback.

### 5.16 Modifier and container policy

1. ui-contract Modifier contains globally stable semantics, never a policy meaningful to only one
   container.
2. `reusePolicy` and `motionPolicy` are container DSL and NodeSpec fields read directly by the
   renderer. A behavior that is required for correctness, such as native focused-descendant
   visibility, is an invariant rather than an opt-in policy.
3. Pager residency and direct-input controls remain explicit container fields. Disabling direct
   input does not disable state commands or programmatic focus visibility.
4. A new policy includes DSL-to-NodeSpec and renderer bind/patch tests. Before adding a Boolean,
   verify that the behavior is genuinely optional and has one stable owner.

### 5.17 Developer Preview

1. preview-core contains annotations, deterministic configuration, and version protocol without
   Android/AndroidX imports.
2. preview-runner mounts native Views, captures screenshots, and exports diagnostics without Compose
   or IDE SDK dependencies.
3. `PreviewCatalog` in preview is the single source; a component adds a `PreviewSpec`.
4. Paparazzi consumes the same catalog and does not maintain separate screenshot examples.
5. `qaPreview` is required; visual changes update baselines and pass protocol/runner/snapshot tests.
6. Preview modules do not depend on app or import Demo packages.
7. Worker and IDE plugin communicate only through structured data with
   `protocolVersion/requestId`.
8. Preview overlays are static simulations; real windows are instrumentation concerns.

### 5.18 Animation and gestures

1. Animation ownership is animation-core, animation DSL, and host-android interop. Gesture ownership
   is gesture-core policy, gesture DSL, and renderer Android adaptation.
2. TransitionManager, MotionLayout, and Animator are host-android interop only.
3. graphicsLayer changes add renderer patch/rebind tests and cannot use full-rebind fallback.
4. Gesture consumption is gesture-first then clickable fallback; parent-scroll competition is
   tested.
5. List/Pager motion is opt-in; motion/reuse semantic changes add container tests and docs.
6. AnimatedVisibility uses `NodeType.AnimatedVisibilityHost`; hidden content is removed after exit.
7. pointerInput changes test that `Consumed` blocks transform, drag, anchored drag, and combined click.
8. Transform thresholds test pan/zoom/rotation slop plus two-pointer instrumentation.
9. Anchored settle tests velocity, distance, and nearest-anchor paths.
10. `updateTransition` maintains one shared timeline across channels; AnimatedVisibility reuses it.
11. `animateContentSize` remains a layout transition observable by the parent, not a visual scale.
12. AnimatedSizeHost tests smooth expansion and collapse.
13. Axis lock, transform slop, and swipe settle algorithms live in gesture-core; renderer adapts
    thresholds and events only.
14. Enabled `combinedClickable` with no callbacks remains a no-op and does not consume input.

### 5.19 ConstraintLayout

1. DSL/scope lives in widget-constraintlayout; renderer maps to Android ConstraintLayout.
2. `layoutId/constrainAs/constrain` is parent data; a wrong parent emits
   `ModifierParentDataValidator` warning.
3. Inline constraints override a decoupled ConstraintSet for the same child and warn once.
4. ConstraintDimension overrides Modifier width/height/size.
5. A new helper or ConstraintSet semantic adds DSL unit, renderer unit, and Demo UI anchors.
6. `Barrier(allowsGoneWidgets)` must affect rendering.
7. Chain weights/reference size mismatch fails in DSL and warns once in renderer.
8. New min/max/percent/constrained, baseline extension, or circle semantics add DSL emission and
   renderer application assertions.

### 5.20 Graphics

1. graphics-core contains platform-neutral models and commands without Android/AndroidX.
2. graphics contains Canvas and drawing DSL without Android Canvas execution.
3. Renderer alone maps commands to Android Canvas/Paint/Path and patches.
4. drawWithCache changes test cache hits and invalidation; per-frame rebuild is prohibited.
5. RenderEffect, RuntimeShader, and Drawable bridges are host-android interop only.
6. Visual semantic changes update PreviewCatalog and Paparazzi under the `qaPreview` gate.

## 6. Interrupted-task recovery

Recover in this order:

1. `git log`;
2. current `git diff`;
3. current roadmap and architecture documents;
4. the latest failed log or test report;
5. conversation memory last.

Repository state is the project context of record; a chat thread is not.

## 7. Commit messages

The subject describes one minimum step. Use a direct `docs:`, `feat:`, `fix:`, `test:`, or
`refactor:` subject, for example `feat: add overlay host contract` or
`fix: refresh dialog content on state updates`.

## 8. Default execution order

Plan, implement the minimum step, commit it when complete, and continue to the next step. The goal
is not commit count; it is reviewable, reversible, and recoverable progress.

## 9. Documentation layers

The [documentation governance standard](./documentation-governance.md) owns complete placement,
naming, linking, and lifecycle rules.

1. Current entry: [`docs/README.md`](../README.md)
2. Long-lived standards: architecture, guides, tooling, and project
3. Cross-session execution: `docs/project/plans/`
4. Historical evidence: `docs/archive/`

The repository root contains only project entry and community governance files.

## 10. Durable execution plans

For work spanning multiple steps or days:

1. create a lowercase kebab-case plan under `docs/project/plans/`;
2. after each completed step and commit, update its checklist and execution log;
3. record baseline, completion condition, remaining work, and next step;
4. after completion, write durable conclusions into current documents and move the plan to archive;
5. close stale in-progress, incomplete, next, and pending markers in the roadmap and related current
   documents.
