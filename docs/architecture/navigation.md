---
schema_version: 2
document_id: architecture.navigation-runtime
doc_type: architecture
owner:
  kind: capability
  id: navigation.host
version_lane: released
capability_ids:
  - navigation.deep-links
  - navigation.destination-context
  - navigation.host
  - navigation.presentation-retention
  - navigation.scene-projection
  - viewmodel.owner-boundaries
  - viewmodel.scoped-owners
  - viewmodel.store-resolution
  - viewmodel.saved-state
artifact_ids:
  - viewcompose-navigation-android
  - viewcompose-navigation-core
sample_ids:
  - module.navigation-core-execution-plan
  - tutorial.navigation
invariants:
  - Navigation state commits only after required destination sessions and owners are prepared successfully.
  - Each retained destination and graph instance keeps stable lifecycle, saved-state, and ViewModel ownership independently of optional presentation retention.
  - Navigation entry and graph stores are allocated only by the shared Lifecycle 2.11 scoped-owner provider.
  - Visual transitions and predictive Back never become a second source of navigation state.
  - Restored state is accepted only when it remains compatible with the current graph and stack configuration.
evidence:
  - viewcompose-navigation-core/src/test/kotlin/com/viewcompose/navigation/core/NavBackStackControllerTest.kt
  - viewcompose-navigation-core/src/test/kotlin/com/viewcompose/navigation/core/NavExecutionReducerTest.kt
  - viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/TransactionalNavHostCoordinatorTest.kt
  - viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavEntryOwnerStoreTest.kt
  - viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostPublicApiTest.kt
  - viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostSavedStateTest.kt
  - viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostTransitionCoordinatorTest.kt
  - viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/AdaptiveNavHostCoordinatorTest.kt
  - viewcompose-android/src/test/java/com/viewcompose/android/FragmentHostLifecycleIntegrationTest.kt
  - app/src/androidTest/java/com/viewcompose/NavigationBackDeviceTest.kt
---

# Navigation runtime architecture

## 1. Ownership boundary

ViewCompose navigation uses an Activity or Window as the outer Android host, but a destination is a
framework-owned page rather than an Activity or Fragment. The capability is split across two
published artifacts:

- `viewcompose-navigation-core` owns the platform-neutral route, graph, retained-stack, transaction,
  lifecycle-plan, structured URI/action/MIME deep-link matcher, and pane-scene models;
- `viewcompose-navigation-android` owns destination and graph Android owners, child render sessions,
  native View presentation, SavedState encoding, `Intent` adaptation, system and predictive Back,
  and visual motion.

The split keeps Android ownership out of the state machine while giving the native host one place
to coordinate stack state, rendering, lifecycle, and View hierarchy changes.

## 2. Transaction boundary

Navigation is a two-phase operation. Core `prepare` calculates an immutable candidate state and
entry mutation without publishing it. The Android host prepares the destination owner and child
render session, renders into a staged native container, and only then commits the core transaction.
Preparation failure rolls back the candidate and preserves the old stack, visible scene, and
owners.

Only one prepared transaction may exist for a controller. Re-entrant commands received during
render, lifecycle movement, or visual motion are serialized after the current operation reaches a
terminal state. `NavResult.Queued` therefore means accepted pending work, not committed completion.

After the stack commits, visual motion may be completed, cancelled, or redirected, but it cannot
undo application state. Every terminal visual path settles on the committed target. A failure in a
post-commit effect is reported with `stackCommitted = true`; the host never pretends that the old
stack is still authoritative.

## 3. Unified execution plan

Navigation Core reduces each settled reconciliation, committed transition, or predictive preview
into one immutable `NavExecutionPlan`. The three entry points describe distinct caller moments but
share one reducer implementation and one output vocabulary. That plan is the sole decision source
for the before/after stack, scene and layer order, lifecycle targets, presentation preparation,
refresh, retention, eviction and disposal, render suspension, input/accessibility/focus ownership,
system Back ownership, rollback, and terminal cleanup.

The Android `AndroidNavExecutionPlanExecutor` interprets the plan in a fixed boundary order:
prepare or refresh candidates before stack commit; publish presentations and interaction;
reconcile destination context and owner lifecycle; pause outgoing rendering; then perform safe
eviction. Permanent-removal cleanup remains terminal so an exiting View can finish motion before
its owner is destroyed. Destination containers consume disallowed touch, generic-motion, and key
input, block descendant focus, and leave the accessibility tree while the plan marks them
non-owning. The host Back adapter reads the same plan rather than querying a parallel stack rule.

The reducer is pure and platform-neutral; executors contain Android effects and must not infer a
second policy from View visibility or attachment. Pre-commit preparation failure rolls back only
the plan's candidate presentations and owners without publishing its stack or destination context.
After stack commit, failures preserve the committed target and use the plan's terminal cleanup.

## 4. Destination and graph identity

