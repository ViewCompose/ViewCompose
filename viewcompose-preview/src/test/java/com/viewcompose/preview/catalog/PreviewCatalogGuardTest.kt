package com.viewcompose.preview.catalog

/*
 * 契约测试职责：锁定 preview catalog 的 Preview Catalog Guard 边界，防止依赖或公开 API 在重构中漂移。
 * Contract test responsibility: locks down the Preview Catalog Guard boundary in preview catalog and prevents dependency or public API drift.
 */

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewCatalogGuardTest {
    @Test
    fun previewSpecIdsAndGroupsAreUniqueAndTitlesAreNotBlank() {
        val specs = PreviewCatalog.specs
        val duplicateIds = specs.groupBy { it.id }.filterValues { entries -> entries.size > 1 }.keys
        assertTrue("Duplicate preview ids: $duplicateIds", duplicateIds.isEmpty())

        val blankTitles = specs.filter { it.title.isBlank() }.map { it.id }
        assertTrue("Blank titles are not allowed: $blankTitles", blankTitles.isEmpty())

        val duplicateDomainTitles = specs
            .groupBy { spec -> spec.domain to spec.title.trim() }
            .filterValues { entries -> entries.size > 1 }
            .keys
        assertTrue(
            "Duplicate titles in the same domain are not allowed: $duplicateDomainTitles",
            duplicateDomainTitles.isEmpty(),
        )
    }

    @Test
    fun previewCatalogCoverageMatchesTargetList() {
        val actual = PreviewCatalog.specs.map { it.id }.toSet()
        assertEquals(
            "Preview catalog ids and coverage target ids must stay in sync.",
            PreviewCoverageTargets.requiredSpecIds,
            actual,
        )
    }

    @Test
    fun previewModuleMustNotDependOnAppModuleOrDemoImports() {
        val buildFile = File("build.gradle.kts")
        assertTrue("Missing build.gradle.kts in preview module.", buildFile.exists())
        val buildScript = buildFile.readText()
        assertTrue(
            "viewcompose-preview must not depend on :app module.",
            !buildScript.contains("project(\":app\")"),
        )

        val srcMain = File("src/main")
        val demoImports = srcMain.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .flatMap { file -> file.readLines().asSequence() }
            .filter { line -> line.contains("import com.viewcompose.demo") }
            .toList()
        assertTrue(
            "Preview source must not import demo package: $demoImports",
            demoImports.isEmpty(),
        )
    }
}
