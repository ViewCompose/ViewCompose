---
schema_version: 2
document_id: module.viewcompose-navigation-android
doc_type: module
owner:
  kind: module
  id: viewcompose-navigation-android
version_lane: released
capability_ids:
  - navigation.deep-links
  - navigation.destination-context
  - navigation.host
  - navigation.presentation-retention
  - navigation.result-consumption
  - navigation.results
  - navigation.scene-projection
artifact_ids:
  - viewcompose-navigation-android
sample_ids:
  - module.navigation-android-dependency
  - module.navigation-android-destination-context
  - module.navigation-android-deep-link
  - module.navigation-android-host
  - module.navigation-android-host-construction
  - module.navigation-android-presentation-retention
  - module.navigation-android-results
coordinate: com.viewcompose:viewcompose-navigation-android:0.1.0-alpha02
minimal_usage_sample_id: module.navigation-android-dependency
---

# Navigation Android

`viewcompose-navigation-android` mounts `viewcompose-navigation-core` state as native Android View pages. It
owns destination and graph lifecycle boundaries, scoped ViewModel owner leases, SavedStateRegistry
namespaces, policy-bound child render sessions, transactional failure recovery, Android system and
predictive Back, adaptive pane layout, and command-aware View motion.

The application still uses an Activity or Window as its outer Android host, but individual pages do
not require an Activity or Fragment. The platform-neutral back stack remains in
`viewcompose-navigation-core`; this module is its Android execution boundary.

