# ConstraintLayout First-Release Hardening Plan

## Status

Active and release-blocking. Production hardening started on 2026-08-18. The hard-cut transport and
DSL source, immutable graph compiler, complete helper registry, clean-set native commit/rollback,
bounded diagnostics, dedicated DSL Scope, axis-typed references, reference-based ConstraintSet,
Q3 samples, ADR, migration table, and first Changeset are now present. Pure DSL/graph tests, an
offline Android 36 + cached ConstraintLayout `2.2.1` source compilation, and a focused API 35
Robolectric run pass. The Gradle-managed ConstraintLayout scope and documentation gate now also
pass against AndroidX ConstraintLayout `2.2.2`, but no delivery phase is accepted yet: the complete
all-helper device matrix and interpreted performance controls remain release-blocking.

Topology/scalar optimization, high-value AndroidX parity, the complete configuration/screenshot
matrix, and performance-leadership evidence have been split into the active
[ConstraintLayout parity and performance expansion plan](./constraintlayout-parity-performance-expansion.md).
That follow-up owns no Changeset and must not begin production implementation until this plan is
archived and the ensuing release window has completed.

The existing `0.1.0-alpha01` API is explicitly allowed to break. An unsuitable public contract or
renderer design must be replaced in one hard cut; this plan does not preserve a second engine,
compatibility feature flag, legacy execution branch, or indefinite deprecated alias merely to
avoid an Alpha-line migration.

This plan is canonical English-only under the documentation-governance policy. Durable API,
behavior, compatibility, migration, benchmark, and operational conclusions must move into the
owning active documents, with required Simplified Chinese mirrors, before the plan is archived.

Last verified: 2026-08-19.

Next action: complete the remaining physical-device all-helper warning/geometry matrix, including
dark theme, RTL, and enlarged-font checks. Only after that evidence is accepted may the
performance-safety controls and Phase 3 release closeout begin.

## Maven release changesets

- `release/changes/20260818-constraintlayout-first-release-hardening.json` classifies the UI
  Contract and ConstraintLayout hard cuts, Android Renderer correctness work, Preview-only source
  migration, the UI Foundation scoped-container construction boundary, and AndroidX dependency
  pin. It remains part of the first-release train.

## Execution ledger

| Area | Current result | Acceptance state |
| --- | --- | --- |
| Q3 API and migration | Dedicated `ConstraintLayoutScope`; immutable post-content helper snapshot; horizontal/vertical/baseline target planes; reference-based `ConstraintSetBuilder.constrain`; typed dimensions and ratio; strict IDs; migration table and compiled samples | Implemented; positive sample compilation plus four compiler-negative contracts pass; broader release gates pending |
| Architecture | ADR-0016 accepted; module and renderer ownership docs plus Chinese mirrors updated | Implemented; `verifyDocumentationStructure` passed |
| Graph validation | Complete namespace/reference/anchor/range/circle/chain/helper preflight with structured rejection | Gradle-managed JUnit: 12/12 passed; broader matrix pending |
| Native ownership | One registry owns all six retained helper View kinds; child IDs enter AndroidX's index before `onViewAdded`; Barrier uses `Barrier.*` direction constants | Gradle-managed Robolectric: 16/16 passed against ConstraintLayout `2.2.2`, including exact Barrier geometry, 1,000 retypes, one-ID retyping across all six helper kinds, and all-kind declaration reorder identity; device matrix pending |
| Native transaction | Clean ConstraintSet application, state snapshots, rollback, accepted revision, bounded diagnostics | Invalid-candidate retention, injected mid-commit failure rollback, and valid retry passed |
| Helper overlays | Group/Layer/Placeholder child runtime state is restored before replacement and retained for rollback | Group removal/rebind, attached Layer transform/removal, Placeholder release, and Layer detach/reattach regressions passed |
| Dependency | Version catalog pinned to stable AndroidX ConstraintLayout `2.2.2` | Gradle runtime resolution confirmed `constraintlayout:2.2.2` and `constraintlayout-core:1.1.2`; focused compatibility run passed |
| Demo | Hard-cut API migration and terminology update; obvious chain-axis conflicts removed; the focused Guideline/Barrier fixture now exposes wrap-content source bounds, a visible 55% Guideline, and an End Barrier that follows the longest source | Focused light/LTR/default-font build, screenshot review, and 1/1 device geometry test accepted on SM-G991B; complete all-helper/configuration review pending |
| Performance safety | No new result | Required unchanged/direct-native controls pending |

