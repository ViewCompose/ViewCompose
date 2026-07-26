package com.viewcompose.widget.core

import android.util.Log
import android.view.View
import java.util.concurrent.atomic.AtomicBoolean
import java.util.ServiceLoader

data class OverlaySessionId(
    val value: String,
)

enum class OverlayType {
    Dialog,
    Snackbar,
    Toast,
    Popup,
    ModalBottomSheet,
}

data class OverlayRequest(
    val key: String,
    val type: OverlayType,
    val payload: Any? = null,
    val contentToken: Any? = null,
)

interface OverlayHost {
    fun commit(
        sessionId: OverlaySessionId,
        requests: List<OverlayRequest>,
    )

    fun clear(sessionId: OverlaySessionId)
}

fun interface OverlayHostFactoryProvider {
    fun create(rootView: View): OverlayHost
}

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

object OverlayHostContext {
    val current: OverlayHost
        get() = UiLocals.current(LocalOverlayHost)
}

fun UiTreeBuilder.ProvideOverlayHost(
    host: OverlayHost,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(LocalOverlayHost, host) {
        content()
    }
}
