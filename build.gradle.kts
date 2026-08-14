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
    "viewcompose-navigation-android" to "com.viewcompose.navigation",
    "viewcompose-ui-contract" to "com.viewcompose.ui",
    "viewcompose-renderer-android" to "com.viewcompose.renderer",
    "viewcompose-ui-foundation" to "com.viewcompose.ui.foundation",
    "viewcompose-host-android" to "com.viewcompose.host.android",
    "viewcompose-material3" to "com.viewcompose.material3",
    "viewcompose-material3-android" to "com.viewcompose.material3.android",
    "viewcompose-oneui7" to "com.viewcompose.oneui7",
    "viewcompose-android" to "com.viewcompose.android",
    "viewcompose-overlay-android" to "com.viewcompose.overlay.android",
    "viewcompose-overlay-material3-android" to "com.viewcompose.overlay.material3.android",
    "viewcompose-overlay-oneui7-android" to "com.viewcompose.overlay.oneui7.android",
    "viewcompose-image-coil" to "com.viewcompose.image.coil",
    "viewcompose-image-glide" to "com.viewcompose.image.glide",
    "viewcompose-benchmark" to "com.viewcompose.benchmark",
    "viewcompose-lifecycle-androidx" to "com.viewcompose.lifecycle",
    "viewcompose-viewmodel-androidx" to "com.viewcompose.viewmodel",
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
    "viewcompose-constraintlayout-androidx" to "com.viewcompose.constraintlayout",
)

