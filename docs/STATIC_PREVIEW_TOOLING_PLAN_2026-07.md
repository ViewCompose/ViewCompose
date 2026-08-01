# ViewCompose Static Preview Tooling Plan

## 1. Goal

Build a ViewCompose-owned static preview workflow without making Compose Preview the foundation.
The first supported IDE target is the locally installed Android Studio build:

- Product code: `AI`
- Build: `261.25134.95.2612.15914620`
- Data directory generation: `AndroidStudio2026.1.2`
- Required Java runtime: 21

The initial product is intentionally static. Interaction, IME, gesture playback, animation
timelines, and device streaming remain optional later stages.

## 2. Repository and build boundary

The preview stack stays in the ViewCompose Git repository while its IDE plugin uses an
independent Gradle build.

```text
ViewCompose
├── viewcompose-preview-core       pure Kotlin contracts and protocol
├── viewcompose-preview-gradle-plugin
│                                   Android variant export and bytecode discovery
├── viewcompose-preview-runner     isolated Android/Layoutlib renderer
├── viewcompose-preview            optional Compose Preview adapter and catalog
└── tools/viewcompose-studio-plugin
    └── independent IntelliJ Platform Gradle build
```

The root quality gate must never download or compile an Android Studio distribution. Plugin CI is
path-filtered and separate. The plugin communicates with the runner through a versioned data
protocol instead of linking renderer internals into the IDE process.

## 3. Architecture invariants

1. `viewcompose-preview-core` is Kotlin/JVM-pure and imports neither Android nor Compose.
2. A render request is fully resolved: no system theme, locale, density, or layout direction.
3. Preview rendering runs outside the Android Studio process.
4. The IDE plugin never depends on `com.android.tools.idea` implementation classes unless a
   separately documented experiment proves there is no public alternative.
5. Preview failures cross the process boundary as structured diagnostics.
6. Every request and response carries a protocol version and request ID.
7. Old render results must never overwrite a newer request in the IDE.
8. Compose Preview remains an optional adapter and consumes the same core theme/configuration
   vocabulary.
9. Normal `qaQuick` remains independent from Android Studio plugin assembly.
10. Source locations and render diagnostics are additive metadata; preview tooling must not change
    production rendering semantics.
11. Runtime source identity is captured only inside an explicit tooling frame, survives VNode
    wrapping and native View mounting, and never participates in VNode equality or reconciliation.
12. An application-owned preview theme is resolved once and shared by the preview root Context,
    native Android Views, and the outermost ViewCompose `UiTheme`; Studio never serializes runtime
    `UiThemeTokens` objects across the process boundary.
13. Automatic save refresh is latest-wins and must not repeatedly cancel an expensive compile or
    Layoutlib render already in flight. User-driven selection and manual refresh may preempt work.
14. Gallery throughput is improved through bounded batching, never unbounded parallel Layoutlib
    instances. One external worker may reuse Layoutlib across Gradle invocations, but it is
    serialized per variant and retires after 24 renders, 120 seconds idle, a failed render, or
    768 MiB used heap.
15. Studio image retention is budgeted by decoded pixel bytes rather than PNG count or file size;
    every project-owned listener, task, popup, and image cache has an explicit disposal boundary.
16. Performance data crosses the same versioned boundary as render results. Layoutlib setup,
    mount/layout, artifact export, Gradle execution, and Studio decoding remain separately visible.
17. Global preview loading is viewport-first. It may use two bounded render calls per module to
    show the visible screen early, but discovery and compilation are still shared.
18. Studio uses one cancellable Gradle Tooling API connection per project. Layoutlib remains in a
    bounded external worker, and the connection is closed with the project service.
19. Persistent workers retain only renderer/runtime/resource infrastructure. Project bytecode is
    loaded by a fresh closeable class loader for every command; source-only changes must neither
    restart Layoutlib nor observe stale application classes.
20. Warm-worker output must pass an explicit cold-versus-warm equivalence gate. Pixels are exact;
    render snapshots are exact after removing tooling-only node identities. Any transport or warm
    render failure retries in an isolated process before the task reports failure.

## 4. Delivery stages

### Stage 0 — foundation

