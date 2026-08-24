plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.viewcompose.exoplayer2"
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
    api(libs.exoplayer2.core)
    implementation(project(":viewcompose-lifecycle-androidx"))
    implementation(libs.exoplayer2.ui)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
