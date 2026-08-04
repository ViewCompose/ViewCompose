---
title: Use a lazy list
sidebar_position: 6
---

# Use a lazy list

## Required dependencies

This page is standalone. `LazyColumn` is part of `viewcompose-widget-core`; no optional collection
artifact is required:

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

## Show a keyed collection

Create `LazyListsTutorialActivity.kt`:

{/* tutorial-sample source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/LazyListsTutorialActivity.kt" region="lazy-lists" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.host.android.setUiContent
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.LazyColumn
import com.viewcompose.widget.core.Text

class LazyListsTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUiContent {
            val messages = List(100) { index -> "Message #${index + 1}" }

            LazyColumn(
                items = messages,
                key = { message -> message },
                contentType = { "message" },
                spacing = 8.dp,
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) { message ->
                Text(message, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
```
{/* tutorial-sample-end */}

`LazyColumn` creates and reuses rows around the visible viewport. `key` must remain stable for the
same logical item after insertions or moves. `contentType` groups rows that can safely reuse the
same native structure.

## Verify the result

Scroll from `Message #1` toward `Message #100` and confirm that the Activity remains responsive.
Compile with:

```bash
./gradlew :samples:tutorials:assembleDebug
```

Use [Tune lazy-list performance](./lazy-list-performance.md) only when measurements show that the
default prefetch and cache hints need adjustment.
