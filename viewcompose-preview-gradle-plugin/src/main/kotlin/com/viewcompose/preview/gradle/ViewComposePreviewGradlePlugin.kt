package com.viewcompose.preview.gradle

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import java.io.File
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.file.FileCollection
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

/**
 * Public Gradle bridge between Android variants and the isolated static-preview worker.
 *
 * @sample com.viewcompose.preview.gradle.samples.applyPreviewPluginSample
 */
class ViewComposePreviewGradlePlugin : Plugin<Project> {
    /**
     * Registers preview tooling when an Android application or library plugin becomes available.
     *
     * Debuggable variants receive discovery, render, and fast-refresh tasks plus isolated tool
     * configurations. Non-debuggable variants receive only project-class instrumentation that
     * removes direct and composed preview annotations before DEX/AAR packaging. Applying the plugin
     * before or after the Android plugin is supported; a project is configured at most once.
     *
     * @param project target Gradle project
     */
    override fun apply(project: Project) {
        val toolConfigurations = project.createPreviewToolConfigurations()
        val aggregate = project.tasks.register("viewComposePreviewDescriptors") { task ->
            task.group = TASK_GROUP
            task.description = "Exports ViewCompose preview descriptors for every Android variant."
        }
        var configured = false
        project.pluginManager.withPlugin("com.android.application") {
            if (!configured) {
                configured = true
                configureAndroidComponents(
                    project = project,
                    androidComponents = project.extensions.getByType(
                        ApplicationAndroidComponentsExtension::class.java,
                    ),
                    aggregate = aggregate,
                    toolConfigurations = toolConfigurations,
                )
            }
        }
        project.pluginManager.withPlugin("com.android.library") {
            if (!configured) {
                configured = true
                configureAndroidComponents(
                    project = project,
                    androidComponents = project.extensions.getByType(
                        LibraryAndroidComponentsExtension::class.java,
                    ),
                    aggregate = aggregate,
                    toolConfigurations = toolConfigurations,
                )
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun <DslT, BuilderT : VariantBuilder, VariantT : Variant> configureAndroidComponents(
    project: Project,
    androidComponents: AndroidComponentsExtension<DslT, BuilderT, VariantT>,
    aggregate: TaskProvider<Task>,
    toolConfigurations: PreviewToolConfigurations,
) {
    androidComponents.onVariants(androidComponents.selector().all()) { variant ->
        if (!variant.debuggable) {
            variant.instrumentation.transformClassesWith(
                StripViewComposePreviewAnnotationsVisitorFactory::class.java,
                InstrumentationScope.PROJECT,
            ) {}
            variant.instrumentation.setAsmFramesComputationMode(FramesComputationMode.COPY_FRAMES)
            return@onVariants
        }
        val annotationClasspath = variant.compileConfiguration.artifactFiles(
            artifactType = ANDROID_CLASSES_JAR_ARTIFACT_TYPE,
        )
        val runtimeClasspath = variant.runtimeConfiguration.artifactFiles(
            artifactType = ANDROID_CLASSES_JAR_ARTIFACT_TYPE,
        )
        val moduleResources = variant.runtimeConfiguration.artifactFiles(
            artifactType = ANDROID_RES_ARTIFACT_TYPE,
            componentFilter = { identifier -> identifier is ProjectComponentIdentifier },
        )
        val libraryResources = variant.runtimeConfiguration.artifactFiles(
            artifactType = ANDROID_RES_ARTIFACT_TYPE,
            componentFilter = { identifier -> identifier !is ProjectComponentIdentifier },
        )
        val moduleAssets = variant.runtimeConfiguration.artifactFiles(
            artifactType = ANDROID_ASSETS_ARTIFACT_TYPE,
            componentFilter = { identifier -> identifier is ProjectComponentIdentifier },
        )
        val libraryAssets = variant.runtimeConfiguration.artifactFiles(
            artifactType = ANDROID_ASSETS_ARTIFACT_TYPE,
            componentFilter = { identifier -> identifier !is ProjectComponentIdentifier },
        )
        val resourcePackageFiles = variant.runtimeConfiguration.artifactFiles(
            artifactType = ANDROID_SYMBOL_WITH_PACKAGE_ARTIFACT_TYPE,
        )
        val runnerClasspath = project.configurations.create(
            "viewComposePreview${variant.name.replaceFirstChar(Char::uppercase)}RunnerClasspath",
        ) { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true
            configuration.extendsFrom(toolConfigurations.runner)
            configuration.attributes.copyFrom(variant.runtimeConfiguration.attributes)
            configuration.description =
                "Android runner classpath for '${variant.name}' ViewCompose previews."
        }.artifactFiles(ANDROID_CLASSES_JAR_ARTIFACT_TYPE)
        val artifactRoot = project.layout.buildDirectory.dir(
            "viewcompose-preview/${variant.name}",
        )
        val task = project.tasks.register(
            variant.computeTaskName("discover", "ViewComposePreviews"),
            DiscoverViewComposePreviewsTask::class.java,
        ) { discovery ->
            discovery.group = TASK_GROUP
            discovery.description =
                "Discovers ViewCompose static previews for the '${variant.name}' variant."
            discovery.modulePath.set(project.path)
            discovery.buildVariant.set(variant.name)
            discovery.namespace.set(variant.namespace)
            discovery.androidGradlePluginVersion.set(androidComponents.pluginVersion.toString())
            discovery.minSdk.set(variant.minSdk.apiLevel)
            discovery.targetSdk.set(variant.targetSdkVersion.apiLevel)
            discovery.sdkDirectoryPath.set(
                androidComponents.sdkComponents.sdkDirectory.map { directory ->
                    directory.asFile.absolutePath
                },
            )
            discovery.annotationClasspath.from(annotationClasspath)
            discovery.runtimeClasspath.from(runtimeClasspath)
            discovery.bootClasspath.from(androidComponents.sdkComponents.bootClasspath)
            discovery.sourceDirectories.from(variant.sources.java?.all)
            discovery.sourceDirectories.from(variant.sources.kotlin?.all)
            discovery.localResourceDirectories.from(variant.sources.res?.all)
            discovery.moduleResourceDirectories.from(moduleResources)
            discovery.libraryResourceDirectories.from(libraryResources)
            discovery.localAssetDirectories.from(variant.sources.assets?.all)
            discovery.moduleAssetDirectories.from(moduleAssets)
            discovery.libraryAssetDirectories.from(libraryAssets)
            discovery.resourcePackageFiles.from(resourcePackageFiles)
            discovery.mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            discovery.artifactRootDirectory.set(artifactRoot)
            discovery.buildManifestFile.set(
                artifactRoot.map { directory -> directory.file("build-manifest.json") },
            )
            discovery.descriptorCatalogFile.set(
                artifactRoot.map { directory -> directory.file("descriptors.json") },
            )
        }
        variant.artifacts
            .forScope(com.android.build.api.variant.ScopedArtifacts.Scope.PROJECT)
            .use(task)
            .toGet(
                ScopedArtifact.CLASSES,
                DiscoverViewComposePreviewsTask::projectClassJars,
                DiscoverViewComposePreviewsTask::projectClassDirectories,
            )
        project.tasks.register(
            variant.computeTaskName("render", "ViewComposePreview"),
            RenderViewComposePreviewTask::class.java,
        ) { render ->
            render.group = TASK_GROUP
            render.description =
                "Renders one ViewCompose static preview for the '${variant.name}' variant."
            render.dependsOn(task)
            render.buildManifestFile.set(task.flatMap { discovery ->
                discovery.buildManifestFile
            })
            render.descriptorCatalogFile.set(task.flatMap { discovery ->
                discovery.descriptorCatalogFile
            })
            render.workerHostClasspath.from(toolConfigurations.workerHost)
            render.runnerClasspath.from(runnerClasspath)
            render.layoutlibRuntimeArchive.from(toolConfigurations.layoutlibRuntime)
            render.layoutlibResourcesArchive.from(toolConfigurations.layoutlibResources)
            render.renderToolchainFile.set(
                artifactRoot.map { directory -> directory.file(RENDER_TOOLCHAIN_FILE_NAME) },
            )
            render.previewId.convention(
                project.providers.gradleProperty(PREVIEW_ID_PROJECT_PROPERTY),
            )
            render.variantId.convention(
                project.providers.gradleProperty(PREVIEW_VARIANT_ID_PROJECT_PROPERTY),
            )
            render.batchTargetsFile.set(
                project.layout.file(
                    project.providers.gradleProperty(PREVIEW_TARGETS_FILE_PROJECT_PROPERTY)
                        .map(::File),
                ),
            )
            render.rerender.convention(
                project.providers.gradleProperty(PREVIEW_RERENDER_PROJECT_PROPERTY)
                    .map(String::toBooleanStrict)
                    .orElse(false),
            )
        }
        project.tasks.register(
            variant.computeTaskName("refresh", "ViewComposePreview"),
            RenderViewComposePreviewTask::class.java,
        ) { refresh ->
            refresh.group = TASK_GROUP
            refresh.description =
                "Incrementally compiles and refreshes one known ViewCompose preview for the " +
                    "'${variant.name}' variant."
            refresh.dependsOn(variant.computeTaskName("compile", "Sources"))
            refresh.fastRefresh.set(true)
            // These are deliberately direct file locations. Depending on the discovery provider
            // would pull the complete descriptor/resource graph back into the save fast path.
            refresh.buildManifestFile.set(
                artifactRoot.map { directory -> directory.file("build-manifest.json") },
            )
            refresh.descriptorCatalogFile.set(
                artifactRoot.map { directory -> directory.file("descriptors.json") },
            )
            refresh.fastBuildManifestFile.set(
                artifactRoot.map { directory -> directory.file(FAST_BUILD_MANIFEST_FILE_NAME) },
            )
            refresh.fastDescriptorCatalogFile.set(
                artifactRoot.map { directory -> directory.file(FAST_DESCRIPTOR_CATALOG_FILE_NAME) },
            )
            refresh.renderToolchainFile.set(
                artifactRoot.map { directory -> directory.file(RENDER_TOOLCHAIN_FILE_NAME) },
            )
            refresh.previewId.convention(
                project.providers.gradleProperty(PREVIEW_ID_PROJECT_PROPERTY),
            )
            refresh.variantId.convention(
                project.providers.gradleProperty(PREVIEW_VARIANT_ID_PROJECT_PROPERTY),
            )
            refresh.rerender.convention(
                project.providers.gradleProperty(PREVIEW_RERENDER_PROJECT_PROPERTY)
                    .map(String::toBooleanStrict)
                    .orElse(false),
            )
        }
        aggregate.configure { taskGroup -> taskGroup.dependsOn(task) }
    }
}

private data class PreviewToolConfigurations(
    val workerHost: Configuration,
    val runner: Configuration,
    val layoutlibRuntime: Configuration,
    val layoutlibResources: Configuration,
)

private fun Project.createPreviewToolConfigurations(): PreviewToolConfigurations {
    val workerHost = configurations.create(WORKER_HOST_CONFIGURATION_NAME) { configuration ->
        configuration.isCanBeConsumed = false
        configuration.isCanBeResolved = true
        configuration.description = "Standalone ViewCompose preview worker host classpath."
        configuration.attributes { attributes ->
            attributes.attribute(
                Usage.USAGE_ATTRIBUTE,
                objects.named(Usage::class.java, Usage.JAVA_RUNTIME),
            )
            attributes.attribute(
                Category.CATEGORY_ATTRIBUTE,
                objects.named(Category::class.java, Category.LIBRARY),
            )
            attributes.attribute(
                LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                objects.named(LibraryElements::class.java, LibraryElements.JAR),
            )
            attributes.attribute(
                Bundling.BUNDLING_ATTRIBUTE,
                objects.named(Bundling::class.java, Bundling.EXTERNAL),
            )
            attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17)
        }
    }
    val runner = configurations.create(RUNNER_CONFIGURATION_NAME) { configuration ->
        configuration.isCanBeConsumed = false
        configuration.isCanBeResolved = false
        configuration.description =
            "Android ViewCompose preview runner dependency bucket shared by all variants."
    }
    val runtime = configurations.create("viewComposePreviewLayoutlibRuntime") { configuration ->
        configuration.isCanBeConsumed = false
        configuration.isCanBeResolved = true
        configuration.isTransitive = false
        configuration.description = "Pinned native Layoutlib runtime for ViewCompose previews."
        configuration.defaultDependencies { dependencies ->
            dependencies.add(
                project.dependencies.create(
                    "com.android.tools.layoutlib:layoutlib-runtime:" +
                        "$LAYOUTLIB_NATIVE_VERSION:${currentLayoutlibClassifier()}",
                ),
            )
        }
    }
    val resources = configurations.create("viewComposePreviewLayoutlibResources") { configuration ->
        configuration.isCanBeConsumed = false
        configuration.isCanBeResolved = true
        configuration.isTransitive = false
        configuration.description = "Pinned framework Layoutlib resources for ViewCompose previews."
        configuration.defaultDependencies { dependencies ->
            dependencies.add(
                project.dependencies.create(
                    "com.android.tools.layoutlib:layoutlib-resources:$LAYOUTLIB_NATIVE_VERSION",
                ),
            )
        }
    }
    return PreviewToolConfigurations(
        workerHost = workerHost,
        runner = runner,
        layoutlibRuntime = runtime,
        layoutlibResources = resources,
    )
}

private fun AttributeContainer.copyFrom(source: AttributeContainer) {
    source.keySet().forEach { rawAttribute ->
        @Suppress("UNCHECKED_CAST")
        val key = rawAttribute as Attribute<Any>
        source.getAttribute(key)?.let { value ->
            attribute(key, value)
        }
    }
}

private fun currentLayoutlibClassifier(): String {
    val osName = System.getProperty("os.name").lowercase()
    return when {
        osName.startsWith("windows") -> "win"
        osName.startsWith("mac") -> {
            if (System.getProperty("os.arch").lowercase().startsWith("x86")) {
                "mac"
            } else {
                "mac-arm"
            }
        }
        else -> "linux"
    }
}

private fun Configuration.artifactFiles(
    artifactType: String,
    componentFilter: (ComponentIdentifier) -> Boolean = { true },
): FileCollection {
    return incoming.artifactView { view ->
        view.attributes.attribute(ARTIFACT_TYPE_ATTRIBUTE, artifactType)
        view.componentFilter(componentFilter)
    }.files
}

private const val TASK_GROUP = "viewcompose preview"
private const val ANDROID_RES_ARTIFACT_TYPE = "android-res"
private const val ANDROID_ASSETS_ARTIFACT_TYPE = "android-assets"
private const val ANDROID_SYMBOL_WITH_PACKAGE_ARTIFACT_TYPE =
    "android-symbol-with-package-name"
private const val ANDROID_CLASSES_JAR_ARTIFACT_TYPE = "android-classes-jar"
private const val LAYOUTLIB_NATIVE_VERSION = "15.2.3"
internal const val PREVIEW_ID_PROJECT_PROPERTY = "viewComposePreviewId"
internal const val PREVIEW_VARIANT_ID_PROJECT_PROPERTY = "viewComposePreviewVariantId"
internal const val PREVIEW_TARGETS_FILE_PROJECT_PROPERTY = "viewComposePreviewTargetsFile"
internal const val PREVIEW_RERENDER_PROJECT_PROPERTY = "viewComposePreviewRerender"
