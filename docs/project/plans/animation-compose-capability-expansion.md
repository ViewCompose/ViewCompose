# Animation Compose-Capability Expansion Plan

## Status

Active. Phases 0 through 2 are complete and merged. Phase 3 implementation and acceptance are
complete on the candidate branch; pull-request delivery remains before Phase 4 begins.
This plan was split out on 2026-08-18 from the Animation follow-up in the unified roadmap and the
framework-wide physical-spring candidate recorded by the multi-design-system plan. Those documents
now point here; this file is the only active plan that owns the seven animation expansions defined
below.

The completed Animation and gesture milestone remains valid for its original baseline: timing and
sampling, target-as-state animation, `Animatable`, synchronized `Transition`,
`InfiniteTransition`, fade/size `AnimatedVisibility`, `Crossfade`, `animateContentSize`, native
Android interop, Demo, Preview, and regression coverage. This plan extends that baseline; it does
not reopen the completed architecture-split work as unfinished.

This plan is canonical English-only under the documentation-governance policy. Every durable API,
behavior, migration, tooling, dependency, and compatibility contract must move into active
architecture, guide, reference, and owning-module documentation before this plan is archived.

Last verified: 2026-08-23.

Next action: submit and merge the Phase 3 pull request, then begin Phase 4 without reopening the
accepted visibility scope, shared-clock, native-ownership, or transform-geometry contracts.

## Maven release changesets

- `release/changes/20260822-animation-physical-foundation.json` — Phase 1 hard cut and shared
  physical engine; accepted and merged.
- `release/changes/20260822-animated-content-phase2.json` — Phase 2 keyed content replacement,
  renderer ownership, and rollback contracts; accepted and merged.
- `release/changes/20260823-animated-visibility-phase3.json` — Phase 3 rich visibility primitives,
  type-safe content scope, shared descendant clock, and native interaction ownership; candidate
  changeset pending Phase 3 delivery.

## Objective

Expand ViewCompose animation in seven ordered capability groups:

1. physical spring, velocity continuity, decay, bounds, and structured animation results;
2. full content replacement transitions rather than alpha-only `Crossfade`;
3. slide, scale, transform-origin, and descendant visibility transitions;
4. generic, segment-aware, and seekable transitions;
5. layout-coordinate and size animation through `animateBounds`;
6. shared-element and shared-bounds transitions integrated with navigation; and
7. request-driven animation timeline inspection and controlled seeking in development tooling.

The result should cover the broadly reusable parts of Jetpack Compose Animation while preserving
ViewCompose's Android View renderer, transactional apply, structured ownership, deterministic
sampling, and optional-tooling isolation. API-name similarity is not sufficient: every retained
surface must have explicit cancellation, retargeting, layout, lifecycle, failure, accessibility,
and performance behavior.

## Scope decision and non-goals

This plan intentionally does not implement or schedule MotionLayout. The existing raw
`MotionLayoutView` and `animateToState`/`animateToStart`/`animateToEnd` host interop remains
available for applications that already own a `MotionScene`. A typed ViewCompose `MotionScene`
DSL, key-position/key-cycle model, `OnSwipe`, MotionCarousel, and MotionScene XML/JSON ownership
will be reconsidered only after a separate prioritized product requirement demonstrates that
seekable transitions plus `animateBounds` cannot express the required interaction.

The first plan release also excludes:

- symbol-for-symbol reproduction of every Compose Animation overload or experimental API;
- a general physics engine beyond spring and exponential/spline-style decay needed by animation
  and gesture handoff;
- spline keyframes, arbitrary path easing, arc motion, repeat start offsets, and a complete
  built-in wrapper catalog for every geometry type;
- color-space or gamma-correct color interpolation changes;
- lazy collection `animateItem` parity or a new collection diff owner;
- arbitrary vector-path morphing;
- cross-window, cross-Activity, or cross-process shared elements;
- a continuously active profiler, production animation telemetry, or tooling work on ordinary
  frames when no valid request is active; and
- redesign of navigation state, destination ownership, or predictive-Back commit semantics.

These exclusions may receive separate plans after this work, but they are not hidden completion
criteria for this plan.

## Planning origin and ownership transfer

| Previous active location | Previous responsibility | Status after this split |
| --- | --- | --- |
| [Unified roadmap](../roadmap.md), Animation and Milestone F | Recorded the completed first-round animation baseline and listed performance/examples as the next focus | Baseline remains completed. The seven capability expansions and their acceptance evidence move here; the roadmap retains a summary and link. |
| [Unified roadmap](../roadmap.md), ConstraintLayout | Listed MotionLayout interop as a future focus | Superseded. Raw host interop already exists, and no typed MotionLayout expansion is active. A future requirement needs a separate plan. |
| [Archived multi-design-system and high-fidelity theme plan](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/multi-design-system-high-fidelity.md), Phase 3 | Considered a bounded physical spring only when needed for component fidelity, then retained the duration-based approximation | That completed design-system decision remains valid. Framework-wide physical spring, decay, and velocity contracts move here; the design-system implementation consumes the accepted engine instead of reopening Phase 3. |
| [Archived Demo benchmark and verification harness plan](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/demo-benchmark-verification-harness-rearchitecture.md) | Established reusable Animation scenario and benchmark infrastructure | The infrastructure remains authoritative. This plan owns animation behavior, public APIs, domain fixtures, and acceptance interpretation added through it; remaining generic harness closure is tracked separately. |
| `docs/archive/` | Contains completed animation architecture, visibility, gesture, and static-preview evidence | Unchanged. Archived pages remain historical evidence and are not rewritten as current status. |

