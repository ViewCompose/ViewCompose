package com.viewcompose.widget.core

/**
 * A platform-neutral registry for values that must survive composition and host recreation.
 *
 * Android hosts provide the persistence boundary through `androidx.savedstate`; custom hosts can
 * install their own registry with [ProvideSaveableStateRegistry].
 */
interface SaveableStateRegistry {
    /**
     * Reserves restored state for a composition attempt.
     *
     * The reservation must be [RestoredSaveableValue.commit]ed after the composition commits or
     * [RestoredSaveableValue.release]d when it is abandoned. Reserved values remain part of
     * [performSave], so a host save racing an in-flight composition cannot lose them.
     */
    fun claimRestored(key: String): RestoredSaveableValue?

    fun consumeRestored(key: String): RestoredSaveableValue? {
        return claimRestored(key)?.also(RestoredSaveableValue::commit)
    }

    fun registerProvider(
        key: String,
        valueProvider: () -> Any?,
    ): Entry

    fun canBeSaved(value: Any?): Boolean

    fun performSave(): Map<String, Any?>

    fun interface Entry {
        fun unregister()
    }
}

class RestoredSaveableValue internal constructor(
    val value: Any?,
    private val onCommit: () -> Unit,
    private val onRelease: () -> Unit,
) {
    private val lock = Any()
    private var completed = false

    fun commit() {
        complete(onCommit)
    }

    fun release() {
        complete(onRelease)
    }

    private fun complete(operation: () -> Unit) {
        val shouldRun = synchronized(lock) {
            if (completed) {
                false
            } else {
                completed = true
                true
            }
        }
        if (shouldRun) {
            operation()
        }
    }
}

fun createSaveableStateRegistry(
    restoredValues: Map<String, Any?> = emptyMap(),
    canBeSaved: (Any?) -> Boolean = { true },
): SaveableStateRegistry {
    return SaveableStateRegistryImpl(
        restoredValues = restoredValues,
        canBeSavedPredicate = canBeSaved,
    )
}

private class SaveableStateRegistryImpl(
    restoredValues: Map<String, Any?>,
    private val canBeSavedPredicate: (Any?) -> Boolean,
) : SaveableStateRegistry {
    private val lock = Any()
    private val restored = LinkedHashMap(restoredValues)
    private val retained = LinkedHashMap<String, Any?>()
    private val claims = LinkedHashMap<String, Claim>()
    private val providers = LinkedHashMap<String, () -> Any?>()

    override fun claimRestored(key: String): RestoredSaveableValue? = synchronized(lock) {
        require(key.isNotBlank()) { "Saveable state key must not be blank." }
        if (key in claims) {
            return@synchronized null
        }
        val value = when {
            restored.containsKey(key) -> restored.remove(key)
            retained.containsKey(key) -> retained.remove(key)
            else -> return@synchronized null
        }
        val claim = Claim(value)
        claims[key] = claim
        RestoredSaveableValue(
            value = value,
            onCommit = {
                synchronized(lock) {
                    if (claims[key] === claim) {
                        claims.remove(key)
                    }
                }
            },
            onRelease = {
                synchronized(lock) {
                    if (claims[key] === claim) {
                        claims.remove(key)
                        retained[key] = value
                    }
                }
            },
        )
    }

    override fun registerProvider(
        key: String,
        valueProvider: () -> Any?,
    ): SaveableStateRegistry.Entry {
        require(key.isNotBlank()) { "Saveable state key must not be blank." }
        synchronized(lock) {
            check(key !in providers) {
                "A saveable state provider is already registered for key '$key'. " +
                    "Use unique explicit keys or keep rememberSaveable calls at stable positions."
            }
            retained.remove(key)
            providers[key] = valueProvider
        }
        var registered = true
        return SaveableStateRegistry.Entry {
            val shouldRetain = synchronized(lock) {
                if (!registered || providers[key] !== valueProvider) {
                    false
                } else {
                    registered = false
                    providers.remove(key)
                    true
                }
            }
            if (shouldRetain) {
                retainLatestValue(
                    key = key,
                    valueProvider = valueProvider,
                )
            }
        }
    }

    override fun canBeSaved(value: Any?): Boolean = canBeSavedPredicate(value)

    override fun performSave(): Map<String, Any?> {
        val (baseValues, activeProviders) = synchronized(lock) {
            val base = LinkedHashMap<String, Any?>()
            base.putAll(restored)
            base.putAll(retained)
            claims.forEach { (key, claim) ->
                base[key] = claim.value
            }
            base to LinkedHashMap(providers)
        }
        activeProviders.forEach { (key, provider) ->
            val value = provider()
            requireCanBeSaved(
                key = key,
                value = value,
            )
            baseValues[key] = value
        }
        return baseValues
    }

    private fun retainLatestValue(
        key: String,
        valueProvider: () -> Any?,
    ) {
        val result = runCatching(valueProvider)
        if (result.isFailure) return
        val value = result.getOrNull()
        if (!canBeSaved(value)) return
        synchronized(lock) {
            if (key !in providers) {
                retained[key] = value
            }
        }
    }

    private fun requireCanBeSaved(
        key: String,
        value: Any?,
    ) {
        require(canBeSaved(value)) {
            val type = value?.let { it::class.java.name } ?: "null"
            "Value for saveable state key '$key' cannot be saved: $type. " +
                "Provide a Saver that converts it to supported values."
        }
    }

    private class Claim(
        val value: Any?,
    )
}
