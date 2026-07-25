package com.viewcompose.widget.core

import android.util.Log
import android.view.ViewGroup
import com.viewcompose.runtime.composition.ComposerLite
import java.util.concurrent.atomic.AtomicInteger

class RenderSession(
    private val container: ViewGroup,
    private val content: UiTreeBuilder.() -> Unit,
    private val debug: Boolean = false,
    private val debugTag: String = "ViewCompose",
    private val overlayHost: OverlayHost = OverlayHostDefaults.noOp,
    private val onRenderStats: ((RenderStats) -> Unit)? = null,
    private val onRenderResult: ((RenderTreeResult) -> Unit)? = null,
) {
    private val overlaySessionId = OverlaySessionId("render-session-${nextOverlaySessionId.incrementAndGet()}")
    private var mountedNodes: List<Any> = emptyList()
    private var disposed: Boolean = false
    private val overlayRequestStore = OverlayRequestStore()
    private var requestRender: (() -> Unit)? = null
    private val compositionCoroutineScope = CompositionCoroutineScopeOwner(
        parentContext = renderSessionCoroutineContext(),
        onError = { error ->
            Log.e(debugTag, "Composition coroutine failed", error)
        },
    )
    private val composer = ComposerLite(
        warningLogger = { message -> Log.w(debugTag, message) },
        onInvalidated = { requestRender?.invoke() },
    )
    private val runtime = RenderSessionRuntimeProvider
        .create(
            onRenderNow = ::renderNow,
            onDisposeNow = ::disposeNow,
        ).also { installedRuntime ->
            requestRender = installedRuntime::requestRender
        }

    fun render() {
        runtime.render()
    }

    fun dispose() {
        runtime.dispose()
    }

    private fun renderNow() {
        if (disposed) return
        var preparedComposition:
            ComposerLite.PreparedComposition<List<com.viewcompose.ui.node.VNode>>? = null
        try {
            var tree: List<com.viewcompose.ui.node.VNode> = emptyList()
            if (!composer.hasPendingInvalidations()) {
                // External render requests (e.g. lazy/pager sessionUpdater) must recompose root even
                // without runtime state invalidation signals.
                composer.requestRootRecompose()
            }
            LocalContext.provide(LocalOverlayHost.holder, overlayHost) {
                OverlayRequestContext.withStore(overlayRequestStore) {
                    ComposerContext.withComposer(
                        composer = composer,
                        coroutineContext = compositionCoroutineScope.coroutineContext,
                    ) {
                        preparedComposition = composer.prepareRoot {
                            buildVNodeTree(content)
                        }
                        tree = checkNotNull(preparedComposition).value
                    }
                }
            }
            val frame = CoreRenderEngineProvider.engine.renderInto(
                container = container,
                previousMountedNodes = mountedNodes,
                nodes = tree,
            )
            mountedNodes = frame.mountedNodes
            overlayHost.commit(
                sessionId = overlaySessionId,
                requests = overlayRequestStore.currentRequests(),
            )
            checkNotNull(preparedComposition).commit()
            composer.commitSideEffects()
            if (debug && frame.renderResult == null) {
                Log.d(debugTag, "Rendered ${tree.size} root nodes")
            }
            onRenderStats?.invoke(frame.renderStats)
            frame.renderResult?.let { result ->
                onRenderResult?.invoke(result)
            }
        } catch (e: Exception) {
            preparedComposition?.abort()
            Log.e(debugTag, "Render failed, keeping previous view tree", e)
        }
    }

    private fun disposeNow() {
        if (disposed) return
        disposed = true
        requestRender = null
        compositionCoroutineScope.cancel()
        CoreRenderEngineProvider.engine.disposeMounted(
            container = container,
            mountedNodes = mountedNodes,
        )
        mountedNodes = emptyList()
        overlayHost.clear(overlaySessionId)
        composer.dispose()
    }

    private companion object {
        val nextOverlaySessionId = AtomicInteger(0)
    }
}
