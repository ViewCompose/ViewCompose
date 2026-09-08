---
schema_version: 2
document_id: module.viewcompose-runtime
doc_type: module
owner:
  kind: module
  id: viewcompose-runtime
version_lane: released
capability_ids:
  - runtime.reusable-content
  - runtime.state
artifact_ids:
  - viewcompose-runtime
sample_ids:
  - module.runtime-dependency
  - module.runtime-reusable-content
  - module.runtime-state
  - module.runtime-snapshot
coordinate: com.viewcompose:viewcompose-runtime:0.1.0-alpha04
minimal_usage_sample_id: module.runtime-state
---

# Runtime

`viewcompose-runtime` is the platform-neutral state, snapshot, observation, and lightweight
composition engine used by the rest of ViewCompose. Use it directly when building an integration
that needs ViewCompose state or composition semantics without an Android `View` host.

This module does not render UI, provide Android lifecycle integration, schedule visual frames, or
persist state across process recreation. Those responsibilities belong to higher-level modules and
their hosts.

## Artifact and stability

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="runtime-module-dependency" sample_id="module.runtime-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-runtime:0.1.0-alpha04")
}
```

- Stability: **Alpha**. Source and binary compatibility may change between alpha releases.
- Platform: Kotlin/JVM, compiled with the Java 11 toolchain; no Android SDK or AndroidX dependency.
- Direct ViewCompose dependencies: none.
- Transitively supplied ViewCompose modules: none.
- Kotlin Coroutines is exposed because the public `snapshotFlow` API returns `Flow`.
- Build baseline for this release: Kotlin 2.2.10. Consumers do not need the Android Gradle Plugin
  unless another selected artifact requires it.

## Minimal state usage

{/* compiled-region source="viewcompose-runtime/src/test/samples/com/viewcompose/runtime/samples/RuntimeSamples.kt" region="runtime-module-state" sample_id="module.runtime-state" build_target=":viewcompose-runtime:compileTestKotlin" */}
```kotlin
val count = mutableStateOf(0)
val label = derivedStateOf { "Count: ${count.value}" }

count.value += 1
check(label.value == "Count: 1")
```

State writes outside an explicit mutable snapshot are committed immediately. Use a transaction when
multiple values must become visible atomically:

{/* compiled-region source="viewcompose-runtime/src/test/samples/com/viewcompose/runtime/samples/RuntimeSamples.kt" region="runtime-module-snapshot" sample_id="module.runtime-snapshot" build_target=":viewcompose-runtime:compileTestKotlin" */}
```kotlin
val count = mutableStateOf(0)
val enabled = mutableStateOf(false)

Snapshot.withMutableSnapshot {
    count.value = 1
    enabled.value = true
}

check(count.value == 1 && enabled.value)
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
- Q3 `ComposerLite.withReusableContent` changes the logical state owner of reusable structure
  without replacing equal pure structural results. An explicit owner transfer reruns only the
  descendant groups that own remembered values, saveable paths, effects, or observations; failed
  preparation restores the previously committed owner.
- `CompositionTimingCollector`, `CompositionTimingScope`, and
  `ComposerLite.prepareRootWithTiming` form the Q3 request-scoped composition timing boundary.
  Only executed scopes are offered; skipped scopes perform no callback or clock read. The collector
  owns one monotonic clock, nesting accounting, caps, and overhead measurement, while the runtime
  supplies a lazily allocated process-local identity and already retained bounded source hints.
- [`MonotonicFrameClock`](https://docs.viewcompose.com/api/viewcompose-runtime/0.1.0-alpha02/viewcompose-runtime/com.viewcompose.runtime.frame/-monotonic-frame-clock/)
  is the platform-neutral timing contract consumed by animation integrations.

The reusable-content owner transfer is explicit so a physical container can retain pure structure
without inheriting another logical item's remembered state:

{/* compiled-region source="viewcompose-runtime/src/test/samples/com/viewcompose/runtime/samples/RuntimeSamples.kt" region="runtime-module-reusable-content" sample_id="module.runtime-reusable-content" build_target=":viewcompose-runtime:compileTestKotlin" */}
```kotlin
val composer = ComposerLite()
var owner = "account-A"
var revision = 0L

fun compose(replaceOwner: Boolean): Any {
    composer.requestRootRecompose()
    return composer.composeRoot {
        composer.runGroup(
            signature = "reusable-host",
            inputs = revision,
        ) {
            composer.withReusableContent(owner, replaceOwner) {
                composer.runGroup(signature = "content") {
                    composer.remember(emptyList()) { Any() }
                }
            }
        }
    }
}

val firstOwnerState = compose(replaceOwner = false)
owner = "account-B"
revision += 1L
val secondOwnerState = compose(replaceOwner = true)

check(firstOwnerState !== secondOwnerState)
composer.dispose()
```

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
- A reusable-content owner must change only in an executing group and must set `replaceOwner` for
  that transfer. The owner participates in remember and saveable identities, so it must be stable
  for one logical lifetime and must not be confused with a physical container identity.
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

## Current-checkout contract hardening

These corrections belong to the unreleased checkout and do not retroactively change published
artifact behavior. Nested snapshots freeze the parent's visible pending values at creation; only
later destination writes participate in conflict detection. Read-only children preserve the same
baseline. A successful apply, including an empty one, is terminal for entry, writes, and subsequent
apply; a child cannot publish into a disposed or already applied parent.

Derived caches distinguish mutable views and local writes. Their upstream subscriptions end when
the last consumer is disposed. Independent reads remain fresh without retaining subscriptions,
and re-observation reconnects dependencies before returning. Calculation failures preserve the
previous subscription set, allowing a later invalidation to trigger another attempt.

Notification occurs after publication and terminal state. Every affected Observation is attempted
at most once per apply, including direct and derived paths; reentrant applies remain independent.
The first callback failure is rethrown with later failures suppressed. The values are already
committed: retrying the whole transaction after this error could duplicate application work.

The accepted 2026-09-06 audit baseline at `d64710459df73f3b42067767bb2f4f273b9eff33`
produced sibling derived values 10/10 instead of 10/20, one false nested conflict, 100 retained
upstream observations after consumer disposal, and zero deliveries to a healthy observer after a
throwing observer. The first candidate source execution produces 10/20, successful nested apply,
zero retained observations, and complete notification with terminal apply. Four reproduced runtime
defects decrease to zero (100% reduction in these four scenarios); classification: **improved** for
correctness. The 818-test standalone baseline and first 835-test candidate both pass; the additional
17 tests exercise isolation, null values, nested merge, lifetime, callback failure, and reentry.
This comparison uses Kotlin 2.2.10, JDK 21, JVM target 11, and the same source aggregation harness;
it does not establish a latency improvement, Android behavior, or Gradle dependency isolation.
Next action: run the final module suite and compiled samples through Gradle, then verify TextField,
animation, and gesture integrations before closing the execution plan.

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

## Final runtime validation

The final candidate adds one more terminal-context regression: global notifications execute against
the committed global view even when `apply()` is called inside `enter()`. The caller's context is
restored afterward, so the applied snapshot still rejects further reads/writes. The new case first
failed and then passed after notification context isolation. The complete Gradle task
`:viewcompose-runtime:test` passes 116 tests, including 19 hardening cases, with zero failures or
skips. Compared with the four accepted audit defects, correctness remains **improved**, with all
four probes corrected; expanded coverage is not a performance measurement. The next action is to
retain the module regressions and use separate device/fan-out benchmarks for performance acceptance.
