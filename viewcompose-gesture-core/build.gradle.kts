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
    implementation(project(":viewcompose-ui-contract"))
    testImplementation(libs.junit)
}
