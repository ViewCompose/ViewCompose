package com.viewcompose.preview.gradle

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

/**
 * Public Gradle bridge between Android variants and the isolated static-preview worker.
 */
class ViewComposePreviewGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
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
) {
    androidComponents.onVariants(androidComponents.selector().all()) { variant ->
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
            discovery.runtimeClasspath.from(variant.runtimeConfiguration)
            discovery.bootClasspath.from(androidComponents.sdkComponents.bootClasspath)
            discovery.sourceDirectories.from(variant.sources.java?.all)
            discovery.sourceDirectories.from(variant.sources.kotlin?.all)
            discovery.resourceDirectories.from(variant.sources.res?.all)
            discovery.mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            val artifactRoot = project.layout.buildDirectory.dir(
                "viewcompose-preview/${variant.name}",
            )
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
        aggregate.configure { taskGroup -> taskGroup.dependsOn(task) }
    }
}

private const val TASK_GROUP = "viewcompose preview"
