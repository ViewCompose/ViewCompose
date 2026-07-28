package com.viewcompose.host.android

import android.os.Bundle
import android.os.IBinder
import android.os.Parcelable
import android.util.Size
import android.util.SizeF
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryOwner
import com.viewcompose.widget.core.SaveableStateRegistry
import com.viewcompose.widget.core.createSaveableStateRegistry
import java.io.Serializable
import java.util.IdentityHashMap

internal object AndroidSaveableStateRegistryStore {
    private const val PROVIDER_KEY = "com.viewcompose.host.android.SaveableStateRegistry"
    private val bindings = IdentityHashMap<SavedStateRegistryOwner, Binding>()

    @Synchronized
    fun registryFor(owner: SavedStateRegistryOwner): SaveableStateRegistry {
        check(owner.lifecycle.currentState != Lifecycle.State.DESTROYED) {
            "Cannot create saveable state for a destroyed SavedStateRegistryOwner."
        }
        bindings[owner]?.let { binding ->
            return binding.registry
        }
        val restored = decodeRegistryState(
            bundle = owner.savedStateRegistry.consumeRestoredStateForKey(PROVIDER_KEY),
            classLoader = owner::class.java.classLoader,
        )
        val registry = createSaveableStateRegistry(
            restoredValues = restored,
            canBeSaved = ::canBeSavedToBundle,
        )
        owner.savedStateRegistry.registerSavedStateProvider(PROVIDER_KEY) {
            encodeRegistryState(registry.performSave())
        }
        val observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                remove(owner as SavedStateRegistryOwner)
            }
        }
        bindings[owner] = Binding(
            registry = registry,
            savedStateRegistry = owner.savedStateRegistry,
            observer = observer,
        )
        owner.lifecycle.addObserver(observer)
        return registry
    }

    @Synchronized
    private fun remove(owner: SavedStateRegistryOwner) {
        val binding = bindings.remove(owner) ?: return
        owner.lifecycle.removeObserver(binding.observer)
        binding.savedStateRegistry.unregisterSavedStateProvider(PROVIDER_KEY)
    }

    private data class Binding(
        val registry: SaveableStateRegistry,
        val savedStateRegistry: SavedStateRegistry,
        val observer: DefaultLifecycleObserver,
    )
}

/**
 * Creates or returns the ViewCompose saveable-state registry bound to an Android
 * [SavedStateRegistryOwner].
 *
 * Custom framework-owned hosts, such as navigation destinations, use this bridge to share the
 * same transactional `rememberSaveable` persistence semantics as Activity and Fragment roots.
 * The binding is removed automatically when [SavedStateRegistryOwner.getLifecycle] is destroyed.
 */
fun viewComposeSaveableStateRegistry(
    owner: SavedStateRegistryOwner,
): SaveableStateRegistry = AndroidSaveableStateRegistryStore.registryFor(owner)

private fun canBeSavedToBundle(value: Any?): Boolean {
    return when (value) {
        null -> true
        is Function<*> -> false
        is List<*> -> value.all(::canBeSavedToBundle)
        is Map<*, *> -> value.all { (key, item) ->
            key is String && canBeSavedToBundle(item)
        }
        is Array<*> -> value.all(::canBeSavedToBundle)
        is Parcelable,
        is Serializable,
        is IBinder,
        is Size,
        is SizeF,
        -> true
        else -> false
    }
}

internal fun encodeRegistryState(values: Map<String, Any?>): Bundle {
    val entries = Bundle()
    values.forEach { (key, value) ->
        entries.putBundle(key, encodeValue(value))
    }
    return Bundle().apply {
        putInt(KEY_FORMAT_VERSION, FORMAT_VERSION)
        putBundle(KEY_ENTRIES, entries)
    }
}

