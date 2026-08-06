// DOCS_REGION_START(animation)
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.animation.AnimatedVisibility
import com.viewcompose.android.setUiContent
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.remember

class AnimationTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUiContent {
            val visible = remember { mutableStateOf(true) }

            Column(
                spacing = 12.dp,
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) {
                Button(
                    if (visible.value) "Hide message" else "Show message",
                    onClick = { visible.value = !visible.value },
                )
                AnimatedVisibility(visible = visible.value) {
                    Text("Animated content")
                }
            }
        }
    }
}
// DOCS_REGION_END(animation)
