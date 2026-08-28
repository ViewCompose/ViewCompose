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
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha02")
    implementation("com.viewcompose:viewcompose-navigation-android:0.1.0-alpha02")
    implementation("com.viewcompose:viewcompose-overlay-material3-android:0.1.0-alpha02")
    implementation("com.viewcompose:viewcompose-animation:0.1.0-alpha05")
    implementation("com.viewcompose:viewcompose-gesture:0.1.0-alpha05")
    implementation("com.viewcompose:viewcompose-image-coil:0.1.0-alpha05")
    implementation("com.viewcompose:viewcompose-image-glide:0.1.0-alpha03")
    implementation("com.viewcompose:viewcompose-constraintlayout-androidx:0.1.0-alpha02")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
