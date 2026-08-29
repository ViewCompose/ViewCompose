---
schema_version: 2
document_id: guide.navigation-production-host
doc_type: guide
owner:
  kind: capability
  id: navigation.host
version_lane: released
capability_ids:
  - navigation.deep-links
  - navigation.destination-context
  - navigation.host
  - navigation.kotlinx-serialization-routes
  - navigation.presentation-retention
  - navigation.result-consumption
  - navigation.results
  - navigation.scene-projection
  - navigation.typed-route-host
  - navigation.typed-routes
artifact_ids:
  - viewcompose-navigation-android
  - viewcompose-navigation-core
  - viewcompose-navigation-kotlinx-serialization
sample_ids:
  - module.navigation-android-results
  - module.navigation-core-execution-plan
  - tutorial.navigation
task: Configure one production NavHost with restoration, system Back, and explicit failure handling.
success_checks:
  - Destination state survives host recreation and valid process-state restoration.
  - Programmatic and system Back operations observe the same committed stack.
  - A result pop reaches the surviving destination once and only after it resumes.
  - Failed destination preparation preserves the previously visible destination.
  - Hidden presentation resources follow one explicit bounded retention policy without clearing entry state.
  - Destination content observes the nearest stable entry context without treating transition progress as lifecycle.
  - One typed route declaration is reused for graph registration, controller commands, and entry decoding.
  - A modal overlay alone owns input, accessibility, and RESUMED lifecycle while covered content stays STARTED.
failure_checks:
  - A controller is attached to more than one active NavHost or receives commands while detached.
  - NavHost is mounted without a LocalViewModelStoreOwner boundary.
  - Route or graph changes silently reuse incompatible restored ownership state.
  - A queued command is treated as committed completion.
  - RetainAll is selected without application-specific memory and rebuild evidence.
  - Resource work is started from destination presentation state instead of AndroidX Lifecycle.
  - Typed codecs retain page objects or depend on mutable process-only registration state.
  - An overlay strategy emits a non-trailing overlay set or lets input fall through to covered content.
---

# Configure a production navigation host

Use this guide after completing the [navigation tutorial](../tutorials/navigation.md). It turns the
two-destination example into one host with explicit restoration, Back, ownership, and failure
policy. For the runtime reasons behind these rules, see the
[navigation architecture](../architecture/navigation.md). Exhaustive signatures and optional
motion, deep-link, multi-stack, graph-owner, and adaptive-pane APIs remain in the
[Navigation Android module manual](../modules/viewcompose-navigation-android/README.md).

## Choose one controller owner

Create the controller with `rememberNavHostController` in the same UI owner that mounts `NavHost`.
Never cache it in a process singleton or share it across hosts. It saves committed stack and owner
identities, arguments, and destination state through the nearest ViewCompose registry.

Mount `NavHost` below both `LocalLifecycleOwner` and `LocalViewModelStoreOwner`. Standard Activity
and Fragment `setUiContent` hosts do this; `renderInto` integrations must provide both because no
private fallback store is created.

Use a stable `NavGraph` when routes need typed arguments, nested ownership, or deep links. Restore
fails closed to the start destination when the graph rejects a saved hierarchy.

Declare one stable `NavRouteSpec<T>` per application route and reuse it in `destination`,
`navigation`, typed commands, and `NavEntry.toRoute`. Encode durable IDs and small primitives, load
domain objects in the ViewModel, and keep names and schemas restore-compatible. Codec errors occur
before the host transaction.

The optional [Kotlinx adapter](../modules/viewcompose-navigation-kotlinx-serialization/README.md)
derives specs for flat serialized routes; unsupported shapes use explicit Core codecs.

Model external navigation with `NavDeepLinkRequest`. A declaration may constrain URI, action,
MIME type, or all three, and every constraint must match. Android maps only Intent data, action,
and type. Inspect `NavDeepLinkResult`, and validate the complete URI at security boundaries.

## Restore state and connect platform Back

Leave `systemBackEnabled = true`. While `STARTED` and able to pop, `NavHost` uses the nearest
View-tree NavigationEvent owner, or Activity Back only when none exists. A root delegates outward;
do not add a second owner or callback around the host.

Call `popBackStack` for an in-UI Back action. System Back and predictive Back then use the same
transaction. Predictive preview never publishes its candidate: cancel restores the committed
scene, and completion follows the programmatic-pop path.

Declare a stable `NavResultKey`, pop with its value, and observe it with `NavResultEffect` in the
previous page. Delivery is saved, FIFO, and waits for `RESUMED`; explicit acknowledgement or retry
uses the destination-context inbox.

## Keep route rendering exhaustive

Render every accepted route in the `NavHost` content block and reject unknown routes immediately.
It runs in the destination-owned lifecycle, stores, saveable namespace, and child render session;
repeated pushes create distinct owners unless launch mode reuses one. Scoped stores survive
configuration recreation with their parent, clear on permanent removal, and recreate ViewModels
from restored state after process death. Change `contentKey` only for a non-observable parent
capture; changing a host owner, controller, factory, debug identity, or host `key` recreates the
native host.

## Observe destination presentation without duplicating Lifecycle

Read `LocalNavDestinationContext.current` during destination DSL declaration when content needs to
distinguish presentation roles, and capture that nearest holder for callbacks. Its entry survives
hidden-presentation disposal; permanent removal ends updates and destroys its Lifecycle. Active
resources still follow AndroidX Lifecycle because this context is coarse and has no frame progress.

## Add modal destinations without a second lifecycle model

Pass a stable
[`NavSceneStrategies.trailingOverlays`](../modules/viewcompose-navigation-android/README.md#modal-navigation-scenes)
strategy to `NavHost`. Its predicate selects only a trailing stack suffix; pane policy lays out the
prefix. Destination content draws its surface/scrim, while the host owns modal input and lifecycle.
Use ordinary Back, result, restore, and predictive APIs rather than parallel page owners.

## Choose presentation retention deliberately

Keep the default `NavPresentationRetentionPolicy.DisposeWhenHidden` unless device evidence for a
destination proves rebuild cost unacceptable. It releases hidden native presentation but retains
owner state. Use measured `Bounded(n)` caching when needed; `RetainAll` is unbounded. Policy changes
preserve owners, and initial or restored attachment materializes only the current scene layout.

## Keep one policy source in custom integrations

Normal applications use `NavHost`. A custom host must execute each complete `NavExecutionPlan`:
prepare before commit, publish scene/lifecycle/interaction/Back together, then clean up at the
planned boundary. It must not derive parallel lifecycle, retention, or Back policy.

## Handle command outcomes

Every command returns `Committed`, `NoChange`, `Queued`, or `Failed`. Observe `navigationState` for
queued completion and report the structured failure without replacing the committed stack. Use
`onFailure` for logging, fallback, or tests; otherwise `NavHostException` is raised. Pre-commit
failure preserves the old page, and `NavFailure.stackCommitted` marks a post-commit boundary.

## Verify the task

Run the compiled tutorial and the Navigation Android tests:

```bash
./gradlew :samples:tutorials:assembleDebug :viewcompose-navigation-android:testDebugUnitTest
```

On one real host, verify UI and system Back agree; Activity recreation preserves route, entry,
saveable state, and ViewModel identity; predictive Back cancel/commit changes the stack zero/one
times; a pre-commit render failure keeps the prior page; and deep-stack presentation stays within
policy while an evicted page restores its owner state. Detached commands, duplicate pops,
premature owner cleanup, unbounded native retention, or treating `Queued` as completion fail the
configuration.
