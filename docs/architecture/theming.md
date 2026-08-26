---
schema_version: 2
document_id: architecture.theme-runtime
doc_type: architecture
owner:
  kind: capability
  id: theme.foundation
version_lane: released
capability_ids:
  - theme.foundation
  - theme.material3
artifact_ids:
  - viewcompose-ui-foundation
  - viewcompose-material3
  - viewcompose-material3-android
sample_ids:
  - tutorial.theming
  - guide.theming-local-override
invariants:
  - One immutable UiThemeTokens snapshot is authoritative for each synchronous subtree build.
  - Component defaults resolve semantic tokens before emission; Renderer never interprets a design-system identity.
  - Explicit instance appearance wins over component scope, theme scope, and framework defaults in that order.
  - Android resource changes replace or revise the host-owned theme snapshot without introducing a global theme singleton.
evidence:
  - viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/theme/ThemeTest.kt
  - viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/theme/ThemeRecipeBoundaryGuardTest.kt
  - viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/theme/ThemeTokenUsageAuditTest.kt
  - viewcompose-material3/src/test/java/com/viewcompose/material3/Material3ThemeBridgeTest.kt
  - viewcompose-material3/src/test/java/com/viewcompose/material3/Material3ThemeLifecycleTest.kt
  - viewcompose-material3-android/src/test/java/com/viewcompose/material3/android/Material3AndroidHostIntegrationTest.kt
---

# Theme runtime architecture

## 1. Ownership boundary

UI Foundation owns the design-system-neutral theme scope. `UiTheme` installs one immutable
`UiThemeTokens` snapshot, `Theme` reads that snapshot during tree construction, and
`UiThemeOverride` derives a nested snapshot without mutating its parent. The theme scope owns
semantic defaults only; controlled state, callbacks, identity, navigation, lifecycle, and Android
resource ownership remain outside it.

Design-system modules convert their inputs into Foundation tokens and private component recipes.
`viewcompose-material3` maps Android Material/AppCompat resources or static Material values;
`viewcompose-oneui7` supplies its own static tokens and recipes. Neither identity is visible to
Android Renderer. The complete dependency, version, fallback, and component-conformance matrices
remain in the owning [Material 3](../modules/viewcompose-material3/README.md) and
[One UI 7](../modules/viewcompose-oneui7/README.md) module manuals.

## 2. Theme snapshot model

`UiThemeTokens` contains eight families:

| Family | Ownership |
| --- | --- |
| `colors` | Semantic surface, content, emphasis, status, inverse, and container roles. |
| `stateColors` | Resolved enabled, disabled, pressed, focused, checked, and selected roles. |
| `typography` | The 15 semantic display, headline, title, body, and label roles. |
| `shapes` | Logical extra-small through extra-large and full shape tiers. |
| `controls` | Design-system-neutral effective and visual sizing policy. |
| `interactions` | Pressed, focused, and hovered state-layer opacities. |
| `overlays` | Cross-component modal and scrim policy. |
| `metadata` | Origin, dark state, revision, and provenance used for refresh and diagnostics. |

The top-level snapshot deliberately does not precompute a complete style for every component.
Component defaults derive only the values they need. New semantic entries become canonical in one
change: defaults and demonstrations move to the new entry, historical aliases are removed, and
`ThemeTokenUsageAuditTest` proves that every non-reserved token has a consumer. Reserved palette
roles remain explicitly allowlisted rather than being forced onto an unrelated component.

## 3. Resolution and precedence

The standard data path is:

`Theme snapshot -> component Defaults or named recipe -> NodeSpec -> Renderer`

The precedence path is:

1. explicit instance appearance;
2. nearest component-owned `XxxOverrides` provider;
3. outer matching component providers;
4. named design-system recipe or Foundation component Defaults;
5. current semantic theme snapshot;
6. framework neutral fallback when no provider exists.

`UiThemeOverride` replaces complete token families for one subtree. Its transforming overload reads
each current family once, applies the supplied transformation, and installs the merged immutable
result. Replacing colors without replacing `stateColors` re-derives state roles from the new color
scheme. The resulting provenance marks only the replaced families as application overrides.

Component-owned overrides are sparse and merge field by field; they do not become a second theme
model. Basic primitives consume complete resolved styles and therefore do not accept sparse
overrides. Application-specific token systems that do not belong in `UiThemeTokens` use an
application-owned `uiLocalOf` and `ProvideLocal`, not a renderer branch or a Foundation-wide recipe
registry.

## 4. Renderer and component boundary

Semantic values are resolved before a node reaches the rendering engine. Text emits its complete
resolved style. Interactive components combine their semantic content role with interaction
opacity and emit resolved ARGB state colors. Effective touch bounds and visible surface geometry
are separate resolved values where the component requires both. Renderer applies those snapshots;
it does not recreate theme semantics, inspect Material or One UI identities, or read Android theme
attributes.

This boundary keeps a neutral renderer usable without Material Components and prevents a theme
adapter from adding branches to reconciliation, measurement, drawing, or input behavior. A named
design system may own a composite structure when exact visual behavior requires it, but its output
still crosses the shared neutral NodeSpec, Basic primitive, or native behavioral-core boundary.

## 5. Android resource and refresh lifecycle

The Material Android host resolves one stable themed Context for the root container, native
descendants, `AndroidView`, and overlays, then provides tokens derived from that same Context.
Dynamic color is a context-resolution policy, not a mutation of a global palette.

The neutral Android host owns configuration observation and publishes
`Environment.resourceRevision`. An Android-backed `Material3Theme` consumes that revision,
refreshes its stable wrapper, and maps a new immutable snapshot. An application that imperatively
changes locale, theme, or resource overlays without a configuration callback explicitly refreshes
its host-owned `AndroidResourceRefreshController`. The low-level
`Material3ThemeRefreshController` is reserved for custom hosts that do not install the standard
resource environment.

Every Activity root owns an independent render session. Multiple roots can observe the same
application preference state, but they never share a remembered theme provider. System mode is
resolved from each root Context; explicit light or dark modes may select the same deterministic
token producer.

## 6. Diagnostics and evidence

`UiThemeMetadata.provenance` identifies static defaults, Android XML, dynamic color, and local
overrides. `DesignSystemDiagnostics.current` adds recipe-set identity, component backend,
conformance, capability path, and fallback evidence from the same scope. Diagnostics describe the
active resolution; they do not select recipes.

The deterministic suite covers provider restoration and nesting, color/state-color rebasing,
token consumption, recipe-boundary isolation, Material fallback mapping, configuration revision,
and Android host/context agreement. Run:

```bash
./gradlew :viewcompose-ui-foundation:test :viewcompose-material3:test \
  :viewcompose-material3-android:testDebugUnitTest
```

Then use the focused guides to verify [application mode switching](../guides/theming.md),
[dynamic color and Android refresh](../guides/theming-dynamic-color.md), and
[local subtree overrides](../guides/theming-local-overrides.md). The project
[roadmap](../project/roadmap.md) owns future device-matrix and component-conformance work; this
architecture page does not carry temporary delivery priorities.
