---
schema_version: 2
document_id: architecture.scene-derived-navigation-lifecycle-presentation-ownership
doc_type: architecture
slug: /architecture/decisions/scene-derived-navigation-lifecycle-and-presentation-ownership
owner:
  kind: capability
  id: navigation.host
version_lane: version-agnostic
capability_ids:
  - lifecycle.effects
  - lifecycle.flow-collection
  - lifecycle.owner-boundaries
  - navigation.host
  - viewmodel.scoped-owners
artifact_ids:
  - viewcompose-lifecycle-androidx
  - viewcompose-navigation-core
  - viewcompose-navigation-android
  - viewcompose-viewmodel-androidx
sample_ids: []
invariants:
  - One host-neutral Lifecycle DSL surface consumes the nearest Activity, Fragment, destination, graph, preview, or custom-container owner.
  - Effective destination lifecycle is the minimum of host, scene, and entry caps, while navigation presentation remains a separate observable contract.
  - Logical entry ownership survives optional native presentation disposal, and permanent removal disposes presentation before destroying the entry owner.
  - One reducer-produced plan owns stack, scene, lifecycle, presentation, focus, transition, rollback, and terminal cleanup decisions.
evidence:
  - docs/project/plans/navigation-lifecycle-and-scene-evolution.md
  - docs/architecture/decisions/0023-retained-viewmodel-scope-ownership.md
  - viewcompose-navigation-core/src/test/kotlin/com/viewcompose/navigation/core/NavLifecyclePlannerTest.kt
  - viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/TransactionalNavHostCoordinatorTest.kt
  - viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavEntryOwnerStoreTest.kt
---

# ADR-0024: Scene-derived navigation lifecycle and presentation ownership

- Status: Accepted
- Date: 2026-08-29

## Context

ViewCompose pages are native `ViewGroup` presentations hosted below one Activity rather than one
Activity or Fragment per destination. Android therefore cannot supply a complete page lifecycle by
itself. A destination can remain attached while hidden, participate in a transition without owning
input, remain logically retained after its native View is disposed, or share a settled scene with
other interactive panes.

The existing navigation runtime already owns transactional stacks, destination and graph owners,
saved state, ViewModel scope leases, predictive Back, adaptive panes, and rollback. Its lifecycle
projection is nevertheless too narrow: visible and interactive ID sets cannot express transition,
overlay, focus, layer, or presentation-retention semantics. Ordinary push and pop can promote the
incoming destination before motion settles; a popped outgoing destination can remain `STARTED`;
hidden stacks retain complete native presentations without a bound.

AndroidX Lifecycle remains the correct resource-threshold protocol, but it cannot encode whether a
page is covered, entering, exiting, or participating in predictive preview. Creating Activity-,
Fragment-, and navigation-specific Lifecycle DSL APIs would duplicate one consumption contract and
make nested hosts ambiguous. Tying ViewModel or saved-state lifetime to native View retention would
also undo the shared retained-owner boundary established by
[ADR-0023](./0023-retained-viewmodel-scope-ownership.md).

These decisions affect multiple published artifacts, establish future public contracts, and are
costly to reverse. They therefore require an ADR before production changes begin.

## Decision

### One Lifecycle consumption surface

Application DSL content uses the same APIs below every owner boundary:

- `LocalLifecycleOwner.current` for optional nearest-owner lookup;
- `Lifecycle.currentStateAsState()` for observable declarative state;
- `LifecycleStartEffect` and `LifecycleResumeEffect` for paired resource work;
- `collectAsStateWithLifecycle` for threshold-gated Flow collection; and
- `ProvideLifecycleOwner` for an explicit custom boundary.

Activity and Fragment hosts publish system-owned lifecycles. Navigation destinations and graphs
publish framework-owned capped lifecycles. Preview and custom containers publish their explicit
owners. The consumer API does not branch on host type, and ViewCompose will not add
`ActivityLifecycleEffect`, `FragmentLifecycleEffect`, `NavPageLifecycleEffect`, a public
`PageLifecycleOwner`, or a navigation-local Flow collector.

The implementation core remains shared while adapters remain scenario-specific. Activity and
Fragment follow platform callbacks; navigation projects scene state; a custom container supplies
its own owner. Every nested or delayed composition captures and resolves the nearest boundary.

### Lifecycle is a three-cap projection

The effective Android lifecycle for an entry is derived by one pure rule:

```text
effective entry lifecycle = min(host cap, scene cap, entry cap)
```

The accepted pre-host-cap matrix is:

