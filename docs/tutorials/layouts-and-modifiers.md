---
title: Use layouts and modifiers
sidebar_position: 4
---

# Use layouts and modifiers

## Required dependencies

This page is standalone. Add the Android host, which supplies the UI contract and widget APIs
transitively:

```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-host-android:0.1.0-alpha03")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

## Build a two-axis layout

Create `LayoutsTutorialActivity.kt`:

{/* tutorial-sample source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/LayoutsTutorialActivity.kt" region="layouts" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.host.android.setUiContent
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Row
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextDefaults

class LayoutsTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUiContent {
            Column(
                spacing = 16.dp,
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) {
                Text("Account", style = TextDefaults.titleLargeStyle())
                Row(
                    spacing = 12.dp,
                    verticalAlignment = VerticalAlignment.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Ada", modifier = Modifier.weight(1f))
                    Button("Edit", onClick = {})
                }
            }
        }
    }
}
```
{/* tutorial-sample-end */}

`Column` places its children vertically. The nested `Row` places the name and button horizontally.
`weight(1f)` gives the name the remaining row width. Modifier order is significant: the example
fills the screen before adding inner padding.

## Verify the result

The title appears above one row; `Ada` expands toward the `Edit` button. Compile with:

```bash
./gradlew :samples:tutorials:assembleDebug
```

See [Modifier architecture](../architecture/modifier.md) when you need ordering and renderer
details beyond this usage example.