val forbiddenLegacyPackageRoots = setOf(
    "com.viewcompose.widget.core",
    "com.viewcompose.widget.constraintlayout",
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

// Every published runtime module belongs to exactly one architectural layer. The layer gate is
// intentionally independent of Maven api/implementation visibility: both kinds of project edge
// must respect the same ownership direction.
val runtimeModuleLayers = mapOf(
    "viewcompose-runtime" to "kernel",
    "viewcompose-text-core" to "kernel",
    "viewcompose-navigation-core" to "kernel",
    "viewcompose-animation-core" to "kernel",
    "viewcompose-graphics-core" to "kernel",
    "viewcompose-ui-contract" to "kernel",
    "viewcompose-gesture-core" to "kernel",
    "viewcompose-ui-foundation" to "ui-foundation",
    "viewcompose-animation" to "ui-foundation",
    "viewcompose-gesture" to "ui-foundation",
    "viewcompose-graphics" to "ui-foundation",
    "viewcompose-renderer-android" to "android-engine",
    "viewcompose-host-android" to "android-engine",
    "viewcompose-material3" to "design-system",
    "viewcompose-oneui7" to "design-system",
    "viewcompose-navigation-android" to "integration",
    "viewcompose-material3-android" to "aggregate",
    "viewcompose-lifecycle-androidx" to "integration",
    "viewcompose-viewmodel-androidx" to "integration",
    "viewcompose-constraintlayout-androidx" to "integration",
    "viewcompose-overlay-android" to "integration",
    "viewcompose-overlay-material3-android" to "integration",
    "viewcompose-overlay-oneui7-android" to "integration",
    "viewcompose-image-coil" to "integration",
    "viewcompose-image-glide" to "integration",
    "viewcompose-shadow-android" to "integration",
    "viewcompose-android" to "aggregate",
)

val allowedDependencyLayers = mapOf(
    "kernel" to setOf("kernel"),
    "ui-foundation" to setOf("kernel", "ui-foundation"),
    "android-engine" to setOf("kernel", "ui-foundation", "android-engine"),
    "design-system" to setOf("kernel", "ui-foundation"),
    "integration" to setOf("kernel", "ui-foundation", "android-engine", "design-system", "integration"),
    "aggregate" to
        setOf("kernel", "ui-foundation", "android-engine", "design-system", "integration", "aggregate"),
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
    ":viewcompose-navigation-android:compileDebugKotlin",
    ":viewcompose-ui-contract:compileKotlin",
    ":viewcompose-host-android:compileDebugKotlin",
    ":viewcompose-material3:compileDebugKotlin",
    ":viewcompose-material3-android:compileDebugKotlin",
    ":viewcompose-oneui7:compileDebugKotlin",
    ":viewcompose-android:compileDebugKotlin",
    ":viewcompose-lifecycle-androidx:compileDebugKotlin",
    ":viewcompose-viewmodel-androidx:compileDebugKotlin",
    ":viewcompose-preview-core:compileKotlin",
    ":viewcompose-preview-gradle-plugin:compileKotlin",
    ":viewcompose-preview-runner:compileDebugKotlin",
    ":viewcompose-preview-worker-host:compileKotlin",
    ":viewcompose-renderer-android:compileDebugKotlin",
    ":viewcompose-ui-foundation:compileDebugKotlin",
    ":viewcompose-overlay-android:compileDebugKotlin",
    ":viewcompose-overlay-material3-android:compileDebugKotlin",
    ":viewcompose-overlay-oneui7-android:compileDebugKotlin",
    ":viewcompose-image-coil:compileDebugKotlin",
    ":viewcompose-image-glide:compileDebugKotlin",
    ":viewcompose-preview:compileDebugKotlin",
    ":viewcompose-animation:compileDebugKotlin",
    ":viewcompose-animation-core:compileKotlin",
    ":viewcompose-gesture:compileDebugKotlin",
    ":viewcompose-gesture-core:compileKotlin",
    ":viewcompose-graphics:compileDebugKotlin",
    ":viewcompose-graphics-core:compileKotlin",
    ":viewcompose-shadow-android:compileDebugKotlin",
    ":viewcompose-constraintlayout-androidx:compileDebugKotlin",
    ":samples:counter:assembleDebug",
    ":samples:counter:compileDebugAndroidTestKotlin",
    ":samples:tutorials:assembleDebug",
    ":samples:tutorials:compileDebugAndroidTestKotlin",
    ":app:compileDebugKotlin",
    ":viewcompose-runtime:test",
    ":viewcompose-navigation-core:test",
    ":viewcompose-navigation-android:testDebugUnitTest",
    ":viewcompose-ui-contract:test",
    ":viewcompose-host-android:testDebugUnitTest",
    ":viewcompose-material3:testDebugUnitTest",
    ":viewcompose-material3-android:testDebugUnitTest",
    ":viewcompose-oneui7:testDebugUnitTest",
    ":viewcompose-android:testDebugUnitTest",
    ":viewcompose-lifecycle-androidx:testDebugUnitTest",
    ":viewcompose-viewmodel-androidx:testDebugUnitTest",
    ":viewcompose-preview-core:test",
    ":viewcompose-preview-gradle-plugin:test",
    ":viewcompose-preview-runner:testDebugUnitTest",
    ":viewcompose-preview-worker-host:test",
    ":viewcompose-renderer-android:testDebugUnitTest",
    ":viewcompose-ui-foundation:testDebugUnitTest",
    ":viewcompose-overlay-android:testDebugUnitTest",
    ":viewcompose-overlay-material3-android:testDebugUnitTest",
    ":viewcompose-overlay-oneui7-android:testDebugUnitTest",
    ":viewcompose-image-coil:testDebugUnitTest",
    ":viewcompose-image-glide:testDebugUnitTest",
    ":viewcompose-preview:testDebugUnitTest",
    ":viewcompose-animation:testDebugUnitTest",
    ":viewcompose-animation-core:test",
    ":viewcompose-gesture:testDebugUnitTest",
    ":viewcompose-gesture-core:test",
    ":viewcompose-graphics:testDebugUnitTest",
    ":viewcompose-graphics-core:test",
    ":viewcompose-shadow-android:testDebugUnitTest",
    ":viewcompose-constraintlayout-androidx:testDebugUnitTest",
    ":app:testDebugUnitTest",
)

tasks.register("verifyModulePackageRoots") {
    group = "verification"
    description = "Verify source package declarations follow module package-root prefixes."
    doLast {
        val packageRegex = Regex("^\\s*package\\s+([A-Za-z0-9_.]+)", RegexOption.MULTILINE)
        val sourceSets = listOf("main", "test", "androidTest")
        val violations = mutableListOf<String>()

        modulePackageRoots.entries
            .groupBy(Map.Entry<String, String>::value)
            .filterValues { owners -> owners.size > 1 }
            .forEach { (packageRoot, owners) ->
                violations +=
                    "package root '$packageRoot' has multiple owners: " +
                        owners.map(Map.Entry<String, String>::key).sorted().joinToString()
            }

        modulePackageRoots.forEach { (module, packageRoot) ->
            if (forbiddenLegacyPackageRoots.any { legacy ->
                    packageRoot == legacy || packageRoot.startsWith("$legacy.")
                }
            ) {
                violations += "$module -> canonical package root '$packageRoot' uses a retired taxonomy"
            }
        }

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
                        if (
                            packageName != expectedPrefix &&
                            !packageName.startsWith("$expectedPrefix.")
                        ) {
                            violations += "${module}:${sourceSet}:${file.relativeTo(rootDir)} -> package '$packageName' not under '$expectedPrefix'"
                        }
                        val canonicalOwner =
                            modulePackageRoots.entries
                                .filter { (_, registeredRoot) ->
                                    packageName == registeredRoot ||
                                        packageName.startsWith("$registeredRoot.")
                                }
                                .maxByOrNull { (_, registeredRoot) -> registeredRoot.length }
                        if (canonicalOwner != null && canonicalOwner.key != module) {
                            violations +=
                                "${module}:${sourceSet}:${file.relativeTo(rootDir)} -> " +
                                    "package '$packageName' belongs to the more-specific root " +
                                    "'${canonicalOwner.value}' owned by '${canonicalOwner.key}'"
                        }
                        forbiddenLegacyPackageRoots.firstOrNull { legacy ->
                            packageName == legacy || packageName.startsWith("$legacy.")
                        }?.let { legacy ->
                            violations +=
                                "${module}:${sourceSet}:${file.relativeTo(rootDir)} -> " +
                                    "package '$packageName' uses retired root '$legacy'"
                        }
                    }
            }


            val serviceDirectory = srcDir.resolve("main/resources/META-INF/services")
            if (serviceDirectory.exists()) {
                serviceDirectory.listFiles().orEmpty()
                    .filter(File::isFile)
                    .forEach { serviceFile ->
                        val declarations = listOf(serviceFile.name) +
                            serviceFile.readLines().map(String::trim).filter(String::isNotEmpty)
                        declarations.forEach { declaration ->
                            forbiddenLegacyPackageRoots.firstOrNull { legacy ->
                                declaration == legacy || declaration.startsWith("$legacy.")
                            }?.let { legacy ->
                                violations +=
                                    "${serviceFile.relativeTo(rootDir)} -> service declaration " +
                                        "'$declaration' uses retired root '$legacy'"
                            }
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

        modulePackageRoots.forEach { (module, packageRoot) ->
            if (module in kotlinJvmModules) return@forEach
            val expectedNamespace = packageRoot
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
        val classifiedModules = runtimeModuleLayers.keys + toolingModules

        classifiedModules.sorted().forEach { module ->
            val memberships =
                listOf(
                    module in runtimeModuleLayers,
                    module in toolingModules,
                ).count { membership -> membership }
            if (memberships != 1) {
                violations += "$module -> module must belong to exactly one dependency-boundary group"
            }
        }

        (declaredModules - classifiedModules).sorted().forEach { module ->
            violations +=
                "$module -> unclassified module; register it in the five-layer runtime map or tooling"
        }
        (classifiedModules - declaredModules).sorted().forEach { module ->
            violations += "$module -> boundary classification has no matching module in settings.gradle.kts"
        }
        (declaredModules - modulePackageRoots.keys).sorted().forEach { module ->
            violations += "$module -> missing canonical package-root registration"
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

        runtimeModuleLayers.forEach { (module, layer) ->
            val allowedLayers = allowedDependencyLayers.getValue(layer)
            dependenciesByModule[module].orEmpty()
                .filter { dependency ->
                    val dependencyLayer = runtimeModuleLayers[dependency]
                    dependency in toolingModules ||
                        (dependencyLayer != null && dependencyLayer !in allowedLayers)
                }
                .sorted()
                .forEach { dependency ->
                    val dependencyLayer = runtimeModuleLayers[dependency] ?: "tooling"
                    violations +=
                        "$module ($layer) -> forbidden dependency '$dependency' ($dependencyLayer); " +
                            "the five-layer dependency direction must remain acyclic"
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

tasks.register("verifyDevelopmentToolingIsolation") {
    group = "verification"
    description =
        "Verify concrete application-process tooling stays downstream and inactive on runtime hot paths."
    doLast {
        val violations = mutableListOf<String>()
        val toolingTextExtensions = setOf("", "kt", "java", "xml", "json", "properties", "txt")
        val concreteToolingMarkers = listOf(
            "DeviceDslSource",
            "device-dsl-source",
            "ViewCompose-DeviceDslSource",
        )
        val prohibitedToolingHotPathMarkers = listOf(
            "ViewTreeObserver.OnScrollChangedListener",
            "ViewTreeObserver.OnGlobalLayoutListener",
            "ViewTreeObserver.OnDrawListener",
            "ViewTreeObserver.OnPreDrawListener",
            "View.OnLayoutChangeListener",
            "View.OnTouchListener",
            "Choreographer.FrameCallback",
            "RecyclerView.OnScrollListener",
            "addOnScrollChangedListener",
            "addOnScrollListener",
            "addOnGlobalLayoutListener",
            "addOnDrawListener",
            "addOnPreDrawListener",
            "addOnLayoutChangeListener",
            "setOnTouchListener",
            "postFrameCallback",
        )

        runtimeModuleLayers.keys.sorted().forEach { module ->
            val mainDirectory = rootDir.resolve(module).resolve("src/main")
            if (!mainDirectory.exists()) return@forEach
            mainDirectory.walkTopDown()
                .filter { file -> file.isFile && file.extension in toolingTextExtensions }
                .forEach { file ->
                    val content = file.readText()
                    concreteToolingMarkers
                        .filter(content::contains)
                        .forEach { marker ->
                            violations +=
                                "${file.relativeTo(rootDir).invariantSeparatorsPath} -> concrete " +
                                    "tooling marker '$marker' is forbidden in runtime production source"
                        }
                }
        }

        toolingModules.sorted().forEach { module ->
            val mainDirectory = rootDir.resolve(module).resolve("src/main")
            if (!mainDirectory.exists()) return@forEach
            mainDirectory.walkTopDown()
                .filter { file -> file.isFile && file.extension in toolingTextExtensions }
                .forEach { file ->
                    val content = file.readText()
                    prohibitedToolingHotPathMarkers
                        .filter(content::contains)
                        .forEach { marker ->
                            violations +=
                                "${file.relativeTo(rootDir).invariantSeparatorsPath} -> tooling hot-path " +
                                    "listener '$marker' requires an ADR-backed allowlist and benchmark"
                        }
                }
        }

        val appBuild = rootDir.resolve("app/build.gradle.kts").readText()
        val previewProjectPattern = Regex(
            """(?m)^\s*(implementation|api|releaseImplementation)\s*\(\s*project\(\s*\":viewcompose-preview\"""",
        )
        previewProjectPattern.findAll(appBuild).forEach { match ->
            violations +=
                "app/build.gradle.kts -> viewcompose-preview must be debug/test scoped, found " +
                    "${match.groupValues[1]}"
        }

        val releaseRuntime = project(":app").configurations.getByName("releaseRuntimeClasspath")
        val releaseComponents = releaseRuntime.incoming.resolutionResult.allComponents
            .map { component -> component.id.displayName }
        toolingModules
            .filterNot { module -> module == "viewcompose-benchmark" }
            .sorted()
            .forEach { toolingModule ->
                releaseComponents
                    .filter { component ->
                        component == "project :$toolingModule" ||
                            component.contains(":$toolingModule:")
                    }
                    .forEach { component ->
                        violations +=
                            "app releaseRuntimeClasspath -> forbidden tooling component '$component'"
                    }
            }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Development-tooling isolation verification failed:")
                    violations.distinct().sorted().forEach { appendLine("- $it") }
                },
            )
        }
    }
}

tasks.register("verifyDemoAutomationSelectors") {
    group = "verification"
    description =
        "Prevent new Demo automation from selecting app-owned UI through localized visible copy."
    doLast {
        val selectorPattern = Regex(
            """\b(?:By\.text|waitForText|waitForTextGone|scrollUntilText|clickVisibleText|""" +
                """tapVisibleText|tapText|scrollTabStripUntilText|assertDeviceTextVisible|""" +
                """clickDeviceText|waitForDeviceText|findObjectByText)\s*\(""",
        )
        // This exact baseline is temporary migration debt. Phase 4 removes Demo-owned entries;
        // any retained system/IME/third-party selector must move to a narrowly named allowlist.
        val legacySelectorBaseline = mapOf(
            "app/src/androidTest/java/com/viewcompose/DemoUiTestHelpers.kt" to 6,
            "viewcompose-benchmark/src/main/java/com/viewcompose/benchmark/ComplexLayoutPerformanceComparisonBenchmark.kt" to 3,
            "viewcompose-benchmark/src/main/java/com/viewcompose/benchmark/DemoBenchmarkScope.kt" to 18,
            "viewcompose-benchmark/src/main/java/com/viewcompose/benchmark/DemoInteractionBenchmark.kt" to 4,
            "viewcompose-benchmark/src/main/java/com/viewcompose/benchmark/ShadowPerformanceComparisonBenchmark.kt" to 3,
        )
        val sourceRoots = listOf(
            rootDir.resolve("app/src/androidTest"),
            rootDir.resolve("viewcompose-benchmark/src/main"),
        )
        val actualCounts = sourceRoots
            .flatMap { sourceRoot ->
                sourceRoot.walkTopDown()
                    .filter { file -> file.isFile && file.extension == "kt" }
                    .toList()
            }
            .associate { file ->
                file.relativeTo(rootDir).invariantSeparatorsPath to
                    selectorPattern.findAll(file.readText()).count()
            }
            .filterValues { count -> count > 0 }
        val violations = (legacySelectorBaseline.keys + actualCounts.keys)
            .distinct()
            .sorted()
            .mapNotNull { path ->
                val expected = legacySelectorBaseline[path] ?: 0
                val actual = actualCounts[path] ?: 0
                if (expected == actual) {
                    null
                } else {
                    "$path -> expected $expected legacy text-selector usages, found $actual"
                }
            }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Demo automation selector verification failed:")
                    violations.forEach { violation -> appendLine("- $violation") }
                    appendLine("Use scenario-owned Android resource IDs; only reduce the legacy baseline.")
                },
            )
        }
    }
}

tasks.register("verifyDemoLocalizationResources") {
    group = "verification"
    description =
        "Verify Demo default-English and Simplified-Chinese resource parity and format contracts."
    doLast {
        fun readResources(directory: File): Map<String, Map<String, String>> {
            val parserFactory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isIgnoringComments = true
            }
            return directory.listFiles()
                .orEmpty()
                .filter { file -> file.isFile && file.extension == "xml" }
                .sortedBy { file -> file.name }
                .flatMap { file ->
                    val document = parserFactory.newDocumentBuilder().parse(file)
                    val root = document.documentElement
                    (0 until root.childNodes.length).mapNotNull { index ->
                        val element = root.childNodes.item(index) as? org.w3c.dom.Element
                            ?: return@mapNotNull null
                        val kind = element.tagName
                        if (kind !in setOf("string", "plurals", "string-array")) {
                            return@mapNotNull null
                        }
                        val name = element.getAttribute("name")
                        require(name.isNotBlank()) {
                            "Missing resource name in ${file.relativeTo(rootDir)}"
                        }
                        val values = when (kind) {
                            "string" -> mapOf("value" to element.textContent.trim())
                            else -> {
                                var ordinal = 0
                                (0 until element.childNodes.length).mapNotNull itemLoop@ { childIndex ->
                                    val item = element.childNodes.item(childIndex) as? org.w3c.dom.Element
                                        ?: return@itemLoop null
                                    if (item.tagName != "item") return@itemLoop null
                                    val selector = if (kind == "plurals") {
                                        item.getAttribute("quantity")
                                    } else {
                                        (ordinal++).toString()
                                    }
                                    selector to item.textContent.trim()
                                }.toMap()
                            }
                        }
                        "$kind:$name" to values
                    }
                }
                .toMap()
        }

        val formatPattern = Regex(
            """%(?:(\d+)\$)?[-#+ 0,(<]*\d*(?:\.\d+)?([a-zA-Z%])""",
        )
        fun formatSignature(value: String): List<String> {
            var implicitIndex = 1
            return formatPattern.findAll(value).mapNotNull { match ->
                val conversion = match.groupValues[2]
                if (conversion == "%") return@mapNotNull null
                val explicitIndex = match.groupValues[1]
                val argumentIndex = explicitIndex.ifBlank { (implicitIndex++).toString() }
                "$argumentIndex:${conversion.lowercase()}"
            }.toList()
        }

        val defaultResources = readResources(rootDir.resolve("app/src/main/res/values"))
        val chineseResources = readResources(rootDir.resolve("app/src/main/res/values-zh-rCN"))
        val violations = mutableListOf<String>()
        (defaultResources.keys - chineseResources.keys).sorted().forEach { key ->
            violations += "$key is missing from values-zh-rCN"
        }
        (chineseResources.keys - defaultResources.keys).sorted().forEach { key ->
            violations += "$key has no canonical default-English resource"
        }
        (defaultResources.keys intersect chineseResources.keys).sorted().forEach { key ->
            val canonical = defaultResources.getValue(key)
            val localized = chineseResources.getValue(key)
            if (canonical.keys != localized.keys) {
                violations +=
                    "$key selectors differ: default=${canonical.keys.sorted()}, " +
                        "zh-rCN=${localized.keys.sorted()}"
                return@forEach
            }
            canonical.keys.sorted().forEach { selector ->
                val canonicalFormat = formatSignature(canonical.getValue(selector))
                val localizedFormat = formatSignature(localized.getValue(selector))
                if (canonicalFormat != localizedFormat) {
                    violations +=
                        "$key[$selector] format differs: default=$canonicalFormat, " +
                            "zh-rCN=$localizedFormat"
                }
            }
        }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Demo localization resource verification failed:")
                    violations.forEach { violation -> appendLine("- $violation") }
                },
            )
        }
    }
}

