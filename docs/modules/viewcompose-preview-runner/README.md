---
schema_version: 2
document_id: module.viewcompose-preview-runner
doc_type: module
owner:
  kind: module
  id: viewcompose-preview-runner
version_lane: released
capability_ids:
  - preview.runner
artifact_ids:
  - viewcompose-preview-runner
sample_ids:
  - module.preview-runner-render
coordinate: com.viewcompose:viewcompose-preview-runner:0.1.0-alpha04
minimal_usage_sample_id: module.preview-runner-render
---

# Preview Runner

`viewcompose-preview-runner` is the Android execution layer for deterministic static previews. It is
normally resolved by the Gradle plugin/worker host, not placed on an application runtime classpath.
It supports Android API 24+ inside Layoutlib, Paparazzi, or another controlled host.

{/* compiled-region source="viewcompose-preview-runner/src/test/samples/com/viewcompose/preview/runner/samples/PreviewRunnerSamples.kt" region="preview-runner-render" sample_id="module.preview-runner-render" build_target=":viewcompose-preview-runner:compileDebugUnitTestKotlin" */}
```kotlin
/** Resolves application bytecode and exports one static preview response. */
fun renderCompiledPreviewSample(
    context: Context,
    request: PreviewRenderRequest,
    applicationClassLoader: ClassLoader,
): PreviewRenderResponse {
    return StaticPreviewWorker().render(context, request, applicationClassLoader)
}
```

## Execution and ownership

`PreviewJvmEntryPointResolver` accepts one unambiguous public static JVM method with a single
`UiTreeBuilder` receiver/parameter and `Unit` return. An application theme provider is constructed
from Kotlin `INSTANCE` or a public no-argument constructor. `StaticPreviewRenderer.mount` verifies
descriptor/API identity, resolves Android configuration and theme, installs lifecycle, ViewModel,
saved-state, resource-environment, and theme owners, and lays out one native hierarchy.

Every successful `StaticPreviewFrame` must be closed. Closing destroys all frame-scoped owners and
providers; independent mounts do not share SDK state. A borrowed application class loader is not
installed as the thread context loader and is never closed by the runner.

The worker exports `preview.png` and `render-tree.json` with atomic replacement. The response records
entry resolution, mount/layout, image export, and snapshot export timings. Expected discovery,
theme, render, layout, capture, and export failures become structured responses; thread death and
out-of-memory errors escape so the host can retire.

## Configuration, sizing, and diagnostics

`PreviewAndroidContextFactory` mirrors density, font scale, viewport, locale, direction, and
light/dark mode into Android resources and `AndroidResourceEnvironment`, with observation disabled.
An application `PreviewThemeProvider` is authoritative when present; otherwise the deterministic
Android theme bridge disables dynamic color. Requested API level must match the worker.

Fixed height uses the configured viewport. Auto height first lays out a real viewport and expands
only root-growing scroll descendants, bounded by maximum dp height and a 16-megapixel capture
budget. PNG capture is lossless. Immutable diagnostics include structure, native bounds, clipping,
patches, composition and source locations without retaining runtime objects.

- Stability: **Alpha** tooling infrastructure; protocol compatibility belongs to Preview Core.
- Close every mounted frame, including assertion-only tests.
- Test fixed/auto height, nested scrollers, capture limits, RTL, locales, font scale, API matching,
  application themes, and each failure phase with `:viewcompose-preview-runner:testDebugUnitTest`.

See [Preview Core](../viewcompose-preview-core/README.md),
[Worker Host](../viewcompose-preview-worker-host/README.md), and the
[generated API reference](https://docs.viewcompose.com/api/viewcompose-preview-runner/current/).
