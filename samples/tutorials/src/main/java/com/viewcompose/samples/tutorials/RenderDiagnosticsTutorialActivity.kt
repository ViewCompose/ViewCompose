// DOCS_REGION_START(render-diagnostics)
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.host.android.setUiContent
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.RenderStats
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.remember
import java.util.concurrent.atomic.AtomicReference

class RenderDiagnosticsTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val latestStats = AtomicReference(RenderStats())
        setUiContent(
            debug = true,
            debugTag = "RenderTutorial",
            onRenderStats = latestStats::set,
        ) {
            val summary = remember { mutableStateOf("No sample yet") }

            Column(
                spacing = 12.dp,
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) {
                Button(
                    "Sample render stats",
                    onClick = {
                        val stats = latestStats.get()
                        summary.value =
                            "${stats.inserts} inserts, ${stats.reuses} reuses, " +
                                "${stats.patchedNodes} patches"
                    },
                )
                Text(summary.value)
            }
        }
    }
}
// DOCS_REGION_END(render-diagnostics)
