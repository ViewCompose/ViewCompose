# Focus Visibility and Scroll Ownership Hard-cut Plan

## Status

Active. Implementation, repository gates, and physical-device acceptance are complete; PR review,
CI, and merge remain. This plan preempted additive roadmap work because device verification proved
that `focusFollowKeyboard` disabled Android's native child-rectangle protocol and reconstructed it
with incompatible container-specific callbacks. The hard cut has removed that policy and its
synthetic monitors, restored native scroll ownership, and replaced the ViewPager2 backend with a
framework-owned RecyclerView pager whose idle relayout preserves focus.

Manual Demo acceptance also exposed another foundational ambiguity: one delayed lazy item or pager
page could emit multiple root VNodes into a physical holder that had no defined sibling layout.
That contract is now hard-cut to exactly one root, with failure during composition preparation and
no renderer commit. Callers use an explicit `Column`, `Row`, or `Box`, or `Spacer` for an empty
entry.

Next action: submit the PR, follow CI, and merge it. Do not begin additive work until this
foundational correction is merged.

## Maven release changesets

- `release/changes/20260822-focus-visibility-scroll-ownership.json`

## Objective

Make focus visibility an invariant of an Android vertical scroll owner instead of a Boolean policy
that callers must enable and each container reimplements. The finished design has:

1. no public or internal `focusFollowKeyboard` field;
2. no renderer listener that polls or observes multiple asynchronous callbacks to synthesize
   platform focus scrolling;
3. native RecyclerView and ScrollView child-rectangle handling as the single Android transport;
4. a discrete pager that owns page selection only, while scrollable content inside a page owns IME
   reveal; and
5. exactly one explicit root for every independently composed lazy item, sticky header, typed item,
   or pager page; and
6. device evidence that LazyColumn, LazyVerticalGrid, ScrollableColumn, and a PullToRefresh child
   reveal the complete focused editor with minimal movement across the accepted matrix.

## Scope

### In scope

- hard removal of `focusFollowKeyboard` from all LazyColumn, LazyVerticalGrid, ScrollableColumn,
  VerticalPager, scope-wrapper, and NodeSpec signatures;
- removal of `LazyFocusFollowLayoutMonitor`, `ScrollableFocusFollowLayoutMonitor`,
  `FocusFollowViewportResolver`, their tags, patch branches, adapter exceptions, and pager/grid
  forwarding;
- restoration of stock LinearLayoutManager/GridLayoutManager child-rectangle behavior;
- replacement of ViewPager2 with the framework-owned RecyclerView and PagerSnapHelper backend;
- page-local scroll ownership for VerticalPager input fixtures and migration guidance;
- exact-one-root validation for delayed lazy item, sticky-header, typed-item, and pager-page content;
- Q3 KDoc/sample, module-manual, architecture, migration, Demo, test, and Changeset updates; and
- Android API 28 physical-device verification plus the existing JVM and repository gates.

### Out of scope

- a new generic BringIntoView API before a concrete non-focus use case exists;
- custom caret tracking, manual IME animation, or duplicated window-inset computation;
- keeping a deprecated alias, ignored compatibility field, or renderer fallback; and
- changing unrelated collection reuse, motion, nested-pointer, or pager-selection policy.

## Hard-cut contract

1. A vertical scroll owner uses Android's `requestChildRectangleOnScreen` chain and moves only when
   the requested rectangle is outside its viewport.
2. Callers control unwanted initial movement by controlling focus ownership; they do not disable
   the scroll container's ability to honor a focused descendant.
3. `userScrollEnabled = false` disables direct pointer input, not programmatic focus visibility or
   state commands.
4. VerticalPager is a discrete page-selection owner. It must not scroll within a page to avoid the
   IME. A page whose content can be occluded declares ScrollableColumn, LazyColumn, or another real
   page-local vertical scroll owner.
5. PullToRefresh remains a wrapper. Its one vertical child owns focus visibility.
6. Nested owners must not compete: the nearest owner consumes the minimal rectangle request and
   normal parent propagation handles any remaining off-screen ancestor relationship.
7. One delayed item or page maps to one physical measurement and placement boundary and therefore
   emits exactly one root. The framework rejects zero or multiple roots before native rendering;
   it never guesses whether sibling roots should stack, line up, or overlay.

## Work packages

### Phase 0: evidence and contract freeze

- Preserve the failing Android 9 evidence and identify every API, spec, renderer, Demo, test, and
  active-document reference.
- Assign the changed LazyColumn, LazyVerticalGrid, ScrollableColumn, and VerticalPager API families
  Q3; no new public symbol is introduced.
- Record the removal as breaking for UI contract/foundation artifacts and classify renderer
  behavior explicitly in the immutable Changeset.

### Phase 1: contract and renderer hard cut

