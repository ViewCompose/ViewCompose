# ConstraintLayout Parity and Performance Expansion Plan

## Status

Complete and archived. The ConstraintLayout first-release hardening plan is archived as
`docs/archive/constraintlayout-native-engine-hardening.md`; Maven Central publication
`0.1.0-alpha01`, its artifact tag, and the release owner's explicit reopen are complete. Demo
fixed-clock baseline PR `#111`, Phase 0 PR `#112`, Phases 1 and 2, and Phase 3 acceptance are merged.
Phase 4's revision-6 released/candidate/direct matrix, durable guidance, Demo fixture correction,
and repository gates are complete in the final closeout change. The frozen chain-boundary,
parent-wrap, physical-edge, typed Grid, and declarative CircularFlow contracts now span transport,
DSL, graph preflight, transactional native rendering, compiled samples, one-purpose Demo fixtures,
Preview snapshots, exact JVM and device tests, manual visual review, and controlled release-safety
measurement.

Phase 0 remains Changeset-free because it changed no published production source. Phase 1 owns the
immutable renderer Changeset listed below. Phase 2 owns one immutable multi-artifact Changeset.
Phase 3 changes only the Demo application, tests, snapshots, and active documentation; it changes no
published-artifact production source, publication input, or compiled public API sample, so no new
`release/changes` file is required. Phase 4 likewise changes only the Demo fixture, benchmark-only
runner, report contracts, tests, and documentation. This completed plan no longer owns an active
release blocker; any further parity or whole-frame optimization work requires a new attributed
plan.

This plan is canonical English-only under the documentation-governance policy. Durable capability,
performance, compatibility, and operational conclusions must move into the owning active documents,
with required Simplified Chinese mirrors, before the plan is archived.

Last verified: 2026-08-22.

Activation trigger: the first-release hardening plan is archived, the corresponding Maven Central
release and Git tag are complete, and the release owner explicitly reopens ConstraintLayout
development.

Next action: archive this record with the final closeout change. Preserve the accepted structural
fast paths and require a new attributed plan before additional whole-frame performance work; the
revision-6 matrix does not support a performance-leadership claim.

## Maven release changesets

- `release/changes/20260821-constraintlayout-reconciliation-fast-path.json`
- `release/changes/20260821-constraintlayout-phase2-parity.json`

## Objective

Build on the correct transactional first-release engine and make
`viewcompose-constraintlayout-androidx` the highest-confidence native layout path for complex
ViewCompose screens by delivering:

1. zero adapter graph work for content-only and semantically equal updates;
2. bounded, classified no-op, scalar, environment, and topology reconciliation paths;
3. high-value AndroidX capabilities that fit ViewCompose's declarative model without copying
   imperative or state APIs;
4. Grid and CircularFlow helpers with the same lifecycle, validation, rollback, and stress
   guarantees as first-release helpers;
5. exact unit, renderer, device, screenshot, configuration, and stress evidence for the expanded
   surface; and
6. direct-native/current/candidate performance evidence strong enough to support precise overhead
   and layout-choice guidance without hiding frame-tail, allocation, or retained-memory costs; and
7. one evidence-based structural DSL Scope contract across ViewCompose, preserving module-specific
   ergonomics while eliminating receiver leakage, ambient collector state, and accidental
   construction where those defects are actually present.

The plan does not reopen the first-release API hard cut, restore a second reconciliation engine, or
introduce MotionLayout. Correctness remains the prerequisite for every optimization.

## Dependency on the first-release plan

This plan inherits, and may not weaken, the following accepted first-release invariants:

1. one immutable validated graph is the renderer source of truth;
2. one native registry owns every helper View and ID;
3. invalid candidates mutate no observable native state;
4. successful native changes publish one accepted revision;
5. generic Modifier-owned state survives commit and rollback;
6. raw ratio strings, `MatchParent`, contradictory dimension fields, partial-link recovery, and a
   dual engine remain absent; and
7. active diagnostics and retained caches are bounded.

If post-release work reveals that one invariant is unsound, pause implementation and amend the
current architecture and migration contract explicitly. Do not silently restore pre-hard-cut
behavior as an optimization shortcut.

## Ownership transferred from the original combined plan

| Transferred area | This plan owns | First-release boundary preserved |
| --- | --- | --- |
| Reconciliation optimization | No-op, content-only, scalar, environment, and topology diff paths; bounded scratch reuse; write/requestLayout suppression | The first release needs only correctness, atomicity, and no material safety regression |
| AndroidX parity | Chain endpoints and endpoint margins, wrap behavior, physical anchors/directions, explicit Guideline logical/physical policy, typed Grid, declarative CircularFlow, and exact geometry for the already-published baseline margins | Existing retained capabilities must already be correct before release; published baseline margin APIs are not duplicated |
| DSL Scope and ergonomics | Inventory and classify structural scopes; enforce the shared receiver-isolation/lifetime contract where needed; evaluate no-argument references, dimension sugar, and Compose-style anchor syntax without erasing the XML-friendly API | The first release already owns a dedicated ConstraintLayout scope, axis-typed targets, reference-based ConstraintSet entries, and compiler-negative safety tests |
| Demo and visual acceptance | One-purpose expanded fixtures, full screenshot/configuration matrix, parity scenes, and optimization diagnostics | Focused visuals and all observed defect regressions remain first-release requirements |
| Performance proof | Complete direct-native/published-baseline/candidate matrix, structural counters, allocations, retained state, and frame tails | The first release runs a smaller no-material-regression safety comparison |
| Performance guidance | Evidence-backed adapter overhead, workload choice, and optional typed optimization policy | No fastest-path or leadership claim is allowed at first release |

## Target reconciliation classes

Each candidate resolves to exactly one internal update class:

| Update class | Required behavior |
| --- | --- |
| No-op | Same topology, scalar values, environment, and child identity; no graph compilation, native allocation, adapter requestLayout, or helper write |
| Content-only | Constraint graph unchanged; native child content may measure normally, but the adapter performs no graph compilation or helper work |
| Scalar | Same nodes, helper kinds, and references; update only changed margins, bias, dimensions, visibility, transforms, or helper scalar properties and issue at most one layout request |
| Environment | Re-resolve density/layout-direction-dependent values from the same semantic graph while preserving IDs and helper instances |
| Topology | Stage membership and the complete native graph, then commit atomically with rollback data |

The accepted semantic graph, resolved graph, environment revision, topology fingerprint, scalar
fingerprint, and stable native ID mapping have explicit container-bounded ownership. Mutable global
pools and cross-container caches are prohibited.

## Public capability decisions

### Required additions

1. Horizontal and vertical chains with explicit start/end targets, target sides, and endpoint
   margins instead of parent-to-parent hard-coding.
