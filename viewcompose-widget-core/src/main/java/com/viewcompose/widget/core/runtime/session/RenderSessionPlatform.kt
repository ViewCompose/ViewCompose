package com.viewcompose.widget.core

import kotlin.coroutines.CoroutineContext

/**
 * 原子安装每个 [RenderSession] 所需的平台能力。
 * Atomically installs the platform capabilities required by every [RenderSession].
 *
 * Android 应用通常由 `viewcompose-host-android` 完成安装。
 * 自定义宿主必须在创建 session 前安装一套完整平台能力。
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

/**
 * RenderSession 运行所需的完整平台能力集合。
 * Complete platform capability set required by RenderSession.
 */
internal data class RenderSessionPlatform(
    val renderEngine: CoreRenderEngine,
    val coroutineContext: CoroutineContext,
    val runtimeFactory: RenderSessionRuntimeFactory,
)

/**
 * 进程级平台能力提供者。
 * Process-wide provider for platform capabilities.
 */
internal object RenderSessionPlatformProvider {
    private val registry = RenderSessionPlatformRegistry()

    fun install(platform: RenderSessionPlatform) {
        registry.install(platform)
    }

    fun requirePlatform(): RenderSessionPlatform = registry.requirePlatform()
}

/**
 * 一次性安装的平台注册表，防止同一进程混用多套 renderer/runtime。
 * One-shot platform registry that prevents mixing multiple renderer/runtime stacks in one process.
 */
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
