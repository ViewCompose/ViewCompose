plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.viewcompose.android"
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets["test"].java.srcDir("src/test/samples")
}

dependencies {
    api(project(":viewcompose-host-android"))
    api(project(":viewcompose-ui-foundation"))
    api(project(":viewcompose-material3"))
    api(project(":viewcompose-lifecycle-androidx"))
    api(project(":viewcompose-viewmodel-androidx"))
    api(libs.androidx.activity)
    api(libs.androidx.fragment)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