tasks.register("verifyDemoLocalizedVisibleCopy") {
    group = "verification"
    description =
        "Prevent hard-coded visible copy from returning to Demo source domains already migrated to resources."
    doLast {
        val migratedSources = listOf(
            rootDir.resolve("app/src/main/java/com/viewcompose/demo/automation"),
            rootDir.resolve("app/src/main/java/com/viewcompose/demo/contract"),
            rootDir.resolve("app/src/main/java/com/viewcompose/demo/core"),
            rootDir.resolve("app/src/main/java/com/viewcompose/demo/registry"),
            rootDir.resolve(
                "app/src/main/java/com/viewcompose/demo/pages/state/DemoStatePage.kt",
            ),
            rootDir.resolve(
                "app/src/main/java/com/viewcompose/demo/pages/diagnostics/DemoDiagnosticsPage.kt",
            ),
            rootDir.resolve(
                "app/src/main/java/com/viewcompose/demo/pages/diagnostics/DemoDiagnosticsThemeSections.kt",
            ),
            rootDir.resolve(
                "app/src/main/java/com/viewcompose/demo/pages/collections/DemoCollectionsPage.kt",
            ),
            rootDir.resolve(
                "app/src/main/java/com/viewcompose/demo/pages/layouts/DemoLayoutsPage.kt",
            ),
            rootDir.resolve(
                "app/src/main/java/com/viewcompose/demo/pages/input/DemoInputPage.kt",
            ),
            rootDir.resolve(
                "app/src/main/java/com/viewcompose/demo/pages/gestures/DemoGesturesPage.kt",
            ),
            rootDir.resolve(
                "app/src/main/java/com/viewcompose/demo/pages/graphics/DemoGraphicsPage.kt",
            ),
            rootDir.resolve(
                "app/src/main/java/com/viewcompose/demo/pages/graphics/DemoGraphicsShadowSections.kt",
            ),
            rootDir.resolve(
                "app/src/main/java/com/viewcompose/demo/pages/animation/DemoAnimationPage.kt",
            ),
            rootDir.resolve("app/src/main/java/com/viewcompose/demo/pages/modifiers"),
            rootDir.resolve("app/src/main/java/com/viewcompose/demo/pages/interop"),
            rootDir.resolve("app/src/main/java/com/viewcompose/demo/pages/feedback"),
            rootDir.resolve(
                "app/src/main/java/com/viewcompose/demo/pages/navigation/DemoSystemNavigationPage.kt",
            ),
            rootDir.resolve(
                "app/src/main/java/com/viewcompose/demo/pages/navigation/DemoSystemNavigationDestination.kt",
            ),
            rootDir.resolve(
                "app/src/main/java/com/viewcompose/activity/demo/pages/interaction/SystemNavigationActivity.kt",
            ),
            rootDir.resolve(
                "app/src/main/java/com/viewcompose/demo/pages/settings/DemoMaterial3DefaultThemePage.kt",
            ),
            rootDir.resolve(
                "app/src/main/java/com/viewcompose/activity/demo/pages/quality/Material3DefaultThemeActivity.kt",
            ),
            rootDir.resolve(
                "app/src/main/java/com/viewcompose/demo/pages/settings/DemoDesignSystemVerificationPage.kt",
            ),
            rootDir.resolve(
                "app/src/main/java/com/viewcompose/activity/demo/pages/quality/DemoDesignSystemVerificationActivity.kt",
            ),
            rootDir.resolve(
                "app/src/main/java/com/viewcompose/demo/pages/settings/DemoOneUi7VerificationPage.kt",
            ),
            rootDir.resolve(
                "app/src/main/java/com/viewcompose/activity/demo/pages/quality/OneUi7VerificationActivity.kt",
            ),
            rootDir.resolve("app/src/main/java/com/viewcompose/demo/pages/actions"),
            rootDir.resolve(
                "app/src/main/java/com/viewcompose/activity/demo/pages/core/ActionsActivity.kt",
            ),
            rootDir.resolve(
                "app/src/main/java/com/viewcompose/demo/pages/navigation/DemoNavigationPage.kt",
            ),
            rootDir.resolve(
                "app/src/main/java/com/viewcompose/activity/demo/pages/interaction/NavigationActivity.kt",
            ),
            rootDir.resolve("app/src/main/java/com/viewcompose/demo/pages/components"),
            rootDir.resolve(
                "app/src/main/java/com/viewcompose/activity/demo/pages/advanced/ComponentShowcaseActivity.kt",
            ),
            rootDir.resolve("app/src/main/java/com/viewcompose/demo/pages/foundations"),
            rootDir.resolve(
                "app/src/main/java/com/viewcompose/activity/demo/pages/core/FoundationsActivity.kt",
            ),
            rootDir.resolve(
                "app/src/main/java/com/viewcompose/demo/designsystem/DemoContrastDesignSystem.kt",
            ),
        )
        val visibleLiteral = Regex(
            """(?:\b(?:text|title|subtitle|label|supportingText|placeholder|""" +
                """contentDescription|what|goal)\s*=\s*|""" +
                """\b(?:Text|Button|Chip|SearchBar)\s*\(\s*)\"""",
        )
        val violations = migratedSources
            .flatMap { source ->
                if (source.isDirectory) {
                    source.walkTopDown()
                        .filter { file -> file.isFile && file.extension == "kt" }
                        .toList()
                } else {
                    listOf(source)
                }
            }
            .flatMap { file ->
                if (!file.exists()) return@flatMap emptyList()
                val source = file.readText()
                visibleLiteral.findAll(source).map { match ->
                    val lineNumber = source.take(match.range.first).count { character ->
                        character == '\n'
                    } + 1
                    val line = source.lineSequence().drop(lineNumber - 1).first().trim()
                    "${file.relativeTo(rootDir)}:$lineNumber -> $line"
                }
                    .toList()
            }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Demo localized visible-copy verification failed:")
                    violations.sorted().forEach { violation -> appendLine("- $violation") }
                    appendLine("Resolve visible copy through Android resources in migrated domains.")
                },
            )
        }
    }
}

