# ConstraintLayout Native Engine Hardening and Parity Plan

## Status

Active. The 2026-08-18 implementation, test, Demo, device, and upstream-capability audit is
complete. Production hardening has not started. This plan is the only active owner of
ConstraintLayout renderer correctness, reconciliation performance, high-value AndroidX parity,
and the corresponding Demo and acceptance matrix.

The existing `0.1.0-alpha01` API is explicitly allowed to break. An unsuitable public contract or
renderer design must be replaced in one hard cut; this plan does not preserve a second engine,
compatibility feature flag, legacy execution branch, or indefinite deprecated alias merely to
avoid an Alpha-line migration.

This plan is canonical English-only under the documentation-governance policy. Durable API,
behavior, compatibility, migration, benchmark, and operational conclusions must move into the
owning active documents, with required Simplified Chinese mirrors, before the plan is archived.

Last verified: 2026-08-18.

Next action: complete Phase 0 by adding failing geometry and helper-lifecycle regressions, freezing
the hard-cut dimension and failure contracts, recording the renderer/helper-ownership ADR, and
capturing unchanged 10/50/100-node native and ViewCompose performance controls before replacing
the current reconciliation engine.

## Maven release changesets

- None.

## Objective

Make `viewcompose-constraintlayout-androidx` the highest-confidence native layout path for complex
ViewCompose screens by delivering all of the following together:

1. exact, warning-free AndroidX ConstraintLayout geometry for every supported anchor, dimension,
   chain, and helper contract;
2. one lifecycle owner for content IDs and every native helper View, including Guideline and
   Barrier;
3. a prevalidated immutable constraint graph and an atomic native commit that retains the last
   accepted layout after a rejected or failed update;
4. no constraint-graph work for content-only or semantically equal updates, and bounded
   topology/scalar update cost demonstrated against direct AndroidX controls;
5. a smaller and safer Alpha API in which invalid dimension combinations, raw ratio strings,
   unsupported `match_parent`, ambiguous IDs, and partial-link recovery are not representable or
   silently accepted;
6. high-value AndroidX parity without copying imperative or state APIs that ViewCompose already
   expresses declaratively; and
7. exact unit, renderer, device, screenshot, stress, and benchmark evidence that supports every
   retained capability and performance claim.

The plan optimizes the adapter around AndroidX ConstraintLayout; it does not introduce a second
constraint solver or claim that every screen should use ConstraintLayout instead of a simpler
container.

## Planning origin and ownership transfer

| Previous active location | Previous statement or responsibility | Status after this split |
| --- | --- | --- |
| [Unified roadmap](../roadmap.md), current baseline and capability matrix | Recorded anchors, dimensions, ConstraintSet, and virtual helpers as a landed baseline, with MotionLayout as the only named future decision | The basic DSL baseline remains landed. Renderer correctness, helper lifecycle, performance proof, and high-value native parity move here and remain incomplete until this plan closes. |
| [ConstraintLayout module manual](../../modules/viewcompose-constraintlayout-androidx/README.md) | Describes the Alpha API, helper set, reconciliation, failure behavior, and performance guidance | Remains the current public contract, but now records the observed Alpha limitations and links here. Every accepted phase must update it rather than leaving the plan as the only source of truth. |
| [Animation Compose-capability expansion](./animation-compose-capability-expansion.md) | Owns future bounds animation and explicitly rejects typed MotionLayout expansion without a separate requirement | Unchanged. ConstraintSet position/size animation uses that plan's accepted bounds model; this plan does not create MotionScene or a competing animation engine. |
| [Demo benchmark and verification harness rearchitecture](./demo-benchmark-verification-harness-rearchitecture.md) | Owns reusable scenario, screenshot, fixture, and benchmark harness infrastructure | Unchanged. This plan owns ConstraintLayout fixtures, assertions, budgets, and interpretation added through that harness. |
| Android Renderer transaction and failure documentation | Owns framework-wide renderer commit, rollback, and failure-report concepts | Remains authoritative. This plan must integrate ConstraintLayout with those contracts rather than invent a private incompatible failure model. |

## Accepted audit baseline

