# UI Contract

`viewcompose-ui-contract` defines the platform-neutral model shared by ViewCompose DSL modules and
renderers: immutable virtual nodes and node specifications, ordered modifiers, environment values,
layout units, interaction contracts, and renderer-connected state for lazy collections and pagers.

Use it directly when building a custom renderer, host bridge, tooling integration, or reusable API
that exposes ViewCompose contract types. Application UI normally receives it transitively through
`viewcompose-ui-foundation`.

This module does not compose a DSL tree, create Android `View` instances, reconcile nodes, schedule
frames, or integrate Android lifecycle and saved state. Those responsibilities belong to the
runtime, widget, renderer, and host modules.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-ui-contract:0.1.0-alpha03")
}
```

- Stability: **Alpha**. Source and binary compatibility may change between alpha releases.
- Platform: Kotlin/JVM, compiled with the Java 11 toolchain; no Android SDK or AndroidX dependency.
- Transitively exposed contract families: platform-neutral text/editing from
  `viewcompose-text-core` and drawing models from `viewcompose-graphics-core`; both appear in public
  UI contract signatures.
- `viewcompose-runtime` remains an implementation dependency.
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

- [`VNode` and `NodeType`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha03/viewcompose-ui-contract/com.viewcompose.ui.node/-v-node/)
  define immutable tree content and renderer dispatch.
- [`NodeSpec`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha03/viewcompose-ui-contract/com.viewcompose.ui.node.spec/-node-spec/)
  and its concrete property snapshots define the supported renderer inputs.
- `TextNodeProps` carries one authoritative `TextDocument`; `ButtonNodeProps` and
  `ToggleNodeProps` carry nullable plain `String` labels. Mutable or platform-specific
  `CharSequence` implementations are converted only at a platform renderer boundary.
- [`Modifier`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha03/viewcompose-ui-contract/com.viewcompose.ui.modifier/-modifier/)
  carries ordered layout, drawing, interaction, semantics, focus, and parent-data elements.
- [`UiEnvironmentValues`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha03/viewcompose-ui-contract/com.viewcompose.ui.environment/-ui-environment-values/)
  captures density, locale tags, and logical layout direction for a subtree.
- [`LazyListState`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha03/viewcompose-ui-contract/com.viewcompose.ui.state/-lazy-list-state/)
  and pager state bridge platform scrolling to observable runtime state.
- [`FocusRequester`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha03/viewcompose-ui-contract/com.viewcompose.ui.focus/-focus-requester/)
  and [`NestedScrollDispatcher`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha03/viewcompose-ui-contract/com.viewcompose.ui.gesture/-nested-scroll-dispatcher/)
  define explicit renderer attachment boundaries for focus and nested scrolling.
- `UiStateLayerColors` carries already-resolved pressed, focused, and hovered ARGB values without
  embedding design-system roles or opacity policy in the renderer contract.
- `SemanticsCollectionInfo` and `SemanticsCollectionItemInfo` are Q3 immutable snapshots for a
  collection's logical dimensions, selection policy, and child positions. They let custom tabs,
  navigation, segmented controls, lists, and grids retain platform accessibility position
  announcements without embedding a design system in the renderer.
- `SurfaceNodeProps` is the Q2 resolved contract for `NodeType.Surface`. It carries a graphics-core
  brush, logical shape, border, state layers, effective minimum dimensions, optional centered
  visual height, and clipping policy without carrying a design-system identity.
- `UiNodeTooling.withFirstSourceCapture` is a Q3 synchronous tooling boundary that reports the
  nearest-first source chain for the first eligible node emitted by a block. Unlike full preview
  capture, it neither assigns node IDs nor retains metadata on the emitted tree.
- `UiNodeTooling.withSourceCandidateCapture` is the Q3 page-source counterpart. It retains bounded
  first and recent source chains across one successful tree build so tooling can distinguish shared
  scaffold chrome from content DSL without annotating the VNode tree.
- `UiSourceSessionContainerHandle` is a Q2 tooling-only renderer-container marker. Its `Host`,
  `Page`, and `Content` roles let source navigation treat pager destinations as page boundaries
  without allowing a deeper ordinary lazy row to replace its enclosing page.
- [`ImageSource`](https://docs.viewcompose.com/api/viewcompose-ui-contract/current/com.viewcompose.ui.node.media/-image-source/),
  [`UiImageRequest`](https://docs.viewcompose.com/api/viewcompose-ui-contract/current/com.viewcompose.ui.node.media/-ui-image-request/),
  and [`UiImageLoader`](https://docs.viewcompose.com/api/viewcompose-ui-contract/current/com.viewcompose.ui.node.media/-ui-image-loader/)
  define portable image sources, request policy, platform targets, and disposable load handles.
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
- Text content has one representation per specification. `TextNodeProps.document` owns both plain
  and rich content, while button and toggle labels are plain immutable strings. Android renderers
  may create a `Spannable` or another `CharSequence` immediately before native View binding, but
  must not retain that platform value in a VNode or use it for structural equality.
- `ButtonNodeProps.minHeight` is the effective minimum View and semantic target height, while
  `visualHeight` is the requested centered surface height. A renderer must clamp an invalid visual
  height to the effective bounds and must keep explicit application surface modifiers
  authoritative.
- `SurfaceNodeProps.minimumWidth` and `minimumHeight` define effective layout, input, focus, and
  semantic bounds. Its nullable `visualHeight` affects only fill, border, ripple, shape outline,
  and default clipping. Explicit caller surface modifiers remain authoritative and disable that
  visual inset. Solid and gradient brush coordinates are resolved in local surface pixels.
- Button, IconButton, Box, Row, and SegmentedControl state layers use
  pressed-before-focused-before-hovered precedence while an enabled target is active; inactive and
  disabled states are transparent. A null `stateLayerColors` preserves the legacy value-only
  `rippleColor` contract for direct emitters and older custom renderers. SegmentedControl carries
  separate selected and unselected sets because their semantic content roles differ.
- `SliderNodeProps.trackColor` is the active segment at or before the current value, while
  `inactiveTrackColor` is the remaining segment. Renderers must bind both resolved colors and must
  not recover either segment from a platform theme.
- Modifier order is semantic. Layout and parent-data collection, visual decoration, input,
  semantics, and drawing phases consume the ordered elements according to their documented phase
  rules; reordering elements may change behavior.
- Collection semantics use logical indexes. RTL may reverse physical placement but does not change
  row/column metadata or callback identity. A collection item derives heading and selected metadata
  from the same `SemanticsConfiguration` fields, avoiding duplicate state ownership.
- `UiEnvironmentValues` is captured on every VNode subtree. A renderer must use the captured values
  instead of consulting unrelated process-global density, locale, or direction state.
- `LazyListState`, pager state, focus requesters, and nested-scroll dispatchers attach to one current
  renderer connector. Hosts must detach old connectors during replacement or disposal.
- State and connector commands are thread-confined to the owning renderer thread. Android
  integrations use the main thread, and callbacks run synchronously unless a concrete contract says
  otherwise.
- `UiNodeTooling.withFirstSourceCapture` observes at most one eligible node in each scope and
  allocates at most one stack trace. Nested scopes observe independently. Its callback runs on the
  emitting thread; callback failures propagate after the scope restores its thread-local state.
- `UiNodeTooling.withSourceCandidateCapture` samples at most 64 eligible emissions and retains at
  most 32 distinct chains. Its callback runs only after a successful block returns and after its
  capture state is restored; failed or empty builds do not report candidates.
- `UiSourceSessionRole` has no rendering or application-state semantics. Hosts and renderers assign
  it only to independently rendered containers; tooling may skip `Content` sessions to keep page
  navigation precise and source-capture overhead bounded.
- `AndroidViewNodeProps.update` and `onReset` are replay-safe transaction callbacks. External
  one-shot work belongs in `onCommit`; resource cleanup belongs in `onRelease`.
- Image loading is an optional capability. `UiImageLoader` is caller-owned, runs on the owning UI
  thread, and returns a handle for the started work. The renderer owns replacing and disposing the
  handle for a mounted image View; a loader must not retain that View after disposal.
- `ImageSource.Url` accepts only absolute HTTP(S) URLs; `ImageSource.Uri` accepts absolute URIs for
  other loader-supported schemes. `UiImageDecodeSize.Fixed` uses positive `UiDp` bounds. The
  renderer includes its captured `UiDensity` in `UiImageRequest`, and adapters resolve those logical
  bounds to platform pixels without changing layout size.
- `ImageSource.Model` requires a caller-provided stable key. Its equality and diagnostic text must
  not depend on the raw model payload, so adapters can accept arbitrary platform-specific models
  without leaking them into logs or persistence.
- A `UiImageRequestExtension` is identified by its concrete runtime type plus `stableKey`. Adapters
  ignore extension types they do not own, and callers must change the key when load behavior changes.

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
- [Image loading guide](../../guides/image-loading.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

## Compatibility notes

The `0.1.0-alpha03` line establishes the first public renderer contract. Adding a `NodeType`, a
concrete `NodeSpec`, or a modifier element can require a renderer update even when application DSL
source remains unchanged. Custom renderers should fail clearly for unknown contracts and should not
persist enum ordinals, sealed-subtype names, tooling metadata, native view identities, or callback
instances as long-lived external data.

Adding `ButtonNodeProps.visualHeight` is a Q2 immutable snapshot-contract change. The source default
equals `minHeight`, but precompiled constructor call sites and custom renderers must be rebuilt for
the corresponding alpha release.

Adding `SliderNodeProps.inactiveTrackColor` is also a Q2 immutable snapshot-contract change. Its
source default equals `trackColor` so direct source construction remains concise, but precompiled
constructor call sites and custom renderers must be rebuilt for the corresponding alpha release.

`UiStateLayerColors` is a Q2 immutable resolved-color value. Adding nullable fields to
`ButtonNodeProps`, `IconButtonNodeProps`, `BoxNodeProps`, `RowNodeProps`, and
`SegmentedControlNodeProps` preserves source construction and the one-color renderer fallback, but
it changes their binary constructor contracts. Precompiled direct constructors and custom
renderers must be rebuilt for the corresponding alpha release.

`SurfaceNodeProps` replaces `BoxNodeProps` for `NodeType.Surface` and is a Q2 immutable snapshot.
Custom renderers must add the new type/spec pairing and rebuild precompiled callers. Adding
`UiCornerFamily.Continuous` and `UiShape.continuous` expands the Q2 shape contract; exhaustive enum
consumers must handle the new family or deliberately select their documented rounded fallback.

`SemanticsCollectionInfo` and `SemanticsCollectionItemInfo` add Q3 platform-neutral collection
metadata. The nullable additions to `SemanticsConfiguration` change its binary constructor
contract, so precompiled callers and custom renderers must rebuild. Renderers that support
accessibility should map the parent collection and child position snapshots together; omitting the
mapping loses position announcements but must not change layout, input, or selection callbacks.

`UiNodeTooling.withFirstSourceCapture` is an additive Q3 tooling API. It does not change VNode
equality or normal-render metadata, but consumers must treat its callback as synchronous and avoid
blocking, re-entrant rendering, or retaining a call chain as application state.

`UiNodeTooling.withSourceCandidateCapture` is also an additive Q3 tooling API. It preserves normal
VNode identity and metadata, but its nested candidate list and sampling bounds are tooling input,
not an application persistence format.

`UiSourceSessionContainerHandle` and `UiSourceSessionRole` are additive Q2 tooling contracts.
Existing `RenderContainerHandle` implementations remain valid; without the marker, page-level
source tooling must use its documented fallback or opt out of capture.

The text-bearing NodeSpec family now enforces immutable, platform-neutral payloads. Direct
`TextNodeProps` callers must replace `text = label` with
`document = TextDocument.plain(label)`; rich text continues to pass its existing `TextDocument`.
`ButtonNodeProps.text` and `ToggleNodeProps.text` narrow from `CharSequence?` to `String?`.
The public `Text`, `RichText`, `Button`, `Checkbox`, `RadioButton`, and `Switch` DSL signatures and
rendered behavior are unchanged. This is a source- and binary-breaking Q2 snapshot-contract change
for direct NodeSpec constructors and custom renderers, which must rebuild and perform any Android
`CharSequence` conversion at their final native binding boundary.
