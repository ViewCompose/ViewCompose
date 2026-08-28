---
schema_version: 2
document_id: architecture.lazy-collections-runtime
doc_type: architecture
owner:
  kind: capability
  id: lazy.collections
version_lane: released
capability_ids:
  - lazy.collections
artifact_ids:
  - viewcompose-ui-contract
  - viewcompose-ui-foundation
  - viewcompose-renderer-android
sample_ids:
  - tutorial.lazy-lists
  - guide.lazy-collections-state
  - guide.lazy-collections-grid
  - guide.lazy-collections-pager
  - tutorial.lazy-list-performance
invariants:
  - Stable non-null keys own logical item, page, and saveable-state identity across reorder and native recycling.
  - Ordinary List declarations reevaluate structure and selectors on every parent composition pass; only an unchanged LazyItemsSnapshot identity and environment may bypass that scan.
  - An observed LazyItemsSnapshot publishes its evaluated table and logical-owner changes only after the exact native list patch commits.
  - Content revision describes every changing ordinary non-State input, while observed State remains independently invalidating inside the active Session.
  - RecyclerView holders and mounted-tree caches never own logical key state or active effects.
  - Prefetch preparation is externally silent until first attachment activates the logical Session.
  - LazyListState and PagerState expose renderer snapshots and commands without transferring controlled application state to Android.
evidence:
  - viewcompose-ui-contract/src/test/kotlin/com/viewcompose/ui/node/LazyItemTableTest.kt
  - viewcompose-ui-contract/src/test/kotlin/com/viewcompose/ui/state/StateConnectorContractTest.kt
  - viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/runtime/TypedLazyCollectionContractTest.kt
  - viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/runtime/LazyItemsSnapshotContractTest.kt
  - viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/runtime/RenderSessionFailureTest.kt
  - viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/view/lazy/adapter/LazyListAdapterTest.kt
  - viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/view/container/PagerAdapterTest.kt
---

# Lazy collection runtime architecture

## 1. Ownership boundary

UI Contract owns renderer-neutral item tables, keys, revisions, span and padding policies, scroll
snapshots, and connector commands. UI Foundation turns list, grid, pager, and tab declarations into
keyed logical content. Android Renderer keeps `RecyclerView` and its layout managers as the
scrolling, recycling, focus, accessibility, nested-scroll, fling, and edge-effect engine.

The application owns collection data and controlled pager selection. ViewCompose owns logical
Sessions and saveable state. Android owns the current physical holder and geometry. These three
identities are deliberately separate.

## 2. Declaration and Session identity

Every item and page key is non-null and unique within its container. A key must continue to identify
the same logical content after insertion, removal, or move. Duplicate keys reject the candidate
declaration atomically rather than weakening diffing.

`contentRevision` is the semantic version of ordinary non-State inputs read by delayed content.
Equal key, revision, captured framework environment, kind, content type, and grid span may retain the
committed item and Session. Observable State read while that Session renders remains independently
tracked. The framework cannot infer an arbitrary Kotlin lambda capture, so an unrepresented changing
value is a correctness error rather than a missed optimization.

Single items, sticky headers, pages, and tabs require an explicit revision. `StaticContentRevision`
is a named promise that the declaration captures no changing ordinary value. Bulk item declarations
default the revision selector to the item value and are safe only when value equality covers all
ordinary inputs.

Every independently mounted lazy item or pager page emits exactly one root node. That root is the
native holder's measurement and placement boundary. Tabs are eager keyed children in their parent
composition; they do not create lazy Sessions.

## 3. Ordinary lists and immutable snapshots

An ordinary `List` declaration reevaluates order, membership, key, content type, revision, and grid
span selectors on every parent composition pass. It does not trust list reference identity or list
equality because Kotlin lists may have mutable aliases. The resulting keyed entries can still reuse
all unaffected committed Sessions, so selector evaluation does not imply full item rendering.

`LazyItemsSnapshot` is the explicit whole-table fast path. `toLazyItemsSnapshot()` shallow-copies
ordered item references and assigns an opaque identity without evaluating selectors. A container
retains its current and immediately previous successfully committed snapshot/environment pair. An
exact pair hit returns the complete evaluated item table in constant time without selectors or a
key scan.

