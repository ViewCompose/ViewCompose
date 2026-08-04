---
title: Use navigation
sidebar_position: 8
---

# Use navigation

## Required dependencies

This page is standalone. Navigation requires both the platform-neutral route model and the Android
navigation host:

```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-runtime:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-ui-contract:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-widget-core:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-host-android:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-navigation-core:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-navigation:0.1.0-alpha01")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

## Navigate between two destinations

Create `NavigationTutorialActivity.kt`:

{/* tutorial-sample source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/NavigationTutorialActivity.kt" region="navigation" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.host.android.setUiContent
import com.viewcompose.navigation.NavHost
import com.viewcompose.navigation.rememberNavHostController
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextDefaults

private const val HOME = "home"
private const val DETAILS = "details"

class NavigationTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUiContent {
            val controller = rememberNavHostController(NavRoute(HOME))

            NavHost(controller = controller) { entry ->
                Column(
                    spacing = 12.dp,
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                ) {
                    when (entry.route.name) {
                        HOME -> {
                            Text("Home", style = TextDefaults.titleLargeStyle())
                            Button(
                                "Open details",
                                onClick = { controller.navigate(NavRoute(DETAILS)) },
                            )
                        }
                        DETAILS -> {
                            Text("Details", style = TextDefaults.titleLargeStyle())
                            Button("Back", onClick = controller::popBackStack)
                        }
                        else -> error("Unknown route ${entry.route.name}")
                    }
                }
            }
        }
    }
}
```
{/* tutorial-sample-end */}

The remembered controller owns the back stack. `NavHost` renders the current `NavRoute` and
connects system Back to the same stack. Call `navigate` or `popBackStack` from UI events after the
host is mounted.

## Verify the result

Press `Open details`, then return with either `Back` or Android system Back. Compile with:

```bash
./gradlew :samples:tutorials:assembleDebug
```

For typed arguments, multiple stacks, SavedState, and predictive Back, see the
[Navigation guide](../guides/navigation.md).
