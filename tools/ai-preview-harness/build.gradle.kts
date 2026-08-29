plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.viewcompose.preview")
}

val requestKeyProperty = providers.gradleProperty("viewComposeAiPreviewRequestKey")
val requestKey = requestKeyProperty.orNull
requestKey?.let { value ->
    require(Regex("^[a-f0-9]{64}$").matches(value)) {
        "ViewCompose AI Preview request keys must be lowercase SHA-256 values."
    }
}
val requestIdentity = requestKey ?: "inactive"
val requestSourceDirectory = rootProject.layout.buildDirectory.dir(
    "ai/preview/requests/$requestIdentity/input",
)

android {
    namespace = "com.viewcompose.ai.preview.harness"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.viewcompose.ai.preview.harness"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    sourceSets.getByName("debug").java.srcDir(requestSourceDirectory.get().asFile)
}

dependencies {
    implementation(project(":viewcompose-material3-android"))

    debugImplementation(project(":viewcompose-preview-core"))
    add("viewComposePreviewWorkerHost", project(":viewcompose-preview-worker-host"))
    add("viewComposePreviewRunner", project(":viewcompose-preview-runner"))
}

val validateAiPreviewRequest by tasks.registering {
    group = "verification"
    description = "Validate the fixed content-addressed source input for one AI Preview request."
    inputs.dir(requestSourceDirectory)

    doLast {
        require(requestKey != null) {
            "The tool-owned Preview harness requires -PviewComposeAiPreviewRequestKey=<sha256>."
        }
        val input = requestSourceDirectory.get().asFile
        require(input.isDirectory) {
            "The tool-owned Preview input directory is missing for request $requestKey."
        }
        require(input.listFiles().orEmpty().map { it.name }.sorted() ==
            listOf("GeneratedPreview.kt", "GeneratedView.kt")) {
            "The tool-owned Preview input must contain exactly GeneratedPreview.kt and GeneratedView.kt."
        }
        require(input.listFiles().orEmpty().all { it.isFile }) {
            "The tool-owned Preview input contains a non-file entry."
        }
    }
}

if (requestKey != null) {
    tasks.matching { task -> task.name == "compileDebugKotlin" }.configureEach {
        dependsOn(validateAiPreviewRequest)
    }
}

tasks.register("prepareAiPreviewLane") {
    group = "verification"
    description = "Resolve the fixed generated-Preview compilation and render classpaths."
    doLast {
        require(configurations.getByName("debugRuntimeClasspath").files.isNotEmpty()) {
            "The fixed generated-Preview runtime classpath resolved no files."
        }
        require(configurations.getByName("viewComposePreviewWorkerHost").files.isNotEmpty()) {
            "The fixed generated-Preview worker-host classpath resolved no files."
        }
        require(configurations.getByName("viewComposePreviewRunner").files.isNotEmpty()) {
            "The fixed generated-Preview runner classpath resolved no files."
        }
    }
}
