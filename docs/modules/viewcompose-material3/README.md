---
schema_version: 2
document_id: module.viewcompose-material3
doc_type: module
owner:
  kind: module
  id: viewcompose-material3
version_lane: released
capability_ids:
  - material3.components
  - theme.material3
artifact_ids:
  - viewcompose-material3
sample_ids:
  - module.material3-dependency
  - module.material3-theme
  - module.material3-components
coordinate: com.viewcompose:viewcompose-material3:0.1.0-alpha01
minimal_usage_sample_id: module.material3-theme
---

# Material 3 Theme Adapter

`viewcompose-material3` is the design-system layer for Google Material 3 on Android. It reads
Material theme colors, typography, and shapes into platform-independent `UiThemeTokens`, resolves
dynamic-color contexts, and refreshes tokens after configuration or imperative theme changes.

It also owns a bounded Material pressure slice for Surface/Card, Button, Switch, TextField, and
NavigationBar. Those APIs resolve named recipes into shared Basic primitives, native behavioral
cores, or neutral renderer nodes; this module does not participate in View reconciliation or map a
generic node to a Material Components widget. The Android engine therefore remains usable without
Material Components; only this module and explicitly Material-backed integrations own that
dependency.

## Artifact and stability

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="material3-module-dependency" sample_id="module.material3-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
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
root View and overlays. `Material3Theme` provides the mapped token snapshot and consumes the
design-system-neutral `Environment.resourceRevision`; standard-host configuration observation is
owned by `viewcompose-host-android`, not Material. `Material3ResolvedTheme.refresh()` refreshes its
stable wrapper before token mapping. `Material3ThemeRefreshController` remains available only for
low-level hosts that have not installed the standard Android resource environment.

{/* compiled-region source="viewcompose-material3/src/test/samples/com/viewcompose/material3/samples/Material3ThemeSamples.kt" region="material3-module-theme" sample_id="module.material3-theme" build_target=":viewcompose-material3:compileDebugUnitTestKotlin" */}
```kotlin
val resolvedTheme = Material3ThemeBridge.resolveContext(context)
Material3Theme(resolvedTheme = resolvedTheme) {
    Text("Content using Material 3 theme tokens")
}
```

Material applications receive this lifecycle automatically through the named
`viewcompose-material3-android` integration. Lower-level integrations may continue to resolve the
Context and install `Material3Theme` explicitly, but must supply configuration/resource
invalidation at their host boundary.

`Material3Theme(tokens = ...)` provides the same recipe and diagnostic scope from static tokens
without reading Android resources. Both overloads export `Material3Reference.recipeSet` plus the
same five-family backend/conformance attribution through `DesignSystemDiagnostics`.

## Public component pressure slice

| Entry point | Recipe/backend boundary | Current conformance |
| --- | --- | --- |
| `Material3Surface`, `Material3Card` | Material recipes resolved into shared `BasicSurface` | Exact |
| `Material3Button` | Material variant recipe resolved into shared `BasicButton` | Exact |
| `Material3Switch` | Material colors/type over the native Android Switch behavioral core | Equivalent |
| `Material3TextField` | Material decoration around the native Android editing core | Equivalent |
| `Material3NavigationBar` | Material selection recipe over the neutral navigation renderer | Equivalent |

The complete compiled pressure-slice example is
[`Material3ThemeSamples.kt`](../../../viewcompose-material3/src/test/samples/com/viewcompose/material3/samples/Material3ThemeSamples.kt).

The Material and One UI public vocabularies intentionally remain different. They share neutral
execution and diagnostic contracts only; no union component API or global recipe bundle is
introduced.

## Token baseline and fallback

`Material3ThemeDefaults.light()` and `Material3ThemeDefaults.dark()` provide deterministic Material
3 snapshots when no Android themed Context is available or an individual Android attribute is
missing. Each snapshot includes:

- the complete Material color scheme used by the adapter, including surface-container, inverse,
  outline, and container-content roles;
- all 15 standard display, headline, title, body, and label typography roles;
- extra-small, small, medium, large, extra-large, and full shape roles; and
- the selected standard sizing profile for buttons, text fields, segmented controls, progress
  indicators, FABs, search, badges, and native compact-input effective targets; and
- standard interaction opacities: `0.10` pressed, `0.10` focused, and `0.08` hovered.

The standard Button profile uses a 48dp effective target with a centered 40dp visible container for
compact and medium buttons, and a 56dp target with a 48dp visible container for large buttons. This
is a token choice consumed through UI Foundation's design-system-neutral sizing contract; the
Material adapter does not participate in Android hit testing or View drawing.

