# ViewCompose Architecture

## 1. Purpose

This document is the **current architecture specification** for `ViewCompose`. It defines:

1. module responsibilities and boundaries;
2. the core execution path;
3. placement rules for new code; and
4. constraints that every change must preserve.

If an implementation needs to depart from this specification, update this document before changing the code.

The [multi-design-system architecture and integration standard](design-systems.md) is the normative
policy for theme, recipe, component-backend, and host ownership. Its explicitly listed current
nonconformance is tracked by the active execution plan and must not be copied into new APIs.

The historical long-form snapshot is available at [ARCHITECTURE_FULL_2026-03-06.md](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/ARCHITECTURE_FULL_2026-03-06.md).

## 2. Current baseline (2026-08)

- Technology: Kotlin on the Android View system.
- SDK: `minSdk 24`, `compileSdk 36`.
- Runtime modules are classified into five dependency layers: Kernel, UI Foundation, Android Engine,
  Design System, and Integrations. `viewcompose-android` is the consumer aggregate above those
  layers; preview, benchmark, and build support remain orthogonal tooling.

### 2.1 Module responsibilities

| Module | Responsibility | Constraint |
| --- | --- | --- |
| `viewcompose-runtime` | State and read-dependency observation (`state/observation`) | Pure Kotlin/JVM; production sources must not import `android.*` or `androidx.*`, and the build must not add AndroidX dependencies. |
| `viewcompose-text-core` | Complete plain-text editing state, including text, selection, composition, `EditingBuffer`, input transformations, undo, and redo | Pure Kotlin/JVM with no Android types; offsets use UTF-16 to match platform editing protocols. |
| `viewcompose-ui-contract` | Pure Kotlin UI contracts such as `Modifier`, `VNode`, `NodeSpec`, layout enums, and collection/state protocols | Production sources must not import `android.*` or `androidx.*`. |
| `viewcompose-navigation-core` | System-navigation kernel: routes, back stack, two-phase transactions, and page-lifecycle planning | Pure Kotlin/JVM with no Android or AndroidX types; page sessions and platform back adapters do not belong here. |
| `viewcompose-navigation-android` | Android system-navigation integration: destination owners, page sessions, `NavHost`, and back adapters | Depends on navigation-core and host-android; host-android must not depend back on it. |
| `viewcompose-animation-core` | Animation kernel: `AnimationSpec`, `Easing`, converters, engine, and `TransitionCore` | Pure Kotlin/JVM; no Android dependency. |
| `viewcompose-animation` | Animation DSL integration: `animate*AsState`, `Animatable`, transitions, `AnimatedVisibility`, and animated content | Public-call API; runtime driving uses `MonotonicFrameClock` plus coroutines and does not depend directly on Android View animations. |
| `viewcompose-gesture-core` | Gesture policy kernel: axis lock, transform slop, and swipe settling | Pure Kotlin/JVM; the renderer only adapts events and invokes this kernel. |
| `viewcompose-gesture` | Platform-independent gesture DSL: `pointerInput`, `combinedClickable`, dragging, anchored dragging, and transforms | Defines modifier and state entry points only; policy decisions stay in gesture-core. |
| `viewcompose-graphics-core` | Platform-independent graphics kernel: geometry, paths, brushes, draw commands, and draw caches | Pure Kotlin/JVM; defines graphics models only. |
| `viewcompose-graphics` | Graphics DSL integration: `Canvas`, `drawBehind`, `drawWithContent`, and `drawWithCache` | Defines business-facing APIs and contract mappings without depending directly on Android Canvas. |
| `viewcompose-shadow-android` | Optional advanced-shadow backend, cache, and Android drawing implementation | Depends only on the renderer's minimal decoration SPI; renderer and host do not depend on it; installation uses `ServiceLoader` or an explicit call. |
| `viewcompose-ui-foundation` | Renderer-independent DSL, framework theme/defaults, locals, composition coordinator, and overlay declaration contracts | Owns `com.viewcompose.ui.foundation`; does not depend on AndroidX, Material, renderer, or Android host entry points, and delegates native containers, focus, logging, and tracing through host-installed contracts. |
| `viewcompose-constraintlayout-androidx` | ConstraintLayout component DSL | Contains only the DSL and scopes; platform rendering remains in renderer. |
| `viewcompose-renderer-android` | Android View rendering: reconciliation, binders, patches, containers, framework shape drawing, and progress drawing | Consumes portable contracts and contains neither business DSL nor Material widgets. |
| `viewcompose-host-android` | Low-level Android engine host: `renderInto`, `RenderSession`, native View interop, and render-platform installation | Does not expose Activity/Fragment convenience entry points and does not depend on Material. |
| `viewcompose-material3` | Material 3 theme snapshot, token mapping, dynamic-color policy, refresh lifecycle, and bounded named component pressure slice | Owns Material/AppCompat theme interpretation plus Material recipes/components; UI Foundation and Android Engine do not depend on it. |
| `viewcompose-material3-android` | Named Material 3 Android application aggregate and Activity/Fragment host integration | Resolves the Material root Context before View construction, then delegates mounting to the neutral Android aggregate and provides the matching token snapshot. |
| `viewcompose-oneui7` | Static One UI 7 alpha tokens and the bounded Button, Surface, Switch, TextField, and text-only NavigationBar set | Owns its named recipes and composites; it has no Material dependency and adds no design-system branch to Android Renderer. |
| `viewcompose-android` | Neutral Android consumer aggregate and Activity/Fragment `setUiContent` entry points | Aggregates the default engine, UI Foundation, Lifecycle, and ViewModel integrations without selecting Material or another design system. An explicit root Context and composition provider establish design policy. |
| `viewcompose-overlay-android` | Material-free Android overlay transport for dialogs, popups, toasts, nested surfaces, and root/session cleanup | Supplies narrow Snackbar and modal-sheet presenter slots; it never selects or depends on a design system. |
| `viewcompose-overlay-material3-android` | Material Snackbar and modal-bottom-sheet adapter | Explicitly composes Material presenters with the neutral Android transport and registers no whole-host provider. |
| `viewcompose-overlay-oneui7-android` | Material-free One UI Snackbar and bottom-dialog adapter | Explicitly composes One UI presenters with the neutral Android transport; it adds no duplicate Activity/Fragment host API. |
| `viewcompose-image-coil` | Optional image-loading adapter | Implements `UiImageLoader` for Coil 3; it accepts the general source/request contract without feeding Coil concerns back into the renderer core. |
| `viewcompose-image-glide` | Optional image-loading adapter | Implements `UiImageLoader` for Glide 5 with target-scoped `RequestManager` resolution and application-owned `AppGlideModule` configuration. |
| `viewcompose-lifecycle-androidx` | Lifecycle-aware collection APIs and lifecycle Local entry points | Does not contain Android View implementations or add host-injection logic. |
| `viewcompose-viewmodel-androidx` | ViewModel and SavedStateHandle collaboration APIs and ViewModel Local entry points | Does not contain Android View implementations or add host-injection logic. |
| `viewcompose-preview-core` | Preview annotations, deterministic configuration, and cross-process request/result protocols | Pure Kotlin/JVM with no Android, Compose, or IDE SDK dependency. |
| `viewcompose-preview-runner` | Native View static rendering, image export, and structured diagnostics in an isolated process | May use Android/Layoutlib; must not depend on Compose or the IDE SDK. |
| `viewcompose-preview` | Development previews and screenshot regression: Compose Preview bridge, `PreviewCatalog`, and Paparazzi | Development-only, excluded from app runtime entry points, and must not depend on `:app`. |
| `viewcompose-benchmark` | Macrobenchmark entry points and performance-regression data collection | Contains neither business demos nor framework semantics. |
| `app` | Demos, manual verification, and UI-test entry points | Contains no framework-core implementation. |

