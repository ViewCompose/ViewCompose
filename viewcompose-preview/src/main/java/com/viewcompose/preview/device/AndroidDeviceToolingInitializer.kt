package com.viewcompose.preview.device

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.viewcompose.animation.tooling.AnimationTimelineTooling
import com.viewcompose.animation.tooling.installAnimationTimelineTooling
import com.viewcompose.host.android.installRenderSessionInspectionTooling
import com.viewcompose.ui.foundation.RenderSessionInspectionTooling

/** Installs neutral tooling ports before `Application.onCreate` and Activity creation. */
internal class AndroidDeviceToolingInitializer : ContentProvider() {
    override fun onCreate(): Boolean {
        val applicationContext = context?.applicationContext ?: context ?: return true
        val debuggable = applicationContext.isDebuggableApplication()
        if (debuggable) AndroidDeviceToolingDebugGate.markAndGet(applicationContext)
        initializeAndroidDeviceTooling(
            debuggable = debuggable,
            installRenderSessionTooling = ::installRenderSessionInspectionTooling,
            installAnimationTooling = ::installAnimationTimelineTooling,
        )
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}

internal fun initializeAndroidDeviceTooling(
    debuggable: Boolean,
    installRenderSessionTooling: (RenderSessionInspectionTooling) -> Unit,
    installAnimationTooling: (AnimationTimelineTooling) -> Unit,
) {
    if (!debuggable) return
    installRenderSessionTooling(AndroidDeviceDslInspectionTooling())
    installAnimationTooling(AndroidAnimationTimelineTooling())
}
