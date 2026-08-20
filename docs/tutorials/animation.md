---
title: Use AnimatedVisibility
sidebar_position: 11
---

# Use AnimatedVisibility

## Required dependencies

This page is standalone. Visibility animation requires the separate `viewcompose-animation`
artifact:

```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-animation:0.1.0-alpha04")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

## Animate content visibility

Create `AnimationTutorialActivity.kt`:

{/* tutorial-sample source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/AnimationTutorialActivity.kt" region="animation" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.animation.AnimatedVisibility
import com.viewcompose.material3.android.setMaterial3UiContent
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.remember

class AnimationTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setMaterial3UiContent {
            val visible = remember { mutableStateOf(true) }

            Column(
                spacing = 12.dp,
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) {
                Button(
                    if (visible.value) "Hide message" else "Show message",
                    onClick = { visible.value = !visible.value },
                )
                AnimatedVisibility(visible = visible.value) {
                    Text("Animated content")
                }
            }
        }
    }
}
```
{/* tutorial-sample-end */}

Changing `visible` starts the default fade and size transition. Content stays mounted until an exit
finishes, then is removed. The first composition is already settled, so it does not play an enter
animation.

## Verify the result

Toggle the button repeatedly and confirm that interrupted motion continues from its current state.
Compile with:

```bash
./gradlew :samples:tutorials:assembleDebug
```

Use the [animation module manual](../modules/viewcompose-animation/README.md) for custom transition
primitives and retained transition state.
