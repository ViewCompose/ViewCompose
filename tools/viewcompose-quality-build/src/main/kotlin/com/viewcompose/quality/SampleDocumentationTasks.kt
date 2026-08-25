package com.viewcompose.quality

import java.io.File
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Verifies compiled migration samples remain identical to canonical and translated snippets. */
abstract class VerifyMigrationPairedSamplesTask : DefaultTask() {
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sampleSourceFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val documentationFiles: ConfigurableFileCollection

    @get:Input
    abstract val documentationRootPaths: ListProperty<String>

    @get:Input
    abstract val expectedPairsByPage: MapProperty<String, String>

    @TaskAction
    fun verifySamples() {
        SampleDocumentationVerifiers.verifyMigrationPairedSamples(
            repository = repositoryDirectory.get().asFile,
            sampleSourceFiles = sampleSourceFiles.files,
            documentationFiles = documentationFiles.files,
            documentationRootPaths = documentationRootPaths.get(),
            expectedPairsByPage = expectedPairsByPage.get().mapValues { (_, encoded) ->
                decodeSampleReferences(encoded)
            },
        ).failOnSampleDocumentationViolation()
    }
}

/** Verifies Maven-backed tutorial sources, snippets, and dependency blocks remain synchronized. */
abstract class VerifyTutorialSamplesTask : DefaultTask() {
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sampleSourceFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val documentationFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sampleBuildFiles: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val publishingPropertiesFile: RegularFileProperty

    @get:Input
    abstract val documentationRootPaths: ListProperty<String>

    @get:Input
    abstract val baseArtifacts: ListProperty<String>

    @get:Input
    abstract val samplesByPage: MapProperty<String, String>

    @TaskAction
    fun verifySamples() {
        SampleDocumentationVerifiers.verifyTutorialSamples(
            repository = repositoryDirectory.get().asFile,
            sampleSourceFiles = sampleSourceFiles.files,
            documentationFiles = documentationFiles.files,
            sampleBuildFiles = sampleBuildFiles.files,
            publishingPropertiesFile = publishingPropertiesFile.get().asFile,
            documentationRootPaths = documentationRootPaths.get(),
            baseArtifacts = baseArtifacts.get(),
            samplesByPage = samplesByPage.get().mapValues { (_, encoded) ->
                decodeTutorialSample(encoded)
            },
        ).failOnSampleDocumentationViolation()
    }
}

internal data class SampleReference(
    val source: String,
    val region: String,
)

internal data class TutorialSampleContract(
    val source: String,
    val region: String,
    val requiredArtifacts: List<String> = emptyList(),
)

