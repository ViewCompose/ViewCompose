# ADR-0019: Animation Physics, Transition, and Inspection Ownership

- Status: Accepted
- Date: 2026-08-22
- Supersedes: the fixed-duration meaning of `SpringSpec` and `spring` in the alpha animation line
- Amended by: [ADR-0020](0020-separate-animation-value-and-velocity-domains.md), which replaces the
  provisional single-type value/velocity generic vocabulary below
- Phase 4 clarification: 2026-08-23, fixing committed-channel duration, zero-velocity continuation,
  snap endpoint, and typed-channel named-argument semantics

## Context

ViewCompose already has deterministic duration sampling, state-driven animation, `Animatable`, a
shared-timeline `Transition`, fade and size visibility transitions, alpha-only `Crossfade`, and
measured-size animation. The current `SpringSpec`, however, is a fixed-duration damped curve whose
progress is clamped to `0f..1f`. It has no physical velocity, overshoot, equilibrium, decay, or
bounds. Extending that model would make gesture handoff and interruption depend on a name that does
not describe its behavior.

The next animation phases also need one ownership model for outgoing content, explicit seeking,
layout bounds, shared visual elements, and request-driven inspection. These features cross the
platform-neutral animation engine, composition, Android renderer, navigation, Preview, and Studio
tooling. Independent implementations would create competing frame loops, coordinate systems, and
lifecycle owners.

The upstream semantic comparison baseline is AndroidX Compose Animation `1.12.0`, the stable
release dated 2026-08-12. The repository's executable Compose fixture remains on `1.7.8` because
Compose `1.12.0` requires compile SDK 37 and AGP 9.2 while this repository currently uses compile
SDK 36 and AGP 8.13.2. Official release notes and API references are semantic evidence; local
Compose execution is explicitly older evidence and cannot prove `1.12.0` behavior.

## Decision

### One physical engine and a hard-cut spring contract

The alpha-line `SpringSpec(dampingRatio, stiffness, durationMillis)` and
`spring(dampingRatio, stiffness, durationMillis)` contracts will be removed when Phase 1 lands.
There will be no deprecated overload, alias, ignored `durationMillis`, or legacy spring model.
Callers that require a fixed interval use `tween`, `keyframes`, or another explicitly
duration-bearing specification. The new `SpringSpec` and `spring` names are reserved for physical
termination.

The platform-neutral engine uses the normalized-mass second-order system

`x'' + 2ζω₀x' + ω₀²(x - target) = 0`, where `ω₀ = sqrt(stiffness)`.

- normalized mass is exactly `1`;
- `dampingRatio` (`ζ`) is dimensionless and must be finite and at least zero;
- `stiffness` is finite and greater than zero in `s⁻²`;
- position uses each converter component's domain unit and velocity uses that unit per second;
- the analytic under-damped, critically damped, or over-damped solution is evaluated with `Double`
  intermediates and converted to `Float` only at vector and domain boundaries;
- every frame is sampled from the segment start state and monotonic play time, not integrated from
  the previous frame, so skipped frames and deterministic clocks produce the same sample;
- a non-monotonic frame clock is a contract failure and cannot publish the candidate sample; and
- a physical spec has a validated `maxDurationMillis`, defaulting to 10,000 and limited to
  `1..60_000`, as a safety guard rather than an animation duration.

`AnimationConverter<T>` is hard-cut to declare a stable vector size, a positive finite default
visibility threshold in domain units, and destination-buffer conversion. The engine allocates its
position, velocity, threshold, and scratch vectors once per run and reuses them. A converter may
allocate the immutable domain value returned for a sample, but it cannot force endpoint or scratch
array allocation on every frame.

Equilibrium requires every vector component to satisfy both
`abs(value - target) <= visibilityThreshold` and
`abs(velocity) <= visibilityThreshold / 0.016 seconds`. Successful target animation publishes the
exact target with zero retained velocity. Reaching the safety duration does not snap to the target;
it returns `DurationLimitReached` with the last accepted state so an invalid or unexpectedly slow
configuration is observable.

