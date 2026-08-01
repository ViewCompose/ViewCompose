package com.viewcompose.preview.gradle

import com.android.build.api.instrumentation.ClassData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes

class StripViewComposePreviewAnnotationsVisitorFactoryTest {
    @Test
    fun `classifier resolves direct and nested preview meta annotations`() {
        val classes = mapOf(
            "sample.CustomPreview" to FakeClassData(
                className = "sample.CustomPreview",
                classAnnotations = listOf(VIEWCOMPOSE_PREVIEW_CLASS_NAME),
            ),
            "sample.NestedPreview" to FakeClassData(
                className = "sample.NestedPreview",
                classAnnotations = listOf("sample.CustomPreview"),
            ),
            "sample.Unrelated" to FakeClassData(
                className = "sample.Unrelated",
                classAnnotations = listOf("java.lang.annotation.Retention"),
            ),
        )
        val classifier = PreviewAnnotationClassifier(classes::get)

        assertTrue(classifier.isPreviewAnnotationDescriptor(VIEWCOMPOSE_PREVIEW_DESCRIPTOR))
        assertTrue(classifier.isPreviewAnnotationDescriptor("Lsample/CustomPreview;"))
        assertTrue(classifier.isPreviewAnnotationDescriptor("Lsample/NestedPreview;"))
        assertFalse(classifier.isPreviewAnnotationDescriptor("Lsample/Unrelated;"))
    }

    @Test
    fun `visitor removes preview annotations while preserving unrelated annotations`() {
        val original = annotatedClass(
            methodAnnotations = listOf(
                VIEWCOMPOSE_PREVIEW_DESCRIPTOR,
                "Lsample/CustomPreview;",
                "Ljava/lang/Deprecated;",
            ),
        )
        val classifier = PreviewAnnotationClassifier { className ->
            if (className == "sample.CustomPreview") {
                FakeClassData(
                    className = className,
                    classAnnotations = listOf(VIEWCOMPOSE_PREVIEW_CLASS_NAME),
                )
            } else {
                null
            }
        }
        val writer = ClassWriter(0)
        ClassReader(original).accept(
            StripPreviewAnnotationsClassVisitor(
                nextClassVisitor = writer,
                isPreviewAnnotation = classifier::isPreviewAnnotationDescriptor,
            ),
            0,
        )

        assertEquals(
            listOf("Ljava/lang/Deprecated;"),
            methodAnnotations(writer.toByteArray()),
        )
    }

    private fun annotatedClass(methodAnnotations: List<String>): ByteArray {
        return ClassWriter(0).apply {
            visit(
                Opcodes.V11,
                Opcodes.ACC_PUBLIC,
                "sample/PreviewEntry",
                null,
                "java/lang/Object",
                null,
            )
            visitMethod(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "preview",
                "()V",
                null,
                null,
            ).apply {
                methodAnnotations.forEach { descriptor ->
                    visitAnnotation(descriptor, false).visitEnd()
                }
                visitCode()
                visitInsn(Opcodes.RETURN)
                visitMaxs(0, 0)
                visitEnd()
            }
            visitEnd()
        }.toByteArray()
    }

    private fun methodAnnotations(bytes: ByteArray): List<String> {
        val annotations = mutableListOf<String>()
        ClassReader(bytes).accept(
            object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ) = object : org.objectweb.asm.MethodVisitor(Opcodes.ASM9) {
                    override fun visitAnnotation(
                        descriptor: String,
                        visible: Boolean,
                    ): AnnotationVisitor? {
                        annotations += descriptor
                        return null
                    }
                }
            },
            0,
        )
        return annotations
    }
}

private data class FakeClassData(
    override val className: String,
    override val classAnnotations: List<String> = emptyList(),
    override val interfaces: List<String> = emptyList(),
    override val superClasses: List<String> = emptyList(),
) : ClassData

private const val VIEWCOMPOSE_PREVIEW_CLASS_NAME =
    "com.viewcompose.preview.tooling.ViewComposePreview"
private const val VIEWCOMPOSE_PREVIEW_DESCRIPTOR =
    "Lcom/viewcompose/preview/tooling/ViewComposePreview;"
