pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ViewCompose"
include(":app")
include(":viewcompose-runtime")
include(":viewcompose-text-core")
include(":viewcompose-ui-contract")
include(":viewcompose-navigation-core")
include(":viewcompose-navigation")
include(":viewcompose-renderer")
include(":viewcompose-widget-core")
include(":viewcompose-host-android")
include(":viewcompose-overlay-android")
include(":viewcompose-image-coil")
include(":viewcompose-benchmark")
include(":viewcompose-lifecycle")
include(":viewcompose-viewmodel")
include(":viewcompose-preview")
include(":viewcompose-animation")
include(":viewcompose-animation-core")
include(":viewcompose-gesture")
include(":viewcompose-gesture-core")
include(":viewcompose-graphics")
include(":viewcompose-graphics-core")
include(":viewcompose-widget-constraintlayout")
 
