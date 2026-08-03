// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.viewcompose.publishing.root")
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
    "viewcompose-text-core" to "com.viewcompose.text",
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
    "viewcompose-preview-gradle-plugin" to "com.viewcompose.preview.gradle",
    "viewcompose-preview-runner" to "com.viewcompose.preview.runner",
    "viewcompose-preview-worker-host" to "com.viewcompose.preview.worker",
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
    "viewcompose-text-core",
    "viewcompose-navigation-core",
    "viewcompose-preview-core",
    "viewcompose-preview-gradle-plugin",
    "viewcompose-preview-worker-host",
    "viewcompose-animation-core",
    "viewcompose-gesture-core",
    "viewcompose-graphics-core",
)

// Foundation modules sit on the runtime path and therefore use an explicit project-dependency
// allowlist. Adding an edge here is an architecture decision, not a convenient implementation
// shortcut: optional capabilities must never become prerequisites of the core render pipeline.
val foundationModuleDependencyRules = mapOf(
    "viewcompose-runtime" to emptySet(),
    "viewcompose-text-core" to setOf("viewcompose-runtime"),
    "viewcompose-navigation-core" to emptySet(),
    "viewcompose-animation-core" to setOf("viewcompose-runtime"),
    "viewcompose-graphics-core" to emptySet(),
    "viewcompose-ui-contract" to
        setOf(
            "viewcompose-runtime",
            "viewcompose-text-core",
            "viewcompose-graphics-core",
        ),
    "viewcompose-gesture-core" to setOf("viewcompose-ui-contract"),
    "viewcompose-widget-core" to
        setOf(
            "viewcompose-runtime",
            "viewcompose-text-core",
            "viewcompose-ui-contract",
        ),
    "viewcompose-renderer" to
        setOf(
            "viewcompose-runtime",
            "viewcompose-text-core",
            "viewcompose-ui-contract",
            "viewcompose-graphics-core",
            "viewcompose-gesture-core",
        ),
    "viewcompose-lifecycle" to
        setOf(
            "viewcompose-runtime",
            "viewcompose-widget-core",
        ),
    "viewcompose-viewmodel" to
        setOf(
            "viewcompose-runtime",
            "viewcompose-widget-core",
        ),
    "viewcompose-host-android" to
        setOf(
            "viewcompose-runtime",
            "viewcompose-ui-contract",
            "viewcompose-widget-core",
            "viewcompose-lifecycle",
            "viewcompose-viewmodel",
            "viewcompose-renderer",
        ),
)

// Optional capabilities may consume foundation modules, but no foundation module may depend on
// them. A capability also cannot depend on preview/build tooling.
val optionalCapabilityModules = setOf(
    "viewcompose-navigation",
    "viewcompose-animation",
    "viewcompose-gesture",
    "viewcompose-graphics",
    "viewcompose-shadow-android",
    "viewcompose-widget-constraintlayout",
    "viewcompose-overlay-android",
    "viewcompose-image-coil",
)