tasks.register("verifyDesignSystemIsolation") {
    group = "verification"
    description =
        "Verify neutral layers and named design-system artifacts remain mutually isolated."
    doLast {
        val violations = mutableListOf<String>()
        val materialFreeModules =
            listOf(
                "viewcompose-ui-foundation",
                "viewcompose-renderer-android",
                "viewcompose-host-android",
                "viewcompose-android",
            )
        val productionConfigurations = setOf("api", "implementation", "compileOnly")

        materialFreeModules.forEach { module ->
            val moduleProject = project(":$module")
            productionConfigurations.forEach { configurationName ->
                moduleProject.configurations.findByName(configurationName)
                    ?.dependencies
                    ?.filter { dependency -> dependency.group == "com.google.android.material" }
                    ?.forEach { dependency ->
                        violations +=
                            "$module:$configurationName -> forbidden Material dependency " +
                                "'${dependency.group}:${dependency.name}'"
                    }
                moduleProject.configurations.findByName(configurationName)
                    ?.dependencies
                    ?.filter { dependency -> dependency.name == "viewcompose-material3" }
                    ?.forEach { dependency ->
                        violations +=
                            "$module:$configurationName -> forbidden Material project dependency " +
                                "'${dependency.name}'"
                    }
            }

            val mainDirectory = rootDir.resolve(module).resolve("src/main")
            if (mainDirectory.exists()) {
                mainDirectory.walkTopDown()
                    .filter { file ->
                        file.isFile && (file.extension == "kt" || file.extension == "java")
                    }
                    .forEach { file ->
                        file.useLines { lines ->
                            lines.forEachIndexed { index, line ->
                                val trimmed = line.trimStart()
                                if (trimmed.startsWith("import com.google.android.material.")) {
                                    violations +=
                                        "${file.relativeTo(rootDir)}:${index + 1} -> " +
                                            "forbidden Material import '$trimmed'"
                                }
                                if (
                                    module == "viewcompose-android" &&
                                    "com.viewcompose.material3" in line
                                ) {
                                    violations +=
                                        "${file.relativeTo(rootDir)}:${index + 1} -> " +
                                            "neutral Android aggregate cannot reference Material '$trimmed'"
                                }
                                if (
                                    module == "viewcompose-ui-foundation" &&
                                    trimmed.startsWith("import androidx.")
                                ) {
                                    violations +=
                                        "${file.relativeTo(rootDir)}:${index + 1} -> " +
                                            "UI Foundation cannot import AndroidX '$trimmed'"
                                }
                            }
                        }
                    }
            }
        }

        val namedSystemProjects = setOf("viewcompose-material3", "viewcompose-oneui7")
        val namedSystemPackages = setOf("com.viewcompose.material3", "com.viewcompose.oneui7")
        materialFreeModules.forEach { module ->
            val moduleProject = project(":$module")
            productionConfigurations.forEach { configurationName ->
                moduleProject.configurations.findByName(configurationName)
                    ?.dependencies
                    ?.filter { dependency -> dependency.name in namedSystemProjects }
                    ?.forEach { dependency ->
                        violations +=
                            "$module:$configurationName -> neutral module cannot depend on " +
                                "named design system '${dependency.name}'"
                    }
            }
            val mainDirectory = rootDir.resolve(module).resolve("src/main")
            if (mainDirectory.exists()) {
                mainDirectory.walkTopDown()
                    .filter { file ->
                        file.isFile && (file.extension == "kt" || file.extension == "java")
                    }
                    .forEach { file ->
                        file.useLines { lines ->
                            lines.forEachIndexed { index, line ->
                                val trimmed = line.trimStart()
                                if (namedSystemPackages.any { prefix ->
                                        trimmed.startsWith("import $prefix.")
                                    }
                                ) {
                                    violations +=
                                        "${file.relativeTo(rootDir)}:${index + 1} -> " +
                                            "neutral module cannot import named design system '$trimmed'"
                                }
                            }
                        }
                    }
            }
        }

        mapOf(
            "viewcompose-material3" to setOf("viewcompose-oneui7"),
            "viewcompose-oneui7" to setOf("viewcompose-material3"),
        ).forEach { (module, forbiddenProjects) ->
            val moduleProject = project(":$module")
            productionConfigurations.forEach { configurationName ->
                moduleProject.configurations.findByName(configurationName)
                    ?.dependencies
                    ?.filter { dependency -> dependency.name in forbiddenProjects }
                    ?.forEach { dependency ->
                        violations +=
                            "$module:$configurationName -> named systems cannot depend on " +
                                "each other ('${dependency.name}')"
                    }
            }
        }

        val oneUiModules = listOf("viewcompose-oneui7", "viewcompose-overlay-oneui7-android")
        oneUiModules.forEach { module ->
            productionConfigurations.forEach { configurationName ->
                project(":$module").configurations.findByName(configurationName)
                    ?.dependencies
                    ?.filter { dependency -> dependency.group == "com.google.android.material" }
                    ?.forEach { dependency ->
                        violations +=
                            "$module:$configurationName -> forbidden Material dependency " +
                                "'${dependency.group}:${dependency.name}'"
                    }
            }
            val oneUiMainDirectory = rootDir.resolve("$module/src/main")
            if (!oneUiMainDirectory.exists()) {
                return@forEach
            }
            oneUiMainDirectory.walkTopDown()
                .filter { file -> file.isFile && (file.extension == "kt" || file.extension == "java") }
                .forEach { file ->
                    file.useLines { lines ->
                        lines.forEachIndexed { index, line ->
                            val trimmed = line.trimStart()
                            if (
                                trimmed.startsWith("import com.viewcompose.material3.") ||
                                trimmed.startsWith("import com.google.android.material.")
                            ) {
                                violations +=
                                    "${file.relativeTo(rootDir)}:${index + 1} -> " +
                                        "One UI cannot import Material policy '$trimmed'"
                            }
                        }
                    }
                }
        }

        val uiFoundation = project(":viewcompose-ui-foundation")
        productionConfigurations.forEach { configurationName ->
            uiFoundation.configurations.findByName(configurationName)
                ?.dependencies
                ?.filter { dependency -> dependency.group?.startsWith("androidx.") == true }
                ?.forEach { dependency ->
                    violations +=
                        "viewcompose-ui-foundation:$configurationName -> forbidden AndroidX " +
                            "dependency '${dependency.group}:${dependency.name}'"
                }
        }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Design-system isolation verification failed:")
                    violations.sorted().forEach { appendLine("- $it") }
                },
            )
        }
    }
}