- Remove the parameter from every overload and scope facade in one compile-breaking change.
- Remove NodeSpec fields and all binder/patch transport.
- Delete monitor/resolver infrastructure and resource IDs with a zero-reference guard.
- Restore stock layout-manager rectangle handling without compatibility branches.
- Replace ViewPager2 with the framework-owned RecyclerView pager and remove the dependency.
- Reject zero- or multi-root delayed item/page content before native rendering without a legacy
  acceptance path.

### Phase 2: consumer migration and executable contract

- Remove opt-in flags from valid vertical-scroll fixtures.
- Restructure VerticalPager input content around an explicit page-local scroll owner.
- Replace tests that inspect the removed Boolean with native focus-visibility and owner-identity
  tests.
- Update Q3 compiled samples so they demonstrate page-local ownership and ordinary vertical forms.
- Update the Demo's delayed focus controls to declare their `Column` root explicitly, and add a Q3
  compiled sample for the single-root contract.

### Phase 3: physical acceptance

- Verify LazyColumn, LazyVerticalGrid, ScrollableColumn, VerticalPager with a page-local owner, and
  PullToRefresh with exactly one child owner.
- Cover English/light/LTR/default scale and Chinese/dark/RTL/font `1.3`/density `1.25`.
- Assert complete editor visibility above the IME, minimal scroll, stable pager selection, no
  unrelated host movement, repeat focus/reset, and no recurring inactive-path work.
- Manually inspect retained screenshots and interactions; automation does not replace IME and
  ownership review.
- Verify both LTR and RTL pager gestures settle on exactly one adjacent page while preserving the
  expected layout direction.

### Phase 4: documentation and closure

- Update architecture, UI-contract/foundation/renderer module manuals, migration guidance, and
  Simplified Chinese mirrors for the released breaking contract.
- Interpret test evidence with context, absolute observations, conclusion, limitations, and next
  action.
- Run API/source documentation, release intent, documentation, tooling isolation, JVM, AndroidTest
  compilation, physical-device, and `qaQuick` gates.
- Archive this plan only after the hard cut is merged and all durable conclusions live in active
  documents.

## Validation matrix

| Area | Required evidence |
| --- | --- |
| Public contract | no `focusFollowKeyboard` symbol in production API, samples, or current docs |
| Renderer | no monitor/resolver/listener tags; stock rectangle request is not overridden or suppressed |
| Lazy owners | list and grid reveal the complete editor with minimal movement |
| Eager owner | ScrollableColumn reveals the editor without coordinate conversion or polling |
| Pager | selected page remains stable; a page-local owner reveals its editor |
| Wrapper/nesting | PullToRefresh delegates to one child owner; unrelated ancestors remain stable |
| Delayed roots | zero or multiple roots fail before native rendering; an explicit-root retry succeeds |
| Configuration | two accepted locale/theme/direction/font/density configurations on a physical device |
| Repository | affected JVM/device suites, API docs/samples, Changeset, docs/localization, and `qaQuick` pass |

## Interpreted acceptance evidence

The 2026-08-22 acceptance run used a rooted Xiaomi MI 6 on Android 9. The foundation suite passed
all 370 tests, including rollback before native rendering for empty and sibling delayed roots and a
successful explicit-`Column` retry. The application and instrumentation APK build completed all 470
tasks.

The final fixed focus-matrix replay completed in 30.049 seconds. It exercised five focus-owner scenarios and two
nested-scroll scenarios in each of two configurations: English/light/LTR/font `1.0`/density `1.0`,
and Chinese/dark/RTL/font `1.3`/density `1.25`. Manual inspection of all ten retained focus
screenshots found every editor fully visible above the IME with focus retained and no overlapping
delayed roots. The final paired LTR/RTL horizontal and vertical pager gesture replay completed in
1.819 seconds; both selected only the adjacent page after settling and asserted the native
RecyclerView layout direction. `qaQuick` completed 1,622 tasks in 3 minutes 39 seconds, including
release publication inputs, source documentation, compiled samples, all core-module unit suites,
Demo and tutorial compilation, release intent, localization, and repository structure gates.

Conclusion: **improved**. The hard cut removes competing synthetic focus owners, preserves focused
page content through pager idle relayout, and turns ambiguous delayed-holder geometry into a
fail-fast composition contract. Limitations: physical evidence currently covers one Android 9
device, and the Chinese stress configuration wraps or clips some non-focus top-chrome text even
though the focus-owner output is correct. The next action is PR CI and merge; broader device
coverage remains a future acceptance expansion, not a compatibility reason to retain the rejected
design.

## Completion criteria

This plan completes only when the old symbol and all synthetic focus-follow infrastructure have zero
production references; the hard-cut API and migration contract are documented; all five owner
shapes pass automated and manual physical-device acceptance; no compatibility shim or timing poll
remains; publication impact is represented by an immutable Changeset; and the PR is merged to the
main branch.
