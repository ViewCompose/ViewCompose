# ViewCompose Unified Roadmap

## 1. Scope

This is the single current roadmap. It consolidates the still-relevant parts of these historical
documents:

1. `WIDGET_ROADMAP.md`
2. `DEMO_ROADMAP.md`
3. `OVERLAY_COMPONENTS_ROADMAP.md`
4. `UI_TESTING.md`

Its goals are one roadmap entry point, no status drift between parallel roadmaps, and AI context
focused on current plans rather than historical phase documents.

Performance retains a dedicated specification in [Performance](../tooling/performance.md).

## 2. Current baseline (2026-08)

### 2.1 Framework

1. Node semantics have converged on NodeSpec-only: `VNode.spec` is non-null and the parallel
   `Props` path is gone.
2. Modifier ownership is general decoration plus scoped parent data.
3. Overlay is split into session-bound surfaces (`Dialog`, `Popup`, `ModalBottomSheet`) and
   host-driven feedback (`Snackbar`, `Toast`).
4. `:viewcompose-android` owns neutral Activity/Fragment `setUiContent`, while
   `:viewcompose-material3-android` owns the named `setMaterial3UiContent` context adapter; the
   low-level `:viewcompose-host-android` engine owns `renderInto`, `RenderSession`, and mounted-tree
   lifetime without selecting a design system.
5. System-bar insets use component-side `Modifier.systemBarsInsetsPadding(...)`.
6. Lifecycle and ViewModel collaboration is split into `:viewcompose-lifecycle-androidx` and
   `:viewcompose-viewmodel-androidx` under `com.viewcompose.lifecycle` and `com.viewcompose.viewmodel`.
7. Recomposition uses SlotTable Lite node-group invalidation without a legacy full-rebuild switch.
8. Runtime ownership follows Kernel, UI Foundation, Android Engine, Design System, and Integrations;
   `viewcompose-android` and `viewcompose-material3-android` are reviewed application aggregates,
   not a sixth layer.
9. `viewcompose-runtime` is pure Kotlin/JVM with policy, snapshot, observation, invalidation, and
   composer branch coverage.
10. Public host diagnostics expose core-owned `RenderStats/RenderTreeResult`, not renderer types.
11. Default overlay assembly uses `AndroidOverlayHostFactoryProvider + ServiceLoader` and falls back to a
    stable no-op without reflection.
12. `viewcompose-preview` provides the Compose Preview bridge, `PreviewCatalog`, and Paparazzi
    snapshots through `qaPreview`.
13. Animation and gesture layers are
    `viewcompose-animation-core + viewcompose-animation + viewcompose-gesture-core +
    viewcompose-gesture`, including Compose-like APIs, a policy core, renderer event mapping,
    Lazy/Pager motion policy, and Android interop.
14. `viewcompose-constraintlayout-androidx` and renderer `DeclarativeConstraintLayout` now include
    the Alpha baseline plus typed chain endpoints, wrap contribution, physical anchors and
    Guidelines, typed Grid, declarative CircularFlow, classified reconciliation, dedicated marked
    Scope, axis-typed targets, immutable helper snapshots, and transactional graph/helper ownership.
    The [archived post-release expansion plan](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/constraintlayout-parity-performance-expansion.md) has
    completed Phases 0--4: exact JVM/device/lifecycle coverage, the reviewed pairwise visual matrix,
    minimum/latest API acceptance, and the revision-6 released/candidate/direct matrix are closed.
    Seven stable longitudinal pairs pass every timing and peak-heap regression row; five remain
    `inconclusive`, so release safety is **no material change** and no whole-frame optimization win
    is claimed. MotionLayout remains intentionally out of scope; any further performance work needs
    a new attributed plan rather than another unbounded rerun.
15. Graphics uses `viewcompose-graphics-core + viewcompose-graphics + renderer draw pipeline +
    host-android interop`, with Demo, Preview/Paparazzi, and v2 P0 fixes for four-corner RoundRect,
    Drawable DrawPaint, and ImageFilter Chain.
16. `ComposerLite.prepareRoot/commit/abort` provides composition transactions for slots,
    observation, RememberObserver, and Effect. Failed composition retains old dependencies and can
    recompose later.
17. `RenderSession` owns the structured parent Job for `LaunchedEffect/rememberCoroutineScope`;
    `produceState` is suspend-based with `awaitDispose`, and Flow/animation no longer create
    independent root Jobs.
18. Renderer apply is transactional: recursive patches share one transaction, removals are released
    late, and binding/insertion failure attempts to restore the old tree.
