// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.paparazzi) apply false
}

val modulePackageRoots = mapOf(
    "app" to "com.viewcompose",
    "viewcompose-runtime" to "com.viewcompose.runtime",
    "viewcompose-navigation-core" to "com.viewcompose.navigation.core",
    "viewcompose-navigation" to "com.viewcompose.navigation",
    "viewcompose-ui-contract" to "com.viewcompose.ui",
    "viewcompose-renderer" to "com.viewcompose.renderer",
    "viewcompose-widget-core" to "com.viewcompose.widget.core",
    "viewcompose-host-android" to "com.viewcompose.host.android",
    "viewcompose-overlay-android" to "com.viewcompose.overlay.android",
    "viewcompose-image-coil" to "com.viewcompose.image.coil",
    "viewcompose-benchmark" to "com.viewcompose.benchmark",
    "viewcompose-lifecycle" to "com.viewcompose.lifecycle",
    "viewcompose-viewmodel" to "com.viewcompose.viewmodel",
    "viewcompose-preview-core" to "com.viewcompose.preview.tooling",
    "viewcompose-preview-runner" to "com.viewcompose.preview.runner",
    "viewcompose-preview" to "com.viewcompose.preview",
    "viewcompose-animation" to "com.viewcompose.animation",
    "viewcompose-animation-core" to "com.viewcompose.animation.core",
    "viewcompose-gesture" to "com.viewcompose.gesture",
    "viewcompose-gesture-core" to "com.viewcompose.gesture.core",
    "viewcompose-graphics" to "com.viewcompose.graphics",
    "viewcompose-graphics-core" to "com.viewcompose.graphics.core",
    "viewcompose-shadow-android" to "com.viewcompose.shadow.android",
    "viewcompose-widget-constraintlayout" to "com.viewcompose.widget.constraintlayout",
)

val kotlinJvmModules = setOf(
    "viewcompose-ui-contract",
    "viewcompose-runtime",
    "viewcompose-navigation-core",
    "viewcompose-preview-core",
    "viewcompose-animation-core",
    "viewcompose-gesture-core",
    "viewcompose-graphics-core",
)

val qaQuickTasks = listOf(
    ":viewcompose-runtime:compileKotlin",
    ":viewcompose-navigation-core:compileKotlin",
    ":viewcompose-navigation:compileDebugKotlin",
    ":viewcompose-ui-contract:compileKotlin",
    ":viewcompose-host-android:compileDebugKotlin",
    ":viewcompose-lifecycle:compileDebugKotlin",
    ":viewcompose-viewmodel:compileDebugKotlin",
    ":viewcompose-preview-core:compileKotlin",
    ":viewcompose-preview-runner:compileDebugKotlin",
    ":viewcompose-renderer:compileDebugKotlin",
    ":viewcompose-widget-core:compileDebugKotlin",
    ":viewcompose-overlay-android:compileDebugKotlin",
    ":viewcompose-image-coil:compileDebugKotlin",
    ":viewcompose-preview:compileDebugKotlin",
    ":viewcompose-animation:compileDebugKotlin",
    ":viewcompose-animation-core:compileKotlin",
    ":viewcompose-gesture:compileDebugKotlin",
    ":viewcompose-gesture-core:compileKotlin",
    ":viewcompose-graphics:compileDebugKotlin",
    ":viewcompose-graphics-core:compileKotlin",
    ":viewcompose-shadow-android:compileDebugKotlin",
    ":viewcompose-widget-constraintlayout:compileDebugKotlin",
    ":app:compileDebugKotlin",
    ":viewcompose-runtime:test",
    ":viewcompose-navigation-core:test",
    ":viewcompose-navigation:testDebugUnitTest",
    ":viewcompose-ui-contract:test",
    ":viewcompose-host-android:testDebugUnitTest",
    ":viewcompose-lifecycle:testDebugUnitTest",
    ":viewcompose-viewmodel:testDebugUnitTest",
    ":viewcompose-preview-core:test",
    ":viewcompose-preview-runner:testDebugUnitTest",
    ":viewcompose-renderer:testDebugUnitTest",
    ":viewcompose-widget-core:testDebugUnitTest",
    ":viewcompose-overlay-android:testDebugUnitTest",
    ":viewcompose-image-coil:testDebugUnitTest",
    ":viewcompose-preview:testDebugUnitTest",
    ":viewcompose-animation:testDebugUnitTest",
    ":viewcompose-animation-core:test",
    ":viewcompose-gesture:testDebugUnitTest",
    ":viewcompose-gesture-core:test",
    ":viewcompose-graphics:testDebugUnitTest",
    ":viewcompose-graphics-core:test",
    ":viewcompose-shadow-android:testDebugUnitTest",
    ":viewcompose-widget-constraintlayout:testDebugUnitTest",
    ":app:testDebugUnitTest",
)