The result model is:

```kotlin
enum class AnimationEndReason {
    Finished,
    BoundReached,
    DurationLimitReached,
}

data class AnimationVelocity<T>(val valuePerSecond: T)

data class AnimationState<T>(
    val value: T,
    val velocity: AnimationVelocity<T>,
    val playTimeNanos: Long,
)

data class AnimationResult<T>(
    val endState: AnimationState<T>,
    val endReason: AnimationEndReason,
)
```

`Interrupted` is deliberately not an end reason. A newer last-writer mutation or external
coroutine cancellation throws `CancellationException`, retains the last atomically published value
and velocity, and returns no result from the cancelled call. A replacement physical animation with
`initialVelocity = null` captures its retained value and velocity in one mutation snapshot; an
explicit initial velocity overrides only the captured velocity. Candidate validation completes
before mutation ownership changes, so an invalid replacement leaves the active mutation
authoritative. Construction validates the initial value and converter contract before exposing
state. `snapTo` and `stop` publish one atomic final idle state with zero retained velocity and no
transient running state; invalid snap input changes no state or ownership. Callback, converter, or
clock failures propagate after leaving the last committed sample authoritative.

Bounds are converter-domain lower and upper values. They are converted once for each mutation.
Every lower component must be no greater than its upper component. A crossing sample is clamped
before publication, terminates the whole run as `BoundReached`, and publishes zero retained
velocity. Updating bounds while idle clamps immediately; updating them while running joins the
same mutation transaction and is observed by the next sample. No out-of-bounds value is visible.

### Decay and gesture handoff

The first decay is platform-neutral exponential decay:

`v(t) = v₀e⁻λᵗ` and `x(t) = x₀ + (v₀ / λ)(1 - e⁻λᵗ)`, with
`λ = 4.2 × frictionMultiplier s⁻¹`.

`frictionMultiplier` must be finite and greater than zero. Decay finishes when every component's
absolute velocity reaches the converter-derived velocity threshold, reaches a bound, or reaches
its validated maximum-duration guard. Android spline fling behavior is not silently substituted
for this equation; a future density- and platform-dependent decay receives a distinct name.

Gesture owners convert platform pixels-per-second into the target converter's units once and pass
that typed velocity to `animateDecay` or `animateTo`. RTL sign resolution and axis projection occur
in the gesture owner before handoff. The animation engine neither reads pointer events nor guesses
density, layout direction, or nested-scroll ownership.

### Motion policy and duration scaling

`MotionScheme` remains a type-neutral role policy. A scale of zero resolves every motion role to
`snap`. Positive duration scaling multiplies duration-bearing specifications normally. For a
physical spring, a time scale `s` resolves stiffness to `stiffness / s²` while retaining damping
ratio and thresholds. Decay friction resolves to `friction / s`. Maximum-duration guards scale with
the same factor and remain within the public validated range. No physical solve receives an
invented nominal duration.

### Content and visibility transition algebra

`Crossfade` remains the small alpha-only contract. `AnimatedContent` owns keyed replacement,
pair-specific `ContentTransform`, optional `SizeTransform`, and an `AnimatedContentScope`.
`AnimatedVisibility` adds slide, scale, transform origin, an owning scope, and descendant
`animateEnterExit` without adding another autonomous frame loop.

The common algebra is fixed as follows:

1. `+` preserves declaration order. For duplicate alpha, size, slide, or scale channels in one
   transition, the last declared channel of that kind wins.
2. Parent and descendant alpha multiply, translations add after RTL resolution, and scales
   multiply around each layer's declared transform origin. Parent clipping is applied last.
3. Both outgoing and incoming content are measured under the same incoming parent constraints.
   Without a size transform, the container uses the maximum current child size. A size transform
   interpolates the container size and declares clipping explicitly.
4. Incoming content draws above outgoing content unless `targetContentZIndex` selects another
   finite order. Equal z values retain declaration order.
5. `contentKey` defines subtree identity. Equal keys patch the retained subtree without a content
   replacement transition. Two unequal states that return the same key are the same identity, not
   a collision fallback.
