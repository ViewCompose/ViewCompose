plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
}

kotlin {
    jvmToolchain(11)
}

sourceSets {
    main {
        kotlin.srcDir("../../viewcompose-preview-gradle-plugin/src/main/kotlin")
    }
}

gradlePlugin {
    plugins {
        create("viewComposePreview") {
            id = "com.viewcompose.preview"
            implementationClass =
                "com.viewcompose.preview.gradle.ViewComposePreviewGradlePlugin"
            displayName = "ViewCompose static preview"
            description = "Exports Android build inputs and ViewCompose static preview descriptors."
        }
    }
}

dependencies {
    implementation(project(":preview-core"))
    implementation(libs.asm)
    implementation("com.android.tools.build:gradle:${libs.versions.agp.get()}")
}
