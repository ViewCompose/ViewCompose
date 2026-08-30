import java.nio.file.Files
import org.gradle.api.artifacts.type.ArtifactTypeDefinition

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
val requestRootDirectory = rootProject.layout.buildDirectory.dir(
    "ai/preview/requests/$requestIdentity",
)
val requestSourceDirectory = requestRootDirectory.map { directory -> directory.dir("input") }
val requestResourceDirectory = requestRootDirectory.map { directory -> directory.dir("res") }

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
    sourceSets.getByName("debug").res.srcDir(requestResourceDirectory.get().asFile)
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
    inputs.dir(requestRootDirectory)

    doLast {
        require(requestKey != null) {
            "The tool-owned Preview harness requires -PviewComposeAiPreviewRequestKey=<sha256>."
        }
        val input = requestSourceDirectory.get().asFile
        val requestRoot = requestRootDirectory.get().asFile
        require(input.isDirectory) {
            "The tool-owned Preview input directory is missing for request $requestKey."
        }
        require(input.listFiles().orEmpty().map { it.name }.sorted() ==
            listOf("GeneratedPreview.kt", "GeneratedView.kt")) {
            "The tool-owned Preview input must contain exactly GeneratedPreview.kt and GeneratedView.kt."
        }
        require(input.listFiles().orEmpty().all { file ->
            file.isFile && !Files.isSymbolicLink(file.toPath())
        }) {
            "The tool-owned Preview input contains a non-file entry."
        }
        val resources = requestResourceDirectory.get().asFile
        if (resources.exists()) {
            require(
                resources.isDirectory &&
                    !Files.isSymbolicLink(resources.toPath()) &&
                    resources.listFiles().orEmpty().map { it.name } == listOf("drawable"),
            ) {
                "The tool-owned Preview resource root must contain only drawable."
            }
            val drawable = resources.resolve("drawable")
            require(
                drawable.isDirectory &&
                    !Files.isSymbolicLink(drawable.toPath()) &&
                    drawable.listFiles().orEmpty().isNotEmpty() &&
                    drawable.listFiles().orEmpty().all { file ->
                        Regex("^vc_ai_[a-f0-9]{64}\\.png$").matches(file.name) &&
                            file.isFile &&
                            !Files.isSymbolicLink(file.toPath())
                    },
            ) {
                "The tool-owned Preview drawable directory contains an invalid asset."
            }
        }
        val expectedRootEntries = if (resources.exists()) {
            listOf("input", "res")
        } else {
            listOf("input")
        }
        require(requestRoot.listFiles().orEmpty().map { it.name }.sorted() == expectedRootEntries) {
            "The tool-owned Preview request directory contains an unexpected entry."
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
        fun resolveArtifacts(configurationName: String, artifactType: String) =
            configurations.getByName(configurationName).incoming.artifactView {
                attributes.attribute(
                    ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
                    artifactType,
                )
            }.files.files

        val runtimeClassJars = resolveArtifacts("debugRuntimeClasspath", "android-classes-jar")
        require(runtimeClassJars.isNotEmpty()) {
            "The fixed generated-Preview runtime classpath resolved no class jars."
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
            "The fixed generated-Preview runner classpath resolved no class jars."
        }
        listOf(
            "viewComposePreviewWorkerHost",
            "viewComposePreviewLayoutlibRuntime",
            "viewComposePreviewLayoutlibResources",
        ).forEach { configurationName ->
            require(configurations.getByName(configurationName).files.isNotEmpty()) {
                "The fixed generated-Preview configuration '$configurationName' resolved no files."
            }
        }
    }
}
