---
schema_version: 2
document_id: module.viewcompose-android
doc_type: module
owner:
  kind: module
  id: viewcompose-android
version_lane: released
capability_ids:
  - host.android-container
  - host.android-resources
  - lifecycle.owner-boundaries
  - viewmodel.owner-boundaries
artifact_ids:
  - viewcompose-android
sample_ids:
  - module.android-dependency
  - module.android-entry
coordinate: com.viewcompose:viewcompose-android:0.1.0-alpha01
minimal_usage_sample_id: module.android-dependency
---

# Neutral Android Application Aggregate

`viewcompose-android` is the recommended single dependency for an Android application that wants
to choose its design system explicitly. It combines the UI foundation, neutral Android host
engine, Lifecycle integration, and ViewModel integration, and exposes the Activity and Fragment
`setUiContent` entry points.

The aggregate contains no Material dependency or design-system policy. It includes the neutral
`viewcompose-overlay-android` transport as a runtime implementation dependency. Its purpose is dependency
curation and a stable application-facing host boundary; Material applications use the named
[`viewcompose-material3-android`](../viewcompose-material3-android/README.md) aggregate instead.

## Artifact and stability

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="android-module-dependency" sample_id="module.android-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-android:0.1.0-alpha01")
}
```

- Stability: **Alpha**.
- Platform: Android library, `minSdk 24`, `compileSdk 36`, and Java 11 bytecode.
- Transitive API surface: host engine, UI foundation, Lifecycle integration, ViewModel
  integration, AndroidX Activity, and AndroidX Fragment.
- Material dependency: none.

## Neutral entry points

{/* compiled-region source="viewcompose-android/src/test/samples/com/viewcompose/android/samples/AndroidEntrySamples.kt" region="android-entry" sample_id="module.android-entry" build_target=":viewcompose-android:compileDebugUnitTestKotlin" */}
```kotlin
fun activityHostSample(activity: ComponentActivity) {
    val diagnostics = RenderDiagnostics(
        collection = RenderDiagnosticCollection(
            frameLevel = RenderFrameDiagnosticLevel.Stats,
        ),
        sink = { event -> println(event) },
    )
    activity.setUiContent(diagnostics = diagnostics) {
        Text("Hello from ViewCompose")
    }
}
```

`ComponentActivity.setUiContent` and `Fragment.setUiContent` create a full-size root and provide:

- Lifecycle and ViewModel owners;
- Android-backed saveable state;
- density, font scale, locale, layout direction, Android resource access, and a resource revision;
- the animation coroutine context and Choreographer frame clock; and
- the neutral Android overlay transport, with an injectable replacement factory.

They do not resolve Material XML, dynamic color, or design tokens. Without an explicit provider,
content reads the deterministic `UiThemeDefaults.light()` framework baseline. The optional
`rootContext` parameter defaults to the Activity or Fragment context and is shared by the root,
native descendants, and default overlays.

When the root design system requires a different Android Context, resolve that Context first and
pass it to `rootContext`. Switching between root design systems must call `setUiContent` again with
the new Context and token provider so Views are reconstructed under one coherent platform/theme
snapshot. Repeated calls dispose the previous session. A Fragment call made from `onCreateView`
returns the root first, then starts rendering as soon as Android publishes that root's
`viewLifecycleOwner`. Content receives that View owner, and the session ends at `onDestroyView`;
Fragment-scoped ViewModel and saved-state ownership remain stable across View recreation. Activity
sessions render synchronously and end when the Activity is destroyed.

The roots also install `LocalSavedStateRegistryOwner` for committed SDK View state. Activity
content receives the Activity for both owner locals. Fragment content receives
`viewLifecycleOwner` through `LocalLifecycleOwner` and the Fragment through
`LocalSavedStateRegistryOwner`; lifecycle-bound native work therefore ends with the View while a
compatible SDK Bundle may restore into the next View instance.

Both entry points accept `diagnostics = RenderDiagnostics(...)`. The configuration starts one
Host diagnostics tree and is inherited by navigation, lazy, pager, and overlay child sessions.
The alpha hard cut removes `onRenderStats`, `onRenderResult`, and `onRenderFailure`; use
`RenderFrameCompleted` and `RenderFailureObserved` instead. `debug` remains logging and
slow-operation policy only.

The standard roots automatically install `AndroidResourceEnvironment`, so content may use the
lookup functions from `com.viewcompose.host.android.resources` without a page-owned invalidation
state. Configuration callbacks refresh ordinary resources and environment values. For an
application locale/theme wrapper mutation that emits no callback, pass one
`AndroidResourceRefreshController` to `setUiContent`, replace the stable `rootContext` resources,
then call `refresh()`. Constructor-sensitive Context or design-system changes still require another
`setUiContent` call and root reconstruction.

## Dependency rule

Use `viewcompose-android` with a static or application-owned design system such as
`viewcompose-oneui7`. Use `viewcompose-material3-android` for Android Material XML and dynamic-color
integration. Add optional capabilities such as navigation, image adapters, named overlay presenters, or advanced
shadows individually; do not repeat transitive foundation artifacts unless the build intentionally
constrains versions or directly uses their standalone APIs.

## Related documentation

- [Multi-design-system architecture](../../architecture/design-systems.md)
- [Getting started](../../tutorials/getting-started.md)
- [Android host engine](../viewcompose-host-android/README.md)
- [Material 3 Android integration](../viewcompose-material3-android/README.md)
- [One UI 7 design system](../viewcompose-oneui7/README.md)

The generated reference is available in the
[`viewcompose-android` API tree](https://docs.viewcompose.com/api/viewcompose-android/current/).

## Compatibility notes

The alpha API makes a source-level hard cut: the generally named `setUiContent` no longer accepts
`Material3DynamicColorPolicy` or `Material3ThemeRefreshController`. Material callers replace their
dependency with `viewcompose-material3-android`, import `setMaterial3UiContent`, and keep the same
content body. No all-default deprecated forwarding overload is retained because it would be
ambiguous with the neutral zero-argument entry point.

The correlated-diagnostics hard cut removes the three independent render callbacks from both
Activity and Fragment entry points. No deprecated forwarding overload or result-only Local remains.
