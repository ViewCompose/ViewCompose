# Navigation Android

`viewcompose-navigation-android` mounts `viewcompose-navigation-core` state as native Android View pages. It
owns destination and graph lifecycle boundaries, ViewModel stores, SavedStateRegistry namespaces,
child render sessions, transactional failure recovery, Android system and predictive Back,
adaptive pane layout, and command-aware View motion.

The application still uses an Activity or Window as its outer Android host, but individual pages do
not require an Activity or Fragment. The platform-neutral back stack remains in
`viewcompose-navigation-core`; this module is its Android execution boundary.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-navigation-android:0.1.0-alpha01")
}
```

- Stability: **Alpha**. Host, transition, and predictive-Back contracts may evolve between alphas.
- Platform: Android library with a minimum SDK inherited from the repository Android policy.
- Direct ViewCompose dependencies include Navigation Core, Android Host, Renderer, UI Foundation,
  lifecycle, and ViewModel integration.
- The artifact transitively supplies `viewcompose-navigation-core`; applications may depend on the
  core artifact alone when they need only the platform-neutral model.

## Controller and host

```kotlin
fun UiTreeBuilder.AppNavigation() {
    val controller = rememberNavHostController(
        startDestination = NavRoute("home"),
    )
    NavHost(controller = controller) { entry ->
        when (entry.route.name) {
            "home" -> HomePage(controller)
            "details" -> DetailsPage(controller)
            else -> error("Unknown route ${entry.route.name}")
        }
    }
}
```

One `NavHostController` can be attached to exactly one active `NavHost`. Navigation commands are
main-thread APIs and require attachment so the core transaction, destination rendering, owner
lifecycle, and native View hierarchy share one commit boundary.

`NavHost` creates one retained child render session per destination. Hidden stack entries keep their
session and owners but pause frame-driven rendering. When they become visible, they render against
the latest captured environment without synchronously recomposing every retained page after each
command.

Change `contentKey` when destination content closes over non-observable values. Observable state
invalidates its owning destination session directly. Changing `key`, controller identity, lifecycle
owner, debug identity, or overlay factory recreates the native host because those inputs change
ownership rather than content.

## Command results and re-entrancy

Controller commands return `NavResult`:

- `Committed` reports the state and entry-owner mutation applied by the host;
- `NoChange` reports a valid command that was already effective;
- `Queued` means a transition or callback is active and the command will run serially later;
- `Failed` reports structured render or commit context.

Destination callbacks may navigate synchronously while another render, lifecycle update, or motion
completion is in progress. The host queues those re-entrant commands and drains them only after the
current operation reaches a terminal state. A queued result is therefore not completion; observe
`controller.navigationState` for the eventual committed multi-stack state.

The controller exposes immediate immutable `snapshot` and `stackState` projections plus observable
`navigationState`. Selected-tab UI should derive its selection from `activeStackId` rather than
maintaining a second source of truth.

## Destination and graph ownership

Every destination entry receives an independent Android owner containing:

- a Lifecycle capped by the host and pane visibility;
- a ViewModelStore cleared only after the entry leaves all retained state;
- a SavedStateRegistry and default SavedStateHandle arguments derived from `NavRoute`;
- a ViewCompose saveable-state registry namespace.

Pushing the same route twice creates two owners and does not share page state.

Nested graph instances receive `NavGraphOwner` boundaries. Destinations in one graph instance share
its Lifecycle, ViewModelStore, and SavedStateRegistry until the last descendant leaves the stack.
Entering the same graph route again later creates a new owner.

`LocalNavGraphOwnerScope.current` exposes the active root-to-leaf owner chain. Use
`ProvideNavGraphOwner(route)` around a subtree that should resolve lifecycle, ViewModels, and saved
state against a graph rather than the leaf destination. It fails when called outside destination
content or for an inactive graph route.

## Failure and rollback

The Android host preserves the two-phase guarantee from navigation core. New destination sessions
and owners are prepared first, then staged into the View hierarchy, then pure stack state commits,
and finally commit effects run. Failures are classified by `NavFailurePhase`.

`NavFailure.stackCommitted` distinguishes failures before and after the irreversible stack boundary.
Before commit, candidate sessions and owners are removed and the core transaction rolls back. After
commit, the host keeps committed state and reports the effect failure rather than pretending that
the previous stack is still authoritative.

Pass `onFailure` to `NavHost` for application logging, fallback, or tests. An unhandled failure is
surfaced as `NavHostException` with the original cause, failed entry, and renderer frame report.

## Save, restore, and process death

`rememberNavHostController` uses the current ViewCompose saveable-state registry. It persists:

- every retained stack, active stack, and selection history;
- destination and graph instance IDs and route arguments;
- destination and graph SavedStateRegistry bundles;
- ViewCompose saveable values owned by each page or graph.

Pending transactions, running animations, Views, sessions, LifecycleRegistry instances, and
ViewModelStore contents are not serialized.

Restore is defensive. Unknown versions, malformed collection types, excessive entry counts,
configuration mismatch, or graph hierarchy changes discard incompatible saved state and create the
configured initial state. This fail-closed behavior avoids attaching an old saved-state namespace to
a different page owner after an application upgrade.

## Android system and predictive Back

With `systemBackEnabled = true`, `NavHost` registers against the nearest AndroidX Back dispatcher
only while the controller can consume Back. At the active root it follows the configured retained
stack history; otherwise dispatch continues to an enclosing host or Activity.

On predictive-Back platforms, gesture progress reveals the previous destination without committing
the core stack. Cancellation springs both pages back to committed state. Gesture completion uses the
same transaction and owner boundary as programmatic `popBackStack`. A programmatic command can
redirect an active preview while preserving its current visual transform for a continuous handoff.

Detaching the View, disabling system Back, or destroying the host actively cancels an unfinished
preview because the dispatcher may no longer send a terminal callback.

## Motion

`NavTransitionSpec` is visual policy only; changing it never mutates navigation state or ownership.
It independently configures push, pop, replace, reset, stack selection, deep-link, and predictive-
Back motion.

`NavDestinationTransform` combines pane-relative travel, dp travel, alpha, and scale. Geometry and
incoming/outgoing alpha can use independent durations, delays, and `NavMotionEasing` curves. The
default push/pop geometry and emphasized easing are aligned with current Android activity motion;
predictive Back follows current WM Shell cross-activity geometry. Use `NavTransitionSpec.None` when
the application or test must disable all motion.

The View driver renders complete starting layouts before starting motion and temporarily promotes
expensive destination hierarchies to hardware layers while only transform/alpha changes. Redirected
motion retains current visual properties so a subsequent command does not jump back to an identity
frame.

## Adaptive panes

`NavPanePolicy.Single` preserves one full-host destination at every width. `Adaptive` admits up to
three newest entries when each pane can retain the configured minimum width. `paneSpacingDp` is
deducted before deciding how many panes fit.

Width changes reuse the committed back stack, destination sessions, and owners. They recalculate
only the pane scene and native child bounds. Layout direction maps primary-to-tertiary roles to the
correct physical order for LTR and RTL.

## Deep links and retained stacks

The string, Android `Uri`, and `ACTION_VIEW Intent` entry points all use the same strict graph
resolver. A match is converted to one atomic command that updates the declared target stack and
selects it. `NavDeepLinkResult.Navigated` still contains a `NavResult`, so rendering or commit failure
is not confused with URI matching success.

For multiple tabs, declare one `NavStackConfiguration` and remember it with the shared graph. Do not
create one controller per tab or mirror active-stack state in application fields; the controller
already retains each stack and owns selection history.

## Related documentation

- [Navigation core module](../viewcompose-navigation-core/README.md)
- [Complete navigation guide](../../guides/navigation.md)
- [Lifecycle and saved-state architecture](../../architecture/lifecycle-and-saved-state.md)
- [Session container architecture](../../architecture/session-containers.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

The complete generated reference is available in the
[`viewcompose-navigation-android` API tree](https://docs.viewcompose.com/api/viewcompose-navigation-android/current/).

## Compatibility notes

The `0.1.0-alpha01` line establishes one-controller/one-host attachment, main-thread serialized
commands, destination and graph ownership, defensive process-death restore, predictive-Back
preview, Android-aligned native View motion, and up to three adaptive panes. Persist controller state
through `rememberNavHostController`; do not retain Android owner or session objects outside the host.