19. Compiler-free recomposition performance includes VNode identity retention, equivalent-result
    normalization, same-frame invalidation merging, explicit `RecomposeBoundary`, composition/View
    mutation journals, renderer fast skips, and opt-in diagnostics.
20. `viewcompose-text-core` supplies `TextDocument`, text/selection/composition, atomic
    EditingBuffer, input transformation, and undo/redo. A dedicated
    `AppCompatEditText/InputConnection` controller synchronizes Android, and
    `rememberTextFieldState` saves the document and selection.
21. Lazy P1 includes observable `LazyListState` layout snapshots, boundaries, and scrolling;
    structured item DSL supports stable keys, sticky headers, content types, Grid spans,
    asymmetric padding, reverse layout, user-scroll control, and prefetch policy.
22. Composition APIs use only `ComposerLite`. Alternative remember/effect/key contexts are removed,
    and composition-time APIs fail immediately outside composition.
23. Platform host capability installs atomically: renderer, frame scheduler, and composition
    coroutine context register as one immutable snapshot; partial empty-render/immediate-schedule
    fallbacks are removed.
24. `Modifier.semantics` covers description, state, role, heading, live region,
    selected/checked/enabled, error, progress, and subtree policy. The renderer maps native
    accessibility and restores original semantics when a View is reused.
25. `RenderFailure/RenderFrameReport` records stage, recovery, frame number, and AndroidView
    operations. Non-replayable work executes through post-transaction `AndroidView.onCommit`.
26. Overlay P2 includes a platform-neutral Popup positioner with four-way anchors, RTL,
    flip/clamp, and scroll following, plus unified Snackbar/Toast queue policy and structured end
    reasons.
27. Theming P2 lives in `viewcompose-material3` and includes Android dynamic-color policy,
    configuration-driven token lifecycle, source/revision metadata, and independent rounded/cut
    dimension/fraction corner bridging.
28. Diagnostics P2 exposes render tree, per-node patch timeline, CompositionLocal snapshots, and
    structured recomposition reasons through `RenderTreeResult` and the Demo inspector.
29. Lifecycle/SavedState P2 uses claim/commit/release restoration transactions, serial collector
    cancellation on rapid restart, corrupt Bundle-entry isolation, and explicit destroyed-host
    rejection.
30. Release performance uses R8 and resource shrink for release/benchmark targets, with
    no-ART-precompilation cold-start/state-patch baselines and `qaRelease`/`benchmarkRelease`.
31. Animation cancel/retarget follows last-mutation-wins. `animateTo/snapTo/stop` share arbitration,
    old frames cannot overwrite a new target, and `targetValue/isRunning` are public.
32. Drag, anchored drag, transform, and pointer input have structured cancellation reasons.
    Transform takeover cannot resume an old drag, and system cancellation cannot trigger
    fling/settle.
33. `DrawScene` supports immutable reuse, nested transforms/clips, isolated Canvas state, and rejects
    unbalanced save/restore.
34. Rich text and Receive Content share `TextDocument`: spans, paragraphs, links, inline attachments,
    clipboard, drag/drop, and IME content use one conversion, transformation, insertion, undo, and
    save/restore path.
35. `viewcompose-shadow-android` provides ordered outer layers, foreground inner layers, bounded
    raster caches, an experimental RenderNode backend, and structured diagnostics. `Auto` remains
    ExactBitmap based on the first release-mode benchmark.

### 2.2 Demo and verification

1. Demo uses a stable multi-Activity structure.
2. Implemented chapters use one scenario template.
3. Instrumentation covers critical smoke paths and delayed-session cases for
   `LazyVerticalGrid`, `HorizontalPager`, `VerticalPager`, and `ModalBottomSheet`.
4. Verification status on 2026-08-03: current main passes `qaQuick`. A complete `qaFull` was not rerun
   in one uniform device environment, so the 2026-03-08 local failure is no longer treated as
   current fact and the aggregate device gate is not claimed green. A milestone requiring UI
   evidence uses current targeted device results or stays `In Progress`.
5. Graphics Demo contains outer-shadow, inner-shadow, and Lazy/diagnostics pages for layers, color,
   offset, spread, shape, input interop, 1,000 stable-key items, cache hits, and actual backend
   selection.
6. The standalone `:samples:counter` avoids Demo internals. `qaQuick` compiles the app, test source,
   and debug Preview; `qaPreview` verifies compiled Preview discovery; `qaFull` verifies counter
   clicks on a device.

### 2.3 Milestone snapshot (2026-08-03)

