package com.viewcompose.ui.foundation

import com.viewcompose.ui.focus.FocusManager

/** Exposes the focus owner for the render session currently being composed. */
object LocalFocusManager {
    /**
     * Current focus manager.
     *
     * @throws IllegalStateException outside composition of a mounted render session
     */
    val current: FocusManager
        get() = FocusManagerContext.requireCurrent()
}

/**
 * Thread-local context for the focus manager.
 */
internal object FocusManagerContext {
    private val current = ThreadLocal<FocusManager?>()

    fun withFocusManager(
        focusManager: FocusManager,
        block: () -> Unit,
    ) {
        val previous = current.get()
        current.set(focusManager)
        try {
            block()
        } finally {
            current.set(previous)
        }
    }

    fun requireCurrent(): FocusManager {
        return checkNotNull(current.get()) {
            "LocalFocusManager.current is only available while composing a mounted RenderSession."
        }
    }
}
