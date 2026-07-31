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
                    arguments = listOf(":help", "--console=plain", "--stacktrace"),
                ),
                indicator = ProgressIndicatorBase(),
            )

            assertEquals(result.errorOutput, 0, result.exitCode)
        }
    }
}

private const val INTEGRATION_PROJECT_ROOT_ENVIRONMENT = "VIEWCOMPOSE_INTEGRATION_PROJECT_ROOT"
