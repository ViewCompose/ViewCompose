package com.viewcompose.lifecycle

import android.os.Bundle
import android.view.View
import androidx.annotation.MainThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import com.viewcompose.host.android.AndroidViewCommitScope

/** Result of binding a committed Android View to one saved-state provider identity. */
sealed interface AndroidViewSavedStateBindResult {
    /**
     * Identifies the first committed bind for this View, owner, key, and format version.
     *
     * [restoredState] is a defensive copy of compatible restored SDK state, or `null` when no
     * compatible state exists. SDK lifecycle initialization may consume it exactly once.
     *
     * @property restoredState compatible one-shot SDK payload, or `null` when none can be restored
     */
    class Initial internal constructor(
        val restoredState: Bundle?,
    ) : AndroidViewSavedStateBindResult

    /** Identifies a later commit that retained the existing provider and only replaced its saver. */
    data object Retained : AndroidViewSavedStateBindResult
}

/**
 * Supplies the committed renderer-owned View when Android requests an SDK saved-state snapshot.
 *
 * @param V exact Android View type being saved
 * @property view committed View associated with the registered provider
 */
class AndroidViewSaveStateScope<V : View> internal constructor(
    val view: V,
)

/**
 * Binds a committed Android View to one explicit AndroidX saved-state namespace.
 *
 * Call this only from `AndroidViewAdapter.onCommit` or
 * `LifecycleAndroidViewAdapter.onViewCommit`. The first bind registers one provider, consumes one
 * restored Bundle, validates [formatVersion], and returns [AndroidViewSavedStateBindResult.Initial].
 * Later commits with the same owner, key, and version retain the provider and replace only
 * [saveState], so Android always reads the latest committed View. Owner or key replacement installs
 * the new provider before removing the old one; a collision or restore failure leaves the old
 * binding intact.
 *
 * [key] is an application-stable persistence identity, not an AndroidView reconciliation key. A
 * missing or incompatible nested Bundle is isolated as `null`. [LifecycleAndroidViewAdapter]
 * clears this binding automatically during reset and release; raw adapters must call
 * [clearAndroidViewSavedStateBinding] from their final View cleanup. The owner remains
 * application-owned, and this API never saves arbitrary ViewCompose or application state.
 *
 * @sample com.viewcompose.lifecycle.samples.androidViewSavedStateBindingSample
 * @receiver committed Android View adapter scope
 * @param owner nearest saved-state owner captured during declaration
 * @param key stable non-blank namespace unique within [owner]
 * @param formatVersion positive SDK-state format version controlled by the integration
 * @param saveState callback invoked by Android to snapshot the latest committed View
 * @return whether this call installed a new provider and any compatible restored payload
 * @throws IllegalStateException for a destroyed owner or duplicate provider namespace
 * @throws IllegalArgumentException for an invalid key or format version
 */
@MainThread
fun <V : View> AndroidViewCommitScope<V>.bindAndroidViewSavedState(
    owner: SavedStateRegistryOwner,
    key: String,
    formatVersion: Int,
    saveState: AndroidViewSaveStateScope<V>.() -> Bundle,
): AndroidViewSavedStateBindResult {
    require(key.isNotBlank()) { "Android View saveableStateKey must not be blank." }
    require(key.length <= MAX_SAVEABLE_STATE_KEY_LENGTH) {
        "Android View saveableStateKey exceeds $MAX_SAVEABLE_STATE_KEY_LENGTH characters."
    }
    require(formatVersion > 0) { "Android View saved-state formatVersion must be positive." }
    check(owner.lifecycle.currentState != Lifecycle.State.DESTROYED) {
        "Android View saved state cannot bind to a destroyed SavedStateRegistryOwner."
    }
    return AndroidViewSavedStateBindingStore.bind(
        view = view,
        owner = owner,
        key = key,
        formatVersion = formatVersion,
        saveState = {
            Bundle(AndroidViewSaveStateScope(view).saveState())
        },
    )
}

/**
 * Removes this View's saved-state provider without saving or consuming another value.
 *
 * This operation is idempotent. Raw adapters call it after lifecycle detachment and before
 * permanent View cleanup, and during reset before the View crosses to another logical key.
 * [LifecycleAndroidViewAdapter] performs both calls automatically.
 */
@MainThread
fun View.clearAndroidViewSavedStateBinding() {
    AndroidViewSavedStateBindingStore.remove(this)?.dispose()
}