internal fun decodeRegistryState(
    bundle: Bundle?,
    classLoader: ClassLoader?,
): Map<String, Any?> {
    if (bundle == null || bundle.getInt(KEY_FORMAT_VERSION) != FORMAT_VERSION) {
        return emptyMap()
    }
    bundle.classLoader = classLoader
    val entries = bundle.getBundle(KEY_ENTRIES) ?: return emptyMap()
    entries.classLoader = classLoader
    return buildMap {
        entries.keySet().forEach { key ->
            val encoded = entries.getBundle(key) ?: return@forEach
            runCatching {
                decodeValue(
                    bundle = encoded,
                    classLoader = classLoader,
                )
            }.onSuccess { value ->
                put(key, value)
            }
        }
    }
}

private fun encodeValue(value: Any?): Bundle {
    return when (value) {
        null -> Bundle().withType(ValueType.Null)
        is List<*> -> Bundle().apply {
            putInt(KEY_VALUE_TYPE, ValueType.ListValue.id)
            putInt(KEY_SIZE, value.size)
            value.forEachIndexed { index, item ->
                putBundle("$KEY_ITEM_PREFIX$index", encodeValue(item))
            }
        }
        is Map<*, *> -> Bundle().apply {
            putInt(KEY_VALUE_TYPE, ValueType.MapValue.id)
            putInt(KEY_SIZE, value.size)
            value.entries.forEachIndexed { index, (mapKey, item) ->
                require(mapKey is String) {
                    "Only String keys are supported in saveable maps."
                }
                putString("$KEY_MAP_KEY_PREFIX$index", mapKey)
                putBundle("$KEY_ITEM_PREFIX$index", encodeValue(item))
            }
        }
        else -> Bundle().apply {
            putInt(KEY_VALUE_TYPE, ValueType.PlatformValue.id)
            putPlatformValue(KEY_PLATFORM_VALUE, value)
        }
    }
}

private fun decodeValue(
    bundle: Bundle,
    classLoader: ClassLoader?,
): Any? {
    bundle.classLoader = classLoader
    return when (ValueType.fromId(bundle.getInt(KEY_VALUE_TYPE))) {
        ValueType.Null -> null
        ValueType.ListValue -> {
            val size = bundle.getInt(KEY_SIZE)
            List(size) { index ->
                decodeValue(
                    bundle = requireNotNull(bundle.getBundle("$KEY_ITEM_PREFIX$index")),
                    classLoader = classLoader,
                )
            }
        }
        ValueType.MapValue -> {
            val size = bundle.getInt(KEY_SIZE)
            buildMap {
                repeat(size) { index ->
                    val key = requireNotNull(bundle.getString("$KEY_MAP_KEY_PREFIX$index"))
                    val value = decodeValue(
                        bundle = requireNotNull(bundle.getBundle("$KEY_ITEM_PREFIX$index")),
                        classLoader = classLoader,
                    )
                    put(key, value)
                }
            }
        }
        ValueType.PlatformValue -> {
            @Suppress("DEPRECATION")
            bundle.get(KEY_PLATFORM_VALUE)
        }
    }
}

private fun Bundle.withType(type: ValueType): Bundle = apply {
    putInt(KEY_VALUE_TYPE, type.id)
}

private fun Bundle.putPlatformValue(
    key: String,
    value: Any,
) {
    when (value) {
        is Parcelable -> putParcelable(key, value)
        is IBinder -> putBinder(key, value)
        is Size -> putSize(key, value)
        is SizeF -> putSizeF(key, value)
        is Serializable -> putSerializable(key, value)
        else -> error("Unsupported Android saveable value: ${value::class.java.name}")
    }
}

private enum class ValueType(
    val id: Int,
) {
    Null(0),
    PlatformValue(1),
    ListValue(2),
    MapValue(3),
    ;

    companion object {
        fun fromId(id: Int): ValueType {
            return entries.firstOrNull { it.id == id }
                ?: error("Unknown saveable value type id: $id")
        }
    }
}

private const val FORMAT_VERSION = 1
private const val KEY_FORMAT_VERSION = "formatVersion"
private const val KEY_ENTRIES = "entries"
private const val KEY_VALUE_TYPE = "valueType"
private const val KEY_SIZE = "size"
private const val KEY_ITEM_PREFIX = "item_"
private const val KEY_MAP_KEY_PREFIX = "mapKey_"
private const val KEY_PLATFORM_VALUE = "platformValue"
