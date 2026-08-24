# ViewCompose Preview

The recommended ViewCompose preview path is the first-party native static-preview toolchain. It
renders the application's compiled ViewCompose DSL as Android Views through Layoutlib, without
requiring Compose in the application module. The Compose Preview bridge remains available as an
optional adapter for projects that already use Compose tooling.

## How the native preview toolchain fits together

| Piece | Responsibility |
| --- | --- |
| `com.viewcompose.preview` Gradle plugin | Discovers compiled preview entries, prepares Android variant inputs, and starts rendering tasks. |
| `viewcompose-preview-core` | Supplies `@ViewComposePreview`, configuration models, and the shared preview protocol on the debug classpath. |
| `viewcompose-preview-worker-host` and `viewcompose-preview-runner` | Run Layoutlib and application render code outside Android Studio, producing PNG and structured diagnostic artifacts. |
| `ViewCompose Preview` Android Studio plugin | Adds gutter actions, the preview tool window and gallery, incremental refresh, source navigation, and diagnostic inspection. |

The Gradle plugin and Android Studio plugin are separate installations. Adding Maven dependencies
and `id("com.viewcompose.preview")` configures the build side only; it does not install the IDE user
interface.

## Install native static preview

### 1. Configure the Android module

Apply the preview Gradle plugin and keep its artifacts on debug or tooling-only configurations:

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
        "com.viewcompose:viewcompose-preview-runner:0.1.0-alpha04",
    )
}
```

These are the versions verified together by the current preview sample. ViewCompose artifacts are
independently versioned; consult the [published module catalog](../modules/README.md) before mixing
other versions. Basic native previews do not require the Compose compiler plugin, Compose
`buildFeatures`, or the `viewcompose-preview` bridge artifact.

### 2. Install the Android Studio plugin

In Android Studio, open **Settings | Plugins | Marketplace**, search for `ViewCompose Preview`, and
install it. Restart Android Studio if prompted. This IDE installation is required to get the
`ViewCompose Preview` tool window, gutter render actions, gallery, source navigation, incremental
refresh, and diagnostics.

The current Marketplace line is `1.1.0` and is advertised for Android Studio build family `261.*`.
The IDE plugin is versioned independently from the Maven artifacts and Gradle plugin.

### 3. Declare a preview entry

Annotate a public top-level DSL function. The compiled function must accept only the
`UiTreeBuilder` receiver and return `Unit`. This entry is copied from the compiled counter sample:

```kotlin title="CounterPreview.kt"
package com.viewcompose.samples.counter

import com.viewcompose.preview.tooling.PreviewTheme
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.ui.foundation.UiTreeBuilder

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

`@ViewComposePreview` is repeatable and may also be used through source-visible custom
meta-annotations. Configuration can describe theme, locale, layout direction, density, font scale,
viewport, and API level. Keep the preview entry and preview-only theme providers in a debug or
dedicated preview source set when they are not needed by application runtime code.

### 4. Render in Android Studio

Sync the project, open the Kotlin file, and click the ViewCompose preview gutter icon beside the
annotated function. You can also open **View | Tool Windows | ViewCompose Preview** and use the
gallery to select an entry. The plugin invokes the Gradle discovery and render pipeline, then shows
the native Android View result and its configurations.

While the tool window is visible, saving a source-only change uses the incremental refresh path.
Use the full-update action after changing signatures, resources, the manifest, or dependencies.
The inspection panels expose the native View tree, VNode structure, layout bounds, composition and
patch activity, phase timings, and source-aware diagnostics. Application code and Layoutlib execute
in bounded worker processes rather than inside the Android Studio process.

## Application theme fidelity

The native runner resolves Android resource qualifiers and ViewCompose environment values from the
preview configuration. If the default Android theme bridge is insufficient, implement
`PreviewThemeProvider` and mark one provider with `@ViewComposePreviewThemeProvider`. The provider
must return a matching themed Android `Context` and `UiThemeTokens` so native Views and the DSL use
one coherent theme.

The provider API is supplied by the debug-scoped `viewcompose-preview` artifact:

```kotlin
dependencies {
    debugImplementation("com.viewcompose:viewcompose-preview:0.1.0-alpha04")
}
```

