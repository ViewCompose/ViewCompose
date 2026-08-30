import java.nio.file.Files

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val requestKey = providers.gradleProperty("viewComposeAiRequestKey").orNull
val cacheRoot = providers.gradleProperty("viewComposeAiRequestCacheRoot").orNull
requestKey?.let { value ->
    require(Regex("^[a-f0-9]{64}$").matches(value)) {
        "ViewCompose AI compiler request keys must be lowercase SHA-256 values."
    }
}
cacheRoot?.let { value ->
    require(file(value).isAbsolute) {
        "The ViewCompose AI request cache must be an absolute tool-owned path."
    }
}
val requestIdentity = requestKey ?: "inactive"
val requestRoot = cacheRoot?.let { root -> file(root).resolve(requestIdentity) }
    ?: layout.buildDirectory.dir("inactive").get().asFile
val inputSource = requestRoot.resolve("input/Snippet.kt")

// The installed tooling chooses both fixed properties. No MCP request can select a project,
// dependency, Gradle task, script, or output path.
layout.buildDirectory.set(requestRoot.resolve("harness"))
val generatedSource = layout.buildDirectory.dir("generated/source/ai/main/kotlin")

android {
    namespace = "com.viewcompose.ai.compiler.harness"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    sourceSets.getByName("main").java.srcDir(generatedSource.get().asFile)
}

dependencies {
    implementation("com.viewcompose:viewcompose-ui-foundation:0.1.0-alpha02")
}

val prepareAiSnippet by tasks.registering(Sync::class) {
    from(inputSource)
    into(generatedSource)
    rename { "Snippet.kt" }
    doFirst {
        require(requestKey != null && cacheRoot != null) {
            "The tool-owned compiler requires its fixed request identity and cache root."
        }
        require(inputSource.isFile && !Files.isSymbolicLink(inputSource.toPath())) {
            "The tool-owned compiler input is missing or unsafe for request $requestKey."
        }
    }
}

if (requestKey != null) {
    tasks.matching { task -> task.name == "compileDebugKotlin" }.configureEach {
        dependsOn(prepareAiSnippet)
    }
}

tasks.register("compileAiSnippet") {
    group = "verification"
    description = "Compile one content-addressed source against released ViewCompose artifacts."
    dependsOn("compileDebugKotlin")
    doFirst {
        require(requestKey != null && cacheRoot != null) {
            "The tool-owned compiler requires its fixed request identity and cache root."
        }
    }
}
