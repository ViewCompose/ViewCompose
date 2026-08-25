package com.viewcompose.quality

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SampleDocumentationParityTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `migration snippet drift preserves diagnostic and declared inputs`() {
        val sourcePath =
            "samples/compose-migration/src/main/java/com/viewcompose/samples/migration/state/ComposeStateSample.kt"
        val pagePath = "docs/migration/compose-state-recomposition-and-restoration.md"
        val repository = fixtureRepository(
            label = "migration",
            sourcePath to
                "// DOCS_REGION_START(compose-state)\ncurrent()\n" +
                "// DOCS_REGION_END(compose-state)\n",
            pagePath to
                "{/* paired-sample source=\"$sourcePath\" region=\"compose-state\" */}\n" +
                "```kotlin\nstale()\n```\n{/* paired-sample-end */}\n",
        )
        assertFrozenParity(
            label = "verifyMigrationPairedSamples",
            repository = repository,
            expected = failedOutcome(
                diagnostic =
                    "Migration paired-sample verification failed:\n" +
                        "- $pagePath -> snippet 'compose-state' differs from $sourcePath",
                pagePath,
                sourcePath,
            ),
        ) { fixture ->
            SampleDocumentationVerifiers.verifyMigrationPairedSamples(
                repository = fixture,
                sampleSourceFiles = setOf(fixture.resolve(sourcePath)),
                documentationFiles = setOf(fixture.resolve(pagePath)),
                documentationRootPaths = listOf("docs/migration"),
                expectedPairsByPage = mapOf(
                    "compose-state-recomposition-and-restoration.md" to
                        listOf(SampleReference(sourcePath, "compose-state")),
                ),
            )
        }
    }

    @Test
    fun `tutorial project dependency preserves diagnostic and build inputs`() {
        val buildPath = "samples/tutorials/build.gradle.kts"
        val propertiesPath = "gradle/viewcompose-publishing.properties"
        val repository = fixtureRepository(
            label = "tutorial-build",
            buildPath to
                "dependencies { implementation(project(\":viewcompose-runtime\")) }\n",
            propertiesPath to "schemaVersion=1\n",
        )
        assertFrozenParity(
            label = "verifyTutorialSamples-build",
            repository = repository,
            expected = failedOutcome(
                diagnostic =
                    "Tutorial sample verification failed:\n" +
                        "- $buildPath -> public tutorial samples must resolve ViewCompose from Maven",
                propertiesPath,
                buildPath,
            ),
        ) { fixture ->
            SampleDocumentationVerifiers.verifyTutorialSamples(
                repository = fixture,
                sampleSourceFiles = emptySet(),
                documentationFiles = emptySet(),
                sampleBuildFiles = setOf(fixture.resolve(buildPath)),
                publishingPropertiesFile = fixture.resolve(propertiesPath),
                documentationRootPaths = emptyList(),
                baseArtifacts = emptyList(),
            )
        }
    }

    @Test
    fun `tutorial snippet drift preserves page contract and getting-started input`() {
        val sourcePath =
            "samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/StateTutorialActivity.kt"
        val pagePath = "docs/tutorials/state-and-events.md"
        val gettingStartedPath = "docs/tutorials/getting-started.md"
        val propertiesPath = "gradle/viewcompose-publishing.properties"
        val dependencyBlock =
            "```kotlin title=\"build.gradle.kts\"\n" +
                "repositories { mavenCentral() }\n" +
                "implementation(\"com.viewcompose:viewcompose-material3-android:1.0.0\")\n" +
                "```\n"
        val repository = fixtureRepository(
            label = "tutorial-snippet",
            sourcePath to
                "// DOCS_REGION_START(state)\ncurrent()\n// DOCS_REGION_END(state)\n",
            pagePath to
                dependencyBlock +
                "{/* tutorial-sample sample_id=\"tutorial.state-and-events\" source=\"$sourcePath\" region=\"state\" */}\n" +
                "```kotlin\nstale()\n```\n{/* tutorial-sample-end */}\n",
            gettingStartedPath to
                dependencyBlock +
                "id(\"com.viewcompose.preview\") version \"1.0.0\"\n" +
                "com.viewcompose:viewcompose-preview-core:1.0.0\n" +
                "com.viewcompose:viewcompose-preview-worker-host:1.0.0\n" +
                "com.viewcompose:viewcompose-preview-runner:1.0.0\n",
            propertiesPath to
                listOf(
                    "module.viewcompose-material3-android.version=1.0.0",
                    "module.viewcompose-preview-gradle-plugin.version=1.0.0",
                    "module.viewcompose-preview-core.version=1.0.0",
                    "module.viewcompose-preview-worker-host.version=1.0.0",
                    "module.viewcompose-preview-runner.version=1.0.0",
                ).joinToString("\n", postfix = "\n"),
        )
        assertFrozenParity(
            label = "verifyTutorialSamples-snippet",
            repository = repository,
            expected = failedOutcome(
                diagnostic =
                    "Tutorial sample verification failed:\n" +
                        "- $pagePath -> snippet 'state' differs from $sourcePath",
                gettingStartedPath,
                pagePath,
                propertiesPath,
                sourcePath,
            ),
        ) { fixture ->
            SampleDocumentationVerifiers.verifyTutorialSamples(
                repository = fixture,
                sampleSourceFiles = setOf(fixture.resolve(sourcePath)),
                documentationFiles = setOf(
                    fixture.resolve(pagePath),
                    fixture.resolve(gettingStartedPath),
                ),
                sampleBuildFiles = emptySet(),
                publishingPropertiesFile = fixture.resolve(propertiesPath),
                documentationRootPaths = listOf("docs/tutorials"),
                baseArtifacts = listOf("viewcompose-material3-android"),
            )
        }
        val unregisteredSource = SampleDocumentationVerifiers.verifyTutorialSamples(
            repository = repository,
            sampleSourceFiles = emptySet(),
            documentationFiles = setOf(
                repository.resolve(pagePath),
                repository.resolve(gettingStartedPath),
            ),
            sampleBuildFiles = emptySet(),
            publishingPropertiesFile = repository.resolve(propertiesPath),
            documentationRootPaths = listOf("docs/tutorials"),
            baseArtifacts = listOf("viewcompose-material3-android"),
        )
        assertTrue(
            unregisteredSource.diagnostics.joinToString("\n").contains(
                "$sourcePath -> source file is not a registered tutorial input",
            ),
        )
    }

    @Test
    fun `a newly discovered tutorial without a compiled sample fails`() {
        val pagePath = "docs/tutorials/foundations/new-capability.md"
        val gettingStartedPath = "docs/tutorials/getting-started.md"
        val propertiesPath = "gradle/viewcompose-publishing.properties"
        val dependencyBlock =
            "```kotlin title=\"build.gradle.kts\"\n" +
                "repositories { mavenCentral() }\n" +
                "implementation(\"com.viewcompose:viewcompose-material3-android:1.0.0\")\n" +
                "```\n"
        val repository = fixtureRepository(
            label = "tutorial-discovery",
            pagePath to dependencyBlock + "# New capability\n",
            gettingStartedPath to
                dependencyBlock +
                "id(\"com.viewcompose.preview\") version \"1.0.0\"\n" +
                "com.viewcompose:viewcompose-preview-core:1.0.0\n" +
                "com.viewcompose:viewcompose-preview-worker-host:1.0.0\n" +
                "com.viewcompose:viewcompose-preview-runner:1.0.0\n",
            propertiesPath to
                listOf(
                    "module.viewcompose-material3-android.version=1.0.0",
                    "module.viewcompose-preview-gradle-plugin.version=1.0.0",
                    "module.viewcompose-preview-core.version=1.0.0",
                    "module.viewcompose-preview-worker-host.version=1.0.0",
                    "module.viewcompose-preview-runner.version=1.0.0",
                ).joinToString("\n", postfix = "\n"),
        )
        assertFrozenParity(
            label = "verifyTutorialSamples-discovery",
            repository = repository,
            expected = failedOutcome(
                diagnostic =
                    "Tutorial sample verification failed:\n" +
                        "- $pagePath -> expected exactly one tutorial-sample block, found 0",
                gettingStartedPath,
                pagePath,
                propertiesPath,
            ),
        ) { fixture ->
            SampleDocumentationVerifiers.verifyTutorialSamples(
                repository = fixture,
                sampleSourceFiles = emptySet(),
                documentationFiles = setOf(
                    fixture.resolve(pagePath),
                    fixture.resolve(gettingStartedPath),
                ),
                sampleBuildFiles = emptySet(),
                publishingPropertiesFile = fixture.resolve(propertiesPath),
                documentationRootPaths = listOf("docs/tutorials"),
                baseArtifacts = listOf("viewcompose-material3-android"),
            )
        }
    }

    private fun fixtureRepository(
        label: String,
        vararg files: Pair<String, String>,
    ): File {
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
}
