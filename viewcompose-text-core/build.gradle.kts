plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(project(":viewcompose-runtime"))
    testImplementation(libs.junit)
}
