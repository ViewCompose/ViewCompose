---
schema_version: 2
document_id: tutorial.overlays
doc_type: tutorial
owner:
  kind: capability
  id: overlay.foundation
version_lane: released
capability_ids:
  - overlay.foundation
  - overlay.android-transport
  - overlay.material3
artifact_ids:
  - viewcompose-ui-foundation
  - viewcompose-overlay-android
  - viewcompose-overlay-material3-android
  - viewcompose-material3-android
sample_ids:
  - tutorial.overlays-dependencies
  - tutorial.overlays
expected_result: A caller-controlled confirmation dialog presented by the root-scoped Material 3 overlay host.
verification_action: Open and dismiss the dialog with its action, Android Back, and an outside press, confirming each path clears the same state.
---

# Use overlays

## Required dependencies

This page is standalone. Dialog presentation requires the separate Android overlay host artifact:

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="overlays-dependencies" sample_id="tutorial.overlays-dependencies" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha02")
    implementation("com.viewcompose:viewcompose-overlay-material3-android:0.1.0-alpha02")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

Without `viewcompose-overlay-material3-android` and `overlayHostFactory`, the `Dialog` declaration has no
Android presenter.

## Show a confirmation dialog

Create `OverlaysTutorialActivity.kt`:

{/* tutorial-sample sample_id="tutorial.overlays" source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/OverlaysTutorialActivity.kt" region="overlays" required_artifacts="viewcompose-overlay-material3-android" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.material3.android.setMaterial3UiContent
import com.viewcompose.overlay.material3.android.host.AndroidOverlayHost
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Dialog
import com.viewcompose.ui.foundation.Surface
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.remember

class OverlaysTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setMaterial3UiContent(overlayHostFactory = ::AndroidOverlayHost) {
            val dialogVisible = remember { mutableStateOf(false) }

            Button("Delete item", onClick = { dialogVisible.value = true })
            Dialog(
                visible = dialogVisible.value,
                requestKey = "delete-item",
                onDismissRequest = { dialogVisible.value = false },
            ) {
                Surface(modifier = Modifier.fillMaxWidth()) {
                    Column(spacing = 12.dp, modifier = Modifier.padding(20.dp)) {
                        Text("Delete this item?")
                        Button("Cancel", onClick = { dialogVisible.value = false })
                    }
                }
            }
        }
    }
}
```
{/* tutorial-sample-end */}

The Boolean is application state; `Dialog` only declares how that state is presented.
`requestKey` gives this request stable identity across recompositions. Both the cancel action and a
platform dismissal update the same state owner.

## Verify the result

Press `Delete item`, then close the dialog with `Cancel`, Back, or an outside press. Compile with:

```bash
./gradlew :samples:tutorials:assembleDebug
```

For popups, bottom sheets, Snackbar, Toast, and queue behavior, see the
[Overlay guide](../guides/overlays.md).
