---
schema_version: 2
document_id: migration.compose-state-recomposition-restoration
doc_type: migration
owner:
  kind: capability
  id: runtime.state
version_lane: released
capability_ids:
  - runtime.state
  - foundation.effects
artifact_ids:
  - viewcompose-runtime
  - viewcompose-ui-foundation
  - viewcompose-android
  - viewcompose-host-android
  - viewcompose-lifecycle-androidx
  - viewcompose-viewmodel-androidx
sample_ids: []
source_state: Jetpack Compose Runtime, UI, and Foundation 1.12.0 state, composition, effect, and saveable-state semantics.
target_state: ViewCompose Runtime 0.1.0-alpha04 and current UI Foundation and Android ownership contracts.
---

# Migrate Compose state, recomposition, and restoration

This page compares the state and composition semantics of Jetpack Compose with ViewCompose and
defines a migration path from a Compose-owned UI to a ViewCompose-owned Android `View` tree. It is
an engineering comparison, not a source-compatibility promise: similarly named APIs do not imply
identical compiler, invalidation, identity, or restoration behavior.

Last verified: **2026-08-27**

Re-verification owner: **maintainers of `viewcompose-runtime`, `viewcompose-ui-foundation`,
`viewcompose-android`, and the AndroidX lifecycle integrations**

## Baseline and comparison rules

The supported comparison target is the following independently versioned ViewCompose set:

| Artifact | Version | Role in this page |
| --- | --- | --- |
| `viewcompose-runtime` | `0.1.0-alpha04` | Mutable state, derived state, snapshots, observation, and `ComposerLite` |
| `viewcompose-ui-foundation` | `0.1.0-alpha02` | `remember`, `key`, effects, `Saver`, and `rememberSaveable` |
| `viewcompose-android` | `0.1.0-alpha02` | Activity/Fragment entry points and default Android owner installation |
| `viewcompose-host-android` | `0.1.0-alpha05` | Low-level custom-container hosting and Android SavedState bridge |
| `viewcompose-lifecycle-androidx` | `0.1.0-alpha02` | Composition- and lifecycle-scoped state collection |
| `viewcompose-viewmodel-androidx` | `0.1.0-alpha02` | AndroidX ViewModel and `SavedStateHandle` ownership |

The upstream stable semantic baseline is:

- Compose Runtime, UI, and Foundation `1.12.0`;
- Activity `1.13.0`;
- Lifecycle `2.11.0`;
- SavedState `1.5.0`.