6. At most two full content subtrees are retained. On A-to-B-to-C interruption, the currently
   incoming B subtree becomes the outgoing subtree from its sampled visual state, A releases once,
   and C enters. This bounds memory and preserves the most recently communicated target.
7. Focus, pointer input, and accessibility ownership move to incoming content when the replacement
   transaction commits. Outgoing content remains renderable but is non-focusable, non-clickable,
   and hidden from accessibility until release.
8. Removal occurs only after every parent and descendant exit channel settles. Host detach cancels
   the segment and releases both subtrees once. A failed candidate apply leaves the prior committed
   pair, identity map, focus owner, and effects authoritative.
9. Existing first-composition `AnimatedVisibility` behavior remains unchanged: initial content is
   rendered at its requested visible endpoint rather than automatically playing enter motion.

### Seek ownership

`SeekableTransitionState<S>` has exactly one mode: autonomous or externally seeking. `seekTo`
cancels and joins the autonomous frame loop before publishing a seek. `animateTo` leaves seek mode
and starts one autonomous loop from the current sampled values. There is never a seek writer and a
frame-loop writer for the same transition.

- `fraction` is finite and in `0f..1f`; invalid input throws `IllegalArgumentException` and does not
  coerce or publish;
- normalized fraction maps to the longest duration in the complete committed channel set, and
  shorter channels clamp to their own terminal sample; committed channel additions or removals
  recompute that duration and resample every surviving channel at the retained fraction;
- retargeting while seeking freezes current sampled channel values as the new starts and resets
  fraction to zero;
- seeking and the Phase 4 seek-to-autonomous continuation supply zero physical velocity because
  position samples do not establish real elapsed input velocity; accepting an explicit gesture
  velocity requires a separately designed overload and a later amendment rather than an implicit
  estimate;
- `snapTo` atomically collapses current state, target state, and both segment endpoints to the
  requested target with fraction zero and no frame loop;
- seek state is not saveable; the logical application or navigation state is restored and the
  visual transition is reconstructed at an endpoint; and
- predictive Back continues to be owned by navigation. Its adapter may drive a seek state, but the
  animation object cannot commit or roll back a navigation stack.

### Bounds and Android layout ownership

`Modifier.animateBounds` operates in the immediate ViewCompose layout parent's local physical-pixel
coordinate system after logical start/end and RTL resolution. It animates a real measured and laid
out rectangle, not only a draw translation, so hit testing and accessibility bounds match the
visible rectangle on every committed frame.

Target measurement uses one lookahead-style candidate measurement per affected node when
constraints or target topology change. Property-only frames reuse the target and do not remeasure
it. A parent, scroll, density, layout-direction, or constraint change retargets from the current
committed rectangle to a target in the new parent coordinate system. Reparenting across an owner
boundary ends local bounds motion and starts the destination's normal enter behavior.

The Android renderer stages measure, layout, hit geometry, accessibility geometry, and animation
ownership in one candidate transaction. Apply failure leaves the previous rectangle and target
authoritative. Visual-only translation is not an accepted fallback for a node that owns input or
accessibility.

Phase 5 implements this with one transparent synthetic host per local owner. Same-chain layout and
parent data move to that host, while drawing, content, input, focus, and semantics remain on the
child. Multiple bounds elements are last-wins; simultaneous bounds and content-size animation is a
pre-mutation ownership error. Duration retargets restart from the sampled rectangle with zero
velocity, physical retargets retain four-edge velocity, and repeated accepted-target layouts keep
the existing writer. Detach and reusable-tree ownership transfer explicitly clear both bounds and
content-size animation state rather than depending on a platform detach callback.

### Shared visual motion

Phase 6 rejects the provisional `SharedTransitionLayout` and scope API. `NavHost` is already the
cross-session coordinator; adding another layout/scope owner would split lifecycle and progress
authority. Q3 `SharedContentKey`, `Modifier.sharedElement`, and `Modifier.sharedBounds` therefore
publish only typed renderer-neutral endpoint metadata. The renderer copies the complete element to
one stable keyed View tag and clears it on reuse. It performs no pairing or animation work.

