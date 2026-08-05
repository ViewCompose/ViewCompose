plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.viewcompose.samples.tutorials"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.viewcompose.samples.tutorials"
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
    implementation("com.viewcompose:viewcompose-host-android:0.1.0-alpha03")
    implementation("com.viewcompose:viewcompose-navigation:0.1.0-alpha03")
    implementation("com.viewcompose:viewcompose-overlay-android:0.1.0-alpha03")
    implementation("com.viewcompose:viewcompose-animation:0.1.0-alpha03")
    implementation("com.viewcompose:viewcompose-gesture:0.1.0-alpha03")

    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
