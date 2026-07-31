package com.viewcompose.studio.preview

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ViewComposePreviewGradleBridgeTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `discovers source descriptor renders selected variant and loads bounded image`() {
        val projectRoot = temporaryFolder.newFolder("project").toPath()
        Files.writeString(projectRoot.resolve("gradlew"), "")
        val moduleRoot = Files.createDirectories(projectRoot.resolve("feature/catalog"))
        Files.writeString(moduleRoot.resolve("build.gradle.kts"), "plugins {}")
        val source = moduleRoot.resolve("src/main/kotlin/sample/Sample.kt")
        Files.createDirectories(checkNotNull(source.parent))
        Files.writeString(source, "fun SampleCard() = Unit")
        val invocations = mutableListOf<PreviewGradleInvocation>()
        val executor = PreviewGradleExecutor { invocation, _ ->
            invocations += invocation
            when {
                invocation.arguments.first().endsWith("viewComposePreviewDescriptors") -> {
                    writeCatalog(moduleRoot, source)
                    PreviewGradleResult(0, "descriptors exported", "")
                }

                invocation.arguments.first().endsWith("renderDebugViewComposePreview") -> {
                    val variantId = invocation.arguments
                        .windowed(2)
                        .single { pair -> pair.first() == "--variant-id" }
                        .last()
                    writeSuccessfulResponse(moduleRoot, variantId)
                    PreviewGradleResult(0, "ViewCompose preview rendered:", "")
                }

                else -> PreviewGradleResult(1, "", "Unexpected task")
            }
        }

        val outcome = ViewComposePreviewRenderCoordinator(
            projectRoot = projectRoot,
            executor = executor,
        ).render(
            selection = PreviewSourceSelection(
                filePath = source.toString(),
                symbolName = "SampleCard",
                line = 10,
            ),
            requestedVariantId = "dark",
            forceRerender = true,
            indicator = TestProgressIndicator(),
        )

        assertTrue(outcome is PreviewRenderOutcome.Success)
        val success = outcome as PreviewRenderOutcome.Success
        assertEquals(2, success.image.width)
        assertEquals(3, success.image.height)
        assertEquals("dark", success.selectedVariantId)
        assertEquals("Dark", success.variantName)
        assertEquals(listOf("default", "dark"), success.variants.map(StudioPreviewVariant::id))
        assertTrue(success.renderSnapshot != null)
        assertFalse(success.cacheHit)
        assertEquals(
            listOf(
                ":feature:catalog:viewComposePreviewDescriptors",
                ":feature:catalog:renderDebugViewComposePreview",
            ),
            invocations.map { invocation -> invocation.arguments.first() },
        )
        assertTrue(
            invocations.last().arguments.windowed(2).any { pair ->
                pair == listOf("--preview-id", "sample-card")
            },
        )
        assertTrue(
            invocations.last().arguments.windowed(2).any { pair ->
                pair == listOf("--variant-id", "dark")
            },
        )
        assertTrue("--rerender" in invocations.last().arguments)
        assertFalse("--rerender=true" in invocations.last().arguments)
    }

    @Test
    fun `reports Gradle discovery failure without attempting render`() {
        val projectRoot = temporaryFolder.newFolder("failed-project").toPath()
        Files.writeString(projectRoot.resolve("gradlew"), "")
        val moduleRoot = Files.createDirectories(projectRoot.resolve("app"))
        Files.writeString(moduleRoot.resolve("build.gradle.kts"), "plugins {}")
        val source = moduleRoot.resolve("src/main/kotlin/sample/Sample.kt")
        Files.createDirectories(checkNotNull(source.parent))
        Files.writeString(source, "fun SampleCard() = Unit")
        var invocationCount = 0

        val outcome = ViewComposePreviewRenderCoordinator(
            projectRoot = projectRoot,
            executor = PreviewGradleExecutor { _, _ ->
                invocationCount += 1
                PreviewGradleResult(1, "", "Task not found")
            },
        ).render(
            selection = PreviewSourceSelection(
                filePath = source.toString(),
                symbolName = "SampleCard",
                line = 10,
            ),
            indicator = TestProgressIndicator(),
        )

        assertTrue(outcome is PreviewRenderOutcome.Failure)
        assertEquals("Preview discovery failed", (outcome as PreviewRenderOutcome.Failure).title)
        assertEquals(1, invocationCount)
    }

    @Test
    fun `gallery compiles a module once before rendering all previews`() {
        val projectRoot = temporaryFolder.newFolder("gallery-project").toPath()
        Files.writeString(projectRoot.resolve("gradlew"), "")
        val moduleRoot = Files.createDirectories(projectRoot.resolve("app"))
        Files.writeString(moduleRoot.resolve("build.gradle.kts"), "plugins {}")
        val firstSource = moduleRoot.resolve("src/main/kotlin/sample/First.kt")
        val secondSource = moduleRoot.resolve("src/main/kotlin/sample/Second.kt")
        Files.createDirectories(checkNotNull(firstSource.parent))
        Files.writeString(firstSource, "fun FirstCard() = Unit")
        Files.writeString(secondSource, "fun SecondCard() = Unit")
        val invocations = mutableListOf<PreviewGradleInvocation>()
        val executor = PreviewGradleExecutor { invocation, _ ->
            invocations += invocation
            when {
                invocation.arguments.first().endsWith("viewComposePreviewDescriptors") -> {
                    writeGalleryCatalog(moduleRoot, firstSource, secondSource)
                    PreviewGradleResult(0, "descriptors exported", "")
                }

                invocation.arguments.first().endsWith("renderDebugViewComposePreview") -> {
                    val previewId = invocation.arguments
                        .windowed(2)
                        .single { pair -> pair.first() == "--preview-id" }
                        .last()
                    writeSuccessfulResponse(moduleRoot, "default", previewId)
                    PreviewGradleResult(0, "ViewCompose preview rendered:", "")
                }

                else -> PreviewGradleResult(1, "", "Unexpected task")
            }
        }

        val outcomes = ViewComposePreviewRenderCoordinator(
            projectRoot = projectRoot,
            executor = executor,
        ).renderAll(
            selections = listOf(
                PreviewSourceSelection(firstSource.toString(), "FirstCard", 1),
                PreviewSourceSelection(secondSource.toString(), "SecondCard", 1),
            ),
            forceRerender = true,
            indicator = TestProgressIndicator(),
        )

        assertEquals(2, outcomes.filterIsInstance<PreviewRenderOutcome.Success>().size)
        assertEquals(
            1,
            invocations.count { invocation ->
                invocation.arguments.first().endsWith("viewComposePreviewDescriptors")
            },
        )
        assertEquals(
            2,
            invocations.count { invocation ->
                invocation.arguments.first().endsWith("renderDebugViewComposePreview")
            },
        )
        invocations
            .filter { invocation ->
                invocation.arguments.first().endsWith("renderDebugViewComposePreview")
            }
            .forEach { invocation ->
                assertTrue("--rerender" in invocation.arguments)
                assertFalse("--rerender=true" in invocation.arguments)
            }
    }

    private fun writeCatalog(
        moduleRoot: Path,
        source: Path,
    ) {
        val catalog = moduleRoot.resolve("build/viewcompose-preview/debug/descriptors.json")
        Files.createDirectories(checkNotNull(catalog.parent))
        Files.writeString(
            catalog,
            catalogJson(
                sourcePath = source.toString(),
                includeDarkVariant = true,
            ),
        )
    }

    private fun writeSuccessfulResponse(
        moduleRoot: Path,
        variantId: String,
        descriptorId: String = "sample-card",
    ) {
        val output = moduleRoot
            .resolve("build/viewcompose-preview/debug/render-cache")
            .resolve("a".repeat(64))
            .resolve("$descriptorId/$variantId")
        Files.createDirectories(output)
        val image = output.resolve("preview.png")
        ImageIO.write(
            BufferedImage(2, 3, BufferedImage.TYPE_INT_ARGB),
            "png",
            image.toFile(),
        )
        val renderTree = output.resolve("render-tree.json")
        Files.writeString(renderTree, "{}")
        Files.writeString(
            output.resolve("response.json"),
            """
            {
              "protocolVersion": 1,
              "requestId": "request",
              "previewId": "$descriptorId",
              "variantId": "$variantId",
              "status": "Success",
              "artifacts": {
                "imagePath": "${image.toJsonText()}",
                "renderTreePath": "${renderTree.toJsonText()}"
              },
              "diagnostics": [],
              "durationMillis": 12
            }
            """.trimIndent(),
        )
    }

    private fun writeGalleryCatalog(
        moduleRoot: Path,
        firstSource: Path,
        secondSource: Path,
    ) {
        val catalog = moduleRoot.resolve("build/viewcompose-preview/debug/descriptors.json")
        Files.createDirectories(checkNotNull(catalog.parent))
        Files.writeString(
            catalog,
            """
            {
              "protocolVersion": 1,
              "modulePath": ":app",
              "buildVariant": "debug",
              "buildFingerprint": "${"a".repeat(64)}",
              "descriptors": [
                {
                  "id": "first-card",
                  "displayName": "First Card",
                  "group": "Gallery",
                  "variants": [{"id": "default", "displayName": "Default"}],
                  "sourceLocation": {
                    "filePath": "${firstSource.toJsonText()}",
                    "line": 1,
                    "column": 1,
                    "symbolName": "FirstCard"
                  }
                },
                {
                  "id": "second-card",
                  "displayName": "Second Card",
                  "group": "Gallery",
                  "variants": [{"id": "default", "displayName": "Default"}],
                  "sourceLocation": {
                    "filePath": "${secondSource.toJsonText()}",
                    "line": 1,
                    "column": 1,
                    "symbolName": "SecondCard"
                  }
                }
              ],
              "diagnostics": []
            }
            """.trimIndent(),
        )
    }
}

