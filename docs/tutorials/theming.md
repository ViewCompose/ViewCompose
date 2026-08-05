---
title: Use themes
sidebar_position: 7
---

# Use themes

## Required dependencies

This page is standalone. The theme API is in `viewcompose-widget-core`; Android theme resolution is
installed by `viewcompose-host-android`:

```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-host-android:0.1.0-alpha03")
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

{/* tutorial-sample source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/ThemingTutorialActivity.kt" region="theming" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.host.android.setUiContent
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextDefaults
import com.viewcompose.widget.core.Theme

class ThemingTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUiContent {
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

`setUiContent` reads the Android theme and provides semantic ViewCompose tokens. Read
`Theme.colors` while building the UI instead of saving resolved color integers. The host refreshes
the tokens when the configuration changes, including light/dark mode.

## Verify the result

Switch the device between light and dark mode. Background, primary text, and the button should all
remain readable. Compile with:

```bash
./gradlew :samples:tutorials:assembleDebug
```

For custom tokens, dynamic-color policy, and runtime theme refresh, see the [Theming guide](../guides/theming.md).
