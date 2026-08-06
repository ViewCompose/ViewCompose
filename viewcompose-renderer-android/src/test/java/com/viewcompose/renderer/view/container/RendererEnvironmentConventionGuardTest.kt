package com.viewcompose.renderer.view.container

/*
 * 契约测试职责：锁定 renderer view/container 中的 Renderer Environment Convention Guard 边界，防止节点协议或依赖关系在重构中漂移。
 * Contract test responsibility: locks down the Renderer Environment Convention Guard boundary in renderer view/container and prevents node protocol or dependency drift.
 */

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererEnvironmentConventionGuardTest {
    @Test
    fun `container layouts should not define private density or dpToPx`() {
        val sourceDir = resolveContainerSourceDir()
        val kotlinFiles = sourceDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        val violations = mutableListOf<String>()
        kotlinFiles.forEach { file ->
            val content = file.readText()
            if (FORBIDDEN_PRIVATE_DENSITY.containsMatchIn(content)) {
                violations += "${file.name}: found private density from displayMetrics"
            }
            if (FORBIDDEN_PRIVATE_DP_TO_PX.containsMatchIn(content)) {
                violations += "${file.name}: found private dpToPx helper"
            }
        }

        assertTrue(
            "Renderer environment convention violated in ${sourceDir.path}:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    private fun resolveContainerSourceDir(): File {
        val candidates = listOf(
            File("src/main/java/com/viewcompose/renderer/view/container"),
            File("viewcompose-renderer-android/src/main/java/com/viewcompose/renderer/view/container"),
            File(System.getProperty("user.dir"), "src/main/java/com/viewcompose/renderer/view/container"),
            File(System.getProperty("user.dir"), "viewcompose-renderer-android/src/main/java/com/viewcompose/renderer/view/container"),
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("Cannot locate renderer container source directory from user.dir=${System.getProperty("user.dir")}")
    }

    companion object {
        private val FORBIDDEN_PRIVATE_DENSITY =
            Regex("""private\s+val\s+\w*density\w*\s*=\s*.*displayMetrics\.density""")
        private val FORBIDDEN_PRIVATE_DP_TO_PX = Regex("""private\s+fun\s+dpToPx\s*\(""")
    }
}
