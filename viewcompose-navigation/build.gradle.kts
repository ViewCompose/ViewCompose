plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.viewcompose.navigation"
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
}

dependencies {
    api(project(":viewcompose-navigation-core"))
    api(project(":viewcompose-runtime"))
    api(project(":viewcompose-ui-contract"))
    api(project(":viewcompose-widget-core"))
    implementation(project(":viewcompose-host-android"))
    implementation(project(":viewcompose-lifecycle"))
    implementation(project(":viewcompose-viewmodel"))
    implementation(libs.androidx.activity)
    implementation(libs.androidx.dynamicanimation)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
