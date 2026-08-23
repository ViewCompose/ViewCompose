# Runtime

`viewcompose-runtime` is the platform-neutral state, snapshot, observation, and lightweight
composition engine used by the rest of ViewCompose. Use it directly when building an integration
that needs ViewCompose state or composition semantics without an Android `View` host.

This module does not render UI, provide Android lifecycle integration, schedule visual frames, or
persist state across process recreation. Those responsibilities belong to higher-level modules and
their hosts.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-runtime:0.1.0-alpha03")
}
```

- Stability: **Alpha**. Source and binary compatibility may change between alpha releases.
- Platform: Kotlin/JVM, compiled with the Java 11 toolchain; no Android SDK or AndroidX dependency.
- Direct ViewCompose dependencies: none.
- Transitively supplied ViewCompose modules: none.
- Kotlin Coroutines is exposed because the public `snapshotFlow` API returns `Flow`.
- Build baseline for this release: Kotlin 2.0.21. Consumers do not need the Android Gradle Plugin
  unless another selected artifact requires it.

## Minimal state usage

```kotlin
val count = mutableStateOf(0)
val label = derivedStateOf { "Count: ${count.value}" }

count.value += 1
check(label.value == "Count: 1")
```

State writes outside an explicit mutable snapshot are committed immediately. Use a transaction when
multiple values must become visible atomically:

```kotlin
Snapshot.withMutableSnapshot {
    count.value = 2
    enabled.value = true
}
```

## Principal APIs

- [`State`, `MutableState`, and `derivedStateOf`](https://docs.viewcompose.com/api/viewcompose-runtime/0.1.0-alpha02/viewcompose-runtime/com.viewcompose.runtime/)
  provide snapshot-aware values and lazy dependency-derived state.
- [`Snapshot` and `MutableSnapshot`](https://docs.viewcompose.com/api/viewcompose-runtime/0.1.0-alpha02/viewcompose-runtime/com.viewcompose.runtime/-snapshot/)
  provide consistent reads and atomic buffered writes with conflict reporting.
- [`RuntimeObservation`](https://docs.viewcompose.com/api/viewcompose-runtime/0.1.0-alpha02/viewcompose-runtime/com.viewcompose.runtime.observation/-runtime-observation/)
  is the Q3 explicit invalidation subscription for state reads. One successful global apply calls
  each affected observation at most once on the applying thread, even when several dependencies
  changed; separate applies remain separate opportunities. Q3 `prepareReplacement` reads a
  candidate dependency set through the same Observation identity, then atomically commits it while
  retaining shared subscriptions or aborts it without disturbing the committed dependency set.
- [`snapshotFlow`](https://docs.viewcompose.com/api/viewcompose-runtime/0.1.0-alpha02/viewcompose-runtime/com.viewcompose.runtime/snapshot-flow.html)
  creates a cold Flow that tracks snapshot reads per collector, conflates invalidations, replaces
  conditional dependencies, and emits structurally distinct calculated values.
- [`ComposerLite`](https://docs.viewcompose.com/api/viewcompose-runtime/0.1.0-alpha02/viewcompose-runtime/com.viewcompose.runtime.composition/-composer-lite/)
  provides transactional positional composition, remembered values, effects, and diagnostics without
  compiler-generated change flags.
- `CompositionTimingCollector`, `CompositionTimingScope`, and
  `ComposerLite.prepareRootWithTiming` form the Q3 request-scoped composition timing boundary.
  Only executed scopes are offered; skipped scopes perform no callback or clock read. The collector
  owns one monotonic clock, nesting accounting, caps, and overhead measurement, while the runtime
  supplies a lazily allocated process-local identity and already retained bounded source hints.
- [`MonotonicFrameClock`](https://docs.viewcompose.com/api/viewcompose-runtime/0.1.0-alpha02/viewcompose-runtime/com.viewcompose.runtime.frame/-monotonic-frame-clock/)
  is the platform-neutral timing contract consumed by animation integrations.

The complete generated reference is available under the
[`viewcompose-runtime` API tree](https://docs.viewcompose.com/api/viewcompose-runtime/current/).
Because the current line is
alpha, the documentation site intentionally does not expose a stable `latest` alias.

## State and lifecycle contracts

- `MutableState` equality and snapshot conflict behavior are selected by its
  `SnapshotMutationPolicy`. Equivalent writes do not advance the global snapshot or notify readers.
- A `Snapshot` pins historical records until it is disposed. Always use `close`, `dispose`, or
  Kotlin `use` when a read snapshot is no longer needed.
- A `MutableSnapshot` is either applied or abandoned, then disposed. A failed conflict apply leaves
  its destination unchanged and may be retried; a successful apply is terminal.
- An `Observation` owns subscriptions to every state read during collection. One successful global
  apply invalidates it at most once, with stable first-observed delivery order across affected
  observations. Dispose it to prevent the observed states from retaining that subscription; a
  callback already racing with disposal may finish.
- A `PreparedObservationReplacement` is terminal: call exactly one of `commit` or `abort` after
  external candidate work succeeds or fails. Preparation preserves committed subscriptions and
  temporarily subscribes candidate-only dependencies so no update can disappear or duplicate the
  callback between reading and publication. One Observation permits only one prepared replacement.
- Each `snapshotFlow` collector owns an independent read observation. Cancellation and calculation
  failure release it; the calculation is side-effect-free and may run more often than it emits.
- `ComposerLite` and derived-state instances are intended for thread-confined use. Hosts serialize
  composition, prepared commit/abort, effect delivery, and disposal.
- A composition timing collector is valid only for its synchronous `prepareRootWithTiming` call.
  It cannot retain scopes, invoke application code, block, perform I/O, or re-enter the composer.
  Collector failures are isolated from composition. The ordinary `prepareRoot` path allocates no
  timing identity, performs no per-scope clock read, and keeps no timing history.
- Remembered lifecycle objects remain pending until `onRemembered` returns successfully. A throwing
  activation is retried by a later successful composition commit without reactivating successful
  siblings. Removal before activation invokes `onAbandoned`; an active value terminates through
  exactly one `onForgotten`. Abort cannot retire a previously committed object or activate a
  candidate replacement.
- `ComposerLite.composeRoot` commits runtime state but does not execute one-shot side effects. The
  host calls `commitSideEffects` only after the corresponding rendered tree and remember lifecycle
  transaction have committed successfully.
- `ComposerLite.rememberUpdatedState` exposes a candidate value only to the active composing thread,
  publishes it before committed lifecycle callbacks, and discards it on abort.
- `ComposerLite.scopedExplicitSaveableKey` derives an explicit `rememberSaveable` registry key from
  the current structural key path. Lazy lists, pagers, and other child-session owners use this
  boundary so equal application keys in different logical items cannot share restoration state;
  changing physical holders never changes the derived logical owner. Unequal active keyed groups
  that produce the same structural-path hash fail before saveable provider registration instead of
  sharing restoration state; custom saveable keys therefore require stable, collision-free hashes.
- Explicitly keyed sibling groups may move without losing their complete scope identity, including
  remember slots, observations, children, and saveable paths. Duplicate effective key/signature
  pairs under one parent fail the composition attempt before either logical item can alias state.
- Callback failures keep their original throwable and append bounded effect kind, operation,
  structural scope, slot, and non-retaining key metadata. Hosts may opt into a non-negative
  synchronous callback warning threshold through the `ComposerLite` constructor.

Holding old snapshots retains additional value records, and frequently abandoning structural group
order prevents composition reuse. Neither operation blocks arbitrary user calculations; callers
must keep expensive work outside state accessors and composition blocks or cache it explicitly.

## Related documentation

- [State and snapshot architecture](../../architecture/state-snapshots.md)
- [Transactional effects and structured work](../../architecture/effects.md)
- [Current architecture and module boundaries](../../architecture/overview.md)
- [Published module catalog](../README.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

Android applications normally consume this artifact transitively through `viewcompose-ui-foundation`
or `viewcompose-host-android`. Depend on it explicitly only when its types appear in your own public
API or when building a custom host/runtime integration.

## Compatibility notes

The `0.1.0-alpha02` line establishes the initial snapshot and lightweight-composition contracts.
There is no earlier stable migration path. Do not persist internal snapshot identifiers,
composition saveable keys, diagnostics shapes, or implementation class names as long-lived external
data; only behavior explicitly described by the public API reference is a supported contract.

This release adds `snapshotFlow`, exposing Kotlin Coroutines as an API dependency, and removes the
alpha `ComposerLite.disposableEffect` slot API. Custom composition integrations migrate owned work
to a remembered `RememberObserver`; application UI uses the effect APIs from
`viewcompose-ui-foundation`. Prepared composition now enforces owner-thread, terminal-disposal, and
callback re-entry boundaries. Remember activation failures are retryable, and explicit keyed
siblings move as complete scopes while duplicate effective identities fail fast.
