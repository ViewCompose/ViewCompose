---
schema_version: 2
document_id: module.viewcompose-host-android
doc_type: module
owner:
  kind: module
  id: viewcompose-host-android
version_lane: released
capability_ids:
  - host.android-container
  - host.android-view
  - host.android-resources
  - host.android-animation
  - host.android-graphics
artifact_ids:
  - viewcompose-host-android
sample_ids:
  - module.host-android-dependency
  - module.host-android-render-into
  - module.host-android-view-adapter
  - module.host-android-resources
  - module.host-android-animation
  - module.host-android-graphics
coordinate: com.viewcompose:viewcompose-host-android:0.1.0-alpha04
minimal_usage_sample_id: module.host-android-dependency
---

# Android Host Engine

`viewcompose-host-android` is the low-level Android View host engine. It installs the renderer,
owns retained render sessions, schedules invalidations on Choreographer frames, bridges Android
saved state and environment values, adapts focus/logging/tracing, offers neutral overlay discovery
for custom low-level hosts, and exposes native View, animation, and graphics interop. It deliberately does not
own Activity/Fragment convenience entry points, Material theme resolution, Lifecycle locals, or
ViewModel locals.

Applications should normally depend on [`viewcompose-android`](../viewcompose-android/README.md).
Depend on this artifact directly only when building a custom container host or using its interop
APIs without the standard Activity/Fragment integration.

