package com.viewcompose.constraintlayout

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URLClassLoader
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConstraintLayoutCompileSafetyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `valid axis targets and reference-based constraint set compile`() {
        assertCompiles(
            source = """
                import com.viewcompose.constraintlayout.*
                import com.viewcompose.ui.foundation.Text
                import com.viewcompose.ui.foundation.UiTreeBuilder
                import com.viewcompose.ui.modifier.Modifier

                fun UiTreeBuilder.validConstraintDsl() {
                    val set = constraintSet {
                        val title = createRef("title")
                        constrain(title) {
                            startToStart(parent)
                            topToTop(parent)
                        }
                    }
                    ConstraintLayout(constraintSet = set) {
                        val title = createRef("title")
                        val startGuide = createGuidelineFromStart(0.25f)
                        val topGuide = createGuidelineFromTop(0.25f)
                        Text(
                            text = "Title",
                            modifier = Modifier.constrainAs(title) {
                                startToStart(startGuide)
                                topToTop(topGuide)
                            },
                        )
                    }
                }
            """.trimIndent(),
        )
    }

    @Test
    fun `horizontal anchors reject vertical-only helpers`() {
        assertDoesNotCompile(
            source = """
                import com.viewcompose.constraintlayout.*
                import com.viewcompose.ui.foundation.Text
                import com.viewcompose.ui.foundation.UiTreeBuilder
                import com.viewcompose.ui.modifier.Modifier

                fun UiTreeBuilder.invalidHorizontalTarget() {
                    ConstraintLayout {
                        val title = createRef("title")
                        val topGuide = createGuidelineFromTop(0.25f)
                        Text("Title", Modifier.constrainAs(title) { startToStart(topGuide) })
                    }
                }
            """.trimIndent(),
        )
    }

    @Test
    fun `vertical anchors reject horizontal-only helpers`() {
        assertDoesNotCompile(
            source = """
                import com.viewcompose.constraintlayout.*
                import com.viewcompose.ui.foundation.Text
                import com.viewcompose.ui.foundation.UiTreeBuilder
                import com.viewcompose.ui.modifier.Modifier

                fun UiTreeBuilder.invalidVerticalTarget() {
                    ConstraintLayout {
                        val title = createRef("title")
                        val startGuide = createGuidelineFromStart(0.25f)
                        Text("Title", Modifier.constrainAs(title) { topToTop(startGuide) })
                    }
                }
            """.trimIndent(),
        )
    }

    @Test
    fun `nested layout scope hides outer constraint helper receiver`() {
        assertDoesNotCompile(
            source = """
                import com.viewcompose.constraintlayout.*
                import com.viewcompose.ui.foundation.Column
                import com.viewcompose.ui.foundation.UiTreeBuilder

                fun UiTreeBuilder.invalidReceiverLeak() {
                    ConstraintLayout {
                        Column {
                            createGuidelineFromTop(0.25f)
                        }
                    }
                }
            """.trimIndent(),
        )
    }

    @Test
    fun `constraint set rejects string constraint targets`() {
        assertDoesNotCompile(
            source = """
                import com.viewcompose.constraintlayout.*

                fun invalidStringConstraintTarget() = constraintSet {
                    constrain("title") {
                        startToStart(parent)
                    }
                }
            """.trimIndent(),
        )
    }

    private fun assertCompiles(source: String) {
        val result = compile(source)
        assertEquals(result.diagnostics, ExitCode.OK, result.exitCode)
    }

    private fun assertDoesNotCompile(source: String) {
        val result = compile(source)
        assertEquals(result.diagnostics, ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.diagnostics, result.diagnostics.contains("error:"))
    }

    private fun compile(source: String): CompilationResult {
        val sourceFile = temporaryFolder.newFile("CompileSafety${temporaryFolder.root.list().orEmpty().size}.kt")
        sourceFile.writeText(source)
        val outputDirectory = temporaryFolder.newFolder("classes${temporaryFolder.root.list().orEmpty().size}")
        val diagnostics = ByteArrayOutputStream()
        val exitCode = PrintStream(diagnostics).use { stream ->
            K2JVMCompiler().exec(
                stream,
                "-classpath",
                compilationClasspath(),
                "-d",
                outputDirectory.absolutePath,
                "-jvm-target",
                "11",
                "-language-version",
                "2.0",
                "-no-stdlib",
                "-no-reflect",
                sourceFile.absolutePath,
            )
        }
        return CompilationResult(
            exitCode = exitCode,
            diagnostics = diagnostics.toString(Charsets.UTF_8.name()),
        )
    }

    private fun compilationClasspath(): String {
        val entries = linkedSetOf<String>()
        entries += System.getProperty("java.class.path").orEmpty()
            .split(File.pathSeparator)
            .filter { entry -> entry.isNotBlank() }
        var loader: ClassLoader? = Thread.currentThread().contextClassLoader
        while (loader != null) {
            if (loader is URLClassLoader) {
                entries += loader.urLs
                    .filter { url -> url.protocol == "file" }
                    .map { url -> File(url.toURI()).absolutePath }
            }
            loader = loader.parent
        }
        return entries.joinToString(File.pathSeparator)
    }

    private data class CompilationResult(
        val exitCode: ExitCode,
        val diagnostics: String,
    )
}
