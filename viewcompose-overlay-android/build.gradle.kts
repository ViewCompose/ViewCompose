plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.viewcompose.overlay.android"
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
    api(project(":viewcompose-ui-contract"))
    api(project(":viewcompose-widget-core"))
    implementation(project(":viewcompose-host-android"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
}
