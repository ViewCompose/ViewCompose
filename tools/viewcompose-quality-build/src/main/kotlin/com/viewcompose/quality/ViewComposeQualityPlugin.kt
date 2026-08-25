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
import org.gradle.api.tasks.Exec
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

        project.tasks.register<VerifyDevelopmentToolingIsolationTask>(
            "verifyDevelopmentToolingIsolation",
        ) {
            group = "verification"
            description =
                "Verify concrete application-process tooling stays downstream and inactive on runtime hot paths."
            repositoryDirectory.set(extension.repositoryDirectory)
            runtimeSourceDirectories.from(project.providers.provider {
                val repository = extension.repositoryDirectory.get().asFile
                extension.runtimeModuleLayers.get().keys.sorted().map { module ->
                    repository.resolve("$module/src/main")
                }
            })
            toolingSourceDirectories.from(project.providers.provider {
                val repository = extension.repositoryDirectory.get().asFile
                extension.toolingModules.get().sorted().map { module ->
                    repository.resolve("$module/src/main")
                }
            })
            appBuildFile.set(extension.repositoryDirectory.file("app/build.gradle.kts"))
            toolingModules.set(extension.toolingModules)
            releaseRuntimeComponents.set(project.providers.provider {
                project.project(":app").configurations
                    .getByName("releaseRuntimeClasspath")
                    .incoming
                    .resolutionResult
                    .allComponents
                    .map { component -> component.id.displayName }
                    .sorted()
            })
        }
        project.tasks.register<VerifyDemoReleaseToolingApkTask>("verifyDemoReleaseToolingApk") {
            group = "verification"
            description = "Verify the optimized Demo release APK contains no concrete development tooling."
            dependsOn(":app:assembleRelease")
            repositoryDirectory.set(extension.repositoryDirectory)
            releaseApk.set(
                extension.repositoryDirectory.file(
                    "app/build/outputs/apk/release/app-release-unsigned.apk",
                ),
            )
        }
        project.tasks.register<VerifyDemoAutomationSelectorsTask>("verifyDemoAutomationSelectors") {
            group = "verification"
            description =
                "Prevent new Demo automation from selecting app-owned UI through localized visible copy."
            repositoryDirectory.set(extension.repositoryDirectory)
            sourceDirectories.from(
                extension.repositoryDirectory.dir("app/src/androidTest"),
                extension.repositoryDirectory.dir("viewcompose-benchmark/src/main"),
            )
        }
        project.tasks.register<VerifyDemoLocalizationResourcesTask>(
            "verifyDemoLocalizationResources",
        ) {
            group = "verification"
            description =
                "Verify Demo default-English and Simplified-Chinese resource parity and format contracts."
            repositoryDirectory.set(extension.repositoryDirectory)
            defaultResourcesDirectory.set(extension.repositoryDirectory.dir("app/src/main/res/values"))
            chineseResourcesDirectory.set(
                extension.repositoryDirectory.dir("app/src/main/res/values-zh-rCN"),
            )
        }
        project.tasks.register<VerifyDemoLocalizedVisibleCopyTask>(
            "verifyDemoLocalizedVisibleCopy",
        ) {
            group = "verification"
            description =
                "Prevent hard-coded visible copy from returning to Demo source domains already migrated to resources."
            repositoryDirectory.set(extension.repositoryDirectory)
            migratedSources.from(project.providers.provider {
                val repository = extension.repositoryDirectory.get().asFile
                demoLocalizedSourcePaths.map(repository::resolve)
            })
        }
        project.tasks.register<VerifyMigrationPairedSamplesTask>("verifyMigrationPairedSamples") {
            group = "verification"
            description =
                "Compile migration pairs and verify canonical and translated snippets match them."
            dependsOn(":samples:compose-migration:compileDebugKotlin")
            repositoryDirectory.set(extension.repositoryDirectory)
            sampleSourceFiles.from(
                migrationPairedSamplesByPage.values.flatten().map { sample ->
                    extension.repositoryDirectory.file(sample.source)
                },
            )
            documentationFiles.from(
                migrationDocumentationRootPaths.flatMap { documentationRootPath ->
                    migrationPairedSamplesByPage.keys.map { pageName ->
                        extension.repositoryDirectory.file("$documentationRootPath/$pageName")
                    }
                },
            )
            documentationRootPaths.set(migrationDocumentationRootPaths)
            expectedPairsByPage.set(
                migrationPairedSamplesByPage.mapValues { (_, samples) ->
                    encodeSampleReferences(samples)
                },
            )
        }
        project.tasks.register<VerifyTutorialSamplesTask>("verifyTutorialSamples") {
            group = "verification"
            description =
                "Compile independent Maven-backed tutorial sources and verify snippets and dependencies."
            dependsOn(":samples:tutorials:compileDebugKotlin")
            repositoryDirectory.set(extension.repositoryDirectory)
            sampleSourceFiles.from(
                tutorialSamplesByPage.values.map { sample ->
                    extension.repositoryDirectory.file(sample.source)
                },
            )
            documentationFiles.from(
                tutorialDocumentationRootPaths.flatMap { documentationRootPath ->
                    tutorialSamplesByPage.keys.map { pageName ->
                        extension.repositoryDirectory.file("$documentationRootPath/$pageName")
                    } + extension.repositoryDirectory.file(
                        "$documentationRootPath/getting-started.md",
                    )
                },
            )
            sampleBuildFiles.from(
                tutorialSampleBuildPaths.map(extension.repositoryDirectory::file),
            )
            publishingPropertiesFile.set(
                extension.repositoryDirectory.file("gradle/viewcompose-publishing.properties"),
            )
            documentationRootPaths.set(tutorialDocumentationRootPaths)
            baseArtifacts.set(tutorialBaseArtifacts)
            samplesByPage.set(
                tutorialSamplesByPage.mapValues { (_, sample) -> encodeTutorialSample(sample) },
            )
        }
        project.tasks.register<Exec>("verifyDocumentationScripts") {
            group = "verification"
            description = "Runs the documentation tooling regression suite."
            workingDir(project.rootDir.resolve("website"))
            commandLine("npm", "run", "test:scripts")
            inputs.files(
                project.fileTree(project.rootDir.resolve("website/scripts")) {
                    include("**/*.mjs")
                },
                project.rootDir.resolve("website/package.json"),
            )
        }
        project.tasks.register<Exec>("verifyDocumentLanguages") {
            group = "verification"
            description =
                "Verifies canonical-English and Simplified-Chinese documentation language."
            workingDir(project.rootDir.resolve("website"))
            commandLine("node", "scripts/verify-document-languages.mjs")
            inputs.files(
                project.fileTree(project.rootDir.resolve("docs")) {
                    include("**/*.md", "**/*.mdx")
                    exclude("archive/**")
                },
                project.fileTree(
                    project.rootDir.resolve(
                        "website/i18n/zh-CN/docusaurus-plugin-content-docs/current",
                    ),
                ) {
                    include("**/*.md", "**/*.mdx")
                },
                project.rootDir.resolve("website/scripts/verify-document-languages.mjs"),
                project.rootDir.resolve(
                    "website/scripts/__tests__/verify-document-languages.test.mjs",
                ),
                project.rootDir.resolve("website/i18n/translation-policy.json"),
            )
        }
        project.tasks.register<Exec>("verifyDocumentationTranslations") {
            group = "verification"
            description =
                "Verifies Chinese documentation coverage, status, and reviewed source fingerprints."
            workingDir(project.rootDir.resolve("website"))
            commandLine("npm", "run", "verify:translations")
            inputs.files(
                project.fileTree(project.rootDir.resolve("docs")) {
                    include("**/*.md", "**/*.mdx")
                    exclude("archive/**")
                },
                project.fileTree(
                    project.rootDir.resolve(
                        "website/i18n/zh-CN/docusaurus-plugin-content-docs/current",
                    ),
                ) {
                    include("**/*.md", "**/*.mdx")
                },
                project.rootDir.resolve("website/scripts/verify-translations.mjs"),
                project.rootDir.resolve("website/i18n/translation-policy.json"),
            )
        }
        project.tasks.register<VerifyDocumentationStructureTask>("verifyDocumentationStructure") {
            group = "verification"
            description =
                "Verifies documentation tooling, localization, placement, link coverage, and module catalog."
            dependsOn(
                "verifyDocumentationScripts",
                "verifyDocumentLanguages",
                "verifyDocumentationTranslations",
            )
            repositoryDirectory.set(extension.repositoryDirectory)
            rootMarkdownFiles.from(project.providers.provider {
                extension.repositoryDirectory.get().asFile.listFiles()
                    .orEmpty()
                    .filter { file ->
                        file.isFile && file.extension.equals("md", ignoreCase = true)
                    }
            })
            activeDocumentationFiles.from(project.providers.provider {
                val documentationRoot = extension.repositoryDirectory.get().asFile.resolve("docs")
                documentationRoot.walkTopDown()
                    .filter(File::isFile)
                    .filter { file -> file.extension.equals("md", ignoreCase = true) }
                    .filterNot { file ->
                        file.relativeTo(documentationRoot).invariantSeparatorsPath
                            .startsWith("archive/")
                    }
                    .toList()
            })
            checkedMarkdownFiles.from(project.providers.provider {
                val repository = extension.repositoryDirectory.get().asFile
                val documentationArchive = repository.resolve("docs/archive").toPath()
                val websiteTranslations = repository.resolve("website/i18n").toPath()
                repository.walkTopDown()
                    .onEnter { directory ->
                        directory == repository ||
                            directory.name !in documentationTraversalExcludedDirectories
                    }
                    .filter(File::isFile)
                    .filter { file -> file.extension.equals("md", ignoreCase = true) }
                    .filterNot { file -> file.toPath().startsWith(documentationArchive) }
                    .filterNot { file -> file.toPath().startsWith(websiteTranslations) }
                    .toList()
            })
            governanceFiles.from(
                extension.repositoryDirectory.file("gradle/viewcompose-publishing.properties"),
                extension.repositoryDirectory.file("docs/modules/README.md"),
            )
            documentationTopLevelDirectories.set(project.providers.provider {
                extension.repositoryDirectory.get().asFile.resolve("docs").listFiles()
                    .orEmpty()
                    .filter(File::isDirectory)
                    .map(File::getName)
            })
        }
        project.tasks.register<VerifyDslApiContractsTask>("verifyDslApiContracts") {
            group = "verification"
            description =
                "Verifies the compact DSL surface, renderer-neutral interaction contract, and Q3 KDoc shape."
            repositoryDirectory.set(extension.repositoryDirectory)
            foundationDslFiles.from(
                extension.repositoryDirectory.dir(
                    "viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/dsl",
                ),
            )
            forbiddenContractFiles.from(
                extension.repositoryDirectory.dir("viewcompose-ui-contract/src/main"),
                extension.repositoryDirectory.dir("viewcompose-ui-foundation/src/main"),
            )
            animationFiles.from(
                extension.repositoryDirectory.dir("viewcompose-animation/src/main"),
            )
        }
    }
}

private val documentationTraversalExcludedDirectories = setOf(
    ".codegraph",
    ".docusaurus",
    ".git",
    ".gradle",
    "build",
    "generated",
    "node_modules",
)

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
