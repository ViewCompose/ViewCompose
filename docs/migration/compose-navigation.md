---
schema_version: 2
document_id: migration.compose-navigation
doc_type: migration
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
  - viewcompose-navigation-core
  - viewcompose-navigation-kotlinx-serialization
  - viewcompose-navigation-android
  - viewcompose-ui-foundation
  - viewcompose-lifecycle-androidx
  - viewcompose-viewmodel-androidx
sample_ids:
  - module.navigation-android-results
  - module.navigation-core-execution-plan
source_state: Jetpack Navigation 2.9.8 and Navigation3 1.1.6 with Compose 1.12.0 and current stable AndroidX owner semantics.
target_state: ViewCompose Navigation Core 0.1.0-alpha03, source-registered Kotlinx Serialization adapter 0.1.0-alpha01, and Navigation Android 0.1.0-alpha02 transactional host contracts.
---

# Migrating Compose Navigation to ViewCompose

This page compares ViewCompose navigation with both Jetpack Navigation 2 and Navigation 3.
Navigation 2 and Navigation 3 have different ownership models, so a migration must identify its
actual source before mapping APIs or lifecycle behavior.

- **Source state:** Navigation 2.9.8 or Navigation3 1.1.6, with Compose UI/Runtime 1.12.0,
  Activity 1.13.0, Lifecycle 2.11.0, and SavedState 1.5.0.
- **Target state:** `viewcompose-navigation-core` 0.1.0-alpha03 and
  source-registered `viewcompose-navigation-kotlinx-serialization` 0.1.0-alpha01, plus
  `viewcompose-navigation-android`, `viewcompose-lifecycle-androidx`, and
  `viewcompose-viewmodel-androidx` 0.1.0-alpha02.
- **Last verified:** 2026-08-29.
- **Re-verification owner:** maintainers of `viewcompose-navigation-core`,
  `viewcompose-navigation-kotlinx-serialization`, `viewcompose-navigation-android`,
  `viewcompose-lifecycle-androidx`, and `viewcompose-viewmodel-androidx`.

## Verification model

