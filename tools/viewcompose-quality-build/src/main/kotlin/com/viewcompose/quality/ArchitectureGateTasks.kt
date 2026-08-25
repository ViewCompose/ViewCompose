package com.viewcompose.quality

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

internal val architectureSourceSets = setOf("main", "test", "androidTest")
internal val designSystemDependencyModules = setOf(
    "viewcompose-ui-foundation",
    "viewcompose-renderer-android",
    "viewcompose-host-android",
    "viewcompose-android",
    "viewcompose-material3",
    "viewcompose-oneui7",
    "viewcompose-overlay-oneui7-android",
)
internal val designSystemSourceModules = setOf(
    "viewcompose-ui-foundation",
    "viewcompose-renderer-android",
    "viewcompose-host-android",
    "viewcompose-android",
    "viewcompose-oneui7",
    "viewcompose-overlay-oneui7-android",
)
internal val productionDependencyConfigurations = setOf("api", "implementation", "compileOnly")

/** Verifies source package declarations against the canonical module ownership map. */
abstract class VerifyModulePackageRootsTask : DefaultTask() {
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @get:Input
    abstract val modulePackageRoots: MapProperty<String, String>

    @get:Input
    abstract val forbiddenLegacyPackageRoots: SetProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceSetDirectories: ConfigurableFileCollection

    @TaskAction
    fun verifyPackageRoots() {
        ArchitectureGateVerifiers.verifyModulePackageRoots(
            repository = repositoryDirectory.get().asFile,
            modulePackageRoots = modulePackageRoots.get(),
            forbiddenLegacyPackageRoots = forbiddenLegacyPackageRoots.get(),
            sourceSetDirectories = sourceSetDirectories.files,
        ).failOnViolation()
    }
}

/** Verifies Android namespaces against the canonical module ownership map. */
abstract class VerifyAndroidModuleNamespacesTask : DefaultTask() {
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @get:Input
    abstract val modulePackageRoots: MapProperty<String, String>

    @get:Input
    abstract val kotlinJvmModules: SetProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val moduleBuildFiles: ConfigurableFileCollection

    @TaskAction
    fun verifyNamespaces() {
        ArchitectureGateVerifiers.verifyAndroidModuleNamespaces(
            repository = repositoryDirectory.get().asFile,
            modulePackageRoots = modulePackageRoots.get(),
            kotlinJvmModules = kotlinJvmModules.get(),
        ).failOnViolation()
    }
}

/** Verifies module classification and allowed project-dependency directions. */
abstract class VerifyModuleDependencyBoundariesTask : DefaultTask() {
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val settingsFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val moduleBuildFiles: ConfigurableFileCollection

    @get:Input
    abstract val modulePackageRoots: MapProperty<String, String>

    @get:Input
    abstract val runtimeModuleLayers: MapProperty<String, String>

    @get:Input
    abstract val allowedDependencyLayers: MapProperty<String, String>

    @get:Input
    abstract val toolingModules: SetProperty<String>

    @TaskAction
    fun verifyDependencyBoundaries() {
        ArchitectureGateVerifiers.verifyModuleDependencyBoundaries(
            repository = repositoryDirectory.get().asFile,
            settingsFile = settingsFile.get().asFile,
            modulePackageRoots = modulePackageRoots.get(),
            runtimeModuleLayers = runtimeModuleLayers.get(),
            allowedDependencyLayers = allowedDependencyLayers.get().mapValues { (_, encoded) ->
                encoded.split(',').filter(String::isNotEmpty).toSet()
            },
            toolingModules = toolingModules.get(),
        ).failOnViolation()
    }
}

/** Verifies neutral layers and named design-system artifacts remain isolated. */
abstract class VerifyDesignSystemIsolationTask : DefaultTask() {
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceSetDirectories: ConfigurableFileCollection

    @get:Input
    abstract val dependencyDeclarations: ListProperty<String>

    @TaskAction
    fun verifyDesignSystems() {
        ArchitectureGateVerifiers.verifyDesignSystemIsolation(
            repository = repositoryDirectory.get().asFile,
            sourceSetDirectories = sourceSetDirectories.files,
            dependencyDeclarations = dependencyDeclarations.get().map(::decodeDependencyDeclaration),
        ).failOnViolation()
    }
}

