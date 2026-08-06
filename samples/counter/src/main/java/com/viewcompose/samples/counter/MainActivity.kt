package com.viewcompose.samples.counter

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.android.setUiContent
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.remember

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUiContent {
            CounterScreen()
        }
    }
}

internal fun UiTreeBuilder.CounterScreen() {
    val count = remember { mutableStateOf(0) }

    Column(
        spacing = 16.dp,
        arrangement = MainAxisArrangement.Center,
        horizontalAlignment = HorizontalAlignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(
            text = "Count: ${count.value}",
            style = TextDefaults.titleLargeStyle(),
        )
        Button(
            text = "Increment",
            onClick = { count.value += 1 },
        )
    }
}
