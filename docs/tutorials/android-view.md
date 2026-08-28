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
expected_result: A native TextView whose ID survives state updates and changes only when the sample's factory-owned text sizing changes.
verification_action: Record the View ID, press Increment to retain it, then toggle the factory-owned native text size and verify that a new ID replaces it.
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
            val largeText = remember { mutableStateOf(false) }

            Column(
                spacing = 12.dp,
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) {
                AndroidView(
                    factory = { context ->
                        TextView(context).apply {
                            id = View.generateViewId()
                            textSize = if (largeText.value) 20f else 14f
                        }
                    },
                    update = { view ->
                        (view as TextView).text =
                            "Native TextView #${view.id} count: ${count.value}"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    constructionKey = largeText.value,
                )
                Button("Increment", onClick = { count.value += 1 })
                Button(
                    if (largeText.value) "Use compact native text" else "Use large native text",
                    onClick = { largeText.value = !largeText.value },
                )
            }
        }
    }
}
```
{/* tutorial-sample-end */}

`factory` creates the native View only when reconciliation needs a new construction identity, so
the generated View ID makes replacement visible. `update` applies the latest count to the retained
View and must be safe to run again during rollback or rebind. This sample intentionally treats the
text-size choice as factory-owned construction configuration and therefore also passes it as
`constructionKey`: changing the count retains the View, while changing that key creates and
atomically replaces it. Keep external one-shot work out of `update`. This callback form is the
concise low-level escape hatch.

## Extract a reusable typed adapter

Use `AndroidViewAdapter<V, S>` when the integration is reused or owns lifecycle callbacks. The View
type and complete state snapshot remain checked across every callback:

{/* compiled-region source="viewcompose-host-android/src/test/samples/com/viewcompose/host/android/samples/HostAndroidSamples.kt" region="host-android-view-adapter" sample_id="module.host-android-view-adapter" build_target=":viewcompose-host-android:compileDebugUnitTestKotlin" */}
```kotlin
fun typedAndroidViewAdapterSample(builder: UiTreeBuilder) {
    builder.AndroidView(
        adapter = NativeLabelAdapter,
        state = NativeLabelState(
            text = "Typed native label",
            enabled = true,
        ),
        key = "label",
        constructionKey = "default-text-appearance",
    )
}

private data class NativeLabelState(
    val text: String,
    val enabled: Boolean,
)

private object NativeLabelAdapter : AndroidViewAdapter<TextView, NativeLabelState> {
    override val reusePolicy: AndroidViewReusePolicy = AndroidViewReusePolicy.Resettable

    override fun create(scope: AndroidViewCreateScope): TextView = TextView(scope.context)

    override fun update(scope: AndroidViewUpdateScope<TextView>, state: NativeLabelState) {
        scope.view.text = state.text
        scope.view.isEnabled = state.enabled
    }

    override fun onReset(
        scope: AndroidViewResetScope<TextView>,
        reason: AndroidViewResetReason,
    ) {
        scope.view.text = null
        scope.view.isEnabled = false
    }
}
```

`NativeLabelState` keeps the adapter-owned text and enabled properties in one immutable snapshot,
so `update` can replay the complete configuration without an untyped side channel.
`key` identifies the logical item. The adapter implementation class plus `constructionKey`
identifies constructor-sensitive View state. A changed state reuses the View and calls only
`update`; a changed construction identity creates and binds a candidate, then replaces the old
View only if the complete transaction succeeds. `onReset` is reserved for integrations that opt
into cross-key mounted-tree reuse with `AndroidViewReusePolicy.Resettable`.

## Verify the result

Record the number after `Native TextView #`. Press `Increment` and confirm that the count changes
while the ID remains the same. Then press the native text-size button and confirm that the text
size and ID both change; the construction identity changed, so replacement is expected. Compile
with:

```bash
./gradlew :samples:tutorials:assembleDebug
```

See [Hosts, lifecycle, and Android interop](../migration/compose-host-lifecycle-and-android-interop.md)
for ownership and cleanup rules.
