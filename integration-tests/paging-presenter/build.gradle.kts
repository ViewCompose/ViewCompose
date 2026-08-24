plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    testImplementation("androidx.paging:paging-common:3.5.1")
    testImplementation("androidx.paging:paging-testing:3.5.1")
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