tasks.register("verifyUiFoundationPlatformBoundary") {
    group = "verification"
    description =
        "Verify UI Foundation delegates Android execution, host adaptation, logging, and tracing."
    doLast {
        val forbiddenImports = setOf(
            "android.content.Context",
            "android.os.LocaleList",
            "android.os.Trace",
            "android.util.Log",
            "android.view.View",
            "android.view.ViewGroup",
        )
        val violations = mutableListOf<String>()
        val mainDirectory = rootDir.resolve("viewcompose-ui-foundation/src/main")

        mainDirectory.walkTopDown()
            .filter { file -> file.isFile && (file.extension == "kt" || file.extension == "java") }
            .forEach { file ->
                file.useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        val importedType = line.trim()
                            .takeIf { it.startsWith("import ") }
                            ?.removePrefix("import ")
                        if (importedType in forbiddenImports) {
                            violations +=
                                "${file.relativeTo(rootDir)}:${index + 1} -> " +
                                    "Android execution import '$importedType' belongs in Android Engine"
                        }
                    }
                }
            }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("UI Foundation platform-boundary verification failed:")
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

val migrationPairedSamplesByPage =
    mapOf(
        "compose-state-recomposition-and-restoration.md" to
            listOf(
                "samples/compose-migration/src/main/java/com/viewcompose/samples/migration/state/ComposeStateSample.kt" to
                    "compose-state",
                "samples/compose-migration/src/main/java/com/viewcompose/samples/migration/state/ViewComposeStateSample.kt" to
                    "viewcompose-state",
            ),
        "compose-layout-modifier-and-environment.md" to
            listOf(
                "samples/compose-migration/src/main/java/com/viewcompose/samples/migration/layout/ComposeLayoutSample.kt" to
                    "compose-layout",
                "samples/compose-migration/src/main/java/com/viewcompose/samples/migration/layout/ViewComposeLayoutSample.kt" to
                    "viewcompose-layout",
            ),
        "compose-host-lifecycle-and-android-interop.md" to
            listOf(
                "samples/compose-migration/src/main/java/com/viewcompose/samples/migration/host/ComposeHostSample.kt" to
                    "compose-host",
                "samples/compose-migration/src/main/java/com/viewcompose/samples/migration/host/ViewComposeHostSample.kt" to
                    "viewcompose-host",
            ),
        "compose-navigation.md" to
            listOf(
                "samples/compose-migration/src/main/java/com/viewcompose/samples/migration/navigation/ComposeNavigationSample.kt" to
                    "compose-navigation",
                "samples/compose-migration/src/main/java/com/viewcompose/samples/migration/navigation/ViewComposeNavigationSample.kt" to
                    "viewcompose-navigation-android",
            ),
    )