One outgoing and one incoming endpoint with the same non-blank key and mode form a pair inside one
native `NavHost` transition. Multiple sources or targets, a missing peer, mode mismatch, an unplaced
or detached root, zero size, surface-backed content, capture failure, or exhausted per-transition
pixel budget falls back for that key to ordinary destination motion; no path guesses a winner or
changes the navigation transaction.

Matched content uses immutable software snapshots in a non-interactive host overlay and consumes
the existing committed-motion or predictive-Back geometry fraction. `sharedElement` moves one
source snapshot to target bounds; `sharedBounds` crossfades source and target snapshots along the
same bounds path. Stable outgoing-tree order defines z-order. The outgoing endpoint becomes
invisible while the incoming live endpoint remains the input and accessibility owner behind a
transparent alpha. Commit can transfer prior focus to a focusable target; cancel restores source
focus. No arbitrary shape morph or live-content reparenting is attempted.

Completion, cancellation, redirect, host destruction, session disposal, configuration change,
capture failure, or a failed renderer transaction releases every bitmap, drawable, listener, and
endpoint record exactly once. Redirect restores endpoints before the next transition rescans its
own sessions. Keys never pair across windows, Activities, or processes. Cross-window/shared-process
motion requires a separate architecture decision.

### Request-driven inspection and controlled Preview seeking

The Q3 runtime-neutral source/snapshot port lives in `viewcompose-animation`, because a runtime
artifact cannot depend upward on `viewcompose-preview-core`. Concrete process protocol, bounded
registry, response storage, and Studio presentation remain in the optional Preview artifact and
plugin. Core animation and production animation artifacts contain no Android receiver, socket,
file watcher, polling loop, Studio class, writer, or concrete provider.

Like ADR-0009's source-identity exception, optional-artifact presence may weakly register one
bounded neutral source per committed transition so an already composed transition remains
discoverable. Registration captures only a process-lifetime identity and diagnostic label,
installs no listener or frame callback, and does not construct a snapshot. The concrete receiver
rejects non-debuggable processes, and active capture additionally requires a valid nonce-bearing
explicit request. Discovery takes one snapshot. A selected capture lasts 500 ms and retains at most
64 distinct samples, 32 channels per sample, and 256 KiB encoded output. It reports safe logical
state summaries, channel kinds, durations, play times, privacy-safe values and velocities, physical
terminal reasons, and interruption history. Malformed, oversized, expired, busy, missing, or stale
requests fail closed and release request-owned state.

Running-device tooling is read-only and has no seek command. Controlled seeking is permitted only
inside static or interactive Preview content that owns a `SeekableTransitionState` and calls the
public Phase 4 `seekTo` API. Tooling cannot seize a live application-owned transition or write its
private fields. A new live-process mutation or continuous-profiling proposal requires another ADR
with explicit authority, lifetime, isolation, and benchmark evidence.

### Public API quality and ownership

Every public API family below is Q3. Internal solvers, vector scratch pools, overlay records, and
request codecs are Q0 and cannot appear in compiled samples.

