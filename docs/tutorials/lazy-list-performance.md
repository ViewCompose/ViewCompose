---
title: Tune lazy-list performance
sidebar_position: 13
---

# Tune lazy-list performance

## Required dependencies

This page is standalone. Collection policies are part of the base UI contract; no optional
performance artifact is required:

```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-host-android:0.1.0-alpha03")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

## Add measured collection hints

Create `LazyListPerformanceTutorialActivity.kt`:

{/* tutorial-sample source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/LazyListPerformanceTutorialActivity.kt" region="lazy-list-performance" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.host.android.setUiContent
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.node.policy.CollectionReusePolicy
import com.viewcompose.ui.node.policy.LazyLayoutPrefetchPolicy
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.LazyColumn
import com.viewcompose.widget.core.Text

class LazyListPerformanceTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUiContent {
            val rows = List(500) { index -> "Row #${index + 1}" }

            LazyColumn(
                items = rows,
                key = { row -> row },
                contentType = { "text-row" },
                prefetchPolicy = LazyLayoutPrefetchPolicy(
                    initialPrefetchItemCount = 4,
                    itemViewCacheSize = 4,
                ),
                reusePolicy = CollectionReusePolicy(sharePool = true),
                modifier = Modifier.fillMaxSize().padding(16.dp),
            ) { row ->
                Text(row, modifier = Modifier.fillMaxWidth().padding(8.dp))
            }
        }
    }
}
```
{/* tutorial-sample-end */}

Prefetch and cache sizes are hints to the native collection renderer; they never change item
semantics. Shared pools are useful only for compatible list structures, and reused rows must still
be fully rebound. Measure before increasing either value because larger caches consume more memory.

## Verify the result

Profile scrolling with the default values and with the explicit policy on the same release build
and device. Compile the sample with:

```bash
./gradlew :samples:tutorials:assembleDebug
```

Use the [Performance guide](../tooling/performance.md) for benchmark conditions and regression
budgets.
