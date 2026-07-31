plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.paparazzi)
}

android {
    namespace = "com.viewcompose.preview.runner"
    compileSdk = 35

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
    implementation(project(":viewcompose-widget-core"))
    implementation(project(":viewcompose-host-android"))
    implementation(project(":viewcompose-renderer"))

    testImplementation(libs.junit)
    testImplementation(libs.paparazzi)
    testImplementation(project(":viewcompose-preview-worker-host")) {
        // The runner's Paparazzi plugin owns this test process and its matching
        // Layoutlib native runtime. The production worker uses a newer isolated
        // classpath, which must not replace the runner test engine transitively.
        isTransitive = false
    }
}