- Create the isolated feature branch.
- Add this execution plan.
- Add pure Kotlin preview contracts.
- Define repeatable `@ViewComposePreview`.
- Define deterministic configuration, descriptor, request, response, artifacts, diagnostics, and
  protocol version.
- Make the existing Compose adapter consume the core theme model.
- Add API invariants, repeatability, validation, and purity tests.

Exit criteria:

- `viewcompose-preview-core:test` passes.
- Existing Compose previews compile without their duplicate theme enum.
- Root package and purity gates include the new module.

### Stage 1 — static runner

- Add `viewcompose-preview-runner`.
- Resolve a descriptor to a compiled JVM entry point.
- Create a deterministic Android preview environment.
- Mount ViewCompose through `RenderSession`.
- Perform measure, layout, and draw.
- Export PNG, render tree, composition diagnostics, warnings, and timing.
- Return structured compile/discovery/render/export failures.

Exit criteria:

- One catalog preview renders without Compose.
- The runner output is byte-addressable through the protocol.
- A broken preview returns a diagnostic rather than crashing the worker.

Implemented foundation:

- `StaticPreviewRenderer` injects a fully explicit environment and theme, then performs native
  measure/layout.
- A module may mark exactly one debug-only `PreviewThemeProvider` with
  `@ViewComposePreviewThemeProvider`. Compiled discovery records its class name, and the isolated
  runner loads it with the application class loader. The provider returns one Context/token pair,
  which prevents native Views and the DSL tree from silently using different theme sources.
- `StaticPreviewWorker` atomically exports PNG and a JSON snapshot containing render tree, patch,
  structure, warnings, bindings, and composition scopes.
- `PreviewJvmEntryPointResolver` validates and invokes a public static
  `UiTreeBuilder.() -> Unit` JVM method through an isolated class loader.
- A Paparazzi/Layoutlib test proves the path renders Android Views without invoking Compose APIs,
  while missing compiled symbols return structured diagnostics.

### Stage 2 — configuration matrix

- Render Light and Dark from one descriptor.
- Add locale, layout direction, width/height, density, font scale, and API-level inputs.
- Provide named phone/tablet and accessibility presets.
- Add deterministic variant IDs and artifact paths.

Exit criteria:

- Light/Dark and LTR/RTL snapshots are independently addressable.
- Invalid configurations fail before worker startup.

Implemented foundation:

- `PreviewConfigurationMatrix` expands ordered axes into a deterministic Cartesian product with
  stable, path-safe variant IDs.
- Built-in meta-annotations cover Light/Dark, phone/tablet, LTR/RTL, and default/large/accessibility
  font scales without coupling discovery to Android Studio.
- `PreviewArtifactLayout` gives Gradle, the worker, and the IDE one canonical artifact directory
  for every preview/variant pair.
- The worker rejects API-level mismatches before mounting and applies the resolved density, font
  scale, locale, layout direction, theme, and device bounds to both ViewCompose locals and native
  Android resources.
- Unit and Layoutlib tests lock ordering, override precedence, validation, native output size, and
  environment propagation.

### Stage 3 — Gradle bridge

- Resolve active Android module/build variant.
- Export compiled classpath, resources, manifest, theme, generated sources, and output directory.
- Add a single-preview task and machine-readable descriptor discovery.
- Add request fingerprints and render caching.

Exit criteria:

- A command can render one preview from `:app:debug`.
- Source, dependency, and resource changes invalidate the correct cache.

Implemented:

- The `com.viewcompose.preview` Gradle plugin registers one discovery task per Android application
  or library variant through the public AGP Variant and Scoped Artifacts APIs.
- `PreviewBuildManifest` exports project classes, runtime and boot classpaths, sources, merged
  Manifest, SDK location, namespace, min/target/compile SDK bounds, resource package names, and a
  content fingerprint.
- The resource environment keeps local, project-module, and external-AAR resources and assets as
  separate ordered input roles. This is the same information Layoutlib needs to resolve dependency
  themes and resources without loading the application in Gradle.
- `CompiledPreviewScanner` reads JVM bytecode with ASM instead of loading application classes into
  the Gradle daemon. Direct, repeatable, built-in, and project-defined meta-preview annotations are
  supported.
