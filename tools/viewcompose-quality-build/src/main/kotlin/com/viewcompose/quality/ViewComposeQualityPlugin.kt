package com.viewcompose.quality

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

/** Explicit repository inputs consumed by compiled ViewCompose quality tasks. */
abstract class ViewComposeQualityExtension {
    /** Root used only to resolve and validate repository-relative input identities. */
    abstract val repositoryDirectory: DirectoryProperty

    /** Canonical published-module catalog used by ownership-aware gates. */
    abstract val moduleCatalogFile: RegularFileProperty

    /** Source roots supplied by the consuming build without filesystem discovery by this plugin. */
    abstract val sourceSetDirectories: ConfigurableFileCollection

    /** Policy files supplied by the consuming build for compiled gate implementations. */
    abstract val policyFiles: ConfigurableFileCollection

    /** Destination for deterministic machine-readable quality reports. */
    abstract val reportsDirectory: DirectoryProperty
}

/** Writes the resolved input contract without executing or replacing an existing quality gate. */
abstract class WriteViewComposeQualityConfigurationTask : DefaultTask() {
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val moduleCatalogFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceSetDirectories: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val policyFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    /** Validates repository ownership and writes stable repository-relative input paths. */
    @TaskAction
    fun writeConfiguration() {
        val repository = repositoryDirectory.get().asFile.canonicalFile
        val moduleCatalog = moduleCatalogFile.get().asFile.relativePathWithin(repository)
        val sourceSets = sourceSetDirectories.files
            .map { sourceSet -> sourceSet.relativePathWithin(repository) }
            .sorted()
        val policies = policyFiles.files
            .map { policy -> policy.relativePathWithin(repository) }
            .sorted()
        val output = reportFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(
            buildString {
                appendLine("{")
                appendLine("  \"schemaVersion\": 1,")
                appendLine("  \"repository\": \".\",")
                appendLine("  \"moduleCatalog\": ${moduleCatalog.asJsonString()},")
                appendJsonArray("sourceSets", sourceSets)
                appendLine(",")
                appendJsonArray("policyFiles", policies)
                appendLine()
                appendLine("}")
            },
        )
    }

    private fun File.relativePathWithin(repository: File): String {
        val repositoryPath = repository.canonicalFile.toPath()
        val candidatePath = canonicalFile.toPath()
        if (!candidatePath.startsWith(repositoryPath)) {
            throw GradleException(
                "ViewCompose quality input '${candidatePath}' is outside repository '${repositoryPath}'.",
            )
        }
        return repositoryPath.relativize(candidatePath).toString().replace(File.separatorChar, '/')
    }

    private fun StringBuilder.appendJsonArray(name: String, values: List<String>) {
        appendLine("  ${name.asJsonString()}: [")
        values.forEachIndexed { index, value ->
            val suffix = if (index == values.lastIndex) "" else ","
            appendLine("    ${value.asJsonString()}$suffix")
        }
        append("  ]")
    }

    private fun String.asJsonString(): String = buildString {
        append('"')
        this@asJsonString.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
        append('"')
    }
}

/** Installs compiled quality ownership on the consuming repository root. */
class ViewComposeQualityRootPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        check(project == project.rootProject) {
            "com.viewcompose.quality.root must be applied to the root project."
        }
        val extension = project.extensions.create<ViewComposeQualityExtension>("viewComposeQuality")
        extension.repositoryDirectory.convention(project.layout.projectDirectory)
        extension.reportsDirectory.convention(
            project.layout.buildDirectory.dir("reports/viewcompose-quality"),
        )

        project.tasks.register<WriteViewComposeQualityConfigurationTask>(
            "writeViewComposeQualityConfiguration",
        ) {
            group = "verification"
            description =
                "Writes the explicit repository inputs owned by the compiled quality build."
            repositoryDirectory.set(extension.repositoryDirectory)
            moduleCatalogFile.set(extension.moduleCatalogFile)
            sourceSetDirectories.from(extension.sourceSetDirectories)
            policyFiles.from(extension.policyFiles)
            reportFile.set(extension.reportsDirectory.file("configuration.json"))
        }
    }
}
