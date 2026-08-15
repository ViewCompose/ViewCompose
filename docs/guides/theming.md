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
6. `interactions`
7. `overlays`
8. `metadata`

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
3. `typography` exposes the complete 15-role `display*/headline*/title*/body*/label*` scale.
4. `shapes` exposes semantic `extraSmall / small / medium / large / extraLarge / full` tiers. Each
   absolute shape represents four corners, rounded/cut families, and absolute or percentage
   dimensions; `full` expresses a bounds-relative pill or circle.
5. `controls` is a framework-owned sizing family and does not promise one-to-one alignment with the
   Android theme system.
6. `interactions` stores design-system-neutral pressed, focused, and hovered state-layer opacities.
   Component defaults combine those opacities with their own enabled content role before emitting
   resolved colors; renderers do not interpret theme roles or opacity policy.
7. `overlays` stores cross-component modal configuration as semantic tokens.
8. `metadata` records token origin, dark state, and configuration revision for lifecycle refresh and
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
3. reserved error containers: `errorContainer / onErrorContainer` until a component has a
   semantically correct container-error treatment;
4. inverse and overlay colors: `inversePrimary / scrim / surfaceTint`;
5. application semantics: `success / warning / info`.

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
6. When a component separates its effective target from its visual surface, the theme supplies both
   dimensions, Defaults resolve them into `NodeSpec`, and the renderer only applies the resolved
   geometry. Explicit application surface modifiers remain authoritative.
7. Button, IconButton, bounded interactive composites, and SegmentedControl state layers follow the
   same path: component Defaults select the semantic content role, `interactions` supplies state
   opacity, `NodeSpec` carries resolved ARGB colors, and the renderer applies the generic
   pressed-before-focused-before-hovered selector. SegmentedControl carries separate selected and
   unselected role sets.

## 4. Local override rules

Local customization has two distinct scopes:

1. `UiThemeOverride` changes semantic theme tokens for a subtree.
2. A component-owned `XxxOverrides` changes low-frequency appearance slots for one component
   family without expanding its primary DSL signature.

Component overrides are sparse and merge field by field. Resolution order is instance overrides,
nearest matching provider, outer matching providers, component Defaults or a named design-system
recipe, then semantic theme tokens. An unspecified inner field preserves the outer value. The
complete contract is [ADR-0013](../architecture/decisions/0013-component-appearance-resolution-boundary.md).

The activated families are Button/IconButton, TextField, independent input controls, linear and
circular progress, SegmentedControl, TabRow, NavigationBar, regular and extended FAB, top and
bottom app bars, Badge, AlertDialog, and modal bottom sheet. App-bar providers also define the
default content role of their slots. Modal-bottom-sheet overrides are resolved before request
submission, so a same-key platform sheet receives theme/configuration changes without reopening
its logical overlay session.

Scaffold and raw Dialog are deliberate exclusions. Their direct parameters describe primary page
surface/layout or overlay lifecycle/placement, and their visual content remains caller-owned.

Appropriate uses:

1. one action with a different border or interaction layer;
2. a subtree with a local component accent, shape, typography, or visual dimension;
3. state-specific contrast or readability corrections that do not belong in the design-system
   recipe.

Non-goals:

1. moving controlled state, callbacks, identity, keyboard behavior, navigation, lifecycle, or
   resource ownership into an appearance object;
2. passing sparse overrides to `Basic*` primitives, which consume complete resolved
   `BasicXxxStyle` values;
3. creating one Foundation-wide component recipe registry.

### 4.1 Application-defined Locals

When an application token system differs from the framework theme:

1. define application tokens in the application module with `uiLocalOf { ... }`;
2. inject them into a subtree through `ProvideLocal(...)` or `ProvideLocals(...)`;
3. read them inside a component through `UiLocals.current(...)`;
4. use a typed component override only when the value is appearance owned by that component.

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
3. The bridge produces semantic tokens only. Material-named components derive private typed
   recipes from that snapshot; generic Foundation components and Renderer never branch on
   Material identity.
4. The `viewcompose-material3-android` `ComponentActivity/Fragment.setMaterial3UiContent` entry
   points resolve and provide Material 3 explicitly. The root container, framework native Views,
   `AndroidView`, and overlays share the same resolved context.
5. `setMaterial3UiContent` applies Material dynamic color when supported unless
   `Material3DynamicColorPolicy.Disabled` is selected. A direct low-level composition uses
   `Material3ThemeBridge.resolveContext(...)` and `Material3Theme(...)` from `viewcompose-material3`.
