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

This low-level entry does not automatically provide Lifecycle, ViewModel, saved state,
environment, theme, or frame-clock locals. A custom host owns those providers and must dispose the
session before abandoning its container. One container must have only one mounted-tree owner.

`AndroidEnvironmentBridge.fromContext(context)` maps density, font scale, locales, and layout
direction to `UiEnvironmentValues`. `AndroidOverlayHostDefaults.androidOrNoOp(root)` performs an
optional neutral-overlay `ServiceLoader` lookup without moving Android service discovery into UI
Foundation. Zero providers returns no-op; multiple providers fail because classpath order may not
choose a design system. Standard Activity and Fragment roots use explicit factories instead.

## Native View transaction contract

`AndroidView` mounts a platform View without weakening renderer rollback semantics:

```kotlin
AndroidView(
    factory = { context -> PlayerView(context) },
    update = { view -> configurePlayer(view as PlayerView, state) },
    key = playerId,
    onCommit = { view -> (view as PlayerView).play() },
    onRelease = { view -> (view as PlayerView).release() },
)
```

- `factory` runs only for a new native node.
- `update`, `onReset`, and `Modifier.nativeView` are replay-safe configuration.
- `onCommit` runs only after the complete View-tree transaction commits.
- `onRelease` runs once after committed removal or session disposal.

## Saved state, scheduling, and threading

`viewComposeSaveableStateRegistry(owner)` binds framework saveable state to an Android
`SavedStateRegistryOwner`. View creation, reconciliation, explicit rendering, and disposal are
main-thread work. State invalidations coalesce onto the next Choreographer frame, while an explicit
`RenderSession.render()` remains synchronous.

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
