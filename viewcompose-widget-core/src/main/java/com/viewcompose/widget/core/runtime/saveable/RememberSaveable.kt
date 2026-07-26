package com.viewcompose.widget.core

import com.viewcompose.runtime.composition.RememberObserver

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
    val holder = remember(
        registry,
        resolvedKey,
        *inputs,
    ) {
        createSaveableHolder(
            registry = registry,
            key = resolvedKey,
            saver = saver,
            init = init,
        )
    }
    SideEffect {
        holder.update(saver)
    }
    return holder.value
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

private fun <T, Saveable> createSaveableHolder(
    registry: SaveableStateRegistry,
    key: String,
    saver: Saver<T, Saveable>,
    init: () -> T,
): SaveableHolder<T> {
    val claim = registry.claimRestored(key)
    val value = if (claim == null) {
        init()
    } else {
        try {
            @Suppress("UNCHECKED_CAST")
            saver.restore(claim.value as Saveable)
        } catch (error: Throwable) {
            claim.release()
            throw IllegalStateException(
                "Failed to restore rememberSaveable value for key '$key'.",
                error,
            )
        }
    }
    @Suppress("UNCHECKED_CAST")
    return SaveableHolder(
        registry = registry,
        key = key,
        value = value,
        saver = saver as Saver<T, Any?>,
        restoredClaim = claim,
    )
}

private class SaveableHolder<T>(
    private val registry: SaveableStateRegistry,
    private val key: String,
    val value: T,
    private var saver: Saver<T, Any?>,
    private var restoredClaim: RestoredSaveableValue?,
) : RememberObserver {
    private var entry: SaveableStateRegistry.Entry? = null

    fun <Saveable> update(saver: Saver<T, Saveable>) {
        @Suppress("UNCHECKED_CAST")
        this.saver = saver as Saver<T, Any?>
    }

    override fun onRemembered() {
        check(entry == null) {
            "rememberSaveable holder for key '$key' is already registered."
        }
        val nextEntry = registry.registerProvider(key) {
            val saved = saver.save(value)
            require(registry.canBeSaved(saved)) {
                val type = saved?.let { it::class.java.name } ?: "null"
                "rememberSaveable value for key '$key' cannot be saved: $type. " +
                    "Provide a Saver that converts it to supported values."
            }
            saved
        }
        entry = nextEntry
        restoredClaim?.commit()
        restoredClaim = null
    }

    override fun onForgotten() {
        entry?.unregister()
        entry = null
        releaseRestoredClaim()
    }

    override fun onAbandoned() {
        entry?.unregister()
        entry = null
        releaseRestoredClaim()
    }

    private fun releaseRestoredClaim() {
        restoredClaim?.release()
        restoredClaim = null
    }
}