tasks.register("verifyModulePackageRoots") {
    group = "verification"
    description = "Verify source package declarations follow module package-root prefixes."
    doLast {
        val packageRegex = Regex("^\\s*package\\s+([A-Za-z0-9_.]+)", RegexOption.MULTILINE)
        val sourceSets = listOf("main", "test", "androidTest")
        val violations = mutableListOf<String>()

        modulePackageRoots.forEach { (module, expectedPrefix) ->
            val srcDir = rootDir.resolve(module).resolve("src")
            if (!srcDir.exists()) return@forEach
            sourceSets.forEach sourceSetLoop@{ sourceSet ->
                val sourceSetDir = srcDir.resolve(sourceSet)
                if (!sourceSetDir.exists()) return@sourceSetLoop
                sourceSetDir.walkTopDown()
                    .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                    .forEach fileLoop@{ file ->
                        val content = file.readText()
                        val packageName = packageRegex.find(content)?.groupValues?.getOrNull(1)
                        if (packageName == null) {
                            violations += "${module}:${sourceSet}:${file.relativeTo(rootDir)} -> missing package declaration"
                            return@fileLoop
                        }
                        if (!packageName.startsWith(expectedPrefix)) {
                            violations += "${module}:${sourceSet}:${file.relativeTo(rootDir)} -> package '$packageName' not under '$expectedPrefix'"
                        }
                    }
            }
        }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Module package-root verification failed:")
                    violations.sorted().forEach { appendLine("- $it") }
                },
            )
        }
    }
}

tasks.register("verifyAndroidModuleNamespaces") {
    group = "verification"
    description = "Verify Android module namespace matches canonical package-root mapping."
    doLast {
        val namespaceRegex = Regex("""namespace\s*=\s*"([^"]+)"""")
        val violations = mutableListOf<String>()

        modulePackageRoots.forEach { (module, expectedNamespace) ->
            if (module in kotlinJvmModules) return@forEach
            val buildFile = rootDir.resolve(module).resolve("build.gradle.kts")
            if (!buildFile.exists()) {
                violations += "$module -> missing build.gradle.kts"
                return@forEach
            }
            val content = buildFile.readText()
            val actualNamespace = namespaceRegex.find(content)?.groupValues?.getOrNull(1)
            if (actualNamespace == null) {
                violations += "$module -> missing namespace declaration"
                return@forEach
            }
            if (actualNamespace != expectedNamespace) {
                violations += "$module -> namespace '$actualNamespace' != expected '$expectedNamespace'"
            }
        }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Android namespace verification failed:")
                    violations.sorted().forEach { appendLine("- $it") }
                },
            )
        }
    }
}

tasks.register("verifyRuntimePurity") {
    group = "verification"
    description = "Verify runtime remains Kotlin/JVM-pure without Android imports/dependencies."
    doLast {
        val violations = mutableListOf<String>()
        val runtimeMainDir = rootDir.resolve("viewcompose-runtime").resolve("src/main")
        if (runtimeMainDir.exists()) {
            runtimeMainDir.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                .forEach { file ->
                    file.useLines { lines ->
                        lines.forEachIndexed { index, line ->
                            val trimmed = line.trimStart()
                            if (
                                trimmed.startsWith("import android.") ||
                                trimmed.startsWith("import androidx.")
                            ) {
                                violations += "${file.relativeTo(rootDir)}:${index + 1} -> forbidden import '$trimmed'"
                            }
                        }
                    }
                }
        }

        val runtimeBuildFile = rootDir.resolve("viewcompose-runtime/build.gradle.kts")
        if (runtimeBuildFile.exists()) {
            val content = runtimeBuildFile.readText()
            if (content.contains("androidx.core.ktx")) {
                violations += "viewcompose-runtime/build.gradle.kts -> forbidden dependency androidx.core.ktx"
            }
        }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Runtime purity verification failed:")
                    violations.sorted().forEach { appendLine("- $it") }
                },
            )
        }
    }
}

