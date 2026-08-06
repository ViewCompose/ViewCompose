plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "com.viewcompose.publishing.smoke.host"
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
    implementation(viewCompose("viewcompose-android"))
}