2. Exact pixel assertions for the already-published baseline-to-baseline normal and gone margins;
   no replacement or duplicate margin API.
3. `wrapBehaviorInParent` through a typed enum covering horizontal-only, vertical-only, included,
   and skipped behavior supported by the selected AndroidX baseline.
4. Physical left/right anchors and Barrier directions for deliberate absolute-layout migration;
   logical start/end remain the default and examples explain RTL consequences.
5. Explicit Guideline logical-versus-physical policy through distinct start/end and left/right
   factories, rather than a boolean whose meaning changes the word `start` under RTL.
6. A typed Grid specification compiled transactionally onto the AndroidX solver without exposing
   AndroidX `Grid` string grammars or allowing it to create registry-external box Views.
7. A declarative CircularFlow bulk specification requiring an explicit radius and angle for every
   member and compiling to the already-accepted circle constraint primitive; AndroidX's
   process-global defaults and imperative member mutation remain inaccessible.
8. A typed container optimization policy only when at least one non-default policy has a
   reproducible benefit without correctness regression. Never expose AndroidX's raw integer
   bitmask.

### Phase 0 published baseline freeze

The follow-up line is rebased on the artifact that consumers can actually resolve, not an
unreleased checkout:

| Field | Frozen value |
| --- | --- |
| Maven artifact | `com.viewcompose:viewcompose-constraintlayout-androidx:0.1.0-alpha01` |
| Artifact tag | `maven/viewcompose-constraintlayout-androidx/0.1.0-alpha01` |
| Tag metadata commit | `b8315d326342797b0dee5e2a343ec84d2beaa764` |
| Corrected immutable source revision | `143b09acf3bfcda81add008b4dcf09d06a09e2dc` |
| AndroidX engine | `androidx.constraintlayout:constraintlayout:2.2.2` |
| Supported floor | Android 7.0 / API 24 |
| Accepted physical reference device | Rooted Xiaomi MI 6, Android 9 / API 28, 60 Hz |
| Target mode | R8 optimized, resource-shrunk, non-debuggable benchmark target |
| Benchmark identity | `performance.complex-layout@6`, `CompilationMode.None`, actual `run-from-apk`, five iterations, sixteen update/reset cycles driven through direct accessibility actions |
| Workload matrix | Stable, scalar, helper, and topology at 10, 50, and 100 nodes against direct AndroidX |
| First-release target APK SHA-256 | `a7d681b90941a8d318108d709b3a7b77147b614180a8d2124840416d07148fac` |
| Pre-hard-cut historical APK SHA-256 | `2b32ca7539be121615fb3e7b61953101be7b9a2e4ac55215690d88a480b25161` |

The accepted first-release run fixed CPU policies 0/4 at 1.4016/1.8048 GHz and Adreno at
515 MHz, kept all CPUs online, suspended charging, stopped vendor performance services, cooled to
at most 37 degrees Celsius before each method, and accepted only run-P50 CV at most `0.15`. Eight
of twelve ViewCompose longitudinal rows were stable and none regressed materially; four remain
inconclusive. Those immutable results remain the historical source-release evidence.

The later collection-stress investigation proved that Qualcomm `cpubw` and `gpubw` plateaus can
change RenderThread/BufferQueue timing while CPU and GPU frequencies remain fixed. Every new
renderer-sensitive ConstraintLayout comparison must therefore add the v4 interconnect vote to the
same clock policy and recapture the released Maven artifact and direct AndroidX controls adjacent
to the candidate. The old v3 values are not silently relabeled as v4 results. Build order, package
identity, target/benchmark APK hashes, actual compilation result, frame count, peak heap, thermal
state, and raw rejected repetitions remain part of every accepted batch.

The initial Phase 4 revision-4 recapture is rejected protocol evidence. Manual review found that
the direct AndroidX scalar, helper, and topology fixtures created the requested Views but left them
transparent while ViewCompose drew visible cells, so their renderer work was not equivalent. After
the direct cells were made visible, stable-50 candidate preflight and its one adjacent repeat still
failed the `0.15` gate at run-P50 CV `0.261` and `0.437`; Direct AndroidX passed at `0.080` and
`0.054`. Each revision-4 run measured only about 16 frames. A diagnostic five-trace run showed
main-thread traversal totals of `30.864--37.758 ms`, RenderThread `DrawFrame` totals of
`30.364--45.315 ms`, and JIT work of `52.647--200.171 ms`, so the short sample remained vulnerable
to runtime and display-pipeline plateaus.

Revision 5 made both engines draw the same visible cells, retained every trace before cleanup, and
raised each run to about 32 frames through eight update/reset cycles. Its complete Candidate matrix
still rejected ViewCompose stable-100, scalar-100, helper-50, and topology-100 at CV `0.175`,
`0.163`, `0.156`, and `0.363`; the single paired repeats produced ViewCompose/direct CV
`0.409/0.104`, `0.116/0.097`, `0.056/0.080`, and `0.256/0.068`. Trace inspection then found a
protocol defect rather than one 32-frame workload: every coordinate click contributed a fast
pointer press/release frame and a slower content mutation/layout frame. The approximately
`16 + 16` bimodal split placed run P50 at the boundary, so small frame-count shifts moved individual
run medians from about `5 ms` to `12--22 ms`. A Released revision-5 matrix was intentionally not
run because no longitudinal claim could survive that input defect.

Revision 6 is the final protocol cut. The benchmark invokes the existing accessibility click action
directly, asserts the resulting state transition after every action, and executes sixteen complete
update/reset cycles so each of five runs contains at least 32 content-update frames without pointer
press/release animation. Direct AndroidX cells carry the same visible fill as the ViewCompose
fixture, and 10-, 50-, and 100-node screens received explicit manual visual acceptance before the
formal batch. No revision-4 or revision-5 value may enter a Phase 4 directional or release claim.

### Phase 0 AndroidX capability audit

The pinned AndroidX `2.2.2` sources were audited against the released ViewCompose API. The audit
freezes these decisions:

