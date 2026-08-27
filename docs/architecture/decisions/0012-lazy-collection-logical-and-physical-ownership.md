---
schema_version: 2
document_id: architecture.lazy-collection-ownership
doc_type: architecture
slug: /architecture/decisions/lazy-collection-logical-and-physical-ownership
owner:
  kind: capability
  id: lazy.collections
version_lane: released
capability_ids:
  - lazy.collections
  - host.android-view
  - renderer.reconciliation
  - renderer.tree-transactions
artifact_ids:
  - viewcompose-ui-contract
  - viewcompose-ui-foundation
  - viewcompose-renderer-android
  - viewcompose-host-android
sample_ids:
  - guide.lazy-collections-state
  - module.host-android-view-adapter
  - module.renderer-reconciliation
  - module.renderer-tree-transaction
invariants:
  - Logical snapshots, key-owned sessions, and reusable physical presentations remain separate ownership layers.
  - Cross-key native reuse requires reset-before-bind and exactly-once release without transferring remembered state, effects, or saveable ownership.
evidence:
  - Revision, keyed-session, reconciliation, Android View reuse, cache eviction, rollback, release, and accepted collection-performance suites.
---

# ADR-0012: Lazy collection logical and physical ownership

- Status: Accepted
- Date: 2026-08-13
- Supersedes: the identity and retention parts of
  [ADR-0011](./0011-prefetched-session-activation-boundary.md)
- Superseded in part: [ADR-0018](./0018-focus-visibility-and-pager-selection-ownership.md) replaces
  the ViewPager2 physical-host and offscreen-default decisions; logical page-session ownership
  remains accepted here.

## Context

ViewCompose maps declarative content to Android Views. Lazy list and pager entries therefore cross
two independent reuse systems: framework composition sessions retain logical identity, state, and
effects, while RecyclerView and ViewPager2 retain physical holders and native Views. The previous
implementation attached both forms of ownership to one holder. It also used callback-reference
changes as an implicit invalidation signal and allowed speculative preparation to perform an
unbounded amount of synchronous work.

That model preserved correctness by rendering conservatively, but it made unchanged submissions
expensive, prevented native-tree reuse across different logical keys, and made performance depend
on incidental lambda allocation. It also risked carrying `remember`, saveable state, or effects
with a recycled physical holder if native reuse were expanded without an explicit boundary.

ViewCompose has no compiler-generated change masks or stability inference. A public revision
contract is therefore required; ordinary captured values cannot be compared reliably by the
runtime.

## Decision

### Three ownership layers

Every virtualized entry is represented by three separate layers:

1. The immutable logical snapshot contains `key`, `contentType`, `contentRevision`, and an
   automatically captured `environmentRevision`.
2. The key-owned logical session contains composition state, `rememberSaveable` ownership, effects,
   and observed-state subscriptions. Its effects exist only while that logical session is active.
3. The physical presentation contains a holder, mounted native tree, and Android Views. It may be
   reused by `contentType`, but never owns or transports a previous key's logical identity.

Every public lazy-list, grid, pager-page, and tab declaration requires a non-null key that is
unique within its container. Position is physical placement and is never a fallback logical
identity.

### Binding rules

The runtime applies these rules without callback-reference fallbacks:

- Same key and equal content plus environment revisions: skip item rendering completely.
- Same key and a changed revision: recompose and patch only that item.
- Different key and equal `contentType`: create a new logical session, then attempt to reset and
  rebind a framework-owned mounted native tree.
- Different `contentType`, including under the same key: terminate the old session, release its
  presentation, and build a new native tree.

Theme, Android resources, locale, layout direction, density, font scale, and other captured
ViewCompose locals are included in `environmentRevision`. State reads remain independently
observable. A non-state value captured by entry content must be represented by
`contentRevision`. Single `item`, `stickyHeader`, `Page`, and `Tab` declarations require a non-null
revision immediately after `key`; optional physical-reuse and layout policy follows it. `null` is
not a static sentinel, and `StaticContentRevision` is the explicit promise that ordinary captured
content remains stable for that key. Bulk item selectors remain nullable and default to the item
value for immutable value models.
The session-update operation is mandatory: a renderer cannot replace the same-key, same-type
logical session merely because an implementation omitted a content installer.
`LazyListItemSession.activate` and `render` report whether the installed candidate actually
committed. A rollback never advances the item or parent submission revision; the same semantic
revision remains retryable. Failures reported after the native frame committed do not reverse that
result.

