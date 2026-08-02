plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "com.viewcompose.publishing.smoke.feature"
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
    implementation(viewCompose("viewcompose-navigation"))
    implementation(viewCompose("viewcompose-animation"))
    implementation(viewCompose("viewcompose-gesture"))
    implementation(viewCompose("viewcompose-graphics"))
    implementation(viewCompose("viewcompose-shadow-android"))
    implementation(viewCompose("viewcompose-widget-constraintlayout"))
}
