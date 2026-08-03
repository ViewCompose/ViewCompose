plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.viewcompose.preview")
}

android {
    namespace = "com.viewcompose.samples.counter"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.viewcompose.samples.counter"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(project(":viewcompose-runtime"))
    implementation(project(":viewcompose-ui-contract"))
    implementation(project(":viewcompose-widget-core"))
    implementation(project(":viewcompose-host-android"))

    debugImplementation(project(":viewcompose-preview-core"))
    add(
        "viewComposePreviewWorkerHost",
        project(":viewcompose-preview-worker-host"),
    )
    add(
        "viewComposePreviewRunner",
        project(":viewcompose-preview-runner"),
    )

    implementation(libs.androidx.activity)
    implementation(libs.material)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

val counterPreviewCatalog = layout.buildDirectory.file(
    "viewcompose-preview/debug/descriptors.json",
)

tasks.register("verifyCounterPreview") {
    group = "verification"
    description = "Verify that the compiled counter preview is discoverable by ViewCompose tooling."
    dependsOn("discoverDebugViewComposePreviews")
    inputs.file(counterPreviewCatalog)

    doLast {
        val catalogFile = counterPreviewCatalog.get().asFile
        check(catalogFile.isFile) {
            "Counter preview descriptor catalog was not generated: ${catalogFile.absolutePath}"
        }
        val catalog = catalogFile.readText()
        check("\"methodName\": \"CounterPreview\"" in catalog) {
            "CounterPreview is missing from ${catalogFile.absolutePath}."
        }
        check(Regex("\\\"diagnostics\\\"\\s*:\\s*\\[\\s*]").containsMatchIn(catalog)) {
            "Counter preview discovery reported diagnostics: ${catalogFile.absolutePath}."
        }
    }
}
