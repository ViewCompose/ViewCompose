package com.viewcompose.quality

import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

/** Registers the stable repository lifecycle entry points without owning their gate bodies. */
internal fun Project.registerLifecycleQualityTasks(extension: ViewComposeQualityExtension) {
    registerAffectedVerificationTask(extension)
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
        dependsOn("verifyAiToolingContracts")
        dependsOn("verifyAiKnowledgeBundle")
        dependsOn("verifyAiStaticTooling")
        dependsOn("verifyAiRetrieval")
        dependsOn("verifyAiMcp")
        dependsOn("verifyAiLayoutDiagnosis")
        dependsOn("verifyAiConsumerWorkflows")
        dependsOn("verifyAiDistribution")
        dependsOn("verifyAiDesignIr")
        dependsOn("verifyAiXmlProjectContext")
        dependsOn("verifyAiXmlLayoutDependencies")
        dependsOn("verifyAiXmlMigration")
        dependsOn("verifyAiGeneratedPreview")
        dependsOn("verifyDocumentationStructure")
        dependsOn("verifyDslApiContracts")
        dependsOn("verifyMigrationPairedSamples")
        dependsOn("verifyTutorialSamples")
        dependsOn("verifyViewComposePublishingConfiguration")
        dependsOn("verifyViewComposeReleaseIntent")
        dependsOn("publishViewComposeToLocalRepository")
        dependsOn(
            providers.provider {
                gradle.includedBuild("viewcompose-publishing-build").task(":test")
            },
        )
        dependsOn("verifyRuntimePurity")
        dependsOn("verifyNavigationCorePurity")
        dependsOn("verifyGestureCorePurity")
        dependsOn("verifyGraphicsCorePurity")
        dependsOn("verifyPreviewCorePurity")
        dependsOn("verifyPreviewRunnerBoundary")
        dependsOn("verifyPreviewGradlePluginBoundary")
        dependsOn("verifyPreviewWorkerHostBoundary")
        dependsOn(qaQuickTaskPaths)
    }

    // The tutorial applications deliberately use Maven coordinates instead of project
    // dependencies. Preserve resolution ordering so a hard-cut artifact rename is verified before
    // the first Central publication, even though the publishing plugin is applied after this one.
    tasks.matching { task -> task.name == "publishViewComposeToLocalRepository" }.configureEach {
        val publishTask = this
        subprojects
            .filter { subproject -> subproject.path in mavenSampleProjectPaths }
            .forEach { sampleProject ->
                sampleProject.tasks.configureEach {
                    mustRunAfter(publishTask)
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

    tasks.register("benchmarkCompare") {
        group = "verification"
        description = "Run release macrobenchmarks and generate the engine comparison report."
        dependsOn(
            "benchmarkRelease",
            "benchmarkComparisonReport",
        )
    }

    tasks.register("qaPreview") {
        group = "verification"
        description = "Run static-runner tests and preview snapshot verification."
        dependsOn("publishViewComposeToLocalRepository")
        dependsOn(
            ":samples:counter:verifyCounterPreview",
            ":viewcompose-preview-core:test",
            ":viewcompose-preview-runner:testDebugUnitTest",
            ":viewcompose-preview:verifyPaparazziDebug",
        )
    }
}

internal val qaQuickTaskPaths = listOf(
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

private val mavenSampleProjectPaths = setOf(
    ":samples:counter",
    ":samples:tutorials",
)
