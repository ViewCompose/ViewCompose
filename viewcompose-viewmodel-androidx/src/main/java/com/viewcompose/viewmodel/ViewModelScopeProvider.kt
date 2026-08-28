package com.viewcompose.viewmodel

import android.os.Bundle
import androidx.annotation.MainThread
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.ViewModelInitializer
import androidx.lifecycle.viewmodel.ViewModelStoreProvider as AndroidXViewModelStoreProvider
import androidx.savedstate.SavedStateRegistryOwner
import java.lang.ref.WeakReference

/**
 * Owns retained child [ViewModelStore] scopes below one parent store.
 *
 * Equal [providerKey] values under the same parent share AndroidX-retained state. Child identities
 * are caller-owned stable values: call position, collection position, and mutable keys are invalid
 * retained identities. Each [acquireOwner] call returns a reference-owning lease; closing a lease
 * ends one temporary use and never declares the child permanently removed. Call [clear] only for a
 * terminal child-removal event and [clearAll] only when this whole provider is permanently removed.
 * Active leases defer either clear, and a terminal child rejects new leases until every old lease
 * closes. A later acquisition then creates a fresh scope rather than resurrecting the old store.
 *
 * The parent [ViewModelStore] retains child stores across configuration recreation and remains the
 * final cleanup boundary. [defaultArguments], [defaultCreationExtras], and [defaultFactory] are
 * copied or captured when this provider is created; later caller mutation does not reconfigure an
 * existing provider. Passing a [SavedStateRegistryOwner] to [acquireOwner] enables AndroidX
 * `SavedStateHandle` creation for that child.
 *
 * All operations are Android-main-thread confined. They perform bounded in-memory owner, store,
 * and reference-count work without I/O, blocking, scheduling, or global lookup.
 *
 * @param parentOwner retained parent whose store owns this provider's AndroidX state holder
 * @param providerKey non-null stable identity that isolates this provider inside [parentOwner]
 * @param defaultArguments default saved-state arguments inherited by child owners
 * @param defaultCreationExtras initial child creation extras; copied before AndroidX receives them
 * @param defaultFactory factory inherited by child owners unless their store already has an entry
 * @throws IllegalArgumentException when [providerKey] is null through a Java or reflective call
 * @see ViewModelStoreOwnerLease
 */
