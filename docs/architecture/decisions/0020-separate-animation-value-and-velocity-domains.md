---
schema_version: 2
document_id: architecture.animation-value-velocity-domains
doc_type: architecture
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
  - architecture.animation-value-velocity-model
invariants:
  - Animation values and velocity tangents retain distinct compile-time domains end to end.
  - Conversion dimensions, thresholds, and public velocity meanings remain explicit and validated.
evidence:
  - Q3 converter samples and Animation Core tests for integer, ARGB, custom-domain, spring, decay, and retargeting behavior.
---

# ADR-0020: Separate Animation Value and Velocity Domains

- Status: Accepted
- Date: 2026-08-22
- Supersedes: the single-type `AnimationConverter<T>`, `AnimationVelocity<T>`,
  `AnimationState<T>`, `AnimationResult<T>`, and `Animatable<T>` generic detail in ADR-0019

## Context

ADR-0019 correctly requires typed physical velocity, destination-buffer conversion, analytic
spring sampling, velocity-preserving retargeting, decay, bounds, and structured results. Its
provisional type vocabulary nevertheless assumed that an animated value and its velocity always
share one Kotlin type.

That assumption is false for valid built-in domains. A packed ARGB value is one `Int`, while its
physical velocity is four independently signed channel rates. Packing those rates back into an
`Int` loses sign and channel-domain meaning. Integer position also reconstructs as `Int`, while its
sub-unit velocity must remain `Float` to avoid quantization during spring continuation and decay.
Using an untyped `FloatArray` in public results would preserve the numbers but discard compile-time
dimension and unit safety.

Phase 1 has not been released, so preserving the provisional single-type surface would make a
known-invalid foundation public. The alpha compatibility policy requires a hard cut rather than a
parallel adapter or a velocity payload whose meaning depends on the value type.

## Decision

Animation values and velocities use separate generic domains:

{/* non-executable sample_id="architecture.animation-value-velocity-model" reason="This decision excerpt defines the type relationship but omits package context and implementation bodies." visible_explanation="Treat this fence as architectural type vocabulary; use versioned API reference for copy-ready signatures." */}
```kotlin
interface AnimationConverter<T, V> {
    val vectorSize: Int
    val zeroVelocity: V
    val visibilityThreshold: V

    fun convertToVector(value: T, destination: FloatArray)
    fun convertFromVector(vector: FloatArray): T
    fun convertVelocityToVector(velocity: V, destination: FloatArray)
    fun convertVelocityFromVector(vector: FloatArray): V
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
```

`AnimatableCore<T, V>`, composition `Animatable<T, V>`, `TargetAnimation<T, V>`, and
`DecayAnimation<T, V>` carry both types end to end. Public mutation callbacks and results never
erase `V` to a raw vector or `Any`.

The built-in mappings are:

| Value domain `T` | Velocity domain `V` | Meaning |
| --- | --- | --- |
| `Float` | `Float` | domain units per second |
| `Int` | `Float` | integer-domain units per second without position quantization |
| packed ARGB `Int` | `ArgbChannels` | signed alpha, red, green, and blue tangent components |
| `UiDp` | `UiDp` | density-independent pixels per second |

`ArgbChannels` is a public immutable tangent value with signed `alpha`, `red`, `green`, and `blue`
components. It is not a color and cannot be passed where a packed ARGB value is required.
`AnimationVelocity<ArgbChannels>` interprets those components as channel units per second, while a
converter threshold uses the same shape as channel-unit deltas.

Both value and velocity conversion use the same stable `vectorSize`, but each direction has its
own methods. Endpoint, bounds, value, velocity, and threshold vectors are allocated once per
evaluator or mutation and reused. Built-in scalar evaluation adds no engine-owned object per frame
beyond the immutable public state/result objects required by the callback contract.

`visibilityThreshold` belongs to `V` because it supplies both a component shape and the domain-unit
tolerance. The position equilibrium check compares converted value displacement to the converted
threshold components. The velocity equilibrium check divides those same components by the
ADR-0019 `0.016`-second window. Every threshold component must be finite and strictly positive.

This is one hard cut. There is no one-parameter `AnimationConverter<T>` alias, inferred
same-domain compatibility overload, packed-color velocity adapter, or deprecated single-type
`Animatable<T>`. Type inference remains concise for built-ins and `rememberAnimatable`, while a
custom converter must state both domains explicitly.

All other ADR-0019 decisions remain accepted, including physical equations, cancellation,
last-writer mutation ownership, bounds, terminal reasons, motion scaling, transition ownership,
tooling isolation, Q3 classification, and performance budgets.

## Consequences

- Invalid value/velocity pairings fail at compile time rather than corrupting runtime motion.
- Integer and packed-color retargeting preserve signed fractional velocity.
- Custom domains that naturally share a type may use the same type twice without extra wrappers.
- Alpha callers must update custom converters and explicit `Animatable` type declarations; no
  released stable contract is being preserved.
- Later generic transition, seek, gesture-handoff, and inspection APIs inherit one precise velocity
  domain instead of adding feature-specific vector escape hatches.

## Rejected alternatives

### Reuse `T` and document exceptions

Rejected because documentation cannot make negative four-channel velocity representable as a
packed color `Int`, and runtime branching would weaken compile safety.

### Expose `FloatArray` as velocity

Rejected because callers could provide the wrong dimension, mutate retained data, or confuse
units. It would also leak the engine's scratch representation into public state.

### Keep a single generic and add only `ColorVelocity`

Rejected because integer animation has the same position-quantization problem and future domains
may also have distinct tangent types. The converter relationship, not a color special case, is the
stable abstraction.

## Validation

Phase 1 must compile Q3 samples for same-domain and distinct-domain converters; round-trip signed
ARGB velocity; preserve fractional integer velocity through spring retargeting and decay; reject
dimension and non-finite threshold failures; and pass the allocation, deterministic clock,
cancellation, bounds, Demo, device, and same-device performance evidence required by ADR-0019.
