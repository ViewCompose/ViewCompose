package com.viewcompose.widget.core

private val LocalSaveableStateRegistryValue = uiLocalOf<SaveableStateRegistry?> { null }

object LocalSaveableStateRegistry {
    val current: SaveableStateRegistry?
        get() = UiLocals.current(LocalSaveableStateRegistryValue)
}

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
