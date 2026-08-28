---
schema_version: 2
document_id: tutorial.lazy-list-performance
doc_type: tutorial
owner:
  kind: capability
  id: lazy.collections
version_lane: released
capability_ids:
  - lazy.collections
  - observed.value-mapping
artifact_ids:
  - viewcompose-material3-android
  - viewcompose-ui-foundation
  - viewcompose-ui-contract
sample_ids:
  - tutorial.lazy-list-performance
  - tutorial.lazy-collections-dependencies
expected_result: A keyed 500-row list with explicit bounded prefetch and mounted-tree reuse policies ready for same-device comparison against defaults.
verification_action: Profile the default and configured variants from the same release build on one device and keep the policy only when frame time improves within the memory budget.
---

# Tune lazy-list performance

## Required dependencies

This page is standalone. Collection policies are part of the base UI contract; no optional
performance artifact is required:

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="lazy-collections-dependencies" sample_id="tutorial.lazy-collections-dependencies" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha01")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

## Add measured collection hints

Create `LazyListPerformanceTutorialActivity.kt`:

{/* tutorial-sample sample_id="tutorial.lazy-list-performance" source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/LazyListPerformanceTutorialActivity.kt" region="lazy-list-performance" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.material3.android.setMaterial3UiContent
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.node.policy.CollectionReusePolicy
import com.viewcompose.ui.node.policy.LazyLayoutPrefetchPolicy
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.Text

class LazyListPerformanceTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setMaterial3UiContent {
            val rows = List(500) { index -> "Row #${index + 1}" }

            LazyColumn(
                items = rows,
                key = { row -> row },
                contentType = { "text-row" },
                contentRevision = { row -> row },
                prefetchPolicy = LazyLayoutPrefetchPolicy(
                    nestedInitialPrefetchItemCount = 4,
                    itemViewCacheSize = 4,
                ),
                reusePolicy = CollectionReusePolicy(
                    sharePool = true,
                    mountedTreeCacheSize = 2,
                ),
                modifier = Modifier.fillMaxSize().padding(16.dp),
            ) { row ->
                Text(row, modifier = Modifier.fillMaxWidth().padding(8.dp))
            }
        }
    }
}
```
{/* tutorial-sample-end */}

Prefetch and cache sizes are bounded renderer policies; they never define business state. Shared
pools retain only empty holder shells. The mounted-tree cache retains reset physical trees by
`contentType` and releases them deterministically on eviction. `contentRevision` does define item
semantics: when a captured non-State value changes, its revision must change too. Measure before
increasing either cache because larger values consume more memory.

If a stable parent recomposes often and selector scans appear in the profile, introduce a remembered
`LazyItemsSnapshot` at the application's immutable data boundary. It is not a general replacement
for an ordinary list: creating a new snapshot on every composition still performs the scan, while
retaining one after ordinary data or captures change is incorrect.

When the immutable submission itself changes frequently but the surrounding screen structure does
not, supply that snapshot through the observed `LazyColumn` overload. It patches the mounted list
without recomposing the parent and exposes each payload as an `ObservedValue`; derive changing text
or other leaf properties with `ObservedValue.map`. Keep item structure dependent only on the stable
key and stable captures. This path is transactional, so a failed native patch retains the previous
item table and observation dependencies for retry.

## Verify the result

Profile scrolling with the default values and with the explicit policy on the same release build
and device. Compile the sample with:

```bash
./gradlew :samples:tutorials:assembleDebug
```

Use the [Performance guide](../tooling/performance.md) for benchmark conditions and regression
budgets.