| Milestone | Status | Completion fields (C/U/D/UI) | Notes |
| --- | --- | --- | --- |
| A: Overlay stability | Completed | C:✅ U:✅ D:✅ UI:✅ | Unified overlay reconciliation covers Dialog, Popup, ModalBottomSheet, and feedback flows |
| B: Collections and containers | Completed | C:✅ U:✅ D:✅ UI:✅ | Lazy/Pager baseline, structured items, complete list state, sticky headers, content types/spans, prefetch, and restoration are implemented |
| C: Input and forms | In Progress | C:✅ U:✅ D:✅ UI:⚠ | TextFieldState, selection/composition, IME batch, undo, transformations, keyboard actions, autofill, and restoration are implemented; real-device IME/accessibility matrix remains |
| D: Diagnostics and performance | In Progress | C:✅ U:✅ D:✅ UI:✅ | Diagnostics visualization and R8 release benchmark are implemented; remaining observability is owned by the active [diagnostics plan](./plans/diagnostics-correlation-inspection-observability.md), while baseline-profile benefit remains to be measured |
| E: Preview and screenshots | In Progress | C:✅ U:✅ D:✅ UI:✅ | Compose Preview/Paparazzi and Studio Preview plugin 1.0 cover source linkage, all previews, cache, incremental refresh, zoom/pan, and diagnostics; Dark/Tablet matrix remains |
| F: Animation and gestures | Completed | C:✅ U:✅ D:✅ UI:✅ | The first-round Core/DSL, Transition, visibility/size, Animatable, interop, Demo, Preview, and regression scope is complete; seven later animation expansions are owned by the active [Animation capability plan](./plans/animation-compose-capability-expansion.md) without reopening this baseline |
| G: Graphics 2D | In Progress | C:✅ U:✅ D:✅ UI:⚠ | Core/DSL layers, Canvas/draw modifiers/cache, renderer pipeline, Android interop, and v2 P0 fixes are implemented; stable current-device UI evidence remains |
| H: Advanced shadows | Completed | C:✅ U:✅ D:✅ UI:✅ | Outer/inner layers, shape/spread/offset, Lazy cache, backend diagnostics, and paired Compose benchmark are complete; Samsung SM-G991B targeted regression passed and Auto remains ExactBitmap |

## 3. Unified design principles

1. Component parameters own semantics, Modifier owns general decoration, and Theme/Defaults owns
   defaults.
2. Platform implementations do not flow back into DSL modules; Android hosting belongs in
   `viewcompose-overlay-material3-android` or a bridge layer.
3. Add capability in minimum verifiable steps with documentation, implementation, tests, and Demo.
4. Roadmap and implementation change together; code cannot advance while the roadmap remains stale.

## 4. Capability matrix

