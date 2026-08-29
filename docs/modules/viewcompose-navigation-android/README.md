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
  - navigation.typed-route-host
  - navigation.typed-routes
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
  - module.navigation-android-typed-route
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
- Implementation dependencies are Android Host, Lifecycle, ViewModel integration, the neutral
  Android overlay transport, Activity Back compatibility, and NavigationEvent 1.1.2 direct input.
  Android Renderer arrives privately through Android Host and is not a direct dependency of this
  artifact; `navigationevent-testing` remains test-only.
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

### Typed commands

{/* compiled-region source="viewcompose-navigation-android/src/test/samples/com/viewcompose/navigation/samples/NavigationAndroidSamples.kt" region="navigation-android-typed-route" sample_id="module.navigation-android-typed-route" build_target=":viewcompose-navigation-android:compileDebugUnitTestKotlin" */}
```kotlin
data class ArticleRoute(val articleId: Long)

fun typedRouteNavigationSample(
    controller: NavHostController,
    destination: NavRouteSpec<ArticleRoute>,
): ArticleRoute {
    controller.navigate(destination, ArticleRoute(articleId = 42L))
    return controller.snapshot.top.toRoute(destination)
}
```

Use the same `NavRouteSpec<T>` with graph declarations, `navigate`, `replaceTop`, `reset`, and
`NavEntry.toRoute`. Encoding runs on the main thread before a host transaction begins, so an
encoder exception cannot mutate the stack, render tree, owner lifecycle, or result inbox. The
controller and saved-state adapter still receive only `NavRoute`; no live route object or callback
is retained.

`NavHost` retains logical owners independently from native presentations. Missing visible
presentations are rebuilt against the latest environment before a scene is published; failure
disposes candidates and preserves the committed stack. Change `contentKey` for non-observable
content inputs, and change the host `key` when an ownership input or overlay factory changes. The
default overlay factory explicitly uses `viewcompose-overlay-android`, never classpath discovery.

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

Capture the context during DSL declaration for later callbacks. It survives presentation disposal
for the retained entry and stops updating after permanent removal. AndroidX Lifecycle remains the
resource threshold; presentation state is only for coarse visibility, pane, and transition UI.
Nested hosts provide the nearest context, and there is no global current-page lookup.

## Return a result to the previous page

{/* compiled-region source="viewcompose-navigation-android/src/test/samples/com/viewcompose/navigation/samples/NavigationAndroidSamples.kt" region="navigation-android-results" sample_id="module.navigation-android-results" build_target=":viewcompose-navigation-android:compileDebugUnitTestKotlin" */}
```kotlin
val SelectedItemResult = NavResultKey.text("catalog.selection")

fun UiTreeBuilder.observeSelectedItem(onSelected: (String) -> Unit) {
    NavResultEffect(SelectedItemResult, onSelected)
}

fun returnSelectedItem(controller: NavHostController, itemId: String): NavResult =
    controller.popBackStack(SelectedItemResult, itemId)
```

The committed pop enqueues into the surviving entry's saved FIFO inbox. `NavResultEffect` consumes
at most once after its destination is `RESUMED`; use `NavDestinationContext.results` for explicit
acknowledgement or retry. Keys are entry-local, not a global or cross-stack bus.

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

Retention trade-offs and interpreted evidence are maintained by the
[navigation architecture](../../architecture/navigation.md).

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

Navigation Core's reducer is the sole lifecycle, retention, input, accessibility, and Back policy
source. The Android executor prepares presentations before commit, then publishes the planned scene
and ordered effects; rollback and terminal cleanup consume plan IDs instead of inspecting Views.
Applications normally use `NavHost`; the reducer is a Q3 boundary for tests and custom executors.

## Destination and graph ownership

Each destination entry owns independent Lifecycle, ViewModelStore, SavedStateRegistry,
SavedStateHandle defaults, and saveable state; graph instances own the same scope set for their
descendants. Hidden retention preserves those identities while capping Lifecycle. Transition
participants stay at most `STARTED`, exiting popped entries stay `CREATED` until presentation
disposal, and only permanent removal reaches `DESTROYED`. Duplicate routes still create distinct
owners.

`NavHost` requires the nearest `LocalViewModelStoreOwner` and inherits its default Factory and
`CreationExtras`; low-level `renderInto` callers provide it explicitly. A persisted host-scope ID
allows configuration recreation to lease the same entry and graph stores, while permanent removal
clears them. Use `ProvideNavGraphOwner(route)` within destination content to select an active graph
scope. General overlay-navigation lifecycle remains unsupported; overlay transport alone does not
create such a scene.

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

The saveable registry persists stacks, history, entry/graph IDs and routes, owner bundles, and
saveable values—not pending work, Views, sessions, Lifecycle objects, or ViewModel contents.
Restored attachment materializes only visible panes. Invalid versions, shapes, limits,
configurations, or graph hierarchies fail closed to initial state; the preceding version-4 format
is accepted with a fresh host-scope identity.

## Android system and predictive Back

While `STARTED` and able to consume Back, `NavHost` registers one default-priority handler with the
nearest `ViewTreeNavigationEventDispatcherOwner`. If none exists, it uses the nearest Activity
`OnBackPressedDispatcherOwner` as a compatibility fallback; the two paths are mutually exclusive.
At an active root the handler is disabled so retained-stack history, an outer handler, or the
dispatcher fallback can continue.

Both paths feed one preview and pop state machine. Predictive preview does not commit the stack:
cancel restores the settled scene, completion uses the ordinary pop transaction, and command
redirection continues from current visuals. Detach, disable, stop, owner replacement, or destroy
cancels an unfinished preview, and a late terminal callback from that cancelled gesture is ignored.
Forward history and Android Studio Preview input remain outside this host contract.

## Motion

`NavTransitionSpec` is visual-only policy for every command and predictive Back.
`NavDestinationTransform` combines pane/dp travel, alpha, scale, timing, and easing; `None` disables
motion. The driver lays out endpoints before motion, uses temporary hardware layers for transform
work, and redirects from current visual properties without changing stack or owner semantics.

## Shared content motion

`sharedElement` and `sharedBounds` are Q3 markers matched once per key and mode within one
destination pair. Invalid, detached, surface-backed, or over-budget endpoints fall back per key
without affecting navigation. The one-window implementation animates bounded snapshots in a
non-interactive overlay, preserves incoming input/accessibility ownership, cleans up exactly once,
and lets predictive Back drive the same visuals without gaining commit authority.

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

Request, URI, and Intent entry points share the strict Core resolver; Intent maps only `data`,
`action`, and `type`. A match atomically updates and selects its target stack, while the nested
`NavResult` preserves render/commit failure. Multiple tabs use one remembered controller and
`NavStackConfiguration`, without mirrored active-stack state.

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