The initial offline compilation against cached `2.2.1` remains only the defect-reproduction and
source-level sanity baseline. The accepted JVM evidence below comes from the repository's Gradle
8.13 configuration resolving ConstraintLayout `2.2.2`; it does not replace physical-device or
performance evidence.

### Focused test interpretation — 2026-08-18

The same hand-assembled Robolectric `4.14.1`, API 35, and cached ConstraintLayout `2.2.1` harness
first reproduced the Barrier defect: the expected trailing coordinate was `125 px`, while the
Barrier and its dependent marker both resolved to `0 px`. After replacing ConstraintSet anchor
constants with `Barrier.*` direction constants and assigning content View IDs before
`ConstraintLayout.onViewAdded` indexes them, the exact result became `125 px` for both nodes. The
coordinate error therefore changed from `125 px` (100% of the expected coordinate) to `0 px`; the
correctness conclusion is **improved**.

The focused renderer run passed 16/16 tests. Its 1,000-alternation control ended with exactly one
managed helper and two total children on every iteration, and bounded diagnostics stayed at or
below 64. Invalid candidates retained the accepted revision, LayoutParams, and bounds; Group
removal restored both the original runtime state and a newer declarative rebind. A later valid
candidate advanced the revision and produced exact geometry. Attached Layer transforms apply at
the owned pre-draw boundary, restore their child state on removal, cancel on detach, and reschedule
on reattach; a pending graph rebuild also survives detach/reattach. Placeholder removal releases
its content to the child's own constraints. An injected `LayoutParams` assignment failure after
helper staging restored the previous helper set, child state, and accepted revision, after which a
valid retry succeeded. One semantic helper ID retyped through Guideline,
Barrier, Flow, Group, Layer, and Placeholder without growing the child set. Reordering two
declarations in each retained helper kind preserved the same native helper instances, and removing
the declarations released both instances. Pure graph tests passed 12/12, UI Contract tests passed
3/3, and DSL tests passed 11/11.

The follow-up repository run used Gradle 8.13 and resolved `constraintlayout:2.2.2` plus
`constraintlayout-core:1.1.2`. `:viewcompose-ui-contract:test` passed 75/75 tests,
`:viewcompose-constraintlayout-androidx:testDebugUnitTest` passed 11/11, and
`:viewcompose-renderer-android:testDebugUnitTest` passed 451/451, including the 12 graph and 16
focused renderer cases. `verifyDocumentationStructure` passed in the same invocation. The formal
compatibility conclusion is **improved** and the earlier `2.2.1`-only limitation is retired.

This remains focused JVM correctness evidence, not phase acceptance. Robolectric prints
resource-name lookup diagnostics for generated IDs during the stress case, and the run does not
cover the exhaustive per-helper device/configuration matrix, device geometry, visuals, retained
memory, or performance. The next action is the physical-device and performance-safety gates.

### Focused Guideline/Barrier Demo interpretation — 2026-08-19

The previous fixture gave both source text Views opposing parent/Guideline anchors and
`MatchConstraints` width. Their right bounds therefore both ended at the Guideline regardless of
glyph length, so the End Barrier had no content-dependent movement and the short/long toggle could
not demonstrate its contract. The revised fixture uses constrained wrap content with a start bias,
shows each source bound with a Surface, draws the 55% Guideline, and constrains the marker to the
parent end.

On a Samsung SM-G991B running Android 13 at 1080 x 2400, light theme, LTR, and font scale 1.0, the
marker center moved from `596 px` with the short copy to `782 px` with the long copy: an absolute
`186 px` delta, equal to 17.2% of screen width. The old match-constraint geometry prescribed a
`0 px` content-dependent delta. Both screenshots showed the long source stopping before the
Guideline and the complete marker remaining inside the container. The focused instrumentation
test passed 1/1 and now asserts source-bound ordering, marker containment, summary growth, and a
movement greater than `8 dp`. The filtered manual/test log contained no unexpected
`ConstraintSet`, helper-layer, renderer-layout, or app-fatal entry. The visual and geometry
conclusion is **improved**.

This accepts only the focused Guideline/Barrier fixture in one default configuration. It does not
accept the complete Demo, all retained helpers, dark theme, RTL, enlarged fonts, memory, or
performance. The next action remains the full all-helper device/configuration matrix.

### DSL compile-safety interpretation — 2026-08-19

