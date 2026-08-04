// DOCS_REGION_START(theming)
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
// DOCS_REGION_END(theming)
