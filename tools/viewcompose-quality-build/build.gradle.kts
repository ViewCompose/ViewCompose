plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

kotlin {
    jvmToolchain(17)
}

group = "com.viewcompose.quality"

dependencies {
    testImplementation(gradleTestKit())
    testImplementation("junit:junit:4.13.2")
}

gradlePlugin {
    plugins {
        create("viewComposeQualityRoot") {
            id = "com.viewcompose.quality.root"
            implementationClass = "com.viewcompose.quality.ViewComposeQualityRootPlugin"
            displayName = "ViewCompose repository quality"
            description = "Provides compiled, testable ownership for ViewCompose quality gates."
        }
    }
}
