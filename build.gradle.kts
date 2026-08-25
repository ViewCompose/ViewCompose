// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.viewcompose.quality.root")
    id("com.viewcompose.publishing.root")
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.paparazzi) apply false
}

extensions.configure<com.viewcompose.quality.ViewComposeQualityExtension> {
    repositoryDirectory.set(project.layout.projectDirectory)
    moduleCatalogFile.set(
        project.layout.projectDirectory.file("gradle/viewcompose-publishing.properties"),
    )
    sourceSetDirectories.from(
        project.subprojects.mapNotNull { subproject ->
            subproject.layout.projectDirectory.dir("src").asFile.takeIf { directory ->
                directory.isDirectory
            }
        },
    )
    policyFiles.from(
        project.layout.projectDirectory.file("AGENTS.md"),
        project.layout.projectDirectory.file("docs/project/documentation-governance.md"),
        project.layout.projectDirectory.file("docs/project/api-documentation-quality.md"),
        project.layout.projectDirectory.file("gradle/viewcompose-dependency-contracts.properties"),
    )
    reportsDirectory.set(
        project.layout.buildDirectory.dir("reports/viewcompose-quality"),
    )
}

val modulePackageRoots = mapOf(
    "app" to "com.viewcompose",
    "viewcompose-runtime" to "com.viewcompose.runtime",
    "viewcompose-text-core" to "com.viewcompose.text",
    "viewcompose-navigation-core" to "com.viewcompose.navigation.core",
    "viewcompose-navigation-android" to "com.viewcompose.navigation",
    "viewcompose-ui-contract" to "com.viewcompose.ui",
    "viewcompose-renderer-android" to "com.viewcompose.renderer",
    "viewcompose-ui-foundation" to "com.viewcompose.ui.foundation",
    "viewcompose-diagnostics" to "com.viewcompose.diagnostics",
    "viewcompose-host-android" to "com.viewcompose.host.android",
    "viewcompose-material3" to "com.viewcompose.material3",
    "viewcompose-material3-android" to "com.viewcompose.material3.android",
    "viewcompose-oneui7" to "com.viewcompose.oneui7",
    "viewcompose-android" to "com.viewcompose.android",
    "viewcompose-overlay-android" to "com.viewcompose.overlay.android",
    "viewcompose-overlay-material3-android" to "com.viewcompose.overlay.material3.android",
    "viewcompose-overlay-oneui7-android" to "com.viewcompose.overlay.oneui7.android",
    "viewcompose-image-coil" to "com.viewcompose.image.coil",
    "viewcompose-image-glide" to "com.viewcompose.image.glide",
    "viewcompose-benchmark" to "com.viewcompose.benchmark",
    "viewcompose-lifecycle-androidx" to "com.viewcompose.lifecycle",
    "viewcompose-viewmodel-androidx" to "com.viewcompose.viewmodel",
    "viewcompose-preview-core" to "com.viewcompose.preview.tooling",
    "viewcompose-preview-gradle-plugin" to "com.viewcompose.preview.gradle",
    "viewcompose-preview-runner" to "com.viewcompose.preview.runner",
    "viewcompose-preview-worker-host" to "com.viewcompose.preview.worker",
    "viewcompose-preview" to "com.viewcompose.preview",
    "viewcompose-animation" to "com.viewcompose.animation",
    "viewcompose-animation-core" to "com.viewcompose.animation.core",
    "viewcompose-gesture" to "com.viewcompose.gesture",
    "viewcompose-gesture-core" to "com.viewcompose.gesture.core",
    "viewcompose-graphics" to "com.viewcompose.graphics",
    "viewcompose-graphics-core" to "com.viewcompose.graphics.core",
    "viewcompose-shadow-android" to "com.viewcompose.shadow.android",
    "viewcompose-constraintlayout-androidx" to "com.viewcompose.constraintlayout",
    "viewcompose-media3-androidx" to "com.viewcompose.media3",
    "viewcompose-exoplayer2-android" to "com.viewcompose.exoplayer2",
    "viewcompose-google-maps-android" to "com.viewcompose.maps.google",
    "viewcompose-camerax-androidx" to "com.viewcompose.camerax",
    "viewcompose-paging-androidx" to "com.viewcompose.paging",
)

val forbiddenLegacyPackageRoots = setOf(
    "com.viewcompose.widget.core",
    "com.viewcompose.widget.constraintlayout",
)

