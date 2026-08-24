plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.viewcompose.media3"
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
    api(libs.androidx.media3.common)
    implementation(project(":viewcompose-lifecycle-androidx"))
    implementation(libs.androidx.media3.ui)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
