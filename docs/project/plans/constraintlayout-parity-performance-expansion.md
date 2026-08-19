# ConstraintLayout Parity and Performance Expansion Plan

## Status

Active as a deliberately deferred post-release plan. Planning and baseline preservation are
complete; production implementation has not started and must not start until the
[ConstraintLayout first-release hardening plan](../../archive/constraintlayout-native-engine-hardening.md)
is complete and archived. Production implementation must still wait until its release window has
ended with a published Central release and tag.

This plan is not a first-release blocker while its Maven release changeset list remains `- None.`.
Once production implementation begins and immutable Changesets are added, it becomes the sole
active owner and release gate for the affected follow-up artifacts.

This plan is canonical English-only under the documentation-governance policy. Durable capability,
performance, compatibility, and operational conclusions must move into the owning active documents,
with required Simplified Chinese mirrors, before the plan is archived.

Last verified: 2026-08-20.

Activation trigger: the first-release hardening plan is archived, the corresponding Maven Central
release and Git tag are complete, and the release owner explicitly reopens ConstraintLayout
development.

Next action: remain at `- None.` through the first-release train. After activation, complete Phase 0
by rebasing every benchmark and parity decision on the published hard-cut API and accepted AndroidX
version, then freeze the structural DSL Scope inventory before proposing any cross-module source
change.

## Maven release changesets

- None.

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
| AndroidX parity | Chain endpoints, endpoint margins, baseline margins, wrap behavior, physical anchors/directions, Guideline RTL policy, Grid, and CircularFlow | Existing retained capabilities must already be correct before release |
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
2. Baseline-to-baseline margin and gone margin with exact pixel assertions.
3. `wrapBehaviorInParent` through a typed enum covering horizontal-only, vertical-only, included,
   and skipped behavior supported by the selected AndroidX baseline.
4. Physical left/right anchors and Barrier directions for deliberate absolute-layout migration;
   logical start/end remain the default and examples explain RTL consequences.
5. Explicit Guideline RTL policy where supported by the selected AndroidX API.
6. Grid and CircularFlow helpers with typed parameters, validation, compiled Q3 samples, Demo
   scenes, renderer tests, device tests, and helper-lifecycle stress.
7. A typed container optimization policy only when at least one non-default policy has a
   reproducible benefit without correctness regression. Never expose AndroidX's raw integer
   bitmask.

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

The audit must explicitly classify non-structural builders instead of forcing them into this
contract. Draw/effect/lifecycle/value builders and `NavGraphBuilder` retain their own semantic DSL
markers or builder rules unless a concrete receiver collision is reproduced. Existing public scope
names and member syntax remain unchanged when they already satisfy the contract; consistency alone
does not justify a mass rename or source break.

ConstraintLayout ergonomics are reviewed only after this audit freezes the common contract:

1. no-argument or destructured auto references may be added only if deterministic identity,
   diagnostics, reusable ConstraintSet mapping, and save/reorder behavior remain obvious;
2. `Dimension.fillToConstraints`, percent, fixed, ratio, and constrained-wrap sugar may wrap the
   accepted algebra but cannot restore contradictory parallel fields;
3. Compose-style anchor objects or `linkTo` may be additive only when they materially reduce code
   without weakening horizontal/vertical/baseline target typing; and
4. the XML-friendly `startToStart`/`topToBottom` family remains supported. Compose naming is not a
   goal by itself.

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
6. Add failing compiler-safety, performance-structure, and parity geometry tests before production
   implementation.
7. Freeze absolute and normalized candidate budgets under the protocol below.

Exit criteria: reproducible published-baseline fixtures; reviewed API table and structural Scope
inventory; accepted parity and ergonomics scope; failing tests for every target behavior; and no
unresolved ownership, coordinate, lifecycle, receiver, failure, or performance field.

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

### Phase 2: high-value AndroidX parity

Planning estimate: 2.5--4 engineering weeks.

1. Add chain endpoints and margins with parent, child, Guideline, and Barrier targets.
2. Add baseline margins, wrap behavior, physical anchors/directions, and Guideline RTL policy.
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
5. 50-node scalar and topology adapter overhead versus paired direct AndroidX: no more than the
   greater of 10% or 0.5 ms at P95 unless Phase 0 accepts a stricter evidence-backed budget; and
6. opposing median, tail, allocation, correctness, or memory movement is classified `mixed`, not
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

1. This plan remains active and `- None.` during the first-release window, so it does not block
   unrelated publication under the active-plan archival gate.
2. Production implementation may start only after the first-release Central publication and Git
   tag are complete.
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
