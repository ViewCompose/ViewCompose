---
schema_version: 2
document_id: migration.compose-navigation
doc_type: migration
owner:
  kind: capability
  id: navigation.host
version_lane: released
capability_ids:
  - navigation.destination-context
  - navigation.host
  - navigation.presentation-retention
  - navigation.scene-projection
artifact_ids:
  - viewcompose-navigation-core
  - viewcompose-navigation-android
  - viewcompose-ui-foundation
  - viewcompose-lifecycle-androidx
  - viewcompose-viewmodel-androidx
sample_ids: []
source_state: Jetpack Navigation 2.9.8 and Navigation3 1.1.5 with Compose 1.12.0 and current stable AndroidX owner semantics.
target_state: ViewCompose Navigation Core 0.1.0-alpha03 and Navigation Android 0.1.0-alpha02 transactional host contracts.
---

# Migrating Compose Navigation to ViewCompose

This page compares ViewCompose navigation with both Jetpack Navigation 2 and Navigation 3.
Navigation 2 and Navigation 3 have different ownership models, so a migration must identify its
actual source before mapping APIs or lifecycle behavior.

- **Source state:** Navigation 2.9.8 or Navigation3 1.1.5, with Compose UI/Runtime 1.12.0,
  Activity 1.13.0, Lifecycle 2.11.0, and SavedState 1.5.0.
- **Target state:** `viewcompose-navigation-core` 0.1.0-alpha03 and
  `viewcompose-navigation-android`, `viewcompose-lifecycle-androidx`, and
  `viewcompose-viewmodel-androidx` 0.1.0-alpha02.
- **Last verified:** 2026-08-27.
- **Re-verification owner:** maintainers of `viewcompose-navigation-core`,
  `viewcompose-navigation-android`, `viewcompose-lifecycle-androidx`, and `viewcompose-viewmodel-androidx`.

## Verification model

The upstream side is a semantic review of official stable documentation and release notes:

