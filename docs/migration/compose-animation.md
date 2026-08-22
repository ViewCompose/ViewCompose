# Migrate Compose animation

This page compares Jetpack Compose Animation with the current ViewCompose animation line and the
accepted expansion contract. It is not a source-compatibility promise. Similar names describe the
same concept only where the lifecycle, timing, geometry, and interruption rows below agree.

Last verified: **2026-08-22**

Re-verification owner: **maintainers of `viewcompose-animation-core`, `viewcompose-animation`, the
Android renderer, navigation, Preview, and Studio tooling**

## Baselines and evidence limits

The current ViewCompose target is:

| Artifact | Version | Current role |
| --- | --- | --- |
| `viewcompose-animation-core` | `0.1.0-alpha04` | Platform-neutral duration sampling, converters, motion policy, and transition coordination |
| `viewcompose-animation` | `0.1.0-alpha04` | Composition-owned state animation, transitions, visibility, Crossfade, and content-size animation |

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
freezes the target architecture and API quality levels. Rows marked **Planned** below are accepted
design, not available APIs. They remain unsupported for application migration until their phase is
implemented, documented in the owning module, and released.

## Capability matrix

| Concern | Compose `1.12.0` semantic | Current ViewCompose status | Migration decision |
| --- | --- | --- | --- |
| Tween, keyframes, snap, repeat | Duration and repeat specifications | **Supported**, with a narrower keyframe/repeat surface | Revalidate unsupported start offsets, spline keyframes, and path easing before porting |
| Physical spring | Threshold-based solve with value and velocity | **Unsupported**; current `SpringSpec` is a clamped fixed-duration approximation | Do not translate Compose spring tuning into the current API; Phase 1 hard-cuts the approximation |
| `Animatable` mutation ownership | Last mutation cancels the previous call; completion returns result state | **Partially supported**; last writer and cancellation exist, velocity/results/decay/bounds do not | Preserve structured coroutine ownership, but wait for Phase 1 when gesture velocity or terminal reason matters |
| Decay and fling handoff | Decay specs and velocity continuation | **Unsupported** | Keep fling ownership in the existing gesture/platform owner until Phase 1 |
| Target-as-state animation | State-driven typed animation | **Supported** for generic values, Float, Int, encoded ARGB, and `UiDp` | Color interpolation is encoded-channel, not color-space aware |
| `Transition` | Shared state segment, generic channels, seeking | **Partially supported**; one autonomous timeline and four built-in channel types exist | Phase 4 adds public generic, segment-aware, and seekable control |
| `AnimatedVisibility` | Enter/exit algebra, slide, scale, descendant choreography | **Partially supported**; fade and measured-size behavior exist | Phase 3 adds slide, scale, transform origin, scope, and descendant enter/exit |
| `AnimatedContent` | Keyed outgoing/incoming replacement and content transforms | **Unsupported**; ViewCompose intentionally renamed its old alpha-only surface to `Crossfade` | Use `Crossfade` only when alpha replacement is sufficient; Phase 2 owns full replacement |
| Content-size animation | Layout size changes | **Supported**, using an Android renderer wrapper | Revalidate parent constraints and wrapper placement; Phase 5 does not replace this small API |
| Bounds animation | Position and size across layout-coordinate changes | **Unsupported** | Phase 5 adds real layout geometry; do not simulate interactive bounds with draw translation |
| Shared element/bounds | Scoped pairing and overlay motion | **Unsupported** | Phase 6 adds one-session pairing and navigation integration; cross-window motion remains excluded |
| Timeline inspection and seeking | Tooling can observe and control eligible animation state | **Unsupported** | Phase 7 adds request-driven Preview tooling; production artifacts remain inactive and dependency-free |

## The spring hard cut

The current API accepts a nominal duration:

```kotlin
spring(
    dampingRatio = 0.8f,
    stiffness = 250f,
    durationMillis = 550,
)
```

This does not have Compose spring semantics. It maps normalized elapsed time through a damped
curve, clamps progress to `0f..1f`, and ends at the requested duration. It cannot communicate real
overshoot, velocity, equilibrium, or gesture continuation.

Phase 1 removes that signature without a deprecated overload or alias. Migration must choose one
of two meanings:

- use `tween(durationMillis = ..., easing = ...)` or `keyframes(...)` when product behavior owns an
  exact interval; or
- use the new `spring(dampingRatio = ..., stiffness = ...)` when physical equilibrium, overshoot,
  interruption velocity, and threshold-based completion are intended.

Deleting only `durationMillis` is not a mechanical migration. Existing damping and stiffness were
fed into a different equation, so visual tuning must be repeated against the Phase 1 physical
engine. Direct `SpringSpec` construction, `MotionScheme` roles, `animateContentSize`,
`AnimatableCore`, `Animatable`, target-as-state calls, transition channels, Demo code, and custom
design systems are all part of the hard-cut audit.

## Mutation and result mapping

Current ViewCompose `Animatable` already uses last-mutation-wins ownership: a newer mutation from a
different job cancels the older caller, stale frames cannot publish, and cancellation retains the
latest accepted value. Phase 1 keeps that direction and adds velocity, decay, bounds, and
structured successful terminal results.

The accepted differences from a simplistic result enum are important:

- cancellation still throws `CancellationException`; it does not return an `Interrupted` result;
- normal target completion, bound collision, and the physical safety-duration guard return
  `Finished`, `BoundReached`, or `DurationLimitReached` respectively;
- a replacement physical animation continues the retained velocity unless the caller supplies
  another initial velocity;
- `snapTo` and `stop` leave zero retained velocity; and
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

Seekable transitions have one writer. `seekTo` cancels and joins autonomous animation before it
publishes a finite `0f..1f` fraction. Seeking does not manufacture velocity. `animateTo` can resume
from the sampled values with explicit gesture velocity. Seek state is not saveable, and navigation
continues to own predictive-Back commit or rollback.

## Layout and shared-motion mapping

Phase 5 bounds animation uses the immediate ViewCompose layout parent's physical-pixel coordinate
system after RTL resolution. The renderer lays out the current animated rectangle so drawing, hit
testing, and accessibility bounds agree. A draw-only offset is not equivalent.

Phase 6 shared motion scopes keys to one `SharedTransitionLayout` and one navigation session.
Exactly one source and one target form a pair. Duplicate or missing peers, detached coordinates,
or an unplaced root fall back to ordinary local enter/exit. The target destination owns input,
focus, and accessibility while a non-interactive overlay renders the shared visual. Keys do not
pair across windows, Activities, processes, or sessions.

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

## Migration sequence

1. Inventory every current `SpringSpec`, `spring`, `AnimationSpec`, converter, `Animatable`,
   transition, visibility, `Crossfade`, and `animateContentSize` use.
2. Classify each animation as exact-duration, physical, decay, keyed replacement, visibility,
   seek, bounds, or shared motion. Do not encode one category through another merely because it is
   currently available.
3. Keep current alpha-only and duration APIs only where their documented semantics are sufficient.
   Defer physical or shared-motion migration until the owning phase is released.
4. When Phase 1 lands, hard-cut all duration-spring calls in one change and retune against the
   physical equation, velocity, threshold, reduced-motion, and terminal-result contracts.
5. Verify cancellation, rapid retargeting, host detach, renderer failure, RTL, focus, input,
   accessibility, and reduced motion for every capability the screen uses.
6. Run the matching revisioned physical-device benchmark before and after a frame, measurement,
   retention, overlay, or tooling path changes.

No Compose runtime or animation dependency enters ViewCompose production artifacts as part of this
migration.
