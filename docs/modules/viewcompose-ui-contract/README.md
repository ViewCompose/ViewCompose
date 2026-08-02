# UI Contract

`viewcompose-ui-contract` defines the platform-neutral model shared by ViewCompose DSL modules and
renderers: immutable virtual nodes and node specifications, ordered modifiers, environment values,
layout units, interaction contracts, and renderer-connected state for lazy collections and pagers.

Use it directly when building a custom renderer, host bridge, tooling integration, or reusable API
that exposes ViewCompose contract types. Application UI normally receives it transitively through
`viewcompose-widget-core`.

This module does not compose a DSL tree, create Android `View` instances, reconcile nodes, schedule
frames, or integrate Android lifecycle and saved state. Those responsibilities belong to the
runtime, widget, renderer, and host modules.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-ui-contract:0.1.0-alpha01")
}
```

- Stability: **Alpha**. Source and binary compatibility may change between alpha releases.
- Platform: Kotlin/JVM, compiled with the Java 11 toolchain; no Android SDK or AndroidX dependency.
- Direct ViewCompose dependencies: `viewcompose-text-core` as an API dependency, plus
  `viewcompose-runtime` and `viewcompose-graphics-core` as implementation dependencies.
- Transitively exposed contract family: the platform-neutral text document and editing model from
  `viewcompose-text-core`.
- Build baseline for this release: Kotlin 2.0.21.

## Minimal contract usage

```kotlin
val gap = VNode(
    type = NodeType.Spacer,
    key = "content-gap",
    spec = EmptyNodeSpec,
    modifier = Modifier
        .size(24.dp)
        .testTag("content-gap"),
)
```

This creates a renderer-neutral semantic node. A compatible renderer resolves `NodeType.Spacer`,
validates its `EmptyNodeSpec`, interprets the modifier chain in order, and owns any native object
created for the node.

## Principal APIs

- [`VNode` and `NodeType`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha01/viewcompose-ui-contract/com.viewcompose.ui.node/-v-node/)
  define immutable tree content and renderer dispatch.
- [`NodeSpec`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha01/viewcompose-ui-contract/com.viewcompose.ui.node.spec/-node-spec/)
  and its concrete property snapshots define the supported renderer inputs.
- [`Modifier`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha01/viewcompose-ui-contract/com.viewcompose.ui.modifier/-modifier/)
  carries ordered layout, drawing, interaction, semantics, focus, and parent-data elements.
- [`UiEnvironmentValues`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha01/viewcompose-ui-contract/com.viewcompose.ui.environment/-ui-environment-values/)
  captures density, locale tags, and logical layout direction for a subtree.
- [`LazyListState`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha01/viewcompose-ui-contract/com.viewcompose.ui.state/-lazy-list-state/)
  and pager state bridge platform scrolling to observable runtime state.
- [`FocusRequester`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha01/viewcompose-ui-contract/com.viewcompose.ui.focus/-focus-requester/)
  and [`NestedScrollDispatcher`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha01/viewcompose-ui-contract/com.viewcompose.ui.gesture/-nested-scroll-dispatcher/)
  define explicit renderer attachment boundaries for focus and nested scrolling.
- The unit, shape, graphics, key-input, gesture, semantics, and tooling packages complete the
  platform-neutral vocabulary used across ViewCompose modules.

The complete generated reference is available under the
[`viewcompose-ui-contract` API tree](https://docs.viewcompose.com/api/viewcompose-ui-contract/current/).
Because the current line is alpha, the documentation site intentionally does not expose a stable
`latest` alias.

## Contract and lifecycle rules

- `VNode.type` and `VNode.spec` are a registry-level pair. Construction is intentionally cheap and
  does not validate compatibility; a renderer must reject an unsupported pair deterministically.
- A node specification is an immutable render snapshot. Callbacks may capture mutable application
  state, but the spec itself must not be used as a native-object owner.
- Modifier order is semantic. Layout and parent-data collection, visual decoration, input,
  semantics, and drawing phases consume the ordered elements according to their documented phase
  rules; reordering elements may change behavior.
- `UiEnvironmentValues` is captured on every VNode subtree. A renderer must use the captured values
  instead of consulting unrelated process-global density, locale, or direction state.
- `LazyListState`, pager state, focus requesters, and nested-scroll dispatchers attach to one current
  renderer connector. Hosts must detach old connectors during replacement or disposal.
- State and connector commands are thread-confined to the owning renderer thread. Android
  integrations use the main thread, and callbacks run synchronously unless a concrete contract says
  otherwise.
- `AndroidViewNodeProps.update` and `onReset` are replay-safe transaction callbacks. External
  one-shot work belongs in `onCommit`; resource cleanup belongs in `onRelease`.

Collection prefetch, native cache sizing, motion, and shared-pool values are renderer hints rather
than semantic state. A platform may clamp or ignore an unsupported optimization without changing
the declared content.

## Related documentation

- [Node specifications and renderer registration](../../architecture/node-spec.md)
- [Modifier architecture](../../architecture/modifier.md)
- [Current architecture and module boundaries](../../architecture/overview.md)
- [Lazy collection guide](../../guides/lazy-collections.md)
- [Focus and input guide](../../guides/focus-and-input.md)
- [Nested scrolling guide](../../guides/nested-scroll.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

## Compatibility notes

The `0.1.0-alpha01` line establishes the first public renderer contract. Adding a `NodeType`, a
concrete `NodeSpec`, or a modifier element can require a renderer update even when application DSL
source remains unchanged. Custom renderers should fail clearly for unknown contracts and should not
persist enum ordinals, sealed-subtype names, tooling metadata, native view identities, or callback
instances as long-lived external data.