private object AndroidViewSavedStateBindingStore {
    fun bind(
        view: View,
        owner: SavedStateRegistryOwner,
        key: String,
        formatVersion: Int,
        saveState: () -> Bundle,
    ): AndroidViewSavedStateBindResult {
        val existing = read(view)
        if (existing != null && existing.owner === owner && existing.key == key) {
            check(existing.formatVersion == formatVersion) {
                "Android View saved-state formatVersion changed for the same owner and key."
            }
            existing.saveState = saveState
            return AndroidViewSavedStateBindResult.Retained
        }

        val candidate = AndroidViewSavedStateBinding(
            view = view,
            owner = owner,
            key = key,
            formatVersion = formatVersion,
            saveState = saveState,
        )
        val restored = candidate.install()
        view.setTag(R.id.viewcompose_android_view_saved_state_binding, candidate)
        existing?.dispose()
        return AndroidViewSavedStateBindResult.Initial(restored)
    }

    fun remove(view: View): AndroidViewSavedStateBinding? {
        val existing = read(view)
        view.setTag(R.id.viewcompose_android_view_saved_state_binding, null)
        return existing
    }

    fun removeIfCurrent(view: View, binding: AndroidViewSavedStateBinding) {
        if (read(view) === binding) {
            view.setTag(R.id.viewcompose_android_view_saved_state_binding, null)
        }
    }

    private fun read(view: View): AndroidViewSavedStateBinding? {
        val existing = view.getTag(R.id.viewcompose_android_view_saved_state_binding)
        check(existing == null || existing is AndroidViewSavedStateBinding) {
            "Android View saved-state binding tag is owned by an incompatible value."
        }
        return existing as? AndroidViewSavedStateBinding
    }
}

private class AndroidViewSavedStateBinding(
    private val view: View,
    val owner: SavedStateRegistryOwner,
    val key: String,
    val formatVersion: Int,
    var saveState: () -> Bundle,
) : DefaultLifecycleObserver {
    private val providerKey = "$PROVIDER_KEY_PREFIX:$key"
    private var installed = false

    fun install(): Bundle? {
        check(!installed) { "Android View saved-state binding is already installed." }
        owner.savedStateRegistry.registerSavedStateProvider(providerKey) {
            Bundle().apply {
                putInt(KEY_FORMAT_VERSION, formatVersion)
                putBundle(KEY_PAYLOAD, saveState())
            }
        }
        installed = true
        try {
            owner.lifecycle.addObserver(this)
            check(installed && owner.lifecycle.currentState != Lifecycle.State.DESTROYED) {
                "Android View saved state owner was destroyed during provider installation."
            }
            val restored = decodeRestoredState(
                state = owner.savedStateRegistry.consumeRestoredStateForKey(providerKey),
                formatVersion = formatVersion,
                classLoader = owner::class.java.classLoader,
            )
            return restored
        } catch (error: Throwable) {
            try {
                dispose()
            } catch (cleanupError: Throwable) {
                if (cleanupError !== error) {
                    error.addSuppressed(cleanupError)
                }
            }
            throw error
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        if (this.owner !== owner) return
        AndroidViewSavedStateBindingStore.removeIfCurrent(view, this)
        dispose()
    }

    fun dispose() {
        if (!installed) return
        installed = false
        var failure: Throwable? = null
        failure = captureFailure(failure) {
            owner.lifecycle.removeObserver(this)
        }
        failure = captureFailure(failure) {
            owner.savedStateRegistry.unregisterSavedStateProvider(providerKey)
        }
        failure?.let { throw it }
    }
}

private fun decodeRestoredState(
    state: Bundle?,
    formatVersion: Int,
    classLoader: ClassLoader?,
): Bundle? {
    if (state == null) return null
    return runCatching {
        state.classLoader = classLoader
        if (state.getInt(KEY_FORMAT_VERSION) != formatVersion) {
            return@runCatching null
        }
        state.getBundle(KEY_PAYLOAD)?.also { payload ->
            payload.classLoader = classLoader
        }?.let(::Bundle)
    }.getOrNull()
}

private const val PROVIDER_KEY_PREFIX = "com.viewcompose.lifecycle.AndroidView"
private const val KEY_FORMAT_VERSION = "formatVersion"
private const val KEY_PAYLOAD = "payload"
private const val MAX_SAVEABLE_STATE_KEY_LENGTH = 160
