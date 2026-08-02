plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(11)
    sourceSets {
        test {
            kotlin.srcDir("src/test/samples")
        }
    }
}

dependencies {
    implementation(project(":viewcompose-runtime"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
