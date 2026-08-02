package com.viewcompose.renderer.view.lazy.session

import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.renderer.reconcile.LazyListChangePayload

/**
 * Manages the child render-session lifecycle for one lazy item.
 * Manages the lifecycle of the inner render session for one lazy item.
 */
internal class LazyItemSessionController(
    private val createSession: (LazyListItem) -> LazyListItemSession,
    private val clearContainer: () -> Unit,
) {
    private var currentKey: Any? = null
    private var currentContentToken: Any? = null
    private var session: LazyListItemSession? = null

    fun bind(
        item: LazyListItem,
        payload: Any? = null,
    ) {
        val shouldRender = when {
            session == null || currentKey != item.key -> {
                // A key change means the holder now represents another item; dispose its old session and clear the container.
                // A key change means the holder now represents a different item, so release the old session and clear the container.
                session?.dispose()
                clearContainer()
                val newSession = createSession(item)
                session = newSession
                currentKey = item.key
                currentContentToken = item.contentToken
                item.sessionUpdater?.invoke(newSession)
                true
            }

            payload is LazyListChangePayload.ContentTokenChanged -> {
                // A DiffUtil payload changes content but not identity, so retain the session and refresh it through the updater.
                // DiffUtil payload only means content token changed; reuse the session but refresh it through updater.
                applyContentTokenUpdate(item)
                true
            }

            currentContentToken == item.contentToken -> {
                session?.let { currentSession ->
                    item.sessionUpdater?.invoke(currentSession)
                }
                false
            }

            else -> {
                applyContentTokenUpdate(item)
                true
            }
        }
        if (shouldRender) {
            session?.render()
        }
    }

    fun recycle() {
        // Dispose on holder recycling so off-screen items cannot retain Views or captured closures.
        // Release the session when the holder is recycled so off-screen items do not retain Views or closures.
        session?.dispose()
        session = null
        currentKey = null
        currentContentToken = null
        clearContainer()
    }

    private fun applyContentTokenUpdate(item: LazyListItem) {
        val currentSession = session
        if (currentSession != null && item.sessionUpdater != null) {
            val updater = item.sessionUpdater
            if (updater != null) {
                updater(currentSession)
            }
            currentContentToken = item.contentToken
        } else {
            currentSession?.dispose()
            clearContainer()
            session = createSession(item)
            currentContentToken = item.contentToken
        }
    }
}
