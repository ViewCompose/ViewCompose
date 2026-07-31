package com.viewcompose.preview.gradle

import com.viewcompose.preview.tooling.PreviewLayoutDirection
import com.viewcompose.preview.tooling.PreviewLightDark
import com.viewcompose.preview.tooling.PreviewTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes

class CompiledPreviewScannerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `discovers direct and dependency meta previews without loading classes`() {
        val classes = temporaryFolder.newFolder("classes")
        val sources = temporaryFolder.newFolder("sources")
        val sourceFile = sources.resolve("unconventional/path/PreviewSamples.kt").apply {
            parentFile.mkdirs()
            writeText(
                "package sample\n" +
                    "\n".repeat(38) +
                    "fun sample() = Unit\nfun themes() = Unit\n",
            )
        }
        classes.writeClass(
            "sample/PreviewSamplesKt",
            previewClass(
                methods = listOf(
                    PreviewMethodFixture(
                        name = "sample",
                        line = 40,
                        annotations = listOf(
                            AnnotationFixture.Direct(
                                name = "Phone dark",
                                group = "catalog",
                                widthDp = 360,
                                heightDp = 720,
                                theme = PreviewTheme.Dark,
                            ),
                        ),
                    ),
                    PreviewMethodFixture(
                        name = "themes",
                        line = 41,
                        annotations = listOf(
                            AnnotationFixture.Marker(
                                descriptor =
                                    "Lcom/viewcompose/preview/tooling/PreviewLightDark;",
                            ),
                        ),
                    ),
                ),
            ),
        )

        val output = CompiledPreviewScanner(
            projectClassDirectories = listOf(classes),
            projectClassJars = emptyList(),
            annotationClasspath = listOf(
                File(
                    PreviewLightDark::class.java.protectionDomain.codeSource.location.toURI(),
                ),
            ),
            sourceDirectories = listOf(sources),
        ).scan()

        assertTrue(output.diagnostics.isEmpty())
        assertEquals(2, output.descriptors.size)
        val sample = output.descriptors.single { descriptor ->
            descriptor.entryPoint.methodName == "sample"
        }
        assertEquals("catalog", sample.group)
        assertEquals(360, sample.variants.single().configuration.widthDp)
        assertEquals(720, sample.variants.single().configuration.heightDp)
        assertEquals(PreviewTheme.Dark, sample.variants.single().configuration.theme)
        assertEquals(40, sample.sourceLocation?.line)
        assertEquals(
            sourceFile.absolutePath,
            sample.sourceLocation?.filePath,
        )

