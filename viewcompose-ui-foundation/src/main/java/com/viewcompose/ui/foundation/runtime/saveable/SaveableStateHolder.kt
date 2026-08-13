package com.viewcompose.ui.foundation

/**
 * Parent-composition owner for the registries of independently composed logical children.
 *
 * The holder is itself saved by [rememberSaveable]. Child keys stay as values in a nested saved
 * representation instead of being flattened into potentially colliding provider-key hashes.
 */
internal class SaveableStateHolder private constructor(
    private val parentRegistry: SaveableStateRegistry,
    restoredStates: Map<Any, Map<String, Any?>>,
) {
    private val lock = Any()
    private val retainedStates = LinkedHashMap(restoredStates)
    private val activeOwners = LinkedHashMap<Any, SaveableStateRegistry>()
    private var retainedKeys: Set<Any>? = null

    /** Creates one lease for an independently composed presentation of [key]. */
    fun acquire(key: Any): SaveableStateRegistryLease {
        val acquisition = synchronized(lock) {
            val existingOwner = activeOwners[key]
            if (existingOwner != null) {
                Acquisition(
                    registry = existingOwner,
                    ownsPersistence = false,
                )
            } else {
                val registry = createChildRegistry(
                    key = key,
                    restoredValues = retainedStates.remove(key).orEmpty(),
                )
                activeOwners[key] = registry
                Acquisition(
                    registry = registry,
                    ownsPersistence = true,
                )
            }
        }
        if (!acquisition.ownsPersistence) {
            // Sticky headers and similar renderer features may present the same logical child twice.
            // A replica starts from the owner's current value but never becomes a second writer.
            return SaveableStateRegistryLease(
                registry = createChildRegistry(
                    key = key,
                    restoredValues = acquisition.registry.performSave(),
                ),
                onClose = {},
            )
        }
        val registry = acquisition.registry
        return SaveableStateRegistryLease(
            registry = registry,
            onClose = {
                val ownsKey = synchronized(lock) {
                    activeOwners[key] === registry
                }
                if (ownsKey) {
                    val saved = registry.performSave()
                    synchronized(lock) {
                        if (activeOwners[key] === registry) {
                            activeOwners.remove(key)
                            if (saved.isEmpty() || retainedKeys?.contains(key) == false) {
                                retainedStates.remove(key)
                            } else {
                                retainedStates[key] = saved
                            }
                        }
                    }
                }
            },
        )
    }

    /** Applies the successfully committed logical child set and removes permanently absent state. */
    fun retainKeys(keys: Set<Any>) {
        synchronized(lock) {
            retainedKeys = keys.toSet()
            retainedStates.keys.retainAll(keys)
        }
    }

    /** Captures retained and currently active child state for the parent provider. */
    fun save(): List<Any?> {
        val (retainedSnapshot, activeSnapshot) = synchronized(lock) {
            LinkedHashMap(retainedStates) to LinkedHashMap(activeOwners)
        }
        activeSnapshot.forEach { (key, registry) ->
            if (synchronized(lock) { retainedKeys?.contains(key) == false }) {
                retainedSnapshot.remove(key)
                return@forEach
            }
            val saved = registry.performSave()
            if (saved.isEmpty()) {
                retainedSnapshot.remove(key)
            } else {
                retainedSnapshot[key] = saved
            }
        }
        val entries = retainedSnapshot.map { (key, state) ->
            listOf(key, state)
        }
        return listOf(
            HOLDER_MARKER,
            HOLDER_FORMAT_VERSION,
            entries,
        )
    }

    private fun createChildRegistry(
        key: Any,
        restoredValues: Map<String, Any?>,
    ): SaveableStateRegistry {
        val registry = createSaveableStateRegistry(
            restoredValues = restoredValues,
            canBeSaved = parentRegistry::canBeSaved,
        )
        return LogicalChildSaveableStateRegistry(
            key = key,
            parentRegistry = parentRegistry,
            delegate = registry,
        )
    }

    private data class Acquisition(
        val registry: SaveableStateRegistry,
        val ownsPersistence: Boolean,
    )

    companion object {
        fun create(parentRegistry: SaveableStateRegistry): SaveableStateHolder {
            return SaveableStateHolder(
                parentRegistry = parentRegistry,
                restoredStates = emptyMap(),
            )
        }

        fun restore(
            parentRegistry: SaveableStateRegistry,
            saved: Any?,
        ): SaveableStateHolder {
            if (
                saved !is List<*> ||
                saved.size != HOLDER_ENVELOPE_SIZE ||
                saved[0] != HOLDER_MARKER ||
                saved[1] != HOLDER_FORMAT_VERSION
            ) {
                // Flat child-session values from versions before ADR-0010 have no recoverable
                // logical owner. Ignore that one holder value and replace it on the next save.
                return create(parentRegistry)
            }
            val restoredStates = runCatching {
                decodeRestoredStates(saved[2])
            }.getOrElse {
                // A corrupt nested entry is isolated to this holder. Other root registry values
                // remain restorable and the next successful save replaces this holder value.
                emptyMap()
            }
            return SaveableStateHolder(
                parentRegistry = parentRegistry,
                restoredStates = restoredStates,
            )
        }

        private fun decodeRestoredStates(rawEntries: Any?): Map<Any, Map<String, Any?>> {
            val entries = rawEntries as? List<*>
                ?: error("Child saveable-state holder entries must be a list.")
            return buildMap {
                entries.forEach { rawEntry ->
                    val entry = rawEntry as? List<*>
                        ?: error("Child saveable-state holder entry must be a list.")
                    require(entry.size == HOLDER_ENTRY_SIZE) {
                        "Child saveable-state holder entry must contain key and state."
                    }
                    val key = requireNotNull(entry[0]) {
                        "Child saveable-state holder key must not be null."
                    }
                    val rawState = entry[1] as? Map<*, *>
                        ?: error("Child saveable-state holder state must be a map.")
                    val state = buildMap {
                        rawState.forEach { (rawKey, value) ->
                            val stateKey = rawKey as? String
                                ?: error("Child saveable-state provider key must be a string.")
                            put(stateKey, value)
                        }
                    }
                    require(put(key, state) == null) {
                        "Duplicate restored child saveable-state key: $key"
                    }
                }
            }
        }
    }
}

