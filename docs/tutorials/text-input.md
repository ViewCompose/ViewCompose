---
title: Use text input
sidebar_position: 5
---

# Use text input

## Required dependencies

This page is standalone. Text editing requires the separate `viewcompose-text-core` artifact in
addition to the base application modules:

```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-runtime:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-text-core:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-ui-contract:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-widget-core:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-host-android:0.1.0-alpha01")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

## Bind a field to editable state

Create `TextInputTutorialActivity.kt`:

{/* tutorial-sample source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TextInputTutorialActivity.kt" region="text-input" */}
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
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextField
import com.viewcompose.widget.core.rememberTextFieldState

class TextInputTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUiContent {
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
