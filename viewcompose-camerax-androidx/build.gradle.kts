plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.viewcompose.camerax"
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    api(project(":viewcompose-host-android"))
    api(libs.androidx.camera.core)
    api(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(project(":viewcompose-lifecycle-androidx"))

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
