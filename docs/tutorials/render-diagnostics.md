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
import com.viewcompose.ui.foundation.RenderStats
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.remember
import java.util.concurrent.atomic.AtomicReference

class RenderDiagnosticsTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val latestStats = AtomicReference(RenderStats())
        setMaterial3UiContent(
            debug = true,
            debugTag = "RenderTutorial",
            onRenderStats = latestStats::set,
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

`onRenderStats` runs after frames, so store its immutable snapshot outside composition. Read it
only from an explicit event. Writing every callback directly into observed UI state would create a
render-observe-render loop and distort the counters being measured.

## Verify the result

Press `Sample render stats` and confirm that a stable counter summary appears. Debug diagnostics
can add work, so use release benchmarks for performance conclusions. Compile with:

```bash
./gradlew :samples:tutorials:assembleDebug
```

See the [Diagnostics guide](../tooling/diagnostics.md) for failure hooks, render traces, and logging
policy.