Button and IconButton Defaults combine those interaction opacities with each variant's enabled
content role before emitting a NodeSpec. For example, a primary Button uses `onPrimary`, while a
tonal Button uses `onSecondaryContainer`. The adapter does not produce selectors and Android
Renderer remains unaware of Material role names.

Checkbox, RadioButton, Switch, and Slider use a 48dp minimum effective height. Their native
indicator, thumb, track, and label geometry remains centered and platform-rendered; an explicit
exact application height or tighter parent constraint still wins. This policy is expressed through
UI Foundation's neutral control-sizing token rather than a Material branch in Android Renderer.

Their enabled selection color is the Material `primary` role resolved by UI Foundation rather than
the AppCompat `colorControlActivated` bridge value. Slider uses `secondaryContainer` for its
inactive segment. The bridge continues to expose legacy state colors for applications that request
them explicitly, but they do not replace these component semantic roles.

The Android bridge replaces available values from the active theme. It reads all 15 Material text
appearances and the five absolute `shapeAppearanceCorner*` roles, while legacy Android
large/medium/small text appearances remain title/body/label family fallbacks. Missing display and
headline values retain the complete static Material snapshot instead of being collapsed onto a
legacy size or falling back to UI Foundation's neutral defaults.

`UiThemeMetadata.provenance` records `viewcompose-material3/android-xml`,
`viewcompose-material3/android-dynamic`, or `viewcompose-material3/static` as the base producer.
For the pressure slice, every consumed color, state-color, type, shape, control, interaction, and
overlay path can resolve its effective origin. A present Android attribute is marked Android theme
or dynamic; a missing value remains a named static Material fallback with `FrameworkDefault`
origin; `UiThemeOverride` marks only the token families replaced by the application. The complete
static snapshot also reports `FrameworkDefault`, rather than misclassifying first-party defaults
as application-owned custom values.

`UiDesignSystemAttribution.integrations` records the overlay transport and per-type presenters.
Material Dialog/Popup content uses captured Material locals, Snackbar and modal bottom sheet report
their Material Components adapters, and Android Toast is an explicit degraded platform fallback.

The adapter does not add Material policy to Android Renderer. Component defaults resolve semantic
roles in UI Foundation before a NodeSpec reaches the renderer. Button visual/effective height
separation is explicitly represented by the sizing tokens and NodeSpec. The native compact-input
target policy is likewise consumed by UI Foundation; composite Chip target/surface separation,
TextField floating-label/focus structure and exact Switch/Slider visual geometry are not implied by
the token bridge. The current named TextField and Switch deliberately retain native behavioral
cores and report Equivalent conformance. Material3Switch preserves native tap and thumb-drag
handling, and accepted caller state does not restart the platform's in-flight thumb transition;
further visual replacement requires the Phase 12 behavior and accessibility gate.

## Related documentation

- [Theme runtime architecture](../../architecture/theming.md)
- [Material 3 dynamic-color guide](../../guides/theming-dynamic-color.md)
- [UI foundation](../viewcompose-ui-foundation/README.md)
- [Material 3 Android integration](../viewcompose-material3-android/README.md)
- [Neutral Android aggregate](../viewcompose-android/README.md)
- [Five-layer architecture](../../architecture/decisions/0002-five-layer-runtime-module-architecture.md)

The generated reference is available in the
[`viewcompose-material3` API tree](https://docs.viewcompose.com/api/viewcompose-material3/current/).

## Compatibility notes

This artifact begins at `0.1.0-alpha01`. Android theme bridge types previously shipped from the UI
foundation were renamed to the `Material3*` API family and moved here without compatibility aliases.
The current alpha line also adds complete shape and typography roles and a public static Material 3
fallback; consumers that exhaustively construct or destructure affected UI Foundation data classes
must update with the corresponding alpha release.

The standard interaction-opacity profile is retained across Android theme mapping because the
platform theme does not expose one complete per-state opacity family. Applications can replace the
generic `UiInteractionTokens` or a component's resolved `stateLayerColors` without depending on
Material APIs.

The bridge no longer republishes Android `colorControlHighlight` through the removed
`UiColors.ripple` or `UiStateColors.controlHighlight` slots. Interaction feedback is resolved from
the Material opacity recipe plus each component's semantic content role; an application that needs
a different policy supplies `UiInteractionTokens` explicitly.

The static `Material3Theme` overload and the six named pressure-slice entry points are additive Q3
APIs. Their enums and `Material3Reference` are Q2 identity/value contracts. They expose no Material
widget type and do not change generic UI Foundation component signatures.
