package com.viewcompose.quality

import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LifecycleQualityTasksTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `stable lifecycle entry points retain metadata and direct dependencies`() {
        val project = ProjectBuilder.builder()
            .withProjectDir(temporaryFolder.newFolder("repository"))
            .build()

        project.pluginManager.apply(ViewComposeQualityRootPlugin::class.java)

        assertLifecycleTask(
            project.tasks.getByName("qaAffected"),
            "Run the graph-validated affected Gradle candidate before complete shadow verification.",
        )
        assertLifecycleTask(
            project.tasks.getByName("qaQuick"),
            "Run compile + unit-test quality gate for all core modules.",
        )
        assertLifecycleTask(
            project.tasks.getByName("qaFull"),
            "Run qaQuick plus connected UI tests on a preflight-verified device/emulator.",
        )
        assertLifecycleTask(
            project.tasks.getByName("qaRelease"),
            "Assemble the optimized release and non-debuggable benchmark artifacts.",
        )
        assertLifecycleTask(
            project.tasks.getByName("benchmarkRelease"),
            "Run release macrobenchmarks on a connected device or emulator.",
        )
        assertLifecycleTask(
            project.tasks.getByName("benchmarkCompare"),
            "Run release macrobenchmarks and generate the engine comparison report.",
        )
        assertLifecycleTask(
            project.tasks.getByName("qaPreview"),
            "Run static-runner tests and preview snapshot verification.",
        )

        val qaQuick = project.tasks.getByName("qaQuick")
        assertEquals(qaQuickTaskPaths.size, qaQuickTaskPaths.toSet().size)
        assertEquals(
            qaQuickTaskPaths.toSet() + setOf(
                "verifyModulePackageRoots",
                "verifyAndroidModuleNamespaces",
                "verifyModuleDependencyBoundaries",
                "verifyDevelopmentToolingIsolation",
                "verifyDemoReleaseToolingApk",
                "testPagingMacrobenchmarkSummaryTool",
                "testDeviceDiagnosticsRequestMeasurementTool",
                "verifyDemoAutomationSelectors",
                "verifyDemoLocalizationResources",
                "verifyDemoLocalizedVisibleCopy",
                "verifyDesignSystemIsolation",
                "verifyUiFoundationPlatformBoundary",
                "verifyAiToolingContracts",
                "verifyAiKnowledgeBundle",
                "verifyAiStaticTooling",
                "verifyAiRetrieval",
                "verifyAiMcp",
                "verifyAiLayoutDiagnosis",
                "verifyAiConsumerWorkflows",
                "verifyAiDistribution",
                "verifyAiDesignIr",
                "verifyAiXmlProjectContext",
                "verifyAiXmlMigration",
                "verifyDocumentationStructure",
                "verifyDslApiContracts",
                "verifyMigrationPairedSamples",
                "verifyTutorialSamples",
                "verifyViewComposePublishingConfiguration",
                "verifyViewComposeReleaseIntent",
                "publishViewComposeToLocalRepository",
                "verifyRuntimePurity",
                "verifyNavigationCorePurity",
                "verifyGestureCorePurity",
                "verifyGraphicsCorePurity",
                "verifyPreviewCorePurity",
                "verifyPreviewRunnerBoundary",
                "verifyPreviewGradlePluginBoundary",
                "verifyPreviewWorkerHostBoundary",
            ),
            qaQuick.stringDependencies(),
        )
        assertTrue(qaQuick.dependsOn.any { dependency -> dependency is Provider<*> })
        assertEquals(
            setOf(
                "qaQuick",
                ":app:connectedDebugAndroidTest",
                ":samples:counter:connectedDebugAndroidTest",
                ":samples:tutorials:connectedDebugAndroidTest",
            ),
            project.tasks.getByName("qaFull").stringDependencies(),
        )
        assertEquals(
            setOf(
                ":app:assembleRelease",
                ":app:assembleBenchmark",
                ":viewcompose-benchmark:assembleBenchmark",
            ),
            project.tasks.getByName("qaRelease").stringDependencies(),
        )
        assertEquals(
            setOf(":viewcompose-benchmark:connectedBenchmarkAndroidTest"),
            project.tasks.getByName("benchmarkRelease").stringDependencies(),
        )
        assertEquals(
            setOf("benchmarkRelease", "benchmarkComparisonReport"),
            project.tasks.getByName("benchmarkCompare").stringDependencies(),
        )
        assertEquals(
            setOf(
                "publishViewComposeToLocalRepository",
                ":samples:counter:verifyCounterPreview",
                ":viewcompose-preview-core:test",
                ":viewcompose-preview-runner:testDebugUnitTest",
                ":viewcompose-preview:verifyPaparazziDebug",
            ),
            project.tasks.getByName("qaPreview").stringDependencies(),
        )
    }

    @Test
    fun `Maven sample tasks remain ordered after lazily registered local publication`() {
        val root = ProjectBuilder.builder()
            .withProjectDir(temporaryFolder.newFolder("root"))
            .build()
        val samples = ProjectBuilder.builder().withName("samples").withParent(root).build()
        val counter = ProjectBuilder.builder().withName("counter").withParent(samples).build()
        val tutorials = ProjectBuilder.builder().withName("tutorials").withParent(samples).build()
        val unrelated = ProjectBuilder.builder().withName("unrelated").withParent(root).build()
        val counterTask = counter.tasks.register("resolveCounter").get()

        root.pluginManager.apply(ViewComposeQualityRootPlugin::class.java)
        val publication = root.tasks.register("publishViewComposeToLocalRepository").get()
        val tutorialTask = tutorials.tasks.register("resolveTutorial").get()
        val unrelatedTask = unrelated.tasks.register("resolveUnrelated").get()

        assertTrue(counterTask.mustRunAfter.getDependencies(counterTask).contains(publication))
        assertTrue(tutorialTask.mustRunAfter.getDependencies(tutorialTask).contains(publication))
        assertFalse(unrelatedTask.mustRunAfter.getDependencies(unrelatedTask).contains(publication))
    }

    private fun assertLifecycleTask(task: Task, description: String) {
        assertEquals("verification", task.group)
        assertEquals(description, task.description)
    }

    private fun Task.stringDependencies(): Set<String> = dependsOn
        .asSequence()
        .flatMap { dependency -> dependency.flattened() }
        .filterIsInstance<String>()
        .toSet()

    private fun Any.flattened(): Sequence<Any> = when (this) {
        is Iterable<*> -> asSequence().filterNotNull().flatMap { value -> value.flattened() }
        is Array<*> -> asSequence().filterNotNull().flatMap { value -> value.flattened() }
        else -> sequenceOf(this)
    }
}