/** Verifies UI Foundation delegates Android execution to the Android Engine layer. */
abstract class VerifyUiFoundationPlatformBoundaryTask : DefaultTask() {
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceSetDirectories: ConfigurableFileCollection

    @TaskAction
    fun verifyPlatformBoundary() {
        ArchitectureGateVerifiers.verifyUiFoundationPlatformBoundary(
            repository = repositoryDirectory.get().asFile,
            sourceSetDirectories = sourceSetDirectories.files,
        ).failOnViolation()
    }
}

internal data class DependencyDeclaration(
    val module: String,
    val configuration: String,
    val group: String?,
    val name: String,
)

internal fun DependencyDeclaration.encode(): String =
    listOf(module, configuration, group.orEmpty(), name).joinToString("|")

private fun decodeDependencyDeclaration(encoded: String): DependencyDeclaration {
    val fields = encoded.split('|', limit = 4)
    require(fields.size == 4) { "Invalid dependency declaration input: '$encoded'" }
    return DependencyDeclaration(
        module = fields[0],
        configuration = fields[1],
        group = fields[2].ifEmpty { null },
        name = fields[3],
    )
}

internal object ArchitectureGateVerifiers {
    fun verifyModulePackageRoots(
        repository: File,
        modulePackageRoots: Map<String, String>,
        forbiddenLegacyPackageRoots: Set<String>,
        sourceSetDirectories: Set<File>,
    ): QualityGateOutcome {
        val canonicalRepository = repository.canonicalFile
        val packageRegex = Regex("^\\s*package\\s+([A-Za-z0-9_.]+)", RegexOption.MULTILINE)
        val violations = mutableListOf<String>()
        val selectedPaths = mutableSetOf<String>()
        val availableSourceDirectories = sourceSetDirectories
            .map { sourceDirectory -> sourceDirectory.canonicalFile }
            .filter { sourceDirectory -> sourceDirectory.isDirectory }
            .toSet()

        modulePackageRoots.entries
            .groupBy(Map.Entry<String, String>::value)
            .filterValues { owners -> owners.size > 1 }
            .forEach { (packageRoot, owners) ->
                violations +=
                    "package root '$packageRoot' has multiple owners: " +
                        owners.map(Map.Entry<String, String>::key).sorted().joinToString()
            }

        modulePackageRoots.forEach { (module, packageRoot) ->
            if (forbiddenLegacyPackageRoots.any { legacy ->
                    packageRoot == legacy || packageRoot.startsWith("$legacy.")
                }
            ) {
                violations += "$module -> canonical package root '$packageRoot' uses a retired taxonomy"
            }
        }

        modulePackageRoots.forEach { (module, expectedPrefix) ->
            val srcDirectory = canonicalRepository.resolve(module).resolve("src").canonicalFile
            if (srcDirectory !in availableSourceDirectories) return@forEach
            architectureSourceSets.forEach sourceSetLoop@{ sourceSet ->
                val sourceSetDirectory = srcDirectory.resolve(sourceSet)
                if (!sourceSetDirectory.exists()) return@sourceSetLoop
                sourceSetDirectory.walkTopDown()
                    .filter { file ->
                        file.isFile && (file.extension == "kt" || file.extension == "java")
                    }
                    .forEach fileLoop@{ file ->
                        selectedPaths += file.repositoryRelativePath(canonicalRepository)
                        val content = file.readText()
                        val packageName = packageRegex.find(content)?.groupValues?.getOrNull(1)
                        if (packageName == null) {
                            violations +=
                                "$module:$sourceSet:${file.relativeTo(canonicalRepository)} -> " +
                                    "missing package declaration"
                            return@fileLoop
                        }
                        if (packageName != expectedPrefix && !packageName.startsWith("$expectedPrefix.")) {
                            violations +=
                                "$module:$sourceSet:${file.relativeTo(canonicalRepository)} -> " +
                                    "package '$packageName' not under '$expectedPrefix'"
                        }
                        val canonicalOwner = modulePackageRoots.entries
                            .filter { (_, registeredRoot) ->
                                packageName == registeredRoot || packageName.startsWith("$registeredRoot.")
                            }
                            .maxByOrNull { (_, registeredRoot) -> registeredRoot.length }
                        if (canonicalOwner != null && canonicalOwner.key != module) {
                            violations +=
                                "$module:$sourceSet:${file.relativeTo(canonicalRepository)} -> " +
                                    "package '$packageName' belongs to the more-specific root " +
                                    "'${canonicalOwner.value}' owned by '${canonicalOwner.key}'"
                        }
                        forbiddenLegacyPackageRoots.firstOrNull { legacy ->
                            packageName == legacy || packageName.startsWith("$legacy.")
                        }?.let { legacy ->
                            violations +=
                                "$module:$sourceSet:${file.relativeTo(canonicalRepository)} -> " +
                                    "package '$packageName' uses retired root '$legacy'"
                        }
                    }
            }

            val serviceDirectory = srcDirectory.resolve("main/resources/META-INF/services")
            if (serviceDirectory.exists()) {
                serviceDirectory.listFiles().orEmpty()
                    .filter(File::isFile)
                    .forEach { serviceFile ->
                        selectedPaths += serviceFile.repositoryRelativePath(canonicalRepository)
                        val declarations = listOf(serviceFile.name) +
                            serviceFile.readLines().map(String::trim).filter(String::isNotEmpty)
                        declarations.forEach { declaration ->
                            forbiddenLegacyPackageRoots.firstOrNull { legacy ->
                                declaration == legacy || declaration.startsWith("$legacy.")
                            }?.let { legacy ->
                                violations +=
                                    "${serviceFile.relativeTo(canonicalRepository)} -> service declaration " +
                                        "'$declaration' uses retired root '$legacy'"
                            }
                        }
                    }
            }
        }

        return gateOutcome(
            header = "Module package-root verification failed:",
            violations = violations,
            selectedPaths = selectedPaths,
        )
    }

