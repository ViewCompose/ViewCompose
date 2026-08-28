package com.viewcompose.ui.foundation

import com.viewcompose.runtime.mutableStateOf
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

    private val stateHolder = saveableStateHolder
    private var saveableStateKey = saveableStateKey
    private var saveableStateLease = saveableStateHolder?.acquire(saveableStateKey)
    private var outgoingSaveableStateLease: SaveableStateRegistryLease? = null
    private val saveableStateRegistryBridge = saveableStateLease
        ?.registry
        ?.let(::MutableSaveableStateRegistryBridge)
    private var capturedLocals = localSnapshot.withChildSaveableStateRegistry()
    private var sourceLocalSnapshot = localSnapshot
    private var renderContent = content
    private var renderContentPayload = contentPayload
    private val observedContentPayloadState = mutableStateOf(contentPayload)
    private val observedContentPayload = observedValue { observedContentPayloadState.value }
    private var payloadOnlyUpdatePending = false
    private val session = RenderSession(
        container = container,
        content = {
            emitDelayedContentRoot(owner = "Lazy item or pager page") {
                withLazyItemOwner(
                    key = this@WidgetLazyListItemSession.saveableStateKey,
                    replaceOwner = outgoingSaveableStateLease != null,
                ) {
                    LocalContext.withSnapshot(capturedLocals) {
                        renderContent.render(
                            builder = this,
                            key = this@WidgetLazyListItemSession.saveableStateKey,
                            payload = renderContentPayload,
                            observedPayload = observedContentPayload,
                        )
                    }
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
        val payloadOnly = payloadOnlyUpdatePending
        if (payloadOnly) {
            session.renderPendingState()
        } else {
            session.render()
        }
        val committed = session.lastFrameReport?.status == RenderFrameStatus.Committed
        if (committed) {
            payloadOnlyUpdatePending = false
            outgoingSaveableStateLease?.close()
            outgoingSaveableStateLease = null
        }
        return committed
    }

    override fun dispose() {
        session.disposeWithLogicalOwnerRelease(::releaseSaveableStateOwnership)
    }

    override fun disposeForReuse(): ReusableItemPresentation? {
        return session.disposeForReuse(::releaseSaveableStateOwnership)
    }

    override fun adoptReusablePresentation(presentation: ReusableItemPresentation): Boolean {
        return session.adoptReusablePresentation(presentation)
    }

    fun updateContent(
        saveableStateKey: Any,
        localSnapshot: LocalSnapshot,
        content: WidgetLazyItemContent,
        contentPayload: Any?,
    ) {
        val keyChanged = this.saveableStateKey != saveableStateKey
        val canPatchPayload = !keyChanged &&
            renderContent === content &&
            content.observesPayload &&
            sourceLocalSnapshot == localSnapshot
        if (keyChanged) {
            check(outgoingSaveableStateLease == null) {
                "A lazy item session cannot begin another owner transfer before rendering."
            }
            outgoingSaveableStateLease = saveableStateLease
            this.saveableStateKey = saveableStateKey
            saveableStateLease = stateHolder?.acquire(saveableStateKey)
            saveableStateLease?.registry?.let { registry ->
                saveableStateRegistryBridge?.delegate = registry
            }
        }
        sourceLocalSnapshot = localSnapshot
        capturedLocals = localSnapshot.withChildSaveableStateRegistry()
        renderContent = content
        renderContentPayload = contentPayload
        observedContentPayloadState.value = contentPayload
        payloadOnlyUpdatePending = canPatchPayload
    }

    fun updateContent(
        localSnapshot: LocalSnapshot,
        content: WidgetLazyItemContent,
        contentPayload: Any?,
    ) {
        updateContent(
            saveableStateKey = saveableStateKey,
            localSnapshot = localSnapshot,
            content = content,
            contentPayload = contentPayload,
        )
    }

    fun updateContent(
        localSnapshot: LocalSnapshot,
        content: UiTreeBuilder.() -> Unit,
    ) {
        updateContent(
            saveableStateKey = saveableStateKey,
            localSnapshot = localSnapshot,
            content = DirectWidgetLazyItemContent,
            contentPayload = content,
        )
    }

    private fun LocalSnapshot.withChildSaveableStateRegistry(): LocalSnapshot {
        return withSaveableStateRegistry(saveableStateRegistryBridge)
    }

    private fun releaseSaveableStateOwnership() {
        val outgoing = outgoingSaveableStateLease
        outgoingSaveableStateLease = null
        outgoing?.close()
        val current = saveableStateLease
        saveableStateLease = null
        current?.close()
    }

}

/** Keeps composition-local identity stable while routing calls to the current logical owner. */
private class MutableSaveableStateRegistryBridge(
    var delegate: SaveableStateRegistry,
) : SaveableStateRegistry {
    override fun claimRestored(key: String): RestoredSaveableValue? =
        delegate.claimRestored(key)

    override fun registerProvider(
        key: String,
        valueProvider: () -> Any?,
    ): SaveableStateRegistry.Entry = delegate.registerProvider(key, valueProvider)

    override fun canBeSaved(value: Any?): Boolean = delegate.canBeSaved(value)

    override fun performSave(): Map<String, Any?> = delegate.performSave()
}

/** Allocation-stable bridge from one declaration strategy to an active item session. */
internal interface WidgetLazyItemContent {
    val observesPayload: Boolean
        get() = false

    fun render(
        builder: UiTreeBuilder,
        key: Any,
        payload: Any?,
        observedPayload: ObservedValue<Any?>,
    )
}

internal data object DirectWidgetLazyItemContent : WidgetLazyItemContent {
    @Suppress("UNCHECKED_CAST")
    override fun render(
        builder: UiTreeBuilder,
        key: Any,
        payload: Any?,
        observedPayload: ObservedValue<Any?>,
    ) {
        (payload as UiTreeBuilder.() -> Unit).invoke(builder)
    }
}

private fun <T> withLazyItemOwner(
    key: Any,
    replaceOwner: Boolean,
    block: () -> T,
): T {
    val composer = ComposerContext.requireCurrentComposer("lazy item owner")
    return composer.withReusableContent(
        ownerKey = key,
        replaceOwner = replaceOwner,
        block = block,
    )
}