- [Navigation 2 back stack](https://developer.android.com/guide/navigation/backstack)
- [Navigation 2 multiple back stacks](https://developer.android.com/guide/navigation/backstack/multi-back-stacks)
- [`NavBackStackEntry`](https://developer.android.com/reference/androidx/navigation/NavBackStackEntry)
- [Navigation 2 deep links](https://developer.android.com/guide/navigation/design/deep-link)
- [Navigation 2 release notes](https://developer.android.com/jetpack/androidx/releases/navigation)
- [Navigation 3 overview](https://developer.android.com/guide/navigation/navigation-3)
- [Navigation 3 basics](https://developer.android.com/guide/navigation/navigation-3/basics)
- [Navigation 3 save state](https://developer.android.com/guide/navigation/navigation-3/save-state)
- [Navigation 3 entry decorators](https://developer.android.com/guide/navigation/navigation-3/naventrydecorators)
- [Navigation 3 scenes](https://developer.android.com/guide/navigation/navigation-3/scenes)
- [Navigation 3 multiple-back-stack recipe](https://developer.android.com/guide/navigation/navigation-3/recipes/multiple-backstacks)
- [Navigation 3 deep-link recipe](https://developer.android.com/guide/navigation/navigation-3/recipes/deeplinks-basic)
- [Navigation3 1.1.5 release notes](https://developer.android.com/jetpack/androidx/releases/navigation3)
- [Lifecycle 2.11 release notes](https://developer.android.com/jetpack/androidx/releases/lifecycle)
- [Activity 1.13 release notes](https://developer.android.com/jetpack/androidx/releases/activity)
- [NavigationEvent release notes](https://developer.android.com/jetpack/androidx/releases/navigationevent)

The repository's executable Android baseline is Compose 1.7.8, Navigation 2.9.8, Activity 1.12.4,
Lifecycle 2.11.0, and Kotlin 2.2.10. The paired sample below compiles one Navigation 2 controller,
host, route, and navigation action on each side. The cited ViewCompose JVM, integration, and device
tests establish broader local behavior. None of this is an executable comparison against
Navigation3 1.1.5, and the paired sample does not prove parity with the complete Navigation 2.9.8
surface. Navigation 2 and Navigation 3 claims must still be re-reviewed from official sources
whenever those versions change.

The ViewCompose implementation is split between the platform-neutral
[navigation core](../modules/viewcompose-navigation-core/README.md) and the Android
[navigation host](../modules/viewcompose-navigation-android/README.md). The task-oriented
[navigation guide](../guides/navigation.md) is supplementary evidence, but source and tests take
precedence if the guide conflicts with executable behavior.

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
| Graphs and typed routes | Navigation 2 supports graphs and typed or serializable routes. Navigation 3 keys are application-defined and normally saveable; 1.1.5 gives an instance-key `entry` registration precedence over a class-key registration. | Graph and destination identities are explicit, but route arguments use the closed `NavValue` set: null, string, int, long, boolean, float, and double. | Partially supported | [`NavigationModel.kt`](../../viewcompose-navigation-core/src/main/kotlin/com/viewcompose/navigation/core/NavigationModel.kt), graph tests in [`NavBackStackControllerTest.kt`](../../viewcompose-navigation-core/src/test/kotlin/com/viewcompose/navigation/core/NavBackStackControllerTest.kt), and public graph coverage in [`NavHostPublicApiTest.kt`](../../viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostPublicApiTest.kt). There is no compiler-generated route serialization or Navigation3 instance/class registration precedence to preserve. |
| Back-stack operations | Navigation 2 supplies `navigate`, `popBackStack`, `popUpTo`, and `NavOptions`; Navigation 3 represents stack changes through application collection updates. | Push, pop, replace, reset, stack selection, and deep-link commands are prepared, rendered, and then committed or rolled back as one transaction. | Partially supported | [`NavBackStackController.kt`](../../viewcompose-navigation-core/src/main/kotlin/com/viewcompose/navigation/core/NavBackStackController.kt), [`NavBackStackControllerTest.kt`](../../viewcompose-navigation-core/src/test/kotlin/com/viewcompose/navigation/core/NavBackStackControllerTest.kt), and [`NavHostPublicApiTest.kt`](../../viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostPublicApiTest.kt). The two-phase transaction is stronger than an API-name mapping but is not the full Navigation 2 `NavOptions` surface. |
| Entry and graph owners | `NavBackStackEntry` owns lifecycle, ViewModel, and saved state. Lifecycle 2.11 adds Navigation3 ViewModel decorators that inherit parent factories and `CreationExtras`. | Each destination and graph gets its own lifecycle, saved-state owner, ViewCompose saveable-state registry, and leased ViewModelStore. Owners inherit the required host parent's default Factory and starting extras, then replace their child ownership and route defaults. | Supported | [`NavEntryOwner.kt`](../../viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavEntryOwner.kt), [`NavGraphOwner.kt`](../../viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavGraphOwner.kt), [`NavEntryOwnerEnvironment.kt`](../../viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavEntryOwnerEnvironment.kt), and Factory, extras, SavedStateHandle, destination, and graph coverage in [`NavEntryOwnerTest.kt`](../../viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavEntryOwnerTest.kt) and [`NavHostPublicApiTest.kt`](../../viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostPublicApiTest.kt). |
| Scoped ViewModels and multiple stacks | Lifecycle 2.11 can hoist a `ViewModelStoreProvider` above the Navigation3 decorator so multiple back stacks retain isolated entry stores. | `NavHost` uses the shared `ViewModelScopeProvider` below its required parent owner. A saved host-scope identity plus entry/graph identity retains isolated stores across stack switches and configuration recreation; terminal pop, graph removal, and normal host removal clear them. | Supported | [`NavHostRuntime.kt`](../../viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavHostRuntime.kt), [`NavEntryOwnerStore.kt`](../../viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavEntryOwnerStore.kt), [`NavEntryOwnerStoreTest.kt`](../../viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavEntryOwnerStoreTest.kt), and the same-route retained-stack isolation test in [`NavHostPublicApiTest.kt`](../../viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostPublicApiTest.kt). Navigation owns lifecycle and identity coordination, not a second ViewModelStore allocator. |
| Destination lifecycle | Navigation entries are lifecycle owners; Navigation 3 scenes can present multiple entries. | `NavSceneEntry` derives scene and entry caps from presence, visibility, interaction, transition, pane, and layer roles; the planner applies `min(host, scene, entry)`. The Android host freezes the scene for ordinary and predictive transitions, caps visible participants at `STARTED`, caps a popped exit at `CREATED`, and resumes settled interactive panes only after termination. | Supported | [`NavScene.kt`](../../viewcompose-navigation-core/src/main/kotlin/com/viewcompose/navigation/core/NavScene.kt), [`NavLifecyclePlanner.kt`](../../viewcompose-navigation-core/src/main/kotlin/com/viewcompose/navigation/core/NavLifecyclePlanner.kt), transition and adaptive coordinator tests, and the selected physical-device lifecycle test in [`NavigationBackDeviceTest.kt`](../../app/src/androidTest/java/com/viewcompose/NavigationBackDeviceTest.kt). Support covers current single- and multi-pane host scenes; general overlay navigation remains a separate partial capability. |
| Destination presentation context | Navigation 3 entry content can observe scene metadata through its entry scope; Compose content also observes composition-local values. | `LocalNavDestinationContext` exposes a stable per-entry holder whose read-only presentation is the exact Core scene entry. It survives native presentation disposal/recreation, nests by nearest host, and excludes frame progress. AndroidX Lifecycle remains the resource-threshold API. | Supported | [`NavDestinationContext.kt`](../../viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavDestinationContext.kt), [`NavEntryOwnerEnvironment.kt`](../../viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavEntryOwnerEnvironment.kt), and holder, local-capture, nested-host, pane, overlay, removal, and predictive-progress coverage in Navigation Android tests. General overlay navigation remains unsupported even though the context can represent a Core overlay scene. |
| Hidden destination composition | Navigation state can be retained independently of whether Compose content remains in Composition. Navigation 3 decorators retain entry state. | Logical entry owners, ViewModels, saved state, and saveable state survive independently of native presentation. `DisposeWhenHidden` is the bounded default; explicit retain-all and bounded least-recently-hidden policies are available. Reveal rebuilds a missing presentation transactionally. | Supported | [`NavPresentationRetentionPolicy.kt`](../../viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavPresentationRetentionPolicy.kt), [`NavDestinationSessionStore.kt`](../../viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavDestinationSessionStore.kt), unit ownership/rebuild/LRU coverage, and physical-device identity and resource-count coverage in [`NavigationBackDeviceTest.kt`](../../app/src/androidTest/java/com/viewcompose/NavigationBackDeviceTest.kt). |
| Multiple back stacks | Navigation 2 uses save/restore options; Navigation 3 documents application-owned multiple-list recipes. | One `NavStackConfiguration` owns all stacks, selection history, and root-back behavior. Non-selected stack owners remain live while their optional presentations follow the host retention policy. | Intentionally different | [`NavStackConfiguration.kt`](../../viewcompose-navigation-core/src/main/kotlin/com/viewcompose/navigation/core/NavStackConfiguration.kt), [`NavBackStackSetControllerTest.kt`](../../viewcompose-navigation-core/src/test/kotlin/com/viewcompose/navigation/core/NavBackStackSetControllerTest.kt), and multi-stack restoration in [`NavHostPublicApiTest.kt`](../../viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostPublicApiTest.kt). |
| Deep links | Navigation 2 matches URI, action, and MIME type. Navigation 3 supplies recipes for parsing external input into application keys. | ViewCompose resolves strict absolute URI patterns and Android `ACTION_VIEW` input, supports nested graphs and caller-selected launch modes, rejects ambiguous matches, and does not match arbitrary actions or MIME types. Extra input query parameters are tolerated but cannot influence route arguments or navigation policy. | Partially supported | [`NavDeepLink.kt`](../../viewcompose-navigation-core/src/main/kotlin/com/viewcompose/navigation/core/NavDeepLink.kt), [`NavDeepLinkTest.kt`](../../viewcompose-navigation-core/src/test/kotlin/com/viewcompose/navigation/core/NavDeepLinkTest.kt), and public-host policy coverage in [`NavHostPublicApiTest.kt`](../../viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostPublicApiTest.kt). Partial support reflects the intentionally smaller action/MIME surface, not an unresolved query contract. |
| Save, restore, and process death | Navigation 2 restores controller and entry state; Navigation 3 restores saveable keys and decorator state. Neither restores live ViewModel instances. | ViewCompose saves the complete configured stack set, route values, entry and graph saved state, saveable values, and a private host-scope identity. It retains live ViewModels only through the parent store during configuration recreation, migrates version-4 snapshots with a fresh scope identity, and rejects corrupt or structurally invalid state. | Supported | [`NavHostSavedState.kt`](../../viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavHostSavedState.kt), [`NavHostSavedStateTest.kt`](../../viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostSavedStateTest.kt), and restoration coverage in [`NavHostPublicApiTest.kt`](../../viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostPublicApiTest.kt). Live Views, ViewModels, effects, animations, and uncommitted transactions are not process-restored. |
| System Back and Predictive Back | Navigation 2 Compose integrates Predictive Back. Navigation 3 uses NavigationEvent and scene transitions. Activity 1.13 keeps `OnBackPressedDispatcher` compatible on top of NavigationEvent. | The Android host registers an `OnBackPressedCallback` and supports predictive start, progress, cancellation, and commit through a transactional preview driver. | Supported | [`AndroidNavHostBackAdapter.kt`](../../viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/AndroidNavHostBackAdapter.kt), [`AndroidNavHostBackAdapterTest.kt`](../../viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/AndroidNavHostBackAdapterTest.kt), and 2/2 selected predictive/lifecycle instrumentation cases on a physical API-33 Pixel 4 XL. API-34 platform edge-gesture delivery was not rerun in this slice. |
| Direct NavigationEvent integration | Activity and Navigation3 expose `NavigationEventDispatcher`, nested dispatcher owners, testing utilities, and Compose handlers. Navigation3 1.1.3 uses NavigationEvent 1.1.2, including Predictive Back in Android Studio Preview inspection mode. | ViewCompose uses the compatible Activity `OnBackPressedDispatcher` path but exposes no direct NavigationEvent callback, dispatcher-owner, forward-event, testing, or Preview integration. | Unsupported | No corresponding ViewCompose public API was found. Existing back behavior remains supported because Activity implements `OnBackPressedDispatcher` on top of NavigationEvent; unsupported refers to direct integration only. |
| Adaptive panes and overlays | Navigation 3 scenes can select one or more entries and coordinate overlays and transitions. Versions 1.1.3 and 1.1.4 fix nested-overlay and popped-entry metadata animation defects. | ViewCompose exposes a general platform-neutral semantic scene value and a fixed host policy for up to three newest pane entries. Adaptive panes consume the same transition lifecycle scene, but the Android host has no general overlay-navigation surface or one reducer plan spanning overlays and focus. | Partially supported | [`NavScene.kt`](../../viewcompose-navigation-core/src/main/kotlin/com/viewcompose/navigation/core/NavScene.kt), adaptive host tests, and lifecycle planner tests. The partial rating is now caused by overlay and scene-strategy breadth, not ordinary or predictive transition lifecycle. |

## Choosing the source navigation model

Identify the source model before changing code:

- **Navigation 2 source:** the library owns a `NavController`, graph, destinations, and back stack.
  Map graph and controller behavior first, then replace Compose, Fragment, or Activity destination
  content with ViewCompose-native page content.
- **Navigation 3 source:** application state owns entry keys and `NavDisplay` derives scenes. Map
  key serialization, scene selection, decorators, and state ownership before moving the data into
  a ViewCompose controller-owned stack set.

ViewCompose is not a drop-in implementation of either source. Its controller-owned immutable
snapshots resemble Navigation 2 ownership, while its explicit entry identities, retained sessions,
and pane selection overlap with Navigation 3 concepts.

## Host and destination architecture

`NavHost` mounts destinations as native View-backed `RenderSession` instances. The nearest host
lifecycle caps every entry and graph lifecycle. Activity and Fragment integrations supply the
outer host; they are not route destinations. `NavHost` also requires their
`LocalViewModelStoreOwner`. A custom low-level `renderInto` host must provide that owner explicitly.

A migration from Navigation 2 Fragment destinations must therefore separate concerns that were
previously combined in the Fragment: route identity, screen content, lifecycle collection,
ViewModel scope, saved state, result delivery, and Android component integration. Fragment-only
behaviors cannot be retained by placing a Fragment inside the ViewCompose render tree.

The host stages controller state, attempts the native-tree render, and commits the navigation
transaction only when rendering succeeds. A failure preserves the previous committed stack and
mounted destination tree.

Hidden presentation lifetime is an explicit migration decision. The default
`NavPresentationRetentionPolicy.DisposeWhenHidden` most closely matches a model where retained
navigation state does not require live composition. It preserves destination and graph owners,
ViewModels, SavedStateRegistry, and `rememberSaveable` values while disposing the native View tree
and composition effects. Use `Bounded` only with an application-selected positive cache size, and
use `RetainAll` only when device evidence justifies unbounded hidden native resources. Restored
hosts create only the visible pane set; selecting an inactive stack rebuilds its presentation
before publishing that scene.

## Graphs, routes, and arguments

ViewCompose graph, destination, entry, and stack identities are explicit. Route arguments are
limited to the `NavValue` primitive set. This keeps controller snapshots deterministic and
saveable, but it is narrower than Navigation 2 typed serialization and arbitrary Navigation 3
application keys.

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

The lifecycle planner now consumes a validated `NavScene` rather than separate retained, visible,
and interactive ID sets. Each destination target is `min(host cap, scene cap, entry cap)`:

- retained hidden entries target `CREATED`;
- visible, non-interactive entries target `STARTED`;
- interactive entries target `RESUMED`; and
- no entry or graph can exceed the host lifecycle.

Downward transitions are applied before upward transitions, and permanent removals reach
`DESTROYED` before a revealed entry advances. An adaptive pane can make more than one destination
interactive, so more than one entry may be `RESUMED`. Code migrated from a single-top-entry model
must not use `RESUMED` as proof that a destination is the only visible page.

During ordinary or predictive motion, the Android host freezes one semantic scene. Every visible
participant is non-interactive and no higher than `STARTED`; a popped outgoing destination is
`CREATED` until its exit presentation is disposed. Cancellation restores the prior settled scene,
and commit resumes the incoming destination only after terminal settlement. Destination DSL uses
the same nearest `LocalLifecycleOwner` API as Activity, Fragment, Preview, and custom-container
content; no navigation-specific Lifecycle API is required.

Destination DSL may additionally read `LocalNavDestinationContext.current` for coarse
presentation semantics. Capture the stable holder during declaration when a callback needs it;
its `entry` identity and read-only presentation state survive native View disposal and recreation.
Do not move resource activation from AndroidX Lifecycle to visibility or transition enums, and do
not expect frame-rate transition or predictive progress from this context. Nested hosts publish
their child holder only inside child destination content, so there is no global current page.

This is an Alpha hard cut. Replace the two old `NavLifecyclePlanner.plan` overloads with the single
overload that accepts `entries` and `scene`. Construct `NavSceneEntry` values for hidden, pane,
transition, overlay, prepared, exiting, and removed roles; do not rebuild visible and interactive
sets beside the scene. The Android host consumes transition roles for its current ordinary,
predictive, and adaptive-pane scenes. Do not infer general overlay-navigation support from the Core
layer vocabulary.

ViewCompose selects up to the three newest eligible pane entries. Navigation3 1.1.5 has a more
general scene and metadata model. Its 1.1.3 nested-overlay fix and 1.1.4 popped-entry metadata-lambda
fix are upstream reliability changes, not evidence that ViewCompose supports arbitrary scenes or
the same overlay animation lifecycle.

## Hidden destination retention

A hidden ViewCompose destination retains its `RenderSession`, owners, mounted View tree, and
composition coroutine scope. The host sets frame-driven rendering inactive and hides the root
View, but hiding alone does not dispose the composition or cancel composition-scoped work.

Any work that should stop while a page is hidden must be lifecycle-aware and stop below the
required destination lifecycle state. Do not rely only on composition disposal, because disposal
occurs when the entry is permanently removed, the graph is destroyed, or the host is torn down.
This is a material difference from migration code whose Compose content leaves Composition while
only saveable entry state remains.

## Multiple back stacks

`NavStackConfiguration` declares the stack set, initial selection, selection-history behavior, and
root-back policy. Selecting another stack retains the previous stack's entries, owners, Views, and
sessions. The state cost is therefore a live-retention cost, not only a serialized back-stack cost.

Lifecycle 2.11 Navigation3 integration can hoist a ViewModelStore provider across multiple stack
displays while isolating stores for repeated keys. ViewCompose relies on its own stack and entry
identities. Before migrating repeated route keys across tabs, verify that the two entries receive
separate owner stores, that removing one does not clear the other, and that save/restore preserves
the intended identity.

## Deep links

ViewCompose deep links use strict absolute URI patterns and can target nested graphs. Resolution
rejects malformed input, untrusted URI components, duplicate parameters, and ambiguous matches.
The Android host accepts the supported external route through `ACTION_VIEW`. Navigation 2 action
and MIME-type matching have no direct counterpart.

### Extra query parameters

ViewCompose tolerates input query parameters that are not declared by the matched pattern. They are
inert: they do not enter `NavRoute.arguments`, increase specificity, resolve an otherwise ambiguous
match, select a retained stack, or override the caller's launch mode. This supports tracking
parameters without allowing unregistered input to become navigation policy. Validate the complete
URL in application code before routing when exact keys, signatures, or security-sensitive query
semantics are required.

## Save, restore, and process death

The Android host saves the complete stack set, current stack, selection history, route arguments,
entry and graph saved-state registries, ViewCompose saveable values, and a private host-scope
identity. The decoder migrates the immediately preceding version-4 state with a fresh scope
identity, validates current format and graph shape, and fails closed to a safe initial state for
corrupt or incompatible input. Defensive limits currently cap decoded stack count at 100 and total
entry count at 10,000.

Process restoration does not resurrect View instances, ViewModel instances, coroutine effects,
animations, predictive-back previews, or uncommitted transactions. It recreates owners and page
state from saved values. Configuration recreation can retain live ViewModels through the parent
store and saved scope identity. Test process death separately from configuration change and from
an in-memory stack switch.

## System Back and Predictive Back

The ViewCompose Android adapter registers with `OnBackPressedDispatcher`, updates enablement from
the controller's ability to handle Back, and drives predictive start, progress, cancellation, and
commit through the staged navigation transaction. Activity 1.13 remains compatible with this API
because Activity's dispatcher is implemented on top of NavigationEvent.

Direct NavigationEvent integration is nevertheless unsupported. ViewCompose does not expose a
`NavigationEventDispatcherOwner`, `NavigationEventCallback`, forward-event fallback, official
NavigationEvent test fake, or inspection-mode Preview handler. New integration work should prefer
NavigationEvent as recommended by Activity rather than assuming the legacy adapter is the final
abstraction.

Navigation3 1.1.3 updates its NavigationEvent dependency to 1.1.2, which enables Predictive Back
in Android Studio Preview inspection mode; Navigation3 1.1.5 retains that behavior. ViewCompose
device procedures and adapter tests do not verify Preview support. The API 35 device procedure
referenced by the navigation guide was not rerun while authoring this page, so device status is
carried as existing repository evidence rather than a fresh result.

## Migration paths

### From Navigation 2

1. Inventory destination types and isolate Fragment- or Activity-specific behavior.
2. Translate graph and route identities using only supported `NavValue` argument types.
3. Rewrite `NavOptions`, `popUpTo`, single-top, and save/restore intent as explicit expected stack
   results.
4. Map destination and graph ViewModel scopes, including factory, extras, and saved-state needs.
5. Configure multiple stacks and root-back behavior explicitly.
6. Rebuild deep links around supported URI patterns and `ACTION_VIEW`; replace action/MIME rules.
7. Verify transaction rollback, process death, system Back, and predictive cancellation.

### From Navigation 3

1. Decide which application-owned entry keys become ViewCompose route, entry, and stack
   identities.
2. Replace arbitrary key serialization with supported primitive route values and repository lookup.
3. Map decorators independently: saveable state, ViewModel stores, lifecycle, and custom metadata
   are not one ViewCompose feature.
4. Replace general scenes with the supported pane policy or record the scene as unsupported.
5. Verify repeated keys across multiple stacks and parent factory/`CreationExtras` propagation.
6. Replace application collection mutations with one supported transactional controller command.
7. Keep direct NavigationEvent and Preview dependencies outside ViewCompose until an integration
   exists.

## Migration risks and unsupported behavior

- Activity and Fragment destinations are unsupported; only their role as Android hosts remains.
- Arbitrary serializable route objects and compiler-generated typed-route parity are unsupported.
- Navigation 2 action and MIME deep-link matching are unsupported.
- Direct NavigationEvent dispatcher-owner, callback, forward-event, testing, and Preview APIs are
  unsupported.
- General Navigation3 scene strategies and metadata are unsupported; the pane policy is narrower.
- Hidden sessions retain effects and native Views, increasing both lifecycle and memory risk.
- Arbitrary non-navigation UI scopes still require an application-owned provider boundary.
- Exact or signed deep-link query sets require application validation before routing; undeclared
  values are otherwise tolerated and inert.
- API-34 platform edge-gesture delivery and NavigationEvent Preview behavior were not rerun for this
  page; selected predictive and lifecycle behavior passed on a physical API-33 device.

## Re-verification requirements

Re-verify this page when any navigation command, entry identity, graph scope, lifecycle target,
deep-link rule, state format, pane policy, or Back integration changes. Also re-verify whenever the
stable Navigation 2, Navigation3, Lifecycle, SavedState, Activity, or NavigationEvent baseline
changes.

The minimum local evidence is the navigation-core controller, lifecycle, and deep-link tests; the
Android host owner, saved-state, destination-session, and Back-adapter tests; process-recreation
coverage; and the documented API 35 predictive-back device procedure. The upstream half requires
a fresh official semantic review. Do not infer Navigation 2.9.8 or Navigation3 1.1.5 parity solely
from the repository's Compose 1.7.8 executable dependency baseline.
