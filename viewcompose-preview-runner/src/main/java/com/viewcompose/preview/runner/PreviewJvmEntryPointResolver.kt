package com.viewcompose.preview.runner

import com.viewcompose.preview.tooling.PreviewDescriptor
import com.viewcompose.preview.tooling.PreviewDiagnostic
import com.viewcompose.preview.tooling.PreviewDiagnosticSeverity
import com.viewcompose.widget.core.UiTreeBuilder
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Resolves the stable protocol entry-point description against an isolated worker class loader.
 */
object PreviewJvmEntryPointResolver {
    fun resolve(
        descriptor: PreviewDescriptor,
        classLoader: ClassLoader,
    ): PreviewEntryResolutionResult {
        return try {
            val owner = classLoader.loadClass(descriptor.entryPoint.ownerClassName)
            val candidates = owner.declaredMethods.filter { method ->
                method.name == descriptor.entryPoint.methodName &&
                    descriptor.entryPoint.methodDescriptor?.let { expected ->
                        method.jvmDescriptor() == expected
                    } != false
            }
            when {
                candidates.isEmpty() -> PreviewEntryResolutionResult.Failure(
                    descriptor.diagnostic(
                        message = "Could not find preview JVM entry point " +
                            "'${descriptor.entryPoint.ownerClassName}." +
                            "${descriptor.entryPoint.methodName}'.",
                        details = descriptor.entryPoint.methodDescriptor?.let { expected ->
                            "Expected JVM descriptor: $expected"
                        },
                    ),
                )

                candidates.size > 1 -> PreviewEntryResolutionResult.Failure(
                    descriptor.diagnostic(
                        message = "Preview JVM entry point is ambiguous.",
                        details = candidates.joinToString(
                            prefix = "Candidates: ",
                            transform = Method::toGenericString,
                        ),
                    ),
                )

                else -> candidates.single().toEntry(descriptor)
            }
        } catch (error: Throwable) {
            error.throwIfFatalPreviewWorkerError()
            PreviewEntryResolutionResult.Failure(
                descriptor.diagnostic(
                    message = "Failed to load preview JVM entry point.",
                    details = error.stackTraceToString(),
                ),
            )
        }
    }

    private fun Method.toEntry(descriptor: PreviewDescriptor): PreviewEntryResolutionResult {
        if (!Modifier.isStatic(modifiers)) {
            return PreviewEntryResolutionResult.Failure(
                descriptor.diagnostic("Preview entry point must be a static JVM method."),
            )
        }
        if (!Modifier.isPublic(modifiers)) {
            return PreviewEntryResolutionResult.Failure(
                descriptor.diagnostic("Preview entry point must be public."),
            )
        }
        if (!parameterTypes.contentEquals(arrayOf(UiTreeBuilder::class.java))) {
            return PreviewEntryResolutionResult.Failure(
                descriptor.diagnostic(
                    message = "Preview entry point must accept exactly one UiTreeBuilder receiver.",
                    details = "Actual signature: ${toGenericString()}",
                ),
            )
        }
        if (returnType != Void.TYPE) {
            return PreviewEntryResolutionResult.Failure(
                descriptor.diagnostic(
                    message = "Preview entry point must return Unit.",
                    details = "Actual return type: ${returnType.typeName}",
                ),
            )
        }
        return PreviewEntryResolutionResult.Success(
            entry = StaticPreviewEntry(descriptor) {
                try {
                    invoke(null, this)
                } catch (error: InvocationTargetException) {
                    throw error.targetException
                }
            },
        )
    }

    private fun Method.jvmDescriptor(): String {
        return buildString {
            append('(')
            parameterTypes.forEach { parameterType ->
                append(parameterType.jvmTypeDescriptor())
            }
            append(')')
            append(returnType.jvmTypeDescriptor())
        }
    }

    private fun PreviewDescriptor.diagnostic(
        message: String,
        details: String? = null,
    ): PreviewDiagnostic {
        return PreviewDiagnostic(
            severity = PreviewDiagnosticSeverity.Error,
            message = message,
            phase = "entry-resolution",
            sourceLocation = sourceLocation,
            details = details,
        )
    }
}

private fun Class<*>.jvmTypeDescriptor(): String {
    if (isArray) return name.replace('.', '/')
    if (!isPrimitive) return "L${name.replace('.', '/')};"
    return when (this) {
        Void.TYPE -> "V"
        java.lang.Boolean.TYPE -> "Z"
        java.lang.Byte.TYPE -> "B"
        java.lang.Character.TYPE -> "C"
        java.lang.Short.TYPE -> "S"
        java.lang.Integer.TYPE -> "I"
        java.lang.Long.TYPE -> "J"
        java.lang.Float.TYPE -> "F"
        java.lang.Double.TYPE -> "D"
        else -> error("Unknown primitive JVM type '$name'.")
    }
}

sealed interface PreviewEntryResolutionResult {
    data class Success(
        val entry: StaticPreviewEntry,
    ) : PreviewEntryResolutionResult

    data class Failure(
        val diagnostic: PreviewDiagnostic,
    ) : PreviewEntryResolutionResult
}
