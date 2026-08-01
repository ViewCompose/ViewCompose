package com.viewcompose.preview.gradle

import com.viewcompose.preview.tooling.PreviewBuildInput
import com.viewcompose.preview.tooling.PreviewBuildInputKind
import com.viewcompose.preview.tooling.PreviewBuildManifest
import com.viewcompose.preview.tooling.PreviewDescriptorCatalog
import com.viewcompose.preview.tooling.PreviewProtocolJson
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Exports one Android variant without loading application classes into the Gradle daemon.
 */
@DisableCachingByDefault(
    because = "The manifest intentionally contains machine-local absolute paths for the IDE worker.",
)
abstract class DiscoverViewComposePreviewsTask : DefaultTask() {
    @get:Input
    abstract val modulePath: Property<String>

    @get:Input
    abstract val buildVariant: Property<String>

    @get:Input
    abstract val namespace: Property<String>

    @get:Input
    abstract val androidGradlePluginVersion: Property<String>

    @get:Input
    abstract val minSdk: Property<Int>

    @get:Input
    abstract val targetSdk: Property<Int>

    @get:Input
    abstract val sdkDirectoryPath: Property<String>

    @get:Classpath
    abstract val projectClassJars: ListProperty<RegularFile>

    @get:Classpath
    abstract val projectClassDirectories: ListProperty<Directory>

    @get:Classpath
    abstract val annotationClasspath: ConfigurableFileCollection

    @get:Classpath
    abstract val runtimeClasspath: ConfigurableFileCollection

    @get:Classpath
    abstract val bootClasspath: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectories: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val localResourceDirectories: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val moduleResourceDirectories: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val libraryResourceDirectories: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val localAssetDirectories: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val moduleAssetDirectories: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val libraryAssetDirectories: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val resourcePackageFiles: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mergedManifest: RegularFileProperty

    @get:Internal
    abstract val artifactRootDirectory: DirectoryProperty

    @get:OutputFile
    abstract val buildManifestFile: RegularFileProperty

    @get:OutputFile
    abstract val descriptorCatalogFile: RegularFileProperty

    @TaskAction
    fun discover() {
        val projectJars = projectClassJars.get().map(RegularFile::getAsFile)
        val projectDirectories = projectClassDirectories.get().map(Directory::getAsFile)
        val annotationFiles = annotationClasspath.files
        val runtimeFiles = runtimeClasspath.files
        val bootFiles = bootClasspath.files
        val sourceFiles = sourceDirectories.files
        val localResourceFiles = localResourceDirectories.files
        val moduleResourceFiles = moduleResourceDirectories.files
        val libraryResourceFiles = libraryResourceDirectories.files
        val localAssetFiles = localAssetDirectories.files
        val moduleAssetFiles = moduleAssetDirectories.files
        val libraryAssetFiles = libraryAssetDirectories.files
        val packageFiles = resourcePackageFiles.files
        val manifestFile = mergedManifest.get().asFile
        val resolvedNamespace = namespace.get()
        val compileSdk = resolveCompileSdk(bootFiles)
        val resourcePackageNames = buildResourcePackageNames(
            namespace = resolvedNamespace,
            packageFiles = packageFiles,
        )
        val fingerprintGroups = linkedMapOf(
            "assets-library" to libraryAssetFiles,
            "assets-local" to localAssetFiles,
            "assets-module" to moduleAssetFiles,
            "annotation-classpath" to annotationFiles,
            "boot-classpath" to bootFiles,
            "manifest" to listOf(manifestFile),
            "project-class-directories" to projectDirectories,
            "project-class-jars" to projectJars,
            "resource-packages" to packageFiles,
            "resources-library" to libraryResourceFiles,
            "resources-local" to localResourceFiles,
            "resources-module" to moduleResourceFiles,
            "runtime-classpath" to runtimeFiles,
            "sources" to sourceFiles,
        )
        val groupFingerprints = PreviewInputFingerprint.calculateByGroup(fingerprintGroups)
        val fingerprint = PreviewInputFingerprint.combine(groupFingerprints)
        val layoutlibCompatibilityFingerprint = PreviewInputFingerprint.combine(
            buildMap {
                LAYOUTLIB_COMPATIBILITY_GROUPS.forEach { group ->
                    groupFingerprints[group]?.let { value -> put(group, value) }
                }
                put("compile-sdk", compileSdk.toString())
                put("namespace", resolvedNamespace)
                put("resource-package-names", resourcePackageNames.joinToString("\n"))
            },
        )
        val discovery = CompiledPreviewScanner(
            projectClassDirectories = projectDirectories,
            projectClassJars = projectJars,
            annotationClasspath = annotationFiles,
            sourceDirectories = sourceFiles,
        ).scan()
        val inputs = buildList {
            addInput(PreviewBuildInputKind.ProjectClassDirectory, projectDirectories)
            addInput(PreviewBuildInputKind.ProjectClassJar, projectJars)
            addInput(PreviewBuildInputKind.RuntimeClasspath, runtimeFiles)
            addInput(PreviewBuildInputKind.BootClasspath, bootFiles)
            addInput(PreviewBuildInputKind.SourceDirectory, sourceFiles)
            addInput(PreviewBuildInputKind.LocalResourceDirectory, localResourceFiles)
            addInput(PreviewBuildInputKind.ModuleResourceDirectory, moduleResourceFiles)
            addInput(PreviewBuildInputKind.LibraryResourceDirectory, libraryResourceFiles)
            addInput(PreviewBuildInputKind.LocalAssetDirectory, localAssetFiles)
            addInput(PreviewBuildInputKind.ModuleAssetDirectory, moduleAssetFiles)
            addInput(PreviewBuildInputKind.LibraryAssetDirectory, libraryAssetFiles)
            addInput(PreviewBuildInputKind.ResourcePackageFile, packageFiles)
        }.sortedBy { input -> input.kind.ordinal }
        val manifest = PreviewBuildManifest(
            modulePath = modulePath.get(),
            buildVariant = buildVariant.get(),
            namespace = resolvedNamespace,
            androidGradlePluginVersion = androidGradlePluginVersion.get(),
            minSdk = minSdk.get(),
            targetSdk = targetSdk.get(),
            compileSdk = compileSdk,
            sdkDirectory = sdkDirectoryPath.get(),
            mergedManifestPath = manifestFile.normalizedAbsolutePath(),
            artifactRootDirectory = artifactRootDirectory.get().asFile.normalizedAbsolutePath(),
            resourcePackageNames = resourcePackageNames,
            inputs = inputs,
            inputFingerprint = fingerprint,
            layoutlibCompatibilityFingerprint = layoutlibCompatibilityFingerprint,
        )
        val catalog = PreviewDescriptorCatalog(
            modulePath = modulePath.get(),
            buildVariant = buildVariant.get(),
            buildFingerprint = fingerprint,
            descriptors = discovery.descriptors,
            diagnostics = discovery.diagnostics,
        )
        buildManifestFile.get().asFile.writeTextAtomically(
            PreviewProtocolJson.encodeBuildManifest(manifest),
        )
        descriptorCatalogFile.get().asFile.writeTextAtomically(
            PreviewProtocolJson.encodeDescriptorCatalog(catalog),
        )
    }