See the [Preview Integration module](../modules/viewcompose-preview/README.md) for the provider
contract and lifecycle rules.

Both native static preview and the Compose bridge install the same `AndroidResourceEnvironment`
used by application hosts. Calls such as `stringResource`, `colorResource`, and
`dimensionResource` resolve from the preview-qualified Context, so locale, density, direction, and
night qualifiers agree with native Views. Static frames disable callback observation because the
preview descriptor owns deterministic configuration replacement.

## Compose Preview bridge (optional)

Use the Compose bridge only when an existing Compose Preview surface is useful to the project. It
embeds ViewCompose in Compose through `AndroidView`; it is not the recommended native preview path
and does not replace the ViewCompose plugin's gallery, source navigation, application-theme
provider, static artifacts, or structured diagnostics.

The consuming module enables Compose and adds the bridge on a development-only classpath:

```kotlin title="build.gradle.kts (optional Compose bridge)"
plugins {
    alias(libs.plugins.kotlin.compose)
}

android {
    buildFeatures {
        compose = true
    }
}

dependencies {
    debugImplementation("com.viewcompose:viewcompose-preview:0.1.0-alpha04")
}
```

Then wrap the DSL with `ViewComposePreview` or `ViewComposePreviewWithRoot` from
`com.viewcompose.preview`:

```kotlin
@Preview
@Composable
fun composePreviewBridgeSample() {
    ViewComposePreview {
        Text("ViewCompose")
    }
}
```

This bridge entry is covered by the compiled `viewcompose-preview` API sample. The bridge uses
`UiThemeDefaults` and Compose Preview lifecycle semantics. Choose the native static runner when
production-theme fidelity or the ViewCompose diagnostic pipeline matters.

## Inspect running-device diagnostics

The Android Studio plugin exposes one **Inspect Device Diagnostics** toolbar and Tools-menu action.
The alpha line hard-removes the former Locate, Highlight, Clear, and Timing actions: selecting a
device and Session once now keeps source navigation, the latest correlated frame and failure,
mounted-node highlighting, and finite timing in one inspector.

1. Add `viewcompose-preview` with `debugImplementation`, then install and foreground that debuggable
   build.
2. Navigate to the ViewCompose page under investigation.
3. Choose **Inspect Device Diagnostics**. If several devices are online, choose one by device kind,
   Android version, and serial number.
4. Select a Session in the parent/child tree. The summary distinguishes active, invisible,
   inactive, and ended lifetimes; shows the latest committed frame separately from a later rolled
   back attempt; and correlates the latest safe failure phase, recovery, exception type, and Android
   View operation.
5. Use **Session sources** to open the selected owner, **Mounted nodes** to load, navigate, highlight,
   or clear real View boundaries, and **Finite timing** to capture and navigate additive top-cost
   records without choosing the device or Session again.

The inspector refreshes only on an explicit command. Each request finds the foreground package,
creates a one-use nonce, and reads one private response only when nonce, operation, package, and live
process all match. Protocol v7 hard-rejects older reports. The correlated snapshot reads state that
the Session already retains; it installs no event history or recurring callback. It exposes no raw
exception, message, cause, stack, application key, View text, semantics, State, Local value, URL,
credential, or arbitrary `toString()` output. Exception output is limited to a 256-character binary
class name.

Host, navigation, and pager sessions may carry bounded source candidates. Lazy-item, overlay, and
preview sessions remain visible in the tree without composition-time source-stack capture. Source
navigation resolves bounded metadata against the current project. A missing source remains explicit
rather than falling through to another Session.

A node request visits at most 2,048 mounted nodes, returns 512 to depth 64, and assigns fresh opaque
process-local tokens. Highlighting resolves a weak current View, reports full and clipped-visible
screen bounds, and installs one non-interactive overlay for at most five seconds. Replacement,
detach, Session disposal, explicit clear, and timeout remove it. Stale, recycled, hidden, fully
clipped, synthetic/unsupported, ended, and rejected states fail closed without changing layout,
focus, accessibility focus, input, or application state.