| AndroidX area | Released ViewCompose state | Phase 0 decision |
| --- | --- | --- |
| Chain boundaries | Horizontal chains are logical parent-to-parent; vertical chains are parent top-to-bottom | Accept typed target side and margin parameters for both endpoints; reject mixed logical/physical horizontal endpoint planes |
| Baseline margins | `baselineToBaseline`, `baselineToTop`, and `baselineToBottom` already carry normal and gone margins | Add exact renderer/device geometry only; no new public API |
| Parent wrap contribution | Not represented | Accept `ConstraintWrapBehavior` with `Included`, `HorizontalOnly`, `VerticalOnly`, and `Skipped` |
| Physical anchors and barriers | Logical start/end plus physical top/bottom only | Accept `leftToLeft`, `leftToRight`, `rightToLeft`, `rightToRight`, `createLeftBarrier`, and `createRightBarrier`; logical and physical horizontal links cannot coexist on one item |
| Guideline RTL | Start/end are logical and follow layout direction | Keep start/end logical; add `createGuidelineFromLeft` and `createGuidelineFromRight` for physical placement instead of exposing `guidelineUseRtl` |
| AndroidX `Grid` | No equivalent constrained-grid helper; `LazyVerticalGrid` has different viewport/session semantics | Accept a typed, bounded solver expansion; do not instantiate AndroidX `Grid`, whose `2.2.2` implementation accepts unchecked strings, contains unfinished RTL handling, and creates box Views outside the accepted registry |
| AndroidX `CircularFlow` | Per-child typed `circular` positioning already exists | Accept only declarative bulk sugar compiled to the existing circle primitive; do not expose imperative add/update/remove methods or process-global default radius/angle |
| `ReactiveGuide`, `SharedValues`, state sets, and imperative `ConstraintSet` mutation | Observable state and immutable graph selection already express the use cases | Reject as duplicate state/imperative ownership |
| Raw optimization bitmasks | Not represented | Keep rejected until one typed non-default policy wins repeatedly without correctness or tail regression |

### Phase 0 public API contract freeze

The following source shapes are authoritative for Phase 2. Names or defaults may change only after
updating this plan with a concrete compiler-safety, lifecycle, or migration defect.

| Surface | Frozen shape and defaults | Q level and applicable contract fields |
| --- | --- | --- |
| Horizontal chain endpoints | Extend `createHorizontalChain` with `startTarget: ConstraintHorizontalAnchorTarget = parent`, `startTargetSide: ConstraintHorizontalAnchorSide = Start`, `startMargin: UiDp = Zero`, and symmetric end fields defaulting to parent/`End`/zero. `ConstraintHorizontalAnchorSide` contains `Start`, `End`, `Left`, and `Right`. | Q3 function and compiled sample; Q2 enum. Inputs, coordinates/RTL, failure, migration, Android mapping, and performance. |
| Vertical chain endpoints | Extend `createVerticalChain` with top target/side/margin and bottom target/side/margin. `ConstraintVerticalAnchorSide` contains `Top` and `Bottom`; defaults remain parent top-to-bottom with zero margins. | Q3 function and compiled sample; Q2 enum. Inputs, coordinates, failure, migration, Android mapping, and performance. |
| Wrap contribution | Add `ConstraintWrapBehavior` and `ConstraintConstrainScope.wrapBehaviorInParent`, default `Included`. Transport snapshots the same typed value. | Q3 property/sample and Q2 enum. Measurement, default, interaction with parent wrap content, failure, Android behavior, and compatibility. |
| Physical edges | Add the four left/right link functions with the existing margin/gone-margin contract and the two physical Barrier factories. Existing horizontal reference types remain the ID capability plane; function names choose the target side. | Q3 family/sample. Coordinates, RTL invariance, mixed-plane rejection, gone behavior, migration, and Android mapping. |
| Physical Guidelines | Add `createGuidelineFromLeft` and `createGuidelineFromRight` offset/fraction overloads. Left/right never mirror; start/end always mirror. | Q3 family/sample. Units/ranges, RTL, failure, Android behavior, and migration. |
| Typed Grid | Add `ConstraintGridOrientation`, `ConstraintGridSpan(reference, index, rowSpan, columnSpan)`, `ConstraintGridSkip(index, rowSpan, columnSpan)`, and `createGrid`. `refs` is non-empty; rows/columns use `0` for auto or `1..50`; weights are finite positive and match resolved axes; gaps are finite non-negative; spans/skips are in-range, unique, and non-overlapping. Defaults are auto rows/columns, horizontal fill order, unit weights, no spans/skips, and zero gaps. | Q3 types, builder, and compiled sample. Identity, units, ranges, topology ownership, lifecycle, RTL, rollback, failure/retry, Android implementation, complexity, allocations, and compatibility. |
| Declarative CircularFlow | Add `ConstraintCircularFlowItem(reference, radius, angle)` and `createCircularFlow(center, vararg items, id = auto)`. Items are non-empty and unique, exclude the center, require finite non-negative radii and finite angles in `0f..<360f`, and may not also own edge, baseline, chain, or circle positioning. Every value is explicit; there are no defaults. | Q3 type, builder, and compiled sample. Identity, units/coordinates, ownership conflicts, lifecycle, rollback, failure/retry, Android implementation, and complexity. |

The Android-free transport names are also frozen:

1. `ConstraintChainSpec` gains nullable `startTarget`/`endTarget` anchor targets plus zero-default
   `startMargin`/`endMargin`; `null` preserves the released orientation-specific parent boundary.
2. `ConstraintItemSpec` gains nullable `left`/`right` links and
   `wrapBehaviorInParent: ConstraintWrapBehavior = Included`. Graph validation rejects any item
   combining logical start/end with physical left/right.
3. `ConstraintAnchor` and `ConstraintBarrierDirection` gain `Left` and `Right` entries;
   `ConstraintGuidelineDirection` gains `FromLeft` and `FromRight`.
4. `ConstraintHelpersSpec` gains `grids: List<ConstraintGridSpec>` and
   `circularFlows: List<ConstraintCircularFlowSpec>`. Grid transport uses
   `ConstraintGridSpanSpec(referenceId, index, rowSpan, columnSpan)` and
   `ConstraintGridSkipSpec(index, rowSpan, columnSpan)`; CircularFlow transport uses
   `ConstraintCircularFlowItemSpec(referenceId, radius, angle)`.

Transport additions remain Android-free immutable snapshots in `viewcompose-ui-contract`. The DSL
module translates only validated typed values into that transport; the renderer performs complete
graph validation again. Grid-generated native identities are deterministic children of the Grid
semantic ID and remain in the one container registry. CircularFlow creates no native helper View;
its accepted graph owns the member circle links atomically. Both removal and failed replacement
restore the prior graph without retaining generated IDs or member ownership.

Baseline margin additions were removed from this API table because the released API already owns
them. Anonymous references, dimension aliases such as `fillToConstraints`, and Compose-style
anchor objects/`linkTo` are rejected for this line: they either weaken deterministic identity or
duplicate an already short typed/XML-friendly spelling without removing a demonstrated error.
`ConstraintDimension.MatchConstraints`, `Fixed`, `WrapContent`, and `ConstrainedWrapContent` remain
the one dimension algebra.

### Structural DSL Scope consistency audit

