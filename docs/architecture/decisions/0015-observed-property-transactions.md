# ADR-0015: Observed property transactions

- Status: Accepted
- Date: 2026-08-16
- Extends: [ADR-0008](./0008-transactional-effect-lifecycle.md) and
  [ADR-0012](./0012-lazy-collection-logical-and-physical-ownership.md)

## Context

ViewCompose already supports compiler-independent group recomposition. Explicit boundaries can
skip stable declaration bodies, and Android Renderer can patch a reused View when a new VNode
reaches it. A State read that determines many leaf properties nevertheless invalidates an ancestor
declaration, rebuilds its VNode results, and sends the complete root tree through reconciliation.

The accepted complex-layout control exposed the consequence. ViewCompose had a lower median than
the direct Android Views control but a `41.187 ms` P95 versus `16.222 ms`. Perfetto showed no lock,
I/O, or foreground-GC stall. Slow frames synchronously performed full declaration, tree diff, and
Android View traversal; placement on a LITTLE CPU amplified that work. Local allocation, physical
tree, subtree-proof, reconcile, and compilation experiments did not materially close the tail.

Compose compiler changed flags and restart lambdas are unavailable. ViewCompose therefore cannot
infer which Kotlin expressions are independently safe to rerun, and it cannot promise automatic
property skipping for arbitrary captures. It can provide an explicit contract whose ownership and
failure semantics match its existing Snapshot, RenderSession, VNode, and native rollback model.

## Decision

ViewCompose adds renderer-neutral observed property transactions as an explicit Q3 capability.

An observed value or observed NodeSpec declares a synchronous reader plus explicit ordinary inputs.
Snapshot State reads made by that reader belong to a session-owned property observation, not the
surrounding composer scope. Equal inputs allow the committed reader and value to remain active;
changing non-State captures without changing inputs is unsupported and documented as the
compiler-independent boundary.

One RenderSession registry owns every observation. It associates the property source with the
logical emitting scope, captures the current Local environment, coalesces invalidations, reads all
dirty sources in one pinned Snapshot, and prepares dependency and value changes without publishing
them. Full composition reconciles registry membership transactionally. Removal and session disposal
release observations only after logical ownership ends.

Observed properties may replace only the NodeSpec of the same logical node. Node type, key,
Modifier, children, and environment ownership remain structural and require full composition. A
contract violation is reported rather than silently falling back, because fallback would hide both
stale-capture mistakes and unbounded performance.

A successful full renderer frame returns opaque property targets. A property frame addresses those
targets directly, compares previous and candidate VNodes, and never reconciles siblings or
children. Renderers preflight and checkpoint the complete batch. Any read or native binding failure
aborts candidate observations and restores every earlier native mutation before the previous
property values remain authoritative. Commit effects run only after the entire batch succeeds.

Framework environment changes remain host owned. A full render caused by locale, resource, theme,
density, font-scale, or layout-direction changes replaces captured Local snapshots and readers.
When structural and property work coalesce, the structural frame wins and commits both candidate
sets at the existing single frame boundary.

The first typed widget integration is observed Text content, accompanied by a low-level observed
NodeSpec path for renderer-neutral custom nodes. More typed integrations must reuse the same
registry and renderer transaction; they must not add widget-specific listeners or renderer-owned
State subscriptions.

## Consequences

- Property-heavy State updates can approach retained Android View mutation without abandoning the
  declarative source or native rollback contract.
- Explicit inputs replace compiler stability inference. The API is predictable but more deliberate
  than Compose syntax.
- Structural updates remain more expensive and separately measurable. Observed properties are not
  a second hidden child-tree model.
- VNode and core-render SPI gain opaque property identity and exact-target transaction concepts.
- RenderSession becomes the only coordinator allowed to combine composition, property, native,
  effect, overlay, and diagnostic commit order.
- A renderer must either implement atomic observed-property patches or reject activation of that
  capability; it may not partially apply a batch.
- Debug tooling can describe property patches, but inactive tooling remains outside the hot path
  under ADR-0009.

## Rejected alternatives

### Add State overloads that listen inside Android binders

Rejected because the Android View would own logical observation, disposal and dependency changes
would escape RenderSession, other renderers would diverge, and native callbacks could mutate a
committed frame outside rollback and effect ordering.

### Treat every RecomposeBoundary as a property transaction

Rejected because boundaries may emit zero, one, or many nodes and may change structure. Updating
them independently requires a second child-tree ownership and anchoring model. They remain the
explicit structural restart primitive.

### Re-run the root but optimize more comparisons

Rejected by measurement. Several comparison, allocation, grouping, physical-depth, and compilation
experiments did not materially improve P95 because the algorithm still visits the complete update
surface before direct Android mutation finishes.

### Infer observed properties from arbitrary State captures

Rejected because the project has no Compose compiler, changed flags, stability inference, or safe
restart-lambda generation. Runtime reflection cannot recover equivalent semantics.

### Fall back silently when a property changes structure

Rejected because it turns a bounded API into workload-dependent whole-tree work and can conceal an
incorrect inputs list. Contract violations are observable failures with a clear structural remedy.

## Public API and module impact

- `viewcompose-ui-contract` carries opaque observed-property identity on VNode.
- `viewcompose-ui-foundation` owns Q3 observed values, observed NodeSpec emission, the candidate
  registry, frame scheduling, failure reporting, and core-render SPI.
- `viewcompose-host-android` translates core property frames without exposing Android Views upward.
- `viewcompose-renderer-android` owns exact mounted-target indexing, binder preflight, atomic apply,
  rollback, commit effects, and diagnostics.
- Demo and benchmark fixtures separate property and structural update actions and compare
  ViewCompose, Compose, and Android Views under one revisioned workload contract.

## Validation and rollout

Implementation follows the active
[observed property transactions plan](../../project/plans/observed-property-transactions.md). The
hard cut requires Q3 KDoc and compiled samples, fake-engine and Android failure injection, Local and
resource-change tests, lifecycle/disposal tests, API and module documentation, Chinese mirrors, one
release changeset, accepted three-engine benchmark evidence, and all repository quality gates.
