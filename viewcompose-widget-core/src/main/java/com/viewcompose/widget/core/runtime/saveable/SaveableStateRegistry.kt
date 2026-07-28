package com.viewcompose.widget.core

/**
 * 平台无关的可保存状态注册表，用于让值跨 composition 和宿主重建存活。
 * A platform-neutral registry for values that must survive composition and host recreation.
 *
 * Android 宿主通过 `androidx.savedstate` 提供持久化边界；自定义宿主可通过 [ProvideSaveableStateRegistry]
 * 安装自己的 registry。
 * Android hosts provide the persistence boundary through `androidx.savedstate`; custom hosts can
 * install their own registry with [ProvideSaveableStateRegistry].
 */
interface SaveableStateRegistry {
    /**
     * 为一次 composition 尝试预留恢复值。
     * Reserves restored state for a composition attempt.
     *
     * composition 提交后必须调用 [RestoredSaveableValue.commit]，放弃时必须调用 [RestoredSaveableValue.release]。
     * 预留值仍会出现在 [performSave] 中，因此宿主保存与进行中的 composition 竞争时不会丢值。
     * The reservation must be [RestoredSaveableValue.commit]ed after the composition commits or
     * [RestoredSaveableValue.release]d when it is abandoned. Reserved values remain part of
     * [performSave], so a host save racing an in-flight composition cannot lose them.
     */
    fun claimRestored(key: String): RestoredSaveableValue?

    /**
     * 兼容旧调用方的立即消费 API，成功 claim 后立刻 commit。
     * Compatibility API that commits immediately after a successful claim.
     */
    fun consumeRestored(key: String): RestoredSaveableValue? {
        return claimRestored(key)?.also(RestoredSaveableValue::commit)
    }

    /**
     * 注册一个保存值提供者，在 host 保存时调用。
     * Registers one save provider that is invoked when the host saves state.
     */
    fun registerProvider(
        key: String,
        valueProvider: () -> Any?,
    ): Entry

    /**
     * 判断某个值是否能被当前宿主持久化。
     * Returns whether a value can be persisted by the current host.
     */
    fun canBeSaved(value: Any?): Boolean

    /**
     * 生成当前所有可保存值的快照。
     * Produces a snapshot of all currently saveable values.
     */
    fun performSave(): Map<String, Any?>

    /**
     * 已注册 provider 的生命周期句柄。
     * Lifecycle handle for a registered provider.
     */
    fun interface Entry {
        fun unregister()
    }
}

/**
 * 被 claim 的恢复值，必须在 composition 结果确定后 commit 或 release。
 * Claimed restored value that must be committed or released once the composition outcome is known.
 */
class RestoredSaveableValue internal constructor(
    val value: Any?,
    private val onCommit: () -> Unit,
    private val onRelease: () -> Unit,
) {
    private val lock = Any()
    private var completed = false

    /**
     * 确认恢复值已被成功使用。
     * Confirms that the restored value was used successfully.
     */
    fun commit() {
        complete(onCommit)
    }

    /**
     * 放弃恢复值并交还给 registry 以便后续尝试继续使用。
     * Releases the restored value back to the registry for a later attempt.
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
 * 创建默认的可保存状态注册表。
 * Creates the default saveable-state registry.
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
 * SaveableStateRegistry 的线程安全实现，区分 restored、retained、claimed 和 active providers。
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
