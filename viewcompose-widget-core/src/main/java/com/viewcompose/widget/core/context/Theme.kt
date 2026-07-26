package com.viewcompose.widget.core

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.os.Looper
import com.viewcompose.runtime.MutableState
import com.viewcompose.runtime.mutableStateOf
import java.lang.ref.WeakReference

private val LocalTheme = uiLocalOf(
    debugName = "Theme",
    debugValueFormatter = { tokens ->
        "${tokens.metadata.origin}, dark=${tokens.metadata.isDark}, revision=${tokens.metadata.revision}"
    },
    defaultFactory = UiThemeDefaults::light,
)

/**
 * Explicit invalidation entry point for hosts that mutate Android theme
 * resources at runtime (for example with Context.setTheme/applyStyle).
 */
class AndroidThemeRefreshController {
    private val listeners = linkedSetOf<() -> Unit>()

    fun refresh() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "AndroidThemeRefreshController.refresh() must be called on the main thread."
        }
        listeners.toList().forEach { listener -> listener() }
    }

    internal fun subscribe(listener: () -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }
}

object Theme {
    val current: UiThemeTokens
        get() = UiLocals.current(LocalTheme)

    val colors: UiColors
        get() = current.colors

    val stateColors: UiStateColors
        get() = current.stateColors

    val typography: UiTypography
        get() = current.typography

    val shapes: UiShapes
        get() = current.shapes

    val controls: UiControlSizing
        get() = current.controls

    val overlays: UiOverlays
        get() = current.overlays
}

fun UiTreeBuilder.UiTheme(
    tokens: UiThemeTokens? = null,
    androidContext: Context? = null,
    resolvedAndroidTheme: AndroidResolvedTheme? = null,
    dynamicColorPolicy: AndroidDynamicColorPolicy = AndroidDynamicColorPolicy.UseIfAvailable,
    refreshController: AndroidThemeRefreshController? = null,
    content: UiTreeBuilder.() -> Unit,
) {
    val sourceCount = listOfNotNull(tokens, androidContext, resolvedAndroidTheme).size
    require(sourceCount <= 1) {
        "UiTheme accepts only one source: tokens, androidContext, or resolvedAndroidTheme."
    }
    val resolvedTokens = tokens
        ?: resolvedAndroidTheme?.let { resolvedTheme ->
            if (ComposerContext.currentComposer() == null) {
                AndroidThemeBridge.fromResolvedTheme(resolvedTheme)
            } else {
                val lifecycle = remember(resolvedTheme) {
                    AndroidThemeTokenLifecycle(resolvedTheme)
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
                lifecycle.tokens.value
            }
        }
        ?: androidContext?.let { context ->
            if (ComposerContext.currentComposer() == null) {
                AndroidThemeBridge.fromContext(
                    context = context,
                    dynamicColorPolicy = dynamicColorPolicy,
                )
            } else {
                val lifecycle = remember(context, dynamicColorPolicy) {
                    AndroidThemeTokenLifecycle(
                        context = context,
                        dynamicColorPolicy = dynamicColorPolicy,
                    )
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
                lifecycle.tokens.value
            }
        }
        ?: UiThemeDefaults.light()
    ProvideLocal(local = LocalTheme, value = resolvedTokens) {
        content()
    }
}

@Suppress("DEPRECATION")
internal class AndroidThemeTokenLifecycle(
    context: Context,
    private val dynamicColorPolicy: AndroidDynamicColorPolicy,
    private val resolvedTheme: AndroidResolvedTheme? = null,
) : ComponentCallbacks {
    private val contextReference = WeakReference(context)
    private val callbackContext = context.applicationContext
    private var started = false
    private var revision = 0L
    val tokens: MutableState<UiThemeTokens> = mutableStateOf(readTokens(context))

    constructor(
        resolvedTheme: AndroidResolvedTheme,
    ) : this(
        context = resolvedTheme.context,
        dynamicColorPolicy = AndroidDynamicColorPolicy.Disabled,
        resolvedTheme = resolvedTheme,
    )

    fun start() {
        if (started) {
            return
        }
        started = true
        callbackContext.registerComponentCallbacks(this)
    }

    fun close() {
        if (!started) {
            return
        }
        started = false
        callbackContext.unregisterComponentCallbacks(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        refresh()
    }

    fun refresh() {
        val context = contextReference.get() ?: run {
            close()
            return
        }
        resolvedTheme?.refreshContext()
        revision += 1
        tokens.value = readTokens(context)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onLowMemory() = Unit

    private fun readTokens(context: Context): UiThemeTokens {
        val resolved = resolvedTheme?.let { theme ->
            AndroidThemeBridge.fromResolvedTheme(theme)
        } ?: AndroidThemeBridge.fromContext(
            context = context,
            dynamicColorPolicy = dynamicColorPolicy,
        )
        return resolved.copy(
            metadata = resolved.metadata.copy(revision = revision),
        )
    }
}

fun UiTreeBuilder.UiThemeOverride(
    colors: UiColors? = null,
    stateColors: UiStateColors? = null,
    typography: UiTypography? = null,
    shapes: UiShapes? = null,
    controls: UiControlSizing? = null,
    overlays: UiOverlays? = null,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(
        local = LocalTheme,
        value = Theme.current.override(
            colors = colors,
            stateColors = stateColors,
            typography = typography,
            shapes = shapes,
            controls = controls,
            overlays = overlays,
        ),
    ) {
        content()
    }
}

fun UiTreeBuilder.UiThemeOverride(
    colors: (UiColors.() -> UiColors)? = null,
    stateColors: (UiStateColors.() -> UiStateColors)? = null,
    typography: (UiTypography.() -> UiTypography)? = null,
    shapes: (UiShapes.() -> UiShapes)? = null,
    controls: (UiControlSizing.() -> UiControlSizing)? = null,
    overlays: (UiOverlays.() -> UiOverlays)? = null,
    content: UiTreeBuilder.() -> Unit,
) {
    UiThemeOverride(
        colors = colors?.invoke(Theme.colors),
        stateColors = stateColors?.invoke(Theme.stateColors),
        typography = typography?.invoke(Theme.typography),
        shapes = shapes?.invoke(Theme.shapes),
        controls = controls?.invoke(Theme.controls),
        overlays = overlays?.invoke(Theme.overlays),
        content = content,
    )
}