The ConstraintLayout module passed 17/17 tests under Kotlin `2.0.21`: 12 behavior tests and five
embedded compiler fixtures. The legal sample compiled with horizontal and vertical Guideline
targets plus `ConstraintSetBuilder.constrain(ref)`. Four formerly representable invalid forms now
fail compilation: vertical-only helper to a horizontal anchor, horizontal-only helper to a
vertical anchor, outer ConstraintLayout helper access from a nested Column receiver, and a String
passed to the reusable `constrain` builder. The invalid-form acceptance count therefore changed
from four of four under the generic target/type-alias surface to zero of four; the compile-safety
conclusion is **improved**.

Behavior tests also proved that nested ConstraintLayout scopes freeze independent immutable helper
snapshots and that a retained scope rejects late reference/helper declarations rather than silently
mutating an already emitted payload. `verifyDslApiContracts`, UI Foundation sample compilation,
Demo compilation, and Preview compilation passed in the same acceptance step. The limitation is
that compiler fixtures establish Kotlin source rejection, not IDE completion quality, Java source
ergonomics, device geometry, or runtime performance. The next action remains the all-helper device
matrix followed by the performance-safety controls.

## Objective

Make the first public release of `viewcompose-constraintlayout-androidx` safe to consume by
delivering all of the following together:

1. exact, warning-free AndroidX ConstraintLayout geometry for every capability retained in the
   first release;
2. one lifecycle owner for content IDs and every currently supported native helper View, including
   Guideline and Barrier;
3. a prevalidated immutable constraint graph and an atomic native commit that retains the last
   accepted layout after a rejected or failed update;
4. a smaller and safer Alpha API in which invalid dimension combinations, raw ratio strings,
   unsupported `match_parent`, cross-axis helper links, escaped helper receivers, ambiguous IDs,
   string/reference drift, and partial-link recovery are not representable or silently accepted;
5. no correctness, retained-memory, or material frame-time regression against unchanged
   ViewCompose and direct AndroidX controls;
6. exact unit, renderer, focused device, stress, and warning-free Demo evidence for every retained
   first-release capability; and
7. an archived release-blocking plan and clean source-freeze handoff that immediately opens the
   release window without beginning follow-up feature work.

The plan optimizes the adapter around AndroidX ConstraintLayout. It does not introduce a second
constraint solver or claim that every screen should use ConstraintLayout instead of a simpler
container.

## Split boundary and ownership transfer

| Owner | Responsibility after the split | Release effect |
| --- | --- | --- |
| This plan | Existing-helper correctness, warning-free geometry, API hard cut, graph validation, atomic commit/rollback, focused device evidence, performance-safety checks, documentation, Changesets, and archive handoff | Blocks the first release once it owns a production Changeset; completion opens the release window |
| [ConstraintLayout parity and performance expansion](./constraintlayout-parity-performance-expansion.md) | No-op/content/scalar/environment/topology optimization, broader AndroidX parity, Grid, CircularFlow, full visual/configuration coverage, performance-leadership evidence, and a repository-wide structural DSL Scope consistency audit | Remains `- None.` during the first-release train and begins only after that train is tagged |
| [Unified roadmap](../roadmap.md) | Current capability state and the two-stage next-focus statement | Must name both plans and keep first-release work separate from post-release expansion |
| [ConstraintLayout module manual](../../modules/viewcompose-constraintlayout-androidx/README.md) | Current public API, behavior, limitations, migration, and performance guidance | Must receive durable conclusions from each accepted phase rather than leaving them only in a plan |
| [Animation Compose-capability expansion](./animation-compose-capability-expansion.md) | Bounds animation and shared-motion decisions | Unchanged; neither ConstraintLayout plan introduces MotionScene or a competing animation engine |
| [Demo benchmark and verification harness rearchitecture](./demo-benchmark-verification-harness-rearchitecture.md) | Reusable scenario, screenshot, fixture, and benchmark infrastructure | Unchanged; this plan owns only the ConstraintLayout fixtures, assertions, and interpreted evidence added through that harness |
| Android Renderer transaction and failure documentation | Framework-wide renderer commit, rollback, and failure-report concepts | Remains authoritative; this plan integrates with those contracts instead of creating a private incompatible failure model |

The split is a real release boundary, not an acceptance loophole. Any defect that can produce wrong
bounds, stale helpers, partial native state, unbounded retained state, unsupported-graph warnings,
or a misleading first-release API remains owned here even if fixing it requires work originally
expected in the follow-up. Conversely, a parity addition or optimization that is not required to
make the retained first-release surface correct moves to the follow-up and cannot delay this
release.

