package com.viewcompose.renderer.guard

/*
 * 契约测试职责：锁定 renderer guard 中的 Legacy Node Contract Guard 边界，防止节点协议或依赖关系在重构中漂移。
 * Contract test responsibility: locks down the Legacy Node Contract Guard boundary in renderer guard and prevents node protocol or dependency drift.
 */

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyNodeContractGuardTest {
    @Test
    fun `renderer must not own a parallel node contract`() {
        val mainSourceRoot = resolveMainSourceRoot()
        val legacyRoot = mainSourceRoot.resolve("com/viewcompose/renderer/node")
        val legacyFiles = if (Files.isDirectory(legacyRoot)) {
            Files.walk(legacyRoot).use { paths ->
                paths
                    .filter { path -> Files.isRegularFile(path) && path.toString().endsWith(".kt") }
                    .map { path -> mainSourceRoot.relativize(path).toString() }
                    .sorted()
                    .toList()
            }
        } else {
            emptyList()
        }

        assertTrue(
            "Renderer node contracts must come from viewcompose-ui-contract; " +
                "remove these renderer-owned mirrors:\n${legacyFiles.joinToString("\n")}",
            legacyFiles.isEmpty(),
        )
    }

    private fun resolveMainSourceRoot(): Path {
        val cwd = Paths.get(requireNotNull(System.getProperty("user.dir")))
            .toAbsolutePath()
            .normalize()
        val moduleRoot = when {
            Files.isDirectory(cwd.resolve("src/main/java")) -> cwd
            Files.isDirectory(cwd.resolve("viewcompose-renderer/src/main/java")) ->
                cwd.resolve("viewcompose-renderer")
            else -> error("Cannot locate viewcompose-renderer from $cwd")
        }
        return moduleRoot.resolve("src/main/java")
    }
}