        val themes = output.descriptors.single { descriptor ->
            descriptor.entryPoint.methodName == "themes"
        }
        assertEquals(
            listOf(PreviewTheme.Light, PreviewTheme.Dark),
            themes.variants.map { variant -> variant.configuration.theme },
        )
    }

    @Test
    fun `custom meta preview and invalid entry point produce stable discovery output`() {
        val classes = temporaryFolder.newFolder("custom-classes")
        classes.writeClass(
            "sample/CustomPreviews",
            customMetaPreviewAnnotation(),
        )
        classes.writeClass(
            "sample/InvalidPreviewKt",
            previewClass(
                methods = listOf(
                    PreviewMethodFixture(
                        name = "invalid",
                        descriptor = "()V",
                        annotations = listOf(
                            AnnotationFixture.Marker("Lsample/CustomPreviews;"),
                        ),
                    ),
                ),
            ),
        )

        val output = CompiledPreviewScanner(
            projectClassDirectories = listOf(classes),
            projectClassJars = emptyList(),
            annotationClasspath = emptyList(),
            sourceDirectories = emptyList(),
        ).scan()

        assertTrue(output.descriptors.isEmpty())
        assertEquals(1, output.diagnostics.size)
        assertEquals("discovery", output.diagnostics.single().phase)
        assertTrue(output.diagnostics.single().details.orEmpty().contains("()V"))
    }

    private fun previewClass(methods: List<PreviewMethodFixture>): ByteArray {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        writer.visit(
            Opcodes.V11,
            Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL,
            "sample/PreviewSamplesKt",
            null,
            "java/lang/Object",
            null,
        )
        writer.visitSource("PreviewSamples.kt", null)
        methods.forEach { fixture ->
            val method = writer.visitMethod(
                fixture.access,
                fixture.name,
                fixture.descriptor,
                null,
                null,
            )
            fixture.annotations.forEach { annotation ->
                when (annotation) {
                    is AnnotationFixture.Direct -> {
                        val visitor = method.visitAnnotation(PREVIEW_DESCRIPTOR, true)
                        visitor.visit("name", annotation.name)
                        visitor.visit("group", annotation.group)
                        visitor.visit("widthDp", annotation.widthDp)
                        visitor.visit("heightDp", annotation.heightDp)
                        visitor.visitEnum(
                            "layoutDirection",
                            LAYOUT_DIRECTION_DESCRIPTOR,
                            annotation.layoutDirection.name,
                        )
                        visitor.visitEnum("theme", THEME_DESCRIPTOR, annotation.theme.name)
                        visitor.visitEnd()
                    }

                    is AnnotationFixture.Marker -> {
                        method.visitAnnotation(annotation.descriptor, true).visitEnd()
                    }
                }
            }
            method.visitCode()
            val label = Label()
            method.visitLabel(label)
            method.visitLineNumber(fixture.line, label)
            method.visitInsn(Opcodes.RETURN)
            method.visitMaxs(0, 0)
            method.visitEnd()
        }
        writer.visitEnd()
        return writer.toByteArray()
    }

    private fun customMetaPreviewAnnotation(): ByteArray {
        val writer = ClassWriter(0)
        writer.visit(
            Opcodes.V11,
            Opcodes.ACC_PUBLIC or
                Opcodes.ACC_ABSTRACT or
                Opcodes.ACC_INTERFACE or
                Opcodes.ACC_ANNOTATION,
            "sample/CustomPreviews",
            null,
            "java/lang/Object",
            arrayOf("java/lang/annotation/Annotation"),
        )
        val container = writer.visitAnnotation(PREVIEWS_DESCRIPTOR, true)
        val variants = container.visitArray("value")
        variants.visitAnnotation(null, PREVIEW_DESCRIPTOR).apply {
            visit("name", "LTR")
            visitEnum("layoutDirection", LAYOUT_DIRECTION_DESCRIPTOR, "Ltr")
            visitEnd()
        }
        variants.visitAnnotation(null, PREVIEW_DESCRIPTOR).apply {
            visit("name", "RTL")
            visitEnum("layoutDirection", LAYOUT_DIRECTION_DESCRIPTOR, "Rtl")
            visitEnd()
        }
        variants.visitEnd()
        container.visitEnd()
        writer.visitEnd()
        return writer.toByteArray()
    }

    private fun File.writeClass(
        internalName: String,
        bytes: ByteArray,
    ) {
        resolve("$internalName.class").apply {
            parentFile.mkdirs()
            writeBytes(bytes)
        }
    }

    private data class PreviewMethodFixture(
        val name: String,
        val descriptor: String = ENTRY_POINT_DESCRIPTOR,
        val access: Int = Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
        val line: Int = 1,
        val annotations: List<AnnotationFixture>,
    )

    private sealed interface AnnotationFixture {
        data class Direct(
            val name: String,
            val group: String = "",
            val widthDp: Int = 411,
            val heightDp: Int = 891,
            val layoutDirection: PreviewLayoutDirection = PreviewLayoutDirection.Ltr,
            val theme: PreviewTheme = PreviewTheme.Light,
        ) : AnnotationFixture

        data class Marker(
            val descriptor: String,
        ) : AnnotationFixture
    }

    private companion object {
        const val PREVIEW_DESCRIPTOR =
            "Lcom/viewcompose/preview/tooling/ViewComposePreview;"
        const val PREVIEWS_DESCRIPTOR =
            "Lcom/viewcompose/preview/tooling/ViewComposePreviews;"
        const val LAYOUT_DIRECTION_DESCRIPTOR =
            "Lcom/viewcompose/preview/tooling/PreviewLayoutDirection;"
        const val THEME_DESCRIPTOR =
            "Lcom/viewcompose/preview/tooling/PreviewTheme;"
        const val ENTRY_POINT_DESCRIPTOR =
            "(Lcom/viewcompose/widget/core/UiTreeBuilder;)V"
    }
}
