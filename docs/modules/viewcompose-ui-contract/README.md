---
schema_version: 2
document_id: module.viewcompose-ui-contract
doc_type: module
owner:
  kind: module
  id: viewcompose-ui-contract
version_lane: released
capability_ids:
  - lazy.item-session-reuse
  - renderer.tree-transactions
  - text.platform-font-family
artifact_ids:
  - viewcompose-ui-contract
sample_ids:
  - module.ui-contract-dependency
  - module.ui-contract-font-family
  - module.ui-contract-lazy-session-reuse
  - module.ui-contract-node
coordinate: com.viewcompose:viewcompose-ui-contract:0.1.0-alpha04
minimal_usage_sample_id: module.ui-contract-node
---

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

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="ui-contract-module-dependency" sample_id="module.ui-contract-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-ui-contract:0.1.0-alpha04")
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

{/* compiled-region source="viewcompose-ui-contract/src/test/samples/com/viewcompose/ui/samples/UiContractNodeSamples.kt" region="ui-contract-module-node" sample_id="module.ui-contract-node" build_target=":viewcompose-ui-contract:compileTestKotlin" */}
```kotlin
val gap = VNode(
    type = NodeType.Spacer,
    key = "content-gap",
    spec = EmptyNodeSpec,
    modifier = Modifier
        .size(width = 24.dp, height = 24.dp)
        .testTag("content-gap"),
)
```

This creates a renderer-neutral semantic node. A compatible renderer resolves `NodeType.Spacer`,
validates its `EmptyNodeSpec`, interprets the modifier chain in order, and owns any native object
created for the node.

## Principal APIs

- [`VNode` and `NodeType`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha03/viewcompose-ui-contract/com.viewcompose.ui.node/-v-node/)
  define immutable tree content and renderer dispatch. Q3 `VNode.observedPropertyId` is an opaque
  session identity used only to publish exact renderer targets after a full frame; direct VNode
  constructors leave it `null`, and it does not replace semantic keys or affect ordinary content.
- [`NodeSpec`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha03/viewcompose-ui-contract/com.viewcompose.ui.node.spec/-node-spec/)
  and its concrete property snapshots define the supported renderer inputs.
- Q3 `AnimatedVisibilityHostNodeProps` is the renderer-neutral frame transport for parent and
  descendant visibility hosts. It carries alpha, measured reveal fractions, visual scale,
  full-measured-size translation fractions, transform origin, reveal alignment, clipping, and
  active interaction ownership. All sampled transform values must be finite; renderers keep the
  full content measurement while reporting the reveal size to the parent, and an inactive host is
  draw-only. The compiled `animatedVisibilityHostNodeContractSample` covers the complete snapshot.
- Q3 `AnimatedContentHostNodeProps` and `AnimatedContentItemNodeProps` are the renderer-neutral
  bounded-pair transport for full content replacement. The host carries segment identity, finite
  size progress, clipping, and logical alignment; each item carries measured-size transform
  fractions and explicit active interaction ownership. A compatible renderer must accept at most
  two item roots and hide every inactive root from input, focus traversal, and accessibility. The
  compiled `animatedContentNodeContractSample` demonstrates the complete pair.
- `TextNodeProps` carries one authoritative `TextDocument`; `ButtonNodeProps` and
  `ToggleNodeProps` carry nullable plain `String` labels. Mutable or platform-specific
  `CharSequence` implementations are converted only at a platform renderer boundary.
- [`Modifier`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha03/viewcompose-ui-contract/com.viewcompose.ui.modifier/-modifier/)
  carries ordered layout, drawing, interaction, semantics, focus, and parent-data elements.
- `paddingRelative`, `marginRelative`, `offsetRelative`,
  `systemBarsInsetsPaddingRelative`, and `imeInsetsPaddingRelative` are Q3 coordinate and Android
  boundary contracts. Their logical start/end values resolve from each VNode's captured layout
  direction; the existing APIs keep physical left/right semantics. The compiled
  `relativeLayoutModifierSample` demonstrates the complete family.
