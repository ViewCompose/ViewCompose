plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

val requestKeyProperty = providers.gradleProperty("viewComposeAiRequestKey")
val requestKey = requestKeyProperty.orNull
requestKey?.let { value ->
    require(Regex("^[a-f0-9]{64}$").matches(value)) {
        "ViewCompose AI compiler request keys must be lowercase SHA-256 values."
    }
}
val requestIdentity = requestKey ?: "inactive"
val requestRoot = rootProject.layout.buildDirectory.dir(
    "ai/compiler/requests/$requestIdentity",
)
val inputSource = requestRoot.map { directory -> directory.file("input/Snippet.kt") }

// Each content-addressed request owns an isolated build tree. The harness accepts no project path,
// Gradle script, dependency coordinate, task name, or output directory from an untrusted request.
layout.buildDirectory.set(requestRoot.map { directory -> directory.dir("harness") })
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
    implementation(project(":viewcompose-ui-foundation"))
}

tasks.register("prepareAiCompilerLane") {
    group = "verification"
    description = "Resolve the fixed AI compiler classpath before offline corpus verification."
    doLast {
        val classpath = configurations.getByName("debugCompileClasspath").incoming.artifactView {
            attributes.attribute(
                org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
                "android-classes-jar",
            )
        }.files
        require(classpath.files.isNotEmpty()) {
            "The fixed ViewCompose AI compiler classpath resolved no files."
        }
    }
}

val prepareAiSnippet by tasks.registering(Sync::class) {
    from(inputSource)
    into(generatedSource)
    rename { "Snippet.kt" }
    doFirst {
        require(requestKey != null) {
            "The tool-owned compiler requires -PviewComposeAiRequestKey=<sha256>."
        }
        require(inputSource.get().asFile.isFile) {
            "The tool-owned compiler input is missing for request $requestKey."
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
    description = "Compile one content-addressed Kotlin source in the fixed ViewCompose harness."
    dependsOn("compileDebugKotlin")
    doFirst {
        require(requestKey != null) {
            "The tool-owned compiler requires -PviewComposeAiRequestKey=<sha256>."
        }
    }
}