Navigation continues to own stack transactions, destination/session lifetime, system Back, and
predictive-Back commit behavior. Phase 6 owns only the shared visual-transition contract and its
integration with those authoritative navigation states. Preview and Studio tooling continue to own
their process and IDE implementation boundaries; Phase 7 owns the animation inspection protocol
and experience built through those boundaries.

No other active plan owns these seven capabilities. A prerequisite discovered elsewhere may remain
with its owning plan only when it is independently useful; this plan continues to own end-to-end
animation semantics and acceptance.

## Baseline and comparison reference

The upstream semantic comparison is frozen at stable Compose Animation `1.12.0`, released on
2026-08-12. The current repository catalog uses Compose UI `1.7.8` for its executable
Compose-backed Preview and migration samples. Compose `1.12.0` requires compile SDK 37 and AGP 9.2,
so the older local fixture is executable evidence only and cannot prove `1.12.0` parity. Exact
official sources and evidence limits are recorded in the
[animation migration comparison](../../migration/compose-animation.md).

The ViewCompose baseline is:

| Area | Current retained contract | Gap owned by this plan |
| --- | --- | --- |
| Timing and spring | Deterministic tween, fixed-duration bounded spring approximation, keyframes, snap, finite/infinite repeat | Physical termination, overshoot, velocity, decay, and bounds |
| `Animatable` | Last-mutation-wins `animateTo`/`snapTo`/`stop`, target/running state, stale-frame rejection | Velocity continuity, initial velocity, `animateDecay`, bounds, end reason, and result state |
| `Transition` | One shared timeline with `Float`, `Int`, packed-color, and `UiDp` channels | Public generic channel, segment policy, controlled seek state, and tooling projection |
| Visibility | Fade and measured-size expand/shrink with exit retention | Slide, scale, transform origin/alignment, descendant enter/exit, and content transition scope |
| Content replacement | Alpha-only `Crossfade` | Keyed outgoing/incoming content, pair-specific transform, size transform, slide, scale, interruption, and disposal |
| Layout motion | Measured-size `animateContentSize` | Position plus bounds animation across layout-coordinate changes |
| Shared motion | Navigation owns destination transforms but there is no generic shared-element coordinator | Shared element/bounds identity, overlay/ownership policy, and navigation progress integration |
| Tooling | Labels are retained but have no animation timeline behavior | Optional inspection protocol, timeline model, controlled seeking, and Studio UI |

The baseline is documented by the Animation and Animation Core module manuals and protected by
their current unit, sample, Demo, Preview, renderer, and device coverage. Phase 0 must identify
which existing contracts are additive and which require an alpha-line hard cut. Compatibility
adapters are allowed only when they preserve one authoritative behavior rather than running two
animation models.

## Delivery order and planning estimate

The order is intentional. Physics and results establish trustworthy motion state. Content and
visibility build reusable transition algebra. Public seeking then supplies progress control for
layout and navigation. Shared transitions depend on both seekable progress and bounds. Tooling is
last so it inspects stable public/runtime concepts rather than private prototypes.

| Phase | Relative difficulty | Primary benefit | Planning estimate for one engineer |
| --- | --- | --- | --- |
| 0. Contract, baseline, and budgets | High | Prevents incompatible physics, layout, ownership, and tooling contracts | 1--1.5 weeks |
| 1. Physical animation foundation | High | Correct gesture handoff, natural retargeting, and structured terminal behavior | 3--4 weeks |
| 2. Animated content replacement | High | Covers common screen, panel, and content-state changes without custom stacking | 2.5--4 weeks |
| 3. Rich visibility transitions | Medium-high | Adds broadly useful slide/scale and descendant choreography | 1.5--2.5 weeks |
| 4. Seekable transition model | High | Enables scrubbing, deterministic inspection, and progress-driven integration | 2--3.5 weeks |
| 5. Bounds animation | High | Covers many coordinated layout changes without MotionLayout | 2.5--4 weeks |
| 6. Shared element and bounds | Very high | Makes destination continuity reusable and navigation-aware | 3.5--5.5 weeks |
| 7. Animation timeline tooling | High | Makes complex channels and interruption behavior inspectable | 2.5--4 weeks |
| Release closeout | Medium-high | Completes docs, migration, performance, device, and publication evidence | 1--2 weeks |

The total planning range is approximately 19--31 engineering weeks. It is an ordering and sizing
aid, not a delivery commitment. A phase cannot trade away transaction safety, lifecycle release,
inactive-path performance, or deterministic tests merely to meet the estimate.

## Phase 0: Contract, baseline, and budget freeze

Before source changes, record one reviewed design decision covering:

1. exact stable Compose/AndroidX comparison versions and which semantics are intentionally
   different for an Android View renderer;
2. Q level and every applicable contract field for each proposed public/protected API, including
   units, coordinate spaces, lifecycle, cancellation, thread confinement, ownership, and failure;
3. physical spring equations, damping/stiffness units, vector velocity representation,
   visibility thresholds, equilibrium test, maximum-duration/failure guard, numeric precision, and
   deterministic clock behavior;
4. decay model, friction/velocity units, bounds collision, terminal reason, and gesture-to-animation
   handoff;
5. content/visibility transition composition rules, duplicate-channel precedence, measurement,
   clipping, z-order, identity, interruption, and subtree release;
6. transition seek ownership, frame-loop exclusion, legal progress/time range, retarget behavior,
   save/restore decision, and relationship to predictive Back;
7. bounds coordinate system, parent/scroll/RTL changes, measurement strategy, hit testing,
   accessibility bounds, and transactional rollback;
8. shared-key namespace, pairing, overlay, collision/missing-pair fallback, destination lifecycle,
   input/accessibility owner, and cross-session limit;