tasks.register("verifyMigrationPairedSamples") {
    group = "verification"
    description = "Compile migration pairs and verify canonical and translated snippets match them."
    dependsOn(":samples:compose-migration:compileDebugKotlin")

    val documentationRoots =
        listOf(
            rootDir.resolve("docs/migration"),
            rootDir.resolve(
                "website/i18n/zh-CN/docusaurus-plugin-content-docs/current/migration",
            ),
        )
    inputs.files(
        migrationPairedSamplesByPage.values
            .flatten()
            .map { (source, _) -> rootDir.resolve(source) },
    )
    inputs.files(
        documentationRoots.flatMap { docsRoot ->
            migrationPairedSamplesByPage.keys.map { page -> docsRoot.resolve(page) }
        },
    )

    doLast {
        val snippetRegex =
            Regex(
                """\{/\* paired-sample source="([^"]+)" region="([^"]+)" \*/\}\s*```kotlin\s*([\s\S]*?)\s*```\s*\{/\* paired-sample-end \*/\}""",
            )
        val violations = mutableListOf<String>()

        fun compiledRegion(sourcePath: String, region: String): String? {
            val sourceFile = rootDir.resolve(sourcePath)
            if (!sourceFile.isFile) {
                violations += "$sourcePath -> source file does not exist"
                return null
            }
            val source = sourceFile.readText().replace("\r\n", "\n")
            val startMarker = "// DOCS_REGION_START($region)"
            val endMarker = "// DOCS_REGION_END($region)"
            if (source.windowed(startMarker.length).count { it == startMarker } != 1) {
                violations += "$sourcePath -> expected exactly one '$startMarker'"
                return null
            }
            if (source.windowed(endMarker.length).count { it == endMarker } != 1) {
                violations += "$sourcePath -> expected exactly one '$endMarker'"
                return null
            }
            val start = source.indexOf(startMarker) + startMarker.length
            val end = source.indexOf(endMarker, start)
            if (end < start) {
                violations += "$sourcePath -> '$endMarker' must follow '$startMarker'"
                return null
            }
            return source.substring(start, end).trim()
        }

        documentationRoots.forEach { docsRoot ->
            migrationPairedSamplesByPage.forEach pageLoop@{ (pageName, expectedPairs) ->
                val page = docsRoot.resolve(pageName)
                if (!page.isFile) {
                    violations += "${page.relativeTo(rootDir)} -> document does not exist"
                    return@pageLoop
                }
                val matches = snippetRegex.findAll(page.readText().replace("\r\n", "\n")).toList()
                val actualPairs = matches.map { it.groupValues[1] to it.groupValues[2] }
                if (actualPairs != expectedPairs) {
                    violations +=
                        "${page.relativeTo(rootDir)} -> paired samples $actualPairs do not match $expectedPairs"
                    return@pageLoop
                }
                matches.forEach snippetLoop@{ match ->
                    val sourcePath = match.groupValues[1]
                    val region = match.groupValues[2]
                    val expectedSnippet = compiledRegion(sourcePath, region) ?: return@snippetLoop
                    val documentedSnippet = match.groupValues[3].trim()
                    if (documentedSnippet != expectedSnippet) {
                        violations +=
                            "${page.relativeTo(rootDir)} -> snippet '$region' differs from $sourcePath"
                    }
                }
            }
        }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Migration paired-sample verification failed:")
                    violations.distinct().sorted().forEach { appendLine("- $it") }
                },
            )
        }
    }
}

data class TutorialSample(
    val source: String,
    val region: String,
    val requiredArtifacts: List<String> = emptyList(),
)

val tutorialBaseArtifacts =
    listOf("viewcompose-material3-android")

val tutorialPublishingPropertiesFile =
    rootDir.resolve("gradle/viewcompose-publishing.properties")
val tutorialPublishingProperties =
    java.util.Properties().apply {
        tutorialPublishingPropertiesFile.inputStream().use(::load)
    }
val tutorialPublishedVersion = { artifact: String ->
    tutorialPublishingProperties.getProperty("module.$artifact.version")
        ?: error("Missing published version for tutorial artifact '$artifact'.")
}
val tutorialPublishedVersions =
    listOf(
        "viewcompose-material3-android",
        "viewcompose-navigation-android",
        "viewcompose-overlay-material3-android",
        "viewcompose-animation",
        "viewcompose-gesture",
    ).associateWith(tutorialPublishedVersion)

val tutorialSamplesByPage =
    mapOf(
        "state-and-events.md" to
            TutorialSample(
                "samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/StateTutorialActivity.kt",
                "state",
            ),
        "layouts-and-modifiers.md" to
            TutorialSample(
                "samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/LayoutsTutorialActivity.kt",
                "layouts",
            ),
        "text-input.md" to
            TutorialSample(
                "samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TextInputTutorialActivity.kt",
                "text-input",
            ),
        "lazy-lists.md" to
            TutorialSample(
                "samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/LazyListsTutorialActivity.kt",
                "lazy-lists",
            ),
        "theming.md" to
            TutorialSample(
                "samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/ThemingTutorialActivity.kt",
                "theming",
            ),
        "navigation.md" to
            TutorialSample(
                "samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/NavigationTutorialActivity.kt",
                "navigation",
                listOf("viewcompose-navigation-android"),
            ),
        "overlays.md" to
            TutorialSample(
                "samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/OverlaysTutorialActivity.kt",
                "overlays",
                listOf("viewcompose-overlay-material3-android"),
            ),
        "android-view.md" to
            TutorialSample(
                "samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/AndroidViewTutorialActivity.kt",
                "android-view",
            ),
        "animation.md" to
            TutorialSample(
                "samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/AnimationTutorialActivity.kt",
                "animation",
                listOf("viewcompose-animation"),
            ),
        "gestures.md" to
            TutorialSample(
                "samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/GesturesTutorialActivity.kt",
                "gestures",
                listOf("viewcompose-gesture"),
            ),
        "lazy-list-performance.md" to
            TutorialSample(
                "samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/LazyListPerformanceTutorialActivity.kt",
                "lazy-list-performance",
            ),
        "render-diagnostics.md" to
            TutorialSample(
                "samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/RenderDiagnosticsTutorialActivity.kt",
                "render-diagnostics",
            ),
    )

