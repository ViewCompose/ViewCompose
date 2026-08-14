package com.viewcompose.ui.foundation

import com.viewcompose.runtime.composition.CompositionLocalDiagnostic
import java.util.concurrent.atomic.AtomicLong

/**
 * Internal storage descriptor for one Composition Local.
 */
internal class LocalValue<T>(
    val debugName: String = nextLocalDebugName(),
    private val debugValueFormatter: ((T) -> String)? = null,
    private val defaultFactory: () -> T,
) {
    fun default(): T = defaultFactory()

    /**
     * Converts a local value into diagnostics without exposing the original object.
     */
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

/**
 * Immutable snapshot of the current thread-local local map.
 */
internal data class LocalSnapshot(
    val values: Map<LocalValue<*>, Any?>,
)

/**
 * Opaque snapshot of every active ViewCompose local.
 *
 * Delayed child sessions capture this while their content is declared and restore it whenever
 * that content is rendered. The values stay opaque so session containers cannot inspect or mutate
 * another composition's local map.
 */
class UiLocalSnapshot internal constructor(
    internal val delegate: LocalSnapshot,
)

/**
 * Captures currently active ViewCompose locals.
 */
fun captureUiLocalSnapshot(): UiLocalSnapshot {
    return UiLocalSnapshot(LocalContext.snapshot())
}

/**
 * Runs block under the specified locals snapshot.
 */
fun <T> withUiLocalSnapshot(
    snapshot: UiLocalSnapshot,
    block: () -> T,
): T {
    return LocalContext.withSnapshot(snapshot.delegate, block)
}

/**
 * Thread-local runtime for Composition Locals.
 */
internal object LocalContext {
    private val currentValues = ThreadLocal<Map<LocalValue<*>, Any?>>()

    fun <T> current(local: LocalValue<T>): T {
        if (CompositionEffectContext.isActive()) {
            error(
                "UiLocal '${local.debugName}' was read from a composition effect after its " +
                    "declaration context ended. Resolve the value before declaring the effect " +
                    "and capture it in the callback.",
            )
        }
        val values = currentValues.get().orEmpty()
        @Suppress("UNCHECKED_CAST")
        return if (values.containsKey(local)) {
            values[local] as T
        } else {
            local.default()
        }
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

    fun <T> withSnapshot(
        snapshot: LocalSnapshot,
        block: () -> T,
    ): T {
        val previous = currentValues.get().orEmpty()
        currentValues.set(snapshot.values)
        return try {
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
