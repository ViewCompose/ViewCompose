// DOCS_REGION_START(state)
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.android.setUiContent
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.remember

class StateTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUiContent {
            val count = remember { mutableStateOf(0) }

            Column(
                spacing = 12.dp,
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) {
                Text("Count: ${count.value}", style = TextDefaults.titleLargeStyle())
                Button("Increment", onClick = { count.value += 1 })
            }
        }
    }
}
// DOCS_REGION_END(state)
