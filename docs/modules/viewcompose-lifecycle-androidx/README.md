# AndroidX Lifecycle Integration

`viewcompose-lifecycle-androidx` connects Kotlin `Flow` collection and ViewCompose composition work to
AndroidX Lifecycle. It provides the lifecycle-owner local installed by Android hosts plus lifecycle-
aware and composition-only `collectAsState` adapters, paired start/resume effects, and observable
lifecycle state.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-lifecycle-androidx:0.1.0-alpha01")
}
```

- Stability: **Alpha**. Collection and owner-propagation contracts are reviewed and tested; naming
  may still evolve between alphas.
- Platform: Android library with a minimum SDK inherited from the repository Android policy.
- Runtime, UI Foundation, Kotlin Coroutines, and AndroidX Lifecycle Runtime are exposed transitively
  because `State`, `UiTreeBuilder`, `Flow`, and lifecycle types appear in public APIs.
- It does not own Activities, Fragments, ViewModels, or saved-state registries.

## Lifecycle owner propagation

Activity, Fragment, custom container, and navigation destination hosts install their nearest Android
`LifecycleOwner` as `LocalLifecycleOwner`. Delayed child sessions capture the local with the rest of
their declaration environment, so overlay and navigation content observes the intended owner rather
than whichever Activity happens to be current when it later renders.

Read `LocalLifecycleOwner.current` when an owner is optional. Lifecycle-aware collection overloads
resolve it automatically and fail with a direct configuration error when none exists. A custom host
or deliberately nested boundary can install an owner with `ProvideLifecycleOwner(owner) { ... }`.
The previous value is restored when the subtree returns or throws.

## Composition-scoped collection

```kotlin
fun UiTreeBuilder.Profile(model: ProfileModel) {
    val profile = model.profile.collectAsState().value
    Text(profile.displayName)
}
```

`StateFlow.collectAsState()` exposes the current `StateFlow.value` synchronously, then launches its
collector after the composition commits. General `Flow.collectAsState(initial)` exposes the supplied
initial value until the first emission.

These overloads collect regardless of Android lifecycle state. They are appropriate for work whose
only boundary is the composition, including custom hosts and non-UI tests. Leaving the composition
cancels collection. Changing the flow or collection context restarts the producer while preserving
the same remembered state holder and its latest value.

## Lifecycle-aware collection

```kotlin
fun UiTreeBuilder.Profile(model: ProfileModel) {
    val profile = model.profile.collectAsStateWithLifecycle().value
    Text(profile.displayName)
}
```

Lifecycle-aware overloads use AndroidX `repeatOnLifecycle`:

- the default threshold is `STARTED`;
- `CREATED`, `STARTED`, and `RESUMED` are supported thresholds;
- collection stops below the threshold without clearing the last value;
- returning to the active state restarts upstream collection;
- cancellation cleanup completes before a rapid restart, so collectors do not overlap;
- reaching `DESTROYED` or leaving composition cancels the producer permanently.

`StateFlow` still supplies its current value synchronously, even when the lifecycle is inactive. A
general Flow displays the caller's `initial` value until its first active emission.

Use the owner overload for normal UI and an explicit `Lifecycle` overload when an infrastructure
component intentionally has no `LifecycleOwner` object.

## Paired lifecycle effects

`LifecycleStartEffect(key)` and `LifecycleResumeEffect(key)` run synchronous paired work only while
the nearest or supplied owner is at least `STARTED` or `RESUMED`. Setup begins after a successful
composition commit. Cleanup runs at the matching down transition, destruction, key or owner
replacement, composition exit, or session disposal.

Each setup ends with `onStopOrDispose { ... }` or `onPauseOrDispose { ... }`. Keys are mandatory and
use structural equality. Replacement cleanup completes before replacement setup; an aborted
candidate neither detaches the committed observer nor starts its replacement. A throwing setup is
detached and is not retried until identity changes. A throwing cleanup is terminal. These callbacks
run synchronously on the lifecycle dispatch thread and must not block it. Resolve composition
locals while declaring the effect; reading a Local from a later lifecycle callback fails with its
diagnostic name even if an unrelated provider is active on that thread.

`Lifecycle.currentStateAsState()` returns one stable composition-owned holder, observes every state
transition after commit, reconciles a transition that races initial installation, and removes its
observer on composition exit.

## Coroutine context and structured ownership

The optional `context` can choose a dispatcher, coroutine name, or other non-Job element for upstream
collection. Passing a `Job`, including `SupervisorJob`, is rejected. The composition's launched
effect and `repeatOnLifecycle` own cancellation; replacing that Job would detach the collector and
allow it to outlive its UI subtree.

Flow identity, lifecycle identity, active threshold, and context are producer restart keys. A
successful composition commit cancels the old producer and starts the new one without replacing the
observable state holder. An abandoned composition attempt does not launch or restart collection.

Upstream exceptions follow structured coroutine semantics and terminate the producer. Handle
recoverable failures in the Flow, for example with `catch`, and expose explicit UI state rather than
depending on an orphan exception handler.

## Choosing an API

| Source and desired boundary | API |
| --- | --- |
| `StateFlow`, composition lifetime only | `collectAsState()` |
| General `Flow`, composition lifetime only | `collectAsState(initial)` |
| `StateFlow`, nearest or explicit owner | `collectAsStateWithLifecycle(...)` |
| General `Flow`, nearest or explicit owner | `collectAsStateWithLifecycle(initial, ...)` |
| No current owner but an explicit lifecycle exists | overload accepting `Lifecycle` |
| Paired setup/cleanup while started | `LifecycleStartEffect(key) { ... }` |
| Paired setup/cleanup while resumed | `LifecycleResumeEffect(key) { ... }` |
| Observe lifecycle state in declarative content | `lifecycle.currentStateAsState()` |

Do not mirror the returned value into a second `MutableState`; reading the returned state already
participates in runtime observation and invalidates the owning composition scope.

## Testing

Use a `LifecycleRegistry` to drive `ON_CREATE`, `ON_START`, `ON_STOP`, and destruction explicitly.
Tests should verify initial value, inactive retention, restart delivery, cancellation on disposal,
missing-owner failure, invalid thresholds, and non-overlapping collectors during rapid restarts.

## Related documentation

- [Android host module](../viewcompose-host-android/README.md)
- [UI Foundation module](../viewcompose-ui-foundation/README.md)
- [Lifecycle and saved-state architecture](../../architecture/lifecycle-and-saved-state.md)
- [Transactional effects and structured work](../../architecture/effects.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

The complete generated reference is available in the
[`viewcompose-lifecycle-androidx` API tree](https://docs.viewcompose.com/api/viewcompose-lifecycle-androidx/current/).

## Compatibility notes

The `0.1.0-alpha01` line establishes nullable owner lookup, scoped owner provision, commit-aware
collector launch, structured cancellation, `repeatOnLifecycle` restart behavior, and retained state
across inactive periods. Keep flow errors explicit and never pass an independent Job as collection
context.

`LifecycleStartEffect`, `LifecycleResumeEffect`, and `Lifecycle.currentStateAsState()` are additive
Q3 lifecycle APIs in this release. Paired effects require at least one explicit key and do not
replace the existing Flow collection APIs; choose them when the owned work itself, rather than only
its data collection, must enter and leave with an Android lifecycle threshold.