## Artifact and stability

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="navigation-android-module-dependency" sample_id="module.navigation-android-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-navigation-android:0.1.0-alpha02")
}
```

- Stability: **Alpha**. Host, transition, and predictive-Back contracts may evolve between alphas.
- Platform: Android library with a minimum SDK inherited from the repository Android policy.
- API dependencies are Navigation Core, Runtime, UI Contract, and UI Foundation because their
  route, state, node, and builder types form the public navigation surface.
- Implementation dependencies are Android Host, Lifecycle, ViewModel integration, and the neutral
  Android overlay transport. Android Renderer arrives privately through Android Host and is not a
  direct dependency of this artifact.
- The artifact transitively supplies `viewcompose-navigation-core`; applications may depend on the
  core artifact alone when they need only the platform-neutral model.

## Controller and host

{/* compiled-region source="viewcompose-navigation-android/src/test/samples/com/viewcompose/navigation/samples/NavigationAndroidSamples.kt" region="navigation-android-host" sample_id="module.navigation-android-host" build_target=":viewcompose-navigation-android:compileDebugUnitTestKotlin" */}
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

Custom overlay transports are constructor inputs. Keep their factory reference stable across
ordinary renders and advance an explicit key only when the transport must be rebuilt:

{/* compiled-region source="viewcompose-navigation-android/src/test/samples/com/viewcompose/navigation/samples/NavigationAndroidSamples.kt" region="navigation-android-host-construction" sample_id="module.navigation-android-host-construction" build_target=":viewcompose-navigation-android:compileDebugUnitTestKotlin" */}
```kotlin
fun UiTreeBuilder.customOverlayNavHostSample(
    controller: NavHostController,
    overlayHostFactory: (ViewGroup) -> OverlayHost,
    overlayFactoryVersion: Any,
) {
    NavHost(
        controller = controller,
        overlayHostFactory = overlayHostFactory,
        key = overlayFactoryVersion,
    ) { entry ->
        Text(entry.route.name)
    }
}
```

One `NavHostController` can be attached to exactly one active `NavHost`. Navigation commands are
main-thread APIs and require attachment so the core transaction, destination rendering, owner
lifecycle, and native View hierarchy share one commit boundary.

`NavHost` retains one logical owner record per destination and creates a child render session only
when policy and visibility require a native presentation. Hidden entries always retain lifecycle,
ViewModel, saved-state, saveable-state, route, and graph identity. A missing presentation is rebuilt
against the latest captured environment before pop, stack selection/history, predictive Back, or
adaptive-pane expansion can publish the entry as visible. Failed rebuilds dispose every candidate
presentation and preserve the previous stack and scene.

Each destination session receives the `NavigationDestination` diagnostics role and the parent
session ID captured with the `NavHost` Local snapshot. Retention preserves that logical identity;
failed candidates emit their own terminal sequence, while recreated destinations receive a fresh
ID. Restoring destination Locals cannot overwrite the child session owner.

Change `contentKey` when destination content closes over non-observable values. Observable state
invalidates its owning destination session directly. Changing `key`, controller identity, lifecycle
owner, or debug identity recreates the native host because those inputs change ownership rather
than content. `overlayHostFactory` is captured when that host is created and is deliberately absent
from identity equality because lambda identity is not stable across render passes; change `key`
when installing a different factory.

The default nested overlay factory explicitly constructs `viewcompose-overlay-android`; it never
discovers a Material backend from classpath order. A named design integration may pass an explicit
factory when its destination surfaces require additional presenters.

## Destination context

`LocalNavDestinationContext.current` is non-null only while declaring content for the nearest
destination. Its stable `NavDestinationContext` exposes the exact `NavEntry` identity and a
read-only `State<NavDestinationPresentation>`. `NavDestinationPresentation` is a source alias for
the Navigation Core `NavSceneEntry`, so visibility, interaction, transition phase, pane role, and
content/overlay layer role cannot drift from the scene used for lifecycle planning.

{/* compiled-region source="viewcompose-navigation-android/src/test/samples/com/viewcompose/navigation/samples/NavigationAndroidSamples.kt" region="navigation-android-destination-context" sample_id="module.navigation-android-destination-context" build_target=":viewcompose-navigation-android:compileDebugUnitTestKotlin" */}
```kotlin
fun UiTreeBuilder.destinationContextSample(controller: NavHostController) {
    NavHost(controller = controller) { entry ->
        val presentation = checkNotNull(LocalNavDestinationContext.current).presentation.value
        Text("${entry.route.name}: ${presentation.visibility}, ${presentation.paneRole}")
    }
}
```

Capture the context holder during DSL declaration when a later callback needs destination
identity; do not read the Local from an effect callback. The holder survives hidden-presentation
disposal and recreation for the same retained entry. After permanent removal it receives no more
presentation updates, while the entry's AndroidX Lifecycle reaches `DESTROYED` and remains the
terminal resource signal.

Use AndroidX Lifecycle APIs for resource thresholds such as collection, camera, sensor, or player
activation. Use destination presentation only for coarse UI decisions such as visible versus
covered, pane role, or transition role. Predictive and ordinary animation progress is deliberately
absent, so repeated frame progress cannot invalidate ordinary destination content. Nested hosts
provide their own nearest context; there is no global current-page lookup.

## Return a result to the previous page

{/* compiled-region source="viewcompose-navigation-android/src/test/samples/com/viewcompose/navigation/samples/NavigationAndroidSamples.kt" region="navigation-android-results" sample_id="module.navigation-android-results" build_target=":viewcompose-navigation-android:compileDebugUnitTestKotlin" */}
```kotlin
val SelectedItemResult = NavResultKey.text("catalog.selection")

fun UiTreeBuilder.navigationResultSample(
    controller: NavHostController,
    onSelected: (String) -> Unit,
) {
    NavHost(controller = controller) { entry ->
        when (entry.route.name) {
            "home" -> {
                NavResultEffect(SelectedItemResult, onSelected)
                Text("Home")
            }
            "details" -> Text("Details")
        }
    }
}

