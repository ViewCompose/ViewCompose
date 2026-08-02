package com.viewcompose.widget.core

import com.viewcompose.runtime.composition.RememberObserver

/**
 * Remembers [init]'s result and saves it through the current host registry.
 *
 * [inputs] reset the remembered value when they change but do not become part of its saved
 * representation. A non-null [key] must be unique among active calls in the registry. Without an
 * installed registry this behaves like [remember]. Use the [Saver] overload for domain objects.
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

/**
 * Remembers a value and saves or restores it with [saver].
 *
 * Provider registration occurs only after composition commit. If restoration or composition
 * fails, the claimed value is released for a later attempt instead of being consumed.
 */
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

/**
 * Resolves the save key; explicit keys use the user prefix, automatic keys come from the composer's stable slot path.
 */
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

/**
 * Creates a holder and attempts to restore a claimed saved value.
 */
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

/**
 * Lifecycle bridge for rememberSaveable that registers providers after commit and releases claims on abandonment.
 */
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
