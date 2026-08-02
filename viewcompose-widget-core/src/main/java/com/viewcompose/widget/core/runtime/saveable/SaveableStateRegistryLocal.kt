package com.viewcompose.widget.core

private val LocalSaveableStateRegistryValue = uiLocalOf<SaveableStateRegistry?>(
    debugName = "SaveableStateRegistry",
    debugValueFormatter = { registry -> if (registry == null) "none" else "installed" },
) { null }

/** Exposes the saveable-state registry installed for the current composition. */
object LocalSaveableStateRegistry {
    /** Returns the nearest registry, or `null` when the host does not provide saveable state. */
    val current: SaveableStateRegistry?
        get() = UiLocals.current(LocalSaveableStateRegistryValue)
}

/** Provides [registry] to saveable-state APIs while [content] is built. */
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
