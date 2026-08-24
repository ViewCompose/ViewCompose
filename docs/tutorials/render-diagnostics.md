---
title: Read render diagnostics
sidebar_position: 14
---

# Read render diagnostics

## Required dependencies

This page is standalone. Host diagnostics and `RenderStats` are in the base application modules;
no optional diagnostics artifact is required:

```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha01")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

## Sample renderer counters on demand

Create `RenderDiagnosticsTutorialActivity.kt`:

{/* tutorial-sample source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/RenderDiagnosticsTutorialActivity.kt" region="render-diagnostics" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.material3.android.setMaterial3UiContent
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.RenderDiagnosticCollection
import com.viewcompose.ui.foundation.RenderDiagnostics
import com.viewcompose.ui.foundation.RenderFrameCompleted
import com.viewcompose.ui.foundation.RenderFrameDiagnosticLevel
import com.viewcompose.ui.foundation.RenderStats
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.remember
import java.util.concurrent.atomic.AtomicReference

class RenderDiagnosticsTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val latestStats = AtomicReference(RenderStats())
        val diagnostics = RenderDiagnostics(
            collection = RenderDiagnosticCollection(
                lifecycle = false,
                failures = false,
                frameLevel = RenderFrameDiagnosticLevel.Stats,
            ),
            sink = { event ->
                if (event is RenderFrameCompleted) {
                    event.stats?.let(latestStats::set)
                }
            },
        )
        setMaterial3UiContent(
            debug = true,
            debugTag = "RenderTutorial",
            diagnostics = diagnostics,
        ) {
            val summary = remember { mutableStateOf("No sample yet") }

            Column(
                spacing = 12.dp,
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) {
                Button(
                    "Sample render stats",
                    onClick = {
                        val stats = latestStats.get()
                        summary.value =
                            "${stats.inserts} inserts, ${stats.reuses} reuses, " +
                                "${stats.patchedNodes} patches"
                    },
                )
                Text(summary.value)
            }
        }
    }
}
```
{/* tutorial-sample-end */}

The diagnostics sink runs synchronously after authoritative frames. Store the immutable stats
snapshot outside composition and read it only from an explicit UI event. Writing every
`RenderFrameCompleted` event directly into observed UI state would create a render-observe-render
loop and distort the counters being measured.

## Count production failures without frame trees

Add the optional artifact when the application needs bounded recurring-failure counts:

```kotlin title="build.gradle.kts"
dependencies {
    implementation("com.viewcompose:viewcompose-diagnostics:0.1.0-alpha01")
}
```

Use one application-owned aggregator as a failure-only sink:

```kotlin
val aggregator = BoundedRenderFailureAggregator()
val failureDiagnostics = RenderDiagnostics(
    collection = RenderDiagnosticCollection(
        lifecycle = false,
        failures = true,
        frameLevel = RenderFrameDiagnosticLevel.None,
    ),
    sink = aggregator,
)

val completedWindow = aggregator.snapshotAndReset()
exportQueue.trySend(completedWindow)
```

Schedule snapshot/export outside sink delivery. The snapshot contains only bounded redacted
fingerprints and safe framework context; it contains no original `Throwable`, message, raw node
key, application stack, file, or line. The framework does not choose a scheduler, storage system,
consent model, upload endpoint, or telemetry vendor.

## Inspect the same app from Android Studio

Add `debugImplementation("com.viewcompose:viewcompose-preview:0.1.0-alpha04")`, run the debuggable
app, and choose **Inspect Device Diagnostics** in Android Studio. Select the Host Session once. Its
summary keeps the latest committed frame separate from a later rolled-back attempt and shows only a
safe failure phase, recovery, exception type, and optional Android View operation.

Use **Session sources** to return to this Activity, **Mounted nodes** to load and optionally
highlight a real View boundary, and **Finite timing** only while reproducing a bounded interaction.
Click **Refresh snapshot** after another render; the inspector never polls or records a continuous
history. If you need a deterministic timing target, the Demo `Diagnostics → Renderer` route exposes
a visible `0/8` to `8/8` workload.

## Verify the result

Press `Sample render stats` and confirm that a stable counter summary appears. If the optional
Preview artifact is present, also confirm the inspector shows the selected Session, source, and
latest committed frame without changing the running page. Debug diagnostics can add work, so use
release benchmarks for performance conclusions. Compile with:

```bash
./gradlew :samples:tutorials:assembleDebug
```

See the [Diagnostics guide](../tooling/diagnostics.md) and
[Diagnostics module manual](../modules/viewcompose-diagnostics/README.md) for failure hooks,
redaction, render traces, and logging policy.
