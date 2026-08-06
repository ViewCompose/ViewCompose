package com.viewcompose.host.android.overlay

import android.util.Log
import android.view.View
import com.viewcompose.ui.foundation.OverlayHost
import com.viewcompose.ui.foundation.OverlayHostDefaults
import java.util.ServiceLoader
import java.util.concurrent.atomic.AtomicBoolean

/** Creates an Android [OverlayHost] attached to [rootView]'s window. */
fun interface AndroidOverlayHostFactoryProvider {
    /** Creates a host whose platform surfaces are scoped to the supplied Android root. */
    fun create(rootView: View): OverlayHost
}

/** Android service-provider discovery and fallback behavior for optional overlay integrations. */
object AndroidOverlayHostDefaults {
    private const val TAG = "ViewCompose"
    private val missingProviderWarningLogged = AtomicBoolean(false)

    private val provider: AndroidOverlayHostFactoryProvider? by lazy {
        runCatching {
            val providers = ServiceLoader.load(
                AndroidOverlayHostFactoryProvider::class.java,
                AndroidOverlayHostFactoryProvider::class.java.classLoader,
            ).iterator()
            if (providers.hasNext()) providers.next() else null
        }.getOrNull()
    }

    /** Returns the installed Android overlay host or the platform-independent no-op fallback. */
    fun androidOrNoOp(rootView: View): OverlayHost {
        val resolvedProvider = provider
        if (resolvedProvider == null) {
            warnMissingProviderOnce()
            return OverlayHostDefaults.noOp
        }
        return runCatching {
            resolvedProvider.create(rootView)
        }.getOrElse { error ->
            Log.w(
                TAG,
                "Failed to create Android overlay host via service provider. Falling back to no-op host.",
                error,
            )
            OverlayHostDefaults.noOp
        }
    }

    internal fun hasProviderForTest(): Boolean = provider != null

    private fun warnMissingProviderOnce() {
        if (!missingProviderWarningLogged.compareAndSet(false, true)) return
        Log.i(
            TAG,
            "Android overlay host provider not found; falling back to no-op overlay host. " +
                "Overlay widgets require viewcompose-overlay-material3-android on the runtime classpath.",
        )
    }
}