// Tooling is downstream of both foundation and optional capabilities and never participates in
// the application runtime dependency direction.
val toolingModules = setOf(
    "viewcompose-preview-core",
    "viewcompose-preview-gradle-plugin",
    "viewcompose-preview-runner",
    "viewcompose-preview-worker-host",
    "viewcompose-preview",
    "viewcompose-benchmark",
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
    ":viewcompose-preview-gradle-plugin:compileKotlin",
    ":viewcompose-preview-runner:compileDebugKotlin",
    ":viewcompose-preview-worker-host:compileKotlin",
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
    ":samples:counter:assembleDebug",
    ":samples:counter:compileDebugAndroidTestKotlin",
    ":app:compileDebugKotlin",
    ":viewcompose-runtime:test",
    ":viewcompose-navigation-core:test",
    ":viewcompose-navigation:testDebugUnitTest",
    ":viewcompose-ui-contract:test",
    ":viewcompose-host-android:testDebugUnitTest",
    ":viewcompose-lifecycle:testDebugUnitTest",
    ":viewcompose-viewmodel:testDebugUnitTest",
    ":viewcompose-preview-core:test",
    ":viewcompose-preview-gradle-plugin:test",
    ":viewcompose-preview-runner:testDebugUnitTest",
    ":viewcompose-preview-worker-host:test",
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

tasks.register("verifyModuleDependencyBoundaries") {
    group = "verification"
    description =
        "Verify framework modules are classified and project dependencies point in the allowed direction."
    doLast {
        val violations = mutableListOf<String>()
        val moduleReferenceRegex = Regex("""\":(viewcompose-[^\"]+)\"""")
        val projectDependencyRegex =
            Regex("""project\(\s*(?:path\s*=\s*)?\"(:[^\"]+)\"""")
        val declaredModules =
            moduleReferenceRegex.findAll(rootDir.resolve("settings.gradle.kts").readText())
                .map { match -> match.groupValues[1] }
                .toSet()
        val classifiedModules =
            foundationModuleDependencyRules.keys + optionalCapabilityModules + toolingModules

        classifiedModules.sorted().forEach { module ->
            val memberships =
                listOf(
                    module in foundationModuleDependencyRules,
                    module in optionalCapabilityModules,
                    module in toolingModules,
                ).count { membership -> membership }
            if (memberships != 1) {
                violations += "$module -> module must belong to exactly one dependency-boundary group"
            }
        }

        (declaredModules - classifiedModules).sorted().forEach { module ->
            violations +=
                "$module -> unclassified module; register it as foundation, optional capability, or tooling"
        }
        (classifiedModules - declaredModules).sorted().forEach { module ->
            violations += "$module -> boundary classification has no matching module in settings.gradle.kts"
        }
        (declaredModules - modulePackageRoots.keys).sorted().forEach { module ->
            violations += "$module -> missing canonical package-root registration"
        }

        foundationModuleDependencyRules.forEach { (module, allowedDependencies) ->
            allowedDependencies
                .filter { dependency -> dependency !in foundationModuleDependencyRules }
                .sorted()
                .forEach { dependency ->
                    violations +=
                        "$module -> invalid foundation allowlist target '$dependency'; " +
                            "foundation modules may only depend on other foundation modules"
                }
        }

        val dependenciesByModule =
            declaredModules.associateWith { module ->
                val buildFile = rootDir.resolve(module).resolve("build.gradle.kts")
                if (!buildFile.exists()) {
                    violations += "$module -> missing build.gradle.kts"
                    emptySet()
                } else {
                    projectDependencyRegex.findAll(buildFile.readText())
                        .map { match -> match.groupValues[1].removePrefix(":") }
                        .toSet()
                }
            }

        foundationModuleDependencyRules.forEach { (module, allowedDependencies) ->
            val actualDependencies = dependenciesByModule[module].orEmpty()
            (actualDependencies - allowedDependencies).sorted().forEach { dependency ->
                violations +=
                    "$module -> forbidden foundation dependency '$dependency'; " +
                        "foundation dependencies must be explicitly allowlisted"
            }
        }

        optionalCapabilityModules.forEach { module ->
            dependenciesByModule[module].orEmpty()
                .filter { dependency -> dependency in toolingModules }
                .sorted()
                .forEach { dependency ->
                    violations +=
                        "$module -> forbidden tooling dependency '$dependency'; " +
                            "runtime capabilities must stay tooling-independent"
                }
        }

        dependenciesByModule.forEach { (module, dependencies) ->
            if ("app" in dependencies) {
                violations += "$module -> framework modules must not depend on the demo app"
            }
            dependencies
                .filter { dependency ->
                    dependency.startsWith("viewcompose-") && dependency !in declaredModules
                }
                .sorted()
                .forEach { dependency ->
                    violations += "$module -> dependency '$dependency' is not declared in settings.gradle.kts"
                }
        }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Module dependency-boundary verification failed:")
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

tasks.register("verifyPreviewGradlePluginBoundary") {
    group = "verification"
    description = "Verify preview Gradle tooling uses public build APIs and stays renderer-free."
    doLast {
        val violations = mutableListOf<String>()
        val pluginDir = rootDir.resolve("viewcompose-preview-gradle-plugin")
        val pluginMainDir = pluginDir.resolve("src/main")
        if (pluginMainDir.exists()) {
            pluginMainDir.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                .forEach { file ->
                    file.useLines { lines ->
                        lines.forEachIndexed { index, line ->
                            val trimmed = line.trimStart()
                            if (
                                trimmed.startsWith("import android.") ||
                                trimmed.startsWith("import androidx.") ||
                                trimmed.startsWith("import com.android.build.gradle.internal.") ||
                                trimmed.startsWith("import com.android.tools.idea.") ||
                                trimmed.startsWith("import com.viewcompose.preview.runner.")
                            ) {
                                violations +=
                                    "${file.relativeTo(rootDir)}:${index + 1} -> forbidden import '$trimmed'"
                            }
                        }
                    }
                }
        }
        val buildFile = pluginDir.resolve("build.gradle.kts")
        if (buildFile.exists() && buildFile.readText().contains("viewcompose-preview-runner")) {
            violations +=
                "${buildFile.relativeTo(rootDir)} -> Gradle tooling must not depend on the renderer"
        }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Preview Gradle plugin boundary verification failed:")
                    violations.sorted().forEach { appendLine("- $it") }
                },
            )
        }
    }
}

