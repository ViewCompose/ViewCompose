package com.viewcompose.ui.foundation

/**
 * Coordinates values that survive composition disposal and host recreation.
 *
 * Android hosts connect this registry to `androidx.savedstate`. Custom hosts install their own
 * registry with [ProvideSaveableStateRegistry]. Restored values use a claim/commit protocol so an
 * aborted composition cannot consume state that a later attempt still needs.
 *
 * @sample com.viewcompose.ui.foundation.samples.saveableStateRegistrySample
 */
interface SaveableStateRegistry {
    /**
     * Reserves restored state for one composition attempt.
     *
     * The reservation must be [RestoredSaveableValue.commit]ed after the composition commits or
     * [RestoredSaveableValue.release]d when it is abandoned. Reserved values remain part of
     * [performSave], so a host save racing an in-flight composition cannot lose them.
     */
    fun claimRestored(key: String): RestoredSaveableValue?

    /**
     * Claims and immediately commits a restored value for [key].
     *
     * Prefer [claimRestored] in transactional composition code that can still abort.
     */
    fun consumeRestored(key: String): RestoredSaveableValue? {
        return claimRestored(key)?.also(RestoredSaveableValue::commit)
    }

    /**
     * Registers one value provider for [key].
     *
     * The provider may be called during [performSave] and when its [Entry] is unregistered. A key
     * can have only one active provider; duplicate registration fails.
     */
    fun registerProvider(
        key: String,
        valueProvider: () -> Any?,
    ): Entry

    /** Returns whether [value] can cross the current host's persistence boundary. */
    fun canBeSaved(value: Any?): Boolean

    /**
     * Returns a snapshot of restored, retained, claimed, and actively provided values.
     *
     * The call fails if an active provider returns a value rejected by [canBeSaved].
     */
    fun performSave(): Map<String, Any?>

    /** Lifecycle handle for one registered save provider. */
    fun interface Entry {
        /**
         * Removes the provider and retains its latest saveable value for a later registration.
         *
         * Repeated calls are ignored.
         */
        fun unregister()
    }
}

/**
 * A reserved restored value that must be committed or released after composition resolves.
 *
 * Exactly the first call to [commit] or [release] takes effect; later calls are ignored.
 *
 * @property value platform-restored value, which may be `null`
 */
class RestoredSaveableValue internal constructor(
    val value: Any?,
    private val onCommit: () -> Unit,
    private val onRelease: () -> Unit,
) {
    private val lock = Any()
    private var completed = false

    /**
     * Permanently consumes this value after a successful composition commit.
     */
    fun commit() {
        complete(onCommit)
    }

    /**
     * Returns this value to the registry after an abandoned or failed composition attempt.
     */
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

/**
 * Creates the thread-safe default [SaveableStateRegistry].
 *
 * @param restoredValues initial values supplied by the host
 * @param canBeSaved host-specific persistence predicate
 */
fun createSaveableStateRegistry(
    restoredValues: Map<String, Any?> = emptyMap(),
    canBeSaved: (Any?) -> Boolean = { true },
): SaveableStateRegistry {
    return SaveableStateRegistryImpl(
        restoredValues = restoredValues,
        canBeSavedPredicate = canBeSaved,
    )
}

/**
 * Thread-safe SaveableStateRegistry implementation that separates restored, retained, claimed, and active providers.
 */
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