6. The neutral Android host observes configuration changes. An Android-backed Material theme
   consumes `Environment.resourceRevision`, refreshes its stable wrapper, rereads tokens, and does
   not register a parallel standard-host callback.
7. After an application-owned locale/theme wrapper mutation that emits no configuration callback,
   pass an `AndroidResourceRefreshController` to `setMaterial3UiContent` and call `refresh()` on the
   main thread. `Material3ThemeRefreshController` remains a low-level compatibility path for custom
   hosts without the standard resource environment.

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
   - these bridge values remain general state roles. Enabled Checkbox, RadioButton, Switch, and
     Slider defaults resolve selection from `colors.primary`; Slider resolves its inactive segment
     from `colors.secondaryContainer`. This prevents an AppCompat accent alias from mixing with a
     Material semantic palette while preserving explicit access to `controlActivated`.
3. `typography`
   - bridged: all 15 Material 3 `textAppearanceDisplay*/Headline*/Title*/Body*/Label*` roles;
   - family fallback: legacy Android `textAppearanceLarge/Medium/Small` applies only to
     title/body/label roles and does not collapse display or headline roles;
   - fields: `fontSizeSp / fontWeight / fontFamily / letterSpacingEm / lineHeightSp / includeFontPadding`.
4. `shapes`
   - bridged: `shapeAppearanceCornerExtraSmall / Small / Medium / Large / ExtraLarge`;
   - bridged: independent corners, `rounded/cut` families, and dimension/fraction corner sizes;
   - physical Android left/right values map to framework logical start/end using current layout
     direction.
5. `overlays`
   - bridged: `android:backgroundDimAmount -> scrimOpacity`.
6. `controls`
   - Android themes provide no uniform sources corresponding to `compact / medium / large`;
   - `Material3ThemeDefaults` therefore supplies the pinned standard sizing profile, and bridge
     results retain it while replacing resource-backed colors, typography, and shapes;
   - the Material 3 profile also selects a 48dp minimum effective height for Checkbox,
     RadioButton, Switch, and Slider. UI Foundation consumes the neutral token before caller
     modifiers, so explicit exact application sizing remains authoritative.
7. `interactions`
   - `Material3ThemeDefaults` supplies `0.10` pressed, `0.10` focused, and `0.08` hovered opacity;
   - the Android bridge retains that fallback policy while replacing resource-backed semantic
     colors, because Android themes do not expose one uniform component state-layer opacity family.

The bridge does not:

1. define application component defaults or generic component policy;
2. introduce component-specific branches in the token mapper or generic Renderer;
3. guess control-size mappings merely to appear complete.

Named component and provenance contract:

1. `Material3Theme` provides one token snapshot, the private `material3-pressure-v1` recipe set,
   and `UiDesignSystemAttribution` from the same synchronous scope.
2. `Material3Surface`, `Material3Card`, `Material3Button`, `Material3Switch`,
   `Material3TextField`, and `Material3NavigationBar` are the bounded first-party pressure slice.
   Their APIs are Material-owned; their execution uses neutral Basic primitives, retained native
   behavior, or a neutral custom View without adding Material branches below the boundary.
3. `UiThemeMetadata.provenance.sourceId` identifies `android-xml`, `android-dynamic`, or the named
   static producer. Mapped Android values report Android origin, static fallbacks report
   `FrameworkDefault`, and `UiThemeOverride` marks only replaced token families as `Override`.
4. `DesignSystemDiagnostics.current` reports design-system identity, recipe-set identity, backend,
   conformance, capability path, and fallback evidence. It is diagnostic data, not a recipe
   registry.
5. The Settings theme matrix renders intentionally different Android XML, static Material, and
   application-override palettes and shapes. Screenshot tests read the production provenance and
   attribution values and use separate identity, component, and navigation anchors.

Implementation constraints:

1. Material bridge fallback resolves explicitly to `Material3ThemeDefaults.light/dark()`; do not
   borrow UI Foundation's neutral fallback or scatter literals.
2. A new bridged field defines its source, fallback rule, and owning token.
3. A bridge change that affects visible output adds `Material3ThemeBridgeTest` or a Material 3 bridge
   test.

Active refresh example:

```kotlin
val themeRefreshController = Material3ThemeRefreshController()

setMaterial3UiContent(themeRefreshController = themeRefreshController) {
    // content
}

setTheme(R.style.AppTheme_Alternate)
themeRefreshController.refresh()
```

### 5.1 Application-owned mode across Activity roots

