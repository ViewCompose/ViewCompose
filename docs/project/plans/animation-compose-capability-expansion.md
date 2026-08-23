# Animation Compose-Capability Expansion Plan

## Status

Active. Phases 0 through 5 are complete and merged. Phase 6 shared-element and shared-bounds
implementation, focused acceptance, Demo/Preview review, rooted-device validation,
fixed-frequency evidence, and repository gates are complete on the candidate branch; pull-request
delivery remains.
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

Next action: deliver and merge the Phase 6 pull request before beginning Phase 7 request-driven
timeline tooling.

## Maven release changesets

- `release/changes/20260822-animation-physical-foundation.json` — Phase 1 hard cut and shared
  physical engine; accepted and merged.
- `release/changes/20260822-animated-content-phase2.json` — Phase 2 keyed content replacement,
  renderer ownership, and rollback contracts; accepted and merged.
- `release/changes/20260823-animated-visibility-phase3.json` — Phase 3 rich visibility primitives,
  type-safe content scope, shared descendant clock, and native interaction ownership; accepted and
  merged.
- `release/changes/20260823-seekable-transition-phase4.json` — Phase 4 generic transition channels,
  normalized seeking, and one seek/animate/snap writer; accepted and merged.
- `release/changes/20260823-animate-bounds-phase5.json` — Phase 5 real parent-local bounds motion,
  renderer ownership, lifecycle-safe reuse, and Preview coverage; accepted and merged.
- `release/changes/20260823-shared-navigation-motion-phase6.json` — Phase 6 typed endpoint markers,
  renderer tag transport, bounded NavHost snapshots, predictive-Back integration, and
  Demo/Preview coverage; candidate acceptance and repository verification complete, with delivery
  pending.

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

## Accepted Phases 0–5

The detailed execution ledger for completed phases was consolidated on 2026-08-23 after Phase 4
acceptance. Durable semantics remain in
[ADR-0019](../../architecture/decisions/0019-animation-physics-transition-and-inspection-ownership.md),
[ADR-0020](../../architecture/decisions/0020-separate-animation-value-and-velocity-domains.md), the
[animation manual](../../modules/viewcompose-animation/README.md), the
[animation-core manual](../../modules/viewcompose-animation-core/README.md), and the
[Compose migration guide](../../migration/compose-animation.md). This table remains the active
plan-status authority; exact measurements, device controls, limitations, and interpretations stay
in the linked performance sections instead of being duplicated here.

