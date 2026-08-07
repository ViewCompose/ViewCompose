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
- Baseline: standard, non-Expressive Material 3 from Material Components `1.13.0`.

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

## Token baseline and fallback

`Material3ThemeDefaults.light()` and `Material3ThemeDefaults.dark()` provide deterministic Material
3 snapshots when no Android themed Context is available or an individual Android attribute is
missing. Each snapshot includes:

- the complete Material color scheme used by the adapter, including surface-container, inverse,
  outline, and container-content roles;
- all 15 standard display, headline, title, body, and label typography roles;
- extra-small, small, medium, large, extra-large, and full shape roles; and
- the selected standard sizing profile for buttons, text fields, segmented controls, progress
  indicators, FABs, search, badges, and native compact-input effective targets.

The standard Button profile uses a 48dp effective target with a centered 40dp visible container for
compact and medium buttons, and a 56dp target with a 48dp visible container for large buttons. This
is a token choice consumed through UI Foundation's design-system-neutral sizing contract; the
Material adapter does not participate in Android hit testing or View drawing.

Checkbox, RadioButton, Switch, and Slider use a 48dp minimum effective height. Their native
indicator, thumb, track, and label geometry remains centered and platform-rendered; an explicit
exact application height or tighter parent constraint still wins. This policy is expressed through
UI Foundation's neutral control-sizing token rather than a Material branch in Android Renderer.

The Android bridge replaces available values from the active theme. It reads all 15 Material text
appearances and the five absolute `shapeAppearanceCorner*` roles, while legacy Android
large/medium/small text appearances remain title/body/label family fallbacks. Missing display and
headline values retain the complete static Material snapshot instead of being collapsed onto a
legacy size or falling back to UI Foundation's neutral defaults.

The adapter does not add Material policy to Android Renderer. Component defaults resolve semantic
roles in UI Foundation before a NodeSpec reaches the renderer. Button visual/effective height
separation is explicitly represented by the sizing tokens and NodeSpec. The native compact-input
target policy is likewise consumed by UI Foundation; composite Chip target/surface separation,
TextField floating-label/focus structure, and exact Switch/Slider visual geometry are not implied
by the token bridge and require separate tested component work.

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
The current alpha line also adds complete shape and typography roles and a public static Material 3
fallback; consumers that exhaustively construct or destructure affected UI Foundation data classes
must update with the corresponding alpha release.
