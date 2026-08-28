plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.viewcompose.host.android"
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
    api(project(":viewcompose-runtime"))
    api(project(":viewcompose-ui-contract"))
    api(project(":viewcompose-ui-foundation"))
    implementation(project(":viewcompose-renderer-android"))
    api(libs.androidx.lifecycle.runtime.ktx)
    api(libs.androidx.savedstate)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.dynamicanimation)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