| Phase | Public API family | Owner | Compiled sample and minimum test category | Compatibility |
| --- | --- | --- | --- | --- |
| 1 | changed `SpringSpec`, `spring`, `AnimationConverter`, duration query/sampling entry points | `viewcompose-animation-core` | physical spring/threshold sample; analytic, clock, numeric, invalid-input, allocation tests | hard cut |
| 1 | `DecayAnimationSpec`, `ExponentialDecaySpec`, `exponentialDecay`, `AnimationVelocity`, `AnimationState`, `AnimationResult`, `AnimationEndReason` | `viewcompose-animation-core` | decay/result sample; velocity, bounds, end-reason tests | additive except replaced `AnimationRunResult` |
| 1 | changed `AnimatableCore.animateTo`, `animateDecay`, `updateBounds`, `velocity` | `viewcompose-animation-core` | imperative core sample; cancellation and concurrency tests | hard cut |
| 1 | changed `Animatable.animateTo`, `animateDecay`, `updateBounds`, `velocity` | `viewcompose-animation` | composition sample; last-writer, lifecycle, snapshot tests | hard cut |
| 2 | `ContentTransform`, `SizeTransform`, `AnimatedContentTransitionScope`, `AnimatedContentScope`, `AnimatedContent` | `viewcompose-animation` | keyed replacement sample; identity, measure, focus, rollback, device tests | additive |
| 3 | slide/scale transition factories, `AnimatedVisibilityScope`, `animateEnterExit` | `viewcompose-animation` | combined visibility sample; algebra, RTL, release, device tests | additive |
| 4 | `TransitionSegment`, generic `Transition.animateValue`, segment-aware channel overloads, `SeekableTransitionState`, seekable `rememberTransition` | `viewcompose-animation` | segment/seek sample; ownership, range, retarget, predictive-Back adapter tests | additive except the typed-channel named argument hard-cut from `animationSpec` to `transitionSpec`; internal segment helpers removed |
| 5 | `Modifier.animateBounds` | `viewcompose-animation` | bounds sample; coordinate, remeasure, input, accessibility, rollback device tests | additive |
| 6 | `SharedContentKey`, `sharedElement`, `sharedBounds` | neutral marker in `viewcompose-ui-contract`; renderer tag transport in `viewcompose-renderer-android`; coordinator in `viewcompose-navigation-android` | UI declaration and navigation shared-motion samples; pairing, overlay, lifecycle, redirect, rollback, accessibility device tests | additive; provisional `SharedTransitionLayout`/scope design rejected before publication |
| 7 | immutable neutral animation source/snapshot port | `viewcompose-animation` | port sample; absence, ambiguity, bounds, privacy, interruption, lifecycle tests | additive; concrete tooling remains optional |
| 7 | Android request/capture implementation and read-only Studio client | `viewcompose-preview` and Studio plugin | Preview-only seek sample; activation, isolation, nonce, request-lifetime, codec, plugin UI tests | additive and optional |

Every implementation pull request supplies canonical English API documentation, compiled Q3
samples, owning-module documentation, compatibility notes, and the production-artifact Changeset
required by repository policy.

### Frozen validation fixtures and budgets

The pre-physics macrobenchmark fixture is `AnimationPerformanceBenchmark` with four revision-1
workloads: `animation.specs` duration-spring value channels, `animation.content` cross-fade,
`animation.content-size` measured-size motion, and `animation.transition` synchronized channels.
Each uses an R8/resource-shrunk non-debuggable target, `CompilationMode.None`, five iterations, a
five-second unmeasured launch settle, accessibility actions, and four complete forward/reverse
round trips per iteration. Results are accepted only with a fixed CPU/GPU/interconnect policy,
`NONE`/`LIGHT` thermal starts when the platform exposes thermal status, unchanged workload
revision, and run-P50 CV at or below `0.15`. A pre-API-29 reference device without the platform
thermal-status service instead records the per-method battery-temperature range, requires zero
AndroidX thermal-throttle sleep, and still fails closed on clock drift or unstable timing.

Later phases compare against the nearest unchanged revision-1 workload on the same device and
clock policy. Shared motion additionally uses the revisioned navigation benchmark. Tooling uses a
debuggable paired inactive/requested fixture because release benchmarks cannot observe optional
debug tooling.

| Budget | Acceptance rule |
| --- | --- |
| Frame CPU | P50 fails only above both `5%` and `0.3 ms`; P95 fails only above both `10%` and `0.8 ms` |
| Peak process memory | fails only above both `10%` and `1,024 KiB`; phase-specific retained-tree counters must also pass |
| Engine allocation | position, velocity, threshold, and scratch vectors allocate once per run; built-in scalar sampling adds zero engine-owned per-frame objects |
| Retained content | at most two full `AnimatedContent` subtrees and one overlay representation per matched shared pair |
| Measurement | at most one extra target measurement per affected node and target invalidation; zero extra measurement on property-only frames |
| Inactive tooling | only optional-artifact bounded weak neutral-source registration and one selected-identity check; the receiver enforces debug/request gates before any snapshot, with zero polls, report writes, request-owned objects, or recurring callbacks |
| Requested tooling | at most 64 timeline samples, 32 channels per sample, 256 KiB, and 500 ms; never amortized into the inactive result |

