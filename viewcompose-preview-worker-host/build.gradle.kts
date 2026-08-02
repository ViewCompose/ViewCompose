plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)

    sourceSets {
        test {
            kotlin.srcDir("src/test/samples")
        }
    }
}

application {
    mainClass.set("com.viewcompose.preview.worker.PreviewWorkerHost")
}

dependencies {
    implementation(project(":viewcompose-preview-core"))
    implementation(libs.paparazzi.worker)

    testImplementation(libs.junit)
}
