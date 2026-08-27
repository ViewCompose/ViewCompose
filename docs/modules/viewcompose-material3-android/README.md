---
schema_version: 2
document_id: module.viewcompose-material3-android
doc_type: module
owner:
  kind: module
  id: viewcompose-material3-android
version_lane: released
capability_ids:
  - material3.components
  - theme.material3
artifact_ids:
  - viewcompose-material3-android
sample_ids:
  - module.material3-android-dependency
  - module.material3-android-host
coordinate: com.viewcompose:viewcompose-material3-android:0.1.0-alpha01
minimal_usage_sample_id: module.material3-android-host
---

# Material 3 Android Application Integration

`viewcompose-material3-android` is the recommended single dependency for an Android Material 3
application. It combines the neutral Android application aggregate with the Material 3 adapter and
the Material overlay adapter, and exposes named Activity and Fragment `setMaterial3UiContent` entry points.

This artifact is a platform integration and release boundary, not a second renderer. It privately
owns Material root-context resolution and publicly exposes the neutral host plus Material token
APIs through intentional `api` dependencies.

## Artifact and stability

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="material3-android-module-dependency" sample_id="module.material3-android-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha01")
}
```

- Stability: **Alpha**.
- Platform: Android library, `minSdk 24`, `compileSdk 36`, and Java 11 bytecode.
- API dependencies: `viewcompose-android` and `viewcompose-material3`.
- Material Components, AppCompat, the neutral and Material overlay artifacts, host, Lifecycle, and ViewModel runtime dependencies resolve
  transitively; applications declare them directly only when their own source uses those APIs.

## Named Material host

{/* compiled-region source="viewcompose-material3-android/src/test/samples/com/viewcompose/material3/android/samples/Material3AndroidHostSamples.kt" region="material3-android-module-host" sample_id="module.material3-android-host" build_target=":viewcompose-material3-android:compileDebugUnitTestKotlin" */}
```kotlin
fun installMaterial3Content(activity: ComponentActivity) {
    activity.setMaterial3UiContent {
        Text("Hello from Material 3 ViewCompose")
    }
}
```

`setMaterial3UiContent` resolves one Material Context before root construction. That same stable
Context creates the root, native descendants, and default overlays; `Material3Theme` reads and
provides the matching immutable token snapshot. The default
`Material3DynamicColorPolicy.UseIfAvailable` follows supported Android dynamic color. Pass
`Disabled` for deterministic XML-theme output.

The named host installs the neutral Android resource environment and refreshes the Material stable
wrapper before publishing each resource revision. Pass `rootContext` plus a host-scoped
`AndroidResourceRefreshController` when an application locale/theme wrapper mutation does not
recreate the Activity or dispatch a configuration callback. Ordinary resources, environment
values, and Material tokens then update from the same revision. Repeating
`setMaterial3UiContent` disposes the old render session and reconstructs the root; use that path
when changing to a design system or constructor-sensitive Context rather than patching only tokens
on existing Views.

Activity and Fragment roots accept the neutral `RenderDiagnostics` configuration and propagate it
through the same Host correlation tree as `viewcompose-android`. The alpha hard cut removes the
three independent render callbacks; Material theme resolution does not alter diagnostics roles,
collection levels, ordering, or sink-failure isolation.

The default overlay factory explicitly constructs the Material adapter. Material behavior is not
selected through `ServiceLoader`, so another design-system root cannot receive Material Snackbar or
bottom-sheet behavior merely because this aggregate is present elsewhere in the application.

## Boundary rule

This module may depend on Material and the neutral aggregate. `viewcompose-android`,
`viewcompose-host-android`, UI Foundation, and Android Renderer may not depend on or import it.
Material recipes still resolve into neutral NodeSpec and Foundation contracts before rendering;
the named integration does not register a Material branch in the renderer.

## Related documentation

- [Material 3 adapter](../viewcompose-material3/README.md)
- [Neutral Android aggregate](../viewcompose-android/README.md)
- [Material 3 dynamic-color guide](../../guides/theming-dynamic-color.md)
- [Theme runtime architecture](../../architecture/theming.md)
- [Multi-design-system architecture](../../architecture/design-systems.md)

The generated reference is available in the
[`viewcompose-material3-android` API tree](https://docs.viewcompose.com/api/viewcompose-material3-android/current/).

## Compatibility notes

This artifact begins at `0.1.0-alpha01`. Existing alpha applications that used the Material-aware
`com.viewcompose.android.setUiContent` replace the coordinate and import with
`viewcompose-material3-android` and `setMaterial3UiContent`. Material policy parameters keep their
previous defaults and behavior; the source-level rename is intentional so neutral hosts cannot
silently select Material.

The correlated-diagnostics hard cut replaces the three render callbacks with the neutral
`diagnostics` parameter and retains no deprecated forwarding overloads.