- Invalid function signatures and invalid annotation configurations are emitted as structured
  discovery diagnostics while valid previews remain usable.
- `discover<Variant>ViewComposePreviews` atomically writes `build-manifest.json` and
  `descriptors.json` under `build/viewcompose-preview/<variant>`.
- A TestKit Android fixture compiles a real preview function with local resources/assets and
  AndroidX resources, then verifies the exported build model and descriptor catalog end to end.
- `viewcompose-preview-worker-host` is a standalone JDK 17 executable boundary. It reads one
  `PreviewWorkerCommand` or one bounded `PreviewWorkerBatchCommand`, owns the
  Paparazzi/Layoutlib environment, and writes each structured response atomically. Its loopback,
  token-authenticated server is serialized and bounded by render count, idle time, used heap, and
  failure retirement.
- The worker host has no compile-time dependency on Gradle, Android Studio, or
  `viewcompose-preview-runner`; the Android runner is found only on the isolated process classpath.
- A real Layoutlib integration test renders a compiled ViewCompose entry through the host and
  verifies both PNG and render-tree artifacts.
- `render<Variant>ViewComposePreview` selects one descriptor/configuration pair or consumes an IDE
  batch target file. Layoutlib archives and the application classpath are prepared once per task;
  uncached targets are rendered in bounded worker batches without loading application or Layoutlib
  classes into the Gradle daemon.
- Worker compatibility is content-addressed across the Layoutlib environment, worker host,
  runner, Layoutlib distribution, dependencies, resources, assets, and Manifest. Application class
  directories/jars are deliberately excluded from that compatibility key and loaded through a new
  per-command class loader. Generated `R` classes are the only project classes retained by the
  worker because Layoutlib resolves them through its own class-loader boundary.
- `--verify-worker-reuse` renders a proven warm result and an isolated cold result, then compares
  exact PNG bytes and normalized render-snapshot semantics. Failed warm transport or rendering
  automatically falls back to a cold worker, so reuse is an optimization rather than a correctness
  dependency.
- Render requests carry the exported build fingerprint. Planning and worker startup both reject a
  stale catalog, mismatched module, variant, or fingerprint.
- Successful PNG and render-tree results are reused from a content-addressed cache keyed by build
  fingerprint, preview id, and variant id. `--rerender` explicitly bypasses it.
- TestKit covers real Android variant discovery, the first single-preview render, and the following
  cache hit. The standalone host additionally has a real Layoutlib integration test.

### Stage 4 — Android Studio plugin shell

- Create an independent IntelliJ Platform build pinned to AI-261.
- Add project detection and a dockable ViewCompose Preview tool window.
- Add Kotlin line markers for valid preview functions.
- Render the active preview and show progress, image, and errors.

Exit criteria:

- The plugin installs into the pinned Android Studio build.
- A gutter action opens the matching static preview.

Implemented shell foundation:

- `tools/viewcompose-studio-plugin` is an independent Gradle 9.6.1 build using IntelliJ Platform
  Gradle Plugin 2.18.1 and Kotlin/JVM 21. The root Android build does not include or evaluate it.
- The build resolves the local Android Studio SDK and verifies the exact
  `AI-261.25134.95.2612.15914620` product before verification, sandbox preparation, or packaging.
- Project detection is a bounded file-system scan for the preview Gradle plugin or an exported
  descriptor catalog. It does not trigger Gradle sync or depend on Android plugin implementation
  APIs.
- A lazily initialized `ViewCompose Preview` tool window is registered only for detected projects
  and provides the stable UI host for later render states.
- Kotlin preview functions receive a leaf-attached gutter marker. Direct, repeatable-container,
  aliased-import, and source meta-annotation forms share the same bounded classifier.
- Clicking the marker records the exact source file, symbol, and line in a project service, then
  opens the matching selection in the lazily created tool window.
- The project service launches Gradle wrapper work in a cancellable background task. Discovery and
  rendering remain separate processes, and superseded selections cannot publish stale UI state.
- The IDE reads only the stable catalog/response subset, matches descriptors by source identity,
  prefers the debug catalog deterministically, and invokes the existing single-preview task with
  its descriptor and first declared variant.