- [`UiEnvironmentValues`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha03/viewcompose-ui-contract/com.viewcompose.ui.environment/-ui-environment-values/)
  captures density, locale tags, and logical layout direction for a subtree.
- [`LazyListState`](https://docs.viewcompose.com/api/viewcompose-ui-contract/0.1.0-alpha03/viewcompose-ui-contract/com.viewcompose.ui.state/-lazy-list-state/),
  Q3 `ScrollState`, and Q3 `PagerState` bridge lazy, eager, and page-oriented platform scrolling to
  immutable observable snapshots. Connectors have one live renderer owner, detach on replacement
  or disposal, and keep immediate versus animated commands explicit.
- Q3 `GridCells.Fixed` and `GridCells.Adaptive` define physical column calculation without exposing
  Android layout managers. Q3 `GridItemSpan.Single`, `Fixed`, and `FullLine` remain meaningful when
  an adaptive grid changes column count; the compiled `gridPolicySample` covers the policy model.
- `maxWidth`, `maxHeight`, and `aspectRatio` are portable measurement modifiers implemented through
  `NodeType.LayoutConstraintHost`. Custom renderers must constrain the complete modified node in
  one measurement boundary, honor an incoming exact parent constraint, apply declared maxima
  otherwise, and preserve the requested ratio whenever the resulting interval is feasible.
- `AnimateContentSizeModifierElement` carries a finite `ContentSizeAnimationSpecModel`. Duration
  models cover tween, keyframes, snap, and finite repeat; the separate physical spring model carries
  damping ratio, normalized-mass stiffness, and a safety guard. Infinite layout motion is not part
  of the transport. The contract owns no clock or solver.
- Q3 `AnimateBoundsModifierElement` reuses that finite layout-animation model, while Q3
  `AnimatedBoundsHostNodeProps` and `NodeType.AnimatedBoundsHost` describe one real rectangle in the
  immediate parent's physical-pixel coordinates. A renderer must keep layout, hit, focus, and
  accessibility geometry aligned, resolve logical direction before animation, reject simultaneous
  bounds/content-size ownership, and clear transient ownership on detach or cross-owner reuse. The
  compiled `animatedBoundsHostNodeContractSample` covers the transport.
- Q3 `SharedContentKey`, `sharedElement`, and `sharedBounds` publish renderer-neutral endpoint
  identity and element/bounds mode. Keys are non-blank and scoped by the consuming host rather than
  registered globally. Later declarations on one modifier chain win. The stable Android tag slot
  is cross-artifact transport only; the UI Contract owns no pairing registry, View, overlay,
  snapshot, clock, or navigation transaction. The compiled `sharedContentModifierSample` covers
  the typed declaration surface.
- Q3 ConstraintLayout transport uses one mutually exclusive `ConstraintDimension` value per axis,
  `ConstraintMatchMode` for spread/wrap/percent behavior, a positive typed `ConstraintRatio`, and
  one baseline link. Logical start/end and physical left/right anchors remain distinct;
  `ConstraintWrapBehavior` selects wrap-parent contribution per axis. Chain transport carries typed
  boundary targets and margins. Typed Grid carries bounded axes, weights, gaps, spans, and skips,
  while declarative CircularFlow carries explicit center/radius/angle values without requiring a
  helper View. The transport has no Android dependency, `match_parent`, independent dimension
  flags, raw ratio grammar, or AndroidX Grid string grammar. Cross-node identity, reference,
  ownership, topology, and range failures reject the complete candidate at the platform renderer
  boundary rather than weakening individual links.
- `NavigationBarItem` and `SegmentedControlItem` require explicit unique logical keys. Their
  NodeSpecs require an in-range selected index for non-empty collections and `-1` for an empty
  collection; navigation badges are nullable non-negative values.
- `LazyListItem` is the Q3 renderer-neutral snapshot/session contract. Logical equality consists of
  key, `contentType`, caller-owned `contentRevision`, framework-owned `environmentRevision`, kind,
  and span; callback identity is deliberately excluded. Equal key and revisions skip the session
  completely. A changed revision updates only that session, while a changed `contentType`
  terminates the old session and requires a full presentation rebuild. Its Q3 `prepare` → `activate` →
  `render` → `disposeForReuse`/`dispose` protocol lets renderers build an externally silent
  candidate, terminate key-owned state, and transfer only a reset physical presentation. The
  Boolean result from `activate` and `render` advances the semantic revision only after the
  installed content commits; rollback returns `false` and remains retryable. The compiled
  `lazyListItemSessionUpdateSample` demonstrates this lifecycle. A strategy may opt into Q3
  `canReuseAcrossKeys` only when its next `update` transaction replaces every key-owned state,
  observation, effect, callback, and saveable owner before the incoming frame becomes visible;
  the default remains `false`.
- `LazyItemTable` is the Q3 ordered, indexed collection boundary used by lazy-list NodeSpecs. It
  exposes count, positional item lookup, key-to-position lookup, and immutable updates from the
  immediately preceding accepted table without prescribing AndroidX Paging or a renderer. Q2
  `LazyItemTableUpdate` values describe bounded insert, remove, move, change, or full reload work;
  a provider that declares an exact update owns its semantic correctness. Iteration and `toList()`
  are full positional scans for compact sources; a finite wrapper returns its retained backing
  list and preserves structural equality without copying. Q2
  `LazyItemTableStickyHeaders` is optional metadata: a table that does not implement it promises
  that all entries are ordinary items. `List<LazyListItem>.asLazyItemTable()` validates unique keys,
  retains the finite list without copying its item models, and is the migration adapter for direct
  NodeSpec construction. The compiled `lazyItemTableSample` demonstrates the contract.
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
- `UiNodeTooling.attachTimingIdentity`, `ensureTimingIdentity`, and `timingIdentityOf` form the Q3
  process-local correlation bridge between an actively timed composition scope and downstream
  renderer phases. Composition identities are positive, renderer-only fallbacks are negative, and
  neither value is an application key, persistence identity, ordering contract, or analytics field.
- [`ImageSource`](https://docs.viewcompose.com/api/viewcompose-ui-contract/current/com.viewcompose.ui.node.media/-image-source/),
  [`UiImageRequest`](https://docs.viewcompose.com/api/viewcompose-ui-contract/current/com.viewcompose.ui.node.media/-ui-image-request/),
  and [`UiImageLoader`](https://docs.viewcompose.com/api/viewcompose-ui-contract/current/com.viewcompose.ui.node.media/-ui-image-loader/)
  define portable image sources, request policy, platform targets, and disposable load handles.
- The unit, shape, graphics, key-input, gesture, semantics, and tooling packages complete the
  platform-neutral vocabulary used across ViewCompose modules.

Cross-key session reuse is deliberately opt-in. The strategy's `update` transaction must replace
all logical-key ownership before the retained session becomes visible:

{/* compiled-region source="viewcompose-ui-contract/src/test/samples/com/viewcompose/ui/samples/UiContractNodeSamples.kt" region="ui-contract-module-cross-key-lazy-session" sample_id="module.ui-contract-lazy-session-reuse" build_target=":viewcompose-ui-contract:compileTestKotlin" */}
```kotlin
return object : LazyListItemSessionStrategy {
    override fun create(
        container: RenderContainerHandle,
        item: LazyListItem,
    ): LazyListItemSession = createSession(container).also { installItem(it, item) }

    override fun update(
        session: LazyListItemSession,
        item: LazyListItem,
    ) {
        // This transaction must replace every key-owned state, effect, callback, and owner.
        installItem(session, item)
    }

    override fun canReuseAcrossKeys(session: LazyListItemSession): Boolean = true
}
```

Platform font wrappers use object identity, not an opaque platform object's value equality. Reusing
the same platform object therefore stabilizes declarative snapshots without conflating different
renderer resources:

{/* compiled-region source="viewcompose-ui-contract/src/test/samples/com/viewcompose/ui/samples/UiContractNodeSamples.kt" region="ui-contract-module-platform-font-family" sample_id="module.ui-contract-font-family" build_target=":viewcompose-ui-contract:compileTestKotlin" */}
```kotlin
val platformFont = Any()
val first = uiFontFamily(platformFont)
val second = uiFontFamily(platformFont)
val different = uiFontFamily(Any())

check(first == second)
check(first != different)
```

The complete generated reference is available under the
[`viewcompose-ui-contract` API tree](https://docs.viewcompose.com/api/viewcompose-ui-contract/current/).
Because the current line is alpha, the documentation site intentionally does not expose a stable
`latest` alias.

## Contract and lifecycle rules

`UiEnvironmentValues.resourceRevision` is a host-published, monotonic invalidation identity. It is
not a semantic configuration model or persisted version. VNodes capture it with density, locales,
and layout direction so a renderer can rebind resource-backed properties whose integer IDs remain
equal after a qualifier change. `UiImageRequest.resourceRevision` carries the same identity through
first-party image loaders; its zero default preserves deterministic non-Android/custom hosts.

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
- `UiInteractionIndication.StateLayer` carries complete renderer-neutral pressed, focused, and
  hovered colors through `Modifier.interactionIndication`. Box, Row, Surface, Button, and
  IconButton NodeSpecs contain no ripple or parallel state-layer fields. Native multi-target
  SegmentedControl and NavigationBar NodeSpecs carry separate selected and unselected sets because
  their semantic roles differ. Inactive and disabled high-level components install no indication.
- `SliderNodeProps.trackColor` is the active segment at or before the current value, while
  `inactiveTrackColor` is the remaining segment. Renderers must bind both resolved colors and must
  not recover either segment from a platform theme.
- Modifier order is semantic. Layout and parent-data collection, visual decoration, input,
  semantics, and drawing phases consume the ordered elements according to their documented phase
  rules; reordering elements may change behavior.
- Physical and relative declarations for padding, margin, offset, or one inset type share one
  resolved slot per family. The later declaration replaces the earlier complete value. Relative
  horizontal offset is positive toward logical end; all other relative start/end values map from
  the VNode's captured `UiLayoutDirection` whenever a renderer binds that node.
- Collection semantics use logical indexes. RTL may reverse physical placement but does not change
  row/column metadata or callback identity. A collection item derives heading and selected metadata
  from the same `SemanticsConfiguration` fields, avoiding duplicate state ownership.
- `UiEnvironmentValues` is captured on every VNode subtree. A renderer must use the captured values
  instead of consulting unrelated process-global density, locale, or direction state.
- `LazyListState`, `ScrollState`, `PagerState`, focus requesters, and nested-scroll dispatchers
  attach to one current renderer connector. Hosts must detach old connectors during replacement or
  disposal. Eager horizontal offsets and all page indexes use logical order in RTL.
- `PagerStateSnapshot` publishes current, settled, and target pages separately. A pager's controlled
  `currentPage` remains authoritative across recreation; `onPageChanged` is a settled-idle event,
  not an `onPageSelected` echo during declarative binding. Page indexes stay logical in RTL;
  `offscreenPageLimit` accepts `-1` or a positive value, and disabled user scrolling blocks pointer
  and accessibility paging without blocking renderer commands.
- Vertical collection NodeSpecs contain no focus-follow policy. Focused-descendant visibility is a
  renderer invariant of a real scroll owner, while a pager remains a discrete selection owner.
- `GridCells.Adaptive` recomputes physical columns from current inner width, spacing, density, and
  configuration while keeping keyed logical sessions intact. `GridItemSpan.FullLine` resolves
  against that current count; `Fixed(1)` is canonicalized to `Single` by Foundation.
- A renderer retaining a `LazyListItem` Session must ignore a newer strategy or payload when key
  and both revisions are equal. When either revision changes, it calls the retained declaration
  strategy with the latest item payload and renders that logical Session until the content reports
  a successful commit. A different key always creates a different logical Session; compatible
  physical presentation may move only after old State and effects are disposed. Typed declarations
  may share one `LazyListItemSessionStrategy` across all item snapshots; strategies consume the
  current item synchronously and cannot retain it. Cross-key Session retention additionally
  requires the installed strategy to accept `canReuseAcrossKeys`; physical kind/content-type
  compatibility alone never authorizes logical state transfer.
- State and connector commands are thread-confined to the owning renderer thread. Android
  integrations use the main thread, and callbacks run synchronously unless a concrete contract says
  otherwise.
- Outside every tooling capture scope, VNode emission performs one atomic inactive check and does
  not read tooling ThreadLocals or allocate a stack trace. Active capture keeps the documented
  synchronous, bounded behavior below.
- `UiNodeTooling.withFirstSourceCapture` observes at most one eligible node in each scope and
  allocates at most one stack trace. Nested scopes observe independently. Its callback runs on the
  emitting thread; callback failures propagate after the scope restores its thread-local state.
- `UiNodeTooling.withSourceCandidateCapture` samples at most 64 eligible emissions and retains at
  most 32 distinct chains. Its callback runs only after a successful block returns and after its
  capture state is restored; failed or empty builds do not report candidates.
- `AndroidViewNodeProps.factory`, `update`, `onReset`, and `onCommit` receive the immutable
  environment captured by their VNode. `update` and `onReset` are replay-safe transaction
  callbacks. External one-shot work belongs in `onCommit`; resource cleanup belongs in
  `onRelease`. Release is one-shot permanent-abandonment cleanup and also covers an uncommitted
  rollback candidate.
- `AndroidViewNodeProps.lifecycleMode` is diagnostic text only. It describes downstream adapter
  ownership without authorizing UI Contract or a renderer to observe a lifecycle owner.
- `AndroidViewNodeProps.constructionIdentity` is physical constructor identity, separate from the
  VNode's logical `key`. Equal identity rebinds the retained View without reset. Changed identity
  requires an atomic candidate replacement; the renderer must preserve the committed View if
  candidate creation or binding fails.
- A mounted tree containing `AndroidView` may cross logical keys only when every interop node
  declares `onReset`. The renderer calls reset after old-session disposal and before new-key bind;
  final cache eviction calls `onRelease` exactly once.
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
the declared content. Prefetch preparation cannot publish committed work; first activation remains
the lifecycle boundary even when a platform ignores the optimization.

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

The five relative layout modifier elements are additive Q3 contracts, but a custom renderer must
recognize them before application code can use their DSL functions. Resolve start/end only from the
VNode environment, keep the existing element types physical, and apply last-declaration-wins
across the physical and relative form of each family.

Shared-content marker elements are additive Q3 contracts. Custom renderers may leave them inert,
but a renderer claiming shared-host support must publish and clear the complete typed element on
reuse; it cannot persist tag values, guess duplicate winners, or create a global key registry.

The native-widget convergence is an alpha hard cut. The old command-only pager state and
fixed-integer grid contracts are removed: callers use immutable `PagerStateSnapshot`,
`GridCells`, and `GridItemSpan`. `ScrollableColumnNodeProps` and `ScrollableRowNodeProps` now carry
`ScrollState` and `userScrollEnabled`; slider snapshots carry step and interaction-boundary
callbacks; pull-to-refresh carries `enabled`; navigation and segmented items carry explicit key
and enabled state; and the dead progress `enabled` field is gone. Precompiled direct NodeSpec
constructors and custom renderers must rebuild and implement the complete new contracts.

`MaxWidthModifierElement`, `MaxHeightModifierElement`, `AspectRatioModifierElement`,
`LayoutConstraintHostNodeProps`, and `NodeType.LayoutConstraintHost` are additive source APIs but
expand the renderer registry. A custom renderer must recognize all of them before application code
uses the modifiers; silently ignoring the host would violate measurement correctness.

The Phase 2 ConstraintLayout transport additions extend immutable data-class constructors and
helper enums with physical anchors, parent-wrap policy, chain boundaries, Grid, and CircularFlow.
Source defaults preserve the prior logical-parent behavior, but precompiled direct constructors and
custom renderers must rebuild. A renderer may not silently treat physical edges as logical, anchor
to identity-only Grid/CircularFlow declarations, or partially apply an invalid ownership graph.

Adding `LazyListItemSession.prepare` and `activate` is a Q3 lifecycle hard cut. Kotlin source
implementations inherit safe defaults, but the interface JVM shape changes, so precompiled custom
sessions and renderers must be rebuilt. An override that prepares native content must keep all
commit-bound callbacks deferred and support disposal before activation.

Changing `LazyListItemSession.activate` and `render` to return commit success completes that Q3
hard cut. Custom implementations must return `false` for rolled-back attempts so equal submission
revisions remain retryable; precompiled sessions and renderers must be rebuilt.

Changing `LazyColumnNodeProps.items`, `LazyRowNodeProps.items`, and
`LazyVerticalGridNodeProps.items` from `List<LazyListItem>` to Q3 `LazyItemTable` is an alpha hard
cut. Direct source callers wrap finite models with `asLazyItemTable()`; Foundation collection DSL
callers need no source change. Precompiled NodeSpec producers and custom renderers must rebuild.
Custom compact providers must return unique stable keys, exact `indexOfKey` results, structurally
valid updates, and immutable snapshots; returning `null` from `updatesFrom` requests the renderer's
finite compatibility diff, while `ReloadAll` remains the explicit conservative fallback.

Adding `ButtonNodeProps.visualHeight` is a Q2 immutable snapshot-contract change. The source default
equals `minHeight`, but precompiled constructor call sites and custom renderers must be rebuilt for
the corresponding alpha release.

Adding `SliderNodeProps.inactiveTrackColor` is also a Q2 immutable snapshot-contract change. Its
source default equals `trackColor` so direct source construction remains concise, but precompiled
constructor call sites and custom renderers must be rebuilt for the corresponding alpha release.

`UiStateLayerColors` and `UiInteractionIndication` are Q2 immutable resolved-value contracts.
Removing ripple and state-layer fields from generic and single-target NodeSpecs changes their
binary constructor contracts. Custom renderers must consume indication modifier elements and
exhaustively handle every indication subtype in the UI Contract version they use. Precompiled
direct constructors and custom renderers must be rebuilt for the corresponding alpha release.

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

The Q3 timing-identity helpers may run only behind an explicit finite timing request. Copy and
wrapper helpers preserve an existing identity, while ordinary node construction leaves the
tooling-only field `null`. The field is excluded from equality and hash semantics, and callers must
not serialize or retain it after the capture that requested it.

The alpha diagnostics-correlation hard cut removes the tooling-only
`UiSourceSessionContainerHandle` and `UiSourceSessionRole`. Session identity, parent ownership, and
role now belong to UI Foundation's single `RenderDiagnosticContext`; a render container no longer
carries a second logical-owner marker.

The text-bearing NodeSpec family now enforces immutable, platform-neutral payloads. Direct
`TextNodeProps` callers must replace `text = label` with
`document = TextDocument.plain(label)`; rich text continues to pass its existing `TextDocument`.
`ButtonNodeProps.text` and `ToggleNodeProps.text` narrow from `CharSequence?` to `String?`.
The public `Text`, `RichText`, `Button`, `Checkbox`, `RadioButton`, and `Switch` DSL signatures and
rendered behavior are unchanged. This is a source- and binary-breaking Q2 snapshot-contract change
for direct NodeSpec constructors and custom renderers, which must rebuild and perform any Android
`CharSequence` conversion at their final native binding boundary.

Adding Q3 `VNode.observedPropertyId` extends the public data-class constructor and component shape.
Its source default keeps direct construction concise, but precompiled constructors, destructuring
call sites, and custom renderers must rebuild for this alpha release. Custom renderers that support
observed transactions publish one unique exact target for every non-null identity; renderers that
do not support the capability may otherwise ignore the nullable metadata.

Animation Phase 1 hard-cuts the animated-size model hierarchy. Custom renderers must replace the
old duration-bearing spring approximation with `ContentSizeSpringSpecModel(dampingRatio,
stiffness, maxDurationMillis)`, accept only `ContentSizeAnimationSpecModel` at the host
boundary, and rebuild precompiled exhaustive consumers. There is no infinite-repeat content-size
model or duration-spring compatibility subtype.
