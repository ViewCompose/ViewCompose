# UI Foundation

`viewcompose-ui-foundation` is the Android-facing declarative UI layer of ViewCompose. It provides
the `UiTreeBuilder` DSL, themed component defaults, composition locals and environment propagation,
composition-scoped effects and saveable state, lazy-container scopes, overlay declarations, and the
renderer-independent session coordinator that connects a declarative tree to host-installed
container, engine, focus, scheduling, logging, and tracing contracts.

Use it directly when authoring reusable ViewCompose components, custom hosts, design-system
adapters, or overlay backends. Android applications normally receive it through
`viewcompose-android`, which supplies the standard engine and Material 3 adapter.

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
  expose immutable color, typography, shape, sizing, and overlay tokens without choosing a design
  system.
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
