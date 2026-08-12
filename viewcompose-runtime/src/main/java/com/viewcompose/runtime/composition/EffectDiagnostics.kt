package com.viewcompose.runtime.composition

/** Bounded diagnostic identity captured when a synchronous composition effect is declared. */
internal data class EffectDiagnostic(
    val kind: String,
    val scopePath: String,
    val slot: Int,
    val keySummary: String,
) {
    fun describe(
        operation: String,
        frameId: Long?,
    ): String =
        "effect=$kind operation=$operation scope=$scopePath slot=$slot keys=$keySummary " +
            "frame=${frameId ?: "unknown"}"
}

/** Adds bounded effect identity to a user failure without changing its type or propagation. */
internal class EffectOperationDiagnosticException(
    message: String,
) : RuntimeException(message) {
    override fun fillInStackTrace(): Throwable = this
}

/** Runs one synchronous callback, preserving the original failure and optionally warning on cost. */
internal fun runSynchronousEffectOperation(
    diagnostic: EffectDiagnostic,
    operation: String,
    warningLogger: ((String) -> Unit)?,
    warningThresholdNanos: Long?,
    frameIdProvider: (() -> Long?)?,
    block: () -> Unit,
) {
    val startedAt = if (warningThresholdNanos == null) 0L else System.nanoTime()
    try {
        block()
    } catch (error: Throwable) {
        error.addSuppressed(
            EffectOperationDiagnosticException(
                diagnostic.describe(operation, frameIdProvider.currentFrameId()),
            ),
        )
        throw error
    } finally {
        if (warningThresholdNanos != null) {
            val durationNanos = System.nanoTime() - startedAt
            if (durationNanos >= warningThresholdNanos) {
                runCatching {
                    warningLogger?.invoke(
                        "Slow synchronous composition effect: " +
                            "${diagnostic.describe(operation, frameIdProvider.currentFrameId())} " +
                            "durationNanos=$durationNanos thresholdNanos=$warningThresholdNanos",
                    )
                }
            }
        }
    }
}

private fun (() -> Long?)?.currentFrameId(): Long? =
    runCatching { this?.invoke() }.getOrNull()

/** Uses only stable primitive text and class names so diagnostics never retain arbitrary key data. */
internal fun List<Any?>.toEffectKeySummary(): String {
    if (isEmpty()) return "[]"
    val visible = take(MaxDiagnosticKeys).joinToString(",") { key ->
        when (key) {
            null -> "null"
            is String -> "String(${key.take(MaxDiagnosticStringLength)})"
            is Number,
            is Boolean,
            is Char,
            is Enum<*>,
            -> key.toString().take(MaxDiagnosticStringLength)
            else -> key::class.qualifiedName ?: key::class.simpleName ?: "<anonymous>"
        }
    }
    return if (size > MaxDiagnosticKeys) "[$visible,+${size - MaxDiagnosticKeys}]" else "[$visible]"
}

internal fun Any?.rememberDiagnosticKind(): String {
    if (this !is RememberObserver) return "Remember"
    return this::class.simpleName
        ?.removeSuffix("Observer")
        ?.takeIf(String::isNotBlank)
        ?: "RememberObserver"
}

private const val MaxDiagnosticKeys = 4
private const val MaxDiagnosticStringLength = 40