class ViewModelScopeProvider(
    parentOwner: ViewModelStoreOwner,
    providerKey: Any,
    defaultArguments: Bundle = Bundle(),
    defaultCreationExtras: CreationExtras = parentOwner.defaultCreationExtras(),
    defaultFactory: ViewModelProvider.Factory = parentOwner.defaultFactory(),
) {
    private val delegate = AndroidXViewModelStoreProvider(
        parentOwner = parentOwner,
        parentKey = ProviderStorageKey(requireStableKey(providerKey, "providerKey")),
        defaultArgs = Bundle(defaultArguments),
        defaultCreationExtras = MutableCreationExtras(defaultCreationExtras),
        defaultFactory = defaultFactory,
    )
    private val state = providerState(delegate)

    /**
     * Acquires one retained child owner and protects its store until the returned lease is closed.
     *
     * Equal [key] values in this provider resolve the same child store. While a lease is active,
     * subsequent acquisitions must use the same live [savedStateRegistryOwner] boundary. A
     * terminally cleared child rejects acquisition until its final old lease closes.
     *
     * @param key non-null caller-owned stable child identity
     * @param savedStateRegistryOwner optional owner that supplies lifecycle and saved-state registry
     * @return an idempotently closeable reference to the scoped owner
     * @throws IllegalArgumentException when [key] is null through a Java or reflective call
     * @throws IllegalStateException after provider disposal, during terminal child removal, or when
     *   an active child is requested with an inconsistent saved-state owner
     */
    @MainThread
    @JvmOverloads
    fun acquireOwner(
        key: Any,
        savedStateRegistryOwner: SavedStateRegistryOwner? = null,
    ): ViewModelStoreOwnerLease {
        val candidate = acquireCandidate(
            key = requireStableKey(key, "key"),
            savedStateRegistryOwner = savedStateRegistryOwner,
        )
        candidate.commit()
        return candidate.lease
    }

    /**
     * Requests terminal removal of [key].
     *
     * The request is idempotent. With no active leases the store clears synchronously; otherwise
     * cleanup occurs when the last old lease closes. New leases fail during that interval. Once
     * cleanup completes, the same key denotes a fresh scope.
     *
     * @param key non-null stable child identity to remove
     * @throws IllegalArgumentException when [key] is null through a Java or reflective call
     */
    @MainThread
    fun clear(key: Any) {
        val storageKey = ChildStorageKey(requireStableKey(key, "key"))
        state.scopeOrNull(storageKey)?.requestTerminalRemoval()
        delegate.clearKey(storageKey)
    }

    /**
     * Permanently disposes this provider and requests cleanup of every child store.
     *
     * The operation is idempotent. Active owner or composition leases defer physical clearing, but
     * all new acquisitions fail immediately. Create a new provider after old references finish to
     * begin a new lifetime.
     */
    @MainThread
    fun clearAll() {
        state.requestTerminalRemoval()
        delegate.clearAllKeys()
    }

    @MainThread
    internal fun acquireCandidate(
        key: Any,
        savedStateRegistryOwner: SavedStateRegistryOwner?,
    ): OwnerCandidate {
        state.requireUsable()
        val storageKey = ChildStorageKey(requireStableKey(key, "key"))
        val store = delegate.getOrCreate(storageKey)
        val scopeState = scopeState(
            store = store,
            providerState = state,
            storageKey = storageKey,
        )
        scopeState.prepare(savedStateRegistryOwner)
        val token = try {
            delegate.acquireToken(storageKey)
        } catch (error: Throwable) {
            scopeState.release()
            throw error
        }
        val owner = try {
            delegate.getOrCreateOwner(
                key = storageKey,
                savedStateRegistryOwner = savedStateRegistryOwner,
            )
        } catch (error: Throwable) {
            scopeState.release()
            if (!scopeState.everCommitted) {
                scopeState.requestTerminalRemoval()
                delegate.clearKey(storageKey)
            }
            token.close()
            throw error
        }
        val lease = ViewModelStoreOwnerLease(owner) {
            scopeState.release()
            token.close()
        }
        return OwnerCandidate(
            provider = this,
            storageKey = storageKey,
            state = scopeState,
            lease = lease,
        )
    }

    @MainThread
    internal fun prepareCompositionBinding(
        lifecycle: Lifecycle,
    ): ProviderCompositionBinding {
        state.prepareBinding(lifecycle)
        val token = try {
            delegate.acquireToken(AndroidXViewModelStoreProvider.ProviderMarkerKey)
        } catch (error: Throwable) {
            state.releasePreparedBinding()
            throw error
        }
        return ProviderCompositionBinding(
            provider = this,
            state = state,
            lifecycle = lifecycle,
            token = token,
        )
    }

    @MainThread
    private fun abandonCandidate(
        storageKey: ChildStorageKey,
        scopeState: ScopeState,
        lease: ViewModelStoreOwnerLease,
    ) {
        if (!scopeState.everCommitted) {
            scopeState.requestTerminalRemoval()
            delegate.clearKey(storageKey)
        }
        lease.close()
    }

    internal class OwnerCandidate(
        private val provider: ViewModelScopeProvider,
        private val storageKey: ChildStorageKey,
        private val state: ScopeState,
        val lease: ViewModelStoreOwnerLease,
    ) {
        private var terminal = false

        @MainThread
        fun commit() {
            if (terminal) return
            state.commit()
            terminal = true
        }

        @MainThread
        fun abandon() {
            if (terminal) return
            terminal = true
            provider.abandonCandidate(
                storageKey = storageKey,
                scopeState = state,
                lease = lease,
            )
        }
    }

    internal class ProviderCompositionBinding(
        private val provider: ViewModelScopeProvider,
        private val state: ProviderState,
        private val lifecycle: Lifecycle,
        private val token: AndroidXViewModelStoreProvider.ReferenceToken,
    ) {
        private var committed = false
        private var released = false

        @MainThread
        fun commit() {
            if (released || committed) return
            state.commitBinding()
            committed = true
        }

        @MainThread
        fun forget() {
            release(
                requestTerminal = lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED),
            )
        }

        @MainThread
        fun abandon() {
            release(requestTerminal = false)
        }

        private fun release(requestTerminal: Boolean) {
            if (released) return
            released = true
            val shouldClear = state.releaseBinding(
                committed = committed,
                requestTerminal = requestTerminal,
            )
            token.close()
            if (shouldClear || state.isTerminal) {
                provider.delegate.clearAllKeys()
            }
        }
    }
}

