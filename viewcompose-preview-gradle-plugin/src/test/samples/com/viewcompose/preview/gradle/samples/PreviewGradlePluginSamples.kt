package com.viewcompose.preview.gradle.samples

import org.gradle.api.Project

fun applyPreviewPluginSample(project: Project) {
    project.pluginManager.apply("com.viewcompose.preview")
    project.tasks.named("viewComposePreviewDescriptors")
}
