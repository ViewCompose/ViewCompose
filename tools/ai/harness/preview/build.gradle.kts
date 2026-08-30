import java.nio.file.Files

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.viewcompose.preview")
}

val requestKey = providers.gradleProperty("viewComposeAiPreviewRequestKey").orNull
val cacheRoot = providers.gradleProperty("viewComposeAiPreviewRequestCacheRoot").orNull
requestKey?.let { value ->
    require(Regex("^[a-f0-9]{64}$").matches(value)) {
        "ViewCompose AI Preview request keys must be lowercase SHA-256 values."
    }
}
cacheRoot?.let { value ->
    require(file(value).isAbsolute) {
        "The ViewCompose AI Preview request cache must be an absolute tool-owned path."
    }
}
val requestIdentity = requestKey ?: "inactive"
val requestRoot = cacheRoot?.let { root -> file(root).resolve(requestIdentity) }
    ?: layout.buildDirectory.dir("inactive").get().asFile
val inputDirectory = requestRoot.resolve("input")
val resourceDirectory = requestRoot.resolve("res")

layout.buildDirectory.set(requestRoot.resolve("build"))

android {
    namespace = "com.viewcompose.ai.preview.harness"
    compileSdk = 36

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

    sourceSets.getByName("debug").java.srcDir(inputDirectory)
    sourceSets.getByName("debug").res.srcDir(resourceDirectory)
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
}

val validateAiPreviewRequest by tasks.registering {
    group = "verification"
    description = "Validate one fixed content-addressed generated Preview input."
    inputs.dir(inputDirectory)
    if (resourceDirectory.exists()) inputs.dir(resourceDirectory)
    doLast {
        require(requestKey != null && cacheRoot != null) {
            "The tool-owned Preview harness requires its fixed request identity and cache root."
        }
        require(
            inputDirectory.isDirectory &&
                inputDirectory.listFiles().orEmpty().map { it.name }.sorted() ==
                listOf("GeneratedPreview.kt", "GeneratedView.kt") &&
                inputDirectory.listFiles().orEmpty().all { file ->
                    file.isFile && !Files.isSymbolicLink(file.toPath())
                },
        ) {
            "The tool-owned Preview input must contain exactly two safe generated Kotlin files."
        }
        if (resourceDirectory.exists()) {
            val drawable = resourceDirectory.resolve("drawable")
            require(
                resourceDirectory.isDirectory &&
                    resourceDirectory.listFiles().orEmpty().map { it.name } == listOf("drawable") &&
                    drawable.isDirectory &&
                    drawable.listFiles().orEmpty().isNotEmpty() &&
                    drawable.listFiles().orEmpty().all { file ->
                        Regex("^vc_ai_[a-f0-9]{64}\\.png$").matches(file.name) &&
                            file.isFile &&
                            !Files.isSymbolicLink(file.toPath())
                    },
            ) {
                "The tool-owned Preview resource root contains an unsafe asset."
            }
        }
        val expected = if (resourceDirectory.exists()) listOf("input", "res") else listOf("input")
        val authoredEntries = requestRoot.listFiles().orEmpty().filter { it.name != "build" }
        require(authoredEntries.map { it.name }.sorted() == expected) {
            "The tool-owned Preview request contains an unexpected entry."
        }
        requestRoot.resolve("build").takeIf { it.exists() }?.let { build ->
            require(build.isDirectory && !Files.isSymbolicLink(build.toPath())) {
                "The tool-owned Preview output directory is unsafe."
            }
        }
    }
}

if (requestKey != null) {
    tasks.matching { task -> task.name == "compileDebugKotlin" }.configureEach {
        dependsOn(validateAiPreviewRequest)
    }
}
