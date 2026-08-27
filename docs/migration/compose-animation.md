---
schema_version: 2
document_id: migration.compose-animation
doc_type: migration
owner:
  kind: capability
  id: animation.composition-motion
version_lane: released
capability_ids:
  - animation.composition-motion
artifact_ids:
  - viewcompose-animation-core
  - viewcompose-animation
sample_ids:
  - migration.compose-animation-legacy-spring
source_state: Compose Animation 1.12.0 semantics and the removed ViewCompose nominal-duration spring signature.
target_state: ViewCompose Animation 0.1.0-alpha04 physical, state, content, and layout-motion contracts.
---

# Migrate Compose animation

This page compares Jetpack Compose Animation with the current ViewCompose animation line and the
accepted expansion contract. It is not a source-compatibility promise. Similar names describe the
same concept only where the lifecycle, timing, geometry, and interruption rows below agree.

Last verified: **2026-08-23**

Re-verification owner: **maintainers of `viewcompose-animation-core`, `viewcompose-animation`, the
Android renderer, navigation, Preview, and Studio tooling**

## Baselines and evidence limits

The current ViewCompose target is:

| Artifact | Version | Current role |
| --- | --- | --- |
| `viewcompose-animation-core` | `0.1.0-alpha04` | Platform-neutral duration/physical sampling, typed velocity, mutation, motion policy, and explicit transition coordination |
| `viewcompose-animation` | `0.1.0-alpha04` | Composition-owned physical/state animation, generic and seekable transitions, visibility, Crossfade, and content-size animation |

