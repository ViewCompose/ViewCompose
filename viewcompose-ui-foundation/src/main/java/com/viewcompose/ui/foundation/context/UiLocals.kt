package com.viewcompose.ui.foundation

/** Application-facing handle for a typed, thread-scoped ViewCompose local value. */
class UiLocal<T> internal constructor(
    internal val holder: LocalValue<T>,
) {
    /** Stable diagnostic name shown in composition tooling. */
    val debugName: String
        get() = holder.debugName
}

/**
 * One pending local/value binding used by ProvideLocals for batch installation.
 */
class UiLocalProvider internal constructor(
    internal val local: UiLocal<*>,
    internal val value: Any?,
)

/**
 * Creates a UiLocal with an automatic diagnostic name.
 */
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

/**
 * Creates a UiLocal with an explicit diagnostic name and optional formatter.
 */
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

/** Reads typed local values from the current thread-scoped composition context. */
object UiLocals {
    /**
     * Returns the nearest provided [local] value, or evaluates its default during declaration.
     * Presence and nullability are distinct: explicitly providing `null` for a nullable Local
     * returns `null` and never falls through to a non-null default.
     *
     * Built-in effect callback scopes do not reinstall the declaring provider stack. Reading a
     * Local from such a callback fails with its diagnostic name even if another provider happens
     * to be active on the callback thread; resolve and capture the value while declaring the effect.
     *
     * @param local typed value handle resolved from the current declaration context
     * @return nearest provided value, including an explicitly provided `null`, or the Local's
     * declaration-time default when no binding is present
     */
    fun <T> current(local: UiLocal<T>): T = LocalContext.current(local.holder)
}

/**
 * Builds one local/value binding.
 */
infix fun <T> UiLocal<T>.provides(value: T): UiLocalProvider {
    return UiLocalProvider(local = this, value = value)
}

/**
 * Provides one UiLocal within the content scope.
 */
fun <T> UiTreeBuilder.ProvideLocal(
    local: UiLocal<T>,
    value: T,
    content: UiTreeBuilder.() -> Unit,
) {
    LocalContext.provide(local.holder, value) {
        content()
    }
}

/**
 * Provides multiple UiLocals in order within the content scope.
 */
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
