plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
}

kotlin {
    jvmToolchain(11)
}

tasks.withType<Test>().configureEach {
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(17))
        },
    )
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
    implementation(project(":viewcompose-preview-core"))
    implementation(libs.asm)

    implementation("com.android.tools.build:gradle:${libs.versions.agp.get()}")

    testImplementation(gradleTestKit())
    testImplementation(libs.junit)
}
