package com.viewcompose.quality

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.SetProperty
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

    /** Gradle settings file that declares the repository's module set. */
    abstract val settingsFile: RegularFileProperty

    /** Module build files supplied explicitly for namespace and dependency-boundary checks. */
    abstract val moduleBuildFiles: ConfigurableFileCollection

    /** Canonical package owner for every module participating in repository gates. */
    abstract val modulePackageRoots: MapProperty<String, String>

    /** Retired package taxonomies that cannot return through source or service declarations. */
    abstract val forbiddenLegacyPackageRoots: SetProperty<String>

    /** Modules that intentionally have no Android namespace. */
    abstract val kotlinJvmModules: SetProperty<String>

    /** Runtime module to architectural-layer classifications. */
    abstract val runtimeModuleLayers: MapProperty<String, String>

    /** Allowed dependency layers encoded as stable comma-separated values by source layer. */
    abstract val allowedDependencyLayers: MapProperty<String, String>

    /** Build-time tooling modules kept outside the application runtime dependency graph. */
    abstract val toolingModules: SetProperty<String>

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

        project.tasks.register<VerifyModulePackageRootsTask>("verifyModulePackageRoots") {
            group = "verification"
            description = "Verify source package declarations follow module package-root prefixes."
            repositoryDirectory.set(extension.repositoryDirectory)
            modulePackageRoots.set(extension.modulePackageRoots)
            forbiddenLegacyPackageRoots.set(extension.forbiddenLegacyPackageRoots)
            sourceSetDirectories.from(extension.sourceSetDirectories)
        }
        project.tasks.register<VerifyAndroidModuleNamespacesTask>("verifyAndroidModuleNamespaces") {
            group = "verification"
            description = "Verify Android module namespace matches canonical package-root mapping."
            repositoryDirectory.set(extension.repositoryDirectory)
            modulePackageRoots.set(extension.modulePackageRoots)
            kotlinJvmModules.set(extension.kotlinJvmModules)
            moduleBuildFiles.from(extension.moduleBuildFiles)
        }
        project.tasks.register<VerifyModuleDependencyBoundariesTask>(
            "verifyModuleDependencyBoundaries",
        ) {
            group = "verification"
            description =
                "Verify framework modules are classified and project dependencies point in the allowed direction."
            repositoryDirectory.set(extension.repositoryDirectory)
            settingsFile.set(extension.settingsFile)
            moduleBuildFiles.from(extension.moduleBuildFiles)
            modulePackageRoots.set(extension.modulePackageRoots)
            runtimeModuleLayers.set(extension.runtimeModuleLayers)
            allowedDependencyLayers.set(extension.allowedDependencyLayers)
            toolingModules.set(extension.toolingModules)
        }
        project.tasks.register<VerifyDesignSystemIsolationTask>("verifyDesignSystemIsolation") {
            group = "verification"
            description =
                "Verify neutral layers and named design-system artifacts remain mutually isolated."
            repositoryDirectory.set(extension.repositoryDirectory)
            sourceSetDirectories.from(project.providers.provider {
                extension.sourceSetDirectories.files.filter { sourceDirectory ->
                    sourceDirectory.moduleNameWithin(extension.repositoryDirectory.get().asFile) in
                        designSystemSourceModules
                }
            })
            dependencyDeclarations.set(project.providers.provider {
                designSystemDependencyModules.sorted().flatMap { module ->
                    val moduleProject = project.findProject(":$module") ?: return@flatMap emptyList()
                    productionDependencyConfigurations.sorted().flatMap { configurationName ->
                        moduleProject.configurations.findByName(configurationName)
                            ?.dependencies
                            .orEmpty()
                            .map { dependency ->
                                DependencyDeclaration(
                                    module = module,
                                    configuration = configurationName,
                                    group = dependency.group,
                                    name = dependency.name,
                                ).encode()
                            }
                    }
                }.sorted()
            })
        }
        project.tasks.register<VerifyUiFoundationPlatformBoundaryTask>(
            "verifyUiFoundationPlatformBoundary",
        ) {
            group = "verification"
            description =
                "Verify UI Foundation delegates Android execution, host adaptation, logging, and tracing."
            repositoryDirectory.set(extension.repositoryDirectory)
            sourceSetDirectories.from(project.providers.provider {
                extension.sourceSetDirectories.files.filter { sourceDirectory ->
                    sourceDirectory.moduleNameWithin(extension.repositoryDirectory.get().asFile) ==
                        "viewcompose-ui-foundation"
                }
            })
        }

        project.registerSourceBoundaryTask(
            extension = extension,
            name = "verifyRuntimePurity",
            description = "Verify runtime remains Kotlin/JVM-pure without Android imports/dependencies.",
            module = "viewcompose-runtime",
            forbiddenImportPrefixes = listOf("import android.", "import androidx."),
            buildMarkerDiagnostics = mapOf(
                "androidx.core.ktx" to
                    "viewcompose-runtime/build.gradle.kts -> forbidden dependency androidx.core.ktx",
            ),
            diagnosticHeader = "Runtime purity verification failed:",
        )
        project.registerSourceBoundaryTask(
            extension = extension,
            name = "verifyGestureCorePurity",
            description = "Verify gesture-core remains Kotlin/JVM-pure without Android imports.",
            module = "viewcompose-gesture-core",
            forbiddenImportPrefixes = listOf("import android.", "import androidx."),
            diagnosticHeader = "Gesture-core purity verification failed:",
        )
        project.registerSourceBoundaryTask(
            extension = extension,
            name = "verifyGraphicsCorePurity",
            description = "Verify graphics-core remains Kotlin/JVM-pure without Android imports.",
            module = "viewcompose-graphics-core",
            forbiddenImportPrefixes = listOf("import android.", "import androidx."),
            diagnosticHeader = "Graphics-core purity verification failed:",
        )
        project.registerSourceBoundaryTask(
            extension = extension,
            name = "verifyPreviewCorePurity",
            description = "Verify preview-core remains Kotlin/JVM-pure without Android or Compose imports.",
            module = "viewcompose-preview-core",
            forbiddenImportPrefixes = listOf("import android.", "import androidx."),
            diagnosticHeader = "Preview-core purity verification failed:",
        )
        project.registerSourceBoundaryTask(
            extension = extension,
            name = "verifyPreviewRunnerBoundary",
            description = "Verify the native static preview runner stays independent from Compose.",
            module = "viewcompose-preview-runner",
            forbiddenImportPrefixes = listOf("import androidx.compose."),
            buildMarkerDiagnostics = mapOf(
                "libs.plugins.kotlin.compose" to
                    "viewcompose-preview-runner/build.gradle.kts -> forbidden Compose dependency " +
                    "'libs.plugins.kotlin.compose'",
                "libs.androidx.compose" to
                    "viewcompose-preview-runner/build.gradle.kts -> forbidden Compose dependency " +
                    "'libs.androidx.compose'",
            ),
            diagnosticHeader = "Preview-runner boundary verification failed:",
        )
        project.registerSourceBoundaryTask(
            extension = extension,
            name = "verifyPreviewGradlePluginBoundary",
            description = "Verify preview Gradle tooling uses public build APIs and stays renderer-free.",
            module = "viewcompose-preview-gradle-plugin",
            forbiddenImportPrefixes = listOf(
                "import android.",
                "import androidx.",
                "import com.android.build.gradle.internal.",
                "import com.android.tools.idea.",
                "import com.viewcompose.preview.runner.",
            ),
            buildMarkerDiagnostics = mapOf(
                "viewcompose-preview-runner" to
                    "viewcompose-preview-gradle-plugin/build.gradle.kts -> " +
                    "Gradle tooling must not depend on the renderer",
            ),
            diagnosticHeader = "Preview Gradle plugin boundary verification failed:",
        )
        project.registerSourceBoundaryTask(
            extension = extension,
            name = "verifyPreviewWorkerHostBoundary",
            description =
                "Verify the preview worker host stays independent from Gradle, Android Studio, and runner binaries.",
            module = "viewcompose-preview-worker-host",
            forbiddenImportPrefixes = listOf(
                "import org.gradle.",
                "import com.intellij.",
                "import org.jetbrains.android.",
            ),
            buildMarkerDiagnostics = mapOf(
                "viewcompose-preview-gradle-plugin" to
                    "viewcompose-preview-worker-host/build.gradle.kts -> forbidden dependency " +
                    "'viewcompose-preview-gradle-plugin'",
                "viewcompose-preview-runner" to
                    "viewcompose-preview-worker-host/build.gradle.kts -> forbidden dependency " +
                    "'viewcompose-preview-runner'",
            ),
            diagnosticHeader = "Preview worker host boundary verification failed:",
        )
        project.registerSourceBoundaryTask(
            extension = extension,
            name = "verifyNavigationCorePurity",
            description = "Verify navigation-core remains Kotlin/JVM-pure without Android imports.",
            module = "viewcompose-navigation-core",
            forbiddenImportPrefixes = listOf("import android.", "import androidx."),
            diagnosticHeader = "Navigation core purity verification failed:",
        )
    }
}

