// DOCS_REGION_START(text-input)
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.host.android.setUiContent
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextField
import com.viewcompose.widget.core.rememberTextFieldState

class TextInputTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUiContent {
            val name = rememberTextFieldState()

            Column(
                spacing = 12.dp,
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) {
                TextField(
                    state = name,
                    hint = "Your name",
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(if (name.text.isBlank()) "Hello" else "Hello, ${name.text}")
            }
        }
    }
}
// DOCS_REGION_END(text-input)