tasks.register("verifyTutorialSamples") {
    group = "verification"
    description =
        "Compile independent Maven-backed tutorial sources and verify snippets and dependencies."
    dependsOn(":samples:tutorials:compileDebugKotlin")

    val documentationRoots =
        listOf(
            rootDir.resolve("docs/tutorials"),
            rootDir.resolve(
                "website/i18n/zh-CN/docusaurus-plugin-content-docs/current/tutorials",
            ),
        )
    inputs.files(
        tutorialSamplesByPage.values.map { sample -> rootDir.resolve(sample.source) },
        rootDir.resolve("samples/tutorials/build.gradle.kts"),
        rootDir.resolve("samples/counter/build.gradle.kts"),
        tutorialPublishingPropertiesFile,
    )
    inputs.files(
        documentationRoots.flatMap { docsRoot ->
            tutorialSamplesByPage.keys.map { page -> docsRoot.resolve(page) }
        },
    )

    doLast {
        val snippetRegex =
            Regex(
                """\{/\* tutorial-sample source="([^"]+)" region="([^"]+)" \*/\}\s*```kotlin\s*([\s\S]*?)\s*```\s*\{/\* tutorial-sample-end \*/\}""",
            )
        val violations = mutableListOf<String>()
        val dependencyBlockRegex =
            Regex(
                """```kotlin title="build\.gradle\.kts"\s*([\s\S]*?)```""",
            )
        val coordinateRegex =
            Regex("""implementation\("com\.viewcompose:([^:"]+):([^"]+)"\)""")

        fun compiledRegion(sourcePath: String, region: String): String? {
            val sourceFile = rootDir.resolve(sourcePath)
            if (!sourceFile.isFile) {
                violations += "$sourcePath -> source file does not exist"
                return null
            }
            val source = sourceFile.readText().replace("\r\n", "\n")
            val startMarker = "// DOCS_REGION_START($region)"
            val endMarker = "// DOCS_REGION_END($region)"
            if (source.windowed(startMarker.length).count { it == startMarker } != 1) {
                violations += "$sourcePath -> expected exactly one '$startMarker'"
                return null
            }
            if (source.windowed(endMarker.length).count { it == endMarker } != 1) {
                violations += "$sourcePath -> expected exactly one '$endMarker'"
                return null
            }
            val start = source.indexOf(startMarker) + startMarker.length
            val end = source.indexOf(endMarker, start)
            if (end < start) {
                violations += "$sourcePath -> '$endMarker' must follow '$startMarker'"
                return null
            }
            return source.substring(start, end).trim()
        }

        documentationRoots.forEach { docsRoot ->
            tutorialSamplesByPage.forEach pageLoop@{ (pageName, sample) ->
                val page = docsRoot.resolve(pageName)
                if (!page.isFile) {
                    violations += "${page.relativeTo(rootDir)} -> document does not exist"
                    return@pageLoop
                }
                val pageText = page.readText().replace("\r\n", "\n")
                val matches = snippetRegex.findAll(pageText).toList()
                val actualSamples = matches.map { it.groupValues[1] to it.groupValues[2] }
                val expectedSamples = listOf(sample.source to sample.region)
                if (actualSamples != expectedSamples) {
                    violations +=
                        "${page.relativeTo(rootDir)} -> tutorial samples $actualSamples do not match $expectedSamples"
                    return@pageLoop
                }
                matches.forEach snippetLoop@{ match ->
                    val sourcePath = match.groupValues[1]
                    val region = match.groupValues[2]
                    val expectedSnippet = compiledRegion(sourcePath, region) ?: return@snippetLoop
                    val documentedSnippet = match.groupValues[3].trim()
                    if (documentedSnippet != expectedSnippet) {
                        violations +=
                            "${page.relativeTo(rootDir)} -> snippet '$region' differs from $sourcePath"
                    }
                }

                val dependencyBlock = dependencyBlockRegex.find(pageText)
                if (dependencyBlock == null || dependencyBlock.range.first > 1_500) {
                    violations +=
                        "${page.relativeTo(rootDir)} -> complete Maven dependencies must appear at the top"
                } else {
                    val block = dependencyBlock.groupValues[1]
                    val actualArtifacts =
                        coordinateRegex.findAll(block)
                            .map { match -> match.groupValues[1] to match.groupValues[2] }
                            .toList()
                    val expectedArtifacts =
                        (tutorialBaseArtifacts + sample.requiredArtifacts).map { artifact ->
                            artifact to tutorialPublishedVersions.getValue(artifact)
                        }
                    if (actualArtifacts != expectedArtifacts) {
                        violations +=
                            "${page.relativeTo(rootDir)} -> Maven artifacts $actualArtifacts do not match $expectedArtifacts"
                    }
                    if ("repositories { mavenCentral() }" !in block) {
                        violations +=
                            "${page.relativeTo(rootDir)} -> dependency block must declare Maven Central"
                    }
                    if ("project(" in block) {
                        violations +=
                            "${page.relativeTo(rootDir)} -> tutorial dependencies must not use project()"
                    }
                }
            }

            val gettingStartedPage = docsRoot.resolve("getting-started.md")
            if (!gettingStartedPage.isFile) {
                violations +=
                    "${gettingStartedPage.relativeTo(rootDir)} -> document does not exist"
            } else {
                val pageText = gettingStartedPage.readText().replace("\r\n", "\n")
                val dependencyBlock = dependencyBlockRegex.find(pageText)
                val leadingContent = pageText.take(5_000)
                if (dependencyBlock == null || dependencyBlock.range.first > 1_500) {
                    violations +=
                        "${gettingStartedPage.relativeTo(rootDir)} -> complete Maven dependencies must appear at the top"
                } else {
                    val actualArtifacts =
                        coordinateRegex.findAll(dependencyBlock.groupValues[1])
                            .map { match -> match.groupValues[1] to match.groupValues[2] }
                            .toList()
                    val expectedArtifacts =
                        tutorialBaseArtifacts.map { artifact ->
                            artifact to tutorialPublishedVersions.getValue(artifact)
                        }
                    if (actualArtifacts != expectedArtifacts) {
                        violations +=
                            "${gettingStartedPage.relativeTo(rootDir)} -> Maven artifacts $actualArtifacts do not match $expectedArtifacts"
                    }
                    if ("repositories { mavenCentral() }" !in dependencyBlock.groupValues[1]) {
                        violations +=
                            "${gettingStartedPage.relativeTo(rootDir)} -> dependency block must declare Maven Central"
                    }
                }
                listOf(
                    "id(\"com.viewcompose.preview\") version \"${tutorialPublishedVersion("viewcompose-preview-gradle-plugin")}\"",
                    "com.viewcompose:viewcompose-preview-core:${tutorialPublishedVersion("viewcompose-preview-core")}",
                    "com.viewcompose:viewcompose-preview-worker-host:${tutorialPublishedVersion("viewcompose-preview-worker-host")}",
                    "com.viewcompose:viewcompose-preview-runner:${tutorialPublishedVersion("viewcompose-preview-runner")}",
                ).forEach { requiredPreviewDependency ->
                    if (requiredPreviewDependency !in leadingContent) {
                        violations +=
                            "${gettingStartedPage.relativeTo(rootDir)} -> missing optional preview dependency '$requiredPreviewDependency' at the top"
                    }
                }
            }
        }

        listOf(
            rootDir.resolve("samples/tutorials/build.gradle.kts"),
            rootDir.resolve("samples/counter/build.gradle.kts"),
        ).forEach { sampleBuild ->
            if ("project(" in sampleBuild.readText()) {
                violations +=
                    "${sampleBuild.relativeTo(rootDir)} -> public tutorial samples must resolve ViewCompose from Maven"
            }
        }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Tutorial sample verification failed:")
                    violations.distinct().sorted().forEach { appendLine("- $it") }
                },
            )
        }
    }
}

val verifyDocumentLanguages by tasks.registering(Exec::class) {
    group = "verification"
    description = "Verifies canonical-English and Simplified-Chinese documentation language."
    workingDir(rootDir.resolve("website"))
    commandLine("npm", "run", "verify:languages")
    inputs.files(
        fileTree(rootDir.resolve("docs")) {
            include("**/*.md", "**/*.mdx")
            exclude("archive/**")
        },
        fileTree(rootDir.resolve("website/i18n/zh-CN/docusaurus-plugin-content-docs/current")) {
            include("**/*.md", "**/*.mdx")
        },
        rootDir.resolve("website/scripts/verify-document-languages.mjs"),
        rootDir.resolve("website/scripts/__tests__/verify-document-languages.test.mjs"),
        rootDir.resolve("website/i18n/translation-policy.json"),
    )
}

