plugins {
    alias(libs.plugins.kotlin.jvm)
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
    testImplementation(libs.junit)
}