## Accepted audit baseline

The baseline below qualifies the existing Alpha implementation; it does not claim the entire
module is unusable.

### Current strengths

1. The DSL and immutable specs cover logical edge anchors, baseline-to-edge links, circular
   placement, bias, ratio, fixed/wrap/fill dimensions, min/max/percent/constrained options,
   reusable ConstraintSet declarations, Guideline, Barrier, horizontal and vertical Chain, Flow,
   Group, Layer, and Placeholder.
2. String references map to stable generated View IDs, equal helper/spec assignments are detected,
   and multiple rebuild requests are coalesced before measure/layout.
3. The Demo contains ten interactive sections, and the focused 2026-08-18 run passed 25 JVM tests
   and six device tests on a Samsung SM-G991B / Android 13.

### Observed first-release blockers

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
   prove exact geometry, helper removal, rollback, RTL, font-scale, or warning-free behavior.
6. No ConstraintLayout-specific control proves that the hard cut avoids a material regression or
   unbounded helper retention. The first release therefore needs a safety comparison, although the
   full optimization and performance-leadership claim belong to the follow-up.

### Root implementation risks

The current renderer creates a new `ConstraintSet`, clones the live layout, clears every child
entry, recreates constraints and helper metadata, and applies the complete set for each accepted
rebuild. That path allocates maps, lists, sets, dependency metadata, arrays, chain items, and a new
native set.

`clearChildConstraints` also clears managed Flow, Group, Layer, and Placeholder Views. Guideline
and Barrier are created by native `ConstraintSet.applyTo`, but the renderer's helper registry does
not own or prune those native Views. The native API adds missing Guideline and Barrier Views but
does not remove an old View merely because the new set omitted its ID. Full helper lifecycle
ownership and stale-child rejection are therefore first-release requirements.

The catch around `ConstraintSet.applyTo` preserves the process but cannot make a partially mutated
layout transactional. The string-valued warning cache is unbounded for a layout lifetime, and
repeated `doOnLayout` Layer callbacks can apply stale transforms after a newer graph is ready.

## Hard-cut mandate

The following decisions are authorized breaking changes. Phase 0 freezes exact names and migration
text, but implementation may not retain rejected behavior in a hidden or compatibility path.

| Current contract or design | Required hard cut | Migration or replacement |
| --- | --- | --- |
| Live `ConstraintSet.clone(layout) -> clear all children -> applyTo(layout)` reconciliation | Replace with precompiled graph reconciliation and an explicit native commit; remove the old path in the same phase | No application migration; internal behavior becomes atomic and measurable |
| Helper ownership split between renderer-created Views and `ConstraintSet.applyTo`-created Guideline/Barrier Views | One registry owns creation, reuse, type, ID, references, and removal for every retained helper kind | No public migration; repeated helper-set switches retain bounded child count |
| Log-and-skip missing links followed by partial application | Prevalidate the complete graph; reject the candidate graph and retain the previous accepted layout | Diagnostics report the rejected graph and reason; no partially accepted authoring error |
| `ConstraintDimension.MatchParent` | Remove from the Alpha API | Use opposing anchors plus match constraints; AndroidX rejects `match_parent` for ConstraintLayout children |
| `FillToConstraints` plus independent width/height min, max, percent, and constrained fields | Replace with one mutually exclusive dimension algebra that owns wrap, constrained wrap, match-constraint mode, min/max, and percent together | Map every legal old combination in migration documentation; contradictory combinations have no compatibility representation |
| Raw `dimensionRatio: String?` | Replace with a typed ratio value containing positive width/height terms and an optional constrained side | Compile to AndroidX syntax only at the renderer boundary; invalid or zero terms fail before mutation |
| Empty IDs, duplicate child IDs, child/helper collisions, duplicate helper IDs across types, and self-reference reaching native apply | Validate deterministically before commit | Builder-time failure where possible; runtime graph rejection for externally constructed transport values |
| Circle plus competing edge constraints with undocumented winner behavior | Make circular placement mutually exclusive with edge positioning in one item | Use separate ConstraintSet states when switching between circle and edge placement |
| `ConstraintLayoutScope` as a `UiTreeBuilder` type alias plus thread-local helper collector | Use a dedicated `@UiDslMarker` scope and freeze its helper NodeSpec after content evaluation through the UI Foundation scoped-container boundary | Ordinary widgets remain available; helper APIs cannot escape onto unrelated builders or leak across nested layout receivers |
| One generic anchor-target plane | Separate logical horizontal, physical vertical, and baseline capabilities in public target types | Cross-axis Guideline/Barrier links become compile errors; ordinary child references support all valid planes |
| `ConstraintSetBuilder.constrain(id: String)` beside separately created references | Accept the constraint-capable reference itself | Replace `constrain(ref.id)` or repeated string literals with `constrain(ref)`; the inline `Modifier.constrain(id)` XML-migration shortcut remains |
| String-valued lifetime warning cache | Replace with bounded structured diagnostics keyed by graph revision, identity, and reason | Deduplicate equivalent failures within one rejected revision; accepted revisions release old diagnostic keys |
| `doOnLayout` queues for Layer transforms | One generation-checked post-layout/pre-draw application point owned by the committed graph | Stale callbacks cannot mutate a later graph; missing references reject before commit |
| Legacy and candidate reconciliation engines behind a flag | Prohibited | Compare Git revisions or separate APKs; do not ship two production engines |

