# Android Host

`viewcompose-host-android` is the supported boundary between a ViewCompose composition and the
Android View system. It creates Activity and Fragment roots, owns retained render sessions, provides
Android lifecycle and state services, resolves theme and environment values, schedules invalidation
work on Choreographer frames, and exposes explicit native View, animation, and graphics interop.

Most Android applications need only this ViewCompose dependency. It exposes runtime, UI contract,
and widget core transitively, while renderer, lifecycle, and ViewModel integration remain private
host implementation details. Depend on one of those lower-level artifacts directly only when the
application intentionally uses its advanced APIs independently of the host.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-host-android:0.1.0-alpha01")
}
```

- Stability: **Alpha**. Host extension and native interop contracts may change between alpha releases.
- Platform: Android library, `minSdk 24`, `compileSdk 36`, and Java 11 bytecode.
- Dependency exposure: runtime, UI contract, and widget core are API dependencies; lifecycle,
  ViewModel, and renderer are implementation dependencies.
- Android dependencies: AndroidX Activity/Fragment/AppCompat, Lifecycle, SavedState,
  ConstraintLayout, DynamicAnimation, Material Components, and Kotlin coroutines for Android.
- Activity/Fragment class hierarchy, Material theme, and optional native animation or
  ConstraintLayout interop are caller-owned platform integrations. Applications declare the
  AndroidX/Material artifacts they directly use; the host does not act as their version catalog.

## Recommended host entry points

Use `ComponentActivity.setUiContent` when ViewCompose owns the Activity content:

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUiContent {
            Text("Hello from ViewCompose")
        }
    }
}
```

Use `Fragment.setUiContent` from `onCreateView` when a Fragment owns the content:

```kotlin
override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
): View = setUiContent {
    ProfilePage()
}
```

Both integrations create a full-size root, render the first frame synchronously, and provide:

- the current `LifecycleOwner` and `ViewModelStoreOwner`;
- an Android-backed `SaveableStateRegistry` for `rememberSaveable`;
- Android density, locale, layout direction, and context environment values;
- Android theme resolution, including the selected dynamic-color policy;
- the main animation coroutine context and a Choreographer-backed monotonic frame clock;
- an Android overlay host by default, with an injectable factory for custom hosts and tests.

Repeated `setUiContent` calls dispose the previous session. Fragment sessions follow the current
View lifecycle and are released across View recreation; Activity sessions end at Activity
destruction.

## Custom container hosting

`renderInto(container)` is the low-level retained-session API. It installs the Android renderer and
commits the first frame before returning:

```kotlin
val session = renderInto(container) {
    CustomSurface()
}

session.setRenderingActive(false)
session.render() // explicit rendering remains synchronous while inactive
session.dispose()
```

This entry deliberately does not provide lifecycle, ViewModel, saved state, environment, theme, or
frame-clock locals. A custom host owns those providers and must dispose the session before the
container or its lifecycle is abandoned. One container must have only one active mounted-tree owner.

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
- `update`, `onReset`, and `Modifier.nativeView` are replay-safe configuration. They can run again
  while a failed frame restores the last committed tree and must not perform external one-shot work.
- `onCommit` runs only after the entire View-tree transaction commits.
- `onRelease` runs once after committed removal or session disposal.
- Stable sibling keys retain the correct native View across reordering.

## Saved state

`viewComposeSaveableStateRegistry(owner)` binds one ViewCompose registry to an Android
`SavedStateRegistryOwner` identity. Restored values are consumed on first access; every Android save
pulls the latest committed ViewCompose snapshot. Destroying the owner unregisters the provider and
releases the process-local binding.

Values may be null, recursively saveable lists or string-keyed maps, or Android values supported by
Bundle such as `Parcelable`, `Serializable`, `IBinder`, `Size`, and `SizeF`. Functions and unsupported
objects are rejected by the saveable-state contract. A corrupt restored entry is isolated so other
keys can still restore.

## Frame scheduling and threading

- View creation, binding, reconciliation, explicit rendering, and disposal are main-thread work.
- State invalidations coalesce onto the next Choreographer frame.
- `RenderSession.render()` cancels a pending scheduled callback and renders synchronously.
- Inactive sessions preserve one pending invalidation and schedule it after reactivation.
- Cancelling a coroutine waiting on `AndroidMonotonicFrameClock` removes its pending frame callback.
- `lastFrameReport` describes the latest attempted frame; `lastRenderFailure` intentionally retains
  historical failure information after a later successful frame.

## Animation and graphics interop

`AndroidAnimationInterop` starts platform `ObjectAnimator`, `ValueAnimator`,
`ViewPropertyAnimator`, spring, fling, transition, and MotionLayout operations. Returned animation
objects are caller-owned and must be cancelled with the owning lifecycle. These operations are not
ViewCompose state animations and do not participate in render rollback.

`AndroidGraphicsInterop` provides API-gated RenderEffect and RuntimeShader factories, bitmap
rendering helpers, and View layer-paint configuration. API-gated methods return `null` or `false`
when unsupported. `Modifier.androidAnimation` and `Modifier.androidGraphics` are replay-safe native
View configuration modifiers; do not start one-shot work from their callbacks.

## Related documentation

- [Architecture and module boundaries](../../architecture/overview.md)
- [Render failure and commit semantics](../../architecture/render-failures.md)
- [Lifecycle and saved-state architecture](../../architecture/lifecycle-and-saved-state.md)
- [Theme integration guide](../../guides/theming.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

The complete generated reference is available in the
[`viewcompose-host-android` API tree](https://docs.viewcompose.com/api/viewcompose-host-android/current/).

## Compatibility notes

The `0.1.0-alpha01` line establishes the Activity, Fragment, custom-container, saveable-state,
frame-scheduling, and native View transaction contracts. Do not persist `RenderSession`, Android
root Views, saved-state registry instances, or renderer diagnostics. Custom hosts must be reviewed
when host, widget-core, or renderer contracts change even when their DSL source still compiles.
