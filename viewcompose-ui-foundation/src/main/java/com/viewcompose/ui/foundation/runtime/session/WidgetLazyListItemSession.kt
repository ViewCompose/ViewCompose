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
    content: WidgetLazyItemContent,
    contentPayload: Any?,
    role: RenderSessionRole = RenderSessionRole.LazyItem,
) : LazyListItemSession {
    constructor(
        container: RenderContainerHandle,
        localSnapshot: LocalSnapshot,
        saveableStateHolder: SaveableStateHolder?,
        saveableStateKey: Any,
        content: UiTreeBuilder.() -> Unit,
    ) : this(
        container = container,
        localSnapshot = localSnapshot,
        saveableStateHolder = saveableStateHolder,
        saveableStateKey = saveableStateKey,
        content = DirectWidgetLazyItemContent,
        contentPayload = content,
        role = RenderSessionRole.LazyItem,
    )

    private val saveableStateLease = saveableStateHolder?.acquire(saveableStateKey)
    private var capturedLocals = localSnapshot.withChildSaveableStateRegistry()
    private var renderContent = content
    private var renderContentPayload = contentPayload
    private val session = RenderSession(
        container = container,
        content = {
            emitDelayedContentRoot(owner = "Lazy item or pager page") {
                LocalContext.withSnapshot(capturedLocals) {
                    renderContent.render(this, renderContentPayload)
                }
            }
        },
        role = role,
        parentLocalSnapshot = UiLocalSnapshot(localSnapshot),
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
        content: WidgetLazyItemContent,
        contentPayload: Any?,
    ) {
        capturedLocals = localSnapshot.withChildSaveableStateRegistry()
        renderContent = content
        renderContentPayload = contentPayload
    }

    fun updateContent(
        localSnapshot: LocalSnapshot,
        content: UiTreeBuilder.() -> Unit,
    ) {
        updateContent(
            localSnapshot = localSnapshot,
            content = DirectWidgetLazyItemContent,
            contentPayload = content,
        )
    }

    private fun LocalSnapshot.withChildSaveableStateRegistry(): LocalSnapshot {
        return withSaveableStateRegistry(saveableStateLease?.registry)
    }

}

/** Allocation-stable bridge from one declaration strategy to an active item session. */
internal fun interface WidgetLazyItemContent {
    fun render(
        builder: UiTreeBuilder,
        payload: Any?,
    )
}

internal data object DirectWidgetLazyItemContent : WidgetLazyItemContent {
    @Suppress("UNCHECKED_CAST")
    override fun render(
        builder: UiTreeBuilder,
        payload: Any?,
    ) {
        (payload as UiTreeBuilder.() -> Unit).invoke(builder)
    }
}
