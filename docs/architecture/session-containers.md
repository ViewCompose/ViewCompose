# Delayed Session Container Checklist

## 1. Scope

This document tracks stability risks in containers that combine delayed creation with
holder/session reuse.

These containers share three properties:

1. Content is not mounted under the parent immediately.
2. Holders or sessions are reused internally.
3. Structural diffing can be decoupled from visible-content refresh.

They are therefore high-risk areas for stale content when structure remains unchanged.

## 2. Current containers

1. `LazyColumn`
2. `LazyRow`
3. `LazyVerticalGrid`
4. `HorizontalPager`
5. `VerticalPager`
6. `TabRow + pager page`, where page content is carried by `LazyListItemSession`
7. navigation destination pages, where content is carried by `NavDestinationSession`

## 3. Hard architecture constraints

Every delayed-session container must satisfy these constraints:

1. An empty diff must not fall back to an old item or page instance.
2. A bound holder/session must have a refresh path when structure does not change.
3. The update path reinjects `localSnapshot`, theme, environment, and the latest parent closure.
4. A delayed create path may prepare a native child tree, but only activation or an active update
   can cross the child composition/effect commit boundary.
5. `activate` happens at most once; later `render` operations apply active submissions, and
   `dispose/recycle` semantics align with holder lifetime.
6. A `Change` update prefers the payload path instead of an unconditional full-change signal.
7. When unusable keys force `ReloadAll`, preserve the current scroll anchor where possible instead
   of jumping the collection to the top after an interaction.
8. Focusing an input must not cause an unrelated list jump. When the container's focus-follow
   policy is enabled, it may scroll only enough to reveal the focused editor while preserving the
   logical item anchor.
9. A parent collection submission is one monotonic child-session revision. Its retained-child
   updates publish only from the parent render frame's commit effects, after composition commit;
   parent rollback discards them without running child composition or effects.
10. Callback identity is not a revision. The renderer submits newly emitted immutable item/page
    snapshots and suppresses only duplicate platform binds for the same submission revision.
11. A detached, never-activated holder may prepare a committed parent submission without running
    remember activation, effects, native commit callbacks, overlays, or committed diagnostics.
    Activation commits a valid candidate without rebuilding it. An already-active detached holder
    stages the latest revision and renders it on reattach; ambiguous duplicate keys never use
    first-match lookup to guess ownership.
12. Pager stable IDs are collision-free for unique keys, and native view types partition
    structurally incompatible `contentType`/kind pairs. Unkeyed cached pages retain position
    ownership; keyed moves resolve only through a unique key in both snapshots.
13. Every independently composed item/page receives a child `SaveableStateRegistry` owned by a
    parent-composition holder and its stable logical key. Recycling retains that registry's saved
    map, reordering follows the key, and nested containers repeat the hierarchy.
14. A renderer-created concurrent presentation replica may restore the logical owner's current
    saveable snapshot but must not register a second persistence owner for the same logical key.

## 4. Required scenarios

Every container covers at least these eight cases:

1. Stable structure, changed closure: visible content updates after the successful parent commit
   while `key` is unchanged.
2. Stable structure, changed local context: theme, Local, or environment changes become visible.
3. Changed `contentToken`: reuse or controlled recreation follows the documented semantics.
4. Keyed reorder: ordering is correct and state does not move between items.
5. Prepare/attach/detach/recycle: a never-activated cache runs no child commit work, attach presents
   the latest committed revision, active detach does not restart lifecycle work, and recycle leaks
   no state.
6. Empty-diff refresh: an attached holder still refreshes once for the committed submission.
7. Failed parent frame: retained child update/render/effects do not run.
8. Missing or duplicate keys: conservative reload avoids guessed holder identity.
9. Saveable-state ownership: sibling local keys do not collide, keyed recycling/restoration does
   not move state, and presentation replicas cannot overwrite the logical owner.

## 5. Current test mapping (2026-08)

Foundation unit tests:

1. [LazyListDiffTest.kt](../../viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/reconcile/LazyListDiffTest.kt)
2. [LazyHolderRegistryTest.kt](../../viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/view/LazyHolderRegistryTest.kt)
3. [LazyItemSessionControllerTest.kt](../../viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/view/LazyItemSessionControllerTest.kt)
4. [LazyListAdapterTest.kt](../../viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/view/lazy/adapter/LazyListAdapterTest.kt)
5. [ViewTreeRenderTransactionTest.kt](../../viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/view/tree/ViewTreeRenderTransactionTest.kt)
6. [PagerAdapterTest.kt](../../viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/view/container/PagerAdapterTest.kt)

Covered special cases:

1. `LazyColumn`: `collectionsStress_toggleUpdatesVisibleControls` (UI)
2. `LazyVerticalGrid`: `collectionsGrid_spanToggle_refreshesVisibleItemContent` (UI)
3. `TabRow + HorizontalPager`: `statePatchStress_refreshesStableTabContent` (UI)
4. `HorizontalPager`: `statePatchStress_horizontalPagerContentUpdatesAcrossAdvances` (UI)
5. `VerticalPager`: `statePatchStress_verticalPagerContentUpdatesAcrossAdvances` (UI)
6. `LazyVerticalGrid/HorizontalPager/VerticalPager`: collection patch cases in
   `NodeBindingDifferTest` (unit)
7. `LazyColumn`: `collectionsStress_rotateOrder_refreshesVisibleIdsAcrossToggles` (UI)
8. Navigation destinations: `NavDestinationSessionStoreTest` covers candidate off-screen first
   render, failed rollback, Local/content-closure refresh, visibility layers, permanent removal, and
   owner release (unit).
9. Transactional navigation host: `TransactionalNavHostCoordinatorTest` covers attach,
   push/pop/replace/reset, revealed-page refresh failure, initial-failure retry, serialized
   reentrancy, and lifecycle caps (unit).
10. Public navigation: the `:samples:tutorials` device test covers push and Back through the
    production `NavHost` (instrumentation).

Current baseline notes:

1. `qaFull` remains the connected-device gate for application behavior.
2. Since 2026-03-07, Lazy/Pager uses the unified DiffUtil plus payload `Change` path while
   preserving empty-diff refresh semantics.
3. Since 2026-07-26, candidate navigation pages commit their first frame off-screen, committed
   pages refresh the latest `UiLocalSnapshot` and content closure, and rollback/removal releases
   session before owner.
4. Since 2026-07-26, a back-stack commit occurs only after candidate first render or revealed-page
   refresh succeeds. Reentrant commands created by a failed candidate do not leak into the old
   stack.
5. Since 2026-08-12, lazy, pager, and tab child submissions join the parent commit-effect boundary.
   Attached holders render once per explicit submission revision; detached caches and rolled-back
   parent frames run no child render or effects.
6. Pager moves proactively refresh attached uniquely keyed pages after commit. Hash-colliding keys
   keep distinct stable IDs, and unkeyed detached pages resolve the committed snapshot by their
   bound position when reattached.
7. Since 2026-08-13, a never-activated lazy holder uses the Prepared → Active → Disposed protocol.
   RecyclerView prefetch can build its composition and native tree before attachment, while the
   existing transaction defers remember activation, effects, native commit work, overlays, and
   diagnostics. An observed state change invalidates the candidate before activation.

## 6. New-container workflow

Adding a delayed-session container requires all of the following:

1. register the container in [the architecture overview](overview.md);
2. add it to this checklist with a test mapping;
3. add unit cases for `diff empty but closure changed`, parent rollback, and detached-holder attach;
4. add real Activity instrumentation;
5. confirm that render/layout diagnostics expose the behavior.

## 7. Investigation order

For stale text, misplaced state, or an outdated page, investigate in this order:

1. determine whether the content is inside a delayed-session container;
2. determine whether a parent commit effect published the latest item/page submission revision;
3. determine whether the holder was attached, detached-cached, or ambiguously keyed;
4. determine whether the holder rendered that revision exactly once;
5. only then inspect the Demo application code.
