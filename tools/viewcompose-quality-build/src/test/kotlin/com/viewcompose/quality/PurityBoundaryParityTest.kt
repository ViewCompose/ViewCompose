package com.viewcompose.quality

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PurityBoundaryParityTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `eight source-boundary failures preserve diagnostics and selected paths`() {
        sourceCases().forEach { case ->
            val relativePath = "${case.module}/src/main/kotlin/Bad.kt"
            val repository = fixtureRepository(case.task, relativePath to "${case.forbiddenImport}\n")
            val expected = failedOutcome(
                diagnostic = "${case.header}\n" +
                    "- $relativePath:1 -> forbidden import '${case.forbiddenImport}'",
                relativePath,
            )
            assertFrozenParity(
                label = case.task,
                repository = repository,
                expected = expected,
            ) { fixture ->
                PurityBoundaryVerifier.verify(
                    repository = fixture,
                    sourceDirectory = fixture.resolve("${case.module}/src/main"),
                    buildFile = null,
                    forbiddenImportPrefixes = case.forbiddenPrefixes,
                    buildMarkerDiagnostics = emptyMap(),
                    diagnosticHeader = case.header,
                )
            }
        }
    }

    @Test
    fun `four build-boundary failures preserve exact legacy marker diagnostics`() {
        buildCases().forEach { case ->
            val relativePath = "${case.module}/build.gradle.kts"
            val repository = fixtureRepository(case.task, relativePath to case.content)
            val expected = failedOutcome(
                diagnostic = buildString {
                    appendLine(case.header)
                    case.markerDiagnostics.values.sorted().forEach { diagnostic ->
                        appendLine("- $diagnostic")
                    }
                }.trimEnd(),
                relativePath,
            )
            assertFrozenParity(
                label = case.task,
                repository = repository,
                expected = expected,
            ) { fixture ->
                PurityBoundaryVerifier.verify(
                    repository = fixture,
                    sourceDirectory = fixture.resolve("${case.module}/src/main"),
                    buildFile = fixture.resolve(relativePath),
                    forbiddenImportPrefixes = emptyList(),
                    buildMarkerDiagnostics = case.markerDiagnostics,
                    diagnosticHeader = case.header,
                )
            }
        }
    }

    private fun sourceCases(): List<SourceCase> = listOf(
        SourceCase(
            task = "verifyRuntimePurity",
            module = "viewcompose-runtime",
            forbiddenImport = "import android.content.Context",
            forbiddenPrefixes = listOf("import android.", "import androidx."),
            header = "Runtime purity verification failed:",
        ),
        SourceCase(
            task = "verifyGestureCorePurity",
            module = "viewcompose-gesture-core",
            forbiddenImport = "import android.view.MotionEvent",
            forbiddenPrefixes = listOf("import android.", "import androidx."),
            header = "Gesture-core purity verification failed:",
        ),
        SourceCase(
            task = "verifyGraphicsCorePurity",
            module = "viewcompose-graphics-core",
            forbiddenImport = "import android.graphics.Canvas",
            forbiddenPrefixes = listOf("import android.", "import androidx."),
            header = "Graphics-core purity verification failed:",
        ),
        SourceCase(
            task = "verifyPreviewCorePurity",
            module = "viewcompose-preview-core",
            forbiddenImport = "import android.app.Application",
            forbiddenPrefixes = listOf("import android.", "import androidx."),
            header = "Preview-core purity verification failed:",
        ),
        SourceCase(
            task = "verifyPreviewRunnerBoundary",
            module = "viewcompose-preview-runner",
            forbiddenImport = "import androidx.compose.runtime.Composable",
            forbiddenPrefixes = listOf("import androidx.compose."),
            header = "Preview-runner boundary verification failed:",
        ),
        SourceCase(
            task = "verifyPreviewGradlePluginBoundary",
            module = "viewcompose-preview-gradle-plugin",
            forbiddenImport = "import android.content.Context",
            forbiddenPrefixes = listOf(
                "import android.",
                "import androidx.",
                "import com.android.build.gradle.internal.",
                "import com.android.tools.idea.",
                "import com.viewcompose.preview.runner.",
            ),
            header = "Preview Gradle plugin boundary verification failed:",
        ),
        SourceCase(
            task = "verifyPreviewWorkerHostBoundary",
            module = "viewcompose-preview-worker-host",
            forbiddenImport = "import org.gradle.api.Project",
            forbiddenPrefixes = listOf(
                "import org.gradle.",
                "import com.intellij.",
                "import org.jetbrains.android.",
            ),
            header = "Preview worker host boundary verification failed:",
        ),
        SourceCase(
            task = "verifyNavigationCorePurity",
            module = "viewcompose-navigation-core",
            forbiddenImport = "import android.os.Bundle",
            forbiddenPrefixes = listOf("import android.", "import androidx."),
            header = "Navigation core purity verification failed:",
        ),
    )

    private fun buildCases(): List<BuildCase> = listOf(
        BuildCase(
            task = "verifyRuntimePurity",
            module = "viewcompose-runtime",
            content = "dependencies { implementation(libs.androidx.core.ktx) }\n",
            markerDiagnostics = mapOf(
                "androidx.core.ktx" to
                    "viewcompose-runtime/build.gradle.kts -> forbidden dependency androidx.core.ktx",
            ),
            header = "Runtime purity verification failed:",
        ),
        BuildCase(
            task = "verifyPreviewRunnerBoundary",
            module = "viewcompose-preview-runner",
            content = "plugins { alias(libs.plugins.kotlin.compose) }\n" +
                "dependencies { implementation(libs.androidx.compose) }\n",
            markerDiagnostics = mapOf(
                "libs.plugins.kotlin.compose" to
                    "viewcompose-preview-runner/build.gradle.kts -> forbidden Compose dependency " +
                    "'libs.plugins.kotlin.compose'",
                "libs.androidx.compose" to
                    "viewcompose-preview-runner/build.gradle.kts -> forbidden Compose dependency " +
                    "'libs.androidx.compose'",
            ),
            header = "Preview-runner boundary verification failed:",
        ),
        BuildCase(
            task = "verifyPreviewGradlePluginBoundary",
            module = "viewcompose-preview-gradle-plugin",
            content = "implementation(project(\":viewcompose-preview-runner\"))\n",
            markerDiagnostics = mapOf(
                "viewcompose-preview-runner" to
                    "viewcompose-preview-gradle-plugin/build.gradle.kts -> " +
                    "Gradle tooling must not depend on the renderer",
            ),
            header = "Preview Gradle plugin boundary verification failed:",
        ),
        BuildCase(
            task = "verifyPreviewWorkerHostBoundary",
            module = "viewcompose-preview-worker-host",
            content = "implementation(project(\":viewcompose-preview-gradle-plugin\"))\n" +
                "implementation(project(\":viewcompose-preview-runner\"))\n",
            markerDiagnostics = mapOf(
                "viewcompose-preview-gradle-plugin" to
                    "viewcompose-preview-worker-host/build.gradle.kts -> forbidden dependency " +
                    "'viewcompose-preview-gradle-plugin'",
                "viewcompose-preview-runner" to
                    "viewcompose-preview-worker-host/build.gradle.kts -> forbidden dependency " +
                    "'viewcompose-preview-runner'",
            ),
            header = "Preview worker host boundary verification failed:",
        ),
    )

    private fun fixtureRepository(label: String, vararg files: Pair<String, String>): File {
        val repository = temporaryFolder.newFolder(label)
        files.forEach { (path, content) ->
            repository.resolve(path).apply {
                parentFile.mkdirs()
                writeText(content)
            }
        }
        return repository
    }

    private fun failedOutcome(diagnostic: String, vararg paths: String): QualityGateOutcome =
        QualityGateOutcome(
            succeeded = false,
            diagnostics = listOf(diagnostic),
            selectedPaths = paths.toList().sorted(),
        )

    private fun assertFrozenParity(
        label: String,
        repository: File,
        expected: QualityGateOutcome,
        candidate: QualityGateImplementation,
    ) {
        val result = QualityGateParityHarness().compare(
            fixtureRepository = repository,
            legacy = QualityGateImplementation { expected },
            candidate = candidate,
        )
        assertTrue("$label: ${result.differences.joinToString("; ")}", result.isEquivalent)
        result.assertEquivalent()
    }

    private data class SourceCase(
        val task: String,
        val module: String,
        val forbiddenImport: String,
        val forbiddenPrefixes: List<String>,
        val header: String,
    )

    private data class BuildCase(
        val task: String,
        val module: String,
        val content: String,
        val markerDiagnostics: Map<String, String>,
        val header: String,
    )
}
