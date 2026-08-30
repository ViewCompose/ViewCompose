import org.gradle.api.artifacts.type.ArtifactTypeDefinition

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.viewcompose.preview")
}

android {
    namespace = "com.viewcompose.samples.counter"
    compileSdk = 37

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
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha02")

    debugImplementation("com.viewcompose:viewcompose-preview-core:0.1.0-alpha03")
    add(
        "viewComposePreviewWorkerHost",
        "com.viewcompose:viewcompose-preview-worker-host:0.1.0-alpha03",
    )
    add(
        "viewComposePreviewRunner",
        "com.viewcompose:viewcompose-preview-runner:0.1.0-alpha05",
    )

    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}

val counterPreviewCatalog = layout.buildDirectory.file(
    "viewcompose-preview/debug/descriptors.json",
)

tasks.register("prepareAiPreviewLane") {
    group = "verification"
    description = "Resolve the fixed counter Preview classpaths before offline AI rendering."
    dependsOn(":publishViewComposeToLocalRepository")
    doLast {
        fun resolveArtifacts(configurationName: String, artifactType: String) =
            configurations.getByName(configurationName).incoming.artifactView {
                attributes.attribute(
                    ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
                    artifactType,
                )
            }.files.files

        val runtimeClassJars = resolveArtifacts("debugRuntimeClasspath", "android-classes-jar")
        require(runtimeClassJars.isNotEmpty()) {
            "The fixed counter Preview runtime classpath resolved no class jars."
        }
        resolveArtifacts("debugCompileClasspath", "android-classes-jar")
        resolveArtifacts("debugRuntimeClasspath", "android-res")
        resolveArtifacts("debugRuntimeClasspath", "android-assets")
        resolveArtifacts("debugRuntimeClasspath", "android-symbol-with-package-name")
        val runnerClassJars = resolveArtifacts(
            "viewComposePreviewDebugRunnerClasspath",
            "android-classes-jar",
        )
        require(runnerClassJars.isNotEmpty()) {
            "The fixed counter Preview runner classpath resolved no class jars."
        }
        listOf(
            "viewComposePreviewWorkerHost",
            "viewComposePreviewLayoutlibRuntime",
            "viewComposePreviewLayoutlibResources",
        ).forEach { configurationName ->
            require(configurations.getByName(configurationName).files.isNotEmpty()) {
                "The fixed counter Preview configuration '$configurationName' resolved no files."
            }
        }
    }
}

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
