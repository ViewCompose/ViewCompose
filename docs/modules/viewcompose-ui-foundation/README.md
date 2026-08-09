# UI Foundation

`viewcompose-ui-foundation` is the Android-facing declarative UI layer of ViewCompose. It provides
the `UiTreeBuilder` DSL, themed component defaults, composition locals and environment propagation,
composition-scoped effects and saveable state, lazy-container scopes, overlay declarations, and the
renderer-independent session coordinator that connects a declarative tree to host-installed
container, engine, focus, scheduling, logging, and tracing contracts.

Use it directly when authoring reusable ViewCompose components, custom hosts, design-system
adapters, or overlay backends. Android applications normally receive it through the neutral
`viewcompose-android` aggregate or the named `viewcompose-material3-android` aggregate.

This module does not implement View reconciliation, own Activity or Fragment lifecycle, present
platform dialogs and popups, perform image decoding, or provide optional animation, gesture, graphics,
shadow, navigation, or ConstraintLayout features. Those responsibilities remain in their dedicated
modules.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-ui-foundation:0.1.0-alpha01")
}
```

- Stability: **Alpha**. Source and binary compatibility may change between alpha releases.
- Platform: Android library, `minSdk 24`, `compileSdk 36`, and Java 11 bytecode.
- Runtime, text core, and UI contract are exposed transitively because their state, editing,
  modifier, unit, node, and environment types form the public widget surface.
- Kotlin coroutines is exposed because `CoroutineScope` appears in composition-effect APIs.
- The production artifact has no AndroidX or Material Components dependency. Its public package is
  `com.viewcompose.ui.foundation`; the retired `com.viewcompose.widget.core` package is not retained.
- Android-only declarative values may remain because ViewCompose targets Android View, but native
  `ViewGroup` access, Context environment extraction, focus adaptation, logging, and tracing belong
  to Android Engine.
- Build baseline for this release: Kotlin 2.0.21 and Android Gradle Plugin 8.13.2.

## Minimal component usage

```kotlin
fun UiTreeBuilder.ProfileSummary(name: String, role: String) {
    UiTheme {
        Column(spacing = 8.dp) {
            Text(name, style = TextDefaults.titleMediumStyle())
            Text(role, color = TextDefaults.secondaryColor())
        }
    }
}
```

`UiTreeBuilder` records immutable VNodes. `UiTheme` provides a complete token snapshot and every
emitted node captures the active theme, density, locale, layout direction, and other locals needed
by a later renderer or child render session.

## Principal APIs

- [`UiTreeBuilder`](https://docs.viewcompose.com/api/viewcompose-ui-foundation/0.1.0-alpha01/viewcompose-ui-foundation/com.viewcompose.ui.foundation/-ui-tree-builder/)
  and its component functions build declarative node trees without creating Android Views.
- [`Theme` and `UiTheme`](https://docs.viewcompose.com/api/viewcompose-ui-foundation/0.1.0-alpha01/viewcompose-ui-foundation/com.viewcompose.ui.foundation/-theme/)
  expose immutable color, typography, shape, sizing, interaction, and overlay tokens without
  choosing a design system. Typography supports all display, headline, title, body, and label
  tiers; shapes support extra-small, small, medium, large, extra-large, and full roles.
  `UiInteractionTokens` supplies generic pressed, focused, and hovered opacities. Design-system
  adapters provide their concrete values.
- `UiTokenProvenance` is the Q2 non-visual source snapshot attached to `UiThemeMetadata`. Exact
  paths such as `colors.primary` inherit a family source when no exact entry exists, allowing
  diagnostics to distinguish framework defaults, Android theme or dynamic mapping, named static
  tokens, and application overrides without changing visual resolution.
- `UiDesignSystemAttribution` and `UiComponentAttribution` are bounded Q2 evidence snapshots, not a
  recipe registry. `DesignSystemAttributionProvider` is the Q3 scope used by named systems to
  publish recipe identity, neutral backend, conformance, capability path, and fallback;
  `DesignSystemDiagnostics.current` reads that same local in eager or captured delayed content.
- `UiButtonSizing` keeps the effective minimum target height separate from the visible surface
  height. Neutral and existing custom themes preserve their previous rendering because each visual
  height defaults to its corresponding effective height; a design-system adapter may opt into a
  smaller centered surface without shrinking the View or accessibility bounds.
- `BasicSurface` is a Q3 design-system-neutral primitive. Its Q2 `BasicSurfaceStyle` accepts a
  resolved solid or gradient brush, logical shape, border, clipping, elevation, and exact shadows;
  it also separates minimum effective bounds from an optional centered visual height. Design
  systems select those values before emission, while the Android Renderer receives only a neutral
  `SurfaceNodeProps` snapshot. The compiled `basicSurfaceSample` demonstrates the contract.
- `BasicButton` is a Q3 action composite over `BasicSurface`, Row, Text, and Icon. Its Q2
  `BasicButtonStyle` contains only resolved geometry, typography, content, and interaction values.
  It emits no native Button node, while the existing `Button` API keeps that compatibility path.
  The compiled `basicButtonSample` demonstrates a continuous-corner action.
- `UiControlSizing.minimumInteractiveHeight` is the design-system-neutral effective-height policy
  used by Checkbox, RadioButton, Switch, and Slider. Its neutral default is zero, preserving native
  intrinsic measurement. A design system may supply a positive minimum; the component applies it
  before the caller modifier so an explicit exact application height remains authoritative.
- Enabled Checkbox, RadioButton, Switch, and Slider defaults resolve their selected or active
  control role from `Theme.colors.primary`. Slider additionally resolves its inactive segment from
  `Theme.colors.secondaryContainer`. AppCompat `controlActivated` remains available as a general
  state token but does not override these component semantic roles.
- Button and IconButton defaults combine their enabled semantic content role with
  `UiInteractionTokens` and emit resolved pressed, focused, and hovered colors. Callers can replace
  the complete set through `stateLayerColors`; Button's explicit legacy `rippleColor` overload
  intentionally retains one color for every active state.
- Chip, FAB, extended FAB, clickable Surface, Card, ListItem, and DropdownMenuItem use the same
  content-role resolution through internal Box/Row NodeSpec fields. SegmentedControl resolves
  independent selected and unselected sets so switching selection also switches the interaction
  role. Passive and disabled composites keep a null multi-state contract.
- [`UiEnvironment`](https://docs.viewcompose.com/api/viewcompose-ui-foundation/0.1.0-alpha01/viewcompose-ui-foundation/com.viewcompose.ui.foundation/-environment/)
  and the local-provider APIs scope density, locales, layout direction, content color, text style,
  image loading, focus, frame clock, and host capabilities.
- `Image`, `Icon`, [`ProvideImageLoader`](https://docs.viewcompose.com/api/viewcompose-ui-foundation/current/com.viewcompose.ui.foundation/-provide-image-loader.html),
  and `UiImageRequestOptions` expose image semantics without selecting Coil, Glide, or another
  decoder. A subtree may install one `UiImageLoader` or leave it absent for resource-only rendering.
- [`remember`, `produceState`, and effects](https://docs.viewcompose.com/api/viewcompose-ui-foundation/0.1.0-alpha01/viewcompose-ui-foundation/com.viewcompose.ui.foundation/)
  integrate the platform-neutral composition runtime with structured coroutines and committed
  side effects.
- [`rememberSaveable` and `SaveableStateRegistry`](https://docs.viewcompose.com/api/viewcompose-ui-foundation/0.1.0-alpha01/viewcompose-ui-foundation/com.viewcompose.ui.foundation/-saveable-state-registry/)
  preserve state through composition disposal and host recreation with transactional restoration.
- [`RenderSession`](https://docs.viewcompose.com/api/viewcompose-ui-foundation/0.1.0-alpha01/viewcompose-ui-foundation/com.viewcompose.ui.foundation/-render-session/)
  coordinates composition, renderer reconciliation, native commit effects, overlays, diagnostics,
  failure recovery, and disposal for one opaque `RenderContainerHandle`. Standard applications use
  the public Host Android session returned by `renderInto` rather than constructing this coordinator.
- Overlay specifications and hosts define platform-neutral dialog, popup, bottom-sheet, snackbar,
  and toast identity, placement, queueing, update, and dismissal contracts.

The complete generated reference is available under the
[`viewcompose-ui-foundation` API tree](https://docs.viewcompose.com/api/viewcompose-ui-foundation/current/).
Because the current line is alpha, the documentation site intentionally does not expose a stable
`latest` alias.

## State, rendering, and lifecycle rules

- A `UiTreeBuilder` is an ephemeral recorder. Do not retain it or invoke a captured builder after
  its content block returns. Retain state and stable keys instead.
- `remember` and effects require an active composition. Positional identity follows the structural
  call path; use stable `key` groups and lazy-item keys when content can move.
- `rememberSaveable` registers providers only after composition commit. A failed or abandoned
  composition releases its restored claim so a later attempt can still restore the value.
- `UiTheme` accepts platform-independent tokens. Material Android resource resolution belongs to
  `viewcompose-material3`, including configuration observation and explicit refresh.
- Existing three-family typography construction remains concise: omitted headline roles derive
  from title roles and omitted display roles derive from headlines. Existing three-tier shape
  construction also remains valid: omitted extra-small/extra-large roles derive from small/large,
  and full defaults to a bounds-relative pill. These are compatibility fallbacks, not Material
  values; Material applications receive the concrete scale from `viewcompose-material3`.
- Each `RenderSession` exclusively owns one container, its mounted nodes, composition, coroutine
  scope, and session-scoped overlays. Call `dispose` with the host lifecycle; the session cannot be
  reused afterward.
- Composition preparation and tree-render failures preserve the previous frame. Failures after a
  renderer has established the new native tree are reported as committed-frame failures and cannot
  roll that tree back.
- Overlay requests are declarative and scoped by render-session id plus request key. Omitting a
  previously committed request dismisses it. Platform presentation requires
  `viewcompose-overlay-material3-android` or a custom `OverlayHost`.
- Lazy collection keys must remain stable and unique. Reuse, prefetch, and motion policies are
  renderer hints; they must not be used as business state.
- Image components keep source identity and request options in the `NodeSpec`. A loader is looked up
  while emitting the node, so changing the provider is an explicit render input. The renderer
  replaces the previous operation before starting the next one and disposes it when the node or
  session leaves the mounted tree. A resource can therefore use an installed loader's decoding and
  transform behavior; a null source selects the node fallback without starting that loader.

Building a VNode tree is thread-confined to its active composition context. Standard Android hosts
serialize rendering, state callbacks, effects, and platform operations on the main thread. Custom
hosts must preserve the same ordering and ownership guarantees.

## Related documentation

- [Current architecture and module boundaries](../../architecture/overview.md)
- [State and snapshot architecture](../../architecture/state-snapshots.md)
- [Node specifications and renderer registration](../../architecture/node-spec.md)
- [Lazy collection guide](../../guides/lazy-collections.md)
- [Theme and Android integration](../../guides/theming.md)
- [Image loading guide](../../guides/image-loading.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

## Compatibility notes

The `0.1.0-alpha01` line establishes the renamed UI-foundation coordinate, the
`com.viewcompose.ui.foundation` package root, and its design-system-neutral theme boundary. It
retains the public widget, local, saveable-state, overlay, and render-session contracts without the
retired package alias. Do not persist automatic saveable keys, session identifiers, VNode
implementation names, callback instances, tooling metadata, or diagnostics shapes as external
long-lived data. Custom renderers and hosts must be upgraded with contract changes even when an
application's component source still compiles.

The complete `UiTypography` and `UiShapes` value contracts are an alpha-line source and binary
change. They remain immutable Q2 values with no lifecycle or ownership protocol; direct
construction keeps source defaults, while exhaustive destructuring, reflection, and precompiled
callers must be rebuilt for the corresponding release.

`UiButtonSizing` is likewise a Q2 immutable value contract. Its added visual-height fields have
source defaults but are a binary change for precompiled direct constructors and exhaustive
destructuring. `Button` resolves both heights into `ButtonNodeProps`; custom renderers must honor
that contract or deliberately document that their visual and effective bounds remain identical.

`UiControlSizing.minimumInteractiveHeight` is another Q2 immutable value field with a source
default and the same binary-compatibility consequence for precompiled direct constructors and
exhaustive destructuring. Checkbox, RadioButton, Switch, and Slider are Q3 component APIs: they
prepend the resolved minimum target and then apply the caller modifier, preserving explicit exact
layout decisions.

Slider's added `inactiveTrackColor` parameter is a Q3 component API change. It has a themed source
default and is resolved into the Q2 `SliderNodeProps` snapshot; precompiled callers and custom
renderers must be rebuilt for the corresponding alpha release.

`UiInteractionTokens` is a Q2 immutable theme value, and its addition to `UiThemeTokens` is a
binary change for precompiled constructors and exhaustive destructuring. Button and IconButton
state-layer parameters are Q3 component API changes. Source callers receive semantic multi-state
defaults, and Button callers that explicitly supplied the former `rippleColor` retain a dedicated
compatibility overload; precompiled default-argument call sites must be rebuilt for this alpha
release.

`BasicSurfaceStyle` is a Q2 resolved-value contract and `BasicSurface` is a Q3 component API.
`BasicSurface` appends caller modifiers after its resolved style and behavior: caller surface
modifiers replace the default visual surface, caller elevation wins, and caller shadows follow
the style shadows. `Surface` now resolves its existing defaults through this primitive, preserving
its public source API while changing the concrete `NodeType.Surface` spec to `SurfaceNodeProps`.

`BasicButtonStyle` is a Q2 resolved-value contract and `BasicButton` is a Q3 composite API. It is
additive and does not change the existing `Button` signature or native renderer behavior. The
internal contrast fixture now consumes this production primitive, proving two independent action
recipes without adding design-system vocabulary to UI Foundation.

`UiTokenProvenance`, `UiDesignSystemAttribution`, and `UiComponentAttribution` are Q2 immutable
diagnostic contracts. `DesignSystemAttributionProvider` is a Q3 provider API. Adding provenance to
`UiThemeMetadata` has a source default but changes the binary constructor/copy/component surface,
so precompiled direct callers must rebuild. The contracts contain stable identities and resolved
evidence only; they do not authorize recipes, factories, or named design-system branches in UI
Foundation or Renderer.
