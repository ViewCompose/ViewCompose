plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(11)
}

sourceSets {
    main {
        kotlin.srcDir("../../../viewcompose-preview-core/src/main/kotlin")
    }
}

dependencies {
    api(libs.kotlinx.serialization.json)
}
