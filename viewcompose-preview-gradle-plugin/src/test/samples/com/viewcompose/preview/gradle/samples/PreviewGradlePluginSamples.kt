package com.viewcompose.preview.gradle.samples

import org.gradle.api.Project

// DOCS_REGION_START(preview-gradle-apply)
fun applyPreviewPluginSample(project: Project) {
    project.pluginManager.apply("com.viewcompose.preview")
    project.tasks.named("viewComposePreviewDescriptors")
}
// DOCS_REGION_END(preview-gradle-apply)
