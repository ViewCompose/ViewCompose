package com.viewcompose.widget.core

import kotlin.coroutines.CoroutineContext

/**
 * Atomically installs the platform capabilities required by every [RenderSession].
 *
 * Android applications normally receive this installation through `viewcompose-host-android`.
 * Custom hosts must install one complete platform before creating a session.
 */
fun installRenderSessionPlatform(
    renderEngine: CoreRenderEngine,
    coroutineContext: CoroutineContext,
    runtimeFactory: RenderSessionRuntimeFactory,
) {
    RenderSessionPlatformProvider.install(
        RenderSessionPlatform(
            renderEngine = renderEngine,
            coroutineContext = coroutineContext,
            runtimeFactory = runtimeFactory,
        ),
    )
}

internal data class RenderSessionPlatform(
    val renderEngine: CoreRenderEngine,
    val coroutineContext: CoroutineContext,
    val runtimeFactory: RenderSessionRuntimeFactory,
)

internal object RenderSessionPlatformProvider {
    private val registry = RenderSessionPlatformRegistry()

    fun install(platform: RenderSessionPlatform) {
        registry.install(platform)
    }

    fun requirePlatform(): RenderSessionPlatform = registry.requirePlatform()
}

internal class RenderSessionPlatformRegistry {
    @Volatile
    private var installedPlatform: RenderSessionPlatform? = null

    fun install(platform: RenderSessionPlatform) {
        synchronized(this) {
            check(installedPlatform == null) {
                "A RenderSession platform is already installed. " +
                    "Platform capabilities must be installed exactly once per process."
            }
            installedPlatform = platform
        }
    }

    fun requirePlatform(): RenderSessionPlatform {
        return checkNotNull(installedPlatform) {
            "RenderSession platform is not installed. Use viewcompose-host-android " +
                "renderInto/setUiContent, or install a complete custom platform with " +
                "installRenderSessionPlatform()."
        }
    }
}