The baseline below is a qualification of the existing Alpha implementation, not a claim that the
entire module is unusable.

### Current strengths

1. The DSL and immutable specs cover logical edge anchors, baseline-to-edge links, circular
   placement, bias, ratio, fixed/wrap/fill dimensions, min/max/percent/constrained options,
   reusable ConstraintSet declarations, Guideline, Barrier, horizontal and vertical Chain, Flow,
   Group, Layer, and Placeholder.
2. String references map to stable generated View IDs, equal helper/spec assignments are detected,
   and multiple rebuild requests are coalesced before measure/layout.
3. The Demo contains ten interactive sections, and the focused 2026-08-18 run passed 25 JVM tests
   and six device tests on a Samsung SM-G991B / Android 13.

### Observed release blockers

1. A fresh Demo scroll emitted nine `ConstraintSet: id unknown UNKNOWN` warnings and one
   `Layer 'layer-helper' transform apply failed` warning caused by a null referenced View.
2. The Barrier marker overlapped both source text nodes even though its contract placed it after
   their end Barrier. The existing device assertion accepted the marker merely for remaining
   inside the container and near its top edge.
3. The basic `Bias` badge overlapped its explanatory copy. Advanced-anchor content was crowded,
   and the dimension fixture wrapped one identifier across several lines.
4. Only two renderer tests instantiate `DeclarativeConstraintLayout`; most focused JVM tests
   validate spec transport, wrappers, or metadata rather than solver geometry.
5. The device tests mostly assert visibility, state-text changes, or coarse movement. They do not
   prove exact baseline, circle, ratio, percent, chain, Flow, Placeholder, Layer, helper removal,
   rollback, RTL, font-scale, or screenshot behavior.
6. No ConstraintLayout-specific benchmark compares the ViewCompose adapter with direct AndroidX
   ConstraintLayout. The current repository therefore has no accepted evidence for adapter
   overhead or a “best-performing layout choice” claim.

### Root implementation risks

The current renderer creates a new `ConstraintSet`, clones the live layout, clears every child
entry, recreates constraints and helper metadata, and applies the complete set for each accepted
rebuild. That path allocates maps, lists, sets, dependency metadata, arrays, chain items, and a
new native set.

`clearChildConstraints` also clears managed Flow, Group, Layer, and Placeholder Views. Guideline
and Barrier are created by native `ConstraintSet.applyTo`, but the renderer's helper registry does
not own or prune those native Views. The native API adds missing Guideline and Barrier Views but
does not remove an old View merely because the new set omitted its ID. This makes full helper
lifecycle ownership and stale-child rejection mandatory rather than optional cleanup.

The catch around `ConstraintSet.applyTo` preserves the process but cannot make a partially mutated
layout transactional. The string-valued warning cache is also unbounded for a layout lifetime,
and repeated `doOnLayout` Layer callbacks can apply stale transforms after a newer graph is ready.

## Hard-cut mandate

The following decisions are authorized breaking changes. Phase 0 freezes exact names and migration
text, but implementation may not retain the rejected behavior in a hidden or compatibility path.

