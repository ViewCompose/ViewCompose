---
schema_version: 2
document_id: tutorial.android-view
doc_type: tutorial
owner:
  kind: capability
  id: host.android-view
version_lane: released
capability_ids:
  - host.android-view
artifact_ids:
  - viewcompose-host-android
  - viewcompose-material3-android
sample_ids:
  - tutorial.android-view-dependencies
  - tutorial.android-view
  - module.host-android-view-adapter
expected_result: A native TextView whose displayed count changes while its factory-assigned View ID remains stable.
verification_action: Run the sample, record the displayed View ID, press Increment, and verify that the count changes without changing the ID.
---

# Use AndroidView

## Required dependencies

This page is standalone. `AndroidView` is provided by `viewcompose-host-android`; no separate
interop artifact is required:

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="android-view-dependencies" sample_id="tutorial.android-view-dependencies" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha02")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

## Embed and update a TextView

Create `AndroidViewTutorialActivity.kt`:

{/* tutorial-sample sample_id="tutorial.android-view" source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/AndroidViewTutorialActivity.kt" region="android-view" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.viewcompose.host.android.AndroidView
import com.viewcompose.material3.android.setMaterial3UiContent
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.remember

class AndroidViewTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setMaterial3UiContent {
            val count = remember { mutableStateOf(0) }

            Column(
                spacing = 12.dp,
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) {
                AndroidView(
                    factory = { context ->
                        TextView(context).apply { id = View.generateViewId() }
                    },
                    update = { view ->
                        (view as TextView).text =
                            "Native TextView #${view.id} count: ${count.value}"
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button("Increment", onClick = { count.value += 1 })
            }
        }
    }
}
```
{/* tutorial-sample-end */}

`factory` creates the native View only when reconciliation needs a new node, so the generated View
ID gives the mounted instance a visible identity. `update` applies the latest observable state to
that retained View and must be safe to run again during rollback or rebind. Keep external one-shot
work out of `update`. This callback form is the concise low-level escape hatch.

## Extract a reusable typed adapter

Use `AndroidViewAdapter<V, S>` when the integration is reused or owns lifecycle callbacks. The View
type and complete state snapshot remain checked across every callback:

{/* compiled-region source="viewcompose-host-android/src/test/samples/com/viewcompose/host/android/samples/HostAndroidSamples.kt" region="host-android-view-adapter" sample_id="module.host-android-view-adapter" build_target=":viewcompose-host-android:compileDebugUnitTestKotlin" */}
```kotlin
fun typedAndroidViewAdapterSample(builder: UiTreeBuilder) {
    builder.AndroidView(
        adapter = NativeLabelAdapter,
        state = "Typed native label",
        key = "label",
        constructionKey = "default-text-appearance",
        modifier = Modifier.nativeView(key = "enabled") { view ->
            view.isEnabled = true
        },
    )
}

private object NativeLabelAdapter : AndroidViewAdapter<TextView, String> {
    override val reusePolicy: AndroidViewReusePolicy = AndroidViewReusePolicy.Resettable

    override fun create(scope: AndroidViewCreateScope): TextView = TextView(scope.context)

    override fun update(scope: AndroidViewUpdateScope<TextView>, state: String) {
        scope.view.text = state
    }

    override fun onReset(
        scope: AndroidViewResetScope<TextView>,
        reason: AndroidViewResetReason,
    ) {
        scope.view.text = null
    }
}
```

`key` identifies the logical item. The adapter implementation class plus `constructionKey`
identifies constructor-sensitive View state. A changed state reuses the View and calls only
`update`; a changed construction identity creates and binds a candidate, then replaces the old
View only if the complete transaction succeeds. `onReset` is reserved for integrations that opt
into cross-key mounted-tree reuse with `AndroidViewReusePolicy.Resettable`.

## Verify the result

Record the number after `Native TextView #`, press `Increment`, and confirm that the count changes
while that View ID remains the same. This distinguishes an update of the mounted instance from a
replacement. Compile with:

```bash
./gradlew :samples:tutorials:assembleDebug
```

See [Hosts, lifecycle, and Android interop](../migration/compose-host-lifecycle-and-android-interop.md)
for ownership and cleanup rules.
