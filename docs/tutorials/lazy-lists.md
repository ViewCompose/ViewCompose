---
schema_version: 2
document_id: tutorial.lazy-lists
doc_type: tutorial
owner:
  kind: capability
  id: lazy.collections
version_lane: released
capability_ids:
  - lazy.collections
artifact_ids:
  - viewcompose-material3-android
  - viewcompose-ui-foundation
sample_ids:
  - tutorial.lazy-lists
  - tutorial.lazy-collections-dependencies
expected_result: A native-backed keyed list that scrolls through 100 messages while preserving logical row identity.
verification_action: Run the sample, scroll from the first to the last message, then insert or move a keyed message and confirm row state follows its key.
---

# Use a lazy list

## Required dependencies

This page is standalone. `LazyColumn` is part of `viewcompose-ui-foundation`; no optional collection
artifact is required:

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="lazy-collections-dependencies" sample_id="tutorial.lazy-collections-dependencies" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha01")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

## Show a keyed collection

Create `LazyListsTutorialActivity.kt`:

{/* tutorial-sample sample_id="tutorial.lazy-lists" source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/LazyListsTutorialActivity.kt" region="lazy-lists" */}
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
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.Text

class LazyListsTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setMaterial3UiContent {
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
default prefetch and cache hints need adjustment. Use the
[lazy collections guide](../guides/lazy-collections.md) for state control, adaptive grids, and
pagers.