| Current contract or design | Required hard cut | Migration or replacement |
| --- | --- | --- |
| Live `ConstraintSet.clone(layout) -> clear all children -> applyTo(layout)` reconciliation | Replace with precompiled graph reconciliation and an explicit native commit; remove the old path in the same phase | No application migration; internal behavior becomes atomic and measurable |
| Helper ownership split between renderer-created Views and `ConstraintSet.applyTo`-created Guideline/Barrier Views | One registry owns creation, reuse, type, ID, references, and removal for every helper kind | No public migration; repeated helper-set switches must retain bounded child count |
| Log-and-skip missing links followed by partial application | Prevalidate the complete graph; reject the candidate graph and retain the previous accepted layout | Diagnostics report the rejected graph and reason; no partially accepted authoring error |
| `ConstraintDimension.MatchParent` | Remove from the Alpha API | Use opposing anchors plus match constraints; AndroidX explicitly rejects `match_parent` for ConstraintLayout children |
| `FillToConstraints` plus independent `widthMin`, `widthMax`, `widthPercent`, `constrainedWidth`, and vertical equivalents | Replace with one mutually exclusive dimension algebra that owns wrap, constrained wrap, match-constraint mode, min/max, and percent together | A migration table maps every legal old combination; contradictory combinations have no compatibility representation |
| Raw `dimensionRatio: String?` | Replace with a typed ratio value containing positive width/height terms and an optional constrained side | Compile to AndroidX syntax only at the renderer boundary; invalid or zero terms fail before mutation |
| Empty IDs, duplicate child IDs, child/helper collisions, duplicate helper IDs across types, and circular/helper self-reference reaching native apply | Validate deterministically before commit | Builder-time failures where possible; runtime spec validation for externally constructed transport values |
| Circle plus competing edge constraints with undocumented winner behavior | Make circular placement mutually exclusive with edge positioning in one item | Use separate ConstraintSet states when switching between circle and edge placement |
| String-valued lifetime warning cache | Replace with bounded structured diagnostics keyed by graph revision, node/helper identity, and reason | Repeated equivalent failures may deduplicate within one rejected revision; accepted revisions release old diagnostic keys |
| Unconditional full rebuild after every ConstraintLayout bind | Diff accepted graph, environment, topology, and scalar values; route to no-op, scalar, environment, or topology commit | Equal and content-only changes perform zero adapter graph rebuilds |
| `doOnLayout` queues for Layer transform application | One generation-checked post-layout/pre-draw application point owned by the committed graph | Stale callbacks cannot mutate a later graph; missing references reject before commit |
| Legacy and candidate reconciliation engines running behind a flag | Prohibited | Tests and benchmarks compare Git revisions or separate APKs, not two production branches |

Because the affected artifacts are published Alpha modules, hard cuts still require Q-level
classification, canonical KDoc, compiled samples, API validation, module and migration
documentation, and breaking Changesets. Alpha status permits correction; it does not waive release
discipline.

### Provisional hard-cut API shape

The semantic shape below is part of the plan. Phase 0 may improve names after API-quality review,
but it may not restore the rejected independent fields or an untyped escape hatch:

```kotlin
sealed interface ConstraintDimension {
    data object WrapContent : ConstraintDimension
    data object ConstrainedWrapContent : ConstraintDimension
    data class Fixed(val value: UiDp) : ConstraintDimension
    data class MatchConstraints(
        val mode: ConstraintMatchMode = ConstraintMatchMode.Spread,
        val min: UiDp? = null,
        val max: UiDp? = null,
    ) : ConstraintDimension
}

sealed interface ConstraintMatchMode {
    data object Spread : ConstraintMatchMode
    data object Wrap : ConstraintMatchMode
    data class Percent(val fraction: Float) : ConstraintMatchMode
}

enum class ConstraintRatioSide { Width, Height }

data class ConstraintRatio(
    val width: Float,
    val height: Float,
    val constrainedSide: ConstraintRatioSide? = null,
)

data class ConstraintChainEndpoint(
    val target: ConstraintAnchorTarget,
    val margin: UiDp = UiDp.Zero,
)
```

`ConstraintItemSpec.width` and `height` own all dimension semantics. The old width/height
min/max/percent/constrained fields disappear. Baseline-to-baseline becomes a
`ConstraintAnchorLink`, so ordinary and gone margins use the same validated link model as other
anchors. Horizontal and vertical chain builders accept explicit typed endpoints with parent-edge
defaults. Raw native ratio syntax, raw optimization bitmasks, and `MatchParent` have no replacement
escape hatch.

DSL builders fail immediately with `IllegalArgumentException` for deterministic local authoring
errors. A transport value constructed outside the DSL is validated during graph compilation; a
rejection is reported through the existing renderer failure/diagnostic boundary and preserves the
previous accepted layout. Neither path silently drops only the invalid link.

## Target architecture

### Immutable graph compilation

Introduce an internal renderer-neutral compilation result, provisionally named
`ResolvedConstraintGraph`, with these properties:

1. it contains the complete merged item/helper declaration after inline precedence is resolved;
2. all child/helper IDs are unique, non-empty, generation-stable, and resolved before mutation;
3. references, legal anchor planes, circle exclusivity, chain membership, helper self-reference,
   dimension combinations, ratios, ranges, and cycles are validated once;