The affected artifacts are Alpha, but every public hard cut still requires Q-level classification,
canonical KDoc, compiled samples, API validation, module and migration documentation, and breaking
Changesets. Alpha status permits correction; it does not waive release discipline.

### Provisional hard-cut API shape

Phase 0 may improve names after API-quality review, but it may not restore the rejected independent
fields or an untyped escape hatch:

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
```

`ConstraintItemSpec.width` and `height` own all dimension semantics. The old independent
min/max/percent/constrained fields disappear. Raw native ratio syntax, raw optimization bitmasks,
and `MatchParent` have no replacement escape hatch.

DSL builders fail immediately with `IllegalArgumentException` for deterministic local authoring
errors. A transport value constructed outside the DSL is validated during graph compilation; a
rejection is reported through the existing renderer failure/diagnostic boundary and preserves the
previous accepted layout. Neither path silently drops only the invalid link.

## Target architecture

### Immutable graph compilation

Introduce an internal renderer-neutral result, provisionally `ResolvedConstraintGraph`, with these
properties:

1. it contains the complete merged item/helper declaration after inline precedence is resolved;
2. all child/helper IDs are unique, non-empty, generation-stable, and resolved before mutation;
3. references, legal anchor planes, circle exclusivity, chain membership, helper self-reference,
   dimension combinations, ratios, ranges, and cycles are validated once;
4. dp values are resolved from one captured environment revision;
5. topology and scalar fingerprints are deterministic and independent of allocation identity; and
6. compilation produces either one accepted candidate or a structured rejection without native
   View mutation.

The transport specs remain AndroidX-free. Android constants, View IDs, LayoutParams, and helper
instances stay in Android Renderer. The Phase 0 ADR must confirm that UI Contract continues to own
renderer-neutral transport while `viewcompose-constraintlayout-androidx` owns the authoring DSL.

### Single native helper registry

Create one `NativeConstraintHelperRegistry` or equivalent internal owner:

1. stable key is helper semantic ID plus approved helper kind;
2. every active Guideline, Barrier, Flow, Group, Layer, and Placeholder has one registry entry and
   one stable View ID;
3. type changes remove and recreate the helper inside the native transaction;
4. inactive entries are removed before the committed graph becomes observable;
5. reference arrays are generation-owned and cannot retain a removed child;
6. Placeholder content transfer restores the previous child state before adopting the next child;
7. Group overlap precedence is declaration-order deterministic; and
8. Layer transforms run only after every referenced View completes the committed layout pass.

Do not rely on `ConstraintSet.applyTo` to create an unowned Guideline or Barrier as a side effect.

### Native commit and rollback

The live layout is not the source of truth for the next graph. Before mutation, stage:

1. target helper membership and references;
2. target LayoutParams or complete native set;
3. generic Modifier-owned visibility, alpha, elevation, transforms, and accessibility state that
   native ConstraintSet work could overwrite; and
4. rollback data for every View or helper the commit may touch.

After a successful application, publish the new accepted graph, release stale helpers, and emit
one success revision. On failure, restore previous LayoutParams, helper membership, runtime
properties, and accepted graph before reporting the failure. Phase 0 may select direct
LayoutParams mutation, a clean staged ConstraintSet, or a hybrid after a focused spike, but may not
retain clone-and-clear of the live layout as the authoritative algorithm.

## Scope decisions

### Required in this first-release plan

1. Correct every currently exposed anchor, dimension, bias, ratio, circle, chain, and helper
   behavior retained after the hard cut.
2. Own Guideline, Barrier, Flow, Group, Layer, and Placeholder through one registry.
3. Replace the dimension and ratio contracts, validate all IDs/references/planes/ranges, and reject
   an invalid graph atomically.
4. Preserve generic Modifier-owned properties across successful commits and rollback.
5. Add exact regressions for every observed defect, warning-free focused Demo acceptance, bounded
   helper stress, and release-safety performance controls.
6. Update compiled Q3 samples, API dumps, KDoc, migration guidance, module manuals, roadmap,
   English/Chinese public documentation, and immutable Changesets in the same hard cut.

### Transferred to the post-release plan

1. Specialized no-op, content-only, scalar, environment, and topology fast paths beyond what is
   necessary to avoid a first-release material regression.
2. Chain endpoints and endpoint margins beyond the retained first-release parent-edge model.
3. Baseline margins, `wrapBehaviorInParent`, physical left/right anchors and Barrier directions,
   and explicit Guideline RTL policy.
4. Grid and CircularFlow public helpers.
5. A typed container optimization policy; raw AndroidX bitmasks remain prohibited.
6. The exhaustive light/dark, LTR/RTL, phone/tablet, portrait/landscape, and font-scale screenshot
   matrix and performance-leadership claim.

### Intentionally omitted or delegated

1. `ReactiveGuide` and `SharedValues` are replaced by observable ViewCompose state selecting a
   graph; no second mutable global propagation model is added.
2. `ConstraintLayoutStates` and StateSet are replaced by explicit state/environment-driven
   ConstraintSet selection in application code.
3. `ConstraintProperties`, XML ConstraintSet loading, imperative clear/connect, custom attributes,
   and ConstraintSet-owned alpha/rotation/visibility duplicate the declarative DSL or generic
   Modifier and are not copied.
4. `constraintTag` remains omitted until a concrete query requirement cannot use semantic key,
   layout ID, or test tag.
5. ConstraintSet transition animation delegates to the Animation plan's bounds model.
6. MotionLayout, MotionScene, Carousel, MotionEffect, key cycles, and `OnSwipe` remain outside both
   ConstraintLayout plans. Raw Android host interop remains the escape hatch.

## Delivery phases

### Phase 0: contract, ADR, failing evidence, and safety controls

Planning estimate: 4--6 engineering days.

1. Record an ADR for immutable graph compilation, complete helper ownership, atomic native commit,
   and rejection of a dual reconciliation engine.
2. Assign Q levels and applicable contract fields to every changed public type; freeze exact API
   names and a source-to-target migration table before production changes.
3. Compare the selected AndroidX ConstraintLayout dependency with the current stable baseline and
   hard-cut the dependency version only after focused compatibility tests.
4. Add failing renderer/device regressions for Barrier overlap, Layer null reference, `id unknown`
   warnings, stale Guideline/Barrier removal, partial-apply rollback, and repeated set switching.
5. Capture unchanged direct-AndroidX and current-ViewCompose controls for 10/50/100-node stable,
   scalar, helper, and topology smoke workloads.
6. Freeze safety budgets for warning count, retained helpers, allocations, frame tails, and native
   commit count. Budget revisions require interpreted evidence and rationale.

Exit criteria: reviewed ADR; accepted API/migration table; reproducible failing tests and control
fixtures; exact AndroidX version; and no unresolved unit, coordinate, failure, or ownership field
for a Phase 1 or Phase 2 API.

### Phase 1: helper lifecycle and geometry correctness hard cut

Planning estimate: 1.5--2.5 engineering weeks.

1. Replace split helper ownership with the single registry for Guideline, Barrier, Flow, Group,
   Layer, and Placeholder.
2. Stop clearing active helper entries as ordinary content children.
3. Remove every stale native helper during one committed topology update and prove bounded child
   count after repeated type, ID, and set changes.
4. Replace queued Layer callbacks with generation-owned post-layout application and guarantee all
   references belong to the committed container.
5. Make Placeholder release/adopt and Group overlap precedence deterministic across graph changes.
6. Eliminate all unexpected `ConstraintSet` and renderer warnings from supported Demo/test graphs.
7. Correct basic, Barrier, anchor, and dimension Demo geometry without weakening fixtures to hide
   renderer errors.

Exit criteria: every observed device defect has an exact regression; every retained helper passes
add/remove/reorder/retype/detach/reattach tests; 1,000 alternating helper-set commits retain
constant helper count and bounded memory; and the supported Demo is warning-free.

### Phase 2: public contract and atomic graph hard cut

Planning estimate: 2--3 engineering weeks.

1. Replace independent dimension fields with the accepted algebra and remove `MatchParent`.
2. Replace raw ratio strings with the typed ratio contract.
3. Enforce ID namespace, reference, cycle, anchor-plane, range, circle, chain, and helper validation
   before native mutation.
4. Introduce immutable graph compilation and structured candidate rejection.
5. Implement native staging and rollback; remove clone-and-clear and partial-link recovery.
6. Preserve all generic Modifier-owned runtime properties across commits and rollback.
7. Update DSL builders, renderer-neutral transport, binders, compiled Q3 samples, API dumps, KDoc,
   module and migration documentation, and Changesets in the same hard cut.
8. Replace the ConstraintLayout builder alias and thread-local collector with a dedicated marked
   scope, immutable post-content helper snapshot, typed anchor planes, and compiler-negative tests.

Exit criteria: an invalid candidate changes no View bounds, LayoutParams, helper membership,
visibility, transforms, or accepted graph; valid candidates publish once; old API signatures and
old reconciliation classes are absent; and migration samples compile only against the new API.

### Phase 3: release-readiness closeout and archive handoff

Planning estimate: 2--4 engineering days.

1. Run the focused safety matrix against unchanged ViewCompose and direct AndroidX controls and
   interpret absolute results, normalized change, conclusion, limitations, and next action.
2. Run focused module tests, compiled samples, API checks, documentation structure, tooling
   isolation, release-intent verification, `qaQuick`, and relevant `qaFull` device suites.
3. Update the module manual, renderer manual, ADR/current architecture, migration comparison,
   roadmap, Demo verification, performance documentation, and reviewed Chinese mirrors.
4. Confirm every production change is represented by immutable Changesets and that no follow-up
   task is required to make the retained first-release surface correct.
5. Move this plan to `docs/archive/`, update the active and archive indexes, and create the clean
   source-freeze handoff for release planning.

Exit criteria: all first-release gates below pass; durable conclusions have moved to active owners;
the plan is archived; the follow-up still owns no first-release Changeset; and the repository is
ready to enter the release window.

## Required first-release acceptance matrix

| Layer | Minimum required coverage |
| --- | --- |
| DSL and transport | Every retained builder/default/legal combination plus illegal dimension, ratio, ID, range, circle, helper, and reference cases; compiler-negative coverage for cross-axis targets, nested-receiver leakage, and string-based ConstraintSet entries |
| Renderer JVM/Robolectric | Exact LayoutParams and measured bounds for retained anchors/dimensions; helper create/remove/retype; rollback; runtime-property preservation; density and RTL |
| Physical device | Observed defect regressions, native solver geometry, Layer, Placeholder, Group, helper lifecycle, warning-free rapid switching, detach/reattach, configuration change, and valid retry after rejection |
| Demo/visual | Every retained public capability is discoverable; observed overlap/crowding defects are corrected; focused light/dark, LTR/RTL, and font-scale checks cover affected fixtures |
| Stress | 1,000 helper-set alternations and invalid/valid retries with bounded IDs, Views, diagnostics, callbacks, graph revisions, and retained memory |
| Compatibility | Minimum API 24, current primary device API, selected latest API emulator/device, accepted AndroidX version, and host/render-session replacement |
| Performance safety | Current/candidate/direct-native controls at 10/50/100 nodes show no material frame-tail, allocation, retained-state, or native-commit regression under identical conditions |

Exact geometry uses a one-pixel tolerance only where density rounding permits adjacent integer
answers. Baseline equality, helper membership, child count, warning count, accepted revision, and
rollback identity use exact assertions.

## Performance-safety protocol

This plan does not claim that ViewCompose is faster than direct AndroidX. It protects the first
release from a material regression while the follow-up owns deeper optimization and leadership
evidence.

The safety matrix compares direct AndroidX, the unchanged pre-hard-cut ViewCompose revision, and
the candidate with identical children, dimensions, graph, device, build mode, compilation mode,
actions, warmup, and clock/thermal policy. It records stable content, one scalar change, one helper
add/remove, and one topology switch at 10, 50, and 100 nodes.

Minimum acceptance:

1. 1,000 accepted/rejected operations show no monotonic growth in helper Views, IDs, diagnostics,
   callbacks, or retained graph revisions;
2. stable and changed workloads show no material P95/P99 frame-time regression against the
   unchanged ViewCompose control under the repository noise policy;
3. the candidate performs no more than one native commit and one adapter-owned layout request for
   one accepted graph revision;
4. a median improvement accompanied by tail, allocation, memory, or correctness regression is
   classified `mixed`, not accepted as a win; and
5. raw output never closes the plan without comparison context, absolute results, normalized
   change, conclusion, limitations, and next action in active performance documentation.

## Release-window contract

Completion of this plan is the explicit trigger to enter the release window:

1. archive this plan only after all first-release completion criteria and durable documentation
   transfers are satisfied;
2. freeze production source and public documentation in a clean commit;
3. do not begin the parity/performance follow-up while release planning, preparation, Central
   validation, upload, and tagging are in progress;
4. generate the deterministic release plan from a clean, fetched checkout and review the exact
   direct and dependency-propagated artifact/version set;
5. close or archive every other active plan intersecting that selected set before upload; and
6. resume post-release ConstraintLayout work only after the Central release is published and the
   release tag is complete.

If first-release acceptance exposes a new correctness dependency, it remains in this plan. If it
exposes only an optional optimization or parity opportunity, record it in the follow-up without
moving its Changeset into the first-release train.

## Documentation and release impact

Implementation is expected to affect at least:

- `viewcompose-ui-contract` for hard-cut immutable transport values;
- `viewcompose-ui-foundation` for the Q3 modular scoped-container construction boundary;
- `viewcompose-constraintlayout-androidx` for the public DSL and compiled samples;
- `viewcompose-renderer-android` for graph compilation, native commit, helpers, diagnostics, tests,
  and safety counters; and
- the Demo and benchmark applications for acceptance fixtures.

Every pull request that changes published production source, publication inputs, or compiled API
samples adds immutable Changesets for every directly detected artifact. Do not hand-write reverse
dependency propagation. Breaking API work includes migration text and replacement KDoc in the same
change.

## Risks and controls

| Risk | Control |
| --- | --- |
| A hard cut changes subtle solver behavior | Land failing geometry tests before replacement and compare direct native controls for every retained contract |
| API migration touches many Demo/test call sites | Freeze one migration table, update call sites atomically, and prohibit deprecated aliases that keep contradictory states alive |
| Rollback duplicates expensive View state | Snapshot only touched fields and prove bounded allocation; correctness takes precedence over speculative pooling |
| Direct helper ownership conflicts with AndroidX internals | Pin the accepted AndroidX version and protect assumptions with source review plus device tests |
| The split defers a real first-release defect | Apply the release-boundary rule: wrong geometry, stale state, warnings, unbounded retention, atomicity failures, and misleading API contracts cannot move out |
| The first release starts follow-up work too early | Keep the follow-up at `- None.` and enforce the release-window source freeze until the release tag completes |
| Safety checks are misrepresented as performance leadership | State only no-material-regression conclusions here; require the complete direct-native matrix in the follow-up before making a leadership claim |

## Completion criteria

This plan is complete only when all of the following are true:

1. the old clone-clear-partial-apply engine and old hard-cut API signatures are absent;
2. every retained graph is warning-free and every invalid graph leaves previous accepted native
   state unchanged;
3. every retained helper type has one owner and passes lifecycle, stress, and retained-child gates;
4. observed Demo geometry defects have exact automated regressions and reviewed focused visuals;
5. every changed public contract has a Q level, canonical KDoc, compiled sample, migration guidance,
   module documentation, and exact automated evidence, including compiler-negative DSL safety;
6. the performance-safety comparison shows no material correctness, frame-tail, allocation, or
   retained-memory regression;
7. required Changesets and release validations pass;
8. durable conclusions are present in active architecture, module, migration, roadmap, Demo, and
   performance documentation with current Chinese mirrors where required;
9. the follow-up plan contains every deliberately deferred optimization/parity item, owns no
   first-release Changeset, and is not in production implementation; and
10. this plan is archived and removed from the active-plan index before any affected artifact is
    uploaded to Maven Central.

## Planning estimate

For one engineer, the first-release scope is approximately 5--7.5 engineering weeks:

| Work | Estimate |
| --- | ---: |
| Phase 0: contract, ADR, failing evidence, and controls | 0.8--1.2 weeks |
| Phase 1: helper correctness | 1.5--2.5 weeks |
| Phase 2: API and atomic graph hard cut | 2--3 weeks |
| Phase 3: release-readiness closeout | 0.4--0.8 weeks |

Phases may overlap after Phase 0, but acceptance remains ordered: API shape is frozen before the
hard cut, correctness precedes optimization, and the release window opens only after this plan is
archived.
