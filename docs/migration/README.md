---
title: Migrate from Jetpack Compose
slug: /migration
schema_version: 2
document_id: migration.compose-overview
doc_type: migration
owner:
  kind: project
  id: documentation-governance
version_lane: released
capability_ids: []
artifact_ids: []
sample_ids: []
source_state: Stable Jetpack Compose and AndroidX semantics reviewed from official release documentation.
target_state: The current independently versioned ViewCompose release set and its verified migration contracts.
---

# Migrate from Jetpack Compose

ViewCompose is Compose-inspired, but it is not a Compose compatibility layer. A successful
migration preserves ownership, lifecycle, and observable behavior rather than replacing similarly
named functions. Use this section to identify semantic gaps before moving a screen to the native
Android View renderer.

Last verified: **2026-08-28**

Re-verification owner: **maintainers of the Kernel, UI Foundation, Android Engine, Android
aggregate, and navigation module families**

## Verified source and target states

The target is the following independently versioned ViewCompose set:

| Module family | Artifacts | Verified version |
| --- | --- | --- |
| State and composition | `viewcompose-runtime`, `viewcompose-ui-foundation` | runtime `0.1.0-alpha04`; UI Foundation `0.1.0-alpha02` |
| UI and rendering | `viewcompose-ui-contract`, `viewcompose-renderer-android`, `viewcompose-constraintlayout-androidx` | contract `0.1.0-alpha05`; renderer/ConstraintLayout `0.1.0-alpha02` |
| Android ownership | `viewcompose-android`, `viewcompose-material3-android`, `viewcompose-host-android`, `viewcompose-lifecycle-androidx`, `viewcompose-viewmodel-androidx` | aggregates/integrations `0.1.0-alpha02`; host `0.1.0-alpha05` |
| Navigation | `viewcompose-navigation-core`, `viewcompose-navigation-android` | core `0.1.0-alpha03`; Android `0.1.0-alpha02` |
| Animation | `viewcompose-animation-core`, `viewcompose-animation` | both `0.1.0-alpha05` |

The immutable release revisions are recorded in
[`gradle/viewcompose-publishing.properties`](../../gradle/viewcompose-publishing.properties).

The upstream semantic baseline is:

| Dependency family | Version |
| --- | --- |
| Compose Runtime, UI, and Foundation | `1.12.0` |
| Activity | `1.13.0` |
| Lifecycle | `2.11.0` |
| SavedState | `1.5.0` |
| Navigation 2 | `2.9.8` |
| Navigation 3 | `1.1.5` |

The repository's executable comparison baseline remains Compose `1.7.8`, Activity `1.12.4`,
Lifecycle `2.11.0`, and Kotlin `2.2.10`, as declared in
[`gradle/libs.versions.toml`](../../gradle/libs.versions.toml). Official Android documentation and
release notes establish the newer upstream semantics; local source, tests, and compiled samples
establish ViewCompose behavior. Passing a local comparison against `1.7.8` does not prove parity
with `1.12.0`.

No performance equivalence is claimed. Any future performance comparison must state devices,
build modes, workloads, warm-up, sampling, and statistical treatment.

## Choose the migration path

| Source concern | Start here | Decide before implementation |
| --- | --- | --- |
| State, recomposition, keys, effects, or saveable state | [State, recomposition, and restoration](compose-state-recomposition-and-restoration.md) | State owner, restart boundary, identity, effect commit point, and restoration lifetime |
| Layout, Modifier, density, locals, insets, or Android View output | [Layout, Modifier, and environment](compose-layout-modifier-and-environment.md) | Measurement engine, modifier folding, logical edges, local invalidation, and inset owner |
| Activity, Fragment, existing View host, lifecycle, ViewModel, or Android interop | [Hosts, lifecycle, and Android interop](compose-host-lifecycle-and-android-interop.md) | Root owner, disposal boundary, installed owners, replay-safe work, and release cleanup |
| Navigation 2 or Navigation 3 | [Navigation](compose-navigation.md) | Source navigation model, route identity, owner scope, hidden-session policy, and Back integration |
| Image loading | [Image loading](image-loading.md) | Source types, loader ownership, request policy, and recycled-View disposal |
| Lazy collections and pagers | [Lazy collection revisions and reuse](lazy-collection-revision-and-reuse.md) | Semantic revisions, mounted-tree reuse, interop reset/release, and TabRow/Pager hard cuts |
| Component DSL aliases, interaction feedback, TextField wrappers, or alpha-only content animation | [DSL contract convergence](dsl-contract-convergence.md) | Variant replacement, indication ownership, typed input profiles, and Crossfade naming |
| Physics, `Animatable`, content/visibility transitions, seeking, bounds, shared motion, or animation tooling | [Animation](compose-animation.md) | Duration versus physical semantics, velocity, subtree identity, geometry owner, and inspection activation |

