package com.viewcompose.ui.foundation

/** Attempts every owned cleanup before reporting the first failure and its suppressed siblings. */
internal inline fun <T> Iterable<T>.forEachOverlayCleanup(action: (T) -> Unit) {
    var failure: Throwable? = null
    for (entry in this) {
        try {
            action(entry)
        } catch (error: Throwable) {
            val first = failure
            if (first == null) failure = error else if (first !== error) first.addSuppressed(error)
        }
    }
    failure?.let { throw it }
}