    private fun MutableList<PreviewBuildInput>.addInput(
        kind: PreviewBuildInputKind,
        files: Collection<File>,
    ) {
        val paths = files.asSequence()
            .filter(File::exists)
            .map(File::normalizedAbsolutePath)
            .distinct()
            .sorted()
            .toList()
        if (paths.isNotEmpty()) {
            add(PreviewBuildInput(kind = kind, paths = paths))
        }
    }
}

internal fun resolveCompileSdk(bootClasspath: Collection<File>): Int {
    val apiLevels = bootClasspath.asSequence()
        .map(File::normalizedAbsolutePath)
        .mapNotNull { path ->
            ANDROID_PLATFORM_PATTERN.find(path)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
        }
        .distinct()
        .sorted()
        .toList()
    require(apiLevels.size == 1) {
        "Could not resolve one Android compile SDK from boot classpath: " +
            bootClasspath.joinToString { file -> file.normalizedAbsolutePath() }
    }
    return apiLevels.single()
}

internal fun buildResourcePackageNames(
    namespace: String,
    packageFiles: Collection<File>,
): List<String> {
    return buildList {
        add(namespace)
        packageFiles.asSequence()
            .filter(File::isFile)
            .mapNotNull { file ->
                file.useLines { lines ->
                    lines.firstOrNull(String::isNotBlank)?.trim()
                }
            }
            .forEach(::add)
    }.distinct().sorted()
}

private fun File.normalizedAbsolutePath(): String = absoluteFile.normalize().path

private fun File.writeTextAtomically(value: String) {
    parentFile?.let { parent ->
        check(parent.isDirectory || parent.mkdirs()) {
            "Could not create ViewCompose preview output directory '${parent.absolutePath}'."
        }
    }
    val temporary = resolveSibling("$name.tmp")
    temporary.writeText(value)
    if (exists()) {
        check(delete()) { "Could not replace ViewCompose preview output '$absolutePath'." }
    }
    check(temporary.renameTo(this)) {
        "Could not publish ViewCompose preview output '$absolutePath'."
    }
}

private fun File.resolveSibling(name: String): File = File(checkNotNull(parentFile), name)

private val ANDROID_PLATFORM_PATTERN = Regex("""[/\\]platforms[/\\]android-(\d+)[/\\]""")
private val LAYOUTLIB_COMPATIBILITY_GROUPS = setOf(
    "assets-library",
    "assets-local",
    "assets-module",
    "boot-classpath",
    "manifest",
    "resource-packages",
    "resources-library",
    "resources-local",
    "resources-module",
    "runtime-classpath",
)
