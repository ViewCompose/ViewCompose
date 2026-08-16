# Observed Property Transactions Plan

## Status

Active. Architecture and public-contract design are fixed by
[ADR-0015](../../architecture/decisions/0015-observed-property-transactions.md). Implementation has
not yet crossed the public API boundary.

Last verified: 2026-08-16.

Next action: add the Q3 observed-value and observed-node-spec contracts, then implement the
session-owned candidate registry before changing the renderer SPI.

## Maven release changesets

- `release/changes/20260816-plain-text-binding-allocation.json`

The final pull request owns one changeset. Its summary and artifact classifications will expand as
the transaction implementation reaches `viewcompose-ui-contract`, `viewcompose-ui-foundation`,
`viewcompose-host-android`, and `viewcompose-renderer-android`.

## Objective

Remove the algorithmic tail-latency gap for property-heavy updates without weakening ViewCompose's
transactional tree, effect, environment, and Android interop guarantees. Explicitly observed node
properties must update through one renderer-neutral session transaction instead of invalidating the
root declaration, rebuilding the complete VNode tree, and traversing every mounted node.

The 2026-08-16 Samsung SM-G991B investigation is the baseline. Under
`performance.complex-layout@3`, ViewCompose measured `6.023/41.187 ms` frame-CPU P50/P95 while the
Android Views control measured `7.253/16.222 ms`. Perfetto attributed the gap to full declaration,
tree diff, and View traversal work amplified when the main thread ran on a LITTLE CPU. Physical
wrapper removal, recursive subtree proof, reconcile allocation reduction, plain-text allocation,
and full ART compilation did not materially close P95.

## Architectural contract

### Public declaration

1. `observedValue(inputs, read)` is a Q3 UI Foundation value whose State reads belong to a node
   property transaction rather than the surrounding composition scope.
2. First-party widgets may accept typed observed values where the renderer has a complete field
   contract. The first slice covers `Text` content because it dominates the accepted workload.
3. `observedNodeSpec(inputs, read)` plus the low-level observed `emit` path is the renderer-neutral
   escape hatch for a complete `NodeSpec`. It remains type checked against the declared `NodeType`.
4. Captured ordinary Kotlin values are not inferred. Every changing non-State capture must appear
   in `inputs`; equal inputs authorize reuse of the previously committed reader and value.
5. Observed properties retain the node's type, key, Modifier, child list, and environment ownership.
   A reader that attempts to change structural identity fails before native commit. Structural
   changes use normal composition or an explicit `RecomposeBoundary`.

### Runtime and session

1. An observed spec read executes under a nested `RuntimeObservation`, so it does not subscribe the
   root or enclosing composition scope.
2. `RenderSession` owns the observation registry. Logical identity follows the emitting composer
   scope and is committed or abandoned with that scope; no Android View or renderer object owns an
   observation.
3. State invalidations are coalesced per binding and scheduled through the existing session
   runtime. All dirty readers in one frame execute inside one pinned `Snapshot`.
4. Candidate observations, values, dependency changes, and invalidation versions remain isolated
   until the renderer transaction succeeds. Abort disposes only candidates and keeps the previous
   committed observations authoritative.
5. A full composition reconciles the complete active binding set. Skipped groups retain their
   committed bindings, removed groups dispose after successful frame commit, and a failed frame
   cannot detach the previous observations.
6. Captured `LocalSnapshot` is reinstalled around every reader. A changed framework environment,
   resource revision, locale, density, layout direction, or design-system input invalidates the
   binding through full composition and replaces its captured environment before later property
   frames.
7. Explicit host renders, structural State invalidations, and property invalidations may coalesce.
   Any pending structural work wins and produces one full frame whose property candidates are
   committed at the same boundary.

### Renderer transaction

1. A full render returns opaque targets for every observed property identity. UI Foundation never
   casts or retains a platform View.
2. A property frame supplies the target plus previous and candidate VNodes to `CoreRenderEngine`.
   The engine rejects missing, duplicated, cross-session, type-changing, key-changing, Modifier,
   child, or environment mutations.
3. Android Renderer preflights every candidate, checkpoints every affected mounted node, applies
   patches in stable declaration order, and publishes retained-child commit effects only after the
   whole batch succeeds.
4. One failure rebinds all earlier targets to their previous VNodes and leaves their committed
   property values and observations unchanged. Rollback failures are suppressed onto the primary
   failure and reported through the existing render-failure channel.
5. Property-only frames do not reconcile siblings or children and do not scan the mounted tree.
   Target lookup is established by the preceding successful full render.