Every destination entry owns stable route identity, Lifecycle, SavedStateRegistry namespace,
ViewCompose saveable-state namespace, and a lease on a keyed ViewModelStore. Its child render
session and native View tree are an optional presentation rather than part of that logical owner.
The store is allocated by the shared Lifecycle 2.11 `ViewModelScopeProvider`, not by a
navigation-specific map. Pushing the same route twice creates two entry identities. Hidden retained
entries keep their identity and stored state even when no presentation exists.

`NavPresentationRetentionPolicy` controls presentation resources only. The default
`DisposeWhenHidden` removes every fully hidden presentation after transition settlement.
`RetainAll` is an explicit unbounded opt-in, and `Bounded` keeps a deterministic
least-recently-hidden set with a positive maximum. Visible panes and transition participants are
never eviction candidates. A newly visible entry without a presentation is rendered into a hidden
candidate container, staged, and committed before the scene changes; failure disposes all
candidates and keeps the previous stack and scene. Permanent removal always disposes presentation
before owner destruction and ViewModel clear.

The bounded default is evidence-selected. On a physical Pixel 4 XL at API 33, a synthetic heavy
13-entry stack reduced retained presentations from 13 to 1 and process PSS from 191,953 KiB to
185,510 KiB (92.3% fewer presentations and 3.4% lower PSS). Synchronous pop-and-rebuild median time
rose from 13,318 us to 49,573 us (272.2%), while both policies captured 252 animated frames at
90 Hz with 9 ms P95 and no frame above 32 ms. This is a **mixed** result: idle resource ownership
improved and measured settled motion had **no material change**, but rebuild-sensitive surfaces may
need an explicit bounded or retain-all policy. One device, synthetic content, process-wide PSS, and
a short run limit the conclusion; broader device, leak, and representative-workload evidence stays
open in the active navigation plan.

The entry owner also retains one `NavDestinationContext`. Destination DSL reads it from
`LocalNavDestinationContext`; nested hosts replace the Local for their child entry and restore the
parent holder afterward. Its observable `NavDestinationPresentation` is the exact Core
`NavSceneEntry` used by lifecycle planning, not an Android reconstruction. A captured Local keeps
the holder, so later coarse visibility, interaction, transition, pane, or layer changes remain
observable across native presentation disposal and recreation. Permanent removal stops updates
and destroys the entry Lifecycle; no process-global current-page registry exists.

Presentation observation and resource activation are deliberately separate. AndroidX Lifecycle
is the only resource-threshold API. The destination context supports coarse layout and behavior
decisions, while continuous ordinary-transition and predictive-Back progress stays on motion
executors and never enters its observable state. This bounds ordinary content invalidation to
semantic scene changes rather than animation frames.

Nested graph instances have independent `NavGraphOwner` identities. Descendants in one graph
instance share its lifecycle, saved state, and ViewModels until the last retained descendant is
removed. Entering the same graph route later creates a new owner. The root-to-leaf graph chain is
available only while rendering destination content; it cannot be used to manufacture ownership
outside the active host.

The nearest parent ViewModelStore owner is mandatory and supplies its default factory and creation
extras. A child navigation owner replaces only the store owner, saved-state owner, and route or
graph arguments. The controller's saved host-scope identity namespaces all child stores below that
parent. Changing the parent owner identity recreates the native host so retained entries never
combine two provider contracts.

A destination- or graph-scoped business ViewModel receives `SavedStateHandle` through its
constructor and the owner's default Factory, or inside a `viewModel` initializer through
`createSavedStateHandle()`. Navigation provides the owner namespace and arguments but never creates
a standalone handle-only ViewModel. UI-only values use `rememberSaveable`; restored business values
use one ViewModel-owned mutable flow, so a page has one writer and one restoration path.

Lifecycle and storage termination are ordered but distinct. Permanent entry or graph removal first
requests terminal clear, then sends `ON_DESTROY`; the active lease defers physical ViewModel clear
until owner destruction closes it. Normal host removal clears the complete provider. Host disposal
after the parent lifecycle reaches `DESTROYED` closes presentation owners without terminally
removing stores, allowing a configuration-recreated host with the same parent store and saved scope
identity to lease the existing ViewModels. A finishing parent remains the final clear boundary.

## 5. Lifecycle projection

Navigation Core owns one immutable `NavScene`. Each destination projection carries presence,
visibility, interaction, coarse transition phase, pane role, and content/overlay layer role. The
planner derives lifecycle from one rule rather than independent ID sets:

```text
effective destination lifecycle = min(host cap, scene cap, entry cap)
```

The accepted targets are:

| Role | Target state |
| --- | --- |
| Interactive settled destination and its graph path | `RESUMED` |
| Visible transition participant | `STARTED` |
| Popped destination retaining an exit presentation | `CREATED` |
| Retained hidden destination or graph | `CREATED` |
| Prepared candidate before commit | No higher than `CREATED` |
| Permanently removed destination or graph | `DESTROYED` |

Downward transitions happen before upward transitions, so a single-pane host never briefly owns
two resumed destinations. A validated multi-pane scene may resume multiple leaf destinations and
their shared graph paths intentionally. Graph owners take the highest effective state among their
descendants while Android still applies child-down and parent-up ordering. Destroyed entry and
graph identities cannot be reintroduced.