internal object SampleDocumentationVerifiers {
    fun verifyMigrationPairedSamples(
        repository: File,
        sampleSourceFiles: Set<File>,
        documentationFiles: Set<File>,
        documentationRootPaths: List<String>,
        expectedPairsByPage: Map<String, List<SampleReference>>,
    ): QualityGateOutcome {
        val canonicalRepository = repository.canonicalFile
        val selectedPaths = (sampleSourceFiles + documentationFiles)
            .map { file -> file.repositoryRelativePath(canonicalRepository) }
            .toSortedSet()
        val sourceFilesByPath = sampleSourceFiles.associateBy { file ->
            file.repositoryRelativePath(canonicalRepository)
        }
        val documentationFilesByPath = documentationFiles.associateBy { file ->
            file.repositoryRelativePath(canonicalRepository)
        }
        val violations = mutableListOf<String>()

        fun compiledRegion(sourcePath: String, region: String): String? {
            val sourceFile = sourceFilesByPath[sourcePath] ?: canonicalRepository.resolve(sourcePath)
            if (!sourceFile.isFile) {
                violations += "$sourcePath -> source file does not exist"
                return null
            }
            val source = sourceFile.readText().replace("\r\n", "\n")
            val startMarker = "// DOCS_REGION_START($region)"
            val endMarker = "// DOCS_REGION_END($region)"
            if (source.windowed(startMarker.length).count { it == startMarker } != 1) {
                violations += "$sourcePath -> expected exactly one '$startMarker'"
                return null
            }
            if (source.windowed(endMarker.length).count { it == endMarker } != 1) {
                violations += "$sourcePath -> expected exactly one '$endMarker'"
                return null
            }
            val start = source.indexOf(startMarker) + startMarker.length
            val end = source.indexOf(endMarker, start)
            if (end < start) {
                violations += "$sourcePath -> '$endMarker' must follow '$startMarker'"
                return null
            }
            return source.substring(start, end).trim()
        }

        documentationRootPaths.forEach { documentationRootPath ->
            expectedPairsByPage.forEach pageLoop@{ (pageName, expectedPairs) ->
                val pagePath = "$documentationRootPath/$pageName"
                val page = documentationFilesByPath[pagePath] ?: canonicalRepository.resolve(pagePath)
                if (!page.isFile) {
                    violations += "$pagePath -> document does not exist"
                    return@pageLoop
                }
                val matches = PAIRED_SAMPLE_REGEX
                    .findAll(page.readText().replace("\r\n", "\n"))
                    .toList()
                val actualPairs = matches.map { match ->
                    SampleReference(match.groupValues[1], match.groupValues[2])
                }
                if (actualPairs != expectedPairs) {
                    violations +=
                        "$pagePath -> paired samples ${actualPairs.asLegacyPairs()} do not match " +
                            expectedPairs.asLegacyPairs()
                    return@pageLoop
                }
                matches.forEach snippetLoop@{ match ->
                    val sourcePath = match.groupValues[1]
                    val region = match.groupValues[2]
                    val expectedSnippet = compiledRegion(sourcePath, region) ?: return@snippetLoop
                    val documentedSnippet = match.groupValues[3].trim()
                    if (documentedSnippet != expectedSnippet) {
                        violations += "$pagePath -> snippet '$region' differs from $sourcePath"
                    }
                }
            }
        }

        return failedOutcome(
            violations = violations.distinct().sorted(),
            selectedPaths = selectedPaths,
            header = "Migration paired-sample verification failed:",
        )
    }

