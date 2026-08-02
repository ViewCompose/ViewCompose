plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

group = "com.viewcompose.build"

dependencies {
    implementation("com.android.tools.build:gradle:8.13.2")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:2.2.0")
    // 0.34.x supports this build's Kotlin 2.0 convention plugin toolchain. 0.36+ requires Kotlin 2.2.
    implementation("com.vanniktech:gradle-maven-publish-plugin:0.34.0")
}

gradlePlugin {
    plugins {
        create("viewComposePublishingRoot") {
            id = "com.viewcompose.publishing.root"
            implementationClass =
                "com.viewcompose.publishing.ViewComposePublishingRootPlugin"
        }
    }
}
