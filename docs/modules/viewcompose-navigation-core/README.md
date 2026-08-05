# Navigation Core

`viewcompose-navigation-core` is ViewCompose's platform-neutral navigation state machine. It owns
immutable routes and graph declarations, strict deep-link resolution, single- and multi-stack
snapshots, rollback-safe two-phase transactions, page-lifecycle planning, and adaptive pane-scene
selection.

The module contains no Android or AndroidX types. `Activity`, predictive Back, `LifecycleOwner`,
`SavedStateRegistryOwner`, View mounting, transitions, and process-death adapters live in
`viewcompose-navigation`.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-navigation-core:0.1.0-alpha02")
}
```

- Stability: **Alpha**. Snapshot compatibility and route contracts may evolve between alpha releases.
- Platform: Kotlin/JVM library targeting Java 11.
- Direct ViewCompose dependencies: none.
- Platform boundary: no Android, View, lifecycle, saved-state, or rendering types are allowed.

## Graphs and routes

```kotlin
val graph = navGraph(
    route = "root",
    startDestination = NavRoute("home"),
) {
    destination("home")
    navigation(
        route = "account",
        startDestination = NavRoute("profile"),
    ) {
        destination("profile")
        destination("settings")
    }
}
```

Route names are unique across the complete root graph. A nested graph's start destination must be a
direct child. Requesting a graph route recursively enters its start chain and produces a leaf
`NavGraphResolution` plus a root-to-leaf graph-owner path.

`NavRoute` arguments use the closed `NavValue` model. Constructors copy every collection. Route
equality includes arguments, so `SingleTop` treats the same route name with different arguments as a
different request.

`NavEntryId` identifies a concrete destination or graph owner, not a route. IDs remain stable while
their owners are retained and must be persisted with navigation snapshots. Graph owners allow an
Android host to share lifecycle, saved state, and ViewModels across destinations inside one graph
instance without putting Android concepts in this module.

## Two-phase transactions

```kotlin
when (val preparation = controller.prepare(NavCommand.Push(NavRoute("details")))) {
    is NavPreparation.NoChange -> Unit
    is NavPreparation.Ready -> preparation.transaction.use { transaction ->
        // First mount transaction.after and apply owner lifecycle changes.
        transaction.commit()
    }
}
```

`prepare` computes a prospective immutable state and entry delta without publishing either. A host
renders and applies lifecycle ownership first, then calls `commit`. If mounting fails, `rollback`
releases the pending slot and leaves the committed state unchanged. Closing a prepared transaction
rolls it back automatically.

Only one transaction may be pending per controller. Transaction completion is single-use and
synchronized. A `NavStackMutation` spans every retained stack so selecting tabs or opening a deep
link cannot leave platform owners out of sync.

No-change outcomes are explicit:

- a root entry cannot be popped;
- `SingleTop`, replace, or reset already targets the effective destination;
- a selected stack is already active with the requested policy.

## Independent retained stacks

```kotlin
val configuration = NavStackConfiguration(
    initialStackId = NavStackId("home"),
    stacks = listOf(
        NavStackSpec(NavStackId("home"), NavRoute("home")),
        NavStackSpec(NavStackId("account"), NavRoute("profile")),
    ),
    rootBackBehavior = NavRootBackBehavior.PreviousStack,
)
val controller = NavBackStackController.create(configuration, graph)
```

Each declared stack owns an independent non-empty back stack and independent destination and graph
IDs. `Preserve` resumes a stack exactly where it was left; `PopToRoot` removes entries above its
root before selection. Selection history records inactive stacks from oldest to newest.

`systemBackCommand()` is a pure query. It returns `Pop` for a non-root active stack, optionally
returns `PopStackHistory` at root, or returns `null` so the Android host can delegate Back outward.

`NavStackSetSnapshot` validates that no destination or graph-owner identity crosses stack
boundaries. This invariant prevents lifecycle, saved-state, and ViewModel ownership from leaking
between tabs.

## Deep links

```kotlin
val profileLink = NavDeepLink(
    uriPattern = "https://viewcompose.com/users/{userId}",
    argumentTypes = mapOf("userId" to NavDeepLinkArgumentType.Long),
    targetStackId = NavStackId("account"),
)
```

Patterns are strict allowlists. Placeholders occupy a complete path segment or query value. URI
fragments, user info, malformed percent encoding, invalid UTF-8, duplicate query names, undeclared
types, and partial placeholders are rejected. Typed floating-point values must be finite and
booleans accept only lowercase `true` or `false`.

Resolution returns one of four outcomes:

- `Matched` contains the winning declaration and decoded `NavRoute`;
- `NoMatch` means a valid URI did not enter any registered pattern domain;
- `Rejected` reports malformed input, typed-argument failure, or an ambiguous best match;
- `Unsupported` means the controller was created without a graph.

Static path segments rank above placeholders, followed by query specificity. Equally specific best
matches are rejected instead of depending on declaration order. A host converts a match to
`OpenDeepLink`, which mutates the target stack and selects it in one transaction.

## Lifecycle planning

`NavLifecyclePlanner` consumes stable owner IDs, not Android `LifecycleOwner` objects. Retained
background owners target `Created`, visible owners target `Started`, and interactive owners target
`Resumed`. The host lifecycle caps every target.

Owners removed from retention transition to `Destroyed` and cannot be resurrected. Downward and
destroy transitions are ordered before upward transitions so replacing the interactive destination
does not temporarily leave two owners resumed. The Android module applies the resulting
`NavLifecyclePlan` to concrete owners.

## Adaptive panes

`NavPaneStrategy` converts the active stack into one to three logical panes. `Single` exposes only
the top destination. `BackStack` places the newest retained destinations into contiguous primary,
secondary, and tertiary panes.

Always execute custom strategies through `calculateValidated`. Validation enforces the pane limit,
rejects entries outside the active stack, and requires the active top to remain visible. A
`NavPaneScene` treats all visible panes as interactive by default; hosts may derive a narrower focus
set before lifecycle planning.

## Save and restore contract

Persist the complete immutable `NavStackSetSnapshot`, including route arguments, destination IDs,
graph-owner entries, active stack, and selection history. The Android integration encodes this model
into `SavedStateRegistry` values.

Graph-aware state must be restored with the current `NavGraph`. Restore fails closed if a route was
removed, resolves to another leaf, or moved to a different graph hierarchy, because reusing its old
platform owner would be unsafe. Multi-stack restore also requires the current configuration to have
exactly the saved stack IDs. Pending transactions are never part of persisted state.

## Related documentation

- [Complete navigation guide](../../guides/navigation.md)
- [Lifecycle and saved-state architecture](../../architecture/lifecycle-and-saved-state.md)
- [Session container architecture](../../architecture/session-containers.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

The complete generated reference is available in the
[`viewcompose-navigation-core` API tree](https://docs.viewcompose.com/api/viewcompose-navigation-core/current/).

## Compatibility notes

The `0.1.0-alpha02` line establishes immutable snapshots, single-pending two-phase transactions,
independent retained stacks, strict URI matching, graph-hierarchy validation, lifecycle planning,
and three logical pane roles. Persist only committed snapshots. Do not persist controllers,
transactions, strategies, factories, or host lifecycle plans.
