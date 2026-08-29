package com.viewcompose.quality

import org.gradle.api.tasks.Exec
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ViewComposeQualityRootPluginTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `maps explicit extension inputs to the configuration report task`() {
        val repository = temporaryFolder.newFolder("repository")
        val catalog = repository.resolve("catalog.properties").apply { writeText("module=value\n") }
        val sourceSet = repository.resolve("module/src/main").apply { mkdirs() }
        val policy = repository.resolve("policy.txt").apply { writeText("policy\n") }
        repository.resolve("docs/tutorials/getting-started.md").apply {
            parentFile.mkdirs()
            writeText("# Getting started\n")
        }
        repository.resolve("docs/tutorials/nested/automatically-discovered.md").apply {
            parentFile.mkdirs()
            writeText("# Automatically discovered\n")
        }
        val project = ProjectBuilder.builder().withProjectDir(repository).build()

        project.pluginManager.apply(ViewComposeQualityRootPlugin::class.java)
        val extension = project.extensions.getByType(ViewComposeQualityExtension::class.java)
        extension.repositoryDirectory.set(project.layout.projectDirectory)
        extension.moduleCatalogFile.set(catalog)
        extension.sourceSetDirectories.from(sourceSet)
        extension.policyFiles.from(policy)

        val task = project.tasks.getByName("writeViewComposeQualityConfiguration")
            as WriteViewComposeQualityConfigurationTask
        assertEquals(repository.canonicalFile, task.repositoryDirectory.get().asFile.canonicalFile)
        assertEquals(catalog.canonicalFile, task.moduleCatalogFile.get().asFile.canonicalFile)
        assertEquals(
            setOf(sourceSet.canonicalFile),
            task.sourceSetDirectories.files.map { it.canonicalFile }.toSet(),
        )
        assertEquals(
            setOf(policy.canonicalFile),
            task.policyFiles.files.map { it.canonicalFile }.toSet(),
        )
        assertEquals(
            repository.resolve("build/reports/viewcompose-quality/configuration.json").canonicalFile,
            task.reportFile.get().asFile.canonicalFile,
        )

        task.writeConfiguration()
        val report = task.reportFile.get().asFile.readText()
        assertTrue(report.contains("\"moduleCatalog\": \"catalog.properties\""))
        assertTrue(report.contains("\"module/src/main\""))
        assertTrue(report.contains("\"policy.txt\""))

        assertTrue(project.tasks.getByName("verifyModulePackageRoots") is VerifyModulePackageRootsTask)
        assertTrue(
            project.tasks.getByName("verifyAndroidModuleNamespaces") is
                VerifyAndroidModuleNamespacesTask,
        )
        assertTrue(
            project.tasks.getByName("verifyModuleDependencyBoundaries") is
                VerifyModuleDependencyBoundariesTask,
        )
        assertTrue(project.tasks.getByName("verifyDesignSystemIsolation") is VerifyDesignSystemIsolationTask)
        assertTrue(
            project.tasks.getByName("verifyUiFoundationPlatformBoundary") is
                VerifyUiFoundationPlatformBoundaryTask,
        )
        listOf(
            "verifyRuntimePurity",
            "verifyGestureCorePurity",
            "verifyGraphicsCorePurity",
            "verifyPreviewCorePurity",
            "verifyPreviewRunnerBoundary",
            "verifyPreviewGradlePluginBoundary",
            "verifyPreviewWorkerHostBoundary",
            "verifyNavigationCorePurity",
        ).forEach { taskName ->
            assertTrue(project.tasks.getByName(taskName) is VerifySourceBoundaryTask)
        }
        assertTrue(
            project.tasks.getByName("verifyDevelopmentToolingIsolation") is
                VerifyDevelopmentToolingIsolationTask,
        )
        assertTrue(
            project.tasks.getByName("verifyDemoReleaseToolingApk") is
                VerifyDemoReleaseToolingApkTask,
        )
        assertTrue(
            project.tasks.getByName("verifyDemoAutomationSelectors") is
                VerifyDemoAutomationSelectorsTask,
        )
        assertTrue(
            project.tasks.getByName("verifyDemoLocalizationResources") is
                VerifyDemoLocalizationResourcesTask,
        )
        assertTrue(
            project.tasks.getByName("verifyDemoLocalizedVisibleCopy") is
                VerifyDemoLocalizedVisibleCopyTask,
        )
        assertTrue(
            project.tasks.getByName("verifyMigrationPairedSamples") is
                VerifyMigrationPairedSamplesTask,
        )
        val tutorialTask = project.tasks.getByName("verifyTutorialSamples")
            as VerifyTutorialSamplesTask
        assertTrue(
            tutorialTask.documentationFiles.files.any { file ->
                file.invariantSeparatorsPath.endsWith("docs/tutorials/getting-started.md")
            },
        )
        assertTrue(
            tutorialTask.documentationFiles.files.any { file ->
                file.invariantSeparatorsPath.endsWith(
                    "docs/tutorials/nested/automatically-discovered.md",
                )
            },
        )
        assertTrue(project.tasks.getByName("verifyDocumentationScripts") is Exec)
        assertTrue(project.tasks.getByName("verifyAiToolingContracts") is Exec)
        assertTrue(project.tasks.getByName("verifyAiStaticTooling") is Exec)
        assertTrue(project.tasks.getByName("verifyAiRetrieval") is Exec)
        assertTrue(project.tasks.getByName("verifyAiMcp") is Exec)
        assertTrue(project.tasks.getByName("verifyAiLayoutDiagnosis") is Exec)
        assertTrue(project.tasks.getByName("generateAiKnowledgeBundle") is Exec)
        assertTrue(project.tasks.getByName("verifyAiKnowledgeBundle") is Exec)
        assertTrue(project.tasks.getByName("verifyDocumentLanguages") is Exec)
        assertTrue(project.tasks.getByName("verifyDocumentationTranslations") is Exec)
        val documentationStructure = project.tasks.getByName("verifyDocumentationStructure")
        val documentationGovernance = project.tasks.getByName("verifyDocumentationGovernanceV2")
        val updateCapabilityReference =
            project.tasks.getByName("updateDocumentationCapabilityReference")
        assertTrue(documentationStructure is VerifyDocumentationStructureTask)
        assertTrue(documentationGovernance is VerifyDocumentationGovernanceV2Task)
        assertTrue(updateCapabilityReference is UpdateDocumentationCapabilityReferenceTask)
        assertEquals(
            repository.resolve("website/src/data/capability-reference.json").canonicalFile,
            (documentationGovernance as VerifyDocumentationGovernanceV2Task)
                .committedReferenceFile.get().asFile.canonicalFile,
        )
        assertEquals(
            repository.resolve("website/src/data/capability-reference.json").canonicalFile,
            (updateCapabilityReference as UpdateDocumentationCapabilityReferenceTask)
                .referenceFile.get().asFile.canonicalFile,
        )
        assertTrue(
            documentationStructure.taskDependencies
                .getDependencies(documentationStructure)
                .contains(documentationGovernance),
        )
        assertTrue(
            project.tasks.getByName("verifyDslApiContracts") is VerifyDslApiContractsTask,
        )
        assertTrue(
            project.tasks.getByName("verifyConnectedAndroidDeviceReady") is
                VerifyConnectedAndroidDeviceReadyTask,
        )
        listOf(
            "benchmarkComparisonReport",
            "testBenchmarkComparisonTool",
            "testPagingMacrobenchmarkSummaryTool",
            "testDeviceDiagnosticsRequestMeasurementTool",
        ).forEach { taskName ->
            assertTrue(project.tasks.getByName(taskName) is Exec)
        }
        listOf(
            "qaAffected",
            "qaQuick",
            "qaFull",
            "qaRelease",
            "benchmarkRelease",
            "benchmarkCompare",
            "qaPreview",
        ).forEach { taskName ->
            assertEquals("verification", project.tasks.getByName(taskName).group)
        }
    }

    @Test
    fun `rejects application below the root project`() {
        val root = ProjectBuilder.builder().withProjectDir(temporaryFolder.newFolder("root")).build()
        val child = ProjectBuilder.builder().withName("child").withParent(root).build()

        assertThrows(IllegalStateException::class.java) {
            ViewComposeQualityRootPlugin().apply(child)
        }
    }
}