The upstream comparison uses official [Navigation 2 guides](https://developer.android.com/guide/navigation),
[Navigation 2 releases](https://developer.android.com/jetpack/androidx/releases/navigation),
[Navigation 3 guides](https://developer.android.com/guide/navigation/navigation-3),
[Navigation 3 releases](https://developer.android.com/jetpack/androidx/releases/navigation3), and
[NavigationEvent releases](https://developer.android.com/jetpack/androidx/releases/navigationevent).
The executable baseline is Compose 1.7.8, Navigation 2.9.8, Activity 1.12.4, Lifecycle 2.11.0, and
Kotlin 2.2.10. The paired sample proves only its compiled path; broader claims rely on the linked
Core and Android module tests and must be re-reviewed when an upstream version changes.

## Compiled Navigation 2 starting point

This is the executable route-level starting point for a Navigation 2 source migration. Both
snippets are extracted from `:samples:compose-migration`; `qaQuick` compiles them and verifies exact
agreement with the documentation.

Compose Navigation 2 source:

{/* paired-sample source="samples/compose-migration/src/main/java/com/viewcompose/samples/migration/navigation/ComposeNavigationSample.kt" region="compose-navigation" */}
```kotlin
@Composable
fun ComposeNavigationSample() {
    val controller = rememberNavController()

    NavHost(
        navController = controller,
        startDestination = "home",
    ) {
        composable("home") {
            BasicText(
                text = "Open details",
                modifier = Modifier.clickable {
                    controller.navigate("details")
                },
            )
        }
        composable("details") {
            BasicText("Details")
        }
    }
}
```
{/* paired-sample-end */}

ViewCompose target:

{/* paired-sample source="samples/compose-migration/src/main/java/com/viewcompose/samples/migration/navigation/ViewComposeNavigationSample.kt" region="viewcompose-navigation-android" */}
```kotlin
fun UiTreeBuilder.ViewComposeNavigationSample() {
    val controller = rememberNavHostController(
        startDestination = NavRoute("home"),
    )

    NavHost(controller = controller) { entry ->
        when (entry.route.name) {
            "home" -> Button(
                text = "Open details",
                onClick = {
                    controller.navigate(NavRoute("details"))
                },
            )
            "details" -> Text("Details")
            else -> error("Unknown route ${entry.route.name}")
        }
    }
}
```
{/* paired-sample-end */}

This pair proves a minimal controller-owned single-stack flow only. It does not cover typed routes,
`NavOptions`, deep links, owner propagation, multiple stacks, restoration, Predictive Back, or any
Navigation 3 scene/decorator behavior.

## Capability matrix

Status values are limited to **Supported**, **Partially supported**, **Intentionally different**,
and **Unsupported**.

| Concept | Navigation 2 / Navigation 3 behavior | ViewCompose behavior | Status | Local evidence and verification note |
| --- | --- | --- | --- | --- |
| Navigation ownership | Navigation 2 centers on a library-owned `NavController`. Navigation 3 normally exposes an application-owned back-stack collection to `NavDisplay`. | A `NavBackStackController` owns immutable single- or multi-stack snapshots and exposes prepared transitions to the Android host. | Intentionally different | [`NavBackStackController.kt`](../../viewcompose-navigation-core/src/main/kotlin/com/viewcompose/navigation/core/NavBackStackController.kt) and [`NavHostRuntime.kt`](../../viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavHostRuntime.kt). It combines controller ownership similar to Navigation 2 with explicit snapshot and pane concepts closer to Navigation 3. |
| Host and destination type | Navigation 2 supports Compose, Fragment, Activity, and custom destinations. Navigation 3 renders entry content through `NavDisplay`. | `NavHost` renders framework-managed native View sessions. An Activity or Fragment is a host owner, not a destination type. | Intentionally different | [`NavHostDsl.kt`](../../viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavHostDsl.kt) and [`NavDestinationSessionStore.kt`](../../viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavDestinationSessionStore.kt). Direct Fragment or Activity destinations are not implemented. |
| Graphs and typed routes | Navigation 2 type-safe routes use serializable route types across graph declaration, navigation, and entry decoding. Navigation 3 keys are application-defined and normally saveable; 1.1.6 retains instance-key `entry` precedence over a class-key registration. | One `NavRouteSpec<T>` supplies stable identity and encoding. The optional Kotlinx adapter derives it for flat scalar class/object schemas; Graph DSL, Android commands, and `NavEntry.toRoute` still store only closed `NavRoute`/`NavValue`. | Partially supported | [`NavRouteSpec.kt`](../../viewcompose-navigation-core/src/main/kotlin/com/viewcompose/navigation/core/NavRouteSpec.kt), [`SerializableNavRouteSpec.kt`](../../viewcompose-navigation-kotlinx-serialization/src/main/kotlin/com/viewcompose/navigation/serialization/SerializableNavRouteSpec.kt), both focused suites, and typed host tests. Generated scalar serializers are covered; custom `NavType`, nested/collection/polymorphic keys, and Navigation3 instance/class precedence remain absent. |
| Back-stack operations | Navigation 2 supplies `navigate`, `popBackStack`, `popUpTo`, and `NavOptions`; Navigation 3 represents stack changes through application collection updates. | Push, pop, replace, reset, stack selection, and deep-link commands are prepared, rendered, and then committed or rolled back as one transaction. | Partially supported | [`NavBackStackController.kt`](../../viewcompose-navigation-core/src/main/kotlin/com/viewcompose/navigation/core/NavBackStackController.kt), [`NavBackStackControllerTest.kt`](../../viewcompose-navigation-core/src/test/kotlin/com/viewcompose/navigation/core/NavBackStackControllerTest.kt), and [`NavHostPublicApiTest.kt`](../../viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostPublicApiTest.kt). The two-phase transaction is stronger than an API-name mapping but is not the full Navigation 2 `NavOptions` surface. |
| Scene execution plan | Navigation 3 derives scenes from application back-stack state and composes entry content through decorators; Navigation 2 keeps most execution policy inside the controller and navigator implementations. | `NavExecutionReducer` is a public, pure Q3 boundary. Settled, transition, and predictive-preview inputs produce one immutable plan for stack, scene, lifecycle, presentation, interaction, Back, rollback, and cleanup; the Android host performs typed effects from that plan. | Intentionally different | [`NavExecutionPlan.kt`](../../viewcompose-navigation-core/src/main/kotlin/com/viewcompose/navigation/core/NavExecutionPlan.kt), its compiled sample, reducer model tests, and Android coordinator tests. This is stronger inspectability for custom executors, not parity with Navigation 3's open scene/decorator ecosystem. |
| Entry and graph owners | `NavBackStackEntry` owns lifecycle, ViewModel, and saved state. Lifecycle 2.11 adds Navigation3 ViewModel decorators that inherit parent factories and `CreationExtras`. | Each destination and graph gets its own lifecycle, saved-state owner, ViewCompose saveable-state registry, and leased ViewModelStore. Owners inherit the required host parent's default Factory and starting extras, then replace their child ownership and route defaults. | Supported | [`NavEntryOwner.kt`](../../viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavEntryOwner.kt), [`NavGraphOwner.kt`](../../viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavGraphOwner.kt), [`NavEntryOwnerEnvironment.kt`](../../viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavEntryOwnerEnvironment.kt), and Factory, extras, SavedStateHandle, destination, and graph coverage in [`NavEntryOwnerTest.kt`](../../viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavEntryOwnerTest.kt) and [`NavHostPublicApiTest.kt`](../../viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostPublicApiTest.kt). |
| Scoped ViewModels and multiple stacks | Lifecycle 2.11 can hoist a `ViewModelStoreProvider` above the Navigation3 decorator so multiple back stacks retain isolated entry stores. | `NavHost` uses the shared `ViewModelScopeProvider` below its required parent owner. A saved host-scope identity plus entry/graph identity retains isolated stores across stack switches and configuration recreation; terminal pop, graph removal, and normal host removal clear them. | Supported | [`NavHostRuntime.kt`](../../viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavHostRuntime.kt), [`NavEntryOwnerStore.kt`](../../viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavEntryOwnerStore.kt), [`NavEntryOwnerStoreTest.kt`](../../viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavEntryOwnerStoreTest.kt), and the same-route retained-stack isolation test in [`NavHostPublicApiTest.kt`](../../viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostPublicApiTest.kt). Navigation owns lifecycle and identity coordination, not a second ViewModelStore allocator. |
| Destination lifecycle | Navigation entries are lifecycle owners; Navigation 3 scenes can present multiple entries. | `NavSceneEntry` derives scene and entry caps from presence, visibility, interaction, transition, pane, and layer roles; the planner applies `min(host, scene, entry)`. The Android host freezes ordinary and predictive scenes, caps visible participants at `STARTED`, and resumes settled interactive panes or the top modal overlay only after termination. | Supported | [`NavScene.kt`](../../viewcompose-navigation-core/src/main/kotlin/com/viewcompose/navigation/core/NavScene.kt), [`NavLifecyclePlanner.kt`](../../viewcompose-navigation-core/src/main/kotlin/com/viewcompose/navigation/core/NavLifecyclePlanner.kt), reducer/coordinator tests, and selected physical-device lifecycle coverage in [`NavigationBackDeviceTest.kt`](../../app/src/androidTest/java/com/viewcompose/NavigationBackDeviceTest.kt). Content panes, covered layers, nested modal overlays, and popped exits share the same executable lifecycle rule. |
| Destination presentation context | Navigation 3 entry content can observe scene metadata through its entry scope; Compose content also observes composition-local values. | `LocalNavDestinationContext` exposes a stable per-entry holder whose read-only presentation is the exact Core scene entry. It survives native presentation disposal/recreation, nests by nearest host, and excludes frame progress. AndroidX Lifecycle remains the resource-threshold API. | Supported | [`NavDestinationContext.kt`](../../viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavDestinationContext.kt), [`NavEntryOwnerEnvironment.kt`](../../viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavEntryOwnerEnvironment.kt), and holder, local-capture, nested-host, pane, overlay, removal, and predictive-progress coverage in Navigation Android tests. Content and overlays use this same holder and Lifecycle owner path. |
| Hidden destination composition | Navigation state can be retained independently of whether Compose content remains in Composition. Navigation 3 decorators retain entry state. | Logical entry owners, ViewModels, saved state, and saveable state survive independently of native presentation. `DisposeWhenHidden` is the bounded default; explicit retain-all and bounded least-recently-hidden policies are available. Reveal rebuilds a missing presentation transactionally. | Supported | [`NavPresentationRetentionPolicy.kt`](../../viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavPresentationRetentionPolicy.kt), [`NavDestinationSessionStore.kt`](../../viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavDestinationSessionStore.kt), unit ownership/rebuild/LRU coverage, and physical-device identity and resource-count coverage in [`NavigationBackDeviceTest.kt`](../../app/src/androidTest/java/com/viewcompose/NavigationBackDeviceTest.kt). |
| Multiple back stacks | Navigation 2 uses save/restore options; Navigation 3 documents application-owned multiple-list recipes. | One `NavStackConfiguration` owns all stacks, selection history, and root-back behavior. Non-selected stack owners remain live while their optional presentations follow the host retention policy. | Intentionally different | [`NavStackConfiguration.kt`](../../viewcompose-navigation-core/src/main/kotlin/com/viewcompose/navigation/core/NavStackConfiguration.kt), [`NavBackStackSetControllerTest.kt`](../../viewcompose-navigation-core/src/test/kotlin/com/viewcompose/navigation/core/NavBackStackSetControllerTest.kt), and multi-stack restoration in [`NavHostPublicApiTest.kt`](../../viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostPublicApiTest.kt). |
| Deep links | Navigation 2 matches URI, action, and MIME type. Navigation 3 supplies recipes for parsing external input into application keys. | `NavDeepLinkRequest` and `NavDeepLink` match strict URI, action, MIME, or combined declarations without Android types. Android maps `Intent.data`, `action`, and `type` into the same resolver; nested graphs, launch modes, structured rejection, ambiguity rejection, and inert extra query values remain supported. | Supported | [`NavDeepLink.kt`](../../viewcompose-navigation-core/src/main/kotlin/com/viewcompose/navigation/core/NavDeepLink.kt), [`NavDeepLinkTest.kt`](../../viewcompose-navigation-core/src/test/kotlin/com/viewcompose/navigation/core/NavDeepLinkTest.kt), and Intent/transaction coverage in [`NavHostPublicApiTest.kt`](../../viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostPublicApiTest.kt). ViewCompose deliberately omits Navigation 2's Android-specific builder surface while preserving the material matching capability in Core. |
| Return results | Navigation 2 uses the previous entry's `SavedStateHandle`; Navigation 3 uses application-owned state. | Result pop is atomic; the surviving entry owns a saved FIFO inbox, and `NavResultEffect` consumes at `RESUMED`. | Supported | [`NavResult.kt`](../../viewcompose-navigation-core/src/main/kotlin/com/viewcompose/navigation/core/NavResult.kt), [`NavResultInbox.kt`](../../viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavResultInbox.kt), and result transaction/lifecycle tests. No global or cross-stack bus is provided. |
| Save, restore, and process death | Navigation 2 restores controller and entry state; Navigation 3 restores saveable keys and decorator state. Neither restores live ViewModel instances. | ViewCompose saves the complete configured stack set, route values, entry and graph saved state, saveable values, and a private host-scope identity. It retains live ViewModels only through the parent store during configuration recreation, migrates version-4 snapshots with a fresh scope identity, and rejects corrupt or structurally invalid state. | Supported | [`NavHostSavedState.kt`](../../viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavHostSavedState.kt), [`NavHostSavedStateTest.kt`](../../viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostSavedStateTest.kt), and restoration coverage in [`NavHostPublicApiTest.kt`](../../viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostPublicApiTest.kt). Live Views, ViewModels, effects, animations, and uncommitted transactions are not process-restored. |
| System Back and Predictive Back | Navigation 2 Compose integrates Predictive Back. Navigation 3 uses NavigationEvent and scene transitions. | The Android host drives transactional predictive start, progress, cancellation, and commit from the nearest NavigationEvent owner, with Activity Back as a compatibility fallback. | Supported | [`AndroidNavHostBackAdapter.kt`](../../viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/AndroidNavHostBackAdapter.kt), its direct/legacy tests, and focused device coverage. Both inputs share one transactional preview and pop state machine. |
| Direct NavigationEvent integration | Activity and Navigation3 expose `NavigationEventDispatcher`, nested dispatcher owners, testing utilities, and Compose handlers. Navigation3 uses NavigationEvent 1.1.2, including Predictive Back in Android Studio Preview inspection mode. | `NavHost` registers directly with the nearest View-tree owner, observes lifecycle and root delegation, and is tested with the official dispatcher fixture. Activity Back is used only when no direct owner exists. | Partially supported | The production handler is internal because applications already provide the official owner boundary. Forward history, a ViewCompose dispatcher facade, and Android Studio Preview input remain absent. |
| Adaptive panes and overlays | Navigation 3 scenes can select one or more entries and coordinate overlays and transitions. Versions 1.1.3 and 1.1.4 fix nested-overlay and popped-entry metadata animation defects. | `NavSceneLayout` combines up to three content panes with a validated trailing modal-overlay suffix. Ordered strategies, lifecycle, sessions, input, accessibility, results, restore, Back, and overlay-only ordinary/predictive motion consume one reducer plan. | Partially supported | [`NavSceneLayout.kt`](../../viewcompose-navigation-core/src/main/kotlin/com/viewcompose/navigation/core/NavSceneLayout.kt), [`NavExecutionPlan.kt`](../../viewcompose-navigation-core/src/main/kotlin/com/viewcompose/navigation/core/NavExecutionPlan.kt), overlay/pane reducer tests, and Android host tests. Arbitrary Navigation 3 scene shapes, decorators, separate windows, forward history, and Preview input remain absent; modal execution itself is supported. |

## Choosing the source navigation model

For Navigation 2, map the library-owned controller, graph, and stack before replacing destination
content. For Navigation 3, map application-owned keys, scenes, decorators, and state first.
ViewCompose is not drop-in compatible: its controller-owned snapshots resemble Navigation 2,
while explicit entry identities and scene projection overlap with Navigation 3.

## Host and destination architecture

`NavHost` mounts View-backed destination sessions below the nearest host lifecycle and required
`LocalViewModelStoreOwner`; Activity and Fragment remain outer hosts, not route destinations.
Fragment migration must separate route, content, lifecycle, ViewModel, saved state, and results.
Transactions publish only after native rendering succeeds. Hidden entries retain logical owners
and state while the default `DisposeWhenHidden` releases their View tree; use `Bounded` or
`RetainAll` only with device evidence. Restore materializes the current scene and rebuilds other
presentations when revealed.

## Graphs, routes, and arguments

ViewCompose graph, destination, entry, and stack identities are explicit. `NavRouteSpec<T>` closes
the application use path around one declaration: register the spec in the graph, navigate with a
typed value, and decode the entry with that same spec. Its callbacks still encode into the
`NavValue` primitive set, which keeps snapshots deterministic and saveable. The optional Kotlinx
adapter generates that codec for flat scalar class/object schemas; structured values and arbitrary
Navigation 3 application keys remain outside the contract.

Prefer stable identifiers and primitive route values. Load complex domain objects from a
repository or ViewModel after navigation instead of serializing them into a route. A migration
must also define how unknown routes, malformed values, and graph-shape changes fail; ViewCompose
restore and deep-link paths intentionally fail closed.

## Back-stack transactions

ViewCompose navigation changes have prepare, render, and commit phases. Push, pop, replace, reset,
stack selection, and deep-link handling do not become authoritative until the host render
succeeds. Rollback restores the prior controller snapshot and native tree.

Do not translate Navigation 2 `NavOptions` mechanically. Record the intended result of every
`popUpTo`, inclusive flag, single-top rule, state-save option, and restore option, then express that
result using the available ViewCompose commands and stack configuration. Where no public command
expresses the same result, classify the route operation as unsupported for that migration rather
than composing several non-atomic mutations.

## Entry and graph ownership

Each ViewCompose destination entry has lifecycle, ViewModelStore, saved-state, and saveable-state
ownership. Nested graphs have separate owner scopes. Removing an entry or graph permanently moves
its lifecycle to `DESTROYED` and clears its ViewModelStore. Retaining it in a hidden stack does not.

Lifecycle 2.11 raises the upstream parity bar. `ViewModelStoreProvider` supports arbitrary UI
scopes, and `ViewModelStoreNavEntryDecorator` can inherit the parent owner's default factory and
`CreationExtras`. Its hoisted-provider overload supports multiple back stacks without prematurely
clearing sibling stores. ViewCompose destination and graph owners now inherit the nearest host
owner's default Factory and starting extras, replace their own store/saved-state owners and route
defaults, and preserve unrelated Application and DI extras. Their stores are leased from the same
`ViewModelScopeProvider` available to arbitrary ViewCompose subtrees. Same-route entries in
retained stacks remain isolated. Configuration recreation keeps leases addressable through a saved
host-scope identity; permanent removal sends terminal clear and prevents resurrection.

Before migrating a custom ViewModel factory, verify all required `CreationExtras`, application
objects, default arguments, and `SavedStateHandle` construction at both destination and graph
scope. The existence of a `ViewModelStoreOwner` alone is insufficient evidence.

## Lifecycle and adaptive panes

The lifecycle target is `min(host cap, scene cap, entry cap)`:

- retained hidden entries target `CREATED`;
- visible, non-interactive entries target `STARTED`;
- interactive entries target `RESUMED`; and
- no entry or graph can exceed the host lifecycle.

Downward changes precede upward promotion. Adaptive panes may resume multiple entries; a modal
suffix instead keeps covered layers at `STARTED` and resumes only its top overlay. Ordinary and
predictive motion cap visible participants at `STARTED`, with popped exits at `CREATED` until
cleanup. Destination DSL observes the nearest AndroidX `LocalLifecycleOwner`; use the stable
`LocalNavDestinationContext` holder only for coarse presentation state.

The Alpha hard cut requires before/after `NavSceneLayout` values. Ordered strategies run before pane
selection, and modal motion moves only the overlay. ViewCompose does not claim Navigation 3's
arbitrary scenes/decorators, separate windows, forward history, or Preview input.

## Hidden destination retention

Retained hidden presentations keep their session, View tree, and effects even while frame rendering
is inactive. Stop resource work through Lifecycle, or choose a policy that disposes the
presentation; permanent removal always clears both presentation and logical ownership.

## Multiple back stacks

`NavStackConfiguration` owns stacks, selection history, and root Back. Inactive entries and owners
remain retained while presentations follow host policy. Verify that repeated routes across stacks
receive isolated stores and survive save/restore independently.

## Deep links

One platform-neutral request matches declared URI, action, and MIME constraints before mutation;
more constrained matches win and tied best matches fail closed. Android maps `Intent.data`,
`action`, and `type`, while extras and categories remain outside routing policy.

### Extra query parameters

Undeclared query parameters are inert. Validate the complete URL before routing when exact keys,
signatures, or security-sensitive query semantics are required.

## Save, restore, and process death

Saved state contains stack/selection identity, routes, owner registries, and saveable values, with
bounded decoding and fail-closed validation. Process restore recreates owners and values, not live
Views, ViewModels, effects, animations, previews, or uncommitted transactions. Configuration change
may retain ViewModels through the parent store; test these paths separately.

## System Back and Predictive Back

While `STARTED` and poppable, the host consumes the nearest NavigationEvent owner and falls back to
Activity Back. Both drive one transaction; roots delegate and removal cancels first. ViewCompose
exposes no duplicate dispatcher facade, forward history, or Android Studio Preview input.

## Migration paths

### From Navigation 2

1. Inventory destination types and isolate Fragment- or Activity-specific behavior.
2. Translate graph and route identities using only supported `NavValue` argument types.
3. Rewrite `NavOptions`, `popUpTo`, single-top, and save/restore intent as explicit expected stack
   results.
4. Map destination and graph ViewModel scopes, including factory, extras, and saved-state needs.
5. Configure multiple stacks and root-back behavior explicitly.
6. Translate URI, action, MIME, and combined rules into `NavDeepLink` declarations, then verify
   rejected and ambiguous external requests explicitly.
7. Verify transaction rollback, process death, system Back, and predictive cancellation.

### From Navigation 3

1. Decide which application-owned entry keys become ViewCompose route, entry, and stack
   identities.
2. Replace arbitrary key serialization with supported primitive route values and repository lookup.
3. Map decorators independently: saveable state, ViewModel stores, lifecycle, and custom metadata
   are not one ViewCompose feature.
4. Map panes plus trailing modal overlays to stable scene strategies; record other scene shapes as unsupported.
5. Verify repeated keys across multiple stacks and parent factory/`CreationExtras` propagation.
6. Replace application collection mutations with one supported transactional controller command.
7. Use the nearest official NavigationEvent owner already consumed by `NavHost`; do not wrap it in a
   duplicate owner. Keep forward history and Preview-specific input application-owned.

## Migration risks and unsupported behavior

- Activity and Fragment destinations are unsupported; only their role as Android hosts remains.
- The optional Kotlinx adapter supports generated flat scalar route serializers, not arbitrary
  nested/collection/polymorphic route objects, custom `NavType`, or Navigation3 key precedence;
  use an explicit `NavRouteSpec<T>` for unsupported wire shapes.
- Direct backward NavigationEvent input is supported, but forward history, a ViewCompose dispatcher
  facade, and Android Studio Preview input are unsupported.
- Scene strategies support content panes plus trailing modal overlays, not arbitrary Navigation3
  scene shapes, decorators, metadata, or separate-window destinations.
- Hidden sessions retain effects and native Views, increasing both lifecycle and memory risk.
- Arbitrary non-navigation UI scopes still require an application-owned provider boundary.
- Exact or signed deep-link query sets require application validation before routing; undeclared
  values are otherwise tolerated and inert.
- Pixel 4 XL/API 33 passed 16/16 host and platform-Back device cases. Direct nested-owner input
  passed the official JVM dispatcher fixture; Android Studio Preview input remains unverified.

## Re-verification requirements

Re-verify this page when any navigation command, entry identity, graph scope, lifecycle target,
deep-link rule, state format, pane policy, or Back integration changes. Also re-verify whenever the
stable Navigation 2, Navigation3, Lifecycle, SavedState, Activity, or NavigationEvent baseline
changes.

The minimum local evidence is the navigation-core controller, lifecycle, and deep-link tests; the
Android host owner, saved-state, destination-session, and Back-adapter tests; process-recreation
coverage; and the documented API 35 predictive-back device procedure. The upstream half requires
a fresh official semantic review. Do not infer Navigation 2.9.8 or Navigation3 1.1.6 parity solely
from the repository's Compose 1.7.8 executable dependency baseline.
