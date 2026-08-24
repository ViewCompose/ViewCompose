# AndroidX Lifecycle Integration

`viewcompose-lifecycle-androidx` connects Kotlin `Flow` collection, ViewCompose composition work,
and committed native Views to AndroidX lifecycle and saved-state owners. It provides owner locals,
lifecycle-aware and composition-only `collectAsState` adapters, paired start/resume effects,
observable lifecycle state, and the typed boundary used by SDK View integrations.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-lifecycle-androidx:0.1.0-alpha01")
}
```

- Stability: **Alpha**. Collection and owner-propagation contracts are reviewed and tested; naming
  may still evolve between alphas.
- Platform: Android library with a minimum SDK inherited from the repository Android policy.
- Runtime, UI Foundation, Android Host, Kotlin Coroutines, AndroidX Lifecycle Runtime, and AndroidX
  SavedState are exposed transitively because their types appear in public APIs.
- It propagates application-owned lifecycle and saved-state owners and may register a scoped View
  provider. It does not own Activities, Fragments, ViewModels, owner registries, SDK state formats,
  or application business state.

## Lifecycle owner propagation

Activity, Fragment, custom container, and navigation destination hosts install their nearest Android
`LifecycleOwner` as `LocalLifecycleOwner`. Delayed child sessions capture the local with the rest of
their declaration environment, so overlay and navigation content observes the intended owner rather
than whichever Activity happens to be current when it later renders.

Read `LocalLifecycleOwner.current` when an owner is optional. Lifecycle-aware collection overloads
resolve it automatically and fail with a direct configuration error when none exists. A custom host
or deliberately nested boundary can install an owner with `ProvideLifecycleOwner(owner) { ... }`.
The previous value is restored when the subtree returns or throws.

Android hosts also install `LocalSavedStateRegistryOwner`. The two locals are deliberately
independent: Fragment content uses the Fragment View owner for lifecycle and the Fragment itself for
saved state, while a navigation destination or graph installs its own object for both. Custom hosts
may use `ProvideSavedStateRegistryOwner(owner) { ... }` only after that registry is attached and
restored.

## Lifecycle-bound Android Views

Reusable SDK integrations subclass `LifecycleAndroidViewAdapter<V, S>` and capture the nearest
`LifecycleOwner` in immutable adapter state. Construction and `update` remain replay-safe. The
protected `onViewCommit` hook runs only after the renderer transaction commits; only then does the
base class install an observer and catch the View up in Android order through `ON_CREATE`,
`ON_START`, and `ON_RESUME` as required.

Owner replacement first drives the old View side down through `ON_PAUSE`, `ON_STOP`, and
`ON_DESTROY`, detaches it, and then runs the new commit and catch-up. The owners never overlap. This
also means a retained navigation destination automatically caps a player, map, or camera View when
the destination becomes hidden, even though the physical View can remain mounted.

`onLifecycleEvent` receives the latest successfully committed state. A lifecycle-event callback
failure is terminal for that binding: bounded downward cleanup and observer removal are attempted
before the error is rethrown. `onViewCommit` must keep SDK-specific work failure-atomic; if it
throws, the base still clears its lifecycle and saved-state bindings instead of leaving preceding
state active. Reset and release always remove both bindings before the protected adapter cleanup
hooks run. The callbacks are synchronous main-thread work and must not issue application-owned
lifecycle commands or block dispatch.

## Committed Android View saved state

Call `AndroidViewCommitScope.bindAndroidViewSavedState(...)` from `onViewCommit` when an SDK View
owns a Bundle payload such as `MapView` state. The stable key is a persistence identity within the
captured `SavedStateRegistryOwner`, not the AndroidView reconciliation key. The integration chooses
and versions its own SDK payload; the framework only isolates registration, replacement, restore,
and cleanup.

The first bind returns `AndroidViewSavedStateBindResult.Initial` with one defensive restored Bundle
or `null`. Later commits with the same owner, key, and version return `Retained` and replace only the
saver, so Android snapshots the latest committed View. A format mismatch or corrupt nested payload
is isolated as absent state without blocking the new provider. Lifecycle-aware adapters clear the
provider automatically on reset, release, or owner destruction; a raw `AndroidViewAdapter` must
call `clearAndroidViewSavedStateBinding()` from its own final cleanup.

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
candidate neither detaches the committed observer nor starts its replacement. If initial setup
throws while an already-active owner is installed during composition commit, the effect remains
pending and retries on a later commit, so setup must be retry-safe. A setup that throws on a later
lifecycle transition detaches its observer and is not retried until identity changes. A throwing
cleanup is terminal. These callbacks run synchronously on the lifecycle dispatch thread and must
not block it. Resolve composition locals while declaring the effect; reading a Local from a later
lifecycle callback fails with its diagnostic name even if an unrelated provider is active on that
thread.

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
| Coordinate one committed native View with a replaceable owner | subclass `LifecycleAndroidViewAdapter<V, S>` |
| Read or install the nearest saved-state owner | `LocalSavedStateRegistryOwner` / `ProvideSavedStateRegistryOwner` |
| Restore and save one SDK View Bundle | `bindAndroidViewSavedState(...)` from commit |

Do not mirror the returned value into a second `MutableState`; reading the returned state already
participates in runtime observation and invalidates the owning composition scope.

## Testing

Use a `LifecycleRegistry` to drive `ON_CREATE`, `ON_START`, `ON_STOP`, and destruction explicitly.
Collection tests should verify initial value, inactive retention, restart delivery, cancellation on
disposal, missing-owner failure, invalid thresholds, and non-overlapping collectors during rapid
restarts. Native-View adapter tests should additionally verify post-commit catch-up, owner
replacement ordering, hidden-destination capping, callback failure cleanup, process recreation,
format mismatch isolation, and one-shot provider removal.

## Phase 2 verification evidence

The 2026-08-24 acceptance against baseline `eb02abc5` passed all 35 lifecycle-module JVM and
Robolectric tests, including six lifecycle-adapter and three SDK saved-state cases. The affected
Host, Renderer, Android aggregate, navigation, and Preview tests also passed. The selected Q3 API
audit, documentation/dependency/release/tooling gates, full `qaQuick` (1,954 tasks in 6 min 35 s),
and `qaPreview` (1,115 tasks in 22 s) all passed.

The baseline had composition-scoped lifecycle effects but no transaction-bound native View owner
or SDK Bundle provider boundary. The accepted implementation records post-commit catch-up, serial
owner replacement, retained-destination capping, failure cleanup, process recreation, and provider
removal, so the behavioral conclusion is **improved**. Gate timings are not normalized for cache
state and support no performance claim. This phase adds no SDK or visual surface, so physical-device
UI evidence would not test a new behavior; real Surface and foreground/background validation starts
with the Media3 integration.

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

`LifecycleAndroidViewAdapter`, the saved-state-owner local, and the committed Android View
saved-state binding are Q3 integration APIs. They add Android Host and AndroidX SavedState to this
artifact's transitive API surface. SDK integrations should hard-cut hand-written lifecycle observer
and provider bookkeeping to these boundaries; application state and playback, permission, or
credential policy remain outside them.
