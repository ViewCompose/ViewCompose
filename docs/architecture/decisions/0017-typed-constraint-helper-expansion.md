---
schema_version: 2
document_id: architecture.typed-constraint-helper-expansion
doc_type: architecture
slug: /architecture/decisions/typed-constraint-helper-expansion
owner:
  kind: capability
  id: constraintlayout.helpers
version_lane: released
capability_ids:
  - constraintlayout.core
  - constraintlayout.helpers
  - renderer.tree-transactions
artifact_ids:
  - viewcompose-ui-contract
  - viewcompose-constraintlayout-androidx
  - viewcompose-renderer-android
sample_ids:
  - module.constraintlayout-inline
  - module.constraintlayout-helpers
  - module.renderer-tree-transaction
invariants:
  - Grid and CircularFlow remain typed Android-free graph declarations whose native expansion participates in the existing atomic renderer transaction.
  - Generated Grid proxies and restored physical margins stay bounded, stable, removable, and invisible to application anchoring.
evidence:
  - Frozen chain, margin, wrap, physical-edge, Grid, and circle geometry cases; invalid-candidate retention; LTR and RTL; replacement stress; compiled samples; Demo and Preview acceptance.
---

# ADR-0017: Typed ConstraintLayout helper expansion

- Status: Accepted
- Date: 2026-08-21
- Extends: [ADR-0016](./0016-constraintlayout-graph-and-helper-ownership.md)

## Context

The post-release ConstraintLayout parity phase needs Grid, grouped circular placement, explicit
physical edges, and custom chain boundaries without weakening the immutable graph and single-owner
transaction established by ADR-0016.

AndroidX Grid exposes spans and skips through compact strings and may construct native structures
outside ViewCompose's registry. AndroidX CircularFlow is a mutable helper View whose defaults and
member arrays can outlive one declarative candidate. Forwarding either API directly would move
validation after parsing or split generated identity, removal, and rollback ownership again.

The renderer also has to preserve exact baseline and physical gone margins. ConstraintLayout
`2.2.2` records these fields in `ConstraintSet`, but its apply path does not copy every field into
the destination `LayoutParams`.

## Decision

ViewCompose represents the new helper families as typed Android-free graph declarations and keeps
all native expansion inside the existing renderer transaction.

A Grid declaration contains bounded fixed or inferred axes, fill orientation, row and column
weights, dp gaps, typed member spans, and typed skipped rectangles. Graph preflight resolves one
non-overlapping placement for every member within a `50 x 50` bound. The Grid semantic ID is
identity-only and cannot be used as an anchor.

Android Renderer expands each accepted Grid into zero-thickness row and column proxy Views. Their
stable generated IDs, creation, reuse, pruning, and rollback belong to the same container-local
registry as native helpers. One Grid therefore owns at most 50 row plus 50 column proxies. The
renderer does not instantiate AndroidX Grid or parse its string grammar.

A CircularFlow declaration contains one child center and explicit child/radius/angle items. Graph
preflight gives the group exclusive circular-position ownership for every member. Android Renderer
applies ordinary per-child circle constraints, so CircularFlow creates no helper View and no
generated native ID. Angles use AndroidX coordinates: `0f` is above the center and values advance
clockwise.

Logical start/end and physical left/right remain separate anchor planes. A child or horizontal
chain cannot mix those planes. Chain boundaries accept typed parent, child, Guideline, or Barrier
targets with explicit non-negative margins. Parent-wrap contribution is one exhaustive enum rather
than independent Boolean fields.

After `ConstraintSet.applyTo`, Android Renderer restores baseline margins plus physical left/right
gone margins from the accepted graph and resets absent values. This is a version-specific platform
workaround inside the transaction, not a second source of layout truth.

## Consequences

- Invalid spans, skips, references, planes, or competing Chain/Grid/CircularFlow ownership reject
  the complete candidate before native mutation.
- Grid adds `O(rows + columns)` native proxy Views rather than one AndroidX Grid View. The explicit
  50-axis bound keeps identity and child-count growth finite and stress-testable.
- Grid and CircularFlow references express declaration identity only; application code cannot
  depend on generated View IDs or use them as anchors.
- CircularFlow removal clears ordinary circle constraints and requires no helper lifecycle.
- Renderer forks must implement the typed expansion and margin restoration together; silently
  ignoring either transport family violates geometry and rollback correctness.

## Rejected alternatives

### Wrap AndroidX Grid directly

Rejected because its string grammar loses compile-time structure and moves overlap, bounds, and
membership failures behind parsing. Its generated native ownership also does not fit the accepted
registry transaction without another reconciliation path.

### Expose raw spans and skips strings

Rejected because malformed indexes, duplicate members, and overlapping rectangles would become
runtime parser behavior rather than typed authoring and complete-graph validation.

### Retain AndroidX CircularFlow as a helper View

Rejected because explicit radius and angle values already map to ordinary circle constraints. A
mutable helper would add identity, removal, and rollback work without adding supported semantics.

### Expose Grid row and column proxies as anchors

Rejected because proxy count and shape are renderer implementation details that may change without
changing the semantic Grid contract.

## Public API and module impact

- `viewcompose-ui-contract` owns physical anchors, parent-wrap policy, chain boundary transport,
  typed Grid transport, and declarative CircularFlow transport.
- `viewcompose-constraintlayout-androidx` owns Q3 validated DSLs and compiled samples for inline and
  reusable constraint sets.
- `viewcompose-renderer-android` owns graph placement, proxy identity, no-View circle expansion,
  exact AndroidX margin restoration, and atomic rollback.
- Demo and Preview expose dedicated Grid and CircularFlow fixtures; generated infrastructure is not
  presented as an application-facing child.

## Validation and rollout

The six frozen `CL-P2-*` cases require exact chain, margin, wrap, physical-edge, Grid, and circle
geometry; invalid-candidate retention; LTR/RTL behavior; removal; and 1,000-replacement bounds.
Compiled Q3 samples, Demo/Preview compilation, focused device automation, strict API docs,
documentation structure, release intent, and the Phase 2 Changeset are merge gates. Complete
multi-configuration visual and lifecycle acceptance remains Phase 3 work, and no performance claim
is accepted before the Phase 4 direct-native/released-baseline/candidate matrix.
