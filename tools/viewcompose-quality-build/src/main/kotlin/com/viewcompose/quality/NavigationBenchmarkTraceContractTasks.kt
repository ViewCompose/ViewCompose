package com.viewcompose.quality

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register

/** Fails when navigation runtime trace names drift from the release benchmark collectors. */
abstract class VerifyNavigationBenchmarkTraceContractsTask : DefaultTask() {
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val benchmarkSource: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val runtimeSources: ConfigurableFileCollection

    @TaskAction
    fun verifyContracts() {
        NavigationBenchmarkTraceContractVerifier.verify(
            repository = repositoryDirectory.get().asFile,
            benchmarkSource = benchmarkSource.get().asFile,
            runtimeSources = runtimeSources.files,
        ).failOnNavigationBenchmarkTraceContractViolation()
    }
}

internal object NavigationBenchmarkTraceContractVerifier {
    fun verify(
        repository: File,
        benchmarkSource: File,
        runtimeSources: Set<File>,
    ): QualityGateOutcome {
        val benchmarkText = benchmarkSource.takeIf(File::isFile)?.readText().orEmpty()
        val runtimeText = runtimeSources.filter(File::isFile).joinToString("\n") { file ->
            file.readText()
        }
        val diagnostics = buildList {
            if (benchmarkText.isEmpty()) {
                add("navigation benchmark source is missing")
            }
            if (runtimeText.isEmpty()) {
                add("navigation runtime trace sources are missing")
            }
            navigationBenchmarkTraceContracts.forEach { contract ->
                if (!runtimeText.contains("\"${contract.sectionName}\"")) {
                    add("runtime does not emit ${contract.sectionName}")
                }
                if (!benchmarkText.contains("sectionName = \"${contract.sectionName}\"")) {
                    add("benchmark does not collect ${contract.sectionName}")
                }
                if (!benchmarkText.contains("label = \"${contract.label}\"")) {
                    add("benchmark does not expose ${contract.sectionName} as ${contract.label}")
                }
            }
            obsoleteNavigationBenchmarkTraceSections.forEach { obsolete ->
                if (benchmarkText.contains("\"$obsolete\"")) {
                    add("benchmark still collects obsolete section $obsolete")
                }
            }
        }
        val selectedPaths = (runtimeSources + benchmarkSource)
            .map { file ->
                file.canonicalFile.relativeTo(repository.canonicalFile).invariantSeparatorsPath
            }
            .sorted()
        return QualityGateOutcome(
            succeeded = diagnostics.isEmpty(),
            diagnostics = diagnostics,
            selectedPaths = selectedPaths,
        )
    }
}

internal fun Project.registerNavigationBenchmarkTraceContractTask(
    extension: ViewComposeQualityExtension,
) {
    val verify = tasks.register<VerifyNavigationBenchmarkTraceContractsTask>(
        "verifyNavigationBenchmarkTraceContracts",
    ) {
        group = "verification"
        description = "Verify navigation runtime trace names match release benchmark collectors."
        repositoryDirectory.set(extension.repositoryDirectory)
        benchmarkSource.set(
            extension.repositoryDirectory.file(
                "viewcompose-benchmark/src/main/java/com/viewcompose/benchmark/" +
                    "NavigationMotionBenchmark.kt",
            ),
        )
        runtimeSources.from(
            extension.repositoryDirectory.file(
                "viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/" +
                    "TransactionalNavHostCoordinator.kt",
            ),
            extension.repositoryDirectory.file(
                "viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/" +
                    "AndroidViewNavHostTransitionDriver.kt",
            ),
            extension.repositoryDirectory.file(
                "viewcompose-host-android/src/main/java/com/viewcompose/host/android/runtime/" +
                    "AndroidFrameAlignedRenderSessionRuntime.kt",
            ),
            extension.repositoryDirectory.file(
                "viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/" +
                    "session/RenderSession.kt",
            ),
        )
    }
    tasks.named("qaQuick").configure {
        dependsOn(verify)
    }
}

private data class NavigationBenchmarkTraceContract(
    val sectionName: String,
    val label: String,
)

private val navigationBenchmarkTraceContracts = listOf(
    NavigationBenchmarkTraceContract("VC.Nav.PrepareCommand", "navPrepareCommand"),
    NavigationBenchmarkTraceContract(
        "VC.Nav.PreparePresentations",
        "navPreparePresentations",
    ),
    NavigationBenchmarkTraceContract("VC.FrameRender", "frameRenderCount"),
    NavigationBenchmarkTraceContract("VC.RenderTree", "renderTreeMax"),
    NavigationBenchmarkTraceContract("VC.Nav.MotionFrame", "navMotionFrameMax"),
)

private val obsoleteNavigationBenchmarkTraceSections = setOf(
    "VC.Nav.PrepareDestination",
)

private fun QualityGateOutcome.failOnNavigationBenchmarkTraceContractViolation() {
    if (!succeeded) {
        error(
            buildString {
                appendLine("Navigation benchmark trace contract verification failed:")
                diagnostics.forEach { diagnostic -> appendLine("- $diagnostic") }
            }.trimEnd(),
        )
    }
}
