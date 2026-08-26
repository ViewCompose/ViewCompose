---
schema_version: 2
document_id: tutorial.theming
doc_type: tutorial
owner:
  kind: capability
  id: theme.material3
version_lane: released
capability_ids:
  - theme.material3
artifact_ids:
  - viewcompose-material3-android
  - viewcompose-material3
  - viewcompose-ui-foundation
sample_ids:
  - tutorial.theming
  - tutorial.theming-dependencies
expected_result: A Material-hosted screen whose semantic background, text, and control colors follow Android light and dark configuration.
verification_action: Run the sample and switch the device between light and dark modes while the Activity is active.
---

# Use themes

## Required dependencies

This page is standalone. The Material Android aggregate supplies UI Foundation and the Material
token adapter transitively.

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="theming-dependencies" sample_id="tutorial.theming-dependencies" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha01")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

Use a Material DayNight theme on the application or Activity:

```xml title="res/values/themes.xml"
<style name="Theme.Example" parent="Theme.Material3.DayNight.NoActionBar" />
```

## Read semantic colors

Create `ThemingTutorialActivity.kt`:

{/* tutorial-sample sample_id="tutorial.theming" source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/ThemingTutorialActivity.kt" region="theming" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.material3.android.setMaterial3UiContent
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.Theme

class ThemingTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setMaterial3UiContent {
            Column(
                spacing = 12.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .backgroundColor(Theme.colors.background)
                    .padding(24.dp),
            ) {
                Text(
                    "Theme-aware screen",
                    color = Theme.colors.primary,
                    style = TextDefaults.titleLargeStyle(),
                )
                Text("Change the device theme to see the semantic colors update.")
                Button("Theme-aware button", onClick = {})
            }
        }
    }
}
```
{/* tutorial-sample-end */}

`setMaterial3UiContent` resolves one Android Material Context for the native tree and provides its
semantic ViewCompose tokens. Read `Theme.colors` while building the UI instead of retaining
resolved color integers. The host refreshes the tokens after light/dark configuration changes.

## Verify the result

Switch the device between light and dark mode. Background, primary text, and the button must remain
readable and change as one coherent snapshot. Compile with
`./gradlew :samples:tutorials:assembleDebug`.

Continue with the focused tasks for [application mode switching](../guides/theming.md),
[dynamic color and resource refresh](../guides/theming-dynamic-color.md), or
[local subtree overrides](../guides/theming-local-overrides.md). The long-lived token and
precedence model is in the [theme architecture](../architecture/theming.md).
