package com.viewcompose.samples.tutorials

private class TutorialRepositoryHandler {
    fun mavenCentral() = Unit
}

private class TutorialDependencyHandler {
    fun implementation(coordinate: String) = coordinate
    fun debugImplementation(coordinate: String) = coordinate
    fun add(configuration: String, coordinate: String) = configuration to coordinate
}

private class TutorialPluginSpec(private val id: String) {
    infix fun version(version: String) = id to version
}

private class TutorialPluginHandler {
    fun id(id: String) = TutorialPluginSpec(id)
}

private fun repositories(content: TutorialRepositoryHandler.() -> Unit) {
    TutorialRepositoryHandler().content()
}

private fun dependencies(content: TutorialDependencyHandler.() -> Unit) {
    TutorialDependencyHandler().content()
}

private fun plugins(content: TutorialPluginHandler.() -> Unit) {
    TutorialPluginHandler().content()
}

private val animationTutorialDependencies = run {
    // DOCS_REGION_START(animation-dependencies)
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha02")
    implementation("com.viewcompose:viewcompose-animation:0.1.0-alpha05")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
    // DOCS_REGION_END(animation-dependencies)
}

private val animationModuleDependency = run {
    // DOCS_REGION_START(animation-module-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-animation:0.1.0-alpha05")
}
    // DOCS_REGION_END(animation-module-dependency)
}

private val animationCoreModuleDependency = run {
    // DOCS_REGION_START(animation-core-module-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-animation-core:0.1.0-alpha05")
}
    // DOCS_REGION_END(animation-core-module-dependency)
}

private val gestureCoreModuleDependency = run {
    // DOCS_REGION_START(gesture-core-module-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-gesture-core:0.1.0-alpha05")
}
    // DOCS_REGION_END(gesture-core-module-dependency)
}

private val gestureModuleDependency = run {
    // DOCS_REGION_START(gesture-module-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-gesture:0.1.0-alpha05")
}
    // DOCS_REGION_END(gesture-module-dependency)
}

private val graphicsCoreModuleDependency = run {
    // DOCS_REGION_START(graphics-core-module-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-graphics-core:0.1.0-alpha02")
}
    // DOCS_REGION_END(graphics-core-module-dependency)
}

private val graphicsModuleDependency = run {
    // DOCS_REGION_START(graphics-module-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-graphics:0.1.0-alpha05")
}
    // DOCS_REGION_END(graphics-module-dependency)
}

private val hostAndroidModuleDependency = run {
    // DOCS_REGION_START(host-android-module-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-host-android:0.1.0-alpha05")
}
    // DOCS_REGION_END(host-android-module-dependency)
}

private val androidModuleDependency = run {
    // DOCS_REGION_START(android-module-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-android:0.1.0-alpha02")
}
    // DOCS_REGION_END(android-module-dependency)
}

private val runtimeModuleDependency = run {
    // DOCS_REGION_START(runtime-module-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-runtime:0.1.0-alpha04")
}
    // DOCS_REGION_END(runtime-module-dependency)
}

private val textCoreModuleDependency = run {
    // DOCS_REGION_START(text-core-module-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-text-core:0.1.0-alpha04")
}
    // DOCS_REGION_END(text-core-module-dependency)
}

private val uiContractModuleDependency = run {
    // DOCS_REGION_START(ui-contract-module-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-ui-contract:0.1.0-alpha05")
}
    // DOCS_REGION_END(ui-contract-module-dependency)
}

private val lifecycleAndroidxModuleDependency = run {
    // DOCS_REGION_START(lifecycle-androidx-module-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-lifecycle-androidx:0.1.0-alpha02")
}
    // DOCS_REGION_END(lifecycle-androidx-module-dependency)
}

private val viewModelAndroidxModuleDependency = run {
    // DOCS_REGION_START(viewmodel-androidx-module-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-viewmodel-androidx:0.1.0-alpha02")
}
    // DOCS_REGION_END(viewmodel-androidx-module-dependency)
}

private val uiFoundationModuleDependency = run {
    // DOCS_REGION_START(ui-foundation-module-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-ui-foundation:0.1.0-alpha02")
}
    // DOCS_REGION_END(ui-foundation-module-dependency)
}

private val material3ModuleDependency = run {
    // DOCS_REGION_START(material3-module-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-material3:0.1.0-alpha02")
}
    // DOCS_REGION_END(material3-module-dependency)
}

private val material3AndroidModuleDependency = run {
    // DOCS_REGION_START(material3-android-module-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha02")
}
    // DOCS_REGION_END(material3-android-module-dependency)
}

private val oneUi7ModuleDependency = run {
    // DOCS_REGION_START(oneui7-module-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-oneui7:0.1.0-alpha02")
}
    // DOCS_REGION_END(oneui7-module-dependency)
}

private val pagingModuleDependency = run {
    // DOCS_REGION_START(paging-module-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-paging-androidx:0.1.0-alpha01")
}
    // DOCS_REGION_END(paging-module-dependency)
}

private val gettingStartedDependencies = run {
    // DOCS_REGION_START(getting-started-dependencies)
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha02")
}
    // DOCS_REGION_END(getting-started-dependencies)
}

private val stateAndEventsDependencies = run {
    // DOCS_REGION_START(state-and-events-dependencies)
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha02")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
    // DOCS_REGION_END(state-and-events-dependencies)
}

private val rendererAndroidModuleDependency = run {
    // DOCS_REGION_START(renderer-android-module-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-renderer-android:0.1.0-alpha02")
}
    // DOCS_REGION_END(renderer-android-module-dependency)
}

private val androidViewTutorialDependencies = run {
    // DOCS_REGION_START(android-view-dependencies)
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha02")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
    // DOCS_REGION_END(android-view-dependencies)
}

