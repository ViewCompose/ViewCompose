package com.viewcompose.renderer.view.lazy.session

import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.ui.node.LazyListItemSessionFactory
import com.viewcompose.renderer.reconcile.LazyListChangePayload

/** Manages the child render-session lifecycle for one lazy item. */
internal class LazyItemSessionController(
    private val createSession: (LazyListItem) -> LazyListItemSession,
    private val clearContainer: () -> Unit,
) {
    private var currentKey: Any? = null
    private var currentContentToken: Any? = null
    private var currentSessionFactory: LazyListItemSessionFactory? = null
    private var currentSessionUpdater: ((LazyListItemSession) -> Unit)? = null
    private var session: LazyListItemSession? = null

    fun bind(
        item: LazyListItem,
        payload: Any? = null,
    ) {
        val shouldRender = when {
            session == null || currentKey != item.key -> {
                // A key change means the holder now represents a different item, so release the old session and clear the container.
                session?.dispose()
                clearContainer()
                val newSession = createSession(item)
                session = newSession
                currentKey = item.key
                currentContentToken = item.contentToken
                currentSessionFactory = item.sessionFactory
                item.sessionUpdater?.invoke(newSession)
                currentSessionUpdater = item.sessionUpdater
                true
            }

            currentContentToken == item.contentToken &&
                currentSessionUpdater === item.sessionUpdater &&
                (item.sessionUpdater != null || currentSessionFactory === item.sessionFactory) -> {
                // RecyclerView may deliver the queued payload after submitItems already refreshed
                // this exact item instance. Do not render the same closure twice: SideEffect and
                // native commit callbacks must still run once per logical child render.
                currentSessionFactory = item.sessionFactory
                false
            }

            payload is LazyListChangePayload.ContentTokenChanged -> {
                // DiffUtil payload only means content token changed; reuse the session but refresh it through updater.
                applyContentTokenUpdate(item)
                true
            }

            currentContentToken == item.contentToken -> {
                val currentSession = session
                val updater = item.sessionUpdater
                if (currentSession != null && updater != null) {
                    // Equal semantic tokens preserve identity, but the parent composition may have
                    // supplied a new closure that captures changed state. Install and render that
                    // closure so a visible retained item cannot display a stale parent snapshot.
                    updater(currentSession)
                    currentSessionFactory = item.sessionFactory
                    currentSessionUpdater = updater
                    true
                } else {
                    replaceSession(item)
                    true
                }
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
        // Release the session when the holder is recycled so off-screen items do not retain Views or closures.
        session?.dispose()
        session = null
        currentKey = null
        currentContentToken = null
        currentSessionFactory = null
        currentSessionUpdater = null
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
            currentSessionFactory = item.sessionFactory
            currentSessionUpdater = item.sessionUpdater
        } else {
            replaceSession(item)
        }
    }

    private fun replaceSession(item: LazyListItem) {
        session?.dispose()
        clearContainer()
        session = createSession(item)
        currentContentToken = item.contentToken
        currentSessionFactory = item.sessionFactory
        currentSessionUpdater = item.sessionUpdater
    }
}