| Destination condition | Scene cap | Entry cap | Effective target |
| --- | --- | --- | --- |
| Prepared candidate before commit | `CREATED` | `CREATED` | `CREATED` |
| Retained hidden entry | `CREATED` | `RESUMED` | `CREATED` |
| Settled visible and interactive entry | `RESUMED` | `RESUMED` | `RESUMED` |
| Forward or back transition participant | `STARTED` | `RESUMED` | `STARTED` |
| Entry covered by an overlay | `STARTED` | `RESUMED` | `STARTED` |
| Settled top overlay | `RESUMED` | `RESUMED` | `RESUMED` |
| Underlying entry covered by an overlay | `STARTED` | `RESUMED` | `STARTED` |
| Popped entry still animating out | at most `STARTED` | `CREATED` | `CREATED` |
| Permanently removed entry | n/a | `DESTROYED` | `DESTROYED` |

An active transition scene cannot contain a `RESUMED` destination. A popped exiting destination is
already absent from retained navigation state and cannot exceed `CREATED`. Multiple entries may be
`RESUMED` only in a settled scene whose pane policy makes them simultaneously interactive. Graph
owners derive their caps from retained descendants while preserving child-down and parent-up
ordering. A destroyed identity cannot be resurrected.

### Presentation state is not Lifecycle state

Navigation publishes one coarse, stable per-entry presentation snapshot. Its frozen semantic fields
are visibility (`Hidden`, `Visible`, or `Covered`), interaction (`Interactive` or
`NonInteractive`), transition phase (`Prepared`, `Entering`, `Settled`, `Exiting`, or
`PredictivePreview`), pane role, and content/overlay layer role. The platform-neutral value family
is owned by Navigation Core and is the same data used by scene projection; Android does not create
a second enum model.

The Android public boundary is one `LocalNavDestinationContext` whose
`NavDestinationContext` holds the stable `NavEntry` identity and observable
`NavDestinationPresentation`. The holder survives presentation disposal and recreation for the
same retained entry. A captured Local stores the holder rather than one stale enum snapshot. There
is no global current-page lookup because nested hosts, overlays, and panes can expose multiple
destinations at once.

Coarse presentation changes may invalidate destination content. Frame-rate transition or
predictive progress is deliberately excluded. Content that needs continuous motion uses a separate
opt-in motion API, so ordinary pages cannot accidentally recompose on every animation frame.

### Entry lifetime and presentation lifetime are independent

One retained entry record owns route identity, destination or graph owner, saved and saveable state,
ViewModel scope lease, destination-context holder, and an optional native presentation. Disposing a
presentation ends its child `RenderSession`, View tree, effects, focus, accessibility state, and
native resources but does not clear the entry owner, mark it `DESTROYED`, or change its context
identity.

The public `NavPresentationRetentionPolicy` family supports three explicit behaviors:

- dispose a presentation when its entry becomes fully hidden;
- retain presentations explicitly for application-proven expensive surfaces; and
- retain a bounded least-recently-used set with a positive maximum and deterministic eviction.

The safe default is not selected by preference. Phase 4 selects it from accepted device memory,
recreation-time, and settled-frame evidence. No default may be unbounded. Permanent entry removal
disposes its presentation before owner destruction and terminal ViewModel clear. Configuration or
process restoration recreates no live View, effect, animation, or candidate transaction.

### One reducer owns navigation decisions

Core evolves toward one pure reducer whose immutable execution plan contains stack mutation, scene
and layer projection, entry and graph lifecycle targets, presentation create/refresh/retain/evict/
dispose operations, focus and input ownership, Back ownership, transition effects, rollback, and
terminal cleanup. Android executors perform `LifecycleRegistry`, View, focus, Back-dispatch, and
animation work; they do not independently reconstruct policy.

Pre-commit failure publishes neither the candidate stack nor its destination context. Post-commit
failure follows one documented terminal recovery path. The existing visible/interactive-only
projection and command-sequenced parallel decisions are deleted in the slices that replace them;
old and new state machines never run side by side.

### Capability and public-contract freeze

The stable capability identities are frozen before declarations change:

| Capability ID | Artifact owner | Public role | Quality and contract fields |
| --- | --- | --- | --- |
| `navigation.scene-projection` | `viewcompose-navigation-core` | immutable scene, entry-presentation values, lifecycle caps, and reducer projection | Q3; behavior, inputs, outputs, state, lifecycle, failure, performance, compatibility |
| `navigation.presentation-retention` | `viewcompose-navigation-android` | `NavPresentationRetentionPolicy` and host selection | Q3; behavior, inputs, outputs, state, lifecycle, concurrency, failure, Android, performance, compatibility |
| `navigation.destination-context` | `viewcompose-navigation-android` | `LocalNavDestinationContext`, stable context holder, and observable coarse presentation | Q3; behavior, outputs, state, lifecycle, concurrency, Android, performance, compatibility |

