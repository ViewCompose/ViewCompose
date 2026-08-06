# ViewCompose Theming

## 1. Scope

This document is the current theme-system specification. It defines:

1. the theme-model boundary;
2. the default-value resolution path;
3. local override rules;
4. constraints for adding theme capabilities.

For historical detail, see
[THEMING_FULL_2026-03-06.md](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/THEMING_FULL_2026-03-06.md).

## 2. Current theme model

The core fields of `UiThemeTokens` are:

1. `colors`
2. `stateColors`
3. `typography`
4. `shapes`
5. `controls`
6. `overlays`
7. `metadata`

Core principles:

1. The top-level theme contains semantic tokens, not a complete resolved style for every component.
2. Component defaults derive values lazily from `Theme` in the `Defaults` layer instead of
   precomputing every style.
3. An explicit component parameter takes precedence over a theme default.

Current token semantics:

1. `colors` includes base colors, `on*` foregrounds, `*Container` colors, outlines, inverse
   surfaces, and ripple.
2. `stateColors` defines default/disabled/pressed/focused/checked/selected values for text, ordinary
   controls, activated controls, and interaction highlights.
3. `typography` exposes only tiered `title*/body*/label*` values as canonical entries.
4. `shapes` exposes only semantic `small / medium / large` tiers. Each shape represents four
   corners, rounded/cut families, and absolute or percentage dimensions.
5. `controls` is a framework-owned sizing family and does not promise one-to-one alignment with the
   Android theme system.
6. `overlays` stores cross-component modal configuration as semantic tokens.
7. `metadata` records token origin, dark state, and configuration revision for lifecycle refresh and
   diagnostics; it does not derive component defaults.

### 2.1 Canonical semantic entries

Theme evolution uses a canonical semantic entry and one-time convergence:

1. Once a new field becomes canonical, defaults and Demo content migrate in the same change.
2. If an old field is only a historical alias, remove it during convergence instead of maintaining
   it indefinitely.
3. Documentation distinguishes canonical semantic fields from reserved tokens.
4. New default logic reads canonical semantic fields only and cannot reintroduce aliases.

### 2.2 Hard-coded color prohibition

Do not write these semantic colors as literals in `Defaults`; resolve them from `Theme.colors`:

1. Error states, including values such as `0xFFB3261E`, use `Theme.colors.error`.
2. Badges and alerts use `Theme.colors.error` or another semantic color instead of a private
   component constant.
3. Foreground content on a semantic color is derived with `contentColorFor(semanticColor)` instead
   of hard-coded black or white.

### 2.3 Token-consumption closure

A public token cannot remain defined but unused indefinitely. It must either:

1. be consumed explicitly by at least one core default or composite default; or
2. be allowlisted with a documented reason.

The allowlist currently contains only reserved semantic palette values without a direct core
component consumer:

1. extended surfaces: `onBackground / surfaceDim / surfaceBright / surfaceContainer*`;
2. tertiary emphasis: `tertiary / onTertiary / tertiaryContainer / onTertiaryContainer`;
3. inverse and overlay colors: `inversePrimary / scrim / surfaceTint`;
4. application semantics: `success / warning / info`.

These are not forced onto existing core components merely to increase usage counts.

`ThemeTokenUsageAuditTest` prevents regression:

1. A new token fails the test when it is neither consumed nor allowlisted.
2. A default that returns from a semantic token to a removed alias is exposed by the audit.

## 3. Default-value path

The standard path is:

`Theme -> Defaults -> NodeSpec -> Renderer`

Constraints:

1. Do not convert theme values directly into general-purpose modifiers.
2. Do not put application semantic defaults in the renderer.
3. Do not duplicate theme derivation throughout the DSL layer.
4. Text inside a composite writes its complete resolved style into `NodeSpec`; sending only
   `textSizeSp` is insufficient.
5. The renderer applies the text style already resolved in `NodeSpec` and does not recreate theme
   semantics.

## 4. Local override rules

Local overrides remain supported, but they are sparse:

1. Override only required fields.
2. Unspecified fields fall back to the parent theme or default.
3. Overrides propagate through `LocalContext` scope.
4. Public usage goes through `UiLocal/uiLocalOf/ProvideLocal(s)/UiLocals.current` to prevent
   specialized wrapper APIs from proliferating.

Appropriate uses:

1. a local brand or accent color;
2. a local typography adjustment;
3. contrast or readability improvements within one region.

Non-goals:

1. a complete per-component field matrix in every override;
2. replacing explicit component parameters with overrides.

### 4.1 Application-defined Locals

When an application token system differs from the framework theme:

1. define application tokens in the application module with `uiLocalOf { ... }`;
2. inject them into a subtree through `ProvideLocal(...)` or `ProvideLocals(...)`;
3. read them inside a component through `UiLocals.current(...)`;
4. prefer the unified APIs and do not add a new specialized `ProvideXxx` wrapper.

Boundary constraints:

1. An application Local contains semantic values, not renderer platform details.
2. Local scope restoration and snapshot propagation must remain correct through Lazy and overlay
   sessions.

## 5. Material 3 design-system boundary

