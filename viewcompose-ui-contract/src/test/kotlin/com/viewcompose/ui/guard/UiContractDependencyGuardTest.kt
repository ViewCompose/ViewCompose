package com.viewcompose.ui.guard

/*
 * 契约测试职责：锁定 UI contract 的 Ui Contract Dependency Guard 边界，防止依赖或公开 API 在重构中漂移。
 * Contract test responsibility: locks down the Ui Contract Dependency Guard boundary in UI contract and prevents dependency or public API drift.
 */

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines

class UiContractDependencyGuardTest {
    @Test
    fun `ui-contract main source must stay platform neutral`() {
        val sourceRoot = resolveMainSourceRoot()
        val violations = mutableListOf<String>()
        Files.walk(sourceRoot).use { paths ->
            paths
                .filter { it.isRegularFile() && it.extension == "kt" }
                .forEach { file ->
                    file.readLines()
                        .filter { line ->
                            line.startsWith("import android.") || line.startsWith("import androidx.")
                        }
                        .forEach { line ->
                            violations += "${sourceRoot.relativize(file)}: $line"
                        }
                }
        }

        assertTrue(
            "ui-contract should not import android/androidx APIs.\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    private fun resolveMainSourceRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val moduleRoot = when {
            Files.isDirectory(cwd.resolve("src/main/kotlin")) -> cwd
            Files.isDirectory(cwd.resolve("viewcompose-ui-contract/src/main/kotlin")) ->
                cwd.resolve("viewcompose-ui-contract")
            else -> error("Cannot locate viewcompose-ui-contract module root from $cwd")
        }
        return moduleRoot.resolve("src/main/kotlin")
    }
}
