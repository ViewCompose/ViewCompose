# Android Renderer Engine

`viewcompose-renderer-android` is ViewCompose's Android View rendering engine. It reconciles immutable
VNode snapshots with an owned mounted tree, creates and binds native Views, applies targeted
patches, drives lazy collections and pager state, bridges shapes and drawing commands, and exposes
diagnostics for render work, tree structure, layout passes, and source tooling.

Applications normally receive this module through `viewcompose-android`. Depend on it directly
when implementing a custom Android host, renderer diagnostics, a platform decoration backend, or
tests that exercise reconciliation independently from the widget DSL.

This module does not own composition, application lifecycle, saved state, navigation, overlay
windows, image decoding, or concrete advanced-shadow rasterization. Those responsibilities remain in
`viewcompose-runtime`, `viewcompose-ui-foundation`, `viewcompose-host-android`, and optional feature
modules.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-renderer-android:0.1.0-alpha01")
}
```

- Stability: **Alpha**. Renderer extension contracts and diagnostics may change between alpha
  releases.
- Platform: Android library, `minSdk 24`, `compileSdk 36`, and Java 11 bytecode.
- UI contract is exposed transitively because renderer entry points accept and return its node and
  modifier types. Runtime, text core, graphics core, and gesture core remain implementation
  dependencies.
- Android runtime dependencies: AndroidX Core, AppCompat, RecyclerView, ViewPager2,
  ConstraintLayout, and SwipeRefreshLayout. Material Components is not a dependency.
- Generic surfaces, rounded/cut/continuous shapes, and progress indicators use engine-owned Android drawing
  implementations driven by resolved node values.
- `SurfaceNodeProps` uses one cached `UiShapeDrawable` geometry for solid or gradient fill, border,
  ripple mask, outline, and optional clipping. Continuous corners use a convex cubic path; stable
  drawing performs no per-frame Path, shader, drawable, or collection allocation.
- Engine-owned rounded shapes use circular arcs. Shape borders are centered on a path inset by half
  the stroke width, keeping the complete outline inside its logical drawable bounds even when a
  component centers a shorter visible surface inside a larger target.
- A Button may request a visible surface shorter than its effective View target. The engine centers
  its background, border, ripple, and outline inside the View without changing measurement,
  hit-testing, or accessibility bounds. An explicit background, border, corner radius, or shape
  modifier disables that component-provided inset so application styling remains authoritative.
- Button, IconButton, interactive Box/Row composites, and SegmentedControl state layers use
  resolved `UiStateLayerColors` from their NodeSpecs. The engine applies enabled pressed, focused,
  and hovered selector states inside the existing shape mask and visual-surface inset; it does not
  select semantic roles or Material opacity values.
- Generic collection semantics map to AndroidX accessibility collection metadata. Parent nodes own
  row/column counts and selection cardinality; child nodes own logical positions and spans while
  existing `selected` and `heading` semantics remain the single source of item state.
- Build baseline for this release: Kotlin 2.0.21 and Android Gradle Plugin 8.13.2.

## Rendering model

```kotlin
var mounted = ViewTreeRenderer.renderInto(
    container = container,
    previous = emptyList(),
    nodes = firstFrame,
).mountedNodes

mounted = ViewTreeRenderer.renderInto(
    container = container,
    previous = mounted,
    nodes = nextFrame,
).mountedNodes

