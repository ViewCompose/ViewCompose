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
  and its component functions build declarative node trees without creating Android Views. Its Q3
  low-level `emit` boundary treats child-content closure identity as a recomposition input; the
  compiled `emittedContentClosureSample` demonstrates direct custom-node construction.
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
- `UiDesignSystemAttribution`, `UiComponentAttribution`, and `UiIntegrationAttribution` are bounded Q2 evidence snapshots, not a
  recipe registry. `DesignSystemAttributionProvider` is the Q3 scope used by named systems to
  publish recipe identity, neutral backend, integration transport/presenter, conformance,
  capability path, and fallback;
  `DesignSystemDiagnostics.current` reads that same local in eager or captured delayed content.
- `UiButtonSizing` keeps the effective minimum target height separate from the visible surface
  height. Neutral and existing custom themes preserve their previous rendering because each visual
  height defaults to its corresponding effective height; a design-system adapter may opt into a
  smaller centered surface without shrinking the View or accessibility bounds.
- `UiSwitchSizing` is the Q2, design-system-neutral visible-geometry contract for a composed
  Switch track, thumb, track inset, and label spacing. It deliberately does not own the effective
  target; the compiled `switchSizingTokenSample` keeps a compact visual track inside an independent
  `minimumInteractiveHeight` policy.
- `BasicSurface` is a Q3 design-system-neutral primitive. Its Q2 `BasicSurfaceStyle` accepts a
  resolved solid or gradient brush, logical shape, border, clipping, elevation, exact shadows, and
  optional renderer-neutral interaction indication; it also separates minimum effective bounds
  from an optional centered visual height. Design systems select those values before emission,
  while Android Renderer receives only neutral NodeSpec and Modifier snapshots. The compiled
  `basicSurfaceSample` demonstrates the contract.
- `BasicButton` is a Q3 action composite over `BasicSurface`, Row, Text, and Icon. Its Q2
  `BasicButtonStyle` contains only resolved geometry, typography, content, and interaction values.
  It emits no native Button node, while the existing `Button` API keeps that compatibility path.
  The compiled `basicButtonSample` demonstrates a continuous-corner action.
- High-level components use Q2 sparse typed appearance values such as `ButtonOverrides`,
  `TextFieldOverrides`, and independent Checkbox, Switch, RadioButton, and Slider models. Q3
  `ProvideXxxOverrides` scopes merge nested values field by field; an instance patch wins over the
  merged scope. Behavior, state, callbacks, identity, and lifecycle stay explicit. The compiled
  `componentOverridesSample` demonstrates nesting and instance precedence.
- `BasicTextField` is a Q3 editing primitive whose Q2 `BasicTextFieldStyle` contains every resolved
  visual input. It performs no Theme or component-Local lookup. High-level `TextField` resolves
  semantic defaults and sparse overrides before constructing that complete style; named design
  systems construct it from their private recipes. `TextFieldInputProfile` couples keyboard and
  autofill purpose, while `TextFieldLinePolicy` enforces single-line or validated multiline
  behavior. Separate password, email, number, and text-area wrappers are not parallel APIs.
- `UiControlSizing.minimumInteractiveHeight` is the design-system-neutral effective-height policy
  used by Checkbox, RadioButton, Switch, and Slider. Its neutral default is zero, preserving native
  intrinsic measurement. A design system may supply a positive minimum; the component applies it
  before the caller modifier so an explicit exact application height remains authoritative.
- Enabled Checkbox, RadioButton, Switch, and Slider defaults resolve their selected or active
  control role from `Theme.colors.primary`. Slider additionally resolves its inactive segment from
  `Theme.colors.secondaryContainer`. AppCompat `controlActivated` remains available as a general
  state token but does not override these component semantic roles.
- Button and IconButton defaults combine their enabled semantic content role with
  `UiInteractionTokens` and emit resolved pressed, focused, and hovered colors. A caller replaces
  the complete set through the component's typed `stateLayerColors` override slot; the retired
  direct `rippleColor` and `stateLayerColors` parameters are not parallel precedence paths.
- Chip, FAB, extended FAB, clickable Surface, Card, ListItem, and DropdownMenuItem use the same
  content-role resolution and install `UiInteractionIndication.StateLayer` through an ordered
  Modifier. Box and Row remain pure layout primitives. SegmentedControl and NavigationBar retain
  independent selected and unselected sets because their native backends own multiple internal
  targets. Passive and disabled composites install no indication.
