package com.viewcompose.material3

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.os.Looper
import com.viewcompose.runtime.MutableState
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.DisposableEffect
import com.viewcompose.ui.foundation.UiTheme
import com.viewcompose.ui.foundation.UiThemeTokens
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.remember
import java.lang.ref.WeakReference

/**
 * Invalidates active Material 3 theme providers after an imperative Android theme mutation.
 *
 * Create one controller for each host that changes theme resources without an Android
 * configuration change. [refresh] is main-thread confined and affects only currently active
 * [Material3Theme] providers that use this controller.
 *
 * @sample com.viewcompose.material3.samples.material3ThemeRefreshSample
 */
class Material3ThemeRefreshController {
    private val listeners = linkedSetOf<() -> Unit>()

    /**
     * Requests every active provider to reread its Android theme on the main thread.
     *
     * The refresh completes synchronously. Calling it when no matching provider is active has no
     * effect.
     *
     * @throws IllegalStateException when called off the Android main thread
     */
    fun refresh() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "Material3ThemeRefreshController.refresh() must be called on the main thread."
        }
        listeners.toList().forEach { listener -> listener() }
    }

    internal fun subscribe(listener: () -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }
}

/**
 * Resolves Material 3 Android theme resources and provides the resulting framework tokens.
 *
 * The resolved context must also be used to create the tree's Views and overlays so native and
 * declarative surfaces observe the same dynamic-color and configuration state.
 *
 * The provider registers for Android configuration changes while it is composed and unregisters
 * when disposed. An optional refresh controller supports imperative theme mutations.
 *
 * @sample com.viewcompose.material3.samples.material3ThemeSample
 * @receiver builder receiving the scoped Material 3 tokens
 * @param resolvedTheme resolved Android theme whose stable context creates the tree's Views
 * @param refreshController optional host controller for theme changes that do not recreate or
 * dispatch a configuration change
 * @param content declarative subtree that reads the resolved framework theme tokens
 */
fun UiTreeBuilder.Material3Theme(
    resolvedTheme: Material3ResolvedTheme,
    refreshController: Material3ThemeRefreshController? = null,
    content: UiTreeBuilder.() -> Unit,
) {
    val lifecycle = remember(resolvedTheme) {
        Material3ThemeTokenLifecycle(resolvedTheme)
    }
    DisposableEffect(lifecycle, refreshController) {
        lifecycle.start()
        val unsubscribe = refreshController?.subscribe(lifecycle::refresh)
        val disposeEffect: () -> Unit = {
            unsubscribe?.invoke()
            lifecycle.close()
        }
        disposeEffect
    }
    UiTheme(tokens = lifecycle.tokens.value, content = content)
}

/** Observes Android configuration changes and advances the immutable theme-token revision. */
@Suppress("DEPRECATION")
internal class Material3ThemeTokenLifecycle(
    context: Context,
    private val dynamicColorPolicy: Material3DynamicColorPolicy,
    private val resolvedTheme: Material3ResolvedTheme? = null,
) : ComponentCallbacks {
    private val contextReference = WeakReference(context)
    private val callbackContext = context.applicationContext
    private var started = false
    private var revision = 0L
    val tokens: MutableState<UiThemeTokens> = mutableStateOf(readTokens(context))

    constructor(resolvedTheme: Material3ResolvedTheme) : this(
        context = resolvedTheme.context,
        dynamicColorPolicy = Material3DynamicColorPolicy.Disabled,
        resolvedTheme = resolvedTheme,
    )

    fun start() {
        if (started) return
        started = true
        callbackContext.registerComponentCallbacks(this)
    }

    fun close() {
        if (!started) return
        started = false
        callbackContext.unregisterComponentCallbacks(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) = refresh()

    fun refresh() {
        val context = contextReference.get() ?: run {
            close()
            return
        }
        resolvedTheme?.refreshContext()
        revision += 1
        val resolved = resolvedTheme?.let(Material3ThemeBridge::fromResolvedTheme)
            ?: Material3ThemeBridge.fromContext(context, dynamicColorPolicy)
        tokens.value = resolved.copy(metadata = resolved.metadata.copy(revision = revision))
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onLowMemory() = Unit

    private fun readTokens(context: Context): UiThemeTokens {
        return resolvedTheme?.let(Material3ThemeBridge::fromResolvedTheme)
            ?: Material3ThemeBridge.fromContext(context, dynamicColorPolicy)
    }
}