4. dp values are resolved from one captured environment revision rather than repeated Local reads;
5. topology and scalar fingerprints are deterministic and independent of map implementation or
   declaration allocation identity; and
6. compilation produces either one accepted candidate or a structured rejection with no native
   View mutation.

The transport specs remain AndroidX-free. Android constants, View IDs, LayoutParams, and helper
instances stay in Android Renderer. The Phase 0 ADR must confirm that the UI Contract continues to
own renderer-neutral transport while `viewcompose-constraintlayout-androidx` owns the authoring
DSL; do not move AndroidX classes into a platform-neutral artifact.

### Single native helper registry

Create one `NativeConstraintHelperRegistry` or equivalent internal owner:

1. stable key is helper semantic ID plus approved helper kind;
2. every active Guideline, Barrier, Flow, Grid, CircularFlow, Group, Layer, and Placeholder has one
   registry entry and one stable View ID;
3. type changes are remove-and-create operations inside the native transaction, never ID reuse
   with a stale class;
4. inactive entries are removed before the committed graph becomes observable;
5. reference arrays are generation-owned and cannot retain a removed child;
6. Placeholder content transfer restores the previous child state before adopting the next child;
7. Group overlap precedence is declaration-order deterministic; and
8. Layer transforms run only after all referenced Views have completed the committed layout pass.

Do not rely on `ConstraintSet.applyTo` to create an unowned Guideline or Barrier as a side effect.

### Reconciliation classes

Each candidate graph resolves to exactly one internal update class:

| Update class | Required behavior |
| --- | --- |
| No-op | Same topology, scalar values, environment, and child identity; no ConstraintSet/LayoutParams allocation and no requestLayout from the adapter |
| Content-only | Constraint graph unchanged; native child content may request normal measurement, but the adapter performs no graph compilation or helper work |
| Scalar | Same nodes, helper kinds, and references; update only changed margins, bias, dimensions, visibility, transform, or helper scalar properties and issue at most one layout request |
| Environment | Re-resolve density/layout-direction-dependent values from the same semantic graph; preserve IDs and helper instances |
| Topology | Stage child/helper membership and the complete native graph, then commit once with rollback data |

### Native commit and rollback

The accepted implementation must not use the live layout as the source of truth for the next
constraint graph. Before mutation, it stages:

1. the target helper membership and references;
2. target LayoutParams or the complete native set;
3. generic Modifier-owned visibility, alpha, elevation, transforms, and accessibility state that a
   native ConstraintSet operation could otherwise overwrite; and
4. rollback data for every View or helper that the commit may touch.

After successful native application, publish the new accepted graph, release stale helpers, and
emit one success revision. On any failure, restore the previous LayoutParams, helper membership,
runtime properties, and accepted graph before reporting the failure. Phase 0 may select direct
LayoutParams mutation, a clean staged ConstraintSet, or a hybrid after a focused spike, but it may
not retain clone-and-clear of the live layout as the authoritative algorithm.

## Public capability decisions

### Required additions in this plan

1. Horizontal and vertical chains with explicit start/end targets, target sides, and endpoint
   margins rather than parent-to-parent hard-coding.
2. Baseline-to-baseline margin and gone margin with exact pixel assertions.
3. `wrapBehaviorInParent` through a typed enum, including horizontal-only, vertical-only, included,
   and skipped behavior supported by the selected AndroidX baseline.
4. Explicit match-constraint spread, wrap, and percent modes through the new dimension algebra.
5. Physical left/right anchors and Barrier directions for deliberate absolute-layout migration;
   logical start/end remain the default and examples must explain RTL consequences.
6. Guideline RTL policy when supported by the selected AndroidX API.
7. Grid and CircularFlow helpers with typed parameters, validation, compiled samples, Demo scenes,
   renderer tests, device tests, and helper-lifecycle stress.
8. A typed container optimization policy only if the benchmark phase demonstrates a repeatable
   use case. Never expose AndroidX's raw integer bitmask as the public contract.

