---
schema_version: 2
document_id: tutorial.navigation
doc_type: tutorial
owner:
  kind: capability
  id: navigation.host
version_lane: released
capability_ids:
  - navigation.host
artifact_ids:
  - viewcompose-navigation-android
  - viewcompose-navigation-core
  - viewcompose-material3-android
sample_ids:
  - tutorial.navigation
  - tutorial.navigation-dependencies
expected_result: A two-destination native View host that pushes and pops one committed back stack.
verification_action: Run the sample, open Details, and return with both the UI action and Android system Back.
---

# Use navigation

## Required dependencies

This page is standalone. The Android navigation artifact supplies the platform-neutral route model
transitively.

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="navigation-dependencies" sample_id="tutorial.navigation-dependencies" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-navigation-android:0.1.0-alpha01")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

## Navigate between two destinations

Create `NavigationTutorialActivity.kt`:

{/* tutorial-sample sample_id="tutorial.navigation" source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/NavigationTutorialActivity.kt" region="navigation" required_artifacts="viewcompose-navigation-android" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.material3.android.setMaterial3UiContent
import com.viewcompose.navigation.NavHost
import com.viewcompose.navigation.rememberNavHostController
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults

private const val HOME = "home"
private const val DETAILS = "details"

class NavigationTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setMaterial3UiContent {
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

The remembered controller owns the committed back stack. `NavHost` renders the current `NavRoute`
and connects Android system Back to the same stack. Call `navigate` or `popBackStack` from UI events
after the host is mounted.

## Verify the result

Press `Open details`, then return with the on-screen `Back` action. Open Details again and use
Android system Back. Both paths must return to Home exactly once. Compile with
`./gradlew :samples:tutorials:assembleDebug`.

For restoration, explicit failure handling, and predictive Back acceptance, continue with
[Configure a production navigation host](../guides/navigation.md).