| Phase | Accepted boundary | Delivery status | Performance and final evidence |
| --- | --- | --- | --- |
| 0 | Hard-cut compatibility, physical units and termination, content/visibility algebra, seek/layout/shared-motion ownership, Q3 inventory, and fixed budgets | Complete and merged in `6d6bdbe4` | [Revision-1 pre-physics baseline](../../tooling/performance.md#248-animation-revision-1-pre-physics-baseline); verification isolation was corrected before implementation |
| 1 | Analytic physical spring, typed value/velocity domains, decay, bounds, structured results, fail-before-ownership validation, and one last-writer mutation model | Complete and merged in `a8196f0b` | [Physical candidate comparison](../../tooling/performance.md#249-animation-revision-1-phase-1-physical-candidate); fixed-frequency rows passed and rooted gesture handoff reached `BoundReached` |
| 2 | Keyed `AnimatedContent`, pair-specific transforms, measured size, bounded two-subtree ownership, incoming-only interaction, rollback, and exact release | Complete and merged in `84dce0ae` | [AnimatedContent comparison](../../tooling/performance.md#2410-animation-revision-2-animatedcontent-comparison); frame and peak memory were `no material change` |
| 3 | Slide/scale/aligned visibility, hard-cut typed scope, shared parent/descendant timeline, complete-host geometry, and immediate inactive interaction removal | Complete and merged in `2a21db65` | [Rich-visibility comparison](../../tooling/performance.md#2411-animation-revision-3-rich-visibility-release-safety-comparison); frozen gates passed and P99 remains a recorded watch item |
| 4 | Stable `TransitionSegment`, generic `animateValue<T, V>`, hard-cut `transitionSpec` name, dynamic committed-channel duration, and one seek/animate/snap writer | Complete and merged in `984ac9bd` | [Seekable-transition absolute baseline](../../tooling/performance.md#2412-animation-revision-2-seekable-transition-baseline): five 200-frame runs, P50/P95/P99 `7.775/10.493/11.718 ms`, heap `8,474 KiB`, CV `0.011`, zero thermal sleep |
| 5 | Additive `Modifier.animateBounds`, one complete synthetic layout owner, real parent-local geometry, physical/duration retargeting, hard rejection of dual size ownership, explicit reuse reset, and transactional rollback | Complete and merged in `bb57fcd0` | [Bounds versus immediate-layout comparison](../../tooling/performance.md#2413-animation-revision-1-real-bounds-comparison): stable `464` versus `16` frames/run, animation P50/P95/P99 `5.124/6.438/18.503 ms`, snap P50/P95/P99 `8.727/25.762/28.556 ms`, heap `6,714` versus `6,868 KiB`, CV `0.055` versus `0.083`, zero thermal sleep |
| 6 | Typed shared endpoint markers, stable renderer transport, bounded one-window snapshots, per-pair fallback, and committed/predictive navigation progress ownership | Complete on candidate branch; PR delivery remains | [Shared-content versus ordinary navigation](../../tooling/performance.md#2414-navigation-revision-1-shared-content-comparison): identical `124` frames/run, shared P50/P95 `4.073/8.096 ms` versus control `3.989/8.487 ms`, heap `6,971` versus `6,651 KiB`, CV `0.059` versus `0.072`, zero thermal sleep; `no material change` |

The cumulative accepted contract through Phase 5 is:

- physical and finite specifications share one vector-aware sampling engine; invalid replacement
  input cannot take mutation ownership or publish a partial snapshot;
- keyed content and visibility retain bounded renderer ownership, transactional rollback, and one
  shared transition timeline rather than feature-specific frame loops;
- normalized seeking maps to the longest complete committed channel set, clamps shorter channels,
  resamples dynamic membership at the retained fraction, and uses zero velocity when handing an
  explicit seek to autonomous continuation;
- `snapTo` atomically collapses current state, target state, and segment endpoints; the controller
  owns no scope, saved state, or navigation commit/rollback; and
- real-bounds animation owns one complete synthetic layout host, lays out sampled parent-local
  geometry without per-frame child measurement, rejects dual size ownership before mutation, and
  clears sampled state on detach or cross-owner reuse; and
- every Q3 surface landed with canonical KDoc, compiled samples, owning-module documentation,
  Demo/Preview coverage, reviewed Chinese mirrors, immutable Changesets, and interpreted
  performance evidence.

Phase 5 final gates pass: the quick/Preview/tooling-isolation gate completed 1,624 tasks, the
non-device `qaFull` path completed 1,622 tasks, and root-installed Xiaomi suites passed Demo
138/138, Counter 1/1, and Tutorials 2/2 with no skips or failures. Phases 6–7 below retain their
full unresolved requirements.

## Phase 5: Layout-coordinate and bounds animation

Phase 5 added the Q3 `Modifier.animateBounds` contract for position and size changes between
accepted layout states. One transparent synthetic host owns the complete parent-data and layout
chain while the child retains drawing, content, input, and semantics. `animateBounds` plus
`animateContentSize` on the same node is rejected before native mutation; repeated bounds elements
remain last-wins.

The Android host accepts one target measurement, then lays out every sampled rectangle directly
without per-frame child measurement. Logical anchors resolve before physical pixels; duration
retargets restart from the current rectangle with zero velocity, while physical springs retain all
four edge velocities. Detach and cross-owner lazy reuse cancel and clear animation state explicitly,
so adoption settles under the new owner rather than replaying stale geometry.

Focused evidence now covers position-only, size-only, and combined motion; Row, Column, Box, and
ConstraintLayout parents; logical RTL; nested host ownership; density/font-scale rebinding;
clipping and retained focus; active retargeting; detach/reattach and cross-owner lazy reuse;
failed renderer apply with geometry/spec rollback; stable endpoints; and zero additional child
measurements during property frames. The Xiaomi device test proves real endpoint size, visible and
accessibility bounds, and touch delivery. The reviewed Demo and Paparazzi fixtures expose the same
three motion classes. The fixed-frequency same-fixture control records deterministic frame counts,
shared engine-vector reuse, stable peak heap, and improved frame CPU; per-object allocation events
and total energy remain explicit measurement limitations rather than inferred claims.

## Phase 6: Shared element and shared bounds transitions

Build a bounded coordinator on Phase 4 seeking and Phase 5 bounds instead of introducing a second
motion engine. The first release is limited to one Android window and reviewed ViewCompose session
relationships. Phase 6 hard-cuts the provisional Compose-shaped scope: `NavHost` is already the
cross-session coordinator, so adding `SharedTransitionLayout`, `SharedTransitionScope`, or an
`AnimatedVisibilityScope` parameter would create competing ownership without improving safety.
The Q3 declaration surface is instead:

```kotlin
@JvmInline
value class SharedContentKey(val value: String)

fun Modifier.sharedElement(
    key: SharedContentKey,
): Modifier

fun Modifier.sharedBounds(
    key: SharedContentKey,
): Modifier
```

Keys are local to one outgoing/incoming destination pair and pair only when both endpoints declare
the same key and the same element/bounds mode exactly once. A missing endpoint, duplicate key,
mode mismatch, detached or zero-sized View, unsupported surface-backed View, or exceeded snapshot
budget disables only that pair and leaves normal destination motion intact. Later shared-content
elements on one modifier chain replace earlier ones.

The first release uses immutable renderer snapshots in a non-interactive `NavHost` overlay. It does
not keep live content, morph arbitrary shapes, or move native input targets between sessions.
`sharedElement` moves one source snapshot and reveals the committed target at the terminal state;
`sharedBounds` interpolates the same bounds while crossfading source and target snapshots. Snapshot
pixels preserve each endpoint's rendered alpha and local clipping; host clipping remains the final
boundary. Pairs draw in stable outgoing-tree traversal order above destination surfaces. Bounds use
host-local physical pixels and scale snapshots into the sampled rectangle. The committed incoming
destination owns navigation, lifecycle, input, accessibility, and final focus; overlay snapshots
never receive events or expose semantics. If focus was inside the outgoing endpoint, the matched
incoming endpoint receives focus only after a successful commit and only when it is focusable.

The coordinator retains no destination beyond the transition already owned by navigation. Push,
pop, replace, retained-stack selection, and predictive-Back completion consume the existing native
transition progress. Predictive-Back cancellation restores the outgoing endpoint and never commits
the stack. A redirect disposes the old shared snapshots, restores both endpoint properties, and
lets the next committed command rescan from its own sessions; it does not attempt visual continuity
through stale keys. Configuration change, process recreation, released sessions, cross-window
content, capture failure, and reduced or disabled motion settle directly to the committed scene.
Every complete, cancel, redirect, destroy, preparation failure, and renderer failure path removes
overlay drawables, releases bitmap storage, and restores only coordinator-owned endpoint state.

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
5. **Complete and merged — Phase 4:** public generic/segment-aware channels, normalized seekable
   ownership, focused tests, Demo/Preview/manual review, the stable fixed-frequency absolute
   baseline, repository gates, and full physical-device suites were merged in `984ac9bd`.
6. **Complete and merged — Phase 5:** transactional real-bounds ownership, focused
   correctness and reuse tests, Demo automation, manual device review, Preview Golden,
   fixed-frequency animated-versus-snap comparison, repository gates, and root-installed physical
   suites were merged in `bb57fcd0`.
7. **Complete on candidate branch — Phase 6:** typed endpoint transport, bounded one-window
   snapshots, committed and predictive navigation integration, fallback/release coverage,
   Demo/Preview/manual review, rooted-device suites, and fixed-frequency comparison are accepted;
   repository gates pass and PR delivery remains.
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
| 2026-08-23 | Record Phase 4 merged at `984ac9bdd3be9684ce60670a73481f052a2f6aea` and begin Phase 5 without reopening seek ownership. |
| 2026-08-23 | Freeze the Phase 5 hard cut: one complete synthetic layout owner, real immediate-parent pixels, last bounds specification wins, dual `animateBounds`/`animateContentSize` ownership fails before mutation, duration retargets use zero velocity, physical retargets retain four-edge velocity, and detach or cross-owner reuse settles under the next owner. |
| 2026-08-23 | Accept the focused Phase 5 matrix and Xiaomi manual/device evidence: Row/Column/Box/ConstraintLayout, RTL, density/font scale, nested ownership, clipping/focus, lazy reuse, rollback, measurement count, accessibility geometry, and endpoint touch pass. |
| 2026-08-23 | Accept the final-candidate `animation.bounds@1` fixed-frequency comparison as improved active-frame latency with no material peak-heap change: animated P50/P95/P99 are `5.124/6.438/18.503 ms` versus snap `8.727/25.762/28.556 ms`, median heap is `6,714` versus `6,868 KiB`, run-P50 CV is `0.055` versus `0.083`, and thermal sleep is zero. The unequal `464` versus `16` frames prohibit a total-CPU or energy claim. |
| 2026-08-23 | Accept the Phase 5 final gates: documentation, translation, quick, Preview, and tooling-isolation checks pass in the 1,624-task gate. The non-device `qaFull` path passes 1,622 tasks; the ordinary device-inclusive path executes 1,734 tasks before MIUI rejects Counter APK installation with zero tests. Root-installing the exact rebuilt APKs then passes Demo 138/138, Counter 1/1, and Tutorials 2/2 with no skips or failures. Final start/mid/end visual review confirms all three Bounds motion classes, and endpoint UI Automation reports the combined target at `[378,1332][990,1506]`. |
| 2026-08-23 | Record Phase 5 merged at `bb57fcd049e3b1d359d18ea271d0505fc08eb033` and begin Phase 6 without reopening seek ownership or real parent-local bounds ownership. |
| 2026-08-23 | Freeze the Phase 6 hard cut: reject the provisional `SharedTransitionLayout`/scope owner, publish only typed `SharedContentKey` endpoint markers, let `NavHost` pair one key/mode per destination side, reuse committed or predictive-Back progress, render bounded immutable one-window snapshots, fall back per invalid key, and release old overlays before redirected commands rescan. |
| 2026-08-23 | Accept Phase 6 focused and rooted-device evidence: unique/missing/duplicate/mismatched/over-budget/surface-backed pairs, Push/Pop/Replace, predictive Back cancel/commit, redirect, disabled motion, focus transfer, host destruction, endpoint reuse, process recreation, adaptive stacks, and strict deep links pass; reviewed slow-motion frames show the bounds surface resizing/moving and the element chip moving independently without changing input ownership. |
| 2026-08-23 | Accept `navigation.shared-motion@1` as `no material change`: shared P50/P90/P95/P99 are `4.073/5.526/8.096/36.099 ms` versus ordinary motion `3.989/5.466/8.487/30.020 ms`, median heap is `6,971` versus `6,651 KiB`, run-P50 CV is `0.059` versus `0.072`, both arms hold exactly `124` frames in all five runs, and thermal sleep is zero. The `+6.079 ms` P99 remains an explicit tail watch item outside the frozen P50/P95 gate. |
| 2026-08-23 | Accept the Phase 6 final gates: documentation, translation, quick, Preview, and tooling-isolation checks pass in the 1,624-task gate. MIUI rejects ordinary APK installation, so the exact rebuilt APKs were root-installed; Demo passes 138/138, Counter 1/1, and Tutorials 2/2 with no skips or failures. The final Demo suite completes in `784.863 s`; its shared-endpoint geometry cycles through three bounded states so repeated navigation cannot move the automation target outside the lazy viewport, and assertions tolerate the intentionally overlapping outgoing and incoming snapshot hosts. |
