package com.viewcompose.viewmodel

import android.os.Bundle
import androidx.annotation.MainThread
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.savedstate.SavedStateRegistryOwner
import com.viewcompose.runtime.composition.RememberObserver
import com.viewcompose.ui.foundation.remember

/**
 * Remembers a retained child-scope provider below [parentOwner].
 *
 * [key] is mandatory because retained identity must come from the caller rather than call position.
 * Equal keys in the same parent store share provider state. The binding survives configuration
 * recreation through the parent store. When committed composition removes the final binding while
 * [lifecycleOwner] is at least `CREATED`, all children receive terminal cleanup; removal after the
 * parent is `DESTROYED` preserves the stores for configuration recreation and lets a finishing
 * parent clear them through its own store.
 *
 * Provider creation is transactional: an abandoned first candidate is cleared, while an aborted
 * candidate for a previously committed provider leaves that provider intact. [defaultArguments],
 * [defaultCreationExtras], and [defaultFactory] are read only when a new remembered provider is
 * created. All work is Android-main-thread-confined and bounded in memory.
 *
 * @param key non-null stable provider identity owned by the caller or retained container
 * @param parentOwner retained store owner; defaults to [LocalViewModelStoreOwner]
 * @param lifecycleOwner lifecycle used to distinguish normal removal from parent destruction;
 *   defaults to [parentOwner] when it implements [LifecycleOwner]
 * @param defaultArguments default saved-state arguments inherited by child owners
 * @param defaultCreationExtras initial child creation extras
 * @param defaultFactory initial child ViewModel factory
 * @return one remembered provider for the resolved parent and key
 * @throws IllegalArgumentException when [key] is null through a Java or reflective call
 * @throws IllegalStateException without an owner, without a compatible lifecycle, outside active
 *   composition, or when equal provider keys use inconsistent live lifecycle boundaries
 * @sample com.viewcompose.viewmodel.samples.retainedViewModelScopeSample
 */
@MainThread
fun rememberViewModelScopeProvider(
    key: Any,
    parentOwner: ViewModelStoreOwner = requireLocalViewModelStoreOwner(),
    lifecycleOwner: LifecycleOwner = requireLifecycleOwner(parentOwner),
    defaultArguments: Bundle = Bundle(),
    defaultCreationExtras: CreationExtras = parentOwner.defaultCreationExtrasForScope(),
    defaultFactory: ViewModelProvider.Factory = parentOwner.defaultFactoryForScope(),
): ViewModelScopeProvider {
    val holder = remember(
        parentOwner,
        lifecycleOwner,
        key,
    ) {
        val provider = ViewModelScopeProvider(
            parentOwner = parentOwner,
            providerKey = key,
            defaultArguments = defaultArguments,
            defaultCreationExtras = defaultCreationExtras,
            defaultFactory = defaultFactory,
        )
        RememberedProviderBinding(
            provider = provider,
            binding = provider.prepareCompositionBinding(lifecycleOwner.lifecycle),
        )
    }
    return holder.provider
}

/**
 * Remembers one reference-protected child owner from [provider].
 *
 * Candidate acquisition happens before application content can use the returned owner. Commit
 * retains the lease for this composition slot; abandonment releases it and clears only a child
 * created exclusively by the failed candidate. Forgetting a committed slot releases its reference
 * without terminally clearing the logical child. The owning container calls
 * [ViewModelScopeProvider.clear] when [key] is permanently removed.
 *
 * [savedStateRegistryOwner] defaults to the current ViewModel owner when that owner also implements
 * [SavedStateRegistryOwner]. The value is captured with the remembered lease, including by delayed
 * child sessions. All work is Android-main-thread-confined and bounded in memory.
 *
 * @param key non-null stable child identity inside [provider]
 * @param provider retained provider that owns the child store
 * @param savedStateRegistryOwner optional parent registry and lifecycle for `SavedStateHandle`
 * @return the scoped owner protected while this remembered call remains active
 * @throws IllegalArgumentException when [key] is null through a Java or reflective call
 * @throws IllegalStateException outside active composition, after provider disposal, during
 *   terminal child removal, or for an inconsistent active saved-state boundary
 * @sample com.viewcompose.viewmodel.samples.retainedViewModelScopeSample
 */
@MainThread
fun rememberViewModelStoreOwner(
    key: Any,
    provider: ViewModelScopeProvider,
    savedStateRegistryOwner: SavedStateRegistryOwner? =
        LocalViewModelStoreOwner.current as? SavedStateRegistryOwner,
): ViewModelStoreOwner {
    val holder = remember(
        provider,
        key,
        savedStateRegistryOwner,
    ) {
        RememberedOwnerBinding(
            candidate = provider.acquireCandidate(
                key = key,
                savedStateRegistryOwner = savedStateRegistryOwner,
            ),
        )
    }
    return holder.owner
}

private class RememberedProviderBinding(
    val provider: ViewModelScopeProvider,
    private val binding: ViewModelScopeProvider.ProviderCompositionBinding,
) : RememberObserver {
    override fun onRemembered() {
        binding.commit()
    }

    override fun onForgotten() {
        binding.forget()
    }

    override fun onAbandoned() {
        binding.abandon()
    }
}

private class RememberedOwnerBinding(
    private val candidate: ViewModelScopeProvider.OwnerCandidate,
) : RememberObserver {
    val owner: ViewModelStoreOwner
        get() = candidate.lease.owner

    override fun onRemembered() {
        candidate.commit()
    }

    override fun onForgotten() {
        candidate.lease.close()
    }

    override fun onAbandoned() {
        candidate.abandon()
    }
}

private fun requireLocalViewModelStoreOwner(): ViewModelStoreOwner {
    return checkNotNull(LocalViewModelStoreOwner.current) {
        "No ViewModelStoreOwner found. Use ComponentActivity/Fragment.setUiContent " +
            "or wrap with ProvideViewModelStoreOwner."
    }
}

private fun requireLifecycleOwner(owner: ViewModelStoreOwner): LifecycleOwner {
    return owner as? LifecycleOwner ?: error(
        "The ViewModelScopeProvider parent does not implement LifecycleOwner; " +
            "pass lifecycleOwner explicitly.",
    )
}

private fun ViewModelStoreOwner.defaultFactoryForScope(): ViewModelProvider.Factory {
    return (this as? HasDefaultViewModelProviderFactory)?.defaultViewModelProviderFactory
        ?: ViewModelProvider.NewInstanceFactory()
}

private fun ViewModelStoreOwner.defaultCreationExtrasForScope(): CreationExtras {
    return (this as? HasDefaultViewModelProviderFactory)?.defaultViewModelCreationExtras
        ?: CreationExtras.Empty
}
