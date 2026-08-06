pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            name = "viewComposeLocal"
            url = uri("../../build/maven-repository")
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "viewcompose-publishing-smoke"
include(":feature-consumer")
include(":core-consumer")
include(":host-consumer")
include(":engine-consumer")
