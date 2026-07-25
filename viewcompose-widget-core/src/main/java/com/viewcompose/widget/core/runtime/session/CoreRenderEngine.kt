package com.viewcompose.widget.core

import android.util.Log
import android.view.ViewGroup
import com.viewcompose.ui.node.VNode

/**
 * Android rendering engine contract registered by host-android at runtime.
 * widget-core keeps this contract to avoid a direct renderer dependency.
 */
interface CoreRenderEngine {
    fun renderInto(
        container: ViewGroup,
        previousMountedNodes: List<Any>,
        nodes: List<VNode>,
        collectDiagnostics: Boolean,
    ): CoreRenderFrame

    fun disposeMounted(
        container: ViewGroup,
        mountedNodes: List<Any>,
    )
}

data class CoreRenderFrame(
    val mountedNodes: List<Any>,
    val renderStats: RenderStats = RenderStats(),
    val renderResult: RenderTreeResult? = null,
)

fun installCoreRenderEngine(engine: CoreRenderEngine) {
    CoreRenderEngineProvider.install(engine)
}

internal object CoreRenderEngineProvider {
    private const val TAG = "ViewCompose"
    private val fallbackEngine = NoOpCoreRenderEngine
    @Volatile
    private var installedEngine: CoreRenderEngine? = null
    @Volatile
    private var warnedMissingEngine: Boolean = false

    val engine: CoreRenderEngine
        get() {
            val current = installedEngine
            if (current != null) {
                return current
            }
            warnMissingEngineOnce()
            return fallbackEngine
        }

    fun install(engine: CoreRenderEngine) {
        installedEngine = engine
    }

    private fun warnMissingEngineOnce() {
        if (warnedMissingEngine) {
            return
        }
        warnedMissingEngine = true
        Log.w(
            TAG,
            "Host render engine is not installed; widget-core render sessions will no-op.",
        )
    }

    private object NoOpCoreRenderEngine : CoreRenderEngine {
        override fun renderInto(
            container: ViewGroup,
            previousMountedNodes: List<Any>,
            nodes: List<VNode>,
            collectDiagnostics: Boolean,
        ): CoreRenderFrame {
            return CoreRenderFrame(
                mountedNodes = previousMountedNodes,
            )
        }

        override fun disposeMounted(
            container: ViewGroup,
            mountedNodes: List<Any>,
        ) = Unit
    }
}