### Native View reuse lifecycle

Cross-key mounted-tree reuse is opt-in at every native interop node. An `AndroidView` participates
only when it declares a reset callback. Reset runs after the old logical session is disposed and
before the new key binds the tree. Final mounted-tree eviction runs release exactly once.

RecyclerView's opaque global pool does not own mounted trees. A bounded, framework-owned cache
holds reusable trees by `contentType`, exposes deterministic eviction, and releases every evicted
tree. RecyclerView may still pool empty holder shells. A mounted tree that contains a non-resettable
interop View is released instead of cached.

### Collection-specific policies

- Lazy lists use virtualized key sessions, adaptive cost-bounded speculative preparation, and the
  three-layer reuse model.
- Pagers use delayed page sessions and delegate residency to ViewPager2's native offscreen policy.
  The default does not force an extra framework offscreen page count.
- Tab rows are eager parent-tree children. Tabs use ordinary keyed reconciliation; selection
  changes invalidate only the previously selected and newly selected children. Tab rows do not
  create lazy item sessions or independent saveable-state owners.

## Public API and compatibility impact

This is a deliberate hard cut. Lazy and pager DSLs replace the former `contentToken` input with
`contentRevision`. The revision is a caller-visible correctness contract rather than a best-effort
performance hint. Single-entry declarations place the required non-null revision after `key` and
before optional `contentType` or layout policy; callers use `StaticContentRevision`, not `null`, for
intentional static content. Bulk `items` overloads keep a nullable revision selector, page
declarations expose all four snapshot fields, tab content gains the same explicit revision input,
and pager offscreen defaults follow the native ViewPager2 policy.

The parameter reorder is source-breaking and requires recompiling every consumer. Adjacent
`Any?`/`Any` values may erase to the same JVM `Object` descriptor, so a binary compiled against the
previous order is not guaranteed to fail linkage and could pass physical `contentType` and logical
`contentRevision` values into the opposite positions.

Changed APIs are Q3 because incorrect revision or lifecycle usage can retain stale UI, leak native
resources, or publish effects for the wrong logical entry. Canonical KDoc, compiled samples,
module manuals, migration guidance, and deterministic lifecycle tests are required in the same
change.

## Consequences

- Stable entries do no item composition or native patch work on an equal submission.
- A changed entry cannot refresh unrelated attached holders.
- Native allocation can be amortized across keys without transferring composition state or effects.
- The framework has an observable final-release boundary for pooled mounted trees.
- Unequal keyed groups that collide in their persistent saveable-path hash fail before provider
  registration; a collision can never merge two logical owners silently.
- Large list entries can still cost a frame when first encountered; adaptive preparation avoids
  moving an unknown or previously expensive entry into an earlier fling frame but cannot preempt
  arbitrary user code. Entry granularity remains an application design concern.
- Callers must make changing captured data observable as State or include it in
  `contentRevision`. This limitation cannot be removed without compiler support.

## Rejected alternatives

### Treat every new callback object as changed content

Rejected because allocation identity is not semantic identity. It forces work on every parent
render and hides missing revision declarations instead of defining a stable contract.

### Keep logical sessions inside recycled holders

Rejected because a holder is physical capacity, not logical identity. Cross-key reuse would move
remembered state, saveable ownership, subscriptions, and effects to unrelated content.

### Put complete mounted trees directly in RecyclerView's shared pool

Rejected because pool overflow and permanent holder loss do not provide a sufficiently explicit
release callback. Framework-owned eviction is required for `AndroidView.onRelease` and other native
resources.

### Model TabRow, LazyList, and Pager with one item-session abstraction

Rejected because the three containers have different residency and ownership needs. Uniform API
shape would preserve unnecessary sessions and prevent the cheapest eager keyed patch path.

## Validation

The hard cut requires deterministic coverage for the four binding rules, key-owned saveable state,
effect disposal before cross-key rebind, reset-before-bind, exactly-once final release, cache
eviction, non-resettable interop fallback, environment revisions, tab selection invalidation,
native pager residency, sticky-header lookup, state publication, and duplicate submissions.

Performance validation uses release builds and exact Demo routes. Diagnostics tab switching must
be followed immediately by forceful long flings that reach the bottom and return to the top.
Operation counters must prove skipped item render, targeted changed-item work, and mounted-tree
reuse before timing results are interpreted.