`viewcompose-material3` maps Android Material/AppCompat semantics to framework semantics through:

`Material3ThemeSnapshotReader -> Material3ThemeTokenMapper -> UiThemeTokens`

Responsibilities:

1. `SnapshotReader` reads Android, AppCompat, and Material theme fields in batches.
2. `ThemeTokenMapper` maps platform fields to framework tokens and applies fallback rules.
3. The bridge does not produce component defaults or bypass `Defaults`.
4. The `viewcompose-android` `ComponentActivity/Fragment.setUiContent` entry points resolve and
   provide the Material 3 theme by default. The root container, framework native Views,
   `AndroidView`, and overlays share the same resolved context.
5. `setUiContent` applies Material dynamic color when supported unless
   `Material3DynamicColorPolicy.Disabled` is selected. A direct low-level composition uses
   `Material3ThemeBridge.resolveContext(...)` and `Material3Theme(...)` from `viewcompose-material3`.
6. An Android-backed theme used in composition observes configuration changes and rereads tokens;
   callbacks are removed when it leaves composition, and `metadata.revision` increments on refresh.
7. After runtime `setTheme/applyStyle`, pass a `Material3ThemeRefreshController` to `setUiContent`
   and call `refresh()` on the main thread. The controller resolves the dynamic-color context again
   and refreshes the themed subtree.

Current bridge matrix:

1. `colors`
   - bridged: `background / onBackground / surface / surfaceVariant / primary / secondary / tertiary / error`;
   - bridged: `surfaceDim / surfaceBright / surfaceContainerLowest/Low/Container/High/Highest`;
   - bridged: `onPrimary / onSecondary / onTertiary / onError`;
   - bridged: `primaryContainer / secondaryContainer / tertiaryContainer / errorContainer`;
   - bridged: `onPrimaryContainer / onSecondaryContainer / onTertiaryContainer / onErrorContainer`;
   - bridged: `outline / outlineVariant / inverseSurface / inverseOnSurface / inversePrimary`;
   - bridged: `onSurface / onSurfaceVariant`;
   - bridged: `ripple`, preferring `colorControlHighlight`;
   - `surfaceTint` falls back to the Material 3 `primary` role and no longer incorrectly borrows
     AppCompat `colorAccent`.
2. `stateColors`
   - bridged: `android:textColorPrimary / textColorSecondary`;
   - bridged: AppCompat `colorControlNormal / colorControlActivated / colorControlHighlight`;
   - standard states: `disabled / pressed / focused / checked / selected`.
3. `typography`
   - bridged: Material 3 `textAppearanceTitle*/Body*/Label*`;
   - fallback: legacy Android `textAppearanceLarge/Medium/Small`;
   - fields: `fontSizeSp / fontWeight / fontFamily / letterSpacingEm / lineHeightSp / includeFontPadding`.
4. `shapes`
   - bridged: `shapeAppearanceSmallComponent / Medium / Large`;
   - bridged: independent corners, `rounded/cut` families, and dimension/fraction corner sizes;
   - physical Android left/right values map to framework logical start/end using current layout
     direction.
5. `overlays`
   - bridged: `android:backgroundDimAmount -> scrimOpacity`.
6. `controls`
   - no theme-level bridge; values continue to use framework defaults;
   - Android themes provide no uniform sources corresponding to `compact / medium / large`.

The bridge does not:

1. define application component defaults;
2. introduce component-specific branches;
3. guess control-size mappings merely to appear complete.

Implementation constraints:

1. Fallback resolves explicitly to `UiThemeDefaults.light/dark()`; do not scatter literals.
2. A new bridged field defines its source, fallback rule, and owning token.
3. A bridge change that affects visible output adds `Material3ThemeBridgeTest` or a Material 3 bridge
   test.

Active refresh example:

```kotlin
val themeRefreshController = Material3ThemeRefreshController()

setUiContent(themeRefreshController = themeRefreshController) {
    // content
}

setTheme(R.style.AppTheme_Alternate)
themeRefreshController.refresh()
```

## 6. Boundary with components and Modifier

1. Theme provides defaults.
2. Component parameters express component semantics.
3. `Modifier` applies general outer decoration.

See [Modifier architecture](../architecture/modifier.md) and the
[NodeSpec-only specification](../architecture/node-spec.md).

## 7. Checklist for adding theme capability

Adding a theme field or override capability requires:

1. model ownership: `tokens`, `defaults`, or component parameter;
2. precedence between defaults and explicit parameters;
3. renderer verification that a style change produces the expected patch or rebind;
4. Light/Dark and local-override Demo coverage;
5. at least one unit or instrumentation regression path.

The authoritative manual verification path is `Diagnostics -> Theme diagnostics`. The theme,
override, and typography pages under `Foundations` remain teaching examples and are not the final
regression contract.

## 8. Current priorities

1. Keep the theme model stable and do not return to complete per-component token precomputation.
2. Dynamic color, complete shape mapping, and configuration lifecycle are implemented; expand the
   multi-window and vendor-theme device matrix.
3. Keep theme regression aligned with overlay, input, and container scenarios in the
   [roadmap](../project/roadmap.md).