private fun Project.registerSourceBoundaryTask(
    extension: ViewComposeQualityExtension,
    name: String,
    description: String,
    module: String,
    forbiddenImportPrefixes: List<String>,
    buildMarkerDiagnostics: Map<String, String> = emptyMap(),
    diagnosticHeader: String,
) {
    tasks.register<VerifySourceBoundaryTask>(name) {
        group = "verification"
        this.description = description
        repositoryDirectory.set(extension.repositoryDirectory)
        sourceDirectory.set(extension.repositoryDirectory.dir("$module/src/main"))
        if (buildMarkerDiagnostics.isNotEmpty()) {
            buildFile.set(extension.repositoryDirectory.file("$module/build.gradle.kts"))
        }
        this.forbiddenImportPrefixes.set(forbiddenImportPrefixes)
        this.buildMarkerDiagnostics.set(buildMarkerDiagnostics)
        this.diagnosticHeader.set(diagnosticHeader)
    }
}

private fun File.moduleNameWithin(repository: File): String? {
    val repositoryPath = repository.canonicalFile.toPath()
    val sourcePath = canonicalFile.toPath()
    if (!sourcePath.startsWith(repositoryPath)) return null
    val relative = repositoryPath.relativize(sourcePath)
    return relative.getName(0)?.toString()
}
