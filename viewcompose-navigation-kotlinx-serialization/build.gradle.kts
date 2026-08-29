plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(11)
    sourceSets {
        named("test") {
            kotlin.srcDir("src/test/samples")
        }
    }
}

dependencies {
    api(project(":viewcompose-navigation-core"))
    api(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