| Area | Current state | Next focus |
| --- | --- | --- |
| Foundations / Input / Layout / State | v1 core plus declarative focus, directional navigation, focus groups, and hardware KeyEvent dispatch | Real-device keyboard/focus edge cases and complex compositions |
| Accessibility / Semantics | Structured semantics and native Android mapping for state, role, heading, live region, errors, progress, and more | TalkBack, Switch Access, and font-scale device matrix |
| Text Editing | TextDocument, TextFieldState, EditingBuffer, InputTransformation, and Android editor bridge support rich text, selection/composition/undo/save, attachments, and Receive Content | Chinese/Japanese IMEs, TalkBack, hardware keyboard, drag/drop, and third-party content providers |
| Runtime Effects / Transactions | Composition transaction, structured coroutines, renderer recovery, failure reports, and onCommit boundary | Production failure aggregation and exception sampling have moved to the active [diagnostics plan](./plans/diagnostics-correlation-inspection-observability.md) |
| Runtime Recomposition Performance | VNode subtree cache, mutation journals, invalidation merging, explicit boundaries, and O(1) identity skip | Maintain leaf-update scale benchmarks and bound whole-tree fixed cost |
| Lifecycle / ViewModel | Split modules, serial lifecycle collection, transactional SavedState claim, destroyed-host and corrupt-entry handling | Multi-window and background process-recovery matrix |
| Collections | LazyColumn/Row/Grid plus Pager, complete list state, sticky headers, content types/spans, and prefetch | Paging 3 execution has moved to the active [Paging 3 integration plan](./plans/paging3-integration.md); it remains an optional AndroidX integration outside the core contract |
| Overlay | Precise Popup anchoring/following/RTL/flip/clamp and unified feedback queues | Multi-window, IME, and freeform-window device matrix |
| Theming | Semantic tokens, dynamic-color policy, complete shape bridge, configuration lifecycle, and authoritative Theme diagnostics | Multi-window, vendor-theme, and dynamic-color matrix |
| Interop | AndroidView replay-safe update/reset/nativeView, commit-time onCommit, and one-time release | Complex native and third-party Views with theme coordination |
| Diagnostics | Aggregate render/layout, tree, per-node patches, Locals, and recomposition reasons | Node highlighting, cross-session correlation, and per-node timing have moved to the active [diagnostics plan](./plans/diagnostics-correlation-inspection-observability.md) |
| UI Testing | Core instrumentation plus P1 focus/keyboard, nested-scroll, and rollback cases | Multi-API, TV, ChromeOS, overlay host, and theme assertions |
| Developer Preview | Compose Preview, Paparazzi, and Studio plugin with static render, source linkage, diagnostics, bounded cache, and incremental refresh | More domains and Dark/Tablet snapshots |
| ConstraintLayout | Alpha DSL plus classified reconciliation, typed chain/wrap/physical-direction APIs, typed Grid, declarative CircularFlow, exact helper/rollback/lifecycle coverage, pairwise visual acceptance, API 24/33/36 device coverage, and a stable-row-safe released/candidate/direct matrix | The [archived parity/performance expansion](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/constraintlayout-parity-performance-expansion.md) completed Phases 0--4 with **no material change** release safety and no whole-frame optimization win. Keep MotionScene/MotionLayout out of scope; require a new attributed plan for multi-OEM performance or any additional parity work |
| Animation | Core/DSL layers, shared Transition timeline, last-mutation-wins Animatable, AnimatedVisibility, Crossfade, animateContentSize, and raw Android interop | Physical motion/results, full content and visibility transforms, seeking, bounds, shared motion, and timeline tooling have moved to the active [Animation capability plan](./plans/animation-compose-capability-expansion.md) |
| Gesture | Policy core, DSL, dispatcher, nested scroll, structured cancellation, and tap/drag/anchored/transform support | Third-party native scrollers and real-device multi-touch |
| Graphics | 2D drawing and optional shadow decoration with DrawScene, ordered outer/inner layers, bounded cache, and backend diagnostics | Dark/Tablet snapshots and budgeted dynamic RenderEffect research |
| Performance | R8 Macrobenchmark, DiffUtil/payload/SlotTable/subtree skip, paired Compose list/complex-layout controls, memory metrics, reports, and normalized gates | Accumulate same-device paired baselines and quantify baseline profile |

### 4.1 Completion fields (C/U/D/UI)

1. `C` (Compile): compile gate.
2. `U` (Unit): unit-test gate.
3. `D` (Demo): Demo scenario and verification instructions.
4. `UI` (Instrumentation): device UI gate.

Values are `✅` passed, `⚠` partial or blocked, and `❌` not passed.

Defaults:

1. C: compilation tasks pass in `qaQuick`.
2. U: unit tests pass in `qaQuick`.
3. D: the capability has a Demo page and verification points.
4. UI: instrumentation passes in `qaFull`, or the roadmap records a scoped exemption and deadline.

### 4.2 Deferred design-system enhancement candidates

These candidates are not active work, current defect classifications, or release blockers. Work
starts only after its activation trigger is met and a separate narrow execution plan is accepted.
Do not reopen an archived parent plan as a mutable backlog.

| Candidate | Current decision | Activation trigger | Scheduling contract |
| --- | --- | --- | --- |
| Material 3 TextField structural fidelity | Retain the supported native TextField structure and current theme bridge | A prioritized product requirement or reviewed visual baseline demonstrates a materially visible incompatibility with the pinned standard Material 3 behavior | Create a `material3-textfield-structural-fidelity` plan that owns IME, selection, accessibility, RTL, font-scale, measurement, save/restore, visual, performance, and rollback evidence; do not add Material dependencies to UI Foundation or Android Renderer |
| Material 3 Switch and Slider exact geometry and motion | Retain the accepted colors, touch targets, semantics, native behavior, and current geometry | Product review demonstrates a visible normal-density geometry or motion gap, or an accessibility impact | Create a `material3-switch-slider-geometry` plan with screenshot/geometry, touch, keyboard, accessibility, RTL, density, frame/allocation, and independently revertible per-control evidence |

The former additional-component-appearance candidate was activated by the 2026-08-15 field audit
and completed in the archived
[remaining component appearance convergence](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/remaining-component-appearance-convergence.md)
plan. FAB, app-bar, Badge, AlertDialog, and modal-bottom-sheet appearance now follows ADR-0013.
Scaffold and raw Dialog were rejected as override families after the same audit and retain their
layout and overlay contracts.

## 5. Milestone contracts

### Milestone A: Overlay stability

Deliver Dialog position/scrim/dismiss regression, Popup alignment/anchor/window stability,
documented and tested Snackbar/Toast queues, and ModalBottomSheet host-lifecycle behavior.