- Successful PNG output is decoded under file-size, pixel-count, and artifact-root boundaries.
  Render diagnostics and bounded Gradle output are displayed in the same tool window on failure.
- Unit tests cover project detection, source-selection validation, direct annotations, source
  meta-annotations, same-short-name rejection, protocol hardening, nested-module task planning,
  render result loading, and discovery failure handling. Plugin configuration, archive structure,
  binary compatibility, and sandbox installation are verified against the pinned local Android
  Studio SDK.

### Stage 5 — automatic refresh and navigation

- Debounce saved document changes.
- Cancel superseded compile/render requests.
- Reject stale responses by request ID.
- Navigate compile and render diagnostics to source.
- Preserve zoom and selected variant across refreshes.

Exit criteria:

- Saving a preview or direct dependency refreshes the image.
- Old results cannot replace newer output.
- Clicking a diagnostic opens its source line.

Implemented experience foundation:

- When the preview tool window is visible, moving the editor caret into another
  `@ViewComposePreview` function follows that function after a short debounce. Merely browsing
  code while the tool window is hidden never starts Gradle.
- Saving the selected source, a source/resource/asset in its module, or an input module inferred
  from the exported build manifest schedules one debounced refresh. Unrelated modules and
  generated `build`, `.gradle`, `.idea`, and VCS output are ignored. Root Gradle configuration and
  version catalogs remain project-wide inputs.
- Editor following and save refresh are project-level options, enabled by default and exposed in
  the tool-window gear menu.
- User-driven superseding requests cancel their active progress indicator and cannot publish over
  a newer selection. Save events arriving during a render collapse to the latest request and run
  once after the current render instead of repeatedly terminating Gradle.
- After the first successful render, save refresh, variant changes, and manual refresh reuse the
  stable descriptor id. Saving only the selected Kotlin/Java preview source uses a dedicated fast
  task: it incrementally compiles the current variant, rescans current project bytecode, and reuses
  the last complete resource/toolchain contract without executing full discovery. A missing or
  incompatible baseline, renamed preview, changed signature, or changed configuration fails closed
  and retries through full discovery. Other saved inputs retain the complete known-preview render
  route. Initial discovery targets debug directly and falls back to the all-variant aggregate only
  when a conventional debug task does not exist.
- Gradle discovery and rendering use one project-scoped Tooling API connection, removing repeated
  wrapper-client JVM startup while preserving Gradle daemon reuse, cancellation, and external
  Layoutlib isolation. A compatible bounded Layoutlib worker can survive consecutive Tooling API
  builds; source edits replace only the application class loader.
- On the local five-preview verification set, cold Layoutlib setup measured about 2403 ms while
  warm setup measured 31–44 ms. The warm batch passed exact cold-output equivalence, and a temporary
  source edit changed the output hash without changing the worker PID; reverting the edit restored
  the original hash exactly.
- In the local source-edit profile, the stable fast route completed in 2.626 s end to end: Android
  Kotlin compilation took 0.699 s and preview refresh/render took 0.476 s. The previous Studio
  known-preview route typically measured about 6–7 s on the same project. These are development
  machine measurements, not a cross-device benchmark, and remain exposed as separate timing phases.
- The all-previews gallery publishes placeholders before compilation, renders the current viewport
  first, preserves scroll position across partial results, and fills remaining previews in one
  second bounded batch. Disk-cache metadata is read eagerly, but PNG thumbnails decode only when a
  card becomes visible.
- Low-memory notifications clear high-resolution gallery details and decoded thumbnails. Phase
  timings are available from the preview header and `idea.log`, so cold, warm, saved-input, and
  gallery regressions can be attributed rather than guessed from one total duration.
- The selected preview variant survives save refreshes. Preview zoom, the selected diagnostics
  tab, and the layout-bounds toggle survive rerenders and UI-language changes.
- The preview canvas supports fit-to-window and fixed 50–200% zoom. A title-bar refresh action
  bypasses the render cache for an explicit clean rerender.
- While a newer frame is compiling, Studio keeps the last successful image visible and marks it
  explicitly as the previous result, avoiding flicker without presenting stale output as current.
