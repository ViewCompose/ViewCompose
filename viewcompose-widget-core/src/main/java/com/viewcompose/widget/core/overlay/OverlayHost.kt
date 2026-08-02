package com.viewcompose.widget.core

import android.util.Log
import android.view.View
import java.util.concurrent.atomic.AtomicBoolean
import java.util.ServiceLoader

/**
 * Identifies overlay commits owned by one render session.
 *
 * @property value stable identifier that must not be reused by simultaneously active sessions
 */
data class OverlaySessionId(
    val value: String,
)

/** Overlay surface and transient-feedback types supported by the core protocol. */
enum class OverlayType {
    Dialog,
    Snackbar,
    Toast,
    Popup,
    ModalBottomSheet,
}

/**
 * Describes one overlay that a render frame wants to keep active.
 *
 * A host scopes [key] by the committing [OverlaySessionId]. A later commit replaces the request
 * with the same scoped key and removes requests omitted by that session.
 *
 * @property key stable identity within one render session
 * @property type protocol discriminator used by specialized hosts
 * @property payload type-specific behavior and presentation options
 * @property contentToken optional type-specific content snapshot or identity token
 */
data class OverlayRequest(
    val key: String,
    val type: OverlayType,
    val payload: Any? = null,
    val contentToken: Any? = null,
)

/**
 * Reconciles declarative overlay requests with platform surfaces.
 *
 * Calls are isolated by [OverlaySessionId]. Implementations must not remove overlays owned by a
 * different session and must make repeated commits of an unchanged request idempotent.
 */
interface OverlayHost {
    /**
     * Makes [requests] the complete desired overlay set for [sessionId].
     *
     * Requests previously committed by this session but absent from [requests] must be dismissed.
     */
    fun commit(
        sessionId: OverlaySessionId,
        requests: List<OverlayRequest>,
    )

    /** Dismisses every active overlay owned by [sessionId]. */
    fun clear(sessionId: OverlaySessionId)
}

/** Creates a platform [OverlayHost] attached to a root Android [View]. */
fun interface OverlayHostFactoryProvider {
    /** Creates a host whose platform surfaces are attached to [rootView]'s window. */
    fun create(rootView: View): OverlayHost
}

/**
 * Default overlay host implementations and Android service-provider discovery entry point.
 */
object OverlayHostDefaults {
    private const val TAG = "ViewCompose"
    private val missingAndroidHostWarningLogged = AtomicBoolean(false)

    private val androidOverlayHostProvider: OverlayHostFactoryProvider? by lazy {
        resolveAndroidOverlayHostProvider()
    }

    /**
     * A host that ignores every request.
     *
     * This fallback keeps the core renderer operational when no platform overlay module is present.
     */
    val noOp: OverlayHost = object : OverlayHost {
        override fun commit(
            sessionId: OverlaySessionId,
            requests: List<OverlayRequest>,
        ) = Unit

        override fun clear(sessionId: OverlaySessionId) = Unit
    }

    /**
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

/** Exposes the overlay host installed for the current composition. */
object OverlayHostContext {
    /** Returns the nearest provided host, or [OverlayHostDefaults.noOp] when none is installed. */
    val current: OverlayHost
        get() = UiLocals.current(LocalOverlayHost)
}

/**
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
