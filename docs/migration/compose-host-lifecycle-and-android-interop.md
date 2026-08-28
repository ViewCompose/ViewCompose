---
schema_version: 2
document_id: migration.compose-host-lifecycle-android-interop
doc_type: migration
owner:
  kind: capability
  id: host.android-container
version_lane: released
capability_ids:
  - host.android-container
  - host.android-view
  - lifecycle.owner-boundaries
  - lifecycle.flow-collection
  - lifecycle.android-view
  - viewmodel.owner-boundaries
  - viewmodel.store-resolution
  - viewmodel.saved-state
artifact_ids:
  - viewcompose-android
  - viewcompose-host-android
  - viewcompose-lifecycle-androidx
  - viewcompose-viewmodel-androidx
  - viewcompose-renderer-android
sample_ids:
  - migration.compose-host
  - migration.viewcompose-host
source_state: Jetpack Compose UI and Runtime 1.12.0 host, lifecycle, state-owner, and Android View interop semantics.
target_state: ViewCompose Android 0.1.0-alpha02 and Host Android 0.1.0-alpha05 ownership and interop contracts.
---

# Migrating Compose Hosts, Lifecycle, and Android Interop to ViewCompose

This page maps Android host, lifecycle, state-owner, and Android View interop behavior from Jetpack
Compose to ViewCompose. It is an engineering comparison, not a claim that similarly named APIs
have identical semantics.

- **Source state:** Jetpack Compose UI and Runtime 1.12.0, Activity 1.13.0, Lifecycle 2.11.0,
  and SavedState 1.5.0.
- **Target state:** `viewcompose-android`, `viewcompose-lifecycle-androidx`,
  `viewcompose-viewmodel-androidx`, and `viewcompose-renderer-android` 0.1.0-alpha02, plus the
  low-level `viewcompose-host-android` 0.1.0-alpha05 engine.
- **Last verified:** 2026-08-28.
- **Re-verification owner:** maintainers of `viewcompose-android`, `viewcompose-host-android`,
  `viewcompose-lifecycle-androidx`, `viewcompose-viewmodel-androidx`, and
  `viewcompose-renderer-android`.

## Verification model

The upstream side of this page is a semantic review of the stable AndroidX documentation and
release notes:

- [Compose in Views](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/compose-in-views)
- [Views in Compose](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/views-in-compose)
- [`ComponentActivity.setContent`](https://developer.android.com/reference/kotlin/androidx/activity/compose/package-summary)
- [Composition lifecycle](https://developer.android.com/develop/ui/compose/lifecycle)
- [`Composition`](https://developer.android.com/reference/kotlin/androidx/compose/runtime/Composition)
- [Lifecycle 2.11 release notes](https://developer.android.com/jetpack/androidx/releases/lifecycle)
- [SavedState release notes](https://developer.android.com/jetpack/androidx/releases/savedstate)

The local executable baseline is Compose 1.7.8, Activity 1.12.4, Lifecycle 2.11.0, and Kotlin
2.2.10. Repository tests and compiled samples cited below verify ViewCompose behavior against that
dependency set. They do not constitute execution of upstream Compose 1.12.0 or Activity 1.13.0;
the Lifecycle family now matches the reviewed 2.11.0 baseline. Re-verification must repeat both the
official semantic review and the local test run when either baseline changes.

The ViewCompose contracts in scope are owned by the
[Android aggregate](../modules/viewcompose-android/README.md),
[Android host engine](../modules/viewcompose-host-android/README.md),
[lifecycle](../modules/viewcompose-lifecycle-androidx/README.md),
[ViewModel](../modules/viewcompose-viewmodel-androidx/README.md), and
[renderer](../modules/viewcompose-renderer-android/README.md) modules.

## Compiled side-by-side starting point

This pair shows the narrow Activity-root and native-View path before lifecycle and cleanup policy
are added. Both snippets are extracted from `:samples:compose-migration`; `qaQuick` compiles their
source files and rejects documentation drift.

Compose source:

{/* paired-sample source="samples/compose-migration/src/main/java/com/viewcompose/samples/migration/host/ComposeHostSample.kt" region="compose-host" */}
```kotlin
fun ComponentActivity.installComposeInteropSample() {
    setContent {
        ComposeInteropSample()
    }
}

@Composable
private fun ComposeInteropSample() {
    AndroidView(
        factory = { context -> TextView(context) },
        update = { view -> view.text = "Native TextView" },
    )
}
```
{/* paired-sample-end */}

ViewCompose target:

{/* paired-sample source="samples/compose-migration/src/main/java/com/viewcompose/samples/migration/host/ViewComposeHostSample.kt" region="viewcompose-host" */}
```kotlin
fun ComponentActivity.installViewComposeInteropSample() {
    setMaterial3UiContent {
        ViewComposeInteropSample()
    }
}

private fun UiTreeBuilder.ViewComposeInteropSample() {
    AndroidView(
        factory = { context -> TextView(context) },
        update = { view ->
            (view as TextView).text = "Native TextView"
        },
    )
}
```
{/* paired-sample-end */}

The example proves only the callback escape hatch's public installation, factory, and replay-safe
update path. Reusable integrations should use `AndroidViewAdapter<V, S>` so View type, state,
construction identity, reuse policy, and cleanup remain one compiled contract. Neither form
inherits Compose disposal or reuse semantics implicitly.

## Capability matrix

Status values are limited to **Supported**, **Partially supported**, **Intentionally different**,
and **Unsupported**.

| Concept | Compose / AndroidX behavior | ViewCompose behavior | Status | Local evidence and verification note |
| --- | --- | --- | --- | --- |
| Activity root host | `ComponentActivity.setContent` installs Compose content into the Activity and owns the Composition through the host. | Neutral `ComponentActivity.setUiContent` and named Material `setMaterial3UiContent` replace the Activity content view, render the first frame synchronously, return the new root `ViewGroup`, and keep the `RenderSession` in an internal registry until replacement or Activity destruction. | Partially supported | [`AndroidHostBridge.kt`](../../viewcompose-android/src/main/java/com/viewcompose/android/AndroidHostBridge.kt), [`Material3AndroidHostBridge.kt`](../../viewcompose-material3-android/src/main/java/com/viewcompose/material3/android/Material3AndroidHostBridge.kt), and their compiled samples. The synchronous first frame and internally owned session are ViewCompose-specific. |
| Fragment host | A Fragment-hosted `ComposeView` is normally disposed with the Fragment view tree through `DisposeOnViewTreeLifecycleDestroyed`. | Neutral `Fragment.setUiContent` and named Material `setMaterial3UiContent` return a root for `onCreateView`, start its session when that root's `viewLifecycleOwner` is published, provide that owner to content, and dispose at `onDestroyView`. | Supported | [`AndroidHostBridge.kt`](../../viewcompose-android/src/main/java/com/viewcompose/android/AndroidHostBridge.kt) and [`FragmentHostLifecycleIntegrationTest.kt`](../../viewcompose-android/src/test/java/com/viewcompose/android/FragmentHostLifecycleIntegrationTest.kt) verify owner identity, View recreation, cleanup, and independently retained Fragment-scoped ViewModel/saveable ownership. |
| Existing View hierarchy | `ComposeView` supplies composition disposal strategies and discovers ViewTree owners. | `renderInto` renders into a supplied `ViewGroup`; it supplies no lifecycle, ViewModel, saved-state, environment, theme, or frame-clock owner and requires explicit session disposal. | Partially supported | [`RenderInto.kt`](../../viewcompose-host-android/src/main/java/com/viewcompose/host/android/RenderInto.kt) and the compiled `renderIntoSample` in [`HostAndroidSamples.kt`](../../viewcompose-host-android/src/test/samples/com/viewcompose/host/android/samples/HostAndroidSamples.kt). |
| Lifecycle owner propagation | Compose host integrations resolve AndroidX owners from the Activity, Fragment view, or ViewTree. | Activity content receives the Activity owner, and Fragment content receives its current View owner. Custom `renderInto` containers receive no automatic owner. | Partially supported | [`AndroidHostBridge.kt`](../../viewcompose-android/src/main/java/com/viewcompose/android/AndroidHostBridge.kt), [`FragmentHostLifecycleIntegrationTest.kt`](../../viewcompose-android/src/test/java/com/viewcompose/android/FragmentHostLifecycleIntegrationTest.kt), and [`LifecycleHostGuards.kt`](../../viewcompose-android/src/main/java/com/viewcompose/android/LifecycleHostGuards.kt). The remaining difference is deliberate low-level custom-host ownership. |
| ViewModel owner propagation | Lifecycle 2.11 can create arbitrary child UI scopes with `ViewModelStoreProvider` and can inherit parent factories and `CreationExtras`. | Activity, Fragment, navigation-entry, and navigation-graph scopes exist. Navigation owners inherit the nearest host's default Factory and starting `CreationExtras`, then replace their child ownership/default arguments; no equivalent public provider exists for arbitrary ViewCompose UI subtrees. | Partially supported | [`NavEntryOwner.kt`](../../viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavEntryOwner.kt), [`NavGraphOwner.kt`](../../viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavGraphOwner.kt), Factory/extras coverage in [`NavEntryOwnerTest.kt`](../../viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavEntryOwnerTest.kt) and [`NavHostPublicApiTest.kt`](../../viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostPublicApiTest.kt), and Lifecycle 2.11 official semantic evidence. |
| Saved state | Compose host integrations combine `SavedStateRegistryOwner`, `SavedStateHandle`, and saveable-state facilities. | ViewCompose hosts install a ViewCompose `SaveableStateRegistry`; applicable Activity, Fragment, and navigation owners also participate in AndroidX saved state. These are related layers, not one interchangeable owner API. | Partially supported | [`AndroidHostBridge.kt`](../../viewcompose-android/src/main/java/com/viewcompose/android/AndroidHostBridge.kt), [`NavEntryOwner.kt`](../../viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavEntryOwner.kt), and saved-state coverage in [`NavHostPublicApiTest.kt`](../../viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostPublicApiTest.kt). |
| Frame scheduling and explicit rendering | Compose recomposition is coordinated by its Recomposer and frame clock. | An explicit `render` is synchronous. State invalidations are coalesced to an Android frame, and an inactive session retains invalidation until reactivated. | Intentionally different | [`AndroidFrameAlignedRenderSessionRuntime.kt`](../../viewcompose-host-android/src/main/java/com/viewcompose/host/android/runtime/AndroidFrameAlignedRenderSessionRuntime.kt) and [`AndroidFrameAlignedRenderSessionRuntimeTest.kt`](../../viewcompose-host-android/src/test/java/com/viewcompose/host/android/runtime/AndroidFrameAlignedRenderSessionRuntimeTest.kt). |
| Effect ownership and terminal disposal | Effects leave with their Composition scope; disposing a `Composition` is terminal. | A `RenderSession` owns one composition coroutine scope, render state, overlays, native views, and cleanup. Disposal is idempotent; later public render or activation work fails fast, while already queued internal callbacks safely no-op. | Supported | [`RenderSession.kt`](../../viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/session/RenderSession.kt), [`RenderSessionFailureTest.kt`](../../viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/runtime/RenderSessionFailureTest.kt), and [`AndroidFrameAlignedRenderSessionRuntimeTest.kt`](../../viewcompose-host-android/src/test/java/com/viewcompose/host/android/runtime/AndroidFrameAlignedRenderSessionRuntimeTest.kt). |
| Android View factory and update | `AndroidView` creates the View once for an instance and runs `update` on applicable recompositions. | The typed `AndroidViewAdapter<V, S>` and callback escape hatch create a View for one construction identity and apply complete replay-safe state inside a transactional native-tree patch. A changed adapter class or `constructionKey` creates a detached candidate; failure preserves the committed View. | Supported | [`AndroidViewAdapter.kt`](../../viewcompose-host-android/src/main/java/com/viewcompose/host/android/AndroidViewAdapter.kt), [`ViewTreePatchPipeline.kt`](../../viewcompose-renderer-android/src/main/java/com/viewcompose/renderer/view/tree/pipeline/ViewTreePatchPipeline.kt), and [`AndroidInteropRenderingUiTest.kt`](../../app/src/androidTest/java/com/viewcompose/AndroidInteropRenderingUiTest.kt). |
| Android View reset, commit, and release | Compose uses non-null `onReset` to opt into reusable content and `onRelease` when content permanently leaves composition. It has no equivalent transaction-commit callback. | `onReset(..., MountedTreeReuse)` runs only for opted-in cross-logical-key mounted-tree reuse, never ordinary update or rollback. `onCommit` runs only after the complete composition transaction succeeds; `onRelease` is one-shot cleanup for permanent abandonment, including failed candidates and displaced construction identities. | Intentionally different | [`AndroidViewNodeProps.kt`](../../viewcompose-ui-contract/src/main/kotlin/com/viewcompose/ui/node/spec/container/AndroidViewNodeProps.kt), [`ViewTreeDisposer.kt`](../../viewcompose-renderer-android/src/main/java/com/viewcompose/renderer/view/tree/pipeline/ViewTreeDisposer.kt), and [`ViewTreeRenderTransactionTest.kt`](../../viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/view/tree/ViewTreeRenderTransactionTest.kt). |
| ViewBinding and Fragment-in-tree interop | Compose supplies `AndroidViewBinding` and `AndroidFragment` integrations. | XML can be inflated manually inside an Android View factory, but there is no direct ViewBinding integration or supported Fragment-in-render-tree counterpart. | Unsupported | No corresponding public API or compiled sample was found in the reviewed modules. |

## Choosing a host entry point

Use neutral `setUiContent` or named Material `setMaterial3UiContent` for an Activity or Fragment
that gives ViewCompose ownership of the host's root content. Use `renderInto` only when an existing
Android View hierarchy must remain the owner of the container. The latter is a lower-level bridge,
not a ViewCompose spelling of `ComposeView`:

| Source pattern | Target pattern | Ownership change |
| --- | --- | --- |
| `ComponentActivity.setContent` | Neutral `ComponentActivity.setUiContent` or Material `setMaterial3UiContent` | ViewCompose owns the internal session; the return value is the installed root `ViewGroup`, not a session handle. |
| Fragment `ComposeView` | Return neutral `Fragment.setUiContent()` or Material `setMaterial3UiContent()` from `onCreateView` | ViewCompose starts after the View owner is published, provides that owner to content, and disposes the internal session at `onDestroyView`. |
| Embedded `ComposeView` | `renderInto(existingViewGroup)` | The caller becomes responsible for owner provision and disposal. |

All host entry points must be called for an active host, and rendering is main-thread Android work.
Activity `setUiContent` and low-level `renderInto` commit their first frame before returning.
Fragment `setUiContent` returns the root from `onCreateView`, then commits its first frame when
Android publishes that root's View lifecycle owner.

## Activity hosting

`ComponentActivity.setUiContent` installs a neutral ViewCompose root. The named
`setMaterial3UiContent` resolves a Material context and token snapshot before delegating to the
same host lifecycle. Both supply the Activity lifecycle and ViewModel owners, the host
saveable-state registry, animation context, frame clock, and environment. Calling either again
replaces and disposes the previously registered Activity session.

The returned value is the installed root `ViewGroup`, not the internal `RenderSession`. Public
Activity hosting therefore does not expose manual renders, rendering-active control, or early
session disposal. Replacing the content or destroying the Activity disposes the registered session.

## Fragment hosting

Neutral `Fragment.setUiContent` and named Material `setMaterial3UiContent` create and return the
Fragment root `ViewGroup`; call the selected entry from `onCreateView` and return that root. The
internal session registry binds disposal when the current `viewLifecycleOwner` becomes available.
The same owner is installed into content. A recreated Fragment view gets a new owner and session;
the old session is disposed exactly once at `onDestroyView`. ViewModel and saveable-state ownership
remain Fragment-scoped and therefore survive that View-only recreation.

## Rendering into an existing View hierarchy

`renderInto` performs a synchronous first render into the supplied `ViewGroup`. It deliberately
does not discover or install lifecycle, ViewModel, saved-state, environment, theme, or frame-clock
owners. Migration code that previously relied on `ComposeView` owner discovery must provide the
required ViewCompose locals around the content and bind disposal to the owning Android lifecycle.

The caller must dispose the returned session before permanently abandoning the container. It must
also avoid retaining the session beyond the lifetime of the Android Views that it owns.

After `renderInto` disposal, another caller-initiated `render` or `setRenderingActive` call throws
`IllegalStateException`. Disposal itself remains idempotent. An invalidation or Android frame
callback already queued inside the session is cancelled or ignored and cannot publish another
frame.

## Lifecycle, ViewModel, and saved-state owners

Owner migration is a semantic task, not a type-name substitution:

- an Activity host receives Activity-scoped owners;
- a Fragment host uses the current View lifecycle for content and session disposal, while retaining
  Fragment-scoped ViewModel and saved-state ownership;
- navigation entries and graphs own separate lifecycle, ViewModel, and saved-state scopes; and
- `renderInto` supplies none of these scopes automatically.

Lifecycle 2.11 adds general scoped ViewModels for arbitrary Compose UI regions. A
`ViewModelStoreProvider` can keep child stores across configuration changes, clear them when their
UI scope permanently leaves, and inherit the parent's factory and `CreationExtras`. ViewCompose
0.1.0-alpha05 has comparable permanent-removal behavior for navigation entry and graph owners. Its
navigation tests prove that those owners inherit the nearest host's default Factory and starting
`CreationExtras`, replace their child owner/default-argument entries, and preserve unrelated extras.
It still does not expose an equivalent general provider for arbitrary UI subtrees.

ViewModel lookup now matches AndroidX key and creation semantics. Only `null` selects the
class-derived default key; empty and whitespace-only strings are explicit keys. Migration code that
used a blank string as a default sentinel must pass `null`. Reified and runtime-`KClass`
initializer overloads receive the owner's `CreationExtras`, allowing constructor dependencies and
`createSavedStateHandle()` without a one-class Factory. The owner's `ViewModelStore` is the only
instance cache, so clearing it is visible on the next executed composition call.

ViewCompose `SaveableStateRegistry`, AndroidX `SavedStateRegistryOwner`, and `SavedStateHandle`
serve different layers. A migration should identify which layer owns every value and verify
process recreation separately from in-memory configuration change.

## Session, frame, effect, and disposal semantics

A `RenderSession` owns more than a Composition-like content function. It owns the composition
coroutine scope, mounted native tree, overlay state, frame scheduling, and cleanup. A successful
explicit render commits synchronously. State-driven invalidations are frame-aligned and coalesced.
Disabling rendering pauses delivery of those frames without discarding the pending invalidation.

Disposal is terminal and idempotent. It first cancels composition-scoped work, then releases the
native tree and overlays. Navigation is a special retention case: a hidden destination session can
remain alive while frame-driven rendering is inactive. Its composition-scoped effects are not
cancelled solely because the destination is hidden. See
[Migrating Compose navigation](compose-navigation.md#hidden-destination-retention) for the
lifecycle-aware migration rule.

## Android View interop callback mapping

ViewCompose Android View callbacks participate in the renderer's native-tree transaction:

For a reusable integration, implement `AndroidViewAdapter<V, S>` and pass a complete state
snapshot. The VNode `key` is logical content identity. The adapter implementation class plus
`constructionKey` is physical constructor identity: changing it replaces the View atomically
without pretending that the logical item changed. Adapter scopes expose the VNode's immutable
environment; their constructors, renderer/session internals, and mutable transaction are not
public.

| Callback | Required migration interpretation |
| --- | --- |
| `create` / `factory` | Creates only a new construction identity. Do not read changing state that belongs in `update`. |
| `update` | Must be replay-safe. A failed frame can restore the previously committed tree. |
| `onReset` | Must be replay-safe. It runs only when a `Resettable` node crosses logical keys; ordinary update and rollback never invoke it. |
| `onCommit` | Runs only after the complete native-tree transaction succeeds. Put irreversible work that requires a committed tree here. |
| `onRelease` | Performs one-shot cleanup whenever a created node is permanently abandoned, including replacement, removal, session disposal, and rollback of an uncommitted candidate. |

## Unsupported direct interop

ViewCompose 0.1.0-alpha05 has no direct equivalent of Compose `AndroidViewBinding` or
`AndroidFragment`. A factory can inflate an XML layout, but ViewBinding lifecycle management and
Fragment ownership remain application responsibilities. Do not place a Fragment directly inside a
ViewCompose render tree or infer support from the ability to host its root View.

## Migration risks

- Fragment content begins after `setUiContent` returns, when Android publishes the View owner; code
  must not require content-side work to finish inside `onCreateView` itself.
- A hidden navigation destination retains its composition scope and effects while frame rendering
  is inactive.
- Lifecycle 2.11 arbitrary scoped ViewModels do not have ViewCompose parity evidence; navigation
  entry/graph parent Factory and `CreationExtras` inheritance does.
- `renderInto` has no automatic ViewTree-owner discovery or composition-disposal strategy.
- Direct ViewBinding and Fragment-in-tree interoperability are unsupported.

## Migration checklist

1. Choose Activity, Fragment, or existing-container hosting before translating content.
2. Record the lifecycle, ViewModel, saved-state, theme, and frame owner for the target root.
3. For `renderInto`, install every required owner explicitly and bind session disposal.
4. Move replay-safe View binding into `update` or `onReset`; move irreversible committed-tree work
   into `onCommit`.
5. Make `onRelease` safe for rollback candidates as well as committed removals.
6. Treat session disposal as terminal; clear caller references instead of catching fail-fast misuse.
7. Test Fragment view recreation independently from Fragment destruction.
8. Test configuration change, permanent removal, and process recreation as distinct state events.
9. Keep lifecycle-aware work lifecycle-aware when a navigation destination is retained but hidden.
10. Record any dependency on an unsupported Compose interop API before removing the Compose host.

## Re-verification requirements

Re-verify this page when any of the following changes:

- a host entry point, owner local, session disposal rule, or Android View callback contract;
- Compose UI/Runtime, Activity, Lifecycle, or SavedState stable baselines;
- the repository's executable Compose/AndroidX comparison baseline; or
- any retained verification gap listed above.

The minimum evidence is the owning module contract, the cited JVM tests, Android interop
instrumentation, compiled host samples, and a fresh review of the linked official AndroidX
documentation. Fragment view recreation and renderer transaction behavior require behavioral
tests; API signatures alone are insufficient.