tasks.register("verifyDocumentationStructure") {
    group = "verification"
    description =
        "Verifies documentation placement, link-graph coverage, module catalog, and relative links."
    dependsOn(verifyDocumentLanguages)

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
    dependsOn("verifyDevelopmentToolingIsolation")
    dependsOn("verifyDemoAutomationSelectors")
    dependsOn("verifyDemoLocalizationResources")
    dependsOn("verifyDemoLocalizedVisibleCopy")
    dependsOn("verifyDesignSystemIsolation")
    dependsOn("verifyUiFoundationPlatformBoundary")
    dependsOn("verifyDocumentationStructure")
    dependsOn("verifyMigrationPairedSamples")
    dependsOn("verifyTutorialSamples")
    dependsOn("verifyViewComposePublishingConfiguration")
    dependsOn("verifyViewComposeReleaseIntent")
    dependsOn("publishViewComposeToLocalRepository")
    dependsOn(gradle.includedBuild("viewcompose-publishing-build").task(":test"))
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

// The tutorial applications deliberately use Maven coordinates instead of project dependencies.
// Order their resolution after the current checkout has produced its local Maven repository so a
// hard-cut artifact rename is verifiable before the first Central publication.
val publishForMavenSamples = tasks.named("publishViewComposeToLocalRepository")
listOf(":samples:counter", ":samples:tutorials").forEach { sampleProjectPath ->
    project(sampleProjectPath).tasks.configureEach {
        mustRunAfter(publishForMavenSamples)
    }
}

val connectedDebugTestProjects = setOf(
    ":app",
    ":samples:counter",
    ":samples:tutorials",
)

val verifyConnectedAndroidDeviceReady = tasks.register("verifyConnectedAndroidDeviceReady") {
    group = "verification"
    description = "Fail early unless one selected Android device is online, booted, awake, and unlocked."
    outputs.upToDateWhen { false }

    doLast {
        fun adbExecutable(): String {
            val executable = if (System.getProperty("os.name").startsWith("Windows")) {
                "adb.exe"
            } else {
                "adb"
            }
            val sdkRoot = System.getenv("ANDROID_SDK_ROOT")
                ?.takeIf(String::isNotBlank)
                ?: System.getenv("ANDROID_HOME")?.takeIf(String::isNotBlank)
            return sdkRoot
                ?.let { File(it).resolve("platform-tools/$executable") }
                ?.takeIf(File::isFile)
                ?.absolutePath
                ?: executable
        }

        fun runAdb(adb: String, vararg arguments: String): String {
            val command = listOf(adb) + arguments
            val process = try {
                ProcessBuilder(command)
                    .directory(rootDir)
                    .redirectErrorStream(true)
                    .start()
            } catch (error: Exception) {
                throw GradleException(
                    "Android connected-test preflight could not start adb. Install Android SDK " +
                        "platform-tools or set ANDROID_SDK_ROOT.",
                    error,
                )
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw GradleException(
                    "Android connected-test preflight failed: ${command.joinToString(" ")} " +
                        "exited with $exitCode.\n$output",
                )
            }
            return output
        }

        val adb = adbExecutable()
        val deviceRows = runAdb(adb, "devices")
            .lineSequence()
            .drop(1)
            .map(String::trim)
            .filter(String::isNotBlank)
            .map { row -> row.split(Regex("\\s+"), limit = 3) }
            .filter { fields -> fields.size >= 2 }
            .associate { fields -> fields[0] to fields[1] }
        val requestedSerial = System.getenv("ANDROID_SERIAL")?.trim()?.takeIf(String::isNotEmpty)
        val onlineSerials = deviceRows.filterValues { state -> state == "device" }.keys.sorted()
        val serial = when {
            requestedSerial != null && deviceRows[requestedSerial] == "device" -> requestedSerial
            requestedSerial != null -> throw GradleException(
                "Android connected-test preflight failed: ANDROID_SERIAL '$requestedSerial' is " +
                    "${deviceRows[requestedSerial] ?: "not attached"}. Run `adb devices` and select " +
                    "an online device.",
            )
            onlineSerials.size == 1 -> onlineSerials.single()
            onlineSerials.isEmpty() -> throw GradleException(
                "Android connected-test preflight failed: no online device is available. Run " +
                    "`adb devices`, authorize the device, and retry.",
            )
            else -> throw GradleException(
                "Android connected-test preflight failed: multiple online devices are attached " +
                    "(${onlineSerials.joinToString()}). Set ANDROID_SERIAL explicitly.",
            )
        }

        val bootCompleted = runAdb(adb, "-s", serial, "shell", "getprop", "sys.boot_completed")
        val powerState = runAdb(adb, "-s", serial, "shell", "dumpsys", "power")
        val windowPolicy = runAdb(adb, "-s", serial, "shell", "dumpsys", "window", "policy")
        val isBooted = bootCompleted == "1"
        val isAwake = Regex("(?m)^\\s*mWakefulness=Awake\\s*$").containsMatchIn(powerState) ||
            Regex("(?m)^\\s*mInteractive=true\\s*$").containsMatchIn(powerState)
        fun readBooleanPolicyField(name: String): Boolean? {
            return Regex("(?m)^\\s*${Regex.escape(name)}=(true|false)\\s*$")
                .find(windowPolicy)
                ?.groupValues
                ?.get(1)
                ?.toBooleanStrict()
        }
        // Android 7.0 can leave showingAndNotOccluded=true after the launcher is visible while
        // reporting the authoritative mIsShowing=false. Prefer explicit keyguard state and use
        // older/version-specific fields only when the stronger signal is absent.
        val isKeyguardShowing = readBooleanPolicyField("mIsShowing")
            ?: readBooleanPolicyField("mKeyguardShowing")
            ?: readBooleanPolicyField("showingAndNotOccluded")
            ?: false
        val failures = buildList {
            if (!isBooted) add("Android has not completed booting")
            if (!isAwake) add("the display is not awake")
            if (isKeyguardShowing) add("the keyguard is showing")
        }
        if (failures.isNotEmpty()) {
            throw GradleException(
                "Android connected-test preflight failed for '$serial': ${failures.joinToString()}. " +
                    "Wake and unlock the selected device, keep its screen on, then rerun the task. " +
                    "The gate deliberately does not bypass a secure keyguard.",
            )
        }

        logger.lifecycle("Android connected-test device '$serial' is online, booted, awake, and unlocked.")
    }
}

subprojects {
    if (path in connectedDebugTestProjects) {
        tasks.matching { task -> task.name == "connectedDebugAndroidTest" }.configureEach {
            dependsOn(verifyConnectedAndroidDeviceReady)
        }
    }
}

tasks.register("qaFull") {
    group = "verification"
    description = "Run qaQuick plus connected UI tests on a preflight-verified device/emulator."
    dependsOn(
        "qaQuick",
        ":app:connectedDebugAndroidTest",
        ":samples:counter:connectedDebugAndroidTest",
        ":samples:tutorials:connectedDebugAndroidTest",
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
    dependsOn(publishForMavenSamples)
    dependsOn(
        ":samples:counter:verifyCounterPreview",
        ":viewcompose-preview-core:test",
        ":viewcompose-preview-runner:testDebugUnitTest",
        ":viewcompose-preview:verifyPaparazziDebug",
    )
}