#### 2.1.1 Hard dependency direction

Runtime dependencies follow the five-layer order below. A layer may consume the same or a lower
layer when the dependency contract permits it; lower layers never depend on a higher layer.

1. **Kernel** contains pure state, text, UI contracts, and policy kernels: runtime, text-core,
   ui-contract, navigation-core, animation-core, gesture-core, and graphics-core.
2. **UI Foundation** contains the renderer-independent public UI surface: ui-foundation,
   animation, gesture, and graphics. It may model Android-only declarative values because this
   framework targets Android View, but native container access, host adaptation, logging, tracing,
   and scheduling are installed by Android Engine. It cannot depend on Android Engine, Design
   System, or Integrations.
3. **Android Engine** contains renderer-android and host-android. It maps contracts to Android View
   without owning Material design policy or AndroidX feature integrations.
4. **Design System** contains material3 and oneui7. A design-system module supplies concrete token
   profiles, resolved recipes, and owned composites without leaking its identity into UI Foundation
   or Android Engine. Only material3 interprets Material/AppCompat themes; oneui7 uses static,
   ViewCompose-owned values and has no Material dependency.
5. **Integrations** contains navigation-android, lifecycle-androidx, viewmodel-androidx,
   constraintlayout-androidx, overlay-android, overlay-material3-android, image adapters, and shadow-android. A name
   suffix identifies the external platform or design-system ownership when that distinction affects
   dependencies.
6. `viewcompose-android` and `viewcompose-material3-android` are application aggregates, not a
   sixth architectural layer. The former is neutral; the latter is the one-dependency Material
   application path and may depend on the neutral aggregate plus the Material adapter.
7. Preview, preview worker/runner/Gradle plugin, and benchmark are tooling. Runtime modules must not
   depend on tooling, and no framework module may depend on `app`.
8. Every new runtime module must be classified into one of the five layers or as an aggregate in the
   same change. `verifyModuleDependencyBoundaries` rejects unclassified modules and upward edges.
