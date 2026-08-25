// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.viewcompose.quality.root")
    id("com.viewcompose.publishing.root")
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.paparazzi) apply false
}

extensions.configure<com.viewcompose.quality.ViewComposeQualityExtension> {
    repositoryDirectory.set(project.layout.projectDirectory)
    moduleCatalogFile.set(
        project.layout.projectDirectory.file("gradle/viewcompose-publishing.properties"),
    )
    sourceSetDirectories.from(
        project.subprojects.mapNotNull { subproject ->
            subproject.layout.projectDirectory.dir("src").asFile.takeIf { directory ->
                directory.isDirectory
            }
        },
    )
    policyFiles.from(
        project.layout.projectDirectory.file("AGENTS.md"),
        project.layout.projectDirectory.file("docs/project/documentation-governance.md"),
        project.layout.projectDirectory.file("docs/project/api-documentation-quality.md"),
        project.layout.projectDirectory.file("gradle/viewcompose-dependency-contracts.properties"),
    )
    reportsDirectory.set(
        project.layout.buildDirectory.dir("reports/viewcompose-quality"),
    )
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
    "viewcompose-diagnostics" to "com.viewcompose.diagnostics",
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
    "viewcompose-media3-androidx" to "com.viewcompose.media3",
    "viewcompose-exoplayer2-android" to "com.viewcompose.exoplayer2",
    "viewcompose-google-maps-android" to "com.viewcompose.maps.google",
    "viewcompose-camerax-androidx" to "com.viewcompose.camerax",
    "viewcompose-paging-androidx" to "com.viewcompose.paging",
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
    "viewcompose-diagnostics" to "integration",
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
    "viewcompose-media3-androidx" to "integration",
    "viewcompose-exoplayer2-android" to "integration",
    "viewcompose-google-maps-android" to "integration",
    "viewcompose-camerax-androidx" to "integration",
    "viewcompose-paging-androidx" to "integration",
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

val configuredModulePackageRoots = modulePackageRoots
val configuredForbiddenLegacyPackageRoots = forbiddenLegacyPackageRoots
val configuredKotlinJvmModules = kotlinJvmModules
val configuredRuntimeModuleLayers = runtimeModuleLayers
val configuredAllowedDependencyLayers = allowedDependencyLayers
val configuredToolingModules = toolingModules

extensions.configure<com.viewcompose.quality.ViewComposeQualityExtension> {
    settingsFile.set(project.layout.projectDirectory.file("settings.gradle.kts"))
    moduleBuildFiles.from(
        project.subprojects.map { subproject ->
            subproject.layout.projectDirectory.file("build.gradle.kts")
        },
    )
    modulePackageRoots.set(configuredModulePackageRoots)
    forbiddenLegacyPackageRoots.set(configuredForbiddenLegacyPackageRoots)
    kotlinJvmModules.set(configuredKotlinJvmModules)
    runtimeModuleLayers.set(configuredRuntimeModuleLayers)
    allowedDependencyLayers.set(
        configuredAllowedDependencyLayers.mapValues { (_, layers) ->
            layers.sorted().joinToString(",")
        },
    )
    toolingModules.set(configuredToolingModules)
}

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
    ":viewcompose-diagnostics:compileDebugKotlin",
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
    ":viewcompose-media3-androidx:compileDebugKotlin",
    ":viewcompose-exoplayer2-android:compileDebugKotlin",
    ":viewcompose-google-maps-android:compileDebugKotlin",
    ":viewcompose-camerax-androidx:compileDebugKotlin",
    ":viewcompose-paging-androidx:compileDebugKotlin",
    ":samples:counter:assembleDebug",
    ":samples:counter:compileDebugAndroidTestKotlin",
    ":samples:tutorials:assembleDebug",
    ":samples:tutorials:compileDebugAndroidTestKotlin",
    ":app:compileDebugKotlin",
    ":viewcompose-benchmark:compileBenchmarkKotlin",
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
    ":viewcompose-diagnostics:testDebugUnitTest",
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
    ":viewcompose-media3-androidx:testDebugUnitTest",
    ":viewcompose-exoplayer2-android:testDebugUnitTest",
    ":viewcompose-google-maps-android:testDebugUnitTest",
    ":viewcompose-camerax-androidx:testDebugUnitTest",
    ":viewcompose-paging-androidx:testDebugUnitTest",
    ":integration-tests:paging-presenter:test",
    ":app:testDebugUnitTest",
)

tasks.register("qaQuick") {
    group = "verification"
    description = "Run compile + unit-test quality gate for all core modules."
    dependsOn("verifyModulePackageRoots")
    dependsOn("verifyAndroidModuleNamespaces")
    dependsOn("verifyModuleDependencyBoundaries")
    dependsOn("verifyDevelopmentToolingIsolation")
    dependsOn("verifyDemoReleaseToolingApk")
    dependsOn("testPagingMacrobenchmarkSummaryTool")
    dependsOn("testDeviceDiagnosticsRequestMeasurementTool")
    dependsOn("verifyDemoAutomationSelectors")
    dependsOn("verifyDemoLocalizationResources")
    dependsOn("verifyDemoLocalizedVisibleCopy")
    dependsOn("verifyDesignSystemIsolation")
    dependsOn("verifyUiFoundationPlatformBoundary")
    dependsOn("verifyDocumentationStructure")
    dependsOn("verifyDslApiContracts")
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
    description = "Generate engine-neutral benchmark Markdown and JSON reports."
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
    description = "Run release macrobenchmarks and generate the engine comparison report."
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

tasks.register<Exec>("testPagingMacrobenchmarkSummaryTool") {
    group = "verification"
    description = "Run unit tests for the Paging Macrobenchmark summary tool."
    workingDir(rootDir.resolve("tools/performance"))
    commandLine(
        "python3",
        "-m",
        "unittest",
        "test_summarize_paging_macrobenchmark.py",
    )
}

tasks.register<Exec>("testDeviceDiagnosticsRequestMeasurementTool") {
    group = "verification"
    description = "Run unit tests for the explicit device diagnostics request measurement tool."
    workingDir(rootDir.resolve("tools/performance"))
    commandLine(
        "python3",
        "-m",
        "unittest",
        "test_measure_device_diagnostics_request.py",
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