The Android coordinator freezes exactly one semantic scene when an ordinary or predictive
transition starts and reuses it for owner reconciliation and host-lifecycle changes. Every visible
entry is non-interactive and no higher than `STARTED` until settlement. A popped outgoing entry is
marked `Exiting` and capped at `CREATED` while its View remains available for motion; presentation
disposal then precedes owner destruction. Predictive cancellation restores the gesture-start
settled scene, while commit hands the same pages to the ordinary pop transition before promoting
the incoming entry to `RESUMED` at terminal settlement. Adaptive panes use the same rule, so no
pane resumes early during a scene change.

Core can model content and overlay layers, but the current Android navigation host has no general
overlay-navigation surface. Overlay lifecycle execution therefore remains unclaimed rather than
being inferred from the model or the separate UI overlay transport.

## 6. Restoration boundary

Remembered controllers persist committed stacks, route arguments, destination and graph identities,
selection history, a private host-scope identity, destination and graph SavedStateRegistry bundles,
and ViewCompose saveable values. They do not serialize Views, render sessions, LifecycleRegistry
instances, ViewModelStore contents, pending transactions, or running animations. Initial and
restored attachment creates owners for every retained entry but materializes only the current
visible pane set; hidden destination content is not executed eagerly. Configuration recreation can
retain live ViewModels through the parent store; process recreation creates new instances from
restored owner state.

Restore validates format limits, stack configuration, route existence, leaf resolution, and graph
hierarchy. Incompatible or malformed state is discarded and the configured initial state is used.
The immediately preceding version-4 format is migrated by assigning a fresh host-scope identity;
newer unknown formats remain fail-closed. These rules prevent an old SavedState or ViewModel
namespace from being attached to a different destination after an application update.

## 7. Back and visual motion

System Back participates only while the active controller can consume it. Predictive Back creates a
preview over committed entries without changing the core stack. Cancellation restores the settled
scene; completion uses the normal pop transaction. Detach, disabled Back, or host destruction
cancels an unfinished preview because the platform dispatcher may no longer deliver a terminal
callback. Preview participants remain `STARTED`; on commit the popped outgoing entry becomes
`CREATED` until its exit presentation is disposed.

`NavTransitionSpec` and shared-content capture are presentation policy. They operate on already
owned destination roots after commit, own no page/session retention, and cannot receive input or
accessibility focus. Capture failure degrades the affected visual pair without changing navigation
state.

## 8. Evidence and verification

The invariant boundary is covered at three levels:

- Navigation Core tests exercise two-phase transactions, deterministic retained stacks, graph
  validation, strict URI/action/MIME deep links, lifecycle plans, and pane-scene validation.
- Navigation Android tests exercise candidate rollback, retained owner identity, lifecycle order,
  shared scoped-store recreation and terminal cleanup, SavedState compatibility, queued commands,
  transition redirection, adaptive transition scenes, and predictive Back.
- Android aggregate-host tests exercise Activity ViewTree owner discovery, nested explicit-owner
  precedence, and Fragment owner retention across View recreation.
- The debug navigation device host captures the exact nearest `LocalLifecycleOwner` seen by DSL
  content while instrumentation checks real View motion and owner state on a physical device.
- The compiled [navigation tutorial](../tutorials/navigation.md) and the
  [production-host guide](../guides/navigation.md) provide the public first-success and manual
  acceptance paths.

Run `./gradlew :viewcompose-navigation-core:test :viewcompose-navigation-android:testDebugUnitTest`
for the deterministic architecture suite. Device behavior is accepted only when the guide's real
Back, recreation, predictive-Back, and failure journey also passes.

The transition-lifecycle correction passed a fresh 151/151 Navigation Android suite. Its focused
ordinary, predictive, redirect, host-cap, and adaptive matrix was 20/20. On a physical Pixel 4 XL
running API 33, 2/2 selected instrumentation cases passed: one samples the Lifecycle owner captured
inside destination DSL across push, preview, cancellation, commit, and settlement; the other
rechecks predictive progress against real native Views. Compared with the previous suite that
encoded premature incoming `RESUMED` and popped outgoing `STARTED`, the conclusion is
**improved**. General navigation overlays, API-34 platform edge-gesture delivery, memory, leaks,
and performance remain **inconclusive** and stay assigned to the active plan.

The reducer/executor convergence passed fresh 71/71 Navigation Core and 165/165 Navigation Android
suites, compared with Phase 5 baselines of 60 and 162 respectively. All 15 navigation Back cases
then passed on the same physical API-33 Pixel 4 XL. The coordinator fell from 1,597 to 1,176 lines
because scene, lifecycle, presentation, interaction, Back, rollback, and cleanup policy now comes
from one Core plan. The conclusion is **improved**. The device run is limited to one API level and
synthetic navigation host; line/branch coverage, representative leaks, memory, and performance,
general overlays, typed routes, and direct NavigationEvent integration remain **inconclusive** and
belong to Phase 7.
