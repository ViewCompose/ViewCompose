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
  modifier types. Runtime, text core, graphics core, gesture core, and animation core remain
  implementation dependencies. Animation core supplies the single physical size solver; renderer
  APIs do not expose that dependency.
- Android runtime dependencies: AndroidX Core, AppCompat, RecyclerView, ConstraintLayout, and
  SwipeRefreshLayout. Material Components and ViewPager2 are not dependencies.
- Generic surfaces, rounded/cut/continuous shapes, and progress indicators use engine-owned Android drawing
  implementations driven by resolved node values.
- `SurfaceNodeProps` uses cached `UiShapeDrawable` geometry for solid or gradient fill, an optional
  border, and ripple masks. A paint-free bounds-cached provider reports the View outline and
  optional clipping geometry instead of retaining a second drawable. Continuous corners use a
  convex cubic path; stable drawing performs no per-frame Path, shader, drawable, or collection
  allocation.
- Uniform rounded rectangles use Android's native round-rectangle draw and outline operations.
  They retain no `Path`, and surfaces without a visible border retain no stroke paint or path.
  Non-uniform rounded, continuous, and cut corners retain the cached generic path, so this common
  scrolling fast path does not narrow shape, gradient, border, ripple-mask, or clipping behavior.
- Engine-owned rounded shapes use circular arcs. Shape borders are centered on a path inset by half
  the stroke width, keeping the complete outline inside its logical drawable bounds even when a
  component centers a shorter visible surface inside a larger target.
- A Button may request a visible surface shorter than its effective View target. The engine centers
  its background, border, ripple, and outline inside the View without changing measurement,
  hit-testing, or accessibility bounds. An explicit background, border, corner radius, or shape
  modifier disables that component-provided inset so application styling remains authoritative.
- General interactive surfaces receive `UiInteractionIndication.StateLayer` through resolved
  modifiers. The engine maps its pressed, focused, and hovered values into the existing shape mask
  and visual-surface inset without selecting semantic roles or Material opacity values.
  SegmentedControl and NavigationBar receive complete selected and unselected state-layer values
  in their NodeSpecs because they own multiple internal targets. NavigationBar draws each item state
  layer in the foreground across the complete item target, so its selection indicator, icon, badge,
  and label cannot hide pressed, focused, or hovered feedback. When a click synchronously changes
  selection or theme colors, both NavigationBar and general interactive surfaces update the
  retained ripple's color selector in place instead of replacing its drawable, so the active
  release animation remains visible. This also covers BasicSurface-based text navigation.
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

Full animated-content replacement uses one dedicated measurement host and at most two dedicated
item hosts. Both items receive the same parent constraints. The measurement host interpolates from
the last committed dimensions to the incoming dimensions, captures the current size when a segment
retargets, applies logical alignment, and optionally clips animated bounds. Item hosts apply
measured-size translation, scale origin, alpha, and reveal clipping. The inactive outgoing host is
draw-only: it rejects pointer and key dispatch, contributes no focusables, clears retained focus,
and uses `IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS`. All of these bindings participate in the
normal renderer rollback transaction, so a later failing node restores the prior size, visuals,
and interaction owner together.

Real bounds animation uses one transparent `DeclarativeAnimatedBoundsHostLayout` around the
complete parent-data and layout chain. A parent lays out its accepted target once; the host retains
the previous physical rectangle and commits sampled left/top/right/bottom values without measuring
the child on property frames. The child remains the drawing, input, focus, and accessibility owner,
so platform geometry follows the visible rectangle. Duration retargets restart from the current
sample, physical retargets retain four-edge velocity, and repeated target layouts do not restart the
writer. Renderer rollback restores both the prior host specification and parent layout input.
Detach and cross-owner reusable-tree reset explicitly clear bounds and content-size animation state
before adoption, including when no platform detach callback is delivered.

