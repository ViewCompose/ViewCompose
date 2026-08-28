package com.viewcompose.samples.tutorials

private class PublishingDependencyScope {
    val coordinates = mutableListOf<String>()

    fun implementation(coordinate: String) {
        coordinates += coordinate
    }
}

private fun dependencies(block: PublishingDependencyScope.() -> Unit): List<String> =
    PublishingDependencyScope().apply(block).coordinates

private fun materialEntryPointDependencySample() {
    // DOCS_REGION_START(project-publishing-material-entry)
    dependencies {
        implementation("com.viewcompose:viewcompose-material3-android:<version-with-this-contract>")
    }
    // DOCS_REGION_END(project-publishing-material-entry)
}

private fun featureDependenciesSample() {
    // DOCS_REGION_START(project-publishing-feature-entries)
    dependencies {
        implementation("com.viewcompose:viewcompose-navigation-android:0.1.0-alpha02")
        implementation("com.viewcompose:viewcompose-animation:0.1.0-alpha05")
        implementation("com.viewcompose:viewcompose-gesture:0.1.0-alpha05")
        implementation("com.viewcompose:viewcompose-graphics:0.1.0-alpha05")
    }
    // DOCS_REGION_END(project-publishing-feature-entries)
}

private fun coreDependenciesSample() {
    // DOCS_REGION_START(project-publishing-core-entries)
    dependencies {
        implementation("com.viewcompose:viewcompose-navigation-core:0.1.0-alpha03")
        implementation("com.viewcompose:viewcompose-animation-core:0.1.0-alpha05")
        implementation("com.viewcompose:viewcompose-gesture-core:0.1.0-alpha05")
        implementation("com.viewcompose:viewcompose-graphics-core:0.1.0-alpha02")
    }
    // DOCS_REGION_END(project-publishing-core-entries)
}
