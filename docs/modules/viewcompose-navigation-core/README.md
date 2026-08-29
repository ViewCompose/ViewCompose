---
schema_version: 2
document_id: module.viewcompose-navigation-core
doc_type: module
owner:
  kind: module
  id: viewcompose-navigation-core
version_lane: released
capability_ids:
  - navigation.deep-links
  - navigation.host
  - navigation.scene-projection
artifact_ids:
  - viewcompose-navigation-core
sample_ids:
  - module.navigation-core-dependency
  - module.navigation-core-graph
  - module.navigation-core-transaction
  - module.navigation-core-stacks
  - module.navigation-core-deep-link
  - module.navigation-core-scene-projection
  - module.navigation-core-execution-plan
coordinate: com.viewcompose:viewcompose-navigation-core:0.1.0-alpha03
minimal_usage_sample_id: module.navigation-core-dependency
---

# Navigation Core

`viewcompose-navigation-core` is ViewCompose's platform-neutral navigation state machine. It owns
immutable routes and graph declarations, strict deep-link resolution, single- and multi-stack
snapshots, rollback-safe two-phase transactions, page-lifecycle planning, and adaptive pane-scene
selection.

The module contains no Android or AndroidX types. `Activity`, predictive Back, `LifecycleOwner`,
`SavedStateRegistryOwner`, View mounting, transitions, and process-death adapters live in
`viewcompose-navigation-android`.

