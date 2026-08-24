package com.viewcompose.lifecycle

import androidx.savedstate.SavedStateRegistryOwner
import com.viewcompose.ui.foundation.ProvideLocal
import com.viewcompose.ui.foundation.UiLocals
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.uiLocalOf

private val LocalSavedStateRegistryOwnerValue = uiLocalOf<SavedStateRegistryOwner?>(
    debugName = "SavedStateRegistryOwner",
    debugValueFormatter = { owner -> owner?.javaClass?.name ?: "none" },
) { null }

/** Reads the nearest AndroidX [SavedStateRegistryOwner] for the current ViewCompose subtree. */
object LocalSavedStateRegistryOwner {
    /** Returns the nearest owner, or `null` when the host has no Android saved-state boundary. */
    val current: SavedStateRegistryOwner?
        get() = UiLocals.current(LocalSavedStateRegistryOwnerValue)
}

/**
 * Associates [owner] with [content] without changing lifecycle or ViewModel ownership.
 *
 * Standard Activity, Fragment, navigation, and Preview hosts install this local automatically.
 * Fragment content intentionally receives the Fragment as saved-state owner while
 * [LocalLifecycleOwner] remains the shorter-lived Fragment View owner. Custom hosts use this
 * provider only when they own a fully attached and restored AndroidX saved-state registry.
 *
 * @sample com.viewcompose.lifecycle.samples.provideSavedStateRegistryOwnerSample
 * @receiver tree builder receiving the nested owner boundary
 * @param owner saved-state registry owner exposed to the subtree
 * @param content subtree evaluated with [owner] as [LocalSavedStateRegistryOwner.current]
 */
fun UiTreeBuilder.ProvideSavedStateRegistryOwner(
    owner: SavedStateRegistryOwner,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(
        local = LocalSavedStateRegistryOwnerValue,
        value = owner,
        content = content,
    )
}