The application must replace that snapshot whenever order, membership, retained item data, selector
captures, or ordinary item-content captures change. A new environment is always a miss so theme,
resources, locale, layout direction, density, and font scale remain correct. Selector failure or a
duplicate key publishes no cached result; retry evaluates the whole candidate again.

The observed `LazyColumn` overload moves that immutable submission read into the property
transaction. One consistent Snapshot evaluates every dirty declaration, the renderer patches the
exact mounted list, and only a successful native commit publishes the evaluated table, dependency
replacement, and saveable-key membership. Item content receives a stable key and an
`ObservedValue<T>` so leaf payload changes can patch existing nodes without rebuilding parent or
row structure. An abort leaves the preceding table, observations, and logical owners installed.

## 4. Renderer mapping and reuse

The Android adapter consumes Q3 `LazyItemTable`. Finite Foundation declarations publish an indexed
table; compact integrations may calculate positions on demand and publish neutral range updates.
Stable IDs are collision-safe and allocated lazily. The table owns key-to-position lookup and may
provide sticky-header metadata without exposing Android types to UI Contract.

`contentType` partitions only physically compatible structures. Model values and revisions do not
belong in it. A mounted container accepts at most 1,024 distinct kind/type combinations so a dynamic
taxonomy fails instead of retaining unbounded view-type history.

RecyclerView prefetch may prepare a detached tree only after its type has demonstrated a bounded
synchronous cost. Preparation cannot activate remember state, effects, overlays, native commit
callbacks, or committed diagnostics. First attachment activates current State. Ordinary cache
detach keeps an activated Session alive; recycling terminates its logical key Session.

Cross-key mounted-tree reuse requires every interop `AndroidView` in that tree to provide `onReset`.
The old Session and effects end before reset. The bounded cache carries only reset physical trees;
logical key state never enters it or RecyclerView's pool. Eviction or container disposal calls
`onRelease` exactly once.

The renderer may retain one reset-compatible presentation in its local recycled pool. After scroll
idle, it may prepare one non-adjacent candidate outside the gesture path. Cross-key Session reuse is
legal only when the declaration strategy explicitly accepts it; Runtime then replaces remembered,
observed, effect, callback, and saveable ownership transactionally while equal pure structural
results retain identity. The adapter weakly caches only the two exact immutable cyclic transitions
needed by an alternating submission, never the whole historical list sequence.

## 5. Layout, state, and pager contracts

`LazyListState` observes the latest immutable anchor, visible-item geometry, boundaries, direction,
and scrolling status. Immediate commands also update the retained anchor; animated commands depend
on renderer snapshots. Host recreation saves only first visible index and pixel offset. Transient
geometry and motion remain attached-layout state.

Grid cells are renderer-neutral policies. `GridCells.Fixed` preserves an exact positive count;
`GridCells.Adaptive` derives at least one column from inner width, density, spacing, and minimum
size. `FullLine` tracks physical column changes without changing the item's logical identity,
remembered state, or effects.

Pager selection is controlled by `currentPage` and `onPageChanged`. `PagerState` observes current,
settled, target, offset, count, and motion and sends commands to the attached presentation; it does
not replace controlled selection across detach or recreation. `TabRow` may share that state for
indicator progress while preserving eager keyed tab identity.

## 6. Focus, accessibility, and persistence

Each logical item key owns a child saveable-state registry, so sibling rows may reuse local saveable
keys. Detach and reorder retain the map by item key. A pinned sticky-header copy is a non-owning
presentation replica and cannot overwrite the owner's persisted state. The ordinary header remains
the only accessibility node, preventing duplicate TalkBack announcements.

Focused descendants use Android child-rectangle propagation through real vertical scroll owners.
Programmatic focus visibility remains available when direct user scrolling is disabled. A pager
page whose content can be obscured supplies its own vertical scroll owner; the pager itself owns
discrete page movement only. Application tasks and the optional Paging boundary are covered by the
[lazy collections guide](../guides/lazy-collections.md).