The upstream stable semantic baseline is Compose Animation `1.12.0`, released on 2026-08-12. It was
verified against the official [Compose Animation release notes](https://developer.android.com/jetpack/androidx/releases/compose-animation),
[animation-core API reference](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/package-summary),
[`Animatable` reference](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/Animatable),
[`SeekableTransitionState` reference](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/SeekableTransitionState),
and [shared-element guide](https://developer.android.com/develop/ui/compose/animation/shared-elements).

The repository's executable Compose dependencies remain on `1.7.8`. Compose `1.12.0` also requires
compile SDK 37 and AGP 9.2, while this repository currently uses compile SDK 36 and AGP 8.13.2.
Consequently:

1. official Android documentation establishes the `1.12.0` semantic comparison;
2. repository source, unit tests, compiled samples, Demo, Preview, and device tests establish
   current ViewCompose behavior; and
3. a local Compose `1.7.8` fixture cannot prove `1.12.0` parity.

[ADR-0019](../architecture/decisions/0019-animation-physics-transition-and-inspection-ownership.md)
freezes the target architecture and API quality levels. [ADR-0020](../architecture/decisions/0020-separate-animation-value-and-velocity-domains.md)
separates animated value and velocity domains. Rows marked **Planned** below are accepted
design, not available APIs. They remain unsupported for application migration until their phase is
implemented, documented in the owning module, and released.

## Capability matrix

| Concern | Compose `1.12.0` semantic | Current ViewCompose status | Migration decision |
| --- | --- | --- | --- |
| Tween, keyframes, snap, repeat | Duration and repeat specifications | **Supported**, with a narrower keyframe/repeat surface | Revalidate unsupported start offsets, spline keyframes, and path easing before porting |
| Physical spring | Threshold-based solve with value and velocity | **Supported** with normalized mass, typed initial velocity, analytic damping branches, threshold termination, and a safety guard | Retune Compose parameters against ViewCompose units; an exact interval still uses tween/keyframes |
| `Animatable` mutation ownership | Last mutation cancels the previous call; completion returns result state | **Supported** with `Animatable<T, V>`, retained velocity, structured results, bounds, and last-writer cancellation | Preserve structured ownership; cancellation throws rather than returning an interrupted result |
| Decay and fling handoff | Decay specs and velocity continuation | **Supported** with platform-neutral exponential decay | Gesture owners still convert density, direction, axis, and nested-scroll velocity before handoff |
| Target-as-state animation | State-driven typed animation | **Supported** for generic values, Float, Int, encoded ARGB, and `UiDp` | Color interpolation is encoded-channel, not color-space aware |
| `Transition` | Shared state segment, generic channels, seeking | **Supported** with generic `AnimationConverter<T, V>` channels, stable segment-aware specifications, one autonomous or seeking writer, and normalized seek progress | Use `updateTransition` for composition-owned targets or bind one `SeekableTransitionState` through `rememberTransition`; play-time seeking and initial seek velocity are intentionally not public |
| `AnimatedVisibility` | Enter/exit algebra, slide, scale, descendant choreography | **Supported** for fade, aligned measured reveal, measured-fraction slide, pivoted scale, owning scope, and shared-clock descendant enter/exit | Use `AnimatedVisibilityScope.AnimatedEnterExit` rather than Compose's modifier form; callback-calculated offsets remain unsupported |
| `AnimatedContent` | Keyed outgoing/incoming replacement and content transforms | **Supported** for keyed replacement, pair-specific fade/slide/scale, z-order, alignment, and optional size transforms | Keep `Crossfade` for alpha-only replacement; full-size callback offsets and descendant choreography remain unsupported |
| Content-size animation | Layout size changes | **Supported**, using an Android renderer wrapper and the shared physical spring solver | Revalidate parent constraints and wrapper placement; infinite specifications are rejected |
| Bounds animation | Position and size across layout-coordinate changes | **Supported** through `Modifier.animateBounds`, with real parent-local geometry, transactional rollback, and aligned drawing/input/accessibility bounds | Keep bounds ownership in the renderer; callback-calculated lookahead coordinates and cross-window bounds remain unsupported |
| Shared element/bounds | Scoped pairing and overlay motion | **Supported** for typed one-window navigation endpoints, bounded snapshots, push/pop/replace, and predictive-Back complete/cancel | Use `Modifier.sharedElement`/`sharedBounds` with `NavHost`; cross-window pairing, live reparenting, and shape morphing remain unsupported |
| Timeline inspection and seeking | Tooling can observe and control eligible animation state | **Partially supported**: the optional Preview/Studio tooling performs nonce-bound read-only live-device discovery and 500 ms selected capture; Preview-owned `SeekableTransitionState` remains the only control path | Keep `viewcompose-preview` debug-scoped. Continuous profiling and remote live-application seeking are intentionally unsupported |

## The spring hard cut

The pre-Phase-1 API accepted a nominal duration:

{/* non-executable sample_id="migration.compose-animation-legacy-spring" reason="The removed durationMillis spring overload cannot compile against the current released animation artifacts." visible_explanation="This historical source shows the signature being migrated; do not copy it into current code." */}
```kotlin
spring(
    dampingRatio = 0.8f,
    stiffness = 250f,
    durationMillis = 550,
)
```

That contract did not have Compose spring semantics. It mapped normalized elapsed time through a damped
curve, clamps progress to `0f..1f`, and ends at the requested duration. It cannot communicate real
overshoot, velocity, equilibrium, or gesture continuation.

Phase 1 removes that signature without a deprecated overload or alias. Migration chooses one
of two meanings:

- use `tween(durationMillis = ..., easing = ...)` or `keyframes(...)` when product behavior owns an
  exact interval; or
- use the new `spring(dampingRatio = ..., stiffness = ...)` when physical equilibrium, overshoot,
  interruption velocity, and threshold-based completion are intended.

Deleting only `durationMillis` is not a mechanical migration. Earlier damping and stiffness were
fed into a different equation, so visual tuning must be repeated against the physical
engine. Direct `SpringSpec` construction, `MotionScheme` roles, `animateContentSize`,
`AnimatableCore`, `Animatable`, target-as-state calls, transition channels, Demo code, and custom
design systems are all part of the hard-cut audit.

## Mutation and result mapping

ViewCompose `Animatable<T, V>` uses last-mutation-wins ownership: a newer mutation from a different
job cancels the older caller, stale frames cannot publish, and cancellation retains the latest
accepted value and velocity. It exposes decay, bounds, and structured successful terminal results.

`T` and `V` are deliberately separate. `Int` positions use `Float` velocity, while packed ARGB
`Int` values use signed four-channel `ArgbChannels` inside `AnimationVelocity`. Custom converters implement
`AnimationConverter<T, V>` with destination buffers, stable dimensions, zero velocity, and positive
finite thresholds; the old one-parameter converter has no compatibility alias.

The accepted differences from a simplistic result enum are important:

- cancellation still throws `CancellationException`; it does not return an `Interrupted` result;
- normal target completion, bound collision, and the physical safety-duration guard return
  `Finished`, `BoundReached`, or `DurationLimitReached` respectively;
- `initialVelocity = null` captures the replacement's retained value and velocity atomically;
  supplying an explicit velocity overrides only the captured velocity;
- an invalid replacement fails before ownership changes and does not cancel the active mutation;
- construction validates the initial value and complete converter contract before exposing state;
- `snapTo` and `stop` publish one final idle snapshot with zero retained velocity and no transient
  running state; an invalid snap leaves the active mutation authoritative; and
- a successful target animation publishes the exact target and zero retained velocity.

Gesture code hands off values in converter-domain units per second. The gesture owner, not the
animation engine, owns density conversion, RTL sign, axis projection, and nested-scroll decisions.

## Transition and content ownership

Compose migration should preserve the following ownership rather than merely rename calls:

- `Crossfade` remains alpha-only and keeps at most outgoing and incoming content;
- full `AnimatedContent` uses `contentKey` as subtree identity, measures both children under the
  same parent constraints, moves focus/input/accessibility ownership to the committed incoming
  subtree, and releases outgoing content only after all exit channels settle;
- A-to-B-to-C replacement keeps at most two full subtrees by promoting sampled B to outgoing and
  releasing A once;
- duplicate transform channels use the last declaration of that channel kind; parent/descendant
  alpha multiply, translations add after RTL resolution, and scales multiply around their declared
  origins; and
- renderer apply failure cannot publish candidate identity, focus, geometry, effects, or release.

ViewCompose visibility and content slide distances are finite non-negative fractions of the
participating full measured axis rather than full-size callback results. Start/end resolve from the
segment's captured layout direction. `AnimatedVisibilityScope.transition` exposes the owning
Boolean coordinator, while `AnimatedEnterExit` contributes descendant channels to the same frame
loop and extends the shared removal lifetime. Accepting an exit immediately removes pointer, focus,
and accessibility ownership while retaining drawing through the last channel. Parent and descendant
native hosts preserve the documented transform order and transactional renderer rollback.

Phase 3 intentionally hard-cuts the `AnimatedVisibility` content receiver from `BoxScope` to
`AnimatedVisibilityScope`. Ordinary builder calls continue to compile. A caller that used
`Modifier.align` from the former receiver must add an explicit `Box` and apply alignment in that
box. ViewCompose uses the scoped `AnimatedEnterExit` host instead of Compose's descendant Modifier
because measured bounds, clipping, interaction ownership, and rollback cross a native View boundary.
Do not replace unsupported callback-calculated offsets with draw-only translation on interactive
content.

For keyed `AnimatedContent`, a changed target is admitted only after one candidate tree commits
successfully, adding one commit boundary before the replacement segment so renderer failure cannot
mutate content identity.

Seekable transitions have one writer and one active composition binding. `seekTo` validates a
finite `0f..1f` fraction before ownership changes, cancels and joins the previous command, and maps
that fraction to the longest committed channel duration; shorter channels clamp at their endpoints.
Dynamic channel additions or removals retain the normalized fraction and resample against the new
maximum. A changed target freezes every sampled value as the new start, and seeking always retains
zero physical velocity.

`animateTo` resumes from the seek sample on one autonomous frame loop with zero initial velocity;
there is no public initial-velocity parameter in this phase. `snapTo` publishes no frame and
collapses current state, target state, and both segment endpoints to one idle value. Removing the
binding cancels an active writer and preserves unfinished progress as seeking state. The state owns
no scope, is not automatically saveable, and navigation continues to own predictive-Back commit or
rollback. ViewCompose exposes normalized fraction rather than public nanosecond play-time control,
and it requires explicit `animateTo`/`snapTo` after predictive progress instead of mutating the
navigation transaction.

## Layout and shared-motion mapping

Phase 5 bounds animation uses the immediate ViewCompose layout parent's physical-pixel coordinate
system after RTL resolution. The renderer lays out the current animated rectangle so drawing, hit
testing, and accessibility bounds agree. A draw-only offset is not equivalent.

Phase 6 intentionally does not port Compose's `SharedTransitionLayout`, `SharedTransitionScope`, or
`AnimatedVisibilityScope` parameter. ViewCompose declares typed endpoints directly with
`Modifier.sharedElement(SharedContentKey(...))` or `Modifier.sharedBounds(...)`; the surrounding
`NavHost` already owns cross-destination coordination and consumes its existing committed or
predictive-Back progress. Exactly one source and one target with the same key and mode form a pair.
Duplicate, missing, mismatched, detached, zero-sized, surface-backed, or over-budget peers fall back
per key to ordinary destination motion. The target destination owns input, accessibility, and final
focus while a non-interactive snapshot overlay renders the shared visual. Keys do not pair across
windows, Activities, or processes; live-content reparenting and shape morphing remain unsupported.

## Performance and verification baseline

`AnimationPerformanceBenchmark` freezes four revision-1 workloads before Phase 1:

| Scenario | Current behavior isolated | Measured action |
| --- | --- | --- |
| `animation.specs@1` | fixed-duration spring across Float, Int, encoded color, and `UiDp` | four forward/reverse target round trips after selecting Spring |
| `animation.content@1` | alpha-only `Crossfade` | four forward/reverse content replacements |
| `animation.content-size@1` | wrapper-backed measured-size animation | four expand/collapse round trips |
| `animation.transition@1` | synchronized multi-channel transition | four forward/reverse state segments |

All use five `CompilationMode.None` iterations, an R8/resource-shrunk non-debuggable target, a
five-second unmeasured launch settle, accessibility actions, complete animation settle windows,
and frame CPU plus peak process-memory metrics. Formal comparison requires the same physical
device, fixed CPU/GPU/interconnect policy, workload revision, build mode, refresh rate, and thermal
start, with run-P50 coefficient of variation at or below `0.15`.

The Phase 0 Xiaomi MI 6 / API 28 run accepted all four absolute baselines with identical frame
counts across iterations and run-P50 CV `0.010..0.075`. It has no candidate or same-run Compose
control, so its normalized conclusion is `inconclusive`, not an improvement claim. Exact
percentiles, heap values, APK identities, thermal limitation, and next action are recorded in
[performance Section 2.4.8](../tooling/performance.md#248-animation-revision-1-pre-physics-baseline).

The frozen regression gate is stricter than a visual check: frame CPU P50 may not regress by more
than both 5% and 0.3 ms, P95 by more than both 10% and 0.8 ms, and peak process memory by more than
both 10% and 1,024 KiB. Engine vectors and scratch buffers allocate once per run; content retention,
extra measurement, overlay release, and inactive tooling also have structural counters. Raw output
without an interpreted same-device comparison does not close a phase.

Phase 2 advances only the content fixture to `animation.content@2`. Its primary action measures
keyed AnimatedContent with fade, slide, scale, clipping, and unequal-height size transformation;
the secondary action keeps Crossfade as an alpha-only same-page control. On the fixed-frequency
Xiaomi batch, AnimatedContent changed P50/P95/peak heap by `-1.6%/+7.5%/+3.9%` relative to
Crossfade, with a `+0.651 ms` absolute P95 delta and `+312 KiB` heap delta. Neither crosses the
regression budget, both run-P50 CV values are below `0.01`, and the interpreted conclusion is
`no material change`. Exact values and limitations are recorded in
[performance Section 2.4.10](../tooling/performance.md#2410-animation-revision-2-animatedcontent-comparison).

Phase 3 advances `animation.core` to revision 3 and makes the visibility Demo deliberately more
complex: parent fade, logical slide, pivoted scale, and aligned reveal share one clock with an
opposing descendant transition. Against the merged pre-Phase-3 release-safety control on the same
fixed-frequency Xiaomi batch, candidate P50/P95/peak heap changed by `+2.4%/+3.3%/+3.9%`, or
`+0.197 ms/+0.355 ms/+303 KiB`; none crosses the frozen regression gate. P99 rose by `3.380 ms` to
`15.723 ms` and remains a recorded tail watch item. Because the visible workload and duration are
not identical, this is `no material change` release-safety evidence rather than a like-for-like
throughput or power claim. Exact values, controls, and limitations are recorded in
[performance Section 2.4.11](../tooling/performance.md#2411-animation-revision-3-rich-visibility-release-safety-comparison).

Phase 4 advances only the transition fixture to `animation.transition@2`. It measures normalized
seek followed by autonomous completion across one generic two-dimensional channel and four typed
channels with unequal durations. The rooted Xiaomi fixed-frequency batch produced identical
`200/200/200/200/200` frame counts, frame CPU P50/P95/P99 of
`7.775/10.493/11.718 ms`, median peak heap of `8,474 KiB`, run-P50 CV `0.011`, and zero thermal
throttle sleep. This is an accepted **absolute baseline** for the new workload. It is not compared
longitudinally with `animation.transition@1`, whose controls, action path, channel set, and revision
differ. Exact APK identities, temperature, clock checks, limits, and next action are recorded in
[performance Section 2.4.12](../tooling/performance.md#2412-animation-revision-2-seekable-transition-baseline).

## Migration sequence

1. Inventory every current `SpringSpec`, `spring`, `AnimationSpec`, converter, `Animatable`,
   transition, visibility, `Crossfade`, and `animateContentSize` use.
2. Classify each animation as exact-duration, physical, decay, keyed replacement, visibility,
   seek, bounds, or shared motion. Do not encode one category through another merely because it is
   currently available.
3. Keep alpha-only and duration APIs only where their documented semantics are sufficient. Defer
   shared-motion migration until its owning phase is released.
4. Hard-cut all duration-spring and one-domain converter calls in one change, then retune against
   the physical equation, value/velocity domains, thresholds, reduced motion, and terminal results.
5. Verify cancellation, rapid retargeting, host detach, renderer failure, RTL, focus, input,
   accessibility, and reduced motion for every capability the screen uses.
6. Run the matching revisioned physical-device benchmark before and after a frame, measurement,
   retention, overlay, or tooling path changes.

No Compose runtime or animation dependency enters ViewCompose production artifacts as part of this
migration.
