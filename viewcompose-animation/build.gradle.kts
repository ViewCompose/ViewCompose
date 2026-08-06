plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.viewcompose.animation"
    compileSdk = 36

    sourceSets["test"].java.srcDir("src/test/samples")

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
}

dependencies {
    api(project(":viewcompose-animation-core"))
    api(project(":viewcompose-runtime"))
    api(project(":viewcompose-ui-contract"))
    api(project(":viewcompose-ui-foundation"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
