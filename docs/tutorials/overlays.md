---
title: Use overlays
sidebar_position: 9
---

# Use overlays

## Required dependencies

This page is standalone. Dialog presentation requires the separate Android overlay host artifact:

```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-host-android:0.1.0-alpha03")
    implementation("com.viewcompose:viewcompose-overlay-android:0.1.0-alpha03")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

Without `viewcompose-overlay-android` and `overlayHostFactory`, the `Dialog` declaration has no
Android presenter.

## Show a confirmation dialog

Create `OverlaysTutorialActivity.kt`:

{/* tutorial-sample source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/OverlaysTutorialActivity.kt" region="overlays" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.host.android.setUiContent
import com.viewcompose.overlay.android.host.AndroidOverlayHost
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Dialog
import com.viewcompose.widget.core.Surface
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.remember

class OverlaysTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUiContent(overlayHostFactory = ::AndroidOverlayHost) {
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