ViewTreeRenderer.disposeMounted(container, mounted)
```

The mounted-node list is an ownership token, not an optional cache. A host must pass the exact roots
from its previous successful frame back to the same container and renderer. Stable keyed siblings
can retain their native View across reordering; unkeyed siblings reuse only at the same index and
type so platform state cannot silently move between visually similar items.

Rendering is transactional through structural mutation. If reconciliation, View creation, or
binding fails, the pipeline restores the previous View structure and rethrows the error. Android
View lifecycle callbacks and deferred disposal run after structural commit; their failures are
isolated in `RenderTreeResult.commitFailures` because the new visible tree can no longer be rolled
back safely.

## Principal APIs

- [`ViewTreeRenderer`](https://docs.viewcompose.com/api/viewcompose-renderer-android/0.1.0-alpha01/viewcompose-renderer-android/com.viewcompose.renderer.view.tree/-view-tree-renderer/)
  owns the transactional VNode-to-View render and disposal boundary.
- [`ChildReconciler`](https://docs.viewcompose.com/api/viewcompose-renderer-android/0.1.0-alpha01/viewcompose-renderer-android/com.viewcompose.renderer.reconcile/-child-reconciler/)
  produces insert, reuse, and removal plans without mutating platform state.
- [`LazyListDiff`](https://docs.viewcompose.com/api/viewcompose-renderer-android/0.1.0-alpha01/viewcompose-renderer-android/com.viewcompose.renderer.reconcile/-lazy-list-diff/)
  converts stable lazy-item keys into ordered RecyclerView updates and deliberately falls back to a
  full reload when identity is missing or ambiguous.
- `RenderTreeResult`, `RenderStats`, `RenderStructureStats`, patch records, and layout-pass sampling
  provide immutable diagnostics used by the demo, preview tooling, and performance tests.
- [`AndroidViewDecorationBackend`](https://docs.viewcompose.com/api/viewcompose-renderer-android/0.1.0-alpha01/viewcompose-renderer-android/com.viewcompose.renderer.decoration/-android-view-decoration-backend/)
  is the optional SPI for effects such as advanced shadows. Without a backend, decoration requests
  stay on a no-op path and no shadow implementation is loaded.
- `ViewDecorationHostLayout` and `DecorationChildDrawingOrder` support custom drawing planes and
  declarative `zIndex` without wrapping every child in another View.
- `ViewNodeToolingRegistry` weakly associates mounted Views with source metadata only when tooling
  metadata exists; ordinary rendering retains no extra source objects.
- Image nodes bind `UiImageRequest` to an injected `UiImageLoader` when one is present. The renderer
  stores the disposable handle on the mounted `ImageView`, leaves an equivalent request and its
  loaded drawable untouched, and disposes a changed request before applying its placeholder and
  starting replacement work. It also clears handles during removal, rollback, and session disposal.
  The request carries the node's captured density so adapters can resolve fixed `UiDp` decode
  bounds consistently with layout. Resource sources still render without an adapter; null sources
  bind fallback without a request. Image content is always cropped to the `ImageView` padding bounds,
  even though decoration-aware layout hosts permit child overflow for effects such as shadows.

The complete generated reference is available under the
[`viewcompose-renderer-android` API tree](https://docs.viewcompose.com/api/viewcompose-renderer-android/current/).
Because the current line is alpha, the documentation site intentionally does not expose a stable
`latest` alias.

## Identity and patch rules

- A keyed child reuses a previous payload only when key and `NodeType` both match. Keys must be
  stable and unique among siblings.
- An unkeyed child reuses only the previous payload at the same index and type. Reordering unkeyed
  stateful content is therefore a semantic replacement, not a move.
- Lazy-list precision additionally requires every item to have a unique non-null key. Missing or
  duplicate keys produce `ReloadAll` to protect RecyclerView holder state.
- A lazy item's `contentToken` must change whenever captured values that affect output change.
  Session callbacks are refreshed from the exact next item instance even when the semantic item is
  otherwise unchanged.
- Targeted patching and subtree skipping are optimizations. Custom host behavior must not infer
  business state from patch records or diagnostic counters.
- Gesture dispatch retains an undecided pointer stream until drag recognition. If the stream ends
  without gesture consumption, the retained target receives one normal click; a recognized drag
  consumes the stream and suppresses that click.
- Button surface-inset changes participate in targeted style patching. They must not recreate the
  native View or change its effective measured target.
- Basic Surface uses the same effective/visual-bound model. A changed surface snapshot performs a
  neutral rebind of the retained `DeclarativeBoxLayout`; caller background, border, or shape
  modifiers remove the component-provided visual inset and occupy the full effective bounds.
- Engine-created Box and Surface containers skip XML attribute parsing. Children without an
  explicit `BoxScope.align` retain inherited content alignment in their layout parameters, so a
  content-alignment patch updates only those children instead of rescanning every child during
  every layout pass; explicitly aligned children remain unchanged.
- Button and IconButton state-layer changes participate in targeted style patching and rebuild only
  the surface drawable. Interactive Box/Row changes re-run their existing style binding, while
  SegmentedControl rebuilds only segment backgrounds whose selected role changed. Pressed takes
  precedence over focused and hovered, focused takes precedence over hovered, and inactive or
  disabled multi-state layers are transparent. A null multi-state contract keeps the previous
  value-only ripple selector unchanged.
- Slider binding uses a renderer-neutral `AppCompatSeekBar` subclass because the platform widget
  can ignore `minimumHeight` under an `AT_MOST` measure spec. It honors the declared minimum while
  leaving an exact application or parent height authoritative; no Material policy or token is
  interpreted in Android Renderer.
- Native Switch and Slider binding applies every resolved tint with `SRC_IN`, preserving the
  platform or OEM drawable mask. Slider owns active-track, inactive-track, and thumb tint
  independently, and targeted patches update the inactive track without recreating the View.
  When a controlled callback accepts a native Switch's already-committed value, targeted patching
  does not assign that same value again, so the platform or OEM thumb transition stays in flight.
  Platform drawable geometry and its built-in coverage remain authoritative until a separate
  tested custom-control contract is accepted.
- Collection row and column indexes are logical, zero-based positions. The renderer must not
  reverse them when Android physically lays out descendants in RTL. Selection and heading values
  are read from the item's existing semantic fields so a component cannot expose contradictory
  accessibility state through duplicate contracts.

## Android host and threading rules

- Render, disposal, View binding, pager updates, and decoration callbacks are UI-thread confined.
- One container has one mounted-tree owner. Do not share mounted nodes between containers or render
  sessions.
- `collectDiagnostics = false` omits structure, patch, warning, and detailed binding snapshots; use
  it on performance-sensitive paths that do not consume diagnostics.
- `LayoutPassTracker` is process-local and opt-in. It adds monotonic clock reads and synchronized
  aggregation to instrumented passes, so use it for bounded diagnostics rather than continuous
  production telemetry.
- `AndroidViewDecorationRuntime.install` is process-wide. Install a backend during application
  initialization; existing Views switch only when their decoration request is rebound.
- Decoration hosts add no per-child wrapper. The common no-decoration path delegates directly to
  normal View drawing after one branch; decorated children incur indexed backend dispatch only for
  the drawing planes they request.
- `Row` and `Column` treat a direct animated-visibility host as a progressive spacing participant.
  Its main-axis item gap expands and collapses with the host's measured-size channel, while stable
  siblings retain their existing gap across a fully collapsed intermediate host.
- A settled hidden visibility host stays mounted as an empty, zero-size reconciliation anchor. Its
  content subtree is absent, but keeping the host stable preserves following unkeyed siblings'
  native View identity and interaction state across visibility changes.

## Related documentation

- [Current architecture and module boundaries](../../architecture/overview.md)
- [VNode and renderer registration](../../architecture/node-spec.md)
- [Render failure and commit semantics](../../architecture/render-failures.md)
- [Lazy collection guide](../../guides/lazy-collections.md)
- [Shadow and decoration guide](../../guides/shadows.md)
- [Image loading guide](../../guides/image-loading.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

## Compatibility notes

The `0.1.0-alpha01` line establishes the renamed Material-independent Android renderer coordinate. Its reconciliation, native binding,
diagnostics, tooling association, and decoration-backend contracts. Do not persist mounted nodes,
patch records, diagnostic tree objects, opaque lazy content tokens, or View tags as external data.
Custom hosts and decoration backends must be upgraded with renderer contract changes even when an
application's DSL source still compiles.

The renderer's multi-state path is an implementation of the generic UI Contract rather than a
Material feature. Custom renderers that adopt `UiStateLayerColors` must preserve its enabled-state
precedence and transparent inactive behavior; renderers that receive null may continue their
documented one-color compatibility path.

Custom renderers that consume collection semantics must preserve logical row/column order and map
item spans, selection, and heading state to equivalent platform accessibility metadata. Renderers
that do not yet recognize the nullable collection fields may ignore them during the alpha line,
but their accessibility output will not announce collection position.
