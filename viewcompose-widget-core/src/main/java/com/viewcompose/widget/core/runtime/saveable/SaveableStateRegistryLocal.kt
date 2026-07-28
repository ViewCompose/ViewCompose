package com.viewcompose.widget.core

private val LocalSaveableStateRegistryValue = uiLocalOf<SaveableStateRegistry?>(
    debugName = "SaveableStateRegistry",
    debugValueFormatter = { registry -> if (registry == null) "none" else "installed" },
) { null }

/**
 * 当前 composition 的可保存状态注册表。
 * Saveable-state registry for the current composition.
 */
object LocalSaveableStateRegistry {
    val current: SaveableStateRegistry?
        get() = UiLocals.current(LocalSaveableStateRegistryValue)
}

/**
 * 在 content 范围内安装可保存状态注册表。
 * Installs a saveable-state registry within the content scope.
 */
fun UiTreeBuilder.ProvideSaveableStateRegistry(
    registry: SaveableStateRegistry,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(
        local = LocalSaveableStateRegistryValue,
        value = registry,
        content = content,
    )
}
