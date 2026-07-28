package com.viewcompose.widget.core

import com.viewcompose.runtime.composition.CompositionLocalDiagnostic
import java.util.concurrent.atomic.AtomicLong

/**
 * 单个 Composition Local 的内部存储描述。
 * Internal storage descriptor for one Composition Local.
 */
internal class LocalValue<T>(
    val debugName: String = nextLocalDebugName(),
    private val debugValueFormatter: ((T) -> String)? = null,
    private val defaultFactory: () -> T,
) {
    fun default(): T = defaultFactory()

    /**
     * 将 local 值转成诊断信息，避免 diagnostics 暴露真实对象。
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
 * 当前线程 local map 的不可变快照。
 * Immutable snapshot of the current thread-local local map.
 */
internal data class LocalSnapshot(
    val values: Map<LocalValue<*>, Any?>,
)

/**
 * 当前所有 ViewCompose local 的不透明快照。
 * Opaque snapshot of every active ViewCompose local.
 *
 * 延迟子 session 在声明 content 时捕获该快照，并在后续渲染 content 时恢复。
 * 值保持不透明，防止 session 容器检查或修改另一个 composition 的 local map。
 * Delayed child sessions capture this while their content is declared and restore it whenever
 * that content is rendered. The values stay opaque so session containers cannot inspect or mutate
 * another composition's local map.
 */
class UiLocalSnapshot internal constructor(
    internal val delegate: LocalSnapshot,
)

/**
 * 捕获当前活跃 ViewCompose locals。
 * Captures currently active ViewCompose locals.
 */
fun captureUiLocalSnapshot(): UiLocalSnapshot {
    return UiLocalSnapshot(LocalContext.snapshot())
}

/**
 * 在指定 locals 快照下执行 block。
 * Runs block under the specified locals snapshot.
 */
fun <T> withUiLocalSnapshot(
    snapshot: UiLocalSnapshot,
    block: () -> T,
): T {
    return LocalContext.withSnapshot(snapshot.delegate, block)
}

/**
 * Composition Local 的线程局部运行时。
 * Thread-local runtime for Composition Locals.
 */
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