/**
 * One active reference to a scoped [ViewModelStoreOwner].
 *
 * [close] is idempotent and releases only this temporary reference. It never permanently removes
 * the logical child; the owning container uses [ViewModelScopeProvider.clear] for that signal.
 * The [owner] remains readable after close for diagnostics, but using it after release is outside
 * the lifetime contract.
 *
 * @property owner read-only AndroidX owner protected by this lease until [close]
 */
class ViewModelStoreOwnerLease internal constructor(
    val owner: ViewModelStoreOwner,
    private val closeAction: () -> Unit,
) : AutoCloseable {
    private var closed = false

    /** Releases this reference once; subsequent calls have no effect. */
    @MainThread
    override fun close() {
        if (closed) return
        closed = true
        closeAction()
    }
}

private data class ProviderStorageKey(
    val value: Any,
)

internal data class ChildStorageKey(
    val value: Any,
)

private fun providerState(
    provider: AndroidXViewModelStoreProvider,
): ProviderState {
    val store = provider.getOrCreate(AndroidXViewModelStoreProvider.ProviderMarkerKey)
    return ViewModelProvider(
        store,
        ViewModelProvider.Factory.from(
            ViewModelInitializer(ProviderState::class) { ProviderState() },
        ),
    )[ProviderState::class.java]
}

private fun scopeState(
    store: ViewModelStore,
    providerState: ProviderState,
    storageKey: ChildStorageKey,
): ScopeState {
    return ViewModelProvider(
        store,
        ViewModelProvider.Factory.from(
            ViewModelInitializer(ScopeState::class) {
                ScopeState(
                    providerState = providerState,
                    storageKey = storageKey,
                )
            },
        ),
    )[ScopeState::class.java].also { state ->
        providerState.register(storageKey, state)
    }
}

internal class ProviderState : ViewModel() {
    private val scopes = linkedMapOf<ChildStorageKey, ScopeState>()
    private var activeBindings = 0
    private var committedBindings = 0
    private var everCommitted = false
    private var pendingTerminalRemoval = false
    private var activeLifecycle = WeakReference<Lifecycle>(null)
    var isTerminal: Boolean = false
        private set

    fun register(
        key: ChildStorageKey,
        state: ScopeState,
    ) {
        scopes[key] = state
        if (isTerminal) {
            state.requestTerminalRemoval()
        }
    }

    fun unregister(
        key: ChildStorageKey,
        state: ScopeState,
    ) {
        if (scopes[key] === state) {
            scopes.remove(key)
        }
    }

    fun scopeOrNull(key: ChildStorageKey): ScopeState? = scopes[key]

    fun requireUsable() {
        check(!isTerminal) {
            "This ViewModelScopeProvider has been permanently disposed."
        }
    }

    fun prepareBinding(lifecycle: Lifecycle) {
        requireUsable()
        check(lifecycle.currentState != Lifecycle.State.DESTROYED) {
            "A ViewModelScopeProvider cannot bind to a destroyed parent Lifecycle."
        }
        val current = activeLifecycle.get()
        check(
            activeBindings == 0 ||
                current === lifecycle ||
                current?.currentState == Lifecycle.State.DESTROYED,
        ) {
            "Equal provider keys cannot use different live parent Lifecycle boundaries."
        }
        if (activeBindings == 0 || current?.currentState == Lifecycle.State.DESTROYED) {
            activeLifecycle = WeakReference(lifecycle)
        }
        activeBindings += 1
    }