    fun verifyTutorialSamples(
        repository: File,
        sampleSourceFiles: Set<File>,
        documentationFiles: Set<File>,
        sampleBuildFiles: Set<File>,
        publishingPropertiesFile: File,
        documentationRootPaths: List<String>,
        baseArtifacts: List<String>,
        samplesByPage: Map<String, TutorialSampleContract>,
    ): QualityGateOutcome {
        val canonicalRepository = repository.canonicalFile
        val allInputFiles =
            sampleSourceFiles + documentationFiles + sampleBuildFiles + publishingPropertiesFile
        val selectedPaths = allInputFiles
            .map { file -> file.repositoryRelativePath(canonicalRepository) }
            .toSortedSet()
        val sourceFilesByPath = sampleSourceFiles.associateBy { file ->
            file.repositoryRelativePath(canonicalRepository)
        }
        val documentationFilesByPath = documentationFiles.associateBy { file ->
            file.repositoryRelativePath(canonicalRepository)
        }
        val violations = mutableListOf<String>()
        val publishingProperties = Properties().apply {
            publishingPropertiesFile.inputStream().use(::load)
        }
        fun publishedVersion(artifact: String): String =
            publishingProperties.getProperty("module.$artifact.version")
                ?: error("Missing published version for tutorial artifact '$artifact'.")

        fun compiledRegion(sourcePath: String, region: String): String? {
            val sourceFile = sourceFilesByPath[sourcePath] ?: canonicalRepository.resolve(sourcePath)
            if (!sourceFile.isFile) {
                violations += "$sourcePath -> source file does not exist"
                return null
            }
            val source = sourceFile.readText().replace("\r\n", "\n")
            val startMarker = "// DOCS_REGION_START($region)"
            val endMarker = "// DOCS_REGION_END($region)"
            if (source.windowed(startMarker.length).count { it == startMarker } != 1) {
                violations += "$sourcePath -> expected exactly one '$startMarker'"
                return null
            }
            if (source.windowed(endMarker.length).count { it == endMarker } != 1) {
                violations += "$sourcePath -> expected exactly one '$endMarker'"
                return null
            }
            val start = source.indexOf(startMarker) + startMarker.length
            val end = source.indexOf(endMarker, start)
            if (end < start) {
                violations += "$sourcePath -> '$endMarker' must follow '$startMarker'"
                return null
            }
            return source.substring(start, end).trim()
        }

        documentationRootPaths.forEach { documentationRootPath ->
            samplesByPage.forEach pageLoop@{ (pageName, sample) ->
                val pagePath = "$documentationRootPath/$pageName"
                val page = documentationFilesByPath[pagePath] ?: canonicalRepository.resolve(pagePath)
                if (!page.isFile) {
                    violations += "$pagePath -> document does not exist"
                    return@pageLoop
                }
                val pageText = page.readText().replace("\r\n", "\n")
                val matches = TUTORIAL_SAMPLE_REGEX.findAll(pageText).toList()
                val actualSamples = matches.map { match ->
                    SampleReference(match.groupValues[1], match.groupValues[2])
                }
                val expectedSamples = listOf(SampleReference(sample.source, sample.region))
                if (actualSamples != expectedSamples) {
                    violations +=
                        "$pagePath -> tutorial samples ${actualSamples.asLegacyPairs()} do not match " +
                            expectedSamples.asLegacyPairs()
                    return@pageLoop
                }
                matches.forEach snippetLoop@{ match ->
                    val sourcePath = match.groupValues[1]
                    val region = match.groupValues[2]
                    val expectedSnippet = compiledRegion(sourcePath, region) ?: return@snippetLoop
                    val documentedSnippet = match.groupValues[3].trim()
                    if (documentedSnippet != expectedSnippet) {
                        violations += "$pagePath -> snippet '$region' differs from $sourcePath"
                    }
                }

                val dependencyBlock = DEPENDENCY_BLOCK_REGEX.find(pageText)
                if (dependencyBlock == null || dependencyBlock.range.first > 1_500) {
                    violations += "$pagePath -> complete Maven dependencies must appear at the top"
                } else {
                    val block = dependencyBlock.groupValues[1]
                    val actualArtifacts = COORDINATE_REGEX.findAll(block)
                        .map { match -> match.groupValues[1] to match.groupValues[2] }
                        .toList()
                    val expectedArtifacts = (baseArtifacts + sample.requiredArtifacts).map { artifact ->
                        artifact to publishedVersion(artifact)
                    }
                    if (actualArtifacts != expectedArtifacts) {
                        violations +=
                            "$pagePath -> Maven artifacts $actualArtifacts do not match $expectedArtifacts"
                    }
                    if ("repositories { mavenCentral() }" !in block) {
                        violations += "$pagePath -> dependency block must declare Maven Central"
                    }
                    if ("project(" in block) {
                        violations += "$pagePath -> tutorial dependencies must not use project()"
                    }
                }
            }

            val gettingStartedPath = "$documentationRootPath/getting-started.md"
            val gettingStartedPage =
                documentationFilesByPath[gettingStartedPath]
                    ?: canonicalRepository.resolve(gettingStartedPath)
            if (!gettingStartedPage.isFile) {
                violations += "$gettingStartedPath -> document does not exist"
            } else {
                val pageText = gettingStartedPage.readText().replace("\r\n", "\n")
                val dependencyBlock = DEPENDENCY_BLOCK_REGEX.find(pageText)
                val leadingContent = pageText.take(5_000)
                if (dependencyBlock == null || dependencyBlock.range.first > 1_500) {
                    violations +=
                        "$gettingStartedPath -> complete Maven dependencies must appear at the top"
                } else {
                    val actualArtifacts = COORDINATE_REGEX.findAll(dependencyBlock.groupValues[1])
                        .map { match -> match.groupValues[1] to match.groupValues[2] }
                        .toList()
                    val expectedArtifacts = baseArtifacts.map { artifact ->
                        artifact to publishedVersion(artifact)
                    }
                    if (actualArtifacts != expectedArtifacts) {
                        violations +=
                            "$gettingStartedPath -> Maven artifacts $actualArtifacts do not match " +
                                expectedArtifacts
                    }
                    if ("repositories { mavenCentral() }" !in dependencyBlock.groupValues[1]) {
                        violations +=
                            "$gettingStartedPath -> dependency block must declare Maven Central"
                    }
                }
                listOf(
                    "id(\"com.viewcompose.preview\") version \"${publishedVersion("viewcompose-preview-gradle-plugin")}\"",
                    "com.viewcompose:viewcompose-preview-core:${publishedVersion("viewcompose-preview-core")}",
                    "com.viewcompose:viewcompose-preview-worker-host:${publishedVersion("viewcompose-preview-worker-host")}",
                    "com.viewcompose:viewcompose-preview-runner:${publishedVersion("viewcompose-preview-runner")}",
                ).forEach { requiredPreviewDependency ->
                    if (requiredPreviewDependency !in leadingContent) {
                        violations +=
                            "$gettingStartedPath -> missing optional preview dependency " +
                                "'$requiredPreviewDependency' at the top"
                    }
                }
            }
        }

        sampleBuildFiles.sortedBy(File::getPath).forEach { sampleBuild ->
            if ("project(" in sampleBuild.readText()) {
                violations +=
                    "${sampleBuild.repositoryRelativePath(canonicalRepository)} -> " +
                        "public tutorial samples must resolve ViewCompose from Maven"
            }
        }

        return failedOutcome(
            violations = violations.distinct().sorted(),
            selectedPaths = selectedPaths,
            header = "Tutorial sample verification failed:",
        )
    }

