package com.viewcompose.widget.core

/**
 * 业务可见的 Composition Local 句柄，隐藏内部 LocalValue 存储。
 * App-facing Composition Local handle that hides the internal LocalValue storage.
 */
class UiLocal<T> internal constructor(
    internal val holder: LocalValue<T>,
) {
    val debugName: String
        get() = holder.debugName
}

/**
 * 一条待提供的 local/value 绑定，用于 ProvideLocals 批量安装。
 * One pending local/value binding used by ProvideLocals for batch installation.
 */
class UiLocalProvider internal constructor(
    internal val local: UiLocal<*>,
    internal val value: Any?,
)

/**
 * 创建带自动诊断名的 UiLocal。
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
 * 创建带显式诊断名和可选格式化器的 UiLocal。
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

/**
 * 读取当前 composition 中的 local 值。
 * Reads a local value from the current composition.
 */
object UiLocals {
    fun <T> current(local: UiLocal<T>): T = LocalContext.current(local.holder)
}

/**
 * 构建一条 local/value 绑定。
 * Builds one local/value binding.
 */
infix fun <T> UiLocal<T>.provides(value: T): UiLocalProvider {
    return UiLocalProvider(local = this, value = value)
}

/**
 * 在 content 范围内提供单个 UiLocal。
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
 * 在 content 范围内按顺序提供多个 UiLocal。
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
