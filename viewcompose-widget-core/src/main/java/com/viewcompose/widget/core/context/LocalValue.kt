package com.viewcompose.widget.core

import com.viewcompose.runtime.composition.CompositionLocalDiagnostic
import java.util.concurrent.atomic.AtomicLong

internal class LocalValue<T>(
    val debugName: String = nextLocalDebugName(),
    private val debugValueFormatter: ((T) -> String)? = null,
    private val defaultFactory: () -> T,
) {
    fun default(): T = defaultFactory()

    fun describe(value: Any?): CompositionLocalDiagnostic {
        val formatter = debugValueFormatter
        @Suppress("UNCHECKED_CAST")
        val formatted = if (formatter == null) {
            value.toDiagnosticString()
        } else {
            runCatching { formatter(value as T) }
                .getOrElse { error -> "<formatter failed: ${error::class.simpleName}>" }
        }
        return CompositionLocalDiagnostic(
            name = debugName,
            value = formatted.take(MAX_DEBUG_VALUE_LENGTH),
        )
    }
}

internal data class LocalSnapshot(
    val values: Map<LocalValue<*>, Any?>,
)

internal object LocalContext {
    private val currentValues = ThreadLocal<Map<LocalValue<*>, Any?>>()

    fun <T> current(local: LocalValue<T>): T {
        val values = currentValues.get().orEmpty()
        @Suppress("UNCHECKED_CAST")
        return values[local] as? T ?: local.default()
    }

    fun <T> provide(
        local: LocalValue<T>,
        value: T,
        block: () -> Unit,
    ) {
        val previous = currentValues.get().orEmpty()
        currentValues.set(previous + (local to value))
        try {
            block()
        } finally {
            currentValues.set(previous)
        }
    }

    fun snapshot(): LocalSnapshot {
        return LocalSnapshot(
            values = currentValues.get().orEmpty(),
        )
    }

    fun describeSnapshot(snapshot: Any?): List<CompositionLocalDiagnostic> {
        val localSnapshot = snapshot as? LocalSnapshot ?: return emptyList()
        return localSnapshot.values
            .map { (local, value) -> local.describe(value) }
            .sortedBy(CompositionLocalDiagnostic::name)
    }

    fun withSnapshot(
        snapshot: LocalSnapshot,
        block: () -> Unit,
    ) {
        val previous = currentValues.get().orEmpty()
        currentValues.set(snapshot.values)
        try {
            block()
        } finally {
            currentValues.set(previous)
        }
    }
}

internal fun nextLocalDebugName(): String = "UiLocal#${nextLocalId.incrementAndGet()}"

private val nextLocalId = AtomicLong(0)

private const val MAX_DEBUG_VALUE_LENGTH = 160

private fun Any?.toDiagnosticString(): String {
    return when (this) {
        null -> "null"
        is String -> this
        is Number,
        is Boolean,
        is Char,
        is Enum<*>,
        -> toString()
        else -> this::class.qualifiedName ?: this::class.simpleName ?: "<anonymous>"
    }
}
