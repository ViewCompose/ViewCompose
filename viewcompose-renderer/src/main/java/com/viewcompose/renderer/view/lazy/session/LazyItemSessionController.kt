package com.viewcompose.renderer.view.lazy.session

import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.renderer.reconcile.LazyListChangePayload

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