Completion requires consistent show/hide/update/dismiss unit tests, real-host instrumentation, and
no leak after Activity finish or configuration change.

### Milestone B: Collections and containers

Deliver LazyRow/Grid and horizontal/vertical Pager, register them in
[the session checklist](../architecture/session-containers.md), test stable-structure closure
refresh, provide complete LazyListState and structured items/sticky headers/content types/Grid
spans/prefetch.

Completion requires verifiable empty-diff refresh, stable keyed reorder/local state, a stress Demo,
and automated sticky-header, stable-ID, view-type, and restoration regression.

### Milestone C: Input and forms

Deliver focus/IME actions, complete text/selection/composition transactions, transformations,
undo/redo, autofill/restoration, rich text and Receive Content, form validation/read-only/error
compositions, and theme/state visual regression.

Completion requires predictable input without cross-control interference, reproducible clipping and
height tests, and correct Chinese/Japanese composition, directional selection, external updates, and
process restoration without cursor jumps or text loss.

### Milestone D: Diagnostics and performance

The remaining observability work has moved to the active
[diagnostics correlation, inspection, and production observability plan](./plans/diagnostics-correlation-inspection-observability.md).
It owns enhanced render/patch/layout inspection and the panel that locates high-frequency problems.
This milestone retains maintained benchmark baselines and release optimization such as baseline
profiles. Completion requires quantitative evidence from both work streams.

### Milestone E: Preview and screenshots

Deliver the Compose bridge, static runner, Studio plugin, shared PreviewCatalog/Paparazzi IDs,
compiled app previews including CounterScreen, and representative Light/Dark and Phone/Tablet
configurations.

Completion requires `qaPreview` coverage of runner/discovery/snapshots, plugin source linkage and
refresh/diagnostic regression, and an agreed Dark/Tablet matrix for public components and tutorials.

### Milestone F: Animation and gestures

Deliver animation core/DSL APIs, gesture policy/DSL APIs, graphicsLayer patches and Android interop,
plus the six-tab Animation API index, instrumentation, PreviewCatalog, and Paparazzi.

Completion requires opt-in behavior, stable consumed-gesture fallback, `qaQuick`/`qaPreview`, and
`qaFull` when a device is available.

That first-round milestone remains completed. Physical spring/decay/results, full animated content,
rich visibility transforms, seekable transitions, bounds animation, navigation-aware shared motion,
and request-driven timeline tooling are a separate active expansion tracked by the
[Animation Compose-capability expansion plan](./plans/animation-compose-capability-expansion.md).
MotionLayout expansion is not scheduled and is not a completion condition of either scope.

### Milestone G: Graphics 2D

Deliver graphics core/DSL layers, renderer pipeline, Canvas/draw/cache/DrawScene/interop, and
Preview/Demo/unit/targeted instrumentation.

Completion requires purity, compile, unit, and snapshot gates; regression for RoundRect, Drawable,
and ImageFilter Chain; and stable Graphics instrumentation on the current device environment.

### Milestone H: Advanced shadows

Deliver ordered outer/inner layers, shape/spread/offset, bounded raster cache, experimental
RenderNode, diagnostics, Lazy/input scenarios, paired Compose benchmarks, and targeted devices.

Completion requires benchmark-selected Auto behavior, related quick/preview/benchmark gates and
Samsung SM-G991B regression, plus observable cache/backend/fallback diagnostics.

## 6. Unified test and Demo gate

A capability cannot be marked implemented without unit tests, a Demo scenario with verification
points, and any required Demo UI test.

Add delayed-session special coverage for RecyclerView-based lazy and pager reuse containers, containers whose
structure diff can diverge from content refresh, and independent overlay-surface sessions.

Before a milestone is `Completed`:

1. `:viewcompose-renderer-android:compileDebugKotlin` and `:app:compileDebugKotlin` pass;
2. `:app:connectedDebugAndroidTest` and applicable tutorial connected tests pass, or the roadmap
   records a scoped exemption and deadline.

## 7. Current non-goals

1. Reproducing the complete Compose Runtime/Compiler model.
2. A complex global overlay-routing system in v1.
3. Maintaining duplicate roadmap files merely for documentation completeness.

## 8. Historical migration

| Old document | Current location |
| --- | --- |
| `WIDGET_ROADMAP.md` | This document plus archive |
| `DEMO_ROADMAP.md` | This document plus archive |
| `OVERLAY_COMPONENTS_ROADMAP.md` | This document plus archive |
| `UI_TESTING.md` | This document plus archive |

See the [archive index](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/README.md).