Governance capability records describe compiled inventory and are therefore added with the first
public declarations, not pre-created by this ADR. Each declaration slice adds one structured impact
record per changed symbol, canonical-English KDoc, a compiled Q3 sample, generated Reference input,
owning module and architecture documentation, migration disposition, locale mirrors, and an
immutable Changeset.

Lifecycle DSL work remains under `lifecycle.owner-boundaries`, `lifecycle.effects`, and
`lifecycle.flow-collection`; it does not receive a navigation alias. If Phase 1 can close its race
and host matrix without a public declaration change, it adds no artificial API. Replacing the
current public visible/interactive lifecycle-planner surface is a breaking Alpha hard cut under
`navigation.scene-projection`, with no deprecated bridge or dual projection.

## Alternatives considered

### Use one Activity per page

Rejected as the framework navigation model. Activity provides a complete platform lifecycle but
cannot represent in-host panes, overlays, retained stacks, shared element ownership, or
transactional child rendering without moving navigation back to system component boundaries.
Applications may still choose multi-Activity architecture outside this module.

### Use Fragment as the page abstraction

Rejected as a required dependency. Fragment supplies mature owner integration but adds a second
manager, transaction model, View lifecycle, saved-state model, and restoration protocol around the
existing ViewCompose transaction engine. ViewCompose instead implements the equivalent owner
contracts directly and keeps Fragment as a supported outer host.

### Derive everything from View attachment and visibility

Rejected because attached Views may be hidden, covered, retained, exiting after pop, or
non-interactive. View state cannot decide resource thresholds or logical ownership safely.

### Add navigation-specific lifecycle callbacks

Rejected because it duplicates AndroidX Lifecycle consumption, makes nesting ambiguous, and still
cannot express presentation semantics without another state source.

### Keep every hidden View tree

Rejected as an unbounded default. Deep and multiple stacks would turn logical retention into native
memory retention, while ViewModel and saveable-state identity do not require a live presentation.

### Dispose every hidden View immediately

Rejected as the only policy. It bounds memory but can impose unacceptable recreation and frame
cost on expensive surfaces. The policy family and measured default preserve the choice without
coupling it to entry ownership.

## Consequences

- ViewCompose assumes responsibility for virtual page lifecycle correctness that Activity or
  Fragment would otherwise provide; the lifecycle, restoration, leak, and device matrices are
  release requirements rather than optional tests.
- Application code receives one Lifecycle DSL family regardless of host and one separate
  navigation presentation context only when it needs navigation semantics.
- Native presentation memory can be bounded independently of ViewModel, saved-state, and route
  identity, at the cost of transactional recreation machinery and explicit policy evidence.
- Navigation Core gains richer public state and a reducer boundary. The Alpha planner hard cut is
  accepted to avoid permanent compatibility layers around known-defective lifecycle ordering.
- The coordinator and transition driver become executors of one plan rather than competing state
  machines. Migration occurs in bounded phases, but no merged phase may retain two authoritative
  paths.

## Affected modules and contracts

- `viewcompose-lifecycle-androidx` owns host-neutral consumption mechanics and their race/failure
  tests; it does not depend on navigation.
- `viewcompose-navigation-core` owns scene semantics, lifecycle caps, and pure reducer output with no
  Android or View type.
- `viewcompose-navigation-android` owns entry and graph LifecycleRegistry application, context
  publication, presentation retention, View hierarchy, focus, Back, and animation execution.
- `viewcompose-viewmodel-androidx` remains the sole retained child-store provider; navigation keeps
  its entry leases across presentation disposal.
- `viewcompose-android` continues to install Activity and Fragment owners and does not create
  navigation page owners.

## Validation and rollout

1. Phase 1 proves the existing Lifecycle DSL across Activity, Fragment, destination, graph, preview,
   and custom boundaries, including commit races, replacement, failure, rapid events, and disposal.
2. Phase 2 introduces the Core scene/cap model and exhaustive or property-based projection tests,
   then removes visible/interactive-only planning in the same breaking slice.
3. Phase 3 applies the accepted transition, overlay, pane, host-cap, graph-order, focus, and terminal
   lifecycle matrix on Android.
4. Phase 4 separates presentations, implements all three policy modes, and selects the default only
   after device memory, restoration, recreation, leak, and frame evidence is interpreted.
5. Phase 5 publishes destination context with compiled Q3 samples and proves stable holder identity,
   delayed capture, presentation recreation, nested hosts, panes, overlays, and removal.
6. Phase 6 converges reducer and executors and deletes superseded command sequencing.
7. Phases 7 and 8 close typed-route/ecosystem dispositions, coverage, device, performance,
   documentation, release, and archival gates.

The active
[Navigation Lifecycle and Scene Evolution Plan](../../project/plans/navigation-lifecycle-and-scene-evolution.md)
owns sequencing and acceptance evidence. Current architecture and module manuals change only when
their corresponding implementation behavior lands.