## Artifact and stability

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="navigation-core-module-dependency" sample_id="module.navigation-core-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-navigation-core:0.1.0-alpha03")
}
```

- Stability: **Alpha**. Snapshot compatibility and route contracts may evolve between alpha releases.
- Platform: Kotlin/JVM library targeting Java 11.
- Direct ViewCompose dependencies: none.
- Platform boundary: no Android, View, lifecycle, saved-state, or rendering types are allowed.

## Graphs and routes

{/* compiled-region source="viewcompose-navigation-core/src/test/samples/com/viewcompose/navigation/core/samples/NavigationCoreSamples.kt" region="navigation-core-graph" sample_id="module.navigation-core-graph" build_target=":viewcompose-navigation-core:compileTestKotlin" */}
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

{/* compiled-region source="viewcompose-navigation-core/src/test/samples/com/viewcompose/navigation/core/samples/NavigationCoreSamples.kt" region="navigation-core-transaction" sample_id="module.navigation-core-transaction" build_target=":viewcompose-navigation-core:compileTestKotlin" */}
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

{/* compiled-region source="viewcompose-navigation-core/src/test/samples/com/viewcompose/navigation/core/samples/NavigationCoreSamples.kt" region="navigation-core-stacks" sample_id="module.navigation-core-stacks" build_target=":viewcompose-navigation-core:compileTestKotlin" */}
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

{/* compiled-region source="viewcompose-navigation-core/src/test/samples/com/viewcompose/navigation/core/samples/NavigationCoreSamples.kt" region="navigation-core-deep-link" sample_id="module.navigation-core-deep-link" build_target=":viewcompose-navigation-core:compileTestKotlin" */}
```kotlin
val graph = navGraph(
    route = "root",
    startDestination = NavRoute("home"),
) {
    destination("home")
    destination(
        route = "shared-image",
        deepLinks = listOf(
            NavDeepLink(
                action = "android.intent.action.SEND",
                mimeType = "image/*",
            ),
        ),
    )
}
val result = graph.resolveDeepLink(
    NavDeepLinkRequest(
        action = "android.intent.action.SEND",
        mimeType = "image/png",
    ),
)
check((result as NavDeepLinkResolution.Matched).match.route.name == "shared-image")
```

Declarations are strict URI, action, MIME, or combined allowlists. Every declared dimension is
required; extra request dimensions are inert. MIME values compare case-insensitively and support
exact `type/subtype`, `type/*`, `*/subtype`, and `*/*` constraints. Actions compare exactly.
Combined declarations rank before a matching single-dimension declaration. URI specificity then
ranks static path segments above placeholders and declared query values. Equally specific winners
are rejected instead of depending on declaration order.

URI placeholders occupy a complete path segment or query value. URI fragments, user info,
malformed percent encoding, invalid UTF-8, duplicate query names, undeclared types, and partial
placeholders are rejected. Typed floating-point values must be finite and booleans accept only
lowercase `true` or `false`.

Extra input query parameters are tolerated but inert. An undeclared value never enters
`NavRoute.arguments`, changes specificity, resolves ambiguity, selects a retained stack, or chooses
a launch mode. Applications that require an exact or signed URL validate the complete input before
passing it to the resolver.

Resolution returns one of four outcomes:

- `Matched` contains the winning declaration and decoded `NavRoute`;
- `NoMatch` means a valid request did not satisfy any complete declaration;
- `Rejected` reports malformed input, typed-argument failure, or an ambiguous best match;
- `Unsupported` means the controller was created without a graph.

A host converts a match to `OpenDeepLink`, which mutates the target stack and selects it in one
transaction. String URI resolution is a convenience overload over `NavDeepLinkRequest`; it is not
a second matching implementation.

This Alpha slice intentionally replaces `NavDeepLinkRejection.matchingPatterns` with
`NavDeepLinkRejection.candidates`. Consumers must inspect the immutable declarations when rendering
diagnostics because an action-only or MIME-only candidate has no URI pattern. No deprecated bridge
or parallel string projection is retained.

## Lifecycle planning

`NavScene` replaces parallel visible and interactive ID sets with one validated, bottom-to-top
semantic projection. Each `NavSceneEntry` records presence, visibility, interaction, coarse
transition phase, content-pane role, and content/overlay layer. It derives independent scene and
entry lifecycle caps without Android types or frame-rate progress.

`NavLifecyclePlanner` accepts destination records plus that scene and applies one rule:

```text
effective destination lifecycle = min(host cap, scene cap, entry cap)
```

Prepared and hidden entries cap at `Created`; covered and active-transition entries cap at
`Started`; only retained, visible, interactive, settled entries may reach `Resumed`. A popped entry
that is still exiting caps at `Created`, and terminal removal targets `Destroyed`. An active
transition scene rejects every interactive entry, preventing premature `Resumed` state by
construction.

Owners removed from retention transition to `Destroyed` and cannot be resurrected. Downward and
destroy transitions are ordered before upward transitions so replacing the interactive destination
does not temporarily leave two owners resumed. Graph-owner targets are the highest effective state
among their descendants, so parents never fall below active children. The Android module applies
the resulting immutable `NavLifecyclePlan` to concrete owners.

{/* compiled-region source="viewcompose-navigation-core/src/test/samples/com/viewcompose/navigation/core/samples/NavigationCoreSamples.kt" region="navigation-core-scene-projection" sample_id="module.navigation-core-scene-projection" build_target=":viewcompose-navigation-core:compileTestKotlin" */}
```kotlin
val list = NavEntry(NavEntryId("list"), NavRoute("list"))
val detail = NavEntry(NavEntryId("detail"), NavRoute("detail"))
val scene = NavScene(
    listOf(
        NavSceneEntry(
            entryId = list.id,
            presence = NavEntryPresence.Retained,
            visibility = NavSceneVisibility.Hidden,
            interaction = NavSceneInteraction.NonInteractive,
            transitionPhase = NavSceneTransitionPhase.Settled,
            paneRole = null,
        ),
        NavSceneEntry(
            entryId = detail.id,
            presence = NavEntryPresence.Retained,
            visibility = NavSceneVisibility.Visible,
            interaction = NavSceneInteraction.Interactive,
            transitionPhase = NavSceneTransitionPhase.Settled,
            paneRole = NavPaneRole.Primary,
        ),
    ),
)
val plan = NavLifecyclePlanner.plan(
    currentStates = mapOf(
        list.id to NavEntryLifecycleState.Resumed,
        detail.id to NavEntryLifecycleState.Created,
    ),
    entries = listOf(list, detail),
    scene = scene,
    hostState = NavHostLifecycleState.Resumed,
)

check(plan.targetStates[list.id] == NavEntryLifecycleState.Created)
check(plan.targetStates[detail.id] == NavEntryLifecycleState.Resumed)
check(plan.transitions.first().entryId == list.id)
```

## Unified execution reducer

