plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.viewcompose.constraintlayout"
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

    sourceSets["test"].java.srcDir("src/test/samples")
}

dependencies {
    implementation(project(":viewcompose-runtime"))
    api(project(":viewcompose-ui-contract"))
    api(project(":viewcompose-ui-foundation"))
    testImplementation(libs.kotlin.compiler.embeddable)
    testImplementation(libs.junit)
}
