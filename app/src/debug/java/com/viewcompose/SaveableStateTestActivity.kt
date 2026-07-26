package com.viewcompose

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.viewcompose.host.android.setUiContent
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.rememberSaveable

class SaveableStateTestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUiContent {
            val count = rememberSaveable {
                mutableStateOf(0)
            }
            Column {
                Text(
                    text = count.value.toString(),
                    modifier = Modifier.testTag(COUNT_TAG),
                )
                Button(
                    text = "Increment",
                    onClick = {
                        count.value += 1
                    },
                    modifier = Modifier.testTag(INCREMENT_TAG),
                )
            }
        }
    }

    companion object {
        const val COUNT_TAG = "saveable-state-count"
        const val INCREMENT_TAG = "saveable-state-increment"
    }
}
