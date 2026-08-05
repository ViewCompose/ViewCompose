plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(11)
    sourceSets.named("test") {
        kotlin.srcDir("src/test/samples")
    }
}

dependencies {
    implementation(project(":viewcompose-runtime"))
    api(project(":viewcompose-text-core"))
    api(project(":viewcompose-graphics-core"))
    testImplementation(libs.junit)
}