tasks.register("verifyGestureCorePurity") {
    group = "verification"
    description = "Verify gesture-core remains Kotlin/JVM-pure without Android imports."
    doLast {
        val violations = mutableListOf<String>()
        val gestureCoreMainDir = rootDir.resolve("viewcompose-gesture-core").resolve("src/main")
        if (gestureCoreMainDir.exists()) {
            gestureCoreMainDir.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                .forEach { file ->
                    file.useLines { lines ->
                        lines.forEachIndexed { index, line ->
                            val trimmed = line.trimStart()
                            if (
                                trimmed.startsWith("import android.") ||
                                trimmed.startsWith("import androidx.")
                            ) {
                                violations += "${file.relativeTo(rootDir)}:${index + 1} -> forbidden import '$trimmed'"
                            }
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Gesture-core purity verification failed:")
                    violations.sorted().forEach { appendLine("- $it") }
                },
            )
        }
    }
}

tasks.register("verifyGraphicsCorePurity") {
    group = "verification"
    description = "Verify graphics-core remains Kotlin/JVM-pure without Android imports."
    doLast {
        val violations = mutableListOf<String>()
        val graphicsCoreMainDir = rootDir.resolve("viewcompose-graphics-core").resolve("src/main")
        if (graphicsCoreMainDir.exists()) {
            graphicsCoreMainDir.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                .forEach { file ->
                    file.useLines { lines ->
                        lines.forEachIndexed { index, line ->
                            val trimmed = line.trimStart()
                            if (
                                trimmed.startsWith("import android.") ||
                                trimmed.startsWith("import androidx.")
                            ) {
                                violations += "${file.relativeTo(rootDir)}:${index + 1} -> forbidden import '$trimmed'"
                            }
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Graphics-core purity verification failed:")
                    violations.sorted().forEach { appendLine("- $it") }
                },
            )
        }
    }
}

tasks.register("verifyPreviewCorePurity") {
    group = "verification"
    description = "Verify preview-core remains Kotlin/JVM-pure without Android or Compose imports."
    doLast {
        val violations = mutableListOf<String>()
        val previewCoreMainDir = rootDir.resolve("viewcompose-preview-core").resolve("src/main")
        if (previewCoreMainDir.exists()) {
            previewCoreMainDir.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                .forEach { file ->
                    file.useLines { lines ->
                        lines.forEachIndexed { index, line ->
                            val trimmed = line.trimStart()
                            if (
                                trimmed.startsWith("import android.") ||
                                trimmed.startsWith("import androidx.")
                            ) {
                                violations +=
                                    "${file.relativeTo(rootDir)}:${index + 1} -> forbidden import '$trimmed'"
                            }
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Preview-core purity verification failed:")
                    violations.sorted().forEach { appendLine("- $it") }
                },
            )
        }
    }
}

tasks.register("verifyPreviewRunnerBoundary") {
    group = "verification"
    description = "Verify the native static preview runner stays independent from Compose."
    doLast {
        val violations = mutableListOf<String>()
        val runnerDir = rootDir.resolve("viewcompose-preview-runner")
        val runnerMainDir = runnerDir.resolve("src/main")
        if (runnerMainDir.exists()) {
            runnerMainDir.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                .forEach { file ->
                    file.useLines { lines ->
                        lines.forEachIndexed { index, line ->
                            val trimmed = line.trimStart()
                            if (trimmed.startsWith("import androidx.compose.")) {
                                violations +=
                                    "${file.relativeTo(rootDir)}:${index + 1} -> forbidden import '$trimmed'"
                            }
                        }
                    }
                }
        }
        val buildFile = runnerDir.resolve("build.gradle.kts")
        if (buildFile.exists()) {
            val buildScript = buildFile.readText()
            listOf("libs.plugins.kotlin.compose", "libs.androidx.compose").forEach { marker ->
                if (buildScript.contains(marker)) {
                    violations +=
                        "${buildFile.relativeTo(rootDir)} -> forbidden Compose dependency '$marker'"
                }
            }
        }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Preview-runner boundary verification failed:")
                    violations.sorted().forEach { appendLine("- $it") }
                },
            )
        }
    }
}

