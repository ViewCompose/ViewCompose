package com.viewcompose.samples.tasklist

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.host.android.setUiContent
import com.viewcompose.overlay.android.host.AndroidOverlayHost
import com.viewcompose.widget.core.RenderStats
import java.util.concurrent.atomic.AtomicReference

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val latestRenderStats = AtomicReference(RenderStats())
        setUiContent(
            debug = true,
            debugTag = "TaskListHost",
            overlayHostFactory = ::AndroidOverlayHost,
            onRenderStats = latestRenderStats::set,
        ) {
            TaskListCompleteScreen(latestRenderStats::get)
        }
    }
}
