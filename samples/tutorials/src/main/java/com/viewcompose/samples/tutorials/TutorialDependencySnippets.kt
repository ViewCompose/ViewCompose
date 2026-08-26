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

private val navigationTutorialDependencies = run {
    // DOCS_REGION_START(navigation-dependencies)
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-navigation-android:0.1.0-alpha01")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
    // DOCS_REGION_END(navigation-dependencies)
}

private val themingTutorialDependencies = run {
    // DOCS_REGION_START(theming-dependencies)
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha01")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
    // DOCS_REGION_END(theming-dependencies)
}

private val textInputTutorialDependencies = run {
    // DOCS_REGION_START(text-input-dependencies)
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha01")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
    // DOCS_REGION_END(text-input-dependencies)
}

private val lazyCollectionsTutorialDependencies = run {
    // DOCS_REGION_START(lazy-collections-dependencies)
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha01")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
    // DOCS_REGION_END(lazy-collections-dependencies)
}

private val layoutsAndModifiersTutorialDependencies = run {
    // DOCS_REGION_START(layouts-and-modifiers-dependencies)
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha01")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
    // DOCS_REGION_END(layouts-and-modifiers-dependencies)
}

private val gesturesTutorialDependencies = run {
    // DOCS_REGION_START(gestures-dependencies)
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-gesture:0.1.0-alpha04")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
    // DOCS_REGION_END(gestures-dependencies)
}

private val shadowModuleDependency = run {
    // DOCS_REGION_START(shadow-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-shadow-android:0.1.0-alpha04")
}
    // DOCS_REGION_END(shadow-dependency)
}

private val overlaysTutorialDependencies = run {
    // DOCS_REGION_START(overlays-dependencies)
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-overlay-material3-android:0.1.0-alpha01")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
    // DOCS_REGION_END(overlays-dependencies)
}

private val neutralOverlayModuleDependency = run {
    // DOCS_REGION_START(overlay-android-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-overlay-android:0.1.0-alpha04")
}
    // DOCS_REGION_END(overlay-android-dependency)
}

private val materialOverlayModuleDependency = run {
    // DOCS_REGION_START(overlay-material3-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-overlay-material3-android:0.1.0-alpha01")
}
    // DOCS_REGION_END(overlay-material3-dependency)
}

private val oneUiOverlayModuleDependency = run {
    // DOCS_REGION_START(overlay-oneui7-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-oneui7:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-overlay-oneui7-android:0.1.0-alpha01")
}
    // DOCS_REGION_END(overlay-oneui7-dependency)
}

private val imageCoilModuleDependency = run {
    // DOCS_REGION_START(image-coil-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-image-coil:0.1.0-alpha04")
}
    // DOCS_REGION_END(image-coil-dependency)
}

private val imageGlideModuleDependency = run {
    // DOCS_REGION_START(image-glide-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-image-glide:0.1.0-alpha02")
}
    // DOCS_REGION_END(image-glide-dependency)
}

private val constraintLayoutModuleDependency = run {
    // DOCS_REGION_START(constraintlayout-dependency)
dependencies {
    implementation("com.viewcompose:viewcompose-constraintlayout-androidx:0.1.0-alpha01")
}
    // DOCS_REGION_END(constraintlayout-dependency)
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
        "com.viewcompose:viewcompose-preview-runner:0.1.0-alpha04",
    )
}
    // DOCS_REGION_END(preview-native-install)
}
