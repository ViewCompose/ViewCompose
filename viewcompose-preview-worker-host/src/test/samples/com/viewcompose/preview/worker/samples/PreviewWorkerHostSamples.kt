package com.viewcompose.preview.worker.samples

import com.viewcompose.preview.tooling.PreviewRenderResponse
import com.viewcompose.preview.worker.PreviewWorkerHost
import java.io.File

// DOCS_REGION_START(preview-worker-execute)
fun executeWorkerCommandSample(commandJsonFile: File): PreviewRenderResponse {
    return PreviewWorkerHost.execute(commandJsonFile)
}
// DOCS_REGION_END(preview-worker-execute)
