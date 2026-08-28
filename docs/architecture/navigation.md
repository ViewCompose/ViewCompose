---
schema_version: 2
document_id: architecture.navigation-runtime
doc_type: architecture
owner:
  kind: capability
  id: navigation.host
version_lane: released
capability_ids:
  - navigation.host
  - viewmodel.owner-boundaries
  - viewmodel.scoped-owners
  - viewmodel.store-resolution
  - viewmodel.saved-state
artifact_ids:
  - viewcompose-navigation-android
  - viewcompose-navigation-core
sample_ids:
  - tutorial.navigation
invariants:
  - Navigation state commits only after required destination sessions and owners are prepared successfully.
  - Each retained destination and graph instance keeps stable lifecycle, saved-state, ViewModel, and render-session ownership.
  - Navigation entry and graph stores are allocated only by the shared Lifecycle 2.11 scoped-owner provider.
  - Visual transitions and predictive Back never become a second source of navigation state.
  - Restored state is accepted only when it remains compatible with the current graph and stack configuration.
evidence:
  - viewcompose-navigation-core/src/test/kotlin/com/viewcompose/navigation/core/NavBackStackControllerTest.kt
  - viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/TransactionalNavHostCoordinatorTest.kt
  - viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavEntryOwnerStoreTest.kt
  - viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostSavedStateTest.kt
  - viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostTransitionCoordinatorTest.kt
  - viewcompose-android/src/test/java/com/viewcompose/android/FragmentHostLifecycleIntegrationTest.kt
---

# Navigation runtime architecture

## 1. Ownership boundary

ViewCompose navigation uses an Activity or Window as the outer Android host, but a destination is a
framework-owned page rather than an Activity or Fragment. The capability is split across two
published artifacts:

- `viewcompose-navigation-core` owns the platform-neutral route, graph, retained-stack, transaction,
  lifecycle-plan, deep-link, and pane-scene models;
- `viewcompose-navigation-android` owns destination and graph Android owners, child render sessions,
  native View presentation, SavedState encoding, system and predictive Back, and visual motion.

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

## 3. Destination and graph identity

Every destination entry owns a stable child render session, Lifecycle, SavedStateRegistry
namespace, ViewCompose saveable-state namespace, and lease on a keyed ViewModelStore. The store is
allocated by the shared Lifecycle 2.11 `ViewModelScopeProvider`, not by a navigation-specific map.
Pushing the same route twice creates two entry identities. Hidden retained entries keep their
identity and stored state, but frame-driven work is capped by lifecycle and rendering resumes
before the page becomes visible again.

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

Lifecycle and storage termination are ordered but distinct. Permanent entry or graph removal first
requests terminal clear, then sends `ON_DESTROY`; the active lease defers physical ViewModel clear
until owner destruction closes it. Normal host removal clears the complete provider. Host disposal
after the parent lifecycle reaches `DESTROYED` closes presentation owners without terminally
removing stores, allowing a configuration-recreated host with the same parent store and saved scope
identity to lease the existing ViewModels. A finishing parent remains the final clear boundary.

## 4. Lifecycle projection

The host projects committed navigation and pane state into Android lifecycles, capped by the outer
host lifecycle:

| Role | Target state |
| --- | --- |
| Interactive settled destination and its graph path | `RESUMED` |
| Visible transition participant | `STARTED` |
| Retained hidden destination or graph | `CREATED` |
| Prepared candidate before commit | No higher than `CREATED` |
| Permanently removed destination or graph | `DESTROYED` |

Downward transitions happen before upward transitions, so a single-pane host never briefly owns
two resumed destinations. A validated multi-pane scene may resume multiple leaf destinations and
their shared graph paths intentionally. Destroyed entry and graph identities cannot be
reintroduced.

## 5. Restoration boundary

Remembered controllers persist committed stacks, route arguments, destination and graph identities,
selection history, a private host-scope identity, destination and graph SavedStateRegistry bundles,
and ViewCompose saveable values. They do not serialize Views, render sessions, LifecycleRegistry
instances, ViewModelStore contents, pending transactions, or running animations. Configuration
recreation can retain live ViewModels through the parent store; process recreation creates new
instances from restored owner state.

Restore validates format limits, stack configuration, route existence, leaf resolution, and graph
hierarchy. Incompatible or malformed state is discarded and the configured initial state is used.
The immediately preceding version-4 format is migrated by assigning a fresh host-scope identity;
newer unknown formats remain fail-closed. These rules prevent an old SavedState or ViewModel
namespace from being attached to a different destination after an application update.

## 6. Back and visual motion

System Back participates only while the active controller can consume it. Predictive Back creates a
preview over committed entries without changing the core stack. Cancellation restores the settled
scene; completion uses the normal pop transaction. Detach, disabled Back, or host destruction
cancels an unfinished preview because the platform dispatcher may no longer deliver a terminal
callback.

`NavTransitionSpec` and shared-content capture are presentation policy. They operate on already
owned destination roots after commit, own no page/session retention, and cannot receive input or
accessibility focus. Capture failure degrades the affected visual pair without changing navigation
state.

## 7. Evidence and verification

The invariant boundary is covered at three levels:

- Navigation Core tests exercise two-phase transactions, deterministic retained stacks, graph
  validation, strict deep links, lifecycle plans, and pane-scene validation.
- Navigation Android tests exercise candidate rollback, retained owner identity, lifecycle order,
  shared scoped-store recreation and terminal cleanup, SavedState compatibility, queued commands,
  transition redirection, and predictive Back.
- Android aggregate-host tests exercise Activity ViewTree owner discovery, nested explicit-owner
  precedence, and Fragment owner retention across View recreation.
- The compiled [navigation tutorial](../tutorials/navigation.md) and the
  [production-host guide](../guides/navigation.md) provide the public first-success and manual
  acceptance paths.

Run `./gradlew :viewcompose-navigation-core:test :viewcompose-navigation-android:testDebugUnitTest`
for the deterministic architecture suite. Device behavior is accepted only when the guide's real
Back, recreation, predictive-Back, and failure journey also passes.

The clean Phase 3 comparison passed 151/151 Navigation Android tests and 21/21 aggregate-host
executed cases. This is an **improved** ownership result over the 148-test navigation baseline:
allocation is shared, missing boundaries fail directly, and recreation/cleanup are executable.
The result is not device, memory, leak, or performance evidence; those dimensions remain
**inconclusive** and stay assigned to the active navigation lifecycle-and-scene plan.
