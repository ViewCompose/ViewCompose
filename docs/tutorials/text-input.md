---
title: Use text input
sidebar_position: 5
---

# Use text input

## Required dependencies

This page is standalone. The Android host supplies text editing and the base application APIs
transitively, so no separate `viewcompose-text-core` dependency is needed:

```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha01")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

## Bind a field to editable state

Create `TextInputTutorialActivity.kt`:

{/* tutorial-sample sample_id="tutorial.text-input" source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TextInputTutorialActivity.kt" region="text-input" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.material3.android.setMaterial3UiContent
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextField
import com.viewcompose.ui.foundation.rememberTextFieldState

class TextInputTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setMaterial3UiContent {
            val name = rememberTextFieldState()

            Column(
                spacing = 12.dp,
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) {
                TextField(
                    state = name,
                    hint = "Your name",
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(if (name.text.isBlank()) "Hello" else "Hello, ${name.text}")
            }
        }
    }
}
```
{/* tutorial-sample-end */}

`rememberTextFieldState` owns the text, selection, IME composition, and editing history. The native
editor updates that state; reading `name.text` updates the greeting without a separate string
callback.

## Verify the result

Type a name and confirm that the greeting changes on every edit. Compile with:

```bash
./gradlew :samples:tutorials:assembleDebug
```

For validation, rich text, undo, and Receive Content, use the [Text input guide](../guides/text-input.md).
