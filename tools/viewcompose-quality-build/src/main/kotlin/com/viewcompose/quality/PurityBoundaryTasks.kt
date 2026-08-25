package com.viewcompose.quality

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Scans one source boundary and its optional build script for forbidden platform dependencies. */
abstract class VerifySourceBoundaryTask : DefaultTask() {
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectory: DirectoryProperty

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val buildFile: RegularFileProperty

    @get:Input
    abstract val forbiddenImportPrefixes: ListProperty<String>

    @get:Input
    abstract val buildMarkerDiagnostics: MapProperty<String, String>

    @get:Input
    abstract val diagnosticHeader: org.gradle.api.provider.Property<String>

    @TaskAction
    fun verifyBoundary() {
        PurityBoundaryVerifier.verify(
            repository = repositoryDirectory.get().asFile,
            sourceDirectory = sourceDirectory.get().asFile,
            buildFile = buildFile.orNull?.asFile,
            forbiddenImportPrefixes = forbiddenImportPrefixes.get(),
            buildMarkerDiagnostics = buildMarkerDiagnostics.get(),
            diagnosticHeader = diagnosticHeader.get(),
        ).failOnPurityViolation()
    }
}

internal object PurityBoundaryVerifier {
    fun verify(
        repository: File,
        sourceDirectory: File,
        buildFile: File?,
        forbiddenImportPrefixes: List<String>,
        buildMarkerDiagnostics: Map<String, String>,
        diagnosticHeader: String,
    ): QualityGateOutcome {
        val canonicalRepository = repository.canonicalFile
        val violations = mutableListOf<String>()
        val selectedPaths = mutableSetOf<String>()

        if (sourceDirectory.exists()) {
            sourceDirectory.walkTopDown()
                .filter { file ->
                    file.isFile && (file.extension == "kt" || file.extension == "java")
                }
                .forEach { file ->
                    val relativePath = file.relativePathWithin(canonicalRepository)
                    selectedPaths += relativePath
                    file.useLines { lines ->
                        lines.forEachIndexed { index, line ->
                            val trimmed = line.trimStart()
                            if (forbiddenImportPrefixes.any(trimmed::startsWith)) {
                                violations +=
                                    "$relativePath:${index + 1} -> " +
                                        "forbidden import '$trimmed'"
                            }
                        }
                    }
                }
        }

        if (buildFile?.exists() == true) {
            selectedPaths += buildFile.relativePathWithin(canonicalRepository)
            val content = buildFile.readText()
            buildMarkerDiagnostics.forEach { (marker, diagnostic) ->
                if (content.contains(marker)) violations += diagnostic
            }
        }

        val sortedViolations = violations.sorted()
        return QualityGateOutcome(
            succeeded = sortedViolations.isEmpty(),
            diagnostics = if (sortedViolations.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    buildString {
                        appendLine(diagnosticHeader)
                        sortedViolations.forEach { violation -> appendLine("- $violation") }
                    },
                )
            },
            selectedPaths = selectedPaths.sorted(),
        )
    }
}

private fun QualityGateOutcome.failOnPurityViolation() {
    if (!succeeded) error(diagnostics.joinToString("\n"))
}

private fun File.relativePathWithin(repository: File): String =
    canonicalFile.relativeTo(repository.canonicalFile).invariantSeparatorsPath
