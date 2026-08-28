---
schema_version: 2
document_id: tooling.preview
doc_type: tooling
owner:
  kind: project
  id: preview-tooling
version_lane: released
capability_ids:
  - preview.core.annotations
  - preview.core.protocol
  - preview.gradle
  - preview.runner
  - preview.worker
  - preview.integration
artifact_ids:
  - viewcompose-preview-core
  - viewcompose-preview-gradle-plugin
  - viewcompose-preview-runner
  - viewcompose-preview-worker-host
  - viewcompose-preview
sample_ids:
  - tooling.preview-native-install
  - tooling.preview-entry
  - module.preview-theme-provider
  - module.preview-compose-bridge
supported_versions:
  - Gradle plugin and Preview Core/Worker Host 0.1.0-alpha03
  - Preview Runner and optional Preview Integration 0.1.0-alpha05
  - ViewCompose Preview Android Studio plugin 1.2.0 on build family 261.*
verification_commands:
  - ./gradlew :samples:counter:verifyCounterPreview
  - ./gradlew :viewcompose-preview:verifyPaparazziDebug
  - ./gradlew qaPreview
---

# ViewCompose Preview

Use the first-party static-preview pipeline for compiled ViewCompose DSL rendered as native Android
Views through Layoutlib. It needs no Compose compiler/runtime in the application module. The Compose
Preview bridge is optional for projects that already use Compose tooling.

## Install the static-preview pipeline

The Gradle plugin discovers compiled entries and prepares Android inputs; Preview Core defines
annotations/protocol; Worker Host owns Layoutlib; Runner mounts and exports the frame. Install the
Android Studio `ViewCompose Preview` plugin separately for gutter actions, gallery/tool window,
refresh, source navigation, and diagnostics.

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="preview-native-install" sample_id="tooling.preview-native-install" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin title="build.gradle.kts"
plugins {
    id("com.viewcompose.preview") version "0.1.0-alpha03"
}

