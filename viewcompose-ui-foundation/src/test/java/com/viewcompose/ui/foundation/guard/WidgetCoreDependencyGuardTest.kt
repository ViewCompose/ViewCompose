package com.viewcompose.ui.foundation.guard

/*
 * 契约测试职责：锁定 widget-core guard 中的 Widget Core Dependency Guard 边界，防止 DSL 或依赖关系在重构中漂移。
 * Contract test responsibility: locks down the Widget Core Dependency Guard boundary in widget-core guard and prevents DSL or dependency drift.
 */

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines

class WidgetCoreDependencyGuardTest {
    @Test
    fun `widget-core main source must not import renderer package`() {
        val sourceRoot = resolveMainSourceRoot()
        val violations = mutableListOf<String>()
        Files.walk(sourceRoot).use { paths ->
            paths
                .filter { it.isRegularFile() && it.extension == "kt" }
                .forEach { file ->
                    file.readLines()
                        .filter { line -> line.startsWith("import com.viewcompose.renderer.") }
                        .forEach { line ->
                            violations += "${sourceRoot.relativize(file)}: $line"
                        }
                }
        }

        assertTrue(
            "widget-core should not import renderer package.\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun `widget-core main source must not import renderer diagnostics types`() {
        val sourceRoot = resolveMainSourceRoot()
        val diagnosticsImports = setOf(
            "import com.viewcompose.renderer.view.tree.RenderStats",
            "import com.viewcompose.renderer.view.tree.RenderTreeResult",
            "import com.viewcompose.renderer.view.tree.RenderStructureStats",
        )
        val violations = mutableListOf<String>()
        Files.walk(sourceRoot).use { paths ->
            paths
                .filter { it.isRegularFile() && it.extension == "kt" }
                .forEach { file ->
                    file.readLines()
                        .filter { line -> diagnosticsImports.any(line::startsWith) }
                        .forEach { line ->
                            violations += "${sourceRoot.relativize(file)}: $line"
                        }
                }
        }

        assertTrue(
            "widget-core should not import renderer diagnostics types.\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    private fun resolveMainSourceRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val moduleRoot = when {
            Files.isDirectory(cwd.resolve("src/main/java")) -> cwd
            Files.isDirectory(cwd.resolve("viewcompose-ui-foundation/src/main/java")) ->
                cwd.resolve("viewcompose-ui-foundation")
            else -> error("Cannot locate viewcompose-ui-foundation module root from $cwd")
        }
        return moduleRoot.resolve("src/main/java")
    }
}
