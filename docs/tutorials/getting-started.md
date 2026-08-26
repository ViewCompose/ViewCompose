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

Make sure the application resolves Maven Central, then add the named Material Android aggregate:

```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha01")
}
```

The aggregate exposes runtime, UI-contract, UI Foundation, host, Material 3 theme, Lifecycle, and
ViewModel APIs transitively. Their advanced APIs may still be addressed through a deliberate direct
dependency. Add a lower-level coordinate directly only when building an integration without the
aggregate.

The counter runs without preview tooling. To follow the optional preview section, also add the
published plugin and its debug-only artifacts now:

```kotlin title="build.gradle.kts (optional preview)"
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

These preview entries configure the native render pipeline but do not install its Android Studio
interface. Before opening the preview, go to **Settings | Plugins | Marketplace**, search for
`ViewCompose Preview`, and install it. Restart Android Studio if prompted. The IDE plugin is a
separate installation from `id("com.viewcompose.preview")`; the current Marketplace line is `1.1.0`
for Android Studio build family `261.*`.

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

This hard-cut dependency set was verified on 2026-08-06 through the repository-generated local
Maven repository. It becomes a public installation path after the listed new coordinates are
released to Maven Central:

| Artifact | Version | How it is supplied |
| --- | --- | --- |
| `viewcompose-material3-android` | `0.1.0-alpha01` | Explicit application dependency |
| `viewcompose-android` | `0.1.0-alpha01` | Transitive neutral application aggregate |
| `viewcompose-host-android` | `0.1.0-alpha03` | Transitive low-level engine dependency |
| `viewcompose-runtime` | `0.1.0-alpha02` | Transitive foundation dependency |
| `viewcompose-ui-contract` | `0.1.0-alpha03` | Transitive foundation dependency |
| `viewcompose-ui-foundation` | `0.1.0-alpha01` | Transitive UI Foundation dependency |
| `viewcompose-material3` | `0.1.0-alpha01` | Transitive design-system dependency |
| `viewcompose-lifecycle-androidx` | `0.1.0-alpha01` | Transitive AndroidX integration |
| `viewcompose-viewmodel-androidx` | `0.1.0-alpha01` | Transitive AndroidX integration |
| `viewcompose-preview-gradle-plugin` | `0.1.0-alpha03` | Optional explicit plugin |
| `viewcompose-preview-core` | `0.1.0-alpha03` | Optional debug dependency |
| `viewcompose-preview-worker-host` | `0.1.0-alpha03` | Optional preview configuration |
| `viewcompose-preview-runner` | `0.1.0-alpha04` | Optional preview configuration |

ViewCompose artifacts evolve independently. Check the
[published module catalog](../modules/README.md) before mixing versions outside this verified set.

The repository sample uses these exact Maven coordinates. `qaQuick` first publishes the current
checkout to `build/maven-repository`, then verifies the same generated POM path used by an external
application.

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
import com.viewcompose.material3.android.setMaterial3UiContent
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.remember

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setMaterial3UiContent {
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

1. `setMaterial3UiContent` installs a lifecycle-aware Android host and performs the first render.
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
import com.viewcompose.ui.foundation.UiTreeBuilder

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

After installing `ViewCompose Preview` from the Android Studio Marketplace, open
`CounterPreview.kt` and click its preview gutter icon, or open the `ViewCompose Preview` tool
window, to inspect both variants. The native static runner uses the compiled DSL function, so the
Activity and preview cannot drift into two separate screen implementations. Verify discovery
locally with:

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
- Read the [theme architecture](../architecture/theming.md) before defining application tokens, and
  use the [dynamic-color guide](../guides/theming-dynamic-color.md) for Android resource policy.
- Read [Preview tooling](../tooling/preview.md) for theme providers, diagnostics, and snapshot policy.
- Use the [module catalog](../modules/README.md) to add navigation, text editing, graphics, or other
  optional capabilities without pulling them into the minimal application.
