---
title: Use AndroidView
sidebar_position: 10
---

# Use AndroidView

## Required dependencies

This page is standalone. `AndroidView` is provided by `viewcompose-host-android`; no separate
interop artifact is required:

```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-android:0.1.0-alpha01")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

## Embed and update a TextView

Create `AndroidViewTutorialActivity.kt`:

{/* tutorial-sample source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/AndroidViewTutorialActivity.kt" region="android-view" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.viewcompose.host.android.AndroidView
import com.viewcompose.android.setUiContent
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.remember

class AndroidViewTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUiContent {
            val count = remember { mutableStateOf(0) }

            Column(
                spacing = 12.dp,
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) {
                AndroidView(
                    factory = { context -> TextView(context) },
                    update = { view ->
                        (view as TextView).text = "Native TextView count: ${count.value}"
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button("Increment", onClick = { count.value += 1 })
            }
        }
    }
}
```
{/* tutorial-sample-end */}

`factory` creates the native View only when reconciliation needs a new node. `update` applies the
latest observable state to a retained View and must be safe to run again during rollback or
rebind. Keep external one-shot work out of `update`.

## Verify the result

Press `Increment` and confirm that the mounted native `TextView` changes. Compile with:

```bash
./gradlew :samples:tutorials:assembleDebug
```

See [Hosts, lifecycle, and Android interop](../migration/compose-host-lifecycle-and-android-interop.md)
for ownership and cleanup rules.
