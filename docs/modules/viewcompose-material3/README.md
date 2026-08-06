# Material 3 Theme Adapter

`viewcompose-material3` is the design-system layer for Google Material 3 on Android. It reads
Material theme colors, typography, and shapes into platform-independent `UiThemeTokens`, resolves
dynamic-color contexts, and refreshes tokens after configuration or imperative theme changes.

It does not render core controls and does not participate in View reconciliation. The Android
engine therefore remains usable without Material Components; only this adapter and explicitly
Material-backed integrations own that dependency.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-material3:0.1.0-alpha01")
}
```

- Stability: **Alpha**.
- Platform: Android library, `minSdk 24`, `compileSdk 36`, and Java 11 bytecode.
- API dependency: `viewcompose-ui-foundation`.
- Implementation dependencies: Material Components, AppCompat, and AndroidX Core.

## Theme resolution

`Material3ThemeBridge.resolveContext` creates the stable themed context that must be shared by the
root View and overlays. `Material3Theme` provides the mapped token snapshot and observes Android
configuration changes while mounted. Call `Material3ThemeRefreshController.refresh()` on the main
thread after `Context.setTheme` or another imperative resource mutation that does not emit a
configuration change.

```kotlin
val resolved = Material3ThemeBridge.resolveContext(
    context = activity,
    dynamicColorPolicy = Material3DynamicColorPolicy.UseIfAvailable,
)

Material3Theme(resolvedTheme = resolved) {
    Content()
}
```

Standard applications receive this lifecycle automatically through `viewcompose-android`.

## Related documentation

- [Theme guide](../../guides/theming.md)
- [UI foundation](../viewcompose-ui-foundation/README.md)
- [Android aggregate](../viewcompose-android/README.md)
- [Five-layer architecture](../../architecture/decisions/0002-five-layer-runtime-module-architecture.md)

The generated reference is available in the
[`viewcompose-material3` API tree](https://docs.viewcompose.com/api/viewcompose-material3/current/).

## Compatibility notes

This artifact begins at `0.1.0-alpha01`. Android theme bridge types previously shipped from the UI
foundation were renamed to the `Material3*` API family and moved here without compatibility aliases.
