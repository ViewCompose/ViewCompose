plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.viewcompose.viewmodel"
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
    api(project(":viewcompose-ui-foundation"))
    api(libs.androidx.lifecycle.viewmodel.ktx)
    api(libs.androidx.lifecycle.viewmodel.savedstate)
    testImplementation(project(":viewcompose-runtime"))
    testImplementation(libs.junit)
}
