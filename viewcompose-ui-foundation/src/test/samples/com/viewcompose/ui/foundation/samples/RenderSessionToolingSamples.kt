package com.viewcompose.ui.foundation.samples

import com.viewcompose.ui.foundation.RenderSessionSourceRegistration
import com.viewcompose.ui.foundation.RenderSessionSourceTooling
import com.viewcompose.ui.node.PlatformRenderContainerHandle
import com.viewcompose.ui.node.RenderContainerHandle
import com.viewcompose.ui.tooling.UiSourceCallSite

fun renderSessionSourceToolingSample() {
    var renderingActive = true
    var disposed = false
    val tooling = object : RenderSessionSourceTooling {
        override fun shouldCapture(container: RenderContainerHandle): Boolean = true

        override fun register(
            container: RenderContainerHandle,
            sourceCandidates: List<List<UiSourceCallSite>>,
        ): RenderSessionSourceRegistration {
            check(sourceCandidates.flatten().all { source -> source.lineNumber > 0 })
            return object : RenderSessionSourceRegistration {
                override fun setRenderingActive(active: Boolean) {
                    renderingActive = active
                }

                override fun dispose() {
                    disposed = true
                }
            }
        }
    }
    val container = object : PlatformRenderContainerHandle {
        override val container: Any = Any()
    }
    val registration = tooling.register(
        container = container,
        sourceCandidates = listOf(
            listOf(
                UiSourceCallSite(
                    className = "com.example.SettingsPageKt",
                    methodName = "SettingsPage",
                    fileName = "SettingsPage.kt",
                    lineNumber = 24,
                ),
            ),
        ),
    )

    registration.setRenderingActive(false)
    registration.dispose()
    check(!renderingActive && disposed)
}
