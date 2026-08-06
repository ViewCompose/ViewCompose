package com.viewcompose.viewmodel

import androidx.lifecycle.ViewModelStoreOwner
import com.viewcompose.ui.foundation.ProvideLocal
import com.viewcompose.ui.foundation.UiLocals
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.uiLocalOf

private val LocalViewModelStoreOwnerValue = uiLocalOf<ViewModelStoreOwner?>(
    debugName = "ViewModelStoreOwner",
    debugValueFormatter = { owner -> owner?.javaClass?.name ?: "none" },
) { null }

/**
 * Reads the Android [ViewModelStoreOwner] associated with the current ViewCompose subtree.
 *
 * Android hosts install their nearest owner automatically. Navigation destinations and graph-owner
 * scopes override it so ViewModels follow entry or graph lifetime rather than Activity lifetime.
 * Delayed child sessions capture and restore the local with the rest of their declaration context.
 */
object LocalViewModelStoreOwner {
    /** Returns the nearest provided owner, or `null` when no host or provider installed one. */
    val current: ViewModelStoreOwner?
        get() = UiLocals.current(LocalViewModelStoreOwnerValue)
}

/**
 * Associates [owner] with [content] and any nested composition-scoped lookup.
 *
 * The previous owner is restored after [content] returns, including when declaration throws. The
 * provider does not clear [owner]'s store; the host that created the owner remains responsible for
 * clearing it at the intended lifecycle boundary.
 *
 * @sample com.viewcompose.viewmodel.samples.provideViewModelStoreOwnerSample
 * @param owner store owner exposed to the subtree
 * @param content declarative subtree evaluated with [owner] as [LocalViewModelStoreOwner.current]
 */
fun UiTreeBuilder.ProvideViewModelStoreOwner(
    owner: ViewModelStoreOwner,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(
        local = LocalViewModelStoreOwnerValue,
        value = owner,
        content = content,
    )
}
