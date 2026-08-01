package com.viewcompose.studio.preview

import com.intellij.openapi.progress.util.ProgressIndicatorBase
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class PersistentPreviewGradleExecutorIntegrationTest {
    @Test
    fun `executes a wrapper build over the persistent tooling connection`() {
        val configuredRoot = System.getenv(INTEGRATION_PROJECT_ROOT_ENVIRONMENT)
        assumeTrue(!configuredRoot.isNullOrBlank())
        val projectRoot = Path.of(configuredRoot).toAbsolutePath().normalize()
        val wrapper = projectRoot.resolve("gradlew")
        assumeTrue(Files.isRegularFile(wrapper))

        PersistentPreviewGradleExecutor(projectRoot).use { executor ->
            val result = executor.execute(
                invocation = PreviewGradleInvocation(
                    executable = wrapper,
                    workingDirectory = projectRoot,
                    task = ":help",
                    buildArguments = listOf(
                        "--console=plain",
                        "--stacktrace",
                        "-PviewComposeToolingApiProbe=accepted",
                    ),
                ),
                indicator = ProgressIndicatorBase(),
            )

            assertEquals(result.errorOutput, 0, result.exitCode)
        }
    }

    @Test
    fun `executes a project property configured preview through the tooling connection`() {
        val configuredRoot = System.getenv(INTEGRATION_PROJECT_ROOT_ENVIRONMENT)
        val renderTask = System.getenv(INTEGRATION_RENDER_TASK_ENVIRONMENT)
        val previewId = System.getenv(INTEGRATION_PREVIEW_ID_ENVIRONMENT)
        val variantId = System.getenv(INTEGRATION_VARIANT_ID_ENVIRONMENT)
        assumeTrue(
            listOf(configuredRoot, renderTask, previewId, variantId)
                .all { value -> !value.isNullOrBlank() },
        )
        val projectRoot = Path.of(checkNotNull(configuredRoot)).toAbsolutePath().normalize()
        val wrapper = projectRoot.resolve("gradlew")
        assumeTrue(Files.isRegularFile(wrapper))

        PersistentPreviewGradleExecutor(projectRoot).use { executor ->
            val result = executor.execute(
                invocation = PreviewGradleInvocation(
                    executable = wrapper,
                    workingDirectory = projectRoot,
                    task = checkNotNull(renderTask),
                    buildArguments = listOf(
                        "--console=plain",
                        "--stacktrace",
                        "-PviewComposePreviewId=${checkNotNull(previewId)}",
                        "-PviewComposePreviewVariantId=${checkNotNull(variantId)}",
                        "-PviewComposePreviewRerender=true",
                    ),
                ),
                indicator = ProgressIndicatorBase(),
            )

            assertEquals(result.errorOutput, 0, result.exitCode)
        }
    }
}

private const val INTEGRATION_PROJECT_ROOT_ENVIRONMENT = "VIEWCOMPOSE_INTEGRATION_PROJECT_ROOT"
private const val INTEGRATION_RENDER_TASK_ENVIRONMENT = "VIEWCOMPOSE_INTEGRATION_RENDER_TASK"
private const val INTEGRATION_PREVIEW_ID_ENVIRONMENT = "VIEWCOMPOSE_INTEGRATION_PREVIEW_ID"
private const val INTEGRATION_VARIANT_ID_ENVIRONMENT = "VIEWCOMPOSE_INTEGRATION_VARIANT_ID"