### Intentionally omitted or delegated

1. `ReactiveGuide` and `SharedValues` are replaced by observable ViewCompose state selecting a
   candidate graph; adding a second mutable global propagation model is a non-goal.
2. `ConstraintLayoutStates` and StateSet are replaced by explicit state/environment-driven
   ConstraintSet selection in application code.
3. `ConstraintProperties`, XML ConstraintSet loading, imperative clear/connect calls, custom
   attributes, and ConstraintSet-owned alpha/rotation/visibility duplicate the declarative DSL or
   generic Modifier and are not copied.
4. `constraintTag` is omitted until a concrete query/state-selection requirement cannot use
   semantic key, layout ID, or test tag.
5. Container min/max dimensions continue through general layout modifiers unless native parity
   testing proves a semantic gap.
6. ConstraintSet transition animation delegates to the Animation plan's accepted bounds model.
7. MotionLayout, MotionScene, Carousel, MotionEffect, key cycles, and `OnSwipe` remain outside this
   plan. Existing raw Android host interop remains the escape hatch.

## Delivery phases

### Phase 0: contract, ADR, dependency, failing evidence, and budgets

Planning estimate: 4--6 engineering days.

1. Record an ADR for immutable graph compilation, complete helper ownership, atomic native commit,
   and the rejection of a dual reconciliation engine.
2. Assign Q levels and applicable contract fields to every changed public type. Treat the dimension
   algebra, ratio value, chain endpoints, physical anchors, wrap behavior, Grid, and CircularFlow as
   Q3 unless the API standard requires a stricter level.
3. Freeze the exact hard-cut API and a source-to-target migration table before production code
   changes.
4. Compare AndroidX ConstraintLayout `2.2.1` with the currently selected stable baseline, including
   `2.2.2`; hard-cut the dependency to the accepted version after focused compatibility tests.
5. Add failing renderer/device regressions for the observed Barrier overlap, Layer null-reference
   path, `id unknown` warnings, stale Guideline/Barrier removal, partial-apply rollback, and repeated
   set switching.
6. Capture unchanged direct-AndroidX and current-ViewCompose controls for 10/50/100-node stable,
   scalar, helper, and topology workloads before optimizing.
7. Freeze absolute and normalized performance budgets using the protocol below. A budget may be
   revised later only with an interpreted result and explicit rationale in the plan and performance
   documentation.

Exit criteria: reviewed ADR; accepted API/migration table; reproducible failing tests; reproducible
control fixtures; exact AndroidX version; and no unresolved unit, coordinate, failure, or ownership
field for a Phase 1 or Phase 2 API.

### Phase 1: helper lifecycle and geometry correctness hard cut

Planning estimate: 1.5--2.5 engineering weeks.

1. Replace split helper ownership with the single registry for the currently supported Guideline,
   Barrier, Flow, Group, Layer, and Placeholder types.
2. Stop clearing active helper entries as ordinary content children.
3. Remove every stale native helper View during one committed topology update and prove bounded
   child count after repeated type/ID/set changes.
4. Replace queued Layer callbacks with generation-owned post-layout application and guarantee that
   all references are non-null and belong to the committed container.
5. Make Placeholder release/adopt and Group overlap precedence deterministic across graph changes.
6. Eliminate all `ConstraintSet: id unknown` and supported-graph renderer warnings from the Demo and
   tests.
7. Correct the basic, Barrier, anchor, and dimension Demo geometry; do not weaken the fixture to
   hide a renderer error.

Exit criteria: all observed device defects have exact regressions; every current helper passes
add/remove/reorder/retype/detach/reattach tests; 1,000 alternating helper-set commits retain constant
helper count and memory; and the supported Demo emits zero ConstraintLayout warnings.

### Phase 2: public contract and atomic graph hard cut

Planning estimate: 2--3 engineering weeks.

1. Replace the dimension fields with the accepted mutually exclusive algebra and remove
   `MatchParent`.
2. Replace raw ratio strings with the typed ratio contract.
3. Enforce ID namespace, reference, cycle, anchor-plane, range, circle, chain, and helper validation
   before mutation.