val kotlinJvmModules = setOf(
    "viewcompose-ui-contract",
    "viewcompose-runtime",
    "viewcompose-text-core",
    "viewcompose-navigation-core",
    "viewcompose-preview-core",
    "viewcompose-preview-gradle-plugin",
    "viewcompose-preview-worker-host",
    "viewcompose-animation-core",
    "viewcompose-gesture-core",
    "viewcompose-graphics-core",
)

// Every published runtime module belongs to exactly one architectural layer. The layer gate is
// intentionally independent of Maven api/implementation visibility: both kinds of project edge
// must respect the same ownership direction.
val runtimeModuleLayers = mapOf(
    "viewcompose-runtime" to "kernel",
    "viewcompose-text-core" to "kernel",
    "viewcompose-navigation-core" to "kernel",
    "viewcompose-animation-core" to "kernel",
    "viewcompose-graphics-core" to "kernel",
    "viewcompose-ui-contract" to "kernel",
    "viewcompose-gesture-core" to "kernel",
    "viewcompose-ui-foundation" to "ui-foundation",
    "viewcompose-diagnostics" to "integration",
    "viewcompose-animation" to "ui-foundation",
    "viewcompose-gesture" to "ui-foundation",
    "viewcompose-graphics" to "ui-foundation",
    "viewcompose-renderer-android" to "android-engine",
    "viewcompose-host-android" to "android-engine",
    "viewcompose-material3" to "design-system",
    "viewcompose-oneui7" to "design-system",
    "viewcompose-navigation-android" to "integration",
    "viewcompose-material3-android" to "aggregate",
    "viewcompose-lifecycle-androidx" to "integration",
    "viewcompose-viewmodel-androidx" to "integration",
    "viewcompose-constraintlayout-androidx" to "integration",
    "viewcompose-media3-androidx" to "integration",
    "viewcompose-exoplayer2-android" to "integration",
    "viewcompose-google-maps-android" to "integration",
    "viewcompose-camerax-androidx" to "integration",
    "viewcompose-paging-androidx" to "integration",
    "viewcompose-overlay-android" to "integration",
    "viewcompose-overlay-material3-android" to "integration",
    "viewcompose-overlay-oneui7-android" to "integration",
    "viewcompose-image-coil" to "integration",
    "viewcompose-image-glide" to "integration",
    "viewcompose-shadow-android" to "integration",
    "viewcompose-android" to "aggregate",
)

val allowedDependencyLayers = mapOf(
    "kernel" to setOf("kernel"),
    "ui-foundation" to setOf("kernel", "ui-foundation"),
    "android-engine" to setOf("kernel", "ui-foundation", "android-engine"),
    "design-system" to setOf("kernel", "ui-foundation"),
    "integration" to setOf("kernel", "ui-foundation", "android-engine", "design-system", "integration"),
    "aggregate" to
        setOf("kernel", "ui-foundation", "android-engine", "design-system", "integration", "aggregate"),
)

// Tooling is downstream of both foundation and optional capabilities and never participates in
// the application runtime dependency direction.
val toolingModules = setOf(
    "viewcompose-preview-core",
    "viewcompose-preview-gradle-plugin",
    "viewcompose-preview-runner",
    "viewcompose-preview-worker-host",
    "viewcompose-preview",
    "viewcompose-benchmark",
)

val configuredModulePackageRoots = modulePackageRoots
val configuredForbiddenLegacyPackageRoots = forbiddenLegacyPackageRoots
val configuredKotlinJvmModules = kotlinJvmModules
val configuredRuntimeModuleLayers = runtimeModuleLayers
val configuredAllowedDependencyLayers = allowedDependencyLayers
val configuredToolingModules = toolingModules

extensions.configure<com.viewcompose.quality.ViewComposeQualityExtension> {
    settingsFile.set(project.layout.projectDirectory.file("settings.gradle.kts"))
    moduleBuildFiles.from(
        project.subprojects.map { subproject ->
            subproject.layout.projectDirectory.file("build.gradle.kts")
        },
    )
    modulePackageRoots.set(configuredModulePackageRoots)
    forbiddenLegacyPackageRoots.set(configuredForbiddenLegacyPackageRoots)
    kotlinJvmModules.set(configuredKotlinJvmModules)
    runtimeModuleLayers.set(configuredRuntimeModuleLayers)
    allowedDependencyLayers.set(
        configuredAllowedDependencyLayers.mapValues { (_, layers) ->
            layers.sorted().joinToString(",")
        },
    )
    toolingModules.set(configuredToolingModules)
}