## Hard-cut rules

1. Do not add Android listeners, Text-specific session state, or binder-owned snapshot observation.
2. Do not silently fall back to a full render when a declared property transaction violates its
   contract. Report the failure so invalid usage cannot hide a performance or correctness defect.
3. Do not retain a compatibility API whose reads still invalidate the root. Existing static widget
   overloads remain static; the observed overload has one documented transaction contract.
4. `RecomposeBoundary` remains the explicit structural restart primitive. Its documentation must
   distinguish subtree recomposition from direct property transactions.
5. Tooling may report property identities and patches, but inactive tooling cannot add recurring
   work to either transaction path.

## Implementation phases

### Phase 0: decision and contract

- In progress — accept ADR-0015, register this plan, assign Q3, and define invariants, failures,
  environment propagation, lifecycle ownership, and the no-compiler inputs boundary.

### Phase 1: candidate registry

- Add a session-confined registry with committed and prepared binding sets, invalidation versions,
  one-Snapshot batch reads, dependency replacement, abort, removal, and disposal.
- Install its context only while `RenderSession` prepares full composition; standalone tree builds
  resolve observed values once without claiming automatic updates.
- Add concurrency, invalidation-during-prepare, equal-value, dependency-switch, abort, removal,
  environment, and disposal tests before connecting a renderer.

### Phase 2: renderer-neutral SPI

- Add opaque observed target and property-patch frame types to the core render contract.
- Extend VNode identity metadata without allowing it to affect semantic node content.
- Make full frames publish exact targets and property frames return commit effects, failures, and
  diagnostics through the same session reporting boundary.
- Add fake-engine tests for batching, structural rejection, target loss, rollback, and combined
  structural/property invalidation.

### Phase 3: Android transaction

- Index observed identities while committing a full mounted tree.
- Reuse existing binder differ and rollback binding, but bypass child reconciliation for legal
  property-only candidates.
- Add multi-node failure injection proving all-or-nothing native values, mounted VNodes, retained
  child submissions, diagnostics, and subsequent retry.

### Phase 4: public DSL and migration

- Add canonical-English Q3 KDoc, compiled samples, module manuals, architecture pages, Compose
  migration guidance, and Simplified Chinese mirrors.
- Add typed observed Text content and the low-level observed NodeSpec path. Migrate first-party
  property-heavy fixtures without reading their State in the enclosing declaration.
- Bump the complex-layout workload revision. Separate property-only update evidence from structural
  add/remove evidence so neither workload hides the other's cost.

### Phase 5: benchmark acceptance

- Run ViewCompose, Compose, and Android Views controls with identical data, actions, settle policy,
  thermal gate, compilation mode, and clock-policy declaration.
- Require five accepted iterations and run-P50 CV at or below `0.15` for every engine before
  interpreting P50/P95.
- The property action succeeds only if ViewCompose P95 materially improves from the fresh
  `41.187 ms` diagnostic control and Perfetto confirms that update frames no longer enter complete
  tree reconciliation. A result outside the materiality threshold triggers another trace-driven
  root-cause pass rather than plan completion.
- Structural action results are reported separately and may remain slower than direct Android
  mutation; they must not regress from the accepted revision-3 context without explanation.

### Phase 6: release validation

- Pass focused module tests, compiled samples, API checks, documentation and translation gates,
  development-tooling isolation, `qaQuick`, site build, and release assembly.
- Interpret accepted benchmark evidence in `docs/tooling/performance.md` with absolute values,
  normalized changes, classification, limitations, and next action.
- Archive this plan only after implementation, migration, tests, performance acceptance, and the
  single final changeset agree.

## Validation matrix

| Area | Required evidence |
| --- | --- |
| Runtime | same-apply coalescing, one Snapshot, dependency replacement, invalidation race, abort, disposal |
| Composition | observed reads do not dirty root; inputs/environment changes replace reader; removed scope disposes |
| Renderer SPI | exact target mapping, no tree scan, structural rejection, frame reporting |
| Android | multi-target atomic patch, reverse rollback, retained submission order, retry after failure |
| API | Q3 KDoc, compiled samples, module manuals, migration mapping, API dump |
| Performance | accepted three-engine property and structural actions plus Perfetto phase attribution |

## Completion criteria

The plan completes only when explicitly observed properties no longer execute the root declaration
or reconcile unaffected siblings, all lifecycle and rollback guarantees are protected by tests,
environment changes replace captured readers automatically, ordinary captures have an enforceable
inputs contract, the three-engine benchmark records an accepted material P95 improvement, and every
repository quality and release gate passes.