An unstable run, changed workload, mismatched clock policy, or missing counter is `inconclusive`,
not a pass. A regression that crosses a budget blocks the phase or narrows the feature; rerunning
until a favorable sample appears is not accepted evidence.

## Consequences

- Phase 1 is intentionally source- and binary-breaking for the alpha animation artifacts. Existing
  `spring(durationMillis = ...)` calls must choose physical `spring(...)` or a duration spec.
- Physical state, velocity, decay, bounds, deterministic sampling, and results have one
  platform-neutral owner. Android gesture and layout code adapts units but cannot implement another
  solver.
- Content, visibility, seek, bounds, shared motion, and tooling build in dependency order and use
  one transition coordinator rather than parallel frame loops.
- The Android View renderer remains authoritative for committed geometry, hit testing,
  accessibility, overlays, and rollback.
- Compose naming is used only where semantics align. ViewCompose retains its own transaction,
  navigation, and optional-tooling boundaries.

## Rejected alternatives

### Preserve the duration spring as a legacy overload

Rejected because two `spring` factories with different termination models make code review and
motion policy resolution ambiguous. A deprecated or ignored `durationMillis` would preserve the
wrong mental model and delay failures until runtime.

### Patch velocity onto normalized progress

Rejected because derivative-of-progress velocity is not stable across retargeting, clamping, or
converter dimensions and cannot support decay or gesture handoff correctly.

### Let each feature own its own frame loop

Rejected because content, visibility, seeking, bounds, navigation, and tooling would race to
publish related state and could not share atomic segment completion or cancellation.

### Animate bounds as draw translation only

Rejected because visible geometry would disagree with Android hit testing and accessibility.

### Keep a global shared-key or animation registry

Rejected because it can pair unrelated sessions, retain Views and destinations, and impose work on
ordinary frames. Scoped coordinators and request-owned inspection satisfy the required features
without global lifetime.

## Validation

Phase 0 acceptance requires the four revision-1 benchmark methods to compile and produce a stable
root-controlled baseline, repository documentation and translation gates to pass, and the proposed
Q3 inventory to remain implementation-free. Each later phase must meet its row in the API table,
the relevant deterministic and device matrix, same-device performance budgets, transactional
rollback, lifecycle release, reduced-motion behavior, and Changeset requirements before the next
phase begins.

Phase 5 acceptance records real Row/Column/Box/ConstraintLayout and RTL placement, environment
rebinding, nested ownership, focus/clipping, detach and lazy reuse, rollback, endpoint input and
accessibility geometry, zero additional child measurements on property frames, one reviewed Demo
path and Preview Golden, and a fixed-frequency animated-versus-snap comparison. The scoped result
is improved frame latency with no material peak-heap change; total energy and per-object allocation
events remain unmeasured and are not inferred from frame percentiles.

Phase 6 acceptance records typed marker transport and reuse clearing; unique, missing, duplicate,
mismatched, over-budget, detached, and surface-backed endpoint behavior; committed Push/Pop/Replace;
predictive-Back cancel/commit; redirect and host-destruction release; incoming input/accessibility
ownership and terminal focus transfer; one reviewed Demo path and Preview Golden; root-installed
navigation/process-recreation suites; and a fixed-frequency shared-versus-ordinary Push comparison.
Both arms hold exactly 124 frames in every run. Shared P50/P95 are `4.073/8.096 ms` versus control
`3.989/8.487 ms`, and median peak heap is `6,971` versus `6,651 KiB`, so the frozen gates conclude
no material change. The shared P99 of `36.099 ms` versus `30.020 ms` remains a recorded tail watch
item rather than a post-measurement gate change.
