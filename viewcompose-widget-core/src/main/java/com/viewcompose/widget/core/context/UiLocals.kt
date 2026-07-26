package com.viewcompose.widget.core

class UiLocal<T> internal constructor(
    internal val holder: LocalValue<T>,
) {
    val debugName: String
        get() = holder.debugName
}

class UiLocalProvider internal constructor(
    internal val local: UiLocal<*>,
    internal val value: Any?,
)

fun <T> uiLocalOf(
    defaultFactory: () -> T,
): UiLocal<T> {
    return UiLocal(
        LocalValue(
            debugName = nextLocalDebugName(),
            defaultFactory = defaultFactory,
        ),
    )
}

fun <T> uiLocalOf(
    debugName: String,
    debugValueFormatter: ((T) -> String)? = null,
    defaultFactory: () -> T,
): UiLocal<T> {
    require(debugName.isNotBlank()) { "UiLocal debugName must not be blank." }
    return UiLocal(
        LocalValue(
            debugName = debugName,
            defaultFactory = defaultFactory,
            debugValueFormatter = debugValueFormatter,
        ),
    )
}

object UiLocals {
    fun <T> current(local: UiLocal<T>): T = LocalContext.current(local.holder)
}

infix fun <T> UiLocal<T>.provides(value: T): UiLocalProvider {
    return UiLocalProvider(local = this, value = value)
}

fun <T> UiTreeBuilder.ProvideLocal(
    local: UiLocal<T>,
    value: T,
    content: UiTreeBuilder.() -> Unit,
) {
    LocalContext.provide(local.holder, value) {
        content()
    }
}

fun UiTreeBuilder.ProvideLocals(
    vararg values: UiLocalProvider,
    content: UiTreeBuilder.() -> Unit,
) {
    provideLocalsRecursively(values = values, index = 0, content = content)
}

private fun UiTreeBuilder.provideLocalsRecursively(
    values: Array<out UiLocalProvider>,
    index: Int,
    content: UiTreeBuilder.() -> Unit,
) {
    if (index >= values.size) {
        content()
        return
    }
    val entry = values[index]
    @Suppress("UNCHECKED_CAST")
    val local = entry.local as UiLocal<Any?>
    LocalContext.provide(local.holder, entry.value) {
        provideLocalsRecursively(values = values, index = index + 1, content = content)
    }
}