Timing prompts for a workload and stops after at most eight completed frame attempts or two seconds.
Composition and reconciliation distinguish inclusive from self time; binding is direct. Studio
ranks only self/direct records as additive top costs, reports clock-read overhead, drops,
truncation, unsupported domains, and terminal reason, and lets a selected record open its source.
The result is capped at 64 timed nodes per frame, 512 records, depth 32, 128 bounded strings, and
256 KiB. It does not measure Android measure/layout/draw, GPU, RenderThread, SurfaceFlinger,
decoding, network, database, or external-SDK work. Without a request, there is no mounted-tree
traversal, report write, polling, recurring observer, per-node clock read, or timing-record
allocation.

The receiver requires Android's `DUMP` permission held by ADB shell and independently verifies the
debuggable process. If no report appears, keep the intended app foregrounded and verify the current
`viewcompose-preview` artifact is present. The Diagnostics → Renderer Demo route provides stable
automation tags for refresh, mounted-node replacement/highlighting, and the visible `0/8` to `8/8`
timing workload.

## Inspect a running animation timeline

Choose **Tools | Inspect Device Animation Timeline** while a debuggable application containing
`viewcompose-preview` is in the foreground. The plugin discovers the currently committed
ViewCompose transitions, asks you to select one when several are present, captures that identity
for 500 ms, and opens a read-only report. No device control is performed.

The report makes these distinctions explicit:

- observation is a bounded live-device capture, not a continuous profiler;
- control belongs only to static or interactive Preview content using the public
  `SeekableTransitionState.seekTo` contract;
- every channel retains its own duration, so shorter channels and the longest segment are visible;
- spring safety-guard termination, interruption/retarget samples, and unsupported/private values
  are shown rather than normalized away.

The request uses the same least-privilege debug boundary as device source location: an ADB-shell
`DUMP`-permission broadcast, a one-use 32-character nonce, foreground package and live-process
validation, and an atomically replaced response in application-private cache. Discovery takes one
snapshot. A selected capture lasts at most 500 ms, records at most 64 distinct samples with 32
channels each, and emits at most 256 KiB. Closing the dialog leaves no active capture, callback,
thread, or report publisher.

The 2026-08-23 Xiaomi MI 6 acceptance found four already composed timelines, selected
`demo_seekable_transition`, and captured distinct `180/420/600/720 ms` channel durations plus
unsupported generic-vector and safe numeric values. The running page remained visually unchanged,
which is the required read-only result; only the report advanced. Preview-owned control is covered
separately by the `animation-seekable-transition` catalog spec calling the public `seekTo` API and
by deterministic `SeekableTransitionState` ownership, range, retarget, and cancellation tests.

## Snapshot regression

Run the module snapshot verification:

```bash
./gradlew :viewcompose-preview:verifyPaparazziDebug
```

Committed snapshot baselines live in:

`viewcompose-preview/src/test/snapshots/images/`

When an intentional visual change has been reviewed, record its new baseline with:

```bash
./gradlew :viewcompose-preview:recordPaparazziDebug
```

Review every changed image before committing it. An unexplained mismatch must be fixed, not
recorded. Verification reports and difference images are written under
`viewcompose-preview/build/reports/paparazzi/`, and the repository CI runs `qaPreview` as an
independent required gate. A failed CI run retains its Paparazzi difference images and test reports
in the `qa-preview-failure-<attempt>` artifact for seven days.

The catalog harness permits at most `0.15%` total image difference solely to absorb the known
Layoutlib native editable-text glyph rasterization difference between supported macOS and Linux
hosts. Do not raise this threshold to accept unexplained layout, color, or content changes; fix the
regression or review and record an intentional baseline instead.

## Overlay preview policy

Preview scenes use static content to model overlays instead of creating real window layers. The
actual behavior of dialogs, popups, and bottom sheets is covered by instrumentation tests.

## Related documentation

- [Preview Core module](../modules/viewcompose-preview-core/README.md)
- [Preview Gradle Plugin module](../modules/viewcompose-preview-gradle-plugin/README.md)
- [Preview Runner module](../modules/viewcompose-preview-runner/README.md)
- [Preview Worker Host module](../modules/viewcompose-preview-worker-host/README.md)
- [Preview Integration module](../modules/viewcompose-preview/README.md)
