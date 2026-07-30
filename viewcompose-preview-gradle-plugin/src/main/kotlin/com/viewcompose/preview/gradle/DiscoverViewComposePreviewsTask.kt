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
    abstract val runtimeClasspath: ConfigurableFileCollection

    @get:Classpath
    abstract val bootClasspath: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectories: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourceDirectories: ConfigurableFileCollection

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
        val runtimeFiles = runtimeClasspath.files
        val bootFiles = bootClasspath.files
        val sourceFiles = sourceDirectories.files
        val resourceFiles = resourceDirectories.files
        val manifestFile = mergedManifest.get().asFile
        val fingerprintGroups = linkedMapOf(
            "boot-classpath" to bootFiles,
            "manifest" to listOf(manifestFile),
            "project-class-directories" to projectDirectories,
            "project-class-jars" to projectJars,
            "resources" to resourceFiles,
            "runtime-classpath" to runtimeFiles,
            "sources" to sourceFiles,
        )
        val fingerprint = PreviewInputFingerprint.calculate(fingerprintGroups)
        val discovery = CompiledPreviewScanner(
            projectClassDirectories = projectDirectories,
            projectClassJars = projectJars,
            annotationClasspath = runtimeFiles,
            sourceDirectories = sourceFiles,
        ).scan()
        val inputs = buildList {
            addInput(PreviewBuildInputKind.ProjectClassDirectory, projectDirectories)
            addInput(PreviewBuildInputKind.ProjectClassJar, projectJars)
            addInput(PreviewBuildInputKind.RuntimeClasspath, runtimeFiles)
            addInput(PreviewBuildInputKind.BootClasspath, bootFiles)
            addInput(PreviewBuildInputKind.SourceDirectory, sourceFiles)
            addInput(PreviewBuildInputKind.ResourceDirectory, resourceFiles)
        }.sortedBy { input -> input.kind.ordinal }
        val manifest = PreviewBuildManifest(
            modulePath = modulePath.get(),
            buildVariant = buildVariant.get(),
            namespace = namespace.get(),
            androidGradlePluginVersion = androidGradlePluginVersion.get(),
            minSdk = minSdk.get(),
            targetSdk = targetSdk.get(),
            sdkDirectory = sdkDirectoryPath.get(),
            mergedManifestPath = manifestFile.normalizedAbsolutePath(),
            artifactRootDirectory = artifactRootDirectory.get().asFile.normalizedAbsolutePath(),
            inputs = inputs,
            inputFingerprint = fingerprint,
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