9. `qaQuick` always runs `verifyModuleDependencyBoundaries`, `verifyDesignSystemIsolation`,
   `verifyUiFoundationPlatformBoundary`, and the package/namespace ownership gates. Together they
   reject unclassified/upward dependencies, Material in UI Foundation or Android Engine, AndroidX
   or Android execution imports in UI Foundation, legacy package roots, split package ownership,
   and namespace drift. A compilable demo, an already-present dependency, or review approval is
   not a reason to bypass these gates.
10. Architectural direction and consumer exposure are separate decisions. An allowed lower-level
   dependency is published as `api` only when its types form part of the public/protected surface or
   the artifact intentionally aggregates that capability; otherwise it remains `implementation`.
11. `viewcompose-android` is the neutral Android application entry point and
   `viewcompose-material3-android` is the standard Material application entry point. Lower-level
   artifacts are documented for advanced consumers; a minimal app does not list runtime, UI
   contract, UI Foundation, renderer, host, Lifecycle, or ViewModel separately.
12. The exact published edges live in
   [`gradle/viewcompose-dependency-contracts.properties`](https://github.com/ViewCompose/ViewCompose/blob/main/gradle/viewcompose-dependency-contracts.properties)
   and are enforced against Gradle declarations and generated Maven metadata.

### 2.2 Architectural assessment

The project is a maintainable View-based declarative UI v1:

1. The main-tree update model uses dirty node-group recomposition through SlotTable Lite plus root-tree reference reuse.
2. Reusable containers such as lists and pagers use independent session refresh paths.
3. Overlay declarations and platform implementations are separated.
4. Node semantics are exclusively `NodeSpec`; the former `Props` path no longer exists.
5. Lifecycle and ViewModel collaboration APIs live in dedicated AndroidX integrations while the aggregate owns their automatic host injection.
6. Animation and gesture use kernel, DSL, and Android interop layers.
7. Graphics uses core, DSL, renderer pipeline, and host interop layers.
8. ConstraintLayout separates Q3 authoring from renderer-neutral transport and AndroidX mapping.
   Its immutable graph preflight owns IDs, references, logical/physical anchor planes, typed
   dimensions/ratios, Chain/Grid/CircularFlow placement, and helper validity. One Android registry
   owns native helpers plus typed Grid's bounded row/column proxies; CircularFlow expands to
   ordinary circle constraints without a helper View. Native publication follows the rollback
   boundary in [ADR-0016](./decisions/0016-constraintlayout-graph-and-helper-ownership.md) and the
   typed expansion decision in
   [ADR-0017](./decisions/0017-typed-constraint-helper-expansion.md).
9. Theme tokens are in a consumption-closure phase: every new token must be consumed by defaults/composite defaults or explicitly registered as a reserved semantic palette entry.
10. Text input has one source of truth, `TextFieldState`. The pure-Kotlin editor owns value, selection, composition, and history; renderer's `ViewComposeEditText` only adapts Android `Editable` and `InputConnection`.
11. System navigation keeps its pure-Kotlin transaction kernel separate from Android page sessions and back dispatch.

### 2.3 `app` directory baseline

The app separates entry points from demonstrations:

1. `app/src/main/java/com/viewcompose/activity/entry`: root activities such as `MainActivity` and render-host entry points.
2. `app/src/main/java/com/viewcompose/activity/demo/pages/<domain>`: activity routes grouped by `core`, `interaction`, `advanced`, and `quality`.
3. `app/src/main/java/com/viewcompose/activity/demo/sandbox`: non-core animation, gesture, and graphics experiments.
4. `app/src/main/java/com/viewcompose/demo/core`: shared catalog, theme session, test tags, and section helpers.
5. `app/src/main/java/com/viewcompose/demo/pages/<feature>`: feature demos such as foundations, layouts, input, and feedback.
6. `app/src/androidTest/java/com/viewcompose`: demo and UI regression tests.

### 2.4 `viewcompose-renderer-android` directory baseline

Renderer code is grouped by responsibility instead of flattened into one package:

1. `NodeType`, `VNode`, `NodeSpec`, and their subtypes exist only in ui-contract. Renderer must not create a mirror `com.viewcompose.renderer.node` contract.
2. `view/.../view/container/{core,layout,collection,navigation,input}` maps Android View containers by family.
3. `view/.../view/tree/binder/core` owns the bind pipeline, factory, differ, plan, registry, and modifier application. `NodeBinderDescriptors` is the single source for bind/patch/diff metadata, descriptor files live under `core/descriptor/`, and `ViewModifierApplier` remains a facade whose details are split under `core/modifier`. Container reuse, motion, and focus-follow policy comes from widget DSL through `NodeSpec`, not modifier extraction.
4. `view/.../view/tree/binder/widget` contains binders grouped by widget family. Text fields synchronize full editing snapshots through `ViewComposeEditText` and `AndroidTextFieldController`; ordinary recomposition must not unconditionally call `setText()` or move the cursor to the end.
5. `view/.../view/lazy/{adapter,focus,layout,reuse,session,state}` separates lazy-container capabilities. `LazyListState` receives immutable layout snapshots from RecyclerView scroll/layout/adapter observers and must not reset the anchor when rebound to the same RecyclerView. Item key, content type, span, and sticky kind belong to ui-contract and map to stable IDs, view types, `SpanSizeLookup`, and pinned-header decoration on Android.

## 3. Core execution path

```mermaid
flowchart TD
    A["Business DSL"] --> B["neutral setUiContent or named setMaterial3UiContent"]
    B --> C["host-android: renderInto(container)"]
    C --> D["RenderSession"]
    D --> E["ComposerLite.composeRoot / runGroup"]
    E --> F["buildVNodeTree (group cached reuse)"]
    F --> G["ChildReconciler"]
    G --> H["ViewTreeRenderer"]
    H --> I["Android View Tree"]
    D --> J["SlotTable / RecomposeScope / InvalidationQueue"]
    D --> K["OverlayHost.commit(...)"]
```

## 4. Hard boundaries

### 4.1 Platform implementation

1. Generic Android Dialog, PopupWindow, Toast, anchor observation, and nested overlay containers
   live only in `viewcompose-overlay-android`. Material Snackbar and modal-sheet presenters live
   only in `viewcompose-overlay-material3-android`; One UI Snackbar and bottom-dialog presenters
   live only in `viewcompose-overlay-oneui7-android`.
2. `viewcompose-ui-foundation` retains renderer-independent declaration contracts and runtime
   composition capabilities behind opaque host-installed platform handles.
3. Demo-only logic must not flow back into framework modules.

### 4.1.1 Image loading pipeline

1. `viewcompose-ui-contract` owns the portable `ImageSource`, `UiImageRequest`, `UiImageLoader`,
   platform-target, and disposable-handle contracts. It does not depend on Android or a decoder.
2. `viewcompose-ui-foundation` owns the `Image`/`Icon` declaration surface and the scoped
   `ProvideImageLoader` injection point. A missing loader is valid: resource sources still render.
3. `viewcompose-renderer-android` owns the Android `ImageView` binding lifecycle. It replaces a previous
   handle before starting changed work, clears it before direct fallback/resource binding, and
   disposes it on removal, rollback, and session disposal.
4. `viewcompose-image-coil` and `viewcompose-image-glide` implement the contract beside the renderer.
   They own decoder-specific mapping, use application-owned decoder configuration, and never own
   the mounted View or shut down a caller-owned loader.
5. `ImageSource.Model` uses an explicit stable key. Adapter-specific payloads are not serialized,
   logged, or compared as raw values by the framework.
6. Null-source fallback is renderer state rather than request state. Request extensions are
   immutable, compare by concrete type plus stable key, and are ignored by adapters that do not own
   their type.

### 4.2 `Modifier`, `NodeSpec`, and theme

1. `Modifier` carries general decorations and scoped parent data.
2. Component semantics use component DSL parameters and `NodeSpec`.
3. Theme defaults flow from `Theme` to `Defaults`; theme is not a general-purpose modifier.
4. `Material3ThemeBridge` in `viewcompose-material3` has a snapshot-reader layer and a token-mapper layer. The reader only reads Android/AppCompat/Material fields; the mapper performs semantic mapping and fallbacks.
5. Best-effort `surfaceTint` and uniform `shapeAppearance*Component` mapping is allowed. The bridge must not guess non-uniform corner shapes or three control-size tiers merely to increase coverage.
6. `controls` remain framework-owned defaults unless Android exposes a stable, uniform source. Scattered widget styles must not become global token truth.
7. ui-contract modifier files contain only globally stable semantics. Policies that apply to one container belong in its DSL parameters and `NodeSpec`.
8. Do not reintroduce `Props`, `TypedPropKeys`, `PropKeys`, or `node.props`.
9. Constraint parent data (`layoutId`, `constrainAs`, and `constrain`) is valid only for ConstraintLayout children; an invalid host must produce a validator warning.
10. Composite components must transfer complete text styling through `NodeSpec`, including font size, weight, family, letter spacing, line height, and font-padding inclusion.
11. Foundation tokens, component recipes, and resolved rendering contracts are distinct values.
    Foundation tokens remain reusable immutable semantics; a design-system module owns its typed
    recipes and resolves them through shared Basic primitives or its own composites before
    emitting a design-system-neutral `NodeSpec`.
12. `BasicSurface` is the shared resolved decoration and interaction boundary. It may transport
    fill/brush, shape, border, clip, state layer, visual bounds, effective target bounds, shadows,
    and effects, but it does not select a Material, One UI, Cupertino, or product variant.
13. Structurally different navigation, text-field decoration, and custom-control arrangements stay
    in the owning design-system module. Renderer branches on a design-system identity and one
    universal component-recipe bundle are forbidden.

See [Modifier architecture](modifier.md), [NodeSpec architecture](node-spec.md), and [theming](../guides/theming.md).
The complete design-system ownership and onboarding rules are in the
[multi-design-system architecture standard](design-systems.md).

### 4.3 Host integration

1. Neutral Activity and Fragment `setUiContent(...)` entry points live in `viewcompose-android`;
   named `setMaterial3UiContent(...)` entry points live in `viewcompose-material3-android`. Neither
   exposes internal `RenderSession`, and both dispose it automatically using the Fragment view
   lifecycle where applicable.
2. Neutral Activity/Fragment and nested navigation roots explicitly construct
   `viewcompose-overlay-android`; Material roots explicitly construct the Material adapter. Runtime
   classpath order never selects a design system.
3. `AndroidOverlayHostDefaults.androidOrNoOp(...)` and `ServiceLoader` remain only for custom
   low-level hosts. Exactly one neutral provider is permitted; zero providers returns no-op and
   duplicates fail deterministically. The Material adapter registers no provider.
4. Public host callbacks expose only UI Foundation diagnostic types; renderer diagnostic types remain internal adapters.
5. System-bar insets use `Modifier.systemBarsInsetsPadding(...)`, not a global Activity option.
6. host-android atomically installs the render engine, frame scheduler, composition coroutine context, focus adapter, and logging/tracing adapter through `installRenderSessionPlatform(...)`. UI Foundation coordinates composition against opaque `RenderContainerHandle` values; only Android Engine unwraps them as `ViewGroup`. A session captures one platform snapshot, and missing or duplicate installation fails immediately rather than degrading piecemeal.
7. Android design-system installation has two distinct boundaries: a named adapter may resolve a
   themed `Context` and capabilities before View creation, then the composition root provides one
   immutable token/recipe/motion/capability snapshot. Token provision alone cannot undo attributes
   consumed by a View constructor.
8. `viewcompose-host-android` and `viewcompose-android` never select Material or expose Material
   policy. Material XML/dynamic-color convenience belongs exclusively to the named
   `viewcompose-material3-android` adapter.
9. A public general host adapter SPI is deferred until a second context-changing design system
   proves the same lifecycle contract. Root/session replacement remains the atomic design-system
   switching boundary.

### 4.4 Lazy session containers

Every container with lazy creation and holder/session reuse is a first-class architectural object. It must provide:

1. a visible-content refresh path when structure is stable;
2. refresh behavior for an empty diff;
3. recycle/dispose behavior aligned with lifecycle; and
4. framework-managed RecyclerView defaults of a local pool and system animator, with per-container
   `reusePolicy` and `motionPolicy`; and
5. native focused-descendant rectangle propagation for real scroll owners, while pagers own only
   discrete selection and require a page-local scroll owner for within-page IME reveal.

Use the [session-container checklist](session-containers.md).

### 4.5 Environment and Locals

1. Standard Android host entry points install `AndroidResourceEnvironment` from the same stable Context that creates the root and overlays. It maps density, font scale, locales, and direction, exposes common resource lookups, and advances `resourceRevision` after configuration callbacks or an explicit host refresh; business code may still override platform-neutral values in a local subtree.
2. Renderer consumes resolved `NodeSpec` and platform values; it does not depend on UI Foundation Environment or Local implementations.
3. Renderer dp/sp conversion goes through its shared `DimensionUtils.kt`; containers must not duplicate density helpers.
4. `com.viewcompose.host.android.environment.AndroidEnvironmentBridge` remains the Android-to-contract mapper, while `com.viewcompose.host.android.resources` owns mounted observation and resolution. UI Foundation accepts only resolved `UiEnvironmentValues` and never imports Android resource types.
5. Custom tokens and built-in Locals use `uiLocalOf`, `UiLocals.current`, `ProvideLocal`, and `ProvideLocals`; do not add a new dedicated `ProvideXxx` pattern.
6. Local snapshot/restore behavior must propagate consistently through lazy containers, pagers, overlays, and navigation destinations, including resource revisions. `LocalContext` installs immutable snapshots by identity: provider boundaries allocate them, while repeated group/node capture in one scope returns the installed instance.
7. Lifecycle and ViewModel Locals use the public packages `com.viewcompose.lifecycle` and `com.viewcompose.viewmodel`, while the `viewcompose-android` composition root performs default injection.

### 4.6 SlotTable Lite recomposition

1. `ComposerLite` is the only composition kernel. `RenderSession` schedules initial composition and incremental recomposition without a session-level whole-tree read observer. Invalidations are aligned to `Choreographer` frames.
2. `UiTreeBuilder.emit(...)` establishes group boundaries. A clean group reuses its prior `VNode` reference; only a dirty group rebuilds.
3. State-read invalidation and changed `emit` inputs both enter the deduplicating `InvalidationQueue`.
4. Structural drift in a sibling group key/order falls back to the nearest stable ancestor subtree and reports one warning; silent corruption is forbidden.
5. `LocalContext` snapshots and restores per group.
6. Composition APIs such as `remember`, `key`, effects, and `rememberCoroutineScope` require an active `ComposerLite`; no fallback slot/effect store or silent out-of-composition behavior is allowed.

### 4.7 Text editing

1. text-core is the sole platform-independent source of truth for text, directional selection, IME composition, editing transactions, and undo history.
2. `TextField` and `SearchBar` accept stable `TextFieldState`; input purpose and line behavior use
   `TextFieldInputProfile` and `TextFieldLinePolicy`, not parallel component wrappers or a
   `String + onValueChange` path.
3. Android renderer preserves native IME, accessibility, hardware keyboard, and selection behavior through AppCompatEditText instead of implementing its own text layout or full `InputConnection`.
4. Native input is merged at InputConnection/batch-edit boundaries. State-to-View updates use minimal `Editable.replace()` calls and restore selection/composition.
5. `InputTransformation` applies only to user input; programmatic `TextFieldState.edit` bypasses it.
6. Save/restore persists text and selection, not IME composition or undo/redo history.
7. Rich-text spans, inline attachments, and unified receive-content require a separate document model; Android `Spannable` must not enter core contracts.

### 4.8 State snapshots and composition transactions

1. `MutableState` writes go through snapshot transactions, and `SnapshotMutationPolicy` defines equality and conflict behavior.
2. Concurrent mutable-snapshot apply first checks equality, then attempts merge, and otherwise returns failure.
3. Each composition round reads a consistent snapshot. Derived-state invalidation observes snapshot versions rather than one global dirty bit.
4. `rememberUpdatedState` guarantees visibility after recomposition, not immediate visibility to an effect during the same composition phase.
5. `prepareRoot()` creates a candidate composition. Slots, observations, `RememberObserver`, and effect lifecycles commit only after renderer success and abort together on failure.
6. `DisposableEffect`, `SideEffect`, and `onRemembered` execute only during commit; abandoned candidate values receive `onAbandoned`.
7. `RenderSession` owns the sole composition coroutine tree. Its `SupervisorJob` isolates children, and disposal cancels every descendant.
8. `LaunchedEffect`, `produceState`, state collection, and animation remain children of that tree. Additional contexts passed to composition APIs must not contain a `Job`.
9. Writing snapshot-backed mirror state and immediately reading it during the same composition may return the old snapshot; control flow must use the live kernel value.
10. Composition transactionality covers slots, observations, effects, and VNode publication; it does not promise atomic rollback with arbitrary global snapshot writes or Android View patches.
11. A touched-scope journal copies rollback state only for executed or changed scopes. Duplicate invalidations for one scope in a frame coalesce, while invalidations raised during composition still advance the version for a follow-up pass.
12. An equivalent regenerated VNode must reuse its old reference so renderer can use O(1) `SkipSubtree`.
13. There are no compiler-generated restart groups. Components spanning sibling VNodes may use node-free `RecomposeBoundary`, with captured values declared explicitly as inputs.

### 4.9 Render scheduling and transactions

1. Explicit `RenderSession.render()` and the first frame execute immediately; state invalidations use `FrameAlignedRenderDispatcher` and coalesce to one commit per frame.
2. Disposal cancels pending frame callbacks. Lazy item and overlay surface sessions keep immediate rendering to avoid blank first display.
3. Recursive patching shares one apply transaction. Removed resources are released only after the whole tree succeeds.
4. Failure restores the old VNode, mounted children, layout parameters, and View order as far as possible and releases newly created nodes.
5. `AndroidView.update`, `onReset`, and native-View configuration must be replayable. Irreversible external actions belong in `onCommit` after transaction success.
6. The mutation journal records only actually changed mounted nodes and ViewGroups; stable subtrees are not snapshotted.
7. `AnimatedSizeNodeWrapper` preserves unchanged VNode/list references and converts once per frame; no-animation paths must not recursively copy trees.
8. `NodeBindingDiffer` runs before modifier/layout-parameter resolution, and `SkipSubtree` performs no resolution or repeated preflight.
9. Structural-depth and per-node-type binding statistics are collected only when debug or diagnostics callbacks enable them.
10. Recoverable failures are reported through `RenderFailure(phase, recovery, frameId, operation, nodeKey)`; logs are not an observability API.

### 4.10 Renderer binding complexity

1. Binder registry and differ mappings derive from `NodeBinderDescriptors`; adding a node or patch changes the descriptor source, not parallel maps.
2. Descriptor sources live under `view/tree/binder/core/descriptor/`; do not flatten new `NodeBinder*.kt` files into `core/`.
3. `ViewModifierApplier` orchestrates only. Styling, interaction, insets, and container policies live in focused objects under `core/modifier`.
4. A shortcut around descriptors is an architecture violation and must be corrected in the same iteration.

### 4.11 Module package roots

1. Each module has one responsibility-aligned package-root prefix and may organize subpackages beneath it.
2. The rule covers `src/main`, `src/test`, and `src/androidTest`; tests are not exempt.
3. Android module namespace matches its package root, except the Kotlin/JVM ui-contract module.
4. Lifecycle and ViewModel Local APIs remain in their dedicated public packages and AndroidX integration modules, not UI Foundation.

### 4.12 Development previews

1. Platform-independent annotations, deterministic configuration, and process protocols live in preview-core without Android, Compose, or IDE SDK dependencies.
2. Native static mounting, measurement, layout, drawing, and diagnostic export live in preview-runner without Compose or IDE SDK dependencies.
3. Compose Preview adapters, `PreviewCatalog`, and Paparazzi assets live in preview and do not flow into app or core runtime modules.
4. Android Studio Preview and Paparazzi share one `PreviewCatalog`; duplicate examples are forbidden.
5. Preview worker and IDE plugin communicate through a versioned structured protocol, and business render code never runs in the IDE process.
6. Preview may simulate static overlay content, while instrumentation covers real window behavior.
7. A new component or important composite adds its `PreviewSpec` and Paparazzi baseline in the same change.

### 4.13 Animation and gesture

1. Animation uses animation-core plus animation; gesture uses gesture-core plus gesture.
2. `graphicsLayer` is the main animation carrier and wins when its alpha, offset, elevation, or z-index field conflicts with the same standalone semantic.
3. Android-specific high-level animation enters only through host-android interop.
4. Gesture arbitration consumes in this order: `pointerInput`, transform/drag/swipe, then `combinedClickable`. A consumed pointer-input result short-circuits the rest.
5. Renderer preserves direction lock, slop, and priority. List/pager motion remains opt-in and compatible with reuse policy.
6. `AnimatedVisibility` defaults to fade-in plus expand-in and shrink-out plus fade-out, participates in parent size animation through `AnimatedVisibilityHost`, and removes its subtree only after all exit animations finish.
7. Transform activates only after pan, zoom, or rotation motion crosses touch slop. Once active it emits one combined delta per frame and only then disallows parent interception.
8. Anchored settling uses velocity first, distance second, and nearest anchor last. The distance threshold is `max(touchSlop * 2, segmentSpan * 0.35)`.
9. `animateContentSize` uses an `AnimatedSizeHost` with real measured-size interpolation and parent relayout, not graphics-layer scaling. Its child follows the current host size in both expansion and collapse.
10. `Animatable` normally obtains the frame clock from `rememberAnimatable(...)`; non-composition callers may bind one explicitly.
11. Gesture policy belongs in gesture-core; renderer must not add parallel axis-lock, slop, or settling branches.
12. `combinedClickable` participates only when enabled and at least one click, double-click, or long-click callback exists.
13. `MotionScheme` selects semantic timing and reduced-motion substitution without owning a clock
    or loop. Composition-owned motion continues through `Animatable`, target-as-state APIs, or
    `Transition`; component recipes never launch animation work.
14. Shape transition interpolates only compatible corner family/size representations. Incompatible
    geometry uses a reported discrete/static fallback; arbitrary Path Morph is not a generic
    animation contract.

### 4.14 Graphics

1. Graphics is layered as graphics-core, business DSL, Android renderer execution, and host Android interop.
2. `verifyGraphicsCorePurity` prevents Android imports in graphics-core.
3. `drawBehind` runs before content; `drawWithContent` controls content placement explicitly; multiple draw modifiers execute in stable chain order.
4. `drawWithCache` rebuilds cached commands only when dependencies change.
5. Android-only `RenderEffect`, `RuntimeShader`, and Drawable/Canvas bridges enter through `AndroidGraphicsInterop`.
6. Rounded rectangles use a fast path for uniform corners and `Path.addRoundRect` for non-uniform corners.
7. Drawable image drawing applies alpha, blend, color filter, and image filter, then restores original bounds.
8. `ImageFilterModel.Chain` must execute. The current blur chain combines radii recursively by Gaussian variance before applying the platform filter.

### 4.15 Advanced shadow decoration

1. ui-contract owns the platform-independent immutable `UiShadow` and ordered shadow modifiers.
2. Renderer owns only the minimal `AndroidViewDecorationBackend` protocol, generic hosts, active-decoration index, and independent z-index ordering. Renderer and host do not depend on a concrete shadow module.
3. shadow-android owns Android rasterization, caching, backend choice, and diagnostics, installed through `META-INF/services` or `ShadowDecorationLayer.install()`.
4. Without a backend, shadow modifiers degrade to a stable no-op while core rendering, lazy containers, pagers, tabs, previews, and hosts still compile and run.
5. Necessary roots remain ordinary `FrameLayout`. `renderInto` adds a generic host only when a top-level node actually needs decoration/non-zero z-index and the current container lacks the protocol; nested decorations draw in the nearest framework layout.
6. With no active decorated child, `drawChild` performs one parent-level fast check and delegates directly to native drawing. With decoration, each child is looked up at most once for both drawing planes. Custom child order is disabled when every z-index is zero.
7. Containers draw outer shadows before child content and inner shadows after complete child content and foreground, without extra business Views.
8. Advanced shadows do not affect measurement, layout, hit testing, focus, or accessibility. z-index, Material elevation, and exact shadow remain distinct semantics.
9. Multiple layers preserve declaration order. Outer shadows may exceed child bounds but obey the nearest viewport/explicit clip chain; inner shadows are clipped to the shape.
10. Static raster cache keys include size, density, layout direction, shape, and complete specifications. Translation, scale, rotation, or alpha alone does not rebuild raster content.
11. `ShadowRenderPolicy.Auto` currently selects `ExactBitmap`; the API 29+ `RenderNodeDisplayList` backend remains explicit and experimental until release-device data proves a stable benefit.
12. Lazy recycling, node removal, transaction rollback, and session disposal remove shadow specifications. Parent indexes must not strongly retain Views globally, and process caches contain only immutable rasters.

See the [advanced shadows guide](../guides/shadows.md).

### 4.16 Semantics and accessibility

1. Accessibility declarations use `Modifier.semantics { ... }` and `SemanticsConfiguration`; `contentDescription` is only a convenience entry point.
2. The platform-independent contract covers description, state, role, heading, live region, selected/checked/enabled, error, progress, pane title, click label, descendant merging, and hidden subtrees.
3. Renderer maps semantics through native View properties and `AccessibilityNodeInfoCompat`; it does not maintain a separate accessibility tree.
4. Removing semantics during patch or reuse restores the View's prior content, state, delegate, heading, live-region, and importance values.
5. Native semantics of TextField, list, slider, and similar controls remain unless an explicit structured semantic overrides a field.

### 4.17 System navigation

1. Routes, back stack, navigation transactions, and page-lifecycle planning live in pure-Kotlin navigation-core.
2. AndroidX owners, system back dispatch, and page View containers belong only in the Android navigation integration.
3. Each destination owns a page `RenderSession`; the back stack must not be modeled as ordinary conditional branches in one root session.
4. Navigation uses prepare/commit/rollback. A candidate's first render must succeed before publishing the new stack or pausing the current page.
5. A hidden page retained on the stack stays `CREATED` and keeps state ownership. Multiple interactive adaptive panes may be `RESUMED`. A permanently removed page reaches `DESTROYED` only after its exit transition and then releases resources.
6. Activity/Window is the root platform host, not a destination, and existing Activity/Fragment entry points remain unchanged before navigation stabilizes.
7. Candidate destinations render synchronously in an unattached container and stage hidden. Rollback releases both page session and entry owner.
8. Reusing a committed destination session refreshes its latest `UiLocalSnapshot` and content closure explicitly.
9. Pop refreshes the page being revealed before publishing the stack. Refresh failure leaves the prior stack, visible page, and lifecycle intact.
10. Reentrant commands enter one main-thread serial queue. Commands created while a candidate later fails are discarded with that candidate.
11. An unrecoverable post-commit effect failure places the coordinator in `Failed` and rejects later commands.
12. Adaptive panes alter only the visible set and native View layout for one committed stack. They do not create parallel navigation state, rebuild visible entry owners, or refer to entries outside the active stack.

See the [navigation guide](../guides/navigation.md).

## 5. Current hotspots and risks

1. `ViewTreeRenderer` remains a complexity hotspot; add focused helpers instead of expanding its main class.
2. The current model combines node-group recomposition with root-level traversal scheduling. Future work should improve group-key diagnostics and fine-grained skip hit rates.
3. Preserve the five-layer direction: Kernel -> UI Foundation -> Android Engine -> Design System /
   Integrations, with neutral and named application aggregates only above those layers.
4. Lazy session regression covers grid and both pager orientations. Lazy P1 includes structured item DSL, observable layout state, sticky headers, content type/span, prefetch, and boundary behavior.
5. Neutral Activity/Fragment bridges live in `viewcompose-android`; low-level mounting remains in
   host-android, while Material Context resolution and token installation are joined only by the
   named `viewcompose-material3-android` bridge.
6. The implicit Material Host gap is closed. Remaining design-system work must converge component
   recipe ownership and provenance across root, overlay, lazy, and navigation sessions without
   reopening the neutral dependency boundary.
7. Component backend ownership is intentionally mixed: preserve native behavioral cores, use
   design-system-owned DSL composites for named structure, and add neutral custom Views only for a
   reusable resolved execution semantic. Do not normalize the architecture by mapping every
   component to either native widgets or custom Views.

## 6. Required change checklist

Every architecture-related change must include:

1. module and directory ownership review;
2. updates to this document and affected specifications;
3. unit or instrumentation regression coverage appropriate to the capability; and
4. a demo verification path.

See the [project workflow](../project/workflow.md).

## 7. Related documents

1. [Unified capability roadmap](../project/roadmap.md)
2. [Performance](../tooling/performance.md)
3. [State snapshots](state-snapshots.md)
4. [Documentation home](../README.md)
5. [System navigation](../guides/navigation.md)
6. [Multi-design-system architecture and integration standard](design-systems.md)
