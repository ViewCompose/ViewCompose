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
4. Both create and update paths can drive `RenderSession.render()`.
5. `dispose/recycle` semantics align with holder lifetime.
6. A `Change` update prefers the payload path instead of an unconditional full-change signal.
7. When unusable keys force `ReloadAll`, preserve the current scroll anchor where possible instead
   of jumping the collection to the top after an interaction.
8. Focusing an input must not cause an automatic list jump unless scrolling was requested
   explicitly.

## 4. Required scenarios

Every container covers at least these six cases:

1. Stable structure, changed closure: visible content updates immediately while `key` is unchanged.
2. Stable structure, changed local context: theme, Local, or environment changes become visible.
3. Changed `contentToken`: reuse or controlled recreation follows the documented semantics.
4. Keyed reorder: ordering is correct and state does not move between items.
5. Detach/attach/recycle: no leaks and no state loss.
6. Empty-diff refresh: a bound holder still refreshes when `updates.isEmpty()`.

## 5. Current test mapping (2026-08)

Foundation unit tests:

1. [LazyListDiffTest.kt](../../viewcompose-renderer/src/test/java/com/viewcompose/renderer/reconcile/LazyListDiffTest.kt)
2. [LazyHolderRegistryTest.kt](../../viewcompose-renderer/src/test/java/com/viewcompose/renderer/view/LazyHolderRegistryTest.kt)
3. [LazyItemSessionControllerTest.kt](../../viewcompose-renderer/src/test/java/com/viewcompose/renderer/view/LazyItemSessionControllerTest.kt)

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

## 6. New-container workflow

Adding a delayed-session container requires all of the following:

1. register the container in [the architecture overview](overview.md);
2. add it to this checklist with a test mapping;
3. add a unit case for at least `diff empty but closure changed`;
4. add real Activity instrumentation;
5. confirm that render/layout diagnostics expose the behavior.

## 7. Investigation order

For stale text, misplaced state, or an outdated page, investigate in this order:

1. determine whether the content is inside a delayed-session container;
2. determine whether the diff retained the latest item/page instance;
3. determine whether the bound holder refreshes on the empty-diff path;
4. only then inspect the Demo application code.