    private fun failedOutcome(
        violations: List<String>,
        selectedPaths: Set<String>,
        header: String,
    ): QualityGateOutcome = QualityGateOutcome(
        succeeded = violations.isEmpty(),
        diagnostics = if (violations.isEmpty()) {
            emptyList()
        } else {
            listOf(
                buildString {
                    appendLine(header)
                    violations.forEach { violation -> appendLine("- $violation") }
                },
            )
        },
        selectedPaths = selectedPaths.toList(),
    )

    private fun List<SampleReference>.asLegacyPairs(): List<Pair<String, String>> =
        map { sample -> sample.source to sample.region }

    private val PAIRED_SAMPLE_REGEX = Regex(
        """\{/\* paired-sample source="([^"]+)" region="([^"]+)" \*/\}\s*```kotlin\s*([\s\S]*?)\s*```\s*\{/\* paired-sample-end \*/\}""",
    )
    private val TUTORIAL_SAMPLE_REGEX = Regex(
        """\{/\* tutorial-sample source="([^"]+)" region="([^"]+)" \*/\}\s*```kotlin\s*([\s\S]*?)\s*```\s*\{/\* tutorial-sample-end \*/\}""",
    )
    private val DEPENDENCY_BLOCK_REGEX = Regex(
        """```kotlin title="build\.gradle\.kts"\s*([\s\S]*?)```""",
    )
    private val COORDINATE_REGEX =
        Regex("""implementation\("com\.viewcompose:([^:"]+):([^"]+)"\)""")
}

internal val migrationPairedSamplesByPage = mapOf(
    "compose-state-recomposition-and-restoration.md" to
        listOf(
            SampleReference(
                "samples/compose-migration/src/main/java/com/viewcompose/samples/migration/state/ComposeStateSample.kt",
                "compose-state",
            ),
            SampleReference(
                "samples/compose-migration/src/main/java/com/viewcompose/samples/migration/state/ViewComposeStateSample.kt",
                "viewcompose-state",
            ),
        ),
    "compose-layout-modifier-and-environment.md" to
        listOf(
            SampleReference(
                "samples/compose-migration/src/main/java/com/viewcompose/samples/migration/layout/ComposeLayoutSample.kt",
                "compose-layout",
            ),
            SampleReference(
                "samples/compose-migration/src/main/java/com/viewcompose/samples/migration/layout/ViewComposeLayoutSample.kt",
                "viewcompose-layout",
            ),
        ),
    "compose-host-lifecycle-and-android-interop.md" to
        listOf(
            SampleReference(
                "samples/compose-migration/src/main/java/com/viewcompose/samples/migration/host/ComposeHostSample.kt",
                "compose-host",
            ),
            SampleReference(
                "samples/compose-migration/src/main/java/com/viewcompose/samples/migration/host/ViewComposeHostSample.kt",
                "viewcompose-host",
            ),
        ),
    "compose-navigation.md" to
        listOf(
            SampleReference(
                "samples/compose-migration/src/main/java/com/viewcompose/samples/migration/navigation/ComposeNavigationSample.kt",
                "compose-navigation",
            ),
            SampleReference(
                "samples/compose-migration/src/main/java/com/viewcompose/samples/migration/navigation/ViewComposeNavigationSample.kt",
                "viewcompose-navigation-android",
            ),
        ),
)