tasks.register("verifyPreviewWorkerHostBoundary") {
    group = "verification"
    description =
        "Verify the preview worker host stays independent from Gradle, Android Studio, and runner binaries."
    doLast {
        val violations = mutableListOf<String>()
        val hostDir = rootDir.resolve("viewcompose-preview-worker-host")
        val sourceDir = hostDir.resolve("src/main")
        if (sourceDir.exists()) {
            sourceDir.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                .forEach { file ->
                    file.useLines { lines ->
                        lines.forEachIndexed { index, line ->
                            val trimmed = line.trimStart()
                            if (
                                trimmed.startsWith("import org.gradle.") ||
                                trimmed.startsWith("import com.intellij.") ||
                                trimmed.startsWith("import org.jetbrains.android.")
                            ) {
                                violations +=
                                    "${file.relativeTo(rootDir)}:${index + 1} -> forbidden import '$trimmed'"
                            }
                        }
                    }
                }
        }
        val buildFile = hostDir.resolve("build.gradle.kts")
        if (buildFile.exists()) {
            val content = buildFile.readText()
            listOf(
                "viewcompose-preview-gradle-plugin",
                "viewcompose-preview-runner",
            ).forEach { forbiddenDependency ->
                if (content.contains(forbiddenDependency)) {
                    violations +=
                        "viewcompose-preview-worker-host/build.gradle.kts -> " +
                            "forbidden dependency '$forbiddenDependency'"
                }
            }
        }
        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Preview worker host boundary verification failed:")
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

tasks.register("verifyDocumentationStructure") {
    group = "verification"
    description =
        "Verifies documentation placement, link-graph coverage, module catalog, and relative links."

    val allowedRootMarkdown =
        setOf(
            "AGENTS.md",
            "CODE_OF_CONDUCT.md",
            "CONTRIBUTING.md",
            "README.md",
            "README.zh-CN.md",
            "THIRD_PARTY_NOTICES.md",
        )
    val allowedDocsDirectories =
        setOf(
            "architecture",
            "archive",
            "getting-started",
            "guides",
            "migration",
            "modules",
            "project",
            "tooling",
            "tutorials",
        )
    val activeDocumentName = Regex("[a-z0-9]+(?:-[a-z0-9]+)*\\.md")
    val markdownLink = Regex("""\]\(([^)]+)\)""")
    val htmlLink = Regex("""href=["']([^"']+)["']""")
    val windowsAbsolutePath = Regex("^[A-Za-z]:[/\\\\]")

    doLast {
        val violations = mutableListOf<String>()
        val documentationRoot = rootDir.resolve("docs")
        val documentationIndex = documentationRoot.resolve("README.md")

        val rootMarkdown =
            rootDir.listFiles()
                .orEmpty()
                .filter { file -> file.isFile && file.extension.equals("md", ignoreCase = true) }
                .map { file -> file.name }
                .toSet()
        (rootMarkdown - allowedRootMarkdown).sorted().forEach { fileName ->
            violations += "$fileName -> Markdown is not allowed at the repository root"
        }

        documentationRoot.listFiles()
            .orEmpty()
            .filter(File::isDirectory)
            .map(File::getName)
            .filterNot(allowedDocsDirectories::contains)
            .sorted()
            .forEach { directory ->
                violations += "docs/$directory -> undocumented top-level documentation category"
            }

        val activeDocuments =
            documentationRoot.walkTopDown()
                .filter(File::isFile)
                .filter { file -> file.extension.equals("md", ignoreCase = true) }
                .filterNot { file ->
                    file.relativeTo(documentationRoot).invariantSeparatorsPath.startsWith("archive/")
                }
                .toList()

        activeDocuments.forEach { file ->
            if (file.name != "README.md" && !activeDocumentName.matches(file.name)) {
                violations +=
                    "${file.relativeTo(rootDir).invariantSeparatorsPath} -> " +
                        "active document names must use lowercase kebab-case"
            }
        }

        if (!documentationIndex.isFile) {
            violations += "docs/README.md -> canonical documentation index is missing"
        } else {
            val activeDocumentFiles = activeDocuments.map(File::getCanonicalFile).toSet()
            val reachableDocuments = mutableSetOf<File>()
            val pendingDocuments = java.util.ArrayDeque<File>()
            pendingDocuments.add(documentationIndex.canonicalFile)

            while (pendingDocuments.isNotEmpty()) {
                val currentDocument = pendingDocuments.removeFirst()
                if (!reachableDocuments.add(currentDocument)) continue

                val targets =
                    buildList {
                        val content = currentDocument.readText()
                        markdownLink.findAll(content).forEach { match -> add(match.groupValues[1]) }
                        htmlLink.findAll(content).forEach { match -> add(match.groupValues[1]) }
                    }
                targets.forEach targetLoop@{ rawTarget ->
                    val target = rawTarget.trim().removePrefix("<").removeSuffix(">")
                    if (
                        target.isEmpty() ||
                        target.startsWith("#") ||
                        target.startsWith("mailto:") ||
                        target.contains("://") ||
                        target.startsWith("/") ||
                        windowsAbsolutePath.containsMatchIn(target)
                    ) {
                        return@targetLoop
                    }
                    val path = target.substringBefore('#').substringBefore('?')
                    if (path.isEmpty()) return@targetLoop
                    val linkedDocument = currentDocument.parentFile.resolve(path).normalize()
                    if (linkedDocument.isFile) {
                        val canonicalDocument = linkedDocument.canonicalFile
                        if (
                            canonicalDocument in activeDocumentFiles &&
                            canonicalDocument !in reachableDocuments
                        ) {
                            pendingDocuments.add(canonicalDocument)
                        }
                    }
                }
            }

            activeDocuments
                .filterNot { file -> file.canonicalFile == documentationIndex.canonicalFile }
                .filterNot { file -> file.canonicalFile in reachableDocuments }
                .sortedBy { file -> file.path }
                .forEach { file ->
                    violations +=
                        "${file.relativeTo(rootDir).invariantSeparatorsPath} -> " +
                            "active document is not reachable from docs/README.md"
                }
        }

        val publishingPropertiesFile = rootDir.resolve("gradle/viewcompose-publishing.properties")
        val moduleCatalog = documentationRoot.resolve("modules/README.md")
        if (!publishingPropertiesFile.isFile) {
            violations +=
                "gradle/viewcompose-publishing.properties -> publishing metadata is missing"
        } else if (!moduleCatalog.isFile) {
            violations += "docs/modules/README.md -> published module catalog is missing"
        } else {
            val publishingProperties = java.util.Properties()
            publishingPropertiesFile.inputStream().use(publishingProperties::load)
            val publishedModules =
                publishingProperties.stringPropertyNames()
                    .filter { property ->
                        property.startsWith("module.") && property.endsWith(".version")
                    }
                    .map { property ->
                        property.removePrefix("module.").removeSuffix(".version")
                    }
                    .toSet()
            val moduleRow =
                Regex(
                    pattern = """^\|\s*`(viewcompose-[a-z0-9-]+)`\s*\|""",
                    option = RegexOption.MULTILINE,
                )
            val moduleCatalogContent = moduleCatalog.readText()
            val catalogModules =
                moduleRow.findAll(moduleCatalogContent)
                    .map { match -> match.groupValues[1] }
                    .toList()
            catalogModules.groupingBy { module -> module }.eachCount()
                .filterValues { count -> count > 1 }
                .keys
                .sorted()
                .forEach { module ->
                    violations +=
                        "docs/modules/README.md -> duplicate published module row: $module"
                }
            (publishedModules - catalogModules.toSet()).sorted().forEach { module ->
                violations +=
                    "docs/modules/README.md -> published module is missing from catalog: $module"
            }
            (catalogModules.toSet() - publishedModules).sorted().forEach { module ->
                violations +=
                    "docs/modules/README.md -> catalog artifact has no publishing version: $module"
            }
            publishedModules.sorted().forEach { module ->
                val expectedManual = documentationRoot.resolve("modules/$module/README.md")
                if (!expectedManual.isFile) {
                    violations +=
                        "docs/modules/$module/README.md -> published module manual is missing"
                }
                val expectedLink = "[Available](./$module/README.md)"
                val row = moduleCatalogContent.lineSequence().firstOrNull { line ->
                    line.trimStart().startsWith("| `$module` |")
                }
                if (row == null || expectedLink !in row) {
                    violations +=
                        "docs/modules/README.md -> $module must link its available module manual"
                }
            }
        }

        val checkedMarkdown =
            rootDir.walkTopDown()
                .onEnter { directory ->
                    directory == rootDir ||
                        directory.name !in
                        setOf(
                            ".codegraph",
                            ".docusaurus",
                            ".git",
                            ".gradle",
                            "build",
                            "generated",
                            "node_modules",
                        )
                }
                .filter(File::isFile)
                .filter { file -> file.extension.equals("md", ignoreCase = true) }
                .filterNot { file ->
                    file.toPath().startsWith(documentationRoot.resolve("archive").toPath())
                }
                .filterNot { file ->
                    // Docusaurus owns locale-aware routing and translation freshness checks.
                    file.toPath().startsWith(rootDir.resolve("website/i18n").toPath())
                }
                .toList()

        checkedMarkdown.forEach { file ->
            val content = file.readText()
            val targets =
                buildList {
                    markdownLink.findAll(content).forEach { match -> add(match.groupValues[1]) }
                    htmlLink.findAll(content).forEach { match -> add(match.groupValues[1]) }
                }
            targets.forEach targetLoop@{ rawTarget ->
                val target = rawTarget.trim().removePrefix("<").removeSuffix(">")
                if (target.isEmpty() || target.startsWith("#") || target.startsWith("mailto:")) {
                    return@targetLoop
                }
                if (target.contains("://")) {
                    if (target.startsWith("file://")) {
                        violations +=
                            "${file.relativeTo(rootDir).invariantSeparatorsPath} -> " +
                                "local file link is forbidden: $target"
                    }
                    return@targetLoop
                }
                if (target.startsWith("/") || windowsAbsolutePath.containsMatchIn(target)) {
                    violations +=
                        "${file.relativeTo(rootDir).invariantSeparatorsPath} -> " +
                            "absolute link is forbidden: $target"
                    return@targetLoop
                }
                val path = target.substringBefore('#').substringBefore('?')
                if (path.isNotEmpty() && !file.parentFile.resolve(path).normalize().exists()) {
                    violations +=
                        "${file.relativeTo(rootDir).invariantSeparatorsPath} -> " +
                            "broken relative link: $target"
                }
            }
        }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Documentation structure verification failed:")
                    violations.distinct().sorted().forEach { violation ->
                        appendLine("- $violation")
                    }
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
    dependsOn("verifyModuleDependencyBoundaries")
    dependsOn("verifyDocumentationStructure")
    dependsOn("verifyViewComposePublishingConfiguration")
    dependsOn("verifyRuntimePurity")
    dependsOn("verifyNavigationCorePurity")
    dependsOn("verifyGestureCorePurity")
    dependsOn("verifyGraphicsCorePurity")
    dependsOn("verifyPreviewCorePurity")
    dependsOn("verifyPreviewRunnerBoundary")
    dependsOn("verifyPreviewGradlePluginBoundary")
    dependsOn("verifyPreviewWorkerHostBoundary")
    dependsOn(qaQuickTasks)
}

tasks.register("qaFull") {
    group = "verification"
    description = "Run qaQuick plus connected UI tests on device/emulator."
    dependsOn(
        "qaQuick",
        ":app:connectedDebugAndroidTest",
        ":samples:counter:connectedDebugAndroidTest",
    )
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