Each `setUiContent` or `setMaterial3UiContent` Activity owns an independent root `RenderSession`.
Those sessions do not share remembered values, but they may observe the same application-owned
`MutableState` or equivalent observable store. Put the user's Light/Dark/System choice in that
application state, read it directly from every Activity root, and derive each root's tokens from the
observed value. A change made in a secondary Activity then invalidates the first Activity's separate
session without either Activity owning or addressing the other session.

Keep ownership boundaries explicit:

1. Theme preference and persistence are application policy, not a framework singleton.
2. System mode resolves configuration from each root Context; explicit modes may share one
   deterministic token producer.
3. `Context.setTheme` or `applyStyle` mutates that Context's resources. It does not replace the
   application theme source or notify unrelated Activity sessions; refresh or recreate that host
   when imperative Android resources change.
4. A nested `NavHost` captures the latest inherited theme environment. Hidden retained destinations
   are refreshed with that environment before pop, stack selection/history, predictive Back, or
   pane expansion makes them visible.

## 6. One UI 7 alpha design-system boundary

`viewcompose-oneui7` is an explicit alternative design-system artifact rather than a replacement
for the standard Material aggregate. It provides static light/dark `UiThemeTokens` plus recipes and
owned composites for the bounded five-component alpha set.

```kotlin
setUiContent {
    OneUi7Theme(tokens = OneUi7ThemeDefaults.light()) {
        OneUi7Button(text = "Continue", onClick = { continueFlow() })
    }
}
```

The boundary is intentionally different from the Material bridge:

1. `OneUi7ThemeDefaults` does not read Android or Samsung resources. Its values are ViewCompose
   interpretations of pinned public One UI 7 guidance.
2. `OneUi7Theme` installs one coherent immutable foundation-token and private recipe snapshot.
3. Button and Surface resolve through shared Basic primitives. Switch, TextField decoration, and
   text-only NavigationBar remain design-system-owned composites where structure differs.
4. Android Renderer receives only resolved generic nodes and never tests a One UI identity.
5. Runtime switching replaces the root/session with a new provider; it does not mutate a global
   design-system object.
6. The neutral `viewcompose-android` host installs no design system. Applications opt into
   `viewcompose-oneui7` explicitly without inheriting a Material root Context.
7. The static snapshot reports `viewcompose-oneui7/static` plus `FrameworkDefault` provenance.
   `DesignSystemDiagnostics.current` exports the same five-family recipe/backend/conformance
   attribution used by screenshot evidence.

See the [One UI 7 five-component alpha module manual](../modules/viewcompose-oneui7/README.md) for
the supported component set, conformance labels, fallbacks, and release limitations.

## 7. Boundary with components and Modifier

1. Theme provides defaults.
2. Component parameters express component semantics.
3. `Modifier` applies general outer decoration.

See [Modifier architecture](../architecture/modifier.md) and the
[NodeSpec-only specification](../architecture/node-spec.md).

## 8. Checklist for adding theme capability

Adding a theme field or override capability requires:

1. model ownership: `tokens`, `defaults`, or component parameter;
2. precedence between defaults and explicit parameters;
3. renderer verification that a style change produces the expected patch or rebind;
4. Light/Dark and local-override Demo coverage;
5. at least one unit or instrumentation regression path.

The authoritative design-token acceptance path is `Settings -> Theme and token verification`,
then the Android XML, Material static, or application-override fixture. `Diagnostics -> Theme
diagnostics` remains the broad token explorer. The theme, override, and typography pages under
`Foundations` remain teaching examples and are not the final regression contract.

## 9. Current priorities

1. Keep the theme model stable and do not return to complete per-component token precomputation.
2. Dynamic color, complete 15-role typography, complete absolute shape mapping, and configuration
   lifecycle are implemented; expand the multi-window and vendor-theme device matrix.
3. Button and native compact-input touch targets now use test-backed effective-size policies.
   Button, IconButton, Chip, FAB variants, clickable Surface/Card/ListItem/DropdownMenuItem, and
   SegmentedControl resolve standard pressed, focused, and hovered layers from component content
   roles without introducing Material policy in Android Renderer. Chip touch-target expansion,
   TextField floating/focus behavior, navigation controls with explicit ripple overrides, and exact
   native input geometry remain test-first work; a complete token bridge alone does not provide
   those structural behaviors.
4. Keep theme regression aligned with overlay, input, and container scenarios in the
   [roadmap](../project/roadmap.md).

The ordered evidence and rollback policy for the remaining structural work is recorded in the
[Material 3 design convergence plan](../project/plans/material3-design-convergence.md).
