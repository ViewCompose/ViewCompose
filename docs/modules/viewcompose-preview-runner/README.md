# Preview Runner

`viewcompose-preview-runner` is the Android execution layer for deterministic ViewCompose static
previews. It resolves compiled preview entry points, creates a preview-qualified Android context,
mounts the DSL into a native View hierarchy, captures immutable image and diagnostic artifacts, and
releases every frame-scoped owner after export.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-preview-runner:0.1.0-alpha01")
}
```

- Stability: **Alpha**. This is preview-tooling infrastructure rather than an application runtime
  dependency.
- Runtime: Android API 24 or newer inside a device host, Paparazzi, or the isolated Layoutlib worker.
- Normal installation: the ViewCompose preview Gradle plugin and worker host resolve the runner;
  application modules normally depend only on preview annotations.
- Public API dependencies: preview-core supplies the protocol and `viewcompose-preview` supplies the
  optional application theme-provider contract.

## Execution pipeline

`PreviewJvmEntryPointResolver` loads the descriptor's owner through the supplied application class
loader. A valid entry is one unambiguous public static JVM method with exactly one `UiTreeBuilder`
receiver and a `Unit` return. An optional application theme provider is created from Kotlin's
`INSTANCE` field or a public no-argument constructor.

`StaticPreviewRenderer.mount` then verifies descriptor and API-level identity, resolves the Android
configuration and theme, installs lifecycle, ViewModel, saveable-state, environment, and theme
owners, renders synchronously, and lays out the native hierarchy. The returned
`StaticPreviewFrame` owns these resources and must be closed.

`StaticPreviewWorker` captures `preview.png` and `render-tree.json` into the request output
directory. Temporary files are replaced atomically, so Gradle and Studio never consume partially
written artifacts. The response records entry-resolution, mount/layout, image-export, and
snapshot-export timings.

## Configuration and theme fidelity

`PreviewAndroidContextFactory` mirrors density, font scale, viewport dimensions, locales, layout
direction, and light/dark mode into Android resources. The renderer installs the same values in
`UiEnvironment`, keeping native Views, resource qualifiers, Android View interop, and the DSL on one
configuration.

When a descriptor names a `PreviewThemeProvider`, its context and `UiThemeTokens` are authoritative.
Otherwise the Android theme bridge resolves the configured context with dynamic color disabled,
making Studio, Gradle, and CI renders reproducible. The worker's Android API must exactly match a
request that specifies an API level.

## Sizing and capture

Fixed-height requests are measured at the configured viewport. Auto-height requests first lay out a
real viewport, then expand only scrollable descendants that grow with the root. Expansion is bounded
by the shared maximum dp height and a 16-megapixel capture budget; warnings explain incomplete or
budget-limited results.

`AndroidBitmapCaptureBackend` draws a measured View into an ARGB bitmap and writes lossless PNG.
Alternative hosts may implement `StaticPreviewCaptureBackend`, but the worker remains responsible
for atomic artifact publication and response generation.

## Diagnostics and source mapping

The immutable snapshot contains render statistics, VNode and native View trees, patch records,
composition scopes and invalidation reasons, source call sites, captured View properties, clipping
state, and layout diagnostics. Runtime Views are not retained in the protocol model.

Expected discovery, theme, render, layout, capture, and export failures become source-aware
`RenderFailure` responses. Thread death and out-of-memory errors escape so the worker host can retire
the process. A borrowed application class loader is neither installed as the thread context loader
nor closed by the runner.

## Testing and extension rules

- Exercise the runner through the same Layoutlib API, resources, density, and theme-provider inputs
  used by production preview tasks.
- Close every successful frame, including test assertions that inspect only the snapshot.
- Keep custom capture backends synchronous and fail before returning a partial image.
- Preserve descriptor equality and exact JVM signatures when extending discovery.
- Add protocol data to preview-core first; do not expose live Android objects in serialized models.
- Test fixed height, auto height, nested fixed scrollers, capture budgets, RTL, locales, font scale,
  application themes, and each failure phase.

## Related documentation

- [Preview Core module](../viewcompose-preview-core/README.md)
- [Preview Worker Host module](../viewcompose-preview-worker-host/README.md)
- [Preview Gradle Plugin module](../viewcompose-preview-gradle-plugin/README.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

The complete generated reference is available in the
[`viewcompose-preview-runner` API tree](https://docs.viewcompose.com/api/viewcompose-preview-runner/current/).

## Compatibility notes

The `0.1.0-alpha01` line establishes exact compiled-entry validation, configuration and theme
parity, frame-scoped Android owners, bounded auto-height measurement, atomic PNG/snapshot export,
and immutable diagnostics. Preview protocol compatibility remains owned by preview-core.