The audit unifies contracts, not implementation shape or vocabulary. It must cover at least
`LayoutScope`, `RowScope`, `ColumnScope`, `BoxScope`, `ScrollableScope`, lazy list/grid scopes,
pager scopes, `TabRowScope`, `NavigationBarScope`, `ConstraintLayoutScope`, and
`ConstraintSetBuilder`.

Every structural child-content scope is evaluated against the following contract:

1. it uses the shared UI DSL marker so an outer structural receiver is hidden inside a nested
   structural DSL;
2. its constructor is not an application construction path, and its instance is fresh, ephemeral,
   synchronous, and never retained after content completes;
3. parent-data, helper, and item-declaration functions are available only on the narrowest correct
   receiver;
4. independently published container modules use the UI Foundation scoped-container boundary
   instead of thread-local collectors, global stacks, or mutable NodeSpec payloads;
5. valid nesting retains ordinary widget access, State observation, Local propagation, and
   composition-group behavior; and
6. every repaired leakage or invalid-receiver case has a compiler-negative fixture plus a positive
   compiled Q3 sample.

The Phase 0 inventory found no structural receiver that requires a public source change:

| Receiver family | Classification | Evidence and decision |
| --- | --- | --- |
| `LayoutScope`, `RowScope`, `ColumnScope`, `BoxScope` | Compliant structural child scopes | Shared `@UiDslMarker`, internal construction, fresh synchronous builders, and parent data only on the narrow Row/Column/Box receiver. Keep names and syntax. |
| `ScrollableScope` | Compliant scoped-container facade | Shared marker, internal construction, private `UiTreeBuilder` delegate, synchronous snapshot build, and no ambient collector. Keep the distinct facade because it prevents outer-builder ownership leakage. |
| `LazyListScope`, `LazyGridScope` | Compliant structural item scopes | Shared marker and internal construction. The declaration scope and `LazyItemCollector` are frame-local; the separately remembered bounded canonical reuse cache is container state, not a retained scope. No repair. |
| Pager scope used by horizontal and vertical pagers | Compliant with intentionally retained historical name | `HorizontalPagerScope` is fresh, marked, and synchronous for both axes. Renaming or duplicating it would create migration cost without fixing receiver safety. |
| `TabRowScope`, `NavigationBarScope` | Compliant structural item scopes | Fresh marked collectors freeze ordered immutable items and are discarded after the call. Keep module vocabulary. |
| `ConstraintLayoutScope` | Compliant structural child/helper scope | Fresh marked `UiTreeBuilder`; helper state freezes after content; late retained-scope calls fail; nested layout leakage already has a compiler-negative fixture. |
| `ConstraintSetBuilder`, `ConstraintConstrainScope` | Intentionally domain-specific marked builders | They build immutable graph values rather than child UI. Internal construction, synchronous use, shared marker, local validation, and snapshots already satisfy the relevant lifetime and receiver rules. |
| `NavGraphBuilder` and draw/effect/lifecycle/value builders | Not a structural UI child scope | Retain their own domain marker or builder contract. Do not force them onto the UI scoped-container implementation. |

Framework code retains no scope instance after its declaration callback. Capturing a receiver in
application code is unsupported but does not mutate a committed snapshot: ordinary builders have
already copied/frozen their submission, while ConstraintLayout explicitly rejects late helper or
reference calls. No reproduced receiver collision, ambient structural collector, mutable NodeSpec
payload, or cross-container ownership defect justifies a repository-wide rewrite.

The audit must explicitly classify non-structural builders instead of forcing them into this
contract. Draw/effect/lifecycle/value builders and `NavGraphBuilder` retain their own semantic DSL
markers or builder rules unless a concrete receiver collision is reproduced. Existing public scope
names and member syntax remain unchanged when they already satisfy the contract; consistency alone
does not justify a mass rename or source break.

The Phase 0 ergonomics review is closed for this release line:

1. reject no-argument references and auto-generated child identities because call order is not a
   durable save/reorder/ConstraintSet/diagnostic identity;
2. reject dimension aliases and factory sugar because the accepted algebra is already concise and
   a second vocabulary does not remove a reproduced misuse;
3. reject Compose-style anchor objects and `linkTo` because they create a second authoritative
   syntax without improving axis typing or materially shortening common links; and
4. keep the XML-friendly `startToStart`/`topToBottom` family authoritative, adding only the
   physical and chain-endpoint functions frozen above.

### Intentionally omitted or delegated

1. `ReactiveGuide` and `SharedValues` remain replaced by observable ViewCompose state selecting a
   graph.
2. `ConstraintLayoutStates` and StateSet remain replaced by explicit state/environment-driven
   ConstraintSet selection.
3. `ConstraintProperties`, XML ConstraintSet loading, imperative clear/connect, custom attributes,
   and ConstraintSet-owned alpha/rotation/visibility remain omitted as declarative DSL or Modifier
   duplicates.
4. `constraintTag` remains omitted until a concrete query/state-selection requirement cannot use a
   semantic key, layout ID, or test tag.
5. Container min/max dimensions continue through general layout modifiers unless native parity
   testing proves a semantic gap.
6. ConstraintSet transition animation delegates to the Animation plan's accepted bounds model.
7. MotionLayout, MotionScene, Carousel, MotionEffect, key cycles, and `OnSwipe` remain outside this
   plan. A future concrete requirement needs its own plan.
8. A repository-wide replacement of every builder with one base class, one naming scheme, or one
   marker is omitted. Shared safety invariants do not require identical domain APIs.

### Phase 0 test-first catalog

Phase 0 freezes the red assertions below. The first commit of the owning implementation phase adds
and demonstrates each test failing against the released baseline before production source is
changed; the next commit makes it pass. The branch head and every pull request remain green: this
repository does not merge ignored tests or invert assertions merely to store a permanently failing
suite.