4. Introduce immutable graph compilation and structured candidate rejection.
5. Implement the accepted native staging and rollback algorithm; remove clone-and-clear and the
   partial-link recovery path.
6. Preserve all generic Modifier-owned runtime properties across native commits without cloning
   stale constraint state.
7. Update DSL builders, renderer-neutral transport, binders, compiled Q3 samples, API dumps, KDoc,
   module documentation, migration documentation, and Changesets in the same hard cut.

Exit criteria: an invalid candidate changes no View bounds, LayoutParams, helper membership,
visibility, transforms, or accepted graph; valid candidates publish once; old API signatures and
old reconciliation classes are absent; and migration samples compile only against the new API.

### Phase 3: topology/scalar diff and allocation convergence

Planning estimate: 2--3 engineering weeks.

1. Cache the accepted semantic graph, resolved graph, environment revision, topology fingerprint,
   scalar fingerprint, and stable native ID mapping with bounded ownership.
2. Implement no-op, content-only, scalar, environment, and topology paths with structural counters.
3. Resolve environment once per graph compilation or environment update.
4. Reuse scratch storage only when ownership is local and bounded; do not introduce mutable global
   pools or cross-container caches.
5. Avoid helper property writes when the accepted scalar value is unchanged.
6. Ensure one logical update schedules at most one layout request and never queues stale work.
7. Record allocation and CPU attribution before changing a data structure solely for theoretical
   efficiency.

Exit criteria: content-only and equal updates perform zero adapter graph rebuilds and zero
adapter-owned constraint allocations; scalar updates create/remove no helper Views and do not clone
the live layout; topology work scales with the changed graph and satisfies the accepted benchmark
budgets.

### Phase 4: high-value AndroidX parity

Planning estimate: 2.5--4 engineering weeks.

1. Add chain endpoints and endpoint margins with parent, child, Guideline, and Barrier targets.
2. Add baseline margins, wrap behavior, physical anchors/directions, and Guideline RTL policy.
3. Add Grid and CircularFlow to the public DSL, transport, native registry, samples, Demo, Preview
   where supported, renderer tests, and device tests.
4. Test every new helper under addition, removal, replacement, invalid references, RTL, environment
   change, and 1,000-state-switch stress.
5. Evaluate optimization policies across the accepted matrix. Publish a typed expert policy only
   when at least one non-default policy has a reproducible benefit without correctness regression;
   otherwise retain the native default internally and document why no knob was added.

Exit criteria: every required addition has Q3 documentation and compiled samples, exact native
geometry tests, helper-lifecycle tests, Demo coverage, and device evidence; intentionally omitted
native APIs remain explicitly documented rather than appearing as accidental backlog.

### Phase 5: Demo, screenshot, configuration, and stress acceptance

Planning estimate: 1.5--2.5 engineering weeks.

1. Refactor the ten existing Demo sections into one-purpose fixtures whose labels never overlap the
   geometry being demonstrated.
2. Add separate Grid and CircularFlow scenes and a helper-lifecycle stress scene that exposes
   accepted graph revision, helper count, rebuild class, and structured failure without relying on
   raw log text.
3. Replace coarse assertions with exact or tolerance-bounded geometry for anchors, gone margins,
   baseline, circle, bias, ratio, percent, min/max, chain endpoints/styles/weights, every helper,
   Placeholder content, and Layer transforms.
4. Add deterministic light/dark, LTR/RTL, phone/tablet, portrait/landscape, and font-scale screenshot
   coverage. Use static screenshot fixtures for visual state and physical-device tests for native
   lifecycle and interaction.
5. Fail device acceptance on any unexpected `UIConstraintLayout`, `ConstraintSet`, or uncaught
   AndroidX warning from a supported fixture.
6. Exercise rapid toggles, child reorder, key reuse, detach/reattach, density and layout-direction
   change, process recreation where applicable, and failure followed by a valid retry.

Exit criteria: every public capability maps to at least one exact automated assertion and one
discoverable Demo fixture; the screenshot matrix is reviewed; all interactions remain warning-free;
and no device test can pass only because status text changed.

### Phase 6: benchmark, documentation, release, and archive closeout

