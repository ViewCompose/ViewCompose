package com.viewcompose.preview.gradle

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewWorkerProcessClasspathTest {
    @Test
    fun `android target owner runtime precedes runner and removes desktop variants`() {
        val host = File("host.jar")
        val desktopLifecycle = File("lifecycle-viewmodel-desktop-2.8.7.jar")
        val runner = File("preview-runner.jar")
        val androidLifecycle = File("lifecycle-viewmodel-release-runtime.jar")
        val app = File("app-runtime.jar")
        val symbols = File("resource-symbols")

        val classpath = previewWorkerProcessClasspath(
            hostFiles = listOf(host),
            runnerFiles = listOf(desktopLifecycle, runner),
            targetRuntimeFiles = listOf(app, androidLifecycle),
            resourceSymbols = symbols,
        )

        assertEquals(
            listOf(host, androidLifecycle, runner, app, symbols),
            classpath,
        )
    }

    @Test
    fun `desktop owner runtime remains as fallback when target does not provide one`() {
        val desktopLifecycle = File("lifecycle-runtime-desktop-2.8.7.jar")
        val runner = File("preview-runner.jar")
        val app = File("app-runtime.jar")
        val symbols = File("resource-symbols")

        val classpath = previewWorkerProcessClasspath(
            hostFiles = emptyList(),
            runnerFiles = listOf(desktopLifecycle, runner),
            targetRuntimeFiles = listOf(app),
            resourceSymbols = symbols,
        )

        assertEquals(listOf(desktopLifecycle, runner, app, symbols), classpath)
    }
}
