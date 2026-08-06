---
title: Use gestures
sidebar_position: 12
---

# Use gestures

## Required dependencies

This page is standalone. Gesture modifiers require the separate `viewcompose-gesture` artifact:

```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-android:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-gesture:0.1.0-alpha03")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

## Handle tap and long-press

Create `GesturesTutorialActivity.kt`:

{/* tutorial-sample source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/GesturesTutorialActivity.kt" region="gestures" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.gesture.combinedClickable
import com.viewcompose.android.setUiContent
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Surface
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.remember

class GesturesTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUiContent {
            val message = remember { mutableStateOf("Tap or long-press the card") }

            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { message.value = "Tapped" },
                            onLongClick = { message.value = "Long-pressed" },
                        ),
                ) {
                    Text(message.value, modifier = Modifier.padding(24.dp))
                }
            }
        }
    }
}
```
{/* tutorial-sample-end */}

`combinedClickable` lets one native target distinguish click and long-press without application
timers. The renderer owns touch slop, timing, cancellation, and callback ordering. Use a normal
`Button` when the element is an ordinary action with button semantics.

## Verify the result

Tap the card, then long-press it, and confirm that the label reports each gesture. Compile with:

```bash
./gradlew :samples:tutorials:assembleDebug
```

For drag, anchored drag, transform, raw pointer, and nested-scroll APIs, see the
[gesture module manual](../modules/viewcompose-gesture/README.md).
