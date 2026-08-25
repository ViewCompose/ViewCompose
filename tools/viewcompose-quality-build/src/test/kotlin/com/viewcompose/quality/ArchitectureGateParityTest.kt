package com.viewcompose.quality

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArchitectureGateParityTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `package-root failure preserves diagnostics and selected source`() {
        val repository = fixtureRepository(
            "viewcompose-runtime/src/main/kotlin/Bad.kt" to "package wrong\n",
        )
        assertFrozenParity(
            repository = repository,
            expected = failedOutcome(
                "Module package-root verification failed:\n" +
                    "- viewcompose-runtime:main:viewcompose-runtime/src/main/kotlin/Bad.kt -> " +
                    "package 'wrong' not under 'com.viewcompose.runtime'",
                "viewcompose-runtime/src/main/kotlin/Bad.kt",
            ),
        ) { fixture ->
            ArchitectureGateVerifiers.verifyModulePackageRoots(
                repository = fixture,
                modulePackageRoots = mapOf("viewcompose-runtime" to "com.viewcompose.runtime"),
                forbiddenLegacyPackageRoots = emptySet(),
                sourceSetDirectories = sourceSetDirectories(fixture),
            )
        }
    }

    @Test
    fun `namespace failure preserves diagnostics and selected build file`() {
        val repository = fixtureRepository(
            "viewcompose-host-android/build.gradle.kts" to
                "android { namespace = \"wrong.namespace\" }\n",
        )
        assertFrozenParity(
            repository = repository,
            expected = failedOutcome(
                "Android namespace verification failed:\n" +
                    "- viewcompose-host-android -> namespace 'wrong.namespace' != expected " +
                    "'com.viewcompose.host.android'",
                "viewcompose-host-android/build.gradle.kts",
            ),
        ) { fixture ->
            ArchitectureGateVerifiers.verifyAndroidModuleNamespaces(
                repository = fixture,
                modulePackageRoots = mapOf(
                    "viewcompose-host-android" to "com.viewcompose.host.android",
                ),
                kotlinJvmModules = emptySet(),
            )
        }
    }

    @Test
    fun `dependency-boundary failure preserves diagnostics and selected build inputs`() {
        val repository = fixtureRepository(
            "settings.gradle.kts" to "include(\":viewcompose-runtime\")\n",
            "viewcompose-runtime/build.gradle.kts" to
                "dependencies { implementation(project(\":app\")) }\n",
        )
        assertFrozenParity(
            repository = repository,
            expected = failedOutcome(
                "Module dependency-boundary verification failed:\n" +
                    "- viewcompose-runtime -> framework modules must not depend on the demo app",
                "settings.gradle.kts",
                "viewcompose-runtime/build.gradle.kts",
            ),
        ) { fixture ->
            ArchitectureGateVerifiers.verifyModuleDependencyBoundaries(
                repository = fixture,
                settingsFile = fixture.resolve("settings.gradle.kts"),
                modulePackageRoots = mapOf("viewcompose-runtime" to "com.viewcompose.runtime"),
                runtimeModuleLayers = mapOf("viewcompose-runtime" to "kernel"),
                allowedDependencyLayers = mapOf("kernel" to setOf("kernel")),
                toolingModules = emptySet(),
            )
        }
    }

    @Test
    fun `design-system failure preserves diagnostics and selected source`() {
        val repository = fixtureRepository(
            "viewcompose-ui-foundation/src/main/kotlin/Bad.kt" to
                "package com.viewcompose.ui.foundation\n" +
                "import com.google.android.material.button.MaterialButton\n",
        )
        assertFrozenParity(
            repository = repository,
            expected = failedOutcome(
                "Design-system isolation verification failed:\n" +
                    "- viewcompose-ui-foundation/src/main/kotlin/Bad.kt:2 -> forbidden Material " +
                    "import 'import com.google.android.material.button.MaterialButton'",
                "viewcompose-ui-foundation/src/main/kotlin/Bad.kt",
            ),
        ) { fixture ->
            ArchitectureGateVerifiers.verifyDesignSystemIsolation(
                repository = fixture,
                sourceSetDirectories = sourceSetDirectories(fixture),
                dependencyDeclarations = emptyList(),
            )
        }
    }

    @Test
    fun `UI Foundation platform failure preserves diagnostics and selected source`() {
        val repository = fixtureRepository(
            "viewcompose-ui-foundation/src/main/kotlin/Bad.kt" to
                "package com.viewcompose.ui.foundation\nimport android.view.View\n",
        )
        assertFrozenParity(
            repository = repository,
            expected = failedOutcome(
                "UI Foundation platform-boundary verification failed:\n" +
                    "- viewcompose-ui-foundation/src/main/kotlin/Bad.kt:2 -> Android execution " +
                    "import 'android.view.View' belongs in Android Engine",
                "viewcompose-ui-foundation/src/main/kotlin/Bad.kt",
            ),
        ) { fixture ->
            ArchitectureGateVerifiers.verifyUiFoundationPlatformBoundary(
                repository = fixture,
                sourceSetDirectories = sourceSetDirectories(fixture),
            )
        }
    }

    @Test
    fun `design-system dependency model preserves duplicate legacy violations`() {
        val repository = fixtureRepository()
        val outcome = ArchitectureGateVerifiers.verifyDesignSystemIsolation(
            repository = repository,
            sourceSetDirectories = emptySet(),
            dependencyDeclarations = listOf(
                DependencyDeclaration(
                    module = "viewcompose-ui-foundation",
                    configuration = "implementation",
                    group = "com.viewcompose",
                    name = "viewcompose-material3",
                ),
            ),
        )

        assertEquals(false, outcome.succeeded)
        val diagnostic = outcome.diagnostics.single()
        assertTrue(diagnostic.contains("forbidden Material project dependency 'viewcompose-material3'"))
        assertTrue(diagnostic.contains("neutral module cannot depend on named design system"))
    }

    private fun fixtureRepository(vararg files: Pair<String, String>): File {
        val repository = temporaryFolder.newFolder()
        files.forEach { (path, content) ->
            repository.resolve(path).apply {
                parentFile.mkdirs()
                writeText(content)
            }
        }
        return repository
    }

    private fun sourceSetDirectories(repository: File): Set<File> =
        repository.walkTopDown()
            .filter { file -> file.isDirectory && file.name == "src" }
            .toSet()

    private fun failedOutcome(diagnostic: String, vararg paths: String): QualityGateOutcome =
        QualityGateOutcome(
            succeeded = false,
            diagnostics = listOf(diagnostic),
            selectedPaths = paths.toList().sorted(),
        )

    private fun assertFrozenParity(
        repository: File,
        expected: QualityGateOutcome,
        candidate: QualityGateImplementation,
    ) {
        val result = QualityGateParityHarness().compare(
            fixtureRepository = repository,
            legacy = QualityGateImplementation { expected },
            candidate = candidate,
        )
        assertTrue(result.differences.joinToString("\n"), result.isEquivalent)
        result.assertEquivalent()
    }
}
