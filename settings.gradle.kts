pluginManagement {
    includeBuild("tools/viewcompose-quality-build")
    includeBuild("tools/viewcompose-preview-build")
    includeBuild("tools/viewcompose-publishing-build")
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
        // Source-tree Maven samples resolve the exact artifacts produced by the current checkout.
        // Release consumers use Maven Central; this repository never escapes the build directory.
        maven {
            url = uri(layout.rootDirectory.dir("build/maven-repository"))
            content {
                includeGroup("com.viewcompose")
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "ViewCompose"
include(":app")
include(":integration-tests:paging-presenter")
include(":samples:compose-migration")
include(":samples:counter")
include(":samples:tutorials")
include(":tools:ai-compiler-harness")
include(":tools:ai-preview-harness")
include(":viewcompose-runtime")
include(":viewcompose-text-core")
include(":viewcompose-ui-contract")
include(":viewcompose-navigation-core")
include(":viewcompose-navigation-android")
include(":viewcompose-renderer-android")
include(":viewcompose-ui-foundation")
include(":viewcompose-diagnostics")
include(":viewcompose-host-android")
include(":viewcompose-material3")
include(":viewcompose-material3-android")
include(":viewcompose-oneui7")
include(":viewcompose-android")
include(":viewcompose-overlay-android")
include(":viewcompose-overlay-material3-android")
include(":viewcompose-overlay-oneui7-android")
include(":viewcompose-image-coil")
include(":viewcompose-image-glide")
include(":viewcompose-benchmark")
include(":viewcompose-lifecycle-androidx")
include(":viewcompose-viewmodel-androidx")
include(":viewcompose-preview-core")
include(":viewcompose-preview-gradle-plugin")
include(":viewcompose-preview-runner")
include(":viewcompose-preview-worker-host")
include(":viewcompose-preview")
include(":viewcompose-animation")
include(":viewcompose-animation-core")
include(":viewcompose-gesture")
include(":viewcompose-gesture-core")
include(":viewcompose-graphics")
include(":viewcompose-graphics-core")
include(":viewcompose-shadow-android")
include(":viewcompose-constraintlayout-androidx")
include(":viewcompose-media3-androidx")
include(":viewcompose-exoplayer2-android")
include(":viewcompose-google-maps-android")
include(":viewcompose-camerax-androidx")
include(":viewcompose-paging-androidx")
 
