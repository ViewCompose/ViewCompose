package com.viewcompose.widget.core

import android.util.Log
import android.view.View
import java.util.concurrent.atomic.AtomicBoolean
import java.util.ServiceLoader

/**
 * overlay 提交的 session 标识，用于隔离不同 RenderSession 的 overlay 请求。
 * Session id for overlay commits, isolating overlay requests from different RenderSessions.
 */
data class OverlaySessionId(
    val value: String,
)

/**
 * 框架支持的 overlay 类型。
 * Overlay types supported by the framework.
 */
enum class OverlayType {
    Dialog,
    Snackbar,
    Toast,
    Popup,
    ModalBottomSheet,
}

/**
 * 一条 overlay 声明请求。
 * One declarative overlay request.
 */
data class OverlayRequest(
    val key: String,
    val type: OverlayType,
    val payload: Any? = null,
    val contentToken: Any? = null,
)

/**
 * overlay 宿主接口，平台实现负责展示和清理实际浮层。
 * Overlay host interface; platform implementations show and clear actual surfaces.
 */
interface OverlayHost {
    fun commit(
        sessionId: OverlaySessionId,
        requests: List<OverlayRequest>,
    )

    fun clear(sessionId: OverlaySessionId)
}

/**
 * 从根 View 创建 OverlayHost 的服务提供者。
 * Service provider that creates an OverlayHost from a root View.
 */
fun interface OverlayHostFactoryProvider {
    fun create(rootView: View): OverlayHost
}

/**
 * overlay host 默认实现和 Android service-provider 发现入口。
 * Default overlay host implementations and Android service-provider discovery entry point.
 */
object OverlayHostDefaults {
    private const val TAG = "ViewCompose"
    private val missingAndroidHostWarningLogged = AtomicBoolean(false)

    private val androidOverlayHostProvider: OverlayHostFactoryProvider? by lazy {
        resolveAndroidOverlayHostProvider()
    }

    val noOp: OverlayHost = object : OverlayHost {
        override fun commit(
            sessionId: OverlaySessionId,
            requests: List<OverlayRequest>,
        ) = Unit

        override fun clear(sessionId: OverlaySessionId) = Unit
    }

    /**
     * 返回 Android overlay host；找不到 provider 时返回 no-op 并只记录一次提示。
     * Returns an Android overlay host, or a no-op host with one-time logging when no provider exists.
     */
    fun androidOrNoOp(rootView: View): OverlayHost {
        val provider = androidOverlayHostProvider
        if (provider == null) {
            warnMissingAndroidOverlayHostOnce()
            return noOp
        }
        return runCatching {
            provider.create(rootView)
        }.getOrElse {
            Log.w(
                TAG,
                "Failed to create Android overlay host via service provider. Falling back to no-op host.",
                it,
            )
            noOp
        }
    }

    internal fun hasAndroidOverlayHostProviderForTest(): Boolean {
        return androidOverlayHostProvider != null
    }

    private fun resolveAndroidOverlayHostProvider(): OverlayHostFactoryProvider? {
        return runCatching {
            val providers = ServiceLoader.load(
                OverlayHostFactoryProvider::class.java,
                OverlayHostFactoryProvider::class.java.classLoader,
            ).iterator()
            if (providers.hasNext()) providers.next() else null
        }.getOrNull()
    }

    private fun warnMissingAndroidOverlayHostOnce() {
        if (!missingAndroidHostWarningLogged.compareAndSet(false, true)) {
            return
        }
        Log.i(
            TAG,
            "Android overlay host provider not found; falling back to no-op overlay host. " +
                "Overlay widgets (Dialog/Popup/Snackbar/Toast/BottomSheet) require ui-overlay-android service registration.",
        )
    }
}

internal val LocalOverlayHost = uiLocalOf(
    debugName = "OverlayHost",
    debugValueFormatter = { host ->
        if (host === OverlayHostDefaults.noOp) "none" else host::class.qualifiedName.orEmpty()
    },
) { OverlayHostDefaults.noOp }

/**
 * 当前 composition 中的 overlay host。
 * Overlay host for the current composition.
 */
object OverlayHostContext {
    val current: OverlayHost
        get() = UiLocals.current(LocalOverlayHost)
}

/**
 * 在 content 范围内提供 overlay host。
 * Provides an overlay host within the content scope.
 */
fun UiTreeBuilder.ProvideOverlayHost(
    host: OverlayHost,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(LocalOverlayHost, host) {
        content()
    }
}