private fun Path.toJsonText(): String {
    return toAbsolutePath().normalize().toString()
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
}

private class TestProgressIndicator : ProgressIndicator {
    private var running = false
    private var cancelled = false
    private var primaryText = ""
    private var secondaryText = ""
    private var progressFraction = 0.0
    private var indeterminate = true

    override fun start() {
        running = true
    }

    override fun stop() {
        running = false
    }

    override fun isRunning(): Boolean = running

    override fun cancel() {
        cancelled = true
    }

    override fun isCanceled(): Boolean = cancelled

    override fun setText(text: String?) {
        primaryText = text.orEmpty()
    }

    override fun getText(): String = primaryText

    override fun setText2(text: String?) {
        secondaryText = text.orEmpty()
    }

    override fun getText2(): String = secondaryText

    override fun getFraction(): Double = progressFraction

    override fun setFraction(fraction: Double) {
        progressFraction = fraction
    }

    override fun pushState() = Unit

    override fun popState() = Unit

    override fun isModal(): Boolean = false

    override fun getModalityState(): ModalityState = ModalityState.nonModal()

    override fun setModalityProgress(modalityProgress: ProgressIndicator?) = Unit

    override fun isIndeterminate(): Boolean = indeterminate

    override fun setIndeterminate(indeterminate: Boolean) {
        this.indeterminate = indeterminate
    }

    override fun checkCanceled() {
        if (cancelled) throw ProcessCanceledException()
    }

    override fun isPopupWasShown(): Boolean = false

    override fun isShowing(): Boolean = false
}