- [`UiEnvironment`](https://docs.viewcompose.com/api/viewcompose-ui-foundation/0.1.0-alpha01/viewcompose-ui-foundation/com.viewcompose.ui.foundation/-environment/)
  and the local-provider APIs scope density, locales, layout direction, content color, text style,
  image loading, focus, frame clock, and host capabilities. `UiLocals.current` is a Q2 scoped lookup:
  an absent binding evaluates its default, while an explicitly provided `null` for a nullable Local
  remains `null` through nesting, batch providers, snapshots, and delayed child sessions. Each
  provider boundary installs one immutable internal snapshot; repeated captures in the same scope
  reuse that identity, and `ProvideLocals` installs its complete batch atomically. Public
  `UiLocalSnapshot` wrappers remain opaque and independently allocated.
- `Image`, `Icon`, [`ProvideImageLoader`](https://docs.viewcompose.com/api/viewcompose-ui-foundation/current/com.viewcompose.ui.foundation/-provide-image-loader.html),
  and `UiImageRequestOptions` expose image semantics without selecting Coil, Glide, or another
  decoder. A subtree may install one `UiImageLoader` or leave it absent for resource-only rendering.
- [`remember`, `produceState`, and effects](https://docs.viewcompose.com/api/viewcompose-ui-foundation/0.1.0-alpha01/viewcompose-ui-foundation/com.viewcompose.ui.foundation/)
  integrate the platform-neutral composition runtime with structured coroutines and committed
  side effects. `DisposableEffect` and `LaunchedEffect` require keys; disposable setup terminates
  in `onDispose`. Unkeyed `SideEffect` runs after every successful invocation, while keyed overloads
  publish only on initial commit and structural key change.
- `CompositionEffectContext` is the Q3 low-level bridge for optional integration modules that
  implement additional synchronous or coroutine effect primitives. It marks callbacks so any Local
  read fails instead of consuming a default or unrelated provider, but never captures or restores a
  provider stack; ordinary application code uses the standard effect APIs.
- [`rememberSaveable` and `SaveableStateRegistry`](https://docs.viewcompose.com/api/viewcompose-ui-foundation/0.1.0-alpha01/viewcompose-ui-foundation/com.viewcompose.ui.foundation/-saveable-state-registry/)
  preserve state through composition disposal and host recreation with transactional restoration.
- `LazyColumn`, `LazyRow`, `LazyVerticalGrid`, and pager page declarations use an explicit Q3
  revision contract. Bulk overloads accept `contentRevision = { model.version }`; ordinary captured
  values must be observed State or participate in that revision. Pager pages also declare
  `contentType`. `TabRow` uses eager keyed children in the parent composition rather than lazy item
  sessions.
- `ScrollableColumn` and `ScrollableRow` accept Q3 `ScrollState` plus `userScrollEnabled` without
  unmounting eager children. `HorizontalPager` and `VerticalPager` accept Q3 `PagerState`; their
  change callback fires only after a different page settles. The compiled `eagerScrollStateSample`
  demonstrates caller-owned eager scrolling.
- `LazyVerticalGrid` accepts `GridCells.Fixed` or `GridCells.Adaptive`, and grid items declare
  `GridItemSpan.Single`, `Fixed`, or `FullLine`. Adaptive resizing changes the native column count,
  not logical item identity. The compiled `adaptiveGridSample` covers full-line content.
- Slider stepping and start/change/finish callbacks, pull-to-refresh enablement, and explicit
  stable NavigationBar/SegmentedControl item keys are ordinary product behavior rather than
  Android interop. The compiled `sliderInteractionSample`, `pullToRefreshEnablementSample`, and
  `stableSelectionItemIdentitySample` define their public use. A non-empty selection control
  requires an in-range selected index; an empty control uses `-1`, duplicate keys fail, and
  navigation badge counts cannot be negative.
- [`RenderSession`](https://docs.viewcompose.com/api/viewcompose-ui-foundation/0.1.0-alpha01/viewcompose-ui-foundation/com.viewcompose.ui.foundation/-render-session/)
  coordinates composition, renderer reconciliation, native commit effects, overlays, diagnostics,
  failure recovery, and disposal for one opaque `RenderContainerHandle`. Standard applications use
  the public Host Android session returned by `renderInto` rather than constructing this coordinator.
- `RenderSessionSourceTooling` and `RenderSessionSourceRegistration` form a Q3 optional platform
  diagnostics contract. They capture one bounded source chain only when the platform opts in and
  track the active/disposed lifetime of root, lazy-item, and pager-item render sessions. The
  compiled `renderSessionSourceToolingSample` demonstrates the adapter lifecycle.
- Overlay specifications and hosts define platform-neutral dialog, popup, bottom-sheet, snackbar,
  and toast identity, placement, queueing, update, and dismissal contracts.

The complete generated reference is available under the
[`viewcompose-ui-foundation` API tree](https://docs.viewcompose.com/api/viewcompose-ui-foundation/current/).
Because the current line is alpha, the documentation site intentionally does not expose a stable
`latest` alias.

## Component appearance hard cut

The alpha API no longer retains parallel color-only and direct low-frequency appearance paths:

| Previous call | Replacement |
| --- | --- |
| `ButtonColorOverride` and `ProvideButtonColors` | `ButtonOverrides` and `ProvideButtonOverrides` |
| `TextFieldColorOverride` and `ProvideTextFieldColors` | `TextFieldOverrides` and `ProvideTextFieldOverrides` |
| shared `InputControlColorOverride` | independent Checkbox, Switch, RadioButton, and Slider overrides |
| `ProgressIndicatorColorOverride` | independent linear and circular progress overrides |
| direct Button, input, progress, TabRow, or NavigationBar appearance parameters | the component's `overrides` argument |
| direct FAB, app-bar, or Badge appearance parameters | independent regular/extended FAB, top/bottom app-bar, or Badge `overrides` |
| AlertDialog visual constants | `AlertDialogOverrides` or `ProvideAlertDialogOverrides` |
| modal-bottom-sheet container/content/scrim/system-bar appearance | `ModalBottomSheetOverrides`, resolved into `ModalBottomSheetAppearance` before submission |
| individual `BasicTextField` appearance arguments | one complete `BasicTextFieldStyle` |
| `PasswordField`, `EmailField`, `NumberField`, or `TextArea` | `TextField(inputProfile = ..., linePolicy = ...)` |
| `TextButton` | `Button(variant = ButtonVariant.Text)` |
| `ElevatedCard` or `OutlinedCard` | `Card(variant = CardVariant.Elevated/Outlined)` |

Nested providers now merge instead of replacing an outer patch. Instance values have the highest
precedence. A component-family field is variant-agnostic unless its name declares a specific
state; apply it at the instance when only one variant should differ. Callers that need to restore a
semantic value under a broader provider pass that resolved value explicitly.

TopAppBar supplies independent navigation/action content roles and BottomAppBar supplies its row
content role; nested IconButtons inherit that role unless their instance override replaces it.
Regular/extended FAB and top/bottom app-bar types remain separate so irrelevant geometry cannot be
silently ignored. Scaffold and raw Dialog have no appearance override family: they retain direct
page-surface or overlay-lifecycle inputs and caller-owned content.

ModalBottomSheet is the overlay-specific case. Foundation resolves container/content colors,
shape, scrim opacity, and exact-versus-platform-default navigation-bar policy into one immutable
`ModalBottomSheetAppearance`. The overlay spec compares that snapshot so a same-key request updates
its presenter without replacing logical identity or captured saveable state.

## State, rendering, and lifecycle rules

`UiEnvironment` now transports `resourceRevision`; `Environment.resourceRevision` exposes the
current immutable value while building content. Local snapshots preserve it for lazy items,
pagers, overlays, and navigation destinations. Android resource resolution and observation remain
outside UI Foundation in `viewcompose-host-android`; no named design system owns this neutral Local.

Local binding presence is independent of value nullability. Defaults run only when the current
snapshot has no entry for that Local; they are not a fallback for an explicitly provided `null`.

- A `UiTreeBuilder` is an ephemeral recorder. Do not retain it or invoke a captured builder after
  its content block returns. Retain state and stable keys instead.
- ViewCompose has no compiler transform that can infer every ordinary captured Kotlin value. A
  newly installed emitted-content closure therefore rebuilds that group even when the node spec is
  value-equal; only the exact retained closure may reuse a clean child result. This favors correct
  captured values and child-session callbacks over an unsafe value-equality subtree skip.
- Collection item snapshots, rather than callback object identities, delimit logical child
  submissions. Equal key plus content/environment revisions perform no child composition or native
  patch. A non-State capture that can change must enter `contentRevision`; omitting it promises the
  capture remains stable for that key.
- Eager scroll and pager state objects attach only while their native container is mounted. A
  replacement state detaches the old owner, disposal rejects later commands at the renderer
  boundary, and equal snapshots do not invalidate observers. Horizontal eager offsets and pager
  indexes remain logical in RTL.
- NavigationBar and SegmentedControl keys identify logical items independently from translated
  labels. Disabled items remain present for ordering and accessibility but do not dispatch
  selection callbacks. Duplicate keys, out-of-range selected indexes, and negative navigation
  badge counts fail during tree construction.
- Lazy item and pager child revisions advance only when their `activate` or `render` attempt reports
  a committed frame. Composition or native-tree rollback retains the logical session and retries
  the same semantic revision; failures after frame commit remain observable without undoing it.
- `remember` and effects require an active composition. Positional identity follows the structural
  call path. Stable ordinary `key` groups move complete logical scopes among siblings; duplicate
  effective keys fail before state can alias. Lazy containers keep their separate item-session key
  contract.
- Candidate effect changes are transactional. A failed composition or native tree render starts no
  candidate work, retains committed subscriptions and jobs, and discards candidate
  `rememberUpdatedState` publication. After native success, committed-value publication and all
  outgoing lifecycle callbacks precede incoming lifecycle callbacks, then `SideEffect`, native
  `AndroidView.onCommit`, overlay, and diagnostics work.
- `DisposableEffect` setup and cleanup are synchronous. A throwing setup owns no cleanup and remains
  pending for retry on a later successful composition commit; it must therefore be retry-safe. A
  successful setup activates once, and a throwing cleanup is never invoked again. Independent
  lifecycle callbacks are still attempted.
- `LaunchedEffect` inherits the render session coroutine context and requires explicit restart
  identity. `rememberCoroutineScope` is for event callbacks and owns a normal child Job. Passing a
  context containing a Job returns a failed scope rather than detaching work from composition.
- Effect callbacks resolve and capture `Theme`, environment, lifecycle, and host capability values
  while declaring the effect. Provider stacks are not implicitly restored around callbacks;
  built-in effect scopes reject Local reads with a named diagnostic even if another provider is
  active on the callback thread. Debug render sessions warn when synchronous callbacks exceed 16 ms.
- `rememberSaveable` registers providers only after composition commit. A failed provider
  registration preserves its restored claim in `performSave` and retries registration on a later
  commit. An aborted or abandoned candidate releases its uncommitted claim so a later owner can
  restore the value.
- Delayed child compositions do not share the host registry's flat provider-key namespace. Lazy,
  Pager, and overlay containers remember hierarchical child registries by logical key, retain them
  across recycling, and restore them without moving state across keyed reorders. Tab children use
  the parent composition's keyed saveable namespace. Concurrent visual replicas are non-owning and
  cannot overwrite the logical child's persisted state.
- A never-activated lazy child session may retain a prepared composition and already-built native
  tree for RecyclerView prefetch. It uses the same transaction as a normal frame, so remember
  activation, user effects, native commit callbacks, overlays, and diagnostics remain deferred
  until attachment. State invalidation abandons the stale candidate before activation; an active
  cached session keeps its lifecycle until recycle rather than treating viewport detach as stop.
- Recycling terminates the logical key session before a compatible mounted tree enters the bounded
  renderer-owned cache. Only resettable native trees cross keys; eviction releases native resources
  deterministically. RecyclerView pools empty holder shells, not logical state or mounted trees.
- `UiTheme` accepts platform-independent tokens. Android resource observation belongs to
  `viewcompose-host-android`; a named design system such as Material maps the resulting host
  revision into its own token refresh policy.
- Existing three-family typography construction remains concise: omitted headline roles derive
  from title roles and omitted display roles derive from headlines. Existing three-tier shape
  construction also remains valid: omitted extra-small/extra-large roles derive from small/large,
  and full defaults to a bounds-relative pill. These are compatibility fallbacks, not Material
  values; Material applications receive the concrete scale from `viewcompose-material3`.
- Each `RenderSession` exclusively owns one container, its mounted nodes, composition, coroutine
  scope, and session-scoped overlays. Call `dispose` with the host lifecycle. Disposal is
  idempotent; later caller-initiated `render` or `setRenderingActive` calls fail fast, while already
  queued internal invalidations and frame callbacks are ignored without publishing work.
- Source tooling is disabled by default. An installed adapter is consulted before initial tree
  construction, receives bounded source candidates from that successful build, is registered only
  after a native tree is established, updated by `setRenderingActive`, and disposed with the
  session. Candidate chains allow a platform to remove shared scaffold callers before navigation.
  Tooling failures are diagnostic-only and must not become an application rendering dependency.
- Composition preparation and tree-render failures preserve the previous frame. Failures after a
  renderer has established the new native tree are reported as committed-frame failures and cannot
  roll that tree back.
- Overlay requests are declarative and scoped by render-session id plus request key. Omitting a
  previously committed request dismisses it. Platform presentation requires
  `viewcompose-overlay-android`, a named adapter such as
  `viewcompose-overlay-material3-android`, or a custom `OverlayHost`.
- Lazy collection keys must remain stable and unique. `contentType` must group structurally
  compatible native trees, and `mountedTreeCacheSize` bounds reset physical presentations retained
  per container. Prefetch and motion policies remain renderer hints and must not be business state.
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
- [Transactional effects and structured work](../../architecture/effects.md)
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

Child-composition saveable ownership is a hard correction described by
[ADR-0010](../../architecture/decisions/0010-hierarchical-saveable-state-ownership.md). Historical
child values written through the defective flat registry namespace are not migrated because their
logical owner cannot be recovered safely. Root-composition saved keys and the Android host Bundle
format remain unchanged for explicit keys. Each delayed container holder occupies one automatic
saveable slot in its parent structural scope, so callers must not treat generated automatic keys as
a persistence compatibility surface.

The effect-runtime hard cut requires at least one key for `DisposableEffect` and `LaunchedEffect`.
Disposable setup now returns cleanup only through `DisposableEffectScope.onDispose`; migrate a
lambda-return cleanup by making `onDispose { ... }` the setup block's final expression. Keyed
`SideEffect` is an additive ViewCompose change-only publication form. Effect lifecycle, rollback,
coroutine ownership, and `rememberUpdatedState` publication now follow the transactional contract
in [Transactional effects and structured work](../../architecture/effects.md).

The native-widget contract hard cut replaces `LazyVerticalGrid(spanCount = ..., span = Int)` with
`cells = GridCells...` and `span = GridItemSpan...`. It also replaces the previous pager-state
shape, adds `state` and `userScrollEnabled` to eager scroll containers, adds slider step and
interaction-boundary callbacks, adds pull-to-refresh `enabled`, and requires stable navigation and
segmented item keys. These changes intentionally keep one source of truth; no deprecated parallel
signature is retained on the alpha line.

`RenderSessionPlatformDiagnostics.sourceTooling`, `RenderSessionSourceTooling`, and
`RenderSessionSourceRegistration` are additive Q3 tooling APIs. Existing platform diagnostics use
the default `null` adapter and retain their previous behavior. Opted-in custom platforms must keep
registration state bounded by its render session, consume the bounded candidate-chain list
synchronously, and perform callbacks on the platform render thread. Registration is passive: it
may retain a weak container reference but cannot install recurring scroll, global-layout, draw,
touch, frame, or recomposition observers. Live inspection requires an explicit tooling request as
defined by [ADR-0009](../../architecture/decisions/0009-development-tooling-isolation.md).

The complete `UiTypography` and `UiShapes` value contracts are an alpha-line source and binary
change. They remain immutable Q2 values with no lifecycle or ownership protocol; direct
construction keeps source defaults, while exhaustive destructuring, reflection, and precompiled
callers must be rebuilt for the corresponding release.

`UiButtonSizing` is likewise a Q2 immutable value contract. Its added visual-height fields have
source defaults but are a binary change for precompiled direct constructors and exhaustive
destructuring. `Button` resolves both heights into `ButtonNodeProps`; custom renderers must honor
that contract or deliberately document that their visual and effective bounds remain identical.

`UiSwitchSizing` is a Q2 immutable value contract added to `UiControlSizing` with a source default.
It is a binary change for precompiled direct constructors and exhaustive destructuring. Design
recipes consume the resolved geometry; the neutral Android Renderer receives no One UI or other
named design-system branch.

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
state-layer override slots are Q3 component API changes. Source callers receive semantic
multi-state defaults. The former `rippleColor` compatibility path and the unused
`UiColors.ripple`/`UiStateColors.controlHighlight` theme slots are removed. Custom interaction
surfaces use `Modifier.interactionIndication`; theme producers configure state-layer policy through
`UiInteractionTokens`, and precompiled default-argument call sites must be rebuilt for this alpha
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
