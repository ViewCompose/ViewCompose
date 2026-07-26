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
- Stage 3 destination `RenderSession`: complete
- Stage 4 transactional coordinator: complete
- Stage 4 transition retention protocol: complete
- Stage 4 native View transition driver: complete
- Stage 4 public `NavHost`: complete
- Stage 5 restoration and platform back: next

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

Destination sessions are rendered into an unattached candidate container first. A successful
candidate can then be staged hidden, committed, and presented; a failed render or explicit rollback
disposes its composition and destroys its owner without publishing the page. Committed sessions
refresh both their captured CompositionLocal snapshot and latest content closure without replacing
the entry owner or container.

### Stage 4: transactional `NavHost`

- render a candidate destination before publishing the new stack
- commit stack and lifecycle state only after successful candidate render
- roll back the candidate session when rendering fails
- retain outgoing and incoming sessions during transitions
- serialize re-entrant navigation commands on the main thread

The internal `TransactionalNavHostCoordinator` now owns the settled-state transaction boundary.
It attaches the initial stack, executes `push/pop/replaceTop/reset`, refreshes a page before it is
revealed by `pop`, applies host lifecycle caps, and serializes navigation requested while another
page is rendering. A destination render failure rolls back the pure back-stack transaction and
discards commands emitted by that failed candidate.

The coordinator also owns transition retention. After the stack commits, it publishes an immutable
transition scene containing the outgoing page, incoming page, retained entries, visibility set, and
layer order. Both transition participants remain visible; the incoming page is the only interactive
`RESUMED` destination and the outgoing page remains `STARTED`. Permanently removed sessions are
destroyed only when the transition reaches a terminal result.

Transition drivers are cancellable policy adapters. Completion settles the committed target;
explicit cancellation also settles that target and never rolls the stack back. A newer navigation
command redirects the active transition by cancelling its visual work, settling its committed
target, and then preparing the next transaction. Stale completion callbacks are ignored by
transition ID. Host destruction cancels visual work and destroys every retained page immediately.

The public `NavHost` mounts this coordinator through the existing transactional `AndroidView`
interop node. Its configuration is staged during parent rendering and applied only by the node's
commit effect. Parent render rollback therefore cannot attach a controller, publish a destination,
or leak an entry owner. Removing the node or destroying its lifecycle owner tears down every child
session and unbinds the controller.

`NavHostController` is the only application-facing mutation handle. It can bind to exactly one host,
rejects commands while detached, and maps coordinator results to public `Committed`, `NoChange`,
`Queued`, and `Failed` results without exposing transition internals. The same controller can mount a
new host after release while retaining its pure back-stack snapshot.

```kotlin
val navController = rememberNavHostController(
    startDestination = NavRoute("home"),
)

NavHost(
    controller = navController,
) { entry ->
    when (entry.route.name) {
        "home" -> HomePage()
        "details" -> DetailsPage(entry.route)
    }
}
```

The Android View driver uses cancellable property animation after commit. Forward/back motion honors
the host layout direction, supports slide, fade-only, and disabled policies, and resets all mutated
View properties on completion or cancellation. An unlaid-out or detached host settles immediately
so invisible animation can never retain removed page resources indefinitely.

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

Failures before back-stack commit preserve the old stack, visible page, and lifecycle ownership.
An unexpected failure while applying effects after stack commit marks the coordinator `Failed`;
further commands are rejected until the host is destroyed instead of continuing on partial state.

A visual transition begins only after the candidate page and back-stack transaction commit. Visual
cancellation cannot undo application state: all terminal paths converge on the committed target
snapshot. Removed entry resources remain addressable during the transition and are cleared in
top-first order at its terminal boundary. Only the active transition ID may complete; callbacks from
redirected or destroyed transitions have no effect.

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
