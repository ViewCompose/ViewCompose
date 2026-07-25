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
        var tree: List<com.viewcompose.ui.node.VNode> = emptyList()
        val frame = try {
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
            CoreRenderEngineProvider.engine.renderInto(
                container = container,
                previousMountedNodes = mountedNodes,
                nodes = tree,
            )
        } catch (error: Exception) {
            try {
                preparedComposition?.abort()
            } catch (abortError: Throwable) {
                error.addSuppressed(abortError)
            }
            Log.e(debugTag, "Render failed, restored previous composition and view tree", error)
            return
        }

        mountedNodes = frame.mountedNodes
        try {
            checkNotNull(preparedComposition).commit()
        } catch (error: Exception) {
            Log.e(debugTag, "Composition lifecycle callback failed during commit", error)
        }
        try {
            composer.commitSideEffects()
        } catch (error: Exception) {
            Log.e(debugTag, "Post-commit composition effect failed", error)
        }
        try {
            overlayHost.commit(
                sessionId = overlaySessionId,
                requests = overlayRequestStore.currentRequests(),
            )
        } catch (error: Exception) {
            Log.e(debugTag, "Overlay commit failed after view render", error)
        }
        if (debug && frame.renderResult == null) {
            Log.d(debugTag, "Rendered ${tree.size} root nodes")
        }
        try {
            onRenderStats?.invoke(frame.renderStats)
            frame.renderResult?.let { result ->
                onRenderResult?.invoke(result)
            }
        } catch (error: Exception) {
            Log.e(debugTag, "Render diagnostics callback failed", error)
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
