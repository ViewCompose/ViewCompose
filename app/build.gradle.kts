plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.viewcompose.preview")
}

android {
    namespace = "com.viewcompose"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gzq.uiframework"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("benchmark") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
        }
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
    implementation(project(":viewcompose-android"))
    implementation(project(":viewcompose-shadow-android"))
    implementation(project(":viewcompose-constraintlayout-androidx"))
    implementation(project(":viewcompose-animation"))
    implementation(project(":viewcompose-gesture"))
    implementation(project(":viewcompose-graphics"))
    implementation(project(":viewcompose-overlay-material3-android"))
    implementation(project(":viewcompose-image-coil"))
    compileOnly(project(":viewcompose-preview-core"))
    debugImplementation(project(":viewcompose-preview"))
    add(
        "viewComposePreviewWorkerHost",
        project(":viewcompose-preview-worker-host"),
    )
    add(
        "viewComposePreviewRunner",
        project(":viewcompose-preview-runner"),
    )
    implementation(project(":viewcompose-navigation-android"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.uiautomator)
}
