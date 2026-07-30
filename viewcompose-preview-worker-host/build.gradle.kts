plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.viewcompose.preview.worker.PreviewWorkerHost")
}

dependencies {
    implementation(project(":viewcompose-preview-core"))
    implementation(libs.paparazzi)

    testImplementation(libs.junit)
}
