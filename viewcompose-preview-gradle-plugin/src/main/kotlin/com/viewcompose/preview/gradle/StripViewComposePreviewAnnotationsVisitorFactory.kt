package com.viewcompose.preview.gradle

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/** Removes preview-only annotations from non-debuggable Android variants before DEX/AAR output. */
abstract class StripViewComposePreviewAnnotationsVisitorFactory :
    AsmClassVisitorFactory<InstrumentationParameters.None> {
    /**
     * Returns `true` for every project class so direct and composed preview annotations can be removed.
     *
     * The visitor preserves bytecode and all unrelated annotations.
     */
    override fun isInstrumentable(classData: ClassData): Boolean = true

    /**
     * Creates a visitor that removes root preview annotations and recursively classified
     * meta-annotations from classes and methods.
     */
    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor,
    ): ClassVisitor {
        val classifier = PreviewAnnotationClassifier(classContext::loadClassData)
        return StripPreviewAnnotationsClassVisitor(
            nextClassVisitor = nextClassVisitor,
            isPreviewAnnotation = classifier::isPreviewAnnotationDescriptor,
        )
    }
}

internal class PreviewAnnotationClassifier(
    private val loadClassData: (String) -> ClassData?,
) {
    private val cache = mutableMapOf<String, Boolean>()

    fun isPreviewAnnotationDescriptor(descriptor: String): Boolean {
        val className = runCatching { Type.getType(descriptor).className }.getOrNull()
            ?: return false
        return isPreviewAnnotationClass(className, linkedSetOf())
    }

    private fun isPreviewAnnotationClass(
        className: String,
        visiting: MutableSet<String>,
    ): Boolean {
        cache[className]?.let { return it }
        if (className in ROOT_PREVIEW_ANNOTATIONS) {
            cache[className] = true
            return true
        }
        if (!visiting.add(className)) return false
        val result = loadClassData(className)
            ?.classAnnotations
            .orEmpty()
            .any { annotationName ->
                isPreviewAnnotationClass(annotationName, visiting)
            }
        visiting.remove(className)
        cache[className] = result
        return result
    }
}

internal class StripPreviewAnnotationsClassVisitor(
    nextClassVisitor: ClassVisitor,
    private val isPreviewAnnotation: (String) -> Boolean,
) : ClassVisitor(Opcodes.ASM9, nextClassVisitor) {
    override fun visitAnnotation(
        descriptor: String,
        visible: Boolean,
    ): AnnotationVisitor? {
        return if (isPreviewAnnotation(descriptor)) {
            null
        } else {
            super.visitAnnotation(descriptor, visible)
        }
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?,
    ): MethodVisitor? {
        val next = super.visitMethod(access, name, descriptor, signature, exceptions) ?: return null
        return object : MethodVisitor(Opcodes.ASM9, next) {
            override fun visitAnnotation(
                descriptor: String,
                visible: Boolean,
            ): AnnotationVisitor? {
                return if (isPreviewAnnotation(descriptor)) {
                    null
                } else {
                    super.visitAnnotation(descriptor, visible)
                }
            }
        }
    }
}

private val ROOT_PREVIEW_ANNOTATIONS = setOf(
    "com.viewcompose.preview.tooling.ViewComposePreview",
    "com.viewcompose.preview.tooling.ViewComposePreviews",
    "com.viewcompose.preview.tooling.ViewComposePreviewThemeProvider",
)