tasks.register("verifyNavigationCorePurity") {
    group = "verification"
    description = "Verify navigation-core remains Kotlin/JVM-pure without Android imports."
    doLast {
        val violations = mutableListOf<String>()
        val navigationCoreMainDir = rootDir.resolve("viewcompose-navigation-core").resolve("src/main")
        if (navigationCoreMainDir.exists()) {
            navigationCoreMainDir.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                .forEach { file ->
                    file.useLines { lines ->
                        lines.forEachIndexed { index, line ->
                            val trimmed = line.trimStart()
                            if (
                                trimmed.startsWith("import android.") ||
                                trimmed.startsWith("import androidx.")
                            ) {
                                violations += "${file.relativeTo(rootDir)}:${index + 1} -> forbidden import '$trimmed'"
                            }
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Navigation core purity verification failed:")
                    violations.sorted().forEach { appendLine("- $it") }
                },
            )
        }
    }
}

tasks.register("qaQuick") {
    group = "verification"
    description = "Run compile + unit-test quality gate for all core modules."
    dependsOn("verifyModulePackageRoots")
    dependsOn("verifyAndroidModuleNamespaces")
    dependsOn("verifyRuntimePurity")
    dependsOn("verifyNavigationCorePurity")
    dependsOn("verifyGestureCorePurity")
    dependsOn("verifyGraphicsCorePurity")
    dependsOn("verifyPreviewCorePurity")
    dependsOn("verifyPreviewRunnerBoundary")
    dependsOn(qaQuickTasks)
}

tasks.register("qaFull") {
    group = "verification"
    description = "Run qaQuick plus connected UI tests on device/emulator."
    dependsOn("qaQuick", ":app:connectedDebugAndroidTest")
}

tasks.register("qaRelease") {
    group = "verification"
    description = "Assemble the optimized release and non-debuggable benchmark artifacts."
    dependsOn(
        ":app:assembleRelease",
        ":app:assembleBenchmark",
        ":viewcompose-benchmark:assembleBenchmark",
    )
}

tasks.register("benchmarkRelease") {
    group = "verification"
    description = "Run release macrobenchmarks on a connected device or emulator."
    dependsOn(":viewcompose-benchmark:connectedBenchmarkAndroidTest")
}

tasks.register<Exec>("benchmarkComparisonReport") {
    group = "verification"
    description = "Generate paired ViewCompose/Compose benchmark Markdown and JSON reports."
    workingDir(rootDir)
    mustRunAfter("benchmarkRelease")
    doFirst {
        val current = providers.gradleProperty("benchmarkResult").orNull
            ?: "viewcompose-benchmark/build/outputs/connected_android_test_additional_output"
        val command = mutableListOf(
            "python3",
            rootDir.resolve("tools/performance/compare_macrobenchmarks.py").absolutePath,
            current,
        )
        providers.gradleProperty("benchmarkBaseline").orNull?.let { baseline ->
            command += listOf(
                "--baseline",
                baseline,
                "--enforce",
            )
        }
        commandLine(command)
    }
}

tasks.register("benchmarkCompare") {
    group = "verification"
    description = "Run release macrobenchmarks and generate the paired comparison report."
    dependsOn(
        "benchmarkRelease",
        "benchmarkComparisonReport",
    )
}

tasks.register<Exec>("testBenchmarkComparisonTool") {
    group = "verification"
    description = "Run unit tests for the Macrobenchmark comparison report tool."
    workingDir(rootDir.resolve("tools/performance"))
    commandLine(
        "python3",
        "-m",
        "unittest",
        "test_compare_macrobenchmarks.py",
    )
}

tasks.register("qaPreview") {
    group = "verification"
    description = "Run static-runner tests and preview snapshot verification."
    dependsOn(
        ":viewcompose-preview-core:test",
        ":viewcompose-preview-runner:testDebugUnitTest",
        ":viewcompose-preview:verifyPaparazziDebug",
    )
}
