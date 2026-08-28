// DOCS_REGION_START(android-view)
package com.viewcompose.samples.tutorials

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.viewcompose.host.android.AndroidView
import com.viewcompose.material3.android.setMaterial3UiContent
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.remember

class AndroidViewTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setMaterial3UiContent {
            val count = remember { mutableStateOf(0) }
            val largeText = remember { mutableStateOf(false) }

            Column(
                spacing = 12.dp,
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) {
                AndroidView(
                    factory = { context ->
                        TextView(context).apply {
                            id = View.generateViewId()
                            textSize = if (largeText.value) 20f else 14f
                        }
                    },
                    update = { view ->
                        (view as TextView).text =
                            "Native TextView #${view.id} count: ${count.value}"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    constructionKey = largeText.value,
                )
                Button("Increment", onClick = { count.value += 1 })
                Button(
                    if (largeText.value) "Use compact native text" else "Use large native text",
                    onClick = { largeText.value = !largeText.value },
                )
            }
        }
    }
}
// DOCS_REGION_END(android-view)