Planning estimate: 1--2 engineering weeks.

1. Run the accepted current/candidate/direct-native performance matrix with identical APK mode,
   fixture revision, device, thermal/clock policy, compilation mode, and action protocol.
2. Interpret absolute and normalized results in the performance documentation, including median,
   tail, allocation, retained helper count, classification, limitations, and next action.
3. Run focused module tests, compiled samples, API checks, documentation structure, tooling
   isolation, release-intent verification, `qaQuick`, relevant `qaFull` device suites, and selected
   release build/benchmark gates.
4. Update the module manual, renderer manual, architecture/ADR, migration comparison, roadmap, Demo
   verification, performance documentation, English/Chinese mirrors, and final Changeset list.
5. Move this plan to `docs/archive/`, update both plan indexes, and preserve final evidence before
   any affected artifact is selected for Maven Central upload.

Exit criteria: all gates below pass; every durable conclusion has moved to its active owner; the
plan contains no unowned deferred release blocker; and the active plan no longer blocks publication.

## Required test and device matrix

| Layer | Minimum required coverage |
| --- | --- |
| DSL and transport | every builder, default, legal combination, illegal combination, ID collision, range, typed ratio, endpoint, Grid, and CircularFlow serialization |
| Renderer JVM/Robolectric | exact LayoutParams and measured bounds for every anchor/dimension; helper creation/removal/retype; rollback; runtime-property preservation; diff classification; density and RTL |
| Physical device | native solver geometry, Layer transforms, Placeholder hosting, Group precedence, helper lifecycle, warning-free rapid switching, detach/reattach, configuration change, and recovery after rejection |
| Visual | light/dark, LTR/RTL, phone/tablet, portrait/landscape, font scale `1.0`, `1.3`, and `2.0`; no clipping, overlap, ambiguous demonstration, or hidden target |
| Stress | 1,000 equal updates, scalar updates, set alternations, helper additions/removals, and invalid/valid retries with bounded IDs, Views, diagnostics, callbacks, and retained memory |
| Compatibility | minimum API 24, current primary device API, selected latest API emulator/device, accepted AndroidX version, and host/render-session replacement |

Exact geometry uses a one-pixel tolerance only where density rounding makes two adjacent integer
answers valid. Baseline equality, helper membership, child count, warning count, accepted revision,
and rollback identity use exact assertions.

## Performance protocol and initial budgets

The benchmark matrix contains direct AndroidX controls, the unchanged pre-hard-cut ViewCompose
revision, and the candidate. Each arm uses identical children, text, dimensions, helper graph,
device, build mode, compilation mode, actions, warmup, and clock/thermal policy.

Required graph sizes and actions:

1. 10, 50, and 100 ordinary constrained children;
2. stable content-only text/state update;
3. semantically equal ConstraintSet submission;
4. one margin, bias, or dimension scalar update;
5. one child or helper add/remove;
6. complete ConstraintSet topology switch;
7. Flow/Grid/CircularFlow reference update; and
8. density and RTL environment update.

Required metrics include graph-compilation count/time, native commit count/time, measure/layout
count/time, requestLayout count, helper create/remove count, Java/Kotlin allocation count and bytes,
retained helper/View count after GC, frame P50/P95/P99, run stability, and failure/rollback cost.

Initial budgets, frozen or tightened in Phase 0:

1. content-only and equal submissions: zero adapter graph compilation, zero native commit, zero
   helper mutation, and zero adapter-owned constraint allocation;
2. scalar update: zero helper creation/removal, zero live-layout clone, one native commit at most,
   and one layout request at most;
3. 1,000 equal or alternating accepted operations: no monotonic growth in helper Views, generated-ID
   maps, diagnostic keys, callbacks, or retained graph revisions;
4. steady layout frame P95 and P99: no material regression against the unchanged ViewCompose
   control under the repository's accepted noise policy;
5. 50-node scalar and topology adapter overhead versus the paired direct AndroidX operation: no
   more than the greater of 10% or 0.5 ms at P95, unless Phase 0 replaces this threshold with a
   stricter evidence-backed absolute budget; and