fun returnSelectedItem(controller: NavHostController, itemId: String): NavResult {
    return controller.popBackStack(SelectedItemResult, itemId)
}
```

The result pop commits the stack before enqueueing the payload on the surviving entry. Each
retained entry owns one saved-state-backed FIFO inbox, exposed as
`NavDestinationContext.results` for explicit `peek`/`consume` control. `NavResultEffect` uses the
same nearest destination `LocalLifecycleOwner` as the rest of the DSL; it consumes one matching
value only after that owner is `RESUMED` and a successful render reaches its side-effect phase.
Consumption is at-most-once, so use the inbox API when a failed application callback must be
retried. Result keys are local entry contracts: there is no process-global bus, cross-stack
addressing, or predictive-Back result pop.

## Presentation retention

`NavPresentationRetentionPolicy` controls native presentation lifetime independently of entry
ownership. `DisposeWhenHidden` is the default: after transition settlement, every fully hidden
child `RenderSession` and View tree is disposed while its entry owner remains at `CREATED`.
`RetainAll` is an explicit unbounded opt-in for surfaces whose measured rebuild cost justifies the
memory, effect, focus, accessibility, and native-resource cost. `Bounded` retains a positive maximum
of hidden presentations and evicts the least-recently-hidden presentation deterministically.
Visible panes and ordinary or predictive transition participants do not count against that bound.

{/* compiled-region source="viewcompose-navigation-android/src/test/samples/com/viewcompose/navigation/samples/NavigationAndroidSamples.kt" region="navigation-android-presentation-retention" sample_id="module.navigation-android-presentation-retention" build_target=":viewcompose-navigation-android:compileDebugUnitTestKotlin" */}
```kotlin
fun UiTreeBuilder.BoundedPresentationNavigation(controller: NavHostController) {
    NavHost(
        controller = controller,
        presentationRetentionPolicy = NavPresentationRetentionPolicy.Bounded(
            maxHiddenPresentations = 2,
        ),
    ) { entry ->
        Text(entry.route.name)
    }
}
```

Changing the policy on an existing host does not recreate the host or any entry owner. Tightening
the bound disposes excess hidden presentations immediately. Relaxing it affects presentations that
are created or hidden later; it does not eagerly build pages that are not visible. On initial,
configuration-restored, or process-restored attachment, only the current visible pane set is
materialized even under `RetainAll`.

The Phase 4 device comparison used a physical Pixel 4 XL on API 33 and a synthetic heavy 13-entry
stack. `DisposeWhenHidden` retained 1 presentation and reported 185,510 KiB PSS; `RetainAll`
retained 13 and reported 191,953 KiB. That is 12 fewer presentations (92.3%) and 6,443 KiB lower
process PSS (3.4%). Synchronous pop-and-rebuild median time increased from 13,318 us to 49,573 us,
or 272.2%. A separate animated comparison captured 252 frames per policy at 90 Hz; both reported
9 ms P95 and zero frames above 32 ms. The interpretation is **mixed**: the bounded default improves
idle resource ownership and has **no material change** in this settled-frame sample, while measured
expensive pages may prefer `Bounded` or `RetainAll`. This is not a universal benchmark: it uses one
device, synthetic content, process-wide PSS, and a short run. Phase 7 keeps broader device, leak,
and representative-workload validation as the next action.

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

## Typed plan execution

The host coordinator does not independently calculate lifecycle, retention, focus, accessibility,
or Back policy. Navigation Core's `NavExecutionReducer` supplies one `NavExecutionPlan` through
separate settled, transition, and predictive-preview entry points backed by the same reducer. The
internal `AndroidNavExecutionPlanExecutor` then performs only typed platform effects.

Preparation and refresh happen before the irreversible stack boundary. After commit, the executor
publishes the exact planned scene and layer order, applies input and accessibility ownership,
updates destination context and lifecycle in child-down/parent-up order, suspends outgoing render
work, and evicts only plan-selected hidden presentations. Terminal cleanup disposes a permanently
removed presentation before destroying its owner. Rollback similarly consumes explicit candidate
IDs from the plan rather than inspecting whichever Views happen to be attached.

During motion, destination containers outside `inputEntryIds` consume touch, generic-motion, and
key events and block descendant focus. Entries outside `accessibilityEntryIds` hide descendants
from accessibility independently. At settlement, the plan restores those rights to the interactive
pane set. `NavHost` system-Back registration reads the same plan's ownership result. Applications
normally do not invoke the Core reducer directly; it is a Q3 integration boundary for tests and
custom platform executors.

## Destination and graph ownership

Every destination entry receives an independent Android owner containing:

- a Lifecycle capped by the host and semantic scene, including visibility, interaction, transition,
  and retained-entry presence;
- a ViewModelStore leased from the shared Lifecycle 2.11 scoped-owner provider and cleared only
  after the entry leaves all retained state;
- a SavedStateRegistry and default SavedStateHandle arguments derived from `NavRoute`;
- a ViewCompose saveable-state registry namespace.

Destination content installs that object into `LocalLifecycleOwner`,
`LocalSavedStateRegistryOwner`, `LocalViewModelStoreOwner`, and the ViewCompose saveable-state local.
Graph content installs the selected graph owner through the same four boundaries. A retained hidden
destination keeps its owner identity and persisted data but receives a capped lifecycle, so a
`LifecycleAndroidViewAdapter` drives its native View inactive without relying on physical removal.

An ordinary or predictive transition freezes one semantic scene for all owner reconciliation.
Visible participants remain no higher than `STARTED` until terminal settlement. A popped outgoing
entry is capped at `CREATED` while its exit View is still presented, then its session is disposed
before the owner reaches `DESTROYED`. Predictive cancellation restores the previous settled owner
states; commit flows through the same capped pop transition. Settled adaptive panes may each be
`RESUMED`, but all visible panes are capped at `STARTED` while their scene changes.

Navigation Core defines overlay layer roles, but this Android host does not yet expose a general
overlay-navigation scene. The separate overlay host transport must not be interpreted as lifecycle
integration for navigation overlays.

Pushing the same route twice creates two owners and does not share page state.

`NavHost` requires the nearest `LocalViewModelStoreOwner`; there is no private fallback store.
Standard Activity and Fragment `setUiContent` hosts install it, while callers of low-level
`renderInto` must use `ProvideViewModelStoreOwner` explicitly. If the parent implements
`HasDefaultViewModelProviderFactory`, its default Factory and starting `CreationExtras` are
inherited. Each child owner then replaces only the ViewModelStore owner, saved-state owner, and
route or graph default arguments, preserving unrelated Application and DI extras. A different
parent-owner identity recreates the native host; retained stacks therefore never mix provider
contracts from two parents.

The controller persists a private host-scope identity beside its stack state. Recreating a host
under the same retained parent store and restored controller identity rebuilds destination
Lifecycle and saved-state owners while leasing the same entry and graph ViewModelStores. Normal
host removal, permanent pop, graph removal, or controller replacement sends terminal clear. This
separates Android presentation lifetime from logical page-state lifetime without a navigation-only
store allocator.

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

A retained-page render that fails before reveal is reported as `DestinationRefresh` with
`stackCommitted = false`. The previous stack, pane scene, visible Views, owners, and sessions remain
authoritative; predictive previews and pane expansion are not published.

Pass `onFailure` to `NavHost` for application logging, fallback, or tests. An unhandled failure is
surfaced as `NavHostException` with the original cause, failed entry, and renderer frame report.

## Save, restore, and process death

`rememberNavHostController` uses the current ViewCompose saveable-state registry. It persists:

- every retained stack, active stack, and selection history;
- destination and graph instance IDs and route arguments;
- destination and graph SavedStateRegistry bundles;
- ViewCompose saveable values owned by each page or graph.

Pending transactions, running animations, Views, sessions, LifecycleRegistry instances, and
ViewModelStore contents are not serialized. Initial and restored attachment materialize only the
visible pane set; retained hidden owners are recreated without executing destination content.
Configuration recreation may retain live ViewModels through the parent store; process recreation
restores their saved-state inputs into newly created instances.

Restore is defensive. Unknown versions, malformed collection types, excessive entry counts,
configuration mismatch, or graph hierarchy changes discard incompatible saved state and create the
configured initial state. This fail-closed behavior avoids attaching an old saved-state namespace to
a different page owner after an application upgrade.

The current format also accepts the immediately preceding version-4 snapshot. It assigns a fresh
host-scope identity while preserving valid stacks and destination state, because no live parent
store can cross a process or application-code restart.

## Android system and predictive Back

With `systemBackEnabled = true`, `NavHost` registers against the nearest AndroidX Back dispatcher
only while the controller can consume Back. At the active root it follows the configured retained
stack history; otherwise dispatch continues to an enclosing host or Activity.

On predictive-Back platforms, gesture progress reveals the previous destination without committing
the core stack. Cancellation springs both pages back to committed state. Gesture completion uses the
same transaction and owner boundary as programmatic `popBackStack`. A programmatic command can
redirect an active preview while preserving its current visual transform for a continuous handoff.
Both preview pages are capped at `STARTED`; after commit the popped page is capped at `CREATED`
until its exit presentation is removed, and only settlement resumes the incoming page.

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

## Shared content motion

`Modifier.sharedElement(SharedContentKey(...))` and `Modifier.sharedBounds(...)` are Q3 endpoint
markers consumed automatically by `NavHost`; no `SharedTransitionLayout` or animation scope is
required. Keys are local to one outgoing/incoming destination pair. A pair exists only when each
tree declares the same key and mode exactly once. Missing, duplicate, mismatched, detached,
zero-sized, surface-backed, or over-budget endpoints fall back per key to ordinary destination
motion and never change the navigation transaction.

The first release is one-window snapshot motion. `sharedElement` moves the source snapshot to the
target bounds. `sharedBounds` uses the same bounds path while crossfading source and target
snapshots. Snapshots draw in stable outgoing-tree order in a non-interactive host overlay and are
bounded to at most two host areas of pixels for one transition. The incoming destination remains
the input and accessibility owner. Successful commit may transfer focus from a focused source to a
focusable target; cancellation restores the source. Completion, cancellation, redirect, host
destruction, capture failure, and session release all remove snapshots and restore endpoint state
exactly once. Predictive Back drives the same overlay from gesture progress and continues from that
fraction on commit; it does not acquire stack commit authority.

## Adaptive panes

`NavPanePolicy.Single` preserves one full-host destination at every width. `Adaptive` admits up to
three newest entries when each pane can retain the configured minimum width. `paneSpacingDp` is
deducted before deciding how many panes fit.

Width changes reuse the committed back stack, destination sessions, and owners. They refresh only
retained entries newly admitted to the pane scene before recalculating native child bounds. Layout
direction maps primary-to-tertiary roles to the correct physical order for LTR and RTL.

## Deep links and retained stacks

{/* compiled-region source="viewcompose-navigation-android/src/test/samples/com/viewcompose/navigation/samples/NavigationAndroidSamples.kt" region="navigation-android-deep-link" sample_id="module.navigation-android-deep-link" build_target=":viewcompose-navigation-android:compileDebugUnitTestKotlin" */}
```kotlin
fun navigateSharedImageRequest(controller: NavHostController): NavDeepLinkResult {
    return controller.navigateDeepLink(
        NavDeepLinkRequest(
            action = Intent.ACTION_SEND,
            mimeType = "image/png",
        ),
    )
}