- Source links in the header and structured compile/render diagnostics navigate to the exact file
  and line.

### Stage 6 — diagnostics

- Display VNode and native View trees.
- Export measured bounds and clipping rectangles.
- Display insert/reuse/patch/skip and recomposition reasons.
- Add optional layout-bound overlays to exported images.

Exit criteria:

- The diagnostics panel and screenshot describe the same committed frame.
- Diagnostics remain disabled by default outside tooling renders.

Implemented:

- Studio displays the committed VNode tree, native Android View tree, render structure, patch
  records, composition scopes, invalidation reasons, and binding skip/reuse statistics exported by
  the runner.
- Native measured/layout bounds are displayed in the View tree and can be overlaid on the preview
  image. Overlay coordinates follow preview zoom.
- Tooling renders capture a bounded DSL call chain for every emitted VNode and preserve one stable
  node ID through semantic copies, synthetic wrapper nodes, renderer diagnostics, and native View
  mounting. Normal app rendering performs no stack capture or source-metadata allocation.
- The preview canvas resolves the deepest mapped native View under the pointer. A single click
  highlights it, while double-clicking opens the highest-value project source call site.
- VNode and Android View trees use the same source metadata and support double-click or Enter
  navigation. Source resolution prefers application/project sources over framework internals and
  excludes generated build output.
- Editor caret movement performs the reverse lookup from project source lines to runtime node IDs.
  Canvas, VNode tree, Android View tree, and patch diagnostics share one linked selection without
  triggering a new render.
- Multiline DSL reverse lookup uses the enclosing Kotlin call expression's exact start line rather
  than a proximity heuristic, preventing neighboring declarations from being selected by mistake.
- Patch records retain the affected node identity and source chain for insert, remove, rebind,
  modifier patch, and skip operations.
- Composition scopes retain their creation-site source chain independently of VNode identity, so
  recomposition reasons and skipped scopes can navigate to source without inventing an unreliable
  scope-to-View association.
- The runner exports each native View's actual visible bounds after ancestor clipping, the clipping
  source, and whether that clipping belongs to a scrolling container. Canvas hit testing uses the
  visible region instead of selecting pixels that were clipped away.
- Source-aware layout diagnostics detect collapsed visible content, partial/full clipping,
  ellipsized text, and text content clipped by height or line constraints. The Studio Layout tab,
  canvas issue overlay, VNode tree, native View tree, and editor all share the same node selection.
- Layout diagnostics remain structured protocol data rather than preformatted English messages,
  allowing Studio to localize the same captured frame in English or Simplified Chinese.

## 5. Experience backlog priority

The remaining experience work is intentionally ranked by product value rather than feature count.

1. **Medium priority — multi-variant gallery and comparison.** Moderate implementation cost and
   useful for projects that routinely declare Light/Dark, locale, and screen-size matrices. The
   current single-variant selector remains faster and clearer for ordinary previews, so gallery
   mode should be optional rather than the default.
2. **Low priority — preview history, favorites, and bulk export UI.** Straightforward but offers
   little value until projects have a larger preview catalog, so it is not part of the current
   implementation target.

The following items require an explicit maintenance/value decision before implementation:

- **Editor Design Surface / custom file-editor split:** high implementation and compatibility cost.
  It requires owning an IntelliJ `FileEditorProvider`, split-editor lifecycle, focus, persistence,
  and Android Studio-version compatibility. The dockable right-side tool window already covers the
  main static-preview workflow.
- **Unsaved-code Live Edit:** very high cost. Gradle cannot compile editor buffers; this requires an
  incremental Kotlin compilation daemon or bytecode replacement pipeline and substantially changes
  the isolation model.
- **Interactive input, gestures, IME, and animation timelines:** high cost and currently low value
  for the requested static-preview workflow.

## 6. Deferred scope

- Unsaved-code Live Edit.
- Compiler plugin or bytecode hot replacement.
- Interactive input and gesture simulation.
- IME and accessibility service emulation.
- Animation timeline controls.
- Existing Compose Design-surface integration.
- JetBrains Marketplace publication.
- Multi-major Android Studio compatibility.

These features require separate value and maintenance reviews after static preview tooling is stable.
