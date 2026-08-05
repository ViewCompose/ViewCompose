package com.viewcompose.publishing.smoke.host

import androidx.activity.ComponentActivity
import com.viewcompose.host.android.setUiContent
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.remember
import com.viewcompose.widget.core.rememberCoroutineScope
import kotlinx.coroutines.isActive

/** Compiles the advertised base application surface from the host artifact alone. */
fun ComponentActivity.installViewComposeCounter() {
    setUiContent {
        val count = remember { mutableStateOf(0) }
        val compositionScope = rememberCoroutineScope()
        check(compositionScope.coroutineContext.isActive)
        Column(
            spacing = 16.dp,
            arrangement = MainAxisArrangement.Center,
            horizontalAlignment = HorizontalAlignment.Center,
            modifier = Modifier.fillMaxSize().padding(24.dp),
        ) {
            Text("Count: ${count.value}")
            Button(
                text = "Increment",
                onClick = { count.value += 1 },
            )
        }
    }
}