fun navigateSharedImageIntent(
    controller: NavHostController,
    intent: Intent,
): NavDeepLinkResult {
    return controller.navigateDeepLink(intent)
}
```

The platform-neutral `NavDeepLinkRequest`, string URI, Android `Uri`, and Android `Intent` entry
points all use the same strict Core resolver. The Intent adapter maps only `data`, `action`, and
`type`; extras and categories never enter route arguments or matching policy. URI-only declarations
continue to accept `ACTION_VIEW` Intents, while action-only, MIME-only, and combined declarations
support shares and other explicit integrations without Android types in Navigation Core.

A match is converted to one atomic command that updates the declared target stack and selects it.
`NavDeepLinkResult.Navigated` still contains a `NavResult`, so rendering or commit failure is not
confused with request matching success. An Intent without data, action, or MIME type returns
`NoMatch`; malformed supplied fields return the Core resolver's structured rejection.

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

The Lifecycle 2.11 hard cut requires `NavHost` to run below a `LocalViewModelStoreOwner`. Existing
Activity and Fragment `setUiContent` integrations satisfy the requirement. A custom `renderInto`
host must add `ProvideViewModelStoreOwner`; no implicit root store or compatibility alias is kept.

Typed shared-content markers are additive Q3 UI Contract APIs, but they require a renderer that
publishes the stable endpoint tag and this navigation-host implementation to produce motion. Older
or custom renderers may treat the marker as inert. Cross-window, cross-Activity, cross-process,
live-content, shape-morphing, and arbitrary surface-backed capture are intentionally unsupported in
this alpha and use ordinary destination motion.