dependencies {
    debugImplementation("com.viewcompose:viewcompose-preview-core:0.1.0-alpha03")
    add(
        "viewComposePreviewWorkerHost",
        "com.viewcompose:viewcompose-preview-worker-host:0.1.0-alpha03",
    )
    add(
        "viewComposePreviewRunner",
        "com.viewcompose:viewcompose-preview-runner:0.1.0-alpha05",
    )
}
```

The artifacts are independently versioned. Keep them on debug/tooling configurations and verify
the current [module catalog](../modules/README.md) before mixing versions.

## Declare and render an entry

Annotate a public top-level/static function with exactly one `UiTreeBuilder` receiver/parameter and
`Unit` return. Repeated annotations and source-visible meta-annotations create variants.

{/* compiled-region source="samples/counter/src/debug/java/com/viewcompose/samples/counter/CounterPreview.kt" region="preview-entry" sample_id="tooling.preview-entry" build_target=":samples:counter:compileDebugKotlin" */}
```kotlin title="CounterPreview.kt"
@ViewComposePreview(
    name = "Counter · Light",
    group = "Samples/Getting started",
)
@ViewComposePreview(
    name = "Counter · Dark",
    group = "Samples/Getting started",
    theme = PreviewTheme.Dark,
)
fun UiTreeBuilder.CounterPreview() {
    CounterScreen()
}
```

Sync, open the source, and use the gutter icon or **View | Tool Windows | ViewCompose Preview**.
Source-only saves use incremental refresh; signatures, resources, manifest, or dependencies require
a full update. The inspector exposes native/VNode structure, bounds, composition/patch activity,
phase timings, and source-aware diagnostics while application/Layoutlib code stays outside Studio.

## Match application themes

Add `com.viewcompose:viewcompose-preview:0.1.0-alpha05` to `debugImplementation` when the default
Android theme bridge is insufficient. One provider returns a configuration-qualified Context and
matching ViewCompose tokens.

{/* compiled-region source="viewcompose-preview/src/test/samples/com/viewcompose/preview/samples/PreviewSamples.kt" region="preview-theme-provider" sample_id="module.preview-theme-provider" build_target=":viewcompose-preview:compileDebugUnitTestKotlin" */}
```kotlin
@ViewComposePreviewThemeProvider
object ApplicationPreviewThemeProvider : PreviewThemeProvider {
    override fun resolve(
        context: Context,
        theme: PreviewTheme,
    ): PreviewThemeResolution {
        val tokens = when (theme) {
            PreviewTheme.Light -> UiThemeDefaults.light()
            PreviewTheme.Dark -> UiThemeDefaults.dark()
        }
        return PreviewThemeResolution(context = context, tokens = tokens)
    }
}
```

Native Views and `stringResource`/`colorResource`/`dimensionResource` then share locale, density,
direction, night qualifiers, and one resource environment.

## Use the optional Compose bridge

Enable Compose and add the same optional `viewcompose-preview` artifact only when an existing
Compose Preview surface is valuable. The bridge is not the native gallery, application-theme
provider, static artifact, or structured-diagnostic pipeline.

{/* compiled-region source="viewcompose-preview/src/test/samples/com/viewcompose/preview/samples/PreviewSamples.kt" region="preview-compose-bridge" sample_id="module.preview-compose-bridge" build_target=":viewcompose-preview:compileDebugUnitTestKotlin" */}
```kotlin
@Preview
@Composable
fun composePreviewBridgeSample() {
    val diagnostics = remember {
        RenderDiagnostics(
            collection = RenderDiagnosticCollection(),
            sink = { event -> println(event) },
        )
    }
    ViewComposePreview(
        options = ViewComposePreviewOptions(diagnostics = diagnostics),
    ) {
        Text("ViewCompose")
    }
}
```

## Inspect a running debug build

With `viewcompose-preview` in a debuggable foreground application, Studio offers two explicit,
request-only tools:

- **Inspect Device Diagnostics** selects a session, shows correlated committed frame/failure state,
  navigates bounded source candidates, snapshots/highlights mounted Views, and records a finite
  eight-frame/two-second ViewCompose timing workload. **Capture next LazyItem** instead arms the
  selected exact parent for ten seconds and records one first supported frame from the next logical
  `LazyItem`, including an opaque physical-container token for holder-reuse correlation.
- **Inspect Device Animation Timeline** discovers committed transitions and records one selected,
  read-only capture for at most 500 ms. It cannot seek or mutate device state.

Both use Android `DUMP`, a one-use nonce, foreground package/process checks, private atomic response
files, bounded payloads, and fail-closed stale/disposed paths. No valid request means no report
polling/write, recurring tree traversal, frame observer, timing allocation, or active capture.
The future-item arm matches only parent Session ID, `LazyItem` role, and a post-request Session-ID
floor; it never accepts or returns an application key or native object.
Device timing excludes Android measure/layout/draw, GPU, RenderThread, SurfaceFlinger, decode,
network, database, and external SDK work. See the
[Preview Integration module](../modules/viewcompose-preview/README.md) for exact ownership and bounds.

## Snapshot verification

Run `./gradlew :viewcompose-preview:verifyPaparazziDebug`; reviewed Goldens live under
`viewcompose-preview/src/test/snapshots/images/`. After an intentional reviewed visual change, use
`./gradlew :viewcompose-preview:recordPaparazziDebug`. Never record an unexplained mismatch.
`qaPreview` is the required independent CI gate and uploads difference/test artifacts after failure.
The current `0.15%` catalog tolerance covers the documented Layoutlib editable-text glyph variance,
not layout, color, or content regressions. Static catalog scenes model overlays without opening real
windows; instrumentation owns actual dialog/popup/sheet behavior.

## Ownership map

- [Preview Core](../modules/viewcompose-preview-core/README.md): annotations, configurations,
  protocol, snapshots.
- [Gradle Plugin](../modules/viewcompose-preview-gradle-plugin/README.md): variants, discovery,
  fingerprints, tasks, stripping.
- [Runner](../modules/viewcompose-preview-runner/README.md): entry resolution, Android frame,
  capture and diagnostics.
- [Worker Host](../modules/viewcompose-preview-worker-host/README.md): Layoutlib process and class
  loader isolation.
- [Preview Integration](../modules/viewcompose-preview/README.md): application themes, Compose
  bridge, optional running-device tooling.
