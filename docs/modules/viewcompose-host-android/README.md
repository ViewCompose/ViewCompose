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

```kotlin
val session = renderInto(container) {
    CustomSurface()
}

session.setRenderingActive(false)
session.render()
session.dispose()
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

## Optional source-inspection boundary

The Host performs one process-local `ServiceLoader` lookup for the neutral
`RenderSessionSourceTooling` port. No provider is the normal production configuration and is a
stable no-op. Ambiguous or failed discovery disables source inspection and logs a diagnostic rather
than changing rendering. The Host contains no device-locator protocol, Android component, report
writer, View-tree listener, or recurring inspection lifecycle.

Running-device DSL navigation is implemented downstream by the optional `viewcompose-preview`
artifact. Add it with `debugImplementation` to enable the feature. When present in a debuggable
process, it may retain bounded source candidates from the first successful Host, navigation
destination, or pager-page frame through the neutral port. The report uses the runtime trace ID,
parent ID, and role rather than a second source-only identity. Live visibility is inspected and a private report is written only after Android
Studio sends an explicit request. Scroll, layout, rendering-active changes, and session disposal do
not publish reports. This ownership follows
[ADR-0009](../../architecture/decisions/0009-development-tooling-isolation.md).

## Native View transaction contract

`AndroidView` mounts a platform View without weakening renderer rollback semantics:

```kotlin
AndroidView(
    factory = { context -> PlayerView(context) },
    update = { view -> configurePlayer(view as PlayerView, state) },
    key = playerId,
    onReset = { view -> resetPlayer(view as PlayerView) },
    onCommit = { view -> (view as PlayerView).play() },
    onRelease = { view -> (view as PlayerView).release() },
)
```

- `factory` runs only for a new native node.
- `update`, `onReset`, and `Modifier.nativeView` are replay-safe configuration.
- `onReset` opts the node into mounted-tree reuse across lazy keys. It runs only after the old
  logical session, effects, and saveable lease have ended and before the new key binds.
- `onCommit` runs only after the complete View-tree transaction commits.
- `onRelease` runs once whenever a created View is permanently abandoned: candidate rollback,
  committed removal, non-reusable session disposal, or final reuse-cache eviction. Omitting
  `onReset` prevents that mounted tree from crossing keys.

## Saved state, scheduling, and threading

`viewComposeSaveableStateRegistry(owner)` binds framework saveable state to an Android
`SavedStateRegistryOwner`. View creation, reconciliation, explicit rendering, and disposal are
main-thread work. State invalidations coalesce onto the next Choreographer frame, while an explicit
`RenderSession.render()` remains synchronous until terminal disposal.

The installed `AndroidCoreRenderEngine` also translates UI Foundation's Q3 observed-property SPI
to exact Android Renderer targets. Property-only frames keep the mounted root list and target map
stable, validate that every target still belongs to the committed frame, and return only commit
effects, failures, and optional diagnostics. A foreign or stale target fails instead of triggering
a whole-tree render.

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
integration inputs. Custom platforms may keep the default `null` port. Applications that use **Locate Device DSL** retain
`viewcompose-preview` in a debug configuration, while release builds carry no locator
implementation.
