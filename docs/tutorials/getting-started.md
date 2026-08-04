---
title: Build your first application
sidebar_position: 1
---

# Build your first ViewCompose application

This tutorial builds a runnable counter backed by Android Views. Pressing the button updates
snapshot state, invalidates the observed UI, and patches the existing native View tree.

The complete compiled application lives in
[`samples/counter`](https://github.com/ViewCompose/ViewCompose/tree/main/samples/counter). The code below is copied from that module.
`qaQuick` compiles the application, its device test, and the debug-only preview entry; `qaPreview`
also verifies that preview discovery stays connected to the compiled function.

## Required dependencies

Make sure the application resolves Maven Central, then add the complete runtime dependency set:

```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-runtime:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-ui-contract:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-widget-core:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-host-android:0.1.0-alpha01")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

The counter runs without preview tooling. To follow the optional preview section, also add the
published plugin and its debug-only artifacts now:

```kotlin title="build.gradle.kts (optional preview)"
plugins {
    id("com.viewcompose.preview") version "0.1.0-alpha01"
}

dependencies {
    debugImplementation("com.viewcompose:viewcompose-preview-core:0.1.0-alpha01")
    add(
        "viewComposePreviewWorkerHost",
        "com.viewcompose:viewcompose-preview-worker-host:0.1.0-alpha01",
    )
    add(
        "viewComposePreviewRunner",
        "com.viewcompose:viewcompose-preview-runner:0.1.0-alpha01",
    )
}
```

## What you will build

The application contains one Activity and one declarative tree:

- a centered text label showing `Count: 0`;
- an `Increment` button;
- retained snapshot state that drives the label;
- an Android host that owns lifecycle, SavedState, theme, and rendering services.
- light and dark static previews of the same `CounterScreen` used by the Activity.

Expected result: every press increments the visible count without replacing the Activity.

## Prerequisites and verified baseline

You need an Android application using Kotlin, an Android SDK, and JDK 17 for the Android Gradle
Plugin. The sample uses `compileSdk = 36`, `minSdk = 24`, and JVM target 11.

This tutorial was last verified on 2026-08-03 with these ViewCompose artifacts:

| Artifact | Version | Why it is explicit |
| --- | --- | --- |
| `viewcompose-runtime` | `0.1.0-alpha01` | Snapshot state and observation |
| `viewcompose-ui-contract` | `0.1.0-alpha01` | `Modifier`, layout units, and alignment contracts |
| `viewcompose-widget-core` | `0.1.0-alpha01` | `Column`, `Text`, `Button`, theme defaults, and `remember` |
| `viewcompose-host-android` | `0.1.0-alpha01` | Activity host and Android renderer installation |
| `viewcompose-preview-core` | `0.1.0-alpha01` | Debug-only static-preview annotation and configuration |
| `viewcompose-preview-gradle-plugin` | `0.1.0-alpha01` | Compiled preview discovery and isolated rendering tasks |

ViewCompose artifacts evolve independently. Check the
[published module catalog](../modules/README.md) before mixing versions newer than this verified
set.

The repository sample uses these exact Maven coordinates, so its quality gate verifies the same
dependency path used by an external application.

## 1. Use a Material application theme

The host resolves ViewCompose tokens from the Android theme. A new View-based Android Studio
project normally already has a suitable Material theme. The counter sample uses:

```xml title="res/values/themes.xml"
<resources>
    <style name="Theme.ViewCompose.Counter" parent="Theme.Material3.DayNight.NoActionBar" />
</resources>
```

Apply that theme to the application or Activity in `AndroidManifest.xml`. ViewCompose will follow
the host's light/dark configuration and Android theme bridge; no Compose theme is involved.

## 2. Install the declarative content

Replace the generated Activity content with the compiled
[`MainActivity.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/samples/counter/src/main/java/com/viewcompose/samples/counter/MainActivity.kt):

```kotlin
package com.example.counter

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.host.android.setUiContent
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextDefaults
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.widget.core.remember

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUiContent {
            CounterScreen()
        }
    }
}

internal fun UiTreeBuilder.CounterScreen() {
    val count = remember { mutableStateOf(0) }

    Column(
        spacing = 16.dp,
        arrangement = MainAxisArrangement.Center,
        horizontalAlignment = HorizontalAlignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(
            text = "Count: ${count.value}",
            style = TextDefaults.titleLargeStyle(),
        )
        Button(
            text = "Increment",
            onClick = { count.value += 1 },
        )
    }
}
```

Four pieces form the complete update path:

1. `setUiContent` installs a lifecycle-aware Android host and performs the first render.
2. `remember` retains the state object at its composition position.
3. reading `count.value` subscribes this composition scope to state invalidation.
4. the button writes a new value; ViewCompose recomposes the affected scope and patches the native
   `TextView` rather than recreating the Activity.

`remember` retains values while this composition survives. Use `rememberSaveable` when the value
must also survive Activity recreation or process restoration; see
[Lifecycle and SavedState](../architecture/lifecycle-and-saved-state.md).

## 3. Preview the compiled screen

Keep the optional preview dependencies listed at the top on the debug path. The repository sample
and an external application both use the published plugin artifacts.

The sample's debug source set exposes the same `CounterScreen` through a public static-preview
entry point:

```kotlin title="CounterPreview.kt"
package com.viewcompose.samples.counter

import com.viewcompose.preview.tooling.PreviewTheme
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.widget.core.UiTreeBuilder

/**
 * Renders the initial counter state through the native static-preview toolchain.
 *
 * @receiver DSL tree builder supplied by the static preview runner.
 */
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

Open `CounterPreview.kt` with the ViewCompose Studio plugin to inspect both variants. The native
static runner uses the compiled DSL function, so the Activity and preview cannot drift into two
separate screen implementations. Verify discovery locally with:

```bash
./gradlew :samples:counter:verifyCounterPreview
./gradlew qaPreview
```

## 4. Run and verify

Run the application from Android Studio, or build the repository sample from the command line:

```bash
./gradlew :samples:counter:assembleDebug
```

With an emulator or device connected, install the sample and run its click regression:

```bash
./gradlew :samples:counter:installDebug
./gradlew :samples:counter:connectedDebugAndroidTest
```

The test asserts `Count: 0`, presses `Increment`, and then asserts `Count: 1` against the real
Android View hierarchy.

## Where to go next

- Read [State snapshots](../architecture/state-snapshots.md) for transaction and observation rules.
- Read [Theming](../guides/theming.md) before defining application tokens or dynamic color policy.
- Read [Preview tooling](../tooling/preview.md) for theme providers, diagnostics, and snapshot policy.
- Use the [module catalog](../modules/README.md) to add navigation, text editing, graphics, or other
  optional capabilities without pulling them into the minimal application.
