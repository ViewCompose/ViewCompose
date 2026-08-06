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
    implementation("com.viewcompose:viewcompose-runtime:0.1.0-alpha02")
}
```

- Stability: **Alpha**. Source and binary compatibility may change between alpha releases.
- Platform: Kotlin/JVM, compiled with the Java 11 toolchain; no Android SDK or AndroidX dependency.
- Direct ViewCompose dependencies: none.
- Transitively supplied ViewCompose modules: none.
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
  turns state reads into an explicit invalidation subscription.
- [`ComposerLite`](https://docs.viewcompose.com/api/viewcompose-runtime/0.1.0-alpha02/viewcompose-runtime/com.viewcompose.runtime.composition/-composer-lite/)
  provides transactional positional composition, remembered values, effects, and diagnostics without
  compiler-generated change flags.
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
- An `Observation` owns subscriptions to every state read during collection. Dispose it to prevent
  the observed states from retaining that subscription.
- `ComposerLite` and derived-state instances are intended for thread-confined use. Hosts serialize
  composition, prepared commit/abort, effect delivery, and disposal.
- `ComposerLite.composeRoot` commits runtime state but does not execute effects. The host calls
  `commitSideEffects` only after its corresponding rendered tree has committed successfully.

Holding old snapshots retains additional value records, and frequently abandoning structural group
order prevents composition reuse. Neither operation blocks arbitrary user calculations; callers
must keep expensive work outside state accessors and composition blocks or cache it explicitly.

## Related documentation

- [State and snapshot architecture](../../architecture/state-snapshots.md)
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
