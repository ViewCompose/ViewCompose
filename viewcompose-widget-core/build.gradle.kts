plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.viewcompose.widget.core"
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
    api(project(":viewcompose-text-core"))
    api(project(":viewcompose-runtime"))
    api(project(":viewcompose-ui-contract"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