`NavExecutionReducer` is the single policy boundary above stack transactions, pane scenes, and
lifecycle projection. Its `settled`, `transition`, and `predictivePreview` entry points make each
event's preconditions explicit, then delegate to one implementation and return the same immutable
`NavExecutionPlan`. `reconcile` retains the plan's stack and scene decision while recalculating
outer-host lifecycle, presentation inventory, retention, or Back ownership.

The plan contains the candidate or committed stack delta, exact semantic scene, ordered lifecycle
targets, presentation prepare/refresh/retain/evict/dispose lists, input/focus/accessibility and Back
ownership, rendering suspension, pre-commit rollback, and terminal cleanup. It contains IDs and
Core values only—never a View, `LifecycleOwner`, callback, or animation progress value. Platform
adapters must prepare every requested presentation before committing a transaction, publish effects
in plan order after commit, and use the recorded rollback or cleanup lists instead of deriving a
second policy from controller state.

{/* compiled-region source="viewcompose-navigation-core/src/test/samples/com/viewcompose/navigation/core/samples/NavigationCoreSamples.kt" region="navigation-core-execution-plan" sample_id="module.navigation-core-execution-plan" build_target=":viewcompose-navigation-core:compileTestKotlin" */}
```kotlin
val plan = NavExecutionReducer.transition(
    currentLifecycleStates = mapOf(
        before.top.id to NavEntryLifecycleState.Resumed,
    ),
    transaction = transaction,
    beforePaneScene = NavPaneScene(
        listOf(NavPane(NavPaneRole.Primary, before.top.id)),
    ),
    afterPaneScene = NavPaneScene(
        listOf(NavPane(NavPaneRole.Primary, transaction.after.top.id)),
    ),
    hostState = NavHostLifecycleState.Resumed,
    presentedEntryIds = listOf(before.top.id),
    maxRetainedHiddenPresentations = 0,
)

// A platform adapter prepares these identities before committing the stack.
check(plan.preparePresentationEntryIds == listOf(transaction.after.top.id))
check(plan.inputEntryIds.isEmpty())
check(plan.rollbackOwnerEntryIds == listOf(transaction.after.top.id))
check(plan.lifecycle.targetStates.values.none(NavEntryLifecycleState.Resumed::equals))
```

All reducer calls are side-effect free and linear in retained entries, graph depth, current owners,
and presentations. `null` is the explicit unbounded hidden-presentation limit; non-negative values
are deterministic oldest-first bounds. The API is Alpha and intentionally has no legacy dual-plan
bridge.

## Execution reducer acceptance

The Phase 5 baseline passed 60/60 Navigation Core tests. A fresh Phase 6 run passed 71/71 with zero
failures, errors, or skips: 11 reducer tests now cover settled, push, pop, reset, predictive preview,
bounded and unbounded retention, reconciliation, candidate Back ownership, immutable snapshots, and
invalid inputs. This is an absolute increase of 11 tests and a normalized increase of 18.3%.

Conclusion: **improved**. Stack mutation, semantic scene, lifecycle, presentation inventory,
interaction, Back, rollback, and terminal cleanup now have one pure deterministic result. This run
does not establish line/branch coverage, Android executor correctness, device behavior, leak
freedom, or representative performance; those dimensions are **inconclusive** here and are covered
by the Android module evidence or Phase 7.

## Adaptive panes

`NavPaneStrategy` converts the active stack into one to three logical panes. `Single` exposes only
the top destination. `BackStack` places the newest retained destinations into contiguous primary,
secondary, and tertiary panes.

Always execute custom strategies through `calculateValidated`. Validation enforces the pane limit,
rejects entries outside the active stack, and requires the active top to remain visible. A
`NavPaneScene` treats all visible panes as interactive by default; hosts may derive a narrower focus
policy when constructing `NavScene`.

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

The scene-projection API is an Alpha hard cut. Replace both `NavLifecyclePlanner.plan` overloads
that accepted `retainedEntryIds`, `visibleEntryIds`, and `interactiveEntryId(s)` with the single
`entries` plus `scene` overload. No deprecated bridge or dual planner exists.

The `0.1.0-alpha03` line establishes immutable snapshots, single-pending two-phase transactions,
independent retained stacks, strict URI matching, graph-hierarchy validation, lifecycle planning,
and three logical pane roles. Persist only committed snapshots. Do not persist controllers,
transactions, strategies, factories, or host lifecycle plans.
