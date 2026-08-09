// DOCS_REGION_START(layouts)
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.material3.android.setMaterial3UiContent
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults

class LayoutsTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setMaterial3UiContent {
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
