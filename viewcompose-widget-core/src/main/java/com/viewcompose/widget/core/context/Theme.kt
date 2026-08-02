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
 * Invalidates active Android-backed theme providers after runtime resource mutation.
 *
 * Configuration changes are observed automatically. A host that calls `Context.setTheme` or
 * applies a style without a configuration change calls [refresh] on the main thread. Subscriptions
 * are owned by active `UiTheme` effects and are removed when those providers leave composition.
 */
class AndroidThemeRefreshController {
    private val listeners = linkedSetOf<() -> Unit>()

    /**
     * Synchronously requests every active provider to reread its Android theme.
     *
     * @throws IllegalStateException when called off the Android main thread
     */
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

/** Exposes the immutable theme snapshot and its token families for the current composition. */
object Theme {
    /** Current complete theme snapshot. */
    val current: UiThemeTokens
        get() = UiLocals.current(LocalTheme)

    /** Current semantic color scheme. */
    val colors: UiColors
        get() = current.colors

    /** Current state-aware component colors. */
    val stateColors: UiStateColors
        get() = current.stateColors

    /** Current typography tiers. */
    val typography: UiTypography
        get() = current.typography

    /** Current component shape tiers. */
    val shapes: UiShapes
        get() = current.shapes

    /** Current core component sizing tokens. */
    val controls: UiControlSizing
        get() = current.controls

    /** Current modal overlay tokens. */
    val overlays: UiOverlays
        get() = current.overlays
}

/**
 * Resolves and provides one theme snapshot while building [content].
 *
 * [tokens], [androidContext], and [resolvedAndroidTheme] are mutually exclusive. Explicit tokens
 * are used unchanged. A resolved Android theme keeps root Views and overlays on the same mutable
 * themed context. A plain Android context is resolved according to [dynamicColorPolicy]. When no
 * source is supplied, [UiThemeDefaults.light] is used.
 *
 * During mounted composition, Android-backed sources install a lifecycle that observes
 * configuration changes and [refreshController]. Outside a composer, Android tokens are read once.
 * Nested providers restore the previous theme after [content] returns.
 *
 * @sample com.viewcompose.widget.core.samples.themeProviderSample
 * @throws IllegalArgumentException if more than one theme source is supplied
 */
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

/**
 * Lifecycle for Android theme tokens, observing configuration changes and driving recomposition by revision.
 */
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

/**
 * Provides selected token families over the current theme while building [content].
 *
 * When [colors] changes without an explicit [stateColors], state colors are re-derived from the new
 * scheme. The resulting metadata origin is [UiThemeOrigin.Override].
 */
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

/**
 * Computes and provides selected token families from their current values.
 *
 * Every non-null transformation runs immediately and exactly once for this tree build before the
 * delegated provider executes [content]. Color changes without a state-color transformation
 * re-derive state colors through [UiThemeTokens.override].
 */
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