    fun releasePreparedBinding() {
        check(activeBindings > 0) {
            "ViewModelScopeProvider binding reference count underflow."
        }
        activeBindings -= 1
        if (activeBindings == 0) {
            activeLifecycle.clear()
        }
    }

    fun commitBinding() {
        requireUsable()
        committedBindings += 1
        everCommitted = true
        pendingTerminalRemoval = false
    }

    fun releaseBinding(
        committed: Boolean,
        requestTerminal: Boolean,
    ): Boolean {
        check(activeBindings > 0) {
            "ViewModelScopeProvider binding reference count underflow."
        }
        if (committed) {
            check(committedBindings > 0) {
                "ViewModelScopeProvider committed binding reference count underflow."
            }
            committedBindings -= 1
        }
        activeBindings -= 1
        if (requestTerminal && committedBindings == 0) {
            pendingTerminalRemoval = true
        }
        if (!committed && !everCommitted && committedBindings == 0) {
            pendingTerminalRemoval = true
        }
        if (activeBindings == 0) {
            activeLifecycle.clear()
        }
        if (!isTerminal && activeBindings == 0 && pendingTerminalRemoval) {
            requestTerminalRemoval()
            return true
        }
        return false
    }

    fun requestTerminalRemoval() {
        if (isTerminal) return
        isTerminal = true
        pendingTerminalRemoval = false
        scopes.values.toList().forEach(ScopeState::requestTerminalRemoval)
    }

    override fun onCleared() {
        requestTerminalRemoval()
        scopes.clear()
    }
}

internal class ScopeState(
    private val providerState: ProviderState,
    private val storageKey: ChildStorageKey,
) : ViewModel() {
    private var activeReferences = 0
    private var savedStateOwner = WeakReference<SavedStateRegistryOwner>(null)
    private var activeOwnerWasNull = false
    var everCommitted: Boolean = false
        private set
    private var terminal = false

    fun prepare(owner: SavedStateRegistryOwner?) {
        check(!terminal) {
            "ViewModel scope '${storageKey.value}' is being permanently removed; " +
                "wait for old leases to close before reusing its key."
        }
        if (activeReferences > 0) {
            val current = savedStateOwner.get()
            val consistent = if (activeOwnerWasNull) {
                owner == null
            } else {
                current === owner || current?.lifecycle?.currentState == Lifecycle.State.DESTROYED
            }
            check(consistent) {
                "Active ViewModel scope '${storageKey.value}' cannot change SavedStateRegistryOwner."
            }
        }
        if (activeReferences == 0 || savedStateOwner.get()?.lifecycle?.currentState == Lifecycle.State.DESTROYED) {
            activeOwnerWasNull = owner == null
            savedStateOwner = WeakReference(owner)
        }
        activeReferences += 1
    }

    fun commit() {
        check(!terminal) {
            "A terminal ViewModel scope cannot commit a new owner lease."
        }
        everCommitted = true
    }

    fun release() {
        check(activeReferences > 0) {
            "ViewModel scope reference count underflow for '${storageKey.value}'."
        }
        activeReferences -= 1
        if (activeReferences == 0) {
            savedStateOwner.clear()
            activeOwnerWasNull = false
        }
    }

    fun requestTerminalRemoval() {
        terminal = true
    }

    override fun onCleared() {
        terminal = true
        providerState.unregister(storageKey, this)
    }
}

private fun ViewModelStoreOwner.defaultFactory(): ViewModelProvider.Factory {
    return (this as? HasDefaultViewModelProviderFactory)?.defaultViewModelProviderFactory
        ?: ViewModelProvider.NewInstanceFactory()
}

private fun ViewModelStoreOwner.defaultCreationExtras(): CreationExtras {
    return (this as? HasDefaultViewModelProviderFactory)?.defaultViewModelCreationExtras
        ?: CreationExtras.Empty
}

private fun requireStableKey(
    key: Any?,
    name: String,
): Any {
    return requireNotNull(key) {
        "ViewModelScopeProvider $name must be a non-null stable value."
    }
}
