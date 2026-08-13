package com.viewcompose.ui.foundation

import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.ui.node.RenderContainerHandle

/**
 * Captures a lazy item's content token and key so item sessions can recompose the correct content during reuse.
 */
internal data class CapturedLazyContentToken(
    val contentToken: Any?,
    val localSnapshot: LocalSnapshot,
)

/**
 * Reads the lazy item token from the current composition; absence means the call site is outside lazy-content capture.
 */
internal fun capturedLazyContentToken(
    contentToken: Any?,
    localSnapshot: LocalSnapshot,
): CapturedLazyContentToken {
    return CapturedLazyContentToken(
        contentToken = contentToken,
        localSnapshot = localSnapshot,
    )
}

/**
 * Maintains an isolated composition session for one lazy-list item so scroll reuse does not pollute parent state.
 */
internal class WidgetLazyListItemSession(
    container: RenderContainerHandle,
    localSnapshot: LocalSnapshot,
    saveableStateHolder: SaveableStateHolder?,
    saveableStateKey: Any,
    content: UiTreeBuilder.() -> Unit,
) : LazyListItemSession {
    private val saveableStateLease = saveableStateHolder?.acquire(saveableStateKey)
    private var capturedLocals = localSnapshot.withChildSaveableStateRegistry()
    private var renderContent = content
    private var diagnosticsListener = resolveDiagnosticsListener(localSnapshot)
    private val session = RenderSession(
        container = container,
        content = {
            LocalContext.withSnapshot(capturedLocals) {
                renderContent()
            }
        },
        onRenderResult = { result ->
            diagnosticsListener?.invoke(result)
        },
    )

    override fun prepare() {
        session.prepareForActivation()
    }

    override fun activate() {
        session.activatePrepared()
    }

    override fun render() {
        // Lazy item session bind path must keep immediate render semantics.
        session.render()
    }

    override fun dispose() {
        try {
            session.dispose()
        } finally {
            saveableStateLease?.close()
        }
    }

    fun updateContent(
        localSnapshot: LocalSnapshot,
        content: UiTreeBuilder.() -> Unit,
    ) {
        capturedLocals = localSnapshot.withChildSaveableStateRegistry()
        renderContent = content
        // Keep the previously resolved listener if this snapshot does not carry it.
        // This avoids accidentally dropping diagnostics callbacks during partial recomposition paths.
        diagnosticsListener = resolveDiagnosticsListener(localSnapshot) ?: diagnosticsListener
    }

    private fun resolveDiagnosticsListener(
        snapshot: LocalSnapshot,
    ): ((RenderTreeResult) -> Unit)? {
        @Suppress("UNCHECKED_CAST")
        return snapshot.values[LocalRenderResultListener.holder] as? ((RenderTreeResult) -> Unit)
    }

    private fun LocalSnapshot.withChildSaveableStateRegistry(): LocalSnapshot {
        return withSaveableStateRegistry(saveableStateLease?.registry)
    }
}
