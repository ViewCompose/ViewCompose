package com.viewcompose.quality

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
