# Migrating Compose Hosts, Lifecycle, and Android Interop to ViewCompose

This page maps Android host, lifecycle, state-owner, and Android View interop behavior from Jetpack
Compose to ViewCompose. It is an engineering comparison, not a claim that similarly named APIs
have identical semantics.

- **Source state:** Jetpack Compose UI and Runtime 1.11.4, Activity 1.13.0, Lifecycle 2.11.0,
  and SavedState 1.5.0.
- **Target state:** ViewCompose 0.1.0-alpha01.
- **Last verified:** 2026-08-03.
- **Re-verification owner:** maintainers of `viewcompose-host-android`, `viewcompose-lifecycle`,
  `viewcompose-viewmodel`, and `viewcompose-renderer`.

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

The local executable baseline is Compose 1.7.8, Activity 1.12.4, Lifecycle 2.8.7, and Kotlin
2.0.21. Repository tests and compiled samples cited below verify ViewCompose behavior against that
dependency set. They do not constitute execution of upstream Compose 1.11.4, Activity 1.13.0, or
Lifecycle 2.11.0. Re-verification must therefore repeat both the official semantic review and the
local test run when either baseline changes.

The ViewCompose contracts in scope are owned by the
[Android host](../modules/viewcompose-host-android/README.md),
[lifecycle](../modules/viewcompose-lifecycle/README.md),
[ViewModel](../modules/viewcompose-viewmodel/README.md), and
[renderer](../modules/viewcompose-renderer/README.md) modules.

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
    setUiContent {
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

The example proves only the public installation, factory, and replay-safe update path. The target
does not inherit Compose disposal or reuse semantics; choose owners and add `onReset`, `onCommit`,
and `onRelease` behavior from the contracts below when the embedded View requires them.

## Capability matrix

Status values are limited to **Supported**, **Partially supported**, **Intentionally different**,
and **Unsupported**.

| Concept | Compose / AndroidX behavior | ViewCompose behavior | Status | Local evidence and verification note |
| --- | --- | --- | --- | --- |
| Activity root host | `ComponentActivity.setContent` installs Compose content into the Activity and owns the Composition through the host. | `ComponentActivity.setUiContent` replaces the Activity content view, renders the first frame synchronously, returns the new root `ViewGroup`, and keeps the `RenderSession` in an internal registry until replacement or Activity destruction. | Partially supported | [`AndroidHostBridge.kt`](../../viewcompose-host-android/src/main/java/com/viewcompose/host/android/AndroidHostBridge.kt), Activity path and session registry; compiled [`HostAndroidSamples.kt`](../../viewcompose-host-android/src/test/samples/com/viewcompose/host/android/samples/HostAndroidSamples.kt). The synchronous first frame and internally owned session are ViewCompose-specific. |
| Fragment host | A Fragment-hosted `ComposeView` is normally disposed with the Fragment view tree through `DisposeOnViewTreeLifecycleDestroyed`. | `Fragment.setUiContent` creates and returns a root `ViewGroup` for `onCreateView`. Its internal session is disposed with the current `viewLifecycleOwner`, but the lifecycle owner installed into ViewCompose content is currently the Fragment instance. | Partially supported | [`AndroidHostBridge.kt`](../../viewcompose-host-android/src/main/java/com/viewcompose/host/android/AndroidHostBridge.kt), Fragment path and registry; [`LifecycleBoundDisposerTest.kt`](../../viewcompose-host-android/src/test/java/com/viewcompose/host/android/LifecycleBoundDisposerTest.kt). The installed-owner mismatch is a known verification gap. |
| Existing View hierarchy | `ComposeView` supplies composition disposal strategies and discovers ViewTree owners. | `renderInto` renders into a supplied `ViewGroup`; it supplies no lifecycle, ViewModel, saved-state, environment, theme, or frame-clock owner and requires explicit session disposal. | Partially supported | [`RenderInto.kt`](../../viewcompose-host-android/src/main/java/com/viewcompose/host/android/RenderInto.kt) and the compiled `renderIntoSample` in [`HostAndroidSamples.kt`](../../viewcompose-host-android/src/test/samples/com/viewcompose/host/android/samples/HostAndroidSamples.kt). |
| Lifecycle owner propagation | Compose host integrations resolve AndroidX owners from the Activity, Fragment view, or ViewTree. | Activity content receives the Activity owner. Fragment content currently receives the Fragment owner while session disposal follows the Fragment view lifecycle. Custom containers receive no automatic owner. | Partially supported | [`AndroidHostBridge.kt`](../../viewcompose-host-android/src/main/java/com/viewcompose/host/android/AndroidHostBridge.kt), [`LifecycleBoundDisposer.kt`](../../viewcompose-host-android/src/main/java/com/viewcompose/host/android/LifecycleBoundDisposer.kt), and [`LifecycleHostGuards.kt`](../../viewcompose-host-android/src/main/java/com/viewcompose/host/android/LifecycleHostGuards.kt). |
| ViewModel owner propagation | Lifecycle 2.11 can create arbitrary child UI scopes with `ViewModelStoreProvider` and can inherit parent factories and `CreationExtras`. | Activity, Fragment, navigation-entry, and navigation-graph scopes exist. There is no equivalent public provider for arbitrary ViewCompose UI subtrees, and navigation owners do not yet have evidence of inheriting every custom parent factory and `CreationExtras`. | Partially supported | [`NavEntryOwner.kt`](../../viewcompose-navigation/src/main/java/com/viewcompose/navigation/NavEntryOwner.kt), [`NavGraphOwner.kt`](../../viewcompose-navigation/src/main/java/com/viewcompose/navigation/NavGraphOwner.kt), and [`NavEntryOwnerTest.kt`](../../viewcompose-navigation/src/test/java/com/viewcompose/navigation/NavEntryOwnerTest.kt). Lifecycle 2.11 behavior is official semantic evidence only. |
| Saved state | Compose host integrations combine `SavedStateRegistryOwner`, `SavedStateHandle`, and saveable-state facilities. | ViewCompose hosts install a ViewCompose `SaveableStateRegistry`; applicable Activity, Fragment, and navigation owners also participate in AndroidX saved state. These are related layers, not one interchangeable owner API. | Partially supported | [`AndroidHostBridge.kt`](../../viewcompose-host-android/src/main/java/com/viewcompose/host/android/AndroidHostBridge.kt), [`NavEntryOwner.kt`](../../viewcompose-navigation/src/main/java/com/viewcompose/navigation/NavEntryOwner.kt), and saved-state coverage in [`NavHostPublicApiTest.kt`](../../viewcompose-navigation/src/test/java/com/viewcompose/navigation/NavHostPublicApiTest.kt). |
| Frame scheduling and explicit rendering | Compose recomposition is coordinated by its Recomposer and frame clock. | An explicit `render` is synchronous. State invalidations are coalesced to an Android frame, and an inactive session retains invalidation until reactivated. | Intentionally different | [`AndroidFrameAlignedRenderSessionRuntime.kt`](../../viewcompose-host-android/src/main/java/com/viewcompose/host/android/runtime/AndroidFrameAlignedRenderSessionRuntime.kt) and [`AndroidFrameAlignedRenderSessionRuntimeTest.kt`](../../viewcompose-host-android/src/test/java/com/viewcompose/host/android/runtime/AndroidFrameAlignedRenderSessionRuntimeTest.kt). |
| Effect ownership and terminal disposal | Effects leave with their Composition scope; disposing a `Composition` is terminal. | A `RenderSession` owns one composition coroutine scope, render state, overlays, native views, and cleanup. Disposal cancels the composition scope before clearing the mounted tree and is idempotent. Some operations after disposal currently no-op rather than fail. | Partially supported | [`RenderSession.kt`](../../viewcompose-widget-core/src/main/java/com/viewcompose/widget/core/runtime/session/RenderSession.kt), runtime tests, and [`RenderSessionFailureTest.kt`](../../viewcompose-widget-core/src/test/java/com/viewcompose/widget/core/runtime/RenderSessionFailureTest.kt). The post-disposal contract is a known verification gap. |
| Android View factory and update | `AndroidView` creates the View once for an instance and runs `update` on applicable recompositions. | `AndroidView` uses a factory for new nodes and replay-safe update binding inside a transactional native-tree patch. Failed candidate insertion is rolled back. | Supported | [`AndroidInteropDsl.kt`](../../viewcompose-host-android/src/main/java/com/viewcompose/host/android/AndroidInteropDsl.kt), [`ViewTreePatchPipeline.kt`](../../viewcompose-renderer/src/main/java/com/viewcompose/renderer/view/tree/pipeline/ViewTreePatchPipeline.kt), and [`AndroidInteropRenderingUiTest.kt`](../../app/src/androidTest/java/com/viewcompose/AndroidInteropRenderingUiTest.kt). |
| Android View reset, commit, and release | Compose uses non-null `onReset` to opt into reusable content and `onRelease` when content permanently leaves composition. It has no equivalent transaction-commit callback. | `onReset` may run when a same-key, same-type node receives changed props; `onCommit` runs only after the complete native-tree transaction succeeds; `onRelease` is one-shot cleanup for a created node that is permanently abandoned, including rollback candidates. | Intentionally different | [`AndroidViewNodeProps.kt`](../../viewcompose-ui-contract/src/main/kotlin/com/viewcompose/ui/node/spec/container/AndroidViewNodeProps.kt), [`ViewTreeDisposer.kt`](../../viewcompose-renderer/src/main/java/com/viewcompose/renderer/view/tree/pipeline/ViewTreeDisposer.kt), and [`ViewTreeRenderTransactionTest.kt`](../../viewcompose-renderer/src/test/java/com/viewcompose/renderer/view/tree/ViewTreeRenderTransactionTest.kt). Existing public wording that limits release to committed removal or session disposal is a known verification gap. |
| ViewBinding and Fragment-in-tree interop | Compose supplies `AndroidViewBinding` and `AndroidFragment` integrations. | XML can be inflated manually inside an Android View factory, but there is no direct ViewBinding integration or supported Fragment-in-render-tree counterpart. | Unsupported | No corresponding public API or compiled sample was found in the reviewed modules. |

## Choosing a host entry point

Use `setUiContent` for an Activity or Fragment that gives ViewCompose ownership of the host's root
content. Use `renderInto` only when an existing Android View hierarchy must remain the owner of the
container. The latter is a lower-level bridge, not a ViewCompose spelling of `ComposeView`:

| Source pattern | Target pattern | Ownership change |
| --- | --- | --- |
| `ComponentActivity.setContent` | `ComponentActivity.setUiContent` | ViewCompose owns the internal session; the return value is the installed root `ViewGroup`, not a session handle. |
| Fragment `ComposeView` | Return `Fragment.setUiContent()` from `onCreateView` | ViewCompose owns the internal session and follows the Fragment view lifecycle, but see the Fragment-owner verification gap below. |
| Embedded `ComposeView` | `renderInto(existingViewGroup)` | The caller becomes responsible for owner provision and disposal. |

All host entry points must be called for an active host. Rendering is main-thread Android work, and
the first ViewCompose frame is committed before the entry-point call returns.

## Activity hosting

`ComponentActivity.setUiContent` installs ViewCompose content as the Activity root and supplies the
Activity lifecycle and ViewModel owners, the host saveable-state registry, animation context,
frame clock, environment, and theme locals. Calling it again replaces and disposes the previously
registered Activity session.

The returned value is the installed root `ViewGroup`, not the internal `RenderSession`. Public
Activity hosting therefore does not expose manual renders, rendering-active control, or early
session disposal. Replacing the content or destroying the Activity disposes the registered session.

## Fragment hosting

`Fragment.setUiContent` creates and returns the Fragment root `ViewGroup`; call it from
`onCreateView` and return that root. The internal session registry binds disposal when the current
`viewLifecycleOwner` becomes available. A recreated Fragment view gets a new session, and the old
view session is disposed at `onDestroyView`.

### Known verification gap: Fragment owner identity

The implementation currently passes the Fragment itself as the content lifecycle owner while the
session registry disposes against `viewLifecycleOwner`. This is not the same owner identity as the
recommended Fragment `ComposeView` arrangement. Until the contract and implementation agree:

- do not document the installed owner as `viewLifecycleOwner`;
- do not assume `onDestroyView` moves the installed owner to `DESTROYED`;
- scope View-bound collection and cleanup to the actual Fragment view lifecycle explicitly; and
- re-verify both owner identity and disposal when this gap is resolved.

## Rendering into an existing View hierarchy

`renderInto` performs a synchronous first render into the supplied `ViewGroup`. It deliberately
does not discover or install lifecycle, ViewModel, saved-state, environment, theme, or frame-clock
owners. Migration code that previously relied on `ComposeView` owner discovery must provide the
required ViewCompose locals around the content and bind disposal to the owning Android lifecycle.

The caller must dispose the returned session before permanently abandoning the container. It must
also avoid retaining the session beyond the lifetime of the Android Views that it owns.

### Known verification gap: operations after `renderInto` disposal

The public `renderInto` wording describes post-disposal operations as fail-fast, while the current
Android frame-aligned runtime silently ignores some render and activation calls after disposal.
Migration code must not depend on either behavior. Treat disposal as terminal and guard the session
reference in the caller until the implementation, documentation, and tests establish one contract.

## Lifecycle, ViewModel, and saved-state owners

Owner migration is a semantic task, not a type-name substitution:

- an Activity host receives Activity-scoped owners;
- a Fragment host currently combines Fragment owner propagation with Fragment-view session
  disposal;
- navigation entries and graphs own separate lifecycle, ViewModel, and saved-state scopes; and
- `renderInto` supplies none of these scopes automatically.

Lifecycle 2.11 adds general scoped ViewModels for arbitrary Compose UI regions. A
`ViewModelStoreProvider` can keep child stores across configuration changes, clear them when their
UI scope permanently leaves, and inherit the parent's factory and `CreationExtras`. ViewCompose
0.1.0-alpha01 has comparable permanent-removal behavior for navigation entry and graph owners, but
does not expose an equivalent general provider for arbitrary UI subtrees. Its navigation owner
factory behavior must also not be described as full parent-factory or `CreationExtras` propagation
without additional implementation and tests.

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

| Callback | Required migration interpretation |
| --- | --- |
| `factory` | Creates only a new native node. Do not read changing state that belongs in `update`. |
| `update` | Must be replay-safe. A failed frame can restore the previously committed tree. |
| `onReset` | Must be replay-safe. Unlike Compose lazy-content reuse, it can run for an ordinary same-key, same-type node whose props changed. |
| `onCommit` | Runs only after the complete native-tree transaction succeeds. Put irreversible work that requires a committed tree here. |
| `onRelease` | Performs one-shot cleanup whenever a created node is permanently abandoned, including successful removal, session disposal, and rollback of an uncommitted candidate. |

### Known verification gap: release wording and rollback

Renderer tests establish that rollback of a newly created candidate invokes `onRelease`, while
some current public wording limits release to committed removal or session disposal. Migration
code must implement `onRelease` as one-shot cleanup for any permanently abandoned created View.
Re-verify the KDoc, module manual, rollback test, and this page together when the wording is fixed.

## Unsupported direct interop

ViewCompose 0.1.0-alpha01 has no direct equivalent of Compose `AndroidViewBinding` or
`AndroidFragment`. A factory can inflate an XML layout, but ViewBinding lifecycle management and
Fragment ownership remain application responsibilities. Do not place a Fragment directly inside a
ViewCompose render tree or infer support from the ability to host its root View.

## Migration risks

- Fragment content currently receives the Fragment lifecycle owner while session disposal follows
  the Fragment view lifecycle.
- The `renderInto` post-disposal fail-fast wording conflicts with runtime no-op behavior.
- Android View `onRelease` can run for a rollback candidate even though some public wording only
  names committed removal and session disposal.
- A hidden navigation destination retains its composition scope and effects while frame rendering
  is inactive.
- Lifecycle 2.11 arbitrary scoped ViewModels and complete parent factory/`CreationExtras`
  inheritance do not have ViewCompose parity evidence.
- `renderInto` has no automatic ViewTree-owner discovery or composition-disposal strategy.
- Direct ViewBinding and Fragment-in-tree interoperability are unsupported.

## Migration checklist

1. Choose Activity, Fragment, or existing-container hosting before translating content.
2. Record the lifecycle, ViewModel, saved-state, theme, and frame owner for the target root.
3. For `renderInto`, install every required owner explicitly and bind session disposal.
4. Move replay-safe View binding into `update` or `onReset`; move irreversible committed-tree work
   into `onCommit`.
5. Make `onRelease` safe for rollback candidates as well as committed removals.
6. Treat session disposal as terminal and do not depend on current post-disposal no-op behavior.
7. Test Fragment view recreation independently from Fragment destruction.
8. Test configuration change, permanent removal, and process recreation as distinct state events.
9. Keep lifecycle-aware work lifecycle-aware when a navigation destination is retained but hidden.
10. Record any dependency on an unsupported Compose interop API before removing the Compose host.

## Re-verification requirements

Re-verify this page when any of the following changes:

- a host entry point, owner local, session disposal rule, or Android View callback contract;
- Compose UI/Runtime, Activity, Lifecycle, or SavedState stable baselines;
- the repository's executable Compose/AndroidX comparison baseline; or
- any known verification gap listed above.

The minimum evidence is the owning module contract, the cited JVM tests, Android interop
instrumentation, compiled host samples, and a fresh review of the linked official AndroidX
documentation. Fragment view recreation and renderer transaction behavior require behavioral
tests; API signatures alone are insufficient.