| ID | Owning phase | Red assertion against `0.1.0-alpha01` |
| --- | --- | --- |
| `CL-P1-EQUAL-001` | Phase 1 | 1,000 semantically equal submissions report zero graph compilations, native commits, helper writes, layout requests, and adapter-owned allocations after the accepted graph |
| `CL-P1-CONTENT-002` | Phase 1 | Content-only child updates preserve the graph/fingerprint/helper identity and perform zero adapter graph work while normal child measurement remains allowed |
| `CL-P1-SCALAR-003` | Phase 1 | One scalar change reports exactly one scalar classification, no helper create/remove, no live-layout clone, and at most one native commit/layout request |
| `CL-P1-ENV-004` | Phase 1 | Density or direction change preserves topology/native IDs, resolves the environment once, and commits only changed resolved scalar fields |
| `CL-P1-TOPOLOGY-005` | Phase 1 | Topology change stages complete membership, publishes once, and restores graph, IDs, helper instances, LayoutParams, and diagnostics after an injected failure |
| `CL-P2-CHAIN-001` | Phase 2 | Parent/child/Guideline/Barrier endpoints and margins produce exact LTR/RTL bounds; mixed logical/physical endpoint planes reject before native mutation |
| `CL-P2-BASELINE-002` | Phase 2 | Existing baseline normal/gone margins match AndroidX pixels and retain the prior accepted layout after an invalid retry |
| `CL-P2-WRAP-003` | Phase 2 | All four wrap behaviors affect only the documented parent axes under exact wrap-content measures |
| `CL-P2-PHYSICAL-004` | Phase 2 | Left/right anchors, Barriers, and Guidelines remain physically fixed across RTL while start/end counterparts mirror |
| `CL-P2-GRID-005` | Phase 2 | Typed auto/fixed axes, weights, gaps, spans, and skips produce exact geometry; invalid/overlapping areas create no generated native identity; 1,000 replacements remain bounded |
| `CL-P2-CIRCULAR-006` | Phase 2 | Explicit member radius/angle values match direct AndroidX circle geometry; ownership conflicts reject atomically; removal and 1,000 replacements retain no member ownership or generated ID |
| `CL-SCOPE-001` | Phase 0/2 | Nested Row/Column/Box, lazy, pager, tab, navigation, ConstraintLayout, and ConstraintSet compiler fixtures hide the outer structural receiver while positive widget/State/Local nesting compiles |

Structural counters are container-bound test/benchmark evidence. Their inactive path may perform
only one predictable disabled check and may not allocate, register global callbacks, retain graph
history, or expose development tooling as application API. The owning tests assert both enabled
counts and a zero-work disabled path under ADR-0009.

## Delivery phases

### Phase 0: published-baseline refresh and contract freeze

Planning estimate: 5--8 engineering days after activation.

1. Record the exact released ViewCompose versions, source tag, AndroidX version, device/build mode,
   and accepted first-release benchmark controls.
2. Re-audit selected AndroidX capabilities against the released API and remove any proposed
   addition that duplicates a clearer ViewCompose primitive.
3. Assign Q levels and applicable contract fields to every new public type and freeze exact names,
   defaults, valid ranges, failure behavior, and migration examples.
4. Produce the structural Scope inventory and classify each receiver as compliant, repairable,
   intentionally domain-specific, or not a DSL Scope; reproduce every proposed repair before
   changing public source.
5. Freeze a reviewed accept/reject decision for anonymous references, dimension sugar, and
   Compose-style anchor syntax, retaining the XML-friendly family regardless of that decision.
6. Freeze compiler-safety, performance-structure, and parity geometry red assertions and require
   the first commit of each implementation phase to demonstrate them failing before production
   source changes; never merge an ignored or intentionally failing suite.
7. Freeze absolute and normalized candidate budgets under the protocol below.

Exit criteria: reproducible published-baseline fixtures; reviewed API table and structural Scope
inventory; accepted parity and ergonomics scope; named red assertions and implementation order for
every target behavior; and no unresolved ownership, coordinate, lifecycle, receiver, failure, or
performance field.

Phase 0 completion record (2026-08-21): every exit criterion above is represented by the frozen
tables and catalog in this plan. Compiler-positive and compiler-negative structural Scope fixtures
were added without changing published source; the owning module unit suite and documentation
structure verification pass. No Changeset is required because the phase changes tests and active
documentation only. The later implementation phases still own the required fail-before/fix-after
evidence for their named red assertions.

### Phase 1: topology, scalar, and allocation convergence

Planning estimate: 2--3 engineering weeks.

1. Cache accepted semantic/resolved graphs, environment revision, deterministic topology/scalar
   fingerprints, and stable native IDs with container-bounded ownership.
2. Implement no-op, content-only, scalar, environment, and topology paths with structural counters.
3. Resolve environment once per graph compilation or environment update.
4. Reuse scratch storage only when ownership is local and bounded.
5. Avoid helper and LayoutParams writes when accepted scalar values are unchanged.
6. Ensure one logical update schedules at most one layout request and never queues stale work.
7. Attribute CPU and allocation costs before replacing data structures solely for theoretical
   efficiency.

Exit criteria: content-only/equal updates perform zero adapter graph rebuilds and allocations;
scalar updates create/remove no helper Views and never clone the live layout; topology work scales
with the changed graph and satisfies accepted budgets.

Phase 1 completion record (2026-08-21): the renderer caches accepted semantic and resolved graphs,
environment, deterministic topology/scalar fingerprints, raw child specifications, native IDs,
and helper ownership inside each container. Rebuilds are classified as no-op, content-only,
scalar, environment, or topology before work is committed. Equal and content-only requests return
without compiler/environment/native/helper/layout work; scalar updates retain helper identity and
references, create/remove no helper, clone no live LayoutParams, and schedule at most one adapter
layout request. Environment updates resolve once while preserving topology and IDs. An injected
topology failure restores the accepted graph, revision, fingerprint, IDs, LayoutParams, helper
instances, and diagnostics, then publishes exactly once on a valid retry.

Named cases `CL-P1-EQUAL-001` through `CL-P1-TOPOLOGY-005` pass as part of all 459 renderer tests.
`verifyDevelopmentToolingIsolation`, `verifyDocumentationStructure`, and
`verifyViewComposeReleaseIntent` also pass. The counters are container-local, optional, and
internal to test diagnostics; their inactive path performs one nullable check and owns no global
or recurring application-process work. No public/protected API documentation field applies because
all new symbols and activation hooks are `internal`; the owning module manuals and this plan record
the behavior change instead. The immutable renderer fix Changeset is listed above.

The same-day fixed-clock 50-node full-frame preflight is retained as `inconclusive`, not as a
performance claim. Released-baseline stable/scalar run-P50 CVs were `0.181`/`0.261`; an adjacent
scalar repeat remained `0.244`. Candidate stable/scalar CVs were `0.212`/`0.143`. Because each
longitudinal pair requires both arms at or below `0.15`, no pair is directional and the direct-native
control was intentionally not promoted into a comparison. Phase 4 owns the cooled, stable
direct-native/released-baseline/candidate matrix. This does not block the structural Phase 1 exit,
and it does block any whole-frame optimization-win claim.

### Phase 2: high-value AndroidX parity

Planning estimate: 2.5--4 engineering weeks.

1. Add chain endpoints and margins with parent, child, Guideline, and Barrier targets.
2. Add wrap behavior, physical anchors/directions, explicit physical Guidelines, and exact geometry
   coverage for the existing baseline margins.
