package com.viewcompose.lifecycle

import androidx.lifecycle.LifecycleOwner
import com.viewcompose.ui.foundation.ProvideLocal
import com.viewcompose.ui.foundation.UiLocals
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.uiLocalOf

private val LocalLifecycleOwnerValue = uiLocalOf<LifecycleOwner?>(
    debugName = "LifecycleOwner",
    debugValueFormatter = { owner -> owner?.lifecycle?.currentState?.name ?: "none" },
) { null }

/**
 * Reads the Android [LifecycleOwner] associated with the current ViewCompose subtree.
 *
 * Standard Activity, Fragment, and Android View hosts install this local automatically. Nested
 * session containers capture and restore it with other ViewCompose locals. Custom render hosts can
 * establish an explicit boundary with [ProvideLifecycleOwner].
 */
object LocalLifecycleOwner {
    /** Returns the nearest provided owner, or `null` when no host or provider installed one. */
    val current: LifecycleOwner?
        get() = UiLocals.current(LocalLifecycleOwnerValue)
}

/**
 * Associates [owner] with [content] and any nested composition-scoped work.
 *
 * The previous owner is restored after [content] returns, including when declaration throws. Use
 * this at custom host or explicit nested-lifecycle boundaries; ordinary Activity and Fragment hosts
 * already provide their owner.
 *
 * @sample com.viewcompose.lifecycle.samples.provideLifecycleOwnerSample
 * @param owner lifecycle owner exposed to the subtree
 * @param content declarative subtree evaluated with [owner] as [LocalLifecycleOwner.current]
 */
fun UiTreeBuilder.ProvideLifecycleOwner(
    owner: LifecycleOwner,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(
        local = LocalLifecycleOwnerValue,
        value = owner,
        content = content,
    )
}
