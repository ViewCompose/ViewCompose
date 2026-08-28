---
schema_version: 2
document_id: tutorial.gestures
doc_type: tutorial
owner:
  kind: capability
  id: gesture.modifiers
version_lane: released
capability_ids:
  - gesture.modifiers
artifact_ids:
  - viewcompose-gesture
  - viewcompose-material3-android
sample_ids:
  - tutorial.gestures
  - tutorial.gestures-dependencies
expected_result: A card that reports distinct tap and long-press gestures in its label.
verification_action: Run the sample, tap and long-press the card, and confirm that both labels appear.
---

# Use gestures

## Required dependencies

This page is standalone. Gesture modifiers require the separate `viewcompose-gesture` artifact:

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="gestures-dependencies" sample_id="tutorial.gestures-dependencies" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha02")
    implementation("com.viewcompose:viewcompose-gesture:0.1.0-alpha05")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

## Handle tap and long-press

Create `GesturesTutorialActivity.kt`:

{/* tutorial-sample sample_id="tutorial.gestures" source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/GesturesTutorialActivity.kt" region="gestures" required_artifacts="viewcompose-gesture" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.gesture.combinedClickable
import com.viewcompose.material3.android.setMaterial3UiContent
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

        setMaterial3UiContent {
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
