package com.viewcompose.samples.tutorials

private class TutorialRepositoryHandler {
    fun mavenCentral() = Unit
}

private class TutorialDependencyHandler {
    fun implementation(coordinate: String) = coordinate
}

private fun repositories(content: TutorialRepositoryHandler.() -> Unit) {
    TutorialRepositoryHandler().content()
}

private fun dependencies(content: TutorialDependencyHandler.() -> Unit) {
    TutorialDependencyHandler().content()
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
