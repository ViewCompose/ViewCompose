package com.viewcompose.renderer.guard

/*
 * 契约测试职责：锁定 Android Renderer 只执行已解析协议，防止按具体设计系统名称分支。
 * Contract test responsibility: keeps Android Renderer limited to resolved contracts and prevents
 * branches named after concrete design systems.
 */

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignSystemIsolationGuardTest {
    private val forbiddenPatterns = listOf(
        "Material3" to Regex("""\bMaterial3\b"""),
        "One UI" to Regex("""\b(?:OneUi|OneUI)\b"""),
        "Cupertino" to Regex("""\bCupertino\b"""),
        "design-system identity" to Regex("""\bdesignSystem(?:Id|Name|Identity)\b"""),
        "Material 3 module import" to Regex("""import\s+com\.viewcompose\.material3(?:\.|\b)"""),
    )

    @Test
    fun `renderer source must remain independent from concrete design systems`() {
        val sourceRoot = resolveMainSourceRoot()
        val violations = mutableListOf<String>()

        Files.walk(sourceRoot).use { paths ->
            paths
                .filter { path -> path.isRegularFile() && path.extension == "kt" }
                .forEach { file ->
                    val source = file.readText()
                    forbiddenPatterns.forEach { (name, pattern) ->
                        if (pattern.containsMatchIn(source)) {
                            violations += "${sourceRoot.relativize(file)}: $name"
                        }
                    }
                }
        }

        assertTrue(
            "Android Renderer must not identify or branch on a design system.\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    private fun resolveMainSourceRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val moduleRoot = when {
            Files.isDirectory(cwd.resolve("src/main/java")) -> cwd
            Files.isDirectory(cwd.resolve("viewcompose-renderer-android/src/main/java")) ->
                cwd.resolve("viewcompose-renderer-android")
            else -> error("Cannot locate viewcompose-renderer-android module root from $cwd")
        }
        return moduleRoot.resolve("src/main/java")
    }
}
