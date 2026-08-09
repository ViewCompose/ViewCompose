// DOCS_REGION_START(gestures)
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.gesture.combinedClickable
import com.viewcompose.material3.android.setMaterial3UiContent
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Surface
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.remember

class GesturesTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setMaterial3UiContent {
            val message = remember { mutableStateOf("Tap or long-press the card") }

            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { message.value = "Tapped" },
                            onLongClick = { message.value = "Long-pressed" },
                        ),
                ) {
                    Text(message.value, modifier = Modifier.padding(24.dp))
                }
            }
        }
    }
}
// DOCS_REGION_END(gestures)
