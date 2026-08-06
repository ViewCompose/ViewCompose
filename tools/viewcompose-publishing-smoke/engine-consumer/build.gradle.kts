plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "com.viewcompose.publishing.smoke.engine"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

val viewComposeGroup = rootProject.extra["viewComposeGroup"] as String
@Suppress("UNCHECKED_CAST")
val viewComposeVersions = rootProject.extra["viewComposeVersions"] as Map<String, String>
fun viewCompose(module: String): String =
    "$viewComposeGroup:$module:${checkNotNull(viewComposeVersions[module])}"

dependencies {
    implementation(viewCompose("viewcompose-host-android"))
}

val verifyMaterialFreeRuntimeClasspath = tasks.register("verifyMaterialFreeRuntimeClasspath") {
    group = "verification"
    description = "Verifies the low-level Android engine resolves without Material Components."
    doLast {
        val forbidden = configurations.getByName("releaseRuntimeClasspath")
            .resolvedConfiguration
            .resolvedArtifacts
            .filter { artifact -> artifact.moduleVersion.id.group == "com.google.android.material" }
            .map { artifact -> artifact.moduleVersion.id.toString() }
            .sorted()
        check(forbidden.isEmpty()) {
            "Low-level Android engine unexpectedly resolved Material artifacts: $forbidden"
        }
    }
}

tasks.named("assemble").configure {
    dependsOn(verifyMaterialFreeRuntimeClasspath)
}
