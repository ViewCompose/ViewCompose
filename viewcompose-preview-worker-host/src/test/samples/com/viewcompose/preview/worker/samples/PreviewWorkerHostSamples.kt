package com.viewcompose.preview.worker.samples

import com.viewcompose.preview.tooling.PreviewRenderResponse
import com.viewcompose.preview.worker.PreviewWorkerHost
import java.io.File

fun executeWorkerCommandSample(commandJsonFile: File): PreviewRenderResponse {
    return PreviewWorkerHost.execute(commandJsonFile)
}