Read more than one page when a boundary crosses concerns. For example, `rememberSaveable` in a
navigation destination requires both the state/restoration and navigation ownership contracts.

## Consolidated capability matrix

This matrix is a coarse migration decision aid. The linked page owns the detailed contract and
evidence. Status terms have one meaning across all pages:

- **Supported** — the migration-relevant behavior exists with repository evidence.
- **Partially supported** — the main use case exists, but an important API or semantic boundary is
  narrower or different.
- **Intentionally different** — ViewCompose deliberately uses another ownership or execution
  model, so code must be redesigned.
- **Unsupported** — no corresponding public capability exists in this release.

| Domain | Capability | Status | Migration decision | Details |
| --- | --- | --- | --- | --- |
| State | Mutable state, mutation policies, and read observation | **Supported** | Preserve state ownership; do not depend on Compose callback counts or threads. | [State](compose-state-recomposition-and-restoration.md#mutable-state-and-mutation-policies) |
| State | Derived state and snapshot transactions | **Partially supported** | Review equal-result suppression, nesting, conflicts, and thread rules. | [State](compose-state-recomposition-and-restoration.md#derived-state-and-invalidation-differences) |
| State | Snapshot collections and `snapshotFlow` | **Partially supported** | `snapshotFlow` is available; snapshot collections still use immutable values in `MutableState`. | [State](compose-state-recomposition-and-restoration.md#snapshots-atomic-updates-and-conflicts) |
| Composition | Compiler-generated restart, stability, and strong skipping | **Intentionally different** | Choose explicit ViewCompose groups and place reads at the smallest update boundary. | [Recomposition](compose-state-recomposition-and-restoration.md#recomposition-without-the-compose-compiler) |
| Composition | Positional remember and keyed identity | **Partially supported** | Keep call order stable and do not rely on ordinary keyed-sibling movement during reorder. | [Identity](compose-state-recomposition-and-restoration.md#remembered-identity-keys-and-reordering) |
| Effects | `SideEffect`, `DisposableEffect`, `LaunchedEffect`, and `produceState` | **Supported** | Move external work to committed effects and make failure cleanup explicit. | [Effects](compose-state-recomposition-and-restoration.md#effects-and-committed-frame-boundaries) |
| Restoration | `rememberSaveable`, Saver, and host restoration | **Partially supported** | Prefer automatic keys, keep values small, and install services explicitly for custom hosts. | [Restoration](compose-state-recomposition-and-restoration.md#saveable-state-and-saver-migration) |
| Layout | Built-in containers, size, fill, and parent data | **Partially supported** | Revalidate behavior against Android View measurement and LayoutParams. | [Layout](compose-layout-modifier-and-environment.md#two-layout-engines-compose-constraints-and-android-views) |
| Layout | General custom measurement | **Unsupported** | Use a built-in container, ConstraintLayout, or a lifecycle-owned Android `ViewGroup`. | [Custom measurement](compose-layout-modifier-and-environment.md#two-layout-engines-compose-constraints-and-android-views) |
| Modifier | Padding, margin, ordering, and renderer folding | **Intentionally different** | Normalize chains and apply each modifier family's documented resolution rule. | [Modifier folding](compose-layout-modifier-and-environment.md#modifier-ordering-folding-and-equality) |
| Modifier | Structural equality and renderer reuse | **Supported** | Use semantic stable keys; a fresh callback object is not necessarily an update signal. | [Modifier equality](compose-layout-modifier-and-environment.md#modifier-ordering-folding-and-equality) |
| Modifier | Application-defined `Modifier.Node` lifecycle | **Unsupported** | Use supported modifiers, interop, or a reviewed UI-contract and renderer feature. | [Modifier.Node](compose-layout-modifier-and-environment.md#why-modifiernode-does-not-migrate-directly) |
| Environment | Density and font scale | **Supported** | Retain logical dp/sp values and convert only at the renderer boundary. | [Environment](compose-layout-modifier-and-environment.md#density-locales-and-layout-direction) |
| Environment | Locales, layout direction, and logical/physical edges | **Supported** | Use relative APIs for start/end intent, retain physical APIs for deliberate left/right behavior, and test RTL output. | [Environment](compose-layout-modifier-and-environment.md#density-locales-and-layout-direction) |
| Environment | `UiLocal` as a `CompositionLocal` replacement | **Intentionally different** | Back changing locals with observable state; local lookup alone does not invalidate readers. | [UiLocal](compose-layout-modifier-and-environment.md#uilocal-versus-compositionlocal) |
| Insets | System bars, IME, and nested consumption | **Partially supported** | Assign one owner per edge and verify mixed View/ViewCompose handling. | [Insets](compose-layout-modifier-and-environment.md#system-bar-and-ime-insets) |
| Interop | ViewCompose `AndroidView` callback lifecycle | **Intentionally different** | Separate replay-safe update/reset, post-transaction commit, and permanent-release cleanup. | [Android View interop](compose-host-lifecycle-and-android-interop.md#android-view-interop-callback-mapping) |
| Hosting | Activity and Fragment roots | **Partially supported** | Account for internally owned sessions and the Fragment owner/disposal mismatch. | [Standard hosts](compose-host-lifecycle-and-android-interop.md#choosing-a-host-entry-point) |
| Hosting | Existing-container `renderInto` | **Partially supported** | Install every required owner and dispose the returned session explicitly. | [Custom host](compose-host-lifecycle-and-android-interop.md#rendering-into-an-existing-view-hierarchy) |
| Ownership | General UI-scoped ViewModels and inherited `CreationExtras` | **Partially supported** | Preserve the proven destination/graph Factory/extras inheritance; add a general arbitrary-subtree provider. | [Owners](compose-host-lifecycle-and-android-interop.md#lifecycle-viewmodel-and-saved-state-owners) |
| Session | Explicit rendering, frame scheduling, and terminal disposal | **Intentionally different** | Treat `RenderSession` as the owner of composition, native tree, overlays, and cleanup. | [Sessions](compose-host-lifecycle-and-android-interop.md#session-frame-effect-and-disposal-semantics) |
| Interop | Direct ViewBinding and Fragment-in-tree APIs | **Unsupported** | Keep Fragment ownership outside the render tree and manage inflated XML explicitly. | [Unsupported interop](compose-host-lifecycle-and-android-interop.md#unsupported-direct-interop) |
| Navigation | Controller, destination, and multiple-stack ownership | **Intentionally different** | Translate desired state transitions rather than Navigation 2 or 3 API names. | [Navigation model](compose-navigation.md#choosing-the-source-navigation-model) |
| Navigation | Graphs, typed routes, and stack operations | **Partially supported** | Use supported primitive `NavValue` arguments and one transactional command. | [Routes and transactions](compose-navigation.md#graphs-routes-and-arguments) |
| Navigation | Entry/graph owners and Lifecycle 2.11 factory inheritance | **Supported** | Preserve the inherited parent Factory/extras and keep repeated-route stack owners isolated. | [Entry ownership](compose-navigation.md#entry-and-graph-ownership) |
| Navigation | Destination lifecycle and adaptive panes | **Intentionally different** | Allow multiple resumed entries and do not infer sole visibility from `RESUMED`. | [Lifecycle](compose-navigation.md#lifecycle-and-adaptive-panes) |
| Navigation | Hidden destination composition retention | **Partially supported** | Make background work lifecycle-aware; hidden sessions retain effects and native Views. | [Retention](compose-navigation.md#hidden-destination-retention) |
| Navigation | Deep links | **Partially supported** | Replace action/MIME rules; undeclared query values are tolerated but cannot affect navigation policy. | [Deep links](compose-navigation.md#deep-links) |
| Navigation | Save/restore, system Back, and Predictive Back | **Supported** | Recreate live objects after restore and retain device validation in the release procedure. | [Restoration and Back](compose-navigation.md#save-restore-and-process-death) |
| Navigation | Direct NavigationEvent integration | **Unsupported** | Keep direct dispatcher-owner, forward-event, test-fake, and Preview needs outside ViewCompose. | [NavigationEvent](compose-navigation.md#system-back-and-predictive-back) |
| Animation | Tween/keyframes/snap/repeat, physical spring, `Animatable`, decay, target-as-state, generic/seekable transitions, visibility, content replacement, and content-size animation | **Supported** | Retune physical units and preserve ViewCompose mutation, subtree, and shared-clock ownership instead of translating names mechanically. | [Animation](compose-animation.md#capability-matrix) |
| Animation | Bounds and one-window navigation shared motion | **Supported** | Keep geometry in the renderer and use typed `animateBounds`, `sharedElement`, or `sharedBounds` contracts; cross-window pairing and live reparenting remain unsupported. | [Animation](compose-animation.md#layout-and-shared-motion-mapping) |
| Animation | Timeline inspection and seeking tooling | **Partially supported** | Keep tooling debug-scoped; selected capture and Preview-owned seeking exist, while continuous profiling and remote live-app seeking do not. | [Animation](compose-animation.md#capability-matrix) |

## Migration sequence

1. Record the source Compose, Activity, Lifecycle, SavedState, and Navigation versions.
2. Inventory state, lifecycle, ViewModel, navigation, and durable-data owners before changing UI
   declarations.
3. Mark compiler restart boundaries, layout-measurement assumptions, modifier ordering, logical
   edges, locals, and inset ownership.
4. Classify every required capability using the matrix above. Stop and redesign any unsupported
   dependency before implementation begins.
5. Move one independently testable screen or subtree. Do not mix a host rewrite, navigation model
   rewrite, and persistence rewrite without separate behavioral assertions.
6. Compile the target code and verify recomposition, configuration recreation, process recreation,
   RTL, insets, Android View rollback, Back, and lifecycle behavior that applies to the screen.
7. Re-run the comparison when a listed upstream or ViewCompose version changes.

## Executable migration anchors

Documentation snippets are not a second source of truth. Use these compiled repository examples:

- the [`:samples:compose-migration` module](../../samples/compose-migration/build.gradle.kts) contains the paired
  state, layout/environment, host/Android interop, and Navigation 2 excerpts embedded in the four
  detailed migration pages;
- the [counter application](../../samples/counter/src/main/java/com/viewcompose/samples/counter/MainActivity.kt)
  combines Activity hosting, remembered mutable state, View-backed layout, modifiers, and input;
- [runtime samples](../../viewcompose-runtime/src/test/samples/com/viewcompose/runtime/samples/RuntimeSamples.kt)
  cover mutable and derived state, snapshot transactions, policies, observation, and composition;
- [UI Foundation samples](../../viewcompose-ui-foundation/src/test/samples/com/viewcompose/ui/foundation/samples/WidgetCoreSamples.kt)
  cover saveable-state registry and theme ownership;
- [Android application-entry samples](../../viewcompose-android/src/test/samples/com/viewcompose/android/samples/AndroidEntrySamples.kt)
  cover Activity, Fragment, custom-container, and Android View hosting;
- [navigation-core samples](../../viewcompose-navigation-core/src/test/samples/com/viewcompose/navigation/core/samples/NavigationCoreSamples.kt)
  cover graphs, deep links, transactions, and lifecycle planning; and
- [Android navigation samples](../../viewcompose-navigation-android/src/test/samples/com/viewcompose/navigation/samples/NavigationAndroidSamples.kt)
  cover remembered hosts, controller operations, and motion configuration.

The root `qaQuick` task compiles these sample source sets or the tests that consume them. It also
runs `verifyMigrationPairedSamples`, which rejects missing, extra, reordered, or stale paired
snippets in both canonical English pages and required Chinese mirrors. Device-only restoration and
Predictive Back evidence remains governed by the procedures linked from the
[state/restoration comparison](./compose-state-recomposition-and-restoration.md) and
[navigation guide](../guides/navigation.md).

## Known contract gaps

Do not strengthen a capability label until source documentation, implementation, and executable
evidence agree on these points:

- Equal-result and nested derived state plus read-only snapshot nesting need focused regression
  coverage.
- Repeated size/padding rules, nested inset consumption, and native-view callback identity need
  broader executable coverage.
- Lifecycle `2.11.0` arbitrary UI scopes do not have ViewCompose parity evidence.
- A fresh Predictive Back device run remains narrower than the full semantic baseline.

Re-verification must review official upstream documentation first, then immutable ViewCompose
source contracts, tests, compiled samples, and applicable device procedures. A signature match or
API-name similarity is never sufficient evidence.