ConstraintLayout reconciliation first compiles a complete immutable candidate and rejects invalid
IDs, references, anchor planes, helper dependencies, ownership conflicts, dimensions, and ranges
before touching native Views. One registry owns stable IDs, instances, type changes, references,
and removal for Guideline, Barrier, Flow, Group, Layer, and Placeholder plus typed Grid's bounded
row/column solver proxies. Grid's semantic identity is not a native View; declarative CircularFlow
compiles to ordinary per-child circle constraints and owns no helper View or generated ID. Accepted
candidates apply from a clean native set; failed native commits restore the previous helper
registry, LayoutParams, runtime properties, environment, and accepted graph. AndroidX `2.2.2`
omits baseline margins and physical gone margins while copying an applied `ConstraintSet` into
`LayoutParams`, so the renderer restores those exact fields before measurement and resets removed
values to prevent cross-graph leakage. Group/Layer/Placeholder effects are retained
as overlays over restorable child runtime properties instead of becoming the next graph's source of
truth. The post-release reconciliation path additionally caches accepted raw specifications,
semantic/resolved graphs, environment, topology/scalar fingerprints, native IDs, and helper
ownership per container. It classifies no-op, content-only, scalar, environment, and topology
updates before commit. Equal/content-only requests bypass graph compilation, environment
resolution, native commit, helper writes, adapter layout requests, and adapter-owned allocation;
scalar requests retain unchanged helper instances and references, create/remove no helper, clone no
live LayoutParams, and issue at most one adapter layout request. Optional structural counters are
container-local and internal to tests; when inactive they own no global observer or recurring work.
The complete contract is recorded in
[ADR-0016](../../architecture/decisions/0016-constraintlayout-graph-and-helper-ownership.md).
The focused 2026-08-18 offline API 35 run passed 16/16 ConstraintLayout renderer regressions,
including exact `125 px` Barrier geometry after a prior `0 px` result, rejected-candidate state
retention, injected mid-commit rollback plus valid retry, Group overlay restoration, Layer and
Placeholder release, Layer detach/reattach callback ownership, density changes, constant ownership
through 1,000 helper retypes, and one-ID retyping across all six helper kinds. The result is
**improved**; reordering two declarations of every retained helper kind also preserved the same
native instances. The cached ConstraintLayout `2.2.1` and manual Robolectric evidence is retained
only for the original defect reproduction. The follow-up Gradle 8.13 run resolved
ConstraintLayout `2.2.2` plus core `1.1.2` and passed all 451 Renderer tests, including the 12 graph
and 16 focused ConstraintLayout cases. The `2.2.2` JVM compatibility limitation is therefore
retired. The subsequent rooted Xiaomi MI 6 / Android 9 matrix passed 3/3 tests across
light/LTR/font-scale 1.0 and dark/RTL/font-scale 1.3, all six retained helper kinds, 200 helper-state
alternations, and nine manually reviewed screenshots without unexpected renderer/helper warnings.
That pass also found and closed an Android 9 environment-transition defect: retained programmatic
helpers now synchronize `layoutDirection` with the container before graph apply, so AndroidX
resolves logical Guideline begin/end correctly after an LTR-to-RTL change. The transition
regression and exact mirrored device geometry classify the renderer result as **improved**. The
final rooted 10/50/100-node matrix then passed the corrected Android-Views-normalized longitudinal
gate with no stable timing or peak-heap regression. During that gate the renderer removed an
O(n-squared) child-index lookup from rollback snapshot capture and avoids a duplicate snapshot when
no Group/Layer/Placeholder content overlay was released; topology-50 P50 moved from a failing
`7.076 ms` to `6.162 ms` against the `6.304 ms` baseline. The performance-safety conclusion is
**no material change**; four unstable actions remain `inconclusive`, and direct Android Views still
owns a material P95 advantage. Broader cross-OEM/API and performance-leadership work remains a
post-release limitation rather than an observed first-release correctness defect.

The 2026-08-21 Phase 1 acceptance passed all 459 renderer tests, including named no-op,
content-only, scalar, environment, and topology/rollback cases. Equal-input stress performed 1,000
classifications with zero compiler, environment, native commit, helper write, adapter layout
request, or adapter allocation batches after the accepted graph. The Changeset and release-intent,
development-tooling-isolation, and documentation gates pass. A fixed-clock 50-node full-frame
preflight remains **inconclusive**: released-baseline stable/scalar run-P50 CV was `0.181`/`0.261`
and the scalar repeat remained `0.244`; candidate stable/scalar CV was `0.212`/`0.143`. Only the
candidate scalar arm met `0.15`, so the result supports no longitudinal timing claim and the Phase 4
matrix must replicate it before claiming an end-to-end win.

