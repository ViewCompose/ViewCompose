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

### Stage 3 — Gradle bridge

- Resolve active Android module/build variant.
- Export compiled classpath, resources, manifest, theme, generated sources, and output directory.
- Add a single-preview task and machine-readable descriptor discovery.
- Add request fingerprints and render caching.

Exit criteria:

- A command can render one preview from `:app:debug`.
- Source, dependency, and resource changes invalidate the correct cache.

### Stage 4 — Android Studio plugin shell

- Create an independent IntelliJ Platform build pinned to AI-261.
- Add project detection and a dockable ViewCompose Preview tool window.
- Add Kotlin line markers for valid preview functions.
- Render the active preview and show progress, image, and errors.

Exit criteria:

- The plugin installs into the pinned Android Studio build.
- A gutter action opens the matching static preview.

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

### Stage 6 — diagnostics

- Display VNode and native View trees.
- Export measured bounds and clipping rectangles.
- Display insert/reuse/patch/skip and recomposition reasons.
- Add optional layout-bound overlays to exported images.

Exit criteria:

- The diagnostics panel and screenshot describe the same committed frame.
- Diagnostics remain disabled by default outside tooling renders.

## 5. Deferred scope

- Unsaved-code Live Edit.
- Compiler plugin or bytecode hot replacement.
- Interactive input and gesture simulation.
- IME and accessibility service emulation.
- Animation timeline controls.
- Existing Compose Design-surface integration.
- JetBrains Marketplace publication.
- Multi-major Android Studio compatibility.

These features require separate value and maintenance reviews after static preview tooling is stable.
