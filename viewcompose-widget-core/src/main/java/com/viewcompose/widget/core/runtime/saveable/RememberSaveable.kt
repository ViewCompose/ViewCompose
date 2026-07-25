package com.viewcompose.widget.core

/**
 * Remembers [init]'s result and saves it through the current host registry.
 *
 * Primitive, collection, Android Bundle-compatible, and framework [com.viewcompose.runtime.MutableState]
 * values are supported by the default saver. Use the [Saver] overload for domain objects.
 */
fun <T> rememberSaveable(
    vararg inputs: Any?,
    key: String? = null,
    init: () -> T,
): T {
    return rememberSaveable(
        *inputs,
        key = key,
        saver = autoSaver(),
        init = init,
    )
}

fun <T, Saveable> rememberSaveable(
    vararg inputs: Any?,
    key: String? = null,
    saver: Saver<T, Saveable>,
    init: () -> T,
): T {
    val registry = LocalSaveableStateRegistry.current
    if (registry == null) {
        return remember(*inputs, calculation = init)
    }
    val resolvedKey = resolveSaveableKey(key)
    val value = remember(
        registry,
        resolvedKey,
        *inputs,
    ) {
        restoreOrInitialize(
            registry = registry,
            key = resolvedKey,
            saver = saver,
            init = init,
        )
    }
    val holder = remember(registry, resolvedKey) {
        SaveableHolder(
            registry = registry,
            key = resolvedKey,
        )
    }
    holder.update(
        value = value,
        saver = saver,
    )
    DisposableEffect(registry, resolvedKey) {
        val entry = holder.register()
        return@DisposableEffect entry::unregister
    }
    return value
}

private fun resolveSaveableKey(explicitKey: String?): String {
    if (explicitKey != null) {
        require(explicitKey.isNotBlank()) {
            "rememberSaveable key must not be blank."
        }
        return "user:$explicitKey"
    }
    val composer = checkNotNull(ComposerContext.currentComposer()) {
        "rememberSaveable without an explicit key requires an active composition."
    }
    return composer.nextSaveableKey()
}

private fun <T, Saveable> restoreOrInitialize(
    registry: SaveableStateRegistry,
    key: String,
    saver: Saver<T, Saveable>,
    init: () -> T,
): T {
    val restored = registry.consumeRestored(key) ?: return init()
    return try {
        @Suppress("UNCHECKED_CAST")
        saver.restore(restored.value as Saveable)
    } catch (error: Throwable) {
        throw IllegalStateException(
            "Failed to restore rememberSaveable value for key '$key'.",
            error,
        )
    }
}

private class SaveableHolder(
    private val registry: SaveableStateRegistry,
    private val key: String,
) {
    private var value: Any? = null
    private var saver: Saver<Any?, Any?> = autoSaver()

    fun <T, Saveable> update(
        value: T,
        saver: Saver<T, Saveable>,
    ) {
        this.value = value
        @Suppress("UNCHECKED_CAST")
        this.saver = saver as Saver<Any?, Any?>
    }

    fun register(): SaveableStateRegistry.Entry {
        return registry.registerProvider(key) {
            val saved = saver.save(value)
            require(registry.canBeSaved(saved)) {
                val type = saved?.let { it::class.java.name } ?: "null"
                "rememberSaveable value for key '$key' cannot be saved: $type. " +
                    "Provide a Saver that converts it to supported values."
            }
            saved
        }
    }
}
