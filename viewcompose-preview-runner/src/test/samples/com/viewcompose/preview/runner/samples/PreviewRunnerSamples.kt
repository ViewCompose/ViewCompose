package com.viewcompose.preview.runner.samples

import android.content.Context
import com.viewcompose.preview.runner.PreviewEntryResolutionResult
import com.viewcompose.preview.runner.PreviewJvmEntryPointResolver
import com.viewcompose.preview.runner.StaticPreviewEntry
import com.viewcompose.preview.runner.StaticPreviewMountResult
import com.viewcompose.preview.runner.StaticPreviewRenderer
import com.viewcompose.preview.runner.StaticPreviewWorker
import com.viewcompose.preview.tooling.PreviewRenderRequest
import com.viewcompose.preview.tooling.PreviewRenderResponse
import com.viewcompose.preview.tooling.PreviewRenderSnapshot

/** Resolves application bytecode and exports one static preview response. */
fun renderCompiledPreviewSample(
    context: Context,
    request: PreviewRenderRequest,
    applicationClassLoader: ClassLoader,
): PreviewRenderResponse {
    return StaticPreviewWorker().render(context, request, applicationClassLoader)
}

/** Renders a host-owned entry without reflective discovery. */
fun renderResolvedPreviewSample(
    context: Context,
    request: PreviewRenderRequest,
    entry: StaticPreviewEntry,
): PreviewRenderResponse {
    return StaticPreviewWorker().render(context, request, entry)
}

/** Mounts a frame for direct View capture or tooling-snapshot inspection. */
fun mountStaticPreviewSample(
    context: Context,
    request: PreviewRenderRequest,
    applicationClassLoader: ClassLoader,
): PreviewRenderSnapshot? {
    val entry = when (
        val resolved = PreviewJvmEntryPointResolver.resolve(
            descriptor = request.descriptor,
            classLoader = applicationClassLoader,
        )
    ) {
        is PreviewEntryResolutionResult.Success -> resolved.entry
        is PreviewEntryResolutionResult.Failure -> return null
    }
    return when (val mounted = StaticPreviewRenderer.mount(context, request, entry)) {
        is StaticPreviewMountResult.Failure -> null
        is StaticPreviewMountResult.Success -> mounted.frame.use { frame -> frame.snapshot }
    }
}