6. any median improvement accompanied by a tail, allocation, correctness, or retained-memory
   regression is classified `mixed`, not accepted as a performance win.

Raw benchmark output does not close a phase. Every accepted result records comparison context,
absolute values, normalized change, one conclusion classification, limitations, and the next
action in the active performance documentation.

## Documentation and release impact

Implementation is expected to affect at least:

- `viewcompose-ui-contract` for hard-cut immutable transport values;
- `viewcompose-constraintlayout-androidx` for the public DSL and compiled samples;
- `viewcompose-renderer-android` for graph compilation, native commit, helpers, diagnostics, tests,
  and performance counters; and
- the Demo and benchmark applications, which are not published artifacts but own acceptance
  fixtures.

Every pull request that changes published production source, publication inputs, or compiled API
samples adds immutable Changesets for every directly detected artifact. Do not hand-write reverse
dependency propagation. Breaking API phases must include migration text and replacement KDoc in
the same change.

The owning active documents must be updated phase by phase. The plan must not become a parallel
public manual, capability matrix, or performance source of truth.

## Risks and controls

| Risk | Control |
| --- | --- |
| A complete rewrite changes subtle AndroidX solver behavior | Land failing geometry tests before replacement; compare direct native controls for every retained contract |
| Hard-cut API migration touches many Demo/tests | Freeze one migration table, update all call sites atomically, and prohibit deprecated aliases that keep contradictory states alive |
| Rollback duplicates expensive View state | Snapshot only touched fields and prove bounded allocation; correctness takes precedence over speculative pooling |
| Direct helper ownership conflicts with AndroidX internal lifecycle | Validate against the accepted AndroidX source/version and device tests; do not depend on undocumented side effects without a pinned regression |
| Fine-grained scalar updates diverge from topology commits | Run the same graph through both paths in differential tests and compare final LayoutParams, bounds, helper state, and diagnostics |
| Exposing optimization controls harms defaults | Publish no raw bitmask; require a repeated measured win and exact fallback semantics |
| Screenshot matrices become noisy | Separate deterministic static visuals from physical-device lifecycle tests and record device/theme/font/locale metadata |
| Performance work optimizes allocations but worsens solver tail | Gate P50, P95, P99, measure/layout count, allocations, and retained state together; classify opposing movement as `mixed` |

## Completion criteria

This plan is complete only when all of the following are true:

1. the old clone-clear-partial-apply engine and old hard-cut API signatures are absent;
2. every supported graph is warning-free and every invalid graph leaves the previous accepted
   native state unchanged;
3. all helper types have one owner and pass lifecycle, stress, and retained-child gates;
4. all required native capability additions are implemented or explicitly rejected by the
   accepted scope decision;
5. every public contract has Q-level documentation, canonical KDoc, compiled samples, migration
   guidance, module documentation, and exact automated evidence;
6. Demo geometry and the complete visual/configuration matrix are reviewed and pass;
7. direct-native/current/candidate performance evidence satisfies the accepted budgets without a
   hidden tail, allocation, memory, or correctness regression;
8. required Changesets and release validation pass;
9. durable conclusions are present in active architecture, module, migration, roadmap, Demo, and
   performance documentation with current Chinese mirrors where required; and
10. the plan is archived and removed from the active-plan index before affected artifacts are
    uploaded to Maven Central.

## Planning estimate

For one engineer, the current estimate is approximately 11--18 engineering weeks:

| Work | Estimate |
| --- | ---: |
| Phase 0: contracts, ADR, failing evidence, controls | 0.8--1.2 weeks |
| Phase 1: helper correctness | 1.5--2.5 weeks |
| Phase 2: API and atomic graph hard cut | 2--3 weeks |
| Phase 3: diff and allocation convergence | 2--3 weeks |
| Phase 4: high-value parity | 2.5--4 weeks |
| Phase 5: Demo and device/visual matrix | 1.5--2.5 weeks |
| Phase 6: release closeout | 1--2 weeks |

Some phases may overlap after Phase 0, but acceptance remains ordered: performance cannot excuse a
correctness defect, and additional parity cannot land on an unowned or non-transactional helper
engine.
