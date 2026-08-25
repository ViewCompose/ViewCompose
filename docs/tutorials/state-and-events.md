---
title: Use state and events
sidebar_position: 3
---

# Use state and events

## Required dependencies

This page is standalone. Add Maven Central and the Android host before copying the example. The
host supplies runtime state and widget APIs transitively:

```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha01")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

## Build a counter

Create `StateTutorialActivity.kt`. The file owns its state, UI, and Android host, so it does not
depend on another tutorial file.

{/* tutorial-sample sample_id="tutorial.state-and-events" source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/StateTutorialActivity.kt" region="state" */}
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
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.remember

class StateTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setMaterial3UiContent {
            val count = remember { mutableStateOf(0) }

            Column(
                spacing = 12.dp,
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) {
                Text("Count: ${count.value}", style = TextDefaults.titleLargeStyle())
                Button("Increment", onClick = { count.value += 1 })
            }
        }
    }
}
```
{/* tutorial-sample-end */}

Register the Activity in `AndroidManifest.xml` and run it. `remember` keeps the state object while
the composition is alive. Reading `count.value` subscribes the UI; changing it in `onClick`
rebuilds the affected description and patches the existing native text View.

## Verify the result

The screen starts at `Count: 0`. Each press of `Increment` increases the number. Compile the exact
repository example with:

```bash
./gradlew :samples:tutorials:assembleDebug
```

For state that must survive Activity recreation, continue with
[Lifecycle and SavedState](../architecture/lifecycle-and-saved-state.md).
