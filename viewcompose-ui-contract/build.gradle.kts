plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    api(project(":viewcompose-text-core"))
    implementation(project(":viewcompose-graphics-core"))
    testImplementation(libs.junit)
}