3. Add Grid and CircularFlow across DSL, transport, native registry, samples, Demo, Preview where
   supported, renderer tests, and device tests.
4. Test each addition under removal, replacement, invalid reference, RTL, environment changes,
   rollback, and 1,000-state-switch stress.
5. Evaluate optimization policies across the accepted matrix and publish a typed expert policy only
   if evidence meets the activation rule.
6. Repair only the structural Scope violations accepted in Phase 0, preserving already-correct
   scope syntax and adding compiler-negative receiver-leak tests for each changed family.
7. Implement only the ConstraintLayout ergonomics accepted in Phase 0; keep typed target planes,
   explicit-ID diagnostics, and the XML-friendly anchor family authoritative.

Exit criteria: every addition has Q3 documentation and compiled samples, exact native geometry,
helper lifecycle, Demo, device, and compiler-safety evidence; omitted APIs and unchanged compliant
scopes remain explicitly documented.

Completion record (2026-08-21): the six frozen `CL-P2-*` API-35 Robolectric cases pass with exact
geometry, rollback, removal, environment-change, bounded-helper, and 1,000-replacement assertions.
On a rooted Xiaomi MI 6 / Android 9, the complete `ConstraintLayoutReleaseDeviceTest` class passes
4/4 in `15.674 s`; its new Phase 2 case verifies Grid span/skip ordering with exactly five generated
row/column proxies and four CircularFlow cardinal positions at a `78 dp` radius with no helper View.
The other three cases retain the earlier light/LTR/font-scale 1.0 and dark/RTL/font-scale 1.3 helper
matrix plus 200 rapid state switches, and diagnostics report no unexpected renderer warning. Manual
review of the Chinese Demo confirmed the visible `2 x 3` weighted Grid span/skip geometry and the
four-member CircularFlow without overlap, clipping, or helper artifacts; process-filtered logs had
no `UIConstraintLayout`, `ConstraintSet`, renderer, helper-layer, or fatal entry. The capability and
failure-safety conclusion is **improved** relative to the released source. This is one physical
OEM/API point and two focused screenshots, so Phase 3 still owns the broader configuration,
screenshot, and lifecycle matrix; it is not performance evidence, and Phase 4 still owns timing
claims.

The final repository acceptance passes `qaQuick`, Demo and AndroidTest APK assembly, the selected
three-module Q3 API-documentation audit, release intent, documentation structure and localization,
and development-tooling isolation. The build completed 1,735 tasks with no failed test or gate;
there is no remaining Phase 2 implementation or acceptance work before review.

### Phase 3: complete Demo, visual, configuration, and stress acceptance

Planning estimate: 1.5--2.5 engineering weeks.

1. Keep each Demo section a one-purpose fixture whose labels never overlap demonstrated geometry.
2. Add Grid, CircularFlow, and helper-lifecycle scenes exposing accepted revision, helper count,
   update class, and structured failure without relying on raw logs.
3. Use exact or tolerance-bounded geometry for anchors, gone margins, baseline, circle, bias,
   ratio, percent, min/max, chain endpoints/styles/weights, every helper, Placeholder, and Layer.
4. Add deterministic light/dark, LTR/RTL, phone/tablet, portrait/landscape, and font-scale
   screenshot coverage, separating static visuals from physical-device lifecycle tests.
5. Fail device acceptance on unexpected `UIConstraintLayout`, `ConstraintSet`, or uncaught
   AndroidX warnings in a supported fixture.
6. Exercise rapid toggles, child reorder, key reuse, detach/reattach, density and direction changes,
   process recreation where applicable, and failure followed by valid retry.

Completion record (2026-08-21): the Demo now exposes one-purpose Grid, CircularFlow, gone-margin,
parent-wrap, chain, and helper-lifecycle scenes. The lifecycle fixture reports mounted accepted
revision, helper count, update class, and structured rejection state after two animation frames;
its instrumentation remains app-only and does not add renderer work, public API, or an inactive
production observer. Exact device assertions cover those scenes plus anchors, dimensions, bias,
logical and physical direction, recreation at density scale `1.25`, child reorder, key reuse,
detach/reattach, invalid-candidate rollback, and valid retry.

The deterministic Paparazzi matrix passes 12/12 reviewed snapshots across a pairwise/orthogonal
selection of phone/tablet, portrait/landscape, light/dark, LTR/RTL, and font scales `1.0`, `1.3`,
and `2.0`. Manual review found no overlap, clipping, ambiguous geometry, or direction/theme defect.
The combined Phase 2/3 device suite passes 8/8 on the minimum API-24 emulator in `16.45 s`, 8/8 on
the latest API-36 emulator across the final focused runs, and 8/8 in `26.442 s` on a physical Google
Pixel 4 XL / Android 13 (API 33); the final physical Gradle invocation completed in `39 s`.
Structured log filtering reports no unexpected `UIConstraintLayout`, `ConstraintSet`, or uncaught
AndroidX warning. The one deliberately invalid lifecycle candidate produces exactly the expected
bounded rejection before a successful retry. The earlier 200-toggle physical case and Phase 2
1,000-replacement JVM cases remain green.

Relative to Phase 2-only acceptance, confidence in configuration, visual geometry, lifecycle, and
minimum/latest-API compatibility is **improved**. Phase 3 changes no renderer behavior and supplies
no controlled timing comparison, so its performance conclusion is **no material change** and it
does not support a Phase 4 optimization claim. Limitations are one final physical Google/API-33
point in addition to the earlier Xiaomi/API-28 evidence, a 12-case pairwise matrix rather than all
48 Cartesian visual combinations, and no cooled direct-native/released-baseline/candidate run.
Phase 4 owns that benchmark, durable performance guidance, release gates, and plan archive.

Exit criteria: every public capability maps to exact automation and a discoverable Demo fixture;
the screenshot matrix is reviewed; interactions are warning-free; and no device test passes only
because status text changed.

### Phase 4: benchmark, documentation, release, and archive closeout

Planning estimate: 1--2 engineering weeks.

1. Run the published-baseline/candidate/direct-native matrix with identical APK mode, fixture,
   device, thermal/clock policy, compilation mode, and action protocol.
2. Interpret absolute and normalized results in active performance documentation, including median,
   tail, allocation, retained helper count, conclusion, limitations, and next action.
3. Run focused module tests, compiled samples, API checks, documentation structure, tooling
   isolation, release-intent verification, `qaQuick`, relevant `qaFull`, and selected release
   build/benchmark gates.
4. Update module/renderer manuals, architecture, migration comparison, roadmap, Demo verification,
   performance guidance, reviewed Chinese mirrors, and final Changeset list.
5. Archive this plan and update plan indexes before affected follow-up artifacts are uploaded.

