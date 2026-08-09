// DOCS_REGION_START(lazy-lists)
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.material3.android.setMaterial3UiContent
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.Text

class LazyListsTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setMaterial3UiContent {
            val messages = List(100) { index -> "Message #${index + 1}" }

            LazyColumn(
                items = messages,
                key = { message -> message },
                contentType = { "message" },
                spacing = 8.dp,
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) { message ->
                Text(message, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
// DOCS_REGION_END(lazy-lists)