## Artifact and stability

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="host-android-module-dependency" sample_id="module.host-android-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-host-android:0.1.0-alpha04")
}
```

- Stability: **Alpha**.
- Platform: Android library, `minSdk 24`, `compileSdk 36`, and Java 11 bytecode.
- API dependencies: runtime, UI contract, UI foundation, AndroidX Lifecycle, and AndroidX
  SavedState where their types appear in public signatures.
- Private implementation dependencies: Android renderer, coroutines Android, ConstraintLayout,
  and DynamicAnimation.
- Material Components is not a dependency of this module.

The module exclusively owns `com.viewcompose.host.android`. Activity and Fragment composition
roots use `com.viewcompose.android` and therefore cannot silently expand this low-level package.

## Custom container hosting

`renderInto(container)` installs the Android engine and commits the first frame before returning:

{/* compiled-region source="viewcompose-host-android/src/test/samples/com/viewcompose/host/android/samples/HostAndroidSamples.kt" region="host-render-into" sample_id="module.host-android-render-into" build_target=":viewcompose-host-android:compileDebugUnitTestKotlin" */}
```kotlin
fun renderIntoSample(container: ViewGroup) {
    val diagnostics = RenderDiagnostics(
        collection = RenderDiagnosticCollection(
            frameLevel = RenderFrameDiagnosticLevel.Stats,
        ),
        sink = { event -> println(event) },
    )
    val session = renderInto(container, diagnostics = diagnostics) {
        Text("Custom host")
    }
    session.setRenderingActive(false)
    session.render()
    session.dispose()
    check(runCatching(session::render).exceptionOrNull() is IllegalStateException)
}
```

Pass `diagnostics = RenderDiagnostics(...)` to start a correlated diagnostics tree. The low-level
`role` and `parentLocalSnapshot` parameters are Q3 integration controls for independently rendered
children; ordinary custom roots keep the `Host` default and no parent snapshot. `debug` controls
logging and slow-operation warnings, not diagnostics collection.

This low-level entry does not automatically provide Lifecycle, ViewModel, saved state,
environment, theme, or frame-clock locals. A custom host owns those providers and must dispose the
session before abandoning its container. One container must have only one mounted-tree owner.
Disposal is idempotent and terminal: later caller-initiated `render` or `setRenderingActive` calls
throw `IllegalStateException`. A frame callback already queued inside the Android runtime is
cancelled or ignored and cannot render after disposal.

The frame-aligned runtime uses a dedicated internal callback instead of a generic captured
function on the UI-thread dispatch path. Cross-thread requests still post one bounded `Runnable`;
same-thread requests and Choreographer delivery add no callback wrapper per frame.

`AndroidEnvironmentBridge.fromContext(context)` maps density, font scale, locales, and layout
direction to `UiEnvironmentValues`. `AndroidOverlayHostDefaults.androidOrNoOp(root)` performs an
optional neutral-overlay `ServiceLoader` lookup without moving Android service discovery into UI
Foundation. Zero providers returns no-op; multiple providers fail because classpath order may not
choose a design system. Standard Activity and Fragment roots use explicit factories instead.

Custom hosts that need Android resources install `AndroidResourceEnvironment(context)`. Inside the
provider, content can call `stringResource`, formatted strings, `pluralStringResource`,
`colorResource`, logical or pixel dimension lookups, boolean/integer lookups, and string/integer
array lookups. `LocalAndroidContext.current` and `LocalAndroidResources.current` are bounded escape
hatches for uncommon APIs; access without the provider fails with an installation error.

The provider observes Android configuration callbacks, republishes density, font scale, locales,
direction, and a monotonic resource revision, and unregisters with the mounted composition. Use one
host-scoped `AndroidResourceRefreshController` after replacing a stable Context wrapper or another
imperative resource mutation that emits no callback. Calls, callbacks, and disposal are main-thread
work. Resource results are synchronous snapshots; do not retain provider-owned Context or Resources
beyond the session.

{/* compiled-region source="viewcompose-host-android/src/test/samples/com/viewcompose/host/android/samples/HostAndroidSamples.kt" region="host-android-resources" sample_id="module.host-android-resources" build_target=":viewcompose-host-android:compileDebugUnitTestKotlin" */}
```kotlin
fun androidResourceEnvironmentSample(
    builder: UiTreeBuilder,
    context: Context,
    titleResource: Int,
) {
    builder.AndroidResourceEnvironment(context) {
        Text(stringResource(titleResource))
    }
}
```

## Optional session-inspection boundary

The Host reads the neutral `RenderSessionInspectionTooling` port from one process-local in-memory
slot. No provider is the normal production configuration and freezes to a stable no-op at the first
session. A downstream tooling artifact may use the Q3
`installRenderSessionInspectionTooling` integration hook during Android component initialization,
before any render session starts. Reinstalling the same instance is idempotent; distinct early
providers disable the port, and late installation is ignored. Installation and first access are
synchronized and perform no classpath scan, file I/O, or Android service lookup. The Host contains
no device-locator protocol, Android component, report writer, View-tree listener, or recurring
inspection lifecycle.

Running-device DSL navigation is implemented downstream by the optional `viewcompose-preview`
artifact. Add it with `debugImplementation` to enable the feature. When present in a debuggable
process, it may retain bounded source candidates from the first successful Host, navigation
destination, or pager-page frame through the neutral port. `RenderSessionInspectionPolicy` tracks
lazy-item, overlay, and preview sessions without enabling their composition-time source capture, so
request-driven node inspection can reach the real child owner without adding high-churn stack
capture. The report uses the runtime trace ID, parent ID, and role rather than a second source-only
identity.

The same registration receives a `RenderSessionNodeInspection` whose session state is weak outside
the render owner. It calls `CoreRenderEngine.inspectMountedNodes` only after an explicit request;
`AndroidCoreRenderEngine` then performs the bounded current-tree traversal and returns weak native
targets. No provider means no inspection state or mounted-node assignment. Live visibility,
mounted nodes, and a private response are read only after Android Studio requests them. Scroll,
layout, rendering-active changes, and session disposal do not publish reports, and Host owns no
overlay or IDE protocol. This ownership follows
[ADR-0009](../../architecture/decisions/0009-development-tooling-isolation.md).
The no-discovery initialization mechanism is fixed by
[ADR-0022](../../architecture/decisions/0022-in-memory-development-tooling-installation.md).

The registration also receives the neutral Q3 `RenderSessionTimingInspection` control. An explicit
downstream request may start one finite composition/reconciliation/binding capture; Android Host
only maps the synchronous `CoreRenderTimingCollector` to the Android renderer and owns no protocol,
poller, report, or Studio UI. The engine preserves composition node identity across reconciliation
and binding, while renderer-only nodes receive an opaque capture-local fallback. Without the
optional tooling artifact and request, normal Host rendering performs zero per-node clock reads and
keeps no timing history.

## Native View transaction contract

Reusable integrations implement the typed `AndroidViewAdapter<V, S>` contract. The adapter class
and `constructionKey` identify constructor-sensitive state, while `key` continues to identify
logical content:

{/* compiled-region source="viewcompose-host-android/src/test/samples/com/viewcompose/host/android/samples/HostAndroidSamples.kt" region="host-android-view-adapter" sample_id="module.host-android-view-adapter" build_target=":viewcompose-host-android:compileDebugUnitTestKotlin" */}
```kotlin
fun typedAndroidViewAdapterSample(builder: UiTreeBuilder) {
    builder.AndroidView(
        adapter = NativeLabelAdapter,
        state = "Typed native label",
        key = "label",
        constructionKey = "default-text-appearance",
        modifier = Modifier.nativeView(key = "enabled") { view ->
            view.isEnabled = true
        },
    )
}

private object NativeLabelAdapter : AndroidViewAdapter<TextView, String> {
    override val reusePolicy: AndroidViewReusePolicy = AndroidViewReusePolicy.Resettable

    override fun create(scope: AndroidViewCreateScope): TextView = TextView(scope.context)

    override fun update(scope: AndroidViewUpdateScope<TextView>, state: String) {
        scope.view.text = state
    }

