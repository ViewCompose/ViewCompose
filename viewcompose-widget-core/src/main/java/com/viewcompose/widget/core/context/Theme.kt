package com.viewcompose.widget.core

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
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

object Theme {
    val current: UiThemeTokens
        get() = UiLocals.current(LocalTheme)

    val colors: UiColors
        get() = current.colors

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
    dynamicColorPolicy: AndroidDynamicColorPolicy = AndroidDynamicColorPolicy.UseIfAvailable,
    content: UiTreeBuilder.() -> Unit,
) {
    val resolvedTokens = tokens
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
                DisposableEffect(lifecycle) {
                    lifecycle.start()
                    lifecycle::close
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
) : ComponentCallbacks {
    private val contextReference = WeakReference(context)
    private val callbackContext = context.applicationContext
    private var started = false
    private var revision = 0L
    val tokens: MutableState<UiThemeTokens> = mutableStateOf(readTokens(context))

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
        val context = contextReference.get() ?: run {
            close()
            return
        }
        revision += 1
        tokens.value = readTokens(context)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onLowMemory() = Unit

    private fun readTokens(context: Context): UiThemeTokens {
        val resolved = AndroidThemeBridge.fromContext(
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
    typography: (UiTypography.() -> UiTypography)? = null,
    shapes: (UiShapes.() -> UiShapes)? = null,
    controls: (UiControlSizing.() -> UiControlSizing)? = null,
    overlays: (UiOverlays.() -> UiOverlays)? = null,
    content: UiTreeBuilder.() -> Unit,
) {
    UiThemeOverride(
        colors = colors?.invoke(Theme.colors),
        typography = typography?.invoke(Theme.typography),
        shapes = shapes?.invoke(Theme.shapes),
        controls = controls?.invoke(Theme.controls),
        overlays = overlays?.invoke(Theme.overlays),
        content = content,
    )
}
