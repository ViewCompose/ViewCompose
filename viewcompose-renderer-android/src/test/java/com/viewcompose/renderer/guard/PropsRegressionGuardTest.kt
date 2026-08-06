package com.viewcompose.renderer.guard

/*
 * 契约测试职责：锁定 renderer guard 中的 Props Regression Guard 边界，防止节点协议或依赖关系在重构中漂移。
 * Contract test responsibility: locks down the Props Regression Guard boundary in renderer guard and prevents node protocol or dependency drift.
 */

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PropsRegressionGuardTest {
    private val scanRoots = listOf(
        "viewcompose-renderer-android/src/main/java",
        "viewcompose-ui-foundation/src/main/java",
        "viewcompose-overlay-material3-android/src/main/java",
        "viewcompose-runtime/src/main/java",
    )

    private val forbiddenPatterns = listOf(
        "node.props access" to Regex("""\bnode\s*\.\s*props\b"""),
        "TypedPropKeys reference" to Regex("""\bTypedPropKeys\b"""),
        "PropKeys reference" to Regex("""\bPropKeys\b"""),
        "Props reference" to Regex("""\bProps\b"""),
    )

    @Test
    fun `framework main sources must remain props free`() {
        val projectRoot = File(requireNotNull(System.getProperty("user.dir")))
        val violations = mutableListOf<String>()

        scanRoots.forEach { relativeRoot ->
            val dir = projectRoot.resolve(relativeRoot)
            if (!dir.exists()) return@forEach
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    file.useLines { lines ->
                        lines.forEachIndexed { index, line ->
                            forbiddenPatterns.forEach { (name, regex) ->
                                if (regex.containsMatchIn(line)) {
                                    val path = file.relativeTo(projectRoot).path
                                    violations += "$path:${index + 1}: $name -> ${line.trim()}"
                                }
                            }
                        }
                    }
                }
        }

        assertTrue(
            "Detected removed Props API references:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }
}
