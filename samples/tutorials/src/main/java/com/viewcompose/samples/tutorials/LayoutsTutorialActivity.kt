// DOCS_REGION_START(layouts)
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.host.android.setUiContent
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Row
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextDefaults

class LayoutsTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUiContent {
            Column(
                spacing = 16.dp,
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) {
                Text("Account", style = TextDefaults.titleLargeStyle())
                Row(
                    spacing = 12.dp,
                    verticalAlignment = VerticalAlignment.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Ada", modifier = Modifier.weight(1f))
                    Button("Edit", onClick = {})
                }
            }
        }
    }
}
// DOCS_REGION_END(layouts)
