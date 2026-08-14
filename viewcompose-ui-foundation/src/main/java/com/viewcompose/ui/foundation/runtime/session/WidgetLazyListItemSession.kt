package com.viewcompose.ui.foundation

import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.ui.node.RenderContainerHandle
import com.viewcompose.ui.node.ReusableItemPresentation

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

    override fun activate(): Boolean {
        session.activatePrepared()
        return session.lastFrameReport?.status == RenderFrameStatus.Committed
    }

    override fun render(): Boolean {
        // Lazy item session bind path must keep immediate render semantics.
        session.render()
        return session.lastFrameReport?.status == RenderFrameStatus.Committed
    }

    override fun dispose() {
        session.disposeWithLogicalOwnerRelease {
            saveableStateLease?.close()
        }
    }

    override fun disposeForReuse(): ReusableItemPresentation? {
        return session.disposeForReuse {
            saveableStateLease?.close()
        }
    }

    override fun adoptReusablePresentation(presentation: ReusableItemPresentation): Boolean {
        return session.adoptReusablePresentation(presentation)
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