/** One independently composed presentation's registry lifetime. */
internal class SaveableStateRegistryLease(
    val registry: SaveableStateRegistry,
    private val onClose: () -> Unit,
) {
    private var closed = false

    fun close() {
        if (closed) return
        closed = true
        onClose()
    }
}

/** Validates a logical key only when its child actually registers saveable state. */
private class LogicalChildSaveableStateRegistry(
    private val key: Any,
    private val parentRegistry: SaveableStateRegistry,
    private val delegate: SaveableStateRegistry,
) : SaveableStateRegistry by delegate {
    override fun registerProvider(
        key: String,
        valueProvider: () -> Any?,
    ): SaveableStateRegistry.Entry {
        require(parentRegistry.canBeSaved(this.key)) {
            val type = this.key::class.java.name
            "Logical child key '${this.key}' cannot own saveable state because the current host " +
                "cannot save $type. Use a host-saveable stable item, page, tab, or surface key."
        }
        return delegate.registerProvider(key, valueProvider)
    }
}

/** Remembers a holder only when a registry and an active composition are available. */
internal fun rememberSaveableStateHolder(): SaveableStateHolder? {
    val parentRegistry = LocalSaveableStateRegistry.current ?: return null
    if (ComposerContext.currentComposer() == null) return null
    return rememberSaveable(
        saver = Saver<SaveableStateHolder, Any?>(
            save = SaveableStateHolder::save,
            restore = { saved ->
                SaveableStateHolder.restore(
                    parentRegistry = parentRegistry,
                    saved = saved,
                )
            },
        ),
    ) {
        SaveableStateHolder.create(parentRegistry)
    }
}

private const val HOLDER_MARKER =
    "com.viewcompose.ui.foundation.runtime.saveable.SaveableStateHolder"
private const val HOLDER_FORMAT_VERSION = 1
private const val HOLDER_ENVELOPE_SIZE = 3
private const val HOLDER_ENTRY_SIZE = 2