Exit criteria: all gates pass; durable conclusions are in active owners; no unowned deferred release
blocker remains; and the active plan no longer blocks its own publication train.

Phase 4 completion record (2026-08-21): the frozen released framework source was
`143b09acf3bfcda81add008b4dcf09d06a09e2dc`; the candidate framework base was
`b1aa64f206554a91443715e2a32f37864ab71432`, while the APK hashes below pin the complete revision-6
fixture and benchmark-runner overlay. Released, candidate, and shared benchmark APK
SHA-256 values were respectively
`bdf94fdc934780afd8e46298a7f5081a402838aa08cefc1deb46fe705335e179`,
`3b4dcf71c9952c5650d11cb448a6c3281f901fa9936e269529ae36be8053d71c`, and
`5c4b611fee97fa35faee7303ef3f7b073f5e34de1413f03b5783f6ffcfeb0b0e`. The rooted Xiaomi
MI 6 / Android 9 reference device ran revision 6 with the v4 CPU/GPU/interconnect policy, charging
suspended, vendor performance services stopped, starts at or below 37 degrees Celsius, five
`run-from-apk` iterations, and sixteen state-checked update/reset cycles. The Candidate and
Released primary matrices each retained 24 JSON files and 120 traces. One complete paired repeat
was captured for each unstable pair: 12 Candidate methods/60 traces and 16 Released methods/80
traces. No third attempt was selected.

Every cell below is ViewCompose frame CPU P50/P95/P99 in milliseconds; Direct is the paired
Candidate AndroidX P50/P95. CV is final Released/Candidate run-P50 CV after the single replacement
repeat where required.

| Action | Released ViewCompose | Candidate ViewCompose | Direct AndroidX | CV | Conclusion |
| --- | ---: | ---: | ---: | ---: | --- |
| `stable-10` | `9.116/10.918/13.854` | `8.803/11.462/14.612` | `3.438/4.676` | `0.143/0.120` | `no material change` |
| `stable-50` | `10.614/19.107/23.141` | `11.237/17.137/19.951` | `4.402/5.971` | `0.117/0.180` | `inconclusive` |
| `stable-100` | `12.674/24.434/26.283` | `12.585/24.398/28.491` | `5.635/7.486` | `0.111/0.121` | `no material change` |
| `scalar-10` | `10.478/13.931/14.977` | `9.776/13.316/14.977` | `4.828/6.421` | `0.179/0.171` | `inconclusive` |
| `scalar-50` | `11.588/21.484/24.060` | `11.553/22.947/25.301` | `7.392/9.032` | `0.229/0.205` | `inconclusive` |
| `scalar-100` | `15.597/36.150/41.827` | `16.100/34.624/38.874` | `11.538/14.207` | `0.021/0.125` | `no material change` |
| `helper-10` | `7.955/10.926/13.923` | `8.320/11.380/13.513` | `4.558/6.126` | `0.128/0.124` | `no material change` |
| `helper-50` | `7.678/12.484/14.134` | `7.346/12.243/14.959` | `6.301/8.201` | `0.227/0.140` | `inconclusive` |
| `helper-100` | `8.303/15.280/19.498` | `7.826/14.579/16.399` | `9.373/10.830` | `0.109/0.082` | `no material change` |
| `topology-10` | `9.774/12.489/14.589` | `10.390/14.101/19.010` | `4.811/6.366` | `0.124/0.137` | `no material change` |
| `topology-50` | `13.570/22.272/27.262` | `12.367/21.920/25.432` | `7.312/8.683` | `0.201/0.140` | `inconclusive` |
| `topology-100` | `15.719/32.688/34.876` | `15.390/34.778/38.771` | `11.409/12.850` | `0.110/0.098` | `no material change` |

Seven longitudinal pairs are interpretable and all timing and peak-heap regression rows pass. The
Direct-normalized Candidate P50 movement for those rows spans `-5.7%` to `+8.4%`; normalized P95
spans `-4.0%` to `+14.3%`. Candidate peak heap changes range from `-3.8%` to `+10.5%`; the largest
absolute increase is `2,199 KiB`, and no row crosses the combined memory gate. Stable-50,
scalar-10, scalar-50, helper-50, and topology-50 remain `inconclusive` because at least one final
ViewCompose arm exceeds CV `0.15`. Direct AndroidX is faster at P95 in every Candidate action and
at P50 in eleven of twelve; helper-100 is the sole opposing median, with Candidate `16.5%` faster
at P50 but `34.6%` slower at P95. The required 50-node stable/scalar 25% gap closure is therefore
not established.

The matrix-level conclusion is **no material change** for release safety: there is no stable
regression and no whole-frame optimization win. The environment-only fast path remains protected
by exact Phase 1 structural counters instead of a full-frame Direct comparison because AndroidX
has no equivalent declarative environment-resolution action; no environment timing claim is made.
Limitations are one OEM/API-28 point, `run-from-apk` JIT/code-placement sensitivity, five unresolved
CV rows, peak rather than post-GC retained memory, and no isolated adapter-only timing probe in the
physical matrix. The next action is to keep the structural fast paths, archive this completed plan,
and require a new attributed optimization plan before further whole-frame performance work.
Production source and public/protected API did not change in Phase 4: the diff is limited to the Demo
fixture, benchmark-only runner, report contracts, tests, and documentation, so no published-artifact
Changeset or API documentation field applies.

Closeout verification on 2026-08-22 passed all 22 report-tool tests, the focused app benchmark and
registry contract tests, both Benchmark APK assemblies, documentation structure/language/translation
checks, development-tooling isolation, release-intent verification with zero affected artifacts,
and the complete `qaQuick` gate. The controlled physical matrix is the applicable Phase 4 device
gate; no additional `qaFull` slice applies because this phase changes no published production
behavior, while the accepted Phase 3 connected geometry/lifecycle matrix remains unchanged. No
architecture invariant changed in Phase 4, so accepted ADR-0016 and ADR-0017 remain historical
records; current capability, migration, renderer, roadmap, and performance guidance own the durable
conclusions.

## Required acceptance matrix

| Layer | Minimum required coverage |
| --- | --- |
| DSL and transport | Every new builder, default, legal/illegal combination, endpoint, Grid, CircularFlow, physical/logical direction, wrap behavior, serialization, accepted ergonomics, and repaired structural receiver boundary |
| Renderer JVM/Robolectric | Exact LayoutParams/bounds; diff classification; no-op/write suppression; helper lifecycle; rollback; density and RTL |
| Physical device | Native solver geometry, every helper, warning-free rapid switching, detach/reattach, configuration change, and recovery after rejection |
| Visual | Light/dark, LTR/RTL, phone/tablet, portrait/landscape, and font scale `1.0`, `1.3`, and `2.0` with no clipping or ambiguous fixture |
| Stress | 1,000 equal, scalar, topology, helper, and invalid/valid operations with bounded IDs, Views, diagnostics, callbacks, allocations, and retained memory |
| Compatibility | Minimum API 24, current primary device API, selected latest API emulator/device, released ViewCompose baseline, and accepted AndroidX version |
| Performance | Direct AndroidX, released ViewCompose baseline, and candidate at 10/50/100 nodes across stable, scalar, helper, topology, and environment workloads |