private val navigationTutorialDependencies = run {
    // DOCS_REGION_START(navigation-dependencies)
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha02")
    implementation("com.viewcompose:viewcompose-navigation-android:0.1.0-alpha02")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
    // DOCS_REGION_END(navigation-dependencies)
}

private val navigationCoreModuleDependency = run {
    // DOCS_REGION_START(navigation-core-module-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-navigation-core:0.1.0-alpha03")
}
    // DOCS_REGION_END(navigation-core-module-dependency)
}

private val navigationAndroidModuleDependency = run {
    // DOCS_REGION_START(navigation-android-module-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-navigation-android:0.1.0-alpha02")
}
    // DOCS_REGION_END(navigation-android-module-dependency)
}

private val themingTutorialDependencies = run {
    // DOCS_REGION_START(theming-dependencies)
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha02")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
    // DOCS_REGION_END(theming-dependencies)
}

private val textInputTutorialDependencies = run {
    // DOCS_REGION_START(text-input-dependencies)
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha02")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
    // DOCS_REGION_END(text-input-dependencies)
}

private val lazyCollectionsTutorialDependencies = run {
    // DOCS_REGION_START(lazy-collections-dependencies)
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha02")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
    // DOCS_REGION_END(lazy-collections-dependencies)
}

private val layoutsAndModifiersTutorialDependencies = run {
    // DOCS_REGION_START(layouts-and-modifiers-dependencies)
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha02")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
    // DOCS_REGION_END(layouts-and-modifiers-dependencies)
}

private val gesturesTutorialDependencies = run {
    // DOCS_REGION_START(gestures-dependencies)
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha02")
    implementation("com.viewcompose:viewcompose-gesture:0.1.0-alpha05")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
    // DOCS_REGION_END(gestures-dependencies)
}

private val shadowModuleDependency = run {
    // DOCS_REGION_START(shadow-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-shadow-android:0.1.0-alpha05")
}
    // DOCS_REGION_END(shadow-dependency)
}

private val overlaysTutorialDependencies = run {
    // DOCS_REGION_START(overlays-dependencies)
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha02")
    implementation("com.viewcompose:viewcompose-overlay-material3-android:0.1.0-alpha02")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
    // DOCS_REGION_END(overlays-dependencies)
}

private val neutralOverlayModuleDependency = run {
    // DOCS_REGION_START(overlay-android-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-overlay-android:0.1.0-alpha05")
}
    // DOCS_REGION_END(overlay-android-dependency)
}

private val materialOverlayModuleDependency = run {
    // DOCS_REGION_START(overlay-material3-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-overlay-material3-android:0.1.0-alpha02")
}
    // DOCS_REGION_END(overlay-material3-dependency)
}

private val oneUiOverlayModuleDependency = run {
    // DOCS_REGION_START(overlay-oneui7-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-oneui7:0.1.0-alpha02")
    implementation("com.viewcompose:viewcompose-overlay-oneui7-android:0.1.0-alpha02")
}
    // DOCS_REGION_END(overlay-oneui7-dependency)
}

private val imageCoilModuleDependency = run {
    // DOCS_REGION_START(image-coil-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-image-coil:0.1.0-alpha05")
}
    // DOCS_REGION_END(image-coil-dependency)
}

private val imageGlideModuleDependency = run {
    // DOCS_REGION_START(image-glide-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-image-glide:0.1.0-alpha03")
}
    // DOCS_REGION_END(image-glide-dependency)
}

private val constraintLayoutModuleDependency = run {
    // DOCS_REGION_START(constraintlayout-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-constraintlayout-androidx:0.1.0-alpha02")
}
    // DOCS_REGION_END(constraintlayout-dependency)
}

private val cameraXModuleDependency = run {
    // DOCS_REGION_START(camerax-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-camerax-androidx:0.1.0-alpha01")

    // The application, not the integration, selects the CameraX hardware backend.
    implementation("androidx.camera:camera-camera2:1.6.1")
}
    // DOCS_REGION_END(camerax-dependency)
}

private val googleMapsModuleDependency = run {
    // DOCS_REGION_START(google-maps-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-google-maps-android:0.1.0-alpha01")
}
    // DOCS_REGION_END(google-maps-dependency)
}

private val media3ModuleDependency = run {
    // DOCS_REGION_START(media3-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-media3-androidx:0.1.0-alpha01")
}
    // DOCS_REGION_END(media3-dependency)
}

private val exoPlayer2ModuleDependency = run {
    // DOCS_REGION_START(exoplayer2-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-exoplayer2-android:0.1.0-alpha01")
}
    // DOCS_REGION_END(exoplayer2-dependency)
}

private val renderDiagnosticsDependencies = run {
    // DOCS_REGION_START(render-diagnostics-dependencies)
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha02")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
    // DOCS_REGION_END(render-diagnostics-dependencies)
}

private val diagnosticsModuleDependency = run {
    // DOCS_REGION_START(diagnostics-module-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-diagnostics:0.1.0-alpha01")
}
    // DOCS_REGION_END(diagnostics-module-dependency)
}

private val previewNativeInstall = run {
    // DOCS_REGION_START(preview-native-install)
plugins {
    id("com.viewcompose.preview") version "0.1.0-alpha03"
}

dependencies {
    debugImplementation("com.viewcompose:viewcompose-preview-core:0.1.0-alpha03")
    add(
        "viewComposePreviewWorkerHost",
        "com.viewcompose:viewcompose-preview-worker-host:0.1.0-alpha03",
    )
    add(
        "viewComposePreviewRunner",
        "com.viewcompose:viewcompose-preview-runner:0.1.0-alpha05",
    )
}
    // DOCS_REGION_END(preview-native-install)
}
