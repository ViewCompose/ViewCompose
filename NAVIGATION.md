# System Navigation

## 1. Scope

ViewCompose navigation treats an Android `Activity`/`Window` as the root platform host only.
Destinations are framework-owned page sessions and do not map to an `Activity` or `Fragment`.

The navigation subsystem is split into two layers:

1. `viewcompose-navigation-core`
   - pure Kotlin/JVM
   - immutable routes and back-stack snapshots
   - two-phase navigation transactions
   - page lifecycle planning
   - persistence contracts
2. `viewcompose-navigation`
   - destination `RenderSession` ownership
   - AndroidX `LifecycleOwner`, `ViewModelStoreOwner`, and SavedState adapters
   - back dispatch, transitions, and destination container Views

The Android integration remains isolated from existing application entry points during incubation.
It does not expose `NavHost` until page rendering and navigation commit share one rollback boundary.

Current feature-branch status:

- Stage 1 navigation kernel: complete
- Stage 2 lifecycle kernel: complete
- Stage 3 Android page owner cluster: complete
- Stage 3 destination `RenderSession`: next
- Stages 4–5: pending

## 2. P0 delivery plan

### Stage 1: navigation kernel

- stable `NavEntryId`
- typed, persistence-safe route values
- non-empty immutable back stack
- `push`, `pop`, `replaceTop`, and `reset`
- single-top behavior
- prepare/commit/rollback transaction protocol
- deterministic stack snapshot restoration

### Stage 2: page lifecycle kernel

- host lifecycle caps destination lifecycle
- exactly one interactive `RESUMED` destination
- visible transition participants remain at least `STARTED`
- retained hidden destinations remain `CREATED`
- removed destinations become `DESTROYED`
- downward transitions are applied before upward transitions

### Stage 3: Android page ownership

- one child `RenderSession` per `NavEntry`
- one child saveable-state namespace per entry
- one `ViewModelStore` per entry
- one AndroidX `LifecycleRegistry` per entry
- captured CompositionLocal snapshot per entry
- entry resources survive hiding and are cleared only after permanent removal

### Stage 4: transactional `NavHost`

- render a candidate destination before publishing the new stack
- commit stack and lifecycle state only after successful candidate render
- roll back the candidate session when rendering fails
- retain outgoing and incoming sessions during transitions
- serialize re-entrant navigation commands on the main thread

### Stage 5: restoration and platform back

- save and restore the back stack and each entry state across host recreation/process death
- connect Android back dispatch without making Activity/Fragment a destination owner
- define root-pop delegation to the platform host

## 3. Transaction invariants

Navigation is a two-phase operation:

1. `prepare` creates an immutable candidate snapshot without publishing it.
2. The Android integration prepares/renders any required page sessions.
3. `commit` publishes the candidate snapshot.
4. `rollback` abandons it and preserves the previous snapshot.

Only one prepared transaction may exist for a controller. A prepared transaction must be committed
or rolled back before another command can be prepared.

Entry IDs allocated by an abandoned transaction are never reused. This prevents stale SavedState,
ViewModel, overlay, or result ownership from being attached to a later page.

## 4. Lifecycle invariants

The destination lifecycle is framework-owned but capped by the root host lifecycle:

| Destination role | Desired state |
| --- | --- |
| interactive top destination | `RESUMED` |
| visible non-interactive/transition destination | `STARTED` |
| retained hidden destination | `CREATED` |
| prepared but not committed destination | `CREATED` |
| permanently removed destination | `DESTROYED` |

When ownership changes, current interactive destinations are downgraded before a new destination is
upgraded. This prevents two destinations from being `RESUMED` at the same time.

Host `DESTROYED` destroys every destination. A destroyed `NavEntryId` cannot be reintroduced.

## 5. Initial non-goals

The first stable merge does not include:

- nested navigation graphs
- multiple tab back stacks
- URI deep-link matching
- predictive-back progress animation
- adaptive multi-pane placement
- compiler-generated route serialization

These features must build on the same transaction and ownership contracts instead of adding a
parallel navigation path.
