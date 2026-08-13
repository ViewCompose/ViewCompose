plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.paparazzi)
}

android {
    namespace = "com.viewcompose.preview"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    buildFeatures {
        compose = true
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

configurations.configureEach {
    resolutionStrategy.force(
        "androidx.core:core:1.15.0",
        "androidx.core:core-ktx:1.15.0",
    )
}

dependencies {
    api(project(":viewcompose-preview-core"))
    implementation(project(":viewcompose-runtime"))
    implementation(project(":viewcompose-ui-contract"))
    api(project(":viewcompose-ui-foundation"))
    implementation(project(":viewcompose-constraintlayout-androidx"))
    implementation(project(":viewcompose-animation"))
    implementation(project(":viewcompose-gesture"))
    implementation(project(":viewcompose-graphics"))
    implementation(project(":viewcompose-host-android"))

    api(libs.androidx.compose.runtime)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.paparazzi)
    testImplementation(libs.robolectric)
}
