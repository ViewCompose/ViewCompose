plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.viewcompose.samples.tasklist"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.viewcompose.samples.tasklist"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    implementation(project(":viewcompose-runtime"))
    implementation(project(":viewcompose-text-core"))
    implementation(project(":viewcompose-ui-contract"))
    implementation(project(":viewcompose-widget-core"))
    implementation(project(":viewcompose-host-android"))

    implementation(libs.androidx.activity)
    implementation(libs.material)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