9. runtime-neutral inspection port and request protocol compliant with ADR-0009, including whether
   controlled live seeking requires a follow-up ADR; and
10. per-frame CPU, allocation, retained-tree, extra-measure, inactive-tooling, and request-driven
    tooling budgets with reproducible baseline scenarios.

Phase 0 must decide whether the current duration-bearing `SpringSpec` is hard-cut, renamed as a
legacy approximation, or preserved as a separate duration specification. A physical spring must
not silently reinterpret `durationMillis`, and two factories named `spring` must not produce
ambiguous termination semantics.

Exit criteria:

- the design is reviewed against the five-layer architecture, renderer transaction model,
  navigation ownership, API documentation standard, and development-tooling isolation ADR;
- each planned API has a Q level, owner module, compiled-sample destination, compatibility choice,
  and test category;
- baseline benchmarks and device fixtures are reproducible before implementation; and
- no Phase 1 production API is added while physical units or termination remain implicit.

Phase 0 was accepted on 2026-08-22. ADR-0019 freezes the hard-cut compatibility choice, physical
equations and units, typed velocity/result model, cancellation and bounds behavior,
content/visibility algebra, seek ownership, real layout geometry, session-scoped shared motion,
request-driven tooling, Q3 inventory, and budgets. The migration comparison records supported and
planned semantics against Compose `1.12.0`. The four revision-1 Xiaomi MI 6 / API 28 fixed-clock
workloads all have identical per-run frame counts and run-P50 CV `0.010..0.075`, below the `0.15`
ceiling. Their exact frame percentiles, heap values, APK identities, protocol, limitations, and
`inconclusive` normalized conclusion are interpreted in
[performance Section 2.4.8](../../tooling/performance.md#248-animation-revision-1-pre-physics-baseline).
The connected gate passed 134/137 tests before three warning-free ConstraintLayout checks read the
expected `lifecycle-invalid-grid` warning from the immediately preceding lifecycle case. All three
passed in isolation. The test harness was hard-cut from an unbounded global logcat read to a unique
per-test marker window, and the exact four-test class then passed 4/4 in the contaminating order.
This closes verification isolation without changing framework production behavior.

## Phase 1: Physical spring, velocity, decay, bounds, and results

Replace the fixed-duration approximation only through the compatibility decision from Phase 0 and
add one vector-aware physical execution model shared by deterministic sampling, `AnimatableCore`,
composition `Animatable`, gestures, and transition channels.

ADR-0019 freezes the Phase 1 behavior, while ADR-0020 supersedes its provisional single-type
generic vocabulary because packed values such as ARGB cannot represent signed multi-channel
velocity. The accepted Phase 1 result vocabulary is:

```kotlin
enum class AnimationEndReason {
    Finished,
    BoundReached,
    DurationLimitReached,
}

data class AnimationVelocity<V>(val valuePerSecond: V)

data class AnimationState<T, V>(
    val value: T,
    val velocity: AnimationVelocity<V>,
    val playTimeNanos: Long,
)

data class AnimationResult<T, V>(
    val endState: AnimationState<T, V>,
    val endReason: AnimationEndReason,
)

suspend fun Animatable<T, V>.animateTo(
    targetValue: T,
    animationSpec: FiniteAnimationSpec,
    initialVelocity: AnimationVelocity<V>? = null,
): AnimationResult<T, V>

suspend fun Animatable<T, V>.animateDecay(
    initialVelocity: AnimationVelocity<V>,
    animationSpec: DecayAnimationSpec,
): AnimationResult<T, V>

fun Animatable<T, V>.updateBounds(
    lowerBound: T? = null,
    upperBound: T? = null,
)
```

The exact overload set may be refined during implementation only within the accepted ADR contracts;
it cannot restore a duration spring, return interruption as a normal result, or weaken typed
velocity. The retained contract must:

1. capture the retained value and velocity atomically when `initialVelocity` is `null`, while an
   explicit initial velocity replaces only the captured velocity and a non-physical spec ignores it;
2. propagate interruption as coroutine cancellation while preserving last-mutation-wins
   ownership and the last committed value/velocity;
3. validate a candidate target animation before it replaces or cancels the active mutation;
4. validate construction before exposing state, and commit `snapTo`/`stop` as one atomic idle
   snapshot without cancelling active work when snap validation fails;
5. stop at bounds without publishing a sample outside accepted bounds;
6. expose an exact target at successful target completion while retaining physically meaningful
   terminal velocity rules;
7. keep deterministic explicit-time sampling for tests, Preview, transition seeking, and tooling;
8. resolve reduced-motion and `MotionScheme` roles without inventing a duration for an unbounded
   physical solve; and
9. allocate no avoidable objects per frame beyond the accepted converter/vector strategy.

Required evidence includes under-, critical-, and over-damped cases; overshoot; threshold and
maximum-duration termination; velocity continuation; decay; bounds; rapid retarget; external
cancellation; reduced motion; zero-distance motion; invalid configuration; float/vector numeric
stability; gesture handoff; and same-device performance against the duration-based baseline.

Implementation status on 2026-08-22: the production hard cut is implemented for separate value and
velocity domains, destination-buffer converters, analytic physical springs, exponential decay,
bounds, structured results, last-writer velocity continuation, finite target-as-state/visibility/
content-size contracts, transition channels, motion scaling, and the Android animated-size host.
ADR-0020 supersedes only ADR-0019's invalid single-type generic detail; all physical and ownership
decisions remain in force. Focused core, composition, renderer, and Demo tests accept analytic
damping and terminal reasons, velocity continuation, cancellation, bounds, converter-buffer
reuse, renderer physical sizing, and one-snapshot instant mutations. Invalid construction, target
replacement, and snap input are also accepted as fail-before-publication contracts: they neither
publish a partial state nor take ownership from the active mutation. A rooted-device gesture
handoff accepted a measured `2222 px/s` swipe and reached the bound with `BoundReached`, confirming
the Demo path uses physical decay rather than a separate gesture approximation. The final
`qaQuick qaPreview` gate passed together with 1,624 actionable tasks (156 executed and 1,468
up-to-date), so compilation, unit, API-sample, documentation-structure, and Preview coverage show
no local regression. This evidence supports the conclusion `no material change` for the covered
deterministic and visual-test contracts. The same-device fixed-frequency comparison is accepted in
[performance Section 2.4.9](../../tooling/performance.md#249-animation-revision-1-phase-1-physical-candidate):
all four rows pass, run-P50 CV is `0.002..0.012`, frame CPU is `improved`, and peak heap is
`no material change`. The warmer 36--38 degree environment, changed physical-settling frame counts,
and absence of direct energy measurement remain explicit limitations. Manual Xiaomi validation
then accepted critical Spring at exact targets with `Finished` and corrected the Demo's insufficient
decay velocity from `2.4/s` to `4.8/s`; positive and negative decay now visibly clamp with
`BoundReached`. The exact four-direction device regression passed 1/1. The final `qaFull` gate then
passed in 22 minutes 9 seconds with 1,763 actionable tasks (168 executed and 1,595 up-to-date): the
main Demo passed 137/137 device tests, Counter passed 1/1, and Tutorials passed 2/2, with no skipped
or failed tests. Phase 1 is accepted on the candidate branch; merge is the only remaining delivery
step before Phase 2 begins.

## Phase 2: Full animated content replacement

Add a true `AnimatedContent` contract while retaining `Crossfade` as the small alpha-only API. The
first accepted surface is expected to include a stable content key, initial/target-pair transition
selection, enter/exit composition, size transformation, slide/scale primitives, and a scope that
can derive direction from the logical segment.

The planning vocabulary is:

```kotlin
data class ContentTransform(
    val targetContentEnter: EnterTransition,
    val initialContentExit: ExitTransition,
    val sizeTransform: SizeTransform? = SizeTransform(),
)

fun <S> UiTreeBuilder.AnimatedContent(
    targetState: S,
    modifier: Modifier = Modifier,
    contentKey: (S) -> Any? = { it },
    transitionSpec: AnimatedContentTransitionScope<S>.() -> ContentTransform,
    content: AnimatedContentScope.(S) -> Unit,
)
```

The final implementation must define outgoing/incoming measurement, z-order, clipping, alignment,
focus and accessibility ownership, keyed descendant state, nullable targets, equal keys with
changed content, pair-specific direction, rapid A-to-B-to-C retargeting, removed-subtree effects,
renderer rollback, and disposal after every participating channel settles. `Crossfade` may delegate
to a proven alpha-only configuration only if doing so preserves its existing retarget and identity
contract without adding size or layout behavior.

Required evidence covers fixed and changing sizes, slide directions, scale origin, simultaneous
fade/size motion, interruption at multiple fractions, key collision behavior, nullable state,
focus/accessibility transfer, nested content transitions, failed candidate apply, host detach,
reduced motion, screenshots, and frame/allocation comparison with current `Crossfade`.

Implementation status on 2026-08-22: the Q3 surface is implemented with nullable-safe keyed
identity, pair-specific `ContentTransform`, logical measured-item slide, scale origins, target
z-order, optional `SizeTransform`, and a typed content scope. The renderer owns one dedicated size
host and at most two item hosts; the incoming item exclusively owns input, focus traversal, and
accessibility, while the outgoing item is draw-only. Admission occurs after successful candidate
commit, renderer rollback restores the previous visuals and owner, A-to-B-to-C interruption keeps
B's keyed composition scope and releases A once, and Crossfade remains on its original alpha-only
engine. Twelve deterministic animation tests, five renderer layout tests, two renderer transaction
tests, compiled samples in both public owner modules, and the focused Xiaomi device regression
cover the implemented contract. Manual primary/midpoint/alternative screenshots accept unequal
height, clipping, simultaneous channels, and settled geometry. The same-device fixed-frequency
comparison in [performance Section 2.4.10](../../tooling/performance.md#2410-animation-revision-2-animatedcontent-comparison)
reports run-P50 CV `0.004..0.008`; relative to Crossfade, AnimatedContent changes P50/P95/peak heap
by `-1.6%/+7.5%/+3.9%`, with no budget crossing, so frame and peak memory are
`no material change`. Android 9 exposes no per-object allocation events; retained-tree and exact
release counters therefore bound the structural allocation evidence without claiming lower churn.
The full repository, documentation, API-sample, Preview, and tooling-isolation gate passed through
`./gradlew qaQuick qaPreview verifyDevelopmentToolingIsolation` in 4 minutes with 1,624 actionable
tasks (254 executed and 1,370 up-to-date). MIUI rejected Gradle's ordinary connected-test install
and produced a zero-test report, which is excluded from evidence. Installing the same six generated
application/test APKs through the authorized root channel and invoking the same AndroidJUnitRunner
suites passed Demo 137/137, Counter 1/1, and Tutorials 2/2, with no skipped or failed tests. Phase 2
was merged in `84dce0ae6220517b5488070fa285ccc9226235f7`; its physical, keyed-identity, interaction,
rollback, and release contracts remain closed.

## Phase 3: Rich AnimatedVisibility transitions

Extend the existing fade/expand/shrink algebra with:

- `slideIn`/`slideOut` and horizontal/vertical convenience primitives;
- `scaleIn`/`scaleOut` with an explicit transform origin;
- alignment and full-size-dependent offset contracts;
- an `AnimatedVisibilityScope` exposing the owning transition; and
- a descendant `animateEnterExit` modifier or an equivalently scoped mechanism that does not
  create an unrelated frame loop.

Existing tree-builder, `RowScope`, `ColumnScope`, and `MutableTransitionState` behavior remains one
coherent state machine. Phase 3 must settle initial-enter behavior explicitly rather than silently
changing the current first-composition rule. Duplicate transition channels require documented
precedence; parent and descendant transformations require deterministic composition order.

Required evidence covers every primitive alone and in combination, LTR/RTL offsets, Row/Column
spacing, clipping, negative/full-size offsets, transform origins, parent plus child motion, rapid
enter/exit reversal, first composition, externally controlled state, focus/accessibility removal,
reduced motion, renderer rollback, and no leaked empty hosts or effects after disposal.

Implementation status on 2026-08-23: the Q3 surface is implemented with finite fraction-based
logical and axis-specific slide, pivoted scale, aligned expand/shrink, duplicate-channel
last-declaration precedence, and an `AnimatedVisibilityScope` whose `AnimatedEnterExit` descendants
share the parent's Boolean `Transition` and removal lifetime. The old `BoxScope` receiver is hard-cut
rather than bridged. Initial composition remains settled, layout direction is frozen per segment,
and inactive content loses input, focus, and accessibility ownership immediately while its host is
retained only for drawing until every parent and descendant channel settles. The Android renderer
applies translation and pivot from the complete measured host rather than the first child, preserves
parent-before-descendant transform order, and rolls failed patches back transactionally.

Twelve deterministic composition tests, five renderer geometry/lifecycle tests, one renderer
rollback test, compiled Q3 samples in both owner modules, the Preview fixture, and the updated Demo
cover first composition, combination and precedence, LTR/RTL, multi-child geometry, rapid reversal,
shared time, active ownership, reduced-motion endpoints, disposal, and rollback. Manual Xiaomi
review found and corrected the initial multi-child transform-geometry error; settled, hide midpoint,
hidden, and show midpoint paths then made the parent and opposing descendant choreography visibly
distinct. The same-device fixed-frequency release-safety comparison is accepted in
[performance Section 2.4.11](../../tooling/performance.md#2411-animation-revision-3-rich-visibility-release-safety-comparison):
candidate P50/P95/peak heap changed by `+2.4%/+3.3%/+3.9%` (`+0.197 ms/+0.355 ms/+303 KiB`) versus
the merged pre-Phase-3 control, so none crosses the frozen gate and the scoped conclusion is
`no material change`. Candidate P99 increased by `3.380 ms` to `15.723 ms` and remains a tail watch
item. The workload deliberately grows from a single old visibility host to rich parent-plus-child
choreography, so frame-count growth is not interpreted as throughput or energy. The final
`qaQuick qaPreview verifyDevelopmentToolingIsolation` gate passed in 2 minutes 26 seconds with
1,624 actionable tasks (197 executed and 1,427 up-to-date), covering repository, documentation,
compiled API samples, Preview, and tooling isolation. After the expected animation Preview Golden
was reviewed and accepted, its focused snapshot and full Preview suite passed. Installing the same
six generated application/test APKs through the authorized root channel then passed Demo 137/137,
Counter 1/1, and Tutorials 2/2 with no skipped or failed tests. Phase 3 is accepted on the candidate
branch; pull-request merge is the only remaining delivery step.

## Phase 4: Generic, segment-aware, and seekable transitions

Publish a generic channel using `AnimationConverter<T, V>`, expose a stable segment object to
transition-spec selection, and add a controlled seek state that cannot race an autonomous frame
loop. The provisional model is:

```kotlin
interface TransitionSegment<S> {
    val initialState: S
    val targetState: S
    fun isTransitioningTo(initial: S, target: S): Boolean
}

fun <S, T, V> Transition<S>.animateValue(
    converter: AnimationConverter<T, V>,
    transitionSpec: TransitionSegment<S>.() -> AnimationSpec,
    targetValueByState: (S) -> T,
): State<T>

class SeekableTransitionState<S>(initialState: S) {
    suspend fun animateTo(targetState: S)
    suspend fun seekTo(fraction: Float, targetState: S)
    suspend fun snapTo(targetState: S)
}
```

The final API must define whether seeking uses normalized fraction or play time when channel
durations differ, how the longest-channel duration is recomputed, how a seek becomes autonomous
animation, how retargeting preserves visual continuity and velocity, and how state publication
remains atomic. A seek state is composition/lifecycle owned and is not automatically saveable
unless an explicit durable contract is approved.

Required evidence includes generic converter dimensions, segment-specific specs, channels added or
removed during a segment, zero/unequal/infinite durations, seek endpoints and out-of-range inputs,
seek-to-animate handoff, animate-to-seek takeover, cancellation, rapid target replacement,
predictive-Back progress adaptation, deterministic explicit-time sampling, and no duplicate frame
owners.

Implementation status on 2026-08-23: the Q3 surface is implemented with a stable
`TransitionSegment<S>`, generic `animateValue<T, V>`, segment-aware finite specifications on every
typed channel, and `SeekableTransitionState<S>` bound through `rememberTransition`. The typed
channel named argument is hard-cut from `animationSpec` to `transitionSpec`; no compatibility
overload or second runner remains. The complete committed channel set owns duration, including
growth and shrink after call-position changes. Normalized seeking maps to that maximum, clamps
shorter channels, uses zero velocity, and resamples dynamic sets without changing the retained
fraction.

The controller accepts one post-commit composition binding and one mutation writer. A new command
cancels and joins its predecessor; invalid fractions fail before ownership changes. Retargeting
freezes every current channel sample, seek-to-animate creates one autonomous loop with zero initial
velocity, snap atomically collapses both logical endpoints and the segment, and disposal retains an
unfinished sample only as seeking state. The state owns no scope, is not saveable, and does not own
predictive-Back commit or rollback.

Focused core/composition tests cover custom two-dimensional conversion, stable segment identity,
direction-specific selection, dynamic duration removal, unequal and zero channels, finite-range
validation, seek/animate takeover in both directions, rapid replacement, disposal, unique binding,
and snap collapse. The revision-2 Demo and Preview expose normalized Seek, autonomous completion,
generic position, four typed unequal-duration channels, segment status, and deterministic Reset.
Manual Xiaomi review accepted the 70% sample and autonomous continuation; targeted device tests and
the English/Chinese action-reset matrix pass. The fixed-frequency absolute baseline in
[performance Section 2.4.12](../../tooling/performance.md#2412-animation-revision-2-seekable-transition-baseline)
records identical 200-frame runs, P50/P95/P99 `7.775/10.493/11.718 ms`, median peak heap
`8,474 KiB`, run-P50 CV `0.011`, and zero thermal sleep. It is not compared to the incompatible
revision-1 workload. The immutable release record is
`release/changes/20260823-seekable-transition-phase4.json`. Final gates pass: the combined
`qaQuick`, `qaPreview`, and development-tooling-isolation run completes 1,624 tasks; `qaFull`
completes 1,763 tasks and the physical-device suites pass Demo 137/137, Counter 1/1, and Tutorials
2/2 with no skipped or failed tests. Pull-request delivery remains.

## Phase 5: Layout-coordinate and bounds animation

Add a layout-participating `Modifier.animateBounds` or equivalently named Q3 contract that animates
position and size between accepted layout states. It must not be implemented as visual scale alone
or as a wrapper that permanently changes hit/accessibility bounds independently from what users
see.

ADR-0019 selects one lookahead-style candidate measurement when constraints or target topology
change and stages measure, layout, hit geometry, accessibility geometry, and animation ownership
transactionally. The implementation must preserve that immediate-parent physical-pixel contract
through parent-local conversions, alignment, clipping, RTL, scroll, density and configuration
changes, nested bounds animation, z-order, focus, detach/reattach, and active retargeting.

Required evidence includes position-only, size-only, and combined motion; parent constraint
changes; Row/Column/Box/ConstraintLayout parents; nested scroll and lazy reuse; RTL; density/font
scale; clipping; focus and touch at visible bounds; rapid retarget; failed renderer apply and
rollback; stable endpoint layout; extra-measure counts; per-frame allocations; and same-device
frame CPU against equivalent non-animated layout changes.

## Phase 6: Shared element and shared bounds transitions

Build a bounded coordinator on Phase 4 seeking and Phase 5 bounds instead of introducing a second
motion engine. The first release is limited to one Android window and reviewed ViewCompose session
relationships. Its provisional vocabulary may resemble:

```kotlin
fun UiTreeBuilder.SharedTransitionLayout(
    modifier: Modifier = Modifier,
    content: SharedTransitionScope.() -> Unit,
)

fun Modifier.sharedElement(
    state: SharedContentState,
    animatedVisibilityScope: AnimatedVisibilityScope,
): Modifier

fun Modifier.sharedBounds(
    state: SharedContentState,
    animatedVisibilityScope: AnimatedVisibilityScope,
): Modifier
```

The contract must define key namespace, source/target pairing, missing and duplicate keys,
snapshot/live-content choice, overlay and clipping, shape/alpha/bounds participation, z-order,
content scaling, input and accessibility owner, focus, destination/session retention, navigation
commit/cancel, predictive-Back progress, interruption by another navigation command, process
recreation, and deterministic cleanup.

The navigation adapter may consume committed and predictive progress but cannot mutate the
back-stack transaction to satisfy a visual effect. Unsupported cross-window or released-source
cases must select a documented local enter/exit fallback rather than retaining an Activity, View,
destination owner, or obsolete child session.

Required evidence covers push/pop/replace, predictive Back complete/cancel, redirected navigation,
adaptive panes, retained stacks, overlay destinations, duplicate/missing pairs, scroll offsets,
configuration change, process recreation fallback, accessibility/focus transfer, source/target
release, renderer failure, memory retention, screenshots, and same-device navigation performance.

## Phase 7: Request-driven animation timeline tooling

Expose only the minimum neutral runtime projection needed to inspect an explicitly selected
animation: stable diagnostic identity, label, logical current/target state summary, channel names,
spec family, start/end values when privacy-safe and bounded, play time, duration or physical
terminal status, velocity where applicable, and running/idle/interrupted state. Concrete process
protocol, report storage, Studio UI, and developer lifecycle remain downstream in optional Preview
and Studio tooling.

Activation must follow
[ADR-0009](../../architecture/decisions/0009-development-tooling-isolation.md):

1. the optional tooling artifact is packaged;
2. the application is debuggable; and
3. a valid explicit IDE request selects a bounded inspection lifetime.

With no valid request, animation execution may pay only an approved nullable-port check or bounded
metadata already justified by a neutral contract. It performs no tooling-owned frame callback,
serialization, file I/O, View traversal, listener registration, or report publication. A finite
request may capture a bounded timeline. Continuously observing or remotely seeking a live
application animation requires an ADR amendment with activation lifetime, mutation authority,
failure isolation, and benchmark evidence.

The Studio experience must distinguish observation from control, show unequal channel durations,
physical terminal conditions, interruption and retarget history, and unsupported/unbounded data.
Preview-controlled seeking must use Phase 4 ownership rather than writing private runtime fields.

Required evidence includes no-artifact and release-classpath absence, debuggable-without-request
inactivity, one request/one bounded capture, nonce and stale-response behavior, lifecycle disposal,
malformed and oversized input, privacy-safe projection, physical and duration timelines, seek
control only in approved Preview contexts, plugin tests, `verifyDevelopmentToolingIsolation`, and
same-device inactive/requested performance interpretation.

## Cross-cutting implementation rules

1. Preserve platform-neutral physics, sampling, converters, transition coordination, and result
   types in `viewcompose-animation-core`; composition state and DSL remain in
   `viewcompose-animation`.
2. Android measurement, layout hosts, overlays, View snapshots, and navigation adapters stay in
   the Android Engine or owning Integration layer. Platform View types do not enter core or UI
   Foundation contracts.
3. Every new or changed public/protected API is assigned a Q level before implementation and lands
   with canonical-English KDoc/Javadoc, compiled Q3 samples, API comment verification, and owning
   module documentation in the same change.
4. Each production-source pull request adds one immutable `release/changes/<unique>.json` and lists
   it in this plan. Release planning derives dependency propagation; the plan never hand-writes it.
5. Animation state, coroutine work, target measurement, retained outgoing content, shared
   overlays, and tooling registrations release exactly once with their owning composition,
   session, destination, or request.
6. Failed composition or renderer apply cannot publish candidate animation ownership, seek state,
   bounds, content identity, overlay, or navigation visuals as committed state.
7. Reduced motion preserves state communication and endpoint correctness for every phase.
8. Accepted test and benchmark output is interpreted in the owning active documentation with
   comparison context, absolute results, normalized change, conclusion, limitations, and next
   action.
9. A phase that requires a new published artifact must update publishing metadata, the module
   catalog, module manual, dependency verification, release registry workflow, and both-locale
   durable public documentation before release.
10. No phase introduces an AndroidX Compose runtime or animation dependency into ViewCompose
    production artifacts.

## Validation matrix

Every phase runs the smallest focused tests during iteration and the following gates before its
status can become complete:

- `./gradlew qaQuick` for compilation, unit, documentation, API sample, dependency, and structural
  checks;
- `./gradlew qaPreview` for affected Preview/Paparazzi scenarios;
- focused Android renderer, animation, navigation, and Studio-plugin tests for the changed phase;
- `./gradlew verifyDevelopmentToolingIsolation` for Phase 7 and any earlier neutral inspection
  port;
- `./gradlew qaFull` with an online supported device for animation, layout, input, accessibility,
  navigation, and lifecycle behavior; and
- release-class benchmarks on the same physical-device configuration for any changed frame,
  measurement, overlay, navigation, or tooling path.

The minimum device matrix is API 24, 31, 35, and the current compile-target API, with Pixel-like and
Samsung hardware evidence for phases that affect rendered motion or input. Test matrices cover
Light/Dark, LTR/RTL, default and enlarged font scale, system animation scale/reduced motion,
configuration change, detach/reattach, rapid retargeting, and process recreation where the owning
contract survives it.

## Completion criteria

This plan is complete only when:

1. all seven phases have their accepted scope implemented, or a reviewed narrowing decision has
   moved an explicitly rejected item into durable roadmap documentation without calling it done;
2. current module manuals and migration comparison state supported, partial, intentionally
   different, and unsupported semantics against the frozen Compose baseline;
3. physical animation, content, visibility, seeking, bounds, shared motion, reduced motion, and
   interruption have deterministic unit and compiled-sample coverage;
4. renderer and navigation tests prove transactional rollback, lifecycle release, focus/input,
   accessibility, RTL, configuration, and predictive-Back behavior where applicable;
5. Demo, Preview/Paparazzi, and Studio fixtures expose the capabilities without depending on
   private APIs or unstable text selectors;
6. benchmark evidence accepts frame CPU, allocation, measurement, retained-memory, and inactive
   tooling budgets or records a regression and rollback/narrowing action;
7. `qaQuick`, `qaPreview`, `verifyDevelopmentToolingIsolation`, focused plugin gates, and supported
   `qaFull` device coverage pass at the final revision;
8. every immutable Maven Changeset owned by the plan is listed here and accepted into release
   documentation;
9. no active document still presents MotionLayout expansion as scheduled work; and
10. durable conclusions have moved to active documentation, this file has moved to `docs/archive/`,
    and both plan indexes reflect the archival move before affected artifacts are uploaded to Maven
    Central.

## Ordered execution checklist

1. **Complete — Phase 0:** Compose `1.12.0` semantics, ADR-0019 contracts/Q3 inventory, migration
   limits, budgets, revision-1 scenarios, and the stable Xiaomi fixed-clock baseline are frozen.
2. **Complete — Phase 1:** hard-cut the duration spring/converter/result surface and implement
   physical spring, velocity continuity, decay, bounds, and results.
3. **Complete — Phase 2:** keyed `AnimatedContent`, content transforms, size transforms, renderer
   ownership, repository/Preview/full-device validation, and performance comparison are accepted
   and merged.
4. **Complete and merged — Phase 3:** slide/scale/aligned visibility primitives, type-safe scope,
   shared-clock descendant choreography, focused/full tests, manual-device review, Preview Golden,
   fixed-frequency performance, and root-installed device suites were merged in `2a21db65`.
5. **Complete on candidate branch — Phase 4:** public generic/segment-aware channels, normalized
   seekable ownership, focused tests, Demo/Preview/manual review, the stable fixed-frequency
   absolute baseline, repository gates, and full physical-device suites are accepted; pull-request
   delivery remains.
6. **Pending — Phase 5:** implement transactional layout-coordinate and bounds animation.
7. **Pending — Phase 6:** implement bounded shared-element/shared-bounds coordination and navigation
   integration.
8. **Pending — Phase 7:** implement isolated request-driven timeline inspection and approved
   Preview control.
9. **Pending — Closeout:** update durable documentation, migration matrix, Demo/Preview/Studio
   evidence, release records, and archive this plan.

## Risks and stop conditions

| Risk | Required response |
| --- | --- |
| Physical sampling is nondeterministic or unbounded | Do not publish the physical spec; retain the duration model until termination and test clocks are explicit. |
| Velocity continuity conflicts with current cancellation | Freeze one mutation/result contract in Phase 0; do not expose two competing terminal interpretations. |
| Content or bounds animation breaks transaction rollback | Keep the last committed subtree/layout authoritative and stop the phase before adding more visual primitives. |
| Bounds motion changes hit/accessibility geometry incorrectly | Do not retain a visual-only workaround as layout animation; narrow to contexts with provable geometry ownership. |
| Shared overlays retain destinations or Views | Fall back to local enter/exit and remove shared motion until weak ownership and terminal cleanup pass. |
| Tooling adds recurring work without a request | Remove the concrete runtime work and fail `verifyDevelopmentToolingIsolation`; debug-only packaging is not an exception. |
| Compose parity would require AndroidX Compose in production | Document the semantic difference or reject the feature; do not add the dependency. |
| One phase expands into MotionScene/MotionLayout policy | Stop and require a separate user-approved plan with a demonstrated use case. |

## Decision log

| Date | Decision |
| --- | --- |
| 2026-08-18 | Activate one plan for the seven ordered animation expansions: physics/results, animated content, rich visibility, seekable transitions, bounds, shared motion, and timeline tooling. |
| 2026-08-18 | Do not schedule MotionLayout expansion. Retain raw host interop and reconsider typed MotionScene support only from a future concrete requirement. |
| 2026-08-18 | Preserve the completed first-round Animation milestone as the baseline; this plan extends it rather than rewriting historical completion. |
| 2026-08-22 | Complete Phase 0 through ADR-0019: hard-cut the duration spring without a compatibility layer, freeze one physical/transition/layout/tooling ownership model, and assign every planned public family Q3. |
| 2026-08-22 | Freeze Compose Animation `1.12.0` as the semantic baseline and retain local Compose `1.7.8` only as the executable comparison because the stable upstream release requires compile SDK 37 and AGP 9.2. |
| 2026-08-22 | Accept four revision-1 absolute animation baselines on the root-controlled Xiaomi reference device; normalized direction remains `inconclusive` until Phase 1 supplies a same-device candidate. |
| 2026-08-22 | Accept the Phase 2 keyed-content architecture and focused Xiaomi evidence: incoming content owns interaction, failed candidate apply cannot publish replacement identity, at most two keyed subtrees are retained, and fixed-frequency AnimatedContent versus Crossfade is `no material change`. |
| 2026-08-22 | Accept the Phase 2 final gates: repository, documentation, Preview, and tooling checks pass; MIUI's zero-test ordinary-install result is rejected, while root installation of the same APKs passes Demo 137/137, Counter 1/1, and Tutorials 2/2. |
| 2026-08-23 | Record Phase 2 merged at `84dce0ae6220517b5488070fa285ccc9226235f7` and begin Phase 3 without reopening its physical or content-ownership contracts. |
| 2026-08-23 | Accept the Phase 3 hard-cut design and focused evidence: `AnimatedVisibilityScope` replaces `BoxScope`, descendants share one transition/removal lifetime, inactive native content relinquishes interaction immediately, and renderer transforms use complete host geometry. |
| 2026-08-23 | Accept the Phase 3 Xiaomi fixed-frequency release-safety comparison as `no material change`; P50/P95/heap remain inside frozen gates, while P99 at `15.723 ms` is retained as a Phase 4 tail watch item rather than hidden or converted into an unfrozen blocker. |
| 2026-08-23 | Accept the Phase 3 final gates: repository, documentation, compiled samples, Preview, and tooling isolation pass in the 1,624-task gate; the reviewed rich-visibility Golden passes; root-installed device suites pass Demo 137/137, Counter 1/1, and Tutorials 2/2. |
| 2026-08-23 | Record Phase 3 merged at `2a21db658f3214afef1436a25c3463b7f78e53d0` and begin Phase 4 without reopening its visibility ownership or performance conclusions. |
| 2026-08-23 | Freeze the Phase 4 hard cut: one stable segment-aware finite channel surface, one explicit seek/animate/snap writer, normalized longest-duration progress, zero seek velocity, cancel-and-join takeover, one post-commit binding, and no save/navigation ownership. |
| 2026-08-23 | Accept the `animation.transition@2` Xiaomi fixed-frequency absolute baseline: five identical 200-frame runs, run-P50 CV `0.011`, P50/P95/P99 `7.775/10.493/11.718 ms`, median peak heap `8,474 KiB`, and zero thermal sleep; reject longitudinal comparison with revision 1. |
| 2026-08-23 | Accept the Phase 4 final gates: the 1,624-task quick/Preview/tooling-isolation gate and 1,763-task `qaFull` gate pass; physical-device suites pass Demo 137/137, Counter 1/1, and Tutorials 2/2 with no skips or failures. |