The final revision-6 Phase 4 matrix supersedes that short preflight. Seven of twelve released-to-
candidate pairs are stable on both ViewCompose arms and pass every Direct-normalized P50/P95 and
peak-heap regression row; five remain `inconclusive` after the single permitted paired repeat. The
stable rows classify the whole-frame result as **no material change**: they establish release safety
but neither an optimization win nor performance leadership. Direct AndroidX is faster at P95 for
all twelve Candidate actions and at P50 for eleven. This does not invalidate the exact structural
counters above; it limits their claim to avoided adapter work and bounded mutation. The protocol,
absolute values, normalization, limitations, and next action are recorded in
[ViewCompose Performance](../../tooling/performance.md#247-constraintlayout-phase-4-controlled-matrix).

The focused 2026-08-21 Phase 2 API-35 Robolectric acceptance passes all six frozen `CL-P2-*`
renderer cases. Exact bounds cover parent, child, Guideline, and Barrier chain boundaries in
logical LTR/RTL and physical coordinates; baseline normal/gone margins match a direct AndroidX
control; all four parent-wrap policies produce their documented axes; and physical links remain
fixed through an environment-direction update. Weighted Grid retains exactly five proxies for a
`2 x 3` graph, rolls an overlapping candidate back without changing geometry, and remains bounded
through 1,000 add/remove replacements. CircularFlow matches AndroidX angle/radius geometry, rejects
competing direct ownership, and owns zero helper identity through 1,000 replacements. The result is
**improved** relative to the released renderer because it adds atomic support for the frozen Phase
2 transport and also closes the AndroidX baseline/physical-margin copy omission. This evidence is
not a performance comparison and covers Robolectric rather than the later Phase 3 device/OEM
matrix.

The same 2026-08-21 candidate passed 4/4 `ConstraintLayoutReleaseDeviceTest` cases in `15.674 s` on
a rooted Xiaomi MI 6 / Android 9. The new Phase 2 case verifies exact weighted-Grid span/skip
ordering with exactly five generated row/column proxies, plus four direct CircularFlow circle
constraints at the cardinal positions of a `78 dp` radius with no helper View. The retained helper
matrix still passes in light/LTR/font-scale 1.0 and dark/RTL/font-scale 1.3, including 200 rapid
state switches, and structured diagnostics contain no unexpected warning. Manual review of two
focused Chinese Demo captures found no overlap, clipping, or helper artifact; process-filtered logs
contained no `UIConstraintLayout`, `ConstraintSet`, renderer, helper-layer, or fatal entry. This
physical-device capability and lifecycle result is **improved** relative to the released renderer.
It covers one OEM/API point and a focused visual sample, not the complete Phase 3 configuration and
screenshot matrix, and it is not Phase 4 performance evidence.

The 2026-08-21 Phase 3 acceptance adds app-owned one-purpose fixtures and mounted-scene diagnostics
without changing renderer production source, public API, or inactive-path work. The 12/12 reviewed
Paparazzi snapshots span a pairwise/orthogonal selection of phone/tablet, portrait/landscape,
light/dark, LTR/RTL, and font scales `1.0`, `1.3`, and `2.0`, with no overlap, clipping, ambiguous
geometry, or direction/theme defect. The combined Phase 2/3 device suite passes 8/8 on API 24 in
`16.45 s`, 8/8 on API 36 across the final focused runs, and 8/8 in `26.442 s` on a physical Google
Pixel 4 XL / Android 13 (API 33). It asserts exact or tolerance-bounded Grid, CircularFlow,
normal/gone-margin, four-policy parent-wrap, anchor, dimension, bias, direction, and chain geometry,
then covers reorder, key reuse, detach/reattach, density/direction recreation, rejection rollback,
and valid retry. No unexpected `UIConstraintLayout`, `ConstraintSet`, or uncaught AndroidX warning
was observed; the deliberate invalid candidate emits one bounded rejection and recovers. Relative
to Phase 2-only acceptance, renderer configuration, visual, lifecycle, and API-compatibility
confidence is **improved**. Because renderer behavior did not change and no controlled timing
comparison ran, the performance conclusion is **no material change**. Limits are one final physical
Google/API-33 point alongside the earlier Xiaomi/API-28 evidence, 12 pairwise cases rather than all
48 Cartesian visual combinations, and no cooled direct-native/released-baseline/candidate matrix.
Phase 4 owns that benchmark and final guidance.

## Principal APIs

- [`ViewTreeRenderer`](https://docs.viewcompose.com/api/viewcompose-renderer-android/0.1.0-alpha01/viewcompose-renderer-android/com.viewcompose.renderer.view.tree/-view-tree-renderer/)
  owns the transactional VNode-to-View render and disposal boundary.
- Q3 `ViewTreeRenderer.patchObservedProperties` accepts a non-empty batch of unique exact mounted
  targets. It preflights property-only invariants, reuses the normal binder differ, bypasses tree
  wrapping and child reconciliation, and rolls every earlier native binding back when one patch
  fails. `ObservedPropertyRenderResult` intentionally carries no replacement mounted roots.
- [`ChildReconciler`](https://docs.viewcompose.com/api/viewcompose-renderer-android/0.1.0-alpha01/viewcompose-renderer-android/com.viewcompose.renderer.reconcile/-child-reconciler/)
  produces insert, reuse, and removal plans without mutating platform state.
- [`LazyListDiff`](https://docs.viewcompose.com/api/viewcompose-renderer-android/0.1.0-alpha01/viewcompose-renderer-android/com.viewcompose.renderer.reconcile/-lazy-list-diff/)
  converts stable lazy-item keys into ordered RecyclerView updates and deliberately falls back to a
  full reload when identity is missing or ambiguous.
- Eager scroll containers use one renderer connector to publish logical offsets, range, viewport,
  direction, and motion while preserving pending commands until layout. Pager containers use a
  framework-owned RecyclerView, LinearLayoutManager, PagerSnapHelper, and one settled-state
  coordinator for observation and callback de-duplication. An idle relayout is not a page
  selection and cannot clear current-page focus. A vertical
  eager container nested inside a same-axis non-nested-scrolling parent reserves the pointer stream
  only while it can consume that direction, then releases the stream at the matching scroll edge;
  disabling user scrolling never reserves the parent stream.
- Adaptive grids recalculate `GridLayoutManager.spanCount` from available inner width and density
  without replacing the adapter or keyed sessions. The span lookup resolves `FullLine` against the
  current count and caps fixed spans safely.
- Maximum-size and aspect-ratio modifiers install one synthetic measurement host around the
  complete mapped node. The host is renderer-owned infrastructure, not a semantic child and not a
  second logical session.
- Animated content-size nodes install one synthetic measurement host. Duration contracts use a
  retained Android `ValueAnimator`; physical spring contracts use animation-core's analytic solver,
  preserve width/height velocity across retargeting, and still request layout once per accepted
  platform frame. Keyframes are ordered once when the animator is created, not on every frame.
- `RenderTreeResult`, `RenderStats`, `RenderStructureStats`, patch records, and layout-pass sampling
  provide immutable diagnostics used by the demo, preview tooling, and performance tests.
- [`AndroidViewDecorationBackend`](https://docs.viewcompose.com/api/viewcompose-renderer-android/0.1.0-alpha01/viewcompose-renderer-android/com.viewcompose.renderer.decoration/-android-view-decoration-backend/)
  is the optional SPI for effects such as advanced shadows. Without a backend, decoration requests
  stay on a no-op path and no shadow implementation is loaded.
- `AndroidUiShapeDrawables.solid` is the Q2 Android boundary for downstream platform presenters
  that already own a native container. It converts one immutable logical `UiShape`, ARGB color,
  captured layout direction, and density into a newly owned bounds-aware Drawable. Semantic theme
  lookup and presenter lifecycle remain outside Renderer.
- `ViewDecorationHostLayout` and `DecorationChildDrawingOrder` support custom drawing planes and
  declarative `zIndex` without wrapping every child in another View.
- `ViewNodeToolingRegistry` weakly associates mounted Views with source metadata only when tooling
  metadata exists; ordinary rendering retains no extra source objects.
- Renderer-owned child containers carry a tooling-only `UiSourceSessionRole`: pager destinations
  are `Page`, while lazy rows and tab items are `Content`. A debuggable Android host can therefore
  capture page source sessions without paying stack-capture cost for every ordinary lazy item.
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
- The lazy adapter classifies each accepted snapshot before notifying RecyclerView. Equal key order
  batches adjacent native changes without running `DiffUtil`; a same-size cyclic permutation emits
  the smaller left/right sequence of moves; other structural changes retain AndroidX diffing.
  Logical item Sessions still consume changed revisions synchronously. When item animations are
  disabled, a semantic-only update therefore avoids a redundant RecyclerView bind; if that direct
  Session commit returns false or throws, exactly that attached position receives one payload
  retry. A thrown failure propagates only after the other attached holders are attempted and sticky
  metadata catches up with the published snapshot. Notification planning never changes key
  ownership, content-type compatibility, or failure recovery.
- Horizontal and vertical pager holders preserve the `Page` source-session role across reuse.
  RecyclerView rows and tab items remain `Content`; this role does not affect keys, diffing,
  measurement, visibility, or callbacks.
- A lazy item's `contentRevision` and framework-captured `environmentRevision` are the only content
  invalidation inputs after identity and type. Equal key and revisions skip item composition and
  native patching completely, even when the parent supplied a different strategy or payload. A
  changed revision asks the item's shared strategy to install the latest payload and renders only
  that item; callers must use observed State or include every changing ordinary capture in
  `contentRevision`. A changed `contentType`, even under the same key and revisions, terminates the
  old child Session and performs a full native presentation rebuild. Holder creation and update
  call the strategy directly and allocate no item-specific callback adapter on the bind path.
- A detached lazy holder that has never activated may prepare its child composition and native
  View tree under RecyclerView prefetch, but it does not commit remember lifecycle, effects,
  native commit work, overlays, or diagnostics. First attachment activates a valid prepared frame
  without rebuilding it; an observed state change causes a current-state render instead. An active
  detached holder stages a newer submission and renders it on reattach. Missing or duplicate keys
  use the conservative reload path; the renderer never resolves an ambiguous holder through
  first-match key lookup.
- The lazy adapter builds one unique-key position index per accepted submission. Attached and
  reattached holders therefore resolve stable keys without scanning the item list. A payload bind
  may skip Session routing only when the holder has committed the exact item-snapshot instance at
  the exact submission revision; revision equality alone is not sufficient. This acknowledgement
  rule prevents queued RecyclerView notifications from treating an older logical commit as current.
- The same collision-safe submission table owns primitive positions and renderer-assigned stable
  IDs, avoiding overlapping boxed key maps. A compact registry preserves view-type identity for the
  mounted adapter lifetime without `Pair` keys or boxed IDs. Because `contentType` is a finite
  physical-compatibility taxonomy, one mounted container accepts at most 1,024 distinct
  kind/type combinations and rejects a larger history before it can grow without bound.
- Lazy-list and pager holders cache their container handle for the holder lifetime and call a
  dedicated Session host plus the declaration-shared item strategy directly. Native recycling
  still changes logical Session ownership by key; this removes callback-wrapper allocation without
  merging physical and logical identity.
- Pager stable IDs use renderer-assigned values rather than key hashes. Pager view types partition
  incompatible `contentType`/kind pairs, keyed moves refresh only uniquely owned changed holders,
  and every public page declaration requires a unique stable key. RecyclerView's default caching
  policy owns offscreen residency unless callers explicitly request a positive page limit. An
  accepted pager submission
  applies `currentPage` even when its page snapshot is unchanged; page-content diffing never gates
  destination selection.
- Targeted patching and subtree skipping are optimizations. A complete native subtree is skipped
  only when every direct child is the exact VNode instance reused by composition; newly built,
  value-equal children still reconcile because nested session callbacks may have changed. Custom
  host behavior must not infer business state from patch records or diagnostic counters.
- When type, environment, and NodeSpec are unchanged, a changed Modifier uses a modifier-only
  patch. It retains the native View and semantic Node binding, applies the existing per-family
  Modifier diff, and reconciles children. Visual-only changes retain the existing LayoutParams;
  layout or parent-data changes replace them. A changed `NativeViewElement.stableKey` replays its
  configuration, while AndroidView update, reset, commit, and release callbacks remain untouched.
  Diagnostics classify this path as a targeted patch with detail `ModifierOnly`.
- Physical padding, margin, offset, and inset selectors retain left/right semantics. Their
  `Relative` counterparts map logical start/end from the VNode's captured layout direction during
  every bind or environment rebind. A later physical or relative declaration replaces the earlier
  complete value for that family. Positive `offsetRelative.horizontal` translates toward logical
  end without changing measurement.
- Gesture dispatch retains an undecided pointer stream until drag recognition. If the stream ends
  without gesture consumption, the retained target receives one normal click; a recognized drag
  consumes the stream and suppresses that click.
- Renderer-owned eager and lazy scroll containers reserve an axis-matching pointer stream only
  while they can consume movement in that direction. They release cross-axis movement and hand
  movement to an ancestor at the matching logical edge. A vertical child at its top yields the
  initial downward pull to an enabled, idle `PullToRefresh` ancestor so the refresh host can own
  the threshold gesture.
- `FlowColumn` measures every child against the same available cross-axis width; completed columns
  never reduce the width offered to later columns. `FlowRow` applies the symmetric rule to child
  height. Natural flow content may still exceed a constrained cross axis, but it is not compressed
  merely because earlier rows or columns consumed space.
- Button surface-inset changes participate in targeted style patching. They must not recreate the
  native View or change its effective measured target.
- Basic Surface uses the same effective/visual-bound model. A changed surface snapshot performs a
  neutral rebind of the retained `DeclarativeBoxLayout`; caller background, border, or shape
  modifiers remove the component-provided visual inset and occupy the full effective bounds.
- Engine-created Box and Surface containers skip XML attribute parsing. Children without an
  explicit `BoxScope.align` retain inherited content alignment in their layout parameters, so a
  content-alignment patch updates only those children instead of rescanning every child during
  every layout pass; explicitly aligned children remain unchanged.
- An indication modifier change uses modifier-only binding. Structural surface changes rebuild only
  the retained View's affected drawable, while a color-only indication patch updates the retained
  `RippleDrawable` selector in place so a synchronous selected-state patch cannot cancel a quick
  tap's release animation. SegmentedControl rebuilds only affected internal backgrounds;
  NavigationBar retains each item foreground ripple while updating its selected or unselected
  state-layer colors. Pressed takes precedence over focused and hovered, focused takes precedence
  over hovered, and inactive or disabled high-level targets have no indication. Android's
  value-only ripple fallback remains private to low-level renderer code.
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
- Text alignment updates horizontal gravity bits only. FlowRow, FlowColumn, and TabRow mirror
  physical placement in RTL while retaining logical callback and accessibility indexes.
- NavigationBar, SegmentedControl, and TabRow publish single-selection parent collection metadata
  and item positions. Keyed navigation/segment Views may be reused within the same container, but
  a label or index is never treated as logical identity. SegmentedControl recreates its internal
  shape drawable when density or layout direction changes so resolved corners cannot retain an
  obsolete environment.

## Android host and threading rules

Every VNode binding includes its captured resource revision. A revision change therefore performs
a normal full rebind even when the NodeSpec and resource IDs compare equal. Direct drawable/icon
resources resolve again from the node's current Context, and normalized image requests carry the
revision to adapters when a source, placeholder, error, or fallback is resource-backed. Remote-only
requests retain their ordinary request identity.

Text nodes with no explicit `lineHeightSp` retain the native View's line-spacing parameters rather
than a pixel line height captured at an earlier text size. Their natural line height therefore
tracks the resolved typeface, text size, and font scale across reuse and environment rebinds. An
explicit `lineHeightSp` remains authoritative.

Plain `TextDocument` values bind their existing `String` directly. Styled documents continue to
materialize a `SpannableString`, so span application remains isolated to rich-text nodes while
ordinary Text patches avoid an otherwise redundant platform wrapper allocation.

For lazy collections, the renderer owns one composite native padding value: logical
`contentPadding`, resolved physical or relative Modifier padding, and selected system-bar/IME
insets are additive. All logical start/end values resolve against the captured layout direction.
When a direction change also changes relative inset selectors, the renderer immediately resolves
available root insets or clears the obsolete physical contribution until Android dispatches the
new snapshot; it never renders one frame with the prior side selected.

- Render, disposal, View binding, pager updates, and decoration callbacks are UI-thread confined.
- One container has one mounted-tree owner. Do not share mounted nodes between containers or render
  sessions.
- `collectDiagnostics = false` omits structure, patch, warning, and detailed binding snapshots; use
  it on performance-sensitive paths that do not consume diagnostics.
- Lazy prefetch work is deadline-controlled by RecyclerView. Cold activation supplies only a
  conservative bootstrap ceiling because it also includes commit and effect work; the first
  detached preparation replaces that estimate with an authoritative preparation cost. Estimates
  retain expensive observations and decay through later cheaper samples only while preparation
  remains eligible. One over-budget authoritative sample disables further speculative preparation
  for that content type until the adapter is disposed, so it returns to staging instead of
  extending a fling tail. This can shift bounded work ahead of attach but cannot guarantee
  preparation.
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
- `DeclarativeAnimatedVisibilityHostLayout` measures every direct child at full size, reports the
  animated reveal size to its parent, and computes logical slide fractions and transform pivots from
  the complete full host measurement rather than the first child. It applies reveal alignment and
  clipping before native visual scale/translation. Parent and descendant hosts are ordinary nested
  native layers, so descendant-local transforms compose before the parent layer without another
  renderer frame owner.
- Accepting an exit marks the visibility host inactive before its retained drawing content is
  removed. The host blocks pointer, hover, key, focus traversal, and accessibility event ownership,
  clears descendant focus, and restores participation on reversal. A failed renderer patch restores
  alpha, reveal, slide, scale, pivot, alignment, clipping, and active ownership together.
- A settled hidden visibility host stays mounted as an empty, zero-size reconciliation anchor. Its
  content subtree is absent, but keeping the host stable preserves following unkeyed siblings'
  native View identity and interaction state across visibility changes.
- An animated-size host cancels its active animator on detach. Its first measurement snaps; later
  targets start from the currently displayed dimensions. Physical retargeting carries the last
  sampled pixel-per-second velocity, while a duration-spec retarget resets it. Parent measurement
  constraints remain authoritative on every frame.

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

Adding tooling-only page/content roles to renderer-owned child container handles is an internal
behavior change over the additive UI Contract marker. Rendering output and public renderer
signatures are unchanged; custom renderers may adopt the same marker when their child sessions
represent page boundaries.

The renderer's multi-state path is an implementation of the generic UI Contract rather than a
Material feature. Custom renderers that adopt `UiStateLayerColors` must preserve its enabled-state
precedence and transparent inactive behavior; renderers that receive null may continue their
documented one-color compatibility path.

Custom renderers that consume collection semantics must preserve logical row/column order and map
item spans, selection, and heading state to equivalent platform accessibility metadata. Renderers
that do not yet recognize the nullable collection fields may ignore them during the alpha line,
but their accessibility output will not announce collection position.

The relative modifier family is resolved entirely from each VNode environment. Renderer forks must
upgrade their folding, LayoutParams, translation, and inset-selection paths together; mapping from
the process configuration or reinterpreting the existing physical elements would violate the
public UI Contract.

The native-widget convergence removes the renderer-local pager state and consumes the Q3 UI
Contract state directly. Renderer forks must detach Scroll/Pager connectors on replacement and
disposal, deliver pager callbacks only after idle settlement, honor `userScrollEnabled`, apply
slider interaction phases and steps, preserve descendant input when refresh is disabled, and map
the new keyed selection-item semantics. The grid policy and layout-constraint host also require a
registry and measurement upgrade; treating them as optional hints is incorrect.

The animation Phase 1 alpha adds an implementation dependency on `viewcompose-animation-core` and
hard-cuts the animated-size transport to finite specifications. Custom renderer forks must consume
the physical damping/stiffness/safety-guard model through one equivalent solver, retain velocity on
spring retarget, and reject infinite layout motion. Reusing the old fixed-duration damped
interpolator under the `spring` name is not compatible.