## Performance protocol and initial budgets

Required metrics include graph compilation/commit count and time, measure/layout count and time,
requestLayout count, helper create/remove count, allocation count and bytes, retained helpers/Views
after GC, frame P50/P95/P99, run stability, and failure/rollback cost.

Initial budgets, frozen or tightened in Phase 0:

1. content-only and equal submissions: zero adapter graph compilation, native commit, helper
   mutation, and adapter-owned constraint allocation;
2. scalar update: zero helper creation/removal, zero live-layout clone, one native commit at most,
   and one layout request at most;
3. 1,000 operations: no monotonic growth in helper Views, IDs, diagnostics, callbacks, scratch
   storage, or retained graph revisions;
4. steady layout P95/P99: no material regression against the published ViewCompose baseline under
   the repository noise policy;
5. an isolated 50-node equal/content-only adapter probe: P95 overhead versus paired direct AndroidX
   is no more than the greater of 10% or 0.5 ms, because the adapter owns no graph work;
6. an isolated 50-node scalar adapter probe: P95 overhead versus paired direct AndroidX is no more
   than the greater of 20% or 1.0 ms, with topology/helper frame results gated longitudinally until
   their complete atomic work is separately attributed;
7. the full-frame 50-node stable/content and scalar candidate must close at least 25% of the
   released-ViewCompose-to-direct-AndroidX P95 gap before this plan claims an optimization win;
   otherwise the result is retained as `no material change`, `mixed`, or `inconclusive` and the
   remaining attributed gap stays explicit; and
8. opposing median, tail, allocation, correctness, or memory movement is classified `mixed`, not
   accepted as a performance win.

Raw benchmark output never closes a phase. Every accepted result records comparison context,
absolute values, normalized change, one conclusion classification, limitations, and next action.

### First-release optimization evidence carried forward

The first-release safety work tested several speculative reductions in ConstraintSet preparation,
LayoutParams assignment, parent-data preflight, and snapshot work. None produced a repeatable
whole-matrix win: favorable medians were accompanied by neutral or worse P95/P99 directions, so
every candidate was reverted. Phase 1 must therefore start from attributed structural counters and
traces rather than restoring any rejected patch by inspection.

The Android 9 controls also showed that `CompilationMode.None` reports `verify` while remaining
sensitive to JIT/code placement, package reinstall behavior, and run order. Phase 0 must preserve
the immutable first-release raw results, verify that target/candidate/direct-native APKs are
package- and build-mode-matched, record the actual compilation result, and require per-method CV
acceptance plus adjacent reruns for unstable methods before directional claims. A root clock lock
does not make an unstable frame sample conclusive, and a direct-native matrix cannot substitute for
the longitudinal released-ViewCompose comparison.

## Release relationship

1. The first-release Central publication and Git tag are complete; Phase 0 remains active and
   `- None.` because it changes planning, tests, and baseline contracts only.
2. Production implementation may start only after the Phase 0 branch is reviewed and the Demo
   fixed-clock baseline PR `#111` has landed ahead of it in main history.
3. The first production pull request replaces `- None.` with every immutable Changeset it owns; from
   then on, this plan blocks only the affected follow-up release train until archived.
4. Do not amend the immutable first-release Changeset or fold post-release optimization into the
   source-freeze commit.
5. If an urgent first-release fix appears after tagging, handle it as an independent fix plan and
   Changeset instead of mixing it into this expansion.

## Risks and controls

| Risk | Control |
| --- | --- |
| Optimization diverges from topology commit | Differentially run identical graphs through update classes and compare final LayoutParams, bounds, helper state, and diagnostics |
| Parity copies imperative AndroidX concepts | Require a ViewCompose-native declarative use case and reject APIs already expressed by state, Modifier, or generic animation |
| Grid/CircularFlow introduce new lifecycle leaks | Put them under the same registry, rollback, removal, and 1,000-switch gates as first-release helpers |
| Allocation wins worsen solver tails | Gate P50/P95/P99, measure/layout count, allocations, retained state, and correctness together |
| Exposed optimization controls harm defaults | Publish no raw bitmask and require a repeated measured win plus exact fallback semantics |
| Full screenshot matrices become noisy | Separate deterministic static visuals from physical-device lifecycle tests and record device/theme/font/locale metadata |
| Follow-up work contaminates the release window | Enforce the activation trigger and keep Changesets at `- None.` until the first release is tagged |

## Completion criteria

This plan is complete only when all of the following are true:

1. all five update classes have exact structural counters and accepted behavior;
2. content-only/equal updates do zero adapter graph work and scalar/topology paths meet budgets;
3. every required parity addition is implemented or explicitly rejected with evidence;
4. Grid and CircularFlow share the single helper owner and pass lifecycle, rollback, stress, and
   retained-state gates;
5. every public addition has Q3 documentation, canonical KDoc, compiled samples, module guidance,
   and exact automated evidence;
6. the structural DSL Scope audit is complete, every accepted repair has positive and
   compiler-negative evidence, and compliant or intentionally distinct scopes remain unchanged;
7. the full device, visual, configuration, and stress matrix passes without unexpected warnings;
8. direct-native/published-baseline/candidate results support every retained performance claim;
9. required Changesets and release validations pass;
10. durable conclusions are present in active architecture, module, migration, roadmap, Demo, and
   performance documents with current Chinese mirrors; and
11. this plan is archived before its affected artifacts are uploaded.

## Planning estimate

For one engineer after the first-release train, the estimate is approximately 9--14 engineering
weeks:

| Work | Estimate |
| --- | ---: |
| Phase 0: published-baseline refresh, Scope audit, and API freeze | 1--1.5 weeks |
| Phase 1: diff and allocation convergence | 2--3 weeks |
| Phase 2: high-value parity | 2.5--4 weeks |
| Phase 3: Demo and complete visual/configuration matrix | 1.5--2.5 weeks |
| Phase 4: benchmark and release closeout | 1--2 weeks |

Some implementation may overlap after Phase 0, but acceptance remains ordered: correctness
invariants cannot be relaxed for speed, and a performance claim cannot precede reproducible
direct-native evidence.
