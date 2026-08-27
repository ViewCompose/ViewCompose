---
schema_version: 2
document_id: architecture.focus-visibility-pager-selection
doc_type: architecture
slug: /architecture/decisions/focus-visibility-and-pager-selection-ownership
owner:
  kind: capability
  id: focus.input
version_lane: released
capability_ids:
  - focus.input
  - lazy.collections
  - text.input
artifact_ids:
  - viewcompose-ui-contract
  - viewcompose-ui-foundation
  - viewcompose-renderer-android
sample_ids:
  - guide.focus-form
  - guide.lazy-collections-state
  - guide.text-input-editing
invariants:
  - Focused-editor visibility delegates movement to the nearest real Android scroll owner and activates one window-scoped coordinator only while a ViewCompose editor owns focus.
  - Pager selection is discrete logical-page ownership; idle relayout cannot clear focus and within-page visibility stops at the page-local scroll boundary.
evidence:
  - Focus and rectangle-propagation suites, pager selection and geometry tests, offscreen and accessibility coverage, LTR and RTL physical-device acceptance, compiled samples, and removed-symbol gates.
---

# ADR-0018: Focus Visibility and Pager Selection Ownership

- Status: Accepted
- Date: 2026-08-22
- Supersedes: the ViewPager2 physical-host and offscreen-default portions of ADR-0012

## Context

The former `focusFollowKeyboard` parameter made focused-editor visibility an opt-in policy on each
vertical container. The Android renderer then reconstructed the behavior with different focus,
layout, global-layout, inset, and frame callbacks for RecyclerView, ScrollView, ViewPager2, and
nested PullToRefresh paths. These paths used different coordinate spaces and disagreed about which
container owned movement.

Physical Android 9 verification exposed both classes of failure: ScrollableColumn could over-scroll
because descendant coordinates were treated as viewport coordinates, and VerticalPager could lose
editor focus while the IME changed the window viewport. The latter was not a timing defect in the
framework callback. ViewPager2 reports a page selection during an idle RecyclerView relayout and
clears focus from the current item, even when no page transition occurred.

## Decision

Focused-editor visibility is an invariant of a real Android scroll owner, not a caller-selected
container mode.

1. `focusFollowKeyboard` is removed from public DSL, NodeSpec, binder, patch, and renderer state.
   There is no deprecated alias or ignored compatibility field.
2. LazyColumn, LazyVerticalGrid, and ScrollableColumn preserve Android's
   `requestChildRectangleOnScreen` chain. The nearest scroll owner performs the minimum reveal and
   ordinary parent propagation handles any remaining ancestor movement.
3. One window-scoped coordinator is active only while a ViewCompose editor owns focus. It reissues
   the editor's native rectangle request when the visible window viewport changes, covering Android
   versions whose first focus request precedes the completed IME resize. It does not calculate or
   write container offsets.
4. HorizontalPager and VerticalPager use a framework-owned RecyclerView, LinearLayoutManager, and
   PagerSnapHelper viewport. Idle relayout is not a selection event and never clears current-page
   focus. A real settled page transition clears focus only from the outgoing page.
5. A pager owns discrete page selection only. A page whose content may be obscured by the IME
   declares a page-local ScrollableColumn, LazyColumn, or another real vertical scroll owner. The
   page boundary stops a within-page rectangle request before it reaches the discrete pager.
6. Pager indexes remain logical in RTL. `userScrollEnabled = false` blocks pointer and accessibility
   paging while retaining state commands and programmatic focus visibility. `offscreenPageLimit =
   -1` selects RecyclerView's default caching policy; positive values add that many page-sized
   layout spaces on each side.

The removed API families and changed pager contracts are Q3. Canonical KDoc, compiled samples,
module manuals, migration guidance, and physical-device acceptance are required in the same
change.

## Consequences

- Applications delete `focusFollowKeyboard` arguments. VerticalPager forms add a page-local scroll
  owner when the page can exceed the IME-visible viewport.
- The renderer removes four container-specific monitor/resolver types and its ViewPager2 runtime
  dependency. The inactive path owns no focus listener or recurring work.
- Pager selection, target-page reporting, RTL geometry, nested same-axis gesture ownership,
  offscreen residency, focus clearing, and accessibility input become framework-tested contracts.
- The one window-scoped focus listener exists only for an attached focused editor and delegates all
  movement to Android's native rectangle protocol.

## Rejected alternatives

### Patch each container-specific monitor

Rejected because correcting coordinates or adding another delayed callback preserves multiple
owners and timing races. It cannot establish one invariant across RecyclerView, ScrollView, pager,
and wrapper paths.

### Keep `focusFollowKeyboard` as a deprecated or ignored parameter

Rejected because a compatibility field would preserve an invalid mental model and allow callers to
believe focus visibility can be disabled independently from focus ownership.

### Keep ViewPager2 and suppress its focus clearing

Rejected because the focus clear is coupled to ViewPager2's internal page-selection callback and
idle-relayout interpretation. Subclass or timing suppression would be a fragile patch over a
backend whose ownership model conflicts with the framework contract.

### Make the pager scroll arbitrary within-page rectangles

Rejected because a discrete page selector has no valid within-page coordinate policy. The page's
real scrollable content is the only owner with enough information to reveal an editor minimally.

## Validation

Acceptance requires JVM tests for idle relayout, real page transitions, forward/backward and
commanded target pages, page-sized geometry, offscreen residency, and disabled accessibility
input. Physical-device tests must cover horizontal and vertical gestures, same-axis nesting, stable
pager selection, and complete editor reveal for LazyColumn, LazyVerticalGrid, ScrollableColumn,
VerticalPager with a page-local owner, and PullToRefresh across the accepted LTR/RTL matrix.