internal val tutorialSamplesByPage = mapOf(
    "state-and-events.md" to
        TutorialSampleContract(
            "samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/StateTutorialActivity.kt",
            "state",
        ),
    "layouts-and-modifiers.md" to
        TutorialSampleContract(
            "samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/LayoutsTutorialActivity.kt",
            "layouts",
        ),
    "text-input.md" to
        TutorialSampleContract(
            "samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TextInputTutorialActivity.kt",
            "text-input",
        ),
    "lazy-lists.md" to
        TutorialSampleContract(
            "samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/LazyListsTutorialActivity.kt",
            "lazy-lists",
        ),
    "theming.md" to
        TutorialSampleContract(
            "samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/ThemingTutorialActivity.kt",
            "theming",
        ),
    "navigation.md" to
        TutorialSampleContract(
            "samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/NavigationTutorialActivity.kt",
            "navigation",
            listOf("viewcompose-navigation-android"),
        ),
    "overlays.md" to
        TutorialSampleContract(
            "samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/OverlaysTutorialActivity.kt",
            "overlays",
            listOf("viewcompose-overlay-material3-android"),
        ),
    "android-view.md" to
        TutorialSampleContract(
            "samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/AndroidViewTutorialActivity.kt",
            "android-view",
        ),
    "animation.md" to
        TutorialSampleContract(
            "samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/AnimationTutorialActivity.kt",
            "animation",
            listOf("viewcompose-animation"),
        ),
    "gestures.md" to
        TutorialSampleContract(
            "samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/GesturesTutorialActivity.kt",
            "gestures",
            listOf("viewcompose-gesture"),
        ),
    "lazy-list-performance.md" to
        TutorialSampleContract(
            "samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/LazyListPerformanceTutorialActivity.kt",
            "lazy-list-performance",
        ),
    "render-diagnostics.md" to
        TutorialSampleContract(
            "samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/RenderDiagnosticsTutorialActivity.kt",
            "render-diagnostics",
        ),
)

internal val migrationDocumentationRootPaths = listOf(
    "docs/migration",
    "website/i18n/zh-CN/docusaurus-plugin-content-docs/current/migration",
)
internal val tutorialDocumentationRootPaths = listOf(
    "docs/tutorials",
    "website/i18n/zh-CN/docusaurus-plugin-content-docs/current/tutorials",
)
internal val tutorialBaseArtifacts = listOf("viewcompose-material3-android")
internal val tutorialSampleBuildPaths = listOf(
    "samples/tutorials/build.gradle.kts",
    "samples/counter/build.gradle.kts",
)

internal fun encodeSampleReferences(samples: List<SampleReference>): String =
    samples.joinToString("\n") { sample -> "${sample.source}|${sample.region}" }

private fun decodeSampleReferences(encoded: String): List<SampleReference> =
    encoded.lineSequence().filter(String::isNotEmpty).map { line ->
        val fields = line.split('|', limit = 2)
        require(fields.size == 2) { "Invalid sample reference input: '$line'" }
        SampleReference(source = fields[0], region = fields[1])
    }.toList()

internal fun encodeTutorialSample(sample: TutorialSampleContract): String =
    listOf(sample.source, sample.region, sample.requiredArtifacts.joinToString(",")).joinToString("|")

private fun decodeTutorialSample(encoded: String): TutorialSampleContract {
    val fields = encoded.split('|', limit = 3)
    require(fields.size == 3) { "Invalid tutorial sample input: '$encoded'" }
    return TutorialSampleContract(
        source = fields[0],
        region = fields[1],
        requiredArtifacts = fields[2].split(',').filter(String::isNotEmpty),
    )
}

private fun QualityGateOutcome.failOnSampleDocumentationViolation() {
    if (!succeeded) error(diagnostics.joinToString("\n"))
}

private fun File.repositoryRelativePath(repository: File): String =
    canonicalFile.relativeTo(repository.canonicalFile).invariantSeparatorsPath
