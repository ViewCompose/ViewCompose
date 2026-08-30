pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.viewcompose.preview") {
                useModule(
                    "com.viewcompose:viewcompose-preview-gradle-plugin:${requested.version}",
                )
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "viewcompose-ai-harness"
include(":compiler")
include(":preview")