The upstream versions and release status were checked against the official
[Compose Runtime](https://developer.android.com/jetpack/androidx/releases/compose-runtime),
[Compose Foundation](https://developer.android.com/jetpack/androidx/releases/compose-foundation),
[Activity](https://developer.android.com/jetpack/androidx/releases/activity),
[Lifecycle](https://developer.android.com/jetpack/androidx/releases/lifecycle), and
[SavedState](https://developer.android.com/jetpack/androidx/releases/savedstate) release notes.
Compose `1.12.0` adds keyed `SideEffect` overloads and continues the experimental `LinkTable`
work. ViewCompose already exposes keyed `SideEffect` overloads, but its explicit positional DSL,
transactional commit boundary, and runtime remain intentionally independent. Neither upstream
change alters the capability labels in this page. Lifecycle `2.11.0` adds Compose-scoped ViewModel
ownership APIs; that addition affects ownership choices but does not make the two composition
runtimes equivalent.

The repository's executable comparison fixtures intentionally remain on an older set:

| Dependency | Repository version |
| --- | --- |
| Compose Runtime, UI, and Foundation | `1.7.8` |
| Activity | `1.12.4` |
| Lifecycle | `2.8.7` |
| Kotlin and Compose compiler plugin | `2.0.21` |

These versions are recorded in `gradle/libs.versions.toml`. Consequently, this page uses two
different evidence classes:

1. **Official semantic evidence** describes the Compose `1.12.0` and AndroidX baseline and links
   only to Android's official documentation or API reference.
2. **Repository execution evidence** describes current ViewCompose source, tests, and compiled
   samples. It does not prove Compose `1.12.0` behavior because the local comparison dependency is
   `1.7.8`.

Capability labels have fixed meanings:

- **Supported** — ViewCompose provides the migration-relevant behavior with repository evidence.
- **Partially supported** — the main use case exists, but an important semantic or API boundary
  differs.
- **Intentionally different** — ViewCompose deliberately uses a different ownership or execution
  model and callers must adopt it.
- **Unsupported** — no corresponding public ViewCompose capability exists in this release.

No quantitative performance equivalence is claimed. Performance guidance below concerns execution
boundaries only and is not a benchmark result.

## Compiled side-by-side starting point

This pair is the smallest executable state migration anchor in the repository. Both snippets come
from `:samples:compose-migration`; `qaQuick` compiles the module and rejects either snippet if it
stops matching its marked source region.

Compose source:

{/* paired-sample source="samples/compose-migration/src/main/java/com/viewcompose/samples/migration/state/ComposeStateSample.kt" region="compose-state" */}
```kotlin
@Composable
fun ComposeStateCounter() {
    var count by remember { mutableIntStateOf(0) }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(24.dp),
    ) {
        BasicText("Count: $count")
        BasicText(
            text = "Increment",
            modifier = Modifier.clickable { count += 1 },
        )
    }
}
```
{/* paired-sample-end */}

ViewCompose target:

{/* paired-sample source="samples/compose-migration/src/main/java/com/viewcompose/samples/migration/state/ViewComposeStateSample.kt" region="viewcompose-state" */}
```kotlin
fun UiTreeBuilder.ViewComposeStateCounter() {
    val count = remember { mutableStateOf(0) }

    Column(
        spacing = 16.dp,
        modifier = Modifier.padding(24.dp),
    ) {
        Text("Count: ${count.value}")
        Button(
            text = "Increment",
            onClick = { count.value += 1 },
        )
    }
}
```
{/* paired-sample-end */}

The target keeps the state object explicit and reads `value` while building the ViewCompose tree.
This verifies a compileable syntax path, not equivalence of snapshot transactions, compiler
restart scopes, keyed identity, effects, or restoration; use the contracts below for those
decisions.

## Choose the state owner before migrating

Do not begin by replacing API names. First decide which lifetime owns each value:

| Required lifetime | ViewCompose owner | Migration guidance |
| --- | --- | --- |
| One committed composition position | `remember` | Use for replaceable in-memory objects and state holders |
| Composition plus Activity/Fragment recreation | `rememberSaveable` with an installed `SaveableStateRegistry` | Save only the minimum UI state needed to reconstruct the screen |
| Screen or navigation destination business state | AndroidX `ViewModel` through `viewcompose-viewmodel-androidx` | The `ViewModelStoreOwner`, not the call position, defines lifetime |
| Mounted eager scroll position | Q3 `ScrollState` | Retain a caller-owned state; it attaches only while one ScrollableColumn/Row backend is mounted |
| Mounted pager observation and commands | Q3 `PagerState` plus controlled `currentPage` | Keep `currentPage` authoritative; treat `onPageChanged` as a settled-idle event |
| System-initiated process recreation for ViewModel state | `SavedStateHandle` | Store small reconstruction inputs rather than derived screen models |
| Durable application data | Repository or database outside composition | Neither `rememberSaveable` nor `SavedStateHandle` is durable storage |

Lifecycle `2.11.0` also provides Compose-specific scoped ViewModel APIs. They are relevant to the
source application's ownership design, but ViewCompose destination, graph, Activity, and Fragment
owners are installed through its own host and navigation integrations. See the
[ViewModel integration manual](../modules/viewcompose-viewmodel-androidx/README.md) before translating a
Compose-scoped ViewModel boundary.

## Mutable state and mutation policies

Compose `mutableStateOf` creates observable snapshot state. Reading it from a composable subscribes
the current recompose scope, and a non-equivalent write schedules affected work. The official
[state guide](https://developer.android.com/develop/ui/compose/state) and
[`SnapshotMutableState` reference](https://developer.android.com/reference/kotlin/androidx/compose/runtime/snapshots/SnapshotMutableState)
define that upstream contract.

ViewCompose provides the same migration-level model:

- `MutableState.value` reads participate in the current snapshot and `RuntimeObservation`;
- writes outside an explicit mutable snapshot use an automatic mutable transaction;
- `SnapshotMutationPolicy.equivalent` suppresses equivalent writes, global-version advancement,
  and observation invalidation;
- `SnapshotMutationPolicy.merge` resolves concurrent writes when it can produce a non-null merged
  value;
- observation callbacks run on the thread applying the successful write, while a host is
  responsible for serializing composition and Android work.

Repository evidence:

- `viewcompose-runtime/src/main/java/com/viewcompose/runtime/state/State.kt`, lines 25-80;
- `viewcompose-runtime/src/main/java/com/viewcompose/runtime/state/MutableStateImpl.kt`, lines 16-114;
- `viewcompose-runtime/src/main/java/com/viewcompose/runtime/state/SnapshotMutationPolicy.kt`,
  lines 3-119;
- `viewcompose-runtime/src/main/java/com/viewcompose/runtime/observation/RuntimeObservation.kt`,
  lines 9-94;
- `viewcompose-runtime/src/test/java/com/viewcompose/runtime/SnapshotStateTest.kt`, lines 15-177;
- `viewcompose-runtime/src/test/java/com/viewcompose/runtime/observation/RuntimeObservationTest.kt`,
  lines 18-122;
- compiled samples in
  `viewcompose-runtime/src/test/samples/com/viewcompose/runtime/samples/RuntimeSamples.kt`.

One successful global apply invokes an affected `RuntimeObservation` at most once on the applying
thread, even when several observed states changed. Separate applies remain separate callback
opportunities; ViewCompose does not debounce observations across transactions, frames, or time.
This is a ViewCompose callback contract, not a promise of callback-count equivalence with Compose.

## Derived state and invalidation differences

Compose `derivedStateOf` caches a calculation and tracks every snapshot state read by that
calculation. The overload accepting a `SnapshotMutationPolicy` controls when a changed calculation
result updates observers. Android's
[`derivedStateOf` reference](https://developer.android.com/reference/kotlin/androidx/compose/runtime/package-summary#derivedStateOf(androidx.compose.runtime.SnapshotMutationPolicy,kotlin.Function0))
and [side-effects guide](https://developer.android.com/develop/ui/compose/side-effects#derivedstateof)
describe the common use case: reduce downstream recomposition when inputs change more frequently
than the derived result.

ViewCompose `derivedStateOf` is **Partially supported**. It is lazy, caches its last result, observes
its calculation dependencies, and invalidates its own observers when a dependency changes. It does
not expose a result mutation-policy overload, and its public contract explicitly states that equal
derived results are not suppressed. It also revalidates the cached calculation against the current
snapshot read token.

Repository evidence:

- `viewcompose-runtime/src/main/java/com/viewcompose/runtime/state/State.kt`, lines 67-80;
- `viewcompose-runtime/src/main/java/com/viewcompose/runtime/state/DerivedStateImpl.kt`, lines 9-68;
- `viewcompose-runtime/src/test/java/com/viewcompose/runtime/DerivedStateTest.kt`, lines 11-40;
- `derivedStateSample` in the compiled runtime samples.

Migration consequence: do not translate a Compose `derivedStateOf` optimization and promise that
an equal result suppresses ViewCompose invalidation. If that suppression is required for
correctness or cost control, publish a separately compared `MutableState` value or move the
calculation behind an explicit stable input boundary. Nested derived-state behavior, dependency
switching, calculation failures, and equal-result invalidation need broader regression coverage
before stronger parity can be documented.

## Snapshots, atomic updates, and conflicts

Compose mutable snapshots isolate writes and publish them atomically on `apply`; disposing an
unapplied snapshot publishes none of its changes. Active snapshots retain state history and must be
disposed. See the official
[`Snapshot` reference](https://developer.android.com/reference/kotlin/androidx/compose/runtime/snapshots/Snapshot)
and
[`MutableSnapshot` reference](https://developer.android.com/reference/kotlin/androidx/compose/runtime/snapshots/MutableSnapshot).

ViewCompose supports consistent read snapshots, automatic mutable transactions, explicit mutable
snapshots, nested mutable application into a parent, atomic global application, conflict reporting,
policy-based conflict merging, and history pruning after old readers dispose. `withMutableSnapshot`
disposes its temporary transaction and throws `SnapshotApplyConflictException` when a merge cannot
resolve all conflicts.

This area is still **Partially supported**, rather than fully equivalent:

- ViewCompose snapshots are explicitly not safe for concurrent entry or mutation of the same
  instance;
- the ViewCompose policy protocol uses `null` to mean an unmergeable conflict, so it cannot express
  a successful merge whose result is `null`;
- Compose rejects creation of a mutable snapshot from a current read-only snapshot, while the
  current ViewCompose implementation derives a root mutable snapshot from the current read ID and
  does not perform that rejection;
- ViewCompose does not provide Compose `SnapshotStateList`, `SnapshotStateMap`, or
  `SnapshotStateSet`. It does provide a cold `snapshotFlow` with per-collector read observation,
  conflated invalidations, conditional dependency replacement, and structural distinct emission.
  Its calculation must remain side-effect-free and runs in the collector coroutine.

Repository evidence:

- `viewcompose-runtime/src/main/java/com/viewcompose/runtime/snapshot/Snapshot.kt`, lines 5-197;
- `viewcompose-runtime/src/main/java/com/viewcompose/runtime/snapshot/SnapshotRuntime.kt`,
  lines 49-296;
- `viewcompose-runtime/src/main/java/com/viewcompose/runtime/state/MutableStateImpl.kt`,
  lines 98-142;
- `viewcompose-runtime/src/test/java/com/viewcompose/runtime/SnapshotStateTest.kt`;
- `viewcompose-runtime/src/test/java/com/viewcompose/runtime/SnapshotApiTest.kt`;
- `viewcompose-runtime/src/main/java/com/viewcompose/runtime/state/SnapshotFlow.kt`;
- `viewcompose-runtime/src/test/java/com/viewcompose/runtime/SnapshotFlowTest.kt`.

The read-only-to-mutable nesting difference is inferred from the current implementation and lacks a
dedicated regression test. Treat it as a migration risk, not as a feature to depend on. Replace
Compose snapshot collections with immutable collections stored in `MutableState`, or keep the
collection in an external observable owner and collect it through
[`viewcompose-lifecycle-androidx`](../modules/viewcompose-lifecycle-androidx/README.md).

## Recomposition without the Compose compiler

Compose compiler transformation is central to upstream recomposition. It creates restart groups,
records changed arguments, infers stability, and decides which composables can be skipped. Strong
skipping additionally changes comparison and lambda memoization rules. The official
[stability guide](https://developer.android.com/develop/ui/compose/performance/stability),
[strong-skipping guide](https://developer.android.com/develop/ui/compose/performance/stability/strongskipping),
and [composable lifecycle guide](https://developer.android.com/develop/ui/compose/lifecycle)
describe those rules.

ViewCompose is **Intentionally different**. `ComposerLite` coordinates positional groups without
compiler-generated change flags. Widget and renderer integrations establish explicit groups with
`runGroup(signature, inputs)`. Each executed group collects state reads with
`RuntimeObservation`; an invalidation marks that scope and its ancestors dirty, queues the scope,
and allows clean siblings to reuse cached results. Explicit inputs are compared with Kotlin
equality. There is no stable/unstable type inference, `@Stable` effect, compiler-generated lambda
memoization, or automatic composable-function restart boundary.

Repository evidence:

- `viewcompose-runtime/src/main/java/com/viewcompose/runtime/composition/ComposerLite.kt`;
- `viewcompose-runtime/src/main/java/com/viewcompose/runtime/composition/RecomposeScope.kt`;
- `viewcompose-runtime/src/main/java/com/viewcompose/runtime/composition/InvalidationQueue.kt`,
  lines 3-76;
- `viewcompose-runtime/src/test/java/com/viewcompose/runtime/composition/ComposerLiteTest.kt`,
  lines 16-169;
- `viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/runtime/SubtreeRecompositionTest.kt`;
- `viewcompose-runtime/src/test/kotlin/com/viewcompose/runtime/composition/ComposerDiagnosticsTest.kt`.

Migration consequence: move frequently changing reads into the smallest ViewCompose component or
node group that should update. Do not port `@Stable`, `@Immutable`, or Compose compiler reports as
ViewCompose optimization controls. Verify actual recomposed and skipped groups with ViewCompose
diagnostics. Plain Kotlin function boundaries do not automatically become restart scopes.

### Explicit property transactions

ViewCompose Q3 observed properties are an **Intentionally different** opt-in for a narrower case.
`observedValue(inputs) { ... }` and `observedNodeSpec(inputs) { ... }` move their State reads out of
the enclosing composition scope. A `RenderSession` reads all dirty property declarations from one
Snapshot and asks the renderer to patch their exact committed nodes atomically. The first typed
integration is `Text(observedValue { state.value })`; the low-level observed `emit` overload accepts
a complete same-concrete-type `NodeSpec`.

This is not Compose compiler skipping. Node type, key, Modifier, children, and captured environment
remain structural and use ordinary composition. Every changing non-State Kotlin capture must appear
in `inputs`; omitting it is unsupported because ViewCompose cannot infer changed arguments. A
property-contract violation fails and rolls back instead of silently falling back to a whole-tree
render. Use this boundary for high-frequency leaf properties with a complete renderer patch
contract, and keep conditional children or node replacement in `RecomposeBoundary`.

Repository evidence includes `observedTextValueSample`, `observedNodeSpecSample`, runtime dependency
replacement tests, RenderSession batching/failure tests, Android multi-target rollback tests, and
[the observed-property ADR](../architecture/decisions/0015-observed-property-transactions.md).

## Remembered identity, keys, and reordering

Both runtimes use composition position and keys to retain in-memory values, but their structural
matching is not equivalent.

ViewCompose `remember` uses the next positional slot of the current `RecomposeScope`. Its keys use
structural equality, and a changed key creates a candidate replacement. Prepared composition makes
that replacement transactional: commit invokes remember lifecycle callbacks, while abort restores
the previous slot and abandons the new value.

ViewCompose `key` extends the current key namespace used by group signatures, remember slots,
effects, observations, child scopes, and automatic saveable keys. An explicitly keyed sibling scope
can move to a different sibling position as one complete logical identity. Duplicate effective
key/signature pairs under one parent fail the composition attempt before either item can alias
state. Prepared abort restores the previous order, observations, and invalidation ownership.

Repository evidence:

- `viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/composition/Remember.kt`;
- `viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/composition/Key.kt`;
- `viewcompose-runtime/src/main/java/com/viewcompose/runtime/composition/ComposerLite.kt`;
- `viewcompose-runtime/src/test/java/com/viewcompose/runtime/composition/ComposerLiteTest.kt`;
- `viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/runtime/RememberTest.kt`.

Use stable `key` scopes to preserve ordinary sibling identity across insertion, deletion, and
reordering. Keys must remain unique at that structural boundary. Lazy containers still use their
separate item-key, revision, Session, and native-tree reuse contract; ordinary scope movement does
not replace it.

## Effects and committed-frame boundaries

Compose effects run only after successful composition. `SideEffect` publishes current state after
each successful recomposition; `DisposableEffect` cleans up when keys change or the call leaves the
Composition; `LaunchedEffect` cancels and restarts its coroutine with its keys. See Android's
[side-effects guide](https://developer.android.com/develop/ui/compose/side-effects).

ViewCompose supports these migration-level lifecycles, with a host-specific commit boundary:

- candidate effects are recorded during composition and discarded if the prepared composition
  aborts;
- after the renderer establishes the new native tree, `RenderSession` commits the prepared runtime
  composition;
- committed `rememberUpdatedState` values publish before lifecycle callbacks;
- every outgoing remembered, disposable, and launched lifecycle runs before any incoming one;
- `LaunchedEffect` and `DisposableEffect` start from remembered observers during runtime commit;
- `SideEffect` operations then run from `commitSideEffects` in declaration order;
- native commit callbacks run after composition side effects;
- leaving composition or disposing the session cancels coroutines and runs committed cleanup.

Repository evidence:

- `viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/effects/SideEffect.kt`;
- `viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/effects/DisposableEffect.kt`;
- `viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/effects/CoroutineEffects.kt`;
- `viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/composition/ProduceState.kt`;
- `viewcompose-runtime/src/main/java/com/viewcompose/runtime/composition/ComposerLite.kt`;
- `viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/session/RenderSession.kt`;
- `viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/runtime/SideEffectTest.kt`;
- `viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/runtime/DisposableEffectTest.kt`;
- `viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/runtime/CoroutineEffectsTest.kt`;
- `viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/runtime/RenderSessionFailureTest.kt`.

Failures after the renderer establishes the new native tree are reported as committed-frame
failures and do not roll that tree back. Effects must therefore contain only post-commit work and
must handle their own failure cleanup. A throwing remembered activation remains pending and is
retried on a later successful commit; a `DisposableEffect` setup must therefore tolerate retry until
it returns its cleanup. `DisposableEffect` and `LaunchedEffect` require at least one key in this
release. Disposable setup must finish with `onDispose { ... }`; the former lambda-return cleanup
shape is not accepted. ViewCompose also provides keyed `SideEffect` overloads for
change-only publication, while the unkeyed overload remains the every-invocation form. A launched
effect's dispatcher and parent job come from the installed ViewCompose host context rather than a
Compose `Recomposer`.

## Saveable state and Saver migration

Compose `rememberSaveable` retains a value through recomposition and uses the saved-instance-state
mechanism for Activity or system-initiated process recreation. Inputs reset the retained value, and
a `Saver`, `listSaver`, or `mapSaver` converts domain state to a saveable representation. Android's
[state guide](https://developer.android.com/develop/ui/compose/state#store-state-with-keys-beyond-recomposition),
[state-saving guide](https://developer.android.com/develop/ui/compose/state-saving), and
[`rememberSaveable` API](https://developer.android.com/reference/kotlin/androidx/compose/runtime/saveable/rememberSaveable.composable)
define the upstream behavior.

ViewCompose is **Partially supported**:

- `rememberSaveable` falls back to ordinary `remember` when no registry is installed;
- inputs reset the holder but are not stored in its saved representation;
- automatic keys combine the structural group path, positional saveable slot, and active explicit
  key hash;
- `Saver`, `autoSaver`, `listSaver`, `mapSaver`, and `mutableStateSaver` are available;
- ViewCompose still accepts a user-provided string key, while Compose `1.12.0` marks custom
  `rememberSaveable` keys unsupported in favor of positional scoping;
- explicit keys must be nonblank and unique among active providers in one registry.

Repository evidence:

- `viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/saveable/RememberSaveable.kt`,
  lines 5-160;
- `viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/saveable/Saver.kt`,
  lines 8-90;
- `viewcompose-runtime/src/main/java/com/viewcompose/runtime/composition/ComposerLite.kt`;
- `viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/runtime/RememberSaveableTest.kt`;
- compiled saveable-registry sample in
  `viewcompose-ui-foundation/src/test/samples/com/viewcompose/ui/foundation/samples/WidgetCoreSamples.kt`.

Prefer automatic positional keys. Use a ViewCompose explicit key only when the ownership design
requires one, keep it stable and unique, and do not carry that pattern back to current Compose.
Never persist an automatic saveable key outside the registry. Save small reconstruction inputs,
not large lists or complete screen models, because Android's saved-state Bundle has a finite size.

## Restoration transactions: claim, commit, and release

Compose's public `SaveableStateRegistry.consumeRestored` contract removes a restored value when it
is consumed, so that key cannot restore the same value twice. See the official
[`SaveableStateRegistry` reference](https://developer.android.com/reference/kotlin/androidx/compose/runtime/saveable/SaveableStateRegistry).

ViewCompose is **Intentionally different** because a render frame is prepared before the native
tree commits. Its registry uses a claim transaction:

1. `rememberSaveable` calls `claimRestored` while preparing composition.
2. A claimed value remains included in `performSave`, protecting a host save that races the
   in-flight frame.
3. After composition commit, the holder registers its provider and commits the restored claim.
4. Provider-registration failure leaves the claim saveable and retries on a later composition
   commit. Composition abort, restore failure, abandonment, or forgetting releases an uncommitted
   claim so a later owner can restore it.

Repository evidence:

- `viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/saveable/SaveableStateRegistry.kt`,
  lines 3-253;
- `viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/saveable/RememberSaveable.kt`,
  lines 76-160;
- `viewcompose-runtime/src/main/java/com/viewcompose/runtime/composition/ComposerLite.kt`;
- `viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/runtime/RememberSaveableTest.kt`,
  lines 39-163.

A custom ViewCompose host registry must implement this claim protocol; a Compose-style immediate
consume is not a compatible substitute. Provider registration and restored-claim commit are
retry-safe as one remembered activation: a failed registration does not consume the claim,
`performSave` continues to include it, and a later commit can finish ownership without recreating
the holder.

## Activity, Fragment, custom-host, and process-death behavior

Standard `ComponentActivity.setUiContent` and `Fragment.setUiContent` install an Android-backed
ViewCompose saveable-state registry. One registry is bound to each `SavedStateRegistryOwner`
identity. The bridge consumes the owner's restored Bundle on first access, registers a provider that
pulls the latest committed ViewCompose snapshot at Android save time, and removes the binding when
the owner is destroyed.

The Android codec accepts `null`, recursively saveable lists, string-keyed maps, object arrays, and
Bundle-supported `Parcelable`, `Serializable`, `IBinder`, `Size`, and `SizeF` values. An unknown
outer format is ignored; a corrupt individual entry is isolated so other entries can restore. A
custom `renderInto` session does not install lifecycle, ViewModel, saved state, environment, theme,
or frame-clock services. Its owner must provide and dispose those services explicitly.

Repository evidence:

- `viewcompose-android/src/main/java/com/viewcompose/android/AndroidHostBridge.kt`,
  lines 60-108, 131-180, and 194-224;
- `viewcompose-host-android/src/main/java/com/viewcompose/host/android/AndroidSaveableStateRegistry.kt`,
  lines 18-254;
- `viewcompose-host-android/src/main/java/com/viewcompose/host/android/RenderInto.kt`, lines 52-93;
- `viewcompose-host-android/src/test/java/com/viewcompose/host/android/AndroidSaveableStateRegistryTest.kt`;
- `app/src/androidTest/java/com/viewcompose/SaveableStateRestorationUiTest.kt`;
- `app/src/debug/java/com/viewcompose/SaveableStateTestActivity.kt`; and
- `tools/state/validate_android_activity_root_process_death.sh`.

`SaveableStateRestorationUiTest` remains the fast Activity-recreation regression path. The
Activity-root certification runner at
`tools/state/validate_android_activity_root_process_death.sh` seeds automatic-key
`rememberSaveable` state, backgrounds the retained task, terminates only the application process,
restores the task under a new PID, and verifies the restored value. The navigation certification
runner at `tools/navigation/validate_android_process_death.sh` similarly backgrounds the task,
terminates only the application process without force-stopping the package, restores the existing
task, requires a new PID, and compares the full restored navigation and state report. See the
[navigation restoration guide](../guides/navigation.md#restore-state-and-connect-platform-back)
for its exact scope.

The current evidence supports standard Android host restoration through both ordinary Activity
roots and navigation hosts. Custom `renderInto` restoration remains **Partially supported** because
the caller must install and own SavedState services explicitly. Like Compose, ViewCompose does not
promise saved-instance-state restoration after a user force-stop or explicit removal from recents.

## Capability matrix

| Concept | Status | Migration boundary | Primary repository evidence |
| --- | --- | --- | --- |
| `mutableStateOf`, mutation policies, and read observation | **Supported** | Callback counts and threads are ViewCompose contracts | `State.kt`; `SnapshotMutationPolicy.kt`; `RuntimeObservationTest.kt` |
| Lazy dependency-derived state | **Partially supported** | No result policy; equal derived results are not suppressed | `DerivedStateImpl.kt`; `DerivedStateTest.kt` |
| Read and mutable snapshot transactions | **Partially supported** | Different nesting/thread rules and nullable merge limitation | `Snapshot.kt`; `SnapshotRuntime.kt`; `SnapshotStateTest.kt` |
| Snapshot collections and `snapshotFlow` | **Partially supported** | `snapshotFlow` is supported; snapshot collection types are not | `SnapshotFlow.kt`; `SnapshotFlowTest.kt` |
| Compiler-generated restart/skipping/stability | **Intentionally different** | Explicit `runGroup` and observed reads replace Compose compiler groups | `ComposerLite.kt`; `ComposerDiagnosticsTest.kt` |
| Fine-grained invalidation and clean-sibling reuse | **Partially supported** | Depends on explicit group boundaries; no stability inference | `ComposerLiteTest.kt`; `SubtreeRecompositionTest.kt` |
| Positional `remember` | **Supported** | Structural keys and transactional commit/abort | `Remember.kt`; `ComposerLiteTest.kt` |
| `key` identity across ordinary sibling reorder | **Supported** | Explicit keys move complete scopes; duplicate effective identities fail | `Key.kt`; `ComposerLiteTest.kt` keyed movement, ownership, and abort tests |
| `SideEffect`, `DisposableEffect`, `LaunchedEffect`, and `produceState` | **Supported** | Execute at ViewCompose committed-frame boundaries | Effect sources; `RenderSession.kt`; effect tests |
| `rememberSaveable`, inputs, and `Saver` | **Partially supported** | Explicit-key API and registry fallback differ from current Compose | `RememberSaveable.kt`; `Saver.kt`; `RememberSaveableTest.kt` |
| Restored claim/commit/release | **Intentionally different** | Required to survive abandoned render preparation | `SaveableStateRegistry.kt`; abort and in-flight-save tests |
| Standard Android host restoration | **Supported** | Activity/Fragment hosts install the registry automatically | `AndroidHostBridge.kt`; `AndroidSaveableStateRegistry.kt` |
| Custom-host restoration | **Partially supported** | `renderInto` installs no SavedState services | `RenderInto.kt` |
| General process-death certification | **Supported** | Real process-kill runners certify ordinary Activity-root and navigation-host state; custom `renderInto` ownership remains manual | Activity-root and navigation process-death runners |
| Eager scroll and pager snapshots | **Supported** | Connector ownership is mount-scoped; horizontal offsets and page indexes remain logical in RTL | `ScrollState.kt`; `PagerState.kt`; connector and renderer lifecycle tests |

## Migration checklist and known risks

Before replacing a Compose stateful subtree:

1. Record the source state owner and required lifetime: composition, host recreation, navigation
   entry, ViewModel, or durable storage.
2. Identify each Compose compiler restart boundary and choose the ViewCompose component or node
   group that will own the corresponding state reads.
3. Remove assumptions based on Compose stability inference, strong skipping, or automatic lambda
   memoization.
4. Review every `derivedStateOf`; add an explicit result comparison when equal-result suppression
   matters.
5. Replace snapshot collections with immutable values in `MutableState`; use `snapshotFlow` only
   for side-effect-free state calculations whose collection lifetime is explicitly owned.
6. Keep unkeyed `remember` call order stable. Use unique stable `key` values when ordinary siblings
   can be inserted, removed, or reordered.
7. Retain `ScrollState` or `PagerState` at the desired composition position. Do not retain a native
   connector, and do not use pager callbacks during declarative binding as business events.
8. Move all external work into committed effects. Treat an effect failure as a committed-frame
   failure that cannot restore the previous native tree.
9. Prefer automatic `rememberSaveable` keys, keep Saver output small and Bundle-compatible, and
   verify input-driven reset behavior.
10. Preserve claim/commit/release when implementing a custom registry.
11. Use Activity/Fragment hosts when possible. If using `renderInto`, install lifecycle,
    ViewModel, saveable-state, environment, and frame-clock services explicitly.
12. Test configuration recreation and system-style process death separately; Activity recreation
    alone is insufficient evidence.

Known risks requiring new executable evidence before stronger documentation claims:

- equal-result and nested `derivedStateOf` invalidation behavior;
- mutable snapshot creation under a read-only snapshot;
- direct semantic comparison tests against the official Compose `1.12.0` baseline rather than the
  repository's older `1.7.8` fixture.

## Verification baseline and re-verification owner

This page was verified by reading current implementation, public source contracts, existing tests,
and compiled sample sources. The review did not execute tests while producing the initial semantic
matrix; paths above identify existing evidence, not a claim that every test ran in this review.

Re-verify this page when any of the following changes:

- a listed ViewCompose module version or source revision;
- Compose Runtime/UI/Foundation, Activity, Lifecycle, or SavedState stable baseline;
- `State`, `Snapshot`, `ComposerLite`, group matching, remember, effect, `Saver`, registry, or host
  public behavior;
- renderer prepare/commit/rollback ordering;
- Android process-death certification coverage.

The runtime maintainer owns state, snapshot, observation, remember, and recomposition conclusions.
The UI Foundation maintainer owns effects, Saver, and claim transaction conclusions. The Android
aggregate and Engine maintainers jointly own Activity, Fragment, Bundle, and process-recreation conclusions. A comparison update
is complete only when those owners agree on the capability label and the cited source/test evidence
still protects the documented claim.