    override fun onReset(
        scope: AndroidViewResetScope<TextView>,
        reason: AndroidViewResetReason,
    ) {
        scope.view.text = null
    }
}
```

- `create`, `update`, reset, commit, and release run synchronously on the Android main thread.
  Creation receives the renderer-supplied themed `Context`; creation, update, reset, and commit
  scopes also expose the VNode's immutable `UiEnvironmentValues`.
- `state` remains caller-owned. `update` applies its complete replay-safe configuration and may run
  again during rollback. Ordinary same-identity updates never invoke `onReset`.
- A changed adapter implementation class or `constructionKey` creates and updates a detached
  candidate. Failure releases only that candidate and preserves the committed View; success swaps
  it atomically and releases the displaced View once.
- `AndroidViewReusePolicy.Resettable` opts the node into mounted-tree reuse across lazy keys.
  `onReset(..., MountedTreeReuse)` runs only after the old logical session, effects, and saveable
  lease have ended and before the new key's update. The default `Never` policy prevents the
  containing mounted tree from crossing keys.
- `onCommit` runs only after the complete composition transaction commits. `onRelease` runs once
  whenever a created View is permanently abandoned: candidate rollback, committed replacement or
  removal, non-reusable session disposal, or final reuse-cache eviction.
- `lifecycleMode` is bounded diagnostic metadata. Raw adapters report `None`; AndroidX integration
  adapters report `AdapterManaged`. Host records the value but never installs an owner observer or
  changes transaction ordering because of it.

The callback-based `AndroidView(factory, update, ...)` overload remains the low-level escape hatch
and delegates to the same typed transaction path. Its trailing `constructionKey` has the same
replacement semantics, and supplying `onReset` opts into only cross-key mounted-tree reuse.

## Native animation and graphics interop

`AndroidAnimationInterop` starts platform animators without moving their lifecycle into the
composition animation engine. `MotionLayoutView` hosts an AndroidX `MotionLayout`, while
`Modifier.androidAnimation` applies replay-safe native animation properties during binding:

{/* compiled-region source="viewcompose-host-android/src/test/samples/com/viewcompose/host/android/samples/HostAndroidSamples.kt" region="host-android-animation" sample_id="module.host-android-animation" build_target=":viewcompose-host-android:compileDebugUnitTestKotlin" */}
```kotlin
fun platformAnimationInteropSample(target: View) =
    AndroidAnimationInterop.startObjectAnimator(
        target,
        "alpha",
        0f,
        1f,
        durationMillis = 180L,
    )

fun UiTreeBuilder.motionLayoutInteropSample() {
    MotionLayoutView(
        factory = { context -> MotionLayout(context) },
        update = { layout -> layout.progress = 0f },
        modifier = Modifier.androidAnimation(key = "settled-alpha") { view ->
            view.alpha = 1f
        },
    )
}
```

`AndroidGraphicsInterop` exposes the API-gated platform `RenderEffect` boundary, and
`Modifier.androidGraphics` applies native graphics state through the same retained-View binding
path:

{/* compiled-region source="viewcompose-host-android/src/test/samples/com/viewcompose/host/android/samples/HostAndroidSamples.kt" region="host-android-graphics" sample_id="module.host-android-graphics" build_target=":viewcompose-host-android:compileDebugUnitTestKotlin" */}
```kotlin
fun platformGraphicsInteropSample(target: View): Modifier {
    val effect = AndroidGraphicsInterop.createBlurEffect(radiusX = 12f, radiusY = 12f)
    AndroidGraphicsInterop.applyRenderEffect(target, effect)
    return Modifier.androidGraphics(key = "native-graphics") { view ->
        view.alpha = 1f
    }
}
```

## Saved state, scheduling, and threading

`viewComposeSaveableStateRegistry(owner)` binds framework saveable state to an Android
`SavedStateRegistryOwner`. View creation, reconciliation, explicit rendering, and disposal are
main-thread work. State invalidations coalesce onto the next Choreographer frame, while an explicit
`RenderSession.render()` remains synchronous until terminal disposal.

The installed `AndroidCoreRenderEngine` also translates UI Foundation's Q3 observed-property SPI
to exact Android Renderer targets. Property-only frames keep the mounted root list and target map
stable, validate that every target still belongs to the committed frame, and return only commit
effects, failures, and optional diagnostics. A foreign or stale target fails instead of triggering
a whole-tree render. When a lazy presentation changes logical owner across a renderer-created host
wrapper, the engine propagates that transaction marker only for the synchronous render and clears
it in `finally`; the wrapper therefore cannot hide key-owned state replacement or retain transfer
state after failure.

## Related documentation

- [Five-layer architecture](../../architecture/decisions/0002-five-layer-runtime-module-architecture.md)
- [Architecture overview](../../architecture/overview.md)
- [Render failure semantics](../../architecture/render-failures.md)
- [Android aggregate](../viewcompose-android/README.md)

The generated reference is available in the
[`viewcompose-host-android` API tree](https://docs.viewcompose.com/api/viewcompose-host-android/current/).

## Compatibility notes

The Activity and Fragment `setUiContent` extensions moved to `viewcompose-android` in the hard-cut
five-layer architecture. No compatibility facade remains in this low-level artifact.
Version `0.1.0-alpha04` restricts overlay service discovery to one neutral provider; standard roots
choose their backend explicitly, and duplicate providers are a configuration error.
Device source inspection moved out of this artifact. The alpha `renderInto` hard cut replaces the
three render callbacks with one `RenderDiagnostics` configuration and adds typed role/parent
integration inputs. Custom platforms may keep the default `null` port. Applications that use
**Inspect Device Diagnostics** retain `viewcompose-preview` in a debug configuration, while release
builds carry no device-inspector implementation.