    fun verifyAndroidModuleNamespaces(
        repository: File,
        modulePackageRoots: Map<String, String>,
        kotlinJvmModules: Set<String>,
    ): QualityGateOutcome {
        val canonicalRepository = repository.canonicalFile
        val namespaceRegex = Regex("""namespace\s*=\s*"([^"]+)"""")
        val violations = mutableListOf<String>()
        val selectedPaths = mutableSetOf<String>()

        modulePackageRoots.forEach { (module, packageRoot) ->
            if (module in kotlinJvmModules) return@forEach
            val buildFile = canonicalRepository.resolve(module).resolve("build.gradle.kts")
            if (!buildFile.exists()) {
                violations += "$module -> missing build.gradle.kts"
                return@forEach
            }
            selectedPaths += buildFile.repositoryRelativePath(canonicalRepository)
            val actualNamespace = namespaceRegex.find(buildFile.readText())?.groupValues?.getOrNull(1)
            if (actualNamespace == null) {
                violations += "$module -> missing namespace declaration"
                return@forEach
            }
            if (actualNamespace != packageRoot) {
                violations += "$module -> namespace '$actualNamespace' != expected '$packageRoot'"
            }
        }

        return gateOutcome(
            header = "Android namespace verification failed:",
            violations = violations,
            selectedPaths = selectedPaths,
        )
    }

    fun verifyModuleDependencyBoundaries(
        repository: File,
        settingsFile: File,
        modulePackageRoots: Map<String, String>,
        runtimeModuleLayers: Map<String, String>,
        allowedDependencyLayers: Map<String, Set<String>>,
        toolingModules: Set<String>,
    ): QualityGateOutcome {
        val canonicalRepository = repository.canonicalFile
        val violations = mutableListOf<String>()
        val selectedPaths = mutableSetOf(settingsFile.repositoryRelativePath(canonicalRepository))
        val moduleReferenceRegex = Regex("""":(viewcompose-[^\"]+)"""")
        val projectDependencyRegex = Regex("""project\(\s*(?:path\s*=\s*)?"(:[^\"]+)"""")
        val declaredModules = moduleReferenceRegex.findAll(settingsFile.readText())
            .map { match -> match.groupValues[1] }
            .toSet()
        val classifiedModules = runtimeModuleLayers.keys + toolingModules

        classifiedModules.sorted().forEach { module ->
            val memberships = listOf(
                module in runtimeModuleLayers,
                module in toolingModules,
            ).count { membership -> membership }
            if (memberships != 1) {
                violations += "$module -> module must belong to exactly one dependency-boundary group"
            }
        }

        (declaredModules - classifiedModules).sorted().forEach { module ->
            violations +=
                "$module -> unclassified module; register it in the five-layer runtime map or tooling"
        }
        (classifiedModules - declaredModules).sorted().forEach { module ->
            violations += "$module -> boundary classification has no matching module in settings.gradle.kts"
        }
        (declaredModules - modulePackageRoots.keys).sorted().forEach { module ->
            violations += "$module -> missing canonical package-root registration"
        }

        val dependenciesByModule = declaredModules.associateWith { module ->
            val buildFile = canonicalRepository.resolve(module).resolve("build.gradle.kts")
            if (!buildFile.exists()) {
                violations += "$module -> missing build.gradle.kts"
                emptySet()
            } else {
                selectedPaths += buildFile.repositoryRelativePath(canonicalRepository)
                projectDependencyRegex.findAll(buildFile.readText())
                    .map { match -> match.groupValues[1].removePrefix(":") }
                    .toSet()
            }
        }

        runtimeModuleLayers.forEach { (module, layer) ->
            val allowedLayers = allowedDependencyLayers.getValue(layer)
            dependenciesByModule[module].orEmpty()
                .filter { dependency ->
                    val dependencyLayer = runtimeModuleLayers[dependency]
                    dependency in toolingModules ||
                        (dependencyLayer != null && dependencyLayer !in allowedLayers)
                }
                .sorted()
                .forEach { dependency ->
                    val dependencyLayer = runtimeModuleLayers[dependency] ?: "tooling"
                    violations +=
                        "$module ($layer) -> forbidden dependency '$dependency' ($dependencyLayer); " +
                            "the five-layer dependency direction must remain acyclic"
                }
        }

        dependenciesByModule.forEach { (module, dependencies) ->
            if ("app" in dependencies) {
                violations += "$module -> framework modules must not depend on the demo app"
            }
            dependencies
                .filter { dependency ->
                    dependency.startsWith("viewcompose-") && dependency !in declaredModules
                }
                .sorted()
                .forEach { dependency ->
                    violations += "$module -> dependency '$dependency' is not declared in settings.gradle.kts"
                }
        }

        return gateOutcome(
            header = "Module dependency-boundary verification failed:",
            violations = violations,
            selectedPaths = selectedPaths,
        )
    }

    fun verifyDesignSystemIsolation(
        repository: File,
        sourceSetDirectories: Set<File>,
        dependencyDeclarations: List<DependencyDeclaration>,
    ): QualityGateOutcome {
        val canonicalRepository = repository.canonicalFile
        val violations = mutableListOf<String>()
        val selectedPaths = mutableSetOf<String>()
        val availableSourceDirectories = sourceSetDirectories
            .map { sourceDirectory -> sourceDirectory.canonicalFile }
            .filter { sourceDirectory -> sourceDirectory.isDirectory }
            .toSet()
        val materialFreeModules = listOf(
            "viewcompose-ui-foundation",
            "viewcompose-renderer-android",
            "viewcompose-host-android",
            "viewcompose-android",
        )

        materialFreeModules.forEach { module ->
            productionDependencyConfigurations.forEach { configurationName ->
                dependencyDeclarations
                    .filter { dependency ->
                        dependency.module == module &&
                            dependency.configuration == configurationName &&
                            dependency.group == "com.google.android.material"
                    }
                    .forEach { dependency ->
                        violations +=
                            "$module:$configurationName -> forbidden Material dependency " +
                                "'${dependency.group}:${dependency.name}'"
                    }
                dependencyDeclarations
                    .filter { dependency ->
                        dependency.module == module &&
                            dependency.configuration == configurationName &&
                            dependency.name == "viewcompose-material3"
                    }
                    .forEach { dependency ->
                        violations +=
                            "$module:$configurationName -> forbidden Material project dependency " +
                                "'${dependency.name}'"
                    }
            }

            sourceFilesForModule(canonicalRepository, availableSourceDirectories, module)
                .forEach { file ->
                    selectedPaths += file.repositoryRelativePath(canonicalRepository)
                    file.useLines { lines ->
                        lines.forEachIndexed { index, line ->
                            val trimmed = line.trimStart()
                            if (trimmed.startsWith("import com.google.android.material.")) {
                                violations +=
                                    "${file.relativeTo(canonicalRepository)}:${index + 1} -> " +
                                        "forbidden Material import '$trimmed'"
                            }
                            if (module == "viewcompose-android" && "com.viewcompose.material3" in line) {
                                violations +=
                                    "${file.relativeTo(canonicalRepository)}:${index + 1} -> " +
                                        "neutral Android aggregate cannot reference Material '$trimmed'"
                            }
                            if (
                                module == "viewcompose-ui-foundation" &&
                                trimmed.startsWith("import androidx.")
                            ) {
                                violations +=
                                    "${file.relativeTo(canonicalRepository)}:${index + 1} -> " +
                                        "UI Foundation cannot import AndroidX '$trimmed'"
                            }
                        }
                    }
                }
        }

        val namedSystemProjects = setOf("viewcompose-material3", "viewcompose-oneui7")
        val namedSystemPackages = setOf("com.viewcompose.material3", "com.viewcompose.oneui7")
        materialFreeModules.forEach { module ->
            productionDependencyConfigurations.forEach { configurationName ->
                dependencyDeclarations
                    .filter { dependency ->
                        dependency.module == module &&
                            dependency.configuration == configurationName &&
                            dependency.name in namedSystemProjects
                    }
                    .forEach { dependency ->
                        violations +=
                            "$module:$configurationName -> neutral module cannot depend on " +
                                "named design system '${dependency.name}'"
                    }
            }
            sourceFilesForModule(canonicalRepository, availableSourceDirectories, module)
                .forEach { file ->
                    file.useLines { lines ->
                        lines.forEachIndexed { index, line ->
                            val trimmed = line.trimStart()
                            if (namedSystemPackages.any { prefix -> trimmed.startsWith("import $prefix.") }) {
                                violations +=
                                    "${file.relativeTo(canonicalRepository)}:${index + 1} -> " +
                                        "neutral module cannot import named design system '$trimmed'"
                            }
                        }
                    }
                }
        }

        mapOf(
            "viewcompose-material3" to setOf("viewcompose-oneui7"),
            "viewcompose-oneui7" to setOf("viewcompose-material3"),
        ).forEach { (module, forbiddenProjects) ->
            productionDependencyConfigurations.forEach { configurationName ->
                dependencyDeclarations
                    .filter { dependency ->
                        dependency.module == module &&
                            dependency.configuration == configurationName &&
                            dependency.name in forbiddenProjects
                    }
                    .forEach { dependency ->
                        violations +=
                            "$module:$configurationName -> named systems cannot depend on " +
                                "each other ('${dependency.name}')"
                    }
            }
        }

        val oneUiModules = listOf("viewcompose-oneui7", "viewcompose-overlay-oneui7-android")
        oneUiModules.forEach { module ->
            productionDependencyConfigurations.forEach { configurationName ->
                dependencyDeclarations
                    .filter { dependency ->
                        dependency.module == module &&
                            dependency.configuration == configurationName &&
                            dependency.group == "com.google.android.material"
                    }
                    .forEach { dependency ->
                        violations +=
                            "$module:$configurationName -> forbidden Material dependency " +
                                "'${dependency.group}:${dependency.name}'"
                    }
            }
            sourceFilesForModule(canonicalRepository, availableSourceDirectories, module)
                .forEach { file ->
                    selectedPaths += file.repositoryRelativePath(canonicalRepository)
                    file.useLines { lines ->
                        lines.forEachIndexed { index, line ->
                            val trimmed = line.trimStart()
                            if (
                                trimmed.startsWith("import com.viewcompose.material3.") ||
                                trimmed.startsWith("import com.google.android.material.")
                            ) {
                                violations +=
                                    "${file.relativeTo(canonicalRepository)}:${index + 1} -> " +
                                        "One UI cannot import Material policy '$trimmed'"
                            }
                        }
                    }
                }
        }

        productionDependencyConfigurations.forEach { configurationName ->
            dependencyDeclarations
                .filter { dependency ->
                    dependency.module == "viewcompose-ui-foundation" &&
                        dependency.configuration == configurationName &&
                        dependency.group?.startsWith("androidx.") == true
                }
                .forEach { dependency ->
                    violations +=
                        "viewcompose-ui-foundation:$configurationName -> forbidden AndroidX " +
                            "dependency '${dependency.group}:${dependency.name}'"
                }
        }

        return gateOutcome(
            header = "Design-system isolation verification failed:",
            violations = violations,
            selectedPaths = selectedPaths,
        )
    }

    fun verifyUiFoundationPlatformBoundary(
        repository: File,
        sourceSetDirectories: Set<File>,
    ): QualityGateOutcome {
        val canonicalRepository = repository.canonicalFile
        val forbiddenImports = setOf(
            "android.content.Context",
            "android.os.LocaleList",
            "android.os.Trace",
            "android.util.Log",
            "android.view.View",
            "android.view.ViewGroup",
        )
        val violations = mutableListOf<String>()
        val selectedPaths = mutableSetOf<String>()
        val availableSourceDirectories = sourceSetDirectories
            .map { sourceDirectory -> sourceDirectory.canonicalFile }
            .filter { sourceDirectory -> sourceDirectory.isDirectory }
            .toSet()

        sourceFilesForModule(
            repository = canonicalRepository,
            sourceSetDirectories = availableSourceDirectories,
            module = "viewcompose-ui-foundation",
        ).forEach { file ->
            selectedPaths += file.repositoryRelativePath(canonicalRepository)
            file.useLines { lines ->
                lines.forEachIndexed { index, line ->
                    val importedType = line.trim()
                        .takeIf { trimmed -> trimmed.startsWith("import ") }
                        ?.removePrefix("import ")
                    if (importedType in forbiddenImports) {
                        violations +=
                            "${file.relativeTo(canonicalRepository)}:${index + 1} -> " +
                                "Android execution import '$importedType' belongs in Android Engine"
                    }
                }
            }
        }

        return gateOutcome(
            header = "UI Foundation platform-boundary verification failed:",
            violations = violations,
            selectedPaths = selectedPaths,
        )
    }

    private fun sourceFilesForModule(
        repository: File,
        sourceSetDirectories: Set<File>,
        module: String,
    ): Sequence<File> {
        val sourceDirectory = repository.resolve(module).resolve("src").canonicalFile
        if (sourceDirectory !in sourceSetDirectories) return emptySequence()
        val mainDirectory = sourceDirectory.resolve("main")
        if (!mainDirectory.exists()) return emptySequence()
        return mainDirectory.walkTopDown()
            .filter { file -> file.isFile && (file.extension == "kt" || file.extension == "java") }
    }
}

private fun gateOutcome(
    header: String,
    violations: Collection<String>,
    selectedPaths: Collection<String>,
): QualityGateOutcome {
    val sortedViolations = violations.sorted()
    return QualityGateOutcome(
        succeeded = sortedViolations.isEmpty(),
        diagnostics = if (sortedViolations.isEmpty()) {
            emptyList()
        } else {
            listOf(
                buildString {
                    appendLine(header)
                    sortedViolations.forEach { violation -> appendLine("- $violation") }
                },
            )
        },
        selectedPaths = selectedPaths.sorted(),
    )
}

private fun QualityGateOutcome.failOnViolation() {
    if (!succeeded) error(diagnostics.joinToString("\n"))
}

private fun File.repositoryRelativePath(repository: File): String =
    canonicalFile.relativeTo(repository.canonicalFile).invariantSeparatorsPath
